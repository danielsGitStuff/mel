package de.mein.auth.tools;

import org.junit.Test;

import java.io.File;

public class NTest {
    @Test
    public void castArray() {
        File[] files = new File[]{new File("a"), new File("b"), new File("c")};
        String[] strings = N.arr.cast(files, N.converter(String.class, File::getName));
        NWrap.BWrap matches = new NWrap.BWrap(true);
        N.forLoop(0, files.length, (stoppable, index) -> {
            if (!files[index].getName().equals(strings[index])) {
                matches.v(false);
                stoppable.stop();
            }
        });
        org.junit.Assert.assertTrue(matches.v());


    }
}
