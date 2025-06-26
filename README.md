# Java NIO 101 - Comprehensive Examples

A comprehensive collection of Java NIO (New I/O) examples demonstrating Channels and Selectors for high-performance, non-blocking I/O operations.

## Project Structure

```
src/main/java/
├── channel/
│   ├── SocketChannelExample.java        # Basic socket operations
│   ├── FileChannelExample.java          # File I/O operations
│   ├── ServerSocketChannelExample.java  # Simple server implementation
│   └── ChannelTransferExample.java      # Channel-to-channel transfers
└── selector/
    ├── SelectorExample.java             # Basic multiplexed I/O
    └── AdvancedSelectorExample.java     # Advanced server with client management
```

## Channel Examples

### 1. SocketChannel (`SocketChannelExample.java`)

**Key Concepts:**
- **Blocking vs Non-blocking modes**: Demonstrates both approaches to socket communication
- **Connection establishment**: Shows how to connect to remote servers
- **Data transmission**: HTTP request/response handling with ByteBuffer operations
- **Buffer management**: Proper use of `flip()`, `clear()`, and `remaining()`

**Usage Patterns:**
```java
// Blocking mode (default)
SocketChannel channel = SocketChannel.open();
channel.connect(new InetSocketAddress("example.com", 80));

// Non-blocking mode
channel.configureBlocking(false);
boolean connected = channel.connect(address);
while (!channel.finishConnect()) {
    // Do other work while connecting
}
```

### 2. FileChannel (`FileChannelExample.java`)

**Key Concepts:**
- **File I/O operations**: Reading and writing files using NIO channels
- **Random access**: Positional read/write operations at specific file offsets
- **StandardOpenOption**: Proper file opening modes (CREATE, READ, WRITE, TRUNCATE_EXISTING)
- **Resource management**: Try-with-resources for automatic cleanup

**Usage Patterns:**
```java
// Writing to file
try (FileChannel writeChannel = FileChannel.open(path, 
        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
    ByteBuffer buffer = ByteBuffer.wrap(data.getBytes());
    writeChannel.write(buffer);
}

// Random access operations
channel.read(buffer, position);  // Read from specific position
channel.write(buffer, position); // Write to specific position
```

### 3. ServerSocketChannel (`ServerSocketChannelExample.java`)

**Key Concepts:**
- **Server creation**: Setting up non-blocking servers that can accept multiple connections
- **Client acceptance**: Handling incoming connections without blocking
- **Connection lifecycle**: Proper setup, communication, and cleanup of client connections
- **Non-blocking polling**: Checking for new connections without blocking the thread

**Usage Patterns:**
```java
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.bind(new InetSocketAddress(port));
serverChannel.configureBlocking(false);

// Non-blocking accept
SocketChannel clientChannel = serverChannel.accept();
if (clientChannel != null) {
    // Handle new client
}
```

### 4. Channel Transfer (`ChannelTransferExample.java`)

**Key Concepts:**
- **Efficient data copying**: Direct channel-to-channel transfers without intermediate buffers
- **transferTo() method**: Copying data from source channel to destination
- **transferFrom() method**: Copying data to destination channel from source
- **Performance optimization**: Zero-copy operations for large file transfers

**Usage Patterns:**
```java
// Transfer from source to destination
long transferred = sourceChannel.transferTo(0, sourceChannel.size(), destChannel);

// Transfer to destination from source
long transferred = destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
```

## Selector Examples

### 1. Basic Selector (`SelectorExample.java`)

**Key Concepts:**
- **Multiplexed I/O**: Handling multiple channels with a single thread
- **Selection operations**: ACCEPT, READ, WRITE interest operations
- **Event-driven programming**: Responding to I/O events as they occur
- **Selection loop**: The core pattern of NIO servers

**Core Pattern:**
```java
Selector selector = Selector.open();
channel.register(selector, SelectionKey.OP_READ);

while (true) {
    int readyChannels = selector.select();
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    
    for (SelectionKey key : selectedKeys) {
        if (key.isAcceptable()) handleAccept(key);
        if (key.isReadable()) handleRead(key);
        if (key.isWritable()) handleWrite(key);
    }
    selectedKeys.clear();
}
```

### 2. Advanced Selector (`AdvancedSelectorExample.java`)

**Key Concepts:**
- **Client state management**: Tracking individual client information and connection state
- **Command processing**: Interactive server with multiple command types
- **Connection tracking**: Using ConcurrentHashMap for thread-safe client management
- **Periodic tasks**: Server maintenance and status reporting
- **Attachment usage**: Storing client-specific data with SelectionKey

**Advanced Features:**
- Client ID assignment and tracking
- Connection uptime monitoring
- Interactive command system (time, uptime, clients, help, quit)
- Proper resource cleanup and connection management
- Non-blocking server status reporting

## Key NIO Concepts Demonstrated

### ByteBuffer Operations
- **Buffer states**: Position, limit, and capacity management
- **Buffer flipping**: `flip()` for switching between read/write modes
- **Buffer clearing**: `clear()` for reusing buffers
- **Remaining data**: `hasRemaining()` for checking available data

### Non-blocking I/O Benefits
- **Scalability**: Handle thousands of connections with few threads
- **Resource efficiency**: Reduced memory and CPU overhead
- **Responsiveness**: Applications remain responsive during I/O operations
- **Flexibility**: Mix blocking and non-blocking operations as needed

### Error Handling and Resource Management
- **Try-with-resources**: Automatic cleanup of channels and selectors
- **Exception handling**: Proper IOException and InterruptedException handling
- **Resource cleanup**: Manual cleanup when automatic cleanup isn't sufficient
- **Connection state validation**: Checking channel state before operations

## Running the Examples

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher

### Compilation
```bash
mvn compile
```

### Running Individual Examples
```bash
# Channel examples
mvn exec:java -Dexec.mainClass="channel.SocketChannelExample"
mvn exec:java -Dexec.mainClass="channel.FileChannelExample"
mvn exec:java -Dexec.mainClass="channel.ServerSocketChannelExample"
mvn exec:java -Dexec.mainClass="channel.ChannelTransferExample"

# Selector examples
mvn exec:java -Dexec.mainClass="selector.SelectorExample"
mvn exec:java -Dexec.mainClass="selector.AdvancedSelectorExample"
```

## Learning Path

1. **Start with Channels**: Understand basic channel operations and ByteBuffer usage
2. **File Operations**: Learn FileChannel for file I/O and random access
3. **Network Operations**: Explore SocketChannel and ServerSocketChannel
4. **Channel Transfers**: Understand efficient data copying between channels
5. **Basic Selector**: Learn multiplexed I/O with simple examples
6. **Advanced Selector**: Study complex server implementations with state management

## Performance Considerations

- **Buffer reuse**: Reuse ByteBuffer instances to reduce garbage collection
- **Channel transfers**: Use `transferTo()`/`transferFrom()` for large file operations
- **Selector timeouts**: Use appropriate timeout values in `select()` calls
- **Connection pooling**: Reuse connections when possible
- **Memory mapping**: Consider memory-mapped files for very large file operations

## Common Patterns

### Non-blocking Server Pattern
1. Create ServerSocketChannel and configure non-blocking mode
2. Register with Selector for ACCEPT operations
3. Main loop: select(), iterate keys, handle operations
4. Handle ACCEPT: accept new clients, register for READ
5. Handle READ: read data, process, register for WRITE if response needed
6. Handle WRITE: write response data, switch back to READ mode

### Client-Server Communication
1. Use ByteBuffer for all data transfers
2. Always check return values of read/write operations
3. Handle partial reads/writes in non-blocking mode
4. Implement proper connection lifecycle management
5. Use attachment for storing channel-specific state

## References

Based on the comprehensive tutorials from:
- [Java NIO Channels](https://jenkov.com/tutorials/java-nio/channels.html)
- [Java NIO Selectors](https://jenkov.com/tutorials/java-nio/selectors.html)
- [Java NIO SocketChannel](https://jenkov.com/tutorials/java-nio/socketchannel.html)

## License

This project is provided as educational material for learning Java NIO concepts.
