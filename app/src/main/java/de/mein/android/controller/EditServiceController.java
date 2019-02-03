package de.mein.android.controller;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.service.AndroidService;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.Bootloader;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 3/25/17.
 */

public class EditServiceController extends GuiController {
    private IMeinService runningInstance;
    private ServiceJoinServiceType service;
    private LinearLayout embedded;
    private Button btnApply, btnDelete, btnDeactivate;
    private AndroidServiceGuiController currentController;
    private EditText txtName;
    private TextView lblState;

    public EditServiceController(MainActivity activity, LinearLayout content, ServiceJoinServiceType service, IMeinService runningInstance) {
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
            Lok.debug("showSelected");
        }));
        btnDelete.setOnClickListener(v -> {
            MeinService service = androidService.getMeinAuthService().getMeinService(EditServiceController.this.service.getUuid().v());
            service.shutDown();
            try {
                androidService.getMeinAuthService().getDatabaseManager().deleteService(EditServiceController.this.service.getServiceId().v());
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        });
        btnDeactivate.setOnClickListener(v -> {
            try {
                Service dbService = androidService.getMeinAuthService().getDatabaseManager().getServiceByUuid(service.getUuid().v());
                dbService.setActive(!dbService.isActive());
                service.getActive().v(dbService.isActive());
                androidService.getMeinAuthService().getDatabaseManager().updateService(dbService);
                boolean nowActive = dbService.isActive();
                if (nowActive) {
                    Bootloader bootLoader = androidService.getMeinAuthService().getMeinBoot().getBootLoader(service.getType().v());
                    List<Service> serviceList = new ArrayList<>();
                    serviceList.add(dbService);
                    bootLoader.boot(androidService.getMeinAuthService(), serviceList);
                } else {
                    MeinService meinService = androidService.getMeinAuthService().getMeinService(service.getUuid().v());
                    meinService.shutDown();
                }
                showServiceStatus();

            } catch (SqlQueriesException | IllegalAccessException | InstantiationException | IOException | ClassNotFoundException | JsonSerializationException | JsonDeserializationException | SQLException e) {
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
                MeinService meinService = androidService.getMeinAuthService().getMeinService(service.getUuid().v());
                if (meinService == null) {
                    stateColor = activity.getResources().getColor(R.color.stateStopped);
                    stateText = R.string.stateStopped;
                } else {
                    stateColor = activity.getResources().getColor(R.color.stateRunning);
                    stateText = R.string.stateRunning;
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
