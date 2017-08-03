package de.mein.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseArray;

import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import de.mein.android.drive.boot.AndroidDriveBootLoader;

/**
 * Created by xor on 03.08.2017.
 */

public abstract class MeinActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, AndroidService.AndroidServiceObserver {
    private SparseArray<Deferred<Void, Void, Void>> permissionPromises = new SparseArray<>();


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
            int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

            int id = new SecureRandom().nextInt(65536);
            id = id > 0 ? id : -1 * id;//make positive
            if (permission != PackageManager.PERMISSION_GRANTED) {
                permissionPromises.append(id, deferred);
                ActivityCompat.requestPermissions(this,
                        permissions,
                        id);
            } else {
                deferred.resolve(null);
            }
            System.out.println(AndroidDriveBootLoader.class.getSimpleName() + ".askForPermission(): " + permission);
        } else {
            deferred.resolve(null);
        }
        return deferred;
    }
}
