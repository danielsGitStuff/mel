package de.mein.android.controller.intro;

import android.graphics.Paint;
import android.widget.TextView;

import de.mein.R;


class FourthPage extends IntroPageController {
    private TextView lblCaption1, lblCaption2;

    public FourthPage(IntroWrapper introWrapper) {
        super(introWrapper, R.layout.intro_fourth);
        lblCaption1 = rootView.findViewById(R.id.lblCaption1);
        lblCaption2 = rootView.findViewById(R.id.lblCaption2);
        underline(lblCaption1, lblCaption2);
    }

    private void underline(TextView... labels) {
        for (TextView lbl : labels) {
            lbl.setPaintFlags(lbl.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    @Override
    public String getError() {
        return null;
    }

    @Override
    public Integer getTitle() {
        return R.string.intro_4_title;
    }

    @Override
    public Integer getHelp() {
        return null;
    }
}
