package de.mein.android.controller;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import de.mein.R;
import de.mein.android.service.AndroidService;
import de.mein.android.MeinActivity;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 3/27/17.
 */

public class OthersController extends GuiController {
    private Button btnDelete;
    private ListView listCertificates;
    private KnownCertListAdapter listCertAdapter;
    private Certificate selectedCert;

    public OthersController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_others);
        btnDelete = rootView.findViewById(R.id.btnDelete);
        listCertificates = rootView.findViewById(R.id.listCertificates);
        listCertAdapter = new KnownCertListAdapter(rootView.getContext());
        listCertificates.setAdapter(listCertAdapter);
        btnDelete.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    if (selectedCert != null) {
                        androidService.getMeinAuthService().getCertificateManager().deleteCertificate(selectedCert);
                    }
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
    }


    @Override
    public String getTitle() {
        return "Others";
    }

    @Override
    public void onAndroidServiceAvailable() {
        activity.runOnUiThread(() -> N.r(() -> {
            listCertAdapter.addAll(androidService.getMeinAuthService().getTrustedCertificates());
        }));
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }
}
