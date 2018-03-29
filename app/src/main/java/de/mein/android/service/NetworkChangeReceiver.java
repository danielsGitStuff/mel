package de.mein.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

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

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        NetworkInfo info = extras.getParcelable("networkInfo");
        if (info != null && androidService != null) {
            NetworkInfo.State state = info.getState();
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager != null ? wifiManager.getConnectionInfo() : null;
            System.out.println("NetworkChangeReceiver.onReceive.state: " + state.name());
            if (state.equals(NetworkInfo.State.CONNECTED)) {
                androidService.getMeinAuthService().getPowerManager().onCommunicationsEnabled();
            } else if (state.equals(NetworkInfo.State.DISCONNECTED)) {
                androidService.getMeinAuthService().getPowerManager().onCommunicationsDisabled();
            } else if (state.equals(NetworkInfo.State.DISCONNECTING)) {

            }
        }
    }
}
