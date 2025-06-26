package channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Demonstrates channel-to-channel transfer operations using transferTo()
 * and transferFrom() methods for efficient data copying between channels.
 */
public class ChannelTransferExample {

    public static void main(String[] args) {
        System.out.println("=== Channel Transfer Example ===");
        channelTransferExample();
    }

    /**
     * Demonstrates channel-to-channel transfer operations.
     */
    private static void channelTransferExample() {
        System.out.println("\nChannel transfer operations demonstration:");

        Path sourceFile = Paths.get("source.txt");
        Path destFile = Paths.get("destination.txt");

        try {
            // Create source file
            String sourceContent = "This is source content for transfer demonstration.\n" +
                                 "Channel transfer is efficient for large files.\n" +
                                 "It can transfer data directly between channels.";
            Files.write(sourceFile, sourceContent.getBytes(StandardCharsets.UTF_8));

            // Transfer from source to destination using transferTo
            try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
                 FileChannel destChannel = FileChannel.open(destFile,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                long transferred = sourceChannel.transferTo(0, sourceChannel.size(), destChannel);
                System.out.println("Transferred " + transferred + " bytes using transferTo()");
            }

            // Verify the transfer
            String destContent = Files.readString(destFile, StandardCharsets.UTF_8);
            System.out.println("Destination file content:");
            System.out.println(destContent);

            // Demonstrate transferFrom
            Path dest2File = Paths.get("destination2.txt");
            try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
                 FileChannel destChannel = FileChannel.open(dest2File,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                long transferred = destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
                System.out.println("Transferred " + transferred + " bytes using transferFrom()");
            }

        } catch (IOException e) {
            System.err.println("Channel transfer error: " + e.getMessage());
        } finally {
            // Clean up
            try {
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(destFile);
                Files.deleteIfExists(Paths.get("destination2.txt"));
            } catch (IOException e) {
                System.err.println("Failed to clean up files: " + e.getMessage());
            }
        }
    }
}
