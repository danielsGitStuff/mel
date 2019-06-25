package de.mein.android.boot;

import android.app.Activity;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import android.view.ViewGroup;

import de.mein.android.MainActivity;
import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceGuiController;
import de.mein.auth.MeinNotification;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 2/25/17.
 */

public interface AndroidBootLoader<T extends IMeinService> {

    void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceGuiController currentController);

    AndroidServiceGuiController inflateEmbeddedView(ViewGroup embedded, MainActivity activity, MeinAuthService meinAuthService, IMeinService runningInstance);

    String[] getPermissions();

    int getMenuIcon();

    NotificationCompat.Builder createNotificationBuilder(Context context, IMeinService meinService, MeinNotification meinNotification);

    Class createNotificationActivityClass(IMeinService meinService, MeinNotification meinNotification);
}
