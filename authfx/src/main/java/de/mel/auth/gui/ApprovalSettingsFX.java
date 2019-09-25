package de.mel.auth.gui;

import de.mel.Lok;
import de.mel.auth.data.ApprovalMatrix;
import de.mel.auth.data.db.Approval;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.CheckCell;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.sql.SqlQueriesException;
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
public class ApprovalSettingsFX extends AuthSettingsFX{
    private ApprovalMatrix approvalMatrix;
    @FXML
    private TableView<ServiceJoinServiceType> table;

    @Override
    public boolean onPrimaryClicked() {
        Lok.debug("ApprovalSettingsFX.onPrimaryClicked");
        try {
            melAuthService.saveApprovals(approvalMatrix);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void init() {
        try {
            // organize delicous data
            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
            List<Certificate> certificates = melAuthService.getCertificateManager().getTrustedCertificates();
            List<Approval> approvals = melAuthService.getDatabaseManager().getAllApprovals();

            approvalMatrix = new ApprovalMatrix();
            approvalMatrix.fill(certificates, services, approvals);

            if (table != null) {
                new ArrayList<TableColumn>(table.getColumns())
                        .forEach(tableColumn -> table.getColumns().remove(tableColumn));
                TableColumn<ServiceJoinServiceType, String> servicesColumn = new TableColumn<>(getString("access.services"));
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
        return getString("access.title");
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("apply"));
        melAuthAdminFX.showPrimaryButtonOnly();
    }
}
