package de.mein.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.view.SubMenu;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import org.jdeferred.Promise;

import java.io.File;
import java.util.List;

import de.mein.R;
import de.mein.android.controller.OthersController;
import de.mein.android.drive.boot.AndroidDriveBootLoader;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.controller.NetworkDiscoveryController;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.sql.SqlQueriesException;
import de.mein.android.controller.GeneralController;
import de.mein.android.controller.CreateServiceController;
import de.mein.android.controller.ApprovalController;
import de.mein.android.controller.GuiController;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, AndroidService.AndroidServiceObserver {

    private LinearLayout content;
    private Toolbar toolbar;
    private AndroidService androidService;
    private boolean mBound = false;
    private GuiController guiController;
    private NavigationView navigationView;

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
        setContentView(R.layout.activity_main);
        content = (LinearLayout) findViewById(R.id.content);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        showGeneral();
        System.out.println();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showOthers() {
        toolbar.setTitle("Other Instances");
        content.removeAllViews();
        View v = View.inflate(this, R.layout.content_others, content);
        guiController = new OthersController(this, androidService.getMeinAuthService(), v);
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
            guiController.onMeinAuthStarted(androidService.getMeinAuthService());
        try {
            debugStuff();
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
        promise.done(meinValidationProcess -> {
            N.r(() -> {
                Request<MeinServicesPayload> gotAllowedServices = meinAuthService.getAllowedServices(meinValidationProcess.getConnectedId());
                gotAllowedServices.done(meinServicesPayload -> {
                    N.r(() -> {
                        AndroidDriveBootLoader.askForPermission(this);
                        DriveCreateController driveCreateController = new DriveCreateController(meinAuthService);
                        File dir = new File("/sdcard/Download/drive");
                        File[] content = dir.listFiles();
                        if (content != null)
                            for (File f : content)
                                BashTools.rmRf(f);
                        dir.mkdirs();
                        Promise<MeinDriveClientService, Exception, Void> serviceCreated = driveCreateController.createDriveClientService("drive.debug",
                                dir.getAbsolutePath(),
                                meinValidationProcess.getConnectedId(), meinServicesPayload.getServices().get(0).getUuid().v());
                        serviceCreated.done(meinDriveClientService -> {
                                    N.r(() -> {
                                        System.out.println("successssss");
                                        meinDriveClientService.syncThisClient();
                                    });
                                }
                        );
                    });
                });

            });
        });
    }

    public void showMenuServices() throws SqlQueriesException {
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
                    View v = View.inflate(MainActivity.this, R.layout.content_create_service, content);
                    guiController = new EditServiceController(MainActivity.this, androidService.getMeinAuthService(), MainActivity.this, v, service, runningInstance);
                    return true;
                }
            });
        }
        MenuItem mNewService = subServices.add(5, R.id.nav_new_service, 0, "create new Service");
        mNewService.setIcon(R.drawable.ic_menu_add);
        navigationView.refreshDrawableState();
        System.out.println();
    }
}
