package de.mel.drive.service.sync;

import de.mel.auth.data.ResponseException;
import de.mel.core.serialize.SerializableEntity;

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
