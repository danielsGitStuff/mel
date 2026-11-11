package de.mel.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.mel.Lok;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            if (AndroidService.getInstance() != null && AndroidService.getInstance().isRunning()) {
                return;
            }
            Lok.debug("BootReceiver: starting AndroidService after reboot");
            Intent serviceIntent = new Intent(context, AndroidService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
