package de.mein.core.sql;


import de.mein.core.sql.classes.CrashTestDummy;
import de.mein.execute.SqliteExecutor;
import de.mein.sql.SQLConnection;
import de.mein.sql.SQLQueries;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 1/7/16.
 */
public class SQLConnectionTest {
    private static File sqliteFile = new File("sqlite.db");

    @Test
    public void sqlite() throws SQLException, ClassNotFoundException {
        Connection c = SQLConnection.createConnection("client");
        System.out.println(c);
    }

    @Before
    public void before() {
        sqliteFile.delete();
    }

    @Test
    public void transaction() throws Exception {
        Connection con = SQLConnection.createSqliteConnection((sqliteFile));
        SqliteExecutor executor = new SqliteExecutor(con);
        if (!executor.checkTableExists("atest")) {
            executor.executeStream(new FileInputStream(new File("/test.sql")));
        }

        CrashTestDummy dummy = new CrashTestDummy().setName("test 1");
        CrashTestDummy dummy1 = new CrashTestDummy().setName("test 2");
        SQLQueries sqlQueries = new SQLQueries(con);
        sqlQueries.beginTransaction();
        Long dummyId = sqlQueries.insert(dummy);
        sqlQueries.rollback();
        sqlQueries.insert(dummy1);
        // get stuff out again
        List<Object> args = new ArrayList<>();
        args.add(dummyId);
        List<CrashTestDummy> res = sqlQueries.load(dummy.getAllAttributes(), dummy, dummy.getId().k() + "=?", args);
        CrashTestDummy out1 = res.get(0);
        assertEquals(out1.getName().v(), dummy1.getName().v());
    }
}
