package de.mel.auth;

import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthServiceImpl;

/**
 * Created by xor on 6/26/16.
 */
public interface MelAuthAdmin extends MelNotification.MelProgressListener {

    void start(MelAuthServiceImpl melAuthService);

    /**
     * when there is an event that the user has to be noticed of or has to take care of
     * @param melService
     * @param notification
     */
    void onNotificationFromService(IMelService melService, MelNotification notification);

    void onChanged();

    void shutDown();
}
