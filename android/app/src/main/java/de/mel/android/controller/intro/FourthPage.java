package de.mel.android.controller.intro;

import android.graphics.Paint;
import android.widget.TextView;

import de.mel.R;
import de.mel.android.controller.GuiController;


class FourthPage extends IntroPageController {
    private TextView lblCaption1, lblCaption2, lblCaption3, lblCaption4, lblCaption5;

    public FourthPage(IntroWrapper introWrapper) {
        super(introWrapper, R.layout.intro_fourth);
        lblCaption1 = rootView.findViewById(R.id.lblCaption1);
        lblCaption2 = rootView.findViewById(R.id.lblCaption2);
        lblCaption3 = rootView.findViewById(R.id.lblCaption3);
        lblCaption4 = rootView.findViewById(R.id.lblCaption4);
        lblCaption5 = rootView.findViewById(R.id.lblCaption5);
        GuiController.underline(lblCaption1, lblCaption2, lblCaption3, lblCaption4);
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
