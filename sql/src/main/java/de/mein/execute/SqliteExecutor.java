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

import de.mein.sql.SQLStatement;
import de.mein.sql.con.SQLConnection;

/**
 * Created by xor on 3/28/16.
 */
public class SqliteExecutor {

    private SQLConnection connection;
    private String statement;
    StringBuilder b = new StringBuilder();

    public SqliteExecutor(SQLConnection connection) {
        this.connection = connection;
    }

    public void executeStream(InputStream in) throws IOException, SQLException {
        // this is all hackery and might break
        // it should get along with the intellij auto formatter
        System.out.println("SqliteExecutor.executeStream");
        Scanner s = new Scanner(in,"UTF-8");//new Scanner(String.class.getResourceAsStream(resource), "UTF-8");
        //s.useDelimiter("(;(\r)?\n)|(--\n)");
        s.useDelimiter("\n|\r");
        try {
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
                        SQLStatement st = connection.prepareStatement(statement);
                        st.execute();
                        st.close();
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
            String query = "select * from "+dbName;
            connection.prepareStatement(query).execute();
            return true;
//            ResultSet resultSet = connection.getMetaData().getTables(null, null, "%", null);
//            //connection.getMetaData().getCatalogs();
//            while (resultSet.next()) {
//                String databaseName = resultSet.getString(3);
//                if (databaseName.equals(dbName)) {
//                    return true;
//                }
//            }
//            resultSet.close();
        } catch (Exception e) {
            System.out.println("SqliteExecutor.checkTableExists.false");
        }
        return false;
    }
}