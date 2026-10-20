package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.text.TextUtils;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import android.content.DialogInterface;
import java.util.List;

@CapacitorPlugin(name = "IonicChromecast")
public class IonicChromecastPlugin extends Plugin {

    private IonicChromecast implementation = new IonicChromecast();
    private SessionManagerListener<CastSession> sessionListener;

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
            setupSessionListener();
            call.resolve(ret);
        } else {
            call.reject("Failed to initialize Cast SDK", ret);
        }
    }

    private void setupSessionListener() {
        try {
            SessionManager sm = implementation.getCastContext() != null ? implementation.getCastContext().getSessionManager() : null;
            if (sm == null) return;

            if (sessionListener == null) {
                sessionListener = new SessionManagerListener<CastSession>() {
                    @Override public void onSessionStarting(CastSession session) {
                        JSObject data = new JSObject();
                        data.put("state", "starting");
                        notifyListeners("sessionStarted", data);
                    }

                    @Override public void onSessionStarted(CastSession session, String sessionId) {
                        implementation.clearDlnaSession();
                        JSObject data = new JSObject();
                        data.put("state", "started");
                        data.put("sessionId", sessionId);
                        data.put("transport", "google-cast");
                        notifyListeners("sessionStarted", data);
                    }

                    @Override public void onSessionStartFailed(CastSession session, int i) {
                        JSObject data = new JSObject();
                        data.put("state", "startFailed");
                        data.put("code", i);
                        notifyListeners("sessionEnded", data);
                    }

                    @Override public void onSessionEnding(CastSession session) {
                        JSObject data = new JSObject();
                        data.put("state", "ending");
                        notifyListeners("sessionEnded", data);
                    }

                    @Override public void onSessionEnded(CastSession session, int i) {
                        JSObject data = new JSObject();
                        data.put("state", "ended");
                        data.put("code", i);
                        notifyListeners("sessionEnded", data);
                    }

                    @Override public void onSessionResuming(CastSession session, String s) {}
                    @Override public void onSessionResumed(CastSession session, boolean b) {}
                    @Override public void onSessionResumeFailed(CastSession session, int i) {}
                    @Override public void onSessionSuspended(CastSession session, int i) {}
                };
            }

            sm.removeSessionManagerListener(sessionListener, CastSession.class);
            sm.addSessionManagerListener(sessionListener, CastSession.class);
        } catch (Exception ignored) {}
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        try {
            SessionManager sm = implementation.getCastContext() != null ? implementation.getCastContext().getSessionManager() : null;
            if (sm != null && sessionListener != null) {
                sm.removeSessionManagerListener(sessionListener, CastSession.class);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Discovers DLNA/UPnP renderers while preserving the original Google Cast chooser.
     * When no DLNA renderer is found, Google Cast opens directly as before.
     */
    @PluginMethod
    public void requestSession(PluginCall call) {
        if (!isReady(call)) return;

        new Thread(() -> {
            List<DlnaManager.Device> dlnaDevices = implementation.discoverDlnaDevices(5000);
            if (getActivity() == null || getActivity().isFinishing()) {
                JSObject err = new JSObject();
                err.put("success", false);
                err.put("message", "Activity is not available");
                call.reject("Failed to request playback session", err);
                return;
            }

            String discoveryReport = implementation.getLastDiscoveryReport();
            getActivity().runOnUiThread(() -> showPlaybackChooser(call, dlnaDevices, discoveryReport));
        }, "IonicChromecast-DLNA-Discovery").start();
    }

    /**
     * Opens Google Cast directly, without scanning for DLNA devices.
     */
    @PluginMethod
    public void requestCastSession(PluginCall call) {
        if (!isReady(call)) return;
        getActivity().runOnUiThread(() -> showGoogleCastChooser(call));
    }

    private boolean isReady(PluginCall call) {
        if (!implementation.isInitialized() || implementation.getCastContext() == null) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Cast SDK not initialized");
            call.reject("Failed to request playback session", err);
            return false;
        }
        if (getActivity() == null || getActivity().isFinishing()) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Activity is not available");
            call.reject("Failed to request playback session", err);
            return false;
        }
        return true;
    }

    private void showPlaybackChooser(PluginCall call, List<DlnaManager.Device> dlnaDevices, String discoveryReport) {
        if (dlnaDevices.isEmpty()) {
            showGoogleCastChooser(call, discoveryReport);
            return;
        }

        try {
            String[] choices = new String[dlnaDevices.size() + 1];
            choices[0] = "Chromecast / Android TV (Google Cast)";
            for (int i = 0; i < dlnaDevices.size(); i++) {
                choices[i + 1] = dlnaDevices.get(i).getName() + " (DLNA / UPnP)";
            }

            AlertDialog dialog = new AlertDialog.Builder((AppCompatActivity) getActivity())
                .setTitle("Seleccionar TV")
                .setItems(choices, (selectedDialog, which) -> {
                    if (which == 0) {
                        showGoogleCastChooser(call);
                        return;
                    }

                    DlnaManager.Device selected = dlnaDevices.get(which - 1);
                    implementation.selectDlnaDevice(selected);

                    JSObject event = new JSObject();
                    event.put("state", "started");
                    event.put("transport", "dlna");
                    event.put("deviceName", selected.getName());
                    notifyListeners("sessionStarted", event);

                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("message", "DLNA renderer selected");
                    ret.put("transport", "dlna");
                    ret.put("deviceName", selected.getName());
                    call.resolve(ret);
                })
                .setOnCancelListener(cancelledDialog -> {
                    JSObject ret = new JSObject();
                    ret.put("success", false);
                    ret.put("cancelled", true);
                    ret.put("message", "Device selection was cancelled");
                    call.resolve(ret);
                })
                .create();
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
            notifyListeners("deviceChooserShown", new JSObject());
        } catch (Exception e) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Error showing device chooser: " + e.getMessage());
            call.reject("Failed to request playback session", err);
        }
    }

    private void showGoogleCastChooser(PluginCall call) {
        showGoogleCastChooser(call, null);
    }

    private void showGoogleCastChooser(PluginCall call, String discoveryReport) {
        try {
            implementation.clearDlnaSession();

            try {
                SessionManager sm = implementation.getCastContext().getSessionManager();
                CastSession current = sm != null ? sm.getCurrentCastSession() : null;
                String wantedApp = CastOptionsProvider.sReceiverApplicationId;
                String currentApp = (current != null && current.getApplicationMetadata() != null)
                    ? current.getApplicationMetadata().getApplicationId()
                    : "";
                if (current != null && current.isConnected() && wantedApp != null && !wantedApp.isEmpty() && !wantedApp.equals(currentApp)) {
                    sm.endCurrentSession(true);
                }
            } catch (Exception ignored) {}

            AppCompatActivity activity = (AppCompatActivity) getActivity();
            String receiverId = CastOptionsProvider.sReceiverApplicationId;
            if (TextUtils.isEmpty(receiverId)) receiverId = "CC1AD845";

            MediaRouteSelector selector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(receiverId))
                .build();

            MediaRouteChooserDialog chooserDialog = new MediaRouteChooserDialog(
                activity,
                androidx.appcompat.R.style.Theme_AppCompat_NoActionBar
            );
            chooserDialog.setRouteSelector(selector);
            chooserDialog.setCanceledOnTouchOutside(true);
            chooserDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override public void onCancel(DialogInterface dialog) {}
            });
            chooserDialog.show();
            notifyListeners("deviceChooserShown", new JSObject());

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("message", "Cast chooser displayed");
            ret.put("transport", "google-cast");
            if (discoveryReport != null) ret.put("dlnaDiscovery", discoveryReport);
            call.resolve(ret);
        } catch (Exception e) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Error showing chooser: " + e.getMessage());
            call.reject("Failed to request Cast session", err);
        }
    }

    /**
     * Estado de sesión activa
     */
    @PluginMethod
    public void isSessionActive(PluginCall call) {
        boolean active = implementation.isSessionActive();
        JSObject ret = new JSObject();
        ret.put("active", active);
        String transport = implementation.getActiveTransport();
        if (transport != null) ret.put("transport", transport);
        String deviceName = implementation.getSelectedDeviceName();
        if (deviceName != null) ret.put("deviceName", deviceName);
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

        final String finalImageUrl = imageUrl;
        if (implementation.isDlnaSessionActive()) {
            new Thread(
                () -> resolveLoadMedia(call, url, title, subtitle, finalImageUrl, contentType),
                "IonicChromecast-DLNA-Load"
            ).start();
            return;
        }

        resolveLoadMedia(call, url, title, subtitle, imageUrl, contentType);
    }

    private void resolveLoadMedia(
        PluginCall call,
        String url,
        String title,
        String subtitle,
        String imageUrl,
        String contentType
    ) {
        boolean ok = implementation.loadMedia(url, title, subtitle, imageUrl, contentType);
        JSObject ret = new JSObject();
        ret.put("success", ok);
        String transport = implementation.getActiveTransport();
        if (transport != null) ret.put("transport", transport);
        String err = implementation.getLastError();
        if (err != null && !err.isEmpty()) ret.put("error", err);
        if (ok) {
            call.resolve(ret);
        } else {
            String message = "dlna".equals(transport) && err != null && !err.isEmpty()
                ? "Failed to load media: " + err
                : "Failed to load media";
            call.reject(message, ret);
        }
    }

    /**
     * Finaliza la sesión Cast activa
     */
    @PluginMethod
    public void endSession(PluginCall call) {
        if (implementation.isDlnaSessionActive()) {
            new Thread(() -> resolveEndSession(call), "IonicChromecast-DLNA-End").start();
            return;
        }
        resolveEndSession(call);
    }

    private void resolveEndSession(PluginCall call) {
        boolean ended = implementation.endSession();
        JSObject ret = new JSObject();
        ret.put("success", ended);
        String err = implementation.getLastError();
        if (err != null && !err.isEmpty()) ret.put("error", err);

        if (ended) {
            call.resolve(ret);
        } else {
            call.reject("Failed to end session", ret);
        }
    }

    /**
     * Revisa si hay dispositivos disponibles
     */
    @PluginMethod
    public void areDevicesAvailable(PluginCall call) {
        new Thread(() -> {
            boolean available = implementation.areDevicesAvailable();
            JSObject ret = new JSObject();
            ret.put("available", available);
            ret.put("message", implementation.getLastDiscoveryReport());
            call.resolve(ret);
        }, "IonicChromecast-Device-Discovery").start();
    }
}
