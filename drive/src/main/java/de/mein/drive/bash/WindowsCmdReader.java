package de.mein.drive.bash;

import java.io.*;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by xor on 13.07.2017.
 */
public class WindowsCmdReader extends BufferedReader {


    public WindowsCmdReader(Reader in, int sz) {
        super(in, sz);
    }

    public WindowsCmdReader(Reader in) {
        super(in);
    }

    @Override
    public Stream<String> lines() {
        Iterator<String> iter = new Iterator<String>() {
            String next = null;
            String after = null;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    if (next.length() == 0)
                        return false;
                    return true;
                } else {
                    try {
                        next = readLine();
                        if (next == null || next.length() == 0)
                            return false;
                        after = readLine();
                        return (next != null);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            @Override
            public String next() {
                if (next != null || hasNext()) {
                    String line = next;
                    next = after;
                    after = null;
                    return line;
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                iter, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }
}
