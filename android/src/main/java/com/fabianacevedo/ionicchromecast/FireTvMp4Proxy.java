package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.getcapacitor.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Relays one MP4 to Fire TV so playback can be observed when Fling status calls time out. */
final class FireTvMp4Proxy {
    private static final String TAG = "IonicChromecast-FireTV-MP4";
    private final Network wifiNetwork;
    private final URL source;
    private final ServerSocket server;
    private final String path;
    private final AtomicInteger activeClients = new AtomicInteger();
    private final AtomicInteger getRequests = new AtomicInteger();
    private final AtomicLong mediaBytes = new AtomicLong();
    private volatile String lastResult = "none";
    private volatile boolean closed;

    static FireTvMp4Proxy open(Context context, URL source) throws IOException {
        ConnectivityManager manager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) throw new IOException("No network manager available for Fire TV MP4");
        Network wifi = findWifiNetwork(manager);
        if (wifi == null) throw new IOException("No Wi-Fi network available for Fire TV MP4");
        LinkProperties properties = manager.getLinkProperties(wifi);
        if (properties != null) {
            for (LinkAddress address : properties.getLinkAddresses()) {
                InetAddress ip = address.getAddress();
                if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) {
                    return new FireTvMp4Proxy(wifi, ip, source);
                }
            }
        }
        throw new IOException("No IPv4 address on the phone's Wi-Fi network");
    }

    private FireTvMp4Proxy(Network wifiNetwork, InetAddress listenAddress, URL source)
        throws IOException {
        this.wifiNetwork = wifiNetwork;
        this.source = source;
        path = "/ionic-chromecast/fire-tv-mp4/" + UUID.randomUUID() + "/video.mp4";
        server = new ServerSocket(0, 16, listenAddress);
        Thread listener = new Thread(this::acceptClients, "IonicChromecast-FireTV-MP4");
        listener.setDaemon(true);
        listener.start();
    }

    String getMediaUrl() {
        return "http://" + server.getInetAddress().getHostAddress() + ":"
            + server.getLocalPort() + path;
    }

    boolean hasServedMedia() {
        return mediaBytes.get() > 0;
    }

    String getDiagnostic() {
        return "GETs=" + getRequests.get() + ", mediaBytes=" + mediaBytes.get()
            + ", last=" + lastResult;
    }

    void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {}
    }

    private void acceptClients() {
        while (!closed) {
            try {
                Socket client = server.accept();
                if (activeClients.incrementAndGet() > 16) {
                    activeClients.decrementAndGet();
                    client.close();
                    continue;
                }
                Thread worker = new Thread(() -> {
                    try {
                        serve(client);
                    } finally {
                        activeClients.decrementAndGet();
                    }
                }, "IonicChromecast-FireTV-MP4-Client");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (!closed) Logger.warn(TAG, "MP4 relay accept failed: " + e.getMessage());
            }
        }
    }

    private void serve(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(20000);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            String request = readLine(input);
            if (request == null) return;
            String[] parts = request.split(" ", 3);
            if (parts.length != 3 || !path.equals(parts[1].split("\\?", 2)[0])) {
                sendError(output, 404, "Not Found");
                return;
            }
            String method = parts[0];
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                sendError(output, 405, "Method Not Allowed");
                return;
            }
            String range = null;
            for (int i = 0; i < 64; i++) {
                String header = readLine(input);
                if (header == null || header.isEmpty()) break;
                int separator = header.indexOf(':');
                if (separator > 0 && "range".equalsIgnoreCase(header.substring(0, separator).trim())) {
                    String candidate = header.substring(separator + 1).trim();
                    if (candidate.length() < 128
                        && candidate.matches("bytes=(?:[0-9]+-[0-9]*|-[0-9]+)")) range = candidate;
                }
            }
            relay(method, range, output);
        } catch (IOException e) {
            lastResult = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (!closed) Logger.warn(TAG, "MP4 relay request failed: " + e.getMessage());
        }
    }

    private void relay(String method, String range, OutputStream output) throws IOException {
        HttpURLConnection upstream = null;
        try {
            upstream = (HttpURLConnection) wifiNetwork.openConnection(source);
            upstream.setConnectTimeout(10000);
            upstream.setReadTimeout(20000);
            upstream.setInstanceFollowRedirects(true);
            upstream.setRequestMethod(method);
            upstream.setRequestProperty("Accept-Encoding", "identity");
            if (range != null) upstream.setRequestProperty("Range", range);
            int status = upstream.getResponseCode();
            lastResult = method + " origin HTTP " + status;
            if (status != 200 && status != 206) {
                sendError(output, 502, "Bad Gateway");
                return;
            }
            String contentRange = upstream.getHeaderField("Content-Range");
            if (contentRange != null && !contentRange.matches("bytes [0-9]+-[0-9]+/(?:[0-9]+|\\*)")) {
                contentRange = null;
            }
            writeHeaders(output, status, upstream.getContentLengthLong(), contentRange);
            if ("HEAD".equals(method)) return;
            getRequests.incrementAndGet();
            try (InputStream media = upstream.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while (!closed && (count = media.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    mediaBytes.addAndGet(count);
                }
                if (!closed) lastResult = "MP4 served";
            }
        } finally {
            if (upstream != null) upstream.disconnect();
        }
    }

    private static Network findWifiNetwork(ConnectivityManager manager) {
        Network active = manager.getActiveNetwork();
        if (active != null) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(active);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return active;
        }
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return network;
        }
        return null;
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1 && value != '\n') {
            if (bytes.size() >= 8192) throw new IOException("HTTP header line is too long");
            if (value != '\r') bytes.write(value);
        }
        if (value == -1 && bytes.size() == 0) return null;
        return bytes.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void writeHeaders(OutputStream output, int status, long length,
        String contentRange) throws IOException {
        StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(status)
            .append(status == 206 ? " Partial Content" : " OK")
            .append("\r\nContent-Type: video/mp4")
            .append("\r\nAccept-Ranges: bytes\r\nConnection: close\r\n");
        if (length >= 0) headers.append("Content-Length: ").append(length).append("\r\n");
        if (contentRange != null) headers.append("Content-Range: ").append(contentRange).append("\r\n");
        headers.append("\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void sendError(OutputStream output, int status, String reason) throws IOException {
        String response = "HTTP/1.1 " + status + " " + reason
            + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
    }
}
