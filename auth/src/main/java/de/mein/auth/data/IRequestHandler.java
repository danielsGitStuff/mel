package de.mein.auth.data;

/**
 * Created by xor on 4/28/16.
 */
public interface IRequestHandler {
    void queueForResponse(MeinRequest request);
}
