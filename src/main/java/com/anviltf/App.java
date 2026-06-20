package com.anviltf;

import com.anviltf.anvil.AnvilWorldWriter;
import com.anviltf.anvil.AtfWorldImporter;
import com.anviltf.anvil.ReferenceWorldRoundTripComparator;
import com.anviltf.anvil.ReferenceWorldValidator;
import com.anviltf.dsl.DslParser;
import com.anviltf.plan.PlanExpander;
import com.anviltf.render.RenderGrid;
import com.anviltf.render.WorldImageWriter;
import com.anviltf.render.WorldRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class App {
    private App() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "plan".equals(args[0])) {
            WorldSpec world = new DslParser().parse(Path.of(args[1]));
            System.out.println(new WorldPlanner().render(world));
            return;
        }
        if (args.length == 3 && "plan-expand".equals(args[0])) {
            PlanExpander.ExpansionReport report = new PlanExpander().expand(Path.of(args[1]), Path.of(args[2]));
            System.out.println("Expanded " + report.planFiles() + " plan file(s) to "
                    + report.outputDir().toAbsolutePath() + " as " + report.generatedCommands()
                    + " generated ATF line(s); copied " + report.copiedFiles() + " file(s)");
            return;
        }
        if (args.length == 2 && "plan-metrics".equals(args[0])) {
            planMetrics(Path.of(args[1]));
            return;
        }
        if (args.length == 2 && "plan-outline".equals(args[0])) {
            planOutline(Path.of(args[1]));
            return;
        }
        if (args.length == 2 && "plan-target-audit".equals(args[0])) {
            planTargetAudit(Path.of(args[1]));
            return;
        }
        if (args.length == 2 && "plan-index".equals(args[0])) {
            planIndex(Path.of(args[1]));
            return;
        }
        if (args.length >= 2 && "plan-workbench".equals(args[0])) {
            planWorkbench(args);
            return;
        }
        if (args.length >= 2 && "plan-snippet".equals(args[0])) {
            planSnippet(args);
            return;
        }
        if (args.length >= 2 && "plan-recipe".equals(args[0])) {
            planRecipe(args);
            return;
        }
        if (args.length >= 2 && "plan-report".equals(args[0])) {
            planReport(args);
            return;
        }
        if (args.length >= 2 && "plan-gates".equals(args[0])) {
            planGates(args);
            return;
        }
        if (args.length >= 3 && "plan-edit-trial".equals(args[0])) {
            planEditTrial(args);
            return;
        }
        if (args.length >= 3 && "plan-edit-suite".equals(args[0])) {
            planEditSuite(args);
            return;
        }
        if (args.length == 3 && "plan-edit-manifest".equals(args[0])) {
            planEditManifest(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length == 4 && "plan-edit-manifest-report".equals(args[0])) {
            planEditManifestReport(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            return;
        }
        if (args.length == 3 && "plan-eval-status".equals(args[0])) {
            planEvalStatus(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length == 4 && "plan-eval-status-report".equals(args[0])) {
            planEvalStatusReport(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            return;
        }
        if (args.length >= 3 && "plan-eval-audit".equals(args[0])) {
            planEvalAudit(args);
            return;
        }
        if (args.length >= 3 && "plan-eval-completion".equals(args[0])) {
            planEvalCompletion(args);
            return;
        }
        if (args.length >= 4 && "plan-eval-pack".equals(args[0])) {
            planEvalPack(args);
            return;
        }
        if (args.length >= 4 && "plan-eval-brief".equals(args[0])) {
            planEvalBrief(args);
            return;
        }
        if (args.length == 5 && "plan-eval-score".equals(args[0])) {
            planEvalScore(Path.of(args[1]), Path.of(args[2]), args[3], Path.of(args[4]));
            return;
        }
        if (args.length == 2 && "plan-diagnose".equals(args[0])) {
            var warnings = new PlanExpander().diagnose(Path.of(args[1]));
            if (warnings.isEmpty()) {
                System.out.println("Plan diagnostics OK: no warnings");
            } else {
                for (PlanExpander.PlanWarning warning : warnings) {
                    System.out.println("warning " + warning.code() + " " + warning.file()
                            + " " + warning.x() + " " + warning.z() + ": " + warning.message());
                }
                System.out.println("Plan diagnostics found " + warnings.size() + " warning(s)");
            }
            return;
        }
        if (args.length >= 2 && "plan-render".equals(args[0])) {
            planRender(args);
            return;
        }
        if (args.length == 2 && "validate".equals(args[0])) {
            validate(Path.of(args[1]));
            return;
        }
        if (args.length == 2 && "diagnose".equals(args[0])) {
            diagnose(Path.of(args[1]));
            return;
        }
        if (args.length == 2 && "source-metrics".equals(args[0])) {
            sourceMetrics(Path.of(args[1]));
            return;
        }
        if (args.length == 2 && "source-gates".equals(args[0])) {
            sourceGates(Path.of(args[1]));
            return;
        }
        if (args.length == 3 && "compare".equals(args[0])) {
            compare(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length >= 3 && "import".equals(args[0])) {
            importWorld(args);
            return;
        }
        if (args.length >= 2 && "render".equals(args[0])) {
            render(args);
            return;
        }
        if (args.length != 2 && args.length != 3) {
            usage();
            System.exit(2);
        }
        boolean validate = false;
        if (args.length == 3) {
            if (!"--validate".equals(args[2])) {
                usage();
                System.exit(2);
            }
            validate = true;
        }

        WorldSpec world = new DslParser().parse(Path.of(args[0]));
        printDiagnostics(world);
        Path output = Path.of(args[1]);
        new AnvilWorldWriter().write(world, output);
        System.out.println("Wrote world '" + world.name() + "' to " + output.toAbsolutePath());
        if (validate) {
            validate(output, world);
        }
    }

    private static void validate(Path worldDir) throws Exception {
        validate(worldDir, null);
    }

    private static void diagnose(Path input) throws Exception {
        WorldSpec world = new DslParser().parse(input);
        printDiagnostics(world);
    }

    private static void sourceMetrics(Path input) throws Exception {
        SourceMetrics metrics = collectSourceMetrics(input);
        printSourceMetrics(metrics);
    }

    private static SourceMetrics collectSourceMetrics(Path input) throws IOException {
        Path sourceRoot = Files.isDirectory(input)
                ? input.toAbsolutePath().normalize()
                : input.toAbsolutePath().normalize().getParent();
        List<Path> atfFiles;
        try (var paths = Files.walk(sourceRoot)) {
            atfFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList();
        }

        int totalLines = 0;
        int nonBlankLines = 0;
        int groupFiles = 0;
        int componentFiles = 0;
        int maxFileLines = 0;
        int maxGroupLines = 0;
        int maxComponentLines = 0;
        int includeLines = 0;
        Path largestFile = null;
        Path largestGroup = null;
        Path largestComponent = null;
        Map<String, Integer> commandCounts = new TreeMap<>();

        for (Path file : atfFiles) {
            Path relative = sourceRoot.relativize(file);
            List<String> lines = Files.readAllLines(file);
            int fileLines = lines.size();
            int fileNonBlank = 0;
            for (String line : lines) {
                String stripped = stripSourceComment(line).trim();
                if (stripped.isEmpty() || "}".equals(stripped)) {
                    continue;
                }
                fileNonBlank++;
                String command = firstSourceToken(stripped);
                if (!command.isEmpty()) {
                    commandCounts.merge(command, 1, Integer::sum);
                    if ("include".equals(command)) {
                        includeLines++;
                    }
                }
            }

            totalLines += fileLines;
            nonBlankLines += fileNonBlank;
            if (fileLines > maxFileLines) {
                maxFileLines = fileLines;
                largestFile = relative;
            }
            if (isGroupFile(relative)) {
                groupFiles++;
                if (fileNonBlank > maxGroupLines) {
                    maxGroupLines = fileNonBlank;
                    largestGroup = relative;
                }
            }
            if (isComponentFile(relative)) {
                componentFiles++;
                if (fileNonBlank > maxComponentLines) {
                    maxComponentLines = fileNonBlank;
                    largestComponent = relative;
                }
            }
        }

        return new SourceMetrics(sourceRoot, atfFiles.size(), groupFiles, componentFiles,
                totalLines, nonBlankLines, includeLines, maxFileLines,
                largestFile, maxGroupLines, largestGroup, maxComponentLines,
                largestComponent, commandCounts);
    }

    private static void printSourceMetrics(SourceMetrics metrics) {
        System.out.println("Source metrics for " + metrics.sourceRoot());
        System.out.println("atf_files: " + metrics.atfFiles());
        System.out.println("group_files: " + metrics.groupFiles());
        System.out.println("component_files: " + metrics.componentFiles());
        System.out.println("source_lines: " + metrics.sourceLines());
        System.out.println("nonblank_source_lines: " + metrics.nonBlankSourceLines());
        System.out.println("include_lines: " + metrics.includeLines());
        System.out.println("max_file_lines: " + metrics.maxFileLines()
                + (metrics.largestFile() == null ? "" : " (" + metrics.largestFile() + ")"));
        System.out.println("max_group_nonblank_lines: " + metrics.maxGroupNonBlankLines()
                + (metrics.largestGroup() == null ? "" : " (" + metrics.largestGroup() + ")"));
        System.out.println("max_component_nonblank_lines: " + metrics.maxComponentNonBlankLines()
                + (metrics.largestComponent() == null ? "" : " (" + metrics.largestComponent() + ")"));
        printMetricMap("source_commands", metrics.commandCounts());
    }

    private static void sourceGates(Path input) throws Exception {
        SourceMetrics metrics = collectSourceMetrics(input);
        Map<String, Integer> counts = metrics.commandCounts();
        List<PlanExpander.PlanGate> gates = new ArrayList<>();
        gates.add(gate("group_file_size",
                metrics.maxGroupNonBlankLines() <= 320,
                Integer.toString(metrics.maxGroupNonBlankLines()),
                "<=320 nonblank lines in largest group file",
                metrics.largestGroup() == null ? "" : metrics.largestGroup().toString()));
        gates.add(gate("component_file_size",
                metrics.maxComponentNonBlankLines() <= 220,
                Integer.toString(metrics.maxComponentNonBlankLines()),
                "<=220 nonblank lines in largest component file",
                metrics.largestComponent() == null ? "" : metrics.largestComponent().toString()));
        gates.add(gate("entrypoint_size",
                metrics.maxFileLines() <= 5000,
                Integer.toString(metrics.maxFileLines()),
                "<=5000 lines in largest file",
                metrics.largestFile() == null ? "" : metrics.largestFile().toString()));
        gates.add(gate("ordinary_groups",
                metrics.groupFiles() > 0 && counts.getOrDefault("group", 0) == metrics.groupFiles(),
                metrics.groupFiles() + " group file(s), " + counts.getOrDefault("group", 0) + " group command(s)",
                "group files are ordinary ATF groups",
                ""));
        gates.add(gate("component_extraction",
                metrics.componentFiles() > 0
                        && (counts.getOrDefault("place", 0) > 0 || counts.getOrDefault("cluster", 0) > 0),
                metrics.componentFiles() + " component file(s), " + counts.getOrDefault("place", 0)
                        + " placement(s), " + counts.getOrDefault("cluster", 0) + " cluster(s)",
                "repeated structures are represented as components with place commands or plan clusters",
                ""));
        gates.add(gate("block_compression",
                counts.getOrDefault("fill", 0) >= counts.getOrDefault("set", 0),
                counts.getOrDefault("fill", 0) + " fill(s), " + counts.getOrDefault("set", 0) + " set(s)",
                "fill commands are at least as common as raw set commands",
                ""));

        System.out.println("Source gates for " + metrics.sourceRoot());
        System.out.println("passed: " + countGates(gates, "pass"));
        System.out.println("failed: " + countGates(gates, "fail"));
        for (PlanExpander.PlanGate gate : gates) {
            String line = gate.status() + " " + gate.name()
                    + " value=" + gate.value()
                    + " threshold=" + gate.threshold();
            if (!gate.note().isEmpty()) {
                line += " note=" + gate.note();
            }
            System.out.println(line);
        }
    }

    private static void printDiagnostics(WorldSpec world) {
        var warnings = new WorldDiagnostics().diagnose(world);
        if (warnings.isEmpty()) {
            System.out.println("Diagnostics OK: no warnings");
            return;
        }
        for (WorldDiagnostics.Warning warning : warnings) {
            System.out.println("warning " + warning.code() + " " + warning.dimension()
                    + " " + warning.x() + " " + warning.y() + " " + warning.z()
                    + ": " + warning.message());
        }
        System.out.println("Diagnostics found " + warnings.size() + " warning(s)");
    }

    private static void validate(Path worldDir, WorldSpec expectedWorld) throws Exception {
        ReferenceWorldValidator validator = new ReferenceWorldValidator();
        ReferenceWorldValidator.ValidationReport report = expectedWorld == null
                ? validator.validate(worldDir)
                : validator.validate(worldDir, expectedWorld);
        System.out.println("Reference validation OK: " + report.regionCount()
                + " region file(s), " + report.chunkCount() + " chunk(s), "
                + report.blockStateChecks() + " block-state sample(s), "
                + report.biomeChecks() + " biome sample(s), "
                + report.authoredBlockChecks() + " authored block check(s), "
                + report.authoredBiomeChecks() + " authored biome check(s), "
                + report.blockEntityCount() + " validated block entity tag(s), "
                + report.entityCount() + " validated entity tag(s)"
                + (report.dimensionRegionCount() > 0
                ? ", " + report.dimensionRegionCount() + " dimension region file(s), "
                + report.dimensionChunkCount() + " dimension chunk(s)" : "")
                + (report.entityRegionCount() > 0
                ? ", " + report.entityRegionCount() + " external entity region file(s), "
                + report.entityRegionChunkCount() + " external entity chunk(s)" : "")
                + (report.poiRegionCount() > 0
                ? ", " + report.poiRegionCount() + " POI region file(s), "
                + report.poiChunkCount() + " POI chunk(s)" : "")
                + (report.dataNbtFileCount() > 0
                ? ", " + report.dataNbtFileCount() + " save data NBT file(s)" : ""));
    }

    private static void importWorld(String[] args) throws Exception {
        if (args.length < 3) {
            usage();
            System.exit(2);
        }
        int chunkLimit = 0;
        AtfWorldImporter.ImportLayout layout = AtfWorldImporter.ImportLayout.FLAT;
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--chunk-limit" -> {
                    if (i + 1 >= args.length) {
                        usage();
                        System.exit(2);
                    }
                    chunkLimit = Integer.parseInt(args[++i]);
                    if (chunkLimit <= 0) {
                        throw new IllegalArgumentException("--chunk-limit must be positive");
                    }
                }
                case "--layout" -> {
                    if (i + 1 >= args.length) {
                        usage();
                        System.exit(2);
                    }
                    String value = args[++i];
                    if ("reviewable".equals(value)) {
                        layout = AtfWorldImporter.ImportLayout.REVIEWABLE;
                    } else if ("flat".equals(value)) {
                        layout = AtfWorldImporter.ImportLayout.FLAT;
                    } else {
                        throw new IllegalArgumentException("--layout must be flat or reviewable");
                    }
                }
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }
        AtfWorldImporter.ImportReport report;
        try {
            report = new AtfWorldImporter().importWorld(
                    Path.of(args[1]), Path.of(args[2]), new AtfWorldImporter.ImportOptions(chunkLimit, layout));
        } catch (IllegalArgumentException error) {
            System.err.println("Import failed: " + error.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("Imported " + report.chunkCount() + " chunk(s) to " + Path.of(args[2]).toAbsolutePath()
                + " as " + report.blockCommands() + " block command(s), "
                + report.biomeCommands() + " biome command(s), "
                + report.blockEntities() + " block entity command(s), "
                + report.entities() + " entity command(s)"
                + (layout == AtfWorldImporter.ImportLayout.REVIEWABLE
                ? ", " + report.promotedComponents() + " component(s), "
                + report.componentPlacements() + " component placement(s)"
                : "")
                + (report.skippedNbt() > 0 ? ", skipped " + report.skippedNbt() + " unsupported NBT payload(s)" : "")
                + (report.truncated() ? ", truncated" : ""));
        if (layout == AtfWorldImporter.ImportLayout.REVIEWABLE) {
            System.out.println("Component analysis: " + report.componentCandidates()
                    + " candidate placement(s), " + report.promotedComponents()
                    + " promoted component(s), " + report.componentPlacements()
                    + " promoted placement(s)");
        }
        if (!report.unsupportedFolders().isEmpty()) {
            if (layout == AtfWorldImporter.ImportLayout.REVIEWABLE) {
                System.out.println("Import notes: non-block save data skipped in reviewable layout: "
                        + String.join(", ", report.unsupportedFolders()));
            } else {
                System.out.println("Import notes: raw passthrough save assets preserved: "
                        + String.join(", ", report.unsupportedFolders()));
            }
        }
    }

    private static void render(String[] args) throws Exception {
        if (args.length < 3) {
            usage();
            System.exit(2);
        }
        String subverb = args[1];
        Path input = Path.of(args[2]);

        String dimension = WorldSpec.OVERWORLD;
        Path out = null;
        int scale = 0;
        int max = 1024;
        boolean biome = false;

        int y = 0;
        char axis = 'x';
        int coord = 0;
        int flagStart;
        switch (subverb) {
            case "top" -> {
                if (args.length < 4) {
                    usage();
                    System.exit(2);
                }
                y = Integer.parseInt(args[3]);
                flagStart = 4;
            }
            case "map" -> flagStart = 3;
            case "slice" -> {
                if (args.length < 5) {
                    usage();
                    System.exit(2);
                }
                if (!"x".equals(args[3]) && !"z".equals(args[3])) {
                    throw new IllegalArgumentException("render slice axis must be x or z");
                }
                axis = args[3].charAt(0);
                coord = Integer.parseInt(args[4]);
                flagStart = 5;
            }
            default -> {
                usage();
                System.exit(2);
                return;
            }
        }

        for (int i = flagStart; i < args.length; i++) {
            switch (args[i]) {
                case "--dim" -> dimension = requireValue(args, ++i);
                case "--out" -> out = Path.of(requireValue(args, ++i));
                case "--scale" -> scale = Integer.parseInt(requireValue(args, ++i));
                case "--max" -> max = Integer.parseInt(requireValue(args, ++i));
                case "--biome" -> biome = true;
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }

        WorldSpec world = new DslParser().parse(input);
        WorldRenderer renderer = new WorldRenderer();
        RenderGrid grid = switch (subverb) {
            case "top" -> renderer.top(world, dimension, y);
            case "map" -> renderer.map(world, dimension, biome);
            case "slice" -> renderer.slice(world, dimension, axis, coord);
            default -> throw new IllegalStateException("unreachable");
        };

        if (grid.rows() == 0) {
            System.out.println("Nothing to render: no authored content in dimension " + dimension);
            return;
        }
        if (out == null) {
            System.out.println(renderer.toAscii(grid));
            return;
        }
        WorldImageWriter.WriteResult result = new WorldImageWriter().write(grid, out, scale, max);
        System.out.println("Wrote render to " + out.toAbsolutePath() + " ("
                + result.width() + "x" + result.height() + "px, scale=" + result.scale()
                + (result.downsampled() ? ", sampled 1/" + result.sample() + " blocks" : "")
                + ", " + grid.cols() + "x" + grid.rows() + " blocks)");
        System.out.print(result.legend());
    }

    private static void planMetrics(Path input) throws Exception {
        PlanExpander.PlanMetrics metrics = new PlanExpander().metrics(input);
        System.out.println("Plan metrics for " + input.toAbsolutePath());
        System.out.println("source_files: " + metrics.sourceFiles());
        System.out.println("atf_files: " + metrics.atfFiles());
        System.out.println("component_files: " + metrics.componentFiles());
        System.out.println("source_lines: " + metrics.sourceLines());
        System.out.println("nonblank_source_lines: " + metrics.nonBlankSourceLines());
        System.out.println("component_lines: " + metrics.componentLines());
        System.out.println("nonblank_component_lines: " + metrics.nonBlankComponentLines());
        System.out.println("plan_blocks: " + metrics.planBlocks());
        System.out.println("plan_lines: " + metrics.planLines());
        System.out.println("plan_area_cells: " + metrics.planAreaCells());
        System.out.println("max_plan_area_cells: " + metrics.maxPlanAreaCells());
        System.out.println("plan_areas_at_least_384: " + metrics.planAreasAtLeast384());
        System.out.println("plan_areas_at_least_512: " + metrics.planAreasAtLeast512());
        System.out.println("generated_atf_lines: " + metrics.generatedAtfLines());
        if (metrics.planLines() > 0) {
            System.out.println("generated_atf_lines_per_plan_line: "
                    + String.format(Locale.ROOT, "%.2f",
                    metrics.generatedAtfLines() / (double) metrics.planLines()));
        }
        printMetricMap("plan_commands", metrics.commandCounts());
        printMetricMap("visible_overlay_cells", metrics.overlayCells());
    }

    private static void planOutline(Path input) throws Exception {
        List<PlanExpander.PlanOutline> outlines = new PlanExpander().outline(input);
        System.out.println("Plan outline for " + input.toAbsolutePath());
        System.out.println("plan_blocks: " + outlines.size());
        for (PlanExpander.PlanOutline outline : outlines) {
            System.out.println("group " + quoteForOutput(outline.groupName())
                    + " file=" + outline.file()
                    + " origin=" + outline.originX() + "," + outline.originY() + "," + outline.originZ()
                    + " area=" + outline.minX() + "," + outline.minZ()
                    + " to " + outline.maxX() + "," + outline.maxZ()
                    + " styles=" + outline.styleCount()
                    + " masks=" + outline.maskCount()
                    + " fields=" + outline.fieldCount());
            System.out.println("  routes: " + outline.routes().size());
            for (PlanExpander.RouteOutline route : outline.routes()) {
                System.out.println("    " + quoteForOutput(route.name())
                        + " width=" + route.width()
                        + " points=" + route.points()
                        + " crossings=" + route.crossings()
                        + " first=" + route.first()
                        + " last=" + route.last());
            }
            System.out.println("  reservations: " + outline.reservations().size());
            for (PlanExpander.ReservationOutline reservation : outline.reservations()) {
                System.out.println("    " + quoteForOutput(reservation.name())
                        + " " + reservation.shape()
                        + " anchor=" + reservation.anchorX() + "," + reservation.anchorZ()
                        + " clear_to=" + reservation.clearTo()
                        + " require_route=" + reservation.requiredRouteDistance());
            }
            System.out.println("  landmarks: " + outline.landmarks().size());
            for (PlanExpander.LandmarkOutline landmark : outline.landmarks()) {
                System.out.println("    " + quoteForOutput(landmark.name())
                        + " component=" + landmark.component()
                        + " at=" + landmark.x() + "," + landmark.z()
                        + " reserve=" + quoteForOutput(landmark.reservation())
                        + " radius=" + landmark.radius()
                        + " height=" + landmark.height()
                        + " rotate=" + landmark.rotation());
            }
            System.out.println("  clusters: " + outline.clusters().size());
            for (PlanExpander.ClusterOutline cluster : outline.clusters()) {
                System.out.println("    " + quoteForOutput(cluster.name())
                        + " reserve=" + quoteForOutput(cluster.reservation())
                        + " members=" + cluster.members()
                        + " spacing=" + cluster.spacing());
            }
            System.out.println("  scatter: actions=" + outline.scatterActions()
                    + " requested=" + outline.scatterRequested());
            System.out.println("  loot: actions=" + outline.lootActions()
                    + " requested=" + outline.lootRequested());
        }
    }

    private static void planTargetAudit(Path input) throws Exception {
        PlanExpander expander = new PlanExpander();
        List<PlanExpander.PlanOutline> outlines = expander.outline(input);
        List<PlanExpander.PlanWarning> warnings = expander.diagnose(input);

        int checked = 0;
        int failedGroups = 0;
        System.out.println("Plan target-class audit for " + input.toAbsolutePath());
        System.out.println("plan_blocks: " + outlines.size());
        System.out.println("plan_warnings: " + warnings.size());
        for (PlanExpander.PlanWarning warning : warnings) {
            System.out.println("  warning " + warning.code() + " " + warning.file()
                    + " " + warning.x() + " " + warning.z() + ": " + warning.message());
        }

        for (PlanExpander.PlanOutline outline : outlines) {
            int width = outline.maxX() - outline.minX() + 1;
            int depth = outline.maxZ() - outline.minZ() + 1;
            TargetClassThreshold threshold = targetClassThreshold(width, depth);
            if (threshold == null) {
                System.out.println("skip group " + quoteForOutput(outline.groupName())
                        + " file=" + outline.file()
                        + " area=" + width + "x" + depth
                        + " note=below 384x384 target-class audit floor");
                continue;
            }

            checked++;
            int crossings = outline.routes().stream().mapToInt(PlanExpander.RouteOutline::crossings).sum();
            int clusterMembers = outline.clusters().stream().mapToInt(PlanExpander.ClusterOutline::members).sum();
            int repeatedDetail = clusterMembers + outline.scatterRequested();
            int distinctLandmarkComponents = (int) outline.landmarks().stream()
                    .map(PlanExpander.LandmarkOutline::component)
                    .distinct()
                    .count();
            List<PlanExpander.PlanGate> gates = List.of(
                    gate("routes", outline.routes().size() >= threshold.routes(),
                            Integer.toString(outline.routes().size()), ">=" + threshold.routes(), ""),
                    gate("crossings", crossings >= threshold.crossings(),
                            Integer.toString(crossings), ">=" + threshold.crossings(), ""),
                    gate("reservations", outline.reservations().size() >= threshold.reservations(),
                            Integer.toString(outline.reservations().size()), ">=" + threshold.reservations(), ""),
                    gate("landmarks", outline.landmarks().size() >= threshold.landmarks(),
                            Integer.toString(outline.landmarks().size()), ">=" + threshold.landmarks(), ""),
                    gate("distinct_landmark_components",
                            distinctLandmarkComponents >= threshold.distinctLandmarkComponents(),
                            Integer.toString(distinctLandmarkComponents),
                            ">=" + threshold.distinctLandmarkComponents(),
                            "Proxy for distinct landmark silhouettes."),
                    gate("clusters", outline.clusters().size() >= threshold.clusters(),
                            Integer.toString(outline.clusters().size()), ">=" + threshold.clusters(), ""),
                    gate("repeated_detail", repeatedDetail >= threshold.repeatedDetail(),
                            repeatedDetail + " (" + clusterMembers + " cluster member(s), "
                                    + outline.scatterRequested() + " scatter request(s))",
                            ">=" + threshold.repeatedDetail(), ""),
                    gate("loot", outline.lootRequested() >= threshold.loot(),
                            Integer.toString(outline.lootRequested()), ">=" + threshold.loot(), "")
            );
            boolean groupPassed = gates.stream().allMatch(gate -> "pass".equals(gate.status()));
            if (!groupPassed) {
                failedGroups++;
            }
            System.out.println("group " + quoteForOutput(outline.groupName())
                    + " file=" + outline.file()
                    + " profile=" + threshold.profile()
                    + " area=" + width + "x" + depth
                    + " status=" + (groupPassed ? "pass" : "fail"));
            for (PlanExpander.PlanGate gate : gates) {
                String line = "  " + gate.status() + " " + gate.name()
                        + " value=" + gate.value()
                        + " threshold=" + gate.threshold();
                if (!gate.note().isEmpty()) {
                    line += " note=" + gate.note();
                }
                System.out.println(line);
            }
        }

        boolean passed = warnings.isEmpty() && failedGroups == 0 && checked > 0;
        System.out.println("summary: " + (passed ? "pass" : "fail")
                + " checked_groups=" + checked
                + " failed_groups=" + failedGroups
                + " plan_warnings=" + warnings.size());
        if (!passed) {
            System.exit(1);
        }
    }

    private static TargetClassThreshold targetClassThreshold(int width, int depth) {
        int side = Math.min(width, depth);
        if (side >= 512) {
            return new TargetClassThreshold("512", 5, 2, 6, 6, 5, 8, 120, 35);
        }
        if (side >= 448) {
            return new TargetClassThreshold("448", 5, 4, 6, 6, 5, 10, 180, 30);
        }
        if (side >= 384) {
            return new TargetClassThreshold("384", 4, 1, 5, 5, 4, 5, 100, 25);
        }
        return null;
    }

    private static void planIndex(Path input) throws Exception {
        List<PlanExpander.PlanIndexEntry> entries = new PlanExpander().index(input);
        System.out.println("Plan index for " + input.toAbsolutePath());
        System.out.println("entries: " + entries.size());
        for (PlanExpander.PlanIndexEntry entry : entries) {
            System.out.println(entry.file() + ":" + entry.line()
                    + " group=" + quoteForOutput(entry.groupName())
                    + " kind=" + entry.kind()
                    + " name=" + quoteForOutput(entry.name())
                    + " :: " + entry.summary());
        }
    }

    private static void planWorkbench(String[] args) throws Exception {
        Path input = Path.of(args[1]);
        String groupFilter = null;
        String kindFilter = null;
        int limit = 80;
        int catalogLimit = 40;
        boolean withGates = false;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--group" -> groupFilter = requireValue(args, ++i);
                case "--kind" -> kindFilter = requireValue(args, ++i);
                case "--limit" -> limit = Integer.parseInt(requireValue(args, ++i));
                case "--catalog-limit" -> catalogLimit = Integer.parseInt(requireValue(args, ++i));
                case "--with-gates" -> withGates = true;
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }

        PlanExpander expander = new PlanExpander();
        List<PlanExpander.PlanOutline> outlines = expander.outline(input);
        List<PlanExpander.PlanIndexEntry> entries = expander.index(input);
        List<PlanExpander.PlanGate> gates = withGates
                ? new ArrayList<>(expander.gates(input).gates())
                : List.of();
        List<PlanCatalogEntry> catalog = planCatalog(input, groupFilter);
        List<PlanExpander.PlanOutline> shownOutlines = filteredOutlines(outlines, groupFilter);
        List<PlanExpander.PlanIndexEntry> shownEntries = filteredIndex(entries, groupFilter, kindFilter);

        System.out.println("Plan LLM workbench for " + input.toAbsolutePath());
        System.out.println("candidate: generic-plan-layer");
        System.out.println("group_filter: " + (groupFilter == null ? "all" : quoteForOutput(groupFilter)));
        System.out.println("kind_filter: " + (kindFilter == null ? "all" : kindFilter));
        System.out.println("workflow:");
        System.out.println("  1. Pick a composition group and named handle from this workbench.");
        System.out.println("  2. Edit the line reported in editable_handles; prefer reserves/routes before landmarks/clusters.");
        System.out.println("  3. Run plan-diagnose, plan-render, and plan-edit-trial before generating Anvil output.");
        System.out.println("composition_groups:");
        for (PlanExpander.PlanOutline outline : shownOutlines) {
            int areaWidth = outline.maxX() - outline.minX() + 1;
            int areaDepth = outline.maxZ() - outline.minZ() + 1;
            System.out.println("  " + outline.file()
                    + " group=" + quoteForOutput(outline.groupName())
                    + " origin=" + outline.originX() + "," + outline.originY() + "," + outline.originZ()
                    + " area=" + areaWidth + "x" + areaDepth
                    + " routes=" + outline.routes().size()
                    + " reservations=" + outline.reservations().size()
                    + " landmarks=" + outline.landmarks().size()
                    + " clusters=" + outline.clusters().size()
                    + " scatter_requested=" + outline.scatterRequested()
                    + " loot_requested=" + outline.lootRequested());
        }
        System.out.println("editable_handles:");
        System.out.println("  total_matching: " + shownEntries.size());
        int shown = Math.min(limit, shownEntries.size());
        System.out.println("  showing: " + shown);
        for (int i = 0; i < shown; i++) {
            PlanExpander.PlanIndexEntry entry = shownEntries.get(i);
            System.out.println("  " + entry.file() + ":" + entry.line()
                    + " group=" + quoteForOutput(entry.groupName())
                    + " kind=" + entry.kind()
                    + " name=" + quoteForOutput(entry.name())
                    + " :: " + entry.summary());
        }
        if (shown < shownEntries.size()) {
            System.out.println("  ... " + (shownEntries.size() - shown)
                    + " more handle(s); rerun with --limit " + shownEntries.size()
                    + " or filter with --group/--kind");
        }
        System.out.println("authoring_catalog:");
        System.out.println("  total_matching: " + catalog.size());
        int catalogShown = Math.min(catalogLimit, catalog.size());
        System.out.println("  showing: " + catalogShown);
        for (int i = 0; i < catalogShown; i++) {
            PlanCatalogEntry entry = catalog.get(i);
            System.out.println("  " + entry.file() + ":" + entry.line()
                    + " group=" + quoteForOutput(entry.groupName())
                    + " kind=" + entry.kind()
                    + " name=" + entry.name()
                    + " :: " + entry.value());
        }
        if (catalogShown < catalog.size()) {
            System.out.println("  ... " + (catalog.size() - catalogShown)
                    + " more catalog entries; rerun with --catalog-limit " + catalog.size());
        }
        System.out.println("quality_guardrails:");
        System.out.println("  reserve pads/clearings before placing landmarks or dense clusters");
        System.out.println("  keep routes readable and add crossings where routes enter water/lava hazards");
        System.out.println("  keep cluster members spaced by their radii; diagnostics reject cramped detail");
        System.out.println("  use generic style/component keys; do not add theme-specific core directives");
        System.out.println("current_gates:");
        if (withGates) {
            System.out.println("  passed: " + countGates(gates, "pass"));
            System.out.println("  failed: " + countGates(gates, "fail"));
            System.out.println("  manual: " + countGates(gates, "manual"));
            for (PlanExpander.PlanGate gate : gates) {
                if ("fail".equals(gate.status())) {
                    System.out.println("  fail " + gate.name() + " value=" + gate.value()
                            + " threshold=" + gate.threshold());
                }
            }
        } else {
            System.out.println("  skipped: rerun with --with-gates or use plan-report for full validation");
        }
        System.out.println("validation_commands:");
        System.out.println("  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose " + input);
        System.out.println("  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-target-audit " + input);
        System.out.println("  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render " + input
                + " --out target/plan-workbench-overlay.png --max 1024");
        System.out.println("  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-trial "
                + input + " <edited-world.atf>");
        System.out.println("  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-report "
                + input + " --profile dense");
    }

    private static List<PlanExpander.PlanOutline> filteredOutlines(
            List<PlanExpander.PlanOutline> outlines,
            String groupFilter) {
        if (groupFilter == null) {
            return outlines;
        }
        return outlines.stream()
                .filter(outline -> groupFilter.equals(outline.groupName()) || groupFilter.equals(outline.file()))
                .toList();
    }

    private static List<PlanExpander.PlanIndexEntry> filteredIndex(
            List<PlanExpander.PlanIndexEntry> entries,
            String groupFilter,
            String kindFilter) {
        return entries.stream()
                .filter(entry -> groupFilter == null
                        || groupFilter.equals(entry.groupName())
                        || groupFilter.equals(entry.file()))
                .filter(entry -> kindFilter == null || kindFilter.equals(entry.kind()))
                .toList();
    }

    private static void planSnippet(String[] args) throws Exception {
        Path input = Path.of(args[1]);
        String name = null;
        String group = null;
        String kind = null;
        String file = null;
        String component = null;
        String style = null;
        int line = 0;
        int context = 0;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--name" -> name = requireValue(args, ++i);
                case "--group" -> group = requireValue(args, ++i);
                case "--kind" -> kind = requireValue(args, ++i);
                case "--component" -> component = requireValue(args, ++i);
                case "--style" -> style = requireValue(args, ++i);
                case "--file" -> file = requireValue(args, ++i);
                case "--line" -> line = Integer.parseInt(requireValue(args, ++i));
                case "--context" -> context = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }

        PlanSnippetTarget target = snippetTarget(input, name, group, kind, file, line, component, style);
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        Path source = sourceRoot.resolve(target.file());
        List<String> lines = Files.readAllLines(source);
        int startLine = Math.max(1, target.line() - context);
        int endLine = Math.min(lines.size(), snippetEnd(lines, target.line()) + context);

        System.out.println("Plan source snippet for " + input.toAbsolutePath());
        System.out.println("target: " + target.file() + ":" + target.line()
                + " group=" + quoteForOutput(target.groupName())
                + " kind=" + target.kind()
                + " name=" + quoteForOutput(target.name()));
        for (int current = startLine; current <= endLine; current++) {
            System.out.printf(java.util.Locale.ROOT, "%4d | %s%n", current, lines.get(current - 1));
        }
    }

    private static PlanSnippetTarget snippetTarget(Path input, String name, String group, String kind,
                                                   String file, int line, String component, String style)
            throws IOException {
        if (file != null || line > 0) {
            if (file == null || line <= 0) {
                throw new IllegalArgumentException("plan-snippet requires both --file and --line for direct lookup");
            }
            return new PlanSnippetTarget(file, line, group == null ? "" : group,
                    kind == null ? "line" : kind, name == null ? "" : name);
        }
        if (component != null) {
            return catalogSnippetTarget(input, group, "component", component);
        }
        if (style != null) {
            return catalogSnippetTarget(input, group, "style", style);
        }
        if (name == null) {
            throw new IllegalArgumentException(
                    "plan-snippet requires --name, --component, --style, or --file/--line");
        }

        List<PlanExpander.PlanIndexEntry> matches = new PlanExpander().index(input).stream()
                .filter(entry -> name.equals(entry.name()))
                .filter(entry -> group == null || group.equals(entry.groupName()) || group.equals(entry.file()))
                .filter(entry -> kind == null || kind.equals(entry.kind()))
                .toList();
        if (matches.isEmpty()) {
            System.out.println("No plan handle matched name=" + quoteForOutput(name));
            System.exit(1);
        }
        if (matches.size() > 1) {
            System.out.println("Multiple plan handles matched name=" + quoteForOutput(name)
                    + "; add --group or --kind:");
            for (PlanExpander.PlanIndexEntry entry : matches) {
                System.out.println("  " + entry.file() + ":" + entry.line()
                        + " group=" + quoteForOutput(entry.groupName())
                        + " kind=" + entry.kind()
                        + " name=" + quoteForOutput(entry.name()));
            }
            System.exit(1);
        }
        PlanExpander.PlanIndexEntry entry = matches.get(0);
        return new PlanSnippetTarget(entry.file(), entry.line(), entry.groupName(), entry.kind(), entry.name());
    }

    private static PlanSnippetTarget catalogSnippetTarget(Path input, String group, String kind, String name)
            throws IOException {
        PlanCatalogEntry entry = catalogEntry(input, group, kind, name);
        return new PlanSnippetTarget(entry.file(), entry.line(), entry.groupName(), entry.kind(), entry.name());
    }

    private static PlanCatalogEntry catalogEntry(Path input, String group, String kind, String name)
            throws IOException {
        List<PlanCatalogEntry> matches = planCatalog(input, group).stream()
                .filter(entry -> kind.equals(entry.kind()))
                .filter(entry -> name.equals(entry.name()))
                .toList();
        if (matches.isEmpty()) {
            System.out.println("No catalog entry matched " + kind + "=" + quoteForOutput(name));
            System.exit(1);
        }
        if (matches.size() > 1) {
            System.out.println("Multiple catalog entries matched " + kind + "=" + quoteForOutput(name)
                    + "; add --group:");
            for (PlanCatalogEntry entry : matches) {
                System.out.println("  " + entry.file() + ":" + entry.line()
                        + " group=" + quoteForOutput(entry.groupName())
                        + " kind=" + entry.kind()
                        + " name=" + entry.name());
            }
            System.exit(1);
        }
        return matches.get(0);
    }

    private static void planRecipe(String[] args) throws Exception {
        Path input = Path.of(args[1]);
        String group = null;
        String component = null;
        String style = null;
        int count = 5;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--group" -> group = requireValue(args, ++i);
                case "--component" -> component = requireValue(args, ++i);
                case "--style" -> style = requireValue(args, ++i);
                case "--count" -> count = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }
        if ((component == null && style == null) || (component != null && style != null)) {
            throw new IllegalArgumentException("plan-recipe requires exactly one of --component or --style");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("--count must be positive");
        }

        String reference = component;
        String componentName = component;
        PlanCatalogEntry styleEntry = null;
        if (style != null) {
            styleEntry = catalogEntry(input, group, "style", style);
            reference = style;
            componentName = styleEntry.value();
        }

        PlanCatalogEntry componentEntry = catalogEntry(input, null, "component", componentName);
        ClusterHint hint = parseClusterHint(componentEntry.value());
        int span = hint.radius() * Math.max(1, count - 1);

        System.out.println("Plan component recipe for " + input.toAbsolutePath());
        if (styleEntry != null) {
            System.out.println("style: " + styleEntry.file() + ":" + styleEntry.line()
                    + " " + styleEntry.name() + " -> " + styleEntry.value());
        }
        System.out.println("component: " + componentEntry.file() + ":" + componentEntry.line()
                + " " + componentEntry.name());
        System.out.println("hint: " + componentEntry.value());
        System.out.println("cluster_rows:");
        System.out.println("  component " + reference + " at 0 0 radius "
                + hint.radius() + " height " + hint.height() + " rotate 0");
        System.out.println("  repeat component " + reference + " from -" + span
                + " 0 to " + span + " 0 count " + count
                + " radius " + hint.radius() + " height " + hint.height() + " rotate 0");
        System.out.println("notes:");
        System.out.println("  Use these as safe starting rows inside a cluster block.");
        System.out.println("  Adjust dx/dz, count, and rotate for composition; rerun plan-diagnose after editing.");
    }

    private static ClusterHint parseClusterHint(String value) {
        List<String> tokens = sourceTokens(value);
        int radiusIndex = tokens.indexOf("radius");
        int heightIndex = tokens.indexOf("height");
        if (radiusIndex < 0 || radiusIndex + 1 >= tokens.size()
                || heightIndex < 0 || heightIndex + 1 >= tokens.size()) {
            return new ClusterHint(1, 1);
        }
        try {
            return new ClusterHint(Integer.parseInt(tokens.get(radiusIndex + 1)),
                    Integer.parseInt(tokens.get(heightIndex + 1).replace(",", "")));
        } catch (NumberFormatException ignored) {
            return new ClusterHint(1, 1);
        }
    }

    private static int snippetEnd(List<String> lines, int line) {
        String first = lines.get(line - 1);
        int depth = braceDelta(stripSourceComment(first));
        if (depth <= 0) {
            return line;
        }
        int current = line;
        while (depth > 0 && current < lines.size()) {
            current++;
            depth += braceDelta(stripSourceComment(lines.get(current - 1)));
        }
        return current;
    }

    private static List<PlanCatalogEntry> planCatalog(Path input, String groupFilter) throws IOException {
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        List<PlanCatalogEntry> entries = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList()) {
                Path relative = sourceRoot.relativize(source);
                List<String> lines = Files.readAllLines(source);
                if (isComponentFile(relative)) {
                    collectComponentCatalog(relative, lines, entries);
                } else {
                    collectPlanStyleCatalog(relative, lines, groupFilter, entries);
                }
            }
        }
        entries.sort(Comparator
                .comparingInt((PlanCatalogEntry entry) -> "style".equals(entry.kind()) ? 0 : 1)
                .thenComparing(PlanCatalogEntry::name)
                .thenComparing(PlanCatalogEntry::file)
                .thenComparingInt(PlanCatalogEntry::line));
        return entries;
    }

    private static void collectComponentCatalog(Path relative, List<String> lines,
                                                List<PlanCatalogEntry> entries) {
        String componentName = "";
        int componentLine = 0;
        String bounds = "bounds unknown";
        for (int i = 0; i < lines.size(); i++) {
            String stripped = stripSourceComment(lines.get(i)).trim();
            if (stripped.startsWith("component ") && stripped.contains("{")) {
                componentName = quotedNameOrSecondToken(stripped);
                componentLine = i + 1;
                bounds = "bounds unknown";
            } else if (!componentName.isEmpty() && stripped.startsWith("bounds ")) {
                bounds = stripped;
            } else if (!componentName.isEmpty() && "}".equals(stripped)) {
                entries.add(new PlanCatalogEntry(relative.toString(), componentLine, "",
                        "component", componentName, bounds + ", " + clusterHint(bounds)
                                + ", source_lines=" + nonBlankSourceLines(lines)));
                componentName = "";
            }
        }
    }

    private static String clusterHint(String bounds) {
        List<String> tokens = sourceTokens(bounds);
        if (tokens.size() < 8 || !"bounds".equals(tokens.get(0)) || !"to".equals(tokens.get(4))) {
            return "safe_cluster unknown";
        }
        try {
            int minX = Integer.parseInt(tokens.get(1));
            int minY = Integer.parseInt(tokens.get(2));
            int minZ = Integer.parseInt(tokens.get(3));
            int maxX = Integer.parseInt(tokens.get(5));
            int maxY = Integer.parseInt(tokens.get(6));
            int maxZ = Integer.parseInt(tokens.get(7));
            int safeRadius = Math.max(1, Math.max(
                    Math.max(Math.abs(minX), Math.abs(maxX)),
                    Math.max(Math.abs(minZ), Math.abs(maxZ))));
            int safeHeight = Math.max(1, Math.max(Math.abs(minY), Math.abs(maxY)));
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            return "footprint=" + width + "x" + depth
                    + ", safe_cluster radius " + safeRadius + " height " + safeHeight;
        } catch (NumberFormatException ignored) {
            return "safe_cluster unknown";
        }
    }

    private static void collectPlanStyleCatalog(Path relative, List<String> lines, String groupFilter,
                                                List<PlanCatalogEntry> entries) {
        String groupName = "";
        for (int i = 0; i < lines.size(); i++) {
            String stripped = stripSourceComment(lines.get(i)).trim();
            if (stripped.startsWith("group ") && stripped.contains("{")) {
                groupName = quotedNameOrSecondToken(stripped);
            }
            boolean groupMatches = groupFilter == null
                    || groupFilter.equals(groupName)
                    || groupFilter.equals(relative.toString());
            if (!groupMatches || !stripped.startsWith("style ") || !stripped.contains("{")) {
                continue;
            }

            String styleName = quotedNameOrSecondToken(stripped);
            int depth = braceDelta(stripped);
            while (++i < lines.size() && depth > 0) {
                String styleLine = stripSourceComment(lines.get(i)).trim();
                depth += braceDelta(styleLine);
                if (depth <= 0 || styleLine.isEmpty() || "}".equals(styleLine)) {
                    continue;
                }
                List<String> tokens = sourceTokens(styleLine);
                if (tokens.size() >= 2) {
                    entries.add(new PlanCatalogEntry(relative.toString(), i + 1, groupName,
                            "style", styleName + "." + tokens.get(0), tokens.get(1)));
                }
            }
            i--;
        }
    }

    private static String quotedNameOrSecondToken(String line) {
        int firstQuote = line.indexOf('"');
        if (firstQuote >= 0) {
            int secondQuote = line.indexOf('"', firstQuote + 1);
            if (secondQuote > firstQuote) {
                return line.substring(firstQuote + 1, secondQuote);
            }
        }
        List<String> tokens = sourceTokens(line);
        return tokens.size() >= 2 ? tokens.get(1) : "";
    }

    private static List<String> sourceTokens(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (!quoted && (c == '{' || c == '}')) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static void planGates(String[] args) throws Exception {
        PlanEvidence evidence = parsePlanEvidence(args);
        List<PlanExpander.PlanGate> gates = gatesWithEvidence(evidence);

        System.out.println("Plan gates for " + evidence.input().toAbsolutePath());
        System.out.println("passed: " + countGates(gates, "pass"));
        System.out.println("failed: " + countGates(gates, "fail"));
        System.out.println("manual: " + countGates(gates, "manual"));
        for (PlanExpander.PlanGate gate : gates) {
            String line = gate.status() + " " + gate.name()
                    + " value=" + gate.value()
                    + " threshold=" + gate.threshold();
            if (!gate.note().isEmpty()) {
                line += " note=" + gate.note();
            }
            System.out.println(line);
        }
    }

    private static void planReport(String[] args) throws Exception {
        PlanEvidence evidence = parsePlanEvidence(args);
        PlanExpander expander = new PlanExpander();
        PlanExpander.PlanMetrics metrics = expander.metrics(evidence.input());
        List<PlanExpander.PlanOutline> outlines = expander.outline(evidence.input());
        List<PlanExpander.PlanIndexEntry> index = expander.index(evidence.input());
        List<PlanExpander.PlanGate> gates = gatesWithEvidence(evidence);

        long failed = countGates(gates, "fail");
        long manual = countGates(gates, "manual");
        String status = failed > 0 ? "fail" : manual > 0 ? "evidence_pending" : "pass";
        Map<String, Integer> indexCounts = indexCounts(index);

        System.out.println("Plan format report for " + evidence.input().toAbsolutePath());
        System.out.println("candidate: generic-plan-layer");
        System.out.println("profile: " + evidence.profile());
        System.out.println("status: " + status);
        System.out.println("plan_blocks: " + metrics.planBlocks());
        System.out.println("plan_lines: " + metrics.planLines());
        System.out.println("plan_area_cells: " + metrics.planAreaCells());
        System.out.println("max_plan_area_cells: " + metrics.maxPlanAreaCells());
        System.out.println("generated_atf_lines: " + metrics.generatedAtfLines());
        System.out.println("editable_index_entries: " + index.size());
        printMetricMap("editable_index_kinds", indexCounts);
        System.out.println("composition_groups:");
        for (PlanExpander.PlanOutline outline : outlines) {
            int areaWidth = outline.maxX() - outline.minX() + 1;
            int areaDepth = outline.maxZ() - outline.minZ() + 1;
            System.out.println("  " + outline.file()
                    + " group=" + quoteForOutput(outline.groupName())
                    + " area=" + areaWidth + "x" + areaDepth
                    + " routes=" + outline.routes().size()
                    + " reservations=" + outline.reservations().size()
                    + " landmarks=" + outline.landmarks().size()
                    + " clusters=" + outline.clusters().size()
                    + " scatter_requested=" + outline.scatterRequested()
                    + " loot_requested=" + outline.lootRequested());
        }
        System.out.println("gates:");
        System.out.println("  passed: " + countGates(gates, "pass"));
        System.out.println("  failed: " + failed);
        System.out.println("  manual: " + manual);
        for (PlanExpander.PlanGate gate : gates) {
            String line = "  " + gate.status() + " " + gate.name()
                    + " value=" + gate.value()
                    + " threshold=" + gate.threshold();
            if (!gate.note().isEmpty()) {
                line += " note=" + gate.note();
            }
            System.out.println(line);
        }
    }

    private static PlanEvidence parsePlanEvidence(String[] args) {
        Path input = Path.of(args[1]);
        Path editedInput = null;
        Path referenceWorld = null;
        List<Path> renderArtifacts = new ArrayList<>();
        Path compareSource = null;
        Path compareRegenerated = null;
        String profile = "skeleton";
        int editMaxSourceLines = 5;
        int editMinExpandedLines = 100;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--profile" -> profile = requireValue(args, ++i);
                case "--edit-trial" -> editedInput = Path.of(requireValue(args, ++i));
                case "--edit-max-source-lines" -> editMaxSourceLines = Integer.parseInt(requireValue(args, ++i));
                case "--edit-min-expanded-lines" -> editMinExpandedLines = Integer.parseInt(requireValue(args, ++i));
                case "--reference-world" -> referenceWorld = Path.of(requireValue(args, ++i));
                case "--render-artifact" -> renderArtifacts.add(Path.of(requireValue(args, ++i)));
                case "--compare-worlds" -> {
                    compareSource = Path.of(requireValue(args, ++i));
                    compareRegenerated = Path.of(requireValue(args, ++i));
                }
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }
        return new PlanEvidence(input, profile, editedInput, editMaxSourceLines, editMinExpandedLines, referenceWorld,
                renderArtifacts, compareSource, compareRegenerated);
    }

    private static List<PlanExpander.PlanGate> gatesWithEvidence(PlanEvidence evidence) throws Exception {
        PlanExpander.PlanGateReport report = new PlanExpander().gates(evidence.input());
        List<PlanExpander.PlanGate> gates = new ArrayList<>(report.gates());
        replaceGate(gates, targetClassGroupsGate(evidence.input()));
        if ("dense".equals(evidence.profile())) {
            addDenseProfileGates(gates, report.metrics());
        } else if (!"skeleton".equals(evidence.profile())) {
            throw new IllegalArgumentException("--profile must be skeleton or dense");
        }
        if (evidence.editedInput() != null) {
            replaceGate(gates, editLocalityGate(evidence.input(), evidence.editedInput(),
                    evidence.editMaxSourceLines(), evidence.editMinExpandedLines()));
        }
        if (evidence.referenceWorld() != null) {
            replaceGate(gates, referenceValidationGate(evidence.input(), evidence.referenceWorld()));
        }
        if (!evidence.renderArtifacts().isEmpty()) {
            replaceGate(gates, renderArtifactGate(evidence.renderArtifacts()));
        }
        if (evidence.compareSource() != null) {
            replaceGate(gates, importRoundTripGate(evidence.compareSource(), evidence.compareRegenerated()));
        }
        return gates;
    }

    private static Map<String, Integer> indexCounts(List<PlanExpander.PlanIndexEntry> entries) {
        Map<String, Integer> counts = new TreeMap<>();
        for (PlanExpander.PlanIndexEntry entry : entries) {
            counts.merge(entry.kind(), 1, Integer::sum);
        }
        return counts;
    }

    private static void addDenseProfileGates(List<PlanExpander.PlanGate> gates, PlanExpander.PlanMetrics metrics) {
        Map<String, Integer> counts = metrics.commandCounts();
        int clusterMembers = counts.getOrDefault("cluster_members", 0);
        int scatterRequested = counts.getOrDefault("scatter_requested", 0);
        int repeatedDetail = clusterMembers + scatterRequested;
        int clusters = counts.getOrDefault("clusters", 0);
        int landmarks = counts.getOrDefault("landmarks", 0);
        int loot = counts.getOrDefault("loot_requested", 0);
        int clusterOverlay = metrics.overlayCells().getOrDefault("plan:cluster", 0);
        replaceGate(gates, gate("dense_repeated_detail",
                repeatedDetail >= 1000,
                Integer.toString(repeatedDetail),
                ">=1000 scatter requests plus cluster members",
                "Dense profile for finished-map stress testing."));
        replaceGate(gates, gate("dense_cluster_density",
                clusters >= 36 && clusterMembers >= 475,
                clusters + " cluster(s), " + clusterMembers + " member(s)",
                ">=36 clusters and >=475 cluster members",
                "Dense profile for repeated architecture and local detail."));
        replaceGate(gates, gate("dense_landmarks",
                landmarks >= 24,
                Integer.toString(landmarks),
                ">=24 reserved landmarks across benchmark groups",
                "Dense profile for stronger silhouette coverage."));
        replaceGate(gates, gate("dense_loot",
                loot >= 120,
                Integer.toString(loot),
                ">=120 loot anchors",
                "Dense profile for survival-games scale loot spread."));
        replaceGate(gates, gate("dense_overlay_coverage",
                clusterOverlay >= 16000,
                Integer.toString(clusterOverlay),
                ">=16000 visible cluster overlay cells",
                "Dense profile proxy for authored local-detail footprint."));
    }

    private static PlanExpander.PlanGate gate(String name, boolean passed, String value,
                                             String threshold, String note) {
        return new PlanExpander.PlanGate(name, passed ? "pass" : "fail", value, threshold, note);
    }

    private static PlanExpander.PlanGate targetClassGroupsGate(Path input) throws IOException {
        PlanExpander expander = new PlanExpander();
        List<PlanExpander.PlanOutline> outlines = expander.outline(input);
        List<PlanExpander.PlanWarning> warnings = expander.diagnose(input);
        int checked = 0;
        int failed = 0;
        for (PlanExpander.PlanOutline outline : outlines) {
            int width = outline.maxX() - outline.minX() + 1;
            int depth = outline.maxZ() - outline.minZ() + 1;
            TargetClassThreshold threshold = targetClassThreshold(width, depth);
            if (threshold == null) {
                continue;
            }
            checked++;
            if (!targetClassGroupPasses(outline, threshold)) {
                failed++;
            }
        }
        boolean passed = checked > 0 && failed == 0 && warnings.isEmpty();
        return new PlanExpander.PlanGate("target_class_groups", passed ? "pass" : "fail",
                checked + " checked group(s), " + failed + " failed group(s), "
                        + warnings.size() + " plan warning(s)",
                "each >=384x384 plan group passes per-group routes/reservations/landmarks/detail/loot audit",
                "Same thresholds as plan-target-audit.");
    }

    private static boolean targetClassGroupPasses(PlanExpander.PlanOutline outline,
                                                  TargetClassThreshold threshold) {
        int crossings = outline.routes().stream().mapToInt(PlanExpander.RouteOutline::crossings).sum();
        int clusterMembers = outline.clusters().stream().mapToInt(PlanExpander.ClusterOutline::members).sum();
        int repeatedDetail = clusterMembers + outline.scatterRequested();
        int distinctLandmarkComponents = (int) outline.landmarks().stream()
                .map(PlanExpander.LandmarkOutline::component)
                .distinct()
                .count();
        return outline.routes().size() >= threshold.routes()
                && crossings >= threshold.crossings()
                && outline.reservations().size() >= threshold.reservations()
                && outline.landmarks().size() >= threshold.landmarks()
                && distinctLandmarkComponents >= threshold.distinctLandmarkComponents()
                && outline.clusters().size() >= threshold.clusters()
                && repeatedDetail >= threshold.repeatedDetail()
                && outline.lootRequested() >= threshold.loot();
    }

    private static PlanExpander.PlanGate editLocalityGate(Path baseInput, Path editedInput,
                                                         int maxSourceChangedLines,
                                                         int minExpandedChangedLines) throws IOException {
        PlanExpander.PlanEditTrialReport report = new PlanExpander()
                .editTrial(baseInput, editedInput, maxSourceChangedLines, minExpandedChangedLines);
        return new PlanExpander.PlanGate("edit_locality", report.passed() ? "pass" : "fail",
                report.sourceChangedLines() + " source line(s), "
                        + report.expandedChangedLines() + " expanded line(s), "
                        + report.planWarnings() + " plan warning(s), "
                        + report.coreWarnings() + " core warning(s)",
                "<=" + maxSourceChangedLines + " plan lines and >=" + minExpandedChangedLines
                        + " expanded ATF lines for a semantic edit",
                "Measured against " + editedInput.toAbsolutePath());
    }

    private static PlanExpander.PlanGate referenceValidationGate(Path input, Path worldDir) {
        Path tempRoot = null;
        try {
            tempRoot = Files.createTempDirectory("anvil-tf-reference-gate-");
            Path expanded = tempRoot.resolve("expanded");
            new PlanExpander().expand(input, expanded);
            WorldSpec expectedWorld = new DslParser().parse(expanded.resolve(input.getFileName()));
            ReferenceWorldValidator.ValidationReport report =
                    new ReferenceWorldValidator().validate(worldDir, expectedWorld);
            return new PlanExpander.PlanGate("reference_validation", "pass",
                    report.chunkCount() + " chunk(s), "
                            + report.authoredBlockChecks() + " authored block check(s), "
                            + report.authoredBiomeChecks() + " authored biome check(s)",
                    "generated Anvil world validates with the reference library",
                    "Validated " + worldDir.toAbsolutePath());
        } catch (Exception error) {
            return new PlanExpander.PlanGate("reference_validation", "fail",
                    oneLine(error.getMessage()),
                    "generated Anvil world validates with the reference library",
                    "Validated " + worldDir.toAbsolutePath());
        } finally {
            if (tempRoot != null) {
                try {
                    deleteTree(tempRoot);
                } catch (IOException ignored) {
                    // Temporary cleanup failure should not hide the validation result.
                }
            }
        }
    }

    private static PlanExpander.PlanGate renderArtifactGate(List<Path> artifacts) throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path artifact : artifacts) {
            if (!Files.isRegularFile(artifact) || Files.size(artifact) == 0L) {
                missing.add(artifact.toString());
            }
        }
        boolean passed = missing.isEmpty();
        return new PlanExpander.PlanGate("render_review", passed ? "pass" : "fail",
                passed
                        ? artifacts.size() + " non-empty render artifact(s)"
                        : "missing or empty: " + String.join(", ", missing),
                "map plus at least one top/slice render inspected and recorded",
                "Checks artifact presence; keep the visual inspection notes with the report.");
    }

    private static PlanExpander.PlanGate importRoundTripGate(Path sourceWorld, Path regeneratedWorld) {
        try {
            ReferenceWorldRoundTripComparator.ComparisonReport report =
                    new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);
            return new PlanExpander.PlanGate("import_round_trip", "pass",
                    report.chunkCount() + " chunk(s), "
                            + report.blockChecks() + " block check(s), "
                            + report.biomeChecks() + " biome check(s)",
                    "generated world imports to ordinary ATF and exact-compares",
                    "Compared " + sourceWorld.toAbsolutePath() + " to " + regeneratedWorld.toAbsolutePath());
        } catch (Exception error) {
            return new PlanExpander.PlanGate("import_round_trip", "fail",
                    oneLine(error.getMessage()),
                    "generated world imports to ordinary ATF and exact-compares",
                    "Compared " + sourceWorld.toAbsolutePath() + " to " + regeneratedWorld.toAbsolutePath());
        }
    }

    private static void replaceGate(List<PlanExpander.PlanGate> gates, PlanExpander.PlanGate replacement) {
        for (int i = 0; i < gates.size(); i++) {
            if (gates.get(i).name().equals(replacement.name())) {
                gates.set(i, replacement);
                return;
            }
        }
        gates.add(replacement);
    }

    private static long countGates(List<PlanExpander.PlanGate> gates, String status) {
        return gates.stream().filter(gate -> status.equals(gate.status())).count();
    }

    private static String oneLine(String value) {
        if (value == null || value.isBlank()) {
            return "unknown error";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static void planEditTrial(String[] args) throws Exception {
        Path baseInput = Path.of(args[1]);
        Path editedInput = Path.of(args[2]);
        int maxSourceChangedLines = 5;
        int minExpandedChangedLines = 100;
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--max-source-lines" -> maxSourceChangedLines = Integer.parseInt(requireValue(args, ++i));
                case "--min-expanded-lines" -> minExpandedChangedLines = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }
        PlanExpander.PlanEditTrialReport report = new PlanExpander()
                .editTrial(baseInput, editedInput, maxSourceChangedLines, minExpandedChangedLines);
        System.out.println("Plan edit trial for " + baseInput.toAbsolutePath()
                + " -> " + editedInput.toAbsolutePath());
        System.out.println("source_changed_lines: " + report.sourceChangedLines());
        System.out.println("expanded_changed_lines: " + report.expandedChangedLines());
        System.out.println("plan_warnings: " + report.planWarnings());
        System.out.println("core_warnings: " + report.coreWarnings());
        System.out.println("threshold: source_changed_lines <= " + maxSourceChangedLines
                + ", expanded_changed_lines >= " + minExpandedChangedLines + ", warnings = 0");
        System.out.println("result: " + (report.passed() ? "pass" : "fail"));
    }

    private static void planEditSuite(String[] args) throws Exception {
        Path baseInput = Path.of(args[1]);
        List<Path> editedInputs = new ArrayList<>();
        int maxSourceChangedLines = 5;
        int minExpandedChangedLines = 100;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--max-source-lines" -> maxSourceChangedLines = Integer.parseInt(requireValue(args, ++i));
                case "--min-expanded-lines" -> minExpandedChangedLines = Integer.parseInt(requireValue(args, ++i));
                default -> editedInputs.add(Path.of(args[i]));
            }
        }
        if (editedInputs.isEmpty()) {
            usage();
            System.exit(2);
        }
        int passed = 0;
        System.out.println("Plan edit suite for " + baseInput.toAbsolutePath());
        System.out.println("threshold: source_changed_lines <= " + maxSourceChangedLines
                + ", expanded_changed_lines >= " + minExpandedChangedLines + ", warnings = 0");
        for (Path editedInput : editedInputs) {
            PlanExpander.PlanEditTrialReport report = new PlanExpander()
                    .editTrial(baseInput, editedInput, maxSourceChangedLines, minExpandedChangedLines);
            if (report.passed()) {
                passed++;
            }
            System.out.println((report.passed() ? "pass" : "fail") + " " + editedInput.toAbsolutePath()
                    + " source_changed_lines=" + report.sourceChangedLines()
                    + " expanded_changed_lines=" + report.expandedChangedLines()
                    + " plan_warnings=" + report.planWarnings()
                    + " core_warnings=" + report.coreWarnings());
        }
        System.out.println("summary: " + passed + "/" + editedInputs.size() + " edit trial(s) passed");
        if (passed != editedInputs.size()) {
            System.exit(1);
        }
    }

    private static void planEditManifest(Path baseInput, Path manifest) throws Exception {
        EditScoreReport scoreReport = collectEditManifestScores(baseInput, manifest);

        System.out.println("Plan edit manifest for " + baseInput.toAbsolutePath());
        System.out.println("manifest: " + manifest.toAbsolutePath());
        for (EditScoreRow row : scoreReport.rows()) {
            printEditScoreRow(row);
        }
        System.out.println(editScoreSummary(scoreReport));
        if (scoreReport.passed() != scoreReport.rows().size()) {
            System.exit(1);
        }
    }

    private static void planEditManifestReport(Path baseInput, Path manifest, Path out) throws Exception {
        EditScoreReport scoreReport = collectEditManifestScores(baseInput, manifest);
        Path parent = out.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, renderEditScoreReport(scoreReport), StandardCharsets.UTF_8);
        System.out.println("Wrote plan edit manifest report to " + out.toAbsolutePath());
        System.out.println(editScoreSummary(scoreReport));
        if (scoreReport.passed() != scoreReport.rows().size()) {
            System.exit(1);
        }
    }

    private static EditScoreReport collectEditManifestScores(Path baseInput, Path manifest) throws Exception {
        List<EditManifestRow> rows = readEditManifest(manifest);
        List<EditScoreRow> scoreRows = new ArrayList<>();
        int passed = 0;
        for (EditManifestRow row : rows) {
            PlanExpander.PlanEditTrialReport report = new PlanExpander()
                    .editTrial(baseInput, row.editedInput(), row.maxSourceLines(), row.minExpandedLines());
            List<String> changedFiles = changedAtfFiles(baseInput, row.editedInput());
            boolean expectedFilePassed = row.expectedChangedFile().isEmpty()
                    || changedFiles.equals(List.of(row.expectedChangedFile()));
            boolean passedRow = report.passed() && expectedFilePassed;
            if (passedRow) {
                passed++;
            }
            scoreRows.add(new EditScoreRow(row, report, changedFiles, expectedFilePassed, passedRow));
        }
        return new EditScoreReport(baseInput, manifest, scoreRows, passed);
    }

    private static void printEditScoreRow(EditScoreRow row) {
        EditManifestRow manifestRow = row.manifestRow();
        PlanExpander.PlanEditTrialReport report = row.report();
        System.out.println((row.passed() ? "pass" : "fail") + " " + manifestRow.name()
                + " edited=" + manifestRow.editedInput().toAbsolutePath()
                + " source_changed_lines=" + report.sourceChangedLines()
                + " max_source_lines=" + manifestRow.maxSourceLines()
                + " expanded_changed_lines=" + report.expandedChangedLines()
                + " min_expanded_lines=" + manifestRow.minExpandedLines()
                + " plan_warnings=" + report.planWarnings()
                + " core_warnings=" + report.coreWarnings()
                + " changed_files=" + String.join(",", row.changedFiles())
                + (manifestRow.expectedChangedFile().isEmpty()
                ? ""
                : " expected_changed_file=" + manifestRow.expectedChangedFile()
                + " expected_file=" + (row.expectedFilePassed() ? "pass" : "fail")));
    }

    private static String editScoreSummary(EditScoreReport report) {
        return "summary: " + report.passed() + "/" + report.rows().size() + " edit manifest row(s) passed";
    }

    private static String renderEditScoreReport(EditScoreReport report) {
        StringBuilder out = new StringBuilder();
        appendLine(out, "# Plan Edit Manifest Score");
        appendLine(out, "");
        appendLine(out, "Base source: `" + report.baseInput() + "`");
        appendLine(out, "Manifest: `" + report.manifest() + "`");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, editScoreSummary(report));
        appendLine(out, "```");
        appendLine(out, "");
        appendLine(out, "| Result | Task | Source lines | Expanded lines | Warnings | Expected file | Changed files |");
        appendLine(out, "| --- | --- | --- | --- | --- | --- | --- |");
        for (EditScoreRow row : report.rows()) {
            EditManifestRow manifestRow = row.manifestRow();
            PlanExpander.PlanEditTrialReport trial = row.report();
            appendLine(out, "| `" + (row.passed() ? "pass" : "fail") + "` | `"
                    + manifestRow.name() + "` | "
                    + trial.sourceChangedLines() + " <= " + manifestRow.maxSourceLines() + " | "
                    + trial.expandedChangedLines() + " >= " + manifestRow.minExpandedLines() + " | plan="
                    + trial.planWarnings() + ", core=" + trial.coreWarnings() + " | `"
                    + manifestRow.expectedChangedFile() + "` "
                    + (row.expectedFilePassed() ? "pass" : "fail") + " | `"
                    + joinedOrNone(row.changedFiles()) + "` |");
        }
        return out.toString();
    }

    private static void planEvalStatus(Path baseInput, Path manifest) throws IOException {
        EvalStatusReport report = collectEvalStatus(baseInput, manifest);

        System.out.println("Plan edit eval status for " + baseInput.toAbsolutePath());
        System.out.println("manifest: " + manifest.toAbsolutePath());
        for (EvalStatusRow row : report.rows()) {
            System.out.println(row.status() + " " + row.manifestRow().name()
                    + " edited=" + row.manifestRow().editedInput().toAbsolutePath()
                    + expectedChangedFileOutput(row.manifestRow())
                    + " changed_files=" + String.join(",", row.changedFiles()));
        }

        System.out.println(statusSummary(report));
        if (report.ready() != report.rows().size()) {
            System.exit(1);
        }
    }

    private static void planEvalStatusReport(Path baseInput, Path manifest, Path out) throws IOException {
        EvalStatusReport report = collectEvalStatus(baseInput, manifest);
        Path parent = out.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, renderEvalStatusReport(report), StandardCharsets.UTF_8);
        System.out.println("Wrote plan edit eval status report to " + out.toAbsolutePath());
        System.out.println(statusSummary(report));
    }

    private static void planEvalAudit(String[] args) throws Exception {
        Path baseInput = Path.of(args[1]);
        Path packDir = Path.of(args[2]);
        Path out = null;
        int minPassed = -1;
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(requireValue(args, ++i));
                case "--min-passed" -> minPassed = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }

        EvalAuditReport report = collectEvalAudit(baseInput, packDir, minPassed);
        String rendered = renderEvalAuditReport(report);
        if (out != null) {
            Path parent = out.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, rendered, StandardCharsets.UTF_8);
            System.out.println("Wrote plan edit eval audit report to " + out.toAbsolutePath());
            System.out.println(evalAuditSummary(report));
        } else {
            System.out.print(rendered);
        }
        if (report.failed() > 0) {
            System.exit(1);
        }
    }

    private static void planEvalCompletion(String[] args) throws Exception {
        Path baseInput = Path.of(args[1]);
        List<Path> packDirs = new ArrayList<>();
        Path out = null;
        int minRuns = 2;
        int minPassedPerRun = 3;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(requireValue(args, ++i));
                case "--min-runs" -> minRuns = Integer.parseInt(requireValue(args, ++i));
                case "--min-passed-per-run" -> minPassedPerRun = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    if (args[i].startsWith("--")) {
                        usage();
                        System.exit(2);
                    }
                    packDirs.add(Path.of(args[i]));
                }
            }
        }
        if (packDirs.isEmpty()) {
            usage();
            System.exit(2);
        }

        EvalCompletionReport report = collectEvalCompletion(baseInput, packDirs, minRuns, minPassedPerRun);
        String rendered = renderEvalCompletionReport(report);
        if (out != null) {
            Path parent = out.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, rendered, StandardCharsets.UTF_8);
            System.out.println("Wrote plan edit eval completion report to " + out.toAbsolutePath());
            System.out.println(evalCompletionSummary(report));
        } else {
            System.out.print(rendered);
        }
        if (report.failed() > 0) {
            System.exit(1);
        }
    }

    private static EvalCompletionReport collectEvalCompletion(
            Path baseInput,
            List<Path> packDirs,
            int minRuns,
            int minPassedPerRun) throws Exception {
        List<EvalCompletionPack> packs = new ArrayList<>();
        Set<String> distinctModels = new TreeSet<>();
        int completedRuns = 0;
        boolean sawTargetGate = false;
        boolean targetCoverage = false;

        for (Path packDir : packDirs) {
            EvalAuditReport audit = collectEvalAudit(baseInput, packDir, minPassedPerRun);
            String modelSurface = modelSurfaceForPack(packDir);
            if (!modelSurface.isBlank()) {
                distinctModels.add(modelSurface);
            }
            boolean completed = audit.failed() == 0;
            if (completed) {
                completedRuns++;
            }
            EvalAuditGate targetGate = findAuditGate(audit, "target_family_coverage");
            if (targetGate != null) {
                sawTargetGate = true;
                targetCoverage = targetCoverage || (completed && targetGate.passed());
            }
            packs.add(new EvalCompletionPack(packDir, modelSurface, completed, audit));
        }

        List<EvalAuditGate> gates = new ArrayList<>();
        gates.add(auditGate("pack_count", packDirs.size() >= minRuns,
                String.valueOf(packDirs.size()), "at least " + minRuns + " eval pack(s)", ""));
        gates.add(auditGate("completed_runs", completedRuns >= minRuns,
                String.valueOf(completedRuns), "at least " + minRuns + " completed audited run(s)", ""));
        gates.add(auditGate("distinct_model_surfaces", distinctModels.size() >= minRuns,
                String.valueOf(distinctModels.size()), "at least " + minRuns + " distinct model/surface values", ""));
        if (sawTargetGate) {
            gates.add(auditGate("target_family_coverage", targetCoverage,
                    String.valueOf(targetCoverage),
                    "a completed run covers Kikoshi-scale, castle/canal, and dense polish tasks", ""));
        }

        int passed = 0;
        for (EvalAuditGate gate : gates) {
            if (gate.passed()) {
                passed++;
            }
        }
        return new EvalCompletionReport(baseInput, packDirs, minRuns, minPassedPerRun,
                packs, gates, passed, gates.size() - passed);
    }

    private static String modelSurfaceForPack(Path packDir) throws IOException {
        Path runNotes = packDir.resolve("run-notes.md");
        if (!Files.isRegularFile(runNotes)) {
            return "";
        }
        return runNotesField(Files.readAllLines(runNotes), "Model or surface:");
    }

    private static EvalAuditGate findAuditGate(EvalAuditReport report, String name) {
        for (EvalAuditGate gate : report.gates()) {
            if (gate.name().equals(name)) {
                return gate;
            }
        }
        return null;
    }

    private static EvalAuditReport collectEvalAudit(Path baseInput, Path packDir, int requestedMinPassed)
            throws Exception {
        Path manifest = packDir.resolve("manifest.tsv");
        Path readme = packDir.resolve("README.md");
        Path runNotes = packDir.resolve("run-notes.md");
        Path statusArtifact = packDir.resolve("status.md");
        Path scoreArtifact = packDir.resolve("score.md");
        Path handoffLock = packDir.resolve("handoff-lock.tsv");
        Path baselineLock = packDir.resolve("baseline-lock.tsv");

        List<EvalAuditGate> gates = new ArrayList<>();
        boolean manifestExists = Files.isRegularFile(manifest);
        boolean readmeExists = Files.isRegularFile(readme);
        boolean runNotesExists = Files.isRegularFile(runNotes);
        List<EditManifestRow> rows = manifestExists ? readEditManifest(manifest) : List.of();
        int minPassed = requestedMinPassed >= 0 ? requestedMinPassed : rows.size();

        gates.add(auditGate("readme_artifact", readmeExists,
                readmeExists ? "present" : "missing", "README.md exists", readme.toString()));
        gates.add(auditGate("manifest_artifact", manifestExists,
                manifestExists ? "present" : "missing", "manifest.tsv exists", manifest.toString()));
        gates.add(auditGate("run_notes_artifact", runNotesExists,
                runNotesExists ? "present" : "missing", "run-notes.md exists", runNotes.toString()));
        gates.add(auditGate("status_artifact", Files.isRegularFile(statusArtifact),
                Files.isRegularFile(statusArtifact) ? "present" : "missing",
                "status.md exists", statusArtifact.toString()));
        gates.add(auditGate("score_artifact", Files.isRegularFile(scoreArtifact),
                Files.isRegularFile(scoreArtifact) ? "present" : "missing",
                "score.md exists", scoreArtifact.toString()));

        int briefCount = 0;
        for (EditManifestRow row : rows) {
            if (Files.isRegularFile(packDir.resolve(safeFileStem(row.name()) + ".md"))) {
                briefCount++;
            }
        }
        gates.add(auditGate("task_briefs", manifestExists && briefCount == rows.size(),
                briefCount + "/" + rows.size(), "one task brief per manifest row", packDir.toString()));
        HandoffLockAudit lockAudit = Files.isRegularFile(handoffLock)
                ? auditHandoffLock(packDir, handoffLock)
                : new HandoffLockAudit(false, "missing");
        gates.add(auditGate("handoff_lock", lockAudit.passed(),
                lockAudit.note(), "handoff-lock.tsv hashes match generated handoff files",
                handoffLock.toString()));
        BaselineLockAudit baselineAudit = Files.isRegularFile(baselineLock)
                ? auditBaselineLock(rows, baselineLock)
                : new BaselineLockAudit(false, "missing");
        gates.add(auditGate("baseline_lock", baselineAudit.passed(),
                baselineAudit.note(), "baseline-lock.tsv run-source hashes are unchanged except expected files",
                baselineLock.toString()));
        PreparedSourceScopeAudit sourceScopeAudit = auditPreparedSourceScope(rows);
        gates.add(auditGate("prepared_source_scope", sourceScopeAudit.passed(),
                sourceScopeAudit.note(), "prepared run trees contain only reviewable ATF source files",
                manifest.toString()));
        IndependenceHygieneAudit hygieneAudit = auditIndependenceHygiene(packDir, rows);
        gates.add(auditGate("independence_hygiene", hygieneAudit.clean(),
                hygieneAudit.note(), "handoff Markdown contains no controlled-solution fingerprints",
                packDir.toString()));

        RunNotesAudit runNotesAudit = runNotesExists
                ? auditRunNotes(runNotes, rows)
                : new RunNotesAudit(false, false, "run-notes.md missing", "run-notes.md missing");
        gates.add(auditGate("run_notes_metadata", runNotesAudit.metadataFilled(),
                runNotesAudit.metadataNote(), "model, operator, date, and independence notes filled",
                runNotes.toString()));
        gates.add(auditGate("run_notes_task_rows", runNotesAudit.taskRowsPresent(),
                runNotesAudit.taskRowsNote(), "one retry row per manifest task", runNotes.toString()));

        EvalStatusReport statusReport = null;
        String statusError = "";
        if (manifestExists) {
            try {
                statusReport = collectEvalStatus(baseInput, manifest);
            } catch (Exception e) {
                statusError = e.getMessage();
            }
        }
        boolean allReady = statusReport != null && statusReport.ready() == statusReport.rows().size();
        gates.add(auditGate("readiness", allReady,
                statusReport == null ? statusError : statusSummary(statusReport),
                "all manifest rows are ready", manifest.toString()));

        EditScoreReport scoreReport = null;
        String scoreError = "";
        if (manifestExists && allReady) {
            try {
                scoreReport = collectEditManifestScores(baseInput, manifest);
            } catch (Exception e) {
                scoreError = e.getMessage();
            }
        } else if (manifestExists) {
            scoreError = "skipped because readiness gate failed";
        }
        boolean enoughPassed = scoreReport != null && scoreReport.passed() >= minPassed;
        gates.add(auditGate("score_pass_count", enoughPassed,
                scoreReport == null ? scoreError : editScoreSummary(scoreReport),
                "at least " + minPassed + " manifest row(s) pass", manifest.toString()));

        if (hasTargetTaggedRows(rows)) {
            boolean kikoshi = hasPassedTargetRow(scoreReport, "kikoshi");
            boolean castle = hasPassedTargetRow(scoreReport, "riverton")
                    || hasPassedTargetRow(scoreReport, "canal")
                    || hasPassedTargetRow(scoreReport, "castle");
            boolean polish = hasPassedTargetRow(scoreReport, "polish");
            gates.add(auditGate("target_family_coverage", kikoshi && castle && polish,
                    "kikoshi=" + kikoshi + ", castle_or_canal=" + castle + ", polish=" + polish,
                    "passed rows cover Kikoshi-scale, castle/canal, and dense polish tasks",
                    manifest.toString()));
        }

        int passed = 0;
        for (EvalAuditGate gate : gates) {
            if (gate.passed()) {
                passed++;
            }
        }
        return new EvalAuditReport(baseInput, packDir, manifest, minPassed, gates, passed,
                gates.size() - passed);
    }

    private static void writeHandoffLock(
            Path outDir,
            Path manifest,
            List<EditEvalTask> tasks,
            Path lockFile) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# path\tsha256");
        List<Path> files = new ArrayList<>();
        files.add(outDir.resolve("README.md"));
        files.add(manifest);
        for (EditEvalTask task : tasks) {
            files.add(outDir.resolve(safeFileStem(task.id()) + ".md"));
            Path isolatedReadme = isolatedHandoffDir(outDir, task.id()).resolve("README.md");
            if (Files.isRegularFile(isolatedReadme)) {
                files.add(isolatedReadme);
            }
        }
        for (Path file : files) {
            lines.add(relativeToDirectory(outDir, file) + "\t" + sha256(file));
        }
        Files.writeString(lockFile, String.join(System.lineSeparator(), lines)
                + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static void writeBaselineLock(Path lockFile, List<EditManifestRow> rows) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# task\tpath\tsha256");
        for (EditManifestRow row : rows) {
            Path runRoot = row.editedInput().toAbsolutePath().normalize().getParent();
            if (runRoot == null || !Files.isDirectory(runRoot)) {
                continue;
            }
            Set<String> relatives = new TreeSet<>();
            collectAtfRelatives(runRoot, relatives);
            for (String relative : relatives) {
                lines.add(row.name() + "\t" + relative + "\t" + sha256(runRoot.resolve(relative)));
            }
        }
        Files.writeString(lockFile, String.join(System.lineSeparator(), lines)
                + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static BaselineLockAudit auditBaselineLock(List<EditManifestRow> rows, Path lockFile) throws Exception {
        Map<String, EditManifestRow> byName = new TreeMap<>();
        for (EditManifestRow row : rows) {
            byName.put(row.name(), row);
        }
        Map<String, Integer> lockRowsByTask = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        List<String> lines = Files.readAllLines(lockFile);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length != 3) {
                failures.add("row " + (i + 1) + " invalid");
                continue;
            }
            EditManifestRow row = byName.get(parts[0]);
            if (row == null) {
                failures.add(parts[0] + " not in manifest");
                continue;
            }
            lockRowsByTask.merge(row.name(), 1, Integer::sum);
            Path runRoot = row.editedInput().toAbsolutePath().normalize().getParent();
            Path file = runRoot == null ? null : runRoot.resolve(parts[1]).normalize();
            if (file == null || !Files.isRegularFile(file)) {
                failures.add(row.name() + "/" + parts[1] + " missing");
                continue;
            }
            String actual = sha256(file);
            if (!actual.equals(parts[2]) && !parts[1].equals(row.expectedChangedFile())) {
                failures.add(row.name() + "/" + parts[1] + " changed");
            }
            checked++;
        }
        for (EditManifestRow row : rows) {
            if (!lockRowsByTask.containsKey(row.name())) {
                failures.add(row.name() + " has no baseline rows");
            }
        }
        if (checked == 0) {
            failures.add("no locked source files");
        }
        return new BaselineLockAudit(failures.isEmpty(),
                failures.isEmpty() ? checked + " file(s) match or are expected edits" : joinedOrNone(failures));
    }

    private static PreparedSourceScopeAudit auditPreparedSourceScope(List<EditManifestRow> rows) throws IOException {
        int checked = 0;
        List<String> failures = new ArrayList<>();
        for (EditManifestRow row : rows) {
            Path runRoot = row.editedInput().toAbsolutePath().normalize().getParent();
            if (runRoot == null || !Files.isDirectory(runRoot)) {
                failures.add(row.name() + " run root missing");
                continue;
            }
            try (var paths = Files.walk(runRoot)) {
                for (Path file : paths
                        .filter(Files::isRegularFile)
                        .sorted()
                        .toList()) {
                    Path relative = runRoot.relativize(file);
                    if (isPreparedSourceFile(relative)) {
                        checked++;
                    } else {
                        failures.add(row.name() + "/" + relative);
                    }
                }
            }
        }
        if (checked == 0) {
            failures.add("no prepared source files");
        }
        return new PreparedSourceScopeAudit(failures.isEmpty(),
                failures.isEmpty() ? checked + " source file(s) in scope" : joinedOrNone(failures));
    }

    private static HandoffLockAudit auditHandoffLock(Path packDir, Path lockFile) throws Exception {
        List<String> lines = Files.readAllLines(lockFile);
        int checked = 0;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length != 2) {
                failures.add("row " + (i + 1) + " invalid");
                continue;
            }
            Path file = packDir.resolve(parts[0]).normalize();
            if (!Files.isRegularFile(file)) {
                failures.add(parts[0] + " missing");
                continue;
            }
            String actual = sha256(file);
            if (!actual.equals(parts[1])) {
                failures.add(parts[0] + " changed");
            }
            checked++;
        }
        if (checked == 0) {
            failures.add("no locked files");
        }
        return new HandoffLockAudit(failures.isEmpty(),
                failures.isEmpty() ? checked + " file(s) match" : joinedOrNone(failures));
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(file));
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }

    private static IndependenceHygieneAudit auditIndependenceHygiene(
            Path packDir,
            List<EditManifestRow> rows) throws IOException {
        List<Path> files = new ArrayList<>();
        Path readme = packDir.resolve("README.md");
        if (Files.isRegularFile(readme)) {
            files.add(readme);
        }
        for (EditManifestRow row : rows) {
            Path brief = packDir.resolve(safeFileStem(row.name()) + ".md");
            if (Files.isRegularFile(brief)) {
                files.add(brief);
            }
            Path isolatedReadme = isolatedHandoffDir(packDir, row.name()).resolve("README.md");
            if (Files.isRegularFile(isolatedReadme)) {
                files.add(isolatedReadme);
            }
        }

        List<String> hits = new ArrayList<>();
        List<String> forbidden = List.of(
                "validation-trials/model-eval",
                "model-eval-",
                "target-edit-",
                "dense-edit-source",
                "edit-suite-",
                "recipe-edit-arch",
                "large-map-validation-report.md",
                "controlled solution",
                "controlled batch score");
        for (Path file : files) {
            String text = Files.readString(file).toLowerCase(Locale.ROOT);
            for (String pattern : forbidden) {
                if (text.contains(pattern)) {
                    hits.add(packDir.relativize(file) + ":" + pattern);
                }
            }
        }

        return new IndependenceHygieneAudit(hits.isEmpty(),
                hits.isEmpty() ? files.size() + " handoff file(s) clean" : joinedOrNone(hits));
    }

    private static RunNotesAudit auditRunNotes(Path runNotes, List<EditManifestRow> rows) throws IOException {
        List<String> lines = Files.readAllLines(runNotes);
        boolean model = !runNotesField(lines, "Model or surface:").isBlank();
        boolean operator = !runNotesField(lines, "Operator:").isBlank();
        boolean date = !runNotesField(lines, "Date:").isBlank();
        boolean independence = !runNotesField(lines, "Independence notes:").isBlank();
        boolean metadata = model && operator && date && independence;

        int taskRows = 0;
        for (EditManifestRow row : rows) {
            if (runNotesHasTaskRow(lines, row.name())) {
                taskRows++;
            }
        }

        return new RunNotesAudit(metadata, taskRows == rows.size(),
                "model=" + model + ", operator=" + operator + ", date=" + date
                        + ", independence=" + independence,
                taskRows + "/" + rows.size());
    }

    private static String runNotesField(List<String> lines, String label) {
        for (String line : lines) {
            if (line.startsWith(label)) {
                return line.substring(label.length()).trim();
            }
        }
        return "";
    }

    private static boolean runNotesHasTaskRow(List<String> lines, String taskName) {
        String quoted = "| `" + taskName + "` |";
        String plain = "| " + taskName + " |";
        for (String line : lines) {
            if (line.startsWith(quoted) || line.startsWith(plain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTargetTaggedRows(List<EditManifestRow> rows) {
        for (EditManifestRow row : rows) {
            String haystack = (row.name() + " " + row.expectedChangedFile()).toLowerCase(Locale.ROOT);
            if (haystack.contains("kikoshi") || haystack.contains("riverton")
                    || haystack.contains("canal") || haystack.contains("castle")
                    || haystack.contains("polish")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPassedTargetRow(EditScoreReport scoreReport, String needle) {
        if (scoreReport == null) {
            return false;
        }
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        for (EditScoreRow row : scoreReport.rows()) {
            String haystack = (row.manifestRow().name() + " "
                    + row.manifestRow().expectedChangedFile()).toLowerCase(Locale.ROOT);
            if (row.passed() && haystack.contains(lowerNeedle)) {
                return true;
            }
        }
        return false;
    }

    private static EvalAuditGate auditGate(String name, boolean passed, String value,
                                           String threshold, String note) {
        return new EvalAuditGate(name, passed, value == null ? "" : value, threshold, note);
    }

    private static String evalAuditSummary(EvalAuditReport report) {
        return "summary: " + report.passed() + "/" + report.gates().size()
                + " eval audit gate(s) passed";
    }

    private static String renderEvalAuditReport(EvalAuditReport report) {
        StringBuilder out = new StringBuilder();
        appendLine(out, "# Plan Edit Eval Audit");
        appendLine(out, "");
        appendLine(out, "Base source: `" + report.baseInput() + "`");
        appendLine(out, "Pack directory: `" + report.packDir() + "`");
        appendLine(out, "Manifest: `" + report.manifest() + "`");
        appendLine(out, "Minimum passed rows: `" + report.minPassed() + "`");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, evalAuditSummary(report));
        appendLine(out, "```");
        appendLine(out, "");
        appendLine(out, "| Result | Gate | Value | Threshold | Note |");
        appendLine(out, "| --- | --- | --- | --- | --- |");
        for (EvalAuditGate gate : report.gates()) {
            appendLine(out, "| `" + (gate.passed() ? "pass" : "fail") + "` | `"
                    + gate.name() + "` | `" + gate.value() + "` | `"
                    + gate.threshold() + "` | `" + gate.note() + "` |");
        }
        return out.toString();
    }

    private static String evalCompletionSummary(EvalCompletionReport report) {
        return "summary: " + report.passed() + "/" + report.gates().size()
                + " eval completion gate(s) passed";
    }

    private static String renderEvalCompletionReport(EvalCompletionReport report) {
        StringBuilder out = new StringBuilder();
        appendLine(out, "# Plan Edit Eval Completion");
        appendLine(out, "");
        appendLine(out, "Base source: `" + report.baseInput() + "`");
        appendLine(out, "Minimum runs: `" + report.minRuns() + "`");
        appendLine(out, "Minimum passed rows per run: `" + report.minPassedPerRun() + "`");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, evalCompletionSummary(report));
        appendLine(out, "```");
        appendLine(out, "");
        appendLine(out, "## Completion Gates");
        appendLine(out, "");
        appendLine(out, "| Result | Gate | Value | Threshold |");
        appendLine(out, "| --- | --- | --- | --- |");
        for (EvalAuditGate gate : report.gates()) {
            appendLine(out, "| `" + (gate.passed() ? "pass" : "fail") + "` | `"
                    + gate.name() + "` | `" + gate.value() + "` | `"
                    + gate.threshold() + "` |");
        }
        appendLine(out, "");
        appendLine(out, "## Pack Audits");
        appendLine(out, "");
        appendLine(out, "| Result | Pack | Model or surface | Audit |");
        appendLine(out, "| --- | --- | --- | --- |");
        for (EvalCompletionPack pack : report.packs()) {
            appendLine(out, "| `" + (pack.completed() ? "pass" : "fail") + "` | `"
                    + pack.packDir() + "` | `" + (pack.modelSurface().isBlank()
                    ? "(missing)" : pack.modelSurface()) + "` | `"
                    + evalAuditSummary(pack.audit()) + "` |");
        }
        return out.toString();
    }

    private static EvalStatusReport collectEvalStatus(Path baseInput, Path manifest) throws IOException {
        List<EditManifestRow> rows = readEditManifest(manifest);
        List<EvalStatusRow> statusRows = new ArrayList<>();
        int ready = 0;
        int missing = 0;
        int unedited = 0;
        int wrongFile = 0;

        for (EditManifestRow row : rows) {
            if (!Files.isRegularFile(row.editedInput())) {
                missing++;
                statusRows.add(new EvalStatusRow("missing", row, List.of()));
                continue;
            }

            List<String> changedFiles = changedAtfFiles(baseInput, row.editedInput());
            boolean expectedFilePassed = row.expectedChangedFile().isEmpty()
                    || changedFiles.equals(List.of(row.expectedChangedFile()));
            if (changedFiles.isEmpty()) {
                unedited++;
                statusRows.add(new EvalStatusRow("unedited", row, changedFiles));
            } else if (!expectedFilePassed) {
                wrongFile++;
                statusRows.add(new EvalStatusRow("wrong_file", row, changedFiles));
            } else {
                ready++;
                statusRows.add(new EvalStatusRow("ready", row, changedFiles));
            }
        }

        return new EvalStatusReport(baseInput, manifest, statusRows, ready, missing, unedited, wrongFile);
    }

    private static String statusSummary(EvalStatusReport report) {
        return "summary: ready=" + report.ready()
                + " missing=" + report.missing()
                + " unedited=" + report.unedited()
                + " wrong_file=" + report.wrongFile()
                + " total=" + report.rows().size();
    }

    private static String renderEvalStatusReport(EvalStatusReport report) {
        StringBuilder out = new StringBuilder();
        appendLine(out, "# Plan Edit Eval Status");
        appendLine(out, "");
        appendLine(out, "Base source: `" + report.baseInput() + "`");
        appendLine(out, "Manifest: `" + report.manifest() + "`");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, statusSummary(report));
        appendLine(out, "```");
        appendLine(out, "");
        appendLine(out, "| Status | Task | Edited world | Expected file | Changed files |");
        appendLine(out, "| --- | --- | --- | --- | --- |");
        for (EvalStatusRow row : report.rows()) {
            EditManifestRow manifestRow = row.manifestRow();
            appendLine(out, "| `" + row.status() + "` | `" + manifestRow.name() + "` | `"
                    + manifestRow.editedInput() + "` | `" + manifestRow.expectedChangedFile()
                    + "` | `" + joinedOrNone(row.changedFiles()) + "` |");
        }
        return out.toString();
    }

    private static String joinedOrNone(List<String> values) {
        return values.isEmpty() ? "(none)" : String.join(",", values);
    }

    private static String expectedChangedFileOutput(EditManifestRow row) {
        return row.expectedChangedFile().isEmpty()
                ? ""
                : " expected_changed_file=" + row.expectedChangedFile();
    }

    private static List<EditManifestRow> readEditManifest(Path manifest) throws IOException {
        Path manifestRoot = manifest.toAbsolutePath().normalize().getParent();
        List<EditManifestRow> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(manifest);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length != 4 && parts.length != 5) {
                throw new IllegalArgumentException("Invalid edit manifest row " + (i + 1)
                        + ": expected name<TAB>edited.atf<TAB>max_source_lines<TAB>min_expanded_lines"
                        + "[<TAB>expected_changed_file]");
            }
            Path editedInput = Path.of(parts[1]);
            if (!editedInput.isAbsolute()) {
                editedInput = manifestRoot.resolve(editedInput).normalize();
            }
            rows.add(new EditManifestRow(parts[0], editedInput,
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    parts.length == 5 ? parts[4] : ""));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Edit manifest has no rows: " + manifest);
        }
        return rows;
    }

    private static void planEvalBrief(String[] args) throws Exception {
        Path baseInput = Path.of(args[1]);
        Path tasksFile = Path.of(args[2]);
        String taskId = args[3];
        String runName = "MODEL-RUN";
        Path runsDir = defaultEvalRunsDir();
        boolean withContext = false;
        int contextLines = 160;
        for (int i = 4; i < args.length; i++) {
            switch (args[i]) {
                case "--run" -> runName = requireValue(args, ++i);
                case "--runs-dir" -> runsDir = Path.of(requireValue(args, ++i));
                case "--with-context" -> withContext = true;
                case "--context-lines" -> contextLines = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }
        if (contextLines < 0) {
            throw new IllegalArgumentException("--context-lines must be >= 0");
        }

        EditEvalTask task = readEvalTasks(tasksFile).stream()
                .filter(row -> row.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No eval task found for id " + taskId));
        Path editedInput = evalRunWorldAtf(runsDir, runName);
        System.out.print(renderPlanEvalBrief(baseInput, tasksFile, task, runName,
                editedInput, false, withContext, contextLines,
                tasksFile.toAbsolutePath().normalize().getParent()));
    }

    private static void planEvalPack(String[] args) throws Exception {
        Path baseInput = Path.of(args[1]);
        Path tasksFile = Path.of(args[2]);
        Path outDir = Path.of(args[3]);
        String runPrefix = "model";
        Path runsDir = null;
        boolean withContext = false;
        boolean prepareRuns = false;
        boolean overwriteRuns = false;
        boolean independent = false;
        boolean isolatedHandoffs = false;
        boolean writeInitialReports = false;
        String modelSurface = "";
        String operator = "";
        String date = "";
        String independenceNotes = "";
        int contextLines = 160;
        int initialMinPassed = -1;
        for (int i = 4; i < args.length; i++) {
            switch (args[i]) {
                case "--run-prefix" -> runPrefix = requireValue(args, ++i);
                case "--runs-dir" -> runsDir = Path.of(requireValue(args, ++i));
                case "--with-context" -> withContext = true;
                case "--independent" -> independent = true;
                case "--isolated-handoffs" -> isolatedHandoffs = true;
                case "--prepare-runs" -> prepareRuns = true;
                case "--overwrite-runs" -> overwriteRuns = true;
                case "--write-initial-reports" -> writeInitialReports = true;
                case "--initial-min-passed" -> initialMinPassed = Integer.parseInt(requireValue(args, ++i));
                case "--model-surface" -> modelSurface = requireValue(args, ++i);
                case "--operator" -> operator = requireValue(args, ++i);
                case "--date" -> date = requireValue(args, ++i);
                case "--independence-notes" -> independenceNotes = requireValue(args, ++i);
                case "--context-lines" -> contextLines = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }
        if (contextLines < 0) {
            throw new IllegalArgumentException("--context-lines must be >= 0");
        }
        if (runsDir == null) {
            runsDir = independent ? outDir.resolve("runs") : defaultEvalRunsDir();
        }
        if (independent) {
            prepareRuns = true;
        }
        if (isolatedHandoffs) {
            prepareRuns = true;
        }

        List<EditEvalTask> tasks = readEvalTasks(tasksFile);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Eval task file has no rows: " + tasksFile);
        }
        Files.createDirectories(outDir);
        Path sourceRoot = baseInput.toAbsolutePath().normalize().getParent();
        List<String> manifestRows = new ArrayList<>();
        manifestRows.add("# name\tedited_world_atf\tmax_source_lines\tmin_expanded_lines\texpected_changed_file");
        int preparedRuns = 0;
        for (EditEvalTask task : tasks) {
            String runName = runPrefix + "-" + task.id();
            Path editedInput = isolatedHandoffs
                    ? isolatedHandoffWorldAtf(outDir, task.id())
                    : evalRunWorldAtf(runsDir, runName);
            String brief = renderPlanEvalBrief(baseInput, tasksFile, task, runName,
                    editedInput, prepareRuns, withContext, contextLines, outDir);
            Path briefFile = outDir.resolve(safeFileStem(task.id()) + ".md");
            Files.writeString(briefFile, brief, StandardCharsets.UTF_8);
            if (isolatedHandoffs) {
                Path handoffReadme = isolatedHandoffDir(outDir, task.id()).resolve("README.md");
                Files.createDirectories(handoffReadme.getParent());
                Files.writeString(handoffReadme, brief, StandardCharsets.UTF_8);
            }
            if (prepareRuns) {
                Path editedRoot = editedInput.getParent();
                prepareEvalRunSource(sourceRoot, editedRoot, overwriteRuns);
                preparedRuns++;
            }
            manifestRows.add(task.id() + "\t" + relativeToDirectory(outDir, editedInput)
                    + "\t" + task.maxSourceLines()
                    + "\t" + task.minExpandedLines()
                    + "\t" + task.expectedChangedFile());
        }
        Path manifest = outDir.resolve("manifest.tsv");
        Files.writeString(manifest, String.join(System.lineSeparator(), manifestRows)
                + System.lineSeparator(), StandardCharsets.UTF_8);
        Path readme = outDir.resolve("README.md");
        Files.writeString(readme, renderPlanEvalPackReadme(
                baseInput, tasksFile, outDir, manifest, tasks, runPrefix, runsDir,
                prepareRuns, isolatedHandoffs, contextLines),
                StandardCharsets.UTF_8);
        Path runNotes = outDir.resolve("run-notes.md");
        Files.writeString(runNotes, renderPlanEvalRunNotes(tasks, runPrefix,
                modelSurface, operator, date, independenceNotes), StandardCharsets.UTF_8);
        Path handoffLock = outDir.resolve("handoff-lock.tsv");
        writeHandoffLock(outDir, manifest, tasks, handoffLock);
        Path baselineLock = outDir.resolve("baseline-lock.tsv");
        if (prepareRuns) {
            writeBaselineLock(baselineLock, readEditManifest(manifest));
        }

        System.out.println("Plan edit eval pack for " + baseInput.toAbsolutePath());
        System.out.println("tasks: " + tasks.size());
        System.out.println("brief_dir: " + outDir.toAbsolutePath());
        System.out.println("runs_dir: " + runsDir.toAbsolutePath());
        if (isolatedHandoffs) {
            System.out.println("handoffs_dir: " + outDir.resolve("handoffs").toAbsolutePath());
        }
        System.out.println("manifest: " + manifest.toAbsolutePath());
        System.out.println("readme: " + readme.toAbsolutePath());
        System.out.println("run_notes: " + runNotes.toAbsolutePath());
        System.out.println("handoff_lock: " + handoffLock.toAbsolutePath());
        if (prepareRuns) {
            System.out.println("baseline_lock: " + baselineLock.toAbsolutePath());
        }
        if (prepareRuns) {
            System.out.println("prepared_runs: " + preparedRuns
                    + (overwriteRuns ? " (overwritten)" : ""));
        }
        if (independent) {
            IndependenceHygieneAudit hygieneAudit = auditIndependenceHygiene(outDir, readEditManifest(manifest));
            System.out.println("independent_handoff: " + (hygieneAudit.clean() ? "pass" : "fail")
                    + " " + hygieneAudit.note());
            if (!hygieneAudit.clean()) {
                System.exit(1);
            }
        }
        if (writeInitialReports) {
            Path statusOut = outDir.resolve("status.md");
            EvalStatusReport statusReport = collectEvalStatus(baseInput, manifest);
            Files.writeString(statusOut, renderEvalStatusReport(statusReport), StandardCharsets.UTF_8);
            System.out.println("initial_status: " + statusOut.toAbsolutePath());
            System.out.println(statusSummary(statusReport));

            Path auditOut = outDir.resolve("audit.md");
            EvalAuditReport auditReport = collectEvalAudit(baseInput, outDir, initialMinPassed);
            Files.writeString(auditOut, renderEvalAuditReport(auditReport), StandardCharsets.UTF_8);
            System.out.println("initial_audit: " + auditOut.toAbsolutePath());
            System.out.println(evalAuditSummary(auditReport));
        }
        System.out.println("score_command:");
        System.out.println("  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest "
                + baseInput + " " + manifest);
    }

    private static void prepareEvalRunSource(Path sourceRoot, Path editedRoot, boolean overwriteRuns) throws IOException {
        if (Files.exists(editedRoot)) {
            if (!overwriteRuns) {
                throw new IllegalArgumentException("Eval run directory already exists: " + editedRoot
                        + " (rerun with --overwrite-runs to replace it)");
            }
            deleteTree(editedRoot);
        }
        copyPreparedSourceTree(sourceRoot, editedRoot);
    }

    private static String renderPlanEvalPackReadme(
            Path baseInput,
            Path tasksFile,
            Path outDir,
            Path manifest,
            List<EditEvalTask> tasks,
            String runPrefix,
            Path runsDir,
            boolean prepareRuns,
            boolean isolatedHandoffs,
            int contextLines) {
        StringBuilder out = new StringBuilder();
        appendLine(out, "# Plan Edit Eval Pack");
        appendLine(out, "");
        appendLine(out, "Base source: `" + baseInput + "`");
        appendLine(out, "Task file: `" + tasksFile + "`");
        appendLine(out, "Manifest: `" + manifest + "`");
        appendLine(out, "Run directory root: `" + runsDir + "`");
        appendLine(out, "Context line cap per embedded command: `"
                + (contextLines == 0 ? "unlimited" : contextLines) + "`");
        appendLine(out, "Prepared run directories: `" + (prepareRuns ? "yes" : "no") + "`");
        appendLine(out, "Prepared source scope: `world.atf`, `meta.atf`, `groups/*.atf`, `components/*.atf`");
        appendLine(out, "Isolated handoff directories: `" + (isolatedHandoffs ? "yes" : "no") + "`");
        appendLine(out, "");
        appendLine(out, "## Handoff Scope");
        appendLine(out, "");
        appendLine(out, "- Give a model one task brief and the prepared source tree named by that brief.");
        if (isolatedHandoffs) {
            appendLine(out, "- Prefer giving the model only the matching `handoffs/<task>/` directory.");
        }
        appendLine(out, "- The model should not inspect earlier validation examples or prior answer attempts.");
        appendLine(out, "- Record model/surface, operator, date, independence notes, and retries in `run-notes.md`.");
        appendLine(out, "");
        appendLine(out, "## Workflow");
        appendLine(out, "");
        appendLine(out, "1. Give each task brief Markdown file to a model run.");
        appendLine(out, "2. The model edits only the `edited_world_atf` source tree named in its brief.");
        appendLine(out, "3. Run the brief's `diagnose_command` after editing.");
        appendLine(out, "4. Fill in `run-notes.md` with the model/surface, date, operator, and retries.");
        appendLine(out, "5. Check readiness without expansion:");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, "java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-eval-status "
                + baseInput + " " + manifest);
        appendLine(out, "```");
        appendLine(out, "");
        appendLine(out, "6. Write a Markdown status artifact:");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, "java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-eval-status-report "
                + baseInput + " " + manifest + " " + outDir.resolve("status.md"));
        appendLine(out, "```");
        appendLine(out, "");
        appendLine(out, "7. Write a full score artifact:");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, "java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest-report "
                + baseInput + " " + manifest + " " + outDir.resolve("score.md"));
        appendLine(out, "```");
        appendLine(out, "");
        appendLine(out, "8. Score the whole batch in CI/terminal with:");
        appendLine(out, "");
        appendLine(out, "```text");
        appendLine(out, "java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest "
                + baseInput + " " + manifest);
        appendLine(out, "```");
        appendLine(out, "");
        if (!prepareRuns) {
            appendLine(out, "Run directories were not prepared. Copy the base source tree to each edited");
            appendLine(out, "path from the table before handing the task to a model, or regenerate with");
            appendLine(out, "`--prepare-runs`.");
            appendLine(out, "");
        }
        appendLine(out, "## Tasks");
        appendLine(out, "");
        appendLine(out, "| Task | Brief | Edited world | Expected file | Limits |");
        appendLine(out, "| --- | --- | --- | --- | --- |");
        for (EditEvalTask task : tasks) {
            String runName = runPrefix + "-" + task.id();
            Path brief = outDir.resolve(safeFileStem(task.id()) + ".md");
            Path editedInput = isolatedHandoffs
                    ? isolatedHandoffWorldAtf(outDir, task.id())
                    : evalRunWorldAtf(runsDir, runName);
            appendLine(out, "| `" + task.id() + "` | `" + brief.getFileName() + "` | `"
                    + editedInput + "` | `" + task.expectedChangedFile() + "` | <="
                    + task.maxSourceLines() + " source, >=" + task.minExpandedLines()
                    + " expanded |");
        }
        return out.toString();
    }

    private static String renderPlanEvalRunNotes(
            List<EditEvalTask> tasks,
            String runPrefix,
            String modelSurface,
            String operator,
            String date,
            String independenceNotes) {
        StringBuilder out = new StringBuilder();
        appendLine(out, "# Model Eval Run Notes");
        appendLine(out, "");
        appendLine(out, "Run prefix: `" + runPrefix + "`");
        appendLine(out, "Model or surface: " + modelSurface);
        appendLine(out, "Operator: " + operator);
        appendLine(out, "Date: " + date);
        appendLine(out, "Independence notes: " + independenceNotes);
        appendLine(out, "");
        appendLine(out, "## Per-Task Retries");
        appendLine(out, "");
        appendLine(out, "| Task | Brief | Retry count | Notes |");
        appendLine(out, "| --- | --- | --- | --- |");
        for (EditEvalTask task : tasks) {
            appendLine(out, "| `" + task.id() + "` | `" + safeFileStem(task.id()) + ".md` | 0 |  |");
        }
        appendLine(out, "");
        appendLine(out, "## Evidence Checklist");
        appendLine(out, "");
        appendLine(out, "- [ ] README.md");
        appendLine(out, "- [ ] run-notes.md");
        appendLine(out, "- [ ] manifest.tsv");
        appendLine(out, "- [ ] status.md");
        appendLine(out, "- [ ] final plan-edit-manifest output");
        appendLine(out, "- [ ] edited run source trees");
        return out.toString();
    }

    private static String renderPlanEvalBrief(
            Path baseInput,
            Path tasksFile,
            EditEvalTask task,
            String runName,
            Path editedInput,
            boolean sourcePrepared,
            boolean withContext,
            int contextLines,
            Path manifestDirectory) throws Exception {
        Path sourceRoot = baseInput.toAbsolutePath().normalize().getParent();
        Path editedRoot = editedInput.getParent();
        String editedPathForManifest = relativeToDirectory(manifestDirectory, editedInput);

        StringBuilder out = new StringBuilder();
        appendLine(out, "Plan edit eval brief for " + baseInput.toAbsolutePath());
        appendLine(out, "task: " + task.id());
        appendLine(out, "title: " + task.title());
        appendLine(out, "handoff_scope:");
        appendLine(out, "  use only this brief and the prepared source tree named below");
        appendLine(out, "  do not inspect earlier validation examples or prior answer attempts");
        appendLine(out, "  record any retry or diagnostic-fix request in run-notes.md");
        appendLine(out, "prompt:");
        appendLine(out, task.prompt());
        appendLine(out, "context_commands:");
        String[] contextCommands = task.contextCommands().split(" \\|\\| ");
        for (String command : contextCommands) {
            appendLine(out, "  " + command);
        }
        if (withContext) {
            appendLine(out, "embedded_context:");
            appendLine(out, "context_line_limit_per_command: "
                    + (contextLines == 0 ? "unlimited" : contextLines));
            for (String command : contextCommands) {
                appendLine(out, ">>> " + command);
                out.append(limitedLines(evalContextOutput(command), contextLines));
            }
        }
        appendLine(out, "output_contract:");
        if (sourcePrepared) {
            appendLine(out, "  prepared_source_tree: " + editedRoot);
            appendLine(out, "  edit the prepared_source_tree in place; do not recopy the base source");
        } else {
            appendLine(out, "  source_tree: " + sourceRoot);
            appendLine(out, "  copy source_tree to: " + editedRoot);
        }
        appendLine(out, "  edited_world_atf: " + editedInput);
        appendLine(out, "  edit only the intended ATF source file");
        appendLine(out, "  run plan-diagnose before scoring");
        appendLine(out, "diagnose_command:");
        appendLine(out, "  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose " + editedInput);
        appendLine(out, "manifest_row:");
        appendLine(out, task.id() + "\t" + editedPathForManifest
                + "\t" + task.maxSourceLines()
                + "\t" + task.minExpandedLines()
                + "\t" + task.expectedChangedFile());
        appendLine(out, "score_command:");
        appendLine(out, "  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-eval-score "
                + baseInput + " " + tasksFile + " " + task.id() + " " + editedInput);
        return out.toString();
    }

    private static Path defaultEvalRunsDir() {
        return Path.of("target", "validation-trials");
    }

    private static Path evalRunWorldAtf(Path runsDir, String runName) {
        return runsDir.resolve(runName).resolve("world.atf");
    }

    private static Path isolatedHandoffDir(Path outDir, String taskId) {
        return outDir.resolve("handoffs").resolve(safeFileStem(taskId));
    }

    private static Path isolatedHandoffWorldAtf(Path outDir, String taskId) {
        return isolatedHandoffDir(outDir, taskId).resolve("source").resolve("world.atf");
    }

    private static String relativeToDirectory(Path directory, Path target) {
        if (directory == null) {
            return target.toAbsolutePath().normalize().toString();
        }
        Path root = directory.toAbsolutePath().normalize();
        Path edited = target.toAbsolutePath().normalize();
        try {
            return root.relativize(edited).toString();
        } catch (IllegalArgumentException e) {
            return edited.toString();
        }
    }

    private static String safeFileStem(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void runEvalContextCommand(String command, int lineLimit) throws Exception {
        String output = evalContextOutput(command);
        System.out.print(limitedLines(output, lineLimit));
    }

    private static String evalContextOutput(String command) throws Exception {
        List<String> tokens = sourceTokens(command);
        int commandIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).startsWith("plan-")) {
                commandIndex = i;
                break;
            }
        }
        if (commandIndex < 0) {
            throw new IllegalArgumentException("Eval context command does not contain a plan-* command: " + command);
        }
        String[] args = tokens.subList(commandIndex, tokens.size()).toArray(String[]::new);
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            switch (args[0]) {
                case "plan-workbench" -> planWorkbench(args);
                case "plan-snippet" -> planSnippet(args);
                case "plan-recipe" -> planRecipe(args);
                default -> throw new IllegalArgumentException("Unsupported eval context command: " + args[0]);
            }
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static String limitedLines(String output, int lineLimit) {
        StringBuilder out = new StringBuilder();
        List<String> lines = output.lines().toList();
        int printed = lineLimit == 0 ? lines.size() : Math.min(lines.size(), lineLimit);
        for (int i = 0; i < printed; i++) {
            appendLine(out, lines.get(i));
        }
        if (lineLimit > 0 && lines.size() > lineLimit) {
            appendLine(out, "... truncated " + (lines.size() - lineLimit)
                    + " line(s); rerun with --context-lines 0 for full context");
        }
        return out.toString();
    }

    private static void appendLine(StringBuilder out, String line) {
        out.append(line).append(System.lineSeparator());
    }

    private static void planEvalScore(Path baseInput, Path tasksFile, String taskId, Path editedInput)
            throws IOException {
        EditEvalTask task = readEvalTasks(tasksFile).stream()
                .filter(row -> row.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No eval task found for id " + taskId));
        PlanExpander.PlanEditTrialReport report = new PlanExpander()
                .editTrial(baseInput, editedInput, task.maxSourceLines(), task.minExpandedLines());
        List<String> changedFiles = changedAtfFiles(baseInput, editedInput);
        boolean expectedFilePassed = changedFiles.equals(List.of(task.expectedChangedFile()));
        boolean passed = report.passed() && expectedFilePassed;

        System.out.println("Plan edit eval score for " + baseInput.toAbsolutePath());
        System.out.println("task: " + task.id());
        System.out.println("title: " + task.title());
        System.out.println((passed ? "pass" : "fail")
                + " edited=" + editedInput.toAbsolutePath()
                + " source_changed_lines=" + report.sourceChangedLines()
                + " max_source_lines=" + task.maxSourceLines()
                + " expanded_changed_lines=" + report.expandedChangedLines()
                + " min_expanded_lines=" + task.minExpandedLines()
                + " plan_warnings=" + report.planWarnings()
                + " core_warnings=" + report.coreWarnings()
                + " changed_files=" + String.join(",", changedFiles)
                + " expected_changed_file=" + task.expectedChangedFile()
                + " expected_file=" + (expectedFilePassed ? "pass" : "fail"));
        if (!passed) {
            System.exit(1);
        }
    }

    private static List<EditEvalTask> readEvalTasks(Path tasksFile) throws IOException {
        List<EditEvalTask> tasks = new ArrayList<>();
        List<String> lines = Files.readAllLines(tasksFile);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length != 7) {
                throw new IllegalArgumentException("Invalid eval task row " + (i + 1)
                        + ": expected id<TAB>title<TAB>prompt<TAB>context_commands"
                        + "<TAB>max_source_lines<TAB>min_expanded_lines<TAB>expected_changed_file");
            }
            tasks.add(new EditEvalTask(parts[0], parts[1], parts[2], parts[3],
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), parts[6]));
        }
        return tasks;
    }

    private static List<String> changedAtfFiles(Path baseInput, Path editedInput) throws IOException {
        Path baseRoot = baseInput.toAbsolutePath().normalize().getParent();
        Path editedRoot = editedInput.toAbsolutePath().normalize().getParent();
        Set<String> relatives = new TreeSet<>();
        collectAtfRelatives(baseRoot, relatives);
        collectAtfRelatives(editedRoot, relatives);

        List<String> changed = new ArrayList<>();
        for (String relative : relatives) {
            Path base = baseRoot.resolve(relative);
            Path edited = editedRoot.resolve(relative);
            if (!Files.isRegularFile(base) || !Files.isRegularFile(edited)
                    || Files.mismatch(base, edited) != -1L) {
                changed.add(relative);
            }
        }
        return changed;
    }

    private static void collectAtfRelatives(Path root, Set<String> relatives) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".atf"))
                    .toList()) {
                relatives.add(root.relativize(path).toString());
            }
        }
    }

    private static void printMetricMap(String title, Map<String, Integer> values) {
        System.out.println(title + ":");
        if (values.isEmpty()) {
            System.out.println("  none: 0");
            return;
        }
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private static String quoteForOutput(String value) {
        return "\"" + value + "\"";
    }

    private static String stripSourceComment(String line) {
        int index = line.indexOf('#');
        return index < 0 ? line : line.substring(0, index);
    }

    private static String firstSourceToken(String line) {
        int index = 0;
        while (index < line.length() && !Character.isWhitespace(line.charAt(index)) && line.charAt(index) != '{') {
            index++;
        }
        return line.substring(0, index);
    }

    private static int nonBlankSourceLines(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String stripped = stripSourceComment(line).trim();
            if (!stripped.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static int braceDelta(String line) {
        int delta = 0;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '{') {
                delta++;
            } else if (!quoted && c == '}') {
                delta--;
            }
        }
        return delta;
    }

    private static boolean isGroupFile(Path relative) {
        return relative.getNameCount() > 1
                && "groups".equals(relative.getName(0).toString())
                && relative.getFileName().toString().endsWith(".atf");
    }

    private static boolean isComponentFile(Path relative) {
        return relative.getNameCount() > 1
                && "components".equals(relative.getName(0).toString())
                && relative.getFileName().toString().endsWith(".atf");
    }

    private record SourceMetrics(
            Path sourceRoot,
            int atfFiles,
            int groupFiles,
            int componentFiles,
            int sourceLines,
            int nonBlankSourceLines,
            int includeLines,
            int maxFileLines,
            Path largestFile,
            int maxGroupNonBlankLines,
            Path largestGroup,
            int maxComponentNonBlankLines,
            Path largestComponent,
            Map<String, Integer> commandCounts) {
    }

    private record PlanCatalogEntry(
            String file,
            int line,
            String groupName,
            String kind,
            String name,
            String value) {
    }

    private record PlanSnippetTarget(
            String file,
            int line,
            String groupName,
            String kind,
            String name) {
    }

    private record ClusterHint(int radius, int height) {
    }

    private record TargetClassThreshold(
            String profile,
            int routes,
            int crossings,
            int reservations,
            int landmarks,
            int distinctLandmarkComponents,
            int clusters,
            int repeatedDetail,
            int loot) {
    }

    private record EditManifestRow(
            String name,
            Path editedInput,
            int maxSourceLines,
            int minExpandedLines,
            String expectedChangedFile) {
    }

    private record EditEvalTask(
            String id,
            String title,
            String prompt,
            String contextCommands,
            int maxSourceLines,
            int minExpandedLines,
            String expectedChangedFile) {
    }

    private record EvalStatusRow(
            String status,
            EditManifestRow manifestRow,
            List<String> changedFiles) {
    }

    private record EvalStatusReport(
            Path baseInput,
            Path manifest,
            List<EvalStatusRow> rows,
            int ready,
            int missing,
            int unedited,
            int wrongFile) {
    }

    private record RunNotesAudit(
            boolean metadataFilled,
            boolean taskRowsPresent,
            String metadataNote,
            String taskRowsNote) {
    }

    private record IndependenceHygieneAudit(
            boolean clean,
            String note) {
    }

    private record HandoffLockAudit(
            boolean passed,
            String note) {
    }

    private record BaselineLockAudit(
            boolean passed,
            String note) {
    }

    private record PreparedSourceScopeAudit(
            boolean passed,
            String note) {
    }

    private record EvalAuditGate(
            String name,
            boolean passed,
            String value,
            String threshold,
            String note) {
    }

    private record EvalAuditReport(
            Path baseInput,
            Path packDir,
            Path manifest,
            int minPassed,
            List<EvalAuditGate> gates,
            int passed,
            int failed) {
    }

    private record EvalCompletionPack(
            Path packDir,
            String modelSurface,
            boolean completed,
            EvalAuditReport audit) {
    }

    private record EvalCompletionReport(
            Path baseInput,
            List<Path> packDirs,
            int minRuns,
            int minPassedPerRun,
            List<EvalCompletionPack> packs,
            List<EvalAuditGate> gates,
            int passed,
            int failed) {
    }

    private record EditScoreRow(
            EditManifestRow manifestRow,
            PlanExpander.PlanEditTrialReport report,
            List<String> changedFiles,
            boolean expectedFilePassed,
            boolean passed) {
    }

    private record EditScoreReport(
            Path baseInput,
            Path manifest,
            List<EditScoreRow> rows,
            int passed) {
    }

    private record PlanEvidence(
            Path input,
            String profile,
            Path editedInput,
            int editMaxSourceLines,
            int editMinExpandedLines,
            Path referenceWorld,
            List<Path> renderArtifacts,
            Path compareSource,
            Path compareRegenerated) {
    }

    private static void planRender(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        Path input = Path.of(args[1]);
        Path out = null;
        int scale = 0;
        int max = 1024;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(requireValue(args, ++i));
                case "--scale" -> scale = Integer.parseInt(requireValue(args, ++i));
                case "--max" -> max = Integer.parseInt(requireValue(args, ++i));
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }

        RenderGrid grid = new PlanExpander().overlay(input);
        if (grid.rows() == 0) {
            System.out.println("Nothing to render: no plan blocks found");
            return;
        }
        if (out == null) {
            System.out.println(new WorldRenderer().toAscii(grid));
            return;
        }
        WorldImageWriter.WriteResult result = new WorldImageWriter().write(grid, out, scale, max);
        System.out.println("Wrote plan overlay to " + out.toAbsolutePath() + " ("
                + result.width() + "x" + result.height() + "px, scale=" + result.scale()
                + (result.downsampled() ? ", sampled 1/" + result.sample() + " blocks" : "")
                + ", " + grid.cols() + "x" + grid.rows() + " cells)");
        System.out.print(result.legend());
    }

    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            usage();
            System.exit(2);
        }
        return args[index];
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void copyPreparedSourceTree(Path sourceRoot, Path targetRoot) throws IOException {
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList()) {
                Path relative = sourceRoot.relativize(source);
                if (!isPreparedSourceFile(relative)) {
                    continue;
                }
                Path target = targetRoot.resolve(relative.toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private static boolean isPreparedSourceFile(Path relative) {
        if (relative.getNameCount() == 1) {
            String file = relative.getFileName().toString();
            return "world.atf".equals(file) || "meta.atf".equals(file);
        }
        if (relative.getNameCount() == 2) {
            String dir = relative.getName(0).toString();
            String file = relative.getFileName().toString();
            return ("groups".equals(dir) || "components".equals(dir)) && file.endsWith(".atf");
        }
        return false;
    }

    private static void compare(Path sourceWorld, Path regeneratedWorld) throws Exception {
        ReferenceWorldRoundTripComparator.ComparisonReport report =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);
        System.out.println("Round-trip comparison OK: " + report.chunkCount()
                + " chunk(s), " + report.blockChecks() + " exact block check(s), "
                + report.biomeChecks() + " exact biome check(s), "
                + report.blockEntityChecks() + " block entity anchor check(s), "
                + report.entityChecks() + " entity anchor check(s)");
    }

    private static void usage() {
        System.err.println("Usage:");
        System.err.println("  java -jar anvil-tf.jar plan <input.atf>");
        System.err.println("  java -jar anvil-tf.jar plan-diagnose <input.atf>");
        System.err.println("  java -jar anvil-tf.jar plan-edit-trial <base.atf> <edited.atf> [--max-source-lines n] [--min-expanded-lines n]");
        System.err.println("  java -jar anvil-tf.jar plan-edit-suite <base.atf> <edited.atf>... [--max-source-lines n] [--min-expanded-lines n]");
        System.err.println("  java -jar anvil-tf.jar plan-edit-manifest <base.atf> <manifest.tsv>");
        System.err.println("  java -jar anvil-tf.jar plan-edit-manifest-report <base.atf> <manifest.tsv> <out.md>");
        System.err.println("  java -jar anvil-tf.jar plan-eval-status <base.atf> <manifest.tsv>");
        System.err.println("  java -jar anvil-tf.jar plan-eval-status-report <base.atf> <manifest.tsv> <out.md>");
        System.err.println("  java -jar anvil-tf.jar plan-eval-audit <base.atf> <pack-dir> [--min-passed n] [--out file.md]");
        System.err.println("  java -jar anvil-tf.jar plan-eval-completion <base.atf> <pack-dir>... [--min-runs n] [--min-passed-per-run n] [--out file.md]");
        System.err.println("  java -jar anvil-tf.jar plan-eval-pack <base.atf> <tasks.tsv> <out-dir> [--run-prefix name] [--runs-dir dir] [--independent] [--isolated-handoffs] [--model-surface name] [--operator name] [--date yyyy-mm-dd] [--independence-notes text] [--with-context] [--context-lines n] [--prepare-runs] [--overwrite-runs] [--write-initial-reports] [--initial-min-passed n]");
        System.err.println("  java -jar anvil-tf.jar plan-eval-brief <base.atf> <tasks.tsv> <task-id> [--run name] [--runs-dir dir] [--with-context] [--context-lines n]");
        System.err.println("  java -jar anvil-tf.jar plan-eval-score <base.atf> <tasks.tsv> <task-id> <edited.atf>");
        System.err.println("  java -jar anvil-tf.jar plan-expand <input.atf> <output-dir>");
        System.err.println("  java -jar anvil-tf.jar plan-gates <input.atf> [--profile skeleton|dense] [--edit-trial edited.atf] [--edit-max-source-lines n] [--edit-min-expanded-lines n] [--reference-world world-dir] [--render-artifact file.png]... [--compare-worlds source-world-dir regenerated-world-dir]");
        System.err.println("  java -jar anvil-tf.jar plan-metrics <input.atf>");
        System.err.println("  java -jar anvil-tf.jar plan-outline <input.atf>");
        System.err.println("  java -jar anvil-tf.jar plan-target-audit <input.atf>");
        System.err.println("  java -jar anvil-tf.jar plan-index <input.atf>");
        System.err.println("  java -jar anvil-tf.jar plan-workbench <input.atf> [--group name-or-file] [--kind route|reserve|landmark|cluster|scatter_in|loot] [--limit n] [--catalog-limit n] [--with-gates]");
        System.err.println("  java -jar anvil-tf.jar plan-snippet <input.atf> (--name handle [--group name-or-file] [--kind kind] | --component name | --style style.key [--group name-or-file] | --file file --line n) [--context n]");
        System.err.println("  java -jar anvil-tf.jar plan-recipe <input.atf> (--component name | --style style.key [--group name-or-file]) [--count n]");
        System.err.println("  java -jar anvil-tf.jar plan-report <input.atf> [--profile skeleton|dense] [--edit-trial edited.atf] [--edit-max-source-lines n] [--edit-min-expanded-lines n] [--reference-world world-dir] [--render-artifact file.png]... [--compare-worlds source-world-dir regenerated-world-dir]");
        System.err.println("  java -jar anvil-tf.jar plan-render <input.atf> [--out file.png] [--scale n] [--max n]");
        System.err.println("  java -jar anvil-tf.jar import <world-dir> <output.atf-or-dir> [--chunk-limit n] [--layout flat|reviewable]");
        System.err.println("  java -jar anvil-tf.jar <input.atf> <output-world-dir> [--validate]");
        System.err.println("  java -jar anvil-tf.jar diagnose <input.atf>");
        System.err.println("  java -jar anvil-tf.jar source-metrics <input.atf-or-dir>");
        System.err.println("  java -jar anvil-tf.jar source-gates <input.atf-or-dir>");
        System.err.println("  java -jar anvil-tf.jar validate <world-dir>");
        System.err.println("  java -jar anvil-tf.jar compare <source-world-dir> <regenerated-world-dir>");
        System.err.println("  java -jar anvil-tf.jar render top <input.atf> <y> [--dim id] [--out file.png] [--scale n] [--max n]");
        System.err.println("  java -jar anvil-tf.jar render map <input.atf> [--biome] [--dim id] [--out file.png] [--scale n] [--max n]");
        System.err.println("  java -jar anvil-tf.jar render slice <input.atf> x|z <coord> [--dim id] [--out file.png] [--scale n] [--max n]");
    }
}
