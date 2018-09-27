package de.mein.android.controller.intro;

import android.widget.EditText;

import java.io.IOException;

import de.mein.R;
import de.mein.android.Tools;
import de.mein.android.service.AndroidService;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.core.serialize.exceptions.JsonSerializationException;

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
            MeinAuthSettings settings = androidService.getMeinAuthService().getSettings();
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
        txtName.setText(androidService.getMeinAuthService().getSettings().getName());
    }
}
