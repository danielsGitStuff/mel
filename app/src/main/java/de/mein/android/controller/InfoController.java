package de.mein.android.controller;

import android.content.Context;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v4.content.ContextCompat;
import android.text.format.Formatter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.Threadder;
import de.mein.android.Tools;
import de.mein.android.service.AndroidService;
import de.mein.auth.tools.N;

/**
 * Created by xor on 2/22/17.
 */
public class InfoController extends GuiController {
    private TextView lblStatus;

    public InfoController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_info);
        lblStatus = rootView.findViewById(R.id.lblStatus);
        System.out.println("InfoController.InfoController");

        TextView txtSSID = rootView.findViewById(R.id.txtSSID);
        TextView txtIP = rootView.findViewById(R.id.txtIP);
        N.r(() -> {
            WifiManager wifiManager = (WifiManager) Tools.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo;
            wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo!= null && wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                String ssid = wifiInfo.getSSID();
                txtSSID.setText(ssid);
            } else {
                txtSSID.setText(activity.getText(R.string.infoNotConnected));
            }
            String ip = Formatter.formatIpAddress(wifiInfo.getIpAddress());
            txtIP.setText(ip);
//            Threadder.runNoTryThread(() -> {
//                String keks = InetAddress.
////            byte[] myIPAddress = BigInteger.valueOf(wifiInfo.getIpAddress()).toByteArray();
////            // you must reverse the byte array before conversion. Use Apache's commons library
////            Collections.reverse(Arrays.asList(myIPAddress));
////            InetAddress myInetIP = InetAddress.getByAddress(myIPAddress);
////            String myIP = myInetIP.getHostAddress();
//                System.out.println();
//            });

        });

    }


    @Override
    public Integer getTitle() {
        return R.string.infoTitle;
    }

    @Override
    public void onAndroidServiceAvailable() {
        activity.runOnUiThread(() -> {
            if (androidService.isRunning()) {
                lblStatus.setText("Running");
                lblStatus.setBackgroundColor(Color.parseColor("#ff99cc00"));
            } else {
                lblStatus.setText("Stopped");
                lblStatus.setBackgroundColor(Color.parseColor("#ffcc0000"));
            }
        });
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {
        this.androidService = null;
    }

    @Override
    public Integer getHelp() {
        return R.string.infoHelp;
    }


}
