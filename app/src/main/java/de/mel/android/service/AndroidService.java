package de.mel.android.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import de.mel.auth.MelAuthAdmin;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import androidx.core.app.NotificationCompat;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.AndroidInjector;
import de.mel.android.AndroidRegHandler;
import de.mel.android.MainActivity;
import de.mel.android.Notifier;
import de.mel.android.filesync.AndroidFileSyncBootloader;
import de.mel.auth.MelStrings;
import de.mel.auth.data.JsonSettings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.tools.N;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.filesync.FileSyncSyncListener;
import de.mel.sql.RWLock;
import de.mel.sql.SqlQueriesException;


/**
 * puts Android specific workarounds in their places. eg: sqlite databasing works different than on a standard Linux.
 * <p>
 * Created by xor on 2/3/17.
 */
public class AndroidService extends Service {

    private static AndroidService instance;

    public static AndroidService getInstance() {
        return instance;
    }

    private DeferredObject<MelAuthService, Exception, Void> startedDeferred = new DeferredObject<>();
    private static final int PERMANENT_REQUEST_CODE = 876;
    private final IBinder localBinder = new LocalBinder();
    private MelAuthService melAuthService;
    private MelAuthSettings melAuthSettings;
    private MelBoot melBoot;
    private NetworkChangeListener networkChangeReceiver;
    private PowerChangeReceiver powerChangeReceiver;
    private PowerManager.CommunicationsListener communicationsListener = new PowerManager.CommunicationsListener() {
        @Override
        public void onCommunicationsEnabled() {
            Lok.debug("restarting communications");
            melAuthService.startUpCommunications().done(result -> {
                try {
                    for (Certificate certificate : melAuthService.getCertificateManager().getAllCertificateDetails()) {
                        melAuthService.connect(certificate.getId().v());
                    }
                    melAuthService.discoverNetworkEnvironment();
                } catch (SqlQueriesException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).fail(result -> Lok.error("failed to restart MelAuthWorker."));
        }

        @Override
        public void onCommunicationsDisabled() {
            Lok.debug("shutting down communications");
            melAuthService.shutDownCommunications();
        }
    };
    private Intent permanentNotificationIntent;

    public Promise<MelAuthService, Exception, Void> getStartedPromise() {
        return startedDeferred;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AndroidService.instance = null;
        if (melAuthService != null)
            melAuthService.shutDown();
        if (networkChangeReceiver != null)
            networkChangeReceiver.onDestroy();
        if (powerChangeReceiver != null)
            unregisterReceiver(powerChangeReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Lok.debug("AndroidService.onBind");
        return localBinder;
    }

    public AndroidPowerManager getAndroidPowerManager() {
        if (melAuthService.getPowerManager() == null)
            return null;
        return (AndroidPowerManager) melAuthService.getPowerManager();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (melAuthService == null) {
            Notifier.toast(getApplicationContext(), "booting auth service");
            RWLock lock = new RWLock();
            try {
                Promise<MelAuthService, Exception, Void> bootedPromise = setup(null);
                bootedPromise.done(result -> {
                    N.r(() -> {
                        InputStream in = getAssets().open("de/mel/auth/update.server.cert");
                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = CertificateManager.loadX509CertificateFromStream(in);
                        melAuthService.getCertificateManager().dev_SetUpdateCertificate(cert);
                    });
                    melAuthService.addRegisterHandler(new AndroidRegHandler(this, melAuthService));
                    melAuthService.getPowerManager().addCommunicationListener(communicationsListener);
                    // listen for connectivity changes
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        networkChangeReceiver = new NetworkChangeListenerOlde(this);
                    } else {
                        networkChangeReceiver = new NetworkChangeLollipop(this);
                    }
                    // listen for charging state changes
                    IntentFilter powIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    powerChangeReceiver = new PowerChangeReceiver(this);
                    this.registerReceiver(powerChangeReceiver, powIntentFilter);
                    startedDeferred.resolve(melAuthService);
                });
                lock.lockWrite();

            } catch (Exception e) {
                e.printStackTrace();
                startedDeferred.reject(e);
            }
            lock.unlockWrite();
        }


        return Service.START_STICKY;
    }

    private void debugStuff() {
        try {
            melAuthService.addRegisterHandler(new IRegisterHandler() {
                @Override
                public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
                    listener.onCertificateAccepted(request, certificate);
                }

                @Override
                public void onRegistrationCompleted(Certificate partnerCertificate) {

                }

                @Override
                public void onRemoteRejected(Certificate partnerCertificate) {

                }

                @Override
                public void onLocallyRejected(Certificate partnerCertificate) {

                }

                @Override
                public void onRemoteAccepted(Certificate partnerCertificate) {

                }

                @Override
                public void onLocallyAccepted(Certificate partnerCertificate) {

                }

                @Override
                public void setup(MelAuthAdmin melAuthAdmin) {

                }
            });
            melAuthService.addRegisteredHandler((melAuthService1, registered) -> N.r(() -> {
                List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
                for (ServiceJoinServiceType service : services)
                    melAuthService.getDatabaseManager().grant(service.getServiceId().v(), registered.getId().v());
            }));
            //melAuthService.connect("192.168.1.105", 8888, 8889, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return melAuthService != null;
    }

    public String getAndroidPath() {
        return getApplicationContext().getApplicationInfo().dataDir;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Lok.debug("AndroidService.onCreate()");
        createPermanentSticky();
        // configure MelAuth
        File workingDir = new File(getAndroidPath() + File.separator + "melauth.workingdir");
        workingDir.mkdirs();
        File settingsFile = new File(workingDir.getAbsolutePath() + File.separator + "melauth.settings.json");
        try {
            if (settingsFile.exists()) {
                melAuthSettings = (MelAuthSettings) JsonSettings.load(settingsFile);
            } else {
                melAuthSettings = MelAuthSettings.createDefaultSettings();
                melAuthSettings.setWorkingDirectory(workingDir)
                        .setName("Mel on Android")
                        .setVariant(MelStrings.update.VARIANT_APK);
                melAuthSettings.setJsonFile(settingsFile).save();
            }
        } catch (IOException | JsonDeserializationException | JsonSerializationException | IllegalAccessException e) {
            Lok.error("loading existing melauth.settings failed :(");
            e.printStackTrace();
        }
        Lok.debug("AndroidService.onCreate");
        AndroidService.instance = this;
    }

    /**
     * create notification so hopefully android won't kill our beloved service
     */
    private void createPermanentSticky() {
        permanentNotificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, PERMANENT_REQUEST_CODE, permanentNotificationIntent, 0);
        Notifier.createNotificationManager(this);
        Notification notification = new NotificationCompat.Builder(this, Notifier.CHANNEL_ID_SILENT)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.permanentNotification))
                .setSmallIcon(R.drawable.icon_notification)
                .setContentIntent(pendingIntent)
                .setTicker("starting...")
                .build();
        startForeground(777, notification);
    }

    public void shutDown() {
        Notifier.cancel(permanentNotificationIntent, PERMANENT_REQUEST_CODE);
        this.stopForeground(true);
        N.r(() -> melAuthService.shutDown());
        stopSelf();
    }


    public MelAuthSettings getMelAuthSettings() {
        return melAuthSettings;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Lok.debug("AndroidService.onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRebind(Intent intent) {
        Lok.debug("AndroidService.onRebind");
        super.onRebind(intent);
    }

    @Override
    public void onTrimMemory(int level) {
        Lok.debug("AndroidService.onTrimMemory");
        super.onTrimMemory(level);
    }

    public Promise<MelAuthService, Exception, Void> setup(FileSyncSyncListener clientSyncListener) throws Exception {
        android();

        //setup working directories & directories with test data
        RWLock lock = new RWLock();
        File testdir1 = new File(getAndroidPath() + File.separator + "testdir1");
//        boolean ba = testdir1.mkdirs();
//        boolean bb = workingDir.mkdirs();
        Object asdasda = getResources();
        openFileOutput("durr", MODE_PRIVATE);
//        CertificateManager.deleteDirectory(workingDir);
        CertificateManager.deleteDirectory(testdir1);
        //TestDirCreator.createTestDir(testdir1);
        // we want accept all registration attempts automatically
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }

            @Override
            public void onRemoteRejected(Certificate partnerCertificate) {

            }

            @Override
            public void onLocallyRejected(Certificate partnerCertificate) {

            }

            @Override
            public void onRemoteAccepted(Certificate partnerCertificate) {

            }

            @Override
            public void onLocallyAccepted(Certificate partnerCertificate) {

            }

            @Override
            public void setup(MelAuthAdmin melAuthAdmin) {
                // nothing to do
            }
        };
        lock.lockWrite();

        AndroidAdmin admin = new AndroidAdmin(getApplicationContext());
        AndroidPowerManager powerManager = new AndroidPowerManager(melAuthSettings, (android.os.PowerManager) getSystemService(POWER_SERVICE));
//        melBoot = new MelBoot(melAuthSettings, powerManager, AndroidFileSyncBootloader.class, AndroidContactsBootloader.class, AndroidDumpBootloader.class);
        melBoot = new MelBoot(melAuthSettings, powerManager, AndroidFileSyncBootloader.class);
        melBoot.addMelAuthAdmin(admin);
        Promise<MelAuthService, Exception, Void> promise = melBoot.boot().done(melAuthService -> {
            N.r(() -> {
                Lok.debug("AndroidService.booted");
                powerManager.setMelAuthService(melAuthService);
                AndroidService.this.melAuthService = melAuthService;
            });
        });
        return promise;
        //lock.lockWrite();
        //lock.unlockWrite();
    }

    private void android() throws IOException {
        AndroidInjector.inject(this, getAssets());
    }

    public MelAuthService getMelAuthService() {
        return melAuthService;
    }


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public AndroidService getService() {
            // Return this instance of LocalService so clients can call public methods
            return AndroidService.this;
        }
    }

}
