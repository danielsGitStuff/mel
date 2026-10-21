package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
public class FileSyncTransferTest {
    @TempDir
    File dir;

    @Test
    public void clientGetsTheFilesOfTheServer() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("small.bin", FileSyncInstances.writeFile(new File(dir, "serverRoot"), "small.bin", 1000, 1));
        expected.put("sub/medium.bin", FileSyncInstances.writeFile(new File(dir, "serverRoot"), "sub/medium.bin", 300_000, 2));
        expected.put("sub/deeper/large.bin", FileSyncInstances.writeFile(new File(dir, "serverRoot"), "sub/deeper/large.bin", 3_000_000, 3));
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24100)) {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> {
                try {
                    return expected.equals(FileSyncInstances.hashesBelow(instances.clientRoot));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 60), "the client did not get the files, it has: " + FileSyncInstances.hashesBelow(instances.clientRoot).keySet());
            assertEquals(expected, FileSyncInstances.hashesBelow(instances.clientRoot));
        }
    }
}
