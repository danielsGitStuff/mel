package de.mein.android.controller;

import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import com.annimon.stream.Stream;

import de.mein.android.AndroidService;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.NoTryRunner;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.android.view.UnknownAuthListAdapter;
import mein.de.meindrive.R;

/**
 * Created by xor on 3/7/17.
 */

public class NetworkDiscoveryController implements GuiController {
    private final MeinAuthService meinAuthService;
    private final View rootView;
    private final NetworkEnvironment environment;
    private final KnownCertListAdapter knownCertListAdapter;
    private ListView listKnown, listUnkown;
    private UnknownAuthListAdapter unkownListAdapter;
    private final EditText txtAddress, txtPort, txtDeliveryPort;

    public NetworkDiscoveryController(MeinAuthService meinAuthService, View rootView) {
        this.meinAuthService = meinAuthService;
        this.rootView = rootView;
        this.listKnown = (ListView) rootView.findViewById(R.id.listKnown);
        this.listUnkown = (ListView) rootView.findViewById(R.id.listUnknown);
        this.txtDeliveryPort = (EditText) rootView.findViewById(R.id.txtDeliveryPort);
        this.txtPort = (EditText) rootView.findViewById(R.id.txtPort);
        this.txtAddress = (EditText) rootView.findViewById(R.id.txtAddress);
        environment = meinAuthService.getNetworkEnvironment();
        unkownListAdapter = new UnknownAuthListAdapter(rootView.getContext(), environment);
        listUnkown.setAdapter(unkownListAdapter);
        listUnkown.setOnItemClickListener((parent, view, position, id) -> {
            System.out.println("NetworkDiscoveryController.NetworkDiscoveryController");
            NetworkEnvironment.UnknownAuthInstance unknown = unkownListAdapter.getItemT(position);
            txtAddress.setText(unknown.getAddress());
            txtPort.setText(Integer.toString(unknown.getPort()));
            txtDeliveryPort.setText(Integer.toString(unknown.getPortCert()));
        });
        knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        listKnown.setAdapter(knownCertListAdapter);
        listKnown.setOnItemClickListener((parent, view, position, id) -> {
            System.out.println("NetworkDiscoveryController.NetworkDiscoveryController");
            Certificate c = knownCertListAdapter.getItemT(position);
            txtAddress.setText(c.getAddress().v());
            txtPort.setText(c.getPort().v());
            txtDeliveryPort.setText(c.getCertDeliveryPort().v());
        });
        discover();
        //environment.addUnkown("testAddress", 777, 888);
    }


    private void discover() {
        CertificateManager certificateManager = meinAuthService.getCertificateManager();
        environment.deleteObservers();
        environment.deleteObservers();
        environment.addObserver((observable, o) -> {
            System.out.println("NetworkDiscoveryController.discover");
            unkownListAdapter.clear().addAll(environment.getUnknownAuthInstances());
            knownCertListAdapter.clear();
            Stream.of(environment.getCertificateIds()).forEach(certId -> {
                NoTryRunner.run(() -> {
                    Certificate c = certificateManager.getTrustedCertificateById(certId);
                    knownCertListAdapter.add(c);
                });
            });
            unkownListAdapter.notifyDataSetChanged();
            knownCertListAdapter.notifyDataSetChanged();
        });
//        environment.deleteObservers();
//        environment.deleteObservers();
//        environment.addObserver((o, arg) -> {
//            System.out.println("NetworkDiscoveryController.observed something!!!!!!!!");
//            listUnkown.removeAllViews();
//            listAll.getItems().clear();
//            listAll.getItems().addAll(environment.getUnknownAuthInstances());
//            listKnown.getItems().clear();
//            for (Long certId : environment.getCertificateIds()) {
//                try {
//                    System.out.println("NetworkDiscoveryController.discover");
//                    Certificate certificate = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
//                    listKnown.getItems().add(certificate);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
        meinAuthService.discoverNetworkEnvironment();
    }

    @Override
    public void onMeinAuthStarted(MeinAuthService androidService) {
        System.out.println("NetworkDiscoveryController.onMeinAuthStarted");
    }

    @Override
    public void onAndroidServiceBound(AndroidService androidService) {
        System.out.println("NetworkDiscoveryController.onAndroidServiceBound");
    }
}
