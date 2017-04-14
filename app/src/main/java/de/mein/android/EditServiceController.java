package de.mein.android;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.android.controller.GuiController;
import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.NoTryRunner;

/**
 * Created by xor on 3/25/17.
 */

class EditServiceController extends GuiController {
    private final MeinAuthService meinAuthService;
    private View rootView;
    private LinearLayout embedded;
    private Button btnCreate;
    private Activity activity;
    private AndroidServiceCreatorGuiController currentController;

    public EditServiceController(MeinAuthService meinAuthService, MainActivity mainActivity, View rootView, ServiceJoinServiceType service, IMeinService runningInstance) {
        super();
        this.rootView = rootView;
        this.activity = activity;
        this.meinAuthService = meinAuthService;
        NoTryRunner.run(() -> {
            AndroidBootLoader bootLoader = (AndroidBootLoader) MeinBoot.getBootLoader(meinAuthService, service.getType().v());
            View v = View.inflate(rootView.getContext(), bootLoader.getEditResource(runningInstance), embedded);
            currentController = bootLoader.createGuiController(meinAuthService, activity, v);
            //bootLoader.setupController(meinAuthService,v);
            System.out.println("CreateServiceController.showSelected");
        });
    }



    @Override
    public void onMeinAuthStarted(MeinAuthService androidService) {

    }

    @Override
    public void onAndroidServiceBound(AndroidService androidService) {

    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }
}