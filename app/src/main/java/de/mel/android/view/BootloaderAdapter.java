package de.mel.android.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import de.mel.R;
import de.mel.android.boot.AndroidBootLoader;
import de.mel.auth.service.Bootloader;

/**
 * Created by xor on 9/19/17.
 */

public class BootloaderAdapter extends MelListAdapter<Bootloader> {


    public BootloaderAdapter(Context context, List<Bootloader> bootloaders) {
        super(context);
        items.addAll(bootloaders);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View view, @NonNull ViewGroup parent) {
        if (view == null)
            view = layoutInflator.inflate(R.layout.spinner_melservice, null);
        ImageView imageIcon = view.findViewById(R.id.imageIcon);
        TextView txtText = view.findViewById(R.id.txtText);
        Bootloader bootLoader = items.get(position);
        Drawable icon = null;
        if (bootLoader instanceof AndroidBootLoader) {
            icon = ContextCompat.getDrawable(layoutInflator.getContext(), ((AndroidBootLoader) bootLoader).getMenuIcon());
        }
        imageIcon.setImageDrawable(icon);
        txtText.setText(bootLoader.getName());
        return view;
    }
}
