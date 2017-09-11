package de.mein.android;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.view.SubMenu;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.jdeferred.Promise;

import java.io.File;
import java.util.List;

import de.mein.R;
import de.mein.android.controller.LogCatController;
import de.mein.android.controller.OthersController;
import de.mein.android.service.AndroidService;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.controller.NetworkDiscoveryController;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.MeinLogger;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.android.controller.GeneralController;
import de.mein.android.controller.CreateServiceController;
import de.mein.android.controller.ApprovalController;
import de.mein.android.controller.GuiController;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsEntry;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;

public class MainActivity extends MeinActivity {
    private LinearLayout content;
    private Toolbar toolbar;
    private AndroidService androidService;
    private boolean mBound = false;
    private GuiController guiController;
    private NavigationView navigationView;
    private File driveDir;

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AndroidService.LocalBinder localBinder = (AndroidService.LocalBinder) service;
            androidService = localBinder.getService();
            androidService.setObserver(MainActivity.this);
            if (guiController != null)
                guiController.onAndroidServiceBound(androidService);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            if (guiController != null)
                guiController.onAndroidServiceUnbound(androidService);
        }
    };


    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(getBaseContext(), AndroidService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //use custom logger
        MeinLogger.redirectSysOut(200);
        setContentView(R.layout.activity_main);
        content = findViewById(R.id.content);
        toolbar = findViewById(R.id.toolbar);
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
        showGeneral();

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
            showGeneral();
        } else if (id == R.id.nav_discover) {
            showDiscover();
        } else if (id == R.id.nav_approvals) {
            showApprovals();
        } else if (id == R.id.nav_new_service) {
            showCreateNewService();
        } else if (id == R.id.nav_others) {
            showOthers();
        } else if (id == R.id.nav_logcat) {
            showLogCat();
        }

        closeDrawer();
        return true;
    }

    private void closeDrawer() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    private void showOthers() {
        toolbar.setTitle("Other Instances");
        content.removeAllViews();
        View v = View.inflate(this, R.layout.content_others, content);
        guiController = new OthersController(this, androidService.getMeinAuthService(), v);
    }

    private void showLogCat() {
        toolbar.setTitle("LogCat");
        content.removeAllViews();
        View v = View.inflate(this, R.layout.content_logcat, content);
        guiController = new LogCatController(this, v);
    }

    private void showDiscover() {
        toolbar.setTitle("Discover");
        content.removeAllViews();
        View v = View.inflate(this, R.layout.content_discover, content);
        guiController = new NetworkDiscoveryController(this, androidService.getMeinAuthService(), v);
    }

    private void showCreateNewService() {
        content.removeAllViews();
        toolbar.setTitle("Create new Service");
        View v = View.inflate(this, R.layout.content_create_service, content);
        guiController = new CreateServiceController(androidService.getMeinAuthService(), this, v);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    private void showApprovals() {
        try {
            content.removeAllViews();
            toolbar.setTitle("Approvals");
            View v = View.inflate(this, R.layout.content_approvals, content);
            guiController = new ApprovalController(this, androidService.getMeinAuthService(), v);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showGeneral() {
        content.removeAllViews();
        toolbar.setTitle("General");
        View v = View.inflate(this, R.layout.content_general, content);
        guiController = new GeneralController(this, v, androidService);
    }

    @Override
    public void onMeinAuthStarted(MeinAuthService meinAuthService) {
        if (guiController != null)
            guiController.onMeinAuthStarted(meinAuthService);
        try {
            //debugStuff();
            showMenuServices();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
    }

    private void debugStuff() throws InterruptedException {
        MeinAuthService meinAuthService = androidService.getMeinAuthService();
        System.out.println(meinAuthService);
        Promise<MeinValidationProcess, Exception, Void> promise = meinAuthService.connect(null, "10.0.2.2", 8888, 8889, true);
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
                    TestDirCreator.createTestDir(driveDir, " kek");
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
                                            N.r(() -> {
                                                Order order = new Order();
                                                DriveDatabaseManager dbManager = meinDriveClientService.getDriveDatabaseManager();
                                                StageDao stageDao = dbManager.getStageDao();
                                                FsDao fsDao = dbManager.getFsDao();
                                                // pretend the server deleted "samedir"
                                                StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_SERVER, DriveStrings.STAGESET_STATUS_STAGED, null, null);
                                                FsDirectory root = fsDao.getRootDirectory();
                                                List<GenericFSEntry> content = fsDao.getContentByFsDirectory(root.getId().v());
                                                root.addContent(content);
                                                root.removeSubFsDirecoryByName("samedir");
                                                root.calcContentHash();
                                                Stage rootStage = new Stage().setStageSet(stageSet.getId().v())
                                                        .setContentHash(root.getContentHash().v())
                                                        .setFsId(root.getId().v())
                                                        .setIsDirectory(true)
                                                        .setName(root.getName().v())
                                                        .setOrder(order.ord())
                                                        .setVersion(root.getVersion().v())
                                                        .setDeleted(false);
                                                stageDao.insert(rootStage);
                                                GenericFSEntry sameDir = fsDao.getGenericSubByName(root.getId().v(), "samedir");
                                                Stage sameStage = GenericFSEntry.generic2Stage(sameDir, stageSet.getId().v());
                                                sameStage.setDeleted(true);
                                                stageDao.insert(sameStage);

                                                //pretend that we have added a file to the local "samedir"
                                                StageSet localStageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_FS, DriveStrings.STAGESET_STATUS_STAGED, null, null);
                                                FsDirectory fsDirectory = new FsDirectory(new File("bla"));
                                                fsDirectory.addDummyFsFile("same1.txt");
                                                fsDirectory.addDummyFsFile("same2.txt");
                                                fsDirectory.addDummyFsFile("same3.txt");
                                                fsDirectory.calcContentHash();
                                                sameDir = fsDao.getGenericSubByName(root.getId().v(), "samedir");
                                                sameStage = GenericFSEntry.generic2Stage(sameDir, stageSet.getId().v());
                                                sameStage.setContentHash(fsDirectory.getContentHash().v());
                                                sameStage.setStageSet(localStageSet.getId().v());
                                                stageDao.insert(sameStage);
                                                Stage same3 = new Stage().setContentHash("5")
                                                        .setDeleted(false)
                                                        .setFsParentId(sameStage.getFsId())
                                                        .setParentId(sameStage.getId())
                                                        .setIsDirectory(false)
                                                        .setName("same3.txt")
                                                        .setSynced(true)
                                                        .setStageSet(localStageSet.getId().v());
                                                stageDao.insert(same3);
                                                meinDriveClientService.addJob(new CommitJob());
                                            });
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

    public void showMenuServices() {
        runOnUiThread(() -> N.r(() -> {
            MeinAuthService meinAuthService = androidService.getMeinAuthService();
            Menu menu = navigationView.getMenu();
            menu.clear();
            SubMenu subMeinAuth = menu.addSubMenu("MeinAuth");
            //general
            MenuItem mGeneral = subMeinAuth.add(5, R.id.nav_general, 0, "General");
            mGeneral.setIcon(R.drawable.ic_menu_manage);
            MenuItem mOthers = subMeinAuth.add(5, R.id.nav_others, 1, "Other Instances");
            mOthers.setIcon(R.drawable.ic_menu_gallery);
            //discover ic_menu_search
            MenuItem mDiscover = subMeinAuth.add(5, R.id.nav_discover, 2, "Discover");
            mDiscover.setIcon(R.drawable.ic_menu_search);
            //approvals ic_menu_approval
            MenuItem mApprovals = subMeinAuth.add(5, R.id.nav_approvals, 3, "Approvals");
            mApprovals.setIcon(R.drawable.ic_menu_approval);
            //logcat
            MenuItem mLogCat = subMeinAuth.add(5, R.id.nav_logcat, 4, "LogCat");
            mLogCat.setIcon(R.drawable.ic_menu_slideshow);

            SubMenu subServices = menu.addSubMenu("Services");
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType service : services) {
                IMeinService runningInstance = meinAuthService.getMeinService(service.getUuid().v());
                MenuItem mService = subServices.add(service.getType().v() + "/" + service.getName().v());
                mService.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        content.removeAllViews();
                        toolbar.setTitle("Edit Service: " + service.getName().v());
                        ViewGroup v = (ViewGroup) View.inflate(MainActivity.this, R.layout.content_create_service, content);
                        guiController = new EditServiceController(MainActivity.this, androidService.getMeinAuthService(), MainActivity.this, v, service, runningInstance);
                        closeDrawer();
                        return true;
                    }
                });
            }
            MenuItem mNewService = subServices.add(5, R.id.nav_new_service, 0, "create new Service");
            mNewService.setIcon(R.drawable.ic_menu_add);
            navigationView.refreshDrawableState();
            System.out.println();
        }));

    }
}
