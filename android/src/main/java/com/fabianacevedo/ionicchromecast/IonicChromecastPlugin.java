package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

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
        
        // Extract metadata fields
        String title = null;
        String subtitle = null;
        String imageUrl = null;
        String contentType = null;
        
        if (metadataObj != null) {
            title = metadataObj.getString("title");
            subtitle = metadataObj.getString("subtitle");
            contentType = metadataObj.getString("contentType");
            
            // Get the first image if available
            if (metadataObj.has("images")) {
                try {
                    imageUrl = metadataObj.getJSONArray("images").optString(0, null);
                } catch (Exception e) {
                    // Ignore if images array is malformed
                }
            }
        }
        
        boolean success = implementation.loadMedia(url, title, subtitle, imageUrl, contentType);
        
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
