package app;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import hw.BatteryMonitor;
import io.ImageLoader;
import pipeline.PipelineOrchestrator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command line entry for the RAW pipeline.
 * Example:
 * # CPU
 * gradlew run --args="--input=C:\path\file.ARW --brightness=10 --contrast=20"
 *
 * # GPU requested (also auto-disables on battery < 30%)
 * gradlew run -DuseGPU=true --args="--input=C:\path\file.ARW --brightness=10
 * --contrast=20"
 * # or installed app:
 * set "JAVA_TOOL_OPTIONS=-DuseGPU=true"
 * build\install\raw-pipeline\bin\raw-pipeline.bat --input="C:\path\file.ARW"
 * --brightness=10 --contrast=20
 */
public final class CLI {

    // -------------------- Args --------------------
    private static final class Args {
        @Parameter(names = "--input", description = "Input image path (.jpg/.png/.arw)", required = true)
        String input;

        @Parameter(names = "--brightness", description = "Brightness [-100..100]")
        int brightness = 0;

        @Parameter(names = "--contrast", description = "Contrast [-100..100]")
        int contrast = 0;

        @Parameter(names = "--quality", description = "preview | high")
        String quality = "preview";

        @Parameter(names = "--gpu", description = "Use GPU acceleration (OpenCL). Also honored via -DuseGPU=true")
        boolean gpu = false;

        @Parameter(names = { "-h", "--help" }, help = true, description = "Show help")
        boolean help = false;
    }

    public static void main(String[] argv) {
        Args args = new Args();
        JCommander jc = JCommander.newBuilder().addObject(args).build();
        try {
            jc.parse(argv);
        } catch (ParameterException pe) {
            System.err.println(pe.getMessage());
            jc.usage();
            System.exit(1);
        }
        if (args.help) {
            jc.usage();
            return;
        }

        // Read GPU preference from CLI flag OR JVM property (-DuseGPU=true)
        boolean userWantsGPU = args.gpu || Boolean.parseBoolean(System.getProperty("useGPU", "false"));

        // Resolve input path
        Path inPath = Paths.get(args.input);

        // Detect power state (for thread scaling + auto GPU policy in orchestrator)
        boolean onAC = BatteryMonitor.onAC();
        int battery = BatteryMonitor.levelOrGuess();

        // Banner
        System.out.println("== RAW Pipeline ==");
        System.out.println("Input: " + inPath.toString());
        System.out.println("GPU: " + userWantsGPU + "  Quality: " + args.quality);

        // Load image (ARW -> embedded JPEG preview, or regular PNG/JPG)
        BufferedImage inputImg;
        try {
            inputImg = ImageLoader.load(inPath);
        } catch (IOException e) {
            // Friendly message already printed by ImageLoader
            System.exit(2);
            return;
        }

        // Orchestrate
        PipelineOrchestrator orchestrator = new PipelineOrchestrator(onAC, battery, userWantsGPU, args.quality);

        long t0 = System.nanoTime();
        BufferedImage processed;
        try {
            processed = orchestrator.process(inputImg, args.brightness, args.contrast);
        } catch (InterruptedException e) {
            System.err.println("Processing interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return;
        }
        long totalMs = Math.round((System.nanoTime() - t0) / 1e6);

        // Write preview next to the input
        Path previewOut = inPath.getParent() != null
                ? inPath.getParent().resolve("preview.png")
                : Paths.get("preview.png");
        try {
            ImageIO.write(processed, "png", previewOut.toFile());
            System.out.println("Total processing: " + String.format("%.2f", (double) totalMs) + " ms");
            System.out.println("Preview written to: " + previewOut.toString());
        } catch (IOException e) {
            System.err.println("Failed to write preview: " + e.getMessage());
        }

        // ---- Post-processing interactive shell ----
        System.out.println();
        System.out.println("Post-processing shell. Commands:");
        System.out.println("  brighten <int>   e.g., brighten 10");
        System.out.println("  contrast <int>   e.g., contrast -5");
        System.out.println("  sharpen <float>  e.g., sharpen 0.6");
        System.out.println("  bw");
        System.out.println("  invert");
        System.out.println("  sepia");
        System.out.println("  blur <float>     e.g., blur 1.5");
        System.out.println("  edge");
        System.out.println("  save <name.png>");
        System.out.println("  quit");
        System.out.println();

        BufferedImage current = processed;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("post> ");
                String line = br.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();
                String[] par = parts.length > 1 ? java.util.Arrays.copyOfRange(parts, 1, parts.length)
                        : new String[0];

                switch (cmd) {
                    case "brighten":
                    case "contrast":
                    case "sharpen":
                    case "bw":
                    case "invert":
                    case "sepia":
                    case "blur":
                    case "edge": {
                        current = new pipeline.PipelineOrchestrator(onAC, battery, false, args.quality)
                                .postProcess(current, cmd, par);
                        System.out.println("Updated preview.");
                        break;
                    }
                    case "save": {
                        if (par.length == 0) {
                            System.out.println("Usage: save <name.png>");
                            break;
                        }
                        Path out = inPath.getParent() != null
                                ? inPath.getParent().resolve(par[0])
                                : Paths.get(par[0]);
                        try {
                            ImageIO.write(current, "png", out.toFile());
                            System.out.println("Saved: " + out.toString());
                        } catch (IOException e) {
                            System.out.println("Save failed: " + e.getMessage());
                        }
                        break;
                    }
                    case "quit":
                    case "exit":
                        return;
                    default:
                        System.out.println("Unknown command: " + cmd);
                }
            }
        } catch (IOException ioe) {
            System.err.println("Shell I/O error: " + ioe.getMessage());
        }
    }
}
