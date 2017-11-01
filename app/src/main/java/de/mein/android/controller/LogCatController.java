package de.mein.android.controller;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.service.AndroidService;
import de.mein.android.view.LogListAdapter;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.MeinLogger;

/**
 * Created by xor on 9/8/17.
 */

public class LogCatController extends GuiController {

    private final TextView txtLogCat;
    private LogListAdapter listAdapter;

    public LogCatController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_logcat);
        this.txtLogCat = rootView.findViewById(R.id.txtLogCat);
        listAdapter = new LogListAdapter(activity);
        listAdapter.setClickListener(txtLogCat::setText);
        MeinLogger.setLoggerListener(listAdapter);
        ListView listViewLines = rootView.findViewById(R.id.listViewLines);
        listViewLines.setAdapter(listAdapter);
        showLog();
    }

    @Override
    public String getTitle() {
        return "LogCat";
    }

    @Override
    public void onAndroidServiceAvailable() {
        System.out.println("LogCatController.onAndroidServiceAvailable");
    }

    private void showLog() {
        MeinLogger logger = MeinLogger.getInstance();
        String[] lines = logger.getLines();
        listAdapter.clear().addAll(lines);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {

    }

    @Override
    public Integer getHelp() {
        return R.string.helpLogcat;
    }
}
