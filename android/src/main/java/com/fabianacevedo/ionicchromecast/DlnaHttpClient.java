package com.fabianacevedo.ionicchromecast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small HTTP/1.1 client used only for local UPnP endpoints discovered through
 * SSDP. Raw sockets avoid enabling cleartext HTTP globally for the host app.
 */
final class DlnaHttpClient {

    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    static final class Response {
        final int status;
        final Map<String, String> headers;
        final byte[] body;

        Response(int status, Map<String, String> headers, byte[] body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        String bodyAsText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private DlnaHttpClient() {}

    static Response execute(
        URL url,
        String method,
        Map<String, String> requestHeaders,
        byte[] requestBody,
        int connectTimeoutMs,
        int readTimeoutMs
    ) throws Exception {
        if (!"http".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("DlnaHttpClient only supports local HTTP endpoints");
        }

        int port = url.getPort() >= 0 ? url.getPort() : 80;
        String path = url.getFile();
        if (path == null || path.isEmpty()) path = "/";
        byte[] body = requestBody != null ? requestBody : new byte[0];

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(url.getHost(), port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);

            String hostHeader = url.getHost().contains(":") ? "[" + url.getHost() + "]" : url.getHost();
            if (port != 80) hostHeader += ":" + port;

            StringBuilder request = new StringBuilder();
            request.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(hostHeader).append("\r\n")
                .append("Connection: close\r\n");
            if (requestHeaders != null) {
                for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                    request.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
                }
            }
            if (body.length > 0) {
                request.append("Content-Length: ").append(body.length).append("\r\n");
            }
            request.append("\r\n");

            OutputStream output = socket.getOutputStream();
            output.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (body.length > 0) output.write(body);
            output.flush();

            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            String statusLine = readLine(input);
            if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                throw new EOFException("Invalid HTTP response from DLNA device");
            }
            String[] statusParts = statusLine.split(" ", 3);
            if (statusParts.length < 2) throw new EOFException("Missing HTTP status code");
            int status = Integer.parseInt(statusParts[1]);

            Map<String, String> responseHeaders = new LinkedHashMap<>();
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator <= 0) continue;
                responseHeaders.put(
                    line.substring(0, separator).trim().toLowerCase(Locale.US),
                    line.substring(separator + 1).trim()
                );
            }

            byte[] responseBody;
            String transferEncoding = responseHeaders.get("transfer-encoding");
            if (transferEncoding != null && transferEncoding.toLowerCase(Locale.US).contains("chunked")) {
                responseBody = readChunked(input);
            } else {
                int contentLength = parseContentLength(responseHeaders.get("content-length"));
                responseBody = contentLength >= 0 ? readFixed(input, contentLength) : readToEnd(input);
            }
            return new Response(status, responseHeaders, responseBody);
        }
    }

    private static String readLine(BufferedInputStream input) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            line.write(current);
            previous = current;
            if (line.size() > 32768) throw new IllegalStateException("HTTP header line is too large");
        }
        if (line.size() == 0) return null;
        return line.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static int parseContentLength(String value) {
        if (value == null) return -1;
        try {
            return Math.min(Integer.parseInt(value), MAX_BODY_BYTES);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static byte[] readFixed(BufferedInputStream input, int length) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(length, MAX_BODY_BYTES));
        byte[] buffer = new byte[4096];
        int remaining = Math.min(length, MAX_BODY_BYTES);
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) break;
            output.write(buffer, 0, read);
            remaining -= read;
        }
        return output.toByteArray();
    }

    private static byte[] readToEnd(BufferedInputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while (output.size() < MAX_BODY_BYTES && (read = input.read(buffer)) != -1) {
            output.write(buffer, 0, Math.min(read, MAX_BODY_BYTES - output.size()));
        }
        return output.toByteArray();
    }

    private static byte[] readChunked(BufferedInputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (output.size() < MAX_BODY_BYTES) {
            String sizeLine = readLine(input);
            if (sizeLine == null) break;
            int extension = sizeLine.indexOf(';');
            String sizeValue = (extension >= 0 ? sizeLine.substring(0, extension) : sizeLine).trim();
            int chunkSize = Integer.parseInt(sizeValue, 16);
            if (chunkSize == 0) {
                while ((sizeLine = readLine(input)) != null && !sizeLine.isEmpty()) {}
                break;
            }
            byte[] chunk = readFixed(input, Math.min(chunkSize, MAX_BODY_BYTES - output.size()));
            output.write(chunk);
            int unread = chunkSize - chunk.length;
            while (unread-- > 0 && input.read() != -1) {}
            readLine(input);
        }
        return output.toByteArray();
    }
}
