package de.mel.android.controller.intro;

import android.widget.TextView;

import de.mel.R;
import de.mel.android.service.AndroidService;

public class FirstPage extends IntroPageController {

    private TextView text;

    FirstPage(IntroWrapper introWrapper) {
        super(introWrapper, R.layout.intro_first);
        text = rootView.findViewById(R.id.text);
        introWrapper.setBtnForwardActive(false);
        text();
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        text();
    }

    private synchronized void text() {
        if (androidService != null) {
            activity.runOnUiThread(() -> {
                text.setText(R.string.intro_1_booted);
                introWrapper.setBtnForwardActive(true);
            });
        }
    }

    @Override
    public Integer getTitle() {
        return R.string.intro_1_title;
    }

    @Override
    public Integer getHelp() {
        return null;
    }

    @Override
    public String getError() {
        return null;
    }
}
