package util;

public class Timing {
    long t0 = System.nanoTime();

    public void stop(String label) {
        long dt = System.nanoTime() - t0;
        double ms = dt / 1_000_000.0;
        System.out.printf("%s: %.2f ms%n", label, ms);
        t0 = System.nanoTime();
    }
}
