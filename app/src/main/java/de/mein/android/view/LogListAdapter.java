package de.mein.android.view;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.function.Function;

import de.mein.R;
import de.mein.auth.tools.MeinLogger;

/**
 * Created by xor on 9/11/17.
 */

public class LogListAdapter extends MeinListAdapter<Object> implements MeinLogger.LoggerListener {

    private final Activity activity;
    private ToStringFunction toStringFunction;
    private LogListClickListener clickListener;

    public void setToStringFunction(ToStringFunction toStringFunction) {
        this.toStringFunction = toStringFunction;
    }

    public interface ToStringFunction {
        public String apply(Object obj);
    }

    public interface LogListClickListener {
        void onLineClicked(Object line);
    }

    public LogListAdapter(Activity activity, ToStringFunction toStringFunction) {
        super(activity);
        this.activity = activity;
        this.toStringFunction = toStringFunction;
    }

    public void setClickListener(LogListClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        Object line = items.get(i);
        if (view == null)
            view = layoutInflator.inflate(R.layout.listitem_small, null);
        TextView textView = view.findViewById(R.id.text);
        textView.setText(toStringFunction.apply(line));
        if (clickListener != null)
            view.setOnClickListener(view1 -> {
                if (clickListener != null) {
                    clickListener.onLineClicked(line);
                }
            });
        return view;
    }

    @Override
    public void onPrintLn(String line) {
        activity.runOnUiThread(() -> {
            items.add(line);
            notifyDataSetChanged();
        });
    }
}
