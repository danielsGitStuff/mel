package de.mein.execute;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

/**
 * Created by xor on 3/28/16.
 */
public class SqliteExecutor {

    private Connection connection;
    private String statement;
    StringBuilder b = new StringBuilder();

    public SqliteExecutor(Connection connection) {
        this.connection = connection;
    }

    public void executeStream(InputStream in) throws IOException, SQLException {
        // this is all hackery and might break
        // it should get along with the intellij auto formatter
        System.out.println("SqliteExecutor.executeStream");
        Scanner s = new Scanner(in);//new Scanner(String.class.getResourceAsStream(resource), "UTF-8");
        //s.useDelimiter("(;(\r)?\n)|(--\n)");
        s.useDelimiter("\n|\r");
        Statement st = null;
        try {
            st = connection.createStatement();
            int begin = 0;
            boolean comment = false;
            while (s.hasNext()) {
                String line = s.next();
                String trimmed = line.trim().toLowerCase();
                boolean ap = false;
                if (trimmed.startsWith("begin") && !trimmed.startsWith("begin transaction")) {
                    begin++;
                    append(line);
                    ap = true;
                } else if (trimmed.startsWith("end")) {
                    begin--;
                    append(line);
                    ap = true;
                } else if (trimmed.startsWith("/*"))
                    comment = true;
                else if (trimmed.endsWith("*/"))
                    comment = false;
                if (begin == 0) {
                    if (line.endsWith(";")) {
                        if (!ap)
                            append(line);
                        this.statement = b.toString();
                        st.execute(statement);
                        b = new StringBuilder();
                    } else {
                        if (!ap)
                            append(line);
                    }
                } else {
                    if (!ap)
                        append(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (st != null) st.close();
        }
    }

    /**
     * removes line comments before adding
     *
     * @param line
     */
    private void append(String line) {
        boolean string = false;
        String[] parts = line.split("\\-\\-");
        b.append(" ");
        b.append(parts[0]);
    }

    private boolean isValid(String line) {
        return line.trim().length() > 0 && !line.trim().startsWith("--") && line.trim().endsWith(";");
    }

    /**
     * not optimized for performance but does the job
     *
     * @param names
     * @return
     */
    public boolean checkTablesExist(String... names) {
        for (String name : names) {
            if (!checkTableExists(name))
                return false;
        }
        return true;
    }

    public boolean checkTableExists(String dbName) {
        try {
            ResultSet resultSet = connection.getMetaData().getTables(null, null, "%", null);
            //connection.getMetaData().getCatalogs();
            while (resultSet.next()) {
                String databaseName = resultSet.getString(3);
                if (databaseName.equals(dbName)) {
                    return true;
                }
            }
            resultSet.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}