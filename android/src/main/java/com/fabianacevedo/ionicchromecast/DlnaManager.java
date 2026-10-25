package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;

import com.getcapacitor.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Minimal UPnP AV control point for discovering MediaRenderer devices and
 * starting public HTTP/HTTPS media through AVTransport.
 */
public class DlnaManager {

    private static final String TAG = "IonicChromecast-DLNA";
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String[] SEARCH_TARGETS = new String[] {
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:device:MediaRenderer:2",
        "urn:schemas-upnp-org:service:AVTransport:1",
        "urn:schemas-upnp-org:service:AVTransport:2",
        "urn:samsung.com:device:RemoteControlReceiver:1",
        "upnp:rootdevice",
        "ssdp:all"
    };
    private static final String DLNA_FLAGS = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
    private static final String USER_AGENT = "Android UPnP/1.1 ionic-chromecast/1.0";

    public static final class Device {
        private final String id;
        private final String name;
        private final String location;
        private final String controlUrl;
        private final String serviceType;
        private final boolean samsung;

        Device(String id, String name, String location, String controlUrl, String serviceType, boolean samsung) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.controlUrl = controlUrl;
            this.serviceType = serviceType;
            this.samsung = samsung;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getLocation() {
            return location;
        }

        boolean isSamsung() {
            return samsung;
        }
    }

    private final Context appContext;
    private volatile Device selectedDevice;
    private volatile String lastError;
    private volatile String lastDiscoveryReport = "DLNA discovery has not run";
    private volatile DlnaMediaProxy mediaProxy;

    public DlnaManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastDiscoveryReport() {
        return lastDiscoveryReport;
    }

    public boolean hasSelectedDevice() {
        return selectedDevice != null;
    }

    public String getSelectedDeviceName() {
        Device device = selectedDevice;
        return device != null ? device.getName() : null;
    }

    public Map<String, String> getPlaybackDiagnostics() {
        Map<String, String> result = new LinkedHashMap<>();
        Device device = selectedDevice;
        if (device == null) return result;
        result.put("deviceName", device.name);
        result.put("location", device.location);
        result.put("controlUrl", device.controlUrl);
        result.put("serviceType", device.serviceType);
        DlnaMediaProxy proxy = mediaProxy;
        result.put("relay", proxy != null ? proxy.getDiagnostic() : "none");
        Document position = querySoapDocument(device, "GetPositionInfo");
        if (position != null) {
            for (String field : new String[] { "TrackDuration", "RelTime", "TrackURI", "TrackMetaData" }) {
                result.put(field, firstElementText(position, field));
            }
        }
        Document transport = querySoapDocument(device, "GetTransportInfo");
        if (transport != null) {
            result.put("CurrentTransportState", firstElementText(transport, "CurrentTransportState"));
            result.put("CurrentTransportStatus", firstElementText(transport, "CurrentTransportStatus"));
        }
        return result;
    }

    public void selectDevice(Device device) {
        closeMediaProxy();
        selectedDevice = device;
        lastError = null;
        if (device != null) {
            Logger.info(TAG, "Selected DLNA renderer: " + device.getName());
        }
    }

    public void clearSelectedDevice() {
        closeMediaProxy();
        selectedDevice = null;
    }

    public List<Device> discover(int timeoutMs) {
        lastError = null;
        Map<String, String> locations = new LinkedHashMap<>();
        WifiManager.MulticastLock multicastLock = null;
        MulticastSocket socket = null;
        NetworkInterface networkInterface = null;
        int responses = 0;
        String interfaceName = "unknown";
        String networkDescription = "default network";
        int probes = 0;

        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                multicastLock = wifiManager.createMulticastLock(TAG);
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }

            ConnectivityManager connectivityManager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network wifiNetwork = findWifiNetwork(connectivityManager);
            LinkProperties linkProperties = wifiNetwork != null && connectivityManager != null
                ? connectivityManager.getLinkProperties(wifiNetwork)
                : null;
            if (linkProperties != null && linkProperties.getInterfaceName() != null) {
                interfaceName = linkProperties.getInterfaceName();
            }
            if (wifiNetwork != null) networkDescription = "bound to Wi-Fi";

            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            try {
                socket.bind(new InetSocketAddress(SSDP_PORT));
            } catch (Exception portUnavailable) {
                Logger.warn(TAG, "Could not bind SSDP port 1900; using an ephemeral source port: " + portUnavailable.getMessage());
                socket.close();
                socket = new MulticastSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(0));
            }
            socket.setBroadcast(true);
            if (wifiNetwork != null) wifiNetwork.bindSocket(socket);
            networkInterface = findNetworkInterface(interfaceName, linkProperties);
            if (networkInterface != null) {
                socket.setNetworkInterface(networkInterface);
                interfaceName = networkInterface.getName();
            }
            try {
                InetAddress multicastGroup = InetAddress.getByName(SSDP_ADDRESS);
                if (networkInterface != null) {
                    socket.joinGroup(new InetSocketAddress(multicastGroup, SSDP_PORT), networkInterface);
                } else {
                    socket.joinGroup(multicastGroup);
                }
                Logger.info(TAG, "Listening for SSDP announcements on " + interfaceName + ":" + SSDP_PORT);
            } catch (Exception e) {
                Logger.warn(TAG, "Could not join SSDP multicast group: " + e.getMessage());
            }

            // Try common SSDP search targets because TV implementations vary.
            for (String target : SEARCH_TARGETS) {
                sendSearch(socket, InetAddress.getByName(SSDP_ADDRESS), target);
                probes++;
            }

            // Fall back to subnet broadcast and bounded unicast probes for routers
            // that isolate multicast from wireless clients.
            InetAddress broadcastAddress = getSubnetBroadcast(wifiManager);
            if (broadcastAddress != null) {
                sendSearch(socket, broadcastAddress, "ssdp:all");
                probes++;
            }
            for (InetAddress host : getLocalSubnetHosts(wifiManager)) {
                sendSearch(socket, host, "ssdp:all");
                probes++;
            }

            long deadline = System.currentTimeMillis() + Math.max(3500, timeoutMs);
            byte[] buffer = new byte[8192];
            while (System.currentTimeMillis() < deadline) {
                int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
                socket.setSoTimeout(Math.min(remaining, 400));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    String response = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    Map<String, String> headers = parseHeaders(response);
                    String location = headers.get("location");
                    String statusLine = response.split("\\r?\\n", 2)[0].trim();
                    if (statusLine.startsWith("HTTP/1.1 200") || statusLine.startsWith("HTTP/1.0 200")) {
                        responses++;
                        Logger.info(TAG, "SSDP response from " + packet.getAddress().getHostAddress()
                            + " ST=" + headers.get("st") + " USN=" + headers.get("usn")
                            + " LOCATION=" + location);
                    }
                    if (location != null && !location.isEmpty()) {
                        locations.put(location, headers.get("usn"));
                    }
                } catch (SocketTimeoutException ignored) {
                    // Keep receiving until the discovery deadline.
                }
            }
        } catch (Exception e) {
            lastError = "DLNA discovery failed: " + e.getMessage();
            Logger.error(TAG, lastError, e);
        } finally {
            if (socket != null) {
                socket.close();
            }
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        }

        Map<String, Device> devices = new LinkedHashMap<>();
        List<String> descriptionDetails = new ArrayList<>();
        for (Map.Entry<String, String> entry : locations.entrySet()) {
            try {
                Device device = readDeviceDescription(entry.getKey(), entry.getValue(), descriptionDetails);
                if (device != null) {
                    devices.put(device.getId(), device);
                }
            } catch (Exception e) {
                Logger.warn(TAG, "Ignoring invalid UPnP description at " + entry.getKey() + ": " + e.getMessage());
                descriptionDetails.add(entry.getKey() + " => " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        lastDiscoveryReport = "DLNA discovery: " + responses + " SSDP response(s), "
            + locations.size() + " description URL(s), " + devices.size()
            + " AVTransport renderer(s); interface=" + interfaceName + "; network="
            + networkDescription + "; probes=" + probes
            + (devices.isEmpty() ? "; endpoints=" + descriptionDetails : "")
            + (lastError != null ? "; error=" + lastError : "");
        Logger.info(TAG, lastDiscoveryReport);
        if (devices.isEmpty()) Logger.warn(TAG, "No compatible DLNA renderer found after all SSDP fallbacks");
        return new ArrayList<>(devices.values());
    }

    private Network findWifiNetwork(ConnectivityManager connectivityManager) {
        if (connectivityManager == null) return null;
        try {
            Network active = connectivityManager.getActiveNetwork();
            NetworkCapabilities activeCapabilities = active != null
                ? connectivityManager.getNetworkCapabilities(active)
                : null;
            if (activeCapabilities != null && activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return active;
            }
            for (Network network : connectivityManager.getAllNetworks()) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return network;
            }
        } catch (Exception e) {
            Logger.warn(TAG, "Could not find Wi-Fi network for SSDP: " + e.getMessage());
        }
        return null;
    }

    private NetworkInterface findNetworkInterface(String interfaceName, LinkProperties linkProperties) {
        try {
            if (interfaceName != null && !"unknown".equals(interfaceName)) {
                NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
                if (networkInterface != null) return networkInterface;
            }
            if (linkProperties != null) {
                for (LinkAddress address : linkProperties.getLinkAddresses()) {
                    NetworkInterface networkInterface = NetworkInterface.getByInetAddress(address.getAddress());
                    if (networkInterface != null) return networkInterface;
                }
            }
        } catch (Exception e) {
            Logger.warn(TAG, "Could not select Wi-Fi interface for SSDP: " + e.getMessage());
        }
        return null;
    }

    private InetAddress getSubnetBroadcast(WifiManager wifiManager) {
        try {
            if (wifiManager == null) return null;
            DhcpInfo dhcp = wifiManager.getDhcpInfo();
            if (dhcp == null || dhcp.ipAddress == 0 || dhcp.netmask == 0) return null;
            byte[] ip = littleEndianAddress(dhcp.ipAddress);
            byte[] mask = littleEndianAddress(dhcp.netmask);
            byte[] broadcast = new byte[4];
            for (int i = 0; i < 4; i++) {
                broadcast[i] = (byte) ((ip[i] & mask[i]) | (~mask[i] & 0xff));
            }
            return InetAddress.getByAddress(broadcast);
        } catch (Exception e) {
            Logger.warn(TAG, "Could not compute Wi-Fi broadcast address: " + e.getMessage());
            return null;
        }
    }

    private List<InetAddress> getLocalSubnetHosts(WifiManager wifiManager) {
        List<InetAddress> hosts = new ArrayList<>();
        try {
            if (wifiManager == null) return hosts;
            DhcpInfo dhcp = wifiManager.getDhcpInfo();
            if (dhcp == null || dhcp.ipAddress == 0 || dhcp.netmask == 0) return hosts;

            long ip = addressToLong(littleEndianAddress(dhcp.ipAddress));
            long mask = addressToLong(littleEndianAddress(dhcp.netmask));
            long network = ip & mask;
            long hostMask = 0xffffffffL & ~mask;
            long first = network + 1;
            long last = network + hostMask - 1;
            if (last < first) return hosts;

            // Keep scans bounded on unusually large networks while covering the
            // common 192.168.x.x /24 Wi-Fi subnet around the phone.
            if (last - first > 510) {
                first = ip & 0xffffff00L;
                last = first + 255;
                first++;
                last--;
            }
            for (long candidate = first; candidate <= last && hosts.size() < 512; candidate++) {
                if (candidate == ip) continue;
                hosts.add(longToAddress(candidate));
            }
        } catch (Exception e) {
            Logger.warn(TAG, "Could not enumerate Wi-Fi subnet: " + e.getMessage());
        }
        return hosts;
    }

    private byte[] littleEndianAddress(int value) {
        return new byte[] {
            (byte) (value & 0xff),
            (byte) ((value >> 8) & 0xff),
            (byte) ((value >> 16) & 0xff),
            (byte) ((value >> 24) & 0xff)
        };
    }

    private long addressToLong(byte[] address) {
        return ((address[0] & 0xffL) << 24)
            | ((address[1] & 0xffL) << 16)
            | ((address[2] & 0xffL) << 8)
            | (address[3] & 0xffL);
    }

    private InetAddress longToAddress(long address) throws Exception {
        return InetAddress.getByAddress(new byte[] {
            (byte) ((address >> 24) & 0xff),
            (byte) ((address >> 16) & 0xff),
            (byte) ((address >> 8) & 0xff),
            (byte) (address & 0xff)
        });
    }

    private void sendSearch(DatagramSocket socket, InetAddress destination, String searchTarget) throws Exception {
        String request = "M-SEARCH * HTTP/1.1\r\n"
            + "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n"
            + "MAN: \"ssdp:discover\"\r\n"
            + "MX: 2\r\n"
            + "ST: " + searchTarget + "\r\n"
            + "USER-AGENT: " + USER_AGENT + "\r\n"
            + "\r\n";
        byte[] data = request.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
            data,
            data.length,
            destination,
            SSDP_PORT
        );
        socket.send(packet);
    }

    private Map<String, String> parseHeaders(String response) {
        Map<String, String> headers = new LinkedHashMap<>();
        String[] lines = response.split("\\r?\\n");
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator <= 0) continue;
            String name = line.substring(0, separator).trim().toLowerCase(Locale.US);
            String value = line.substring(separator + 1).trim();
            headers.put(name, value);
        }
        return headers;
    }

    private Device readDeviceDescription(String location, String fallbackId, List<String> details) throws Exception {
        URL descriptionUrl = new URL(location);
        Document document;
        if ("http".equalsIgnoreCase(descriptionUrl.getProtocol())) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "text/xml, application/xml");
            headers.put("User-Agent", USER_AGENT);
            DlnaHttpClient.Response response = DlnaHttpClient.execute(
                descriptionUrl,
                "GET",
                headers,
                null,
                3000,
                4000
            );
            if (response.status < 200 || response.status >= 300) {
                details.add(location + " => HTTP " + response.status);
                return null;
            }
            document = parseDeviceDescription(response.body);
        } else {
            HttpURLConnection connection = (HttpURLConnection) descriptionUrl.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(4000);
            connection.setRequestProperty("Accept", "text/xml, application/xml");
            connection.setRequestProperty("User-Agent", USER_AGENT);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                details.add(location + " => HTTP " + status);
                connection.disconnect();
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > 2 * 1024 * 1024) {
                        throw new IllegalArgumentException("UPnP description is too large");
                    }
                    output.write(buffer, 0, read);
                }
                document = parseDeviceDescription(output.toByteArray());
            } finally {
                connection.disconnect();
            }
        }

        Element renderer = findMediaRenderer(document);
        if (renderer == null) {
            details.add(location + " => no MediaRenderer; " + describeUpnpDevices(document));
            return null;
        }

        Element avTransport = findAvTransportService(renderer);
        if (avTransport == null) {
            details.add(location + " => MediaRenderer without AVTransport; " + describeUpnpDevices(document));
            return null;
        }

        String serviceType = directChildText(avTransport, "serviceType");
        String controlPath = directChildText(avTransport, "controlURL");
        if (serviceType == null || controlPath == null) {
            details.add(location + " => AVTransport missing serviceType or controlURL");
            return null;
        }

        String urlBase = firstElementText(document, "URLBase");
        URL base = urlBase != null && !urlBase.trim().isEmpty()
            ? new URL(urlBase.trim())
            : new URL(location);
        String controlUrl = new URL(base, controlPath.trim()).toExternalForm();

        String name = directChildText(renderer, "friendlyName");
        if (name == null || name.trim().isEmpty()) name = "DLNA TV";
        String udn = directChildText(renderer, "UDN");
        String manufacturer = directChildText(renderer, "manufacturer");
        boolean samsung = manufacturer != null && manufacturer.toLowerCase(Locale.US).contains("samsung");
        String id = udn != null && !udn.trim().isEmpty()
            ? udn.trim()
            : (fallbackId != null && !fallbackId.isEmpty() ? fallbackId : location);

        details.add(location + " => " + name.trim() + " (MediaRenderer/AVTransport)");
        return new Device(id, name.trim(), location, controlUrl, serviceType.trim(), samsung);
    }

    private String describeUpnpDevices(Document document) {
        List<String> found = new ArrayList<>();
        NodeList nodes = document.getElementsByTagNameNS("*", "device");
        for (int i = 0; i < nodes.getLength() && i < 8; i++) {
            if (!(nodes.item(i) instanceof Element)) continue;
            Element device = (Element) nodes.item(i);
            found.add(directChildText(device, "friendlyName") + " (" + directChildText(device, "deviceType") + ")");
        }
        return "devices=" + found;
    }

    private Document parseDeviceDescription(byte[] xml) throws Exception {
        // Android XML parsers do not all support the security feature flags below.
        // Reject DTDs before parsing so entity declarations cannot be expanded.
        String declarations = new String(xml, StandardCharsets.ISO_8859_1)
            .replace("\0", "")
            .toUpperCase(Locale.US);
        if (declarations.contains("<!DOCTYPE") || declarations.contains("<!ENTITY")) {
            throw new IllegalArgumentException("DTD and XML entities are not allowed in UPnP descriptions");
        }
        javax.xml.parsers.DocumentBuilder builder = secureDocumentBuilderFactory().newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> {
            throw new SAXException("External XML entities are not allowed");
        });
        try (InputStream input = new ByteArrayInputStream(xml)) {
            return builder.parse(input);
        }
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
            // Android's bundled XML parser does not implement XInclude configuration.
            // XInclude is disabled by default, so parsing can continue safely.
        }
        try {
            factory.setExpandEntityReferences(false);
        } catch (UnsupportedOperationException ignored) {
            // A few bundled Android parsers do not expose this setting.
        }
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException ignored) {
            // DTDs are also rejected by parseDeviceDescription before parsing.
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException ignored) {
            // The entity resolver rejects external entities on this parser.
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException ignored) {
            // The entity resolver rejects external entities on this parser.
        }
        return factory;
    }

    private Element findMediaRenderer(Document document) {
        NodeList devices = document.getElementsByTagNameNS("*", "device");
        for (int i = 0; i < devices.getLength(); i++) {
            Node node = devices.item(i);
            if (!(node instanceof Element)) continue;
            Element device = (Element) node;
            String type = directChildText(device, "deviceType");
            if (type != null && type.contains(":device:MediaRenderer:")) {
                return device;
            }
        }
        return null;
    }

    private Element findAvTransportService(Element renderer) {
        NodeList services = renderer.getElementsByTagNameNS("*", "service");
        for (int i = 0; i < services.getLength(); i++) {
            Node node = services.item(i);
            if (!(node instanceof Element)) continue;
            Element service = (Element) node;
            String type = directChildText(service, "serviceType");
            if (type != null && type.contains(":service:AVTransport:")) {
                return service;
            }
        }
        return null;
    }

    private String directChildText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String childName = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            if (localName.equals(childName) || childName.endsWith(":" + localName)) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private String firstElementText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    public boolean loadMedia(String url, String title, String subtitle, String imageUrl, String contentType) {
        return loadMedia(url, title, subtitle, imageUrl, contentType, 0);
    }

    public boolean loadMedia(String url, String title, String subtitle, String imageUrl, String contentType, double duration) {
        Device device = selectedDevice;
        if (device == null) {
            lastError = "No DLNA renderer selected";
            return false;
        }

        if (!isPublicHttpUrl(url)) {
            lastError = "DLNA requires a public HTTP or HTTPS media URL";
            return false;
        }

        String mimeType = normalizeContentType(url, contentType);
        Logger.info(TAG, "DLNA load: durationSeconds=" + duration + ", contentType=" + mimeType);
        String metadata = buildDidlMetadata(url, title, subtitle, imageUrl, mimeType, false, duration);
        String playbackUrl = url;
        boolean hlsRelay = url.toLowerCase(Locale.US).contains(".m3u8")
            || mimeType.toLowerCase(Locale.US).contains("mpegurl");
        double relayDuration = device.isSamsung() && hlsRelay ? 0 : duration;
        closeMediaProxy();
        if (hlsRelay || (device.isSamsung() && url.toLowerCase(Locale.US).startsWith("https://")
            && mimeType.toLowerCase(Locale.US).startsWith("video/mp4"))) {
            try {
                mediaProxy = startMediaProxy(device, url, hlsRelay, relayDuration);
                playbackUrl = mediaProxy.getMediaUrl();
                metadata = buildDidlMetadata(
                    playbackUrl,
                    title,
                    subtitle,
                    imageUrl,
                    hlsRelay ? "video/mpeg" : mimeType,
                    hlsRelay && !mediaProxy.supportsByteSeeking(),
                    relayDuration,
                    mediaProxy.getHlsContentLength()
                );
                Logger.info(TAG, hlsRelay
                    ? "Using local MPEG-TS HLS relay for DLNA renderer"
                    : "Using local HTTP media relay for Samsung DLNA renderer");
            } catch (Exception e) {
                lastError = "Could not start DLNA media relay: " + e.getMessage();
                Logger.error(TAG, lastError, e);
                return false;
            }
        }
        // Some renderers keep the previous transport locked after playback ends.
        // Stop is best effort because an idle renderer may reject it.
        sendSoap(device, "Stop", "<InstanceID>0</InstanceID>");
        lastError = null;
        boolean samsungHintAccepted = false;
        String samsungHintError = null;
        if (device.isSamsung()) {
            samsungHintAccepted = sendSoap(
                device,
                "X_PlayerAppHint",
                "<InstanceID>0</InstanceID><UpnpClass>object.item.videoItem</UpnpClass><PlayerHint>load</PlayerHint>"
            );
            if (!samsungHintAccepted) {
                samsungHintError = lastError;
                Logger.warn(TAG, "Samsung player hint was not accepted; continuing with standard AVTransport");
                lastError = null;
            }
        }
        String setUriArguments = "<InstanceID>0</InstanceID>"
            + "<CurrentURI>" + escapeXml(playbackUrl) + "</CurrentURI>"
            + "<CurrentURIMetaData>" + escapeXml(metadata) + "</CurrentURIMetaData>";

        if (!sendSoap(device, "SetAVTransportURI", setUriArguments)) {
            Logger.warn(TAG, "DLNA renderer rejected DIDL metadata containing durationSeconds=" + duration);
            Logger.warn(TAG, "Retrying SetAVTransportURI without metadata for compatibility");
            setUriArguments = "<InstanceID>0</InstanceID>"
                + "<CurrentURI>" + escapeXml(playbackUrl) + "</CurrentURI>"
                + "<CurrentURIMetaData></CurrentURIMetaData>";
            if (!sendSoap(device, "SetAVTransportURI", setUriArguments)) {
                closeMediaProxy();
                return false;
            }
        }

        if (!device.isSamsung()) {
            try {
                Thread.sleep(900);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        boolean played = sendSoap(
            device,
            "Play",
            "<InstanceID>0</InstanceID><Speed>1</Speed>"
        );
        boolean samsungMp4Verified = false;
        if (!played && device.isSamsung() && !hlsRelay && lastError != null
            && lastError.contains("Read timed out") && mediaProxy != null
            && mediaProxy.hasRequestedMedia()) {
            Logger.warn(TAG, "Samsung requested MP4 while Play timed out; checking receiver state before closing relay");
            played = waitForSamsungPlayback(device, 12000, true);
            samsungMp4Verified = played;
        }
        if (!played && device.isSamsung() && lastError != null
            && lastError.contains("<errorCode>705</errorCode>")) {
            String playError = lastError;
            played = waitForSamsungPlayback(device, 5000);
            if (!played && (lastError == null || !lastError.startsWith("Samsung playback stopped:"))) {
                lastError = playError;
                played = sendSoap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
                if (!played && lastError != null && lastError.contains("<errorCode>705</errorCode>")) {
                    played = waitForSamsungPlayback(device, 5000);
                }
            }
        }
        if (!played && !device.isSamsung() && lastError != null && lastError.contains("<errorCode>705</errorCode>")) {
            try {
                Thread.sleep(device.isSamsung() ? 2500 : 1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            played = sendSoap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
        }
        if (played && hlsRelay) {
            played = waitForSamsungPlayback(device, 12000);
        }
        if (played && device.isSamsung() && !hlsRelay && !samsungMp4Verified) {
            played = waitForSamsungPlayback(device, 12000, true);
        }
        if (played) {
            lastError = null;
            Logger.info(TAG, "DLNA playback started on " + device.getName());
            if (hlsRelay) {
                new Thread(() -> {
                    Document position = querySoapDocument(device, "GetPositionInfo");
                    Logger.info(TAG, "DLNA duration diagnostic: sentSeconds=" + duration
                        + ", rendererTrackDuration=" + (position != null
                            ? firstElementText(position, "TrackDuration") : "unavailable"));
                }, "IonicChromecast-DLNA-Duration").start();
            }
        } else {
            if (device.isSamsung()) {
                lastError = "Samsung hint=" + samsungHintAccepted
                    + (samsungHintError != null ? " (" + samsungHintError + ")" : "")
                    + "; relay=" + (mediaProxy != null ? mediaProxy.getDiagnostic() : "not used")
                    + "; " + lastError;
            }
            closeMediaProxy();
        }
        return played;
    }

    private boolean waitForSamsungPlayback(Device device, int timeoutMs) {
        return waitForSamsungPlayback(device, timeoutMs, false);
    }

    private boolean waitForSamsungPlayback(Device device, int timeoutMs, boolean requireMediaBytes) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastObservedState = "unavailable";
        long playingWithMediaSince = 0;
        while (System.currentTimeMillis() < deadline) {
            Document transport = querySoapDocument(device, "GetTransportInfo");
            if (transport != null) {
                String state = firstElementText(transport, "CurrentTransportState");
                String status = firstElementText(transport, "CurrentTransportStatus");
                lastObservedState = state + "/" + status;
                if ("PLAYING".equals(state)) {
                    DlnaMediaProxy proxy = mediaProxy;
                    if (!requireMediaBytes) {
                        Logger.info(TAG, "Samsung reports PLAYING after AVTransport Play response");
                        return true;
                    }
                    if (proxy != null && proxy.hasSentMedia()) {
                        if (playingWithMediaSince == 0) playingWithMediaSince = System.currentTimeMillis();
                        if (System.currentTimeMillis() - playingWithMediaSince >= 2000) {
                            Logger.info(TAG, "Samsung reports PLAYING after receiving MP4 bytes");
                            return true;
                        }
                    }
                } else {
                    playingWithMediaSince = 0;
                }
                if ("STOPPED".equals(state)) {
                    Document stopped = querySoapDocument(device, "X_GetStoppedReason");
                    String reason = stopped != null ? firstElementText(stopped, "StoppedReason") : null;
                    String reasonData = stopped != null ? firstElementText(stopped, "StoppedReasonData") : null;
                    lastObservedState += "/" + reason;
                    DlnaMediaProxy proxy = mediaProxy;
                    if ("STOPPED_FINISHED".equals(reason) && proxy != null && proxy.hasCompletedGet()) {
                        Logger.info(TAG, "Samsung reports completed playback of the relayed video");
                        return true;
                    }
                    if ("ERROR_OCCURRED".equals(status) || "STOPPED_ERROR".equals(reason)) {
                        lastError = "Samsung playback stopped: " + reason
                            + (reasonData != null && !reasonData.isEmpty() ? " (" + reasonData + ")" : "");
                        return false;
                    }
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        lastError = "Samsung playback state after locked Play: " + lastObservedState;
        return false;
    }

    private Document querySoapDocument(Device device, String action) {
        try {
            URL controlUrl = new URL(device.controlUrl);
            if (!"http".equalsIgnoreCase(controlUrl.getProtocol())) return null;
            String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + escapeXml(device.serviceType) + "\">"
                + "<InstanceID>0</InstanceID></u:" + action + "></s:Body></s:Envelope>";
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "text/xml; charset=\"utf-8\"");
            headers.put("SOAPACTION", "\"" + device.serviceType + "#" + action + "\"");
            DlnaHttpClient.Response response = DlnaHttpClient.execute(
                controlUrl,
                "POST",
                headers,
                envelope.getBytes(StandardCharsets.UTF_8),
                3000,
                5000
            );
            return response.status >= 200 && response.status < 300
                ? parseDeviceDescription(response.body)
                : null;
        } catch (Exception e) {
            Logger.warn(TAG, "Could not query Samsung " + action + ": " + e.getMessage());
            return null;
        }
    }

    public void stopAndClear() {
        Device device = selectedDevice;
        if (device != null) {
            sendSoap(device, "Stop", "<InstanceID>0</InstanceID>");
        }
        closeMediaProxy();
        selectedDevice = null;
        lastError = null;
    }

    private DlnaMediaProxy startMediaProxy(Device device, String url, boolean hlsTransportStream) throws Exception {
        return startMediaProxy(device, url, hlsTransportStream, 0);
    }

    private DlnaMediaProxy startMediaProxy(Device device, String url, boolean hlsTransportStream, double duration) throws Exception {
        ConnectivityManager manager =
            (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network wifi = findWifiNetwork(manager);
        LinkProperties properties = wifi != null && manager != null ? manager.getLinkProperties(wifi) : null;
        if (properties != null) {
            for (LinkAddress address : properties.getLinkAddresses()) {
                if (address.getAddress() instanceof Inet4Address && !address.getAddress().isLoopbackAddress()) {
                    InetAddress renderer = InetAddress.getByName(new URL(device.controlUrl).getHost());
                    // Samsung repeatedly closes ranged MPEG-TS requests while still TRANSITIONING.
                    // Keep its HLS delivery sequential; other renderers retain byte seeking.
                    return new DlnaMediaProxy(
                        wifi, address.getAddress(), renderer, url, hlsTransportStream, duration,
                        !device.isSamsung()
                    );
                }
            }
        }
        throw new IOException("No IPv4 address on the phone's Wi-Fi network");
    }

    private void closeMediaProxy() {
        DlnaMediaProxy proxy = mediaProxy;
        mediaProxy = null;
        if (proxy != null) proxy.close();
    }

    private boolean isPublicHttpUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            URL parsed = new URL(value);
            String protocol = parsed.getProtocol();
            return ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                && parsed.getHost() != null
                && !parsed.getHost().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeContentType(String url, String contentType) {
        if (contentType != null && !contentType.trim().isEmpty()) {
            return contentType.trim();
        }
        String lowerUrl = url.toLowerCase(Locale.US);
        return lowerUrl.contains(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp4";
    }

    private String buildDidlMetadata(String url, String title, String subtitle, String imageUrl, String mimeType) {
        return buildDidlMetadata(url, title, subtitle, imageUrl, mimeType, false);
    }

    private String buildDidlMetadata(
        String url,
        String title,
        String subtitle,
        String imageUrl,
        String mimeType,
        boolean streaming
    ) {
        return buildDidlMetadata(url, title, subtitle, imageUrl, mimeType, streaming, 0);
    }

    private String buildDidlMetadata(
        String url,
        String title,
        String subtitle,
        String imageUrl,
        String mimeType,
        boolean streaming,
        double duration
    ) {
        return buildDidlMetadata(url, title, subtitle, imageUrl, mimeType, streaming, duration, -1);
    }

    private String buildDidlMetadata(
        String url,
        String title,
        String subtitle,
        String imageUrl,
        String mimeType,
        boolean streaming,
        double duration,
        long size
    ) {
        String safeTitle = title != null && !title.isEmpty() ? title : "Video";
        StringBuilder metadata = new StringBuilder();
        metadata.append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"")
            .append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"")
            .append(" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
            .append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
            .append("<dc:title>").append(escapeXml(safeTitle)).append("</dc:title>");
        if (subtitle != null && !subtitle.isEmpty()) {
            metadata.append("<dc:description>").append(escapeXml(subtitle)).append("</dc:description>");
        }
        if (imageUrl != null && isPublicHttpUrl(imageUrl)) {
            metadata.append("<upnp:albumArtURI>").append(escapeXml(imageUrl)).append("</upnp:albumArtURI>");
        }
        metadata.append("<upnp:class>object.item.videoItem</upnp:class>")
            .append("<res protocolInfo=\"http-get:*:")
            .append(escapeXml(mimeType))
            .append(":")
            .append(streaming ? "*" : DLNA_FLAGS)
            .append("\"");
        if (Double.isFinite(duration) && duration > 0 && duration < Long.MAX_VALUE / 1000.0) {
            long milliseconds = Math.round(duration * 1000);
            metadata.append(" duration=\"")
                .append(String.format(Locale.US, "%d:%02d:%02d.%03d",
                    milliseconds / 3600000,
                    (milliseconds / 60000) % 60,
                    (milliseconds / 1000) % 60,
                    milliseconds % 1000))
                .append("\"");
        }
        if (size > 0) metadata.append(" size=\"").append(size).append("\"");
        metadata.append(">")
            .append(escapeXml(url))
            .append("</res></item></DIDL-Lite>");
        return metadata.toString();
    }

    private boolean sendSoap(Device device, String action, String arguments) {
        HttpURLConnection connection = null;
        try {
            String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + escapeXml(device.serviceType) + "\">"
                + arguments
                + "</u:" + action + "></s:Body></s:Envelope>";
            byte[] body = envelope.getBytes(StandardCharsets.UTF_8);

            URL controlUrl = new URL(device.controlUrl);
            if ("http".equalsIgnoreCase(controlUrl.getProtocol())) {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Content-Type", "text/xml; charset=\"utf-8\"");
                headers.put("SOAPACTION", "\"" + device.serviceType + "#" + action + "\"");
                DlnaHttpClient.Response response = DlnaHttpClient.execute(
                    controlUrl,
                    "POST",
                    headers,
                    body,
                    5000,
                    8000
                );
                if (response.status >= 200 && response.status < 300) {
                    return true;
                }
                String responseText = response.bodyAsText().replaceAll("\\s+", " ").trim();
                lastError = "DLNA " + action + " failed with HTTP " + response.status
                    + (responseText.isEmpty() ? "" : ": " + responseText);
                Logger.error(TAG, lastError, null);
                return false;
            }

            connection = (HttpURLConnection) controlUrl.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            connection.setRequestProperty("SOAPACTION", "\"" + device.serviceType + "#" + action + "\"");
            connection.setRequestProperty("Connection", "close");
            connection.setFixedLengthStreamingMode(body.length);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            String response = readResponse(connection, status);
            if (status >= 200 && status < 300) {
                return true;
            }

            lastError = "DLNA " + action + " failed with HTTP " + status
                + (response.isEmpty() ? "" : ": " + response);
            Logger.error(TAG, lastError, null);
            return false;
        } catch (Exception e) {
            lastError = "DLNA " + action + " failed: " + e.getMessage();
            Logger.error(TAG, lastError, e);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readResponse(HttpURLConnection connection, int status) {
        InputStream input = null;
        try {
            input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) return "";
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1 && total < 4096) {
                int count = Math.min(read, 4096 - total);
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toString(StandardCharsets.UTF_8.name()).replaceAll("\\s+", " ").trim();
        } catch (Exception ignored) {
            return "";
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
