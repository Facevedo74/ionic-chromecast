package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.text.TextUtils;
import android.app.Activity;
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
import java.util.Collections;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

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
                        implementation.clearFireTvSession();
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
        implementation.closeFireTvDiscovery();
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
            try {
                FutureTask<List<FireTvManager.Device>> fireTvSearch = new FutureTask<>(
                    () -> implementation.discoverFireTvDevices(5000)
                );
                new Thread(fireTvSearch, "IonicChromecast-FireTV-Discovery").start();
                List<DlnaManager.Device> dlnaDevices = implementation.discoverDlnaDevices(5000);
                List<FireTvManager.Device> fireTvDevices;
                try {
                    fireTvDevices = fireTvSearch.get(4, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fireTvDevices = Collections.emptyList();
                }
                final List<FireTvManager.Device> devicesForChooser = fireTvDevices;
                Activity activity = getActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    call.reject("Activity is not available");
                    return;
                }

                String discoveryReport = implementation.getLastDiscoveryReport();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        call.reject("Activity is not available");
                        return;
                    }
                    showPlaybackChooser(call, dlnaDevices, devicesForChooser, discoveryReport);
                });
            } catch (Exception e) {
                call.reject("Failed to discover playback devices: " + e.getMessage());
            }
        }, "IonicChromecast-DLNA-Discovery").start();
    }

    /**
     * Opens Google Cast directly, without scanning for DLNA devices.
     */
    @PluginMethod
    public void requestCastSession(PluginCall call) {
        if (!isReady(call)) return;
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            call.reject("Activity is not available");
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                call.reject("Activity is not available");
                return;
            }
            showGoogleCastChooser(call);
        });
    }

    private boolean isReady(PluginCall call) {
        if (!implementation.isInitialized() || implementation.getCastContext() == null) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Cast SDK not initialized");
            call.reject("Failed to request playback session", err);
            return false;
        }
        if (getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) {
            JSObject err = new JSObject();
            err.put("success", false);
            err.put("message", "Activity is not available");
            call.reject("Failed to request playback session", err);
            return false;
        }
        return true;
    }

    private void showPlaybackChooser(
        PluginCall call,
        List<DlnaManager.Device> dlnaDevices,
        List<FireTvManager.Device> fireTvDevices,
        String discoveryReport
    ) {
        if (dlnaDevices.isEmpty() && fireTvDevices.isEmpty()) {
            showGoogleCastChooser(call, discoveryReport);
            return;
        }

        try {
            String[] choices = new String[dlnaDevices.size() + fireTvDevices.size() + 1];
            choices[0] = "Chromecast / Android TV (Google Cast)";
            for (int i = 0; i < dlnaDevices.size(); i++) {
                choices[i + 1] = dlnaDevices.get(i).getName() + " (DLNA / UPnP)";
            }
            for (int i = 0; i < fireTvDevices.size(); i++) {
                choices[i + dlnaDevices.size() + 1] = fireTvDevices.get(i).getName() + " (Amazon Fire TV)";
            }

            AlertDialog dialog = new AlertDialog.Builder((AppCompatActivity) getActivity())
                .setTitle("Seleccionar TV")
                .setItems(choices, (selectedDialog, which) -> {
                    if (which == 0) {
                        showGoogleCastChooser(call);
                        return;
                    }

                    try {
                        if (which > dlnaDevices.size()) {
                            FireTvManager.Device selectedFireTv = fireTvDevices.get(which - dlnaDevices.size() - 1);
                            implementation.clearDlnaSession();
                            implementation.selectFireTvDevice(selectedFireTv);
                            JSObject event = new JSObject();
                            event.put("state", "started");
                            event.put("transport", "fire-tv");
                            event.put("deviceName", selectedFireTv.getName());
                            notifyListeners("sessionStarted", event);
                            JSObject ret = new JSObject();
                            ret.put("success", true);
                            ret.put("message", "Fire TV selected");
                            ret.put("transport", "fire-tv");
                            ret.put("deviceName", selectedFireTv.getName());
                            call.resolve(ret);
                            return;
                        }
                        DlnaManager.Device selected = dlnaDevices.get(which - 1);
                        implementation.clearFireTvSession();
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
                    } catch (Exception e) {
                        call.reject("Failed to select DLNA renderer: " + e.getMessage());
                    }
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
            implementation.clearFireTvSession();

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

    // Read-only diagnostics for the selected DLNA renderer; never changes playback.
    @PluginMethod
    public void getDlnaDiagnostics(PluginCall call) {
        new Thread(() -> {
            try {
                JSObject result = new JSObject();
                for (java.util.Map.Entry<String, String> entry : implementation.getDlnaPlaybackDiagnostics().entrySet()) {
                    result.put(entry.getKey(), entry.getValue());
                }
                call.resolve(result);
            } catch (Exception e) {
                call.reject("Failed to read DLNA diagnostics: " + e.getMessage());
            }
        }, "IonicChromecast-DLNA-Diagnostics").start();
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
        double durationSeconds = metadataObj != null ? metadataObj.optDouble("duration", 0) : 0;
        final double duration = Double.isFinite(durationSeconds) && durationSeconds > 0
            && durationSeconds < Long.MAX_VALUE / 1000.0 ? durationSeconds : 0;
        String imageUrl = null;
        if (metadataObj != null && metadataObj.has("images")) {
            try { imageUrl = metadataObj.getJSONArray("images").optString(0, null); } catch (Exception ignore) {}
        }

        final String finalImageUrl = imageUrl;
        if (implementation.isDlnaSessionActive() || implementation.isFireTvSessionActive()) {
            new Thread(
                () -> resolveLoadMedia(call, url, title, subtitle, finalImageUrl, contentType, duration),
                "IonicChromecast-DLNA-Load"
            ).start();
            return;
        }

        resolveLoadMedia(call, url, title, subtitle, imageUrl, contentType, duration);
    }

    private void resolveLoadMedia(
        PluginCall call,
        String url,
        String title,
        String subtitle,
        String imageUrl,
        String contentType
    ) {
        resolveLoadMedia(call, url, title, subtitle, imageUrl, contentType, 0);
    }

    private void resolveLoadMedia(
        PluginCall call,
        String url,
        String title,
        String subtitle,
        String imageUrl,
        String contentType,
        double duration
    ) {
        try {
            boolean ok = implementation.loadMedia(url, title, subtitle, imageUrl, contentType, duration);
            JSObject ret = new JSObject();
            ret.put("success", ok);
            String transport = implementation.getActiveTransport();
            if (transport != null) ret.put("transport", transport);
            String err = implementation.getLastError();
            if (err != null && !err.isEmpty()) ret.put("error", err);
            if (ok) {
                call.resolve(ret);
            } else {
                String message = ("dlna".equals(transport) || "fire-tv".equals(transport)) && err != null && !err.isEmpty()
                    ? "Failed to load media: " + err
                    : "Failed to load media";
                call.reject(message, ret);
            }
        } catch (Exception e) {
            call.reject("Failed to load media: " + e.getMessage());
        }
    }

    /**
     * Finaliza la sesión Cast activa
     */
    @PluginMethod
    public void endSession(PluginCall call) {
        if (implementation.isDlnaSessionActive() || implementation.isFireTvSessionActive()) {
            new Thread(() -> resolveEndSession(call), "IonicChromecast-DLNA-End").start();
            return;
        }
        resolveEndSession(call);
    }

    private void resolveEndSession(PluginCall call) {
        try {
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
        } catch (Exception e) {
            call.reject("Failed to end session: " + e.getMessage());
        }
    }

    /**
     * Revisa si hay dispositivos disponibles
     */
    @PluginMethod
    public void areDevicesAvailable(PluginCall call) {
        new Thread(() -> {
            try {
                boolean available = implementation.areDevicesAvailable();
                JSObject ret = new JSObject();
                ret.put("available", available);
                ret.put("message", implementation.getLastDiscoveryReport());
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to check playback devices: " + e.getMessage());
            }
        }, "IonicChromecast-Device-Discovery").start();
    }
}
