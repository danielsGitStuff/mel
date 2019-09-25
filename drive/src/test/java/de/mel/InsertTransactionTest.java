package de.mel;

import de.mel.core.serialize.serialize.tools.OTimer;
import de.mel.sql.*;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.transform.SqlResultTransformer;
import org.junit.Test;

import java.io.File;

public class InsertTransactionTest {
    class TTest extends SQLTableObject {

        TTest() {
            init();
        }

        private Pair<Long> id = new Pair<>(Long.class, "id");
        private Pair<String> txt = new Pair<>(String.class, "txt");

        @Override
        public String getTableName() {
            return "test";
        }

        @Override
        protected void init() {
            this.populateInsert(txt);
            populateAll(id);
        }
    }

//    @Test
    public void transaction() throws Exception {
        File dbFile = new File("performance.db");
        if (dbFile.exists())
            dbFile.delete();
        Lok.debug("writing to " + dbFile.getAbsolutePath());
        SQLQueries sqlQueries = new SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
        String createStmt = "create table test(id integer not null primary key autoincrement,txt text)";
        sqlQueries.execute(createStmt, null);

        SQLStatement st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA synchronous=OFF");
        st.execute();

        OTimer timer = new OTimer("insert").start();
        sqlQueries.beginTransaction();

        for (int i = 0; i < 200000; i++) {
            TTest tTest = new TTest();
            tTest.txt.v("i=" + i);
            sqlQueries.insert(tTest);
//            Long fps = timer.fps();
//            if (i % 1000 == 0)
//                Lok.debug("fps: " + fps);
        }
        sqlQueries.commit();
        timer.stop().print();
    }
}
