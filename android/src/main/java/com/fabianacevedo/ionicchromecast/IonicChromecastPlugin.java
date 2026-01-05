package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.text.TextUtils;
import com.google.android.gms.cast.CastMediaControlIntent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import android.content.DialogInterface;

@CapacitorPlugin(name = "IonicChromecast")
public class IonicChromecastPlugin extends Plugin {

    private IonicChromecast implementation = new IonicChromecast();

    /**
     * Initialize the Google Cast SDK
     * This method must be called before using any other Cast functionality
     */
    @PluginMethod
    public void initialize(PluginCall call) {
        String receiverApplicationId = call.getString("receiverApplicationId");
        
        if (receiverApplicationId == null || receiverApplicationId.isEmpty()) {
            call.reject("Receiver Application ID is required");
            return;
        }
        
        boolean success = implementation.initialize(getContext(), receiverApplicationId);
        
        JSObject ret = new JSObject();
        ret.put("success", success);
        String initError = implementation.getLastError();
        if (initError != null && !initError.isEmpty()) {
            ret.put("error", initError);
        }
        
        if (success) {
            call.resolve(ret);
        } else {
            call.reject("Failed to initialize Cast SDK", ret);
        }
    }

    /**
     * Muestra el selector nativo de dispositivos Cast
     */
    @PluginMethod
    public void requestSession(PluginCall call) {
        if (!implementation.isInitialized() || implementation.getCastContext() == null) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Cast SDK not initialized");
            call.reject("Failed to request Cast session", err);
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                String receiverId = CastOptionsProvider.sReceiverApplicationId;
                if (TextUtils.isEmpty(receiverId)) receiverId = "CC1AD845";

                MediaRouteSelector selector = new MediaRouteSelector.Builder()
                        .addControlCategory(CastMediaControlIntent.categoryForCast(receiverId))
                        .build();

                MediaRouteChooserDialog chooserDialog = new MediaRouteChooserDialog(activity, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar);
                chooserDialog.setRouteSelector(selector);
                chooserDialog.setCanceledOnTouchOutside(true);
                chooserDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) {}
                });
                chooserDialog.show();

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("message", "Cast chooser displayed");
                call.resolve(ret);
            } catch (Exception e) {
                JSObject err = new JSObject();
                err.put("success", false);
                err.put("message", "Error showing chooser: " + e.getMessage());
                call.reject("Failed to request Cast session", err);
            }
        });
    }

    /**
     * Estado de sesión activa
     */
    @PluginMethod
    public void isSessionActive(PluginCall call) {
        boolean active = implementation.isSessionActive();
        JSObject ret = new JSObject();
        ret.put("active", active);
        call.resolve(ret);
    }

    /**
     * Carga media en el dispositivo
     */
    @PluginMethod
    public void loadMedia(PluginCall call) {
        String url = call.getString("url");
        JSObject metadataObj = call.getObject("metadata");

        String title = metadataObj != null ? metadataObj.getString("title") : null;
        String subtitle = metadataObj != null ? metadataObj.getString("subtitle") : null;
        String contentType = metadataObj != null ? metadataObj.getString("contentType") : null;
        String imageUrl = null;
        if (metadataObj != null && metadataObj.has("images")) {
            try { imageUrl = metadataObj.getJSONArray("images").optString(0, null); } catch (Exception ignore) {}
        }

        boolean ok = implementation.loadMedia(url, title, subtitle, imageUrl, contentType);
        JSObject ret = new JSObject();
        ret.put("success", ok);
        String err = implementation.getLastError();
        if (err != null && !err.isEmpty()) ret.put("error", err);
        if (ok) {
            call.resolve(ret);
        } else {
            call.reject("Failed to load media", ret);
        }
    }

    /**
     * Revisa si hay dispositivos disponibles
     */
    @PluginMethod
    public void areDevicesAvailable(PluginCall call) {
        boolean available = implementation.areDevicesAvailable();
        JSObject ret = new JSObject();
        ret.put("available", available);
        call.resolve(ret);
    }
}
