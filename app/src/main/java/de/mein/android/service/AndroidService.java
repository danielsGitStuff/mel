package de.mein.android.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;

import de.mein.auth.MeinAuthAdmin;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import androidx.core.app.NotificationCompat;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.AndroidInjector;
import de.mein.android.AndroidRegHandler;
import de.mein.android.MainActivity;
import de.mein.android.Notifier;
import de.mein.android.contacts.AndroidContactsBootloader;
import de.mein.android.drive.AndroidDriveBootloader;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.DriveSyncListener;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;


/**
 * puts Android specific workarounds in their places. eg: sqlite databasing works different than on a standard Linux.
 * <p>
 * Created by xor on 2/3/17.
 */
public class AndroidService extends Service {


    private DeferredObject<MeinAuthService, Exception, Void> startedDeferred = new DeferredObject<>();
    private static final int PERMANENT_REQUEST_CODE = 876;
    private final IBinder localBinder = new LocalBinder();
    private MeinAuthService meinAuthService;
    private MeinAuthSettings meinAuthSettings;
    private MeinBoot meinBoot;
    private NetworkChangeReceiver networkChangeReceiver;
    private PowerChangeReceiver powerChangeReceiver;
    private PowerManager.CommunicationsListener communicationsListener = new PowerManager.CommunicationsListener() {
        @Override
        public void onCommunicationsEnabled() {
            Lok.debug("restarting communications");
            meinAuthService.startUpCommunications().done(result -> {
                try {
                    for (Certificate certificate : meinAuthService.getCertificateManager().getAllCertificateDetails()) {
                        meinAuthService.connect(certificate.getId().v());
                    }
                    meinAuthService.discoverNetworkEnvironment();
                } catch (SqlQueriesException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).fail(result -> Lok.error("failed to restart MeinAuthWorker."));
        }

        @Override
        public void onCommunicationsDisabled() {
            Lok.debug("shutting down communications");
            meinAuthService.shutDownCommunications();
        }
    };
    private Intent permanentNotificationIntent;

    public Promise<MeinAuthService, Exception, Void> getStartedPromise() {
        return startedDeferred;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (meinAuthService != null)
            meinAuthService.shutDown();
        if (networkChangeReceiver != null)
            unregisterReceiver(networkChangeReceiver);
        if (powerChangeReceiver != null)
            unregisterReceiver(powerChangeReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Lok.debug("AndroidService.onBind");
        return localBinder;
    }

    public AndroidPowerManager getAndroidPowerManager() {
        if (meinAuthService.getPowerManager() == null)
            return null;
        return (AndroidPowerManager) meinAuthService.getPowerManager();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (meinAuthService == null) {
            Notifier.toast(getApplicationContext(), "booting auth service");
            RWLock lock = new RWLock();
            try {
                Promise<MeinAuthService, Exception, Void> bootedPromise = setup(null);
                bootedPromise.done(result -> {
                    N.r(() -> {
                        InputStream in = getAssets().open("de/mein/auth/update.server.cert");
                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = CertificateManager.loadX509CertificateFromStream(in);
                        meinAuthService.getCertificateManager().dev_SetUpdateCertificate(cert);
                    });
                    meinAuthService.addRegisterHandler(new AndroidRegHandler(this, meinAuthService));
                    meinAuthService.getPowerManager().addCommunicationListener(communicationsListener);
                    // listen for connectivity changes
                    IntentFilter conIntentFilter = new IntentFilter();
                    conIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                    networkChangeReceiver = new NetworkChangeReceiver(this);
                    this.registerReceiver(networkChangeReceiver, conIntentFilter);
                    // listen for charging state changes
                    IntentFilter powIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    powerChangeReceiver = new PowerChangeReceiver(this);
                    this.registerReceiver(powerChangeReceiver, powIntentFilter);
                    startedDeferred.resolve(meinAuthService);
                    // todo debug
                    //debugStuff();
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
            meinAuthService.addRegisterHandler(new IRegisterHandler() {
                @Override
                public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
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
                public void setup(MeinAuthAdmin meinAuthAdmin) {

                }
            });
            meinAuthService.addRegisteredHandler((meinAuthService1, registered) -> N.r(() -> {
                List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
                for (ServiceJoinServiceType service : services)
                    meinAuthService.getDatabaseManager().grant(service.getServiceId().v(), registered.getId().v());
            }));
            //meinAuthService.connect("192.168.1.105", 8888, 8889, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return meinAuthService != null;
    }

    public String getAndroidPath() {
        return getApplicationContext().getApplicationInfo().dataDir;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Lok.debug("AndroidService.onCreate()");
        createPermanentSticky();
        // configure MeinAuth
        File workingDir = new File(getAndroidPath() + File.separator + "meinauth.workingdir");
        workingDir.mkdirs();
        File settingsFile = new File(workingDir.getAbsolutePath() + File.separator + "meinauth.settings.json");
        try {
            if (settingsFile.exists()) {
                meinAuthSettings = (MeinAuthSettings) JsonSettings.load(settingsFile);
            } else {
                meinAuthSettings = MeinAuthSettings.createDefaultSettings();
                meinAuthSettings.setWorkingDirectory(workingDir)
                        .setName("Mel on Android")
                        .setVariant(MeinStrings.update.VARIANT_APK);
                meinAuthSettings.setJsonFile(settingsFile).save();
            }
        } catch (IOException | JsonDeserializationException | JsonSerializationException | IllegalAccessException e) {
            Lok.error("loading existing meinauth.settings failed :(");
            e.printStackTrace();
        }
        Lok.debug("AndroidService.onCreate");
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
        N.r(() -> meinAuthService.shutDown());
        stopSelf();
    }


    public MeinAuthSettings getMeinAuthSettings() {
        return meinAuthSettings;
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

    public Promise<MeinAuthService, Exception, Void> setup(DriveSyncListener clientSyncListener) throws Exception {
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
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
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
            public void setup(MeinAuthAdmin meinAuthAdmin) {
                // nothing to do
            }
        };
        // todo debug
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        lock.lockWrite();

        AndroidAdmin admin = new AndroidAdmin(getApplicationContext());
        AndroidPowerManager powerManager = new AndroidPowerManager(meinAuthSettings, (android.os.PowerManager) getSystemService(POWER_SERVICE));
        meinBoot = new MeinBoot(meinAuthSettings, powerManager, AndroidDriveBootloader.class, AndroidContactsBootloader.class);
//        meinBoot = new MeinBoot(meinAuthSettings, powerManager, AndroidDriveBootloader.class);
        meinBoot.addMeinAuthAdmin(admin);
        Promise<MeinAuthService, Exception, Void> promise = meinBoot.boot().done(meinAuthService -> {
            N.r(() -> {
                Lok.debug("AndroidService.booted");
                powerManager.setMeinAuthService(meinAuthService);
                AndroidService.this.meinAuthService = meinAuthService;
                // todo debug
                //meinAuthService.addRegisteredHandler(registeredHandler);
                Long t1 = meinAuthSettings.getWorkingDirectory().lastModified();
                Lok.debug(t1);
//                //todo debug
//                N.forEach(meinAuthService.getMeinServices(),iMeinService -> {
//                    MeinService meinService = meinAuthService.getMeinService(iMeinService.getUuid());
//                    meinService.resume();
//                });
            });
        });
        return promise;
        //lock.lockWrite();
        //lock.unlockWrite();
    }

//    @Override
//    public File getFilesDir() {
//        return new File("/data/data/" + getPackageName());
//    }

    private void android() throws IOException {
//        meinBoot.addBootLoaderClass(AndroidDriveBootloader.class);

        AndroidInjector.inject(this, getAssets());
    }

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
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
