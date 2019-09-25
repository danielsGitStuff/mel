package de.mel.android.controller.intro;

import android.widget.EditText;

import java.io.IOException;

import de.mel.R;
import de.mel.android.Tools;
import de.mel.android.service.AndroidService;
import de.mel.auth.data.MelAuthSettings;
import de.mel.core.serialize.exceptions.JsonSerializationException;

public class SecondPage extends IntroPageController {
    private EditText txtName;

    SecondPage(IntroWrapper introWrapper) {
        super(introWrapper, R.layout.intro_second);
        txtName = rootView.findViewById(R.id.txtName);
    }

    @Override
    public Integer getTitle() {
        return R.string.intro_2_title;
    }

    @Override
    public Integer getHelp() {
        return null;
    }

    @Override
    public String getError() {
        String name = txtName.getText().toString();
        if (name.length() > 0) {
            MelAuthSettings settings = androidService.getMelAuthService().getSettings();
            settings.setName(name);
            try {
                settings.save();
                return null;
            } catch (JsonSerializationException | IllegalAccessException | IOException e) {
                e.printStackTrace();
                return e.getCause().toString();
            }
        }
        return Tools.getApplicationContext().getString(R.string.intro_2_error_too_short);
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        txtName.setText(androidService.getMelAuthService().getSettings().getName());
    }
}
