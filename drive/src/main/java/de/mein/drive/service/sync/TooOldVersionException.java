package de.mein.drive.service.sync;

import de.mein.auth.data.ResponseException;
import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 5/23/17.
 */
public class TooOldVersionException extends ResponseException implements SerializableEntity {
    private Long newVersion;

    public TooOldVersionException(String msg, long newVersion) {
        super(msg);
        this.newVersion = newVersion;
    }

    public Long getNewVersion() {
        return newVersion;
    }

    public TooOldVersionException(){

    }
}
