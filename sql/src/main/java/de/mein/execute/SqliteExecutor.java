package de.mein.execute;

import de.mein.core.serialize.serialize.tools.StringBuilder;
import de.mein.sql.SQLStatement;
import de.mein.sql.con.SQLConnection;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * Created by xor on 3/28/16.
 */
public class SqliteExecutor {

    private SQLConnection connection;
    private String statement;
    private static SqliteExecutorInjection injectedImpl;


    public static void setExecutorImpl(SqliteExecutorInjection injectedImpl) {
        SqliteExecutor.injectedImpl = injectedImpl;
    }

    StringBuilder b = new StringBuilder();
    StringBuilder debug = new StringBuilder();


    public SqliteExecutor(SQLConnection connection) {
        this.connection = connection;
    }

    public void executeStream(InputStream in) throws IOException, SQLException {
        // this is all hackery and might break
        // it should get along with the intellij auto formatter
        System.out.println("SqliteExecutor.executeStream");
        if (injectedImpl != null)
            injectedImpl.executeStream(connection, in);
        else {
            Scanner s = new Scanner(in, "UTF-8");//new Scanner(String.class.getResourceAsStream(resource), "UTF-8");
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
                            debug.append(statement).append("\n");
                            try {
                                SQLStatement st = connection.prepareStatement(statement);
                                st.execute();
                                st.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
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

    public boolean checkTableExists(String tableName) {
        try {
            if (injectedImpl != null) {
                if (injectedImpl.checkTableExists(connection, tableName))
                    return true;
                else
                    System.err.println("SqliteExecutor.checkTableExists(" + tableName + ")=false :(");
            } else {
                String query = "select * from " + tableName;
                connection.prepareStatement(query).execute();
                return true;
            }
        } catch (Exception e) {
            System.out.println("SqliteExecutor.checkTableExists.false");
        }
        return false;
    }
}