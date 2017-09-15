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
import java.util.List;

import de.mein.android.drive.boot.AndroidDriveBootLoader;
import de.mein.android.service.AndroidService;
import de.mein.auth.service.MeinAuthService;

/**
 * Created by xor on 03.08.2017.
 */

public abstract class MeinActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private SparseArray<Deferred<Void, Void, Void>> permissionPromises = new SparseArray<>();


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
            System.out.println("MeinActivity.onServiceConnected: " + androidService.toString());
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

    protected abstract void onAndroidServiceAvailable(AndroidService androidService);

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        List<String> requestAgain = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                requestAgain.add(permissions[i]);
            }
        }
        Deferred<Void, Void, Void> deferred = permissionPromises.get(requestCode);
        if (requestAgain.size() == 0) {
            deferred.resolve(null);
        } else {
            Promise<Void, Void, Void> promise = annoyWithPermissions(requestAgain.toArray(new String[0]));
            promise.done(nil -> deferred.resolve(null));
        }
    }

    public Promise<Void, Void, Void> annoyWithPermissions(@NonNull String... permissions) {
        Deferred<Void, Void, Void> deferred = new DeferredObject<>();
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
            System.out.println(AndroidDriveBootLoader.class.getSimpleName() + ".askForPermission()?: " + request);
        } else {
            deferred.resolve(null);
        }
        return deferred;
    }
}
