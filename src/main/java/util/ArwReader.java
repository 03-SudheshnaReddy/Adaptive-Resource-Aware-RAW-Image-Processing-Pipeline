package util;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArwReader {
    private ArwReader() {
    }

    /**
     * Load embedded JPEG preview from a Sony .ARW.
     * Note: Some ARWs use JPEG features Java's default ImageIO can't decode (e.g.,
     * SOF 0xC3/0xC6).
     */
    public static BufferedImage loadPreview(Path arwPath) throws IOException {
        try {
            // naive approach: ImageIO can sometimes read the embedded preview directly
            return ImageIO.read(Files.newInputStream(arwPath));
        } catch (IIOException iioe) {
            // Provide helpful, assignment-friendly guidance
            String msg = "RAW preview decode failed. Many Sony .ARW files use JPEG variants " +
                    "that Java ImageIO can't read (e.g., lossless JPEG SOF 0xC3/0xC6).\n" +
                    "Options:\n" +
                    "  1) Provide a JPEG/PNG for testing the pipeline (allowed by assignment), or\n" +
                    "  2) Install a RAW decoder/codec and extract the embedded preview to JPEG, then run the pipeline, or\n"
                    +
                    "  3) Extend the pipeline to implement real RAW demosaic (bonus work).\n" +
                    "Technical note: original error = " + iioe.getMessage();
            throw new IOException(msg, iioe);
        } catch (IOException ioe) {
            throw new IOException("Failed to read RAW file: " + arwPath + " — " + ioe.getMessage(), ioe);
        }
    }
}
