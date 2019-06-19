package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.FxApp;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.*;
import de.mein.auth.gui.notification.NotificationCenter;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.sql.SqlQueriesException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * Created by xor on 6/25/16.
 */
public class MeinAuthAdminFX implements Initializable, MeinAuthAdmin, MeinNotification.MeinProgressListener {

    private static final int IMAGE_SIZE = 22;
    public static final String GLOBAL_STYLE_CSS = "/de/mein/modena_dark.css";
    //    public static final String GLOBAL_STYLE_CSS = "/de/mein/jmetro.dark.css";
    private MeinAuthService meinAuthService;
    private Stage stage;

    @FXML
    private TextField txtServiceFilter, txtCertificateFilter;
    @FXML
    private Button btnSecondary, btnPrimary, btnAccess, btnCreateService, btnOthers, btnSettings, btnAbout;
    @FXML
    private Button btnInfo, btnPairing, btnNotifications;
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
    private ImageView imgInfo, imgAccess, imgOthers, imgPairing, imgSettings, imgAbout;
    private TrayIcon trayIcon;
    private NotificationCenter notificationCenter;
    private ResourceBundle resourceBundle;

    @Override
    public void start(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        showServices();
    }

    public MeinAuthAdminFX() {

    }

    @Override
    public void onChanged() {
        Lok.debug("MeinAuthAdminFX.onChanged");
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
        XCBFix.runLater(() -> {
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
                    Bootloader bootloader = meinAuthService.getMeinBoot().getBootLoader(serviceJoinServiceType.getType().v());
                    if (bootloader instanceof BootLoaderFX) {
                        BootLoaderFX bootLoaderFX = (BootLoaderFX) bootloader;
                        ImageView image = new ImageView(new Image(bootLoaderFX.getIconURL(), IMAGE_SIZE, IMAGE_SIZE, true, true));
                        button.setGraphic(image);
                        button.setOnAction(event -> {
                            showService(bootLoaderFX, serviceJoinServiceType);
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
                            Lok.debug("MeinAuthAdminFX.initialize.createmenu.clicked");
                            runner.r(() -> {
                                        onCreateMenuItemClicked(meinAuthService.getMeinBoot().getBootloaderMap().get(name));
                                        createServiceMenu.hide();
                                    }
                            );
                        });
                        N.r(() -> {
                            Bootloader bootLoader = meinAuthService.getMeinBoot().getBootLoader(name);
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

    private void showService(BootLoaderFX bootLoaderFX, ServiceJoinServiceType serviceJoinServiceType) {
        IMeinService meinService = meinAuthService.getMeinService(serviceJoinServiceType.getUuid().v());
        if (meinService == null) {
            N.r(() -> {
                Service service = meinAuthService.getDatabaseManager().getServiceByUuid(serviceJoinServiceType.getUuid().v());
                loadSettingsFX( "de/mein/auth/error.fxml");
                ErrorController errorController = (ErrorController) contentController;
                errorController.showError(service);
            });
        } else {
            loadSettingsFX(bootLoaderFX.getEditFXML(meinService), null);
            ServiceSettingsFX serviceSettingsFX = (ServiceSettingsFX) contentController;
            serviceSettingsFX.feed(serviceJoinServiceType);
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        btnAbout.setOnAction(e -> {
            try {
                Class test = Class.forName("javafx.scene.WebView");
                if (test != null)
                    loadSettingsFX("de/mein/auth/about.fxml");
            } catch (ClassNotFoundException ex) {
                Lok.error("could not load web view. switching fxml...");
                loadSettingsFX("de/mein/auth/aboutnoweb.fxml");
            }
        });
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
        btnNotifications.setOnAction(event -> XCBFix.runLater(() -> N.r(this::loadNotificationCenter)));
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
        imgAbout.setImage(new Image("/de/mein/icon/about.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
    }

    private ObservableList<MeinNotification> notifications = FXCollections.observableArrayList();

    @Override
    public void onNotificationFromService(IMeinService meinService, MeinNotification meinNotification) {
        N.r(() -> {
            notifications.add(meinNotification);
            meinNotification.addProgressListener(this);
            XCBFix.runLater(() -> trayIcon.displayMessage(meinNotification.getTitle(), meinAuthService.getCompleteNotificationText(meinNotification), TrayIcon.MessageType.INFO));
        });
    }

    public ObservableList<MeinNotification> getNotifications() {
        return notifications;
    }

    public void displayTray() throws AWTException, IOException {
        //Obtain only one instance of the SystemTray object
        SystemTray tray = SystemTray.getSystemTray();

        //If the icon is a file
        //URL url = MeinAuthAdmin.class.getResource("de/mein/icon/app_square.png");
        final String res = "/de/mein/icon/app_square.png";
        BufferedImage img = ImageIO.read(getClass().getResourceAsStream(res));
        //Alternative (if the icon is on the classpath):
        trayIcon = new TrayIcon(img, "Tray Demo");
        //Let the system resizes the image if needed
        trayIcon.setImageAutoSize(true);
        //Set tooltip text for the tray icon
        trayIcon.setToolTip("System tray icon demo");
        //add menu. note: trayIcon.actionListener is not called on KDE Plasma 5. that's why
        N.INoTryRunnable notificationCenterRunnable = () -> {
            loadNotificationCenter();
        };
        PopupMenu menu = new PopupMenu();
        java.awt.MenuItem menuNotification = new java.awt.MenuItem();
        menuNotification.setLabel("NotificationCenter");
        menuNotification.addActionListener(e -> XCBFix.runLater(() -> N.r(notificationCenterRunnable)));

        java.awt.MenuItem menuExit = new java.awt.MenuItem();
        menuExit.setLabel("Exit");
        menuExit.addActionListener(e -> XCBFix.runLater(() -> {
            shutDown();
            System.exit(0);
        }));

        menu.add(menuNotification);
        menu.add(menuExit);
        trayIcon.setPopupMenu(menu);
        trayIcon.addActionListener(e -> XCBFix.runLater(() -> N.r(notificationCenterRunnable)));
        tray.add(trayIcon);
        //trayIcon.displayMessage("Hello, World", "notification demo", TrayIcon.MessageType.INFO);
    }

    private NotificationCenter loadNotificationCenter() throws IOException {
        if (notificationCenter == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("de/mein/auth/notificationcenter.fxml"));
            Parent root = loader.load();
            NotificationCenter notificationCenter = loader.getController();
            notificationCenter.setMeinAuthAdminFX(this);
            Scene scene = new Scene(root);
            Stage stage = createStage(scene);
            stage.setTitle("Notification Center");
            stage.setScene(scene);
            stage.show();
            Lok.debug("MeinAuthAdminFX.displayTray");
            notificationCenter.showNotifications();
        } else {
            notificationCenter.show();
        }
        return notificationCenter;
    }

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
    }

    private void onCreateMenuItemClicked(Class<? extends Bootloader<? extends MeinService>> bootloaderClass) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        Bootloader bootLoader = meinAuthService.getMeinBoot().createBootLoader(meinAuthService, bootloaderClass);
        if (bootLoader instanceof BootLoaderFX) {
            showPrimaryButtonOnly();
            BootLoaderFX bootLoaderFX = (BootLoaderFX) bootLoader;
            if (bootLoaderFX.embedCreateFXML()) {
                loadSettingsFX("de/mein/choose.server.fxml");
                RemoteServiceChooserFX remoteServiceChooserFX = (RemoteServiceChooserFX) contentController;
                remoteServiceChooserFX.createFXML(((BootLoaderFX) bootLoader).getCreateFXML());
                lblTitle.setText(contentController.getTitle());
            } else {
                loadSettingsFX(((BootLoaderFX) bootLoader).getCreateFXML(), null);
            }
        } else {
            Lok.debug("MeinAuthAdminFX.onCreateMenuItemClicked.NO.FX.BOOTLOADER");
        }

    }

    /**
     * loads with this instances resource bundle
     *
     * @param resource
     */
    private void loadSettingsFX(String resource) {
        loadSettingsFX(resource, this.resourceBundle);
    }

    /**
     * loads with a given resource bundle
     *
     * @param resource
     * @param resourceBundle
     */
    private void loadSettingsFX(String resource, ResourceBundle resourceBundle) {
        N.r(() -> {
            lblTitle.setVisible(true);
            contentController = null;
            FXMLLoader lo = new FXMLLoader(getClass().getClassLoader().getResource(resource));
            if (resourceBundle != null)
                lo.setResources(resourceBundle);
            Pane pane = lo.load();
            contentController = lo.getController();
            contentController.configureParentGui(this);
            contentController.setMeinAuthService(meinAuthService);
            lblTitle.setText(contentController.getTitle());
            setContentPane(pane);
            Lok.debug("MeinAuthAdminFX.loadSettingsFX.loaded");
        });
    }


    private void setContentPane(Pane pane) {
        paneContainer.getChildren().clear();
        paneContainer.getChildren().add(pane);
        pane.prefWidthProperty().bind(paneContainer.widthProperty());
        pane.prefHeightProperty().bind(paneContainer.heightProperty());
        AnchorPane.setBottomAnchor(pane, 0.0);
        AnchorPane.setLeftAnchor(pane, 0.0);
        AnchorPane.setRightAnchor(pane, 0.0);
        AnchorPane.setTopAnchor(pane, 0.0);
    }

    public static void main(String[] args) {
        WaitLock waitLock = new WaitLock().lock();
        waitLock.lock();
        Lok.debug("MeinAuthAdminFX.main");
    }

    @SuppressWarnings("Duplicates")
    public static MeinAuthAdminFX load(MeinAuthService meinAuthService) {
        new JFXPanel();
        Platform.setImplicitExit(false);
        final MeinAuthAdminFX[] meinAuthAdminFXES = new MeinAuthAdminFX[1];
        WaitLock lock = new WaitLock().lock();
        XCBFix.runLater(() -> {
                    FxApp.setRunAfterStart(() -> {
                        try {
                            Lok.debug("MeinAuthAdminFX.load...");
                            FXMLLoader loader = new FXMLLoader(MeinAuthAdminFX.class.getClassLoader().getResource("de/mein/auth/mainwindow.fxml"));
                            // Intellij Idea might crash here cause it cannot find the locale.
                            // Workaround: Settings/Build&Exec.../Gradle/Runner -> check "Delegate IDE build/run actions to gradle"
                            ResourceBundle resourceBundle = ResourceBundle.getBundle("de/mein/auth/FxUi", new ResourceBundle.Control() {
                                @Override
                                public List<Locale> getCandidateLocales(String name, Locale locale) {
                                    return Collections.singletonList(Locale.ROOT);
                                }
                            });
                            loader.setResources(resourceBundle);
                            HBox root = null;
                            root = loader.load();
                            meinAuthAdminFXES[0] = loader.getController();
                            meinAuthAdminFXES[0].resourceBundle = resourceBundle;
                            meinAuthAdminFXES[0].start(meinAuthService);
                            Scene scene = new Scene(root);
                            //apply theme
                            scene.getStylesheets().add(MeinAuthAdmin.class.getResource(GLOBAL_STYLE_CSS).toExternalForm());

                            Stage stage = createStage(scene);
                            stage.setTitle(resourceBundle.getString("windowTitle") + " '" + meinAuthService.getName() + "'");
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
                    });
                    FxApp.start();
                }
        );
        lock.lock();
        return meinAuthAdminFXES[0];
    }

    public static Stage createStage(Scene scene) {
        Image image = new Image("/de/mein/icon/app_square.png");
        Stage stage = new Stage();
        stage.getIcons().add(image);
        scene.getStylesheets().add(GLOBAL_STYLE_CSS);
        stage.setScene(scene);
        return stage;
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


    @Override
    public void onProgress(MeinNotification notification, int max, int current, boolean indeterminate) {

    }

    @Override
    public void onCancel(MeinNotification notification) {
        notifications.remove(notification);
    }

    @Override
    public void onFinish(MeinNotification notification) {

    }
}
