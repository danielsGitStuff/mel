package de.mein.android.controller;

import android.Manifest;
import android.content.Context;
import android.graphics.Color;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jdeferred.Promise;

import java.util.List;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.Notifier;
import de.mein.android.Tools;
import de.mein.android.service.AndroidPowerManager;
import de.mein.android.service.AndroidService;
import de.mein.android.view.PowerView;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;

/**
 * Created by xor on 2/22/17.
 */
public class InfoController extends GuiController implements PowerManager.IPowerStateListener {
    private TextView lblStatus, txtSSID, txtIP;
    private LinearLayout permissionReasonContainer;
    private PowerView powerView;

    public InfoController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_info);
        lblStatus = rootView.findViewById(R.id.lblStatus);
        powerView = rootView.findViewById(R.id.powerView);
        permissionReasonContainer = rootView.findViewById(R.id.permissionReasonContainer);
        Lok.debug("InfoController.InfoController");

        txtSSID = rootView.findViewById(R.id.txtSSID);
        txtIP = rootView.findViewById(R.id.txtIP);
        //if we are on android O or higher we should request the coarse location to get WiFi info.
        //else this message is just wasting space.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionReasonContainer.setVisibility(View.VISIBLE);
            permissionReasonContainer.setOnClickListener(v -> {
                Promise<Void, List<String>, Void> promise = activity.annoyWithPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
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
        AndroidPowerManager powerManager = (AndroidPowerManager) androidService.getMeinAuthService().getPowerManager();
        powerManager.addStateListener(this);
        updateGui();
    }

    private void updateGui() {
        activity.runOnUiThread(() -> {
            if (androidService.isRunning()) {
                lblStatus.setText("Running");
                lblStatus.setBackgroundColor(Color.parseColor("#ff99cc00"));
                powerView.setPowerManager((AndroidPowerManager) androidService.getMeinAuthService().getPowerManager());
                powerView.update();
            } else {
                lblStatus.setText("Stopped");
                lblStatus.setBackgroundColor(Color.parseColor("#ffcc0000"));
                powerView.disable();
            }
        });
    }

    @Override
    public void onDestroy() {
        AndroidPowerManager powerManager = (AndroidPowerManager) androidService.getMeinAuthService().getPowerManager();
        powerManager.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {
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
