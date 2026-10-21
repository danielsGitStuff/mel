package de.mel.auth.service;

import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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

    public final TwoInstances two;
    public final File serverRoot;
    public final File clientRoot;
    public MelFileSyncServerService serverService;
    public MelFileSyncClientService clientService;

    /**
     * Boots and pairs the two. Nothing is synced yet, see {@link #createServer()} and {@link #createClient()}.
     *
     * @param portBase takes 10 ports from here on
     */
    public FileSyncInstances(File dir, int portBase) throws Exception {
        two = new TwoInstances(dir, portBase, TwoInstances.ACCEPT_ALL, TwoInstances.ACCEPT_ALL, FileSyncBootloader.class);
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
        AtomicReference<MelFileSyncServerService> booted = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = service -> {
            booted.set((MelFileSyncServerService) service);
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
