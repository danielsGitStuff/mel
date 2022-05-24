package de.mel.auth.gui;

import de.mel.Lok;
import de.mel.auth.boot.BootLoaderFX;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.gui.controls.CertListCell;
import de.mel.auth.gui.controls.ServiceListCell;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.tools.N;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class RemoteServiceChooserFX extends AuthSettingsFX {

    @FXML
    ListView<ServiceJoinServiceType> listServices;
    private EmbeddedServiceSettingsFX embeddedServiceSettingsFX;
    @FXML
    private VBox container;
    @FXML
    private RadioButton rdServer, rdClient;
    @FXML
    private ListView<Certificate> listCerts;
    @FXML
    private Label lblAvailable;
    @FXML
    private HBox paneAvailable;

    private ServiceJoinServiceType selectedService;
    private Certificate selectedCertificate;
    private MelAuthAdminFX melAuthAdminFX;
    protected Bootloader bootloader;

    public Certificate getSelectedCertificate() {
        return selectedCertificate;
    }

    public ServiceJoinServiceType getSelectedService() {
        return selectedService;
    }

    @Override
    public boolean onPrimaryClicked() {
        return embeddedServiceSettingsFX.onPrimaryClicked();
    }

    public boolean isServerSelected() {
        return rdServer.isSelected();
    }

    private void onServerSelected() {
        rdClient.selectedProperty().setValue(false);
        rdServer.selectedProperty().setValue(true);
        lblAvailable.setVisible(false);
        paneAvailable.setVisible(false);
        paneAvailable.setManaged(false);
        embeddedServiceSettingsFX.onRbServerSelected();
    }

    private void onClientSelected() {
        rdClient.selectedProperty().setValue(true);
        rdServer.selectedProperty().setValue(false);
        lblAvailable.setVisible(true);
        paneAvailable.setVisible(true);
        paneAvailable.setManaged(true);
        embeddedServiceSettingsFX.onRbClientSelected();
    }


    @Override
    public void init() {
        Lok.debug("RemoteServiceChooserFX.init");
        listCerts.setCellFactory(param -> new CertListCell());
        listServices.setCellFactory(param -> new ServiceListCell());
        rdServer.setOnAction(event -> onServerSelected());
        rdClient.setOnAction(event -> {
            onClientSelected();
            listCerts.getItems().clear();
            listServices.getItems().clear();
            NetworkEnvironment env = melAuthService.getNetworkEnvironment();
            env.deleteObservers();
            listCerts.getItems().clear();
            N.forEach(env.getCertificateIds(), certId -> {
                Certificate cert = melAuthService.getCertificateManager().getCertificateById(certId);
                listCerts.getItems().add(cert);
            });

            listCerts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                listServices.getItems().clear();
                if (newValue != null) {
                    Lok.debug("FileSyncFXCreateController.init");
                    for (ServiceJoinServiceType service : env.getServices(newValue.getId().v())) {
                        if (bootloader.isCompatiblePartner(service))
                            listServices.getItems().add(service);
                    }
                }
            });
            listServices.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                this.selectedService = newValue;
                this.selectedCertificate = listCerts.getSelectionModel().selectedItemProperty().get();
                this.embeddedServiceSettingsFX.onServiceSelected(this.selectedCertificate, this.selectedService);
            });
            melAuthService.discoverNetworkEnvironment();
        });
    }

    @Override
    public String getTitle() {
        return embeddedServiceSettingsFX == null ? null : embeddedServiceSettingsFX.getTitle();
    }

    public void createFXML(Bootloader bootloader, ResourceBundle resourceBundle) {
        this.bootloader = bootloader;
        N.r(() -> {
//            showBottomButtons();
            String fxml = ((BootLoaderFX) bootloader).getCreateFXML();
            URL resource = getClass().getClassLoader().getResource(fxml);
            if (resource == null) {
                Lok.error("could not loadt fxml file from " + fxml);
                return;
            }
            FXMLLoader lo = new FXMLLoader(resource);
            lo.setResources(resourceBundle);
            Pane pane = lo.load();
            embeddedServiceSettingsFX = lo.getController();
            embeddedServiceSettingsFX.setRemoteServiceChooserFX(this);
            embeddedServiceSettingsFX.configureParentGui(melAuthAdminFX);
            embeddedServiceSettingsFX.setStage(melAuthAdminFX.getStage());
            embeddedServiceSettingsFX.setMelAuthService(melAuthService);
//            lblTitle.setText(contentController.getTitle());
//            setContentPane(pane);
            container.getChildren().add(pane);
            Lok.debug("MelAuthAdminFX.loadSettingsFX.loaded");
        });
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        this.melAuthAdminFX = melAuthAdminFX;
    }
}
