package de.mein.android.controller;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.Tools;

/**
 * Created by xor on 3/9/17.
 */

public abstract class AndroidServiceGuiController {
    protected final View rootView;
    protected final MeinActivity activity;
    protected String name;
    protected final View.OnFocusChangeListener hideKeyboardOnFocusLostListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus) {
                InputMethodManager imm = (InputMethodManager) Tools.getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
    };
    protected PermissionsGrantedListener permissionsGrantedListener;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public AndroidServiceGuiController(MeinActivity activity, ViewGroup embedded, int resource) {
        this.rootView = activity.getLayoutInflater().inflate(resource, embedded);
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

    public void setOnPermissionsGrantedListener(PermissionsGrantedListener permissionsGrantedListener) {
        this.permissionsGrantedListener = permissionsGrantedListener;
    }
}
