package de.mel.drive.bash;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

import de.mel.auth.file.AFile;

/**
 * Created by xor on 7/24/17.
 */
public abstract class BufferedIterator<T> extends BufferedReader implements AutoKlausIterator<T> {

    public abstract T convert(String line);


    public BufferedIterator(Reader in) {
        super(in);
    }

    String nextLine = null;

    @Override
    public boolean hasNext() {
        if (nextLine != null) {
            return true;
        } else {
            try {
                nextLine = readLine();
                return (nextLine != null);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    @Override
    public T next() {
        if (nextLine != null || hasNext()) {
            String line = nextLine;
            nextLine = null;
            return convert(line);
        } else {
            throw new NoSuchElementException();
        }
    }


    public static class BufferedFileIterator extends BufferedIterator<AFile> {
        public BufferedFileIterator(Reader in) {
            super(in);
        }

        @Override
        public AFile convert(String line) {
            return AFile.instance(line);
        }
    }

    public static class BufferedStringIterator extends BufferedIterator<String> {

        public BufferedStringIterator(Reader in) {
            super(in);
        }

        @Override
        public String convert(String line) {
            return line;
        }
    }
}
