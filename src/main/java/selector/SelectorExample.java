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
      // Register client channel for read operations
      clientChannel.register(selector, SelectionKey.OP_READ);
    }
  }

  /**
   * Handles reading data from client channels.
   */
  /**
   * Handles reading data from client channels.
   */
  private static void handleRead(SelectionKey key) throws IOException {
    SocketChannel clientChannel = (SocketChannel) key.channel();

    // Get or create buffer for this client
    ByteBuffer buffer = (ByteBuffer) key.attachment();
    if (buffer == null) {
      buffer = ByteBuffer.allocate(1024);
      key.attach(buffer);
    }

    try {
      int bytesRead = clientChannel.read(buffer);

      if (bytesRead > 0) {
        // Check for complete message (assuming newline-delimited messages)
        buffer.flip();
        String data = StandardCharsets.UTF_8.decode(buffer).toString();

        if (data.contains("\n")) {
          // Complete message received
          String message = data.trim();
          System.out.println("Received from client: " + message);

          // Prepare response and switch to write mode
          String response = "Echo: " + message + "\n";
          ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
          key.attach(responseBuffer);
          key.interestOps(SelectionKey.OP_WRITE);
        } else {
          // Incomplete message, continue reading
          buffer.compact(); // Prepare for more data
        }
      } else if (bytesRead == -1) {
        // Client disconnected
        System.out.println("Client disconnected: " + clientChannel.getRemoteAddress());
        key.cancel();
        clientChannel.close();
      }
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
    ByteBuffer buffer = (ByteBuffer) key.attachment();

    try {
      clientChannel.write(buffer);

      if (!buffer.hasRemaining()) {
        // All data written, switch back to read mode
        key.interestOps(SelectionKey.OP_READ);
        key.attach(null);
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
