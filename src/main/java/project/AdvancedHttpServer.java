package project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced HTTP Server example demonstrating handling multiple HTTP clients
 * using NIO Selector pattern, similar to AdvancedSelectorExample but with HTTP protocol.
 */
public class AdvancedHttpServer {

    private static final int HTTP_PORT = 8080;
    private static final AtomicInteger clientCounter = new AtomicInteger(0);
    private static final ConcurrentHashMap<SocketChannel, HttpClientInfo> clients = new ConcurrentHashMap<>();

    /**
     * Holds HTTP client-specific information and request state.
     */
    private static class HttpClientInfo {
        private final int clientId;
        private final long connectTime;
        private ByteBuffer readBuffer;
        private ByteBuffer writeBuffer;
        private StringBuilder requestBuilder;
        private boolean requestComplete;
        private HttpRequest currentRequest;

        public HttpClientInfo(int clientId) {
            this.clientId = clientId;
            this.connectTime = System.currentTimeMillis();
            this.readBuffer = ByteBuffer.allocate(8192); // Larger buffer for HTTP headers
            this.requestBuilder = new StringBuilder();
            this.requestComplete = false;
        }

        // Getters and setters
        public int getClientId() { return clientId; }
        public long getConnectTime() { return connectTime; }
        public ByteBuffer getReadBuffer() { return readBuffer; }
        public ByteBuffer getWriteBuffer() { return writeBuffer; }
        public void setWriteBuffer(ByteBuffer buffer) { this.writeBuffer = buffer; }
        public StringBuilder getRequestBuilder() { return requestBuilder; }
        public boolean isRequestComplete() { return requestComplete; }
        public void setRequestComplete(boolean complete) { this.requestComplete = complete; }
        public HttpRequest getCurrentRequest() { return currentRequest; }
        public void setCurrentRequest(HttpRequest request) { this.currentRequest = request; }
        public void resetForNextRequest() {
            this.requestBuilder = new StringBuilder();
            this.requestComplete = false;
            this.currentRequest = null;
            this.readBuffer.clear();
        }
    }

    /**
     * Simple HTTP request representation.
     */
    private static class HttpRequest {
        private final String method;
        private final String path;
        private final String version;
        private final Map<String, String> headers;

        public HttpRequest(String method, String path, String version, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.version = version;
            this.headers = headers;
        }

        public String getMethod() { return method; }
        public String getPath() { return path; }
        public String getVersion() { return version; }
        public Map<String, String> getHeaders() { return headers; }
    }

    /**
     * Simple HTTP response builder.
     */
    private static class HttpResponse {
        private final int statusCode;
        private final String statusText;
        private final Map<String, String> headers;
        private final String body;

        public HttpResponse(int statusCode, String statusText, String body) {
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.body = body;
            this.headers = new HashMap<>();

            // Default headers
            headers.put("Content-Type", "text/html; charset=UTF-8");
            headers.put("Content-Length", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
            headers.put("Server", "AdvancedHttpServer/1.0");
            headers.put("Date", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }

        public String toHttpString() {
            StringBuilder response = new StringBuilder();

            // Status line
            response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");

            // Headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                response.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }

            // Empty line separating headers from body
            response.append("\r\n");

            // Body
            response.append(body);

            return response.toString();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Advanced HTTP Server Example ===");

        // Start the HTTP server
        Thread serverThread = new Thread(AdvancedHttpServer::runHttpServer);
        serverThread.start();

        // Wait for server to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create multiple HTTP clients
        runHttpClients();

        // Let server run
        try {
            Thread.sleep(15000); // Run for 15 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        serverThread.interrupt();
        System.out.println("HTTP Server example completed");
    }

    /**
     * Runs the HTTP server using NIO Selector pattern.
     */
    private static void runHttpServer() {
        System.out.println("\nStarting HTTP server on port " + HTTP_PORT);

        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            // Configure server
            serverChannel.bind(new InetSocketAddress(HTTP_PORT));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("HTTP server ready for connections at http://localhost:" + HTTP_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                // Select with timeout
                int readyChannels = selector.select(2000);

                if (readyChannels > 0) {
                    processSelectedKeys(selector);
                }

                // Periodic cleanup and status report
                cleanupDisconnectedClients();
                reportServerStatus();
            }

        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                System.err.println("HTTP server error: " + e.getMessage());
            }
        }

        System.out.println("HTTP server stopped");
    }

    /**
     * Process all selected keys.
     */
    private static void processSelectedKeys(Selector selector) throws IOException {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            keyIterator.remove();

            try {
                if (key.isValid()) {
                    if (key.isAcceptable()) {
                        handleHttpAccept(key, selector);
                    } else if (key.isReadable()) {
                        handleHttpRead(key);
                    } else if (key.isWritable()) {
                        handleHttpWrite(key);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error processing HTTP key: " + e.getMessage());
                cleanupKey(key);
            }
        }
    }

    /**
     * Handle new HTTP client connections.
     */
    private static void handleHttpAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            int clientId = clientCounter.incrementAndGet();
            HttpClientInfo clientInfo = new HttpClientInfo(clientId);

            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(clientInfo);

            clients.put(clientChannel, clientInfo);

            System.out.println("New HTTP connection (ID: " + clientId + ") from " +
                             clientChannel.getRemoteAddress());
        }
    }

    /**
     * Handle reading HTTP requests from clients.
     */
    private static void handleHttpRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        HttpClientInfo clientInfo = (HttpClientInfo) key.attachment();

        if (clientInfo == null) {
            cleanupKey(key);
            return;
        }

        ByteBuffer buffer = clientInfo.getReadBuffer();
        int bytesRead = clientChannel.read(buffer);

        if (bytesRead > 0) {
            buffer.flip();
            String data = StandardCharsets.UTF_8.decode(buffer).toString();
            buffer.clear();

            clientInfo.getRequestBuilder().append(data);

            // Check if we have a complete HTTP request (ends with \r\n\r\n)
            String request = clientInfo.getRequestBuilder().toString();
            if (request.contains("\r\n\r\n")) {
                clientInfo.setRequestComplete(true);

                // Parse the HTTP request
                HttpRequest httpRequest = parseHttpRequest(request);
                clientInfo.setCurrentRequest(httpRequest);

                System.out.println("Client " + clientInfo.getClientId() + " - " +
                                 httpRequest.getMethod() + " " + httpRequest.getPath());

                // Generate response
                HttpResponse response = generateHttpResponse(httpRequest, clientInfo);
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.toHttpString().getBytes(StandardCharsets.UTF_8));
                clientInfo.setWriteBuffer(responseBuffer);

                key.interestOps(SelectionKey.OP_WRITE);
            }

        } else if (bytesRead == -1) {
            // Client disconnected
            System.out.println("HTTP Client " + clientInfo.getClientId() + " disconnected");
            cleanupKey(key);
        }
    }

    /**
     * Handle writing HTTP responses to clients.
     */
    private static void handleHttpWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        HttpClientInfo clientInfo = (HttpClientInfo) key.attachment();

        if (clientInfo == null || clientInfo.getWriteBuffer() == null) {
            cleanupKey(key);
            return;
        }

        ByteBuffer buffer = clientInfo.getWriteBuffer();
        clientChannel.write(buffer);

        if (!buffer.hasRemaining()) {
            // All data written, check if connection should be kept alive
            String connection = clientInfo.getCurrentRequest().getHeaders().get("connection");
            if ("keep-alive".equalsIgnoreCase(connection)) {
                // Reset for next request on same connection
                clientInfo.resetForNextRequest();
                key.interestOps(SelectionKey.OP_READ);
            } else {
                // Close connection after response
                cleanupKey(key);
            }
        }
    }

    /**
     * Parse HTTP request from raw string.
     */
    private static HttpRequest parseHttpRequest(String request) {
        String[] lines = request.split("\r\n");
        String[] requestLine = lines[0].split(" ");

        String method = requestLine[0];
        String path = requestLine[1];
        String version = requestLine[2];

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length && !lines[i].isEmpty(); i++) {
            String[] headerParts = lines[i].split(": ", 2);
            if (headerParts.length == 2) {
                headers.put(headerParts[0].toLowerCase(), headerParts[1]);
            }
        }

        return new HttpRequest(method, path, version, headers);
    }

    /**
     * Generate HTTP response based on request.
     */
    private static HttpResponse generateHttpResponse(HttpRequest request, HttpClientInfo clientInfo) {
        String path = request.getPath();

        switch (path) {
            case "/":
                return new HttpResponse(200, "OK", generateHomePage(clientInfo));
            case "/time":
                return new HttpResponse(200, "OK", generateTimePage());
            case "/clients":
                return new HttpResponse(200, "OK", generateClientsPage());
            case "/uptime":
                return new HttpResponse(200, "OK", generateUptimePage(clientInfo));
            case "/health":
                return new HttpResponse(200, "OK", generateHealthPage());
            default:
                return new HttpResponse(404, "Not Found", generate404Page(path));
        }
    }

    /**
     * Generate home page HTML.
     */
    private static String generateHomePage(HttpClientInfo clientInfo) {
        return "<!DOCTYPE html>\n" +
               "<html><head><title>Advanced HTTP Server</title></head>\n" +
               "<body>\n" +
               "<h1>Welcome to Advanced HTTP Server</h1>\n" +
               "<p>You are client ID: " + clientInfo.getClientId() + "</p>\n" +
               "<p>Available endpoints:</p>\n" +
               "<ul>\n" +
               "<li><a href=\"/time\">/time</a> - Current server time</li>\n" +
               "<li><a href=\"/clients\">/clients</a> - Connected clients count</li>\n" +
               "<li><a href=\"/uptime\">/uptime</a> - Your connection uptime</li>\n" +
               "<li><a href=\"/health\">/health</a> - Server health status</li>\n" +
               "</ul>\n" +
               "</body></html>";
    }

    /**
     * Generate time page HTML.
     */
    private static String generateTimePage() {
        return "<!DOCTYPE html>\n" +
               "<html><head><title>Server Time</title></head>\n" +
               "<body>\n" +
               "<h1>Current Server Time</h1>\n" +
               "<p>Server time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "</p>\n" +
               "<p>Timestamp: " + System.currentTimeMillis() + "</p>\n" +
               "<a href=\"/\">Back to home</a>\n" +
               "</body></html>";
    }

    /**
     * Generate clients page HTML.
     */
    private static String generateClientsPage() {
        return "<!DOCTYPE html>\n" +
               "<html><head><title>Connected Clients</title></head>\n" +
               "<body>\n" +
               "<h1>Connected Clients</h1>\n" +
               "<p>Total connected clients: " + clients.size() + "</p>\n" +
               "<a href=\"/\">Back to home</a>\n" +
               "</body></html>";
    }

    /**
     * Generate uptime page HTML.
     */
    private static String generateUptimePage(HttpClientInfo clientInfo) {
        long uptime = System.currentTimeMillis() - clientInfo.getConnectTime();
        return "<!DOCTYPE html>\n" +
               "<html><head><title>Connection Uptime</title></head>\n" +
               "<body>\n" +
               "<h1>Your Connection Uptime</h1>\n" +
               "<p>Connected for: " + uptime + " milliseconds</p>\n" +
               "<p>That's " + (uptime / 1000) + " seconds</p>\n" +
               "<a href=\"/\">Back to home</a>\n" +
               "</body></html>";
    }

    /**
     * Generate health page HTML.
     */
    private static String generateHealthPage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return "<!DOCTYPE html>\n" +
               "<html><head><title>Server Health</title></head>\n" +
               "<body>\n" +
               "<h1>Server Health Status</h1>\n" +
               "<p>Status: <span style=\"color: green;\">Healthy</span></p>\n" +
               "<p>Active connections: " + clients.size() + "</p>\n" +
               "<p>Memory usage: " + (usedMemory / 1024 / 1024) + " MB / " + (totalMemory / 1024 / 1024) + " MB</p>\n" +
               "<p>Available processors: " + runtime.availableProcessors() + "</p>\n" +
               "<a href=\"/\">Back to home</a>\n" +
               "</body></html>";
    }

    /**
     * Generate 404 page HTML.
     */
    private static String generate404Page(String path) {
        return "<!DOCTYPE html>\n" +
               "<html><head><title>404 Not Found</title></head>\n" +
               "<body>\n" +
               "<h1>404 - Page Not Found</h1>\n" +
               "<p>The requested path '" + path + "' was not found on this server.</p>\n" +
               "<a href=\"/\">Back to home</a>\n" +
               "</body></html>";
    }

    /**
     * Clean up a selection key and associated resources.
     */
    private static void cleanupKey(SelectionKey key) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            clients.remove(channel);
            key.cancel();
            channel.close();
        } catch (IOException e) {
            System.err.println("Error during HTTP cleanup: " + e.getMessage());
        }
    }

    /**
     * Clean up disconnected clients.
     */
    private static void cleanupDisconnectedClients() {
        clients.entrySet().removeIf(entry -> !entry.getKey().isOpen());
    }

    /**
     * Report server status periodically.
     */
    private static void reportServerStatus() {
        if (clients.size() > 0) {
            System.out.println("HTTP Server status: " + clients.size() + " active connections");
        }
    }

    /**
     * Create HTTP clients for testing.
     */
    private static void runHttpClients() {
        System.out.println("\nStarting HTTP clients...");

        // Simple HTTP client that requests different endpoints
        new Thread(() -> runHttpClient("HTTP-Client-1", new String[]{"/", "/time", "/clients"})).start();

        new Thread(() -> runHttpClient("HTTP-Client-2", new String[]{"/health", "/uptime"})).start();

        new Thread(() -> runHttpClient("HTTP-Client-3", new String[]{"/", "/nonexistent", "/time"})).start();
    }

    /**
     * Run a simple HTTP client that makes requests to specified paths.
     */
    private static void runHttpClient(String clientName, String[] paths) {
        try {
            for (String path : paths) {
                try (SocketChannel channel = SocketChannel.open()) {
                    channel.connect(new InetSocketAddress("localhost", HTTP_PORT));

                    // Send HTTP request
                    String request = "GET " + path + " HTTP/1.1\r\n" +
                                   "Host: localhost:" + HTTP_PORT + "\r\n" +
                                   "User-Agent: " + clientName + "\r\n" +
                                   "Connection: close\r\n" +
                                   "\r\n";

                    ByteBuffer requestBuffer = ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8));
                    channel.write(requestBuffer);

                    // Read response
                    ByteBuffer responseBuffer = ByteBuffer.allocate(4096);
                    StringBuilder response = new StringBuilder();

                    while (channel.read(responseBuffer) > 0) {
                        responseBuffer.flip();
                        response.append(StandardCharsets.UTF_8.decode(responseBuffer).toString());
                        responseBuffer.clear();
                    }

                    // Extract status line from response
                    String statusLine = response.toString().split("\r\n")[0];
                    System.out.println(clientName + " - GET " + path + " -> " + statusLine);

                    Thread.sleep(1000); // Wait between requests
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println(clientName + " error: " + e.getMessage());
        }
    }
}
