package channel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Demonstrates basic usage of SocketChannel for non-blocking I/O operations.
 * This example shows both blocking and non-blocking socket operations.
 */
public class SocketChannelExample {

    public static void main(String[] args) {
        System.out.println("=== SocketChannel Examples ===");

        // Example 1: Blocking SocketChannel
        blockingSocketChannelExample();

        // Example 2: Non-blocking SocketChannel
        nonBlockingSocketChannelExample();
    }

    /**
     * Demonstrates blocking SocketChannel usage.
     * This is similar to traditional socket programming but uses NIO channels.
     */
    private static void blockingSocketChannelExample() {
        System.out.println("\n1. Blocking SocketChannel Example:");

        try (SocketChannel socketChannel = SocketChannel.open()) {
            // Connect to a server (using httpbin.org as example)
            socketChannel.connect(new InetSocketAddress("httpbin.org", 80));

            // Prepare HTTP GET request
            String httpRequest = "GET /get HTTP/1.1\r\n" +
                               "Host: httpbin.org\r\n" +
                               "Connection: close\r\n\r\n";

            // Write request to channel
            ByteBuffer requestBuffer = ByteBuffer.wrap(httpRequest.getBytes(StandardCharsets.UTF_8));
            socketChannel.write(requestBuffer);

            // Read response
            ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
            StringBuilder response = new StringBuilder();

            while (socketChannel.read(responseBuffer) > 0) {
                responseBuffer.flip();
                response.append(StandardCharsets.UTF_8.decode(responseBuffer));
                responseBuffer.clear();
            }

            System.out.println("Response received (first 200 chars):");
            System.out.println(response.substring(0, Math.min(200, response.length())));

        } catch (IOException e) {
            System.err.println("Error in blocking socket example: " + e.getMessage());
        }
    }

    /**
     * Demonstrates non-blocking SocketChannel usage.
     * This shows how to handle connection and I/O operations asynchronously.
     */
    private static void nonBlockingSocketChannelExample() {
        System.out.println("\n2. Non-blocking SocketChannel Example:");

        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            // Attempt to connect (may not complete immediately)
            boolean connected = socketChannel.connect(new InetSocketAddress("httpbin.org", 80));

            if (!connected) {
                System.out.println("Connection initiated, waiting for completion...");
                // In a real application, you would use a Selector here
                while (!socketChannel.finishConnect()) {
                    System.out.print(".");
                    Thread.sleep(100); // Simulate other work
                }
                System.out.println("\nConnection established!");
            }

            // Prepare and send HTTP request
            String httpRequest = "GET /get HTTP/1.1\r\n" +
                               "Host: httpbin.org\r\n" +
                               "Connection: close\r\n\r\n";

            ByteBuffer requestBuffer = ByteBuffer.wrap(httpRequest.getBytes(StandardCharsets.UTF_8));

            // Write request (may not write everything in one call)
            while (requestBuffer.hasRemaining()) {
                int bytesWritten = socketChannel.write(requestBuffer);
                if (bytesWritten == 0) {
                    // In a real application, you would register for OP_WRITE with a Selector
                    Thread.sleep(10);
                }
            }

            // Read response
            ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
            StringBuilder response = new StringBuilder();

            while (true) {
                int bytesRead = socketChannel.read(responseBuffer);
                if (bytesRead > 0) {
                    responseBuffer.flip();
                    response.append(StandardCharsets.UTF_8.decode(responseBuffer));
                    responseBuffer.clear();
                } else if (bytesRead == -1) {
                    break; // End of stream
                } else {
                    // No data available, in a real app you'd use a Selector
                    Thread.sleep(10);
                }
            }

            System.out.println("Non-blocking response received (first 200 chars):");
            System.out.println(response.substring(0, Math.min(200, response.length())));

            socketChannel.close();

        } catch (IOException | InterruptedException e) {
            System.err.println("Error in non-blocking socket example: " + e.getMessage());
        }
    }
}
