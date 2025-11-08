package stages;

import java.awt.image.BufferedImage;

/**
 * Faster CPU filters designed for HotSpot auto-vectorization:
 * - Uses 256-entry LUTs (no float math per pixel)
 * - Processes whole rows via int[] (contiguous memory)
 * - Simple loops the JIT can auto-vectorize
 *
 * API compatible with FiltersCPU for easy drop-in.
 */
public class FiltersCPUFast {

    /** Brightness [-100..100], Contrast [-100..100] */
    public static BufferedImage applyBrightnessContrast(BufferedImage src, int brightness, int contrast) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        // Build LUT once (256 entries)
        int[] lut = bcLut(brightness, contrast);

        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);

            // Tight loop over contiguous int[] -> hot for auto-vectorization
            for (int x = 0; x < w; x++) {
                int p = row[x];

                int a = (p >>> 24);
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = (p) & 0xFF;

                // LUT mapping does the brightness/contrast transform
                r = lut[r];
                g = lut[g];
                b = lut[b];

                row[x] = (a << 24) | (r << 16) | (g << 8) | b;
            }

            dst.setRGB(0, y, w, 1, row, 0, w);
        }
        return dst;
    }

    /** Grayscale (fast integer luma) */
    public static BufferedImage toGray(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            for (int i = 0; i < w; i++) {
                int p = row[i];
                int a = (p >>> 24);
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = (p) & 0xFF;
                // Fast luma: 0.2126, 0.7152, 0.0722 ≈ 54, 183, 19 (sum 256)
                int y8 = (54 * r + 183 * g + 19 * b) >>> 8;
                row[i] = (a << 24) | (y8 << 16) | (y8 << 8) | y8;
            }
            dst.setRGB(0, y, w, 1, row, 0, w);
        }
        return dst;
    }

    /** Sharpen: reuse the existing CPU implementation (good quality) */
    public static BufferedImage sharpen(BufferedImage src, float amount) {
        return FiltersCPU.sharpen(src, amount);
    }

    // --- helpers ---

    /** Build LUT for brightness/contrast mapping. */
    private static int[] bcLut(int brightness, int contrast) {
        // brightness: add in [−1..+1], contrast: scale (2^(c/50))
        float bf = brightness / 100.0f;
        float cf = (float) Math.pow(2.0, contrast / 50.0);

        int[] lut = new int[256];
        for (int v = 0; v < 256; v++) {
            // v' = clamp((v/255 * cf + bf) * 255)
            int mapped = (int) ((v * cf) + (bf * 255f));
            if (mapped < 0)
                mapped = 0;
            else if (mapped > 255)
                mapped = 255;
            lut[v] = mapped;
        }
        return lut;
    }
}
