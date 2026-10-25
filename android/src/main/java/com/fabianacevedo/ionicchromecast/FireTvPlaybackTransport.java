package com.fabianacevedo.ionicchromecast;

import java.util.ArrayList;
import java.util.List;

/** Adapts the existing Amazon Fling manager without changing its playback behavior. */
final class FireTvPlaybackTransport implements PlaybackTransport {
    private final FireTvManager manager;

    FireTvPlaybackTransport(FireTvManager manager) {
        this.manager = manager;
    }

    @Override public String id() { return "fire-tv"; }

    @Override public String deviceLabel() { return "Amazon Fire TV"; }

    @Override public String selectionMessage() { return "Fire TV selected"; }

    @Override public List<PlaybackDevice> discover(int timeoutMs) {
        List<PlaybackDevice> found = new ArrayList<>();
        for (FireTvManager.Device device : manager.discover(timeoutMs)) {
            found.add(new PlaybackDevice(this, device.getName(), device));
        }
        return found;
    }

    @Override public String discoveryReport() { return manager.getDiscoveryReport(); }

    @Override public void select(PlaybackDevice device) {
        manager.selectDevice((FireTvManager.Device) device.handle);
    }

    @Override public void clearSelection() { manager.clearSelectedDevice(); }

    @Override public boolean hasSession() { return manager.hasSelectedDevice(); }

    @Override public String selectedDeviceName() { return manager.getSelectedDeviceName(); }

    @Override public boolean loadMedia(String url, String title, String subtitle, String imageUrl,
        String contentType, double duration) {
        return manager.loadMedia(url, title, subtitle, imageUrl, contentType);
    }

    @Override public boolean endSession() { return manager.stopAndClear(); }

    @Override public String lastError() { return manager.getLastError(); }

    @Override public void close() { manager.close(); }
}
