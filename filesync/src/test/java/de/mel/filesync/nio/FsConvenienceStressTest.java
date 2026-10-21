package de.mel.filesync.nio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FsConvenienceStressTest {
    @TempDir
    File dir;

    @Test
    public void manyCopiesOfTheSameFileAreAllThere() throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        List<String> problems = new ArrayList<>();
        for (int size : new int[]{0, 30_000}) {
            for (int round = 0; round < 200; round++) {
                UnixFsConvenience convenience = new UnixFsConvenience();
                File round_dir = new File(dir, "r" + size + "_" + round);
                assertTrue(round_dir.mkdirs());
                File source = new File(round_dir, "source");
                Files.write(source.toPath(), new byte[size]);
                for (int i = 0; i < 5; i++) {
                    File target = new File(round_dir, "c" + i);
                    CopyResult result = convenience.copy(AbstractFile.instance(source), AbstractFile.instance(target), true);
                    if (!target.exists() || target.length() != size)
                        problems.add("size " + size + " round " + round + " copy " + i + ": " + result + ", exists " + target.exists());
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " problems, first: " + problems.subList(0, Math.min(10, problems.size())));
    }
}
