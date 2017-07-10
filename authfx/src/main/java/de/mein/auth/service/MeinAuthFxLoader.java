package de.mein.auth.service;

import de.mein.auth.MeinAuthAdmin;

/**
 * Created by xor on 5/30/17.
 */
public class MeinAuthFxLoader implements MeinAuthAdmin {
    private MeinAuthAdminFX meinAuthAdminFX;
    private MeinAuthService meinAuthService;

    @Override
    public void start(MeinAuthService meinAuthService) {
        meinAuthAdminFX = MeinAuthAdminFX.load(meinAuthService);
        this.meinAuthService = meinAuthService;
    }

    @Override
    public void onMessageFromService(MeinService meinService, Object msgObject) {
        meinAuthAdminFX.onMessageFromService(meinService, msgObject);
    }

    @Override
    public void onChanged() {
        meinAuthAdminFX.onChanged();
    }

    @Override
    public void shutDown() {
        meinAuthAdminFX.shutDown();
    }
}
