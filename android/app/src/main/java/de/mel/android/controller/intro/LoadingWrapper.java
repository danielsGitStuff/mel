package de.mel.android.controller.intro;

import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.service.AndroidService;

public class LoadingWrapper extends IntroWrapper {
    public LoadingWrapper(MainActivity melActivity) {
        super(melActivity);
        maxIndex = 1;
    }

    @Override
    protected void showPage() {
        pageController = new FirstPage(this);
        if (melActivity.getAndroidService() != null) {
            pageController.onAndroidServiceAvailable(melActivity.getAndroidService());
        }
        lblTitle.setText(R.string.loading);
        lblIndex.setText("");
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        // we are done here
        if (introDoneListener != null)
            introDoneListener.introDone();
    }
}
