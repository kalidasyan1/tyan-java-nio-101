package channel;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Demonstrates FileChannel operations for file I/O including reading,
 * writing, and random access operations.
 */
public class FileChannelExample {

    public static void main(String[] args) {
        System.out.println("=== FileChannel Example ===");
        fileChannelExample();
    }

    /**
     * Demonstrates FileChannel operations for file I/O.
     */
    private static void fileChannelExample() {
        System.out.println("\nFileChannel operations demonstration:");

        Path tempFile = Paths.get("temp-example.txt");

        try {
            // Writing to a file using FileChannel
            try (FileChannel writeChannel = FileChannel.open(tempFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                String content = "Hello, Java NIO FileChannel!\nThis is a demonstration of file operations.";
                ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));

                writeChannel.write(buffer);
                System.out.println("Data written to file: " + tempFile.toAbsolutePath());
            }

            // Reading from a file using FileChannel
            try (FileChannel readChannel = FileChannel.open(tempFile, StandardOpenOption.READ)) {
                ByteBuffer buffer = ByteBuffer.allocate(1024);

                int bytesRead = readChannel.read(buffer);
                buffer.flip();

                String readContent = StandardCharsets.UTF_8.decode(buffer).toString();
                System.out.println("Data read from file (" + bytesRead + " bytes):");
                System.out.println(readContent);
            }

            // Using RandomAccessFile with FileChannel
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(tempFile.toFile(), "rw");
                 FileChannel channel = randomAccessFile.getChannel()) {

                // Read from specific position
                ByteBuffer buffer = ByteBuffer.allocate(5);
                channel.read(buffer, 7); // Read 5 bytes starting from position 7
                buffer.flip();

                String partial = StandardCharsets.UTF_8.decode(buffer).toString();
                System.out.println("Partial read from position 7: '" + partial + "'");

                // Write at specific position
                ByteBuffer writeBuffer = ByteBuffer.wrap(" [INSERTED]".getBytes(StandardCharsets.UTF_8));
                channel.write(writeBuffer, 6);

                System.out.println("Text inserted at position 6");
            }

        } catch (IOException e) {
            System.err.println("FileChannel error: " + e.getMessage());
        } finally {
            // Clean up
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                System.err.println("Failed to delete temp file: " + e.getMessage());
            }
        }
    }
}
