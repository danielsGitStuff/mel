package de.mel.android.file.chooserdialog;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import de.mel.R;
import de.mel.auth.file.AbstractFile;

public class FileViewHolder extends RecyclerView.ViewHolder {
    private TextView lblDir;
    private AbstractFile directory;
    private ImageView icon;

    public FileViewHolder(View itemView) {
        super(itemView);
        lblDir = itemView.findViewById(R.id.lblDir);
        icon = itemView.findViewById(R.id.icon);

    }

    public void setDir(AbstractFile directory) {
        this.directory = directory;
        this.lblDir.setText(directory.getName());
    }
}
