package de.mein.android.controller;

import android.view.View;
import android.view.ViewGroup;

import de.mein.android.MeinActivity;

/**
 * Created by xor on 3/9/17.
 */

public abstract class AndroidServiceGuiController {
    protected final View rootView;
    protected final MeinActivity activity;
    protected String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public AndroidServiceGuiController(MeinActivity activity, ViewGroup embedded, int resource) {
        this.rootView = activity.getLayoutInflater().inflate(resource,embedded);
        this.activity = activity;
        init();
    }

    /**
     * you should rootView.findView() here
     */
    protected abstract void init();

    /**
     * is called when user presses apply button when editing server
     */
    public abstract void onOkClicked();
}
