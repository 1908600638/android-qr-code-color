package com.shizi.qr.util;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

public final class UiKit {

    private UiKit() {
    }

    public static void toast(Context context, @StringRes int message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
