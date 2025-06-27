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

/**
 * Demonstrates the usage of Java NIO Selector for multiplexed I/O operations.
 * This example shows how to handle multiple channels with a single thread.
 */
public class SelectorExample {

  private static final int PORT = 8081;
  private static final String SERVER_ADDRESS = "localhost";

  /**
   * Client state to manage separate read and write buffers
   * Simplified for one-message-at-a-time scenario
   */
  private static class ClientState {
    private ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;

    public ClientState() {
      this.readBuffer = ByteBuffer.allocate(1024);
      this.writeBuffer = null;
    }

    public ByteBuffer getReadBuffer() {
      return readBuffer;
    }

    public ByteBuffer getWriteBuffer() {
      return writeBuffer;
    }

    public void setWriteBuffer(ByteBuffer buffer) {
      this.writeBuffer = buffer;
    }

    public boolean hasWriteData() {
      return writeBuffer != null && writeBuffer.hasRemaining();
    }
  }

  public static void main(String[] args) {
    System.out.println("=== Selector Examples ===");

    // Start server in a separate thread
    Thread serverThread = new Thread(() -> runServer());
    serverThread.start();

    // Wait a bit for server to start
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Run client examples
    runMultipleClients();

    // Stop server thread
    serverThread.interrupt();
  }

  /**
   * Demonstrates a simple NIO server using Selector to handle multiple clients.
   */
  private static void runServer() {
    System.out.println("\n1. Starting NIO Server with Selector:");

    try (Selector selector = Selector.open(); ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

      // Configure server channel
      serverChannel.bind(new InetSocketAddress(PORT));
      serverChannel.configureBlocking(false);

      // Register server channel with selector for accept operations
      serverChannel.register(selector, SelectionKey.OP_ACCEPT);

      System.out.println("Server listening on port " + PORT);

      while (!Thread.currentThread().isInterrupted()) {
        // Select channels that are ready for I/O operations
        int readyChannels = selector.select(1000); // Timeout of 1 second

        if (readyChannels == 0) {
          continue; // No channels ready
        }

        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
          SelectionKey key = keyIterator.next();
          keyIterator.remove();

          if (key.isAcceptable()) {
            handleAccept(key, selector);
          } else if (key.isReadable()) {
            handleRead(key);
          } else if (key.isWritable()) {
            handleWrite(key);
          }
        }
      }
    } catch (IOException e) {
      if (!Thread.currentThread().isInterrupted()) {
        System.err.println("Server error: " + e.getMessage());
      }
    }

    System.out.println("Server stopped");
  }

  /**
   * Handles new client connections.
   */
  private static void handleAccept(SelectionKey key, Selector selector) throws IOException {
    ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
    SocketChannel clientChannel = serverChannel.accept();

    if (clientChannel != null) {
      System.out.println("New client connected: " + clientChannel.getRemoteAddress());

      clientChannel.configureBlocking(false);
      // Create client state and attach to the key
      ClientState clientState = new ClientState();
      SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
      clientKey.attach(clientState);
    }
  }

  /**
   * Handles reading data from client channels.
   * Simplified version assuming one message per read and client waits for response.
   */
  private static void handleRead(SelectionKey key) throws IOException {
    SocketChannel clientChannel = (SocketChannel) key.channel();
    ClientState clientState = (ClientState) key.attachment();

    if (clientState == null) {
      clientState = new ClientState();
      key.attach(clientState);
    }

    ByteBuffer readBuffer = clientState.getReadBuffer();

    try {
      int bytesRead = clientChannel.read(readBuffer);

      if (bytesRead > 0) {
        // Process the received data
        readBuffer.flip();
        String message = StandardCharsets.UTF_8.decode(readBuffer).toString().trim();
        readBuffer.clear(); // Clear buffer for next message

        if (!message.isEmpty()) {
          System.out.println("Received message: " + message);

          // Prepare response
          String response = "Echo: " + message + "\n";
          ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
          clientState.setWriteBuffer(writeBuffer);

          // Switch to write mode
          key.interestOps(SelectionKey.OP_WRITE);
        }
      } else if (bytesRead == -1) {
        // Client disconnected
        System.out.println("Client disconnected: " + clientChannel.getRemoteAddress());
        key.cancel();
        clientChannel.close();
      }
      // bytesRead == 0 means no data available, just continue

    } catch (IOException e) {
      System.err.println("Error reading from client: " + e.getMessage());
      key.cancel();
      clientChannel.close();
    }
  }

  /**
   * Handles writing data to client channels.
   */
  private static void handleWrite(SelectionKey key) throws IOException {
    SocketChannel clientChannel = (SocketChannel) key.channel();
    ClientState clientState = (ClientState) key.attachment();

    if (clientState == null || !clientState.hasWriteData()) {
      // Nothing to write, switch back to read mode
      key.interestOps(SelectionKey.OP_READ);
      return;
    }

    ByteBuffer writeBuffer = clientState.getWriteBuffer();

    try {
      int bytesWritten = clientChannel.write(writeBuffer);

      if (!writeBuffer.hasRemaining()) {
        // Current buffer fully written, clear the buffer
        clientState.setWriteBuffer(null);

        // Switch back to read mode
        key.interestOps(SelectionKey.OP_READ);
      } else if (bytesWritten == 0) {
        // Socket buffer full, stay in write mode and try again later
        // The selector will notify us when the channel is ready for write again
      }
    } catch (IOException e) {
      System.err.println("Error writing to client: " + e.getMessage());
      key.cancel();
      clientChannel.close();
    }
  }

  /**
   * Demonstrates multiple clients connecting to the server.
   */
  private static void runMultipleClients() {
    System.out.println("\n2. Running Multiple Clients:");

    // Create multiple client threads
    for (int i = 1; i <= 3; i++) {
      final int clientId = i;
      Thread clientThread = new Thread(() -> runClient(clientId));
      clientThread.start();
    }

    // Wait for clients to complete
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Runs a single client that connects to the server.
   */
  private static void runClient(int clientId) {
    try (SocketChannel clientChannel = SocketChannel.open()) {
      clientChannel.connect(new InetSocketAddress(SERVER_ADDRESS, PORT));

      // Send message to server
      String message = "Hello from client " + clientId + "!\n";
      ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
      clientChannel.write(buffer);

      // Read response from server
      ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
      int bytesRead = clientChannel.read(responseBuffer);

      if (bytesRead > 0) {
        responseBuffer.flip();
        String response = StandardCharsets.UTF_8.decode(responseBuffer).toString();
        System.out.println("Client " + clientId + " received: " + response);
      }
    } catch (IOException e) {
      System.err.println("Client " + clientId + " error: " + e.getMessage());
    }
  }
}
