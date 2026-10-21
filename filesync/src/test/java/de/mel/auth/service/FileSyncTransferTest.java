package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import de.mel.filesync.sql.TransferState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client service gets the files of the server service. Two real instances on loopback, real sockets, real databases.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class FileSyncTransferTest {
    @TempDir
    File dir;

    private File serverRoot() {
        return new File(dir, "serverRoot");
    }

    private static void assertClientGets(FileSyncInstances instances, Map<String, String> expected, int seconds) throws Exception {
        assertTrue(TwoInstances.waitFor(() -> {
            try {
                return expected.equals(FileSyncInstances.hashesBelow(instances.clientRoot));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, seconds), "the client does not have what it should after " + seconds + "s. It has: " + FileSyncInstances.hashesBelow(instances.clientRoot).keySet()
                + ", missing: " + missing(expected, FileSyncInstances.hashesBelow(instances.clientRoot)));
        assertEquals(expected, FileSyncInstances.hashesBelow(instances.clientRoot));
    }

    private static String missing(Map<String, String> expected, Map<String, String> actual) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : expected.entrySet())
            if (!entry.getValue().equals(actual.get(entry.getKey())))
                result.append(entry.getKey()).append(actual.containsKey(entry.getKey()) ? "(wrong content) " : " ");
        return result.toString();
    }

    @Test
    public void clientGetsTheFilesOfTheServer() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("small.bin", FileSyncInstances.writeFile(serverRoot(), "small.bin", 1000, 1));
        expected.put("sub/medium.bin", FileSyncInstances.writeFile(serverRoot(), "sub/medium.bin", 300_000, 2));
        expected.put("sub/deeper/large.bin", FileSyncInstances.writeFile(serverRoot(), "sub/deeper/large.bin", 3_000_000, 3));
        expected.put("empty.bin", FileSyncInstances.writeFile(serverRoot(), "empty.bin", 0, 4));
        // the same content in two places is one transfer and two files
        expected.put("twin/one.bin", FileSyncInstances.writeFile(serverRoot(), "twin/one.bin", 50_000, 5));
        expected.put("twin/two.bin", FileSyncInstances.writeFile(serverRoot(), "twin/two.bin", 50_000, 5));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24100)) {
            instances.createServer();
            instances.createClient();
            assertClientGets(instances, expected, 60);
        }
    }

    /**
     * More files than are asked for at once, a lot of them the same size with different content, some in several records, some tiny, some empty, some twice.
     * A piece that lands in the wrong file, a file that is missing or one that is delivered twice shows.
     */
    @Test
    public void manyFilesAtOnceDoNotMixUp() throws Exception {
        Map<String, String> expected = new HashMap<>();
        for (int i = 0; i < 70; i++)
            expected.put("same size/f" + i + ".bin", FileSyncInstances.writeFile(serverRoot(), "same size/f" + i + ".bin", 20_000, 1000 + i));
        for (int i = 0; i < 40; i++)
            expected.put("tiny/t" + i + ".bin", FileSyncInstances.writeFile(serverRoot(), "tiny/t" + i + ".bin", 1 + i * 7, 2000 + i));
        for (int i = 0; i < 4; i++)
            expected.put("big/b" + i + ".bin", FileSyncInstances.writeFile(serverRoot(), "big/b" + i + ".bin", 1_300_000 + i, 3000 + i));
        for (int i = 0; i < 3; i++)
            expected.put("empty/e" + i + ".bin", FileSyncInstances.writeFile(serverRoot(), "empty/e" + i + ".bin", 0, 4000 + i));
        for (int i = 0; i < 5; i++)
            expected.put("copies/c" + i + ".bin", FileSyncInstances.writeFile(serverRoot(), "copies/c" + i + ".bin", 30_000, 5000));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24110)) {
            instances.createServer();
            instances.createClient();
            assertClientGets(instances, expected, 120);
        }
    }

    private static final int MIB = 1024 * 1024;

    /**
     * The server cuts the connection when about 6 MiB of a 24 MiB file are sent. The client has to notice, come back after a while
     * and get the rest of the file, not the whole file again.
     */
    @Test
    public void aBrokenConnectionIsRetriedAndTheDownloadContinues() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("big.bin", FileSyncInstances.writeFile(serverRoot(), "big.bin", 24 * MIB, 1));
        for (int i = 0; i < 5; i++)
            expected.put("small/s" + i + ".bin", FileSyncInstances.writeFile(serverRoot(), "small/s" + i + ".bin", 10_000 + i, 10 + i));
        String bigHash = FileSyncInstances.md5Of(new File(serverRoot(), "big.bin"));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24120)) {
            instances.createServer();
            instances.createClient();
            instances.clientTransferManager().setRetryBaseDelayMs(300);
            long sentBeforeTheCut = instances.cutConnectionAfter(6 * MIB);
            assertClientGets(instances, expected, 90);
            java.util.List<FileSyncInstances.Request> asked = instances.serverService.requestsFor(bigHash);
            assertTrue(asked.size() >= 2, "the file should have been asked for again: " + asked);
            assertEquals(0, asked.get(0).start());
            long second = asked.get(1).start();
            assertTrue(second > 0, "it should not start over: " + asked);
            assertTrue(second <= sentBeforeTheCut, "it cannot continue behind what was sent: " + asked + " after " + sentBeforeTheCut);
            System.out.println("RESUME: sent before the cut " + sentBeforeTheCut + ", continued at " + second + ", requests " + asked);
        }
    }

    /**
     * A 24 MiB file and some small ones on the server.
     */
    private static class Scenario {
        final Map<String, String> expected = new HashMap<>();
        String bigHash;
    }

    private Scenario scenario() throws Exception {
        Scenario scenario = new Scenario();
        scenario.expected.put("big.bin", FileSyncInstances.writeFile(serverRoot(), "big.bin", 24 * MIB, 1));
        for (int i = 0; i < 5; i++)
            scenario.expected.put("small/s" + i + ".bin", FileSyncInstances.writeFile(serverRoot(), "small/s" + i + ".bin", 10_000 + i, 10 + i));
        scenario.bigHash = FileSyncInstances.md5Of(new File(serverRoot(), "big.bin"));
        return scenario;
    }

    /**
     * Starts the sync, cuts the connection after about 6 MiB and waits until the client has noted down that it is suspended.
     * Nothing tries again by itself.
     *
     * @return the partial file
     */
    private File startAndCut(FileSyncInstances instances, Scenario scenario) throws Exception {
        instances.createServer();
        instances.createClient();
        instances.clientTransferManager().setRetryBaseDelayMs(TimeUnit.HOURS.toMillis(1));
        instances.cutConnectionAfter(6 * MIB);
        instances.waitForClientTransferState(scenario.bigHash, TransferState.SUSPENDED);
        File partial = new File(instances.clientTransferDir(), scenario.bigHash);
        assertTrue(partial.isFile(), "what came is kept");
        assertTrue(partial.length() > 0 && partial.length() < 24 * MIB, "the file should be half way there but is " + partial.length() + " bytes");
        assertEquals(partial.length(), (long) instances.clientTransferOf(scenario.bigHash).getTransferred().v(), "the database and the disk disagree");
        return partial;
    }

    @Test
    public void aSuspendedDownloadContinuesExactlyWhereTheFileOnDiskEnds() throws Exception {
        Scenario scenario = scenario();
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24130)) {
            File partial = startAndCut(instances, scenario);
            long length = partial.length();
            // nothing happens by itself, the retry is an hour away
            Thread.sleep(500);
            assertEquals(1, instances.serverService.requestsFor(scenario.bigHash).size());
            instances.clientTransferManager().resumeSuspended(instances.serverIdAtClient(), instances.serverService.getUuid());
            assertClientGets(instances, scenario.expected, 90);
            assertEquals(java.util.List.of(new FileSyncInstances.Request(scenario.bigHash, 0, 24 * MIB), new FileSyncInstances.Request(scenario.bigHash, length, 24 * MIB)),
                    instances.serverService.requestsFor(scenario.bigHash));
        }
    }

    /**
     * The app is closed (or dies) half way through a download and started again.
     */
    private java.util.List<FileSyncInstances.Request> restartAfterCut(int portBase, java.util.function.BiConsumer<File, Long> whileDown) throws Exception {
        Scenario scenario = scenario();
        try (FileSyncInstances instances = new FileSyncInstances(dir, portBase)) {
            File partial = startAndCut(instances, scenario);
            long length = partial.length();
            instances.restartClient(() -> whileDown.accept(partial, length));
            // right after the start the connections between the two are sorted out, an attempt that is caught in that is made again soon
            instances.clientTransferManager().setRetryBaseDelayMs(300);
            assertClientGets(instances, scenario.expected, 90);
            return instances.serverService.requestsFor(scenario.bigHash);
        }
    }

    private static long partialLength(File dir, String name) {
        return new File(dir, name).length();
    }

    @Test
    public void aDownloadContinuesAfterTheClientWasRestarted() throws Exception {
        long[] length = new long[1];
        java.util.List<FileSyncInstances.Request> asked = restartAfterCut(24140, (partial, l) -> length[0] = l);
        // an attempt that is lost in the sorting out of the connections is made again, so there may be more
        assertTrue(asked.size() >= 2, "" + asked);
        assertEquals(0, asked.get(0).start());
        assertEquals(length[0], asked.get(1).start(), "the second try should start where the first ended: " + asked);
        assertTrue(asked.stream().skip(1).allMatch(r -> r.start() >= length[0]), "it never starts over: " + asked);
    }

    @Test
    public void whatIsOnDiskBeyondWhatWasNotedDownIsNotTrusted() throws Exception {
        long[] length = new long[1];
        // the app died after writing and before noting down, and the end of the file is rubbish. So much of it that the file is longer than it should ever be.
        java.util.List<FileSyncInstances.Request> asked = restartAfterCut(24150, (partial, l) -> {
            length[0] = l;
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(partial, true)) {
                out.write(new byte[(int) (24 * MIB - l + 5000)]);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(asked.size() >= 2, "" + asked);
        assertEquals(length[0], asked.get(1).start(), "it continues where the database says, the rubbish is overwritten: " + asked);
        assertTrue(asked.stream().skip(1).allMatch(r -> r.start() >= length[0]), "the rubbish is cut off, so the file is right and nothing has to start over: " + asked);
    }

    @Test
    public void aFileThatIsShorterThanTheDatabaseSaysContinuesWhereTheFileEnds() throws Exception {
        long[] length = new long[1];
        java.util.List<FileSyncInstances.Request> asked = restartAfterCut(24160, (partial, l) -> {
            length[0] = l / 2;
            try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(partial, "rw")) {
                file.setLength(l / 2);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(asked.size() >= 2, "" + asked);
        assertEquals(length[0], asked.get(1).start(), "" + asked);
    }

    @Test
    public void aDownloadWhoseFileIsGoneStartsOver() throws Exception {
        java.util.List<FileSyncInstances.Request> asked = restartAfterCut(24170, (partial, l) -> assertTrue(partial.delete()));
        assertTrue(asked.size() >= 2, "" + asked);
        assertEquals(0, asked.get(1).start(), "" + asked);
    }

    /**
     * The part that is on disk is wrong. Nobody can tell before the end, then the hash says it. The file is thrown away and comes again from the start,
     * and what is in the client's directory is never the wrong file.
     */
    @Test
    public void aDownloadWhoseBeginningIsWrongIsDetectedAndStartedOver() throws Exception {
        long[] length = new long[1];
        java.util.List<FileSyncInstances.Request> asked = restartAfterCut(24180, (partial, l) -> {
            length[0] = l;
            try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(partial, "rw")) {
                file.seek(l / 2);
                int b = file.read();
                file.seek(l / 2);
                file.write(b ^ 0xff);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(asked.size() >= 3, "continued once, noticed, then once more from the start: " + asked);
        assertEquals(length[0], asked.get(1).start());
        assertEquals(0, asked.get(asked.size() - 1).start(), "the last attempt is the one from the start: " + asked);
    }

    /**
     * The server sends something else than the file with that hash. Whatever it is, it must not end up in the client's directory.
     */
    @Test
    public void aFileWithTheWrongContentIsNeverDelivered() throws Exception {
        Scenario scenario = scenario();
        String bad = scenario.bigHash;
        scenario.expected.remove("big.bin");
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24190)) {
            instances.createServer();
            instances.serverService.damaged.add(bad);
            instances.createClient();
            instances.clientTransferManager().setRetryBaseDelayMs(TimeUnit.HOURS.toMillis(1));
            // all the others arrive
            assertClientGets(instances, scenario.expected, 60);
            instances.waitForClientTransferState(bad, TransferState.SUSPENDED);
            assertEquals(3, instances.serverService.requestsFor(bad).size(), "it is given up on after a few attempts");
            assertFalse(new File(instances.clientTransferDir(), bad).exists(), "the wrong file is not kept");
            assertFalse(new File(instances.clientRoot, "big.bin").exists());
            // the server is fine again
            instances.serverService.damaged.clear();
            instances.clientTransferManager().resumeSuspended(instances.serverIdAtClient(), instances.serverService.getUuid());
            scenario.expected.put("big.bin", fileSha256(new File(serverRoot(), "big.bin")));
            assertClientGets(instances, scenario.expected, 60);
            assertEquals(4, instances.serverService.requestsFor(bad).size());
        }
    }

    private static String fileSha256(File file) throws Exception {
        return FileSyncInstances.sha256(java.nio.file.Files.readAllBytes(file.toPath()));
    }

    /**
     * The server does not answer the request for files (it never heard it, it is stuck, the connection that carried it was replaced).
     * The client has to notice, and ask again later.
     */
    @Test
    public void aServerThatDoesNotAnswerIsAskedAgainLater() throws Exception {
        Scenario scenario = scenario();
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24200)) {
            instances.createServer();
            instances.serverService.ignoreRequests = true;
            instances.two.client.setRequestTimeoutMs(1000);
            instances.createClient();
            instances.clientTransferManager().setRetryBaseDelayMs(300);
            assertTrue(TwoInstances.waitFor(() -> instances.serverService.requestsFor(scenario.bigHash).size() >= 2, 60),
                    "the client should ask again after the first request got no answer: " + instances.serverService.requests);
            assertFalse(FileSyncInstances.hashesBelow(instances.clientRoot).containsKey("big.bin"));
            instances.serverService.ignoreRequests = false;
            assertClientGets(instances, scenario.expected, 60);
        }
    }

    /**
     * The server takes the request and then sends nothing. The client does not wait for ever.
     */
    @Test
    public void aServerThatSendsNothingIsGivenUpOn() throws Exception {
        Scenario scenario = scenario();
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24210)) {
            instances.createServer();
            instances.serverService.sendNothing = true;
            instances.createClient();
            instances.clientTransferManager().setStallTimeoutMs(1500);
            instances.clientTransferManager().setRetryBaseDelayMs(300);
            assertTrue(TwoInstances.waitFor(() -> instances.serverService.requestsFor(scenario.bigHash).size() >= 2, 60),
                    "the client should give up on the silent server and ask again: " + instances.serverService.requests);
            instances.serverService.sendNothing = false;
            assertClientGets(instances, scenario.expected, 60);
        }
    }
}
