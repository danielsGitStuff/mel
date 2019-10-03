package de.mel.filesync.bash;

import java.util.Iterator;

public interface AutoKlausIterator<T> extends Iterator<T>, AutoCloseable {

    public static class EmpyAutoKlausIterator<T> implements AutoKlausIterator<T>{
        @Override
        public void close() throws Exception {

        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            return null;
        }
    }
}
