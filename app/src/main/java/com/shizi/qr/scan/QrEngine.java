package com.shizi.qr.scan;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 二维码识别：用 ML Kit 定位并解码，再取每个二维码所在区域的彩色像素，按色相判断颜色。
 *
 * <p>彩色二维码的对比度通常低于黑白码，ML Kit 偶尔漏检，所以第一遍没结果时会把图片转成
 * 灰度并拉高对比度再识别一次；但颜色始终从原图采样，增强图只用来找位置。</p>
 */
public final class QrEngine implements Scanner {

    private final BarcodeScanner scanner;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile boolean closed;

    public QrEngine() {
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);
    }

    @Override
    public void scan(final Bitmap bitmap, final Callback callback) {
        if (closed) {
            return;
        }
        scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(codes -> {
                    if (closed) {
                        return;
                    }
                    if (codes.isEmpty()) {
                        retryWithContrast(bitmap, callback);
                    } else {
                        deliver(bitmap, codes, callback);
                    }
                })
                .addOnFailureListener(error -> fail(callback, error));
    }

    private void retryWithContrast(final Bitmap bitmap, final Callback callback) {
        Bitmap boosted = boostContrast(bitmap);
        scanner.process(InputImage.fromBitmap(boosted, 0))
                .addOnSuccessListener(codes -> {
                    if (!closed) {
                        deliver(bitmap, codes, callback);
                    }
                })
                .addOnFailureListener(error -> fail(callback, error));
    }

    private void deliver(final Bitmap source, final List<Barcode> codes, final Callback callback) {
        worker.execute(() -> {
            List<Detect> detects = new ArrayList<>();
            for (Barcode code : codes) {
                String content = code.getRawValue();
                Rect box = code.getBoundingBox();
                if (content == null || box == null) {
                    continue;
                }
                detects.add(new Detect(colorName(dominantColor(source, box)), content, 0f, box));
            }
            final ScanOutcome outcome = new ScanOutcome(detects, describe(detects));
            final Bitmap overlay = BoxPainter.paint(source, detects);
            main.post(() -> {
                if (!closed) {
                    callback.onResult(outcome, overlay);
                }
            });
        });
    }

    private void fail(final Callback callback, Throwable error) {
        main.post(() -> {
            if (!closed) {
                callback.onError(error);
            }
        });
    }

    /** 灰度 + 对比度 ×2，只用于提高检出率，不参与颜色判断。 */
    private static Bitmap boostContrast(Bitmap source) {
        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0f);
        ColorMatrix stretch = new ColorMatrix(new float[]{
                2f, 0f, 0f, 0f, -128f,
                0f, 2f, 0f, 0f, -128f,
                0f, 0f, 2f, 0f, -128f,
                0f, 0f, 0f, 1f, 0f,
        });
        gray.postConcat(stretch);

        Bitmap output = Bitmap.createBitmap(
                source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(gray));
        canvas.drawBitmap(source, 0f, 0f, paint);
        return output;
    }

    /**
     * 二维码里有大量黑白模块，直接求平均会趋近灰色，所以先丢掉低饱和度和过暗的像素，
     * 只对剩下的彩色像素取均值。
     *
     * @return 采样得到的颜色，没有彩色像素时返回 0
     */
    private static int dominantColor(Bitmap source, Rect box) {
        int left = Math.max(0, box.left);
        int top = Math.max(0, box.top);
        int right = Math.min(source.getWidth(), box.right);
        int bottom = Math.min(source.getHeight(), box.bottom);
        if (left >= right || top >= bottom) {
            return 0;
        }

        float[] hsv = new float[3];
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;

        for (int y = top; y < bottom; y += 3) {
            for (int x = left; x < right; x += 3) {
                int pixel = source.getPixel(x, y);
                Color.colorToHSV(pixel, hsv);
                if (hsv[1] < 0.30f || hsv[2] < 0.25f) {
                    continue;
                }
                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        return Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    private static String colorName(int rgb) {
        if (rgb == 0) {
            return "无色";
        }
        float[] hsv = new float[3];
        Color.colorToHSV(rgb, hsv);
        float hue = hsv[0];
        if (hue < 20f || hue >= 330f) {
            return "红色";
        }
        if (hue < 45f) {
            return "橙色";
        }
        if (hue < 70f) {
            return "黄色";
        }
        if (hue < 165f) {
            return "绿色";
        }
        if (hue < 195f) {
            return "青色";
        }
        if (hue < 260f) {
            return "蓝色";
        }
        if (hue < 290f) {
            return "紫色";
        }
        return "品红";
    }

    private static String describe(List<Detect> detects) {
        if (detects.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < detects.size(); i++) {
            Detect detect = detects.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append("二维码 ").append(i + 1).append("：").append(detect.label).append('\n');
            builder.append("内容：").append(detect.note);
        }
        return builder.toString();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        worker.shutdownNow();
        scanner.close();
    }
}
