package de.mein.android;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import de.mein.R;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.android.controller.GuiController;
import de.mein.android.service.AndroidService;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;

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

    public EditServiceController(MeinActivity activity, MeinAuthService meinAuthService, MainActivity mainActivity, ViewGroup rootView, ServiceJoinServiceType service, IMeinService runningInstance) {
        super(activity);
        this.rootView = rootView;
        this.activity = activity;
        this.meinAuthService = meinAuthService;
        N.r(() -> {
            embedded = rootView.findViewById(R.id.embedded);
            AndroidBootLoader bootLoader = (AndroidBootLoader) meinAuthService.getMeinBoot().getBootLoader(service.getType().v());
            currentController = bootLoader.inflateEmbeddedView(embedded, activity, meinAuthService, runningInstance);

//                    View.inflateEmbeddedView(rootView.getContext(), bootLoader.getEditResource(runningInstance), embedded);
//            currentController = bootLoader.createGuiController(meinAuthService, activity, v, runningInstance);
            //bootLoader.setupController(meinAuthService,v);
            System.out.println("EditServiceController.showSelected");
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
