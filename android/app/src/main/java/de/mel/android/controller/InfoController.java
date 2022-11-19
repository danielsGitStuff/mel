package de.mel.android.controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jdeferred.Promise;

import java.util.List;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.Notifier;
import de.mel.android.Tools;
import de.mel.android.service.AndroidPowerManager;
import de.mel.android.service.AndroidService;
import de.mel.android.view.PowerView;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;

/**
 * Created by xor on 2/22/17.
 */
public class InfoController extends GuiController implements PowerManager.IPowerStateListener {
    private TextView lblStatus, txtSSID, txtIP;
    private LinearLayout permissionReasonContainer;
    private PowerView powerView;

    public InfoController(MainActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_info);
        lblStatus = rootView.findViewById(R.id.lblStatus);
        powerView = rootView.findViewById(R.id.powerView);
        permissionReasonContainer = rootView.findViewById(R.id.permissionReasonContainer);
        Lok.debug("InfoController.InfoController");

        txtSSID = rootView.findViewById(R.id.txtSSID);
        txtIP = rootView.findViewById(R.id.txtIP);
        //if we are on android O or higher we should request the coarse location to get WiFi info.
        //else this message is just wasting space.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.hasPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionReasonContainer.setVisibility(View.VISIBLE);
            permissionReasonContainer.setOnClickListener(v -> {
                Promise<Void, List<String>, Void> promise;
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                    promise = activity.annoyWithPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
                else
                    promise = activity.annoyWithPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
                promise.done(result -> {
                    Notifier.toast(activity, "granted");
                    permissionReasonContainer.setVisibility(View.GONE);
                    showInfo();
                }).fail(result -> Notifier.toast(activity, "denied"));
            });
        }
        showInfo();
    }

    private void showInfo() {
        N.r(() -> {


//            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 142);
//            }


            WifiManager wifiManager = (WifiManager) Tools.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo;
            wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                String ssid = wifiInfo.getSSID();
                txtSSID.setText(ssid);
            } else {
                txtSSID.setText(activity.getText(R.string.infoNotConnected));
            }
            String ip = Formatter.formatIpAddress(wifiInfo.getIpAddress());
            txtIP.setText(ip);
        });
    }


    @Override
    public Integer getTitle() {
        return R.string.infoTitle;
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        AndroidPowerManager powerManager = (AndroidPowerManager) androidService.getMelAuthService().getPowerManager();
        powerManager.addStateListener(this);
        updateGui();
    }

    private void updateGui() {
        activity.runOnUiThread(() -> {
            if (androidService != null && androidService.isRunning()) {
                lblStatus.setText(R.string.stateRunning);
                lblStatus.setBackgroundColor(Color.parseColor("#ff99cc00"));
                powerView.setPowerManager((AndroidPowerManager) androidService.getMelAuthService().getPowerManager());
                powerView.update();
            } else {
                lblStatus.setText(R.string.stateStopped);
                lblStatus.setBackgroundColor(Color.parseColor("#ffcc0000"));
                powerView.disable();
            }
        });
    }

    @Override
    public void onDestroy() {
        if (androidService != null) {
            AndroidPowerManager powerManager = (AndroidPowerManager) androidService.getMelAuthService().getPowerManager();
            powerManager.removeListener(this);
        }
        super.onDestroy();
    }

    @Override
    public void onAndroidServiceUnbound() {
        this.androidService = null;
    }

    @Override
    public Integer getHelp() {
        return R.string.infoHelp;
    }


    @Override
    public void onStateChanged(PowerManager powerManager) {
        updateGui();
    }
}
