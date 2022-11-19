package de.mel.android.filesync.controller;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.controller.AndroidServiceGuiController;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.service.MelFileSyncService;

/**
 * Created by xor on 8/25/17.
 */

public class AndroidFileSyncEditGuiController extends AndroidServiceGuiController {
    private final MelFileSyncService runningInstance;
    private EditText txtPath, txtMaxSize, txtMaxDays;
    private Button btnPath;
    private TextView lblRole;


    public AndroidFileSyncEditGuiController(MelAuthService melAuthService, MainActivity activity, IMelService iMelService, ViewGroup rootView) {
        super(activity, rootView, R.layout.embedded_twice_drive_edit);
        this.runningInstance = (MelFileSyncService) iMelService;
        txtPath.setText(this.runningInstance.getFileSyncSettings().getRootDirectory().getPath());
        btnPath.setEnabled(false);
        String role;
        if (((MelFileSyncService) iMelService).getFileSyncSettings().getRole().equals(FileSyncStrings.ROLE_SERVER))
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
