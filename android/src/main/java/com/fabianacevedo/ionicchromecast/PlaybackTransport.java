package com.fabianacevedo.ionicchromecast;

import java.util.List;

/** Native playback protocol exposed to the shared device chooser and session flow. */
interface PlaybackTransport {
    String id();

    String deviceLabel();

    String selectionMessage();

    List<PlaybackDevice> discover(int timeoutMs);

    String discoveryReport();

    void select(PlaybackDevice device);

    void clearSelection();

    boolean hasSession();

    String selectedDeviceName();

    boolean loadMedia(String url, String title, String subtitle, String imageUrl,
        String contentType, double duration);

    boolean endSession();

    String lastError();

    void close();
}
