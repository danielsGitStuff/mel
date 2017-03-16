package de.mein.controller;

import android.view.View;
import android.widget.ListView;

import java.util.List;

import de.mein.android.AndroidService;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.service.MeinAuthService;
import de.mein.view.UnknownAuthListAdapter;
import mein.de.meindrive.R;

/**
 * Created by xor on 3/7/17.
 */

public class NetworkDiscoveryController implements GuiController {
    private final MeinAuthService meinAuthService;
    private final View rootView;
    private final NetworkEnvironment environment;
    private ListView listKnown, listUnkown;
    private UnknownAuthListAdapter unkownListAdapter;

    public NetworkDiscoveryController(MeinAuthService meinAuthService, View rootView) {
        this.meinAuthService = meinAuthService;
        this.rootView = rootView;
        this.listKnown = (ListView) rootView.findViewById(R.id.listKnown);
        this.listUnkown = (ListView) rootView.findViewById(R.id.listUnknown);
        environment = meinAuthService.getNetworkEnvironment();
        unkownListAdapter = new UnknownAuthListAdapter(rootView.getContext(), environment);
        listUnkown.setAdapter(unkownListAdapter);

        //discover();
        //environment.addUnkown("testAddress", 777, 888);
    }


    private void discover() {
        environment.deleteObservers();
        environment.deleteObservers();
        environment.addObserver((observable, o) -> {
            System.out.println("NetworkDiscoveryController.discover");
            unkownListAdapter.clear().addAllUnknown(environment.getUnknownAuthInstances());
            unkownListAdapter.notifyDataSetChanged();
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
