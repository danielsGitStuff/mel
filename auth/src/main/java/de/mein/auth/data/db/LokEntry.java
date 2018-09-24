package de.mein.auth.data.db;

import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

public class LokEntry extends SQLTableObject {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<Integer> line = new Pair<>(Integer.class, "line");
    private Pair<String> message = new Pair<>(String.class, "msg");
    private Pair<String> source = new Pair<>(String.class, "src");
    private Pair<String> mode = new Pair<>(String.class, "m");
    private Pair<Long> time = new Pair<>(Long.class, "t");

    public LokEntry() {
        init();
    }

    @Override
    public String getTableName() {
        return "lok";
    }

    @Override
    protected void init() {
        populateInsert(line, message, source, mode, time);
        populateAll(id);
    }

    public LokEntry setLine(Integer line) {
        this.line.v(line);
        return this;
    }

    public LokEntry setMessage(String message) {
        this.message.v(message);
        return this;
    }

    public LokEntry setSource(String source) {
        this.source.v(source);
        return this;
    }

    public LokEntry setMode(String mode) {
        this.mode.v(mode);
        return this;
    }

    public Pair<Integer> getLine() {
        return line;
    }

    public Pair<String> getMessage() {
        return message;
    }

    public Pair<String> getSource() {
        return source;
    }

    public Pair<String> getMode() {
        return mode;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<Long> getTime() {
        return time;
    }

    public LokEntry setTime(Long time) {
        this.time.v(time);
        return this;
    }
}
