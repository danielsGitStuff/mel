package de.mel.android.controller;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.Notifier;
import de.mel.android.view.BootloaderAdapter;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.android.boot.AndroidBootLoader;
import de.mel.android.service.AndroidService;
import de.mel.auth.service.MelService;
import de.mel.auth.tools.N;
import de.mel.android.MainActivity;

/**
 * Created by xor on 2/20/17.
 */
public class CreateServiceController extends WakelockedGuiController implements PermissionsGrantedListener {
    @Override
    public void onPermissionsGranted() {
        btnCreate.setOnClickListener(defaultBtnCreateListener);
        btnCreate.setText(R.string.btnCreate);
    }

    private final Spinner spinner;
    private final EditText txtName;
    private LinearLayout embedded;
    private Button btnCreate;
    private AndroidBootLoader bootLoader;
    private AndroidServiceGuiController currentController;
    private MainActivity mainActivity;
    private View.OnClickListener defaultBtnCreateListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (bootLoader != null) {
                try {
                    if (currentController.onOkClicked()) {
                        currentController.setName(txtName.getText().toString());
                        bootLoader.createService(activity, activity.getAndroidService().getMelAuthService(), currentController);
                        mainActivity.showInfo();
                        mainActivity.showMenuServices();
                        mainActivity.showMessage(R.string.success, R.string.successCreateService);
                    }
                } catch (Exception e) {
                    mainActivity.showMessage(R.string.error, R.string.errorCreateService);
                }
            } else {
                mainActivity.showMessage(R.string.error, R.string.errorCreateService);
            }
        }
    };

    public CreateServiceController(MainActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_create_service);
        this.mainActivity = activity;
        this.spinner = rootView.findViewById(R.id.spin_bootloaders);
        this.embedded = rootView.findViewById(R.id.embedded);
        this.btnCreate = rootView.findViewById(R.id.btnCreate);
        this.txtName = rootView.findViewById(R.id.txtName);

    }

    public void invalidateLayout() {
        embedded.invalidate();
    }

    private void showSelected() {
        N.r(() -> {
            bootLoader = (AndroidBootLoader) spinner.getSelectedItem();
            if (bootLoader != null) {
                embedded.removeAllViews();
                currentController = bootLoader.inflateEmbeddedView(embedded, activity, androidService.getMelAuthService(), null);
                currentController.setOnPermissionsGrantedListener(this);
                if (currentController instanceof RemoteServiceChooserController) {
                    RemoteServiceChooserController chooserController = (RemoteServiceChooserController) currentController;
                    chooserController.setCreateServiceController(this);
                }

                if (activity.hasPermissions(bootLoader.getPermissions())) {
                    onPermissionsGranted();
                } else {
                    btnCreate.setOnClickListener(v -> {
                        activity.askUserForPermissions(bootLoader.getPermissions()
                                , this
                                , currentController.getPermissionsTitle()
                                , currentController.getPermissionsText(),
                                this::onPermissionsGranted
                                , r -> Notifier.toast(mainActivity, R.string.infufficientPermissions)
                        );
                    });
                    btnCreate.setText(R.string.btnCreateRequestPerm);
                }

                Bootloader bl = (Bootloader) bootLoader;
                txtName.setText(bl.getName());
            }
            Lok.debug("CreateServiceController.showSelected");
        });

    }


    @Override
    public Integer getTitle() {
        return R.string.createServiceTitle;
    }


    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        if (bootLoader == null) {
            activity.runOnUiThread(() -> {
                MelAuthService melAuthService = androidService.getMelAuthService();
                List<Bootloader> bootloaders = new ArrayList<>();
                for (Class<? extends Bootloader<? extends MelService>> bootloaderClass : melAuthService.getMelBoot().getBootloaderClasses()) {
                    N.r(() -> {
                        Bootloader bootLoader = melAuthService.getMelBoot().createBootLoader(melAuthService, bootloaderClass);
                        bootloaders.add(bootLoader);
                        //MelDriveServerService serverService = new DriveCreateController(melAuthService).createDriveServerService("server service", testdir1.getAbsolutePath());
                        Lok.debug("CreateServiceController.CreateServiceController");
                    });
                }
                Collections.sort(bootloaders, (b1, b2) -> b1.getName().compareToIgnoreCase(b2.getName()));
                BootloaderAdapter adapter = new BootloaderAdapter(rootView.getContext(), bootloaders);
                // Specify the layout to use when the list of choices appears
                // Apply the adapter to the spinner
                spinner.setAdapter(adapter);
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        showSelected();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                });
                //showSelected();
                btnCreate.setOnClickListener(defaultBtnCreateListener);
            });
        }
    }

    @Override
    public Integer getHelp() {
        return R.string.createServiceHelp;
    }

    public void setBtnCreateTitle(int resource) {
        btnCreate.setText(resource);
    }

    public void setBtnCreateEnabled(boolean enabled) {
        btnCreate.setEnabled(enabled);
    }
}
