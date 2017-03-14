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

public class UnknownAuthListAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater layoutInflator;
    private final NetworkEnvironment environment;
    private List<NetworkEnvironment.UnknownAuthInstance> unknownAuthInstances = new ArrayList<>();
    private Set<Long> certIdsFound = new HashSet<>();

    public UnknownAuthListAdapter(Context context, NetworkEnvironment environment) {
        this.context = context;
        this.environment = environment;
        this.layoutInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        unknownAuthInstances.add(new NetworkEnvironment.UnknownAuthInstance("bla", 1, 2));
        unknownAuthInstances.add(new NetworkEnvironment.UnknownAuthInstance("blabla", 3, 4));
    }

    public UnknownAuthListAdapter addUnknownAuthInstance(NetworkEnvironment.UnknownAuthInstance ins) {
        unknownAuthInstances.add(ins);
        return this;
    }

    @Override
    public int getCount() {
        return unknownAuthInstances.size();
    }

    @Override
    public Object getItem(int i) {
        return unknownAuthInstances.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 666;
    }

    @Override
    public View getView(int i, View view, ViewGroup parent) {
        NetworkEnvironment.UnknownAuthInstance ins = unknownAuthInstances.get(i);
        View v = layoutInflator.inflate(R.layout.meintauth_unknown_list_item, null);
        TextView lblAddress = (TextView) v.findViewById(R.id.lblAddress);
        lblAddress.setText(ins.getAddress());
        TextView lblPorts = (TextView) v.findViewById(R.id.lblPorts);
        lblPorts.setText("Port: " + ins.getPort() + " CertPort: " + ins.getPortCert());

        return v;
    }

    public UnknownAuthListAdapter clear() {
        unknownAuthInstances = new ArrayList<>();
        return this;
    }

    public UnknownAuthListAdapter addAllUnknown(List<NetworkEnvironment.UnknownAuthInstance> unknownAuthInstances) {
        this.unknownAuthInstances.addAll(unknownAuthInstances);
        return this;
    }
}
