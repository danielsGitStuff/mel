package de.mein.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.mein.auth.data.NetworkEnvironment;
import mein.de.meindrive.R;

/**
 * Created by xor on 3/14/17.
 */

public class UnknownAuthListAdapter extends MeinListAdapter<NetworkEnvironment.UnknownAuthInstance> {

    private final NetworkEnvironment environment;

    public UnknownAuthListAdapter(Context context, NetworkEnvironment environment) {
        super(context);
        this.environment = environment;
    }


    @Override
    public View getView(int i, View view, ViewGroup parent) {
        NetworkEnvironment.UnknownAuthInstance ins = items.get(i);
        return MeinListAdapter.create2LineView(this, ins.getAddress(), "Port: " + ins.getPort() + " CertPort: " + ins.getPortCert());
    }


}
