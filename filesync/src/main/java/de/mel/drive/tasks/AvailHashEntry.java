package de.mel.drive.tasks;

import de.mel.core.serialize.SerializableEntity;

public class AvailHashEntry implements SerializableEntity {
    private String hash;

    public AvailHashEntry(){}
    public AvailHashEntry(String hash){
        this.hash = hash;
    }

    public String getHash() {
        return hash;
    }
}
