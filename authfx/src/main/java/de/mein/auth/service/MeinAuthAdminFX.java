package de.mein.auth.service;

import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.gui.*;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.sql.SqlQueriesException;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Created by xor on 6/25/16.
 */
public class MeinAuthAdminFX implements Initializable, MeinAuthAdmin {

    private static final int IMAGE_SIZE = 22;
    private MeinAuthService meinAuthService;
    private Stage stage;

    @FXML
    private TextField txtServiceFilter, txtCertificateFilter;
    @FXML
    private Button btnSecondary, btnPrimary, btnAccess, btnCreateService, btnOthers, btnSettings;
    @FXML
    private Button btnInfo, btnPairing;
    @FXML
    private AnchorPane paneContainer;
    private AuthSettingsFX contentController;
    @FXML
    private N runner = new N(Throwable::printStackTrace);
    @FXML
    private TitledPane tpServices;
    @FXML
    private Label lblTitle;
    @FXML
    private HBox hBoxButtons;
    @FXML
    private VBox vboxServices;

    @FXML
    private ImageView imgInfo, imgAccess, imgOthers, imgPairing, imgSettings;
    private TrayIcon trayIcon;

    @Override
    public void start(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        showServices();
    }


    @Override
    public void onNotificationFromService(IMeinService meinService, MeinNotification meinNotification) {
        N.r(() -> {
            Service service = meinAuthService.getDatabaseManager().getServiceByUuid(meinService.getUuid());
            ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeById(service.getTypeId().v());
            BootLoader bootloader = meinAuthService.getMeinBoot().getBootLoader(type.getType().v());
            if (bootloader instanceof BootLoaderFX) {
                BootLoaderFX bootLoaderFX = (BootLoaderFX) bootloader;
                String containingPath = bootLoaderFX.getPopupFXML(meinService, meinNotification);
                loadPopup(containingPath).done(popupContentFX -> {
                    popupContentFX.init(meinService, meinNotification);
                });
            }
        });
    }

    private Promise<PopupContentFX, Void, Void> loadPopup(String containingPath) {
        Deferred<PopupContentFX, Void, Void> deferred = new DeferredObject<>();
        Platform.runLater(() -> {
            N.r(() -> {
                FXMLLoader loader = new FXMLLoader(MeinAuthAdminFX.class.getClassLoader().getResource("de/mein/auth/popup.fxml"));
                Parent root = null;
                root = loader.load();
                Scene scene = new Scene(root);
                scene.getStylesheets().add("de/mein/modena_dark.css");
                Stage stage = new Stage();
                stage.setTitle("MeinAuthAdmin.Popup '" + meinAuthService.getName() + "'");
                stage.setScene(scene);
                stage.show();

                PopupContainerFX popupController = loader.getController();
                popupController.load(containingPath).done(contentFX -> {
                    contentFX.setStage(stage);
                    deferred.resolve(contentFX);
                });
            });
        });
        return deferred;
    }


    public MeinAuthAdminFX() {

    }

    @Override
    public void onChanged() {
        System.out.println("MeinAuthAdminFX.onChanged");
        showServices();
    }

    @Override
    public void shutDown() {
        stage.close();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    private void showServices() {
        Platform.runLater(() -> {
            try {
                List<ServiceJoinServiceType> serviceJoinServiceTypes = meinAuthService.getDatabaseManager().getAllServices();
                vboxServices.getChildren().clear();
                for (ServiceJoinServiceType serviceJoinServiceType : serviceJoinServiceTypes) {
                    IMeinService runningService = meinAuthService.getMeinService(serviceJoinServiceType.getUuid().v());
                    if (runningService != null)
                        serviceJoinServiceType.setRunning(true);
                    // fill vboxServices with Buttons!
                    Button button = new Button(serviceJoinServiceType.getName().v());
                    button.setMaxWidth(Double.MAX_VALUE);
                    button.setAlignment(Pos.TOP_LEFT);
                    BootLoader bootloader = meinAuthService.getMeinBoot().getBootLoader(serviceJoinServiceType.getType().v());
                    if (bootloader instanceof BootLoaderFX) {
                        BootLoaderFX bootLoaderFX = (BootLoaderFX) bootloader;
                        ImageView image = new ImageView(new Image(bootLoaderFX.getIconURL(), IMAGE_SIZE, IMAGE_SIZE, true, true));
                        button.setGraphic(image);
                        button.setOnAction(event -> {
                            IMeinService meinService = meinAuthService.getMeinService(serviceJoinServiceType.getUuid().v());
                            loadSettingsFX(bootLoaderFX.getEditFXML(meinService));
                            ServiceSettingsFX serviceSettingsFX = (ServiceSettingsFX) contentController;
                            serviceSettingsFX.feed(serviceJoinServiceType);
                        });
                    }
                    vboxServices.getChildren().add(button);
                }
                //spacer
                VBox spacer = new VBox();
                spacer.setPrefHeight(15);

                //create service button
                ImageView imgCreate = new ImageView(new Image("de/mein/icon/add.png", IMAGE_SIZE * .8, IMAGE_SIZE * .8, true, true));
                Button btnCreateService = new Button("Create Service");
                btnCreateService.setGraphic(imgCreate);
                btnCreateService.setMaxWidth(Double.MAX_VALUE);
                btnCreateService.setAlignment(Pos.TOP_LEFT);
                btnCreateService.setOnAction(event -> {
                    ContextMenu createServiceMenu = new ContextMenu();
                    createServiceMenu.getItems().clear();
                    Set<String> names = meinAuthService.getMeinBoot().getBootloaderMap().keySet();
                    for (String name : names) {

                        MenuItem menuItem = new MenuItem(name);
                        menuItem.setOnAction(e1 -> {
                            System.out.println("MeinAuthAdminFX.initialize.createmenu.clicked");
                            runner.r(() -> {
                                        onCreateMenuItemClicked(name);
                                        createServiceMenu.hide();
                                    }
                            );
                        });
                        N.r(() -> {
                            BootLoader bootLoader = meinAuthService.getMeinBoot().getBootLoader(name);
                            if (bootLoader instanceof BootLoaderFX) {
                                BootLoaderFX bootLoaderFX = (BootLoaderFX) bootLoader;
                                ImageView imgService = new ImageView(new Image(bootLoaderFX.getIconURL(), IMAGE_SIZE, IMAGE_SIZE, true, true));
                                menuItem.setGraphic(imgService);
                            }
                        });
                        createServiceMenu.getItems().add(menuItem);
                    }
                    Object source = event.getSource();
                    createServiceMenu.show(btnCreateService, Side.TOP, 0, 0);
                });
                vboxServices.getChildren().addAll(spacer, btnCreateService);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        btnSettings.setOnAction(event -> loadSettingsFX("de/mein/auth/settings.fxml"));
        btnInfo.setOnAction(event -> loadSettingsFX("de/mein/auth/info.fxml"));
        btnSecondary.setOnAction(event -> {
            if (contentController != null) {
                contentController.onSecondaryClicked();
                contentController = null;
                paneContainer.getChildren().clear();
            }
        });
        btnPrimary.setOnAction(event -> {
            if (contentController != null) {
                contentController.onPrimaryClicked();
                contentController = null;
                paneContainer.getChildren().clear();
            }
        });
        btnOthers.setOnAction(event -> {
            loadSettingsFX("de/mein/auth/others.fxml");
        });
        btnAccess.setOnAction(event -> loadSettingsFX("de/mein/auth/access.fxml"));
        btnPairing.setOnAction(event -> loadSettingsFX("de/mein/auth/pairing.fxml"));
        tpServices.expandedProperty().addListener((observable, oldValue, newValue) -> showServices());
        //todo debug
        //add system tray
        if (SystemTray.isSupported()) {
            N.r(this::displayTray);
        } else {
            System.err.println("System tray not supported!");
        }
        // load images for buttons
        Image im = new Image("/de/mein/icon/access.n.png", IMAGE_SIZE, IMAGE_SIZE, true, false);
        imgAccess.setImage(im);
        imgInfo.setImage(new Image("/de/mein/icon/info.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
        imgOthers.setImage(new Image("/de/mein/icon/connected.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
        imgPairing.setImage(new Image("/de/mein/icon/pairing.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
        imgSettings.setImage(new Image("/de/mein/icon/settings.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
    }

    public void displayTray() throws AWTException, IOException {
        //Obtain only one instance of the SystemTray object
        SystemTray tray = SystemTray.getSystemTray();

        //If the icon is a file
        URL url = MeinAuthAdmin.class.getResource("/de/mein/icon/app_square.png");
        File f = new File(url.getFile());
        BufferedImage img = ImageIO.read(f);
        //Alternative (if the icon is on the classpath):
        trayIcon = new TrayIcon(img, "Tray Demo");
        //Let the system resizes the image if needed
        trayIcon.setImageAutoSize(true);
        //Set tooltip text for the tray icon
        trayIcon.setToolTip("System tray icon demo");
        tray.add(trayIcon);
        trayIcon.displayMessage("Hello, World", "notification demo", TrayIcon.MessageType.INFO);
    }

    private void onCreateMenuItemClicked(String bootLoaderName) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        Class<? extends BootLoader> bootLoaderClass = meinAuthService.getMeinBoot().getBootloaderMap().get(bootLoaderName);
        BootLoader bootLoader = MeinBoot.createBootLoader(meinAuthService, bootLoaderClass);
        if (bootLoader instanceof BootLoaderFX) {
            showPrimaryButtonOnly();
            BootLoaderFX bootLoaderFX = (BootLoaderFX) bootLoader;
            if (bootLoaderFX.embedCreateFXML()) {
                loadSettingsFX("de/mein/choose.server.fxml");
                RemoteServiceChooserFX remoteServiceChooserFX = (RemoteServiceChooserFX) contentController;
                remoteServiceChooserFX.createFXML(((BootLoaderFX) bootLoader).getCreateFXML());
                lblTitle.setText(contentController.getTitle());
            } else {
                loadSettingsFX(((BootLoaderFX) bootLoader).getCreateFXML());
            }
        } else {
            System.out.println("MeinAuthAdminFX.onCreateMenuItemClicked.NO.FX.BOOTLOADER");
        }

    }

    private void loadSettingsFX(String resource) {
        N runner = new N(e -> e.printStackTrace());
        runner.r(() -> {
            contentController = null;
            FXMLLoader lo = new FXMLLoader(getClass().getClassLoader().getResource(resource));
            Pane pane = lo.load();
            contentController = lo.getController();
            contentController.configureParentGui(this);
            contentController.setMeinAuthService(meinAuthService);
            lblTitle.setText(contentController.getTitle());
            setContentPane(pane);
            System.out.println("MeinAuthAdminFX.loadSettingsFX.loaded");
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

    public static void main(String[] args) {
        WaitLock waitLock = new WaitLock().lock();
        waitLock.lock();
        System.out.println("MeinAuthAdminFX.main");
    }

    @SuppressWarnings("Duplicates")
    public static MeinAuthAdminFX load(MeinAuthService meinAuthService) {
        new JFXPanel();
        Platform.setImplicitExit(false);
        final MeinAuthAdminFX[] meinAuthAdminFXES = new MeinAuthAdminFX[1];
        MeinAuthAdminFX m;
        WaitLock lock = new WaitLock().lock();
        Platform.runLater(() -> {
                    try {
                        System.out.println("MeinAuthAdminFX.load...");
                        FXMLLoader loader = new FXMLLoader(MeinAuthAdminFX.class.getClassLoader().getResource("de/mein/auth/mainwindow.fxml"));
                        HBox root = null;
                        root = loader.load();
                        meinAuthAdminFXES[0] = loader.getController();
                        meinAuthAdminFXES[0].start(meinAuthService);
                        Scene scene = new Scene(root);
                        //apply theme
                        scene.getStylesheets().add(MeinAuthAdmin.class.getResource("/de/mein/modena_dark.css").toExternalForm());
                        //set app icon
                        Image image = new Image("/de/mein/icon/app_square.png");
                        Stage stage = new Stage();
                        stage.getIcons().add(image);
                        stage.setTitle("MeinAuthAdmin '" + meinAuthService.getName() + "'");
                        stage.setScene(scene);
                        stage.show();
                        stage.setOnCloseRequest(event -> {
                            meinAuthAdminFXES[0].shutDown();
                            System.exit(0);
                        });
                        meinAuthAdminFXES[0].setStage(stage);
                        meinAuthAdminFXES[0].showServices();
                        lock.unlock();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        );
        lock.lock();
        return meinAuthAdminFXES[0];
    }

    public void hideBottomButtons() {
        hBoxButtons.setVisible(false);
        hBoxButtons.setManaged(false);
    }

    public void showPrimaryButtonOnly() {
        btnSecondary.setVisible(false);
        btnSecondary.setManaged(false);
        btnPrimary.setVisible(true);
        btnPrimary.setManaged(true);
        hBoxButtons.setVisible(true);
        hBoxButtons.setManaged(true);
    }

    public void showBottomButtons() {
        btnSecondary.setVisible(true);
        btnSecondary.setManaged(true);
        btnPrimary.setVisible(true);
        btnPrimary.setManaged(true);
        hBoxButtons.setVisible(true);
        hBoxButtons.setManaged(true);
    }

    public void setPrimaryButtonText(String text) {
        btnPrimary.setText(text);
    }

    public void setSecondaryButtonText(String text) {
        btnSecondary.setText(text);
    }

    public Stage getStage() {
        return stage;
    }
}
