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


    private void discover() {
        CertificateManager certificateManager = androidService.getMelAuthService().getCertificateManager();
        environment.deleteObservers();
        environment.deleteObservers();
        environment.addObserver((observable, o) -> {
            Lok.debug("NetworkDiscoveryController.discover.adds: " + environment.getUnknownAuthInstances().size());
            if (environment.getUnknownAuthInstances().size() == 2)
                Lok.debug("debug");
            unkownListAdapter.clear().addAll(environment.getUnknownAuthInstances());
            knownCertListAdapter.clear();
            for (Long certId : environment.getCertificateIds()) {
                N.r(() -> {
                    Certificate c = certificateManager.getTrustedCertificateById(certId);
                    knownCertListAdapter.add(c);
                });
            }
            activity.runOnUiThread(() -> {
                unkownListAdapter.notifyDataSetChanged();
                knownCertListAdapter.notifyDataSetChanged();
            });
        });
//        environment.deleteObservers();
//        environment.deleteObservers();
//        environment.addObserver((o, arg) -> {
//            Lok.debug("NetworkDiscoveryController.observed something!!!!!!!!");
//            listUnkown.removeAllViews();
//            listAll.getItems().clear();
//            listAll.getItems().setPaths(environment.getUnknownAuthInstances());
//            listKnown.getItems().clear();
//            for (Long certId : environment.getCertificateIds()) {
//                try {
//                    Lok.debug("NetworkDiscoveryController.discover");
//                    Certificate certificate = melAuthService.getCertificateManager().getTrustedCertificateById(certId);
//                    listKnown.getItems().add(certificate);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
        androidService.getMelAuthService().discoverNetworkEnvironment();
    }

    @Override
    public Integer getTitle() {
        return R.string.discoverTitle;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (environment != null)
            environment.deleteObservers();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (environment != null)
            environment.deleteObservers();
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
                    androidService.getMelAuthService().connect(address, port, portCert, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        activity.runOnUiThread(() -> {
            listUnkown.setAdapter(unkownListAdapter);
            listKnown.setAdapter(knownCertListAdapter);
        });
        discover();
    }

    @Override
    public void onAndroidServiceUnbound() {

    }

    @Override
    public Integer getHelp() {
        return R.string.discoveryHelp;
    }
}
