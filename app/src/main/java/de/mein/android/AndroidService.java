package de.mein.android;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
import de.mein.drive.serialization.TestDirCreator;
import de.mein.sql.RWLock;


/**
 * Created by xor on 2/3/17.
 */

public class AndroidService extends Service {

    private MeinAuthService meinAuthService;
    private final IBinder mBinder = new LocalBinder();
    private MeinAuthSettings meinAuthSettings;
    private MeinBoot meinBoot;

    public interface AndroidServiceObserver {
        void onMeinAuthStarted(MeinAuthService meinAuthService);
    }

    private AndroidServiceObserver observer;

    public void setObserver(AndroidServiceObserver observer) {
        this.observer = observer;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        AndroidService getService() {
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
        RWLock lock = new RWLock();
        try {
            System.out.println("AndroidService.onStartCommand.booting");
            setup(null);
            lock.lockWrite();
            meinAuthService.addRegisterHandler(new AndroidRegHandler(this, meinAuthService));
            observer.onMeinAuthStarted(meinAuthService);
        } catch (Exception e) {
            e.printStackTrace();
        }
        lock.unlockWrite();
        return super.onStartCommand(intent, flags, startId);
    }

    public boolean isRunning() {
        return meinAuthService != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // configure MeinAuth
        File workingDir = new File(getFilesDir().getAbsolutePath() + File.separator + "meinauth.workingdir");
        workingDir.mkdirs();
        File settingsFile = new File(workingDir.getAbsolutePath() + File.separator + "meinauth.settings.json");
        try {
            if (settingsFile.exists()) {
                meinAuthSettings = (MeinAuthSettings) JsonSettings.load(settingsFile);
            } else {
                meinAuthSettings = new MeinAuthSettings()
                        .setPort(8890)// default: 8888
                        .setDeliveryPort(8891) // default: 8889
                        .setBrotcastListenerPort(6699) // default: 9966
                        .setBrotcastPort(9966) // default: 9966
                        .setWorkingDirectory(workingDir)
                        .setName("MeinAuthOnAndroid")
                        .setGreeting("greeting1");
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

    @Override
    public File getFilesDir() {
        return new File("/data/data/" + getPackageName());
    }

    public void setup(DriveSyncListener clientSyncListener) throws Exception {
        android();

        //setup working directories & directories with test data
        RWLock lock = new RWLock();
        File testdir1 = new File(getFilesDir().getAbsolutePath() + File.separator + "testdir1");
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

        meinBoot = new MeinBoot(meinAuthSettings, AndroidDriveBootLoader.class);
        meinBoot.boot().done(meinAuthService -> {
            N.r(() -> {
                System.out.println("DriveFXTest.driveGui.1.booted");
                AndroidService.this.meinAuthService = meinAuthService;
                meinAuthService.addRegisteredHandler(registeredHandler);

                // setup the server Service
//                System.out.println("AndroidService.setup!!!1");
//                MeinDriveServerService serverService = new DriveCreateController(meinAuthService).createDriveServerService("server service", testdir1.getAbsolutePath());
//                System.out.println("AndroidService.setup11121321");


                //lock.unlockWrite();
                // connection goes here

                /*
                System.out.println("DriveFXTest.driveGui.2.booted");
                runner.runTry(() -> {
                    // connect first. this step will register
                    Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                    connectPromise.done(meinValidationProcess -> {
                        runner.runTry(() -> {
                            System.out.println("DriveFXTest.driveGui.connected");
                            // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                            Promise<MeinDriveClientService, Exception, Void> promise = new DriveCreateController(standAloneAuth2).createDriveClientService("client service", testdir2.getAbsolutePath(), 1l, serverService.getUuid());
                            promise.done(clientDriveService -> runner.runTry(() -> {
                                        System.out.println("DriveFXTest attempting first syncThisClient");
                                        clientSyncListener.testStructure.setMaClient(standAloneAuth2)
                                                .setMaServer(standAloneAuth1)
                                                .setClientDriveService(clientDriveService)
                                                .setServerDriveService(serverService)
                                                .setTestdir1(testdir1)
                                                .setTestdir2(testdir2);
                                        clientDriveService.setSyncListener(clientSyncListener);
                                        clientDriveService.syncThisClient();
                                    }
                            ));
                        });
                    });
                });*/
            });
        });
        //lock.lockWrite();
        lock.unlockWrite();
    }

    private void android() throws IOException {
        //meinBoot.addBootLoaderClass(AndroidDriveBootLoader.class);
        AndroidInjector.inject(this, getAssets());
    }

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
    }
}
