package de.mein.auth.tools;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NTest {

    private String[] array, nullArray;
    private StringBuilder resultBuilder;
    private List<String> list, nullList;
    private String completeResult;

    @Before
    public void before() {
        array = new String[]{"a", "b", "c"};
        list = new ArrayList<>();
        completeResult = "";
        for (String s : array) {
            list.add(s);
            completeResult += s;
        }
        nullArray = null;
        nullList = null;
        resultBuilder = new StringBuilder();
    }

    @Test
    public void arrayMerge() {
        Integer[] s1 = new Integer[]{1, 2, 3};
        Integer[] s2 = new Integer[]{4, 5};
        Integer[] s3 = new Integer[]{6, 7, 8};
        Integer[] dest = N.arr.merge(s1, s2, s3);
        for (Integer i = 0; i < s1.length + s2.length + s3.length; i++) {
            assertEquals((Integer) (i + 1), dest[i]);
        }
    }

    @Test
    public void failForEachArrayNull() {
        assertFalse(N.forEach(nullArray, resultBuilder::append));
    }

    @Test
    public void failForEachListNull() {
        assertFalse(N.forEach(nullArray, resultBuilder::append));
    }

    @Test
    public void failForEachArrayException() {
        assertFalse(N.forEach(array, s -> {
            throw new Exception("test");
        }));
    }

    @Test
    public void failForEachListException() {
        assertFalse(N.forEach(list, s -> {
            throw new Exception("test");
        }));
    }

    @Test
    public void forEachArray() {
        boolean success = N.forEach(array, resultBuilder::append);
        assertTrue(success);
        assertEquals(completeResult, resultBuilder.toString());
    }

    @Test
    public void forEachArrayAdv() {
        boolean success = N.forEachAdv(array, (stoppable, index, s) -> resultBuilder.append(s));
        assertTrue(success);
        assertEquals(completeResult, resultBuilder.toString());
    }

    @Test
    public void forEachArrayAdvStop() {
        boolean success = N.forEachAdv(array, (stoppable, index, s) -> {
            resultBuilder.append(s);
            stoppable.stop();
        });
        assertTrue(success);
        assertEquals(array[0], resultBuilder.toString());
    }

    @Test
    public void forEachListy() {
        boolean success = N.forEach(list, resultBuilder::append);
        assertTrue(success);
        assertEquals(completeResult, resultBuilder.toString());
    }

    @Test
    public void forEachListAdv() {
        boolean success = N.forEachAdv(list, (stoppable, index, s) -> resultBuilder.append(s));
        assertTrue(success);
        assertEquals(completeResult, resultBuilder.toString());
    }

    @Test
    public void forEachListAdvStop() {
        boolean success = N.forEachAdv(list, (stoppable, index, s) -> {
            resultBuilder.append(s);
            stoppable.stop();
        });
        assertTrue(success);
        assertEquals(resultBuilder.toString(), array[0]);
    }

    @Test
    public void forLoop() {
        assertTrue(N.forLoop(0, array.length, (stoppable, index) -> {
            resultBuilder.append(array[index]);
        }));
        assertEquals(completeResult, resultBuilder.toString());
    }

    @Test
    public void forLoopStop() {
        assertTrue(N.forLoop(0, array.length, (stoppable, index) -> {
            resultBuilder.append(array[index]);
            stoppable.stop();
        }));
        assertEquals(array[0], resultBuilder.toString());
    }

    @Test
    public void failForLoopException() {
        assertFalse(N.forLoop(0, array.length, (stoppable, index) -> {
            resultBuilder.append(array[index]);
            throw new Exception("test");
        }));
        assertEquals(array[0], resultBuilder.toString());
    }

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
