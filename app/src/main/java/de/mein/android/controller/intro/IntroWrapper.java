package de.mein.android.controller.intro;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.controller.GuiController;
import de.mein.android.service.AndroidService;
import de.mein.android.service.AndroidServiceBind;

public class IntroWrapper extends RelativeLayout implements AndroidServiceBind {

    private MeinActivity meinActivity;
    private LinearLayout container;
    private ImageButton btnForward, btnPrevious;
    private TextView lblTitle, lblIndex;
    private GuiController guiController;

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
        showPage(1);
    }

    private void showPage(int index) {
        switch (index) {
            case 1:
                guiController = new FirstPage(this);
                break;
        }
        lblTitle.setText(guiController.getTitle());
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
        guiController.onAndroidServiceAvailable(androidService);
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {
        guiController.onAndroidServiceUnbound(androidService);
    }
}
