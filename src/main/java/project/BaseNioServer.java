package project;

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
 * Base NIO server class that provides common server functionality.
 * Extracted from AdvancedSelectorExample to avoid code duplication.
 */
public abstract class BaseNioServer<T extends BaseNioServer.BaseClientInfo> {

    protected final int port;
    protected final AtomicInteger connectionCounter = new AtomicInteger(0);
    protected final ConcurrentHashMap<SocketChannel, T> clients = new ConcurrentHashMap<>();
    protected volatile boolean running = false;

    /**
     * Base client information class.
     */
    public static class BaseClientInfo {
        protected final int connectionId;
        protected final String remoteAddress;
        protected final long connectTime;
        protected ByteBuffer readBuffer;
        protected ByteBuffer writeBuffer;

        public BaseClientInfo(int connectionId, String remoteAddress) {
            this.connectionId = connectionId;
            this.remoteAddress = remoteAddress;
            this.connectTime = System.currentTimeMillis();
            this.readBuffer = ByteBuffer.allocate(8192);
        }

        public int getClientId() { return connectionId; } // Keep for backward compatibility
        public int getConnectionId() { return connectionId; }
        public String getRemoteAddress() { return remoteAddress; }
        public long getConnectTime() { return connectTime; }
        public ByteBuffer getReadBuffer() { return readBuffer; }
        public ByteBuffer getWriteBuffer() { return writeBuffer; }
        public void setWriteBuffer(ByteBuffer buffer) { this.writeBuffer = buffer; }
    }

    public BaseNioServer(int port) {
        this.port = port;
    }

    /**
     * Get the total number of active connections.
     */
    public int getActiveConnectionCount() {
        return clients.size();
    }

    /**
     * Get total connections made since server started.
     */
    public int getTotalConnectionCount() {
        return connectionCounter.get();
    }

    /**
     * Start the server in a new thread.
     */
    public Thread startServer() {
        Thread serverThread = new Thread(this::runServer);
        serverThread.start();
        return serverThread;
    }

    /**
     * Stop the server gracefully.
     */
    public void stopServer() {
        running = false;
    }

    /**
     * Main server loop.
     */
    protected void runServer() {
        System.out.println("Starting " + getServerName() + " on port " + port);
        running = true;

        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            // Configure server
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println(getServerName() + " ready for connections");

            while (running && !Thread.currentThread().isInterrupted()) {
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
            if (running && !Thread.currentThread().isInterrupted()) {
                System.err.println(getServerName() + " error: " + e.getMessage());
            }
        }

        System.out.println(getServerName() + " stopped");
    }

    /**
     * Process all selected keys.
     */
    private void processSelectedKeys(Selector selector) throws IOException {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            keyIterator.remove();

            try {
                if (key.isValid()) {
                    if (key.isAcceptable()) {
                        handleAccept(key, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error processing key: " + e.getMessage());
                cleanupKey(key);
            }
        }
    }

    /**
     * Handle new client connections.
     */
    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            int connectionId = connectionCounter.incrementAndGet();
            String remoteAddress = clientChannel.getRemoteAddress().toString();

            // Extract IP address only (remove port)

          T clientInfo = createClientInfo(connectionId, remoteAddress);

            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(clientInfo);

            clients.put(clientChannel, clientInfo);

            System.out.println("New connection (ID: " + connectionId + ") from " + remoteAddress +
                             " (Active connections: " + clients.size() + ")");

            onClientConnected(clientInfo, clientChannel);
        }
    }

    /**
     * Handle reading from clients.
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        @SuppressWarnings("unchecked")
        T clientInfo = (T) key.attachment();

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

            processClientData(clientInfo, data, key);

        } else if (bytesRead == -1) {
            // Client disconnected
            System.out.println("Client " + clientInfo.getClientId() + " disconnected");
            onClientDisconnected(clientInfo, clientChannel);
            cleanupKey(key);
        }
    }

    /**
     * Handle writing to clients.
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        @SuppressWarnings("unchecked")
        T clientInfo = (T) key.attachment();

        if (clientInfo == null || clientInfo.getWriteBuffer() == null) {
            cleanupKey(key);
            return;
        }

        ByteBuffer buffer = clientInfo.getWriteBuffer();
        clientChannel.write(buffer);

        if (!buffer.hasRemaining()) {
            onWriteComplete(clientInfo, key);
        }
    }

    /**
     * Clean up a selection key and associated resources.
     */
    protected void cleanupKey(SelectionKey key) {
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
    protected void cleanupDisconnectedClients() {
        clients.entrySet().removeIf(entry -> !entry.getKey().isOpen());
    }

    /**
     * Report server status periodically.
     */
    protected void reportServerStatus() {
        if (!clients.isEmpty()) {
            System.out.println(getServerName() + " status: " + clients.size() + " active connections");
        }
    }

    // Abstract methods to be implemented by subclasses
    protected abstract String getServerName();
    protected abstract T createClientInfo(int clientId, String remoteAddress);
    protected abstract void processClientData(T clientInfo, String data, SelectionKey key) throws IOException;

    // Hook methods with default implementations
    protected void onClientConnected(T clientInfo, SocketChannel channel) {
        // Default: do nothing
    }

    protected void onClientDisconnected(T clientInfo, SocketChannel channel) {
        // Default: do nothing
    }

    protected void onWriteComplete(T clientInfo, SelectionKey key) {
        // Default: switch back to read mode
        key.interestOps(SelectionKey.OP_READ);
    }
}
