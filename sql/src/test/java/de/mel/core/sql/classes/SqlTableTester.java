package de.mel.core.sql.classes;

import de.mel.core.serialize.SerializableEntity;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 11/5/15.
 */
public class SqlTableTester extends SQLTableObject implements SerializableEntity {
    private Pair<String> pair = new Pair<String>(String.class,"pair");
    private String obj = "bla";
    private List<SqlTableTester> children = new ArrayList<>();
    private SqlTableTester parent;

    @Override
    public String getTableName() {
        return "hurr";
    }

    @Override
    protected void init() {
        populateInsert(pair);
        populateAll();
    }

    public List<SqlTableTester> getChildren() {
        return children;
    }

    public void addChild(SqlTableTester obj){
        children.add(obj);
    }

    public String getObj() {
        return obj;
    }

    public Pair<String> getPair() {
        return pair;
    }

    public SqlTableTester getParent() {
        return parent;
    }

    public void setParent(SqlTableTester parent) {
        this.parent = parent;
    }
}
