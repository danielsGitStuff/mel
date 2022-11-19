package de.mel.android.controller;

import android.view.ViewGroup;

import de.mel.Lok;
import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.service.AndroidPowerManager;
import de.mel.android.service.AndroidService;

/**
 * Extends {@link GuiController} with the {@link AndroidPowerManager} to enable communication when this GUI is shown.
 * It automatically enables communications once onAndroidServiceAvailable() is called and disables it when it is removed from the parent GUI
 * or the app goes to the background.
 */
public abstract class WakelockedGuiController extends GuiController {
    private AndroidPowerManager powerManager;

    protected WakelockedGuiController(MainActivity activity, ViewGroup content, int resourceId) {
        super(activity, content, resourceId);
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        powerManager = (AndroidPowerManager) androidService.getMelAuthService().getPowerManager();
        powerManager.wakeLock(this);
        powerManager.overrideState(this);
    }

    @Override
    public void onDestroy() {
        Lok.debug("destroy");
        if (powerManager != null) {
            powerManager.releaseWakeLock(this);
            powerManager.releaseOverride(this);
        }
        super.onDestroy();
    }

    @Override
    public void onStop() {
        if (powerManager != null) {
            powerManager.releaseWakeLock(this);
            powerManager.releaseOverride(false);
        }
        super.onStop();
    }
}
