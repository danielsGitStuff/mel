package de.mein.android.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mein.R;
import de.mein.auth.data.db.ServiceJoinServiceType;

/**
 * Created by xor on 3/17/17.
 */

public class ApprovalCBListAdapter extends BaseAdapter {

    public interface ApprovalCheckedListener {
        void approvalCheck(Long serviceId, boolean checked);
    }

    private final Context context;
    private final LayoutInflater layoutInflator;
    private List<ServiceJoinServiceType> serviceJoinServiceTypes = new ArrayList<>();
    private Map<Long, Boolean> approvalMap = new HashMap<>();
    private CompoundButton.OnCheckedChangeListener checkChangedListener;
    private ApprovalCheckedListener approvalCheckedListener;

    public ApprovalCBListAdapter(Context context) {
        this.context = context;
        this.layoutInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setApprovalCheckedListener(ApprovalCheckedListener approvalCheckedListener) {
        this.approvalCheckedListener = approvalCheckedListener;
    }

    @Override
    public int getCount() {
        return serviceJoinServiceTypes.size();
    }

    @Override
    public Object getItem(int i) {
        return serviceJoinServiceTypes.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ServiceJoinServiceType service = serviceJoinServiceTypes.get(i);
        boolean checked = approvalMap.get(service.getServiceId().v());
        View v = layoutInflator.inflate(R.layout.service_list_item, null);
        CheckBox cb = (CheckBox) v.findViewById(R.id.cbApproved);
        cb.setText(service.getName().v());
        cb.setChecked(checked);
        cb.setOnCheckedChangeListener((compoundButton, b) -> approvalCheckedListener.approvalCheck(service.getServiceId().v(), cb.isChecked()));
//        cb.setOnClickListener(v1 -> {
//            boolean approved = cb.isChecked();
//            approvalMap.put(service.getServiceId().v(), approved);
//        });
        return v;
    }


    public void clear() {
        serviceJoinServiceTypes = new ArrayList<>();
        approvalMap = new HashMap<>();
    }

    public ApprovalCBListAdapter add(ServiceJoinServiceType serviceJoinServiceType, @NonNull boolean approved) {
        serviceJoinServiceTypes.add(serviceJoinServiceType);
        approvalMap.put(serviceJoinServiceType.getServiceId().v(), approved);
        return this;
    }

    public ServiceJoinServiceType getItemT(int pos) {
        return serviceJoinServiceTypes.get(pos);
    }

    public Boolean isApproved(Long serviceId) {
        return approvalMap.get(serviceId);
    }

    public void setCheckedChangedListener(CompoundButton.OnCheckedChangeListener checkChangedListener) {
        this.checkChangedListener = checkChangedListener;
    }
}
