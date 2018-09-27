package de.mein.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import de.mein.Lok;

/**
 * Created by xor on 9/18/17.
 */

public class NetworkChangeReceiver extends BroadcastReceiver {

    private AndroidService androidService;

    public NetworkChangeReceiver(AndroidService androidService) {
        this.androidService = androidService;
    }


    public NetworkChangeReceiver() {
    }

    private boolean connected = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        NetworkInfo info = extras.getParcelable("networkInfo");
        if (info != null && androidService != null) {
            NetworkInfo.State state = info.getState();
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager != null ? wifiManager.getConnectionInfo() : null;
            Lok.debug("state: " + state.name());
            boolean connectedNow = wifiInfo.getNetworkId() != -1;
            int ip = wifiInfo.getIpAddress();
            connectedNow = connectedNow && (ip != 0) && state.equals(NetworkInfo.State.CONNECTED);
            boolean hasChanged = false;
            if (connectedNow != connected) {
                connected = connectedNow;
                hasChanged = true;
            }
//            if (hasChanged) {
            if (connectedNow) {
                androidService.getMeinAuthService().getPowerManager().onCommunicationsEnabled();
            } else {
                androidService.getMeinAuthService().getPowerManager().onCommunicationsDisabled();
            }
//            }
        }
    }
}
