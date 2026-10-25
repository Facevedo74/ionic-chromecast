package com.fabianacevedo.ionicchromecast;

/** A discovered device and its protocol-specific handle, kept inside the Android plugin. */
final class PlaybackDevice {
    final PlaybackTransport transport;
    final String name;
    final Object handle;

    PlaybackDevice(PlaybackTransport transport, String name, Object handle) {
        this.transport = transport;
        this.name = name;
        this.handle = handle;
    }

    String transportId() {
        return transport.id();
    }

    String displayName() {
        return name + " (" + transport.deviceLabel() + ")";
    }
}
