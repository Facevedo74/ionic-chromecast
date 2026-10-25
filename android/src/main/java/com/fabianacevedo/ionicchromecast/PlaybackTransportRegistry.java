package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Shared discovery, selection, and dispatch for non-Cast playback protocols. */
final class PlaybackTransportRegistry {
    private static final String TAG = "IonicChromecast-Transports";
    private final List<PlaybackTransport> transports = new ArrayList<>();

    synchronized void register(PlaybackTransport transport) {
        if (transport == null || transport.id() == null || transport.id().isEmpty()) {
            throw new IllegalArgumentException("Playback transport needs an ID");
        }
        for (PlaybackTransport existing : transports) {
            if (existing.id().equals(transport.id())) {
                throw new IllegalArgumentException("Duplicate playback transport: " + transport.id());
            }
        }
        transports.add(transport);
    }

    private synchronized List<PlaybackTransport> snapshot() {
        return new ArrayList<>(transports);
    }

    List<PlaybackDevice> discover(int timeoutMs) {
        List<PlaybackTransport> available = snapshot();
        List<FutureTask<List<PlaybackDevice>>> searches = new ArrayList<>();
        for (PlaybackTransport transport : available) {
            FutureTask<List<PlaybackDevice>> search =
                new FutureTask<>(() -> transport.discover(timeoutMs));
            Thread worker = new Thread(search, "IonicChromecast-Discover-" + transport.id());
            worker.setDaemon(true);
            worker.start();
            searches.add(search);
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1000, timeoutMs) + 1000L);
        List<PlaybackDevice> found = new ArrayList<>();
        for (int i = 0; i < searches.size(); i++) {
            FutureTask<List<PlaybackDevice>> search = searches.get(i);
            try {
                long remaining = deadline - System.nanoTime();
                List<PlaybackDevice> devices;
                if (search.isDone()) devices = search.get();
                else if (remaining > 0) devices = search.get(remaining, TimeUnit.NANOSECONDS);
                else throw new java.util.concurrent.TimeoutException();
                if (devices != null) found.addAll(devices);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                search.cancel(true);
                break;
            } catch (Exception e) {
                Logger.warn(TAG, available.get(i).id() + " discovery failed: " + e.getMessage());
                search.cancel(true);
            }
        }
        return found;
    }

    String discoveryReport() {
        List<String> reports = new ArrayList<>();
        for (PlaybackTransport transport : snapshot()) {
            try {
                reports.add(transport.discoveryReport());
            } catch (Exception e) {
                reports.add(transport.id() + " discovery report unavailable");
                Logger.warn(TAG, "Could not read " + transport.id() + " discovery report: " + e.getMessage());
            }
        }
        return android.text.TextUtils.join("; ", reports);
    }

    void select(PlaybackDevice device) {
        if (device == null || !snapshot().contains(device.transport)) {
            throw new IllegalArgumentException("Unknown playback device");
        }
        for (PlaybackTransport transport : snapshot()) {
            if (transport != device.transport) {
                try {
                    transport.clearSelection();
                } catch (Exception e) {
                    Logger.warn(TAG, "Could not clear " + transport.id() + " selection: " + e.getMessage());
                }
            }
        }
        device.transport.select(device);
    }

    PlaybackTransport active() {
        List<PlaybackTransport> available = snapshot();
        for (int i = available.size() - 1; i >= 0; i--) {
            PlaybackTransport transport = available.get(i);
            try {
                if (transport.hasSession()) return transport;
            } catch (Exception e) {
                Logger.warn(TAG, "Could not read " + transport.id() + " session: " + e.getMessage());
            }
        }
        return null;
    }

    boolean hasSession() { return active() != null; }

    void clearAll() {
        for (PlaybackTransport transport : snapshot()) {
            try {
                transport.clearSelection();
            } catch (Exception e) {
                Logger.warn(TAG, "Could not clear " + transport.id() + " session: " + e.getMessage());
            }
        }
    }

    void close() {
        for (PlaybackTransport transport : snapshot()) {
            try {
                transport.close();
            } catch (Exception e) {
                Logger.warn(TAG, "Could not close " + transport.id() + ": " + e.getMessage());
            }
        }
    }
}
