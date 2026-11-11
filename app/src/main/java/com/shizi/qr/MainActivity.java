package com.shizi.qr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.shizi.qr.img.ImageSource;
import com.shizi.qr.scan.ScanOutcome;
import com.shizi.qr.scan.Scanner;
import com.shizi.qr.scan.QrEngine;
import com.shizi.qr.util.UiKit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_CAMERA = 7;

    private PreviewView preview;
    private ImageView overlay;
    private TextView hint;
    private TextView result;
    private Button scanButton;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Scanner scanner;
    private ImageCapture imageCapture;
    private boolean capturing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.preview);
        overlay = findViewById(R.id.overlay);
        hint = findViewById(R.id.hint);
        result = findViewById(R.id.result);
        scanButton = findViewById(R.id.btn_scan);

        scanButton.setOnClickListener(this);
        overlay.setOnClickListener(view -> hideOverlay());

        scanner = new QrEngine();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            UiKit.toast(this, R.string.camera_denied);
        }
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview previewUseCase = new Preview.Builder().build();
                previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase, imageCapture);
                hint.setVisibility(View.GONE);
                scanButton.setEnabled(true);
            } catch (ExecutionException | InterruptedException | RuntimeException error) {
                UiKit.toast(MainActivity.this, R.string.camera_failed);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_scan) {
            capture();
        }
    }

    private void capture() {
        if (imageCapture == null || capturing) {
            return;
        }
        capturing = true;
        scanButton.setEnabled(false);
        scanButton.setText(R.string.scanning);

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy proxy) {
                        final int rotation = proxy.getImageInfo().getRotationDegrees();
                        ByteBuffer buffer = proxy.getPlanes()[0].getBuffer();
                        final byte[] jpeg = new byte[buffer.remaining()];
                        buffer.get(jpeg);
                        proxy.close();

                        io.execute(() -> {
                            Bitmap frame = null;
                            try {
                                frame = ImageSource.fromJpeg(jpeg, rotation);
                            } catch (IOException | OutOfMemoryError ignored) {
                            }
                            final Bitmap bitmap = frame;
                            runOnUiThread(() -> {
                                if (bitmap == null) {
                                    resetScanButton();
                                    UiKit.toast(MainActivity.this, R.string.decode_failed);
                                    return;
                                }
                                recognize(bitmap);
                            });
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException error) {
                        resetScanButton();
                        UiKit.toast(MainActivity.this, R.string.scan_failed);
                    }
                });
    }

    private void recognize(Bitmap bitmap) {
        scanner.scan(bitmap, new Scanner.Callback() {

            @Override
            public void onResult(@NonNull ScanOutcome outcome, @NonNull Bitmap painted) {
                resetScanButton();
                overlay.setImageBitmap(painted);
                overlay.setVisibility(View.VISIBLE);
                result.setText(outcome.isEmpty()
                        ? getString(R.string.no_text)
                        : outcome.summary);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                resetScanButton();
                UiKit.toast(MainActivity.this, R.string.scan_failed);
            }
        });
    }

    /** 点一下标注图回到实时预览。 */
    private void hideOverlay() {
        overlay.setImageDrawable(null);
        overlay.setVisibility(View.GONE);
    }

    private void resetScanButton() {
        capturing = false;
        scanButton.setText(R.string.scan);
        scanButton.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
        io.shutdownNow();
    }
}
