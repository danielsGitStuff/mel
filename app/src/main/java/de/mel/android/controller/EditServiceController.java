package de.mel.android.controller;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.boot.AndroidBootLoader;
import de.mel.android.service.AndroidService;
import de.mel.auth.data.access.DatabaseManager;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelService;
import de.mel.auth.tools.N;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.sql.SqlQueriesException;

/**
 * Created by xor on 3/25/17.
 */

public class EditServiceController extends GuiController {
    private IMelService runningInstance;
    private ServiceJoinServiceType service;
    private LinearLayout embedded;
    private Button btnApply, btnDelete, btnDeactivate;
    private AndroidServiceGuiController currentController;
    private EditText txtName;
    private TextView lblState;

    public EditServiceController(MainActivity activity, LinearLayout content, ServiceJoinServiceType service, IMelService runningInstance) {
        super(activity, content, R.layout.content_edit_service);
        this.service = service;
        this.runningInstance = runningInstance;
        this.txtName = rootView.findViewById(R.id.txtName);
        this.btnApply = rootView.findViewById(R.id.btnApply);
        this.btnDeactivate = rootView.findViewById(R.id.btnDeactivate);
        this.btnDelete = rootView.findViewById(R.id.btnDeleteService);
        this.lblState = rootView.findViewById(R.id.lblState);
        txtName.setText(service.getName().v());
    }

    @Override
    public Integer getTitle() {
        return R.string.editServiceTitle;
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        activity.runOnUiThread(() -> N.r(() -> {
            MelAuthService melAuthService = androidService.getMelAuthService();
            embedded = rootView.findViewById(R.id.embedded);
            AndroidBootLoader bootLoader = (AndroidBootLoader) melAuthService.getMelBoot().getBootLoader(service.getType().v());
            currentController = bootLoader.inflateEmbeddedView(embedded, activity, melAuthService, runningInstance);
            btnApply.setOnClickListener(v -> N.r(() -> {
                service.getName().v(txtName.getText().toString());
                currentController.setName(txtName.getText().toString());
                DatabaseManager databaseManager = melAuthService.getDatabaseManager();
                Service s = databaseManager.getServiceByUuid(service.getUuid().v());
                s.setName(service.getName().v());
                databaseManager.updateService(s);
                currentController.onOkClicked();
            }));
            Lok.debug("showSelected");
        }));
        btnDelete.setOnClickListener(v -> {
            try {
                MelService service = androidService.getMelAuthService().getMelService(EditServiceController.this.service.getUuid().v());
                if (service != null)
                    service.shutDown();
                androidService.getMelAuthService().deleteService(EditServiceController.this.service.getUuid().v());
                activity.showInfo();
            } catch (SqlQueriesException | IllegalAccessException | InstantiationException e) {
                e.printStackTrace();
            }
        });
        btnDeactivate.setOnClickListener(v -> {
            try {
                Service dbService = androidService.getMelAuthService().getDatabaseManager().getServiceByUuid(service.getUuid().v());
                dbService.setActive(!dbService.isActive());
                service.getActive().v(dbService.isActive());
                androidService.getMelAuthService().getDatabaseManager().updateService(dbService);
                boolean nowActive = dbService.isActive();
                if (nowActive) {
//                    Bootloader bootLoader = androidService.getMelAuthService().getMelBoot().getBootLoader(service.getType().v());
//                    List<Service> serviceList = new ArrayList<>();
//                    serviceList.add(dbService);
                    androidService.getMelAuthService().getMelBoot().bootServices();
//                    bootLoader.boot(androidService.getMelAuthService(), serviceList);
                } else {
                    MelService melService = androidService.getMelAuthService().getMelService(service.getUuid().v());
                    melService.shutDown();
                }
                showServiceStatus();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        showServiceStatus();
    }

    private void showServiceStatus() {
        int stateColor = 0;
        int stateText;
        int btnText;
        if (service.getActive().v()) {
            MelService melService = androidService.getMelAuthService().getMelService(service.getUuid().v());
            if (melService == null) {
                stateColor = activity.getResources().getColor(R.color.stateStopped);
                stateText = R.string.stateStopped;
            } else if (melService.getBootLevel() == melService.getReachedBootLevel()) {
                stateColor = activity.getResources().getColor(R.color.stateRunning);
                stateText = R.string.stateRunning;
            } else {
                switch (melService.getReachedBootLevel()) {
                    case NONE:
                        Lok.error("boot level confusion!");
                    case SHORT:
                        stateColor = activity.getResources().getColor(R.color.stateDeactivated);
                        stateText = R.string.stateIncompleteBoot;
                        break;
                    default:
                        stateText = R.string.error;
                        break;
                }
            }
            btnText = R.string.btnDeactivate;
        } else {
            stateColor = activity.getResources().getColor(R.color.stateDeactivated);
            stateText = R.string.stateDeactivated;
            btnText = R.string.btnActivate;
        }
        btnDeactivate.setText(btnText);
        lblState.setBackgroundColor(stateColor);
        lblState.setText(stateText);
    }

    @Override
    public void onAndroidServiceUnbound() {

    }

    @Override
    public Integer getHelp() {
        return R.string.editServiceHelp;
    }
}
