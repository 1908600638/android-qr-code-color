package com.shizi.qr.scan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.List;

/**
 * 把识别框和类别名画到图片上。
 */
public final class BoxPainter {

    private static final int[] TINTS = {
            0xFF3B5BFE, 0xFF00A99D, 0xFFE08000, 0xFFD6336C, 0xFF7C4DFF, 0xFF2F9E44,
    };
    private static final int FILL_ALPHA = 0x22;
    private static final float BASE_WIDTH = 1080f;

    private BoxPainter() {
    }

    public static Bitmap paint(Bitmap source, List<Detect> detects) {
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        if (output == null) {
            return source;
        }
        Canvas canvas = new Canvas(output);
        float unit = scaleFor(source.getWidth());

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.4f * unit);

        Paint tag = new Paint(Paint.ANTI_ALIAS_FLAG);
        tag.setStyle(Paint.Style.FILL);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(15f * unit);
        text.setTextAlign(Paint.Align.LEFT);
        text.setFakeBoldText(true);

        float corner = 7f * unit;

        for (int i = 0; i < detects.size(); i++) {
            Detect detect = detects.get(i);
            if (detect.box == null || detect.box.width() <= 0) {
                continue;
            }
            int tint = TINTS[i % TINTS.length];
            RectF box = new RectF(detect.box);
            box.inset(-3f * unit, -3f * unit);

            fill.setColor(withAlpha(tint, FILL_ALPHA));
            canvas.drawRoundRect(box, corner, corner, fill);

            stroke.setColor(tint);
            canvas.drawRoundRect(box, corner, corner, stroke);

            drawTag(canvas, box, detect.label, tint, unit, tag, text);
        }
        return output;
    }

    private static void drawTag(Canvas canvas, RectF box, String label, int tint,
                                float unit, Paint tag, Paint text) {
        float padding = 6f * unit;
        float height = 19f * unit;
        float width = Math.min(text.measureText(label) + padding * 2f, box.width());
        float left = box.left;
        float top = box.top - height - 2f * unit;
        if (top < 0f) {
            top = box.top + 2f * unit;
        }
        RectF rect = new RectF(left, top, left + width, top + height);

        tag.setColor(tint);
        canvas.drawRoundRect(rect, height / 2f, height / 2f, tag);

        float baseline = rect.centerY() - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(label, rect.left + padding, baseline, text);
    }

    private static float scaleFor(int bitmapWidth) {
        float unit = bitmapWidth / BASE_WIDTH;
        if (unit < 0.55f) {
            return 0.55f;
        }
        if (unit > 2.6f) {
            return 2.6f;
        }
        return unit;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
