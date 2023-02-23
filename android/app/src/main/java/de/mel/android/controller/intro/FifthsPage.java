package de.mel.android.controller.intro;

import android.graphics.Paint;
import android.widget.TextView;

import de.mel.R;


class FifthsPage extends IntroPageController {
    private TextView lblCaption1;

    public FifthsPage(IntroWrapper introWrapper) {
        super(introWrapper, R.layout.intro_fifths);
        lblCaption1 = rootView.findViewById(R.id.lblCaption1);
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
