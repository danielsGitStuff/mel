package de.mel.auth.socket.process.transfer;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.AbstractFileWriter;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The receiving end of one file: pieces in, a file out. And the sending end, which has to start where a download is continued.
 */
public class FileTransferDetailTest {
    @TempDir
    File dir;

    @BeforeEach
    public void configure() {
        AbstractFile.configure(new DefaultFileConfiguration());
    }

    private static byte[] bytes(int length, long seed) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private IFile file(String name) {
        return AbstractFile.instance(new File(dir, name));
    }

    private FileTransferDetail receiving(String name, long start, long end, AtomicInteger done, AtomicInteger failed) {
        return new FileTransferDetail(file(name), 1, start, end)
                .setTransferDoneListener(d -> done.incrementAndGet())
                .setTransferFailedListener(d -> failed.incrementAndGet());
    }

    @Test
    public void piecesInOrderMakeTheFile() throws Exception {
        byte[] content = bytes(1000, 1);
        AtomicInteger done = new AtomicInteger();
        FileTransferDetail detail = receiving("a", 0, 1000, done, new AtomicInteger());
        detail.onReceived(0, Arrays.copyOfRange(content, 0, 300));
        assertEquals(300, detail.getPosition());
        assertEquals(0, done.get());
        detail.onReceived(300, Arrays.copyOfRange(content, 300, 301));
        detail.onReceived(301, Arrays.copyOfRange(content, 301, 1000));
        assertEquals(1, done.get());
        assertTrue(detail.transferred());
        assertArrayEquals(content, Files.readAllBytes(new File(dir, "a").toPath()));
    }

    @Test
    public void aPieceThatDoesNotFollowTheLastOneIsRefused() throws Exception {
        FileTransferDetail detail = receiving("gap", 0, 1000, new AtomicInteger(), new AtomicInteger());
        detail.onReceived(0, new byte[100]);
        assertThrows(IOException.class, () -> detail.onReceived(200, new byte[100]), "a gap");
        assertThrows(IOException.class, () -> detail.onReceived(0, new byte[100]), "the same again");
        assertThrows(IOException.class, () -> detail.onReceived(50, new byte[100]), "overlapping");
        assertEquals(100, detail.getPosition(), "refused pieces change nothing");
        assertEquals(100, new File(dir, "gap").length());
    }

    @Test
    public void aPieceBeyondTheEndIsRefused() throws Exception {
        FileTransferDetail detail = receiving("long", 0, 100, new AtomicInteger(), new AtomicInteger());
        detail.onReceived(0, new byte[60]);
        assertThrows(IOException.class, () -> detail.onReceived(60, new byte[41]));
        assertEquals(60, detail.getPosition());
        assertFalse(detail.transferred());
    }

    @Test
    public void nothingIsAcceptedAfterTheFileIsComplete() throws Exception {
        FileTransferDetail detail = receiving("complete", 0, 10, new AtomicInteger(), new AtomicInteger());
        detail.onReceived(0, new byte[10]);
        assertThrows(IOException.class, () -> detail.onReceived(10, new byte[1]));
    }

    @Test
    public void failureIsReportedOnce() throws Exception {
        AtomicInteger failed = new AtomicInteger();
        FileTransferDetail detail = receiving("failing", 0, 100, new AtomicInteger(), failed);
        detail.onReceived(0, new byte[10]);
        detail.onFailed();
        detail.onFailed();
        assertEquals(1, failed.get());
        // what came stays
        assertEquals(10, new File(dir, "failing").length());
    }

    @Test
    public void aDownloadContinuesWhereItWasAndReplacesWhatCameAfter() throws Exception {
        byte[] content = bytes(1000, 2);
        // 600 bytes are noted down as received. The file has more: the app died after writing and before noting down, and it is not even right
        byte[] onDisk = Arrays.copyOf(content, 800);
        Arrays.fill(onDisk, 600, 800, (byte) 0x55);
        Files.write(new File(dir, "resume").toPath(), onDisk);
        AtomicInteger done = new AtomicInteger();
        FileTransferDetail detail = receiving("resume", 600, 1000, done, new AtomicInteger());
        detail.openWrite();
        assertEquals(600, new File(dir, "resume").length(), "what is not known to be good is cut off");
        detail.onReceived(600, Arrays.copyOfRange(content, 600, 1000));
        assertEquals(1, done.get());
        assertArrayEquals(content, Files.readAllBytes(new File(dir, "resume").toPath()));
    }

    @Test
    public void aDownloadCannotContinueBehindTheEndOfTheFile() throws Exception {
        Files.write(new File(dir, "short").toPath(), new byte[100]);
        FileTransferDetail detail = receiving("short", 101, 1000, new AtomicInteger(), new AtomicInteger());
        assertThrows(IOException.class, detail::openWrite, "there would be a hole");
        FileTransferDetail missing = receiving("missing", 1, 1000, new AtomicInteger(), new AtomicInteger());
        assertThrows(IOException.class, missing::openWrite, "there is nothing to continue");
    }

    @Test
    public void aDownloadFromTheStartReplacesAnExistingFile() throws Exception {
        Files.write(new File(dir, "old").toPath(), bytes(500, 3));
        byte[] content = bytes(100, 4);
        FileTransferDetail detail = receiving("old", 0, 100, new AtomicInteger(), new AtomicInteger());
        detail.onReceived(0, content);
        assertArrayEquals(content, Files.readAllBytes(new File(dir, "old").toPath()), "no rest of the old file");
    }

    @Test
    public void theSenderStartsAtTheStartItWasGiven() throws Exception {
        byte[] content = bytes(5000, 5);
        Files.write(new File(dir, "source").toPath(), content);
        FileTransferDetail sending = new FileTransferDetail(file("source"), 1, 1234, 5000);
        sending.openRead();
        FileTransferDetail.FReadInfo first = sending.readFile(sending.getPosition(), 1000);
        assertArrayEquals(Arrays.copyOfRange(content, 1234, 2234), first.getBytes());
        assertEquals(2234, sending.getPosition());
        assertFalse(sending.transferred());
        FileTransferDetail.FReadInfo rest = sending.readFile(sending.getPosition(), 1_000_000);
        assertArrayEquals(Arrays.copyOfRange(content, 2234, 5000), rest.getBytes());
        assertEquals(0, rest.getNotFilledBytes());
        assertTrue(sending.transferred());
    }

    @Test
    public void theSenderNeverSendsMoreThanTheRangeItWasAskedFor() throws Exception {
        byte[] content = bytes(5000, 6);
        Files.write(new File(dir, "range").toPath(), content);
        FileTransferDetail sending = new FileTransferDetail(file("range"), 1, 100, 400);
        sending.openRead();
        FileTransferDetail.FReadInfo read = sending.readFile(100, 10_000);
        assertArrayEquals(Arrays.copyOfRange(content, 100, 400), read.getBytes());
        assertTrue(sending.transferred());
    }

    @Test
    public void aFileThatEndsBeforeTheRangeIsReportedAsMissingBytes() throws Exception {
        Files.write(new File(dir, "tooshort").toPath(), bytes(300, 7));
        FileTransferDetail sending = new FileTransferDetail(file("tooshort"), 1, 0, 1000);
        sending.openRead();
        FileTransferDetail.FReadInfo read = sending.readFile(0, 10_000);
        assertEquals(300, read.getBytes().length);
        assertEquals(700, read.getNotFilledBytes());
        assertFalse(sending.transferred());
    }

    @Test
    public void theWriterWritesExactlyTheBytesItIsToldTo() throws Exception {
        IFile file = file("writer");
        try (AbstractFileWriter writer = file.writer()) {
            // only the first 3 of the 5 bytes
            writer.append(new byte[]{1, 2, 3, 4, 5}, 0, 3);
            writer.append(new byte[]{9, 9, 9, 9, 9}, 3, 2);
        }
        assertArrayEquals(new byte[]{1, 2, 3, 9, 9}, Files.readAllBytes(new File(dir, "writer").toPath()));
    }

    @Test
    public void aWriterThatContinuesKeepsTheBeginning() throws Exception {
        Files.write(new File(dir, "continue").toPath(), new byte[]{1, 2, 3, 4, 5, 6});
        try (AbstractFileWriter writer = file("continue").writer(4)) {
            writer.append(new byte[]{7, 8}, 4);
        }
        assertArrayEquals(new byte[]{1, 2, 3, 4, 7, 8}, Files.readAllBytes(new File(dir, "continue").toPath()));
    }
}
