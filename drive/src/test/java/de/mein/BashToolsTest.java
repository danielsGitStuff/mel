package de.mein;

import de.mein.auth.file.FFile;
import de.mein.auth.tools.N;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.ModifiedAndInode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class BashToolsTest {

    private File esc1 = new File("'>fail.txt; echo '1 1");
    private File esc2 = new File("\">fail.txt; echo '2 2");
    private File esc3 = new File("acute`.txt");
    private File esc4 = new File("photo-x-$7006232$180.jpg");
    private List<File> files = Arrays.asList(esc1, esc2, esc3, esc4);

    @Before
    public void before() {
        files.forEach(this::touch);
    }

    private void touch(File file) {
        if (!file.exists()) {
            N.r(() -> new FileOutputStream(file).close());
        }
    }

    @After
    public void after() {
        files.forEach(File::deleteOnExit);
    }

    @Test
    public void escapePaths() throws IOException, InterruptedException {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        files.stream().filter(File::exists).forEach(f -> N.r(() -> {
            System.out.println("BashToolsTest.escapePaths");
            BashTools.init();
            ModifiedAndInode modifiedAndInode = BashTools.getINodeOfFile(new FFile(f));
            System.out.println("BashToolsTest.escapePaths: " + modifiedAndInode.getiNode().toString() + " " + modifiedAndInode.getModified().toString());
            count.getAndSet(count.get() + 1);
        }));
        assertEquals(Integer.valueOf(files.size()), Integer.valueOf(count.get()));
    }
}
