package de.mein.android.controller;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.greenrobot.eventbus.Subscribe;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidService;

/**
 * Created by xor on 3/9/17.
 */

public abstract class AndroidServiceCreatorGuiController {
    protected final View rootView;
    protected final MeinActivity activity;


    public AndroidServiceCreatorGuiController(MeinActivity activity, View rootView) {
        this.rootView = rootView;
        this.activity = activity;
        init();
    }

    protected abstract void init();
}
