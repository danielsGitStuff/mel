package de.mein.sql;

/**
 * Just let getTableName() return something like <br>
 *     'A a inner join B b on a.col = b.col'
 */
public abstract class JoinObject extends SQLTableObject {

    @Override
    protected void init() {

    }
}
