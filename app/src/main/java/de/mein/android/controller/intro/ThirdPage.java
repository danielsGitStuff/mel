package de.mein.android.controller.intro;

import android.widget.Button;

import de.mein.R;
import de.mein.android.service.AndroidPowerManager;
import de.mein.android.service.AndroidService;
import de.mein.android.view.PowerView;
import de.mein.auth.service.power.PowerManager;

public class ThirdPage extends IntroPageController implements PowerManager.IPowerStateListener<AndroidPowerManager> {
    private PowerView powerView;
    private Button btnPowerMobile, btnPowerServer;
    private AndroidPowerManager powerManager;

    ThirdPage(IntroWrapper introWrapper) {
        super(introWrapper, R.layout.intro_third);
        powerView = rootView.findViewById(R.id.powerView);
        btnPowerMobile = rootView.findViewById(R.id.btnPowerMobile);
        btnPowerServer = rootView.findViewById(R.id.btnPowerServer);
        btnPowerServer.setOnClickListener(v -> {
            powerManager.configure(true, true, true, true);
            powerView.update();
        });
        btnPowerMobile.setOnClickListener(v -> {
            powerManager.configure(true, false, false, false);
            powerView.update();
        });
    }

    @Override
    public String getError() {
        return null;
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        this.powerManager = (AndroidPowerManager) androidService.getMeinAuthService().getPowerManager();
        powerView.setPowerManager(powerManager);
        powerManager.addStateListener(this);
        powerView.update();
    }

    @Override
    public void onDestroy() {
        if (powerManager != null) {
            powerManager.removeListener(this);
        }
        super.onDestroy();

    }

    @Override
    public Integer getTitle() {
        return R.string.intro_3_title;
    }

    @Override
    public Integer getHelp() {
        return null;
    }

    @Override
    public void onStateChanged(PowerManager powerManager) {

    }
}
