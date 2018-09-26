package de.mein.android.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

import de.mein.R;

public class PowerView extends RelativeLayout {

    private TextView aa, ab, ba, bb, lblH, lblV, lblCaption, vOn, vOff, hOn, hOff;

    public PowerView(Context context) {
        super(context);
        init();
    }

    public PowerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PowerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_power, this);
        aa = findViewById(R.id.aa);
        ab = findViewById(R.id.ab);
        ba = findViewById(R.id.ba);
        bb = findViewById(R.id.bb);
        lblH = findViewById(R.id.lblH);
        lblV = findViewById(R.id.lblV);
        lblCaption = findViewById(R.id.lblCaption);
        vOn = findViewById(R.id.vOn);
        vOff = findViewById(R.id.vOff);
        hOn = findViewById(R.id.hOn);
        hOff = findViewById(R.id.hOff);
    }

}
