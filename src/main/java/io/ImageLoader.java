package io;

import util.ArwReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;

public final class ImageLoader {
    private ImageLoader() {
    }

    public static BufferedImage load(Path input) throws IOException {
        String name = input.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".arw")) {
                System.out.println("Detected RAW (.ARW) file — attempting to load embedded JPEG preview…");
                return ArwReader.loadPreview(input);
            } else {
                return ImageIO.read(Files.newInputStream(input));
            }
        } catch (IOException e) {
            // Print friendly message and rethrow so CLI can exit gracefully
            System.err.println("[ImageLoader] " + e.getMessage());
            throw e;
        }
    }
}
