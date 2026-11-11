package de.mel.android.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import de.mel.R;
import de.mel.auth.data.db.Certificate;

/**
 * Created by xor on 3/16/17.
 */

public class KnownCertListAdapter extends MelListAdapter<Certificate> {

    private Long selectedCertId = null;

    public KnownCertListAdapter(Context context) {
        super(context);
    }

    public void setSelectedCertId(Long selectedCertId) {
        this.selectedCertId = selectedCertId;
        notifyDataSetChanged();
    }

    public Long getSelectedCertId() {
        return selectedCertId;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        Certificate certificate = items.get(position);
        if (v == null)
            v = layoutInflator.inflate(R.layout.line_3_list_item, null);
        TextView lblName = v.findViewById(R.id.lbl1);
        lblName.setText(certificate.getName().v());
        TextView lblAddress = v.findViewById(R.id.lbl2);
        String line2 = (certificate.getAddress().v() != null) ? certificate.getAddress().v() + " " : "";
        lblAddress.setText(line2);
        TextView lblPorts = v.findViewById(R.id.lbl3);
        String line3 = (certificate.getPort().v() != null) ? "Port: " + certificate.getPort().v() + " " : "";
        line3 += (certificate.getCertDeliveryPort().v() != null) ? "CertPort: " + certificate.getCertDeliveryPort().v() : "";
        lblPorts.setText(line3);

        boolean isSelected = false;
        if (selectedCertId != null && certificate.getId() != null) {
            isSelected = selectedCertId.equals(certificate.getId().v());
        }
        if (isSelected) {
            v.setBackgroundColor(ContextCompat.getColor(v.getContext(), R.color.colorListSelected));
        } else {
            v.setBackgroundResource(R.drawable.list_selector);
        }
        return v;
    }

}
