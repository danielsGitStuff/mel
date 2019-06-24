package de.mein.android.controller.intro;

import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidService;

public class LoadingWrapper extends IntroWrapper {
    public LoadingWrapper(MainActivity meinActivity) {
        super(meinActivity);
        maxIndex = 1;
    }

    @Override
    protected void showPage() {
        pageController = new FirstPage(this);
        if (meinActivity.getAndroidService() != null) {
            pageController.onAndroidServiceAvailable(meinActivity.getAndroidService());
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
