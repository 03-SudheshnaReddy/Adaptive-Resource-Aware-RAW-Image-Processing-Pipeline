package stages;

import java.awt.image.BufferedImage;

/**
 * CPU image filters (reference implementation).
 * Pure Java, no external deps. All methods return a NEW BufferedImage.
 */
public final class FiltersCPU {

    private FiltersCPU() {
    }

    // ---------------- Core helpers ----------------

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    private static float clampf(float v, float lo, float hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    // ---------------- Basic adjustments ----------------

    /**
     * Brightness/contrast adjustment.
     * brightness: +/- N in [ -100 .. +100 ] (mapped to [-1..+1] add)
     * contrast: +/- N in [ -100 .. +100 ] mapped to scale = 2^(N/50)
     */
    public static BufferedImage applyBrightnessContrast(BufferedImage src, int brightness, int contrast) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        float add = brightness / 100.0f; // [-1..+1]
        float scale = (float) Math.pow(2.0, contrast / 50.0); // contrast scale

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = p & 0xFF;

                float rf = clampf(r / 255.0f * scale + add, 0f, 1f);
                float gf = clampf(g / 255.0f * scale + add, 0f, 1f);
                float bf = clampf(b / 255.0f * scale + add, 0f, 1f);

                int R = (int) (rf * 255.0f);
                int G = (int) (gf * 255.0f);
                int B = (int) (bf * 255.0f);

                out.setRGB(x, y, (a << 24) | (R << 16) | (G << 8) | B);
            }
        }
        return out;
    }

    /** Convert to grayscale using BT.709 luma. */
    public static BufferedImage toGray(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = p & 0xFF;
                int y8 = clamp((int) (0.2126 * r + 0.7152 * g + 0.0722 * b), 0, 255);
                out.setRGB(x, y, (a << 24) | (y8 << 16) | (y8 << 8) | y8);
            }
        }
        return out;
    }

    // ---------------- Creative filters ----------------

    /** Invert colors (negative). */
    public static BufferedImage invert(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = 255 - ((p >>> 16) & 0xFF);
                int g = 255 - ((p >>> 8) & 0xFF);
                int b = 255 - (p & 0xFF);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /** Sepia tone. */
    public static BufferedImage sepia(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = p & 0xFF;

                int tr = clamp((int) (0.393 * r + 0.769 * g + 0.189 * b), 0, 255);
                int tg = clamp((int) (0.349 * r + 0.686 * g + 0.168 * b), 0, 255);
                int tb = clamp((int) (0.272 * r + 0.534 * g + 0.131 * b), 0, 255);

                out.setRGB(x, y, (a << 24) | (tr << 16) | (tg << 8) | tb);
            }
        }
        return out;
    }

    /** Sharpen via 3×3 kernel; strength in ~[0..1]. */
    public static BufferedImage sharpen(BufferedImage src, float strength) {
        if (strength < 0f)
            strength = 0f;
        float[] k = new float[] {
                0f, -strength, 0f,
                -strength, 1f + 4f * strength, -strength,
                0f, -strength, 0f
        };
        return convolve3x3(src, k);
    }

    /** Small Gaussian blur; radius ~0..3. */
    public static BufferedImage gaussianBlur(BufferedImage src, float radius) {
        // Fixed 3×3 Gaussian-like kernel scaled by radius
        float s = Math.max(0.2f, Math.min(3f, radius));
        float a = 1f * s, b = 2f * s, c = 4f * s;
        float sum = (a + b + a) + (b + c + b) + (a + b + a);
        float inv = 1f / sum;
        float[] k = new float[] {
                a * inv, b * inv, a * inv,
                b * inv, c * inv, b * inv,
                a * inv, b * inv, a * inv
        };
        return convolve3x3(src, k);
    }

    /** Sobel edge magnitude (grayscale). */
    public static BufferedImage edgeDetect(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage gray = toGray(src);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int[] gx = { -1, 0, 1, -2, 0, 2, -1, 0, 1 };
        int[] gy = { -1, -2, -1, 0, 0, 0, 1, 2, 1 };

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int k = 0, sx = 0, sy = 0;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        int p = gray.getRGB(x + i, y + j) & 0xFF;
                        sx += gx[k] * p;
                        sy += gy[k] * p;
                        k++;
                    }
                }
                int mag = clamp((int) Math.hypot(sx, sy), 0, 255);
                int argb = (0xFF << 24) | (mag << 16) | (mag << 8) | mag;
                out.setRGB(x, y, argb);
            }
        }

        // copy borders unchanged
        for (int x = 0; x < w; x++) {
            out.setRGB(x, 0, src.getRGB(x, 0));
            out.setRGB(x, h - 1, src.getRGB(x, h - 1));
        }
        for (int y = 0; y < h; y++) {
            out.setRGB(0, y, src.getRGB(0, y));
            out.setRGB(w - 1, y, src.getRGB(w - 1, y));
        }
        return out;
    }

    // ---------------- Convolution helper ----------------

    /** 3×3 convolution (row-major kernel of length 9); preserves alpha. */
    private static BufferedImage convolve3x3(BufferedImage src, float[] k) {
        if (k == null || k.length != 9)
            throw new IllegalArgumentException("kernel must be length 9");

        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                float rf = 0, gf = 0, bf = 0;
                int a = (src.getRGB(x, y) >>> 24) & 0xFF;
                int t = 0;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        int p = src.getRGB(x + i, y + j);
                        rf += ((p >>> 16) & 0xFF) * k[t];
                        gf += ((p >>> 8) & 0xFF) * k[t];
                        bf += (p & 0xFF) * k[t];
                        t++;
                    }
                }
                int R = clamp(Math.round(rf), 0, 255);
                int G = clamp(Math.round(gf), 0, 255);
                int B = clamp(Math.round(bf), 0, 255);
                out.setRGB(x, y, (a << 24) | (R << 16) | (G << 8) | B);
            }
        }

        // copy borders unchanged
        for (int x = 0; x < w; x++) {
            out.setRGB(x, 0, src.getRGB(x, 0));
            out.setRGB(x, h - 1, src.getRGB(x, h - 1));
        }
        for (int y = 0; y < h; y++) {
            out.setRGB(0, y, src.getRGB(0, y));
            out.setRGB(w - 1, y, src.getRGB(w - 1, y));
        }
        return out;
    }
}
