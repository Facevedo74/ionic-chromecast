package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.Logger;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaRouteSelector;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.images.WebImage;

public class IonicChromecast {
    
    private static final String TAG = "IonicChromecast";
    private static final String PREFS_NAME = "IonicChromecastPrefs";
    private static final String KEY_RECEIVER_APP_ID = "receiverApplicationId";
    private CastContext castContext;
    private boolean isInitialized = false;
    private Context appContext;
    private String lastError = null;
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private DlnaManager dlnaManager;
    private FireTvManager fireTvManager;
    private PlaybackTransportRegistry transportRegistry;
    private final Object discoveryLock = new Object();
    
    /**
     * Initialize the Google Cast SDK with the provided receiver application ID
     * @param context The application context
     * @param receiverApplicationId The Cast receiver application ID
     * @return true if initialization was successful
     */
    public boolean initialize(Context context, String receiverApplicationId) {
        try {
            lastError = null;
            appContext = context.getApplicationContext();
            if (dlnaManager == null) {
                dlnaManager = new DlnaManager(appContext);
            }
            if (fireTvManager == null) {
                fireTvManager = new FireTvManager(appContext);
            }
            if (transportRegistry == null) {
                transportRegistry = new PlaybackTransportRegistry();
                transportRegistry.register(new DlnaPlaybackTransport(dlnaManager));
                transportRegistry.register(new FireTvPlaybackTransport(fireTvManager));
            }
            if (isInitialized) {
                Logger.info(TAG, "Cast SDK already initialized");
                return true;
            }
            
            if (receiverApplicationId == null || receiverApplicationId.isEmpty()) {
                Logger.error(TAG, "Receiver Application ID is required", null);
                return false;
            }

            Logger.info(TAG, "Initializing Cast SDK with receiver ID: " + receiverApplicationId);
            Logger.info(TAG, "Thread at init: " + Thread.currentThread().getName());
            
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_RECEIVER_APP_ID, receiverApplicationId).apply();
            
            // Also set it in the static variable for immediate use
            CastOptionsProvider.sReceiverApplicationId = receiverApplicationId;
            
            int playStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
            Logger.info(TAG, "Google Play Services status=" + playStatus);
            if (playStatus != ConnectionResult.SUCCESS) {
                lastError = "Google Play Services status=" + playStatus;
                Logger.error(TAG, lastError, null);
                return false;
            }
            // Obtener CastContext y preparar MediaRouter siempre en el hilo principal
            Runnable initRunnable = () -> {
                try {
                    castContext = CastContext.getSharedInstance(appContext);
                    if (castContext != null) {
                        mediaRouter = MediaRouter.getInstance(appContext);
                        mediaRouteSelector = new MediaRouteSelector.Builder()
                            .addControlCategory(CastMediaControlIntent.categoryForCast(CastOptionsProvider.sReceiverApplicationId != null ? CastOptionsProvider.sReceiverApplicationId : "CC1AD845"))
                            .build();
                    }
                } catch (Exception e) {
                    lastError = "Error initializing on main thread: " + e.getMessage();
                    Logger.error(TAG, lastError, e);
                }
            };

            if (Looper.myLooper() == Looper.getMainLooper()) {
                initRunnable.run();
            } else {
                final CountDownLatch latch = new CountDownLatch(1);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    initRunnable.run();
                    latch.countDown();
                });
                boolean awaited = latch.await(6, TimeUnit.SECONDS);
                if (!awaited && castContext == null && lastError == null) {
                    lastError = "Timed out waiting for CastContext on main thread";
                    Logger.error(TAG, lastError, null);
                }
            }

            if (castContext != null) {
                isInitialized = true;
                Logger.info(TAG, "Cast SDK initialized successfully");
            } else {
                if (lastError == null) lastError = "Failed to get CastContext";
                Logger.error(TAG, lastError, null);
            }
            return isInitialized;
            
        } catch (Exception e) {
            lastError = "Error initializing Cast SDK: " + e.getMessage();
            Logger.error(TAG, lastError, e);
            return false;
        }
    }

    public String getLastError() {
        return lastError;
    }
    
    public boolean isInitialized() {
        return isInitialized;
    }
    public CastContext getCastContext() {
        return castContext;
    }

    public List<DlnaManager.Device> discoverDlnaDevices(int timeoutMs) {
        if (dlnaManager == null && appContext != null) {
            dlnaManager = new DlnaManager(appContext);
        }
        return dlnaManager != null ? dlnaManager.discover(timeoutMs) : java.util.Collections.emptyList();
    }

    public String getLastDiscoveryReport() {
        return transportRegistry != null ? transportRegistry.discoveryReport()
            : "DLNA discovery is not initialized; Fire TV discovery is not initialized";
    }

    public List<PlaybackDevice> discoverPlaybackDevices(int timeoutMs) {
        return transportRegistry != null ? transportRegistry.discover(timeoutMs)
            : java.util.Collections.emptyList();
    }

    public void selectPlaybackDevice(PlaybackDevice device) {
        if (transportRegistry == null) throw new IllegalStateException("Playback transports are not initialized");
        transportRegistry.select(device);
    }

    public void clearPlaybackSessions() {
        if (transportRegistry != null) transportRegistry.clearAll();
    }

    public boolean isNativePlaybackSessionActive() {
        return transportRegistry != null && transportRegistry.hasSession();
    }

    public void closePlaybackTransports() {
        if (transportRegistry != null) transportRegistry.close();
    }

    public List<FireTvManager.Device> discoverFireTvDevices(int timeoutMs) {
        return fireTvManager != null ? fireTvManager.discover(timeoutMs) : java.util.Collections.emptyList();
    }

    public String getFireTvDiscoveryReport() {
        return fireTvManager != null ? fireTvManager.getDiscoveryReport() : "Fire TV discovery is not initialized";
    }

    public void selectFireTvDevice(FireTvManager.Device device) {
        if (fireTvManager != null) fireTvManager.selectDevice(device);
    }

    public void clearFireTvSession() {
        if (fireTvManager != null) fireTvManager.clearSelectedDevice();
    }

    public boolean isFireTvSessionActive() {
        return fireTvManager != null && fireTvManager.hasSelectedDevice();
    }

    public void closeFireTvDiscovery() {
        if (fireTvManager != null) fireTvManager.close();
    }

    public java.util.Map<String, String> getDlnaPlaybackDiagnostics() {
        return dlnaManager != null ? dlnaManager.getPlaybackDiagnostics() : java.util.Collections.emptyMap();
    }

    public void selectDlnaDevice(DlnaManager.Device device) {
        if (dlnaManager != null) {
            dlnaManager.selectDevice(device);
        }
    }

    public void clearDlnaSession() {
        if (dlnaManager != null) {
            dlnaManager.clearSelectedDevice();
        }
    }

    public boolean isDlnaSessionActive() {
        return dlnaManager != null && dlnaManager.hasSelectedDevice();
    }

    public String getActiveTransport() {
        PlaybackTransport active = transportRegistry != null ? transportRegistry.active() : null;
        if (active != null) return active.id();
        return isCastSessionActive() ? "google-cast" : null;
    }

    public String getSelectedDeviceName() {
        PlaybackTransport active = transportRegistry != null ? transportRegistry.active() : null;
        return active != null ? active.selectedDeviceName() : null;
    }

    /**
     * Verifica si hay sesión activa
     */
    public boolean isSessionActive() {
        if (isNativePlaybackSessionActive()) return true;
        return isCastSessionActive();
    }

    private boolean isCastSessionActive() {
        if (!isInitialized || castContext == null) return false;

        // Consultar SessionManager en el hilo principal para obtener el estado real
        final AtomicBoolean active = new AtomicBoolean(false);
        Runnable check = () -> {
            try {
                CastSession s = castContext.getSessionManager().getCurrentCastSession();
                boolean connected = s != null && s.isConnected();

                // Evita falsos positivos: requiere appId y RemoteMediaClient disponibles
                if (connected) {
                    try {
                        String appId = s.getApplicationMetadata() != null ? s.getApplicationMetadata().getApplicationId() : "";
                        RemoteMediaClient rmc = s.getRemoteMediaClient();
                        if (rmc == null || appId == null || appId.isEmpty()) {
                            connected = false;
                        }
                    } catch (Exception ignored) {
                        connected = false;
                    }
                }

                active.set(connected);
            } catch (Exception e) {
                Logger.error(TAG, "Error checking session status: " + e.getMessage(), e);
                active.set(false);
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            check.run();
            return active.get();
        }

        try {
            CountDownLatch latch = new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(() -> {
                check.run();
                latch.countDown();
            });
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Logger.error(TAG, "Interrupted while checking session", ie);
        }

        return active.get();
    }

    /**
     * Finaliza la sesión Cast actual, si existe
     */
    public boolean endSession() {
        PlaybackTransport active = transportRegistry != null ? transportRegistry.active() : null;
        if (active != null) {
            boolean stopped = active.endSession();
            lastError = stopped ? null : active.lastError();
            if (stopped) Logger.info(TAG, active.id() + " session ended by request");
            return stopped;
        }

        if (!isInitialized || castContext == null) {
            lastError = "Cast SDK not initialized. Call initialize() first.";
            Logger.error(TAG, lastError, null);
            return false;
        }

        lastError = null;
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        Runnable endRunnable = () -> {
            try {
                SessionManager sm = castContext.getSessionManager();
                if (sm == null) {
                    lastError = "SessionManager is null";
                    Logger.error(TAG, lastError, null);
                    latch.countDown();
                    return;
                }

                CastSession session = sm.getCurrentCastSession();
                if (session == null || !session.isConnected()) {
                    lastError = "No active Cast session to end";
                    Logger.error(TAG, lastError, null);
                    latch.countDown();
                    return;
                }

                sm.endCurrentSession(true);
                success.set(true);
                Logger.info(TAG, "Cast session ended by request");
            } catch (Exception e) {
                lastError = "Error ending session: " + e.getMessage();
                Logger.error(TAG, lastError, e);
            } finally {
                latch.countDown();
            }
        };

        mainHandler.post(endRunnable);

        try {
            latch.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        return success.get();
    }

    /**
     * Verifica si hay dispositivos Cast disponibles mediante MediaRouter
     */
    public boolean areDevicesAvailable() {
        if (!isInitialized || castContext == null || appContext == null || mediaRouter == null || mediaRouteSelector == null) {
            Logger.error(TAG, "Cast SDK not initialized. Call initialize() first.", null);
            return false;
        }

        boolean castAvailable = runDeviceScan();
        boolean nativeAvailable = !discoverPlaybackDevices(5000).isEmpty();
        Logger.info(TAG, "areDevicesAvailable: googleCastFound=" + castAvailable
            + ", nativePlaybackFound=" + nativeAvailable
            + "; " + getLastDiscoveryReport());
        return castAvailable || nativeAvailable;
    }

    /**
     * Realiza el escaneo de rutas Cast usando MediaRouter.
     */
    private boolean runDeviceScan() {
        final AtomicBoolean found = new AtomicBoolean(false);
        final CountDownLatch routeFound = new CountDownLatch(1);
        final CountDownLatch registrationComplete = new CountDownLatch(1);
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        MediaRouter.Callback discoveryCallback = new MediaRouter.Callback() {
            @Override
            public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
                if (route != null && route.matchesSelector(mediaRouteSelector) && !route.isDefault()) {
                    found.set(true);
                    routeFound.countDown();
                }
            }

            @Override
            public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
                if (route != null && route.matchesSelector(mediaRouteSelector) && !route.isDefault()) {
                    found.set(true);
                    routeFound.countDown();
                }
            }
        };

        Runnable register = () -> {
            try {
                synchronized (discoveryLock) {
                    mediaRouter.addCallback(
                        mediaRouteSelector,
                        discoveryCallback,
                        MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY | MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
                    );
                }
                found.set(hasMatchingCastRoute());
                if (found.get()) routeFound.countDown();
            } catch (Exception e) {
                Logger.error(TAG, "Could not start Cast route discovery: " + e.getMessage(), e);
            } finally {
                registrationComplete.countDown();
            }
        };

        boolean calledFromMain = Looper.myLooper() == Looper.getMainLooper();
        try {
            if (calledFromMain) register.run();
            else {
                mainHandler.post(register);
                registrationComplete.await(2, TimeUnit.SECONDS);
            }

            // Keep the UI thread free so MediaRouter can deliver route callbacks.
            if (!calledFromMain && !found.get()) routeFound.await(4500, TimeUnit.MILLISECONDS);

            if (!calledFromMain) {
                CountDownLatch finalCheck = new CountDownLatch(1);
                mainHandler.post(() -> {
                    try {
                        found.set(found.get() || hasMatchingCastRoute());
                        synchronized (discoveryLock) {
                            mediaRouter.removeCallback(discoveryCallback);
                        }
                    } catch (Exception e) {
                        Logger.warn(TAG, "Could not finish Cast route discovery: " + e.getMessage());
                    } finally {
                        finalCheck.countDown();
                    }
                });
                finalCheck.await(2, TimeUnit.SECONDS);
            } else {
                synchronized (discoveryLock) {
                    mediaRouter.removeCallback(discoveryCallback);
                }
            }
            Logger.info(TAG, "areDevicesAvailable: googleCastFound=" + found.get());
            return found.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error(TAG, "Interrupted while checking Cast routes", e);
            return false;
        } catch (Exception e) {
            Logger.error(TAG, "Error checking available devices: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean hasMatchingCastRoute() {
        if (mediaRouter == null || mediaRouteSelector == null) return false;
        for (MediaRouter.RouteInfo route : mediaRouter.getRoutes()) {
            if (route != null && route.matchesSelector(mediaRouteSelector) && !route.isDefault()) return true;
        }
        return false;
    }

    /**
     * Envía media al dispositivo Cast (flujo básico)
     */
    public boolean loadMedia(String url, String title, String subtitle, String imageUrl, String contentType) {
        return loadMedia(url, title, subtitle, imageUrl, contentType, 0);
    }

    public boolean loadMedia(String url, String title, String subtitle, String imageUrl, String contentType, double duration) {
        PlaybackTransport active = transportRegistry != null ? transportRegistry.active() : null;
        if (active != null) {
            boolean success = active.loadMedia(url, title, subtitle, imageUrl, contentType, duration);
            lastError = success ? null : active.lastError();
            return success;
        }

        if (!isInitialized || castContext == null) {
            lastError = "Cast SDK not initialized. Call initialize() first.";
            Logger.error(TAG, lastError, null);
            return false;
        }

        lastError = null;
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch done = new CountDownLatch(1);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        Runnable loadRunnable = () -> {
            try {
                if (url == null || url.isEmpty()) {
                    lastError = "Media URL is required";
                    Logger.error(TAG, lastError, null);
                    done.countDown();
                    return;
                }

                String effectiveUrl = url;

                Logger.info(TAG, "loadMedia: url=" + effectiveUrl + ", contentType=" + contentType);

                // Use GENERIC to ensure subtitle surfaces in Cast UI overlays.
                MediaMetadata md = new MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC);
                if (title != null && !title.isEmpty()) md.putString(MediaMetadata.KEY_TITLE, title);
                if (subtitle != null && !subtitle.isEmpty()) md.putString(MediaMetadata.KEY_SUBTITLE, subtitle);
                if (imageUrl != null && !imageUrl.isEmpty()) md.addImage(new WebImage(android.net.Uri.parse(imageUrl)));

                String ct = (contentType != null && !contentType.isEmpty()) ? contentType : "video/mp4";
                MediaInfo.Builder mediaInfoBuilder = new MediaInfo.Builder(effectiveUrl)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(ct)
                    .setMetadata(md);
                if (Double.isFinite(duration) && duration > 0 && duration < Long.MAX_VALUE / 1000.0) {
                    // The frontend supplies seconds; the Cast Android SDK expects milliseconds.
                    mediaInfoBuilder.setStreamDuration(Math.round(duration * 1000));
                }
                MediaInfo mediaInfo = mediaInfoBuilder.build();

                CastSession session = castContext.getSessionManager().getCurrentCastSession();
                if (session == null || !session.isConnected()) {
                    lastError = "No active Cast session";
                    Logger.error(TAG, lastError, null);
                    done.countDown();
                    return;
                }

                // Si la sesión es de otro appId, registra aviso pero intenta cargar igualmente
                try {
                    String currentAppId = session.getApplicationMetadata() != null ? session.getApplicationMetadata().getApplicationId() : "";
                    String desiredAppId = CastOptionsProvider.sReceiverApplicationId;
                    if (desiredAppId != null && !desiredAppId.isEmpty() && !desiredAppId.equals(currentAppId)) {
                        Logger.warn(TAG, "Session appId=" + currentAppId + " differs from desired=" + desiredAppId + "; attempting load on current session");
                    }
                } catch (Exception ignored) {}

                try {
                    String appId = session.getApplicationMetadata() != null ? session.getApplicationMetadata().getApplicationId() : "";
                    String deviceName = session.getCastDevice() != null ? session.getCastDevice().getFriendlyName() : "";
                    Logger.info(TAG, "Session connected. appId=" + appId + ", device=" + deviceName);
                } catch (Exception ignored) {}

                RemoteMediaClient rmc = session.getRemoteMediaClient();
                if (rmc == null) {
                    lastError = "RemoteMediaClient is null";
                    Logger.error(TAG, lastError, null);
                    done.countDown();
                    return;
                }

                // Detener lo que esté reproduciendo antes de cargar
                try {
                    PendingResult<RemoteMediaClient.MediaChannelResult> stopPending = rmc.stop();
                    if (stopPending != null) {
                        stopPending.await(3, TimeUnit.SECONDS);
                    }
                } catch (Exception ignored) {}

                MediaLoadRequestData req = new MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(0L)
                    .build();

                try {
                    PendingResult<RemoteMediaClient.MediaChannelResult> pending = rmc.load(req);
                    if (pending == null) {
                        lastError = "rmc.load() returned null";
                        Logger.error(TAG, lastError, null);
                        done.countDown();
                        return;
                    }

                    pending.setResultCallback(result1 -> {
                        if (result1 != null && result1.getStatus() != null && result1.getStatus().isSuccess()) {
                            success.set(true);
                            Logger.info(TAG, "Media load success");
                        } else {
                            int statusCode = (result1 != null && result1.getStatus() != null) ? result1.getStatus().getStatusCode() : -1;
                            lastError = "Media load failed, statusCode=" + statusCode;
                            Logger.error(TAG, lastError, null);
                        }
                        done.countDown();
                    });
                } catch (Exception e) {
                    lastError = "Error sending media load request: " + e.getMessage();
                    Logger.error(TAG, lastError, e);
                    done.countDown();
                }
            } catch (Exception e) {
                lastError = "Error loading media: " + e.getMessage();
                Logger.error(TAG, lastError, e);
                done.countDown();
            }
        };

        // Always dispatch load to the main thread but wait off-main for completion
        mainHandler.post(loadRunnable);

        boolean awaited = false;
        try {
            awaited = done.await(14, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            lastError = "Interrupted while loading media";
            Logger.error(TAG, lastError, ie);
            return false;
        }

        if (!awaited && !success.get()) {
            try {
                CastSession session = castContext.getSessionManager().getCurrentCastSession();
                String appId = session != null && session.getApplicationMetadata() != null ? session.getApplicationMetadata().getApplicationId() : "";
                String deviceName = session != null && session.getCastDevice() != null ? session.getCastDevice().getFriendlyName() : "";
                lastError = "Media load timed out (appId=" + appId + ", device=" + deviceName + ")";
            } catch (Exception e) {
                lastError = "Media load timed out";
            }
            Logger.error(TAG, lastError, null);
        }

        if (!success.get() && (lastError == null || lastError.isEmpty())) {
            try {
                CastSession session = castContext.getSessionManager().getCurrentCastSession();
                String appId = session != null && session.getApplicationMetadata() != null ? session.getApplicationMetadata().getApplicationId() : "";
                String deviceName = session != null && session.getCastDevice() != null ? session.getCastDevice().getFriendlyName() : "";
                lastError = "Media load failed (unknown reason, appId=" + appId + ", device=" + deviceName + ")";
            } catch (Exception e) {
                lastError = "Media load failed (unknown reason)";
            }
            Logger.error(TAG, lastError, null);
        }
        return success.get();
    }
}
