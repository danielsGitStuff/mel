package de.mein.auth.tools;

import de.mein.sql.ISQLResource;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Syntactic sugar. Saves you lots of try/catches. calls e.stacktrace() per default.
 */
public class N {
    public interface CastMethod<T, R> {
        R cast(T element);
    }

    private static class Converter<T, R> {
        private Class<R> castClass;
        private CastMethod<T, R> castMethod;

        public Class<R> getCastClass() {
            return castClass;
        }

        public Converter(Class<R> clazz, CastMethod<T, R> castMethod) {
            this.castClass = clazz;
            this.castMethod = castMethod;
        }

        public R cast(T t) {
            return castMethod.cast(t);
        }
    }



    /**
     * Creates a {@link Converter} which handles your casting/conversion from S to R.
     *
     * @param clazz  result class
     * @param method put your lambda here
     * @param <S>    source type
     * @param <R>    result type
     * @return
     */
    public static <S, R> Converter<S, R> converter(Class<R> clazz, CastMethod<S, R> method) {
        Converter<S, R> converter = new Converter<>(clazz, method);
        return converter;
    }

    /**
     * helps doing common things with arrays
     */
    public static class arr {


        public static <T, R> R[] cast(T[] source, Converter<T, R> converter) {
            if (source == null)
                return null;
            R[] result = (R[]) Array.newInstance(converter.getCastClass(), source.length);
            for (int i = 0; i < source.length; i++) {
                if (source[i] != null)
                    result[i] = converter.cast(source[i]);
            }
            return result;
        }

        public static <T, R> R[] fromCollection(List<T> source, Converter<T, R> converter) {
            if (source == null)
                return null;
            R[] result = (R[]) Array.newInstance(converter.getCastClass(), source.size());
            int i = 0;
            for (T t : source) {
                result[i] = converter.cast(t);
                i++;
            }
            return result;

        }
    }

    /**
     * fails silently
     */
    public static void s(N.INoTryRunnable noTryRunnable) {
        N.silentRunner.runTry(noTryRunnable);
    }

    private static N silentRunner = new N(e -> {
    });

    public void abort() {
        consumer.accept(new Exception("aborted"));
    }

    /**
     * runs noTryRunnable in a new Thread
     *
     * @param noTryRunnable
     */
    public static void thread(N.INoTryRunnable noTryRunnable) {
        new Thread(() -> N.r(noTryRunnable)).start();
    }

    public interface INoTryRunnable {
        void run() throws Exception;
    }

    public interface SqlTryRunnable<T extends SQLTableObject> {
        void run(ISQLResource<T> sqlResource) throws Exception;
    }

    public interface SqlTryReadRunnable<T extends SQLTableObject> {
        void read(ISQLResource<T> sqlResource, T obj) throws Exception;
    }

    public interface NoTryExceptionConsumer {
        void accept(Exception e);
    }

    private static N runner = new N(new NoTryExceptionConsumer() {
        @Override
        public void accept(Exception e) {
            e.printStackTrace();
        }
    });

    /**
     * prints stacktrace when failing
     *
     * @param noTryRunnable
     */
    public static void r(N.INoTryRunnable noTryRunnable) {
        N.runner.runTry(noTryRunnable);
    }

    /**
     * closes the Resource when failing or finishing
     *
     * @param sqlResource   is handed to the {@link INoTryRunnable}
     * @param noTryRunnable
     */
    public static <T extends SQLTableObject> void sqlResource(ISQLResource<T> sqlResource, SqlTryRunnable<T> noTryRunnable) {
        try {
            noTryRunnable.run(sqlResource);
            sqlResource.close();
        } catch (Exception e) {
            try {
                e.printStackTrace();
                sqlResource.close();
            } catch (SqlQueriesException e1) {
                System.err.println("N.sqlResource.close() FAILED!");
                e1.printStackTrace();
            }
        }
    }

    /**
     * Iterates through the entire SQLResource and closes it after finish. Continues after Exception.
     *
     * @param sqlResource   is iterated over. its values are handed over to the noTryRunnable.
     * @param noTryRunnable call close() on sqlResource if you do not want to iterate over the rest
     * @param <T>
     */
    public static <T extends SQLTableObject> void readSqlResourceIgnorantly(ISQLResource<T> sqlResource, SqlTryReadRunnable<T> noTryRunnable) {
        try {
            T obj = sqlResource.getNext();
            while (obj != null && !sqlResource.isClosed()) {
                try {
                    noTryRunnable.read(sqlResource, obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                obj = sqlResource.getNext();
            }
        } catch (Exception e) {
            try {
                e.printStackTrace();
                sqlResource.close();
            } catch (SqlQueriesException e1) {
                System.err.println("N.sqlResource.close() FAILED!");
                e1.printStackTrace();
            }
        } finally {
            try {
                sqlResource.close();
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Iterates through the entire SQLResource and closes it after finish or when Exceptions occur.
     *
     * @param sqlResource   is iterated over. its values are handed over to the noTryRunnable.
     * @param noTryRunnable call close() on sqlResource if you do not want to iterate over the rest
     * @param <T>
     */
    public static <T extends SQLTableObject> void readSqlResource(ISQLResource<T> sqlResource, SqlTryReadRunnable<T> noTryRunnable) {
        try {
            T obj = sqlResource.getNext();
            try {
                while (obj != null && !sqlResource.isClosed()) {
                    noTryRunnable.read(sqlResource, obj);
                    obj = sqlResource.getNext();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                sqlResource.close();
            }
        } catch (Exception e) {
            try {
                e.printStackTrace();
                sqlResource.close();
            } catch (SqlQueriesException e1) {
                System.err.println("N.sqlResource.close() FAILED!");
                e1.printStackTrace();
            }
        }
    }

    private NoTryExceptionConsumer consumer;

    public N(NoTryExceptionConsumer consumer) {
        this.consumer = consumer;
    }

    public NoTryExceptionConsumer getConsumer() {
        return consumer;
    }

    public N runTry(INoTryRunnable noTryRunnable) {
        try {
            noTryRunnable.run();
        } catch (Exception e) {
            consumer.accept(e);
        }
        return this;
    }

    public N runTry(INoTryRunnable noTryRunnable, NoTryExceptionConsumer consumer) {
        try {
            noTryRunnable.run();
        } catch (Exception e) {
            consumer.accept(e);
        }
        return this;
    }

    public static void main(String[] args) {
        N runner = new N(e -> System.out.println("NoTryRunner.main." + e.getMessage()));
        runner.runTry(() -> {
            List<String> list = (ArrayList) ((Object) 12);
            System.out.println(list);
        });
        System.out.println("NoTryRunner.main.end");
    }

    //####

    /**
     * call stop() on reader when you do not want to iterate further
     *
     * @param <T>
     */
    public interface CollectionReadRunnable<T> {
        /**
         * call stop() on reader when you do not want to iterate further
         *
         * @param reader
         * @param obj
         * @throws Exception
         */
        void iterate(CollectionReader<T> reader, T obj) throws Exception;
    }

    public static class CollectionReader<T> extends Stoppable {
        private final Iterator<T> iterator;

        public CollectionReader(Collection<T> collection) {
            this.iterator = collection.iterator();
        }


        public boolean hasNext() {
            return iterator.hasNext();
        }

        public T next() {
            return iterator.next();
        }
    }

    /**
     * Iterates through the entire SQLResource and closes it after finish or when Exceptions occur.
     *
     * @param collection    is iterated over. its values are handed over to the noTryRunnable.
     * @param noTryRunnable call close() on sqlResource if you do not want to iterate over the rest
     * @param <T>
     */
    public static <T> void readCollection(Collection<T> collection, CollectionReadRunnable<T> noTryRunnable) {
        try {
            CollectionReader<T> reader = new CollectionReader(collection);
            while (reader.hasNext() && !reader.isStopped()) {
                noTryRunnable.iterate(reader, reader.next());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Stoppable {
        private boolean stop = false;

        public void stop() {
            this.stop = true;
        }

        public boolean isStopped() {
            return stop;
        }
    }


    public interface ForLoop {
        void forloop(Stoppable stoppable, int index) throws Exception;
    }

    public static void forLoop(int start, int stop, ForLoop forLoop) {
        Stoppable stoppable = new Stoppable();
        for (int index = start; index < stop; index++) {
            try {
                forLoop.forloop(stoppable, index);
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
            if (stoppable.isStopped())
                break;
        }
    }

    public interface ForEachLoop<T> {
        void foreach(Stoppable stoppable, int index, T t) throws Exception;
    }

    public static <T> void forEach(T[] arr, ForEachLoop<T> forEachLoop) {
        forEach(Arrays.asList(arr), forEachLoop);
    }

    public static <T> void forEach(Collection<T> collection, ForEachLoop<T> forEachLoop) {
        Stoppable stoppable = new Stoppable();
        int index = 0;
        for (T t : collection) {
            try {
                forEachLoop.foreach(stoppable, index, t);
            } catch (Exception e) {
                e.printStackTrace();
                stoppable.stop();
            }
            index++;
            if (stoppable.isStopped())
                break;
        }
    }

    public static <T> void forEach(Iterator<T> iterator, ForEachLoop<T> forEachLoop) {
        Stoppable stoppable = new Stoppable();
        int index = 0;
        while (iterator.hasNext() && !stoppable.isStopped()) {
            try {
                forEachLoop.foreach(stoppable, index, iterator.next());
                if (stoppable.isStopped())
                    break;
                index++;
            } catch (Exception e) {
                e.printStackTrace();
                stoppable.stop();
            }
        }
    }


}

