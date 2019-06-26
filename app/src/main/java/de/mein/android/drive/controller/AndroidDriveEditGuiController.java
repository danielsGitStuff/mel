package de.mein.android.drive.controller;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveService;

/**
 * Created by xor on 8/25/17.
 */

public class AndroidDriveEditGuiController extends AndroidServiceGuiController {
    private final MeinDriveService runningInstance;
    private EditText txtPath, txtMaxSize, txtMaxDays;
    private Button btnPath;
    private TextView lblRole;


    public AndroidDriveEditGuiController(MeinAuthService meinAuthService, MainActivity activity, IMeinService iMeinService, ViewGroup rootView) {
        super(activity, rootView, R.layout.embedded_twice_drive_edit);
        this.runningInstance = (MeinDriveService) iMeinService;
        txtPath.setText(this.runningInstance.getDriveSettings().getRootDirectory().getPath());
        btnPath.setEnabled(false);
        String role;
        if (((MeinDriveService) iMeinService).getDriveSettings().getRole().equals(DriveStrings.ROLE_SERVER))
            role = "Role: Server";
        else
            role = "Role: Client";
        lblRole.setText(role);
    }

    @Override
    protected void init() {
        txtPath = rootView.findViewById(R.id.txtPath);
        btnPath = rootView.findViewById(R.id.btnPath);
        txtMaxDays = rootView.findViewById(R.id.txtMaxDays);
        txtMaxSize = rootView.findViewById(R.id.txtMaxSize);
        lblRole = rootView.findViewById(R.id.lblRole);
    }

    @Override
    public boolean onOkClicked() {
        //todo handle change of path
        return false;
    }
}
