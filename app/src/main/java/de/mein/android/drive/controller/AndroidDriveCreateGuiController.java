package de.mein.android.drive.controller;

import android.app.Activity;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import java.io.File;
import java.util.List;

import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.DriveStrings;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.android.view.ServicesListAdapter;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveCreateGuiController extends AndroidServiceCreatorGuiController {

    private final MeinAuthService meinAuthService;
    private EditText txtName, txtPath;
    private RadioButton rdServer, rdClient;
    private ListView knownCertList, drivesList;
    private LinearLayout lDriveChooser;
    private KnownCertListAdapter knownCertListAdapter;
    private Long selectedCertId = null;
    private ServicesListAdapter drivesListAdapter;
    private ServiceJoinServiceType selectedDrive;

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
        lDriveChooser = (LinearLayout) rootView.findViewById(R.id.lDriveChooser);
        RadioGroup radioGroup = (RadioGroup) rootView.findViewById(R.id.rdgClient);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> checkRadioButtons());
        checkRadioButtons();
        knownCertList = (ListView) rootView.findViewById(R.id.knownCertList);
        knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        knownCertList.setOnItemClickListener((parent, view, position, id) -> {
            selectedCertId = knownCertListAdapter.getItemT(position).getId().v();
            System.out.println("AndroidDriveCreateGuiController.init.CLICKED");
            showDrives(selectedCertId);
        });
        knownCertList.setAdapter(knownCertListAdapter);
        drivesList = (ListView) rootView.findViewById(R.id.listDrives);
        drivesListAdapter = new ServicesListAdapter(rootView.getContext());
        drivesList.setAdapter(drivesListAdapter);
        drivesList.setOnItemClickListener((parent, view, position, id) -> {
            selectedDrive = drivesListAdapter.getItemT(position);
            int colour = ContextCompat.getColor(rootView.getContext(),R.color.colorListSelected);
            view.setBackgroundColor(colour);
        });
    }

    private void checkRadioButtons() {
        if (rdServer.isChecked()) {
            lDriveChooser.setVisibility(View.INVISIBLE);
            selectedCertId = null;
        } else {
            lDriveChooser.setVisibility(View.VISIBLE);
            try {
                selectedCertId = null;
                selectedDrive = null;
                knownCertListAdapter.clear();
                drivesListAdapter.clear();
                NetworkEnvironment env = meinAuthService.getNetworkEnvironment().clear();
                env.addObserver((o, arg) -> {
                    if (selectedCertId != null) {
                        showDrives(selectedCertId);
                    }
                });
                for (Long certId : meinAuthService.getConnectedUserIds()) {
                    Certificate cert = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
                    knownCertListAdapter.add(cert);
                }
                knownCertListAdapter.notifyDataSetChanged();
                drivesListAdapter.notifyDataSetChanged();
                meinAuthService.discoverNetworkEnvironment();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void showDrives(Long selectedCertId) {
        List<ServiceJoinServiceType> services = meinAuthService.getNetworkEnvironment().getServices(selectedCertId);
        List<ServiceJoinServiceType> filtered = Stream.of(services).filter(service -> service.getType().v().equals(DriveStrings.NAME)).collect(Collectors.toList());
        drivesListAdapter.clear();
        drivesListAdapter.addAll(filtered);
        drivesListAdapter.notifyDataSetChanged();
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

    public Long getSelectedCertId() {
        return selectedCertId;
    }

    public ServiceJoinServiceType getSelectedDrive() {
        return selectedDrive;
    }
}
