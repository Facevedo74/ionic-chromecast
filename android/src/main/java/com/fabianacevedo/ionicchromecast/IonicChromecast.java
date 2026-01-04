package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.Logger;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.images.WebImage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// New imports for improved media loading
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
// Nuevos imports para esperar sesión activa
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaRouteSelector;

public class IonicChromecast {
    
    private static final String TAG = "IonicChromecast";
    private static final String PREFS_NAME = "IonicChromecastPrefs";
    private static final String KEY_RECEIVER_APP_ID = "receiverApplicationId";
    private CastContext castContext;
    private boolean isInitialized = false;
    private Context appContext;
    
    // Nueva variable para el descubrimiento de dispositivos
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private final Object discoveryLock = new Object();
    
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
            
            // Save the receiver app ID to SharedPreferences for CastOptionsProvider
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_RECEIVER_APP_ID, receiverApplicationId).apply();
            
            // Also set it in the static variable for immediate use
            CastOptionsProvider.sReceiverApplicationId = receiverApplicationId;
            
            // Ensure CastContext is obtained on the main thread
            final AtomicBoolean initSuccess = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(1);
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    // Use application context per Cast SDK recommendations
                    appContext = context.getApplicationContext();
                    castContext = CastContext.getSharedInstance(appContext);
                    if (castContext != null) {
                        isInitialized = true;
                        Logger.info(TAG, "Cast SDK initialized successfully");
                        initSuccess.set(true);
                    } else {
                        Logger.error(TAG, "Failed to get CastContext", null);
                        initSuccess.set(false);
                    }
                } catch (Exception e) {
                    Logger.error(TAG, "Error initializing Cast SDK on main thread: " + e.getMessage(), e);
                    initSuccess.set(false);
                } finally {
                    latch.countDown();
                }
            });
            // Wait up to 5 seconds for initialization to complete
            latch.await(5, TimeUnit.SECONDS);
            // Save the context for later use
            this.appContext = context.getApplicationContext();
            // Prepare MediaRouter and selector for device discovery
            this.mediaRouter = MediaRouter.getInstance(appContext);
            this.mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastOptionsProvider.sReceiverApplicationId != null ? CastOptionsProvider.sReceiverApplicationId : "CC1AD845"))
                .build();
            return initSuccess.get();
            
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
            // No hay API pública para forzar el diálogo de Cast, debe usarse el CastButton en la UI.
            Logger.info(TAG, "Requested Cast session (UI CastButton should be used)");
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
            com.google.android.gms.cast.framework.CastSession session = castContext.getSessionManager().getCurrentCastSession();
            boolean active = (session != null && session.isConnected());
            Logger.info(TAG, "isSessionActive check: session=" + (session != null) + ", connected=" + (session != null && session.isConnected()) + ", result=" + active);
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
        if (!isInitialized || castContext == null || appContext == null || mediaRouter == null || mediaRouteSelector == null) {
            Logger.error(TAG, "Cast SDK not initialized. Call initialize() first.", null);
            return false;
        }
        final AtomicBoolean found = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        MediaRouter.Callback discoveryCallback = new MediaRouter.Callback() {
            @Override
            public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
                if (route != null && route.matchesSelector(mediaRouteSelector) && !route.isDefault()) {
                    found.set(true);
                    latch.countDown();
                }
            }
            @Override
            public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
                if (route != null && route.matchesSelector(mediaRouteSelector) && !route.isDefault()) {
                    found.set(true);
                    latch.countDown();
                }
            }
        };
        try {
            synchronized (discoveryLock) {
                mediaRouter.addCallback(mediaRouteSelector, discoveryCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
                // También revisa rutas ya conocidas
                for (MediaRouter.RouteInfo route : mediaRouter.getRoutes()) {
                    if (route != null && route.matchesSelector(mediaRouteSelector) && !route.isDefault()) {
                        found.set(true);
                        break;
                    }
                }
                if (!found.get()) {
                    latch.await(2500, TimeUnit.MILLISECONDS); // Espera hasta 2.5s por descubrimiento
                }
                mediaRouter.removeCallback(discoveryCallback);
            }
            Logger.info(TAG, "areDevicesAvailable: found=" + found.get());
            return found.get();
        } catch (Exception e) {
            Logger.error(TAG, "Error checking available devices: " + e.getMessage(), e);
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
                Logger.info(TAG, "Setting title: " + title);
                castMetadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, title);
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                Logger.info(TAG, "Setting subtitle: " + subtitle);
                castMetadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, subtitle);
            }
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Logger.info(TAG, "Setting image: " + imageUrl);
                castMetadata.addImage(new WebImage(android.net.Uri.parse(imageUrl)));
            }
            
            // Use provided content type or default to video/mp4
            String finalContentType = (contentType != null && !contentType.isEmpty()) ? contentType : "video/mp4";
            
            Logger.info(TAG, "📹 Building MediaInfo: URL=" + url + ", contentType=" + finalContentType);
            
            com.google.android.gms.cast.MediaInfo mediaInfo = new com.google.android.gms.cast.MediaInfo.Builder(url)
                .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(finalContentType)
                .setMetadata(castMetadata)
                .build();
            
            Logger.info(TAG, "✅ MediaInfo built successfully");
            
            com.google.android.gms.cast.framework.CastSession session = castContext.getSessionManager().getCurrentCastSession();
            if (session == null || !session.isConnected()) {
                Logger.info(TAG, "No active Cast session yet. Waiting up to 10s...");
                boolean connected = waitForActiveSession(10_000);
                if (!connected) {
                    Logger.error(TAG, "No active Cast session after waiting", null);
                    return false;
                }
                session = castContext.getSessionManager().getCurrentCastSession();
            }

            RemoteMediaClient rmc = session.getRemoteMediaClient();
            if (rmc == null) {
                Logger.error(TAG, "RemoteMediaClient is null (session not ready)", null);
                return false;
            }

            // Build a load request with autoplay
            MediaLoadRequestData requestData = new MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(0L)
                .build();

            Logger.info(TAG, "Sending media load request: URL=" + url + ", contentType=" + finalContentType);

            // Send load and wait briefly for the result so we can report success/failure
            final AtomicBoolean loadSuccess = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                PendingResult<RemoteMediaClient.MediaChannelResult> pending = rmc.load(requestData);
                pending.setResultCallback(result -> {
                    boolean ok = result != null && result.getStatus() != null && result.getStatus().isSuccess();
                    loadSuccess.set(ok);
                    if (ok) {
                        Logger.info(TAG, "Media load success");
                    } else {
                        String msg = (result != null && result.getStatus() != null) ? String.valueOf(result.getStatus().getStatusCode()) : "unknown";
                        Logger.error(TAG, "Media load failed. Status=" + msg, null);
                    }
                    latch.countDown();
                });
                // Wait up to 6 seconds for a result
                latch.await(6, TimeUnit.SECONDS);
            } catch (Exception e) {
                Logger.error(TAG, "Error sending media load request: " + e.getMessage(), e);
                return false;
            }

            return loadSuccess.get();
        } catch (Exception e) {
            Logger.error(TAG, "Error loading media: " + e.getMessage(), e);
            return false;
        }
    }

    // Helper: espera una sesión activa hasta timeoutMs
    private boolean waitForActiveSession(long timeoutMs) {
        if (castContext == null) return false;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean active = new AtomicBoolean(false);

        SessionManagerListener<CastSession> listener = new SessionManagerListener<CastSession>() {
            @Override public void onSessionStarted(CastSession session, String sessionId) {
                active.set(session != null && session.isConnected());
                latch.countDown();
            }
            @Override public void onSessionResumed(CastSession session, boolean wasSuspended) {
                active.set(session != null && session.isConnected());
                latch.countDown();
            }
            @Override public void onSessionStartFailed(CastSession session, int error) { latch.countDown(); }
            @Override public void onSessionResumeFailed(CastSession session, int error) { latch.countDown(); }
            @Override public void onSessionSuspended(CastSession session, int reason) { }
            @Override public void onSessionEnded(CastSession session, int error) { }
            @Override public void onSessionEnding(CastSession session) { }
            @Override public void onSessionStarting(CastSession session) { }
            @Override public void onSessionResuming(CastSession session, String sessionId) { }
        };

        try {
            // Si ya hay sesión conectada, devolver inmediatamente
            CastSession current = castContext.getSessionManager().getCurrentCastSession();
            if (current != null && current.isConnected()) {
                return true;
            }
            castContext.getSessionManager().addSessionManagerListener(listener, CastSession.class);
            // Esperar hasta timeout
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            // Verificar de nuevo
            current = castContext.getSessionManager().getCurrentCastSession();
            return current != null && current.isConnected() || active.get();
        } catch (Exception e) {
            Logger.error(TAG, "Error waiting for Cast session: " + e.getMessage(), e);
            return false;
        } finally {
            try {
                castContext.getSessionManager().removeSessionManagerListener(listener, CastSession.class);
            } catch (Exception ignore) {}
        }
    }
}
