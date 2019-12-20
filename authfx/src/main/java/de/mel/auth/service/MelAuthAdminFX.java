package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.FxApp;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelNotification;
import de.mel.auth.boot.BootLoaderFX;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.gui.*;
import de.mel.auth.gui.notification.NotificationCenter;
import de.mel.auth.tools.N;
import de.mel.auth.tools.WaitLock;
import de.mel.sql.SqlQueriesException;
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
public class MelAuthAdminFX implements Initializable, MelAuthAdmin, MelNotification.MelProgressListener {
    public static final String APP_ICON_RES = "/de/mel/icon/tray.png";


    private static final int IMAGE_SIZE = 22;
    public static final String GLOBAL_STYLE_CSS = "/de/mel/modena_dark.css";
    //    public static final String GLOBAL_STYLE_CSS = "/de/mel/jmetro.dark.css";
    private MelAuthServiceImpl melAuthService;
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
    private Locale locale;

    @Override
    public void start(MelAuthServiceImpl melAuthService) {
        this.melAuthService = melAuthService;
        showServices();
    }

    public MelAuthAdminFX() {

    }

    @Override
    public void onChanged() {
        Lok.debug("MelAuthAdminFX.onChanged");
        showServices();
    }

    @Override
    public void shutDown() {
        XCBFix.runLater(() -> {
            stage.close();
            if (trayIcon != null) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
        });
    }

    private void showServices() {
        XCBFix.runLater(() -> {
            try {
                List<ServiceJoinServiceType> serviceJoinServiceTypes = melAuthService.getDatabaseManager().getAllServices();
                vboxServices.getChildren().clear();
                for (ServiceJoinServiceType serviceJoinServiceType : serviceJoinServiceTypes) {
                    IMelService runningService = melAuthService.getMelService(serviceJoinServiceType.getUuid().v());
                    if (runningService != null)
                        serviceJoinServiceType.setRunning(true);
                    // fill vboxServices with Buttons!
                    Button button = new Button(serviceJoinServiceType.getName().v());
                    button.setMaxWidth(Double.MAX_VALUE);
                    button.setAlignment(Pos.TOP_LEFT);
                    Bootloader bootloader = melAuthService.getMelBoot().getBootLoader(serviceJoinServiceType.getType().v());
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
                ImageView imgCreate = new ImageView(new Image("de/mel/icon/add.png", IMAGE_SIZE * .8, IMAGE_SIZE * .8, true, true));
                Button btnCreateService = new Button(resourceBundle.getString("btn.createService"));
                btnCreateService.setGraphic(imgCreate);
                btnCreateService.setMaxWidth(Double.MAX_VALUE);
                btnCreateService.setAlignment(Pos.TOP_LEFT);
                btnCreateService.setOnAction(event -> {
                    ContextMenu createServiceMenu = new ContextMenu();
                    createServiceMenu.getItems().clear();
                    List<String> names = new ArrayList<>(melAuthService.getMelBoot().getBootloaderMap().keySet());
                    Collections.sort(names);
                    for (String name : names) {

                        MenuItem menuItem = new MenuItem(name);
                        menuItem.setOnAction(e1 -> {
                            Lok.debug("MelAuthAdminFX.initialize.createmenu.clicked");
                            runner.r(() -> {
                                        onCreateMenuItemClicked(melAuthService.getMelBoot().getBootloaderMap().get(name));
                                        createServiceMenu.hide();
                                    }
                            );
                        });
                        N.r(() -> {
                            Bootloader bootLoader = melAuthService.getMelBoot().getBootLoader(name);
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
        IMelService melService = melAuthService.getMelService(serviceJoinServiceType.getUuid().v());
        if (melService == null) {
            N.r(() -> {
                Service service = melAuthService.getDatabaseManager().getServiceByUuid(serviceJoinServiceType.getUuid().v());
                loadSettingsFX("de/mel/auth/error.fxml");
                ErrorController errorController = (ErrorController) contentController;
                errorController.showError(service);
            });
        } else {
            loadServiceSettingsFX(bootLoaderFX.getEditFXML(melService), bootLoaderFX.getResourceBundle(locale), serviceJoinServiceType);

        }
    }

    public Locale getLocale() {
        return locale;
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void showInfo() {
        loadSettingsFX("de/mel/auth/info.fxml");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        btnAbout.setOnAction(e -> {
            try {
                Class test = Class.forName("javafx.scene.WebView");
                if (test != null)
                    loadSettingsFX("de/mel/auth/about.fxml");
            } catch (ClassNotFoundException ex) {
                Lok.error("could not load web view. switching fxml...");
                loadSettingsFX("de/mel/auth/aboutnoweb.fxml");
            }
        });
        btnSettings.setOnAction(event -> loadSettingsFX("de/mel/auth/settings.fxml"));
        btnInfo.setOnAction(event -> showInfo());
        btnSecondary.setOnAction(event -> {
            if (contentController != null) {
                contentController.onSecondaryClicked();
                contentController = null;
                paneContainer.getChildren().clear();
            }
        });
        btnPrimary.setOnAction(event -> {
            if (contentController != null) {
                if (contentController.onPrimaryClicked()) {
                    contentController = null;
                    paneContainer.getChildren().clear();
                }
            }
        });
        btnOthers.setOnAction(event -> {
            loadSettingsFX("de/mel/auth/others.fxml");
        });
        btnNotifications.setOnAction(event -> XCBFix.runLater(() -> N.r(this::loadNotificationCenter)));
        btnAccess.setOnAction(event -> loadSettingsFX("de/mel/auth/access.fxml"));
        btnPairing.setOnAction(event -> loadSettingsFX("de/mel/auth/pairing.fxml"));
        tpServices.expandedProperty().addListener((observable, oldValue, newValue) -> showServices());
        //add system tray
        if (SystemTray.isSupported()) {
            N.r(this::displayTray);
        } else {
            System.err.println("System tray not supported!");
        }
        // load images for buttons
        Image im = new Image("/de/mel/icon/access.n.png", IMAGE_SIZE, IMAGE_SIZE, true, false);
        imgAccess.setImage(im);
        imgInfo.setImage(new Image("/de/mel/icon/info.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
        imgOthers.setImage(new Image("/de/mel/icon/connected.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
        imgPairing.setImage(new Image("/de/mel/icon/pairing.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
        imgSettings.setImage(new Image("/de/mel/icon/settings.n.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
        imgAbout.setImage(new Image("/de/mel/icon/about.png", IMAGE_SIZE, IMAGE_SIZE, true, true));
    }

    private ObservableList<MelNotification> notifications = FXCollections.observableArrayList();

    @Override
    public void onNotificationFromService(IMelService melService, MelNotification melNotification) {
        N.r(() -> {
            notifications.add(melNotification);
            melNotification.addProgressListener(this);
            XCBFix.runLater(() -> trayIcon.displayMessage(melNotification.getTitle(), melAuthService.getCompleteNotificationText(melNotification), TrayIcon.MessageType.INFO));
        });
    }

    public ObservableList<MelNotification> getNotifications() {
        return notifications;
    }

    public void displayTray() throws AWTException, IOException {
        //Obtain only one instance of the SystemTray object
        SystemTray tray = SystemTray.getSystemTray();

        //If the icon is a file
        //URL url = MelAuthAdmin.class.getResource("de/mel/icon/app_square.png");
        BufferedImage img = ImageIO.read(getClass().getResourceAsStream(APP_ICON_RES));
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
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("de/mel/auth/notificationcenter.fxml"));
            loader.setResources(resourceBundle);
            Parent root = loader.load();
            NotificationCenter notificationCenter = loader.getController();
            notificationCenter.setMelAuthAdminFX(this);
            Scene scene = new Scene(root);
            Stage stage = createStage(scene);
            stage.setTitle(resourceBundle.getString("notificationCenter"));
            stage.setScene(scene);
            stage.show();
            Lok.debug("MelAuthAdminFX.displayTray");
            notificationCenter.showNotifications();
        } else {
            notificationCenter.show();
        }
        return notificationCenter;
    }

    public MelAuthServiceImpl getMelAuthService() {
        return melAuthService;
    }

    private void onCreateMenuItemClicked(Class<? extends Bootloader<? extends MelService>> bootloaderClass) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        Bootloader bootLoader = melAuthService.getMelBoot().createBootLoader(melAuthService, bootloaderClass);
        if (bootLoader instanceof BootLoaderFX) {
            showPrimaryButtonOnly();
            BootLoaderFX bootLoaderFX = (BootLoaderFX) bootLoader;
            ResourceBundle resourceBundle = bootLoaderFX.getResourceBundle(locale);
            if (bootLoaderFX.embedCreateFXML()) {
                loadSettingsFX("de/mel/choose.server.fxml");
                RemoteServiceChooserFX remoteServiceChooserFX = (RemoteServiceChooserFX) contentController;
                remoteServiceChooserFX.createFXML(bootLoader, resourceBundle);
                lblTitle.setText(contentController.getTitle());
            } else {
                loadSettingsFX(((BootLoaderFX) bootLoader).getCreateFXML(), resourceBundle);
            }
        } else {
            Lok.debug("MelAuthAdminFX.onCreateMenuItemClicked.NO.FX.BOOTLOADER");
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
            contentController.setMelAuthService(melAuthService);
            lblTitle.setText(contentController.getTitle());
            setContentPane(pane);
            Lok.debug("MelAuthAdminFX.loadSettingsFX.loaded");
        });
    }

    /**
     * loads with a given resource bundle
     *
     * @param resource
     * @param resourceBundle
     * @param serviceJoinServiceType
     * @return
     */
    private void loadServiceSettingsFX(String resource, ResourceBundle resourceBundle, ServiceJoinServiceType serviceJoinServiceType) {
        try {
            lblTitle.setVisible(true);
            lblTitle.setText(this.resourceBundle.getString("service.title"));
            FXMLLoader containerLoader = new FXMLLoader(getClass().getClassLoader().getResource("de/mel/auth/editservice.fxml"));
            containerLoader.setResources(this.resourceBundle);
            Pane containerPane = containerLoader.load();
            ServiceSettingsController serviceSettingsController = containerLoader.getController();
//            serviceSettingsController.initialize(lo);
            contentController = serviceSettingsController;
            FXMLLoader lo = new FXMLLoader(getClass().getClassLoader().getResource(resource));
            if (resourceBundle != null)
                lo.setResources(resourceBundle);
            Pane pane = lo.load();

            ServiceSettingsFX embeddedController = lo.getController();
            serviceSettingsController.setContent(pane, embeddedController);
            serviceSettingsController.setMelAuthService(melAuthService);
            serviceSettingsController.configureParentGui(this);
            serviceSettingsController.feed(serviceJoinServiceType);
//            contentController = lo.getController();
//            contentController.configureParentGui(this);
//            contentController.setMelAuthService(melAuthService);

            setContentPane(containerPane);
            Lok.debug("MelAuthAdminFX.loadSettingsFX.loaded");
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        Lok.debug("MelAuthAdminFX.main");
    }

    @SuppressWarnings("Duplicates")
    public static MelAuthAdminFX load(MelAuthServiceImpl melAuthService) {
        new JFXPanel();
        Platform.setImplicitExit(false);
        final MelAuthAdminFX[] melAuthAdminFXES = new MelAuthAdminFX[1];
        WaitLock lock = new WaitLock().lock();
        XCBFix.runLater(() -> {
                    FxApp.setRunAfterStart(() -> {
                        try {
                            Lok.debug("MelAuthAdminFX.load...");
                            Locale locale;
                            if (melAuthService.getSettings().getLanguage() == null)
                                locale = new Locale(Locale.getDefault().getLanguage(), "");
                            else
                                locale = new Locale(melAuthService.getSettings().getLanguage(), "");
                            FXMLLoader loader = new FXMLLoader(MelAuthAdminFX.class.getResource("/de/mel/auth/mainwindow.fxml"));
                            ResourceBundle resourceBundle = ResourceBundle.getBundle("de/mel/auth/mainwindow", locale);
                            loader.setResources(resourceBundle);
                            HBox root = null;
                            root = loader.load();
                            melAuthAdminFXES[0] = loader.getController();
                            melAuthAdminFXES[0].locale = locale;
                            melAuthAdminFXES[0].resourceBundle = resourceBundle;
                            melAuthAdminFXES[0].resourceBundle = resourceBundle;
                            melAuthAdminFXES[0].start(melAuthService);
                            melAuthAdminFXES[0].setupRegisterHandlers();
                            N.r(() -> melAuthService.getSettings().setLanguage(locale.getLanguage()).save());
                            Scene scene = new Scene(root);
                            //apply theme
                            scene.getStylesheets().add(MelAuthAdmin.class.getResource(GLOBAL_STYLE_CSS).toExternalForm());

                            Stage stage = createStage(scene);
                            stage.setTitle(resourceBundle.getString("windowTitle") + ": '" + melAuthService.getName() + "'");
                            stage.show();
                            stage.setOnCloseRequest(event -> {
                                melAuthAdminFXES[0].shutDown();
                                System.exit(0);
                            });
                            melAuthAdminFXES[0].setStage(stage);
                            melAuthAdminFXES[0].showServices();
                            melAuthAdminFXES[0].showInfo();
                            lock.unlock();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    FxApp.start();
                }
        );
        lock.lock();
        return melAuthAdminFXES[0];
    }

    private void setupRegisterHandlers() {
        N.forEach(melAuthService.getRegisterHandlers(), iRegisterHandler -> iRegisterHandler.setup(this));

    }

    public static Stage createStage(Scene scene) {
        Image image = new Image(APP_ICON_RES);
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
    public void onProgress(MelNotification notification, int max, int current, boolean indeterminate) {

    }

    @Override
    public void onCancel(MelNotification notification) {
        notifications.remove(notification);
    }

    @Override
    public void onFinish(MelNotification notification) {

    }
}
