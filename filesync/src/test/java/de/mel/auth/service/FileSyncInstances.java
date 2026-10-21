package de.mel.auth.service;

import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.socket.process.transfer.FileTransferDetail;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.filesync.service.MelFileSyncService;
import de.mel.filesync.sql.DbTransferDetails;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.TransferState;
import de.mel.filesync.transfer.FileTransferDetailsPayload;
import de.mel.filesync.transfer.TManager;

import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two Mel instances on loopback, one runs a file sync server service, the other a client service. For the transfer tests: the server has files, the client gets them.
 * Builds on {@link TwoInstances}, so the two only talk to each other when the test says so.
 */
public class FileSyncInstances implements AutoCloseable {
    static final int TIMEOUT_SECONDS = TwoInstances.TIMEOUT_SECONDS;

    /**
     * What a server service was asked for.
     */
    public record Request(String hash, long start, long end) {
    }

    /**
     * A server service that writes down what it is asked for, and can send a damaged file instead of the real one.
     */
    public static class RecordingServerService extends MelFileSyncServerService {
        public final List<Request> requests = new CopyOnWriteArrayList<>();
        /**
         * hashes of files that are sent with one bit flipped
         */
        public final Set<String> damaged = ConcurrentHashMap.newKeySet();

        public RecordingServerService(de.mel.auth.service.MelAuthService melAuthService, File workingDirectory, Long serviceTypeId, String uuid, FileSyncSettings fileSyncSettings, FileSyncDatabaseManager databaseManager) {
            super(melAuthService, workingDirectory, serviceTypeId, uuid, fileSyncSettings, databaseManager);
        }

        /**
         * requests are not answered, as if the server did not hear them
         */
        public volatile boolean ignoreRequests = false;
        /**
         * requests are answered but no file is sent, as if the server was stuck
         */
        public volatile boolean sendNothing = false;

        @Override
        protected Promise<Void, Exception> handleSending(Long partnerCertId, FileTransferDetailsPayload payload) {
            for (FileTransferDetail detail : payload.getFileTransferDetailSet().getDetails())
                requests.add(new Request(detail.getHash(), detail.getStart(), detail.getEnd()));
            if (ignoreRequests)
                return PromiseImpl.withoutThrowingOnException();
            if (sendNothing)
                return Promise.resolvedOk();
            return super.handleSending(partnerCertId, payload);
        }

        @Override
        protected IFile findFileByHash(String hash) throws Exception {
            IFile file = super.findFileByHash(hash);
            if (file == null || !damaged.contains(hash))
                return file;
            byte[] bytes = Files.readAllBytes(new File(file.getAbsolutePath()).toPath());
            bytes[bytes.length / 2] ^= 0x01;
            File copy = File.createTempFile("damaged", ".bin");
            copy.deleteOnExit();
            Files.write(copy.toPath(), bytes);
            return AbstractFile.instance(copy);
        }

        /**
         * @return what was asked for of the file with that hash, in the order it was asked
         */
        public List<Request> requestsFor(String hash) {
            return requests.stream().filter(r -> r.hash().equals(hash)).toList();
        }
    }

    public static class RecordingFileSyncBootloader extends FileSyncBootloader {
        @Override
        protected MelFileSyncService createInstance(FileSyncSettings fileSyncSettings, File workingDirectory, Long serviceTypeId, String uuid, FileSyncDatabaseManager databaseManager) {
            if (fileSyncSettings.isServer())
                return new RecordingServerService(melAuthService, workingDirectory, serviceTypeId, uuid, fileSyncSettings, databaseManager);
            return super.createInstance(fileSyncSettings, workingDirectory, serviceTypeId, uuid, databaseManager);
        }
    }

    public final TwoInstances two;
    public final File serverRoot;
    public final File clientRoot;
    public RecordingServerService serverService;
    public MelFileSyncClientService clientService;

    /**
     * Boots and pairs the two. Nothing is synced yet, see {@link #createServer()} and {@link #createClient()}.
     *
     * @param portBase takes 10 ports from here on
     */
    public FileSyncInstances(File dir, int portBase) throws Exception {
        two = new TwoInstances(dir, portBase, TwoInstances.ACCEPT_ALL, TwoInstances.ACCEPT_ALL, RecordingFileSyncBootloader.class);
        serverRoot = new File(dir, "serverRoot");
        clientRoot = new File(dir, "clientRoot");
        serverRoot.mkdirs();
        clientRoot.mkdirs();
        assertTrue(serverRoot.isDirectory() && clientRoot.isDirectory());
        two.pair();
    }

    /**
     * Writes a file with content that depends only on the seed and size, so a test can tell which file it is looking at.
     * Parent directories are created.
     *
     * @return the sha-256 of the content (hex)
     */
    public static String writeFile(File root, String relativePath, long size, long seed) throws Exception {
        File file = new File(root, relativePath);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        byte[] content = content(size, seed);
        Files.write(file.toPath(), content);
        return sha256(content);
    }

    public static byte[] content(long size, long seed) {
        byte[] bytes = new byte[(int) size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    public static String sha256(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            hex.append(String.format("%02x", b));
        return hex.toString();
    }

    /**
     * Starts the server service on {@link #serverRoot}. Whatever is in there is indexed and offered to clients.
     * The client is allowed to use it.
     */
    public void createServer() throws Exception {
        AtomicReference<RecordingServerService> booted = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = service -> {
            booted.set((RecordingServerService) service);
            latch.countDown();
        };
        IFile root = AbstractFile.instance(serverRoot);
        new FileSyncCreateServiceHelper(two.server).createServerService("server", root, .5f, 666, false);
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the server service did not boot");
        serverService = booted.get();
        Long clientAtServer = TwoInstances.idOf(two.client, two.server);
        for (ServiceJoinServiceType service : two.server.getDatabaseManager().getAllServices())
            if (service.getUuid().equalsValue(serverService.getUuid()))
                two.server.getDatabaseManager().grant(service.getServiceId().v(), clientAtServer);
    }

    /**
     * Starts the client service on {@link #clientRoot}. It registers at the server service, indexes and starts syncing by itself.
     */
    public void createClient() throws Exception {
        assertNotNull(serverService, "no server");
        AtomicReference<MelFileSyncClientService> booted = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = service -> {
            booted.set((MelFileSyncClientService) service);
            latch.countDown();
        };
        IFile root = AbstractFile.instance(clientRoot);
        Long serverAtClient = TwoInstances.idOf(two.server, two.client);
        new FileSyncCreateServiceHelper(two.client).createClientService("client", root, serverAtClient, serverService.getUuid(), .5f, 666, false);
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the client service did not boot");
        clientService = booted.get();
    }

    /**
     * Shuts the client down and starts it again, like an app that is closed or dies and is started again. The pairing, the service, its database and the files stay.
     */
    public void restartClient() throws Exception {
        restartClient(() -> {
        });
    }

    /**
     * @param whileDown is run when the client is shut down and not started again yet: the files and databases can be touched
     */
    public void restartClient(Runnable whileDown) throws Exception {
        AtomicReference<MelFileSyncClientService> booted = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = service -> {
            booted.set((MelFileSyncClientService) service);
            latch.countDown();
        };
        two.restartClient(whileDown);
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the client service did not boot again");
        clientService = booted.get();
    }

    public TManager clientTransferManager() {
        return clientService.getSyncHandler().getTransferManager();
    }

    public Long serverIdAtClient() throws Exception {
        return TwoInstances.idOf(two.server, two.client);
    }

    /**
     * Waits until the transfer of that file is in that state on the client
     */
    public void waitForClientTransferState(String hash, TransferState state) throws Exception {
        assertTrue(TwoInstances.waitFor(() -> {
            try {
                DbTransferDetails transfer = clientTransferOf(hash);
                return transfer != null && transfer.getState().equalsValue(state);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, TIMEOUT_SECONDS), "the transfer of " + hash + " did not get to " + state + ", it is " + (clientTransferOf(hash) == null ? "gone" : clientTransferOf(hash).getState().v()));
    }

    public File clientTransferDir() {
        return new File(clientRoot, FileSyncStrings.TRANSFER_DIR);
    }

    /**
     * @return the transfers the client knows about, what state they are in and how far they are
     */
    public List<DbTransferDetails> clientTransfers() throws Exception {
        return clientService.getFileSyncDatabaseManager().getTransferDao().getAll();
    }

    public DbTransferDetails clientTransferOf(String hash) throws Exception {
        return clientTransfers().stream().filter(t -> hash.equals(t.getHash().v())).findFirst().orElse(null);
    }

    /**
     * @return the isolated connection of the server service that sends files, or null
     */
    public MelIsolatedFileProcess serverFileProcess() {
        for (MelIsolatedProcess process : serverService.getEstablishedIsolatedProcesses())
            if (process instanceof MelIsolatedFileProcess && process.isOpen())
                return (MelIsolatedFileProcess) process;
        return null;
    }

    /**
     * Waits until the server has sent that many bytes of file content over one connection, and ends the connection right there.
     *
     * @return what the server had sent
     */
    public long cutConnectionAfter(long bytes) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < end) {
            MelIsolatedFileProcess process = serverFileProcess();
            if (process != null && process.getSentFileBytes() >= bytes) {
                process.stop();
                return process.getSentFileBytes();
            }
            Thread.sleep(1);
        }
        fail("the server did not send " + bytes + " bytes");
        return 0;
    }

    /**
     * @return the hash a file has for the sync (md5 of the content)
     */
    public static String md5Of(File file) throws Exception {
        try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
            return de.mel.sql.Hash.md5(in);
        }
    }

    /**
     * @return what is in the files below the root, by relative path
     */
    public static Map<String, String> hashesBelow(File root) throws Exception {
        Map<String, String> result = new HashMap<>();
        collect(root, root, result);
        return result;
    }

    private static void collect(File root, File dir, Map<String, String> result) throws Exception {
        File[] children = dir.listFiles();
        if (children == null)
            return;
        for (File child : children) {
            if (child.isDirectory()) {
                // the transfer directory and the like are the sync's own business
                if (child.getName().startsWith("."))
                    continue;
                collect(root, child, result);
            } else {
                String relative = root.toPath().relativize(child.toPath()).toString();
                result.put(relative, sha256(Files.readAllBytes(child.toPath())));
            }
        }
    }

    @Override
    public void close() {
        two.close();
    }
}
