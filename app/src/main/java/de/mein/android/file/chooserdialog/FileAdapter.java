package de.mein.android.file.chooserdialog;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import de.mein.R;
import de.mein.auth.file.AFile;

public class FileAdapter extends RecyclerView.Adapter<FileViewHolder> {
    private final Activity activity;
    private final View.OnClickListener clickListener;
    private AFile[] directories;
    private final RecyclerView recyclerView;
    private Clicked clicked;

    public void setOnClicked(Clicked clicked) {
        this.clicked = clicked;
    }

    public static interface Clicked {
        void clicked(AFile clickedDir);
    }

    FileAdapter(Activity activity, RecyclerView recyclerView) {
        this.activity = activity;
        this.recyclerView = recyclerView;
        this.clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clicked != null) {
                    int index = recyclerView.getChildLayoutPosition(v);
                    clicked.clicked(directories[index]);
                }
            }
        };
    }

    public void setDirectories(AFile[] directories) {
        this.directories = directories;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = activity.getLayoutInflater().inflate(R.layout.listitem_dir_chooser, parent, false);
        FileViewHolder viewHolder = new FileViewHolder(view);
        view.setOnClickListener(this.clickListener);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.setDir(directories[position]);
    }

    @Override
    public int getItemCount() {
        return directories.length;
    }


}