package de.mein.auth;

import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;

/**
 * Created by xor on 6/26/16.
 */
public interface MeinAuthAdmin {

    void start(MeinAuthService meinAuthService);

    void onMessageFromService(MeinService meinService, Object msgObject);

    void onChanged();

    void shutDown();
}
