package de.mel.android.controller;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Observer;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.view.KnownCertListAdapter;
import de.mel.android.view.ServicesListAdapter;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;

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

    private final Observer networkObserver = (o, arg) -> {
        if (selectedCertId != null) {
            showServices(selectedCertId);
        }
    };

    public RemoteServiceChooserController(MelAuthService melAuthService, MainActivity activity, ViewGroup viewGroup, int embeddedResource, Bootloader bootloader) {
        super(activity, viewGroup, R.layout.embedded_create_service_chooser);
        this.melAuthService = melAuthService;
        this.bootloader = bootloader;
        chooserContent = rootView.findViewById(R.id.chooserContent);
        View root = View.inflate(activity, embeddedResource, chooserContent);
        initEmbedded();
        if (checkRadioButtons())
            onRbServerSelected();
        else
            onRbClientSelected();
    }

    @Override
    protected void init() {
        rdServer = rootView.findViewById(R.id.rdServer);
        rdClient = rootView.findViewById(R.id.rdClient);
        lblKnownMA = rootView.findViewById(R.id.lblKnownMA);
        lblServices = rootView.findViewById(R.id.lblServices);
        knownCertList = rootView.findViewById(R.id.knownCertList);
        knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        knownCertList.setAdapter(knownCertListAdapter);
        knownCertList.setOnItemClickListener((parent, view, position, id) -> {
            selectedCertId = knownCertListAdapter.getItemT(position).getId().v();
            Lok.debug("RemoteServiceChooserController: cert clicked: " + selectedCertId);
            knownCertListAdapter.setSelectedCertId(selectedCertId);
            selectedService = null;
            drivesListAdapter.setSelectedService(null);
            showServices(selectedCertId);
            onServiceSelected(selectedCertId, null);
        });

        serviceList = rootView.findViewById(R.id.listDrives);
        drivesListAdapter = new ServicesListAdapter(rootView.getContext());
        serviceList.setAdapter(drivesListAdapter);
        serviceList.setOnItemClickListener((parent, view, position, id) -> {
            selectedService = drivesListAdapter.getItemT(position);
            Lok.debug("RemoteServiceChooserController: service clicked: " + (selectedService != null ? selectedService.getName().v() : "null"));
            drivesListAdapter.setSelectedService(selectedService);
            onServiceSelected(selectedCertId, selectedService);
        });

        rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (melAuthService != null) {
                    melAuthService.getNetworkEnvironment().deleteObserver(networkObserver);
                }
            }
        });

        RadioGroup radioGroup = rootView.findViewById(R.id.rdgClient);
        if (radioGroup != null) {
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkRadioButtons())
                    onRbServerSelected();
                else
                    onRbClientSelected();
            });
        }
    }

    protected void onServiceSelected(Long selectedCertId, ServiceJoinServiceType selectedService) {

    }


    private void showServices(Long selectedCertId) {
        if (melAuthService == null || bootloader == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (this.selectedCertId == null || !this.selectedCertId.equals(selectedCertId)) {
                return;
            }
            if (melAuthService == null || bootloader == null) {
                return;
            }
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
            if (selectedService != null) {
                boolean stillPresent = false;
                for (ServiceJoinServiceType s : filtered) {
                    if (s.getUuid() != null && selectedService.getUuid() != null &&
                            s.getUuid().v().equals(selectedService.getUuid().v())) {
                        stillPresent = true;
                        selectedService = s;
                        break;
                    }
                }
                if (!stillPresent) {
                    selectedService = null;
                    onServiceSelected(selectedCertId, null);
                }
            }
            drivesListAdapter.setSelectedService(selectedService);
        });
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
        if (rdServer == null || melAuthService == null) {
            return true;
        }
        if (rdServer.isChecked()) {
            hideServiceChooser();
            selectedCertId = null;
            selectedService = null;
            if (knownCertListAdapter != null) {
                knownCertListAdapter.setSelectedCertId(null);
            }
            if (drivesListAdapter != null) {
                drivesListAdapter.setSelectedService(null);
            }
            melAuthService.getNetworkEnvironment().deleteObserver(networkObserver);
            return true;
        } else {
            showServiceChooser();
            try {
                selectedCertId = null;
                selectedService = null;
                knownCertListAdapter.clear();
                knownCertListAdapter.setSelectedCertId(null);
                drivesListAdapter.clear();
                drivesListAdapter.setSelectedService(null);
                NetworkEnvironment env = melAuthService.getNetworkEnvironment();
                env.deleteObserver(networkObserver);
                env.addObserver(networkObserver);
                for (Long certId : melAuthService.getConnectedUserIds()) {
                    Certificate cert = melAuthService.getCertificateManager().getTrustedCertificateById(certId);
                    if (cert != null) {
                        knownCertListAdapter.add(cert);
                    }
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
        if (createServiceController != null) {
            if (isServer()) {
                onRbServerSelected();
            } else {
                onServiceSelected(selectedCertId, selectedService);
            }
        }
    }
}
