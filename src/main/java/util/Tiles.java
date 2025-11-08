package util;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Tiles {
    public record Tile(BufferedImage image, int x, int y) {
    }

    public static List<Tile> split(BufferedImage src, int tw, int th) {
        int W = src.getWidth(), H = src.getHeight();
        List<Tile> tiles = new ArrayList<>();
        for (int y = 0; y < H; y += th) {
            for (int x = 0; x < W; x += tw) {
                int w = Math.min(tw, W - x), h = Math.min(th, H - y);
                BufferedImage sub = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                int[] row = new int[w];
                for (int yy = 0; yy < h; yy++) {
                    src.getRGB(x, y + yy, w, 1, row, 0, w);
                    sub.setRGB(0, yy, w, 1, row, 0, w);
                }
                tiles.add(new Tile(sub, x, y));
            }
        }
        return tiles;
    }

    public static void copy(BufferedImage tile, BufferedImage dst, int dx, int dy) {
        int[] row = new int[tile.getWidth()];
        for (int y = 0; y < tile.getHeight(); y++) {
            tile.getRGB(0, y, row.length, 1, row, 0, row.length);
            dst.setRGB(dx, dy + y, row.length, 1, row, 0, row.length);
        }
    }
}
