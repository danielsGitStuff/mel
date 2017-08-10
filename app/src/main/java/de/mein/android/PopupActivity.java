package de.mein.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import de.mein.R;
import de.mein.android.service.AndroidService;
import de.mein.auth.MeinStrings;
import de.mein.auth.service.IMeinService;
import de.mein.auth.tools.N;
import de.mein.drive.data.DriveStrings;

public abstract class PopupActivity<T extends IMeinService> extends AppCompatActivity {

    protected Integer requestCode;
    protected AndroidService androidService;
    protected String serviceUuid;
    protected T service;
    protected N runner = new N(e -> {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG);
        toast.show();
    });
    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AndroidService.LocalBinder localBinder = (AndroidService.LocalBinder) binder;
            androidService = localBinder.getService();
            service = (T) androidService.getMeinAuthService().getMeinService(serviceUuid);
            PopupActivity.this.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

        }
    };

    protected abstract void onServiceConnected();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conflict_popup);
        Bundle extra = getIntent().getExtras();
        requestCode = extra.getInt(DriveStrings.Notifications.REQUEST_CODE);
        serviceUuid = extra.getString(MeinStrings.Notifications.SERVICE_UUID);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(getBaseContext(), AndroidService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
}
