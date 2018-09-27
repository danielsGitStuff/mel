package de.mein.android.controller;

import android.view.View;
import android.view.ViewGroup;

import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidService;
import de.mein.android.service.AndroidServiceBind;

/**
 * Created by xor on 2/23/17.
 */

public abstract class GuiController implements AndroidServiceBind {

    protected final MeinActivity activity;
    protected final View rootView;

    protected AndroidService androidService;

    protected GuiController(MeinActivity activity, ViewGroup content, int resourceId) {
        content.removeAllViews();
        this.activity = activity;
        this.rootView = View.inflate(activity, resourceId, content);
    }


    /**
     * called when the controller is removed from the view. you should clean up references to this instance here.
     */
    public void onDestroy() {
        androidService = null;
    }

    public abstract Integer getTitle();

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        this.androidService = androidService;
    }

    ;

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {
        androidService = null;
    }

    ;

    public View getRootView() {
        return rootView;
    }

    public abstract Integer getHelp();
}
