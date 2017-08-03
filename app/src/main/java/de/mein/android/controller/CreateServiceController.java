package de.mein.android.controller;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.List;

import de.mein.R;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.AndroidService;
import de.mein.auth.tools.N;
import de.mein.sql.SqlQueriesException;
import de.mein.android.MainActivity;

/**
 * Created by xor on 2/20/17.
 */
public class CreateServiceController extends GuiController {
    private final MeinAuthService meinAuthService;
    private final Spinner spinner;
    private View rootView;
    private LinearLayout embedded;
    private Button btnCreate;
    private AndroidBootLoader bootLoader;
    private MainActivity activity;
    private AndroidServiceCreatorGuiController currentController;

    public CreateServiceController(MeinAuthService meinAuthService, MainActivity activity, View v) {
        super(activity);
        this.rootView = v;
        this.activity = activity;
        this.meinAuthService = meinAuthService;
        this.spinner = (Spinner) rootView.findViewById(R.id.spin_bootloaders);
        this.embedded = (LinearLayout) rootView.findViewById(R.id.embedded);
        this.btnCreate = (Button) rootView.findViewById(R.id.btnCreate);


        List<BootLoader> bootLoaders = new ArrayList<>();
        Stream.of(meinAuthService.getMeinBoot().getBootloaderClasses()).forEach(bootloaderClass -> N.r(() -> {
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
        btnCreate.setOnClickListener(view -> {
            if (bootLoader != null) {
                bootLoader.createService(activity, meinAuthService, currentController);
                activity.showMenuServices();
            }
        });
    }

    private void showSelected() {
        N.r(() -> {
            bootLoader = (AndroidBootLoader) spinner.getSelectedItem();
            View v = View.inflate(rootView.getContext(), bootLoader.getCreateResource(), embedded);
            currentController = bootLoader.createGuiController(meinAuthService, activity, v);
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

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }
}
