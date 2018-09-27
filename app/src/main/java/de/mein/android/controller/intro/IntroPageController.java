package de.mein.android.controller.intro;

import android.view.ViewGroup;

import de.mein.android.MeinActivity;
import de.mein.android.controller.GuiController;

public abstract class IntroPageController extends GuiController {
    protected IntroWrapper introWrapper;
    IntroPageController(IntroWrapper introWrapper, int resourceId) {
        super(introWrapper.getMeinActivity(), introWrapper.getContainer(), resourceId);
        this.introWrapper = introWrapper;
    }

    /**
     *
     * @return null if eveything is ok. String if an error occured. eg invalid user input
     */
    public abstract String getError();
}
