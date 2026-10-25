package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.amazon.whisperplay.fling.media.controller.DiscoveryController;
import com.amazon.whisperplay.fling.media.controller.RemoteMediaPlayer;
import com.getcapacitor.Logger;

import org.json.JSONObject;

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
        selectedDevice = device;
        lastError = null;
    }

    void clearSelectedDevice() {
        selectedDevice = null;
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
        Device device = selectedDevice;
        if (device == null) {
            lastError = "No Fire TV selected";
            return false;
        }
        try {
            URL mediaUrl = new URL(url);
            if (!("http".equalsIgnoreCase(mediaUrl.getProtocol())
                || "https".equalsIgnoreCase(mediaUrl.getProtocol()))
                || mediaUrl.getHost() == null || mediaUrl.getHost().isEmpty()) {
                lastError = "Fire TV requires an HTTP or HTTPS media URL";
                return false;
            }

            JSONObject metadata = new JSONObject();
            String type = contentType != null && !contentType.isEmpty() ? contentType
                : (url.toLowerCase(java.util.Locale.US).contains(".m3u8")
                    ? "application/vnd.apple.mpegurl" : "video/mp4");
            metadata.put("type", type);
            if (title != null && !title.isEmpty()) metadata.put("title", title);
            if (subtitle != null && !subtitle.isEmpty()) metadata.put("description", subtitle);
            if (imageUrl != null && !imageUrl.isEmpty()) metadata.put("poster", imageUrl);

            // Amazon's built-in receiver replaces the current media source on this player.
            device.player.setMediaSource(url, metadata.toString(), true, false)
                .get(15, TimeUnit.SECONDS);
            lastError = null;
            return true;
        } catch (Exception e) {
            lastError = "Fire TV media load failed: " + e.getMessage();
            Logger.warn(TAG, lastError);
            return false;
        }
    }

    boolean stopAndClear() {
        Device device = selectedDevice;
        selectedDevice = null;
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
