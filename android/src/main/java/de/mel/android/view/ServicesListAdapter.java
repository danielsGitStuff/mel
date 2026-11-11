package de.mel.android.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import de.mel.R;
import de.mel.auth.data.db.ServiceJoinServiceType;

/**
 * Created by xor on 3/20/17.
 */

public class ServicesListAdapter extends MelListAdapter<ServiceJoinServiceType> {
    private ServiceJoinServiceType selectedService = null;

    public ServicesListAdapter(Context context) {
        super(context);
    }

    public void setSelectedService(ServiceJoinServiceType selectedService) {
        this.selectedService = selectedService;
        notifyDataSetChanged();
    }

    public ServiceJoinServiceType getSelectedService() {
        return selectedService;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ServiceJoinServiceType service = items.get(position);
        View v;
        if (convertView != null) {
            v = convertView;
            TextView lbl1 = v.findViewById(R.id.lbl1);
            lbl1.setText(service.getName().v());
            TextView lbl2 = v.findViewById(R.id.lbl2);
            lbl2.setText(service.getUuid().v());
        } else {
            v = MelListAdapter.create2LineView(this, service.getName().v(), service.getUuid().v());
        }

        boolean isSelected = false;
        if (selectedService != null && selectedService.getUuid() != null && service.getUuid() != null) {
            isSelected = selectedService.getUuid().v().equals(service.getUuid().v());
        }
        if (isSelected) {
            v.setBackgroundColor(ContextCompat.getColor(v.getContext(), R.color.colorListSelected));
        } else {
            v.setBackgroundResource(R.drawable.list_selector);
        }
        return v;
    }
}
