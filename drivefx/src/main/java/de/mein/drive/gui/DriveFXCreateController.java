package de.mein.drive.gui;

import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.AuthSettingsFX;
import de.mein.auth.gui.controls.CertListCell;
import de.mein.auth.gui.controls.ServiceListCell;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.NoTryRunner;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.sql.RWLock;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import org.jdeferred.Promise;

import java.util.*;

/**
 * Created by xor on 10/20/16.
 */
public class DriveFXCreateController extends AuthSettingsFX {

    @FXML
    private TextField txtName, txtPath;
    @FXML
    private RadioButton rdServer, rdClient;
    @FXML
    private ListView<Certificate> listCerts;
    @FXML
    ListView<ServiceJoinServiceType> listServices;
    private NoTryRunner runner = new NoTryRunner(Throwable::printStackTrace);
    private ServiceJoinServiceType selectedService;
    private Certificate selectedCertificate;
    private DriveCreateController driveCreateController;

    @Override
    public void onApplyClicked() {
        runner.run(() -> {
            String name = txtName.getText().trim();
            Boolean isServer = rdServer.isSelected();
            String role = isServer ? DriveStrings.ROLE_SERVER : DriveStrings.ROLE_CLIENT;
            String path = txtPath.getText();
            if (isServer)
                driveCreateController.createDriveServerService(name,path);
            else {
                Certificate certificate = listCerts.getSelectionModel().getSelectedItem();
                ServiceJoinServiceType serviceJoinServiceType = listServices.getSelectionModel().getSelectedItem();
                driveCreateController.createDriveClientService(name,path,certificate.getId().v(),serviceJoinServiceType.getUuid().v());
            }
        });
    }

    @Override
    public void init() {
        driveCreateController = new DriveCreateController(meinAuthService);
        listCerts.setCellFactory(param -> new CertListCell());
        listServices.setCellFactory(param -> new ServiceListCell());
        rdServer.setOnAction(event -> {
            rdClient.selectedProperty().setValue(false);
            rdServer.selectedProperty().setValue(true);
        });

        rdClient.setOnAction(event -> {
            rdClient.selectedProperty().setValue(true);
            rdServer.selectedProperty().setValue(false);
            listCerts.getItems().clear();
            listServices.getItems().clear();
            NetworkEnvironment env = meinAuthService.getNetworkEnvironment();
            Map<Long, List<ServiceJoinServiceType>> foundServices = new HashMap<>();
            RWLock mapLock = new RWLock();
            env.deleteObservers();
            env.addObserver((environment, arg) -> {
                System.out.println("DriveFXCreateController.change");
                runner.run(() -> {
                    Collection<ServiceJoinServiceType> services = env.getServices();
                    for (ServiceJoinServiceType service : services) {
                        if (service.getType().v().equals(new DriveBootLoader().getName())) {
                            Long certId = env.getCertificateId(service);
                            Certificate certificate = meinAuthService.getCertificateManager().getCertificateById(certId);
                            Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
                            connected.done(mvp -> runner.run(() -> {
                                Request promise = mvp.request(service.getUuid().v(), DriveStrings.INTENT_DRIVE_DETAILS, null);
                                promise.done(result -> runner.run(() -> {
                                    DriveDetails driveDetails = (DriveDetails) result;
                                    if (driveDetails.getRole() != null && driveDetails.getRole().equals(DriveStrings.ROLE_SERVER)) {
                                        listCerts.getItems().add(certificate);
                                        //finally found one
                                        mapLock.lockWrite();
                                        if (!foundServices.containsKey(certId)) {
                                            foundServices.put(certId, new ArrayList<>());
                                            foundServices.get(certId).add(service);
                                        } else
                                            foundServices.get(certId).add(service);
                                        mapLock.unlockWrite();
                                    }
                                }));
                            }));

                        }
                    }
                });

            });
            listCerts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                listServices.getItems().clear();
                System.out.println("DriveFXCreateController.init");
                Certificate certificate = (Certificate) newValue;
                mapLock.lockRead();
                for (ServiceJoinServiceType service : foundServices.get(certificate.getId().v())) {
                    listServices.getItems().add(service);
                }
                mapLock.unlockRead();
            });
            listServices.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                ServiceJoinServiceType service = (ServiceJoinServiceType) newValue;
                this.selectedService = service;
                this.selectedCertificate = (Certificate) listCerts.getSelectionModel().selectedItemProperty().get();
            });
            meinAuthService.discoverNetworkEnvironment();
        });

    }
}
