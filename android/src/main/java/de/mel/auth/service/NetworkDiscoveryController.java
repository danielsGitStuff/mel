package de.mel.auth.service;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.controller.WakelockedGuiController;
import de.mel.android.service.AndroidPowerManager;
import de.mel.android.service.AndroidService;
import de.mel.android.view.KnownCertListAdapter;
import de.mel.android.view.UnknownAuthListAdapter;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.tools.N;

import java.util.ArrayList;
import java.util.List;
import java.util.Observer;

/**
 * Created by xor on 3/7/17.
 */

public class NetworkDiscoveryController extends WakelockedGuiController {
    private NetworkEnvironment environment;
    private KnownCertListAdapter knownCertListAdapter;
    private ListView listKnown, listUnkown;
    private UnknownAuthListAdapter unkownListAdapter;
    private final EditText txtAddress, txtPort, txtDeliveryPort;
    private final Button btnConnect;

    public NetworkDiscoveryController(MainActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_discover);
        this.listKnown = rootView.findViewById(R.id.listKnown);
        this.listUnkown = rootView.findViewById(R.id.listUnknown);
        this.txtDeliveryPort = rootView.findViewById(R.id.txtDeliveryPort);
        this.txtPort = rootView.findViewById(R.id.txtPort);
        this.txtAddress = rootView.findViewById(R.id.txtAddress);
        this.btnConnect = rootView.findViewById(R.id.btnConnect);

    }

    private void updateLists() {
        if (isDestroyed() || environment == null || androidService == null) return;
        CertificateManager certificateManager = androidService.getMelAuthService().getCertificateManager();
        List<NetworkEnvironment.UnknownAuthInstance> unknowns = new ArrayList<>(environment.getUnknownAuthInstances());
        List<Certificate> knowns = new ArrayList<>();
        for (Long certId : environment.getCertificateIds()) {
            try {
                Certificate c = certificateManager.getTrustedCertificateById(certId);
                if (c != null) knowns.add(c);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        runOnUiThreadSafely(() -> {
            if (unkownListAdapter != null) {
                unkownListAdapter.clear().addAll(unknowns);
                unkownListAdapter.notifyDataSetChanged();
            }
            if (knownCertListAdapter != null) {
                knownCertListAdapter.clear().addAll(knowns);
                knownCertListAdapter.notifyDataSetChanged();
            }
        });
    }

    private final Observer envObserver = (observable, o) -> updateLists();

    private void discover() {
        if (environment != null) {
            environment.deleteObserver(envObserver);
            environment.addObserver(envObserver);
            updateLists();
        }
        if (androidService != null && androidService.getMelAuthService() != null) {
            androidService.getMelAuthService().discoverNetworkEnvironment();
        }
    }


    @Override
    public Integer getTitle() {
        return R.string.discoverTitle;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (environment != null)
            environment.deleteObserver(envObserver);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (environment != null)
            environment.deleteObserver(envObserver);
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        environment = androidService.getMelAuthService().getNetworkEnvironment();
        unkownListAdapter = new UnknownAuthListAdapter(rootView.getContext(), environment);
        listUnkown.setOnItemClickListener((parent, view, position, id) -> {
            Lok.debug("NetworkDiscoveryController.NetworkDiscoveryController");
            NetworkEnvironment.UnknownAuthInstance unknown = unkownListAdapter.getItemT(position);
            txtAddress.setText(unknown.getAddress());
            txtPort.setText(Integer.toString(unknown.getPort()));
            txtDeliveryPort.setText(Integer.toString(unknown.getPortCert()));
        });
        knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        listKnown.setOnItemClickListener((parent, view, position, id) -> {
            Lok.debug("NetworkDiscoveryController.NetworkDiscoveryController");
            Certificate c = knownCertListAdapter.getItemT(position);
            txtAddress.setText(c.getAddress().v());
            txtPort.setText(c.getPort().v());
            txtDeliveryPort.setText(c.getCertDeliveryPort().v());
        });
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String address = txtAddress.getText().toString();
                Integer port = Integer.parseInt(txtPort.getText().toString());
                Integer portCert = Integer.parseInt(txtDeliveryPort.getText().toString());
                try {
                    androidService.getMelAuthService().connect(address, port, portCert, true)
                            .onDone(result -> discover())
                            .onError(e -> Lok.error("Failed to connect/pair with " + address + ": " + e));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        runOnUiThreadSafely(() -> {
            listUnkown.setAdapter(unkownListAdapter);
            listKnown.setAdapter(knownCertListAdapter);
        });
        discover();
    }

    @Override
    public void onAndroidServiceUnbound() {
        super.onAndroidServiceUnbound();
    }

    @Override
    public Integer getHelp() {
        return R.string.discoveryHelp;
    }
}
