package de.mel.android.controller.intro;

import android.view.ViewGroup;

import de.mel.android.MelActivity;
import de.mel.android.controller.GuiController;

public abstract class IntroPageController extends GuiController {
    protected IntroWrapper introWrapper;
    IntroPageController(IntroWrapper introWrapper, int resourceId) {
        super(introWrapper.getMelActivity(), introWrapper.getContainer(), resourceId);
        this.introWrapper = introWrapper;
    }

    /**
     *
     * @return null if eveything is ok. String if an error occured. eg invalid user input
     */
    public abstract String getError();
}
