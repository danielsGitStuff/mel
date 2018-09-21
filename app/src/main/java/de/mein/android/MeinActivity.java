package de.mein.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseArray;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mein.Lok;
import de.mein.android.service.AndroidService;
import de.mein.auth.MeinStrings;
import de.mein.auth.tools.N;

/**
 * Created by xor on 03.08.2017.
 */

public abstract class MeinActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private SparseArray<Deferred<Void, List<String>, Void>> permissionPromises = new SparseArray<>();
    private Map<Integer, MeinActivityLaunchResult> launchResultMap = new HashMap<>();
    private static Map<Integer, List<MeinActivityPayload>> launchPayloads = new HashMap<>();

    public void launchActivityForResult(Intent launchIntent, MeinActivityLaunchResult meinActivityLaunchResult) {
        final int id = Tools.generateIntentRequestCode();
        launchIntent.putExtra(MeinStrings.Notifications.REQUEST_CODE,id);
        launchResultMap.put(id, meinActivityLaunchResult);
        startActivityForResult(launchIntent, id);
    }

    public void launchActivityForResult(Intent launchIntent, MeinActivityLaunchResult meinActivityLaunchResult, MeinActivityPayload... payloads) {
        final int id = Tools.generateIntentRequestCode();
        launchResultMap.put(id, meinActivityLaunchResult);
        launchIntent.putExtra(MeinStrings.Notifications.REQUEST_CODE,id);
        if (payloads != null) {
            launchPayloads.put(id, new ArrayList<>());
            N.forEachAdv(payloads, (stoppable, index, meinActivityPayload) -> {
                launchPayloads.get(id).add(meinActivityPayload);
            });
        }
        startActivityForResult(launchIntent, id);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public static void onLaunchDestroyed(Integer requestCode) {
        launchPayloads.remove(requestCode);
    }

    public static List<MeinActivityPayload> getLaunchPayloads(Integer requestCode) {
        return launchPayloads.get(requestCode);
    }

    public interface MeinActivityLaunchResult {
        void onResultReceived(int resultCode, Intent result);
    }

    protected AndroidService androidService;

    protected void bindService() {
        Intent intent = new Intent(getBaseContext(), AndroidService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        bindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        unbindService(mConnection);
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AndroidService.LocalBinder localBinder = (AndroidService.LocalBinder) service;
            androidService = localBinder.getService();
            Lok.debug(".onServiceConnected: " + androidService.toString());
//            if (guiController != null)
//                guiController.onAndroidServiceBound(androidService);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
//            if (guiController != null)
//                guiController.onAndroidServiceUnbound(androidService);
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onServiceStarted(AndroidService androidService) {
        this.androidService = androidService;
        if (androidService != null) {
            onAndroidServiceAvailable(androidService);
        }
    }

    public AndroidService getAndroidService() {
        return androidService;
    }

    protected abstract void onAndroidServiceAvailable(AndroidService androidService);

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        List<String> deniedPermissions = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permissions[i]);
            }
        }
        Deferred<Void, List<String>, Void> deferred = permissionPromises.get(requestCode);
        if (deniedPermissions.size() == 0) {
            deferred.resolve(null);
        } else {
            deferred.reject(deniedPermissions);
        }
    }

    public boolean hasPermission(String permission) {
        int result = ContextCompat.checkSelfPermission(this, permission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasPermissions(String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission))
                return false;
        }
        return true;
    }


    /**
     * annoys the user one time with each given permission.
     *
     * @param permissions
     * @return Promise that resolves when all permission have been granted or will reject with all denied permissions.
     */
    public Promise<Void, List<String>, Void> annoyWithPermissions(@NonNull String... permissions) {
        Deferred<Void, List<String>, Void> deferred = new DeferredObject<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            boolean request = false;
            for (String permission : permissions) {
                int result = ContextCompat.checkSelfPermission(this, permission);
                if (result != PackageManager.PERMISSION_GRANTED) {
                    request = true;
                    break;
                }
            }
            int id = new SecureRandom().nextInt(65536);
            id = id > 0 ? id : -1 * id;//make positive
            if (request) {
                permissionPromises.append(id, deferred);
                ActivityCompat.requestPermissions(this,
                        permissions,
                        id);
            } else {
                deferred.resolve(null);
            }
            Lok.debug(".askForPermission()?: " + request);
        } else {
            deferred.resolve(null);
        }
        return deferred;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MeinActivityLaunchResult launchResult = launchResultMap.remove(requestCode);
        MeinActivity.launchPayloads.remove(requestCode);
        if (launchResult != null)
            launchResult.onResultReceived(resultCode, data);
    }
}
