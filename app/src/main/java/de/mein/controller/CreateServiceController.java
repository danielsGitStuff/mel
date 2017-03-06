package de.mein.controller;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

import de.mein.auth.boot.BootLoader;
import de.mein.auth.boot.MeinBoot;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.NoTryRunner;
import de.mein.boot.AndroidBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.android.AndroidService;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/20/17.
 */
public class CreateServiceController implements GuiController {
    private final MeinAuthService meinAuthService;
    private final Spinner spinner;
    private View rootView;
    private LinearLayout embedded;

    public CreateServiceController(MeinAuthService meinAuthService, View v) {
        this.rootView = v;
        this.meinAuthService = meinAuthService;
        this.spinner = (Spinner) rootView.findViewById(R.id.spin_bootloaders);
        this.embedded = (LinearLayout) rootView.findViewById(R.id.embedded);

        List<BootLoader> bootLoaders = new ArrayList<>();
        MeinBoot.getBootloaderClasses().forEach((bootloaderClass) -> NoTryRunner.run(() -> {
            BootLoader bootLoader = bootloaderClass.newInstance();
            bootLoaders.add(bootLoader);
            //MeinDriveServerService serverService = new DriveCreateController(meinAuthService).createDriveServerService("server service", testdir1.getAbsolutePath());

            System.out.println("CreateServiceController.CreateServiceController");
        }));
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<BootLoader> adapter = new ArrayAdapter<>(rootView.getContext(), R.layout.support_simple_spinner_dropdown_item, bootLoaders);
        // Specify the layout to use when the list of choices appears
        //adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        showSelected();
    }

    private void showSelected() {
        NoTryRunner.run(() -> {
            DriveCreateController createController = new DriveCreateController(meinAuthService);
            AndroidBootLoader bootLoader = (AndroidBootLoader) spinner.getSelectedItem();
            View v = View.inflate(rootView.getContext(), bootLoader.getCreateResource(), embedded);
            //bootLoader.setupController(meinAuthService,v);
            System.out.println("CreateServiceController.showSelected");
        });

    }

    @Override
    public void onMeinAuthStarted(MeinAuthService androidService) {

    }

    @Override
    public void onAndroidServiceBound(AndroidService androidService) {

    }
}
