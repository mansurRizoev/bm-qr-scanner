package com.mycompany.plugins.example;
import android.util.Log;
import android.content.Context;
import android.content.Intent;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanOptions;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.widget.AppCompatImageView;

import androidx.activity.result.contract.ActivityResultContracts;
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
    private static final int MY_REQUEST_CODE_PERMISSION = 1000;
    private static final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE = 100;
    private static final int REQUEST_GALLERY = 200;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityResultLauncher<ScanOptions> barcodeLauncher;
    //    ResultHandler resultHandler;
    int cnt = 0;
    private boolean isFlashOn = false;
    String package_name;
    Context context;

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
        String fromGallery = intent.getStringExtra("fromGallery");
        if ("tj".equalsIgnoreCase(dt)) {
            get_img_btn.setText("Боргирии QR аз галерея");
        } else if ("ru".equalsIgnoreCase(dt)) {
            get_img_btn.setText("QR загрузить с галереи");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, ZXING_CAMERA_PERMISSION);
        }

        initializeActivityResultLaunchers();

        close_btn.setOnClickListener(v -> finish());
        if(fromGallery != null && "yes".equalsIgnoreCase(fromGallery)) {
            get_img_btn.setVisibility(View.VISIBLE);  
            get_img_btn.setOnClickListener(view -> requestPermissions());
        } else {
            get_img_btn.setVisibility(View.GONE);  
        }

        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(intent, savedInstanceState);
        capture.setShowMissingCameraPermissionDialog(false);

        startScanning();
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

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            requestPermissionsLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            requestPermissionsLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            requestPermissionsLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        capture.onResume();
        cnt = 0;
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
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    for (String permission : result.keySet()) {
                        Boolean isGranted = result.get(permission);
                        if (isGranted != null && isGranted) {
                            if (cnt == 0) {
                                openInGallery();
                            }
                        } else {
                            Toast.makeText(this, permission + " denied", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),

                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri selectedImageUri = data.getData();
                            try {
                                final InputStream imageStream = context.getContentResolver().openInputStream(selectedImageUri);

                                final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                                String qrStr = scanQRImage(selectedImage);
                                Intent resultIntent = new Intent();
                                resultIntent.putExtra("QrResult", qrStr);
                                setResult(Activity.RESULT_OK, resultIntent);
                                finish();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        }

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
        cnt++;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        Log.d("RESULT++", String.valueOf(cnt));
        galleryLauncher.launch(intent);
    }

    @Override
    public void onTorchOn() {

    }

    @Override
    public void onTorchOff() {

    }

}
