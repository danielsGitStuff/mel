package de.mein.android.controller;

import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidService;
import de.mein.auth.service.power.PowerManager;

/**
 * Created by xor on 9/19/17.
 */

public class SettingsController extends GuiController {
    private PowerManager powerManager;

    public SettingsController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_settings);
    }

    @Override
    public String getTitle() {
        return "Settings";
    }

    @Override
    public void onAndroidServiceAvailable() {
        this.powerManager = androidService.getMeinAuthService().getPowerManager();
        CheckBox cbWorkWhenPlugged = rootView.findViewById(R.id.cbWorkWhenPlugged);
        cbWorkWhenPlugged.setChecked(powerManager.getHeavyWorkWhenPlugged());
        cbWorkWhenPlugged.setOnCheckedChangeListener((compoundButton, isChecked) -> powerManager.setHeavyWorkWhenPlugged(isChecked));
        CheckBox cbWorkWhenOffline = rootView.findViewById(R.id.cbWorkWhenOffline);
        cbWorkWhenOffline.setOnCheckedChangeListener((compoundButton, isChecked) -> powerManager.setHeavyWorkWhenOffline(isChecked));
        cbWorkWhenOffline.setChecked(powerManager.getHeavyWorkWhenOffline());
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }

    @Override
    public void onDestroy() {
        powerManager = null;
    }
}
