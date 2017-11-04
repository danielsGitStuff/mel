package de.mein.android;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.mein.R;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.sql.Hash;

/**
 * Created by xor on 3/6/17.
 */
public class AndroidRegHandler implements IRegisterHandler {
    public static final String REGBUNDLE_UUID = "r";
    // these are necessary to couple handler-activity without letting the android
    // gui thread execute network related stuff
    private static final Map<String, RegBundle> regBundles = new HashMap<>();
    private final Map<String, CertActivity> hashRegActivitiesMap = new HashMap<>();

    private final MeinAuthService meinAuthService;
    private final Context context;
    private final NotificationManagerCompat notificationManager;

    public AndroidRegHandler(Context context, MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        this.context = context;
        notificationManager = Notifier.createNotificationManager(context);
    }

    public static RegBundle retrieveRegBundle(String uuid) {
        RegBundle bundle = regBundles.get(uuid);
        return bundle;
    }

    @Override
    public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
        String uuid = UUID.randomUUID().toString();
        RegBundle regBundle = new RegBundle()
                .setListener(listener)
                .setRequest(request)
                .setMyCert(myCertificate)
                .setRemoteCert(certificate)
                .setAndroidRegHandler(this);
        regBundles.put(uuid, regBundle);
//        Intent i = new Intent();
//        i.setClass(context, CertActivity.class);
//        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        i.putExtra(REGBUNDLE_UUID, uuid);
//        context.startActivity(i);

        int requestCode = Tools.generateIntentRequestCode();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND);
        Intent intent = new Intent(context, CertActivity.class);
        intent.putExtra(MeinStrings.Notifications.REQUEST_CODE, requestCode);
        intent.putExtra(REGBUNDLE_UUID, uuid);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = builder.setSmallIcon(R.drawable.icon_notification_2)
                .setContentTitle(context.getString(R.string.notificationCertTitle))
                .setContentText(context.getString(R.string.notificationCertText))
                .setContentIntent(pendingIntent)
                .build();
        notificationManager.notify(requestCode, notification);
    }


    @Override
    public void onRegistrationCompleted(Certificate partnerCertificate) {
        try {
            String hash = Hash.sha256(partnerCertificate.getCertificate().v());
            hashRegActivitiesMap.remove(hash).onRegistrationFinished();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addActivity(Certificate certificate, CertActivity certActivity) {
        try {
            String hash = Hash.sha256(certificate.getCertificate().v());
            hashRegActivitiesMap.put(hash, certActivity);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onUserAccepted(RegBundle regBundle) {
        Threadder.runNoTryThread(() -> {
            regBundle.getListener().onCertificateAccepted(regBundle.getRequest(), regBundle.getRemoteCert());
        });
    }

    public void onUserRejected(RegBundle regBundle) {
        Threadder.runNoTryThread(() -> {
            regBundle.getListener().onCertificateRejected(regBundle.getRequest(), regBundle.getRemoteCert());
        });
    }

    public static void removeRegBundle(String uuid) {
        regBundles.remove(uuid);
    }

    public void removeActivityByBundle(RegBundle regBundle) {
        try {
            String hash = Hash.sha256(regBundle.getRemoteCert().getCertificate().v());
            hashRegActivitiesMap.remove(hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
