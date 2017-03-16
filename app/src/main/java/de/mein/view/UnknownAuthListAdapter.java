package de.mein.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

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
        View v = layoutInflator.inflate(R.layout.meintauth_unknown_list_item, null);
        TextView lblAddress = (TextView) v.findViewById(R.id.lblAddress);
        lblAddress.setText(ins.getAddress());
        TextView lblPorts = (TextView) v.findViewById(R.id.lblPorts);
        lblPorts.setText("Port: " + ins.getPort() + " CertPort: " + ins.getPortCert());
        return v;
    }


}
