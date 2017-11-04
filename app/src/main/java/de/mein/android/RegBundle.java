package de.mein.android;

import java.io.IOException;

import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.sql.Hash;

/**
 * Created by xor on 3/6/17.
 */

public class RegBundle {
    private IRegisterHandlerListener listener;
    private MeinRequest request;
    private Certificate myCert;
    private Certificate remoteCert;
    private AndroidRegHandler androidRegHandler;
    private boolean remoteAccepted = false;
    private String hash;


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
        try {
            hash = Hash.sha256(remoteCert.getCertificate().v());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    public String getHash() {
        return hash;
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

    public RegBundle setAndroidRegHandler(AndroidRegHandler androidRegHandler) {
        this.androidRegHandler = androidRegHandler;
        return this;
    }

    public AndroidRegHandler getAndroidRegHandler() {
        return androidRegHandler;
    }

    public void flagRemoteAccepted() {
        this.remoteAccepted = true;
    }

    public boolean isFlaggedRemoteAccepted() {
        return remoteAccepted;
    }
}
