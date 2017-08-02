package de.mein.android.controller;

import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import com.annimon.stream.Stream;

import java.util.List;
import java.util.Map;

import de.mein.R;
import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.db.Approval;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.SqlQueriesException;
import de.mein.android.AndroidService;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.android.view.ApprovalCBListAdapter;

/**
 * Created by xor on 2/20/17.
 */

public class ApprovalController extends GuiController {
    private final View rootView;
    private final MeinAuthService meinAuthService;
    private final ListView listCertificates, listServices;
    private final KnownCertListAdapter knownCertListAdapter;
    private final ApprovalCBListAdapter serviceAdapter;
    private ApprovalMatrix matrix;
    private Long selectedCertId;

    public ApprovalController(MeinAuthService meinAuthService, View rootView) throws SqlQueriesException {
        this.rootView = rootView;
        this.listCertificates = (ListView) rootView.findViewById(R.id.listCertificates);
        this.listServices = (ListView) rootView.findViewById(R.id.listServices);
        this.meinAuthService = meinAuthService;
        this.knownCertListAdapter = new KnownCertListAdapter(rootView.getContext());
        this.serviceAdapter = new ApprovalCBListAdapter(rootView.getContext());
        listCertificates.setAdapter(knownCertListAdapter);
        listServices.setAdapter(serviceAdapter);
        Button btnApply = (Button) rootView.findViewById(R.id.btnApply);
        btnApply.setOnClickListener(v -> {
            try {
                meinAuthService.getDatabaseManager().saveApprovals(this.matrix);
            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
        });
        System.out.println("ApprovalController.ApprovalController");
        fillContent();
    }

    private void fillContent() throws SqlQueriesException {
        if (meinAuthService != null) {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            List<Certificate> certificates = meinAuthService.getCertificateManager().getTrustedCertificates();
            List<Approval> approvals = meinAuthService.getDatabaseManager().getAllApprovals();
            matrix = new ApprovalMatrix();
            matrix.fill(certificates, services, approvals);

            //insert some test data here
            /*
            Certificate c = new Certificate()
                    .setAddress("c address")
                    .setAnswerUuid("c answer uuid")
                    .setUuid("c uuid")
                    .setCertDeliveryPort(8889)
                    .setPort(8888)
                    .setTrusted(true)
                    .setId(1L)
                    .setName("c name");
            certificates.add(c);

            ServiceJoinServiceType service = new ServiceJoinServiceType();
            service.getName().v("s1 name");
            service.getDescription().v("s1 desc");
            service.getType().v("s1 type");
            service.getServiceId().v(1L);
            service.getUuid().v("s1 uuid");

            services.add(service);
            matrix.approve(c.getId().v(), service.getServiceId().v());

            service = new ServiceJoinServiceType();
            service.getName().v("s2 name");
            service.getDescription().v("s2 desc");
            service.getType().v("s2 type");
            service.getServiceId().v(2L);
            service.getUuid().v("s2 uuid");

            services.add(service);
            matrix.disapprove(c.getId().v(), service.getServiceId().v());
            */
            // end test data

            matrix.getMatrix();
            knownCertListAdapter.clear();
            serviceAdapter.clear();
            for (Certificate certificate : certificates) {
                knownCertListAdapter.add(certificate);
                Stream.of(services).forEach(serviceJoinServiceType -> {
                    long serviceId = serviceJoinServiceType.getServiceId().v();
                    Map<Long, Approval> certIdApprovalList = matrix.getMatrix().get(serviceId);

                });
            }
            knownCertListAdapter.notifyDataSetChanged();
            serviceAdapter.notifyDataSetChanged();
            listCertificates.setOnItemClickListener((parent, view, position, id) -> {
                Certificate cert = knownCertListAdapter.getItemT(position);
                selectedCertId = cert.getId().v();
                System.out.println("ApprovalController.fillContent.CLICKED: " + cert.getName().v());
                serviceAdapter.clear();
                for (ServiceJoinServiceType s : services) {
                    // check if it is approved
                    boolean approved = false;
                    long serviceId = s.getServiceId().v();
                    if (matrix.getMatrix().containsKey(serviceId)) {
                        approved = matrix.getMatrix().get(serviceId).get(selectedCertId) != null;
                    }
                    serviceAdapter.add(s, approved);
                }
                serviceAdapter.notifyDataSetChanged();
            });
        }
        listServices.setOnItemClickListener((parent, view, position, id) -> {
            System.out.println("ApprovalController.fillContent.CLICKED:S");
            ServiceJoinServiceType service = serviceAdapter.getItemT(position);
            Long serviceId = service.getServiceId().v();
            if (serviceAdapter.isApproved(serviceId)) {
                matrix.approve(selectedCertId, serviceId);
            } else {
                matrix.disapprove(selectedCertId, serviceId);
            }
        });

    }

    @Override
    public void onMeinAuthStarted(MeinAuthService androidService) {

    }

    @Override
    public void onAndroidServiceBound(AndroidService androidService) {

    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }
}