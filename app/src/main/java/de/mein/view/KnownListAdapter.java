package de.mein.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.tools.NoTryRunner;
import mein.de.meindrive.R;

/**
 * Created by xor on 3/16/17.
 */

public class KnownListAdapter extends MeinListAdapter<Long> {
    private final CertificateManager certificateManager;
    private Map<Long, Certificate> map = new HashMap<>();

    public KnownListAdapter(Context context, CertificateManager certificateManager) {
        super(context);
        this.certificateManager = certificateManager;
    }

    @Override
    public MeinListAdapter<Long> addAll(Collection<Long> items) {
        super.addAll(items);
        NoTryRunner.run(() -> {
            for (Long id : items) {
                map.put(id, certificateManager.getCertificateById(id));
            }
        });
        return this;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Certificate certificate = map.get(items.get(position));
        View v = layoutInflator.inflate(R.layout.meintauth_known_list_item, null);
        TextView lblName = (TextView) v.findViewById(R.id.lblName);
        lblName.setText(certificate.getName().v());
        TextView lblAddress = (TextView) v.findViewById(R.id.lblAddress);
        lblAddress.setText(certificate.getAddress().v() + " Port: " + certificate.getPort() + " CertPort: " + certificate.getCertDeliveryPort());
        return v;
    }
}
