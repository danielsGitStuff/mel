package de.mel.android.controller;

import android.view.View;
import android.view.ViewGroup;

import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.service.AndroidService;
import de.mel.android.service.AndroidServiceBind;

/**
 * A part of the GUI that is places in an Activity. It comes with bindings to the parent {@link MelActivity}
 * and binds to the {@link AndroidService} when onAndroidServiceAvailable() is called.
 * Make sure to call super.onAndroidServiceAvailable() if you override it.
 * Created by xor on 2/23/17.
 */
public abstract class GuiController implements AndroidServiceBind {

    protected final MainActivity activity;
    protected final View rootView;

    protected AndroidService androidService;

    protected GuiController(MainActivity activity, ViewGroup content, int resourceId) {
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

    /**
     * called when the app goes in background
     */
    public void onStop(){

    }

    public abstract Integer getTitle();

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        this.androidService = androidService;
    }

    ;

    @Override
    public void onAndroidServiceUnbound() {
        androidService = null;
    }

    ;

    public View getRootView() {
        return rootView;
    }

    public abstract Integer getHelp();
}
