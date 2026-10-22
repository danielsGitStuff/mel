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

import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelProcess;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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
                .onDone(p -> {
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
        assertTrue(waitFor(done::get, 20), "file of " + content.length + " bytes did not arrive");
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

    /**
     * One file on its way: queued at the sender, expected at the receiver.
     */
    private class Transfer {
        final int streamId;
        final byte[] content;
        final File target;
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicBoolean failed = new AtomicBoolean();
        final AtomicInteger doneOrder = new AtomicInteger(-1);

        Transfer(int streamId, byte[] content, File target) {
            this.streamId = streamId;
            this.content = content;
            this.target = target;
        }

        boolean over() {
            return done.get() || failed.get();
        }
    }

    private final AtomicInteger completions = new AtomicInteger();
    private int fileCounter = 0;

    /**
     * Puts the file in the queue of the sender and prepares the receiver for it. Does not wait.
     */
    private Transfer queue(MelIsolatedFileProcess sender, MelIsolatedFileProcess receiver, int streamId, byte[] content) throws Exception {
        int number = fileCounter++;
        File source = new File(tempDir, "queued-source-" + number);
        Transfer transfer = new Transfer(streamId, content, new File(tempDir, "queued-target-" + number));
        Files.write(source.toPath(), content);
        FileTransferDetail receiving = new FileTransferDetail(AbstractFile.instance(transfer.target), streamId, 0, content.length).setHash("hash" + number)
                .setTransferDoneListener(d -> {
                    transfer.doneOrder.set(completions.getAndIncrement());
                    transfer.done.set(true);
                })
                .setTransferFailedListener(d -> transfer.failed.set(true));
        receiver.addFilesReceiving(receiving);
        FileTransferDetail sending = new FileTransferDetail(AbstractFile.instance(source), streamId, 0, content.length).setHash("hash" + number);
        sending.openRead();
        sender.sendFile(sending);
        return transfer;
    }

    private static void assertAllArrived(List<Transfer> transfers) throws Exception {
        assertTrue(waitFor(() -> transfers.stream().allMatch(Transfer::over), 20),
                transfers.stream().filter(t -> !t.over()).count() + " of " + transfers.size() + " files did not arrive");
        for (Transfer transfer : transfers) {
            assertFalse(transfer.failed.get(), "stream " + transfer.streamId + " failed");
            assertArrayEquals(transfer.content, Files.readAllBytes(transfer.target.toPath()),
                    "stream " + transfer.streamId + " (" + transfer.content.length + " bytes) arrived with the content of another file or changed");
        }
    }

    /**
     * Files of the same size and different content, so a piece that lands in the wrong file shows.
     */
    private List<Transfer> queueMany(MelIsolatedFileProcess sender, MelIsolatedFileProcess receiver, int firstStreamId, long seed, int sameSize, int largeFiles) throws Exception {
        List<Transfer> transfers = new ArrayList<>();
        int streamId = firstStreamId;
        // many of the same size
        for (int i = 0; i < sameSize; i++)
            transfers.add(queue(sender, receiver, streamId++, randomBytes(150_000, seed + streamId)));
        // some of the size of one record minus the header, plus one, plus minus one
        for (int size : new int[]{1024 * 1024 - 17, 1024 * 1024 - 16, 1024 * 1024 - 18, 1, 2, 17, 0, 0})
            transfers.add(queue(sender, receiver, streamId++, randomBytes(size, seed + streamId)));
        // and a few larger ones, each takes several records
        for (int i = 0; i < largeFiles; i++)
            transfers.add(queue(sender, receiver, streamId++, randomBytes(2_300_000 + i, seed + streamId)));
        return transfers;
    }

    @Test
    public void manyFilesAtOnceInBothDirectionsDoNotMixUp() throws Exception {
        List<Transfer> transfers = new ArrayList<>();
        // everything is queued before anything is waited for, the sides send and receive at the same time
        transfers.addAll(queueMany(clientSide, serverSide, 100, 1000, 25, 3));
        transfers.addAll(queueMany(serverSide, clientSide, 100, 5000, 25, 3));
        assertAllArrived(transfers);
        // the files were different
        Set<String> contents = new HashSet<>();
        for (Transfer transfer : transfers)
            if (transfer.content.length > 100)
                assertTrue(contents.add(Arrays.toString(Arrays.copyOf(transfer.content, 32))), "the test files should differ");
    }

    @Test
    public void severalConnectionsAtOnceDoNotMixUp() throws Exception {
        List<MelIsolatedFileProcess> senders = new ArrayList<>();
        List<MelIsolatedFileProcess> receivers = new ArrayList<>();
        senders.add(clientSide);
        receivers.add(serverSide);
        for (int i = 0; i < 3; i++) {
            CountDownLatch opened = new CountDownLatch(1);
            AtomicReference<MelIsolatedProcess> process = new AtomicReference<>();
            client.connectToService(MelIsolatedFileProcess.class, idOf(server, client), serverService.getUuid(), clientService.getUuid(), null, null, null)
                    .onDone(p -> {
                        process.set(p);
                        opened.countDown();
                    });
            assertTrue(opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            MelIsolatedFileProcess mine = (MelIsolatedFileProcess) process.get();
            assertTrue(waitFor(() -> serverService.established.stream().anyMatch(p -> p.getIsolatedUuid().equals(mine.getIsolatedUuid())), 10));
            senders.add(mine);
            receivers.add((MelIsolatedFileProcess) serverService.established.stream().filter(p -> p.getIsolatedUuid().equals(mine.getIsolatedUuid())).findFirst().get());
        }
        // the same stream ids on every connection: they only mean something within one
        List<Transfer> transfers = new ArrayList<>();
        for (int i = 0; i < senders.size(); i++) {
            transfers.addAll(queueMany(senders.get(i), receivers.get(i), 1, 100L * i, 10, 1));
            transfers.addAll(queueMany(receivers.get(i), senders.get(i), 1, 100_000L + 100L * i, 10, 1));
        }
        assertAllArrived(transfers);
    }

    @Test
    public void aSmallFileDoesNotWaitForAHugeOneThatWasQueuedBefore() throws Exception {
        Transfer huge = queue(clientSide, serverSide, 1, randomBytes(20 * 1024 * 1024, 1));
        Transfer small = queue(clientSide, serverSide, 2, randomBytes(10, 2));
        assertAllArrived(Arrays.asList(huge, small));
        assertTrue(small.doneOrder.get() < huge.doneOrder.get(), "the small file should be done first, the pieces of the files take turns");
    }

    /**
     * How a download continues: the receiver has the beginning, it asks for the rest only.
     */
    @Test
    public void aTransferStartsWhereTheReceiverAsksItTo() throws Exception {
        byte[] content = randomBytes(5 * 1024 * 1024 + 321, 77);
        int start = 3 * 1024 * 1024 + 5;
        File source = new File(tempDir, "resume-source");
        File target = new File(tempDir, "resume-target");
        Files.write(source.toPath(), content);
        // what is there: the beginning, and some garbage that was written but not noted down
        byte[] present = Arrays.copyOf(content, start + 1000);
        Arrays.fill(present, start, present.length, (byte) 0x55);
        Files.write(target.toPath(), present);
        AtomicBoolean done = new AtomicBoolean();
        FileTransferDetail receiving = new FileTransferDetail(AbstractFile.instance(target), 9, start, content.length).setHash("resume")
                .setTransferDoneListener(d -> done.set(true));
        receiving.openWrite();
        serverSide.addFilesReceiving(receiving);
        FileTransferDetail sending = new FileTransferDetail(AbstractFile.instance(source), 9, start, content.length).setHash("resume");
        sending.openRead();
        long before = clientSide.getSentFileBytes();
        clientSide.sendFile(sending);
        assertTrue(waitFor(done::get, 20));
        assertArrayEquals(content, Files.readAllBytes(target.toPath()), "the file is not what was sent: the beginning was lost or the garbage stayed");
        assertEquals(content.length - start, clientSide.getSentFileBytes() - before, "only the rest should be sent");
    }

    private static void raw(MelIsolatedFileProcess process, char type, int streamId, long offset, byte[] data) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(17 + data.length);
        buffer.put((byte) type).putInt(streamId).putLong(offset).putInt(data.length).put(data);
        Field field = MelProcess.class.getDeclaredField("melAuthSocket");
        field.setAccessible(true);
        ((MelAuthSocket) field.get(process)).sendBlock(buffer.array());
    }

    /**
     * Expects the file with that stream id on the receiver, as the receiver of a download would.
     */
    private Transfer expect(MelIsolatedFileProcess receiver, int streamId, int length) {
        Transfer transfer = new Transfer(streamId, null, new File(tempDir, "expected-" + streamId));
        FileTransferDetail receiving = new FileTransferDetail(AbstractFile.instance(transfer.target), streamId, 0, length).setHash("expected" + streamId)
                .setTransferDoneListener(d -> transfer.done.set(true))
                .setTransferFailedListener(d -> transfer.failed.set(true));
        receiver.addFilesReceiving(receiving);
        return transfer;
    }

    /**
     * A transfer that goes wrong is reported and stops, the others on the connection go on.
     */
    private void assertFailsAndOthersGoOn(Transfer failing, MelIsolatedFileProcess sender, MelIsolatedFileProcess receiver) throws Exception {
        assertTrue(waitFor(failing::over, TwoInstances.TIMEOUT_SECONDS), "the receiver was not told that the transfer failed");
        assertTrue(failing.failed.get());
        assertFalse(failing.done.get());
        // the connection and the other transfers are fine
        assertTrue(sender.isOpen() && receiver.isOpen());
        transfer(sender, receiver, 1000, randomBytes(300_000, 1000));
    }

    @Test
    public void aPieceThatLeavesAGapFailsTheTransfer() throws Exception {
        Transfer expected = expect(serverSide, 21, 100);
        raw(clientSide, 'T', 21, 0, new byte[10]);
        raw(clientSide, 'T', 21, 20, new byte[10]);
        assertFailsAndOthersGoOn(expected, clientSide, serverSide);
    }

    @Test
    public void aPieceThatComesTwiceFailsTheTransfer() throws Exception {
        Transfer expected = expect(serverSide, 22, 100);
        raw(clientSide, 'T', 22, 0, new byte[10]);
        raw(clientSide, 'T', 22, 0, new byte[10]);
        assertFailsAndOthersGoOn(expected, clientSide, serverSide);
    }

    @Test
    public void aPieceBeyondTheEndFailsTheTransfer() throws Exception {
        Transfer expected = expect(serverSide, 23, 100);
        raw(clientSide, 'T', 23, 0, new byte[101]);
        assertFailsAndOthersGoOn(expected, clientSide, serverSide);
    }

    @Test
    public void aLastPieceBeforeTheEndFailsTheTransfer() throws Exception {
        Transfer expected = expect(serverSide, 24, 100);
        raw(clientSide, 't', 24, 0, new byte[99]);
        assertFailsAndOthersGoOn(expected, clientSide, serverSide);
    }

    @Test
    public void aRecordThatSaysItIsLongerThanItIsFailsTheTransfer() throws Exception {
        Transfer expected = expect(serverSide, 25, 100);
        ByteBuffer buffer = ByteBuffer.allocate(17 + 5);
        buffer.put((byte) 'T').putInt(25).putLong(0).putInt(50).put(new byte[5]);
        Field field = MelProcess.class.getDeclaredField("melAuthSocket");
        field.setAccessible(true);
        ((MelAuthSocket) field.get(clientSide)).sendBlock(buffer.array());
        assertFailsAndOthersGoOn(expected, clientSide, serverSide);
    }

    @Test
    public void aSenderThatSaysItCannotSendTheFileFailsTheTransfer() throws Exception {
        Transfer expected = expect(serverSide, 26, 100);
        clientSide.sendError(new FileTransferDetail(AbstractFile.instance(new File(tempDir, "nothing")), 26, 0, 100).setHash("expected26"));
        assertFailsAndOthersGoOn(expected, clientSide, serverSide);
    }

    @Test
    public void aSenderWhoseFileIsShorterThanAskedForFailsTheTransfer() throws Exception {
        Transfer expected = expect(serverSide, 27, 100);
        File source = new File(tempDir, "short-source");
        Files.write(source.toPath(), randomBytes(70, 5));
        FileTransferDetail sending = new FileTransferDetail(AbstractFile.instance(source), 27, 0, 100).setHash("expected27");
        sending.openRead();
        clientSide.sendFile(sending);
        assertFailsAndOthersGoOn(expected, clientSide, serverSide);
    }

    @Test
    public void aFailedTransferLetsGoOfItsFileAndKeepsWhatArrived() throws Exception {
        Transfer expected = expect(serverSide, 28, 100);
        byte[] first = randomBytes(40, 6);
        raw(clientSide, 'T', 28, 0, first);
        raw(clientSide, 'T', 28, 50, new byte[10]);
        assertTrue(waitFor(expected::over, TwoInstances.TIMEOUT_SECONDS));
        assertTrue(expected.failed.get());
        // a download that is continued needs what came in order
        assertArrayEquals(first, Files.readAllBytes(expected.target.toPath()));
        // and the file can be opened again
        assertNotNull(AbstractFile.instance(expected.target).writer(40));
    }
}
