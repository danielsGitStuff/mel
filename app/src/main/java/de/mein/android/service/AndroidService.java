package de.mein.android.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationManagerCompat;

import org.greenrobot.eventbus.EventBus;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import de.mein.android.AndroidInjector;
import de.mein.android.AndroidRegHandler;
import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.DriveSyncListener;
import de.mein.android.drive.boot.AndroidDriveBootLoader;
import de.mein.sql.RWLock;


/**
 * Created by xor on 2/3/17.
 */

public class AndroidService extends Service {

    private MeinAuthService meinAuthService;
    private final IBinder mBinder = new LocalBinder();
    private MeinAuthSettings meinAuthSettings;
    private MeinBoot meinBoot;


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


    @Override
    public IBinder onBind(Intent intent) {
        System.out.println("AndroidService.onBind");
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (meinAuthService == null) {
            RWLock lock = new RWLock();
            try {
                System.out.println("AndroidService.onStartCommand.booting");
                Promise<MeinAuthService, Exception, Void> bootedPromise = setup(null);
                bootedPromise.done(result -> {
                    meinAuthService.addRegisterHandler(new AndroidRegHandler(this, meinAuthService));
                    EventBus.getDefault().postSticky(this);
                });
                lock.lockWrite();

            } catch (Exception e) {
                e.printStackTrace();
            }
            lock.unlockWrite();
        }
        return Service.START_STICKY;
        //return super.onStartCommand(intent, flags, startId);
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
        System.out.println("AndroidService.onCreate()");
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
                        .setName("MeinAuthOnAndroid");
                meinAuthSettings.setJsonFile(settingsFile).save();
            }
        } catch (IOException | JsonDeserializationException | JsonSerializationException | IllegalAccessException e) {
            System.err.println("loading existing meinauth.settings failed :(");
            e.printStackTrace();
        }
        System.out.println("AndroidService.onCreate");
    }

    public MeinAuthSettings getMeinAuthSettings() {
        return meinAuthSettings;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        System.out.println("AndroidService.onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRebind(Intent intent) {
        System.out.println("AndroidService.onRebind");
        super.onRebind(intent);
    }

    @Override
    public void onTrimMemory(int level) {
        System.out.println("AndroidService.onTrimMemory");
        super.onTrimMemory(level);
    }

//    @Override
//    public File getFilesDir() {
//        return new File("/data/data/" + getPackageName());
//    }

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
        };
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        lock.lockWrite();

        AndroidAdmin admin = new AndroidAdmin(getApplicationContext());
        meinBoot = new MeinBoot(meinAuthSettings, AndroidDriveBootLoader.class);
        meinBoot.addMeinAuthAdmin(admin);
        Promise<MeinAuthService, Exception, Void> promise = meinBoot.boot().done(meinAuthService -> {
            N.r(() -> {
                System.out.println("AndroidService.booted");
                AndroidService.this.meinAuthService = meinAuthService;
                meinAuthService.addRegisteredHandler(registeredHandler);
                Long t1 = meinAuthSettings.getWorkingDirectory().lastModified();
                System.out.println(t1);
            });
        });
        return promise;
        //lock.lockWrite();
        //lock.unlockWrite();
    }

    private void android() throws IOException {
        //meinBoot.addBootLoaderClass(AndroidDriveBootLoader.class);

        AndroidInjector.inject(this, getAssets());
    }

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
    }
}
