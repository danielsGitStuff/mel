package de.mein.drive.controller;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;

import java.io.File;

import de.mein.AndroidServiceCreatorGuiController;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.view.KnownCertListAdapter;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveCreateGuiController extends AndroidServiceCreatorGuiController {

    private final MeinAuthService meinAuthService;
    private EditText txtName, txtPath;
    private RadioButton rdServer, rdClient;
    private ListView knownCertList, drivesList;
    private RelativeLayout lDriveChooser;
    private KnownCertListAdapter knownCertListAdapter;
    private Long selectedCertId = null;

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
        lDriveChooser = (RelativeLayout) rootView.findViewById(R.id.lDriveChooser);
        RadioGroup radioGroup = (RadioGroup) rootView.findViewById(R.id.rdgClient);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> checkRadioButtons());
        checkRadioButtons();
        knownCertList = (ListView) rootView.findViewById(R.id.knownCertList);
        knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        knownCertList.setOnItemClickListener((parent, view, position, id) -> {
            selectedCertId = knownCertListAdapter.getItemT(position).getId().v();
            System.out.println("AndroidDriveCreateGuiController.init.CLICKED");
        });
        drivesList = (ListView) rootView.findViewById(R.id.listDrives);
        //drivesListAdapter = new
    }

    private void checkRadioButtons() {
        if (rdServer.isChecked()) {
            lDriveChooser.setVisibility(View.INVISIBLE);
            selectedCertId = null;
        } else {
            lDriveChooser.setVisibility(View.VISIBLE);
            try {
                NetworkEnvironment env = meinAuthService.getNetworkEnvironment().clear();
                env.addObserver((o, arg) -> {
                    if (selectedCertId != null) {

                    }
                });
                for (Long certId : meinAuthService.getConnectedUserIds()) {
                    Certificate cert = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
                    knownCertListAdapter.add(cert);
                }
                knownCertListAdapter.notifyDataSetChanged();
                meinAuthService.getNetworkEnvironment();
            } catch (Exception e) {
                e.printStackTrace();
            }

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
