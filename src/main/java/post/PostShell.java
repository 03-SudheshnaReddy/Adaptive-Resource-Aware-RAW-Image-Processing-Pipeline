package post;

import pipeline.PipelineOrchestrator;
import hw.DisplayService;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class PostShell {
    private final PipelineOrchestrator orchestrator;
    private BufferedImage image;
    private Path lastPath;

    public PostShell(PipelineOrchestrator o, BufferedImage current, Path shown) {
        this.orchestrator = o;
        this.image = current;
        this.lastPath = shown;
    }

    public void run() {
        System.out.println("\nPost-processing shell. Commands:");
        System.out.println("  brighten <int>   e.g., brighten 10");
        System.out.println("  contrast <int>   e.g., contrast -5");
        System.out.println("  sharpen <float>  e.g., sharpen 0.6");
        System.out.println("  bw");
        System.out.println("  save <name.png>");
        System.out.println("  quit\n");

        try (var br = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("post> ");
                String line = br.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.isEmpty())
                    continue;
                if (line.equalsIgnoreCase("quit"))
                    break;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();
                String[] args = (parts.length > 1) ? java.util.Arrays.copyOfRange(parts, 1, parts.length)
                        : new String[0];

                switch (cmd) {
                    case "save" -> {
                        String name = (args.length > 0) ? args[0] : "output.png";
                        lastPath = DisplayService.saveAndOpen(image, name);
                        System.out.println("Saved: " + lastPath.toAbsolutePath());
                    }
                    default -> {
                        image = orchestrator.postProcess(image, cmd, args);
                        lastPath = DisplayService.saveAndOpen(image, "preview.png");
                        System.out.println("Updated preview.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Shell error: " + e.getMessage());
        }
    }
}
