package de.mel.android.view;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import de.mel.LokImpl;
import de.mel.R;

/**
 * Created by xor on 9/11/17.
 */

public class LogListAdapter extends MelListAdapter<Object> implements LokImpl.LokListener{

    private final AppCompatActivity activity;
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

    public LogListAdapter(AppCompatActivity activity, ToStringFunction toStringFunction) {
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
