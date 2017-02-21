package mein.de.meindrive;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Binder;
import android.os.IBinder;
import android.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import de.mein.MeinInjector;
import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.tools.NoTryRunner;
import de.mein.drive.DriveInjector;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.AndroidDBConnection;
import de.mein.drive.AndroidDriveBootloader;
import de.mein.drive.watchdog.AndroidWatchdogListener;
import de.mein.sql.RWLock;
import de.mein.sql.con.AndroidSQLQueries;


/**
 * Created by xor on 2/3/17.
 */

public class AndroidService extends Service {

    private MeinAuthService meinAuthService;
    private final IBinder mBinder = new LocalBinder();

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
        } catch (Exception e) {
            e.printStackTrace();
        }
        lock.unlockWrite();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("AndroidService.onCreate");
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
        File workingDir = new File(getFilesDir().getAbsolutePath() + File.separator + "meinauth.workingdir");
        boolean ba = testdir1.mkdirs();
        boolean bb = workingDir.mkdirs();
        Object asdasda = getResources();
        openFileOutput("durr", MODE_PRIVATE);
        CertificateManager.deleteDirectory(workingDir);
        CertificateManager.deleteDirectory(testdir1);
        TestDirCreator.createTestDir(testdir1);
        // configure MeinAuth
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(9966)
                .setWorkingDirectory(workingDir).setName("MA1").setGreeting("greeting1");
        meinAuthService = new MeinAuthService(json1);
        // we want accept all registration attempts automatically
        IRegisterHandler allowRegisterHandler = (listener, request, myCertificate, certificate) -> {
            listener.onCertificateAccepted(request, certificate);
        };
        meinAuthService.addRegisterHandler(allowRegisterHandler);
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        meinAuthService.addRegisteredHandler(registeredHandler);
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot();
        MeinBoot boot2 = new MeinBoot();
        boot1.boot(meinAuthService).done(result -> {
            NoTryRunner.run(() -> {
                System.out.println("DriveFXTest.driveGui.1.booted");


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
        SQLiteStatement s;
        MeinBoot.addBootLoaderClass(AndroidDriveBootloader.class);
        AssetManager assetManager = getAssets();
        InputStream sqlInput = assetManager.open("sql.sql");
        InputStream driveSqlInput = assetManager.open("drive.sql");
        MeinInjector.setMeinAuthSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("sql.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
        MeinInjector.setSQLConnectionCreator(databaseManager -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(this, "meinauth", null, 1) {
                @Override
                public void onCreate(SQLiteDatabase db) {
                    System.out.println("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    System.out.println("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        MeinInjector.setBase64Encoder(bytes -> Base64.encode(bytes, Base64.NO_WRAP));
        MeinInjector.setBase64Decoder(string -> Base64.decode(string, Base64.NO_WRAP));
        DriveInjector.setSqlConnectionCreator((driveDatabaseManager, uuid) -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(this, "service." + uuid + "." + DriveStrings.DB_FILENAME, null, DriveStrings.DB_VERSION) {
                @Override
                public void onCreate(SQLiteDatabase db) {
                    System.out.println("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    System.out.println("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        DriveInjector.setDriveSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("drive.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
        DriveInjector.setWatchDogRunner(meinDriveService -> new AndroidWatchdogListener(meinDriveService));
        DriveInjector.setBinPath("/system/bin/sh");

    }

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
    }
}
