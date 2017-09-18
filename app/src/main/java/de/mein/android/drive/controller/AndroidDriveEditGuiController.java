package de.mein.android.drive.controller;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.service.MeinDriveService;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 8/25/17.
 */

public class AndroidDriveEditGuiController extends AndroidServiceCreatorGuiController {
    private final MeinDriveService runningInstance;
    private EditText txtName;
    private EditText txtPath;
    private Button btnPath;

    public AndroidDriveEditGuiController(MeinAuthService meinAuthService, MeinActivity activity, IMeinService iMeinService, View rootView) {
        super(activity, rootView);
        this.runningInstance = (MeinDriveService) iMeinService;
        try {
            Service service = meinAuthService.getDatabaseManager().getServiceByUuid(runningInstance.getUuid());
            txtName.setText(service.getName().v());
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
        txtPath.setText(this.runningInstance.getDriveSettings().getRootDirectory().getPath());
        btnPath.setEnabled(false);
    }

    @Override
    protected void init() {
        txtName = rootView.findViewById(R.id.txtName);
        txtPath = rootView.findViewById(R.id.txtPath);
        btnPath = rootView.findViewById(R.id.btnPath);
    }
}
