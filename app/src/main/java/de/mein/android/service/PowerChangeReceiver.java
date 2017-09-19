package de.mein.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

/**
 * Created by xor on 9/19/17.
 */

public class PowerChangeReceiver extends BroadcastReceiver {
    private AndroidService androidService;

    public PowerChangeReceiver(AndroidService androidService) {
        this.androidService = androidService;
    }

    public PowerChangeReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (androidService != null) {
//            boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
//            boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
//            int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
            if (isCharging)
                androidService.getMeinAuthService().getPowerManager().onPowerPlugged();
            else
                androidService.getMeinAuthService().getPowerManager().onPowerUnplugged();
        }
    }
}
