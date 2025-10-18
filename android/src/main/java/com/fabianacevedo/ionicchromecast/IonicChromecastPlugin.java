package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import androidx.media.MediaMetadataCompat;

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
        
        if (success) {
            call.resolve(ret);
        } else {
            call.reject("Failed to initialize Cast SDK", ret);
        }
    }

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    /**
     * Request a Cast session from JavaScript
     */
    @PluginMethod
    public void requestSession(PluginCall call) {
        boolean success = implementation.requestSession(getContext());
        JSObject ret = new JSObject();
        ret.put("success", success);
        if (success) {
            ret.put("message", "Cast session requested. Dialog should appear.");
            call.resolve(ret);
        } else {
            ret.put("message", "Failed to request Cast session. Make sure SDK is initialized.");
            call.reject("Failed to request Cast session", ret);
        }
    }

    /**
     * Check if there is an active Cast session from JavaScript
     */
    @PluginMethod
    public void isSessionActive(PluginCall call) {
        boolean active = implementation.isSessionActive();
        JSObject ret = new JSObject();
        ret.put("active", active);
        if (active) {
            ret.put("message", "There is an active Cast session.");
        } else {
            ret.put("message", "No active Cast session.");
        }
        call.resolve(ret);
    }

    /**
     * Check if there are available Cast devices from JavaScript
     */
    @PluginMethod
    public void areDevicesAvailable(PluginCall call) {
        boolean available = implementation.areDevicesAvailable();
        JSObject ret = new JSObject();
        ret.put("available", available);
        if (available) {
            ret.put("message", "There are Cast devices available.");
        } else {
            ret.put("message", "No Cast devices available.");
        }
        call.resolve(ret);
    }

    /**
     * Load media on the Cast device from JavaScript
     */
    @PluginMethod
    public void loadMedia(PluginCall call) {
        String url = call.getString("url");
        JSObject metadataObj = call.getObject("metadata");
        MediaMetadataCompat metadata = null;
        if (metadataObj != null) {
            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
            if (metadataObj.has("title")) builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadataObj.getString("title"));
            if (metadataObj.has("subtitle")) builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadataObj.getString("subtitle"));
            if (metadataObj.has("images")) {
                // Only use the first image for now
                String img = metadataObj.getJSONArray("images").optString(0, null);
                if (img != null) builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, img);
            }
            if (metadataObj.has("studio")) builder.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, metadataObj.getString("studio"));
            if (metadataObj.has("contentType")) builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, metadataObj.getString("contentType"));
            if (metadataObj.has("duration")) builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, metadataObj.getLong("duration"));
            metadata = builder.build();
        }
        boolean success = implementation.loadMedia(url, metadata);
        JSObject ret = new JSObject();
        ret.put("success", success);
        if (success) {
            ret.put("message", "Media sent to Cast device.");
            call.resolve(ret);
        } else {
            ret.put("message", "Failed to send media. Check session and device.");
            call.reject("Failed to send media", ret);
        }
    }
}
