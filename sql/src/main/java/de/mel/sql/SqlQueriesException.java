package de.mel.sql;


import de.mel.core.serialize.exceptions.AbstractException;

/**
 * Created by xor on 30.10.2015.
 */
public class SqlQueriesException extends AbstractException {
    public SqlQueriesException(Exception e) {
        super(e);
    }

    public SqlQueriesException(String s) {
        super(new Exception(s));
    }
}
