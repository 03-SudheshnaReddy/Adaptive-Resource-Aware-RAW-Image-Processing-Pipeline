package pipeline;

import hw.BatteryMonitor;
import hw.MemoryGuard;
import stages.FiltersCPUFast;
import stages.GpuProcessor;
import util.Tiles;
import util.SpillStore;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PipelineOrchestrator {

    private final boolean onACStart;
    private final int batteryStart;
    private final boolean userWantsGPU; // what user requested (flag/property/CLI)
    @SuppressWarnings("unused")
    private final String quality;

    // live GPU permission based on power policy (updated by scaler thread)
    private volatile boolean gpuAllowed;

    private final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

    public PipelineOrchestrator(boolean onAC, int battery, boolean useGPU, String quality) {
        this.onACStart = onAC;
        this.batteryStart = battery;
        this.userWantsGPU = useGPU;
        this.quality = quality;

        // Initial GPU policy: if user asked for GPU AND (on AC or battery >= 30)
        this.gpuAllowed = decideGpuAllowed(onACStart, batteryStart, userWantsGPU);
    }

    // ---- policy helpers ----
    private int threadsFromPolicy(boolean onAC, int battery) {
        if (onAC || battery >= 80)
            return Math.min(cores * 2, cores + 4);
        if (battery >= 40)
            return cores;
        return Math.max(1, cores / 2);
    }

    private static final int BATTERY_GPU_MIN = 30; // threshold

    private static boolean decideGpuAllowed(boolean onAC, int battery, boolean userWantsGPU) {
        // Require both: user asked for GPU AND battery > 30, regardless of AC
        if (!userWantsGPU)
            return false;
        return battery > BATTERY_GPU_MIN; // or >= if you prefer inclusive
    }

    // private static boolean decideGpuAllowed(boolean onAC, int battery, boolean
    // userWantsGPU) {
    // if (!userWantsGPU)
    // return false;
    // // Auto toggle: if on battery and < 30%, prefer CPU
    // if (!onAC && battery < 30)
    // return false;
    // return true;
    // }

    public BufferedImage process(BufferedImage src, int initBright, int initContrast) throws InterruptedException {
        long t0 = System.nanoTime();

        // Tiling
        final int TILE_W = 512, TILE_H = 512;
        List<Tiles.Tile> tiles = Tiles.split(src, TILE_W, TILE_H);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);

        // Memory guard + local spill counter (so we don’t have to change SpillStore)
        final long avgTileBytes = MemoryGuard.estimateARGBBytes(TILE_W, TILE_H);
        MemoryGuard mem = new MemoryGuard(0.6 /* 60% of free heap */, 256 * 1024 /* overhead guess */);
        AtomicInteger spills = new AtomicInteger(0);

        SpillStore spillTmp;
        try {
            spillTmp = new SpillStore();
        } catch (Exception e) {
            spillTmp = null;
        }
        final SpillStore spillRef = spillTmp; // FINAL ref for lambdas

        // Initial pool
        int threads = threadsFromPolicy(onACStart, batteryStart);
        ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(Math.max(2, cores));
        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                threads, threads, 60, TimeUnit.SECONDS, queue,
                new ThreadPoolExecutor.CallerRunsPolicy());

        // Live scaler thread: updates threads AND gpuAllowed based on live battery/AC
        final Thread scaler = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    boolean onAC = BatteryMonitor.onAC();
                    int bat = BatteryMonitor.levelOrGuess();
                    int target = threadsFromPolicy(onAC, bat);
                    if (target != exec.getCorePoolSize()) {
                        exec.setCorePoolSize(target);
                        exec.setMaximumPoolSize(target);
                        System.out.println("Scaler: target threads = " + target);
                    }
                    boolean newGpuAllowed = decideGpuAllowed(onAC, bat, userWantsGPU);
                    if (newGpuAllowed != gpuAllowed) {
                        gpuAllowed = newGpuAllowed;
                        System.out.println(
                                "Scaler: GPU allowed = " + gpuAllowed + " (onAC=" + onAC + ", bat=" + bat + "%)");
                    }
                    Thread.sleep(5000);
                }
            } catch (InterruptedException ignored) {
            }
        }, "battery-scaler");
        scaler.setDaemon(true);
        scaler.start();

        CountDownLatch latch = new CountDownLatch(tiles.size());
        AtomicLong inFlight = new AtomicLong(0);

        try {
            for (Tiles.Tile t : tiles) {
                boolean doSpill = (spillRef != null) && mem.shouldSpill(inFlight.get() + 1, avgTileBytes);

                if (doSpill) {
                    // Producer: spill tile to SSD first
                    SpillStore.Handle h;
                    try {
                        h = spillRef.spill(t.image(), t.x(), t.y());
                        spills.incrementAndGet();
                    } catch (Exception ioEx) {
                        submitInRam(exec, t, initBright, initContrast, out, inFlight, latch);
                        continue;
                    }

                    // Consumer: read from SSD, process, copy, then delete
                    final SpillStore.Handle fh = h; // effectively final for lambda
                    exec.submit(() -> {
                        inFlight.incrementAndGet();
                        try {
                            BufferedImage reloaded = spillRef.load(fh);
                            BufferedImage processed;
                            if (gpuAllowed) {
                                processed = GpuProcessor.applyBrightnessContrast(reloaded, initBright, initContrast);
                            } else {
                                processed = FiltersCPUFast.applyBrightnessContrast(reloaded, initBright, initContrast);
                            }
                            Tiles.copy(processed, out, fh.x, fh.y);
                        } catch (Exception e) {
                            // best effort
                        } finally {
                            spillRef.remove(fh);
                            inFlight.decrementAndGet();
                            latch.countDown();
                        }
                    });
                } else {
                    submitInRam(exec, t, initBright, initContrast, out, inFlight, latch);
                }
            }

            latch.await();

            long totalMs = Math.round((System.nanoTime() - t0) / 1e6);
            // ---- Runtime metrics summary ----
            System.out.printf(
                    "Stats: threads=%d tiles=%d spilled=%d gpuAllowed=%s total=%d ms%n",
                    exec.getCorePoolSize(), tiles.size(), spills.get(), gpuAllowed, totalMs);

            return out;
        } finally {
            exec.shutdown();
            try {
                exec.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            scaler.interrupt();
            if (spillRef != null)
                spillRef.cleanup();
        }
    }

    private void submitInRam(ThreadPoolExecutor exec, Tiles.Tile t, int initBright, int initContrast,
            BufferedImage out, AtomicLong inFlight, CountDownLatch latch) {
        exec.submit(() -> {
            inFlight.incrementAndGet();
            try {
                BufferedImage tile;
                if (gpuAllowed) {
                    tile = GpuProcessor.applyBrightnessContrast(t.image(), initBright, initContrast);
                } else {
                    tile = FiltersCPUFast.applyBrightnessContrast(t.image(), initBright, initContrast);
                }
                Tiles.copy(tile, out, t.x(), t.y());
            } finally {
                inFlight.decrementAndGet();
                latch.countDown();
            }
        });
    }

    // Post-processing (interactive)
    public BufferedImage postProcess(BufferedImage current, String op, String[] args) {
        return switch (op.toLowerCase()) {
            case "brighten" -> FiltersCPUFast.applyBrightnessContrast(current, parse(args, 0, 10), 0);
            case "contrast" -> FiltersCPUFast.applyBrightnessContrast(current, 0, parse(args, 0, 10));
            case "sharpen" -> FiltersCPUFast.sharpen(current, parseF(args, 0, 0.6f));
            case "bw" -> FiltersCPUFast.toGray(current);
            case "invert" -> stages.FiltersCPU.invert(current);
            case "sepia" -> stages.FiltersCPU.sepia(current);
            case "blur" -> stages.FiltersCPU.gaussianBlur(current, parseF(args, 0, 1.0f));
            case "edge" -> stages.FiltersCPU.edgeDetect(current);
            default -> current;
        };
    }

    private static int parse(String[] a, int i, int def) {
        try {
            return Integer.parseInt(a[i]);
        } catch (Exception e) {
            return def;
        }
    }

    private static float parseF(String[] a, int i, float def) {
        try {
            return Float.parseFloat(a[i]);
        } catch (Exception e) {
            return def;
        }
    }
}
