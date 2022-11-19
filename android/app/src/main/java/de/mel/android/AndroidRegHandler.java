package de.mel.android;

import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import de.mel.R;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelStrings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.sql.Hash;

/**
 * Created by xor on 3/6/17.
 */
public class AndroidRegHandler implements IRegisterHandler {
    public static final String REGBUNDLE_CERT_HASH = "r";
    // these are necessary to couple handler-activity without letting the android
    // gui thread execute network related stuff
    private static final Map<String, RegBundle> regBundles = new HashMap<>();
    private final Map<String, CertActivity> hashRegActivitiesMap = new HashMap<>();

    private final MelAuthService melAuthService;
    private final Context context;
    private NotificationManagerCompat notificationManager;

    public AndroidRegHandler(Context context, MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
        this.context = context;
        notificationManager = Notifier.createNotificationManager(Tools.getApplicationContext());
    }

    public static RegBundle retrieveRegBundle(String uuid) {
        RegBundle bundle = regBundles.get(uuid);
        return bundle;
    }

    @Override
    public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {

        try {
            String hash = Hash.sha256(certificate.getCertificate().v());
            final int requestCode = Tools.generateIntentRequestCode();
            RegBundle regBundle = new RegBundle()
                    .setListener(listener)
                    .setRequest(request)
                    .setMyCert(myCertificate)
                    .setRemoteCert(certificate)
                    .setAndroidRegHandler(this)
                    .setNotificationRequestCode(requestCode)
                    .setHash(hash);
            regBundles.put(regBundle.getHash(), regBundle);
            Intent intent = new Intent(context, CertActivity.class);
            intent.putExtra(MelStrings.Notifications.REQUEST_CODE, requestCode);
            intent.putExtra(REGBUNDLE_CERT_HASH, regBundle.getHash());
            regBundle.setNotificationIntent(intent);
            CharSequence title = Tools.getApplicationContext().getText(R.string.coupleNotificationCertTitle);
            CharSequence text = Tools.getApplicationContext().getText(R.string.coupleNotificationCertText);
            Notifier.pendingNotification(requestCode, intent, Notifier.CHANNEL_ID_SOUND, title, text, ":)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onRegistrationCompleted(Certificate partnerCertificate) {
        try {
            String hash = Hash.sha256(partnerCertificate.getCertificate().v());
            CertActivity activity = hashRegActivitiesMap.remove(hash);
            if (activity != null)
                activity.onRegistrationFinished();
            RegBundle bundle = regBundles.remove(hash);
            CharSequence title = Tools.getApplicationContext().getText(R.string.coupleNotificationCertTitleCompleted);
            CharSequence text = Tools.getApplicationContext().getText(R.string.coupleNotificationCertTextCompleted);
            Notifier.notification(bundle.getNotificationRequestCode(), R.drawable.icon_notification, Notifier.CHANNEL_ID_SILENT, title, text, ":)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRemoteRejected(Certificate partnerCertificate) {
        try {
            Notifier.toast(Tools.getApplicationContext(), R.string.coupleRemoteRejectToast);
            String hash = Hash.sha256(partnerCertificate.getCertificate().v());
            CertActivity certActivity = hashRegActivitiesMap.remove(hash);
            RegBundle bundle = regBundles.remove(hash);
            CharSequence title = Tools.getApplicationContext().getText(R.string.coupleNotificationCertTitleRemoteRejected);
            CharSequence text = Tools.getApplicationContext().getText(R.string.coupleNotificationCertTextRemoteRejected);
            Notifier.notification(bundle.getNotificationRequestCode(), R.drawable.icon_notification, Notifier.CHANNEL_ID_SILENT, title, text, ":)");
            if (certActivity != null)
                certActivity.onRemoteRejected();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLocallyRejected(Certificate partnerCertificate) {
        try {
            Notifier.toast(Tools.getApplicationContext(), R.string.coupleLocalRejectToast);
            String hash = Hash.sha256(partnerCertificate.getCertificate().v());
            CertActivity certActivity = hashRegActivitiesMap.remove(hash);
            certActivity.onLocallyRejected();
            RegBundle bundle = regBundles.remove(hash);
            Notifier.cancel(bundle.getNotificationIntent(), bundle.getNotificationRequestCode());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRemoteAccepted(Certificate partnerCertificate) {
        try {
            String hash = Hash.sha256(partnerCertificate.getCertificate().v());
            CertActivity certActivity = hashRegActivitiesMap.get(hash);
            if (certActivity != null)
                certActivity.onRemoteAccepted();
            regBundles.get(hash).flagRemoteAccepted();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLocallyAccepted(Certificate partnerCertificate) {
        try {
            String hash = Hash.sha256(partnerCertificate.getCertificate().v());
            CertActivity certActivity = hashRegActivitiesMap.get(hash);
            certActivity.onLocallyAccepted();
            RegBundle bundle = regBundles.get(hash);
            CharSequence title = Tools.getApplicationContext().getText(R.string.coupleNotificationRemotePendingTitle);
            CharSequence text = Tools.getApplicationContext().getText(R.string.coupleNotificationRemotePendingText);
            Notifier.pendingNotification(bundle.getNotificationRequestCode(), bundle.getNotificationIntent(), Notifier.CHANNEL_ID_SOUND, title, text, ":)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setup(MelAuthAdmin melAuthAdmin) {
        // nothing to do
    }

    public void addActivity(String certHash, CertActivity certActivity) {
        hashRegActivitiesMap.put(certHash, certActivity);
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


    public void removeActivityByBundle(RegBundle regBundle) {
        try {
            String hash = Hash.sha256(regBundle.getRemoteCert().getCertificate().v());
            hashRegActivitiesMap.remove(hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
