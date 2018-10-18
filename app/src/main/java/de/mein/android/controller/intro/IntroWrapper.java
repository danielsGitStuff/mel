package de.mein.android.controller.intro;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.Notifier;
import de.mein.android.service.AndroidService;
import de.mein.android.service.AndroidServiceBind;

public class IntroWrapper extends RelativeLayout implements AndroidServiceBind {

    public interface IntroDoneListener {
        void introDone();
    }

    protected MeinActivity meinActivity;
    private LinearLayout container;
    private AppCompatImageButton btnForward, btnPrevious;
    protected TextView lblTitle;
    protected TextView lblIndex;
    protected IntroPageController pageController;
    protected int index = 1;
    protected int maxIndex = 4;
    protected IntroDoneListener introDoneListener;

    public IntroWrapper(MeinActivity meinActivity) {
        super(meinActivity.getApplicationContext());
        this.meinActivity = meinActivity;
        init(meinActivity.getApplicationContext());
    }


    private void init(Context context) {
        inflate(context, R.layout.intro_wrapper, this);
        lblTitle = findViewById(R.id.lblTitle);
        container = findViewById(R.id.container);
        btnForward = findViewById(R.id.btnForward);
        btnPrevious = findViewById(R.id.btnPrevious);
        lblIndex = findViewById(R.id.lblIndex);
        btnPrevious.setVisibility(INVISIBLE);
        btnForward.setOnClickListener(v -> {
            if (pageController.getError() == null) {
                btnForward.setEnabled(true);
                btnPrevious.setEnabled(true);
                btnPrevious.setVisibility(VISIBLE);
                if (index < maxIndex) {
                    index++;
                    showPage();
                } else {
                    if (introDoneListener != null) {
                        introDoneListener.introDone();
                    }
                    return;
                }
                if (index == maxIndex) {
                    meinActivity.runOnUiThread(() -> {
                        btnForward.setImageResource(R.drawable.icon_finish);
                    });
                }
            } else {
                Notifier.toast(meinActivity, pageController.getError());
            }
        });
        btnPrevious.setOnClickListener(v -> {
            btnPrevious.setEnabled(true);
            btnForward.setEnabled(true);
            if (index > 1) {
                index--;
                showPage();
            }
            if (index == 1) {
                btnPrevious.setEnabled(false);
                btnPrevious.setVisibility(INVISIBLE);
            } else if (index < maxIndex) {
                meinActivity.runOnUiThread(() -> {
                    btnForward.setImageResource(R.drawable.icon_next);
                });
            }
        });
        showPage();
    }

    public void setIntroDoneListener(IntroDoneListener introDoneListener) {
        this.introDoneListener = introDoneListener;
    }

    protected void showPage() {
        if (pageController != null) {
            pageController.onDestroy();
            pageController.onAndroidServiceUnbound();
        }
        switch (index) {
            case 1:
                pageController = new FirstPage(this);
                break;
            case 2:
                pageController = new SecondPage(this);
                break;
            case 3:
                pageController = new ThirdPage(this);
                break;
            case 4:
                pageController = new FourthPage(this);
                break;
        }
        if (meinActivity.getAndroidService() != null) {
            pageController.onAndroidServiceAvailable(meinActivity.getAndroidService());
        }
        lblTitle.setText(pageController.getTitle());
        lblIndex.setText(index + "/" + 4);
    }

    public MeinActivity getMeinActivity() {
        return meinActivity;
    }

    public ViewGroup getContainer() {
        return container;
    }

    public void setBtnForwardActive(boolean active) {
        btnForward.setEnabled(active);
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        pageController.onAndroidServiceAvailable(androidService);
        meinActivity.runOnUiThread(() -> btnForward.setVisibility(VISIBLE));
    }

    @Override
    public void onAndroidServiceUnbound() {
        pageController.onAndroidServiceUnbound();
    }
}
