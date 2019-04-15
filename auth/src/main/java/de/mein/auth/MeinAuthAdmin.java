package de.mein.auth;

import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 6/26/16.
 */
public interface MeinAuthAdmin extends MeinNotification.MeinProgressListener {

    void start(MeinAuthService meinAuthService);

    /**
     * when there is an event that the user has to be noticed of or has to take care of
     * @param meinService
     * @param notification
     */
    void onNotificationFromService(IMeinService meinService, MeinNotification notification);

    void onChanged();

    void shutDown();
}
