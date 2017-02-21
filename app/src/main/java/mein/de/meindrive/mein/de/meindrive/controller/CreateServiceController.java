package mein.de.meindrive.mein.de.meindrive.controller;

import android.content.ContentValues;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

import de.mein.auth.boot.BootLoader;
import de.mein.auth.boot.MeinBoot;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.NoTryRunner;
import de.mein.drive.AndroidDriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.service.MeinDriveServerService;
import mein.de.meindrive.MainActivity;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/20/17.
 */
public class CreateServiceController {
    private final MeinAuthService meinAuthService;
    private final Spinner spinner;
    private View rootView;

    public CreateServiceController(MeinAuthService meinAuthService, View v) {
        this.rootView = v;
        this.meinAuthService = meinAuthService;
        this.spinner = (Spinner) rootView.findViewById(R.id.spin_bootloaders);

        List<BootLoader> bootLoaders = new ArrayList<>();
        MeinBoot.getBootloaderMap().forEach((name, bootloaderClass) -> NoTryRunner.run(() -> {
            BootLoader bootLoader = bootloaderClass.newInstance();
            bootLoaders.add(bootLoader);
            //MeinDriveServerService serverService = new DriveCreateController(meinAuthService).createDriveServerService("server service", testdir1.getAbsolutePath());

            System.out.println("CreateServiceController.CreateServiceController");
        }));
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<BootLoader> adapter = new ArrayAdapter<>(rootView.getContext(), android.R.layout.simple_spinner_item, bootLoaders);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
    }
}
