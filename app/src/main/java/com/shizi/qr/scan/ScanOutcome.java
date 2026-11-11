package com.shizi.qr.scan;

import java.util.Collections;
import java.util.List;

/**
 * 一次识别的全部结果。
 */
public final class ScanOutcome {

    public final List<Detect> detects;
    public final String summary;

    public ScanOutcome(List<Detect> detects, String summary) {
        this.detects = Collections.unmodifiableList(detects);
        this.summary = summary;
    }

    public boolean isEmpty() {
        return detects.isEmpty();
    }
}
