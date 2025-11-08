package util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;

public class SpillStore {

    public static final class Handle {
        private final Path path;
        public final int x, y, w, h;

        public Handle(Path p, int x, int y, int w, int h) {
            this.path = p;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public Path path() {
            return path;
        }
    }

    private final Path dir;

    public SpillStore() throws IOException {
        this.dir = Files.createTempDirectory("raw-pipeline-spill");
        this.dir.toFile().deleteOnExit();
    }

    /**
     * Write a tile as PNG to temp. (Simple & portable; you can switch to raw .bin
     * later for speed.)
     */
    public Handle spill(BufferedImage tile, int x, int y) throws IOException {
        Path p = Files.createTempFile(dir, "tile_", ".png");
        ImageIO.write(tile, "png", p.toFile());
        return new Handle(p, x, y, tile.getWidth(), tile.getHeight());
    }

    /** Read a tile back. */
    public BufferedImage load(Handle h) throws IOException {
        return ImageIO.read(h.path().toFile());
    }

    /** Delete a spilled tile when done. */
    public void remove(Handle h) {
        try {
            Files.deleteIfExists(h.path());
        } catch (Exception ignored) {
        }
    }

    /** Clean up entire spill dir (optional). */
    public void cleanup() {
        try (var s = Files.newDirectoryStream(dir)) {
            for (Path p : s)
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
        } catch (Exception ignored) {
        }
        try {
            Files.deleteIfExists(dir);
        } catch (Exception ignored) {
        }
    }
}
