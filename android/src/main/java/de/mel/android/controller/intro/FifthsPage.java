package de.mel.android.controller.intro;

import android.Manifest;
import android.graphics.Paint;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import de.mel.AndroidPermission;
import de.mel.Lok;
import de.mel.R;
import de.mel.android.controller.GuiController;
import de.mel.android.permissions.PermissionsListAdapter;
import fun.with.Lists;
import kotlin.Unit;


class FifthsPage extends IntroPageController {
    private TextView lblCaption1;
    private ListView lsPermissions;

    public FifthsPage(IntroWrapper introWrapper) {
        super(introWrapper, R.layout.intro_fifths);
        lblCaption1 = rootView.findViewById(R.id.lblCaption1);
        GuiController.underline(lblCaption1);
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
