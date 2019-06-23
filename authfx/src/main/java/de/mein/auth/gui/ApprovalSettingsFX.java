package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.db.Approval;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.CheckCell;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.sql.SqlQueriesException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Created by xor on 9/22/16.
 */
public class ApprovalSettingsFX extends AuthSettingsFX implements Initializable {
    private ApprovalMatrix approvalMatrix;
    @FXML
    private TableView<ServiceJoinServiceType> table;

    @Override
    public boolean onPrimaryClicked() {
        Lok.debug("ApprovalSettingsFX.onPrimaryClicked");
        try {
            meinAuthService.saveApprovals(approvalMatrix);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void init() {
        try {
            // organize delicous data
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            List<Certificate> certificates = meinAuthService.getCertificateManager().getTrustedCertificates();
            List<Approval> approvals = meinAuthService.getDatabaseManager().getAllApprovals();

            approvalMatrix = new ApprovalMatrix();
            approvalMatrix.fill(certificates, services, approvals);

            if (table != null) {
                new ArrayList<TableColumn>(table.getColumns())
                        .forEach(tableColumn -> table.getColumns().remove(tableColumn));
                TableColumn<ServiceJoinServiceType, String> servicesColumn = new TableColumn<>("Services");
                servicesColumn.setStyle("-fx-background-color:rgba(0, 0, 0, 0.05)");
                servicesColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getName().v()));
                table.getColumns().add(servicesColumn);
                for (Certificate certificate : certificates) {
                    TableColumn<ServiceJoinServiceType, ServiceJoinServiceType> certColumn = new TableColumn<>(certificate.getName().v());
                    certColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
                    certColumn.setCellFactory(param -> new CheckCell(certificate, approvalMatrix).setApprovalHandler((certificate1, serviceType, approved) -> {
                        if (approved)
                            approvalMatrix.approve(certificate1.getId().v(), serviceType.getServiceId().v());
                        else
                            approvalMatrix.disapprove(certificate1.getId().v(), serviceType.getServiceId().v());
                    }));
                    table.getColumns().add(certColumn);
                }
                table.setItems(FXCollections.observableArrayList(services));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getTitle() {
        return "Which services shall speak to whom?";
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.setPrimaryButtonText("Apply");
        meinAuthAdminFX.showPrimaryButtonOnly();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}
