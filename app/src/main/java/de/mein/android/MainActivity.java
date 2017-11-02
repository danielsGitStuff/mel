package de.mein.android;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.view.SubMenu;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.jdeferred.Promise;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.mein.R;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.controller.EditServiceController;
import de.mein.android.controller.InfoController;
import de.mein.android.controller.LogCatController;
import de.mein.android.controller.ConnectedController;
import de.mein.android.controller.SettingsController;
import de.mein.android.service.AndroidService;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.controller.NetworkDiscoveryController;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.MeinLogger;
import de.mein.auth.tools.N;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.bash.BashTools;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.android.controller.CreateServiceController;
import de.mein.android.controller.AccessController;
import de.mein.android.controller.GuiController;


public class MainActivity extends MeinActivity {
    private LinearLayout content;
    private Toolbar toolbar;
    private boolean mBound = false;
    private GuiController guiController;
    private NavigationView navigationView;
    private File driveDir;
    private ImageButton btnHelp;

    protected void startService() {
        if (androidService == null) {
            Intent intent = new Intent(getBaseContext(), AndroidService.class);
            startService(intent);
        } else {
            System.out.println("MainActivity.startService(): AndroidService already running");
        }
    }


    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        System.out.println("MainActivity.onAndroidServiceAvailable");
        if (androidService.getMeinAuthService().getSettings().getRedirectSysout()){
            MeinLogger.redirectSysOut(200,true);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.setApplicationContext(getApplicationContext());
        setContentView(R.layout.activity_main);
        content = findViewById(R.id.content);
        toolbar = findViewById(R.id.toolbar);
        btnHelp = toolbar.findViewById(R.id.btnHelp);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

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
        startService();
//        debugStuff3();
    }

    public static void showMessage(Context context, int message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
                .setTitle(R.string.titleHelp)
                .setPositiveButton(R.string.btnOk, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }


    public static void debugStuff3() {
        try {
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
//        annoyWithPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).done(result -> {
//            getContacts();
//        });
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
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
        if (this.guiController != null)
            EventBus.getDefault().unregister(this.guiController);
//        content.removeAllViews();
        toolbar.setTitle(guiController.getTitle());
        this.guiController = guiController;
        boolean offersHelp = guiController.getHelp() != null;
        btnHelp.setVisibility(offersHelp ? View.VISIBLE : View.INVISIBLE);
        EventBus.getDefault().register(guiController);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (guiController != null)
            EventBus.getDefault().unregister(guiController);
    }

    private void debugStuff() throws InterruptedException {
        MeinAuthService meinAuthService = androidService.getMeinAuthService();
        System.out.println(meinAuthService);
        Promise<MeinValidationProcess, Exception, Void> promise = meinAuthService.connect("10.0.2.2", 8888, 8889, true);
        promise.done(meinValidationProcess -> N.r(() -> {
            Request<MeinServicesPayload> gotAllowedServices = meinAuthService.getAllowedServices(meinValidationProcess.getConnectedId());
            gotAllowedServices.done(meinServicesPayload -> N.r(() -> {
                Promise<Void, Void, Void> permissionsGranted = MainActivity.this.annoyWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
                permissionsGranted.done(nil -> Threadder.runNoTryThread(() -> {
                    BashTools.init();
                    Thread.sleep(1000);
                    File download = new File(Environment.getExternalStorageDirectory(), "Download");
                    driveDir = new File(download, "drive");
                    N.r(() -> CertificateManager.deleteDirectory(driveDir));
                    driveDir.mkdirs();
                    driveDir.mkdir();
                    BashTools.mkdir(driveDir);
                    DriveCreateController driveCreateController = new DriveCreateController(meinAuthService);
                    //TestDirCreator.createTestDir(driveDir, " kek");
                    Promise<MeinDriveClientService, Exception, Void> serviceCreated = driveCreateController.createDriveClientService("drive.debug",
                            driveDir.getAbsolutePath(),
                            meinValidationProcess.getConnectedId(), meinServicesPayload.getServices().get(0).getUuid().v());
                    serviceCreated.done(meinDriveClientService -> {
                                N.r(() -> {
                                    System.out.println("successssss");
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
                System.out.println("erererere." + result);
            });

        })).fail(result -> {
            System.out.println("errrrr." + result);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showFirstStart();
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
                if (meinAuthService != null && meinAuthService.getSettings().getRedirectSysout()) {
                    MenuItem mLogCat = subMeinAuth.add(5, R.id.nav_logcat, 5, getText(R.string.logcatTitle));
                    mLogCat.setIcon(R.drawable.icon_fail);
                }
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
                            mService.setIcon(androidBootLoader.getMenuIcon());
                        }
                    }
                }
                MenuItem mNewService = subServices.add(5, R.id.nav_new_service, 0, getText(R.string.drawerCreateNewService));
                mNewService.setIcon(R.drawable.ic_menu_add);
                navigationView.refreshDrawableState();
                System.out.println();
            }));
        }
    }
}
