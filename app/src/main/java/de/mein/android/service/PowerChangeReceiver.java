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

            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            /*
             * this does not ensure the device is actually charging.
             * though it is plugged it might drain battery.
             * I think this will work for the most devices most of the time.
             * the commented code below checks whether the device is really charging
             */
            boolean isPlugged = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
                    || plugged == BatteryManager.BATTERY_PLUGGED_AC
                    || plugged == BatteryManager.BATTERY_PLUGGED_USB
                    || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
//            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
//                    status == BatteryManager.BATTERY_STATUS_FULL;
            if (isPlugged)
                androidService.getMeinAuthService().getPowerManager().onPowerPlugged();
            else
                androidService.getMeinAuthService().getPowerManager().onPowerUnplugged();
        }
    }
}
