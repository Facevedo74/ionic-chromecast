package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.text.TextUtils;
import com.google.android.gms.cast.CastMediaControlIntent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.mediarouter.app.MediaRouteDialogFactory;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;

// New imports for Cast session/media events
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

@CapacitorPlugin(name = "IonicChromecast")
public class IonicChromecastPlugin extends Plugin {

    private IonicChromecast implementation = new IonicChromecast();

    // Session/media listeners
    private SessionManagerListener<CastSession> sessionListener;
    private RemoteMediaClient.Callback mediaCallback;

    // MediaRouter for route selection observation
    private MediaRouter mediaRouter;
    private MediaRouter.Callback mediaRouterCallback;

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
            // Attach session listener to emit events
            try {
                CastContext cc = implementation.getCastContext();
                if (cc != null) {
                    // Remove previous listener if any
                    if (sessionListener != null) {
                        cc.getSessionManager().removeSessionManagerListener(sessionListener, CastSession.class);
                    }
                    sessionListener = new SessionManagerListener<CastSession>() {
                        @Override
                        public void onSessionStarted(CastSession session, String sessionId) {
                            com.getcapacitor.Logger.info("IonicChromecast", "⭐ onSessionStarted called! sessionId=" + sessionId);
                            JSObject data = new JSObject();
                            data.put("sessionId", sessionId);
                            notifyListeners("sessionStarted", data);

                            attachRemoteMediaClient(session);
                        }
                        @Override
                        public void onSessionResumed(CastSession session, boolean wasSuspended) {
                            com.getcapacitor.Logger.info("IonicChromecast", "⭐ onSessionResumed called!");
                            JSObject data = new JSObject();
                            data.put("resumed", true);
                            notifyListeners("sessionStarted", data);

                            attachRemoteMediaClient(session);
                        }
                        @Override public void onSessionEnded(CastSession session, int error) {
                            com.getcapacitor.Logger.info("IonicChromecast", "⭐ onSessionEnded called! error=" + error);
                            JSObject data = new JSObject();
                            data.put("errorCode", error);
                            notifyListeners("sessionEnded", data);
                            detachRemoteMediaClient(session);
                        }
                        @Override public void onSessionStarting(CastSession session) {
                            com.getcapacitor.Logger.info("IonicChromecast", "⭐ onSessionStarting called!");
                        }
                        @Override public void onSessionEnding(CastSession session) {
                            com.getcapacitor.Logger.info("IonicChromecast", "⭐ onSessionEnding called!");
                        }
                        @Override public void onSessionStartFailed(CastSession session, int error) {
                            com.getcapacitor.Logger.error("IonicChromecast", "⭐ onSessionStartFailed! error=" + error, null);
                        }
                        @Override public void onSessionResumeFailed(CastSession session, int error) {
                            com.getcapacitor.Logger.error("IonicChromecast", "⭐ onSessionResumeFailed! error=" + error, null);
                        }
                        @Override public void onSessionSuspended(CastSession session, int reason) {
                            com.getcapacitor.Logger.info("IonicChromecast", "⭐ onSessionSuspended! reason=" + reason);
                        }
                        @Override public void onSessionResuming(CastSession session, String sessionId) {
                            com.getcapacitor.Logger.info("IonicChromecast", "⭐ onSessionResuming! sessionId=" + sessionId);
                        }
                    };
                    cc.getSessionManager().addSessionManagerListener(sessionListener, CastSession.class);
                    com.getcapacitor.Logger.info("IonicChromecast", "✅ SessionManagerListener registered successfully");
                }
            } catch (Exception ignored) {}

            call.resolve(ret);
        } else {
            call.reject("Failed to initialize Cast SDK", ret);
        }
    }

    private void attachRemoteMediaClient(CastSession session) {
        try {
            RemoteMediaClient rmc = session != null ? session.getRemoteMediaClient() : null;
            if (rmc == null) return;
            if (mediaCallback != null) {
                rmc.unregisterCallback(mediaCallback);
            }
            mediaCallback = new RemoteMediaClient.Callback() {
                @Override
                public void onStatusUpdated() {
                    JSObject data = new JSObject();
                    data.put("status", "updated");
                    notifyListeners("playbackStatusChanged", data);
                }
                @Override
                public void onMetadataUpdated() {
                    JSObject data = new JSObject();
                    data.put("status", "metadataUpdated");
                    notifyListeners("playbackStatusChanged", data);
                }
            };
            rmc.registerCallback(mediaCallback);
        } catch (Exception ignored) {}
    }

    private void detachRemoteMediaClient(CastSession session) {
        try {
            RemoteMediaClient rmc = session != null ? session.getRemoteMediaClient() : null;
            if (rmc != null && mediaCallback != null) {
                rmc.unregisterCallback(mediaCallback);
            }
        } catch (Exception ignored) {}
    }

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    /**
     * Request a Cast session from JavaScript - Uses Cast SDK's built-in device picker
     */
    @PluginMethod
    public void requestSession(PluginCall call) {
        // Ensure Cast SDK initialized
        if (!implementation.isInitialized()) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Cast SDK not initialized. Call initialize() first.");
            call.reject("Failed to request Cast session", err);
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                CastContext cc = implementation.getCastContext();
                if (cc == null) {
                    JSObject err = new JSObject();
                    err.put("success", false);
                    err.put("message", "CastContext is null");
                    call.reject("Failed to request Cast session", err);
                    return;
                }

                com.getcapacitor.Logger.info("IonicChromecast", "🚀 Showing Cast device selector via MediaRouteChooserDialog...");
                
                // Use MediaRouteChooserDialogFragment - the proper way to show Cast device selector
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                FragmentManager fm = activity.getSupportFragmentManager();
                
                String receiverId = CastOptionsProvider.sReceiverApplicationId;
                if (TextUtils.isEmpty(receiverId)) {
                    receiverId = "CC1AD845";
                }
                
                MediaRouteSelector selector = new MediaRouteSelector.Builder()
                        .addControlCategory(CastMediaControlIntent.categoryForCast(receiverId))
                        .build();
                
                MediaRouteChooserDialogFragment dialogFragment = new MediaRouteChooserDialogFragment();
                dialogFragment.setRouteSelector(selector);
                
                // Show the dialog
                dialogFragment.show(fm, "chromecast-device-chooser");
                
                com.getcapacitor.Logger.info("IonicChromecast", "✅ Cast device selector dialog displayed");

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("message", "Cast chooser displayed.");
                call.resolve(ret);
            } catch (Exception e) {
                com.getcapacitor.Logger.error("IonicChromecast", "❌ Error showing Cast chooser: " + e.getMessage(), e);
                JSObject err = new JSObject();
                err.put("success", false);
                err.put("message", "Error showing Cast chooser: " + e.getMessage());
                call.reject("Failed to request Cast session", err);
            }
        });
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
            notifyListeners("mediaLoaded", ret);
            call.resolve(ret);
        } else {
            ret.put("message", "Failed to send media. Check session and device.");
            notifyListeners("mediaError", ret);
            call.reject("Failed to send media", ret);
        }
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        try {
            CastContext cc = implementation.getCastContext();
            if (cc != null && sessionListener != null) {
                cc.getSessionManager().removeSessionManagerListener(sessionListener, CastSession.class);
            }
        } catch (Exception ignored) {}
        try {
            if (mediaRouter != null && mediaRouterCallback != null) {
                mediaRouter.removeCallback(mediaRouterCallback);
            }
        } catch (Exception ignored) {}
    }
}
