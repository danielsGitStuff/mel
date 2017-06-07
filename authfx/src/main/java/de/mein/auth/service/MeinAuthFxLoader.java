package de.mein.auth.service;

import de.mein.auth.MeinAuthAdmin;

/**
 * Created by xor on 5/30/17.
 */
public class MeinAuthFxLoader implements MeinAuthAdmin {
    private MeinAuthFX meinAuthFX;
    private MeinAuthService meinAuthService;

    @Override
    public void start(MeinAuthService meinAuthService) {
        meinAuthFX = MeinAuthFX.load(meinAuthService);
        this.meinAuthService = meinAuthService;
    }

    @Override
    public void onMessageFromService(MeinService meinService, Object msgObject) {
        meinAuthFX.onMessageFromService(meinService, msgObject);
    }

    @Override
    public void onChanged() {
        meinAuthFX.onChanged();
    }

    @Override
    public void shutDown() {
        meinAuthFX.shutDown();
    }
}
