package de.mein.auth.gui;

import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.db.Approval;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.CheckCell;
import de.mein.sql.SqlQueriesException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Created by xor on 9/22/16.
 */
public class ApprovalSettingsFX extends AuthSettingsFX implements Initializable{
    private ApprovalMatrix approvalMatrix;
    @FXML
    private TableView<ServiceJoinServiceType> table;
    @Override
    public void onApplyClicked() {
        System.out.println("ApprovalSettingsFX.onApplyClicked");
        try {
            meinAuthService.saveApprovals(approvalMatrix);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void init() {
        try {
            // organize delicous data
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            List<Certificate> certificates = meinAuthService.getCertificateManager().getCertificates();
            List<Approval> approvals = meinAuthService.getDatabaseManager().getAllApprovals();

            approvalMatrix = new ApprovalMatrix();
            approvalMatrix.fill(certificates, services, approvals);

            if (table != null) {
                new ArrayList<TableColumn>(table.getColumns())
                        .forEach(tableColumn -> table.getColumns().remove(tableColumn));
                TableColumn<ServiceJoinServiceType, String> servicesColumn = new TableColumn<>("Services");
                servicesColumn.setStyle("-fx-background-color:rgba(0, 0, 0, 0.05)");
                servicesColumn.setCellValueFactory(cellData -> {
                    return new SimpleObjectProperty<>(cellData.getValue().getType().v());
                });

                table.getColumns().add(servicesColumn);

                for (Certificate certificate : certificates) {
                    TableColumn<ServiceJoinServiceType, ServiceJoinServiceType> certColumn = new TableColumn<>(certificate.getName().v());
                /*TableThingy factory = new TableThingy(certificate) {
                    @Override
                    public ObservableValue<String> call(TableColumn.CellDataFeatures<ServiceJoinServiceType, String> param) {
                        String approved = "fuuuuu";
                        if (approvalMatrix.isApproved(certificate.getId().v(), param.getValue().getServiceId().v()))
                            approved = "t";
                        else
                            approved = "f";
                        return new SimpleStringProperty(approved);
                    }
                };*/

                    //certColumn.setCellValueFactory(factory);
                    certColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ServiceJoinServiceType, ServiceJoinServiceType>, ObservableValue<ServiceJoinServiceType>>() {
                        @Override
                        public ObservableValue<ServiceJoinServiceType> call(TableColumn.CellDataFeatures<ServiceJoinServiceType, ServiceJoinServiceType> param) {
                            return new SimpleObjectProperty<ServiceJoinServiceType>(param.getValue());
                        }
                    });
                    certColumn.setCellFactory(new Callback<TableColumn<ServiceJoinServiceType, ServiceJoinServiceType>, TableCell<ServiceJoinServiceType, ServiceJoinServiceType>>() {
                        @Override
                        public TableCell<ServiceJoinServiceType, ServiceJoinServiceType> call(TableColumn<ServiceJoinServiceType, ServiceJoinServiceType> param) {
                            return new CheckCell(certificate, approvalMatrix).setApprovalHandler((certificate1, serviceType, approved) -> {
                                if (approved)
                                    approvalMatrix.approve(certificate1.getId().v(), serviceType.getServiceId().v());
                                else
                                    approvalMatrix.disapprove(certificate1.getId().v(), serviceType.getServiceId().v());
                            });
                        }
                    });
                    table.getColumns().add(certColumn);
                }

                table.setItems(FXCollections.observableArrayList(services));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}
