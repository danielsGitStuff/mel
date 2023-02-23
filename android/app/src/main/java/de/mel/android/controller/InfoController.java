package de.mel.android.controller;

import android.Manifest;
import android.content.Context;
import android.graphics.Color;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.mel.AndroidPermission;
import de.mel.Lok;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.Tools;
import de.mel.android.permissions.PermissionsManager2;
import de.mel.android.service.AndroidPowerManager;
import de.mel.android.service.AndroidService;
import de.mel.android.view.PowerView;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;
import fun.with.Lists;

/**
 * Created by xor on 2/22/17.
 */
public class InfoController extends GuiController implements PowerManager.IPowerStateListener {
    private final AndroidPermission permissionLocation;
    private final AndroidPermission permissionNotifications;
    private TextView lblStatus, txtSSID, txtIP, txtPermissionNotification;
    private LinearLayout permissionReasonContainer;
    private PowerView powerView;

    PermissionsManager2 permissionsManager;


    public InfoController(MainActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_info);
        this.permissionLocation = new AndroidPermission(Manifest.permission.ACCESS_FINE_LOCATION, R.string.permissionExplainLocationTitle, R.string.permissionExplainLocationText);
        this.permissionNotifications = new AndroidPermission(Manifest.permission.POST_NOTIFICATIONS, R.string.permissionExplainNotificationsTitle, R.string.permissionExplainNotificationsText);
        this.permissionsManager = new PermissionsManager2(InfoController.this.activity, Lists.of(this.permissionLocation).get());
        lblStatus = rootView.findViewById(R.id.lblStatus);
        powerView = rootView.findViewById(R.id.powerView);
        txtPermissionNotification = rootView.findViewById(R.id.txtPermissionNotification);
        permissionReasonContainer = rootView.findViewById(R.id.permissionReasonContainer);
        Lok.debug("InfoController.InfoController");

        txtSSID = rootView.findViewById(R.id.txtSSID);
        txtIP = rootView.findViewById(R.id.txtIP);
        //if we are on android O or higher we should request the coarse location to get WiFi info.
        //else this message is just wasting space.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !this.permissionsManager.hasPermissions()) {
            permissionReasonContainer.setVisibility(View.VISIBLE);
            permissionReasonContainer.setOnClickListener(v -> {
                this.permissionsManager.startPermissionsActivity();
            });
        }
        showWifiInfo();
    }


    private void showWifiInfo() {
        N.r(() -> {
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

    private void showNotificationPermissionInfo() {
        if (permissionsManager.hasPermission(this.permissionNotifications)){
            txtPermissionNotification.setText(R.string.permissionStateGranted);
            txtPermissionNotification.setTextColor(this.activity.getResources().getColor(R.color.stateRunning, this.activity.getTheme()));
        }else {
            txtPermissionNotification.setText(R.string.permissionStateDenied);
            txtPermissionNotification.setTextColor(this.activity.getResources().getColor(R.color.stateStopped, this.activity.getTheme()));
        }
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
            if (this.permissionsManager.hasPermissions())
                permissionReasonContainer.setVisibility(View.GONE);
            this.showWifiInfo();
            this.showNotificationPermissionInfo();
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
