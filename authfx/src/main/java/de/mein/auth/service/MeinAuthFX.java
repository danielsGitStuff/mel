package de.mein.auth.service;

import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.boot.BootLoader;
import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.AuthSettingsFX;
import de.mein.auth.gui.ServiceListItem;
import de.mein.auth.gui.ServiceSettingsFX;
import de.mein.auth.tools.N;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Created by xor on 6/25/16.
 */
public class MeinAuthFX implements Initializable, MeinAuthAdmin {

    private MeinAuthService meinAuthService;
    private Stage stage;

    @FXML
    private TextField txtServiceFilter, txtCertificateFilter;
    @FXML
    private Button btnRefresh, btnApply, btnApprovals, btnCreateService, btnRemoveService, btnOthers;
    @FXML
    private Button btnGeneral, btnDiscover;
    @FXML
    private AnchorPane paneContainer;
    private AuthSettingsFX contentController;
    @FXML
    private ListView<ServiceJoinServiceType> serviceList;
    @FXML
    private ContextMenu createServiceMenu;
    private N runner = new N(Throwable::printStackTrace);
    @FXML
    private TitledPane tpServices;
    @FXML
    private Label lblTitle;
    @FXML
    private HBox hBoxButtons;


    public MeinAuthFX setMeinAuthService(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        showContent();
        return this;
    }

    @Override
    public void start(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }


    public MeinAuthFX() {

    }

    @Override
    public void onChanged() {
        System.out.println("MeinAuthFX.onChanged");
        showContent();
    }

    private void showContent() {
        try {
            List<ServiceJoinServiceType> serviceJoinServiceTypes = meinAuthService.getDatabaseManager().getAllServices();
            serviceList.getItems().clear();
            for (ServiceJoinServiceType serviceJoinServiceType : serviceJoinServiceTypes) {
                IMeinService runningService = meinAuthService.getMeinService(serviceJoinServiceType.getUuid().v());
                if (runningService != null)
                    serviceJoinServiceType.setRunning(true);
                serviceList.getItems().add(serviceJoinServiceType);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        btnGeneral.setOnAction(event -> loadSettingsFX("de/mein/auth/general.fxml"));
        btnRefresh.setOnAction(event -> onChanged());
        btnApply.setOnAction(event -> {
            if (contentController != null) {
                contentController.onApplyClicked();
                contentController = null;
                paneContainer.getChildren().clear();
            }
        });
        btnOthers.setOnAction(event -> {
            loadSettingsFX("de/mein/auth/others.fxml");
        });
        btnApprovals.setOnAction(event -> loadSettingsFX("de/mein/auth/approvals.fxml"));
        btnCreateService.setOnAction(event -> {
            createServiceMenu.getItems().clear();
            int offset = -btnCreateService.heightProperty().intValue();
            Set<String> names = MeinBoot.getBootloaderMap().keySet();
            for (String name : names) {
                MenuItem menuItem = new MenuItem(name);
                menuItem.setOnAction(e1 -> {
                    System.out.println("MeinAuthFX.initialize.createmenu.clicked");
                    runner.r(() -> onCreateMenuItemClicked(name));
                });
                createServiceMenu.getItems().add(menuItem);
            }
            createServiceMenu.show(btnCreateService, Side.TOP, 0, offset);
        });
        btnRemoveService.setOnAction(event -> runner.r(() -> {
            ServiceJoinServiceType service = serviceList.getSelectionModel().getSelectedItem();
            meinAuthService.unregisterMeinService(service.getServiceId().v());
            meinAuthService.getDatabaseManager().deleteService(service.getServiceId().v());
        }));
        serviceList.setCellFactory(param -> new ServiceListItem());
        serviceList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, service) ->
                        runner.r(() -> {
                            if (service != null) {
                                BootLoaderFX bootloader = (BootLoaderFX) MeinBoot.getBootLoader(meinAuthService, service.getType().v());
                                IMeinService meinService = meinAuthService.getMeinService(service.getUuid().v());
                                loadSettingsFX(bootloader.getEditFXML(meinService));
                                ServiceSettingsFX serviceSettingsFX = (ServiceSettingsFX) contentController;
                                serviceSettingsFX.feed(service);
                            }
                        }));
        btnDiscover.setOnAction(event -> loadSettingsFX("de/mein/auth/discover.fxml"));
        tpServices.expandedProperty().addListener((observable, oldValue, newValue) -> showContent());
    }

    private void onCreateMenuItemClicked(String bootLoaderName) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        Class<? extends BootLoader> bootLoaderClass = MeinBoot.getBootloaderMap().get(bootLoaderName);
        BootLoader bootLoader = MeinBoot.createBootLoader(meinAuthService, bootLoaderClass);
        if (bootLoader instanceof BootLoaderFX) {
            loadSettingsFX(((BootLoaderFX) bootLoader).getCreateFXML());
        } else {
            System.out.println("MeinAuthFX.onCreateMenuItemClicked.NO.FX.BOOTLOADER");
        }

    }

    private void loadSettingsFX(String resource) {
        N runner = new N(e -> e.printStackTrace());
        runner.r(() -> {
            showBottomButtons();
            FXMLLoader lo = new FXMLLoader(getClass().getClassLoader().getResource(resource));
            Pane pane = lo.load();
            contentController = lo.getController();
            contentController.configureParentGui(this);
            contentController.setMeinAuthService(meinAuthService);
            lblTitle.setText(contentController.getTitle());
            setContentPane(pane);
            System.out.println("MeinAuthFX.loadSettingsFX.loaded");
        });
    }


    private void setContentPane(Pane pane) {
        paneContainer.getChildren().clear();
        paneContainer.getChildren().add(pane);
        AnchorPane.setBottomAnchor(pane, 0.0);
        AnchorPane.setLeftAnchor(pane, 0.0);
        AnchorPane.setRightAnchor(pane, 0.0);
        AnchorPane.setTopAnchor(pane, 0.0);
    }


    @SuppressWarnings("Duplicates")
    public static MeinAuthFX load(MeinAuthService meinAuthService) {
        new JFXPanel();
        Platform.setImplicitExit(false);
        final MeinAuthFX[] meinAuthFX = new MeinAuthFX[1];
        MeinAuthFX m;
        RWLock lock = new RWLock().lockWrite();
        Platform.runLater(() -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(MeinAuthFX.class.getClassLoader().getResource("de/mein/auth/mainwindow.fxml"));
                        HBox root = null;
                        root = loader.load();
                        meinAuthFX[0] = loader.getController();
                        meinAuthFX[0].setMeinAuthService(meinAuthService);
                        Scene scene = new Scene(root);
                        Stage stage = new Stage();
                        stage.setTitle("MeinAuthAdmin '" + meinAuthService.getName() + "'");
                        stage.setScene(scene);
                        stage.show();
                        meinAuthFX[0].setStage(stage);
                        meinAuthFX[0].showContent();
                        lock.unlockWrite();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        );
        lock.lockWrite().unlockWrite();
        return meinAuthFX[0];
    }

    public void hideBottomButtons() {
        hBoxButtons.setVisible(false);
        hBoxButtons.setManaged(false);
    }

    private void showBottomButtons() {
        hBoxButtons.setVisible(true);
        hBoxButtons.setManaged(true);
    }
}
