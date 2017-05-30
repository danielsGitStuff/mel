package de.mein.auth;

import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 6/26/16.
 */
public interface MeinAuthAdmin {

    void start(MeinAuthService meinAuthService);

    void onChanged();
}
