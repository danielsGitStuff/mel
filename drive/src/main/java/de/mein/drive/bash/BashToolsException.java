package de.mein.drive.bash;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by xor on 5/24/17.
 */
public class BashToolsException extends IOException {
    private List<String> lines = new ArrayList<>();

    public BashToolsException(Iterator<String> lines) {
        readLines(lines);
    }

    public BashToolsException(String line) {
        lines.add(line);
    }

    private void readLines(Iterator<String> iterator) {
        while (iterator.hasNext())
            this.lines.add(iterator.next());
    }

    public BashToolsException(Process proc) {
        Iterator<String> lines = BashTools.inputStreamToIterator(proc.getErrorStream());
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
