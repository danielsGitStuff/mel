package de.mein.konsole;

import de.mein.Lok;
import de.mein.core.serialize.serialize.tools.StringBuilder;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 3/12/17.
 */
public class Konsole<T extends KResult> {
    public static class check{
        /**
         * checks whether a path points to a file or directory and if you can read it
         * @param path
         * @return
         * @throws ParseArgumentException
         */
        public static String checkRead(String path) throws ParseArgumentException {
            File f = new File(path);
            if (!f.exists())
                throw new ParseArgumentException("file does not exist: "+path);
            if (!f.canRead())
                throw new ParseArgumentException("cannot read file: "+path);
            return path;
        }
    }
    private T result;
    private Map<String, KReader> argsMap = new HashMap<>();
    private Map<String, String> descMap = new HashMap<>();
    // set to false if not specified
    private Map<String, Boolean> mandatory = new HashMap<>();
    private int pos = 0;
    private String currentArg;

    public T getResult() {
        return result;
    }

    public Konsole(T result) {
        this.result = result;
        this.optional("--help", "prints help", (result1, args) -> printHelp());
    }

    public Konsole<T> optional(String name, String description, KReader<T> definition) {
        argsMap.put(name, definition);
        descMap.put(name, description);
        return this;
    }

    public Konsole<T> mandatory(String name, String description, KReader<T> definition) {
        optional(name, description, definition);
        mandatory.put(name, false);
        return this;
    }

    public Konsole handle(String[] args) throws KonsoleWrongArgumentsException {
        while (pos < args.length) {
            currentArg = args[pos];
            KReader reader = argsMap.get(currentArg);
            int readerPos = ++pos;
            String nextArg = null;
            for (; pos < args.length; pos++) {
                String arg = args[pos];
                if (argsMap.containsKey(arg)) {
                    nextArg = arg;
                    break;
                }
                // that is how arguments start!
                if (arg.startsWith("-")) {
                    throw new KonsoleWrongArgumentsException("unknown argument: " + currentArg);
                }
            }
            String[] argsForReader = Arrays.copyOfRange(args, readerPos, pos);
            if (reader == null) {
                printHelp();
                throw new KonsoleWrongArgumentsException("unknown argument: " + currentArg);
            }
            try {
                mandatory.put(currentArg, true);
                reader.handle(result, argsForReader);
            } catch (ParseArgumentException e) {
                throw new KonsoleWrongArgumentsException("error when consuming: " + currentArg + ", message: " + e.getMessage());
            } catch (Exception e) {
                throw new KonsoleWrongArgumentsException(e.getMessage());
            }
            currentArg = nextArg;
        }

        if (mandatory.values().stream().anyMatch(aBoolean -> !aBoolean)) {
            printMissingArgs();
            printHelp();
            throw new KonsoleWrongArgumentsException("you did not specify all mandatory arguments!");
        }
        return this;
    }

    private void printMissingArgs() {
        StringBuilder b = new StringBuilder().arrBegin().append("not specified arguments").arrEnd().append(":").lineBreak();
        mandatory.keySet().stream().filter(s -> !mandatory.get(s)).sorted().forEach(s -> b.append(s).lineBreak());
        Lok.error(b.toString());
    }

    private void printLine(String argName) {
        StringBuilder b = new StringBuilder();
        b.append("name: ")
                .append(argName)
                .append(", mandatory: ")
                .append(mandatory.containsKey(argName))
                .append(", descr: ")
                .append(descMap.get(argName));
        Lok.error(b.toString());
    }

    private void printHelp() {
        Lok.error("Help");
        Lok.error("mandatory arguments:");
        mandatory.keySet().stream().sorted().forEach(this::printLine);
        Lok.error("optional arguments:");
        argsMap.keySet().stream().filter(s -> !mandatory.containsKey(s)).sorted().forEach(this::printLine);
    }
}
