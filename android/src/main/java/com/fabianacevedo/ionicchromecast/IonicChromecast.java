package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import com.getcapacitor.Logger;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.CastMediaControlIntent;
import java.util.List;

public class IonicChromecast {
    
    private static final String TAG = "IonicChromecast";
    private CastContext castContext;
    private boolean isInitialized = false;
    
    /**
     * Initialize the Google Cast SDK with the provided receiver application ID
     * @param context The application context
     * @param receiverApplicationId The Cast receiver application ID
     * @return true if initialization was successful
     */
    public boolean initialize(Context context, String receiverApplicationId) {
        try {
            if (isInitialized) {
                Logger.info(TAG, "Cast SDK already initialized");
                return true;
            }
            
            if (receiverApplicationId == null || receiverApplicationId.isEmpty()) {
                Logger.error(TAG, "Receiver Application ID is required", null);
                return false;
            }
            
            Logger.info(TAG, "Initializing Cast SDK with receiver ID: " + receiverApplicationId);
            
            // Initialize CastContext
            castContext = CastContext.getSharedInstance(context);
            
            if (castContext != null) {
                isInitialized = true;
                Logger.info(TAG, "Cast SDK initialized successfully");
                return true;
            } else {
                Logger.error(TAG, "Failed to get CastContext", null);
                return false;
            }
            
        } catch (Exception e) {
            Logger.error(TAG, "Error initializing Cast SDK: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if the Cast SDK is initialized
     * @return true if initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Get the CastContext instance
     * @return CastContext or null if not initialized
     */
    public CastContext getCastContext() {
        return castContext;
    }

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
    
    /**
     * Request a Cast session
     * Shows the Cast dialog and starts a session if a device is selected
     * @param context The application context
     * @return true if session started, false otherwise
     */
    public boolean requestSession(Context context) {
        if (!isInitialized || castContext == null) {
            Logger.error(TAG, "Cast SDK not initialized. Call initialize() first.", null);
            return false;
        }
        try {
            // Show the Cast dialog
            castContext.getSessionManager().startSession(false);
            Logger.info(TAG, "Requested Cast session (dialog should appear)");
            return true;
        } catch (Exception e) {
            Logger.error(TAG, "Error requesting Cast session: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if there is an active Cast session
     * @return true if session is active, false otherwise
     */
    public boolean isSessionActive() {
        if (!isInitialized || castContext == null) {
            Logger.error(TAG, "Cast SDK not initialized. Call initialize() first.", null);
            return false;
        }
        try {
            boolean active = castContext.getSessionManager().getCurrentCastSession() != null;
            Logger.info(TAG, "Session active: " + active);
            return active;
        } catch (Exception e) {
            Logger.error(TAG, "Error checking session status: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if there are available Cast devices
     * @return true if devices are available, false otherwise
     */
    public boolean areDevicesAvailable() {
        if (!isInitialized || castContext == null) {
            Logger.error(TAG, "Cast SDK not initialized. Call initialize() first.", null);
            return false;
        }
        try {
            // Check if there are any Cast devices discovered
            int deviceCount = castContext.getDiscoveryManager().getCastDeviceCount();
            boolean available = deviceCount > 0;
            Logger.info(TAG, "Devices available: " + available + " (" + deviceCount + ")");
            return available;
        } catch (Exception e) {
            Logger.error(TAG, "Error checking device availability: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Load media on the Cast device
     * @param url The media URL
     * @param title Optional title
     * @param subtitle Optional subtitle/artist
     * @param imageUrl Optional image URL
     * @param contentType Optional content type (default: video/mp4)
     * @return true if media loaded successfully
     */
    public boolean loadMedia(String url, String title, String subtitle, String imageUrl, String contentType) {
        if (!isInitialized || castContext == null) {
            Logger.error(TAG, "Cast SDK not initialized. Call initialize() first.", null);
            return false;
        }
        try {
            if (url == null || url.isEmpty()) {
                Logger.error(TAG, "Media URL is required", null);
                return false;
            }
            
            // Build Cast media info
            com.google.android.gms.cast.MediaMetadata castMetadata = new com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE);
            
            // Add metadata if provided
            if (title != null && !title.isEmpty()) {
                castMetadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, title);
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                castMetadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, subtitle);
            }
            if (imageUrl != null && !imageUrl.isEmpty()) {
                castMetadata.addImage(new com.google.android.gms.cast.Image(android.net.Uri.parse(imageUrl)));
            }
            
            // Use provided content type or default to video/mp4
            String finalContentType = (contentType != null && !contentType.isEmpty()) ? contentType : "video/mp4";
            
            com.google.android.gms.cast.MediaInfo mediaInfo = new com.google.android.gms.cast.MediaInfo.Builder(url)
                .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(finalContentType)
                .setMetadata(castMetadata)
                .build();
            
            com.google.android.gms.cast.framework.CastSession session = castContext.getSessionManager().getCurrentCastSession();
            if (session == null || !session.isConnected()) {
                Logger.error(TAG, "No active Cast session", null);
                return false;
            }
            
            session.getRemoteMediaClient().load(mediaInfo, true, 0);
            Logger.info(TAG, "Media loaded to Cast device: " + url);
            return true;
        } catch (Exception e) {
            Logger.error(TAG, "Error loading media: " + e.getMessage(), e);
            return false;
        }
    }
}
