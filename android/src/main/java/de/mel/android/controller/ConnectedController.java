package de.mel.android.controller;

import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.MelActivity;
import de.mel.android.Notifier;
import de.mel.android.service.AndroidService;
import de.mel.android.view.KnownCertListAdapter;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.CertificateAddress;
import de.mel.auth.tools.AddressParser;
import de.mel.auth.tools.N;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by xor on 3/27/17.
 */

public class ConnectedController extends GuiController {
    private Button btnDelete;
    private Button btnEditAddresses;
    private ListView listCertificates;
    private KnownCertListAdapter listCertAdapter;
    private Certificate selectedCert;

    public ConnectedController(MainActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_connected);
        btnDelete = rootView.findViewById(R.id.btnDelete);
        btnEditAddresses = rootView.findViewById(R.id.btnEditAddresses);
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
                                androidService.getMelAuthService().getCertificateManager().deleteCertificate(selectedCert);
                                listCertAdapter.clear();
                                listCertAdapter.addAll(androidService.getMelAuthService().getTrustedCertificates());
                                listCertificates.clearChoices();
                                selectedCert = null;
                                listCertAdapter.notifyDataSetChanged();
                            })).setNegativeButton(R.string.btnCancel, (dialog, which) -> Notifier.toast(activity, R.string.notificationCertNotDeleted));
                    builder.show();
                }));
            }
        });
        btnEditAddresses.setOnClickListener(v -> {
            if (selectedCert != null && selectedCert.getId().v() != null) {
                activity.runOnUiThread(() -> N.r(() -> openEditAddressesDialog(selectedCert)));
            }
        });
        listCertificates.setOnItemClickListener((parent, view, position, id) -> selectedCert = listCertAdapter.getItemT(position));
    }

    private void openEditAddressesDialog(Certificate cert) throws Exception {
        List<CertificateAddress> items = new ArrayList<>(
                androidService.getMelAuthService().getCertificateManager().getAlternativeAddresses(cert.getId().v())
        );
        List<String> labels = new ArrayList<>();
        for (CertificateAddress addr : items) {
            labels.add(AddressParser.format(addr));
        }

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        LinearLayout addRow = new LinearLayout(activity);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText editAddress = new EditText(activity);
        editAddress.setHint(R.string.addressPrompt);
        editAddress.setSingleLine(true);
        editAddress.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button btnAdd = new Button(activity);
        btnAdd.setText(R.string.btnAdd);
        addRow.addView(editAddress);
        addRow.addView(btnAdd);
        container.addView(addRow);

        ListView addressList = new ListView(activity);
        addressList.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_activated_1, labels);
        addressList.setAdapter(adapter);
        int listHeight = (int) (180 * activity.getResources().getDisplayMetrics().density);
        addressList.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, listHeight));
        container.addView(addressList);

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnUp = new Button(activity);
        btnUp.setText(R.string.btnUp);
        Button btnDown = new Button(activity);
        btnDown.setText(R.string.btnDown);
        Button btnRemove = new Button(activity);
        btnRemove.setText(R.string.btnRemove);
        actionRow.addView(btnUp);
        actionRow.addView(btnDown);
        actionRow.addView(btnRemove);
        container.addView(actionRow);

        final int[] selectedPos = {-1};
        addressList.setOnItemClickListener((parent, view, position, id) -> selectedPos[0] = position);

        btnAdd.setOnClickListener(v -> {
            try {
                CertificateAddress parsed = AddressParser.parse(editAddress.getText().toString());
                for (CertificateAddress existing : items) {
                    if (Objects.equals(existing.getAddress().v(), parsed.getAddress().v())
                            && Objects.equals(existing.getPort().v(), parsed.getPort().v())) {
                        editAddress.setError(activity.getString(R.string.duplicateAddress));
                        return;
                    }
                }
                items.add(parsed);
                labels.add(AddressParser.format(parsed));
                adapter.notifyDataSetChanged();
                editAddress.setText("");
                editAddress.setError(null);
            } catch (IllegalArgumentException ex) {
                editAddress.setError(ex.getMessage());
            }
        });

        btnUp.setOnClickListener(v -> {
            int pos = selectedPos[0];
            if (pos > 0 && pos < items.size()) {
                items.add(pos - 1, items.remove(pos));
                labels.add(pos - 1, labels.remove(pos));
                selectedPos[0] = pos - 1;
                addressList.setItemChecked(pos - 1, true);
                adapter.notifyDataSetChanged();
            }
        });

        btnDown.setOnClickListener(v -> {
            int pos = selectedPos[0];
            if (pos >= 0 && pos < items.size() - 1) {
                items.add(pos + 1, items.remove(pos));
                labels.add(pos + 1, labels.remove(pos));
                selectedPos[0] = pos + 1;
                addressList.setItemChecked(pos + 1, true);
                adapter.notifyDataSetChanged();
            }
        });

        btnRemove.setOnClickListener(v -> {
            int pos = selectedPos[0];
            if (pos >= 0 && pos < items.size()) {
                items.remove(pos);
                labels.remove(pos);
                selectedPos[0] = -1;
                addressList.clearChoices();
                adapter.notifyDataSetChanged();
            }
        });

        new AlertDialog.Builder(activity)
                .setCancelable(true)
                .setTitle(activity.getString(R.string.editAddresses) + " - " + cert.getName().v())
                .setView(container)
                .setPositiveButton(R.string.btnOk, (dialog, which) -> N.r(() ->
                        androidService.getMelAuthService().getCertificateManager().replaceAlternativeAddresses(cert.getId().v(), items)))
                .setNegativeButton(R.string.btnCancel, null)
                .show();
    }


    @Override
    public Integer getTitle() {
        return R.string.connectedTitle;
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        runOnUiThreadSafely(() -> N.r(() -> {
            listCertAdapter.clear();
            listCertAdapter.addAll(androidService.getMelAuthService().getTrustedCertificates());
            listCertAdapter.notifyDataSetChanged();
        }));
    }

    @Override
    public void onAndroidServiceUnbound() {
        super.onAndroidServiceUnbound();
    }

    @Override
    public Integer getHelp() {
        return R.string.connectedHelp;
    }
}
