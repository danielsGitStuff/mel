package de.mein.auth.tools;

import de.mein.Lok;
import de.mein.sql.ISQLResource;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Syntactic sugar. Saves you lots of try/catches. calls e.stacktrace() per default.
 * Can iterate several data structures a bit more comfortable with lambdas and without try/catch cluttering.
 * The iteration stuff mainly exists cause Java Streaming API is not available for older Android versions.
 */
public class N {
    private static N silentRunner = new N(e -> {
    });
    private static N runner = new N(new NoTryExceptionConsumer() {
        @Override
        public void accept(Exception e) {
            e.printStackTrace();
        }
    });
    private NoTryExceptionConsumer consumer;

    public N(NoTryExceptionConsumer consumer) {
        this.consumer = consumer;
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
     * fails silently
     *
     * @param noTryRunnables each {@link INoTryRunnable} is run separately. if one fails it won't prevent the others from being executed.
     */
    public static void s(N.INoTryRunnable... noTryRunnables) {
        for (N.INoTryRunnable noTryRunnable : noTryRunnables)
            N.silentRunner.runTry(noTryRunnable);
    }

    /**
     * runs noTryRunnable in a new Thread
     *
     * @param noTryRunnable
     */
    public static void thread(N.INoTryRunnable noTryRunnable) {
        new Thread(() -> N.r(noTryRunnable)).start();
    }

    /**
     * prints stacktrace when failing
     *
     * @param noTryRunnables each {@link INoTryRunnable} is run separately. if one fails it won't prevent the others from being executed.
     */
    public static void r(N.INoTryRunnable... noTryRunnables) {
        for (N.INoTryRunnable noTryRunnable : noTryRunnables)
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
                Lok.error("N.sqlResource.close() FAILED!");
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
                Lok.error("N.sqlResource.close() FAILED!");
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
                Lok.error("N.sqlResource.close() FAILED!");
                e1.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        N runner = new N(e -> Lok.debug("NoTryRunner.main." + e.getMessage()));
        runner.runTry(() -> {
            List<String> list = (ArrayList) ((Object) 12);
            Lok.debug(list);
        });
        Lok.debug("NoTryRunner.main.end");
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

    /**
     * for loop in lambda style
     *
     * @param start
     * @param stop
     * @param forLoop
     */
    public static boolean forLoop(int start, int stop, ForLoop forLoop) {
        Stoppable stoppable = new Stoppable();
        for (Integer index = start; index < stop; index++) {
            try {
                forLoop.forloop(stoppable, index);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            if (stoppable.isStopped())
                break;
        }
        return true;
    }

    public static <T> boolean forEachAdv(T[] arr, ForEachLoopAdv<T> forEachLoop) {
        return forEachAdv(Arrays.asList(arr), forEachLoop);
    }

    public static <T> boolean forEachAdvIgnorantly(T[] arr, ForEachLoopAdv<T> forEachLoop) {
        return forEachAdvIgnorantly(Arrays.asList(arr), forEachLoop);
    }

    public static <K, V> boolean forEachAdv(Map<K, V> map, MapForEachLoop<K, V> loop) {
        Stoppable stoppable = new Stoppable();
        int index = 0;
        for (K k : map.keySet()) {
            try {
                loop.foreach(stoppable, index, k, map.get(k));
            } catch (Exception e) {
                e.printStackTrace();
                stoppable.stop();
                return false;
            }
            index++;
            if (stoppable.isStopped())
                break;
        }
        return true;
    }

    /**
     * lambda foreach loop.
     *
     * @param <T>
     * @param collection
     * @param forEachLoop
     */
    public static <T> boolean forEachAdv(Collection<T> collection, ForEachLoopAdv<T> forEachLoop) {
        return forEachAdv(collection.iterator(), forEachLoop);
    }

    public static <T> boolean forEachAdvIgnorantly(Collection<T> collection, ForEachLoopAdv<T> forEachLoop) {
        return forEachAdvImpl(collection.iterator(), forEachLoop, false);
    }

    public static <T> boolean forEach(Iterable<T> iterable, ForEachLoop<T> forEachLoop) {
        return N.forEachImpl(iterable.iterator(), forEachLoop, true);
    }

    public static <T> boolean forEach(Iterator<T> iterator, ForEachLoop<T> forEachLoop) {
        return N.forEachImpl(iterator, forEachLoop, true);
    }

    /**
     * @param arr
     * @param forEachLoop
     * @param <T>
     * @return true if successful
     */
    public static <T> boolean forEachIgnorantly(T[] arr, ForEachLoop<T> forEachLoop) {
        return N.forEachImpl(arr, forEachLoop, false);
    }

    public static <T> boolean forEachIgnorantly(Iterable<T> iterable, ForEachLoop<T> forEachLoop) {
        return N.forEachImpl(iterable.iterator(), forEachLoop, false);
    }

    public static <T> boolean forEachIgnorantly(Iterator<T> iterator, ForEachLoop<T> forEachLoop) {
        return N.forEachImpl(iterator, forEachLoop, false);
    }

    /**
     * @param arr
     * @param forEachLoop
     * @param <T>
     * @return true if successful
     */
    public static <T> boolean forEach(T[] arr, ForEachLoop<T> forEachLoop) {
        return N.forEachImpl(arr, forEachLoop, true);
    }


    /**
     * @param <T>
     * @param arr
     * @param forEachLoop
     * @return true if successful
     */
    private static <T> boolean forEachImpl(T[] arr, ForEachLoop<T> forEachLoop, boolean returnOnException) {
        if (arr != null) {
            for (T t : arr) {
                try {
                    forEachLoop.foreach(t);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (returnOnException)
                        return false;
                }
            }
            return true;
        }
        return false;
    }

    private static <T> boolean forEachImpl(Iterator<T> iterator, ForEachLoop<T> forEachLoop, boolean returnOnException) {
        while (iterator.hasNext()) {
            try {
                forEachLoop.foreach(iterator.next());
            } catch (Exception e) {
                e.printStackTrace();
                if (returnOnException)
                    return false;
            }
        }
        return true;
    }

    public static <T> boolean forEachAdv(Iterator<T> iterator, ForEachLoopAdv<T> forEachLoop) {
        return N.forEachAdvImpl(iterator, forEachLoop, true);
    }

    public static <T> boolean forEachAdvIgnorantly(Iterator<T> iterator, ForEachLoopAdv<T> forEachLoop) {
        return N.forEachAdvImpl(iterator, forEachLoop, false);
    }

    public static <T> boolean forEachAdvImpl(Iterator<T> iterator, ForEachLoopAdv<T> forEachLoop, boolean returnOnException) {
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
                if (returnOnException)
                    return false;
            }
        }
        return true;
    }


    public void abort() {
        consumer.accept(new Exception("aborted"));
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

    public interface CastMethod<T, R> {
        R cast(T element);
    }

    public interface INoTryRunnable {
        void run() throws Exception;
    }

    //####

    public interface SqlTryRunnable<T extends SQLTableObject> {
        void run(ISQLResource<T> sqlResource) throws Exception;
    }

    public interface SqlTryReadRunnable<T extends SQLTableObject> {
        void read(ISQLResource<T> sqlResource, T obj) throws Exception;
    }

    public interface NoTryExceptionConsumer {
        void accept(Exception e);
    }

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


    public interface ForLoop {
        void forloop(Stoppable stoppable, Integer index) throws Exception;
    }

    public interface ForEachLoopAdv<T> {
        void foreach(Stoppable stoppable, Integer index, T t) throws Exception;
    }

    public interface ForEachLoop<T> {
        void foreach(T t) throws Exception;
    }

    public interface MapForEachLoop<K, V> {
        void foreach(Stoppable stoppable, Integer index, K k, V v) throws Exception;
    }

    private static class Converter<T, R> {
        private Class<R> castClass;
        private CastMethod<T, R> castMethod;

        public Converter(Class<R> clazz, CastMethod<T, R> castMethod) {
            this.castClass = clazz;
            this.castMethod = castMethod;
        }

        public Class<R> getCastClass() {
            return castClass;
        }

        public R cast(T t) {
            return castMethod.cast(t);
        }
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
     * If you want to exit a loop call stop() on this object.
     */
    public static class Stoppable {
        private boolean stop = false;

        public void stop() {
            this.stop = true;
        }

        public boolean isStopped() {
            return stop;
        }
    }


}

