package de.mein;

import de.mein.konsole.KResult;
import de.mein.konsole.Konsole;
import de.mein.konsole.KonsoleWrongArgumentsException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("Duplicates")
public class KonsoleTest {
    private String[] arguments;
    private Konsole<Dummy> konsole;
    private Dummy dummy;

    @Before
    public void before() {
        arguments = new String[]{"-first", "FIRST"};
        konsole = new Konsole<>(new Dummy());
        dummy = konsole.getResult();
    }

    @Test
    public void first() throws Exception {
        konsole.mandatory("-first", "first descr", (result1, args) -> result1.string = args[0]);
        konsole.handle(arguments);
        assertEquals("FIRST", dummy.string);
    }

    @Test
    public void second() throws Exception {
        arguments = new String[]{"-first", "FIRST", "-second", "5"};
        konsole.mandatory("-first", "first descr", (result1, args) -> result1.string = args[0])
                .mandatory("-second", "second descr", (result, args) -> result.number = Integer.parseInt(args[0]));
        konsole.handle(arguments);
        assertEquals("FIRST", dummy.string);
        assertEquals(5, dummy.number);
    }

    @Test
    public void flagAndNumber() throws KonsoleWrongArgumentsException {
        arguments = new String[]{"-f", "-n", "2"};
        konsole.mandatory("-f", "this is a flag", (result, args) -> result.string = "flag set!")
                .mandatory("-n", "number", (result, args) -> result.number = Integer.parseInt(args[0]));
        konsole.handle(arguments);
        assertEquals("flag set!", dummy.string);
        assertEquals(2, dummy.number);
    }

    @Test
    public void twoFlags() throws KonsoleWrongArgumentsException {
        arguments = new String[]{"-f", "-n"};
        konsole.mandatory("-f", "this is a flag", (result, args) -> result.string = "flag set!")
                .mandatory("-n", "number", (result, args) -> result.number = 666);
        konsole.handle(arguments);
        assertEquals("flag set!", dummy.string);
        assertEquals(666, dummy.number);
    }

    @Test
    public void multipleSubArgs() throws KonsoleWrongArgumentsException {
        arguments = new String[]{"-f", "-multi", "m1", "m2", "-n", "2"};
        konsole.mandatory("-f", "this is a flag", (result, args) -> result.string = "flag set!")
                .mandatory("-n", "number", (result, args) -> result.number = Integer.parseInt(args[0]))
                .mandatory("-multi", "multi desc", (result, args) -> result.manyArgs.addAll(Arrays.asList(args)));
        konsole.handle(arguments);
        assertEquals("flag set!", dummy.string);
        assertEquals(2, dummy.number);
        assertEquals("m1", dummy.manyArgs.get(0));
        assertEquals("m2", dummy.manyArgs.get(1));
        assertEquals(2, dummy.manyArgs.size());
    }

    @Test(expected = KonsoleWrongArgumentsException.class)
    public void mandatoryNotSpecified() throws Exception {
        arguments = new String[]{"-first", "FIRST", "-unrelated", "555"};
        konsole.mandatory("-first", "first descr", (result1, args) -> result1.string = args[0])
                .mandatory("-second", "second descr", (result, args) -> result.number = Integer.parseInt(args[0]));
        konsole.handle(arguments);

    }

    @Test
    public void optional() throws Exception {
        arguments = new String[]{"-first", "FIRST", "-opt", "888"};
        konsole.mandatory("-first", "first descr", (result1, args) -> result1.string = args[0])
                .optional("-opt", "opt descr", (result, args) -> result.number = Integer.parseInt(args[0]));
        konsole.handle(arguments);
        assertEquals("FIRST", dummy.string);
        assertEquals(888, dummy.number);
    }

    @Test(expected = KonsoleWrongArgumentsException.class)
    public void unknownArg() throws Exception {
        arguments = new String[]{"-first", "FIRST", "-unknown", "555"};
        konsole.mandatory("-first", "first descr", (result1, args) -> result1.string = args[0]);
        konsole.handle(arguments);
    }

    @Test
    public void noArgs() throws Exception {
        arguments = new String[]{};
        konsole.handle(arguments);
    }

    @Test(expected = KonsoleWrongArgumentsException.class)
    public void gibberish() throws Exception {
        arguments = new String[]{"one", "two"};
        konsole.handle(arguments);
    }

    public static class Dummy extends KResult {
        String string;
        int number;
        List<String> manyArgs = new ArrayList<>();
    }
}
