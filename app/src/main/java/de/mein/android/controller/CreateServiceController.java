package de.mein.android.controller;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.Notifier;
import de.mein.android.view.BootloaderAdapter;
import de.mein.auth.service.Bootloader;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.service.AndroidService;
import de.mein.auth.service.MeinService;
import de.mein.auth.tools.N;
import de.mein.android.MainActivity;

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
                        bootLoader.createService(activity, activity.getAndroidService().getMeinAuthService(), currentController);
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

    public void invalidateLayout(){
        embedded.invalidate();
    }

    private void showSelected() {
        N.r(() -> {
            bootLoader = (AndroidBootLoader) spinner.getSelectedItem();
            if (bootLoader != null) {
                embedded.removeAllViews();
                currentController = bootLoader.inflateEmbeddedView(embedded, activity, androidService.getMeinAuthService(), null);
                currentController.setOnPermissionsGrantedListener(this);
                if (currentController instanceof RemoteServiceChooserController){
                    RemoteServiceChooserController chooserController = (RemoteServiceChooserController) currentController;
                    chooserController.setCreateServiceController(this);
                }
                if (activity.hasPermissions(bootLoader.getPermissions())) {
                    onPermissionsGranted();
                } else {
                    btnCreate.setOnClickListener(v -> {
                        activity.annoyWithPermissions(bootLoader.getPermissions())
                                .done(result -> {
                                    Notifier.toast(activity, "granted");
                                    btnCreate.setOnClickListener(defaultBtnCreateListener);
                                    btnCreate.setText(R.string.btnCreate);
                                })
                                .fail(r -> Notifier.toast(mainActivity, R.string.infufficientPermissions));
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
                MeinAuthService meinAuthService = androidService.getMeinAuthService();
                List<Bootloader> bootloaders = new ArrayList<>();
                for (Class<? extends Bootloader<? extends MeinService>> bootloaderClass : meinAuthService.getMeinBoot().getBootloaderClasses()) {
                    N.r(() -> {
                        Bootloader bootLoader = meinAuthService.getMeinBoot().createBootLoader(meinAuthService, bootloaderClass);
                        bootloaders.add(bootLoader);
                        //MeinDriveServerService serverService = new DriveCreateController(meinAuthService).createDriveServerService("server service", testdir1.getAbsolutePath());
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
