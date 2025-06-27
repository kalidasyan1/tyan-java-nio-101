package project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP Client utilities for testing the Advanced HTTP Server.
 * Demonstrates both synchronous and asynchronous HTTP requests using Java's modern HTTPClient.
 */
public class HttpClientRunner {

    /**
     * Modern HTTP Client implementation using Java's HTTPClient.
     */
    public static class ModernHttpClient {
        private final HttpClient httpClient;
        private final String baseUrl;

        public ModernHttpClient(String baseUrl) {
            this.baseUrl = baseUrl;
            this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        }

        /**
         * Make a synchronous GET request.
         */
        public void makeRequest(String path, String clientName) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("User-Agent", clientName)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                System.out.println(clientName + " - GET " + path + " -> " +
                    response.statusCode() + " " + getStatusText(response.statusCode()));

            } catch (Exception e) {
                System.err.println(clientName + " error: " + e.getMessage());
            }
        }

        /**
         * Make an asynchronous GET request.
         */
        public CompletableFuture<Void> makeAsyncRequest(String path, String clientName) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("User-Agent", clientName)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    System.out.println(clientName + " (async) - GET " + path + " -> " +
                        response.statusCode() + " " + getStatusText(response.statusCode()));
                })
                .exceptionally(throwable -> {
                    System.err.println(clientName + " (async) error: " + throwable.getMessage());
                    return null;
                });
        }

        private String getStatusText(int statusCode) {
            return switch (statusCode) {
                case 200 -> "OK";
                case 404 -> "Not Found";
                case 500 -> "Internal Server Error";
                default -> "Unknown";
            };
        }

        public void close() {
            // HttpClient doesn't need explicit closing in modern Java
        }
    }

    /**
     * Create and run multiple HTTP clients to test the server with various request patterns.
     */
    public static void runModernHttpClients(int serverPort) {
        System.out.println("\nStarting modern HTTP clients...");

        String baseUrl = "http://localhost:" + serverPort;

        // Client 1: Synchronous requests
        new Thread(() -> {
            ModernHttpClient client = new ModernHttpClient(baseUrl);
            String[] paths = {"/", "/time", "/clients"};

            for (String path : paths) {
                client.makeRequest(path, "Modern-Client-1");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            client.close();
        }).start();

        // Client 2: Asynchronous requests
        new Thread(() -> {
            ModernHttpClient client = new ModernHttpClient(baseUrl);
            String[] paths = {"/health", "/uptime", "/nonexistent"};

            @SuppressWarnings("unchecked")
            CompletableFuture<Void>[] futures = new CompletableFuture[paths.length];
            for (int i = 0; i < paths.length; i++) {
                futures[i] = client.makeAsyncRequest(paths[i], "Modern-Client-2");
            }

            // Wait for all async requests to complete
            CompletableFuture.allOf(futures).join();
            client.close();
        }).start();

        // Client 3: Mixed requests (both sync and async)
        new Thread(() -> {
            ModernHttpClient client = new ModernHttpClient(baseUrl);

            client.makeRequest("/", "Modern-Client-3");
            client.makeAsyncRequest("/time", "Modern-Client-3").join();

            client.close();
        }).start();
    }

    /**
     * Main method for standalone testing of HTTP clients.
     * Can be used to test against any HTTP server running on the specified port.
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        System.out.println("=== HTTP Client Runner ===");
        System.out.println("Testing HTTP server on port: " + port);

        runModernHttpClients(port);

        // Wait for all clients to complete
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("HTTP Client testing completed");
    }
}
