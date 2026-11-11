package com.shizi.qr.scan;

import android.graphics.Bitmap;

/**
 * 各工程的识别引擎统一接口，MainActivity 只依赖它。
 */
public interface Scanner {

    interface Callback {
        void onResult(ScanOutcome outcome, Bitmap overlay);

        void onError(Throwable error);
    }

    void scan(Bitmap bitmap, Callback callback);

    void close();
}
