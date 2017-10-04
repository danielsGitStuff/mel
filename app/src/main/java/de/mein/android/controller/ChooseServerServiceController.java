package de.mein.android.controller;

import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.util.ArrayList;
import java.util.List;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.android.view.ServicesListAdapter;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.DriveStrings;

/**
 * Created by xor on 10/4/17.
 */

public abstract class ChooseServerServiceController extends AndroidServiceCreatorGuiController {

    protected abstract void initEmbedded();
    private RadioButton rdServer, rdClient;
    private LinearLayout lDriveChooser;
    private Long selectedCertId = null;
    private ServiceJoinServiceType selectedDrive;
    private KnownCertListAdapter knownCertListAdapter;
    private ServicesListAdapter drivesListAdapter;
    protected final MeinAuthService meinAuthService;
    private ListView knownCertList, drivesList;

    @Override
    protected void init() {
        rdServer = rootView.findViewById(R.id.rdServer);
        rdClient = rootView.findViewById(R.id.rdClient);
        lDriveChooser = rootView.findViewById(R.id.lDriveChooser);
        knownCertList = rootView.findViewById(R.id.knownCertList);
        knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        knownCertList.setOnItemClickListener((parent, view, position, id) -> {
            selectedCertId = knownCertListAdapter.getItemT(position).getId().v();
            System.out.println("AndroidDriveCreateGuiController.init.CLICKED");
            showDrives(selectedCertId);
        });
        knownCertList.setAdapter(knownCertListAdapter);
        drivesList = rootView.findViewById(R.id.listDrives);
        drivesListAdapter = new ServicesListAdapter(rootView.getContext());
        drivesList.setAdapter(drivesListAdapter);
        drivesList.setOnItemClickListener((parent, view, position, id) -> {
            selectedDrive = drivesListAdapter.getItemT(position);
            int colour = ContextCompat.getColor(rootView.getContext(), R.color.colorListSelected);
            view.setBackgroundColor(colour);
        });
        RadioGroup radioGroup = rootView.findViewById(R.id.rdgClient);
        if (radioGroup != null) {
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> checkRadioButtons());
            checkRadioButtons();
        }
        initEmbedded();
    }

    private void showDrives(Long selectedCertId) {
        List<ServiceJoinServiceType> services = meinAuthService.getNetworkEnvironment().getServices(selectedCertId);
        List<ServiceJoinServiceType> filtered = new ArrayList<>();
        if (services != null) {
            for (ServiceJoinServiceType service : services) {
                if (service.getType().v().equals(DriveStrings.NAME))
                    filtered.add(service);
            }
        }
        drivesListAdapter.clear();
        drivesListAdapter.addAll(filtered);
        drivesListAdapter.notifyDataSetChanged();
    }

    public ChooseServerServiceController(MeinAuthService meinAuthService, MeinActivity activity, View rootView) {
        super(activity, rootView);
        this.meinAuthService = meinAuthService;
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
                NetworkEnvironment env = meinAuthService.getNetworkEnvironment();
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

    public boolean isServer() {
        return rdServer.isChecked();
    }

    public Long getSelectedCertId() {
        return selectedCertId;
    }

    public ServiceJoinServiceType getSelectedDrive() {
        return selectedDrive;
    }
}
