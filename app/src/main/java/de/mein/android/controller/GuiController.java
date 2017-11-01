package de.mein.android.controller;

import android.view.View;
import android.widget.LinearLayout;

import org.greenrobot.eventbus.Subscribe;

import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidService;

/**
 * Created by xor on 2/23/17.
 */

public abstract class GuiController {

    protected final MeinActivity activity;
    protected final View rootView;

    protected AndroidService androidService;

    protected GuiController(MeinActivity activity, LinearLayout content, int resourceId) {
        this.activity = activity;
        content.removeAllViews();
        this.rootView = View.inflate(activity, resourceId, content);
    }

    @Subscribe(sticky = true)
    public void onAndroidServiceAvailable(AndroidService androidService) {
        this.androidService = androidService;
        onAndroidServiceAvailable();
    }

    /**
     * called when the controller is removed from the view. you should clean up references to this instance here.
     */
    public void onDestroy(){

    }

    public abstract String getTitle();

    public abstract void onAndroidServiceAvailable();

    public abstract void onAndroidServiceUnbound(AndroidService androidService);

    public View getRootView() {
        return rootView;
    }

    public abstract Integer getHelp();
}
