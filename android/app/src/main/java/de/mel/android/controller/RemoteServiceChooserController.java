package de.mel.android.controller;

import androidx.core.content.ContextCompat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.view.KnownCertListAdapter;
import de.mel.android.view.ServicesListAdapter;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;

/**
 * Created by xor on 10/4/17.
 */

public abstract class RemoteServiceChooserController extends AndroidServiceGuiController {


    private final Bootloader bootloader;
    protected CreateServiceController createServiceController;

    protected abstract void initEmbedded();

    private RadioButton rdServer, rdClient;
    private Long selectedCertId = null;
    private ServiceJoinServiceType selectedService;
    private KnownCertListAdapter knownCertListAdapter;
    private ServicesListAdapter drivesListAdapter;
    protected MelAuthService melAuthService;
    private ListView knownCertList, serviceList;
    private ViewGroup chooserContent;
    private TextView lblKnownMA, lblServices;

    public RemoteServiceChooserController(MelAuthService melAuthService, MainActivity activity, ViewGroup viewGroup, int embeddedResource, Bootloader bootloader) {
        super(activity, viewGroup, R.layout.embedded_create_service_chooser);
        this.melAuthService = melAuthService;
        this.bootloader = bootloader;
        chooserContent = rootView.findViewById(R.id.chooserContent);
        View root = View.inflate(activity, embeddedResource, chooserContent);
        initEmbedded();
    }

    @Override
    protected void init() {
        rdServer = rootView.findViewById(R.id.rdServer);
        rdClient = rootView.findViewById(R.id.rdClient);
        lblKnownMA = rootView.findViewById(R.id.lblKnownMA);
        lblServices = rootView.findViewById(R.id.lblServices);
        knownCertList = rootView.findViewById(R.id.knownCertList);
        knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        knownCertList.setOnItemClickListener((parent, view, position, id) -> {
            selectedCertId = knownCertListAdapter.getItemT(position).getId().v();
            Lok.debug("RemoteDriveServiceChooserGuiController.init.CLICKED");
            showServices(selectedCertId);
        });
        knownCertList.setAdapter(knownCertListAdapter);
        serviceList = rootView.findViewById(R.id.listDrives);
        drivesListAdapter = new ServicesListAdapter(rootView.getContext());
        serviceList.setAdapter(drivesListAdapter);
        serviceList.setOnItemClickListener((parent, view, position, id) -> {
            selectedService = drivesListAdapter.getItemT(position);
            int colour = ContextCompat.getColor(rootView.getContext(), R.color.colorListSelected);
            view.setBackgroundColor(colour);
            onServiceSelected(selectedCertId, selectedService);
        });
        RadioGroup radioGroup = rootView.findViewById(R.id.rdgClient);
        if (radioGroup != null) {
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkRadioButtons())
                    onRbServerSelected();
                else
                    onRbClientSelected();
            });
            checkRadioButtons();
        }
    }

    protected void onServiceSelected(Long selectedCertId, ServiceJoinServiceType selectedService) {

    }


    private void showServices(Long selectedCertId) {
        List<ServiceJoinServiceType> services = melAuthService.getNetworkEnvironment().getServices(selectedCertId);
        List<ServiceJoinServiceType> filtered = new ArrayList<>();
        if (services != null) {
            for (ServiceJoinServiceType service : services) {
                if (bootloader.isCompatiblePartner(service))
                    filtered.add(service);
            }
        }
        drivesListAdapter.clear();
        drivesListAdapter.addAll(filtered);
        drivesListAdapter.notifyDataSetChanged();
    }

    private void hideServiceChooser() {
        knownCertList.setVisibility(View.INVISIBLE);
        serviceList.setVisibility(View.INVISIBLE);
        lblServices.setVisibility(View.INVISIBLE);
        lblKnownMA.setVisibility(View.INVISIBLE);
    }

    private void showServiceChooser() {
        knownCertList.setVisibility(View.VISIBLE);
        serviceList.setVisibility(View.VISIBLE);
        lblServices.setVisibility(View.VISIBLE);
        lblKnownMA.setVisibility(View.VISIBLE);
    }


    private boolean checkRadioButtons() {
        if (rdServer.isChecked()) {
            hideServiceChooser();
            selectedCertId = null;
            return true;
        } else {
            showServiceChooser();
            try {
                selectedCertId = null;
                selectedService = null;
                knownCertListAdapter.clear();
                drivesListAdapter.clear();
                NetworkEnvironment env = melAuthService.getNetworkEnvironment();
                env.addObserver((o, arg) -> {
                    if (selectedCertId != null) {
                        showServices(selectedCertId);
                    }
                });
                for (Long certId : melAuthService.getConnectedUserIds()) {
                    Certificate cert = melAuthService.getCertificateManager().getTrustedCertificateById(certId);
                    knownCertListAdapter.add(cert);
                }
                knownCertListAdapter.notifyDataSetChanged();
                drivesListAdapter.notifyDataSetChanged();
                melAuthService.discoverNetworkEnvironment();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    protected void onRbServerSelected() {

    }

    protected void onRbClientSelected() {

    }

    public boolean isServer() {
        return rdServer.isChecked();
    }

    public Long getSelectedCertId() {
        return selectedCertId;
    }

    public ServiceJoinServiceType getSelectedService() {
        return selectedService;
    }

    public void setCreateServiceController(CreateServiceController createServiceController) {
        this.createServiceController = createServiceController;
    }
}
