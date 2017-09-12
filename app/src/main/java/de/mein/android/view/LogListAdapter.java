package de.mein.android.view;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.mein.R;
import de.mein.auth.tools.MeinLogger;

/**
 * Created by xor on 9/11/17.
 */

public class LogListAdapter extends MeinListAdapter<String> implements MeinLogger.LoggerListener {

    private final Activity activity;
    private LogListClickListener clickListener;

    public interface LogListClickListener {
        void onLineClicked(String line);
    }

    public LogListAdapter(Activity activity) {
        super(activity);
        this.activity = activity;
    }

    public void setClickListener(LogListClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        String line = items.get(i);
        if (view == null)
            view = layoutInflator.inflate(R.layout.listitem_small, null);
        TextView textView = view.findViewById(R.id.text);
        textView.setText(line);
        if (clickListener != null)
            view.setOnClickListener(view1 -> clickListener.onLineClicked(line));
        return view;
    }

    @Override
    public void onPrintLn(String line) {
        items.add(line);
        activity.runOnUiThread(this::notifyDataSetChanged);
    }
}
