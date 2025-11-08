package hw;

//import java.nio.file.Path;

public class MemoryGuard {
    // Soft cap as a fraction of *free* heap at process start (adjust if you like)
    private final long softCapBytes;
    private final long tileOverheadBytes;

    public MemoryGuard(double fractionOfFree, long tileOverheadBytes) {
        long freeAtStart = Runtime.getRuntime().maxMemory()
                - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        if (freeAtStart <= 0)
            freeAtStart = 256L * 1024 * 1024; // fallback 256MB
        this.softCapBytes = (long) Math.max(64L * 1024 * 1024, freeAtStart * fractionOfFree); // at least 64MB
        this.tileOverheadBytes = tileOverheadBytes;
    }

    /** Predict if accepting another tile in memory risks crossing soft cap. */
    public boolean shouldSpill(long inFlightTiles, long avgTileBytes) {
        long predicted = inFlightTiles * (avgTileBytes + tileOverheadBytes);
        return predicted > softCapBytes;
    }

    /** Helper to estimate ARGB tile memory (very close to actual). */
    public static long estimateARGBBytes(int w, int h) {
        return (long) w * (long) h * 4L; // 4 bytes per pixel (ARGB_8888)
    }

    public long getSoftCapBytes() {
        return softCapBytes;
    }
}
