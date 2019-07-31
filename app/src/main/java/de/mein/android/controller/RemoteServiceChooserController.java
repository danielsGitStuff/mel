package de.mein.android.controller;

import androidx.core.content.ContextCompat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.MeinActivity;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.android.view.ServicesListAdapter;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;

/**
 * Created by xor on 10/4/17.
 */

public abstract class RemoteServiceChooserController extends AndroidServiceGuiController {


    protected CreateServiceController createServiceController;

    protected abstract void initEmbedded();

    private RadioButton rdServer, rdClient;
    private Long selectedCertId = null;
    private ServiceJoinServiceType selectedService;
    private KnownCertListAdapter knownCertListAdapter;
    private ServicesListAdapter drivesListAdapter;
    protected MeinAuthService meinAuthService;
    private ListView knownCertList, serviceList;
    private ViewGroup chooserContent;
    private TextView lblKnownMA, lblServices;

    public RemoteServiceChooserController(MeinAuthService meinAuthService, MainActivity activity, ViewGroup viewGroup, int embeddedResource) {
        super(activity, viewGroup, R.layout.embedded_create_service_chooser);
        this.meinAuthService = meinAuthService;
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

    protected abstract boolean showService(ServiceJoinServiceType service);

    private void showServices(Long selectedCertId) {
        List<ServiceJoinServiceType> services = meinAuthService.getNetworkEnvironment().getServices(selectedCertId);
        List<ServiceJoinServiceType> filtered = new ArrayList<>();
        if (services != null) {
            for (ServiceJoinServiceType service : services) {
                if (showService(service))
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
                NetworkEnvironment env = meinAuthService.getNetworkEnvironment();
                env.addObserver((o, arg) -> {
                    if (selectedCertId != null) {
                        showServices(selectedCertId);
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
