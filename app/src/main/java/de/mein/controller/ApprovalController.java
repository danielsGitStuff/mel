package de.mein.controller;

import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.List;

import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.db.Approval;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.SqlQueriesException;
import de.mein.android.AndroidService;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/20/17.
 */

public class ApprovalController implements GuiController {
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
        if (meinAuthService != null) {
            table.removeAllViews();
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            List<Certificate> certificates = meinAuthService.getCertificateManager().getCertificates();
            List<Approval> approvals = meinAuthService.getDatabaseManager().getAllApprovals();
            approvalMatrix = new ApprovalMatrix();
            approvalMatrix.fill(certificates, services, approvals);

            // header goes here
            TableRow header = new TableRow(rootView.getContext());
            TextView lblServices = new TextView(rootView.getContext());
            lblServices.setText("Service");
            header.addView(lblServices);
            for (Certificate certificate : certificates) {
                TextView lblCertName = new TextView(rootView.getContext());
                lblCertName.setText(certificate.getName().v());
                header.addView(lblCertName);
            }
            table.addView(header);

            // iterate over rows/services
            for (ServiceJoinServiceType service : services) {
                TextView lbl = new TextView(rootView.getContext());
                lbl.setText(service.getType().v() + "/" + service.getName().v());
                TableRow row = new TableRow(rootView.getContext());
                row.addView(lbl);
                for (Certificate certificate : certificates) {
                    CheckBox checkBox = new CheckBox(rootView.getContext());
                    boolean approved = approvalMatrix.isApproved(certificate.getId().v(), service.getServiceId().v());
                    checkBox.setChecked(approved);
                    checkBox.setOnClickListener(view -> {
                        boolean newApproved = checkBox.isChecked();
                        if (newApproved)
                            approvalMatrix.approve(certificate.getId().v(), service.getServiceId().v());
                        else
                            approvalMatrix.disapprove(certificate.getId().v(), service.getServiceId().v());
                    });
                    row.addView(checkBox);
                }
                table.addView(row);
            }
            approvalMatrix.getMatrix().forEach((serviceId, longApprovalMap) -> {
                System.out.println("ApprovalController.fillContent");
            });
        }
//        if (table != null) {
//            new ArrayList<TableColumn>(table.getColumns())
//                    .forEach(tableColumn -> table.getColumns().remove(tableColumn));
//            TableColumn<ServiceJoinServiceType, String> servicesColumn = new TableColumn<>("Services");
//            servicesColumn.setStyle("-fx-background-color:rgba(0, 0, 0, 0.05)");
//            servicesColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getType().v()));
//            table.getColumns().add(servicesColumn);
//            for (Certificate certificate : certificates) {
//                TableColumn<ServiceJoinServiceType, ServiceJoinServiceType> certColumn = new TableColumn<>(certificate.getName().v());
//                certColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
//                certColumn.setCellFactory(param -> new CheckCell(certificate, approvalMatrix).setApprovalHandler((certificate1, serviceType, approved) -> {
//                    if (approved)
//                        approvalMatrix.approve(certificate1.getId().v(), serviceType.getServiceId().v());
//                    else
//                        approvalMatrix.disapprove(certificate1.getId().v(), serviceType.getServiceId().v());
//                }));
//                table.getColumns().add(certColumn);
//            }
//            table.setItems(FXCollections.observableArrayList(services));
//        }
    }


    @Override
    public void onMeinAuthStarted(MeinAuthService androidService) {

    }

    @Override
    public void onAndroidServiceBound(AndroidService androidService) {

    }
}
