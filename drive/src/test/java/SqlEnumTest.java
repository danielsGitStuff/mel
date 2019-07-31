import de.mein.Lok;
import de.mein.sql.*;
import de.mein.sql.conn.SQLConnector;
import de.mein.sql.transform.SqlResultTransformer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

public class SqlEnumTest {

    private File dbFile;
    private SQLQueries sqlQueries;


    public static enum TestEnum {
        ONE,
        TWO,
        THREE
    }

    public static class TestObject extends SQLTableObject {

        public TestObject() {
            init();
        }

        Pair<Integer> id = new Pair<>(Integer.class, "id");
        Pair<TestEnum> enumPair = new Pair<>(TestEnum.class, "enum");

        @Override
        public String getTableName() {
            return "test";
        }

        @Override
        protected void init() {
            populateInsert(enumPair);
            populateAll(id);
        }
    }

    @Test
    public void checkPresent() throws SqlQueriesException {
        // if this fails: insert in before method failed!
        TestObject dummy = new TestObject();
        TestObject read = sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, null, null, TestObject.class);
        assertNotNull(read);
        assertEquals(TestEnum.TWO, read.enumPair.v());
    }

    @Test
    public void loadFirstRow() throws SqlQueriesException {
        TestObject dummy = new TestObject();
        String where = dummy.enumPair.k() + "=?";
        TestObject read = sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(TestEnum.TWO), TestObject.class);
        assertNotNull(read);
        assertEquals(TestEnum.TWO, read.enumPair.v());
    }

    @Test
    public void load() throws SqlQueriesException {
        TestObject dummy = new TestObject();
        String where = dummy.enumPair.k() + "=?";
        List<TestObject> read = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(TestEnum.TWO));
        assertNotNull(read);
        assertEquals(1, read.size());
        assertEquals(TestEnum.TWO, read.get(0).enumPair.v());
    }

    @Test
    public void updateOne() throws SqlQueriesException {
        TestObject dummy = new TestObject();
        String updateStmt = "update " + dummy.getTableName() + " set " + dummy.enumPair.k() + "=?";
        sqlQueries.execute(updateStmt, ISQLQueries.whereArgs(TestEnum.THREE));
        String where = dummy.enumPair.k() + "=?";
        TestObject read = sqlQueries.loadFirstRow(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(TestEnum.THREE), TestObject.class);
        assertNotNull(read);
        assertEquals(TestEnum.THREE, read.enumPair.v());
    }

    @Test
    public void updateOneInMany() throws SqlQueriesException {
        for (int i = 0; i < 3; i++) {
            TestObject object = new TestObject();
            object.enumPair.v(TestEnum.TWO);
            sqlQueries.insert(object);
        }
        TestObject dummy = new TestObject();
        String updateStmt = "update " + dummy.getTableName() + " set " + dummy.enumPair.k() + "=? where " + dummy.id.k() + "=?";
        sqlQueries.execute(updateStmt, ISQLQueries.whereArgs(TestEnum.THREE, 3));
        List<TestObject> read = sqlQueries.load(dummy.getAllAttributes(), dummy, null, null);
        assertNotNull(read);
        read.forEach(testObject -> {
            switch (testObject.id.v()) {
                case 1:
                    assertEquals(TestEnum.TWO, testObject.enumPair.v());
                    break;
                case 2:
                    assertEquals(TestEnum.TWO, testObject.enumPair.v());
                    break;
                case 3:
                    assertEquals(TestEnum.THREE, testObject.enumPair.v());
                    break;
                case 4:
                    assertEquals(TestEnum.TWO, testObject.enumPair.v());
                    break;
                default:
                    break;
            }
        });
    }

    @Before
    public void before() throws SQLException, ClassNotFoundException, SqlQueriesException {
        dbFile = new File("db.test.file");
        if (dbFile.exists())
            dbFile.delete();
        Lok.debug("opening database: " + dbFile.getAbsolutePath());
        sqlQueries = new SQLQueries(SQLConnector.createSqliteConnection(dbFile), SqlResultTransformer.sqliteResultSetTransformer());
        String createStatement = "create table test (id integer not null primary key autoincrement,enum text)";
        sqlQueries.execute(createStatement, ISQLQueries.whereArgs());
        // insert a test object
        TestObject writeObject = new TestObject();
        writeObject.enumPair.v(TestEnum.TWO);
        sqlQueries.insert(writeObject);
    }

    @After
    public void after() {
//        dbFile.delete();
    }
}
