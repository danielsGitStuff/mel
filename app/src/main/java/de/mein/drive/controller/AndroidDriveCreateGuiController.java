package de.mein.drive.controller;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TabHost;
import android.widget.TableRow;

import java.io.File;

import de.mein.AndroidServiceCreatorGuiController;
import de.mein.auth.service.MeinAuthService;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveCreateGuiController extends AndroidServiceCreatorGuiController {

    private final MeinAuthService meinAuthService;
    private EditText txtName, txtPath;
    private RadioButton rdServer, rdClient;
    private ListView listMeinAuths, listDrives;
    private TabHost tabHost;

    public AndroidDriveCreateGuiController(MeinAuthService meinAuthService, Activity activity, View rootView) {
        super(activity, rootView);
        this.meinAuthService = meinAuthService;
    }

    @Override
    protected void init() {
        txtName = (EditText) rootView.findViewById(R.id.txtName);
        txtPath = (EditText) rootView.findViewById(R.id.txtPath);
        rdServer = (RadioButton) rootView.findViewById(R.id.rdServer);
        rdClient = (RadioButton) rootView.findViewById(R.id.rdClient);
        listMeinAuths = (ListView) rootView.findViewById(R.id.listMeinAuths);
        listDrives = (ListView) rootView.findViewById(R.id.listDrives);
        tabHost = (TabHost) rootView.findViewById(R.id.tabHost);
        checkRadioButtons();
    }

    private void checkRadioButtons() {
        if (rdServer.isChecked()) {
            tabHost.setVisibility(View.INVISIBLE);
        } else {
            tabHost.setVisibility(View.VISIBLE);
        }
    }

    public boolean isServer() {
        return rdServer.isChecked();
    }

    public String getName() {
        return txtName.getText().toString();
    }

    public String getPath() {
        return txtPath.getText().toString();
    }

    public boolean isValid() {
        if (!(new File(txtPath.getText().toString()).exists()
                && txtName.getText().toString().trim().length() > 0))
            return false;
        if (rdClient.isChecked()) {
        }
        return true;
    }
}
