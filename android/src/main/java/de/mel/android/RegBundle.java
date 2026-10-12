package de.mel.android;

import android.content.Intent;

import java.io.IOException;

import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.sql.Hash;

/**
 * Created by xor on 3/6/17.
 */

public class RegBundle {
    private IRegisterHandlerListener listener;
    private MelRequest request;
    private Certificate myCert;
    private Certificate remoteCert;
    private AndroidRegHandler androidRegHandler;
    private boolean remoteAccepted = false;
    private String hash;
    private Integer notificationRequestCode;
    private Intent notificationIntent;


    public RegBundle setListener(IRegisterHandlerListener listener) {
        this.listener = listener;
        return this;
    }

    public RegBundle setNotificationIntent(Intent notificationIntent) {
        this.notificationIntent = notificationIntent;
        return this;
    }

    public Intent getNotificationIntent() {
        return notificationIntent;
    }

    public RegBundle setRequest(MelRequest request) {
        this.request = request;
        return this;
    }

    public RegBundle setNotificationRequestCode(Integer notificationRequestCode) {
        this.notificationRequestCode = notificationRequestCode;
        return this;
    }

    public Integer getNotificationRequestCode() {
        return notificationRequestCode;
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

    public MelRequest getRequest() {
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

    public RegBundle setHash(String hash) {
        this.hash = hash;
        return this;
    }
}
