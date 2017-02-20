package mein.de.meindrive.mein.de.meindrive.controller;

import android.view.View;
import android.widget.TableLayout;

import java.util.List;

import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.db.Approval;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.SqlQueriesException;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/20/17.
 */

public class ApprovalController {
    private final View rootView;
    private final TableLayout table;
    private final MeinAuthService meinAuthService;
    private ApprovalMatrix approvalMatrix;

    public ApprovalController(MeinAuthService meinAuthService, View rootView) throws SqlQueriesException {
        this.rootView = rootView;
        this.table = (TableLayout) rootView.findViewById(R.id.matrix);
        this.meinAuthService = meinAuthService;
        System.out.println("ApprovalController.ApprovalController");
        fillContent();
    }

    private void fillContent() throws SqlQueriesException {
        List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
        List<Certificate> certificates = meinAuthService.getCertificateManager().getCertificates();
        List<Approval> approvals = meinAuthService.getDatabaseManager().getAllApprovals();

        approvalMatrix = new ApprovalMatrix();
        approvalMatrix.fill(certificates, services, approvals);
    }


}
