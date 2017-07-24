package de.mein.drive.bash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by xor on 5/24/17.
 */
public class BashToolsException extends IOException {
    private List<String> lines = new ArrayList<>();

    public BashToolsException(Stream<String> lines) {
        readLines(lines);
    }

    private void readLines(Stream<String> input) {
        Iterator<String> iterator = input.iterator();
        while (iterator.hasNext())
            this.lines.add(iterator.next());
    }

    public BashToolsException(Process proc) {
        Stream<String> lines = new BufferedReader(new InputStreamReader(proc.getErrorStream())).lines();
        readLines(lines);
    }

    @Override
    public void printStackTrace() {
        System.err.println(toString());
        super.printStackTrace();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(getClass().getSimpleName() + "\n");
        for (String line : lines)
            stringBuilder.append(line).append("\n");
        return stringBuilder.toString();
    }
}
