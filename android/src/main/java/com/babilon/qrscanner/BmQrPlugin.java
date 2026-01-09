package com.babilon.qrscanner;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "BmQrPlugin")
public class BmQrPlugin extends Plugin {

    private BmQr implementation = new BmQr();

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");
        String fromGallery = call.getString("fromGallery");
        Intent intent = new Intent(getActivity(), bmControllerQR.class);
        intent.putExtra("LNG", value);
        intent.putExtra("fromGallery", fromGallery);

        startActivityForResult(call, intent, "handleQrResult");
    }
    @ActivityCallback
    private void handleQrResult(PluginCall call,  ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                String qrCode = data.getStringExtra("QrResult");
                if (qrCode != null && !qrCode.isEmpty()) {
                    JSObject ret = new JSObject();
                    ret.put("result", qrCode);
                    call.resolve(ret);
                } else {
                    String errorMessage = data.getStringExtra("Error");
                    if (errorMessage != null) {
                        call.reject(errorMessage);
                    } else {
                        String value = call.getString("value", "ru");
                        String message = "tj".equalsIgnoreCase(value) 
                            ? "QR код ёфт нашуд"
                            : "ru".equalsIgnoreCase(value)
                            ? "QR код не найден"
                            : "QR code not found";
                        call.reject(message);
                    }
                }
            } else {
                String value = call.getString("value", "ru");
                String cancelMsg = "tj".equalsIgnoreCase(value) 
                    ? "bmQr Activity бекор карда шуд"
                    : "ru".equalsIgnoreCase(value)
                    ? "bmQr Activity было отменено"
                    : "bmQr Activity was cancelled";
                call.reject(cancelMsg);
            }
        } else {
            Intent data = result.getData();
            String value = call.getString("value", "ru");
            String cancelMsg = "tj".equalsIgnoreCase(value) 
                ? "bmQr Activity бекор карда шуд"
                : "ru".equalsIgnoreCase(value)
                ? "bmQr Activity было отменено"
                : "bmQr Activity was cancelled";
            
            if (data != null) {
                String errorMessage = data.getStringExtra("Error");
                if (errorMessage != null) {
                    call.reject(errorMessage);
                } else {
                    call.reject(cancelMsg);
                }
            } else {
                call.reject(cancelMsg);
            }
        }
    }
}
