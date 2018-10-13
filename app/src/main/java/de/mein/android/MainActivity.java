package de.mein.android;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

import org.jdeferred.Promise;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import de.mein.Lok;
import de.mein.R;
import de.mein.Versioner;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.controller.AccessController;
import de.mein.android.controller.ConnectedController;
import de.mein.android.controller.CreateServiceController;
import de.mein.android.controller.EditServiceController;
import de.mein.android.controller.GuiController;
import de.mein.android.controller.InfoController;
import de.mein.android.controller.LogCatController;
import de.mein.android.controller.NetworkDiscoveryController;
import de.mein.android.controller.SettingsController;
import de.mein.android.controller.intro.IntroWrapper;
import de.mein.android.controller.intro.LoadingWrapper;
import de.mein.android.file.AndroidFileConfiguration;
import de.mein.android.file.SAFAccessor;
import de.mein.android.service.AndroidPowerManager;
import de.mein.android.service.AndroidService;
import de.mein.android.service.AndroidServiceBind;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.service.MeinDriveClientService;


public class MainActivity extends MeinActivity implements PowerManager.IPowerStateListener<AndroidPowerManager> {
    private static final String SHOW_INTRO = "shwntr";
    private LinearLayout content;
    private Toolbar toolbar;
    private boolean mBound = false;
    private GuiController guiController;
    private AndroidServiceBind serviceBind;
    private NavigationView navigationView;
    private AFile driveDir;
    private ImageButton btnHelp;
    private int stoppedColor;
    private int runningColor;
    private AndroidPowerManager powerManager;

    public static void showMessage(Context context, int message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
                .setTitle(R.string.titleHelp)
                .setPositiveButton(R.string.btnOk, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    protected void startService() {
        if (androidService == null) {
            Intent intent = new Intent(getBaseContext(), AndroidService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

        } else {
            Lok.debug("MainActivity.startService(): AndroidService already running");
        }
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        Lok.debug("MainActivity.onAndroidServiceAvailable");
        if (serviceBind != null)
            serviceBind.onAndroidServiceAvailable(androidService);
        this.powerManager = (AndroidPowerManager) androidService.getMeinAuthService().getPowerManager();
        powerManager.addStateListener(this);
    }

    private void dev() {
        Uri u = Uri.parse("content://com.android.externalstorage.documents/tree/1A16-1611%3Athisisexternal");
        Uri v = Uri.parse("/data/data/txt.txt");
        String a = u.getAuthority();
        String b = v.getAuthority();
        Lok.debug("ExampleUnitTest.uriTest " + a + " // " + b);
    }

    private void updateBarColor() {
        if (powerManager != null && toolbar != null) {
            boolean run = powerManager.runWhen(powerManager.isPowered(), powerManager.isWifi());
            int color = run ? runningColor : stoppedColor;
            runOnUiThread(() -> toolbar.setBackgroundColor(color));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //dev();
        Tools.init(this.getApplicationContext());
        runningColor = getResources().getColor(R.color.stateRunning);
        stoppedColor = getResources().getColor(R.color.stateDeactivated);
        Versioner.configure(() -> {
            try {
                InputStream in = MainActivity.this.getAssets().open("version.apk.txt");
                byte[] buffer = new byte[1024];
                StringBuilder b = new StringBuilder();
                int read;
                do {
                    read = in.read(buffer);
                    if (read > 0) {
                        b.append(new String(Arrays.copyOfRange(buffer, 0, read)));
                    }
                } while (read > 0);
                return b.toString();
            } catch (IOException e) {
                e.printStackTrace();
                return "could not read version";
            }
        });
        boolean timestamp = Tools.getSharedPreferences().getBoolean(PreferenceStrings.LOK_TIMESTAMP, true);
        int lines = Tools.getSharedPreferences().getInt(PreferenceStrings.LOK_LINE_COUNT, 0);
        Lok.setLokImpl(new AndroidLok().setPrintDebug(true).setup(lines, timestamp));
        SAFAccessor.setupExternalPath();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AFile.configure(new AndroidFileConfiguration(this.getApplicationContext()));
        } else {
            AFile.configure(new DefaultFileConfiguration());
        }
        //testFileWrite();
//        annoyWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).done(result -> {
//
//        });

        boolean showIntro = Tools.getSharedPreferences().getBoolean(MainActivity.SHOW_INTRO, true);
        if (showIntro) {
            showIntroGui();
        } else {
            if (androidService == null)
                showLoadingGui();
            else
                showNormalGui();
        }
        startService();
    }

    public void showLoadingGui() {
        LoadingWrapper loadingWrapper = new LoadingWrapper(this);
        if (guiController != null)
            guiController.onDestroy();
        if (serviceBind != null)
            serviceBind.onAndroidServiceUnbound();
        serviceBind = loadingWrapper;
        loadingWrapper.setIntroDoneListener(() -> {
            runOnUiThread(this::showNormalGui);
        });
        setContentView(loadingWrapper);
        if (androidService != null)
            serviceBind.onAndroidServiceAvailable(androidService);
    }

    public void showIntroGui() {
        IntroWrapper introWrapper = new IntroWrapper(this);
        if (guiController != null)
            guiController.onDestroy();
        if (serviceBind != null)
            serviceBind.onAndroidServiceUnbound();
        serviceBind = introWrapper;
        introWrapper.setIntroDoneListener(() -> {
            Tools.getSharedPreferences().edit().putBoolean(MainActivity.SHOW_INTRO, false).apply();
            showNormalGui();
        });
        setContentView(introWrapper);
        if (androidService != null)
            serviceBind.onAndroidServiceAvailable(androidService);
    }

    private void showNormalGui() {
        setContentView(R.layout.activity_main);
        content = findViewById(R.id.content);
        toolbar = findViewById(R.id.toolbar);
        btnHelp = toolbar.findViewById(R.id.btnHelp);
        setSupportActionBar(toolbar);
        // skip floating button stuff. it has no purpose for now
//        FloatingActionButton fab = findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                // refresh currently available service list
                N.r(() -> showMenuServices());
            }
        };
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        enableGuiController(new InfoController(this, content));
        // turn the help button into something useful
        btnHelp.setOnClickListener(v -> {
            if (guiController != null && guiController.getHelp() != null) {
                showMessage(this, guiController.getHelp());
            }
        });
        // show current app version
        try {
            PackageInfo packageInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = packageInfo.versionName;
            View navView = navigationView.getHeaderView(0);
            TextView textTitle = navView.findViewById(R.id.textTitle);
            String title = getText(R.string.app_name) + " " + version;
            textTitle.setText(title);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        updateBarColor();
    }

    public void debugStuff3() {
        try {
            Notifier.toast(this, "WARNING: DEBUG");
            ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            operationList.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build());


            // first and last names
            operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, "SSSSSSS")
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, "FFFFFFF")
                    .build());

            operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "09876543210")
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                    .build());
            operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)

                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.DATA, "abc@xyz.com")
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                    .build());

            try {
                //ContentProviderResult[] results = Tools.getApplicationContext().getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void debugStuff2() {
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Lok.debug("MainActivity.debugStuff2.DEBUG:ACTIVE");
        Notifier.toast(this, "WARNING: DEBUG");
        annoyWithPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
                , Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS).done(result -> {
            androidService.getMeinAuthService().addRegisterHandler(new IRegisterHandler() {
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
            });
            N.r(() -> androidService.getMeinAuthService().connect("10.0.2.2", 8888, 8889, true));
        });
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if (id == R.id.nav_general) {
            enableGuiController(new InfoController(this, content));
        } else if (id == R.id.nav_discover) {
            enableGuiController(new NetworkDiscoveryController(this, content));
        } else if (id == R.id.nav_access) {
            enableGuiController(new AccessController(this, content));
        } else if (id == R.id.nav_new_service) {
            enableGuiController(new CreateServiceController(this, content));
        } else if (id == R.id.nav_connected) {
            enableGuiController(new ConnectedController(this, content));
        } else if (id == R.id.nav_settings) {
            enableGuiController(new SettingsController(this, content));
        } else if (id == R.id.nav_logcat) {
            enableGuiController(new LogCatController(this, content));
        } else if (id == R.id.nav_exit) {
            Lok.debug("exiting...");
            if (androidService != null) {
                Lok.debug("shutting down android service...");
                androidService.shutDown();
            }
            Lok.debug("bye...");
            finish();
        }
        closeDrawer();
        return true;
    }

    private void closeDrawer() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    private void enableGuiController(GuiController guiController) {

//        content.removeAllViews();
        toolbar.setTitle(guiController.getTitle());
        if (this.guiController != null && this.guiController != guiController) {
            this.guiController.onDestroy();
        }
        if (serviceBind != null) {
            serviceBind.onAndroidServiceUnbound();
        }
        this.guiController = guiController;
        this.serviceBind = this.guiController;
        if (androidService != null)
            this.serviceBind.onAndroidServiceAvailable(androidService);
        boolean offersHelp = guiController.getHelp() != null;
        btnHelp.setVisibility(offersHelp ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (this.guiController != null) {
            this.guiController.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (powerManager != null)
            powerManager.addStateListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (powerManager != null)
            powerManager.removeListener(this);
        if (this.guiController!= null)
            this.guiController.onStop();
    }

    private void debugStuff() throws InterruptedException {
        Notifier.toast(this, "WARNING: DEBUG");
        MeinAuthService meinAuthService = androidService.getMeinAuthService();
        Lok.debug(meinAuthService);
        Promise<MeinValidationProcess, Exception, Void> promise = meinAuthService.connect("10.0.2.2", 8888, 8889, true);
        promise.done(meinValidationProcess -> N.r(() -> {
            Request<MeinServicesPayload> gotAllowedServices = meinAuthService.getAllowedServices(meinValidationProcess.getConnectedId());
            gotAllowedServices.done(meinServicesPayload -> N.r(() -> {
                Promise<Void, List<String>, Void> permissionsGranted = MainActivity.this.annoyWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
                permissionsGranted.done(nil -> Threadder.runNoTryThread(() -> {
                    BashTools.init();
                    Thread.sleep(1000);
                    AFile download = AFile.instance(AFile.instance(Environment.getExternalStorageDirectory()), "Download");
                    driveDir = AFile.instance(download, "drive");
                    N.r(() -> CertificateManager.deleteDirectory(driveDir));
                    driveDir.mkdirs();
                    BashTools.mkdir(driveDir);
                    DriveCreateController driveCreateController = new DriveCreateController(meinAuthService);
                    //TestDirCreator.createTestDir(driveDir, " kek");
                    Promise<MeinDriveClientService, Exception, Void> serviceCreated = driveCreateController.createDriveClientService("drive.debug",
                            driveDir,
                            meinValidationProcess.getConnectedId(), meinServicesPayload.getServices().get(0).getUuid().v(), DriveSettings.DEFAULT_WASTEBIN_RATIO, DriveSettings.DEFAULT_WASTEBIN_MAXDAYS);
                    serviceCreated.done(meinDriveClientService -> {
                                N.r(() -> {
                                    Lok.debug("successssss");
                                    DriveSyncListener syncListener = new DriveSyncListener() {
                                        @Override
                                        public void onSyncFailed() {

                                        }

                                        @Override
                                        public void onTransfersDone() {
//                                            N.r(() -> {
//                                                Order order = new Order();
//                                                DriveDatabaseManager dbManager = meinDriveClientService.getDriveDatabaseManager();
//                                                StageDao stageDao = dbManager.getStageDao();
//                                                FsDao fsDao = dbManager.getFsDao();
//                                                // pretend the server deleted "samedir"
//                                                StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_SERVER, DriveStrings.STAGESET_STATUS_STAGED, null, null);
//                                                FsDirectory root = fsDao.getRootDirectory();
//                                                List<GenericFSEntry> content = fsDao.getContentByFsDirectory(root.getId().v());
//                                                root.addContent(content);
//                                                root.removeSubFsDirecoryByName("samedir");
//                                                root.calcContentHash();
//                                                Stage rootStage = new Stage().setStageSet(stageSet.getId().v())
//                                                        .setContentHash(root.getContentHash().v())
//                                                        .setFsId(root.getId().v())
//                                                        .setIsDirectory(true)
//                                                        .setName(root.getAccountType().v())
//                                                        .setOrder(order.ord())
//                                                        .setVersion(root.getVersion().v())
//                                                        .setDeleted(false);
//                                                stageDao.insert(rootStage);
//                                                GenericFSEntry sameDir = fsDao.getGenericSubByName(root.getId().v(), "samedir");
//                                                Stage sameStage = GenericFSEntry.generic2Stage(sameDir, stageSet.getId().v());
//                                                sameStage.setDeleted(true);
//                                                stageDao.insert(sameStage);
//
//                                                //pretend that we have added a file to the local "samedir"
//                                                StageSet localStageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_FS, DriveStrings.STAGESET_STATUS_STAGED, null, null);
//                                                FsDirectory fsDirectory = new FsDirectory(new File("bla"));
//                                                fsDirectory.addDummyFsFile("same1.txt");
//                                                fsDirectory.addDummyFsFile("same2.txt");
//                                                fsDirectory.addDummyFsFile("same3.txt");
//                                                fsDirectory.calcContentHash();
//                                                sameDir = fsDao.getGenericSubByName(root.getId().v(), "samedir");
//                                                sameStage = GenericFSEntry.generic2Stage(sameDir, stageSet.getId().v());
//                                                sameStage.setContentHash(fsDirectory.getContentHash().v());
//                                                sameStage.setStageSet(localStageSet.getId().v());
//                                                stageDao.insert(sameStage);
//                                                Stage same3 = new Stage().setContentHash("5")
//                                                        .setDeleted(false)
//                                                        .setFsParentId(sameStage.getFsId())
//                                                        .setParentId(sameStage.getId())
//                                                        .setIsDirectory(false)
//                                                        .setName("same3.txt")
//                                                        .setSynced(true)
//                                                        .setStageSet(localStageSet.getId().v());
//                                                stageDao.insert(same3);
//                                                meinDriveClientService.addJob(new CommitJob());
//                                            });
                                        }

                                        @Override
                                        public void onSyncDoneImpl() {

                                        }
                                    };
                                    //meinDriveClientService.setSyncListener(syncListener);
                                    meinDriveClientService.syncThisClient();
                                });
                            }
                    );
                }));
            })).fail(result -> {
                Lok.debug("erererere." + result);
            });

        })).fail(result -> {
            Lok.debug("errrrr." + result);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void showFirstStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean showIntro = prefs.getBoolean(getString(R.string.showIntro), true);
        if (showIntro) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(getString(R.string.showIntro), false);
            editor.apply();
            showMessage(this, R.string.firstStart);
        }
    }

    public void showMenuServices() {
        if (androidService != null) {
            runOnUiThread(() -> N.r(() -> {
                //hide keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(this.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(this.content.getWindowToken(), 0);
                MeinAuthService meinAuthService = androidService.getMeinAuthService();
                Menu menu = navigationView.getMenu();
                menu.clear();
                SubMenu subMeinAuth = menu.addSubMenu(getText(R.string.drawerTitle));
                //general
                MenuItem mGeneral = subMeinAuth.add(5, R.id.nav_general, 0, getText(R.string.infoTitle));
                mGeneral.setIcon(R.drawable.icon_info);
                MenuItem mOthers = subMeinAuth.add(5, R.id.nav_connected, 1, getText(R.string.connectedTitle));
                mOthers.setIcon(R.drawable.icon_connected);
                //discover ic_menu_search
                MenuItem mDiscover = subMeinAuth.add(5, R.id.nav_discover, 2, getText(R.string.discoverTitle));
                mDiscover.setIcon(R.drawable.icon_wifi);
                //approvals ic_menu_approval
                MenuItem mApprovals = subMeinAuth.add(5, R.id.nav_access, 3, getText(R.string.accessTitle));
                mApprovals.setIcon(R.drawable.icon_permissions);
                //settings
                MenuItem mSettings = subMeinAuth.add(5, R.id.nav_settings, 4, R.string.settingsTitle);
                mSettings.setIcon(R.drawable.icon_settings);
                //logcat
                int order = 5;
                if (Lok.isLineStorageActive()) {
                    MenuItem mLogCat = subMeinAuth.add(5, R.id.nav_logcat, order++, getText(R.string.logcatTitle));
                    mLogCat.setIcon(R.drawable.icon_fail);
                }
                MenuItem mExit = subMeinAuth.add(5, R.id.nav_exit, order, getText(R.string.exit));
                mExit.setIcon(R.drawable.icon_exit);
                SubMenu subServices = menu.addSubMenu(getText(R.string.drawerServices));
                if (meinAuthService != null) {
                    List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
                    for (ServiceJoinServiceType service : services) {
                        IMeinService runningInstance = meinAuthService.getMeinService(service.getUuid().v());
                        MenuItem mService = subServices.add(service.getName().v());
                        mService.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                content.removeAllViews();
                                toolbar.setTitle("Edit Service: " + service.getName().v());
                                enableGuiController(new EditServiceController(MainActivity.this, content, service, runningInstance));
                                closeDrawer();
                                return true;
                            }
                        });
                        // get icon from AndroidBootLoader
                        BootLoader bootLoader = meinAuthService.getMeinBoot().getBootLoader(service.getType().v());
                        if (bootLoader instanceof AndroidBootLoader) {
                            AndroidBootLoader androidBootLoader = (AndroidBootLoader) bootLoader;
                            Drawable drawableIcon = MainActivity.this.getResources().getDrawable(androidBootLoader.getMenuIcon());
                            if (!service.getActive().v()) {
                                drawableIcon.mutate();
                                int tint = MainActivity.this.getResources().getColor(R.color.tintDeactive);
                                drawableIcon.setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
                            }
                            mService.setIcon(drawableIcon);
                        }
                    }
                }
                MenuItem mNewService = subServices.add(5, R.id.nav_new_service, 0, getText(R.string.drawerCreateNewService));
                mNewService.setIcon(R.drawable.ic_menu_add);
                navigationView.refreshDrawableState();
                Lok.debug("");
            }));
        }
    }

    /**
     * debug function to figure out if opening an {@link java.io.InputStream} on a file which is currently written to will throw exceptions
     */
    private void testFileWrite() {
        FileOutputStream fos = null;
        FileInputStream fis = null;
        try {
            File file = new File(SAFAccessor.getExternalSDPath() + "target");
            AFile target = AFile.instance(file);
            String path = target.getAbsolutePath();
            target.createNewFile();
            fos = target.outputStream();
            fos.write("test".getBytes());
            AFile duplicate = AFile.instance(file);
            fis = duplicate.inputStream();
            byte[] bytes = new byte[4];
            fis.read(bytes);
            String result = new String(bytes);
            Lok.debug(result);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onStateChanged(PowerManager powerManager) {
        updateBarColor();
    }
}
