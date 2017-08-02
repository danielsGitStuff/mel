package de.mein.android.controller;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import de.mein.R;
import de.mein.android.AndroidService;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 3/27/17.
 */

public class OthersController extends GuiController {
    private final View rootView;
    private final MeinAuthService meinAuthService;
    private Button btnDelete;
    private ListView listCertificates;
    private KnownCertListAdapter listCertAdapter;
    private Certificate selectedCert;

    public OthersController(MeinAuthService meinAuthService, View rootView) {
        this.rootView = rootView;
        this.meinAuthService = meinAuthService;
        try {
            btnDelete = (Button) rootView.findViewById(R.id.btnDelete);
            listCertificates = (ListView) rootView.findViewById(R.id.listCertificates);
            listCertAdapter = new KnownCertListAdapter(rootView.getContext());
            listCertAdapter.addAll(meinAuthService.getTrustedCertificates());
            listCertificates.setAdapter(listCertAdapter);
            btnDelete.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    try {
                        if (selectedCert != null)
                            meinAuthService.getCertificateManager().deleteCertificate(selectedCert);
                        selectedCert = null;
                    } catch (SqlQueriesException e) {
                        e.printStackTrace();
                    }
                }
            });
            listCertificates.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    selectedCert = listCertAdapter.getItemT(position);
                }
            });
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMeinAuthStarted(MeinAuthService androidService) {

    }

    @Override
    public void onAndroidServiceBound(AndroidService androidService) {

    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }
}
