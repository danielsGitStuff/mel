package de.mel.auth.service;

import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelNotification;

/**
 * Created by xor on 5/30/17.
 */
public class MelAuthFxLoader implements MelAuthAdmin {
    private MelAuthAdminFX melAuthAdminFX;
    private MelAuthServiceImpl melAuthService;

    @Override
    public void start(MelAuthServiceImpl melAuthService) {
        melAuthAdminFX = MelAuthAdminFX.load(melAuthService);
        this.melAuthService = melAuthService;
    }

    public MelAuthAdminFX getMelAuthAdminFX() {
        return melAuthAdminFX;
    }

    @Override
    public void onNotificationFromService(IMelService melService, MelNotification notification) {
        melAuthAdminFX.onNotificationFromService(melService, notification);
    }

    @Override
    public void onChanged() {
        melAuthAdminFX.onChanged();
    }

    @Override
    public void shutDown() {
        melAuthAdminFX.shutDown();
    }

    @Override
    public void onProgress(MelNotification notification, int max, int current, boolean indeterminate) {
        melAuthAdminFX.onProgress(notification, max, current, indeterminate);
    }

    @Override
    public void onCancel(MelNotification notification) {
        melAuthAdminFX.onCancel(notification);
    }

    @Override
    public void onFinish(MelNotification notification) {

    }
}
