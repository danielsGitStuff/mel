package de.mel.contacts.data;

import de.mel.contacts.data.db.Contact;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

/**
 * Created by xor on 10/18/17.
 */

public class ContactJoinDummy extends SQLTableObject {
    private Pair<Long> leftId = new Pair<>(Long.class, "loid");
    private Pair<Long> rightId = new Pair<>(Long.class, "reid");
    private Pair<String> name = new Pair<>(String.class, "name");
    private Long choice = null;

    public ContactJoinDummy() {
        init();
    }

    @Override
    public String getTableName() {
        return new Contact().getTableName();
    }

    @Override
    protected void init() {
        populateInsert();
        populateAll(name, leftId, rightId);
    }

    public void setChoice(Long choice) {
        this.choice = choice;
    }

    public Long getChoice() {
        return choice;
    }

    public Pair<String> getName() {
        return name;
    }

    public Pair<Long> getLeftId() {
        return leftId;
    }

    public Pair<Long> getRightId() {
        return rightId;
    }

    public boolean both() {
        return leftId.notNull() && rightId.notNull();
    }
}
