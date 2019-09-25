package de.mel.android.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import de.mel.auth.data.db.ServiceJoinServiceType;

/**
 * Created by xor on 3/20/17.
 */

public class ServicesListAdapter extends MelListAdapter<ServiceJoinServiceType> {
    public ServicesListAdapter(Context context) {
        super(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ServiceJoinServiceType service = items.get(position);
        return MelListAdapter.create2LineView(this, service.getName().v(), service.getUuid().v());
    }
}
