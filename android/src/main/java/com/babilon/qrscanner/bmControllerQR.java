package com.babilon.qrscanner;
import android.util.Log;
import android.content.Context;
import android.content.Intent;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import com.journeyapps.barcodescanner.ScanOptions;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.widget.AppCompatImageView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.ViewfinderView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import java.util.Random;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.zxing.common.HybridBinarizer;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;

import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;


public class bmControllerQR extends AppCompatActivity implements DecoratedBarcodeView.TorchListener {
    Button get_img_btn;
    AppCompatImageView close_btn;
    AppCompatImageView flash_btn;
    private CaptureManager capture;
    private DecoratedBarcodeView barcodeScannerView;
    private Button switchFlashlightButton;
    private ViewfinderView viewfinderView;
    private static final int ZXING_CAMERA_PERMISSION = 1;
    private ActivityResultLauncher<PickVisualMediaRequest> pickVisualMediaLauncher;
    private ActivityResultLauncher<ScanOptions> barcodeLauncher;
    //    ResultHandler resultHandler;
    private boolean isFlashOn = false;
    String package_name;
    Context context;
    private String language = "ru";
    private boolean fromGalleryEnabled;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bm_qr);
        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner);
        viewfinderView = barcodeScannerView.getViewFinder();
        barcodeScannerView.setTorchListener(this);

        close_btn = findViewById(R.id.close_btn);
        flash_btn = findViewById(R.id.flash_btn);
        get_img_btn = findViewById(R.id.get_img_btn);

        context = getApplicationContext();
        Intent intent = getIntent();
        package_name = getApplication().getPackageName();

        String dt = intent.getStringExtra("LNG");
        if (dt != null) {
            language = dt;
        }
        String fromGallery = intent.getStringExtra("fromGallery");
        fromGalleryEnabled = fromGallery != null && "yes".equalsIgnoreCase(fromGallery);
        if ("tj".equalsIgnoreCase(language)) {
            get_img_btn.setText("Боргирии QR аз галерея");
        } else if ("ru".equalsIgnoreCase(language)) {
            get_img_btn.setText("QR загрузить с галереи");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, ZXING_CAMERA_PERMISSION);
        } else {
            // Check if camera is actually available
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                if (!fromGalleryEnabled) {
                    String errorMsg = "tj".equalsIgnoreCase(language)
                            ? "Камера дастрас нест"
                            : "ru".equalsIgnoreCase(language)
                            ? "Камера недоступна"
                            : "Camera unavailable";
                    Intent errorIntent = new Intent();
                    errorIntent.putExtra("Error", errorMsg);
                    setResult(Activity.RESULT_CANCELED, errorIntent);
                    finish();
                    return;
                }
            }
        }

        initializeActivityResultLaunchers();

        close_btn.setOnClickListener(v -> {
            Intent cancelIntent = new Intent();
            cancelIntent.putExtra("Error", "-1");
            setResult(Activity.RESULT_CANCELED, cancelIntent);
            finish();
        });
        if (fromGalleryEnabled) {
            get_img_btn.setVisibility(View.VISIBLE);
            get_img_btn.setOnClickListener(view -> openInGallery());
        } else {
            get_img_btn.setVisibility(View.GONE);
        }

        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(intent, savedInstanceState);
        capture.setShowMissingCameraPermissionDialog(false);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        }
    }

    private void startScanning() {
        barcodeScannerView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                String qrResult = result.getText();
                Log.d("Scanned: ", qrResult);
                Intent resultIntent = new Intent();
                resultIntent.putExtra("QrResult", qrResult);
                setResult(RESULT_OK, resultIntent);
                finish(); // Close the scanner activity
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {}
              
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ZXING_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else if (fromGalleryEnabled) {
                // Остаёмся на экране: можно отсканировать QR только из галереи без камеры.
            } else {
                String errorMsg = "tj".equalsIgnoreCase(language)
                        ? "Иҷозаи камера дода нашуд"
                        : "ru".equalsIgnoreCase(language)
                        ? "Разрешение на использование камеры не предоставлено"
                        : "Camera permission denied";
                Intent errorIntent = new Intent();
                errorIntent.putExtra("Error", errorMsg);
                setResult(Activity.RESULT_CANCELED, errorIntent);
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        capture.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        Intent cancelIntent = new Intent();
        cancelIntent.putExtra("Error", "-1");
        setResult(Activity.RESULT_CANCELED, cancelIntent);
        super.onBackPressed();
    }

    private boolean hasFlash() {
        return getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public void switchFlashlight(View view) {
        if (!isFlashOn) {
            barcodeScannerView.setTorchOn();
            flash_btn.setImageResource(getApplication().getResources().getIdentifier("qrflash_on", "drawable", package_name));
            isFlashOn = true;
        } else {
            barcodeScannerView.setTorchOff();
            flash_btn.setImageResource(getApplication().getResources().getIdentifier("qrflash_off", "drawable", package_name));
            isFlashOn = false;
        }
    }

    public void changeMaskColor(View view) {
        Random rnd = new Random();
        int color = Color.argb(100, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
        viewfinderView.setMaskColor(color);
    }

    public void changeLaserVisibility(boolean visible) {
        viewfinderView.setLaserVisibility(visible);
    }

    private void initializeActivityResultLaunchers() {
        pickVisualMediaLauncher = registerForActivityResult(
                new PickVisualMedia(),
                selectedImageUri -> {
                    if (selectedImageUri == null) {
                        String errorMsg = "tj".equalsIgnoreCase(language)
                                ? "Тасвир интихоб нашуд"
                                : "ru".equalsIgnoreCase(language)
                                ? "Изображение не выбрано"
                                : "Image not selected";
                        Intent errorIntent = new Intent();
                        errorIntent.putExtra("Error", errorMsg);
                        setResult(Activity.RESULT_CANCELED, errorIntent);
                        finish();
                        return;
                    }
                    try {
                        final InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
                        if (imageStream == null) {
                            String errorMsg = "tj".equalsIgnoreCase(language)
                                    ? "Хатоги дар боз кардани тасвир"
                                    : "ru".equalsIgnoreCase(language)
                                    ? "Ошибка при открытии изображения"
                                    : "Error opening image";
                            Intent errorIntent = new Intent();
                            errorIntent.putExtra("Error", errorMsg);
                            setResult(Activity.RESULT_CANCELED, errorIntent);
                            finish();
                            return;
                        }

                        final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                        if (selectedImage == null) {
                            String errorMsg = "tj".equalsIgnoreCase(language)
                                    ? "Хатоги дар таҳияи тасвир"
                                    : "ru".equalsIgnoreCase(language)
                                    ? "Ошибка при обработке изображения"
                                    : "Error processing image";
                            Intent errorIntent = new Intent();
                            errorIntent.putExtra("Error", errorMsg);
                            setResult(Activity.RESULT_CANCELED, errorIntent);
                            finish();
                            return;
                        }

                        String qrStr = scanQRImage(selectedImage);
                        if (qrStr == null || qrStr.isEmpty()) {
                            String errorMsg = "tj".equalsIgnoreCase(language)
                                    ? "QR код дар тасвир ёфт нашуд"
                                    : "ru".equalsIgnoreCase(language)
                                    ? "QR код не найден в изображении"
                                    : "QR code not found in image";
                            Intent errorIntent = new Intent();
                            errorIntent.putExtra("Error", errorMsg);
                            setResult(Activity.RESULT_CANCELED, errorIntent);
                            finish();
                            return;
                        }

                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("QrResult", qrStr);
                        setResult(Activity.RESULT_OK, resultIntent);
                        finish();
                    } catch (SecurityException e) {
                        e.printStackTrace();
                        String errorMsg = "tj".equalsIgnoreCase(language)
                                ? "Дастрасӣ ба файл дода нашуд"
                                : "ru".equalsIgnoreCase(language)
                                ? "Нет доступа к выбранному изображению"
                                : "No access to the selected image";
                        Intent errorIntent = new Intent();
                        errorIntent.putExtra("Error", errorMsg);
                        setResult(Activity.RESULT_CANCELED, errorIntent);
                        finish();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        String errorMsg = "tj".equalsIgnoreCase(language)
                                ? "Файл ёфт нашуд"
                                : "ru".equalsIgnoreCase(language)
                                ? "Файл не найден"
                                : "File not found";
                        Intent errorIntent = new Intent();
                        errorIntent.putExtra("Error", errorMsg);
                        setResult(Activity.RESULT_CANCELED, errorIntent);
                        finish();
                    } catch (Exception e) {
                        e.printStackTrace();
                        String errorMsg = "tj".equalsIgnoreCase(language)
                                ? "Хатоги дар хондани тасвир: " + e.getMessage()
                                : "ru".equalsIgnoreCase(language)
                                ? "Ошибка при чтении изображения: " + e.getMessage()
                                : "Error reading image: " + e.getMessage();
                        Intent errorIntent = new Intent();
                        errorIntent.putExtra("Error", errorMsg);
                        setResult(Activity.RESULT_CANCELED, errorIntent);
                        finish();
                    }
                }
        );
    }

    public static String scanQRImage (Bitmap bMap){
        String contents = null;

        int[] intArray = new int[bMap.getWidth() * bMap.getHeight()];
        bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());
        LuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Reader reader = new MultiFormatReader();
        try {
            Result result = reader.decode(bitmap);
            contents = result.getText();
        } catch (Exception e) {
            Log.e("QR_READER", "error img", e);
        }
        return contents;
    }
    public void openInGallery() {
        pickVisualMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
    }

    @Override
    public void onTorchOn() {

    }

    @Override
    public void onTorchOff() {

    }

}
