package de.mel.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import de.mel.Lok;

/**
 * Created by xor on 9/18/17.
 */

public class NetworkChangeListenerOlde extends BroadcastReceiver implements NetworkChangeListener {

    private AndroidService androidService;

    public NetworkChangeListenerOlde(AndroidService androidService) {
        this.androidService = androidService;
        IntentFilter conIntentFilter = new IntentFilter();
        conIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        androidService.registerReceiver(this, conIntentFilter);
    }


    public NetworkChangeListenerOlde() {
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
                androidService.getMelAuthService().getPowerManager().onCommunicationsEnabled();
            } else {
                androidService.getMelAuthService().getPowerManager().onCommunicationsDisabled();
            }
//            }
        }
    }

    @Override
    public void onDestroy() {
        androidService.unregisterReceiver(this);
    }
}
