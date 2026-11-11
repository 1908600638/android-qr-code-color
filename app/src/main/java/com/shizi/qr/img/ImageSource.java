package com.shizi.qr.img;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * 把相机回传的 JPEG 转成可直接识别的位图：先降采样到最长边 2048，再按元数据摆正。
 */
public final class ImageSource {

    public static final int MAX_EDGE = 2048;

    private ImageSource() {
    }

    /**
     * @param jpeg            相机回调给的 JPEG 字节
     * @param rotationDegrees 相机元数据里的旋转角度
     */
    @NonNull
    public static Bitmap fromJpeg(@NonNull byte[] jpeg, int rotationDegrees) throws IOException {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, probe);
        if (probe.outWidth <= 0 || probe.outHeight <= 0) {
            throw new IOException("not a decodable jpeg");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSizeFor(probe.outWidth, probe.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        if (decoded == null) {
            throw new IOException("BitmapFactory returned null");
        }

        return rotate(shrinkToLimit(decoded), rotationDegrees);
    }

    /** 最大的 2 的幂次降采样比，保证解码后仍不小于 MAX_EDGE。 */
    private static int sampleSizeFor(int width, int height) {
        int longest = Math.max(width, height);
        int sample = 1;
        while (longest / (sample * 2) >= MAX_EDGE) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap shrinkToLimit(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_EDGE) {
            return bitmap;
        }
        float ratio = (float) MAX_EDGE / longest;
        int targetW = Math.max(1, Math.round(width * ratio));
        int targetH = Math.max(1, Math.round(height * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    /** 传感器方向和屏幕方向不一致，不旋正会直接影响识别率。 */
    private static Bitmap rotate(Bitmap bitmap, int degrees) {
        if (degrees % 360 == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }
}
