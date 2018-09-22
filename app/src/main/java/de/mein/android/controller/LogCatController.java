package de.mein.android.controller;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidPowerManager;
import de.mein.android.service.AndroidService;
import de.mein.android.view.LogListAdapter;
import de.mein.auth.tools.N;

/**
 * Created by xor on 9/8/17.
 */

public class LogCatController extends GuiController {

    private TextView txtLogCat;
    private LogListAdapter listAdapter;
    private Button btnToggle;
    private LogListAdapter.ToStringFunction logcatToString = Object::toString;

    private void initLog() {
        listAdapter.setClickListener(line -> txtLogCat.setText(logcatToString.apply(line)));
        Lok.setLokListener(listAdapter);
        btnToggle.setText("showing logcat");
        listAdapter.setToStringFunction(logcatToString);
        listAdapter.notifyDataSetChanged();
    }

    private void initWakeLock() {
        Lok.setLokListener(null);
        AndroidPowerManager powerManager = (AndroidPowerManager) this.androidService.getMeinAuthService().getPowerManager();
        Object[] callers = powerManager.devGetHeldWakeLockCallers();
        listAdapter.clear();
        listAdapter.addAll(callers);
        listAdapter.notifyDataSetChanged();
        listAdapter.setClickListener(caller -> {
            StackTraceElement[] stack = powerManager.devGetCallerStackTrace(caller);
            StringBuilder b = new StringBuilder();
            if (!N.forEach(stack, element -> b.append(element.getClassName() + "//" + element.getLineNumber() + "//" + element.getMethodName() + "\n"))) {
                b.append("stacktrace was null");
            }
            txtLogCat.setText(b.toString());
        });
        listAdapter.setToStringFunction(caller -> caller.getClass().getName());
        btnToggle.setText("showing wakelocks");
    }

    private boolean logEnabled = true;

    public LogCatController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_logcat);
        this.txtLogCat = rootView.findViewById(R.id.txtLogCat);
        this.btnToggle = rootView.findViewById(R.id.btnToggleWakeLock);
        listAdapter = new LogListAdapter(activity, logcatToString);

        ListView listViewLines = rootView.findViewById(R.id.listViewLines);
        listViewLines.setAdapter(listAdapter);
        txtLogCat.setOnLongClickListener(v -> {
            activity.runOnUiThread(() -> {
                listAdapter.clear();
                listAdapter.notifyDataSetChanged();
            });
            return true;
        });
        btnToggle.setOnClickListener(v -> {
            if (logEnabled)
                initWakeLock();
            else {
                initLog();
                showLog();
            }
            logEnabled = !logEnabled;
        });
        initLog();
        showLog();
    }

    @Override
    public Integer getTitle() {
        return R.string.logcatTitle;
    }

    @Override
    public void onAndroidServiceAvailable() {
        Lok.debug("LogCatController.onAndroidServiceAvailable");
    }

    private void showLog() {
        String[] lines = Lok.getLines();
        listAdapter.clear().addAll(lines);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }

    @Override
    public Integer getHelp() {
        return R.string.logcatHelp;
    }
}
