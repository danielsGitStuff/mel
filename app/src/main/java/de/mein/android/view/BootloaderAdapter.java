package de.mein.android.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import de.mein.R;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.auth.service.BootLoader;

/**
 * Created by xor on 9/19/17.
 */

public class BootloaderAdapter extends MeinListAdapter<BootLoader> {


    public BootloaderAdapter(Context context, List<BootLoader> bootLoaders) {
        super(context);
        items.addAll(bootLoaders);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View view, @NonNull ViewGroup parent) {
        if (view == null)
            view = layoutInflator.inflate(R.layout.spinner_meinservice, null);
        ImageView imageIcon = view.findViewById(R.id.imageIcon);
        TextView txtText = view.findViewById(R.id.txtText);
        BootLoader bootLoader = items.get(position);
        Drawable icon = null;
        if (bootLoader instanceof AndroidBootLoader) {
            icon = ContextCompat.getDrawable(layoutInflator.getContext(), ((AndroidBootLoader) bootLoader).getMenuIcon());
        }
        imageIcon.setImageDrawable(icon);
        txtText.setText(bootLoader.getName());
        return view;
    }
}
