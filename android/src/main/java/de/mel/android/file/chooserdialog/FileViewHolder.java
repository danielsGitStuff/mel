package de.mel.android.file.chooserdialog;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import de.mel.R;
import de.mel.auth.file.IFile;

public class FileViewHolder extends RecyclerView.ViewHolder {
    private TextView lblDir;
    private IFile directory;
    private ImageView icon;

    public FileViewHolder(View itemView) {
        super(itemView);
        lblDir = itemView.findViewById(R.id.lblDir);
        icon = itemView.findViewById(R.id.icon);
    }

    public void setDir(IFile directory) {
        this.directory = directory;
        if (directory != null) {
            String name = directory.getName();
            if (name == null || name.isEmpty() || "0".equals(name) || "/storage/emulated/0".equals(directory.getAbsolutePath())) {
                name = "Internal Storage";
            }
            this.lblDir.setText(name);
        } else {
            this.lblDir.setText("");
        }
    }
}