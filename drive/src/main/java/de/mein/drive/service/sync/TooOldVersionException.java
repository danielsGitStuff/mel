package de.mein.drive.service.sync;

import de.mein.auth.data.ResponseException;
import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 5/23/17.
 */
public class TooOldVersionException extends ResponseException implements SerializableEntity {
    public TooOldVersionException(String msg) {
        super(msg);
    }

    public TooOldVersionException() {
        super();
    }
}
