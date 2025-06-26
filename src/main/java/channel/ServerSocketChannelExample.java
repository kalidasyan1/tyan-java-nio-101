package channel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Demonstrates ServerSocketChannel for creating a simple non-blocking server
 * that accepts connections and sends responses to clients.
 */
public class ServerSocketChannelExample {

    public static void main(String[] args) {
        System.out.println("=== ServerSocketChannel Example ===");
        serverSocketChannelExample();
    }

    /**
     * Demonstrates ServerSocketChannel for creating a simple server.
     */
    private static void serverSocketChannelExample() {
        System.out.println("\nServerSocketChannel operations demonstration:");

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(8080));
            serverChannel.configureBlocking(false);

            System.out.println("Server started on port 8080 (non-blocking mode)");
            System.out.println("Waiting for connections for 5 seconds...");

            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 5000) {
                SocketChannel clientChannel = serverChannel.accept();

                if (clientChannel != null) {
                    System.out.println("Client connected: " + clientChannel.getRemoteAddress());

                    // Send a simple response
                    String response = "Hello from NIO Server!\n";
                    ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                    clientChannel.write(buffer);

                    clientChannel.close();
                    System.out.println("Response sent and connection closed");
                } else {
                    // No connection available, do other work
                    Thread.sleep(100);
                }
            }

            System.out.println("Server example completed");

        } catch (IOException | InterruptedException e) {
            System.err.println("ServerSocketChannel error: " + e.getMessage());
        }
    }
}
