package de.mein.android.controller;

import android.app.AlertDialog;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import de.mein.R;
import de.mein.android.Notifier;
import de.mein.android.service.AndroidService;
import de.mein.android.MeinActivity;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.tools.N;

/**
 * Created by xor on 3/27/17.
 */

public class ConnectedController extends GuiController {
    private Button btnDelete;
    private ListView listCertificates;
    private KnownCertListAdapter listCertAdapter;
    private Certificate selectedCert;

    public ConnectedController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_connected);
        btnDelete = rootView.findViewById(R.id.btnDelete);
        listCertificates = rootView.findViewById(R.id.listCertificates);
        listCertificates.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listCertAdapter = new KnownCertListAdapter(rootView.getContext());
        listCertificates.setAdapter(listCertAdapter);
        btnDelete.setOnClickListener(v -> {
            if (selectedCert != null) {
                activity.runOnUiThread(() -> N.r(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setCancelable(true)
                            .setTitle(R.string.confirmCertDeletionTitle)
                            .setMessage(R.string.confirmCertDeletionMessage)
                            .setPositiveButton(R.string.btnOk, (dialog, which) -> N.r(() -> {
                                listCertAdapter.clear();
                                listCertAdapter.addAll(androidService.getMeinAuthService().getTrustedCertificates());
                                listCertificates.clearChoices();
                                selectedCert = null;
                                listCertAdapter.notifyDataSetChanged();
                            })).setNegativeButton(R.string.btnCancel, (dialog, which) -> Notifier.toast(activity, R.string.notificationCertNotDeleted));
                    builder.show();
                }));
            }
        });
        listCertificates.setOnItemClickListener((parent, view, position, id) -> selectedCert = listCertAdapter.getItemT(position));
    }


    @Override
    public Integer getTitle() {
        return R.string.connectedTitle;
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        activity.runOnUiThread(() -> N.r(() -> {
            listCertAdapter.addAll(androidService.getMeinAuthService().getTrustedCertificates());
            listCertAdapter.notifyDataSetChanged();
        }));
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }

    @Override
    public Integer getHelp() {
        return R.string.connectedHelp;
    }
}
