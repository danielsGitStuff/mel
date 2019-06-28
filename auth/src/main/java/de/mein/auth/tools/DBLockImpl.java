package de.mein.auth.tools;

import de.mein.LokImpl;
import de.mein.sql.*;

import java.util.concurrent.atomic.AtomicLong;

public class DBLockImpl extends LokImpl {

    private final Long limit;

    public DBLockImpl(Long preserveLogLinesInDb) {
        this.limit = preserveLogLinesInDb;
    }

    public static class DBLokEntry extends SQLTableObject {
        private Pair<String> msg = new Pair<>(String.class, "msg");
        private Pair<String> mode = new Pair<>(String.class, "mode");
        private Pair<Long> timeStamp = new Pair<>(Long.class, "timestamp");
        private Pair<Long> order = new Pair<>(Long.class, "ord");

        public DBLokEntry() {
            init();
        }

        public DBLokEntry(String mode, long order, String msg) {
            init();
            this.order.v(order);
            this.msg.v(msg);
            this.mode.v(mode);
        }

        @Override
        public String getTableName() {
            return "log";
        }

        @Override
        protected void init() {
            populateInsert(msg, order);
            populateAll(timeStamp);
        }

        public DBLokEntry setMsg(String msg) {
            this.msg.v(msg);
            return this;
        }

        public DBLokEntry setOrder(Long order) {
            this.order.v(order);
            return this;
        }

        public DBLokEntry setTimeStamp(Long timeStamp) {
            this.timeStamp.v(timeStamp);
            return this;
        }

        public DBLokEntry setMode(String mode) {
            this.mode.v(mode);
            return this;
        }
    }

    private AtomicLong order = new AtomicLong(0L);

    @Override
    public void debug(Object msg) {
        if (printDebug) {
            String mode = "d";
            String line = fabricate(findStackElement(), mode, msg, true);
            System.out.println(line);
            try {
                sqlQueries.insert(new DBLokEntry(mode, order.getAndIncrement(), line));
                afterInsert();
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void error(Object msg) {
        if (printError) {
            String mode = "e";
            String line = fabricate(findStackElement(), mode, msg, true);
            System.err.println(line);
            try {
                sqlQueries.insert(new DBLokEntry(mode, order.getAndIncrement(), line));
                afterInsert();
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void warn(Object msg) {
        if (printWarn) {
            String mode = "w";
            String line = fabricate(findStackElement(), mode, msg, true);
            System.out.println(line);
            try {
                sqlQueries.insert(new DBLokEntry(mode, order.getAndIncrement(), line));
                afterInsert();
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void info(Object msg) {
        if (printInfo) {
            String mode = "i";
            String line = fabricate(findStackElement(), mode, msg, true);
            System.out.println(line);
            try {
                sqlQueries.insert(new DBLokEntry(mode, order.getAndIncrement(), line));
                afterInsert();
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void afterInsert() {
        // only reorganize if double the limit. this saves runtime
        if (order.get() > 3 * (limit )-1) {
            DBLokEntry entry = new DBLokEntry();
            String deleteStmt = "delete from " + entry.getTableName() + " where " + entry.order.k() + "<?";
            String updateStmt = "update " + entry.getTableName() + " set " + entry.order.k() + "=" + entry.order.k() + " - ?";
            try {
                sqlQueries.execute(deleteStmt, ISQLQueries.whereArgs(limit));
                sqlQueries.execute(updateStmt, ISQLQueries.whereArgs(limit));
                setMaxOrder();
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }

            order.set(0L);
        }
    }

    private SQLQueries sqlQueries;

    public void setupLogToDb(SQLQueries sqlQueries) {
        this.sqlQueries = sqlQueries;
        try {
            setMaxOrder();
            afterInsert();
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }

    private void setMaxOrder() throws SqlQueriesException {
        DBLokEntry entry = new DBLokEntry();
        String query = "select max(" + entry.order.k() + ") from " + entry.getTableName();
        Long maxOrder = sqlQueries.queryValue(query, Long.class);
        if (maxOrder!=null) {
            this.order.set(maxOrder);
        }
    }
}
