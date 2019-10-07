package de.mel.auth.tools;

import de.mel.Lok;
import de.mel.LokImpl;
import de.mel.execute.SqliteExecutor;
import de.mel.sql.*;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.transform.SqlResultTransformer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public class DBLokImpl extends LokImpl {

    /**
     * whether or not to store in database
     *
     * @return
     */
    protected boolean store() {
        return true;
    }

    public static ByteArrayInputStream getCreateStatementInputStream() {
        String statement = "drop table if exists \"log\";\n" +
                "create table \"log\" (\n" +
                "ord integer,\n" +
                "msg text,\n" +
                "mode text,\n" +
                "timestamp integer default current_timestamp);\n" +
                "create index logi on log(ord);";
        return new ByteArrayInputStream(statement.getBytes());
    }

    public static void setupDBLockImpl(File logDb, long preservedLogLines) throws SQLException, ClassNotFoundException, IOException {
        DBLokImpl lokImpl = new DBLokImpl(preservedLogLines);
        Lok.debug("opening database: " + logDb.getAbsolutePath());
        SQLQueries sqlQueries = new SQLQueries(SQLConnector.createSqliteConnection(logDb), SqlResultTransformer.sqliteResultSetTransformer());
        SQLStatement st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA synchronous=OFF");
        st.execute();
        SqliteExecutor sqliteExecutor = new SqliteExecutor(sqlQueries.getSQLConnection());
        if (!sqliteExecutor.checkTableExists("log")) {
            try (ByteArrayInputStream in = getCreateStatementInputStream()) {
                sqliteExecutor.executeStream(in);
            }
        }
        lokImpl.setupLogToDb(sqlQueries);
        Lok.setLokImpl(lokImpl);
    }

    private final Long limit;

    public DBLokImpl(Long preserveLogLinesInDb) {
        this.limit = preserveLogLinesInDb;
    }

    public static class DBLokEntry extends SQLTableObject {
        private Pair<String> msg = new Pair<>(String.class, "msg");
        private Pair<String> mode = new Pair<>(String.class, "mode");
        private Pair<Long> timeStamp = new Pair<>(Long.class, "timestamp");
        Pair<Long> order = new Pair<>(Long.class, "ord");

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

    protected AtomicLong order = new AtomicLong(0L);

    @Override
    public void debug(Object msg) {
        if (printDebug) {
            String mode = "d";
            String line = fabricate(findStackElement(), mode, msg, true);
            System.out.println(line);
            storeToDb(mode, order.getAndIncrement(), line);
        }
    }

    protected void storeToDb(String mode, long orderAndIncrement, String line) {
        if (store() && sqlQueries != null) {
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
            storeToDb(mode, order.getAndIncrement(), line);
        }
    }

    @Override
    public void warn(Object msg) {
        if (printWarn) {
            String mode = "w";
            String line = fabricate(findStackElement(), mode, msg, true);
            System.out.println(line);
            storeToDb(mode, order.getAndIncrement(), line);
        }
    }

    @Override
    public void info(Object msg) {
        if (printInfo) {
            String mode = "i";
            String line = fabricate(findStackElement(), mode, msg, true);
            System.out.println(line);
            storeToDb(mode, order.getAndIncrement(), line);
        }
    }

    private synchronized void afterInsert() {
        // only reorganize if double the limit. this saves runtime
        if (order.get() > 3 * (limit) - 1) {
            DBLokEntry entry = new DBLokEntry();
            String deleteStmt = "delete from " + entry.getTableName() + " where " + entry.order.k() + "<?";
            String updateStmt = "update " + entry.getTableName() + " set " + entry.order.k() + "=" + entry.order.k() + " - ?";
            try {
                sqlQueries.execute(deleteStmt, ISQLQueries.args(limit));
                sqlQueries.execute(updateStmt, ISQLQueries.args(limit));
                setMaxOrder();
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }

            order.set(0L);
        }
    }

    private ISQLQueries sqlQueries;

    public void setupLogToDb(ISQLQueries sqlQueries) {
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
        if (maxOrder != null) {
            this.order.set(maxOrder);
        }
    }
}
