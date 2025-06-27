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

## Project Package Examples

### 1. AdvancedHttpServer (`AdvancedHttpServer.java`)

**A production-ready HTTP/1.1 server built on BaseNioServer with full request parsing, multiple endpoints, and connection tracking.**

**Key Features:**
- **HTTP/1.1 implementation**: Complete request parsing and response generation
- **Multiple endpoints**: `/`, `/time`, `/clients`, `/uptime`, `/health`, 404 handling
- **Connection tracking**: Unique connection IDs and statistics monitoring
- **Keep-alive support**: HTTP connection reuse for better performance
- **Extensible design**: Template method pattern via BaseNioServer inheritance

**Usage Pattern:**
```java
// Start the server
AdvancedHttpServer server = new AdvancedHttpServer();
server.startServer(); // Runs on port 8080
// Server runs indefinitely until manually stopped
```

### 2. BaseNioServer (`BaseNioServer.java`)

**Key Concepts:**
- **Abstract base class**: Provides common NIO server functionality for reuse
- **Generic client info**: Type-safe client information management with generics
- **Connection lifecycle**: Handles accept, read, write, and cleanup operations
- **Resource management**: Automatic cleanup of disconnected clients and proper resource disposal
- **Template method pattern**: Subclasses implement specific behavior while base handles common operations

**Extension Points:**
```java
// Implement these abstract methods in subclasses
protected abstract String getServerName();
protected abstract T createClientInfo(int connectionId, String remoteAddress);
protected abstract void processClientData(T clientInfo, String data, SelectionKey key);

// Optional hook methods
protected void onClientConnected(T clientInfo, SocketChannel channel);
protected void onClientDisconnected(T clientInfo, SocketChannel channel);
protected void onWriteComplete(T clientInfo, SelectionKey key);
```

### 3. HttpClientRunner (`HttpClientRunner.java`)

**Key Features:**
- **Modern HTTP client**: Uses Java 11+ HttpClient for efficient HTTP operations
- **Synchronous and asynchronous requests**: Demonstrates both blocking and non-blocking HTTP patterns
- **Multiple client simulation**: Creates 3 different logical clients with unique User-Agent headers
- **Configurable testing**: Can test any HTTP server on specified port
- **Connection patterns**: Shows different request patterns (sequential, parallel, mixed)

**Client Types:**
- **Modern-Client-1**: Synchronous requests to `/`, `/time`, `/clients`
- **Modern-Client-2**: Asynchronous requests to `/health`, `/uptime`, `/nonexistent`
- **Modern-Client-3**: Mixed synchronous and asynchronous requests

**Usage Pattern:**
```java
// Test a server running on specific port
HttpClientRunner.runModernHttpClients(8080);

// Or run standalone against any server
java -cp target/classes project.HttpClientRunner [port]
```

## Running the Project Examples

### Prerequisites
- Java 11 or higher (required for HttpClient in HttpClientRunner)
- Maven 3.6 or higher

### Compilation
```bash
mvn compile
```

### Running the Advanced HTTP Server & Client System

#### Option 1: Run Server and Client Separately (Recommended)

**Step 1: Start the HTTP Server**
```bash
# Terminal 1 - Start the server (runs indefinitely)
mvn exec:java -Dexec.mainClass="project.AdvancedHttpServer"
```

**Step 2: Test with HTTP Client**
```bash
# Terminal 2 - Run the HTTP client tests
mvn exec:java -Dexec.mainClass="project.HttpClientRunner"

# Or test against a different port
mvn exec:java -Dexec.mainClass="project.HttpClientRunner" -Dexec.args="8080"
```

**Step 3: Test with Web Browser**
```bash
# Open your web browser and visit:
http://localhost:8080/
http://localhost:8080/time
http://localhost:8080/clients
http://localhost:8080/health
```

#### Option 2: Test with curl
```bash
# Test different endpoints
curl http://localhost:8080/
curl http://localhost:8080/time
curl http://localhost:8080/clients
curl http://localhost:8080/health

# Test with custom User-Agent
curl -H "User-Agent: TestClient" http://localhost:8080/
```

### Expected Output

**Server Log Example:**
```
=== Advanced HTTP Server Example ===
Starting Advanced HTTP Server on port 8080
Advanced HTTP Server ready for connections
New connection (ID: 1) from /127.0.0.1:52341 (Active connections: 1)
Connection 1: GET /
New connection (ID: 2) from /127.0.0.1:52342 (Active connections: 1)
Connection 2: GET /time
Advanced HTTP Server status: 1 active connections
```

**Client Log Example:**
```
=== HTTP Client Runner ===
Testing HTTP server on port: 8080
Starting modern HTTP clients...
Modern-Client-1 - GET / -> 200 OK
Modern-Client-2 (async) - GET /health -> 200 OK
Modern-Client-3 - GET / -> 200 OK
```

### Running Traditional Channel Examples
```bash
# Channel examples
mvn exec:java -Dexec.mainClass="channel.SocketChannelExample"
mvn exec:java -Dexec.mainClass="channel.FileChannelExample"
mvn exec:java -Dexec.mainClass="channel.ServerSocketChannelExample"
mvn exec:java -Dexec.mainClass="channel.ChannelTransferExample"
```

### Running Traditional Selector Examples
```bash
# Selector examples
mvn exec:java -Dexec.mainClass="selector.SelectorExample"
mvn exec:java -Dexec.mainClass="selector.AdvancedSelectorExample"
```

## Architecture Insights

### Production-Ready Server Design

**Separation of Concerns:**
- **BaseNioServer**: Generic NIO server infrastructure
- **AdvancedHttpServer**: HTTP-specific protocol implementation
- **HttpClientRunner**: Testing and demonstration client

**Connection Management:**
- Each connection gets a unique ID for tracking and debugging
- Separate read/write buffers prevent data corruption
- Proper resource cleanup prevents memory leaks
- Connection statistics for monitoring and debugging

**HTTP Implementation:**
- Full HTTP request parsing with headers and methods
- Proper HTTP response formatting with standard headers
- Content-Length calculation for binary safety
- Connection keep-alive support for performance

### Client Testing Strategy

**Multiple Client Types:**
- Different User-Agent headers to simulate distinct clients
- Mix of synchronous and asynchronous request patterns
- Various endpoints to test different server functionality
- Error handling for network issues and HTTP errors

**Real-World Testing:**
- Can test against any HTTP server (not just the provided one)
- Configurable port for testing different environments
- Proper timeout handling for production scenarios



## Recent Updates & Key Learnings

### Buffer Management Best Practices

**Critical Issue: Buffer Sharing and Overwriting**
- **Problem**: Using the same buffer attachment for both read and write operations can cause data corruption
- **Solution**: Separate read and write buffers using a client state object
- **Lesson**: Always maintain separate buffers for different I/O operations to prevent data loss

### Message Framing in NIO Applications

**Read Buffer Reuse vs Write Buffer Creation**
- **Read buffers**: Should be reused (allocated once per client) for memory efficiency
- **Write buffers**: Created fresh for each response to match variable message sizes
- **Rationale**: Read operations are predictable (fixed buffer size), write operations are variable (response-dependent size)

### Selector Event Handling Insights

**When Channels Become Ready:**
- **Read Ready**: When data arrives in socket receive buffer, client disconnects, or after being previously blocked
- **Write Ready**: When socket send buffer has space (usually always), after partial writes complete, or initially after connection

**Partial Read/Write Handling:**
- `read()` doesn't guarantee all available data is read - may need multiple read cycles
- Remaining data in OS socket buffer triggers immediate selector notification
- Must loop until `read()` returns 0 or handle partial data with `compact()`

### Message Framing Strategies

**Complete vs Partial Messages:**
- TCP is a stream protocol - messages can arrive fragmented across multiple read operations
- Use delimiters (like newlines) to identify complete messages
- `buffer.compact()` preserves partial data for the next read cycle
- For simplified scenarios: assume one message per read when clients wait for responses

### Simplified vs Complex Implementations

**Trade-offs Demonstrated:**
- **Complex**: Full message framing with queues handles any client behavior
- **Simple**: Single message assumption reduces complexity but limits client interaction patterns
- **Choice depends on**: Application requirements, expected client behavior, and performance needs

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

