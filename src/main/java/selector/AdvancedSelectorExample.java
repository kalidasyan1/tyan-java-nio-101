package selector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced Selector example demonstrating a more realistic NIO server
 * with proper connection management and message handling.
 */
public class AdvancedSelectorExample {

    private static final int PORT = 8082;
    private static final AtomicInteger clientCounter = new AtomicInteger(0);
    private static final ConcurrentHashMap<SocketChannel, ClientInfo> clients = new ConcurrentHashMap<>();

    /**
     * Holds client-specific information.
     */
    private static class ClientInfo {
        private final int clientId;
        private final long connectTime;
        private ByteBuffer readBuffer;
        private ByteBuffer writeBuffer;

        public ClientInfo(int clientId) {
            this.clientId = clientId;
            this.connectTime = System.currentTimeMillis();
            this.readBuffer = ByteBuffer.allocate(1024);
        }

        public int getClientId() { return clientId; }
        public long getConnectTime() { return connectTime; }
        public ByteBuffer getReadBuffer() { return readBuffer; }
        public ByteBuffer getWriteBuffer() { return writeBuffer; }
        public void setWriteBuffer(ByteBuffer buffer) { this.writeBuffer = buffer; }
    }

    public static void main(String[] args) {
        System.out.println("=== Advanced Selector Example ===");

        // Start the advanced server
        Thread serverThread = new Thread(AdvancedSelectorExample::runAdvancedServer);
        serverThread.start();

        // Wait for server to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create multiple clients with different behaviors
        runAdvancedClients();

        // Let clients run for a while
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        serverThread.interrupt();
        System.out.println("Advanced Selector example completed");
    }

    /**
     * Runs an advanced NIO server with proper client management.
     */
    private static void runAdvancedServer() {
        System.out.println("\nStarting advanced NIO server on port " + PORT);

        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            // Configure server
            serverChannel.bind(new InetSocketAddress(PORT));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Advanced server ready for connections");

            while (!Thread.currentThread().isInterrupted()) {
                // Select with timeout to allow periodic cleanup
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
                System.err.println("Advanced server error: " + e.getMessage());
            }
        }

        System.out.println("Advanced server stopped");
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
                        handleAdvancedAccept(key, selector);
                    } else if (key.isReadable()) {
                        handleAdvancedRead(key);
                    } else if (key.isWritable()) {
                        handleAdvancedWrite(key);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error processing key: " + e.getMessage());
                cleanupKey(key);
            }
        }
    }

    /**
     * Handle new client connections with proper client tracking.
     */
    private static void handleAdvancedAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            int clientId = clientCounter.incrementAndGet();
            ClientInfo clientInfo = new ClientInfo(clientId);

            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(clientInfo);

            clients.put(clientChannel, clientInfo);

            System.out.println("Client " + clientId + " connected from " +
                             clientChannel.getRemoteAddress());

            // Send welcome message
            String welcome = "Welcome! You are client " + clientId + "\n";
            ByteBuffer welcomeBuffer = ByteBuffer.wrap(welcome.getBytes(StandardCharsets.UTF_8));
            clientInfo.setWriteBuffer(welcomeBuffer);
            clientKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    /**
     * Handle reading from clients with proper message parsing.
     */
    private static void handleAdvancedRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientInfo clientInfo = (ClientInfo) key.attachment();

        if (clientInfo == null) {
            cleanupKey(key);
            return;
        }

        ByteBuffer buffer = clientInfo.getReadBuffer();
        int bytesRead = clientChannel.read(buffer);

        if (bytesRead > 0) {
            buffer.flip();
            String message = StandardCharsets.UTF_8.decode(buffer).toString();
            buffer.clear();

            System.out.println("Client " + clientInfo.getClientId() + " says: " + message.trim());

            // Process different commands
            String response = processClientMessage(clientInfo, message.trim());

            if (response != null) {
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                clientInfo.setWriteBuffer(responseBuffer);
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }

        } else if (bytesRead == -1) {
            // Client disconnected
            System.out.println("Client " + clientInfo.getClientId() + " disconnected");
            cleanupKey(key);
        }
    }

    /**
     * Handle writing to clients.
     */
    private static void handleAdvancedWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientInfo clientInfo = (ClientInfo) key.attachment();

        if (clientInfo == null || clientInfo.getWriteBuffer() == null) {
            key.interestOps(SelectionKey.OP_READ);
            return;
        }

        ByteBuffer buffer = clientInfo.getWriteBuffer();
        clientChannel.write(buffer);

        if (!buffer.hasRemaining()) {
            // All data written
            clientInfo.setWriteBuffer(null);
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * Process client messages and return appropriate responses.
     */
    private static String processClientMessage(ClientInfo clientInfo, String message) {
        switch (message.toLowerCase()) {
            case "time":
                return "Current time: " + System.currentTimeMillis() + "\n";
            case "uptime":
                long uptime = System.currentTimeMillis() - clientInfo.getConnectTime();
                return "Your connection uptime: " + uptime + " ms\n";
            case "clients":
                return "Total connected clients: " + clients.size() + "\n";
            case "help":
                return "Available commands: time, uptime, clients, help, quit\n";
            case "quit":
                return "Goodbye!\n";
            default:
                return "Echo: " + message + "\n";
        }
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
            System.err.println("Error during cleanup: " + e.getMessage());
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
            System.out.println("Server status: " + clients.size() + " active clients");
        }
    }

    /**
     * Create advanced clients that interact with the server.
     */
    private static void runAdvancedClients() {
        System.out.println("\nStarting advanced clients...");

        // Client that asks for time
        new Thread(() -> runCommandClient("time")).start();

        // Client that checks uptime
        new Thread(() -> runCommandClient("uptime")).start();

        // Client that sends multiple commands
        new Thread(() -> {
            try (SocketChannel channel = SocketChannel.open()) {
                channel.connect(new InetSocketAddress("localhost", PORT));

                String[] commands = {"help", "clients", "time", "quit"};

                for (String command : commands) {
                    // Send command
                    ByteBuffer buffer = ByteBuffer.wrap((command + "\n").getBytes(StandardCharsets.UTF_8));
                    channel.write(buffer);

                    // Read response
                    ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
                    channel.read(responseBuffer);
                    responseBuffer.flip();
                    String response = StandardCharsets.UTF_8.decode(responseBuffer).toString();
                    System.out.println("Multi-command client received: " + response.trim());

                    Thread.sleep(500);
                }

            } catch (IOException | InterruptedException e) {
                System.err.println("Multi-command client error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Run a client that sends a specific command.
     */
    private static void runCommandClient(String command) {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(new InetSocketAddress("localhost", PORT));

            // Read welcome message
            ByteBuffer welcomeBuffer = ByteBuffer.allocate(1024);
            channel.read(welcomeBuffer);
            welcomeBuffer.flip();
            String welcome = StandardCharsets.UTF_8.decode(welcomeBuffer).toString();
            System.out.println("Client received welcome: " + welcome.trim());

            // Send command
            ByteBuffer commandBuffer = ByteBuffer.wrap((command + "\n").getBytes(StandardCharsets.UTF_8));
            channel.write(commandBuffer);

            // Read response
            ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
            channel.read(responseBuffer);
            responseBuffer.flip();
            String response = StandardCharsets.UTF_8.decode(responseBuffer).toString();
            System.out.println("Command '" + command + "' response: " + response.trim());

        } catch (IOException e) {
            System.err.println("Command client (" + command + ") error: " + e.getMessage());
        }
    }
}
