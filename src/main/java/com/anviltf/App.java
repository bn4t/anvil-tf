package com.anviltf;

import com.anviltf.anvil.AnvilWorldWriter;
import com.anviltf.anvil.AtfWorldImporter;
import com.anviltf.anvil.ReferenceWorldRoundTripComparator;
import com.anviltf.anvil.ReferenceWorldValidator;
import com.anviltf.dsl.DslParser;
import com.anviltf.render.RenderGrid;
import com.anviltf.render.WorldImageWriter;
import com.anviltf.render.WorldRenderer;

import java.nio.file.Path;

public final class App {
    private App() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "plan".equals(args[0])) {
            WorldSpec world = new DslParser().parse(Path.of(args[1]));
            System.out.println(new WorldPlanner().render(world));
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

    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            usage();
            System.exit(2);
        }
        return args[index];
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
        System.err.println("  java -jar anvil-tf.jar import <world-dir> <output.atf-or-dir> [--chunk-limit n] [--layout flat|reviewable]");
        System.err.println("  java -jar anvil-tf.jar <input.atf> <output-world-dir> [--validate]");
        System.err.println("  java -jar anvil-tf.jar diagnose <input.atf>");
        System.err.println("  java -jar anvil-tf.jar validate <world-dir>");
        System.err.println("  java -jar anvil-tf.jar compare <source-world-dir> <regenerated-world-dir>");
        System.err.println("  java -jar anvil-tf.jar render top <input.atf> <y> [--dim id] [--out file.png] [--scale n] [--max n]");
        System.err.println("  java -jar anvil-tf.jar render map <input.atf> [--biome] [--dim id] [--out file.png] [--scale n] [--max n]");
        System.err.println("  java -jar anvil-tf.jar render slice <input.atf> x|z <coord> [--dim id] [--out file.png] [--scale n] [--max n]");
    }
}
