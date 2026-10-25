package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.amazon.whisperplay.fling.media.controller.DiscoveryController;
import com.amazon.whisperplay.fling.media.controller.RemoteMediaPlayer;
import com.amazon.whisperplay.fling.media.service.MediaPlayerInfo;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus;
import com.getcapacitor.Logger;

import org.json.JSONObject;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Controls the built-in Amazon Fling receiver without changing Cast or DLNA playback. */
final class FireTvManager {
    private static final String TAG = "IonicChromecast-FireTV";
    private static final String BUILT_IN_RECEIVER_SID = "amzn.thin.pl";
    private final Context appContext;
    private DiscoveryController controller;
    private final Map<String, Device> devices = new LinkedHashMap<>();
    private volatile Device selectedDevice;
    private volatile boolean started;
    private volatile String lastError;
    private volatile FireTvHlsProxy activeHlsProxy;
    private volatile FireTvHlsProxy pendingHlsProxy;

    static final class Device {
        final String id;
        final String name;
        final RemoteMediaPlayer player;

        Device(RemoteMediaPlayer player) {
            this.player = player;
            this.id = player.getUniqueIdentifier();
            this.name = player.getName();
        }

        String getName() {
            return name;
        }
    }

    FireTvManager(Context context) {
        appContext = context.getApplicationContext();
    }

    private final DiscoveryController.IDiscoveryListener listener = new DiscoveryController.IDiscoveryListener() {
        @Override
        public void playerDiscovered(RemoteMediaPlayer player) {
            if (player == null) return;
            try {
                Device device = new Device(player);
                if (device.id == null || device.id.isEmpty()) return;
                synchronized (devices) {
                    devices.put(device.id, device);
                }
                Device selected = selectedDevice;
                if (selected != null && selected.id.equals(device.id)) selectedDevice = device;
                Logger.info(TAG, "Discovered Fire TV: " + device.name);
            } catch (Exception e) {
                Logger.warn(TAG, "Invalid Fire TV discovery result: " + e.getMessage());
            }
        }

        @Override
        public void playerLost(RemoteMediaPlayer player) {
            if (player == null) return;
            try {
                String id = player.getUniqueIdentifier();
                synchronized (devices) {
                    devices.remove(id);
                }
                Device selected = selectedDevice;
                if (selected != null && selected.id.equals(id)) selectedDevice = null;
            } catch (Exception e) {
                Logger.warn(TAG, "Could not remove lost Fire TV: " + e.getMessage());
            }
        }

        @Override
        public void discoveryFailure() {
            lastError = "Amazon Fling discovery failed";
            Logger.warn(TAG, lastError);
        }
    };

    List<Device> discover(int timeoutMs) {
        startDiscovery();
        if (started) {
            synchronized (devices) {
                if (!devices.isEmpty()) return new ArrayList<>(devices.values());
            }
            try {
                Thread.sleep(Math.max(1000, timeoutMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastError = "Fire TV discovery interrupted";
            }
        }
        synchronized (devices) {
            return new ArrayList<>(devices.values());
        }
    }

    private void startDiscovery() {
        if (started) return;
        CountDownLatch ready = new CountDownLatch(1);
        Runnable start = () -> {
            try {
                if (!started) {
                    if (controller == null) controller = new DiscoveryController(appContext);
                    controller.start(BUILT_IN_RECEIVER_SID, listener);
                    started = true;
                    lastError = null;
                }
            } catch (Exception e) {
                lastError = "Could not start Amazon Fling discovery: " + e.getMessage();
                Logger.warn(TAG, lastError);
            } finally {
                ready.countDown();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            start.run();
        } else {
            new Handler(Looper.getMainLooper()).post(start);
            try {
                if (!ready.await(3, TimeUnit.SECONDS)) lastError = "Fire TV discovery start timed out";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastError = "Fire TV discovery interrupted";
            }
        }
    }

    String getDiscoveryReport() {
        synchronized (devices) {
            return "Fire TV discovery: " + devices.size() + " built-in receiver(s)"
                + (lastError == null ? "" : "; error=" + lastError);
        }
    }

    void selectDevice(Device device) {
        Device previous = selectedDevice;
        if (previous != null && device != null && !previous.id.equals(device.id)) closeHlsProxies();
        selectedDevice = device;
        lastError = null;
    }

    void clearSelectedDevice() {
        selectedDevice = null;
        closeHlsProxies();
    }

    boolean hasSelectedDevice() {
        return selectedDevice != null;
    }

    String getSelectedDeviceName() {
        Device selected = selectedDevice;
        return selected != null ? selected.name : null;
    }

    String getLastError() {
        return lastError;
    }

    boolean loadMedia(String url, String title, String subtitle, String imageUrl, String contentType) {
        closePendingHlsProxy();
        Device device = selectedDevice;
        if (device == null) {
            lastError = "No Fire TV selected";
            return false;
        }
        String type = null;
        String playbackUrl = null;
        boolean sourceRequested = false;
        FireTvHlsProxy newHlsProxy = null;
        try {
            // Escape spaces in media URLs before sending them to the Fire TV receiver.
            playbackUrl = new URI(url.trim().replace(" ", "%20")).toASCIIString();
            URL mediaUrl = new URL(playbackUrl);
            if (!("http".equalsIgnoreCase(mediaUrl.getProtocol())
                || "https".equalsIgnoreCase(mediaUrl.getProtocol()))
                || mediaUrl.getHost() == null || mediaUrl.getHost().isEmpty()) {
                lastError = "Fire TV requires an HTTP or HTTPS media URL";
                return false;
            }

            JSONObject metadata = new JSONObject();
            type = contentType != null && !contentType.isEmpty() ? contentType
                : (url.toLowerCase(java.util.Locale.US).contains(".m3u8")
                    ? "application/vnd.apple.mpegurl" : "video/mp4");
            if (type.toLowerCase(java.util.Locale.US).contains("mpegurl")
                || mediaUrl.getPath().toLowerCase(java.util.Locale.US).endsWith(".m3u8")) {
                newHlsProxy = FireTvHlsProxy.openIfNeeded(appContext, mediaUrl);
                if (newHlsProxy != null) playbackUrl = newHlsProxy.getMediaUrl();
            }
            metadata.put("type", type);
            if (title != null && !title.isEmpty()) metadata.put("title", title);
            if (subtitle != null && !subtitle.isEmpty()) metadata.put("description", subtitle);
            if (imageUrl != null && !imageUrl.isEmpty()) metadata.put("poster", imageUrl);

            // Amazon's built-in receiver replaces the current media source on this player.
            sourceRequested = true;
            device.player.setMediaSource(playbackUrl, metadata.toString(), true, false)
                .get(15, TimeUnit.SECONDS);
            activateHlsProxy(newHlsProxy);
            lastError = null;
            return true;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            StringBuilder playbackCheck = new StringBuilder();
            if (sourceRequested && isPlayingSource(device, playbackUrl, newHlsProxy, playbackCheck)) {
                Logger.warn(TAG, "Fire TV reported a media load error but is playing the requested source: "
                    + cause.getMessage());
                activateHlsProxy(newHlsProxy);
                lastError = null;
                return true;
            }
            lastError = "Fire TV media load failed: " + cause.getMessage();
            if (sourceRequested) lastError += "; playback check=" + playbackCheck;
            if (newHlsProxy != null) {
                lastError += "; HLS relay=" + newHlsProxy.getDiagnostic();
                if (sourceRequested) pendingHlsProxy = newHlsProxy;
                else newHlsProxy.close();
            }
            if (type != null && type.toLowerCase(java.util.Locale.US).contains("mpegurl")) {
                try {
                    Boolean supported = device.player.isMimeTypeSupported(type).get(4, TimeUnit.SECONDS);
                    lastError += "; HLS MIME supported by receiver=" + supported;
                } catch (Exception diagnosticError) {
                    lastError += "; HLS MIME support could not be queried";
                }
            }
            Logger.warn(TAG, lastError);
            return false;
        }
    }

    private boolean isPlayingSource(Device device, String playbackUrl, FireTvHlsProxy proxy,
        StringBuilder report) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int attempts = 0;
        while (true) {
            attempts++;
            MediaPlayerInfo info = null;
            MediaPlayerStatus status = null;
            String infoError = null;
            String statusError = null;
            try {
                info = device.player.getMediaInfo().get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                infoError = e.getClass().getSimpleName();
            }
            try {
                status = device.player.getStatus().get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                statusError = e.getClass().getSimpleName();
            }
            boolean sourceMatches = info != null && playbackUrl.equals(info.getSource());
            boolean relayServedMedia = proxy != null && proxy.hasServedMedia();
            report.setLength(0);
            report.append("sourceMatches=").append(sourceMatches)
                .append(", relayServedMedia=").append(relayServedMedia)
                .append(", state=").append(status != null ? status.getState() : "unavailable")
                .append(", condition=").append(status != null ? status.getCondition() : "unavailable");
            if (infoError != null) report.append(", mediaInfoError=").append(infoError);
            if (statusError != null) report.append(", statusError=").append(statusError);
            if ((sourceMatches || relayServedMedia) && status != null
                && status.getState() == MediaPlayerStatus.MediaState.Playing) {
                return true;
            }
            if (System.nanoTime() >= deadline) break;
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        report.append(", attempts=").append(attempts);
        return false;
    }

    private void activateHlsProxy(FireTvHlsProxy proxy) {
        FireTvHlsProxy previous = activeHlsProxy;
        activeHlsProxy = proxy;
        if (previous != null && previous != proxy) previous.close();
    }

    private void closePendingHlsProxy() {
        FireTvHlsProxy previous = pendingHlsProxy;
        pendingHlsProxy = null;
        if (previous != null) previous.close();
    }

    private void closeHlsProxies() {
        closePendingHlsProxy();
        FireTvHlsProxy previous = activeHlsProxy;
        activeHlsProxy = null;
        if (previous != null) previous.close();
    }

    boolean stopAndClear() {
        Device device = selectedDevice;
        selectedDevice = null;
        closeHlsProxies();
        if (device == null) return true;
        try {
            device.player.stop().get(5, TimeUnit.SECONDS);
            lastError = null;
            return true;
        } catch (Exception e) {
            lastError = "Could not stop Fire TV playback: " + e.getMessage();
            Logger.warn(TAG, lastError);
            return false;
        }
    }

    void close() {
        selectedDevice = null;
        closeHlsProxies();
        if (started) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (controller != null) controller.stop();
                } catch (Exception e) {
                    Logger.warn(TAG, "Could not stop Fire TV discovery: " + e.getMessage());
                }
                started = false;
            });
        }
    }
}
