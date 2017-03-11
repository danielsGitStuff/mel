package de.mein;

import android.app.Activity;
import android.view.View;

/**
 * Created by xor on 3/9/17.
 */

public abstract class AndroidServiceCreatorGuiController {
    protected final View rootView;
    protected final Activity activity;

    public AndroidServiceCreatorGuiController(Activity activity, View rootView) {
        this.rootView = rootView;
        this.activity = activity;
        init();
    }

    protected abstract void init();
}
