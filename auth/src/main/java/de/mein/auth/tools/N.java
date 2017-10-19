package de.mein.auth.tools;

import de.mein.auth.socket.ShamefulSelfConnectException;
import de.mein.sql.ISQLResource;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * Syntactic sugar. Saves you lots of try/catches. calls e.stacktrace() per default.
 */
public class N {
    /**
     * fails silently
     */
    public static void s(N.INoTryRunnable noTryRunnable) {
        N.silentRunner.runTry(noTryRunnable);
    }

    private static N silentRunner = new N(e -> {
    });

    public interface INoTryRunnable {
        void run() throws Exception, ShamefulSelfConnectException;
    }

    public interface SqlTryRunnable<T extends SQLTableObject> {
        void run(ISQLResource<T> sqlResource) throws Exception;
    }

    public interface SqlTryReadRunnable<T extends SQLTableObject> {
        void read(T obj) throws Exception;
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

    public static <T extends SQLTableObject> void readSqlResource(ISQLResource<T> sqlResource, SqlTryReadRunnable<T> noTryRunnable) {
        try {
            T obj = sqlResource.getNext();
            try {
                while (obj != null) {
                    noTryRunnable.read(obj);
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
}

