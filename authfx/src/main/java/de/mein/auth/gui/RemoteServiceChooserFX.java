package de.mein.auth.gui;

import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.controls.CertListCell;
import de.mein.auth.gui.controls.ServiceListCell;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.N;
import de.mein.sql.RWLock;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.*;

public class RemoteServiceChooserFX extends AuthSettingsFX {
    private EmbeddedServiceSettingsFX embeddedServiceSettingsFX;
    @FXML
    private VBox container;
    @FXML
    private RadioButton rdServer, rdClient;
    @FXML
    private ListView<Certificate> listCerts;
    @FXML
    ListView<ServiceJoinServiceType> listServices;
    private ServiceJoinServiceType selectedService;
    private Certificate selectedCertificate;
    private MeinAuthAdminFX meinAuthAdminFX;

    public Certificate getSelectedCertificate() {
        return selectedCertificate;
    }

    public ServiceJoinServiceType getSelectedService() {
        return selectedService;
    }

    @Override
    public void onPrimaryClicked() {
        embeddedServiceSettingsFX.onPrimaryClicked();
    }

    public boolean isServerSelected() {
        return rdServer.isSelected();
    }

    public static class FoundServices extends RWLock {
        private final CertAddedListener certAddedListener;

        public List<ServiceJoinServiceType> get(Long certId) {
            return foundServices.get(certId);
        }

        public static interface CertAddedListener {
            void onCertAdded(Certificate certificate);
        }

        private Map<Long, List<ServiceJoinServiceType>> foundServices = new HashMap<>();

        public FoundServices(CertAddedListener certAddedListener) {
            this.certAddedListener = certAddedListener;
        }

        public void add(Certificate certificate, ServiceJoinServiceType service) {
            Long certId = certificate.getId().v();
            if (!foundServices.containsKey(certId)) {
                foundServices.put(certId, new ArrayList<>());
                if (certAddedListener != null)
                    certAddedListener.onCertAdded(certificate);
            }
            foundServices.get(certId).add(service);
        }
    }

    @Override
    public void init() {
        System.out.println("RemoteServiceChooserFX.init");
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
            FoundServices foundServices = new FoundServices(listCerts.getItems()::add);
            env.deleteObservers();
            env.addObserver((environment, arg) -> {
                System.out.println("DriveFXCreateController.change");
                N.r(() -> {
                    Collection<ServiceJoinServiceType> services = env.getServices();
                    for (ServiceJoinServiceType service : services) {
                        Long certId = env.getCertificateId(service);
                        embeddedServiceSettingsFX.onServiceSpotted(foundServices, certId, service);
                    }
                });

            });
            listCerts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                listServices.getItems().clear();
                System.out.println("DriveFXCreateController.init");
                Certificate certificate = (Certificate) newValue;
                foundServices.lockRead();
                for (ServiceJoinServiceType service : foundServices.get(certificate.getId().v())) {
                    listServices.getItems().add(service);
                }
                foundServices.unlockRead();
            });
            listServices.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                ServiceJoinServiceType service = (ServiceJoinServiceType) newValue;
                this.selectedService = service;
                this.selectedCertificate = (Certificate) listCerts.getSelectionModel().selectedItemProperty().get();
            });
            meinAuthService.discoverNetworkEnvironment();
        });
    }

    @Override
    public String getTitle() {
        return embeddedServiceSettingsFX == null ? null : embeddedServiceSettingsFX.getTitle();
    }

    public void createFXML(String fxml) {
        N.r(() -> {
//            showBottomButtons();
            FXMLLoader lo = new FXMLLoader(getClass().getClassLoader().getResource(fxml));
            Pane pane = lo.load();
            embeddedServiceSettingsFX = lo.getController();
            embeddedServiceSettingsFX.setRemoteServiceChooserFX(this);
            embeddedServiceSettingsFX.configureParentGui(meinAuthAdminFX);
            embeddedServiceSettingsFX.setMeinAuthService(meinAuthService);
//            lblTitle.setText(contentController.getTitle());
//            setContentPane(pane);
            container.getChildren().add(pane);
            System.out.println("MeinAuthAdminFX.loadSettingsFX.loaded");
        });
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        this.meinAuthAdminFX = meinAuthAdminFX;
    }
}
