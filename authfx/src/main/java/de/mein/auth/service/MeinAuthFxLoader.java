package de.mein.auth.service;

import de.mein.auth.MeinAuthAdmin;

/**
 * Created by xor on 5/30/17.
 */
public class MeinAuthFxLoader implements MeinAuthAdmin {
    private MeinAuthFX meinAuthFX;

    @Override
    public void start(MeinAuthService meinAuthService) {
        meinAuthFX = MeinAuthFX.load(meinAuthService);
    }

    @Override
    public void onChanged() {
        meinAuthFX.onChanged();
    }
}
