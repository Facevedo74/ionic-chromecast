package com.fabianacevedo.ionicchromecast;

import java.util.ArrayList;
import java.util.List;

/** Adapts the existing DLNA manager without changing its playback behavior. */
final class DlnaPlaybackTransport implements PlaybackTransport {
    private final DlnaManager manager;

    DlnaPlaybackTransport(DlnaManager manager) {
        this.manager = manager;
    }

    @Override public String id() { return "dlna"; }

    @Override public String deviceLabel() { return "DLNA / UPnP"; }

    @Override public String selectionMessage() { return "DLNA renderer selected"; }

    @Override public List<PlaybackDevice> discover(int timeoutMs) {
        List<PlaybackDevice> found = new ArrayList<>();
        for (DlnaManager.Device device : manager.discover(timeoutMs)) {
            found.add(new PlaybackDevice(this, device.getName(), device));
        }
        return found;
    }

    @Override public String discoveryReport() { return manager.getLastDiscoveryReport(); }

    @Override public void select(PlaybackDevice device) {
        manager.selectDevice((DlnaManager.Device) device.handle);
    }

    @Override public void clearSelection() { manager.clearSelectedDevice(); }

    @Override public boolean hasSession() { return manager.hasSelectedDevice(); }

    @Override public String selectedDeviceName() { return manager.getSelectedDeviceName(); }

    @Override public boolean loadMedia(String url, String title, String subtitle, String imageUrl,
        String contentType, double duration) {
        return manager.loadMedia(url, title, subtitle, imageUrl, contentType, duration);
    }

    @Override public boolean endSession() {
        manager.stopAndClear();
        return true;
    }

    @Override public String lastError() { return manager.getLastError(); }

    @Override public void close() {}
}
