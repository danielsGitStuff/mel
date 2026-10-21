package de.mel.auth.service;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.socket.process.transfer.FileTransferDetail;
import de.mel.auth.socket.process.transfer.MelIsolatedFileProcess;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static de.mel.auth.service.TwoInstances.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Files travel over isolated connections as records in block frames. Nothing else tests this in the build.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class FileTransferTest {
    @TempDir
    File tempDir;

    private TwoInstances instances;
    private MelAuthService server;
    private MelAuthService client;
    private IsolationRecordingService serverService;
    private IsolationRecordingService clientService;
    private MelIsolatedFileProcess clientSide;
    private MelIsolatedFileProcess serverSide;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 31000, ACCEPT_ALL, ACCEPT_ALL);
        server = instances.server;
        client = instances.client;
        instances.pair();
        serverService = new IsolationRecordingService(server, new File(tempDir, "server service"), "server service uuid");
        clientService = new IsolationRecordingService(client, new File(tempDir, "client service"), "client service uuid");
        server.registerMelService(serverService);
        client.registerMelService(clientService);
        grant(server, serverService, client);
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<MelIsolatedProcess> process = new AtomicReference<>();
        client.connectToService(MelIsolatedFileProcess.class, idOf(server, client), serverService.getUuid(), clientService.getUuid(), null, null, null)
                .done(p -> {
                    process.set(p);
                    opened.countDown();
                });
        assertTrue(opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        clientSide = (MelIsolatedFileProcess) process.get();
        assertTrue(serverService.firstEstablished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        serverSide = (MelIsolatedFileProcess) serverService.established.get(0);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    private static byte[] randomBytes(int length, long seed) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    /**
     * the sender sends the file, the receiver has to end up with the same bytes
     */
    private void transfer(MelIsolatedFileProcess sender, MelIsolatedFileProcess receiver, int streamId, byte[] content) throws Exception {
        File source = new File(tempDir, "source" + streamId);
        File target = new File(tempDir, "target" + streamId);
        Files.write(source.toPath(), content);
        IFile sourceFile = AbstractFile.instance(source);
        IFile targetFile = AbstractFile.instance(target);
        AtomicBoolean done = new AtomicBoolean();
        FileTransferDetail receiving = new FileTransferDetail(targetFile, streamId, 0, content.length).setHash("hash" + streamId)
                .setTransferDoneListener(d -> done.set(true));
        receiver.addFilesReceiving(receiving);
        FileTransferDetail sending = new FileTransferDetail(sourceFile, streamId, 0, content.length).setHash("hash" + streamId);
        sending.openRead();
        sender.sendFile(sending);
        assertTrue(waitFor(done::get, 60), "file of " + content.length + " bytes did not arrive");
        assertArrayEquals(content, Files.readAllBytes(target.toPath()), "file of " + content.length + " bytes arrived changed");
    }

    @Test
    public void aSmallFileArrives() throws Exception {
        transfer(clientSide, serverSide, 1, randomBytes(1000, 1));
    }

    @Test
    public void anEmptyFileArrives() throws Exception {
        transfer(clientSide, serverSide, 5, new byte[0]);
    }

    @Test
    public void aFileOfExactlyOneRecordArrives() throws Exception {
        transfer(clientSide, serverSide, 2, randomBytes(1024 * 1024 - 17, 2));
    }

    @Test
    public void aFileOfSeveralRecordsArrivesInBothDirections() throws Exception {
        transfer(clientSide, serverSide, 3, randomBytes(5 * 1024 * 1024 + 123, 3));
        transfer(serverSide, clientSide, 4, randomBytes(2 * 1024 * 1024 + 1, 4));
    }

    @Test
    public void severalFilesInARowArrive() throws Exception {
        for (int i = 0; i < 5; i++)
            transfer(clientSide, serverSide, 10 + i, randomBytes(300_000 + i * 700_000, 10 + i));
    }
}
