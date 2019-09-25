package de.mel.android.drive.controller;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.controller.AndroidServiceGuiController;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;
import de.mel.drive.data.DriveStrings;
import de.mel.drive.service.MelDriveService;

/**
 * Created by xor on 8/25/17.
 */

public class AndroidDriveEditGuiController extends AndroidServiceGuiController {
    private final MelDriveService runningInstance;
    private EditText txtPath, txtMaxSize, txtMaxDays;
    private Button btnPath;
    private TextView lblRole;


    public AndroidDriveEditGuiController(MelAuthService melAuthService, MainActivity activity, IMelService iMelService, ViewGroup rootView) {
        super(activity, rootView, R.layout.embedded_twice_drive_edit);
        this.runningInstance = (MelDriveService) iMelService;
        txtPath.setText(this.runningInstance.getDriveSettings().getRootDirectory().getPath());
        btnPath.setEnabled(false);
        String role;
        if (((MelDriveService) iMelService).getDriveSettings().getRole().equals(DriveStrings.ROLE_SERVER))
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
