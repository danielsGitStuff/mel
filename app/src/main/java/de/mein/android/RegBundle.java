package de.mein.android;

import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;

/**
 * Created by xor on 3/6/17.
 */

public class RegBundle {
    private IRegisterHandlerListener listener;
    private MeinRequest request;
    private Certificate myCert;
    private Certificate remoteCert;

    public RegBundle setListener(IRegisterHandlerListener listener) {
        this.listener = listener;
        return this;
    }

    public RegBundle setRequest(MeinRequest request) {
        this.request = request;
        return this;
    }

    public RegBundle setMyCert(Certificate myCert) {
        this.myCert = myCert;
        return this;
    }

    public RegBundle setRemoteCert(Certificate remoteCert) {
        this.remoteCert = remoteCert;
        return this;
    }

    public Certificate getMyCert() {
        return myCert;
    }

    public Certificate getRemoteCert() {
        return remoteCert;
    }

    public IRegisterHandlerListener getListener() {
        return listener;
    }

    public MeinRequest getRequest() {
        return request;
    }
}
