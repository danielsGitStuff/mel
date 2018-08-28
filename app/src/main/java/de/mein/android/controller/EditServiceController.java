package de.mein.android.controller;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.MeinActivity;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.service.AndroidService;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;

/**
 * Created by xor on 3/25/17.
 */

public class EditServiceController extends GuiController {
    private IMeinService runningInstance;
    private ServiceJoinServiceType service;
    private LinearLayout embedded;
    private Button btnApply;
    private AndroidServiceGuiController currentController;
    private EditText txtName;

    public EditServiceController(MainActivity activity, LinearLayout content, ServiceJoinServiceType service, IMeinService runningInstance) {
        super(activity, content, R.layout.content_edit_service);
        this.service = service;
        this.runningInstance = runningInstance;
        this.txtName = rootView.findViewById(R.id.txtName);
        this.btnApply = rootView.findViewById(R.id.btnApply);
        txtName.setText(service.getName().v());
    }

    @Override
    public Integer getTitle() {
        return R.string.editServiceTitle;
    }

    @Override
    public void onAndroidServiceAvailable() {
        activity.runOnUiThread(() -> N.r(() -> {
            MeinAuthService meinAuthService = androidService.getMeinAuthService();
            embedded = rootView.findViewById(R.id.embedded);
            AndroidBootLoader bootLoader = (AndroidBootLoader) meinAuthService.getMeinBoot().getBootLoader(service.getType().v());
            currentController = bootLoader.inflateEmbeddedView(embedded, activity, meinAuthService, runningInstance);
            btnApply.setOnClickListener(v -> N.r(() -> {
                service.getName().v(txtName.getText().toString());
                currentController.setName(txtName.getText().toString());
                DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
                Service s = databaseManager.getServiceByUuid(service.getUuid().v());
                s.setName(service.getName().v());
               databaseManager.updateService(s);
               currentController.onOkClicked();
            }));

//                    View.inflateEmbeddedView(rootView.getContext(), bootLoader.getEditResource(runningInstance), embedded);
//            currentController = bootLoader.createGuiController(meinAuthService, activity, v, runningInstance);
            //bootLoader.setupController(meinAuthService,v);
            System.out.println("EditServiceController.showSelected");
        }));
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }

    @Override
    public Integer getHelp() {
        return R.string.editServiceHelp;
    }
}
