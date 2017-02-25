package de.mein.drive;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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

import de.mein.auth.boot.MeinBoot;
import de.mein.auth.service.MeinAuthService;
import mein.de.meindrive.R;
import de.mein.drive.controller.GeneralController;
import de.mein.drive.controller.CreateServiceController;
import de.mein.drive.controller.ApprovalController;
import de.mein.drive.controller.GuiController;
import de.mein.drive.boot.AndroidDriveBootLoader;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, AndroidService.AndroidServiceObserver {

    private LinearLayout content;
    private Toolbar toolbar;
    private AndroidService androidService;
    private boolean mBound = false;
    private GuiController guiController;

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
            if (guiController!=null)
                guiController.onAndroidServiceBound(androidService);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, AndroidService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MeinBoot.addBootLoaderClass(de.mein.drive.boot.AndroidDriveBootLoader.class);
        setContentView(R.layout.activity_main);
        content = (LinearLayout) findViewById(R.id.content);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        System.out.println(MeinBoot.defaultWorkingDir2.getAbsolutePath());
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
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        showGeneral();
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
            toolbar.setTitle("Discover");
        } else if (id == R.id.nav_approvals) {
            showApprovals();
        } else if (id == R.id.nav_new_service) {
            showCreateNewService();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showCreateNewService() {
        content.removeAllViews();
        toolbar.setTitle("Create new Service");
        View v = View.inflate(this, R.layout.content_create_service, content);
        guiController = new CreateServiceController(androidService.getMeinAuthService(), v);
    }

    private void showApprovals() {
        try {
            content.removeAllViews();
            toolbar.setTitle("Approvals");
            View v = View.inflate(this, R.layout.content_approvals, content);
            guiController = new ApprovalController(androidService.getMeinAuthService(), v);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showGeneral() {
        content.removeAllViews();
        toolbar.setTitle("General");
        View v = View.inflate(this, R.layout.content_general, content);
        guiController = new GeneralController(v, androidService);
    }

    @Override
    public void onMeinAuthStarted(MeinAuthService meinAuthService) {
        if (guiController!=null)
            guiController.onMeinAuthStarted(androidService.getMeinAuthService());
    }
}
