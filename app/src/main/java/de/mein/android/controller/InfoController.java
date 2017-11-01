package de.mein.android.controller;

import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidService;

/**
 * Created by xor on 2/22/17.
 */
public class InfoController extends GuiController {
    private TextView lblStatus;

    public InfoController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_info);
        lblStatus = rootView.findViewById(R.id.lblStatus);
        System.out.println("InfoController.InfoController");
    }




    @Override
    public Integer getTitle() {
        return R.string.infoTitle;
    }

    @Override
    public void onAndroidServiceAvailable() {
            activity.runOnUiThread(() -> {
                if (androidService.isRunning()) {
                    lblStatus.setText("Running");
                    lblStatus.setBackgroundColor(Color.parseColor("#ff99cc00"));
                } else {
                    lblStatus.setText("Stopped");
                    lblStatus.setBackgroundColor(Color.parseColor("#ffcc0000"));
                }
            });
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {
        this.androidService = null;
    }

    @Override
    public Integer getHelp() {
        return R.string.infoHelp;
    }


}
