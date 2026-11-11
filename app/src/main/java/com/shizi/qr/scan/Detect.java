package com.shizi.qr.scan;

import android.graphics.Rect;

/**
 * 一条识别结果：类别名、附注、置信度、位置框。
 */
public final class Detect {

    public final String label;
    public final String note;
    public final float score;
    public final Rect box;

    public Detect(String label, String note, float score, Rect box) {
        this.label = label;
        this.note = note;
        this.score = score;
        this.box = box;
    }
}
