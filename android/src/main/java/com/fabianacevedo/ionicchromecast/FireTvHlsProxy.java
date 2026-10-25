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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Serves public HLS to Fire TV with media types that its built-in receiver can recognize. */
final class FireTvHlsProxy {
    private static final String TAG = "IonicChromecast-FireTV-HLS";
    private static final Pattern URI_ATTRIBUTE = Pattern.compile("URI=\"([^\"]+)\"");
    private static final int MAX_PLAYLIST_BYTES = 2 * 1024 * 1024;
    private final Network wifiNetwork;
    private final URL source;
    private final ServerSocket server;
    private final String rootPath;
    private final ConcurrentHashMap<String, URL> resources = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicInteger activeClients = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger segments = new AtomicInteger();
    private final AtomicLong mediaBytes = new AtomicLong();
    private volatile String lastResult = "none";
    private volatile boolean closed;

    static FireTvHlsProxy openIfNeeded(Context context, URL source) throws IOException {
        ConnectivityManager manager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network wifi = findWifiNetwork(manager);
        if (wifi == null) throw new IOException("No Wi-Fi network available for Fire TV HLS");

        HttpURLConnection probe = null;
        try {
            probe = openConnection(wifi, source, "HEAD", null);
            int status = probe.getResponseCode();
            if (status != 200) throw new IOException("HLS origin returned HTTP " + status);
            String type = probe.getContentType();
            if (type != null && type.toLowerCase(Locale.US).contains("mpegurl")) return null;
            Logger.info(TAG, "HLS origin type=" + type + "; using Fire TV HLS relay");
        } finally {
            if (probe != null) probe.disconnect();
        }

        LinkProperties properties = manager.getLinkProperties(wifi);
        if (properties != null) {
            for (LinkAddress address : properties.getLinkAddresses()) {
                InetAddress ip = address.getAddress();
                if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) {
                    return new FireTvHlsProxy(wifi, ip, source);
                }
            }
        }
        throw new IOException("No IPv4 address on the phone's Wi-Fi network");
    }

    private FireTvHlsProxy(Network wifiNetwork, InetAddress listenAddress, URL source) throws IOException {
        this.wifiNetwork = wifiNetwork;
        this.source = source;
        this.rootPath = "/ionic-chromecast/fire-tv-hls/" + UUID.randomUUID() + "/";
        this.server = new ServerSocket(0, 16, listenAddress);
        resources.put("0.m3u8", source);
        Thread listener = new Thread(this::acceptClients, "IonicChromecast-FireTV-HLS");
        listener.setDaemon(true);
        listener.start();
    }

    String getMediaUrl() {
        return localUrl("0.m3u8");
    }

    String getDiagnostic() {
        return "requests=" + requests.get() + ", segments=" + segments.get()
            + ", mediaBytes=" + mediaBytes.get() + ", last=" + lastResult;
    }

    boolean hasServedMedia() {
        return mediaBytes.get() > 0;
    }

    void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {}
    }

    private String localUrl(String key) {
        return "http://" + server.getInetAddress().getHostAddress() + ":"
            + server.getLocalPort() + rootPath + key;
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
                }, "IonicChromecast-FireTV-HLS-Client");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (!closed) Logger.warn(TAG, "Fire TV HLS relay accept failed: " + e.getMessage());
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
            if (parts.length != 3 || !parts[1].startsWith(rootPath)) {
                sendError(output, 404, "Not Found");
                return;
            }
            String method = parts[0];
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                sendError(output, 405, "Method Not Allowed");
                return;
            }
            String key = parts[1].substring(rootPath.length()).split("\\?", 2)[0];
            URL resource = resources.get(key);
            if (resource == null) {
                sendError(output, 404, "Not Found");
                return;
            }
            String range = null;
            for (int i = 0; i < 64; i++) {
                String header = readLine(input);
                if (header == null || header.isEmpty()) break;
                int separator = header.indexOf(':');
                if (separator > 0 && "range".equalsIgnoreCase(header.substring(0, separator).trim())) {
                    String candidate = header.substring(separator + 1).trim();
                    if (candidate.length() < 128 && candidate.startsWith("bytes=")) range = candidate;
                }
            }
            requests.incrementAndGet();
            if (key.endsWith(".m3u8")) {
                servePlaylist(output, method, resource);
            } else {
                serveMedia(output, method, resource, range, key);
            }
        } catch (IOException e) {
            lastResult = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (!closed) Logger.warn(TAG, "Fire TV HLS relay request failed: " + e.getMessage());
        }
    }

    private void servePlaylist(OutputStream output, String method, URL resource) throws IOException {
        HttpURLConnection upstream = null;
        try {
            upstream = openConnection(wifiNetwork, resource, "GET", null);
            int status = upstream.getResponseCode();
            lastResult = "playlist HTTP " + status;
            if (status != 200 || !sameOrigin(upstream.getURL())) {
                sendError(output, 502, "Bad Gateway");
                return;
            }
            byte[] original = readLimited(upstream.getInputStream(), MAX_PLAYLIST_BYTES);
            String text = new String(original, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
            if (!text.startsWith("#EXTM3U")) {
                lastResult = "invalid HLS playlist";
                sendError(output, 502, "Bad Gateway");
                return;
            }
            byte[] rewritten = rewritePlaylist(text, upstream.getURL()).getBytes(StandardCharsets.UTF_8);
            writeHeaders(output, 200, "application/x-mpegURL", rewritten.length, null);
            if ("GET".equals(method)) output.write(rewritten);
            lastResult = "playlist served";
        } finally {
            if (upstream != null) upstream.disconnect();
        }
    }

    private void serveMedia(OutputStream output, String method, URL resource, String range, String key)
        throws IOException {
        HttpURLConnection upstream = null;
        try {
            upstream = openConnection(wifiNetwork, resource, method, range);
            int status = upstream.getResponseCode();
            lastResult = "segment HTTP " + status;
            if ((status != 200 && status != 206) || !sameOrigin(upstream.getURL())) {
                sendError(output, 502, "Bad Gateway");
                return;
            }
            String type = key.endsWith(".ts") ? "video/MP2T" : upstream.getContentType();
            if (type == null || type.isEmpty()) type = "application/octet-stream";
            writeHeaders(output, status, type, upstream.getContentLengthLong(),
                upstream.getHeaderField("Content-Range"));
            if ("HEAD".equals(method)) return;

            try (InputStream media = upstream.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while (!closed && (count = media.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    mediaBytes.addAndGet(count);
                }
                if (!closed) {
                    segments.incrementAndGet();
                    lastResult = "segment served";
                }
            }
        } finally {
            if (upstream != null) upstream.disconnect();
        }
    }

    private String rewritePlaylist(String manifest, URL base) throws IOException {
        StringBuilder rewritten = new StringBuilder(manifest.length() + 128);
        int references = 0;
        for (String rawLine : manifest.split("\\n", -1)) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.startsWith("#")) {
                Matcher matcher = URI_ATTRIBUTE.matcher(line);
                StringBuffer updated = new StringBuffer();
                while (matcher.find()) {
                    if (++references > 4096) throw new IOException("HLS playlist has too many resources");
                    String replacement = "URI=\"" + register(base, matcher.group(1)) + "\"";
                    matcher.appendReplacement(updated, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(updated);
                rewritten.append(updated);
            } else if (!line.trim().isEmpty()) {
                if (++references > 4096) throw new IOException("HLS playlist has too many resources");
                rewritten.append(register(base, line.trim()));
            }
            rewritten.append('\n');
        }
        return rewritten.toString();
    }

    private String register(URL base, String reference) throws IOException {
        URL resource = new URL(base, reference.replace(" ", "%20"));
        if (!sameOrigin(resource)) throw new IOException("HLS resource uses another origin");
        String path = resource.getPath().toLowerCase(Locale.US);
        String extension = path.endsWith(".m3u8") ? ".m3u8" : path.endsWith(".ts") ? ".ts" : ".bin";
        int id = nextId.incrementAndGet();
        if (id > 8192) throw new IOException("HLS relay has too many resources");
        String key = id + extension;
        resources.put(key, resource);
        return localUrl(key);
    }

    private boolean sameOrigin(URL other) {
        return source.getProtocol().equalsIgnoreCase(other.getProtocol())
            && source.getHost().equalsIgnoreCase(other.getHost())
            && source.getPort() == other.getPort();
    }

    private static HttpURLConnection openConnection(Network wifi, URL url, String method, String range)
        throws IOException {
        HttpURLConnection connection = (HttpURLConnection) wifi.openConnection(url);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (range != null) connection.setRequestProperty("Range", range);
        return connection;
    }

    private static Network findWifiNetwork(ConnectivityManager manager) {
        if (manager == null) return null;
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

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IOException("HLS playlist is too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
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

    private static void writeHeaders(OutputStream output, int status, String type, long length,
        String contentRange) throws IOException {
        StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(status)
            .append(status == 206 ? " Partial Content" : " OK")
            .append("\r\nContent-Type: ").append(type)
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
