package de.miniserver;

import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.MeinThread;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.tools.N;
import de.mein.execute.SqliteExecutor;
import de.mein.konsole.Konsole;
import de.mein.sql.*;
import de.mein.sql.conn.SQLConnector;
import de.mein.sql.transform.SqlResultTransformer;
import de.mein.update.VersionAnswer;
import de.miniserver.data.FileRepository;
import de.miniserver.socket.BinarySocketOpener;
import de.miniserver.socket.EncSocketOpener;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.*;

public class MiniServer {
    public static final File DEFAULT_WORKING_DIR = new File("miniserver.w");
    public static final String DIR_FILES_NAME = "files";
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
    private EncSocketOpener encSocketOpener;
    private BinarySocketOpener binarySocketOpener;

    public X509Certificate getCertificate() {
        return certificateManager.getMyX509Certificate();
    }

    public MiniServer(ServerConfig config) throws Exception {
        File workingDir = new File(config.getWorkingDirectory());
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
            InputStream resourceStream = DatabaseManager.class.getResourceAsStream("/de/mein/auth/sql.sql");
            sqliteExecutor.executeStream(resourceStream);
        }
        certificateManager = new CertificateManager(workingDir, sqlQueries, 2048);

        // loading and hashing files
        File filesDir = new File(workingDir, DIR_FILES_NAME);
        filesDir.mkdir();
        versionAnswer = new VersionAnswer();
        fileRepository = new FileRepository();

        //looking for jar, apk and their appropriate version.txt
        for (File f : filesDir.listFiles(f -> f.isFile() && (f.getName().endsWith(".jar") || f.getName().endsWith(".apk")))) {
            String hash, version, variant;
            File propertiesFile;

            propertiesFile = new File(filesDir, f.getName() + MeinStrings.update.INFO_APPENDIX);
            Lok.debug("reading binary: " + f.getAbsolutePath());
            Lok.debug("reading  props: " + propertiesFile.getAbsolutePath());
            hash = Hash.sha256(new FileInputStream(f));
            Properties properties = new Properties();
            properties.load(new FileInputStream(propertiesFile));

            variant = properties.getProperty("variant");
            version = properties.getProperty("builddate");
            fileRepository.addEntry(hash, f);
            versionAnswer.addEntry(hash, variant, version);
        }

    }


    public static void main(String[] arguments) {
        Lok.debug("starting in: " + new File("").getAbsolutePath());
        Konsole<ServerConfig> konsole = new Konsole(new ServerConfig());
        konsole.optional("-create-cert", "name of the certificate", (result, args) -> result.setCertName(args[0]), Konsole.dependsOn("-pubkey", "-privkey"))
                .optional("-cert", "path to certificate", (result, args) -> result.setCertPath(Konsole.check.checkRead(args[0])), Konsole.dependsOn("-pubkey", "-privkey"))
                .optional("-pubkey", "path to public key", (result, args) -> result.setPubKeyPath(Konsole.check.checkRead(args[0])), Konsole.dependsOn("-privkey", "-cert"))
                .optional("-privkey", "path to private key", (result, args) -> result.setPrivKeyPath(Konsole.check.checkRead(args[0])), Konsole.dependsOn("-pubkey", "-cert"))
                .optional("-dir", "path to working directory", (result, args) -> result.setWorkingDirectory(args[0]));
//                .optional("-files", "tuples of files, versions and names. eg: '-files -f1 v1 n1 -f2 v12 n2'", ((result, args) -> {
//                    if (args.length % 2 != 0)
//                        throw new Konsole.ParseArgumentException("even number of entries");
//                    for (int i = 0; i < args.length; i += 2) {
//                        result.addEntry(Konsole.check.checkRead(args[0]), args[i+2],Konsole.check.checkRead(args[i + 1]));
//                    }
//                }));
        try {
            konsole.handle(arguments);
        } catch (Konsole.KonsoleWrongArgumentsException | Konsole.DependenciesViolatedException e) {
            Lok.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        } catch (Konsole.HelpException e) {
            System.exit(0);
        }
        ServerConfig config = konsole.getResult();
        File workingDir = DEFAULT_WORKING_DIR;
        if (config.getWorkingDirectory() != null) {
            Path path = Paths.get(config.getWorkingDirectory());
            workingDir = path.toFile();
        }
        config.setWorkingDirectory(workingDir.getAbsolutePath());
        Lok.debug("dir: " + workingDir.getAbsolutePath());
        MiniServer miniServer = null;
        try {
            miniServer = new MiniServer(config);
            miniServer.start();
            new RWLock().lockWrite().lockWrite();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        encSocketOpener = new EncSocketOpener(certificateManager, MeinAuthSettings.UPDATE_MSG_PORT, this, versionAnswer);
        execute(encSocketOpener);
        binarySocketOpener = new BinarySocketOpener(MeinAuthSettings.UPDATE_BINARY_PORT, this, fileRepository);
        execute(binarySocketOpener);
        Lok.debug("I am up!");
    }

    public void shutdown() {
        executorService.shutdown();
        N.r(() -> binarySocketOpener.onShutDown());
        N.r(() -> encSocketOpener.onShutDown());
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
            Lok.debug("is down: " + executorService.isShutdown());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
