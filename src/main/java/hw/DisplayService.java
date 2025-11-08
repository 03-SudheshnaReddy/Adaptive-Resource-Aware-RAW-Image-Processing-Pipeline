package hw;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;

public class DisplayService {
    public static Path save(BufferedImage img, String name) throws IOException {
        Path p = Paths.get(name).toAbsolutePath();
        ImageIO.write(img, "png", p.toFile());
        return p;
    }

    public static Path saveAndOpen(BufferedImage img, String name) throws IOException {
        Path p = save(img, name);
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().open(p.toFile());
        } catch (Exception ignored) {
        }
        return p;
    }
}
