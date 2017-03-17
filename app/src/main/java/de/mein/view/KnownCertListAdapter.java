package de.mein.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.annimon.stream.Stream;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.tools.NoTryRunner;
import mein.de.meindrive.R;

/**
 * Created by xor on 3/16/17.
 */

public class KnownCertListAdapter extends MeinListAdapter<Certificate> {

    public KnownCertListAdapter(Context context) {
        super(context);
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Certificate certificate = items.get(position);
        View v = layoutInflator.inflate(R.layout.meintauth_known_list_item, null);
        TextView lblName = (TextView) v.findViewById(R.id.lblName);
        lblName.setText(certificate.getName().v());
        TextView lblAddress = (TextView) v.findViewById(R.id.lblAddress);
        String line2 = (certificate.getAddress().v() != null) ? certificate.getAddress().v() + " " : "";
        lblAddress.setText(line2);
        TextView lblPorts = (TextView) v.findViewById(R.id.lblPorts);
        String line3 = (certificate.getPort().v() != null) ? "Port: " + certificate.getPort().v() + " " : "";
        line3 += (certificate.getCertDeliveryPort().v() != null) ? "CertPort: " + certificate.getCertDeliveryPort().v() : "";
        lblPorts.setText(line3);
        return v;
    }

}
