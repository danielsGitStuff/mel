package de.mein.android.controller.intro;

import android.view.ViewGroup;

import de.mein.android.MeinActivity;
import de.mein.android.controller.GuiController;

public abstract class IntroController extends GuiController {
    protected IntroWrapper introWrapper;
    IntroController(IntroWrapper introWrapper, int resourceId) {
        super(introWrapper.getMeinActivity(), introWrapper.getContainer(), resourceId);
        this.introWrapper = introWrapper;
    }
}
