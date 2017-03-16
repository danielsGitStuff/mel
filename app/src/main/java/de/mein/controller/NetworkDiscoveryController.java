package de.mein.controller;

import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import de.mein.android.AndroidService;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.view.KnownListAdapter;
import de.mein.view.UnknownAuthListAdapter;
import mein.de.meindrive.R;

/**
 * Created by xor on 3/7/17.
 */

public class NetworkDiscoveryController implements GuiController {
    private final MeinAuthService meinAuthService;
    private final View rootView;
    private final NetworkEnvironment environment;
    private final KnownListAdapter knownListAdapter;
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
        knownListAdapter = new KnownListAdapter(rootView.getContext(), meinAuthService.getCertificateManager());
        listKnown.setAdapter(knownListAdapter);
        listKnown.setOnItemClickListener((parent, view, position, id) -> {
            System.out.println("NetworkDiscoveryController.NetworkDiscoveryController");
            Certificate c = knownListAdapter.getCertificate(knownListAdapter.getItemT(position));
            txtAddress.setText(c.getAddress().v());
            txtPort.setText(c.getPort().v());
            txtDeliveryPort.setText(c.getCertDeliveryPort().v());
        });
        discover();
        //environment.addUnkown("testAddress", 777, 888);
    }


    private void discover() {
        environment.deleteObservers();
        environment.deleteObservers();
        environment.addObserver((observable, o) -> {
            System.out.println("NetworkDiscoveryController.discover");
            unkownListAdapter.clear().addAll(environment.getUnknownAuthInstances());
            knownListAdapter.clear().addAll(environment.getCertificateIds());
            unkownListAdapter.notifyDataSetChanged();
            knownListAdapter.notifyDataSetChanged();
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
//                    Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
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
