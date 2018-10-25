package de.miniserver;

import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.MeinThread;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.execute.SqliteExecutor;
import de.mein.konsole.Konsole;
import de.mein.konsole.ParseArgumentException;
import de.mein.sql.*;
import de.mein.sql.conn.SQLConnector;
import de.mein.sql.transform.SqlResultTransformer;
import de.miniserver.data.FileRepository;
import de.miniserver.data.VersionAnswer;
import de.miniserver.socket.BinarySocketOpener;
import de.miniserver.socket.EncSocketOpener;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

public class MiniServer {
    public static final File DEFAULT_WORKING_DIR = new File("miniserver.w");
    public static final String DIR_FILES_NAME = "files";
    public static final String LATEST_APK = "latest.apk";
    public static final String APK_INFO = "apk.info";
    public static final String LATEST_JAR = "latest.jar";
    public static final String JAR_INFO = "jar.info";
    public static final Integer DEFAULT_ENCRYPTED_PORT = 8956;
    public static final int DEFAULT_BINARY_PORT = 8957;
    private final static Semaphore threadSemaphore = new Semaphore(1, true);
    private final static LinkedList<MeinThread> threadQueue = new LinkedList<>();

    private final CertificateManager certificateManager;
    private final VersionAnswer versionAnswer;
    private final ThreadFactory threadFactory = r -> {
        MeinThread meinThread = null;
        //noinspection Duplicates
        try {
            threadSemaphore.acquire();
            meinThread = threadQueue.poll();
            threadSemaphore.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return meinThread;
    };
    private ExecutorService executorService;
    private FileRepository fileRepository;

    public MiniServer(File workingDir) throws Exception {
        workingDir.mkdir();
        File dbFile = new File(workingDir, "db.db");
        SQLQueries sqlQueries = new SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
        // turn on foreign keys
        try {
            sqlQueries.execute("PRAGMA foreign_keys = ON;", null);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }

        SqliteExecutor sqliteExecutor = new SqliteExecutor(sqlQueries.getSQLConnection());
        if (!sqliteExecutor.checkTablesExist("servicetype", "service", "approval", "certificate")) {
            //find sql file in workingdir
            InputStream resourceStream = DatabaseManager.class.getClassLoader().getResourceAsStream("sql.sql");
            sqliteExecutor.executeStream(resourceStream);
        }
        certificateManager = new CertificateManager(new File("miniserver.w"), sqlQueries, 2048);

        // loading and hashing files
        File filesDir = new File(workingDir, DIR_FILES_NAME);
        filesDir.mkdir();
        versionAnswer = new VersionAnswer();
        fileRepository = new FileRepository();

        File apkFile = new File(filesDir, LATEST_APK);
        File androidInfoFile = new File(filesDir, APK_INFO);
        Pair<Long> androidPair = readFiles(apkFile, androidInfoFile);
        if (androidPair != null) {
            versionAnswer.setAndroidVersion(androidPair.v()).setAndroidSHA256(androidPair.k());
            fileRepository.addEntry(androidPair.k(), apkFile);
        }

        File jarFile = new File(filesDir, LATEST_JAR);
        File pcInfoFile = new File(filesDir, JAR_INFO);
        Pair<Long> pcPair = readFiles(jarFile, pcInfoFile);
        if (pcPair != null) {
            versionAnswer.setPcVersion(pcPair.v()).setPcSHA256(pcPair.k());
            fileRepository.addEntry(pcPair.k(), jarFile);
        }
    }

    static class ArgumentsBundle {
        String certPath;
    }

    public static void main(String[] arguments) throws Exception {
        Konsole<ServerConfig> konsole = new Konsole(new ServerConfig());
        konsole.optional("-cert", "path to certificate", (result, args) -> result.setCertPath(args[0]))
                .optional("-dir", "path to working directory", (result, args) -> result.setWorkingDirectory(args[0]))
                .optional("-files", "pairs of files and versions. eg: '-files -f1 v1 -f2 v12'", ((result, args) -> {
                    if (args.length % 2 != 0)
                        throw new ParseArgumentException("even number of entries");
                    for (int i = 0; i < args.length; i += 2) {
                        result.addEntry(Konsole.check.checkRead(args[0]), Konsole.check.checkRead(args[i + 1]));
                    }
                }));
        konsole.handle(arguments);
        ServerConfig config = konsole.getResult();
        File workingDir = DEFAULT_WORKING_DIR;
        if (config.getWorkingDirectory() != null) {
            Path path = Paths.get(config.getWorkingDirectory());
            workingDir = path.toFile();
        }
        Lok.debug("dir: " + workingDir.getCanonicalPath());
        MiniServer miniServer = new MiniServer(workingDir);
        miniServer.start();
        while (true) {

        }
    }

    private static Pair<Long> readFiles(File dataFile, File infoFile) throws IOException {
        try {
            if (dataFile.exists() && infoFile.exists()) {
                String hash = Hash.sha256(new FileInputStream(dataFile));
                byte[] bytes = Files.readAllBytes(Paths.get(infoFile.toURI()));
                String s = new String(bytes);
                s = s.replaceAll("\n", "");
                Long version = Long.parseLong(s);
                return new Pair<>(Long.class, hash, version);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void execute(MeinRunnable runnable) {
        //noinspection Duplicates
        try {
            if (executorService == null || (executorService != null && (executorService.isShutdown() || executorService.isTerminated())))
                executorService = Executors.newCachedThreadPool(threadFactory);
            threadSemaphore.acquire();
            threadQueue.add(new MeinThread(runnable));
            threadSemaphore.release();
            executorService.execute(runnable);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        // starting sockets
        EncSocketOpener encSocketOpener = new EncSocketOpener(certificateManager, DEFAULT_ENCRYPTED_PORT, this, versionAnswer);
        execute(encSocketOpener);
        BinarySocketOpener binarySocketOpener = new BinarySocketOpener(DEFAULT_BINARY_PORT, this, fileRepository);
        execute(binarySocketOpener);
        Lok.debug("I am up!");
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
