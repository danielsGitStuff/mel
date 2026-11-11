package de.mel.android;

import android.Manifest;
import androidx.activity.OnBackPressedCallback;
import android.content.ContentProviderOperation;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import de.mel.AndroidPermission;
import de.mel.android.file.chooserdialog.DirectoryChooserDialog;

import fun.with.interfaces.actions.ActionConsumer;
import fun.with.promise.Promise;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mel.BuildConfig;
import de.mel.Lok;
import de.mel.R;
import de.mel.Versioner;
import de.mel.android.boot.AndroidBootLoader;
import de.mel.android.controller.AccessController;
import de.mel.android.controller.ConnectedController;
import de.mel.android.controller.CreateServiceController;
import de.mel.android.controller.EditServiceController;
import de.mel.android.controller.GuiController;
import de.mel.android.controller.InfoController;
import de.mel.android.controller.LogCatController;
import de.mel.android.controller.PermissionsGrantedListener;
import de.mel.android.controller.SettingsController;
import de.mel.android.controller.intro.IntroWrapper;
import de.mel.android.controller.intro.LoadingWrapper;
import de.mel.android.service.AndroidPowerManager;
import de.mel.android.service.AndroidService;
import de.mel.android.service.AndroidServiceBind;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.NetworkDiscoveryController;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;


public class MainActivity extends MelActivity implements PowerManager.IPowerStateListener<AndroidPowerManager> {
    private static final String SHOW_INTRO = "shwntr";
    private static final String KEY_CURRENT_NAV_ID = "current_nav_id";
    private static final String KEY_CURRENT_SERVICE_UUID = "current_service_uuid";
    private static final String KEY_BACKSTACK_NAV_IDS = "backstack_nav_ids";
    private static final String KEY_BACKSTACK_SERVICE_UUIDS = "backstack_service_uuids";
    private static final int NAV_EDIT_SERVICE = -1;

    public static class NavDestination {
        public final int navId;
        public final String serviceUuid;

        public NavDestination(int navId) {
            this(navId, null);
        }

        public NavDestination(int navId, String serviceUuid) {
            this.navId = navId;
            this.serviceUuid = serviceUuid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NavDestination that = (NavDestination) o;
            return navId == that.navId && java.util.Objects.equals(serviceUuid, that.serviceUuid);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(navId, serviceUuid);
        }
    }

    private NavDestination currentDestination = new NavDestination(R.id.nav_general);
    private final java.util.ArrayDeque<NavDestination> backStack = new java.util.ArrayDeque<>();

    private LinearLayout content;
    private Toolbar toolbar;
    private boolean mBound = false;
    private GuiController guiController;
    private AndroidServiceBind serviceBind;
    private NavigationView navigationView;
    private ImageButton btnHelp;
    private int stoppedColor;
    private int runningColor;


    /**
     * show a dialog with ok and cancel option
     *
     * @param message
     * @param btnOkListener
     * @param btnAbortListener
     */
    public void showMessageBinary(int title, int message, DialogInterface.OnClickListener btnOkListener, DialogInterface.OnClickListener btnAbortListener) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message)
                    .setTitle(title)
                    .setPositiveButton(R.string.btnDownload, btnOkListener)
                    .setNegativeButton(R.string.btnCancel, btnAbortListener);
            builder.create().show();
        });
    }

    protected void startService() {
        if (getAndroidService() == null) {
            Intent intent = new Intent(getBaseContext(), AndroidService.class);
            startForegroundService(intent);
        } else {
            Lok.debug("MainActivity.startService(): AndroidService already running");
        }
    }

    private AndroidPowerManager getPowerManager() {
        if (getAndroidService() == null)
            return null;
        return getAndroidService().getAndroidPowerManager();
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        Lok.debug("MainActivity.onAndroidServiceAvailable");
        if (serviceBind != null)
            serviceBind.onAndroidServiceAvailable(androidService);
        AndroidPowerManager powerManager = getPowerManager();
        if (powerManager != null) {
            powerManager.addStateListener(this);
        }
        showMenuServices();
    }

    private void dev() {
        Lok.warn("DEV DEV DEV DEV DEV START");
        try {
            String[] arr = new String[1];
            arr[0] = new File("asd.txt").getAbsolutePath();
            DirectoryChooserDialog.FilesActivityPayload payload = new DirectoryChooserDialog.FilesActivityPayload(List.of(arr));
            String json = SerializableEntitySerializer.serialize(payload);
            SerializableEntity des = SerializableEntityDeserializer.deserialize(json);
            Lok.debug("JSON: " + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Lok.warn("DEV DEV DEV DEV DEV END");
    }

    private void updateBarColor() {
        AndroidPowerManager powerManager = getPowerManager();
        if (powerManager != null && toolbar != null) {
            boolean run = powerManager.runWhenOverride(powerManager.isPowered(), powerManager.isWifi());
            int color = run ? runningColor : stoppedColor;
            runOnUiThread(() -> toolbar.setBackgroundColor(color));
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            int navId = savedInstanceState.getInt(KEY_CURRENT_NAV_ID, R.id.nav_general);
            String serviceUuid = savedInstanceState.getString(KEY_CURRENT_SERVICE_UUID);
            currentDestination = new NavDestination(navId, serviceUuid);

            ArrayList<Integer> navIds = savedInstanceState.getIntegerArrayList(KEY_BACKSTACK_NAV_IDS);
            ArrayList<String> serviceUuids = savedInstanceState.getStringArrayList(KEY_BACKSTACK_SERVICE_UUIDS);
            if (navIds != null && serviceUuids != null && navIds.size() == serviceUuids.size()) {
                backStack.clear();
                for (int i = navIds.size() - 1; i >= 0; i--) {
                    backStack.push(new NavDestination(navIds.get(i), serviceUuids.get(i)));
                }
            }
        }
        registerBackHandler();
//        dev();
        Tools.init(this.getApplicationContext());
        runningColor = getResources().getColor(R.color.stateRunning);
        stoppedColor = getResources().getColor(R.color.stateDeactivated);
        Versioner.configure(new Versioner.BuildReader() {

            @Override
            public void readProperties() throws IOException {
                commit = BuildConfig.BUILD_COMMIT;
                variant = BuildConfig.BUILD_VARIANT;
                version = BuildConfig.BUILD_VERSION;
            }
        });
        AndroidLok.setupDbLok(this);

        boolean showIntro = Tools.getSharedPreferences().getBoolean(MainActivity.SHOW_INTRO, true);
        if (showIntro) {
            showIntroGui();
        } else {
            if (getAndroidService() == null)
                showLoadingGui();
            else
                showNormalGui();
        }
        startService();
//        dev();
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
        if (getAndroidService() != null)
            serviceBind.onAndroidServiceAvailable(getAndroidService());
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
        if (getAndroidService() != null)
            serviceBind.onAndroidServiceAvailable(getAndroidService());
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
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null && content != null) {
                    imm.hideSoftInputFromWindow(content.getWindowToken(), 0);
                }
                showMenuServices();
            }
        };
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                v.getPaddingLeft(),
                insets.top,
                v.getPaddingRight(),
                insets.bottom
            );
            return windowInsets;
        });

        if (content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    insets.bottom
                );
                return windowInsets;
            });
        }
        navigateTo(currentDestination, false);
        // turn the help button into something useful
        btnHelp.setOnClickListener(v -> {
            if (guiController != null && guiController.getHelp() != null) {
                showMessage(R.string.titleHelp, guiController.getHelp());
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


    /**
     * Closes the navigation drawer on back, otherwise pops back stack or lets the system handle it.
     */
    private void registerBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                DrawerLayout drawer = findViewById(R.id.drawer_layout);
                if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else if (!backStack.isEmpty()) {
                    NavDestination prev = backStack.pop();
                    navigateTo(prev, false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
        return false;
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

    public void showInfo() {
        backStack.clear();
        navigateTo(new NavDestination(R.id.nav_general), false);
    }

    public void navigateTo(NavDestination dest, boolean addToBackStack) {
        if (dest == null || content == null) return;

        if (dest.navId == R.id.nav_general) {
            backStack.clear();
        } else if (addToBackStack && currentDestination != null && !currentDestination.equals(dest)) {
            backStack.push(currentDestination);
        }
        currentDestination = dest;

        GuiController newController = createControllerFor(dest);
        if (newController != null) {
            enableGuiController(newController);
            updateDrawerSelection(dest.navId);
        }
    }

    private GuiController createControllerFor(NavDestination dest) {
        if (dest.navId == R.id.nav_general) {
            return new InfoController(this, content);
        } else if (dest.navId == R.id.nav_discover) {
            return new NetworkDiscoveryController(this, content);
        } else if (dest.navId == R.id.nav_access) {
            return new AccessController(this, content);
        } else if (dest.navId == R.id.nav_new_service) {
            return new CreateServiceController(this, content);
        } else if (dest.navId == R.id.nav_connected) {
            return new ConnectedController(this, content);
        } else if (dest.navId == R.id.nav_settings) {
            return new SettingsController(this, content);
        } else if (dest.navId == R.id.nav_logcat) {
            return new LogCatController(this, content);
        } else if (dest.navId == NAV_EDIT_SERVICE && dest.serviceUuid != null) {
            if (getAndroidService() != null && getAndroidService().getMelAuthService() != null) {
                MelAuthService melAuthService = getAndroidService().getMelAuthService();
                try {
                    List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
                    for (ServiceJoinServiceType s : services) {
                        if (dest.serviceUuid.equals(s.getUuid().v())) {
                            IMelService runningInstance = melAuthService.getMelService(s.getUuid().v());
                            toolbar.setTitle("Edit Service: " + s.getName().v());
                            return new EditServiceController(this, content, s, runningInstance);
                        }
                    }
                } catch (Exception e) {
                    Lok.error("Failed to load service for edit: " + dest.serviceUuid);
                    e.printStackTrace();
                }
            }
            currentDestination = new NavDestination(R.id.nav_general);
            return new InfoController(this, content);
        }
        return new InfoController(this, content);
    }

    private void updateDrawerSelection(int navId) {
        if (navigationView != null && navId > 0) {
            navigationView.setCheckedItem(navId);
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if (id == R.id.nav_exit) {
            Lok.debug("exiting...");
            if (getAndroidService() != null) {
                Lok.debug("shutting down android service...");
                getAndroidService().shutDown();
            }
            Lok.debug("bye...");
            finish();
            return true;
        }
        if (currentDestination != null && currentDestination.navId == id) {
            closeDrawer();
            return true;
        }
        navigateTo(new NavDestination(id), true);
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
        if (getAndroidService() != null)
            this.serviceBind.onAndroidServiceAvailable(getAndroidService());
        boolean offersHelp = guiController.getHelp() != null;
        btnHelp.setVisibility(offersHelp ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentDestination != null) {
            outState.putInt(KEY_CURRENT_NAV_ID, currentDestination.navId);
            outState.putString(KEY_CURRENT_SERVICE_UUID, currentDestination.serviceUuid);
        }
        ArrayList<Integer> navIds = new ArrayList<>();
        ArrayList<String> serviceUuids = new ArrayList<>();
        for (NavDestination dest : backStack) {
            navIds.add(dest.navId);
            serviceUuids.add(dest.serviceUuid);
        }
        outState.putIntegerArrayList(KEY_BACKSTACK_NAV_IDS, navIds);
        outState.putStringArrayList(KEY_BACKSTACK_SERVICE_UUIDS, serviceUuids);
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
        AndroidPowerManager powerManager = getPowerManager();
        if (powerManager != null)
            powerManager.addStateListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        AndroidPowerManager powerManager = getPowerManager();
        if (powerManager != null)
            powerManager.removeListener(this);
        if (this.guiController != null)
            this.guiController.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBarColor();
        if (this.guiController != null) {
            this.guiController.onResume();
        }
    }

    public void showMenuServices() {
        if (getAndroidService() != null) {
            MelAuthService melAuthService = getAndroidService().getMelAuthService();
            if (melAuthService == null) return;
            N.r(() -> {
                List<ServiceJoinServiceType> services;
                try {
                    services = melAuthService.getDatabaseManager().getAllServices();
                } catch (Exception e) {
                    services = List.of();
                }
                final List<ServiceJoinServiceType> finalServices = services;
                runOnUiThread(() -> {
                    if (navigationView!= null){
                        Menu menu = navigationView.getMenu();
                        MenuItem servicesItem = menu.findItem(R.id.menu_services);
                        if (servicesItem != null && servicesItem.getSubMenu() != null) {
                            SubMenu subServices = servicesItem.getSubMenu();
                            subServices.clear();
                            for (ServiceJoinServiceType service : finalServices) {
                                MenuItem mService = subServices.add(service.getName().v());
                                mService.setOnMenuItemClickListener(item -> {
                                    navigateTo(new NavDestination(NAV_EDIT_SERVICE, service.getUuid().v()), true);
                                    closeDrawer();
                                    return true;
                                });
                                Bootloader bootLoader = null;
                                try {
                                    bootLoader = melAuthService.getMelBoot().getBootLoader(service.getType().v());
                                } catch (Exception ignored) {
                                }
                                if (bootLoader instanceof AndroidBootLoader) {
                                    AndroidBootLoader androidBootLoader = (AndroidBootLoader) bootLoader;
                                    Drawable drawableIcon = androidx.core.content.ContextCompat.getDrawable(MainActivity.this, androidBootLoader.getMenuIcon());
                                    if (drawableIcon != null) {
                                        if (!service.getActive().v()) {
                                            drawableIcon = drawableIcon.mutate();
                                            int tint = androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.tintDeactive);
                                            drawableIcon.setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
                                        }
                                        mService.setIcon(drawableIcon);
                                    }
                                }
                            }
                            MenuItem mNewService = subServices.add(5, R.id.nav_new_service, 0, getText(R.string.drawerCreateNewService));
                            mNewService.setIcon(R.drawable.ic_menu_add);
                        }
                        MenuItem mLogCat = menu.findItem(R.id.nav_logcat);
                        if (mLogCat != null) {
                            mLogCat.setVisible(Lok.isLineStorageActive());
                        }
                    }
                });
            });
        }
    }

    @Override
    public void onStateChanged(PowerManager powerManager) {
        updateBarColor();
    }


    /**
     * ask user for permission and do thing upon approval or denial. calls onSuccess when permissions granted or permissions had already been granted.
     *
     * @param permissions                permissions you want
     * @param permissionsGrantedListener
     * @param permissionTitle
     * @param permissionText
     * @param onSuccess
     * @param onFail
     */
    public void askUserForPermissions(String[] permissions
            , PermissionsGrantedListener permissionsGrantedListener
            , int permissionTitle
            , int permissionText
            , Runnable onSuccess
            , ActionConsumer<List<String>> onFail) {
        if (!hasPermissions(permissions)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(permissionText)
                    .setTitle(permissionTitle)
                    .setPositiveButton(R.string.btnOk, (dialog, which) -> {
                        Promise<Void, List<String>> permissionsPromise = annoyWithPermissions(permissions);
                        permissionsPromise.onDone(nil -> {
                            if (permissionsGrantedListener != null)
                                permissionsGrantedListener.onPermissionsGranted();
                            onSuccess.run();
                        }).onError(result -> {
                            Set<String> failedPermissions = new HashSet<>(result);
                            failedPermissions.remove(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
                            result = new ArrayList<>(failedPermissions);
                            if (result.isEmpty())
                                onSuccess.run();
                            else
                                onFail.accept(result);
                        });
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        } else {
            onSuccess.run();
        }
    }

    /**
     * ask user for permission and do thing upon approval or denial. calls onSuccess when permissions granted or permissions had already been granted.
     *
     * @param onSuccess
     * @param onFail
     */
    public void askUserForPermissionToManageAllFiles(
            Runnable onSuccess,
            ActionConsumer<Void> onFail) {
        if (!Environment.isExternalStorageManager()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.permissionExplainAllText)
                    .setTitle(R.string.permissionExplainAllText)
                    .setPositiveButton(R.string.btnOk, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        } else {
            onSuccess.run();
        }
    }
}
