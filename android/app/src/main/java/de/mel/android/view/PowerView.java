package de.mel.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.gridlayout.widget.GridLayout;
import de.mel.Lok;
import de.mel.R;
import de.mel.android.service.AndroidPowerManager;
import de.mel.auth.service.power.PowerManager;

public class PowerView extends GridLayout {

    private Button btnTT, btnTF, btnFT, btnFF;
    private LinearLayout lTT, lTF, lFT, lFF;
    private AndroidPowerManager powerManager;
    private int colorRun = getResources().getColor(R.color.stateRunning);
    private int colorStop = getResources().getColor(R.color.stateDeactivated);
    private int textRun = R.string.powerRun;
    private int textStop = R.string.powerStop;
    private boolean activeButtons = false;

    public PowerView(Context context) {
        super(context);
        init(null);
    }

    public PowerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PowerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        inflate(getContext(), R.layout.view_power_grid, this);
        TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.PowerView,
                0, 0);
        try {
            activeButtons = typedArray.getBoolean(R.styleable.PowerView_activeButtons, false);
        } finally {
            typedArray.recycle();
        }

        btnTT = findViewById(R.id.btnTT);
        btnTF = findViewById(R.id.btnTF);
        btnFT = findViewById(R.id.btnFT);
        btnFF = findViewById(R.id.btnFF);
        lTT = findViewById(R.id.lTT);
        lTF = findViewById(R.id.lTF);
        lFT = findViewById(R.id.lFT);
        lFF = findViewById(R.id.lFF);

        if (activeButtons) {
            btnTT.setOnClickListener(this::onBtnClicked);
            btnTF.setOnClickListener(this::onBtnClicked);
            btnFT.setOnClickListener(this::onBtnClicked);
            btnFF.setOnClickListener(this::onBtnClicked);
        }

    }

    private void onBtnClicked(View btn) {
        if (btn == btnTT)
            powerManager.togglePowerWifi();
        else if (btn == btnTF)
            powerManager.togglePowerNoWifi();
        else if (btn == btnFT)
            powerManager.toggleNoPowerWifi();
        else
            powerManager.toggleNoPowerNoWifi();
        update();
    }

    public PowerView setPowerManager(AndroidPowerManager powerManager) {
        this.powerManager = powerManager;
        return this;
    }

    public void update() {
        if (powerManager != null) {
            updateButton(btnTT, lTT, true, true);
            updateButton(btnTF, lTF, true, false);
            updateButton(btnFT, lFT, false, true);
            updateButton(btnFF, lFF, false, false);
            int highlightColor = getContext().getResources().getColor(R.color.colorPowerHighlight);
            if (powerManager.isPowered()) {
                if (powerManager.isWifi()) {
                    lTT.setBackgroundColor(highlightColor);
                } else {
                    lTF.setBackgroundColor(highlightColor);
                }
            } else {
                if (powerManager.isWifi()) {
                    lFT.setBackgroundColor(highlightColor);
                } else {
                    lFF.setBackgroundColor(highlightColor);
                }
            }
        }
    }

    private void updateButton(Button btn, LinearLayout background, boolean onPower, boolean onWifi) {
        boolean running = powerManager.runWhen(onPower, onWifi);
        btn.setText(running ? textRun : textStop);
        btn.setBackgroundColor(running ? colorRun : colorStop);
        background.setBackgroundColor(running ? colorRun : colorStop);
    }

    public void disable() {
        Lok.debug("NOT:IMPLEMENTED:YET");
    }
}
