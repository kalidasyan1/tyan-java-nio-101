package project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Advanced HTTP Server that extends BaseNioServer to avoid code duplication
 * and demonstrates handling multiple HTTP clients using NIO.
 */
public class AdvancedHttpServer extends BaseNioServer<AdvancedHttpServer.HttpClientInfo> {

    private static final int HTTP_PORT = 8080;

    /**
     * HTTP-specific client information extending BaseClientInfo.
     */
    public static class HttpClientInfo extends BaseNioServer.BaseClientInfo {
        private StringBuilder requestBuilder;
        private boolean requestComplete;
        private HttpRequestData currentRequest;

        public HttpClientInfo(int connectionId, String remoteAddress) {
            super(connectionId, remoteAddress);
            this.requestBuilder = new StringBuilder();
            this.requestComplete = false;
        }

        public StringBuilder getRequestBuilder() { return requestBuilder; }
        public boolean isRequestComplete() { return requestComplete; }
        public void setRequestComplete(boolean complete) { this.requestComplete = complete; }
        public HttpRequestData getCurrentRequest() { return currentRequest; }
        public void setCurrentRequest(HttpRequestData request) { this.currentRequest = request; }

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
    public static class HttpRequestData {
        private final String method;
        private final String path;
        private final String version;
        private final Map<String, String> headers;

        public HttpRequestData(String method, String path, String version, Map<String, String> headers) {
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
    public static class HttpResponseData {
        private final int statusCode;
        private final String statusText;
        private final Map<String, String> headers;
        private final String body;

        public HttpResponseData(int statusCode, String statusText, String body) {
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

    public AdvancedHttpServer() {
        super(HTTP_PORT);
    }

    @Override
    protected String getServerName() {
        return "Advanced HTTP Server";
    }

    @Override
    protected HttpClientInfo createClientInfo(int connectionId, String remoteAddress) {
        return new HttpClientInfo(connectionId, remoteAddress);
    }

    @Override
    protected void processClientData(HttpClientInfo clientInfo, String data, SelectionKey key) throws IOException {
        clientInfo.getRequestBuilder().append(data);

        // Check if we have a complete HTTP request (ends with \r\n\r\n)
        String request = clientInfo.getRequestBuilder().toString();
        if (request.contains("\r\n\r\n")) {
            clientInfo.setRequestComplete(true);

            // Parse the HTTP request
            HttpRequestData httpRequest = parseHttpRequest(request);
            clientInfo.setCurrentRequest(httpRequest);

            System.out.println("Connection " + clientInfo.getConnectionId() +
                             ": " + httpRequest.getMethod() + " " + httpRequest.getPath());

            // Generate response
            HttpResponseData response = generateHttpResponse(httpRequest, clientInfo);
            ByteBuffer responseBuffer = ByteBuffer.wrap(response.toHttpString().getBytes(StandardCharsets.UTF_8));
            clientInfo.setWriteBuffer(responseBuffer);

            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    @Override
    protected void onWriteComplete(HttpClientInfo clientInfo, SelectionKey key) {
        // Check if connection should be kept alive
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

    /**
     * Parse HTTP request from raw string.
     */
    private HttpRequestData parseHttpRequest(String request) {
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

        return new HttpRequestData(method, path, version, headers);
    }

    /**
     * Generate HTTP response based on request.
     */
    private HttpResponseData generateHttpResponse(HttpRequestData request, HttpClientInfo clientInfo) {
        String path = request.getPath();

        return switch (path) {
            case "/" -> new HttpResponseData(200, "OK", generateHomePage(clientInfo));
            case "/time" -> new HttpResponseData(200, "OK", generateTimePage());
            case "/clients" -> new HttpResponseData(200, "OK", generateClientsPage());
            case "/uptime" -> new HttpResponseData(200, "OK", generateUptimePage(clientInfo));
            case "/health" -> new HttpResponseData(200, "OK", generateHealthPage());
            default -> new HttpResponseData(404, "Not Found", generate404Page(path));
        };
    }

    /**
     * Generate home page HTML.
     */
    private String generateHomePage(HttpClientInfo clientInfo) {
        return """
               <!DOCTYPE html>
               <html><head><title>Advanced HTTP Server</title></head>
               <body>
               <h1>Welcome to Advanced HTTP Server</h1>
               <p>You are client ID: %d</p>
               <p>Available endpoints:</p>
               <ul>
               <li><a href="/time">/time</a> - Current server time</li>
               <li><a href="/clients">/clients</a> - Connected clients count</li>
               <li><a href="/uptime">/uptime</a> - Your connection uptime</li>
               <li><a href="/health">/health</a> - Server health status</li>
               </ul>
               </body></html>
               """.formatted(clientInfo.getClientId());
    }

    /**
     * Generate time page HTML.
     */
    private String generateTimePage() {
        return """
               <!DOCTYPE html>
               <html><head><title>Server Time</title></head>
               <body>
               <h1>Current Server Time</h1>
               <p>Server time: %s</p>
               <p>Timestamp: %d</p>
               <a href="/">Back to home</a>
               </body></html>
               """.formatted(
                   LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                   System.currentTimeMillis()
               );
    }

    /**
     * Generate clients page HTML.
     */
    private String generateClientsPage() {
        return """
               <!DOCTYPE html>
               <html><head><title>Connected Clients</title></head>
               <body>
               <h1>Connection Statistics</h1>
               <p><strong>Active connections:</strong> %d</p>
               <p><strong>Total connections made:</strong> %d</p>
               <a href="/">Back to home</a>
               </body></html>
               """.formatted(
                   getActiveConnectionCount(),
                   getTotalConnectionCount()
               );
    }

    /**
     * Generate uptime page HTML.
     */
    private String generateUptimePage(HttpClientInfo clientInfo) {
        long uptime = System.currentTimeMillis() - clientInfo.getConnectTime();
        return """
               <!DOCTYPE html>
               <html><head><title>Connection Uptime</title></head>
               <body>
               <h1>Your Connection Uptime</h1>
               <p>Connected for: %d milliseconds</p>
               <p>That's %d seconds</p>
               <a href="/">Back to home</a>
               </body></html>
               """.formatted(uptime, uptime / 1000);
    }

    /**
     * Generate health page HTML.
     */
    private String generateHealthPage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return """
               <!DOCTYPE html>
               <html><head><title>Server Health</title></head>
               <body>
               <h1>Server Health Status</h1>
               <p>Status: <span style="color: green;">Healthy</span></p>
               <p>Active connections: %d</p>
               <p>Memory usage: %d MB / %d MB</p>
               <p>Available processors: %d</p>
               <a href="/">Back to home</a>
               </body></html>
               """.formatted(
                   clients.size(),
                   usedMemory / 1024 / 1024,
                   totalMemory / 1024 / 1024,
                   runtime.availableProcessors()
               );
    }

    /**
     * Generate 404 page HTML.
     */
    private String generate404Page(String path) {
        return """
               <!DOCTYPE html>
               <html><head><title>404 Not Found</title></head>
               <body>
               <h1>404 - Page Not Found</h1>
               <p>The requested path '%s' was not found on this server.</p>
               <a href="/">Back to home</a>
               </body></html>
               """.formatted(path);
    }

    public static void main(String[] args) {
        System.out.println("=== Advanced HTTP Server Example ===");

        // Start the Advanced HTTP server
        AdvancedHttpServer server = new AdvancedHttpServer();
        server.startServer();
        // Server will keep running until manually stopped
    }
}
