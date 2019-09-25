package de.mel.auth.data;

import de.mel.auth.data.db.Approval;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;

import java.util.*;

/**
 * Created by xor on 6/26/16.
 */
public class ApprovalMatrix {
    private Map<Long, Map<Long, Approval>> matrix = new HashMap<>();
    private Set<Long> approvalsToDelete = new HashSet<>();

    public ApprovalMatrix fill(List<Certificate> certificates, List<ServiceJoinServiceType> services, List<Approval> approvals) {
        for (ServiceJoinServiceType service : services) {
            matrix.put(service.getServiceId().v(), new HashMap<>());
        }

        for (Certificate certificate : certificates) {
            if (!matrix.containsKey(certificate.getId().v())) {
                matrix.put(certificate.getId().v(), new HashMap<>());
            }
        }
        for (Approval approval : approvals) {
            matrix.get(approval.getCertificateId().v()).put(approval.getServiceId().v(), approval);
        }
        return this;
    }

    /**
     * @return (service.ID,(certificate.ID, Approval))
     */
    public Map<Long, Map<Long, Approval>> getMatrix() {
        return matrix;
    }

    public ApprovalMatrix approve(Long certificateId, Long serviceId) {
        if (!matrix.containsKey(certificateId))
            matrix.put(certificateId, new HashMap<>());
        matrix.get(certificateId).put(serviceId, new Approval().setCertificateId(certificateId).setServiceId(serviceId));
        return this;
    }

    public boolean isApproved(Long certificateId, Long serviceId) {
        return matrix.get(certificateId).containsKey(serviceId);
    }

    public ApprovalMatrix disapprove(Long certificateId, Long serviceId) {
        if (matrix.get(certificateId).containsKey(serviceId)) {
            Approval approval = matrix.get(certificateId).get(serviceId);
            if (approval.getId().v() != null)
                approvalsToDelete.add(approval.getId().v());
            matrix.get(certificateId).remove(serviceId);
        }
        return this;
    }

    public void setApproved(Long certificateId, Long serviceId, boolean approved) {
        if (approved)
            approve(certificateId, serviceId);
        else
            disapprove(certificateId, serviceId);
    }

    public static class ApprovalMatrixRow {
        private Set<Certificate> approvedCertificates = new HashSet<>();
        private ServiceJoinServiceType service;

        public ApprovalMatrixRow setService(ServiceJoinServiceType service) {
            this.service = service;
            return this;
        }

        public ServiceJoinServiceType getService() {
            return service;
        }

        public Set<Certificate> getApprovedCertificates() {
            return approvedCertificates;
        }

        public ApprovalMatrixRow addApprovedCertificate(Certificate certificate) {
            approvedCertificates.add(certificate);
            return this;
        }
    }

}
