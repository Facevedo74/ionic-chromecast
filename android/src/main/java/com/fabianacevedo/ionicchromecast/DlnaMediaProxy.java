package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.Logger;

import android.net.Network;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Relays public HTTPS MP4 or unencrypted MPEG-TS HLS over local HTTP.
 * Only the selected renderer may connect.
 */
final class DlnaMediaProxy {

    private static final String TAG = "IonicChromecast-DLNA";
    private final Network wifiNetwork;
    private final InetAddress rendererAddress;
    private final URL source;
    private final boolean hlsTransportStream;
    private final double durationSeconds;
    private final List<URL> hlsSegments;
    private final ServerSocket server;
    private final String path;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger getRequests = new AtomicInteger();
    private final AtomicInteger completedGets = new AtomicInteger();
    private final AtomicInteger failedRequests = new AtomicInteger();
    private final AtomicLong bytesSent = new AtomicLong();
    private volatile String lastRequestResult = "none";
    private volatile String lastRequestHeaders = "none";
    private volatile String hlsOriginDebug = "none";
    private volatile boolean closed;

    DlnaMediaProxy(Network wifiNetwork, InetAddress listenAddress, InetAddress rendererAddress, String sourceUrl)
        throws IOException {
        this(wifiNetwork, listenAddress, rendererAddress, sourceUrl, false);
    }

    DlnaMediaProxy(
        Network wifiNetwork,
        InetAddress listenAddress,
        InetAddress rendererAddress,
        String sourceUrl,
        boolean hlsTransportStream
    ) throws IOException {
        this(wifiNetwork, listenAddress, rendererAddress, sourceUrl, hlsTransportStream, 0);
    }

    DlnaMediaProxy(
        Network wifiNetwork,
        InetAddress listenAddress,
        InetAddress rendererAddress,
        String sourceUrl,
        boolean hlsTransportStream,
        double durationSeconds
    ) throws IOException {
        this.wifiNetwork = wifiNetwork;
        this.rendererAddress = rendererAddress;
        source = new URL(sourceUrl);
        if (!hlsTransportStream && !"https".equalsIgnoreCase(source.getProtocol())) {
            throw new IOException("DLNA relay only accepts HTTPS media");
        }
        this.hlsTransportStream = hlsTransportStream;
        this.durationSeconds = Double.isFinite(durationSeconds) && durationSeconds > 0 ? durationSeconds : 0;
        hlsSegments = hlsTransportStream ? readHlsSegments() : null;
        path = "/ionic-chromecast/" + UUID.randomUUID().toString()
            + (hlsTransportStream ? "/video.mpg" : "/video.mp4");
        server = new ServerSocket(0, 16, listenAddress);
        Thread listener = new Thread(this::acceptClients, "IonicChromecast-DLNA-Relay");
        listener.setDaemon(true);
        listener.start();
    }

    String getMediaUrl() {
        return "http://" + server.getInetAddress().getHostAddress() + ":" + server.getLocalPort() + path;
    }

    String getDiagnostic() {
        return "requests=" + requests.get() + ", GETs=" + getRequests.get()
            + ", complete=" + completedGets.get() + ", failed=" + failedRequests.get()
            + ", bytes=" + bytesSent.get() + ", last=" + lastRequestResult
            + (hlsTransportStream ? ", request=" + lastRequestHeaders + ", origin=" + hlsOriginDebug : "");
    }

    boolean hasCompletedGet() {
        return completedGets.get() > 0;
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
                Thread worker = new Thread(() -> serve(client), "IonicChromecast-DLNA-Relay-Client");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (!closed) Logger.warn(TAG, "DLNA relay accept failed: " + e.getMessage());
            }
        }
    }

    private void serve(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(15000);
            OutputStream output = socket.getOutputStream();
            if (!rendererAddress.equals(socket.getInetAddress())) {
                sendError(output, 403, "Forbidden");
                return;
            }

            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
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
            requests.incrementAndGet();
            if ("GET".equals(method)) getRequests.incrementAndGet();
            lastRequestResult = method + " received";

            String range = null;
            String userAgent = null;
            for (int i = 0; i < 64; i++) {
                String header = readLine(input);
                if (header == null || header.isEmpty()) break;
                int separator = header.indexOf(':');
                if (separator > 0 && "range".equalsIgnoreCase(header.substring(0, separator).trim())) {
                    String candidate = header.substring(separator + 1).trim();
                    if (candidate.length() < 128 && candidate.startsWith("bytes=")) range = candidate;
                } else if (separator > 0 && "user-agent".equalsIgnoreCase(header.substring(0, separator).trim())) {
                    String candidate = header.substring(separator + 1).trim();
                    userAgent = candidate.substring(0, Math.min(candidate.length(), 80));
                }
            }
            lastRequestHeaders = method + " range=" + range + " agent=" + userAgent;

            if (hlsTransportStream) {
                serveHls(method, output);
                return;
            }

            HttpURLConnection upstream = null;
            try {
                upstream = (HttpURLConnection) (wifiNetwork != null
                    ? wifiNetwork.openConnection(source)
                    : source.openConnection());
                upstream.setConnectTimeout(10000);
                upstream.setReadTimeout(15000);
                upstream.setInstanceFollowRedirects(true);
                upstream.setRequestMethod(method);
                upstream.setRequestProperty("Accept-Encoding", "identity");
                if (range != null) upstream.setRequestProperty("Range", range);

                int status = upstream.getResponseCode();
                lastRequestResult = method + " origin HTTP " + status;
                if (status != 200 && status != 206) {
                    Logger.warn(TAG, "DLNA relay origin returned HTTP " + status);
                    sendError(output, 502, "Bad Gateway");
                    return;
                }
                String type = upstream.getContentType();
                if (type == null || type.isEmpty()) type = "video/mp4";
                StringBuilder headers = new StringBuilder("HTTP/1.1 ")
                    .append(status).append(status == 206 ? " Partial Content" : " OK")
                    .append("\r\nContent-Type: ").append(type)
                    .append("\r\nAccept-Ranges: bytes\r\nConnection: close\r\n");
                long length = upstream.getContentLengthLong();
                if (length >= 0) headers.append("Content-Length: ").append(length).append("\r\n");
                String contentRange = upstream.getHeaderField("Content-Range");
                if (contentRange != null) headers.append("Content-Range: ").append(contentRange).append("\r\n");
                headers.append("\r\n");
                output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
                if ("HEAD".equals(method)) return;

                try (InputStream media = upstream.getInputStream()) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while (!closed && (count = media.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        bytesSent.addAndGet(count);
                    }
                    if (!closed) completedGets.incrementAndGet();
                }
            } finally {
                if (upstream != null) upstream.disconnect();
            }
        } catch (IOException e) {
            failedRequests.incrementAndGet();
            lastRequestResult = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (!closed) Logger.warn(TAG, "DLNA relay request failed: " + e.getMessage());
        }
    }

    private List<URL> readHlsSegments() throws IOException {
        return readHlsSegments(source, 0);
    }

    private List<URL> readHlsSegments(URL playlistUrl, int nestingDepth) throws IOException {
        if (nestingDepth > 3) {
            throw new IOException("HLS master playlist nesting is too deep");
        }

        HttpURLConnection playlist = null;
        try {
            playlist = (HttpURLConnection) (wifiNetwork != null
                ? wifiNetwork.openConnection(playlistUrl)
                : playlistUrl.openConnection());
            playlist.setConnectTimeout(10000);
            playlist.setReadTimeout(15000);
            playlist.setInstanceFollowRedirects(true);
            playlist.setRequestProperty("Accept-Encoding", "identity");
            if (playlist.getResponseCode() != 200) {
                throw new IOException("HLS playlist returned HTTP " + playlist.getResponseCode());
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = playlist.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 2 * 1024 * 1024) {
                        throw new IOException("HLS playlist is too large");
                    }
                    bytes.write(buffer, 0, count);
                }
            }

            URL effectivePlaylistUrl = playlist.getURL();
            String manifest = bytes.toString(StandardCharsets.UTF_8.name()).replaceFirst("^\\uFEFF", "");
            if (!manifest.startsWith("#EXTM3U")) throw new IOException("Invalid HLS playlist");

            List<URL> segments = new ArrayList<>();
            URL highestBandwidthVariant = null;
            long highestBandwidth = -1;
            long pendingBandwidth = -1;
            boolean ended = false;
            for (String rawLine : manifest.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                if (line.equals("#EXT-X-ENDLIST")) ended = true;
                if (line.startsWith("#EXT-X-KEY:") && !line.contains("METHOD=NONE")) {
                    throw new IOException("Encrypted HLS is not supported by the DLNA relay");
                }
                if (line.startsWith("#EXT-X-MAP:") || line.startsWith("#EXT-X-BYTERANGE:")) {
                    throw new IOException("HLS initialization maps and byte-range segments are not supported by the DLNA relay");
                }
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    pendingBandwidth = parseBandwidth(line);
                    continue;
                }
                if (line.startsWith("#")) continue;

                URL resource = new URL(effectivePlaylistUrl, line);
                if (pendingBandwidth >= 0) {
                    if (!isSameOrigin(source, resource)) {
                        throw new IOException("HLS variants must use the same origin as the master playlist");
                    }
                    if (pendingBandwidth > highestBandwidth) {
                        highestBandwidth = pendingBandwidth;
                        highestBandwidthVariant = resource;
                    }
                    pendingBandwidth = -1;
                    continue;
                }

                URL segment = resource;
                if (!isSameOrigin(source, segment)
                    || !segment.getPath().toLowerCase(Locale.US).endsWith(".ts")) {
                    throw new IOException("HLS relay requires same-host MPEG-TS segments");
                }
                segments.add(segment);
                if (segments.size() > 4096) throw new IOException("HLS playlist has too many segments");
            }

            if (highestBandwidthVariant != null) {
                return readHlsSegments(highestBandwidthVariant, nestingDepth + 1);
            }
            if (!ended || segments.isEmpty()) {
                throw new IOException("HLS relay requires a complete MPEG-TS VOD playlist");
            }
            Logger.info(TAG, "HLS relay prepared " + segments.size() + " MPEG-TS segments");
            return segments;
        } finally {
            if (playlist != null) playlist.disconnect();
        }
    }

    private long parseBandwidth(String streamInfo) throws IOException {
        for (String attribute : streamInfo.substring(streamInfo.indexOf(':') + 1).split(",")) {
            String[] keyValue = attribute.trim().split("=", 2);
            if (keyValue.length == 2 && "BANDWIDTH".equals(keyValue[0])) {
                try {
                    return Long.parseLong(keyValue[1]);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid HLS variant bandwidth", e);
                }
            }
        }
        throw new IOException("HLS variant is missing its BANDWIDTH attribute");
    }

    private boolean isSameOrigin(URL expected, URL candidate) {
        return expected.getProtocol().equalsIgnoreCase(candidate.getProtocol())
            && expected.getHost().equalsIgnoreCase(candidate.getHost())
            && expected.getPort() == candidate.getPort();
    }

    private void serveHls(String method, OutputStream output) throws IOException {
        // Compatibility hints for renderers that do not use the DIDL resource duration.
        // They do not advertise seek support: this relay still serves a sequential stream.
        String durationHeaders = durationSeconds > 0
            ? "Content-Duration: " + Double.toString(durationSeconds) + "\r\n"
                + "X-Content-Duration: " + Double.toString(durationSeconds) + "\r\n"
            : "";
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: video/mpeg\r\nConnection: close\r\n"
            + durationHeaders + "Transfer-Encoding: chunked\r\n\r\n";
        Logger.info(TAG, "HLS relay " + method + ": durationSeconds=" + durationSeconds
            + ", transfer=chunked, seekSupported=false");
        output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        if ("HEAD".equals(method)) return;

        byte[] buffer = new byte[64 * 1024];
        byte[] crlf = new byte[] { '\r', '\n' };
        for (int i = 0; i < hlsSegments.size() && !closed; i++) {
            HttpURLConnection upstream = null;
            long segmentStarted = System.currentTimeMillis();
            try {
                upstream = (HttpURLConnection) (wifiNetwork != null
                    ? wifiNetwork.openConnection(hlsSegments.get(i))
                    : hlsSegments.get(i).openConnection());
                upstream.setConnectTimeout(10000);
                upstream.setReadTimeout(15000);
                upstream.setInstanceFollowRedirects(true);
                upstream.setRequestProperty("Accept-Encoding", "identity");
                int status = upstream.getResponseCode();
                lastRequestResult = "HLS segment " + (i + 1) + "/" + hlsSegments.size() + " HTTP " + status;
                if (i == 0) {
                    hlsOriginDebug = upstream.getURL().toString() + " type=" + upstream.getContentType()
                        + " length=" + upstream.getContentLengthLong()
                        + " headersMs=" + (System.currentTimeMillis() - segmentStarted);
                }
                if (status != 200) throw new IOException(lastRequestResult);
                try (InputStream media = upstream.getInputStream()) {
                    int count;
                    while (!closed && (count = media.read(buffer)) != -1) {
                        if (i == 0 && !hlsOriginDebug.contains(" first=")) {
                            StringBuilder first = new StringBuilder(" first=");
                            for (int j = 0; j < Math.min(count, 16); j++) {
                                first.append(String.format(Locale.US, "%02x", buffer[j] & 0xff));
                            }
                            hlsOriginDebug += first;
                            hlsOriginDebug += " firstReadMs=" + (System.currentTimeMillis() - segmentStarted);
                        }
                        output.write(Integer.toHexString(count).getBytes(StandardCharsets.US_ASCII));
                        output.write(crlf);
                        output.write(buffer, 0, count);
                        output.write(crlf);
                        bytesSent.addAndGet(count);
                        if (i == 0 && !hlsOriginDebug.contains(" firstWriteMs=")) {
                            hlsOriginDebug += " firstWriteMs=" + (System.currentTimeMillis() - segmentStarted);
                        }
                    }
                }
            } finally {
                if (upstream != null) upstream.disconnect();
            }
        }
        if (!closed) {
            output.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            completedGets.incrementAndGet();
            lastRequestResult = "HLS stream complete";
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') break;
            if (line.size() >= 8192) throw new IOException("DLNA relay request line is too large");
            if (value != '\r') line.write(value);
        }
        if (value == -1 && line.size() == 0) return null;
        return line.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void sendError(OutputStream output, int status, String reason) throws IOException {
        String response = "HTTP/1.1 " + status + " " + reason
            + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
    }
}
