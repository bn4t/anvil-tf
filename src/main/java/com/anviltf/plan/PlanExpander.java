package com.anviltf.plan;

import com.anviltf.WorldDiagnostics;
import com.anviltf.dsl.DslParser;
import com.anviltf.render.RenderGrid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class PlanExpander {
    public ExpansionReport expand(Path input, Path outputDir) throws IOException {
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        Path targetRoot = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(targetRoot);

        int files = 0;
        int planFiles = 0;
        int generatedCommands = 0;
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> sources = paths
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
            for (Path source : sources) {
                Path relative = sourceRoot.relativize(source);
                Path target = targetRoot.resolve(relative);
                Files.createDirectories(target.getParent());
                files++;
                if (source.getFileName().toString().endsWith(".atf")) {
                    TransformResult transformed = transform(Files.readAllLines(source));
                    if (transformed.expandedPlan()) {
                        planFiles++;
                        generatedCommands += transformed.generatedCommands();
                    }
                    Files.write(target, transformed.lines());
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return new ExpansionReport(files, planFiles, generatedCommands, targetRoot);
    }

    public List<PlanWarning> diagnose(Path input) throws IOException {
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        List<PlanWarning> warnings = new ArrayList<>();
        Map<String, int[]> componentBounds = new HashMap<>();
        List<Plan> plans = new ArrayList<>();
        List<PlanPlacement> placements = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> sources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList();
            for (Path source : sources) {
                List<String> lines = Files.readAllLines(source);
                Path relative = sourceRoot.relativize(source);
                collectComponentBounds(lines, componentBounds);
                collectPlans(lines, plans);
                collectFreePlacements(relative, lines, placements);
            }
            for (Path source : sources) {
                Path relative = sourceRoot.relativize(source);
                diagnoseFile(relative, Files.readAllLines(source), warnings);
            }
        }
        diagnosePlacementOverlaps(plans, placements, componentBounds, warnings);
        warnings.sort(Comparator
                .comparing(PlanWarning::file)
                .thenComparing(PlanWarning::code)
                .thenComparingInt(PlanWarning::z)
                .thenComparingInt(PlanWarning::x));
        return warnings;
    }

    public RenderGrid overlay(Path input) throws IOException {
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        List<Plan> plans = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> sources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList();
            for (Path source : sources) {
                collectPlans(Files.readAllLines(source), plans);
            }
        }
        if (plans.isEmpty()) {
            return RenderGrid.empty("plan overlay");
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        List<OverlayCell> overlayCells = new ArrayList<>();
        for (Plan plan : plans) {
            minX = Math.min(minX, plan.overlayOriginX + plan.area.minX);
            minZ = Math.min(minZ, plan.overlayOriginZ + plan.area.minZ);
            maxX = Math.max(maxX, plan.overlayOriginX + plan.area.maxX);
            maxZ = Math.max(maxZ, plan.overlayOriginZ + plan.area.maxZ);
            overlayCells.addAll(plan.overlayCells());
        }

        int cols = maxX - minX + 1;
        int rows = maxZ - minZ + 1;
        String[][] cells = new String[rows][cols];
        int[][] priorities = new int[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cells[row][col] = "minecraft:air";
            }
        }
        for (OverlayCell cell : overlayCells) {
            if (cell.x < minX || cell.x > maxX || cell.z < minZ || cell.z > maxZ) {
                continue;
            }
            int row = cell.z - minZ;
            int col = cell.x - minX;
            if (cell.priority >= priorities[row][col]) {
                priorities[row][col] = cell.priority;
                cells[row][col] = cell.name;
            }
        }
        return new RenderGrid(cells, minZ, maxZ, minX, maxX, "z", "x", "plan overlay");
    }

    public List<PlanOutline> outline(Path input) throws IOException {
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        List<PlanOutline> outlines = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> sources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList();
            for (Path source : sources) {
                collectOutlines(sourceRoot.relativize(source), Files.readAllLines(source), outlines);
            }
        }
        return outlines;
    }

    public List<PlanIndexEntry> index(Path input) throws IOException {
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        List<PlanIndexEntry> entries = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> sources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList();
            for (Path source : sources) {
                collectIndex(sourceRoot.relativize(source), Files.readAllLines(source), entries);
            }
        }
        entries.sort(Comparator
                .comparing(PlanIndexEntry::file)
                .thenComparingInt(PlanIndexEntry::line));
        return entries;
    }

    public PlanMetrics metrics(Path input) throws IOException {
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        PlanMetricsBuilder metrics = new PlanMetricsBuilder();
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> sources = paths
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
            for (Path source : sources) {
                Path relative = sourceRoot.relativize(source);
                metrics.sourceFiles++;
                if (!source.getFileName().toString().endsWith(".atf")) {
                    continue;
                }

                List<String> lines = Files.readAllLines(source);
                metrics.atfFiles++;
                metrics.sourceLines += lines.size();
                metrics.nonBlankSourceLines += nonBlankLines(lines);
                if (isComponentFile(relative)) {
                    metrics.componentFiles++;
                    metrics.componentLines += lines.size();
                    metrics.nonBlankComponentLines += nonBlankLines(lines);
                }

                TransformResult transformed = transform(lines);
                if (transformed.expandedPlan()) {
                    metrics.generatedAtfLines += transformed.generatedCommands();
                }
                collectMetricPlans(lines, metrics);
            }
        }

        RenderGrid overlay = overlay(input);
        Map<String, Integer> overlayCells = new TreeMap<>();
        for (String[] row : overlay.cells()) {
            for (String cell : row) {
                if (!"minecraft:air".equals(cell)) {
                    overlayCells.merge(cell, 1, Integer::sum);
                }
            }
        }

        return metrics.toMetrics(overlayCells);
    }

    public PlanGateReport gates(Path input) throws IOException {
        PlanMetrics metrics = metrics(input);
        List<PlanWarning> planWarnings = diagnose(input);
        Path sourceRoot = input.toAbsolutePath().normalize().getParent();
        int maxGroupNonBlankLines = maxGroupNonBlankLines(sourceRoot);

        Path tempRoot = Files.createTempDirectory("anvil-tf-plan-gates-");
        boolean deterministic;
        int coreWarnings;
        try {
            Path expandedA = tempRoot.resolve("expanded-a");
            Path expandedB = tempRoot.resolve("expanded-b");
            expand(input, expandedA);
            expand(input, expandedB);
            deterministic = directoriesEqual(expandedA, expandedB);
            coreWarnings = new WorldDiagnostics()
                    .diagnose(new DslParser().parse(expandedA.resolve(input.getFileName())))
                    .size();
        } finally {
            deleteTree(tempRoot);
        }

        Map<String, Integer> counts = metrics.commandCounts();
        int routes = counts.getOrDefault("routes", 0);
        int crossings = counts.getOrDefault("crossings", 0);
        int reservations = counts.getOrDefault("reservations", 0);
        int landmarks = counts.getOrDefault("landmarks", 0);
        int repeatedDetail = counts.getOrDefault("scatter_requested", 0)
                + counts.getOrDefault("cluster_members", 0);
        int loot = counts.getOrDefault("loot_requested", 0);
        double expandedRatio = metrics.planLines() == 0
                ? 0.0
                : metrics.generatedAtfLines() / (double) metrics.planLines();

        List<PlanGate> gates = new ArrayList<>();
        gates.add(gate("minimum_authored_area",
                metrics.planAreasAtLeast512() >= 1 && metrics.planAreasAtLeast384() >= 3,
                metrics.planAreasAtLeast512() + "x >=512, " + metrics.planAreasAtLeast384() + "x >=384",
                ">=1 plan area at 512x512 and >=3 plan areas at 384x384 or larger",
                "Uses plan block area as the measurable benchmark proxy."));
        gates.add(gate("major_zones",
                reservations >= 6,
                Integer.toString(reservations),
                ">=6 reservations",
                "Reservations are the current measurable proxy for named major zones."));
        gates.add(gate("route_network",
                routes >= 5 && crossings >= 4,
                routes + " route(s), " + crossings + " crossing(s)",
                ">=5 routes and >=4 crossings",
                ""));
        gates.add(gate("landmarks",
                landmarks >= 6,
                Integer.toString(landmarks),
                ">=6 reserved landmarks",
                ""));
        gates.add(gate("repeated_detail",
                repeatedDetail >= 120,
                Integer.toString(repeatedDetail),
                ">=120 scatter requests plus cluster members",
                ""));
        gates.add(gate("loot",
                loot >= 35,
                Integer.toString(loot),
                ">=35 loot anchors",
                ""));
        gates.add(gate("source_shape",
                maxGroupNonBlankLines <= 320,
                Integer.toString(maxGroupNonBlankLines),
                "<=320 nonblank lines in the largest authored group file",
                ""));
        gates.add(gate("expanded_ratio",
                expandedRatio >= 15.0,
                String.format(java.util.Locale.ROOT, "%.2f", expandedRatio),
                ">=15.00 generated ATF lines per plan line",
                ""));
        gates.add(new PlanGate("edit_locality", "manual", "not measured",
                "<=5 plan lines and >=100 expanded ATF lines for a semantic move",
                "Run plan-edit-trial with base and edited sources."));
        gates.add(gate("determinism",
                deterministic,
                deterministic ? "directory-identical" : "changed",
                "two expansions from the same source are directory-identical",
                ""));
        gates.add(gate("plan_diagnostics",
                planWarnings.isEmpty(),
                Integer.toString(planWarnings.size()),
                "0 plan warnings",
                ""));
        gates.add(gate("core_diagnostics",
                coreWarnings == 0,
                Integer.toString(coreWarnings),
                "0 expanded ATF warnings",
                ""));
        gates.add(new PlanGate("reference_validation", "manual", "not measured",
                "generated Anvil world validates with the reference library",
                "Run normal generation with --validate."));
        gates.add(new PlanGate("render_review", "manual", "not measured",
                "map plus at least one top/slice render inspected and recorded",
                "Run render map/top/slice or plan-render."));
        gates.add(new PlanGate("import_round_trip", "manual", "not measured",
                "generated world imports to ordinary ATF and exact-compares",
                "Run import --layout reviewable, regenerate, then compare."));
        gates.add(gate("theme_purity",
                themePurity(sourceRoot),
                "generic plan directives",
                "0 plan directives named after map themes or structures",
                ""));

        long passed = gates.stream().filter(gate -> "pass".equals(gate.status())).count();
        long failed = gates.stream().filter(gate -> "fail".equals(gate.status())).count();
        long manual = gates.stream().filter(gate -> "manual".equals(gate.status())).count();
        return new PlanGateReport(metrics, gates, (int) passed, (int) failed, (int) manual);
    }

    public PlanEditTrialReport editTrial(Path baseInput, Path editedInput) throws IOException {
        return editTrial(baseInput, editedInput, 5, 100);
    }

    public PlanEditTrialReport editTrial(Path baseInput, Path editedInput,
                                         int maxSourceChangedLines,
                                         int minExpandedChangedLines) throws IOException {
        Path baseRoot = baseInput.toAbsolutePath().normalize().getParent();
        Path editedRoot = editedInput.toAbsolutePath().normalize().getParent();
        int sourceChangedLines = directoryLineChanges(baseRoot, editedRoot);
        int planWarnings = diagnose(editedInput).size();

        Path tempRoot = Files.createTempDirectory("anvil-tf-plan-edit-trial-");
        int expandedChangedLines;
        int coreWarnings;
        try {
            Path baseExpanded = tempRoot.resolve("base-expanded");
            Path editedExpanded = tempRoot.resolve("edited-expanded");
            expand(baseInput, baseExpanded);
            expand(editedInput, editedExpanded);
            expandedChangedLines = directoryLineChanges(baseExpanded, editedExpanded);
            coreWarnings = new WorldDiagnostics()
                    .diagnose(new DslParser().parse(editedExpanded.resolve(editedInput.getFileName())))
                    .size();
        } finally {
            deleteTree(tempRoot);
        }

        boolean passed = sourceChangedLines <= maxSourceChangedLines
                && expandedChangedLines >= minExpandedChangedLines
                && planWarnings == 0
                && coreWarnings == 0;
        return new PlanEditTrialReport(sourceChangedLines, expandedChangedLines,
                planWarnings, coreWarnings, passed);
    }

    private TransformResult transform(List<String> source) {
        List<String> output = new ArrayList<>();
        boolean expanded = false;
        int generatedCommands = 0;
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            if (!line.trim().equals("plan {")) {
                output.add(line);
                continue;
            }

            List<String> body = new ArrayList<>();
            int depth = braceDelta(line);
            while (++i < source.size() && depth > 0) {
                String bodyLine = source.get(i);
                depth += braceDelta(bodyLine);
                if (depth > 0) {
                    body.add(bodyLine);
                }
            }
            i--;
            Plan plan = parse(body);
            List<String> expandedLines = plan.expand();
            output.add("  # expanded from experimental plan block");
            output.addAll(expandedLines);
            expanded = true;
            generatedCommands += expandedLines.size();
        }
        return new TransformResult(output, expanded, generatedCommands);
    }

    private void diagnoseFile(Path file, List<String> source, List<PlanWarning> warnings) {
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            if (!line.trim().equals("plan {")) {
                continue;
            }
            List<String> body = new ArrayList<>();
            int depth = braceDelta(line);
            while (++i < source.size() && depth > 0) {
                String bodyLine = source.get(i);
                depth += braceDelta(bodyLine);
                if (depth > 0) {
                    body.add(bodyLine);
                }
            }
            i--;
            parse(body).diagnose(file, warnings);
        }
    }

    private void collectPlans(List<String> source, List<Plan> plans) {
        int originX = 0;
        int originZ = 0;
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            String stripped = stripComment(line).trim();
            if (stripped.startsWith("group ") && stripped.contains("{")) {
                originX = 0;
                originZ = 0;
            } else if (stripped.startsWith("origin ")) {
                List<String> tokens = tokenize(stripped);
                if (tokens.size() >= 4) {
                    originX = intAt(tokens, 1);
                    originZ = intAt(tokens, 3);
                }
            }
            if (!line.trim().equals("plan {")) {
                continue;
            }
            List<String> body = new ArrayList<>();
            int depth = braceDelta(line);
            while (++i < source.size() && depth > 0) {
                String bodyLine = source.get(i);
                depth += braceDelta(bodyLine);
                if (depth > 0) {
                    body.add(bodyLine);
                }
            }
            i--;
            Plan plan = parse(body);
            plan.overlayOriginX = originX;
            plan.overlayOriginZ = originZ;
            plans.add(plan);
        }
    }

    private void collectOutlines(Path relative, List<String> source, List<PlanOutline> outlines) {
        String groupName = "";
        int originX = 0;
        int originY = 0;
        int originZ = 0;
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            String stripped = stripComment(line).trim();
            if (stripped.startsWith("group ") && stripped.contains("{")) {
                List<String> tokens = tokenize(stripped);
                groupName = tokens.size() >= 2 ? tokens.get(1) : "";
                originX = 0;
                originY = 0;
                originZ = 0;
            } else if (stripped.startsWith("origin ")) {
                List<String> tokens = tokenize(stripped);
                if (tokens.size() >= 4) {
                    originX = intAt(tokens, 1);
                    originY = intAt(tokens, 2);
                    originZ = intAt(tokens, 3);
                }
            }
            if (!line.trim().equals("plan {")) {
                continue;
            }
            List<String> body = new ArrayList<>();
            int depth = braceDelta(line);
            while (++i < source.size() && depth > 0) {
                String bodyLine = source.get(i);
                depth += braceDelta(bodyLine);
                if (depth > 0) {
                    body.add(bodyLine);
                }
            }
            i--;
            Plan plan = parse(body);
            outlines.add(toOutline(relative.toString(), groupName, originX, originY, originZ, plan));
        }
    }

    private void collectIndex(Path relative, List<String> source, List<PlanIndexEntry> entries) {
        String groupName = "";
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            String stripped = stripComment(line).trim();
            if (stripped.startsWith("group ") && stripped.contains("{")) {
                List<String> tokens = tokenize(stripped);
                groupName = tokens.size() >= 2 ? tokens.get(1) : "";
            }
            if (!line.trim().equals("plan {")) {
                continue;
            }
            int depth = braceDelta(line);
            int planDepth = 0;
            while (++i < source.size() && depth > 0) {
                String bodyLine = source.get(i);
                depth += braceDelta(bodyLine);
                if (depth <= 0) {
                    break;
                }
                String body = stripComment(bodyLine).trim();
                if (!body.isEmpty() && !"}".equals(body) && planDepth == 0) {
                    List<String> tokens = tokenize(body);
                    if (!tokens.isEmpty() && isIndexedPlanCommand(tokens.get(0))) {
                        entries.add(toIndexEntry(relative.toString(), i + 1, groupName, tokens, body));
                    }
                }
                planDepth += braceDelta(bodyLine);
            }
            i--;
        }
    }

    private static PlanIndexEntry toIndexEntry(String file, int line, String groupName,
                                               List<String> tokens, String summary) {
        String kind = tokens.get(0);
        String name = switch (kind) {
            case "route", "reserve", "landmark", "cluster" ->
                    tokens.size() >= 2 ? tokens.get(1) : "";
            case "scatter_in" ->
                    tokens.size() >= 2 ? "scatter " + tokens.get(1) : "scatter";
            case "loot" -> {
                String mask = tokens.size() >= 2 ? tokens.get(1) : "";
                int tier = tokens.indexOf("tier");
                if (tier >= 0 && tier + 1 < tokens.size()) {
                    yield "loot " + mask + " tier " + tokens.get(tier + 1);
                }
                yield "loot " + mask;
            }
            default -> "";
        };
        return new PlanIndexEntry(file, line, groupName, kind, name, summary);
    }

    private static boolean isIndexedPlanCommand(String command) {
        return "route".equals(command)
                || "reserve".equals(command)
                || "landmark".equals(command)
                || "cluster".equals(command)
                || "scatter_in".equals(command)
                || "loot".equals(command);
    }

    private PlanOutline toOutline(String file, String groupName, int originX, int originY, int originZ, Plan plan) {
        List<RouteOutline> routes = new ArrayList<>();
        for (Route route : plan.routes) {
            Point first = route.points.isEmpty() ? null : route.points.get(0);
            Point last = route.points.isEmpty() ? null : route.points.get(route.points.size() - 1);
            routes.add(new RouteOutline(route.name, route.width, route.points.size(), route.crossings.size(),
                    pointText(first), pointText(last)));
        }

        List<ReservationOutline> reservations = new ArrayList<>();
        for (Reservation reservation : plan.reservations) {
            Point anchor = reservation.anchor();
            reservations.add(new ReservationOutline(reservation.name, shapeText(reservation.shape),
                    anchor.x, anchor.z, reservation.clearTo, reservation.requiredRouteDistance));
        }

        List<LandmarkOutline> landmarks = new ArrayList<>();
        for (LandmarkAction landmark : plan.landmarks) {
            landmarks.add(new LandmarkOutline(landmark.name, landmark.component, landmark.x, landmark.z,
                    landmark.reservation, landmark.radius, landmark.height, landmark.rotation));
        }

        List<ClusterOutline> clusters = new ArrayList<>();
        for (ClusterAction cluster : plan.clusters) {
            clusters.add(new ClusterOutline(cluster.name, cluster.reservation, cluster.members.size(),
                    cluster.spacing));
        }

        int scatterActions = 0;
        int scatterRequested = 0;
        int lootActions = 0;
        int lootRequested = 0;
        for (Action action : plan.actions) {
            if (action instanceof ScatterAction scatter) {
                scatterActions++;
                scatterRequested += scatter.count;
            } else if (action instanceof LootAction loot) {
                lootActions++;
                lootRequested += loot.count;
            }
        }

        return new PlanOutline(file, groupName, originX, originY, originZ,
                plan.area.minX, plan.area.minZ, plan.area.maxX, plan.area.maxZ,
                plan.styles.size(), plan.masks.size(), plan.fields.size(),
                routes, reservations, landmarks, clusters,
                scatterActions, scatterRequested, lootActions, lootRequested);
    }

    private static String pointText(Point point) {
        return point == null ? "none" : point.x + "," + point.z;
    }

    private static String shapeText(Mask shape) {
        if (shape instanceof CircleMask circle) {
            return "circle " + circle.centerX + "," + circle.centerZ + " r=" + circle.radius;
        }
        if (shape instanceof RectMask rect) {
            return "rect " + rect.x1 + "," + rect.z1 + " to " + rect.x2 + "," + rect.z2;
        }
        return "mask";
    }

    private void collectMetricPlans(List<String> source, PlanMetricsBuilder metrics) {
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            if (!line.trim().equals("plan {")) {
                continue;
            }
            List<String> body = new ArrayList<>();
            int depth = braceDelta(line);
            while (++i < source.size() && depth > 0) {
                String bodyLine = source.get(i);
                depth += braceDelta(bodyLine);
                if (depth > 0) {
                    body.add(bodyLine);
                }
            }
            i--;
            metrics.addPlan(parse(body), body);
        }
    }

    private static PlanGate gate(String name, boolean passed, String value, String threshold, String note) {
        return new PlanGate(name, passed ? "pass" : "fail", value, threshold, note);
    }

    private static int maxGroupNonBlankLines(Path sourceRoot) throws IOException {
        int max = 0;
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList()) {
                Path relative = sourceRoot.relativize(source);
                if (relative.getNameCount() > 1 && "groups".equals(relative.getName(0).toString())) {
                    max = Math.max(max, nonBlankLines(Files.readAllLines(source)));
                }
            }
        }
        return max;
    }

    private static boolean themePurity(Path sourceRoot) throws IOException {
        Set<String> banned = Set.of("castle", "town", "district", "road", "harbor", "temple",
                "dojo", "ship", "interior", "room", "region", "stamp", "terrace", "surface",
                "material", "scatter_along");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .sorted()
                    .toList()) {
                if (!themePure(Files.readAllLines(source), banned)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean themePure(List<String> source, Set<String> banned) {
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            if (!line.trim().equals("plan {")) {
                continue;
            }
            int depth = braceDelta(line);
            int planDepth = 0;
            while (++i < source.size() && depth > 0) {
                String bodyLine = source.get(i);
                depth += braceDelta(bodyLine);
                if (depth <= 0) {
                    break;
                }
                String stripped = stripComment(bodyLine).trim();
                if (!stripped.isEmpty() && !"}".equals(stripped) && planDepth == 0) {
                    List<String> tokens = tokenize(stripped);
                    if (!tokens.isEmpty() && banned.contains(tokens.get(0))) {
                        return false;
                    }
                }
                planDepth += braceDelta(bodyLine);
            }
            i--;
        }
        return true;
    }

    private static boolean directoriesEqual(Path left, Path right) throws IOException {
        List<Path> leftFiles = relativeFiles(left);
        List<Path> rightFiles = relativeFiles(right);
        if (!leftFiles.equals(rightFiles)) {
            return false;
        }
        for (Path relative : leftFiles) {
            if (Files.mismatch(left.resolve(relative), right.resolve(relative)) != -1) {
                return false;
            }
        }
        return true;
    }

    private static int directoryLineChanges(Path left, Path right) throws IOException {
        Set<Path> files = new HashSet<>();
        files.addAll(relativeAtfFiles(left));
        files.addAll(relativeAtfFiles(right));

        int changes = 0;
        for (Path relative : files.stream().sorted().toList()) {
            Path leftFile = left.resolve(relative);
            Path rightFile = right.resolve(relative);
            if (Files.exists(leftFile) && Files.exists(rightFile)) {
                changes += alignedLineChanges(Files.readAllLines(leftFile), Files.readAllLines(rightFile));
            } else if (Files.exists(leftFile)) {
                changes += Files.readAllLines(leftFile).size();
            } else {
                changes += Files.readAllLines(rightFile).size();
            }
        }
        return changes;
    }

    private static int alignedLineChanges(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            int prefix = 0;
            int min = Math.min(left.size(), right.size());
            while (prefix < min && java.util.Objects.equals(left.get(prefix), right.get(prefix))) {
                prefix++;
            }

            int suffix = 0;
            while (suffix < left.size() - prefix
                    && suffix < right.size() - prefix
                    && java.util.Objects.equals(left.get(left.size() - 1 - suffix),
                    right.get(right.size() - 1 - suffix))) {
                suffix++;
            }

            int leftMiddle = left.size() - prefix - suffix;
            int rightMiddle = right.size() - prefix - suffix;
            return Math.max(leftMiddle, rightMiddle);
        }

        int changes = 0;
        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            String leftLine = i < left.size() ? left.get(i) : null;
            String rightLine = i < right.size() ? right.get(i) : null;
            if (!java.util.Objects.equals(leftLine, rightLine)) {
                changes++;
            }
        }
        return changes;
    }

    private static List<Path> relativeFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> relativeAtfFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".atf"))
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
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

    private static boolean isComponentFile(Path relative) {
        return relative.getNameCount() > 1
                && "components".equals(relative.getName(0).toString())
                && relative.getFileName().toString().endsWith(".atf");
    }

    private static int nonBlankLines(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            if (!stripComment(line).trim().isEmpty()) {
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

    private static int blockStartDepth(String line) {
        int depth = braceDelta(line);
        return depth == 0 && line.contains("{") ? 1 : depth;
    }

    private Plan parse(List<String> lines) {
        Plan plan = new Plan();
        for (int i = 0; i < lines.size(); i++) {
            String line = stripComment(lines.get(i)).trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> tokens = tokenize(line);
            String command = tokens.get(0);
            switch (command) {
                case "area" -> plan.area = new Area(intAt(tokens, 1), intAt(tokens, 2),
                        intAt(tokens, 3), intAt(tokens, 4));
                case "style" -> {
                    List<String> block = new ArrayList<>();
                    int depth = blockStartDepth(line);
                    while (++i < lines.size() && depth > 0) {
                        String styleLine = stripComment(lines.get(i)).trim();
                        depth += braceDelta(styleLine);
                        if (depth > 0 && !styleLine.isEmpty()) {
                            block.add(styleLine);
                        }
                    }
                    i--;
                    parseStyle(plan, tokens, block);
                }
                case "use_style" -> plan.useStyle(tokens.get(1));
                case "mask" -> parseMask(plan, tokens);
                case "field" -> parseField(plan, tokens);
                case "terrain" -> plan.actions.add(parseTerrain(plan, tokens));
                case "water", "lava" -> {
                    FillAction action = parseFill(plan, tokens, false);
                    plan.actions.add(action);
                    if (action.block().contains("water") || action.block().contains("lava")) {
                        plan.hazards.add(new Hazard(action.mask(), action.outside(), action.block()));
                    }
                }
                case "clear" -> plan.actions.add(parseFill(plan, tokens, true));
                case "fill_biome" -> plan.actions.add(parseBiome(tokens));
                case "reserve" -> {
                    Reservation reservation = parseReservation(plan, tokens);
                    plan.reservations.add(reservation);
                    plan.actions.add(reservation);
                }
                case "landmark" -> {
                    LandmarkAction landmark = parseLandmark(plan, tokens);
                    plan.landmarks.add(landmark);
                    plan.actions.add(landmark);
                }
                case "cluster" -> {
                    List<String> block = new ArrayList<>();
                    int depth = blockStartDepth(line);
                    while (++i < lines.size() && depth > 0) {
                        String clusterLine = stripComment(lines.get(i)).trim();
                        depth += braceDelta(clusterLine);
                        if (depth > 0 && !clusterLine.isEmpty()) {
                            block.add(clusterLine);
                        }
                    }
                    i--;
                    ClusterAction cluster = parseCluster(plan, tokens, block);
                    plan.clusters.add(cluster);
                    plan.actions.add(cluster);
                }
                case "route" -> {
                    List<String> block = new ArrayList<>();
                    int depth = blockStartDepth(line);
                    while (++i < lines.size() && depth > 0) {
                        String routeLine = stripComment(lines.get(i)).trim();
                        depth += braceDelta(routeLine);
                        if (depth > 0 && !routeLine.isEmpty()) {
                            block.add(routeLine);
                        }
                    }
                    i--;
                    Route route = parseRoute(plan, tokens, block);
                    plan.routes.add(route);
                    plan.actions.add(route);
                }
                case "scatter_in" -> {
                    List<String> block = new ArrayList<>();
                    int depth = blockStartDepth(line);
                    while (++i < lines.size() && depth > 0) {
                        String scatterLine = stripComment(lines.get(i)).trim();
                        depth += braceDelta(scatterLine);
                        if (depth > 0 && !scatterLine.isEmpty()) {
                            block.add(scatterLine);
                        }
                    }
                    i--;
                    plan.actions.add(parseScatter(plan, tokens, block));
                }
                case "loot" -> plan.actions.add(parseLoot(plan, tokens));
                default -> throw new IllegalArgumentException("Unknown plan command: " + command
                        + " in line: " + line);
            }
        }
        if (plan.area == null) {
            throw new IllegalArgumentException("plan requires an area directive");
        }
        return plan;
    }

    private static void parseStyle(Plan plan, List<String> tokens, List<String> block) {
        String name = tokens.get(1);
        Map<String, String> values = new HashMap<>();
        for (String line : block) {
            List<String> row = tokenize(line);
            if (row.size() != 2) {
                throw new IllegalArgumentException("style entries must be key value pairs: " + line);
            }
            values.put(row.get(0), row.get(1));
        }
        plan.styles.put(name, values);
    }

    private static void parseMask(Plan plan, List<String> tokens) {
        String name = tokens.get(1);
        expect(tokens, 2, "=");
        String kind = tokens.get(3);
        Mask mask;
        switch (kind) {
            case "radial" -> mask = new RadialMask(intAt(tokens, 4), intAt(tokens, 5),
                    intAfter(tokens, "radius"), doubleAfter(tokens, "noise"), intAfter(tokens, "seed"));
            case "circle" -> mask = new CircleMask(intAt(tokens, 4), intAt(tokens, 5), intAfter(tokens, "radius"));
            case "rect" -> mask = new RectMask(intAt(tokens, 4), intAt(tokens, 5), intAt(tokens, 6), intAt(tokens, 7));
            case "outside" -> mask = new NotMask(tokens.get(4));
            default -> {
                if (tokens.size() == 6 && "-".equals(tokens.get(4))) {
                    mask = new DifferenceMask(tokens.get(3), tokens.get(5));
                } else if (tokens.size() == 6 && "+".equals(tokens.get(4))) {
                    mask = new UnionMask(tokens.get(3), tokens.get(5));
                } else if (tokens.size() == 6 && "&".equals(tokens.get(4))) {
                    mask = new IntersectionMask(tokens.get(3), tokens.get(5));
                } else {
                    throw new IllegalArgumentException("Unknown mask directive: " + String.join(" ", tokens));
                }
            }
        }
        plan.masks.put(name, mask);
    }

    private static void parseField(Plan plan, List<String> tokens) {
        String name = tokens.get(1);
        expect(tokens, 2, "=");
        String kind = tokens.get(3);
        Field field;
        switch (kind) {
            case "terrain" -> field = new TerrainField(tokens.get(4), intAfter(tokens, "base"),
                    intAfter(tokens, "amplitude"), intAfter(tokens, "scale"),
                    intAfter(tokens, "octaves"), intAfter(tokens, "seed"));
            case "ridge" -> {
                int widthIndex = tokens.indexOf("width");
                if (widthIndex < 0) {
                    throw new IllegalArgumentException("ridge field requires width");
                }
                List<Point> points = points(tokens.subList(5, widthIndex));
                field = new RidgeField(points, intAfter(tokens, "width"), intAfter(tokens, "height"));
            }
            case "max" -> field = new MaxField(tokens.get(4), tokens.get(5));
            default -> throw new IllegalArgumentException("Unknown field directive: " + String.join(" ", tokens));
        }
        plan.fields.put(name, field);
    }

    private static TerrainAction parseTerrain(Plan plan, List<String> tokens) {
        return new TerrainAction(tokens.get(1), tokens.get(3), intAfter(tokens, "from"),
                plan.resolve(tokenAfter(tokens, "top")), plan.resolve(tokenAfter(tokens, "under")));
    }

    private static FillAction parseFill(Plan plan, List<String> tokens, boolean clear) {
        String mask = tokens.get(1);
        boolean outside = "outside".equals(mask);
        if (outside) {
            mask = tokens.get(2);
        }
        int from = intAfter(tokens, "from");
        int to = intAfter(tokens, "to");
        String block = clear ? "minecraft:air" : plan.resolve(tokens.get(tokens.size() - 1));
        return new FillAction(mask, outside, from, to, block);
    }

    private static BiomeAction parseBiome(List<String> tokens) {
        return new BiomeAction(tokens.get(1), intAt(tokens, 2), intAt(tokens, 3), tokens.get(4));
    }

    private static Reservation parseReservation(Plan plan, List<String> tokens) {
        String name = tokens.get(1);
        String kind = tokens.get(2);
        Mask shape;
        if ("circle".equals(kind)) {
            shape = new CircleMask(intAt(tokens, 3), intAt(tokens, 4), intAfter(tokens, "radius"));
        } else if ("rect".equals(kind)) {
            shape = new RectMask(intAt(tokens, 3), intAt(tokens, 4), intAt(tokens, 5), intAt(tokens, 6));
        } else {
            throw new IllegalArgumentException("reserve shape must be circle or rect");
        }
        String fieldName = tokenAfter(tokens, "conform");
        String floor = plan.resolve(tokenAfter(tokens, "floor"));
        String floorPalette = optionalTokenAfter(tokens, "floor_palette", null);
        if (floorPalette != null) {
            floorPalette = plan.resolve(floorPalette);
        }
        int floorSeed = optionalIntAfter(tokens, "seed", 0);
        String edge = optionalTokenAfter(tokens, "edge", null);
        if (edge != null) {
            edge = plan.resolve(edge);
        }
        String edgePalette = optionalTokenAfter(tokens, "edge_palette", null);
        if (edgePalette != null) {
            edgePalette = plan.resolve(edgePalette);
        }
        int edgeWidth = optionalIntAfter(tokens, "edge_width", edge == null && edgePalette == null ? 0 : 1);
        int edgeSeed = optionalIntAfter(tokens, "edge_seed", floorSeed + 97);
        int clearTo = intAfter(tokens, "clear_to");
        int requiredRouteDistance = optionalIntAfter(tokens, "require_route", 0);
        return new Reservation(name, shape, fieldName, floor, floorPalette, floorSeed, edge, edgePalette,
                edgeWidth, edgeSeed, clearTo, requiredRouteDistance);
    }

    private static LandmarkAction parseLandmark(Plan plan, List<String> tokens) {
        String name = tokens.get(1);
        String component = plan.resolve(tokenAfter(tokens, "component"));
        int at = tokens.indexOf("at");
        if (at < 0 || at + 2 >= tokens.size()) {
            throw new IllegalArgumentException("landmark requires at x z");
        }
        int x = intAt(tokens, at + 1);
        int z = intAt(tokens, at + 2);
        String field = tokenAfter(tokens, "conform");
        String reservation = tokenAfter(tokens, "reserve");
        int radius = intAfter(tokens, "radius");
        int height = intAfter(tokens, "height");
        int rotation = optionalIntAfter(tokens, "rotate", 0);
        return new LandmarkAction(name, component, x, z, field, reservation, radius, height, rotation);
    }

    private static ClusterAction parseCluster(Plan plan, List<String> tokens, List<String> block) {
        String name = tokens.get(1);
        int at = tokens.indexOf("at");
        if (at < 0 || at + 2 >= tokens.size()) {
            throw new IllegalArgumentException("cluster requires at x z");
        }
        boolean anchorOnReservation = "reserve".equals(tokens.get(at + 1));
        String reservation = tokenAfter(tokens, "reserve");
        int x;
        int z;
        if (anchorOnReservation) {
            x = 0;
            z = 0;
        } else {
            x = intAt(tokens, at + 1);
            z = intAt(tokens, at + 2);
        }
        String field = tokenAfter(tokens, "conform");
        int spacing = optionalIntAfter(tokens, "spacing", 0);
        List<ClusterMember> members = new ArrayList<>();
        for (String line : block) {
            List<String> row = tokenize(line);
            if ("component".equals(row.get(0))) {
                int memberAt = row.indexOf("at");
                if (memberAt < 0 || memberAt + 2 >= row.size()) {
                    throw new IllegalArgumentException("cluster component requires at dx dz");
                }
                members.add(new ClusterMember(plan.resolve(row.get(1)), intAt(row, memberAt + 1),
                        intAt(row, memberAt + 2), intAfter(row, "radius"), intAfter(row, "height"),
                        optionalIntAfter(row, "rotate", 0)));
            } else if ("repeat".equals(row.get(0))) {
                parseClusterRepeat(plan, row, members);
            } else {
                throw new IllegalArgumentException("Unknown cluster command: " + line);
            }
        }
        return new ClusterAction(name, x, z, anchorOnReservation, field, reservation, spacing, members);
    }

    private static void parseClusterRepeat(Plan plan, List<String> row, List<ClusterMember> members) {
        if (row.size() < 4 || !"component".equals(row.get(1))) {
            throw new IllegalArgumentException("cluster repeat requires: repeat component name ...");
        }
        int from = row.indexOf("from");
        int to = row.indexOf("to");
        if (from < 0 || from + 2 >= row.size() || to < 0 || to + 2 >= row.size()) {
            throw new IllegalArgumentException("cluster repeat requires from dx dz to dx dz");
        }
        int count = intAfter(row, "count");
        if (count <= 0) {
            throw new IllegalArgumentException("cluster repeat count must be positive");
        }
        String component = plan.resolve(row.get(2));
        int x1 = intAt(row, from + 1);
        int z1 = intAt(row, from + 2);
        int x2 = intAt(row, to + 1);
        int z2 = intAt(row, to + 2);
        int radius = intAfter(row, "radius");
        int height = intAfter(row, "height");
        int rotation = optionalIntAfter(row, "rotate", 0);
        for (int i = 0; i < count; i++) {
            float t = count == 1 ? 0.0f : i / (float) (count - 1);
            int x = x1 + Math.round((x2 - x1) * t);
            int z = z1 + Math.round((z2 - z1) * t);
            members.add(new ClusterMember(component, x, z, radius, height, rotation));
        }
    }

    private static Route parseRoute(Plan plan, List<String> tokens, List<String> block) {
        String name = tokens.get(1);
        int width = intAfter(tokens, "width");
        String palette = plan.resolve(tokenAfter(tokens, "palette"));
        String field = tokenAfter(tokens, "conform");
        int seed = intAfter(tokens, "seed");
        List<Point> points = new ArrayList<>();
        List<Crossing> crossings = new ArrayList<>();
        boolean close = false;
        for (String line : block) {
            List<String> row = tokenize(line);
            if ("point".equals(row.get(0))) {
                points.add(new Point(intAt(row, 1), intAt(row, 2)));
            } else if ("crossing".equals(row.get(0))) {
                crossings.add(new Crossing(intAt(row, 1), intAt(row, 2), intAfter(row, "radius")));
            } else if ("close".equals(row.get(0))) {
                close = true;
            } else {
                throw new IllegalArgumentException("Unknown route command: " + line);
            }
        }
        if (close && points.size() > 1) {
            points.add(points.get(0));
        }
        return new Route(name, width, palette, field, seed,
                resolveOptional(plan, optionalTokenAfter(tokens, "centerline", null)),
                resolveOptional(plan, optionalTokenAfter(tokens, "edge", null)),
                points, crossings);
    }

    private static ScatterAction parseScatter(Plan plan, List<String> tokens, List<String> block) {
        String mask = tokens.get(1);
        int count = intAfter(tokens, "count");
        int seed = intAfter(tokens, "seed");
        int avoidRoutes = optionalIntAfter(tokens, "avoid_routes", 0);
        int avoidReservations = optionalIntAfter(tokens, "avoid_reservations", 0);
        String field = tokenAfter(tokens, "conform");
        List<ScatterChoice> choices = new ArrayList<>();
        for (String line : block) {
            List<String> row = tokenize(line);
            String kind = row.get(0);
            if ("component".equals(kind)) {
                choices.add(new ScatterChoice(kind, plan.resolve(row.get(1)), intAfter(row, "weight")));
            } else if ("set".equals(kind)) {
                choices.add(new ScatterChoice(kind, plan.resolve(row.get(1)), intAfter(row, "weight")));
            } else {
                throw new IllegalArgumentException("Unknown scatter command: " + line);
            }
        }
        return new ScatterAction(mask, count, seed, avoidRoutes, avoidReservations, field, choices);
    }

    private static LootAction parseLoot(Plan plan, List<String> tokens) {
        return new LootAction(tokens.get(1), intAfter(tokens, "tier"), intAfter(tokens, "count"),
                intAfter(tokens, "seed"), optionalIntAfter(tokens, "avoid_routes", 0),
                optionalIntAfter(tokens, "avoid_reservations", 0), tokenAfter(tokens, "conform"));
    }

    private static List<String> tokenize(String line) {
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

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '#') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static List<Point> points(List<String> tokens) {
        if (tokens.size() % 2 != 0) {
            throw new IllegalArgumentException("point list must contain x/z pairs");
        }
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += 2) {
            points.add(new Point(Integer.parseInt(tokens.get(i)), Integer.parseInt(tokens.get(i + 1))));
        }
        return points;
    }

    private static int intAt(List<String> tokens, int index) {
        return Integer.parseInt(tokens.get(index));
    }

    private static int intAfter(List<String> tokens, String key) {
        int index = tokens.indexOf(key);
        if (index < 0 || index + 1 >= tokens.size()) {
            throw new IllegalArgumentException("Missing required token: " + key);
        }
        return Integer.parseInt(tokens.get(index + 1));
    }

    private static int optionalIntAfter(List<String> tokens, String key, int fallback) {
        int index = tokens.indexOf(key);
        return index < 0 ? fallback : Integer.parseInt(tokens.get(index + 1));
    }

    private static double doubleAfter(List<String> tokens, String key) {
        int index = tokens.indexOf(key);
        if (index < 0 || index + 1 >= tokens.size()) {
            return 0.0;
        }
        return Double.parseDouble(tokens.get(index + 1));
    }

    private static String tokenAfter(List<String> tokens, String key) {
        int index = tokens.indexOf(key);
        if (index < 0 || index + 1 >= tokens.size()) {
            throw new IllegalArgumentException("Missing required token: " + key);
        }
        return tokens.get(index + 1);
    }

    private static String optionalTokenAfter(List<String> tokens, String key, String fallback) {
        int index = tokens.indexOf(key);
        return index < 0 ? fallback : tokens.get(index + 1);
    }

    private static String resolveOptional(Plan plan, String token) {
        return token == null ? null : plan.resolve(token);
    }

    private static void expect(List<String> tokens, int index, String expected) {
        if (!expected.equals(tokens.get(index))) {
            throw new IllegalArgumentException("Expected " + expected + " but got " + tokens.get(index));
        }
    }

    public record ExpansionReport(int copiedFiles, int planFiles, int generatedCommands, Path outputDir) {
    }

    public record PlanMetrics(
            int sourceFiles,
            int atfFiles,
            int componentFiles,
            int sourceLines,
            int nonBlankSourceLines,
            int componentLines,
            int nonBlankComponentLines,
            int planBlocks,
            int planLines,
            int generatedAtfLines,
            int planAreaCells,
            int maxPlanAreaCells,
            int planAreasAtLeast384,
            int planAreasAtLeast512,
            Map<String, Integer> commandCounts,
            Map<String, Integer> overlayCells) {
    }

    public record PlanGateReport(
            PlanMetrics metrics,
            List<PlanGate> gates,
            int passed,
            int failed,
            int manual) {
    }

    public record PlanGate(
            String name,
            String status,
            String value,
            String threshold,
            String note) {
    }

    public record PlanEditTrialReport(
            int sourceChangedLines,
            int expandedChangedLines,
            int planWarnings,
            int coreWarnings,
            boolean passed) {
    }

    public record PlanWarning(String file, String code, int x, int z, String message) {
    }

    public record PlanOutline(
            String file,
            String groupName,
            int originX,
            int originY,
            int originZ,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int styleCount,
            int maskCount,
            int fieldCount,
            List<RouteOutline> routes,
            List<ReservationOutline> reservations,
            List<LandmarkOutline> landmarks,
            List<ClusterOutline> clusters,
            int scatterActions,
            int scatterRequested,
            int lootActions,
            int lootRequested) {
    }

    public record PlanIndexEntry(String file, int line, String groupName, String kind,
                                 String name, String summary) {
    }

    public record RouteOutline(String name, int width, int points, int crossings, String first, String last) {
    }

    public record ReservationOutline(String name, String shape, int anchorX, int anchorZ,
                                     int clearTo, int requiredRouteDistance) {
    }

    public record LandmarkOutline(String name, String component, int x, int z, String reservation,
                                  int radius, int height, int rotation) {
    }

    public record ClusterOutline(String name, String reservation, int members, int spacing) {
    }

    private record TransformResult(List<String> lines, boolean expandedPlan, int generatedCommands) {
    }

    private record OverlayCell(int x, int z, int priority, String name) {
    }

    private static final class PlanMetricsBuilder {
        private int sourceFiles;
        private int atfFiles;
        private int componentFiles;
        private int sourceLines;
        private int nonBlankSourceLines;
        private int componentLines;
        private int nonBlankComponentLines;
        private int planBlocks;
        private int planLines;
        private int generatedAtfLines;
        private int planAreaCells;
        private int maxPlanAreaCells;
        private int planAreasAtLeast384;
        private int planAreasAtLeast512;
        private final Map<String, Integer> commandCounts = new TreeMap<>();

        private void addPlan(Plan plan, List<String> body) {
            planBlocks++;
            for (String line : body) {
                String stripped = stripComment(line).trim();
                if (!stripped.isEmpty() && !"}".equals(stripped)) {
                    planLines++;
                }
                if (stripped.startsWith("repeat component ")) {
                    increment("cluster_repeat_directives");
                }
            }
            int areaCells = (plan.area.maxX - plan.area.minX + 1) * (plan.area.maxZ - plan.area.minZ + 1);
            planAreaCells += areaCells;
            maxPlanAreaCells = Math.max(maxPlanAreaCells, areaCells);
            if (areaCells >= 384 * 384) {
                planAreasAtLeast384++;
            }
            if (areaCells >= 512 * 512) {
                planAreasAtLeast512++;
            }
            add("styles", plan.styles.size());
            add("masks", plan.masks.size());
            add("fields", plan.fields.size());
            add("routes", plan.routes.size());
            add("route_points", plan.routes.stream().mapToInt(route -> route.points.size()).sum());
            add("crossings", plan.routes.stream().mapToInt(route -> route.crossings.size()).sum());
            add("reservations", plan.reservations.size());
            add("landmarks", plan.landmarks.size());
            add("clusters", plan.clusters.size());
            add("cluster_members", plan.clusters.stream().mapToInt(cluster -> cluster.members.size()).sum());
            add("scatter_actions", (int) plan.actions.stream()
                    .filter(action -> action instanceof ScatterAction)
                    .count());
            add("scatter_requested", plan.actions.stream()
                    .filter(action -> action instanceof ScatterAction)
                    .map(ScatterAction.class::cast)
                    .mapToInt(scatter -> scatter.count)
                    .sum());
            add("loot_actions", (int) plan.actions.stream()
                    .filter(action -> action instanceof LootAction)
                    .count());
            add("loot_requested", plan.actions.stream()
                    .filter(action -> action instanceof LootAction)
                    .map(LootAction.class::cast)
                    .mapToInt(loot -> loot.count)
                    .sum());
            add("terrain_actions", (int) plan.actions.stream()
                    .filter(action -> action instanceof TerrainAction)
                    .count());
            add("fill_actions", (int) plan.actions.stream()
                    .filter(action -> action instanceof FillAction)
                    .count());
            add("biome_actions", (int) plan.actions.stream()
                    .filter(action -> action instanceof BiomeAction)
                    .count());
            add("hazards", plan.hazards.size());
        }

        private void add(String key, int value) {
            if (value != 0) {
                commandCounts.merge(key, value, Integer::sum);
            }
        }

        private void increment(String key) {
            commandCounts.merge(key, 1, Integer::sum);
        }

        private PlanMetrics toMetrics(Map<String, Integer> overlayCells) {
            return new PlanMetrics(sourceFiles, atfFiles, componentFiles, sourceLines,
                    nonBlankSourceLines, componentLines, nonBlankComponentLines, planBlocks,
                    planLines, generatedAtfLines, planAreaCells, maxPlanAreaCells,
                    planAreasAtLeast384, planAreasAtLeast512, new TreeMap<>(commandCounts),
                    new TreeMap<>(overlayCells));
        }
    }

    private record Area(int minX, int minZ, int maxX, int maxZ) {
    }

    private record Point(int x, int z) {
    }

    private record Cell(int x, int y, int z) {
    }

    private record Crossing(int x, int z, int radius) {
        private boolean covers(int cellX, int cellZ) {
            int dx = cellX - x;
            int dz = cellZ - z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    private record Hazard(String mask, boolean outside, String block) {
        private boolean contains(Plan plan, int x, int z) {
            boolean inside = plan.inMask(mask, x, z);
            return outside ? !inside : inside;
        }
    }

    private interface Mask {
        boolean contains(int x, int z, Plan plan);
    }

    private interface Field {
        int height(int x, int z, Plan plan);
    }

    private interface Action {
        void expand(Plan plan, List<String> out);
    }

    private static final class Plan {
        private Area area;
        private final Map<String, Map<String, String>> styles = new HashMap<>();
        private Map<String, String> activeStyle = Map.of();
        private final Map<String, Mask> masks = new LinkedHashMap<>();
        private final Map<String, Field> fields = new HashMap<>();
        private final List<Route> routes = new ArrayList<>();
        private final List<Reservation> reservations = new ArrayList<>();
        private final List<LandmarkAction> landmarks = new ArrayList<>();
        private final List<ClusterAction> clusters = new ArrayList<>();
        private final List<Hazard> hazards = new ArrayList<>();
        private final List<Action> actions = new ArrayList<>();
        private final Set<Cell> routeCells = new HashSet<>();
        private int overlayOriginX;
        private int overlayOriginZ;

        private List<String> expand() {
            routeCells.clear();
            List<String> out = new ArrayList<>();
            for (Action action : actions) {
                action.expand(this, out);
            }
            return out;
        }

        private List<OverlayCell> overlayCells() {
            expand();
            List<OverlayCell> cells = new ArrayList<>();
            for (Map.Entry<String, Mask> entry : masks.entrySet()) {
                String name = "plan:mask:" + entry.getKey();
                for (int z = area.minZ; z <= area.maxZ; z++) {
                    for (int x = area.minX; x <= area.maxX; x++) {
                        if (entry.getValue().contains(x, z, this)) {
                            cells.add(new OverlayCell(overlayOriginX + x, overlayOriginZ + z, 1, name));
                        }
                    }
                }
            }
            for (Hazard hazard : hazards) {
                for (int z = area.minZ; z <= area.maxZ; z++) {
                    for (int x = area.minX; x <= area.maxX; x++) {
                        if (hazard.contains(this, x, z)) {
                            cells.add(new OverlayCell(overlayOriginX + x, overlayOriginZ + z, 2, "plan:hazard"));
                        }
                    }
                }
            }
            for (Reservation reservation : reservations) {
                for (int z = area.minZ; z <= area.maxZ; z++) {
                    for (int x = area.minX; x <= area.maxX; x++) {
                        if (reservation.shape.contains(x, z, this)) {
                            cells.add(new OverlayCell(overlayOriginX + x, overlayOriginZ + z, 3, "plan:reservation"));
                        }
                    }
                }
            }
            for (Cell cell : routeCells) {
                cells.add(new OverlayCell(overlayOriginX + cell.x, overlayOriginZ + cell.z, 4, "plan:route"));
            }
            for (LandmarkAction landmark : landmarks) {
                int radius = Math.max(1, landmark.radius);
                for (int z = landmark.z - radius; z <= landmark.z + radius; z++) {
                    for (int x = landmark.x - radius; x <= landmark.x + radius; x++) {
                        int dx = x - landmark.x;
                        int dz = z - landmark.z;
                        if (dx * dx + dz * dz <= radius * radius) {
                            cells.add(new OverlayCell(overlayOriginX + x, overlayOriginZ + z,
                                    5, "plan:landmark"));
                        }
                    }
                }
            }
            for (ClusterAction cluster : clusters) {
                Point clusterAnchor = cluster.anchor(this);
                for (ClusterMember member : cluster.members) {
                    Point anchor = member.anchor(clusterAnchor);
                    int radius = Math.max(1, member.radius);
                    for (int z = anchor.z - radius; z <= anchor.z + radius; z++) {
                        for (int x = anchor.x - radius; x <= anchor.x + radius; x++) {
                            int dx = x - anchor.x;
                            int dz = z - anchor.z;
                            if (dx * dx + dz * dz <= radius * radius) {
                                cells.add(new OverlayCell(overlayOriginX + x, overlayOriginZ + z,
                                        6, "plan:cluster"));
                            }
                        }
                    }
                }
            }
            for (Route route : routes) {
                for (Crossing crossing : route.crossings) {
                    for (Cell cell : route.cells(this)) {
                        if (crossing.covers(cell.x, cell.z)) {
                            cells.add(new OverlayCell(overlayOriginX + cell.x, overlayOriginZ + cell.z,
                                    7, "plan:crossing"));
                        }
                    }
                }
            }
            for (Action action : actions) {
                if (action instanceof ScatterAction scatter) {
                    int emitted = 0;
                    for (Point point : candidates(this, scatter.mask, scatter.seed,
                            scatter.avoidRoutes, scatter.avoidReservations)) {
                        if (emitted >= scatter.count) {
                            break;
                        }
                        cells.add(new OverlayCell(overlayOriginX + point.x, overlayOriginZ + point.z,
                                8, "plan:scatter"));
                        emitted++;
                    }
                } else if (action instanceof LootAction loot) {
                    int emitted = 0;
                    for (Point point : candidates(this, loot.mask, loot.seed,
                            loot.avoidRoutes, loot.avoidReservations)) {
                        if (emitted >= loot.count) {
                            break;
                        }
                        cells.add(new OverlayCell(overlayOriginX + point.x, overlayOriginZ + point.z,
                                9, "plan:loot"));
                        emitted++;
                    }
                }
            }
            return cells;
        }

        private Mask mask(String name) {
            Mask mask = masks.get(name);
            if (mask == null) {
                throw new IllegalArgumentException("Unknown mask: " + name);
            }
            return mask;
        }

        private Field field(String name) {
            Field field = fields.get(name);
            if (field == null) {
                throw new IllegalArgumentException("Unknown field: " + name);
            }
            return field;
        }

        private boolean inMask(String name, int x, int z) {
            return mask(name).contains(x, z, this);
        }

        private void useStyle(String name) {
            Map<String, String> style = styles.get(name);
            if (style == null) {
                throw new IllegalArgumentException("Unknown style: " + name);
            }
            activeStyle = style;
        }

        private String resolve(String token) {
            if (!token.startsWith("style.")) {
                return token;
            }
            String key = token.substring("style.".length());
            String value = activeStyle.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Unknown style key: " + key);
            }
            return value;
        }

        private void diagnose(Path file, List<PlanWarning> warnings) {
            expand();
            diagnoseReservationOverlaps(file, warnings);
            diagnoseRoutes(file, warnings);
            diagnoseReservationReachability(file, warnings);
            diagnoseLandmarks(file, warnings);
            diagnoseClusters(file, warnings);
            diagnoseCandidateCounts(file, warnings);
        }

        private void diagnoseReservationOverlaps(Path file, List<PlanWarning> warnings) {
            for (int i = 0; i < reservations.size(); i++) {
                for (int j = i + 1; j < reservations.size(); j++) {
                    Reservation left = reservations.get(i);
                    Reservation right = reservations.get(j);
                    Point overlap = firstOverlap(left.shape, right.shape);
                    if (overlap != null) {
                        warnings.add(new PlanWarning(file.toString(), "reservation-overlap",
                                overlap.x, overlap.z, "reservation " + quote(left.name)
                                + " overlaps " + quote(right.name)));
                    }
                }
            }
        }

        private Point firstOverlap(Mask left, Mask right) {
            for (int z = area.minZ; z <= area.maxZ; z++) {
                for (int x = area.minX; x <= area.maxX; x++) {
                    if (left.contains(x, z, this) && right.contains(x, z, this)) {
                        return new Point(x, z);
                    }
                }
            }
            return null;
        }

        private void diagnoseRoutes(Path file, List<PlanWarning> warnings) {
            for (Route route : routes) {
                if (route.points.size() < 2) {
                    Point point = route.points.isEmpty() ? new Point(area.minX, area.minZ) : route.points.get(0);
                    warnings.add(new PlanWarning(file.toString(), "route-too-short", point.x, point.z,
                            "route " + quote(route.name) + " needs at least two points"));
                    continue;
                }
                for (Point point : route.points) {
                    if (point.x < area.minX || point.x > area.maxX || point.z < area.minZ || point.z > area.maxZ) {
                        warnings.add(new PlanWarning(file.toString(), "route-outside-area", point.x, point.z,
                                "route " + quote(route.name) + " point is outside plan area"));
                    }
                }
                diagnoseRouteSlope(file, warnings, route);
                diagnoseRouteHazards(file, warnings, route);
            }
        }

        private void diagnoseReservationReachability(Path file, List<PlanWarning> warnings) {
            for (Reservation reservation : reservations) {
                if (reservation.requiredRouteDistance <= 0) {
                    continue;
                }
                if (!reservation.touchesRoute(this)) {
                    Point anchor = reservation.anchor();
                    warnings.add(new PlanWarning(file.toString(), "reservation-unreached",
                            anchor.x, anchor.z, "reservation " + quote(reservation.name)
                                    + " has no route within " + reservation.requiredRouteDistance
                                    + " block(s)"));
                }
            }
        }

        private void diagnoseLandmarks(Path file, List<PlanWarning> warnings) {
            for (LandmarkAction landmark : landmarks) {
                if (landmark.x < area.minX || landmark.x > area.maxX
                        || landmark.z < area.minZ || landmark.z > area.maxZ) {
                    warnings.add(new PlanWarning(file.toString(), "landmark-outside-area",
                            landmark.x, landmark.z, "landmark " + quote(landmark.name)
                                    + " anchor is outside plan area"));
                    continue;
                }
                Reservation reservation = reservation(landmark.reservation);
                if (reservation == null) {
                    warnings.add(new PlanWarning(file.toString(), "landmark-missing-reservation",
                            landmark.x, landmark.z, "landmark " + quote(landmark.name)
                                    + " references unknown reservation " + quote(landmark.reservation)));
                    continue;
                }
                Point outside = landmark.firstFootprintPointOutside(reservation.shape, this);
                if (outside != null) {
                    warnings.add(new PlanWarning(file.toString(), "landmark-outside-reservation",
                            outside.x, outside.z, "landmark " + quote(landmark.name)
                                    + " footprint exceeds reservation " + quote(reservation.name)));
                }
                int baseY = reservation.surfaceY(this);
                if (baseY + landmark.height > reservation.clearTo) {
                    warnings.add(new PlanWarning(file.toString(), "landmark-clearance-too-low",
                            landmark.x, landmark.z, "landmark " + quote(landmark.name)
                                    + " reaches y=" + (baseY + landmark.height)
                                    + " but reservation " + quote(reservation.name)
                                    + " only clears to y=" + reservation.clearTo));
                }
            }
        }

        private void diagnoseClusters(Path file, List<PlanWarning> warnings) {
            for (ClusterAction cluster : clusters) {
                Reservation reservation = reservation(cluster.reservation);
                if (reservation == null) {
                    warnings.add(new PlanWarning(file.toString(), "cluster-missing-reservation",
                            cluster.x, cluster.z, "cluster " + quote(cluster.name)
                                    + " references unknown reservation " + quote(cluster.reservation)));
                    continue;
                }
                Point clusterAnchor = cluster.anchor(this);
                if (clusterAnchor.x < area.minX || clusterAnchor.x > area.maxX
                        || clusterAnchor.z < area.minZ || clusterAnchor.z > area.maxZ) {
                    warnings.add(new PlanWarning(file.toString(), "cluster-outside-area",
                            clusterAnchor.x, clusterAnchor.z, "cluster " + quote(cluster.name)
                                    + " anchor is outside plan area"));
                    continue;
                }
                for (ClusterMember member : cluster.members) {
                    Point anchor = member.anchor(clusterAnchor);
                    if (anchor.x < area.minX || anchor.x > area.maxX
                            || anchor.z < area.minZ || anchor.z > area.maxZ) {
                        warnings.add(new PlanWarning(file.toString(), "cluster-member-outside-area",
                                anchor.x, anchor.z, "cluster " + quote(cluster.name)
                                        + " component anchor is outside plan area"));
                        continue;
                    }
                    Point outside = member.firstFootprintPointOutside(clusterAnchor, reservation.shape, this);
                    if (outside != null) {
                        warnings.add(new PlanWarning(file.toString(), "cluster-outside-reservation",
                                outside.x, outside.z, "cluster " + quote(cluster.name)
                                        + " footprint exceeds reservation " + quote(reservation.name)));
                    }
                    int baseY = reservation.surfaceY(this);
                    if (baseY + member.height > reservation.clearTo) {
                        warnings.add(new PlanWarning(file.toString(), "cluster-clearance-too-low",
                                anchor.x, anchor.z, "cluster " + quote(cluster.name)
                                        + " component reaches y=" + (baseY + member.height)
                                        + " but reservation " + quote(reservation.name)
                                        + " only clears to y=" + reservation.clearTo));
                    }
                }
                diagnoseClusterSpacing(file, warnings, cluster);
            }
        }

        private void diagnoseClusterSpacing(Path file, List<PlanWarning> warnings, ClusterAction cluster) {
            Point clusterAnchor = cluster.anchor(this);
            for (int i = 0; i < cluster.members.size(); i++) {
                ClusterMember left = cluster.members.get(i);
                Point leftAnchor = left.anchor(clusterAnchor);
                for (int j = i + 1; j < cluster.members.size(); j++) {
                    ClusterMember right = cluster.members.get(j);
                    Point rightAnchor = right.anchor(clusterAnchor);
                    int dx = leftAnchor.x - rightAnchor.x;
                    int dz = leftAnchor.z - rightAnchor.z;
                    int required = left.radius + right.radius + cluster.spacing;
                    if (dx * dx + dz * dz < required * required) {
                        warnings.add(new PlanWarning(file.toString(), "cluster-spacing-too-tight",
                                rightAnchor.x, rightAnchor.z, "cluster " + quote(cluster.name)
                                        + " has components closer than spacing " + cluster.spacing));
                    }
                }
            }
        }

        private Reservation reservation(String name) {
            for (Reservation reservation : reservations) {
                if (reservation.name.equals(name)) {
                    return reservation;
                }
            }
            return null;
        }

        private void diagnoseRouteSlope(Path file, List<PlanWarning> warnings, Route route) {
            int maxStep = 4;
            for (int i = 0; i + 1 < route.points.size(); i++) {
                Point a = route.points.get(i);
                Point b = route.points.get(i + 1);
                int dx = b.x - a.x;
                int dz = b.z - a.z;
                int steps = Math.max(Math.abs(dx), Math.abs(dz));
                int previousHeight = field(route.field).height(a.x, a.z, this);
                for (int step = 1; step <= steps; step++) {
                    int x = a.x + Math.round(dx * (step / (float) steps));
                    int z = a.z + Math.round(dz * (step / (float) steps));
                    int height = field(route.field).height(x, z, this);
                    if (Math.abs(height - previousHeight) > maxStep) {
                        warnings.add(new PlanWarning(file.toString(), "route-slope-too-steep", x, z,
                                "route " + quote(route.name) + " changes height by "
                                        + Math.abs(height - previousHeight) + " block(s) in one step"));
                        return;
                    }
                    previousHeight = height;
                }
            }
        }

        private void diagnoseRouteHazards(Path file, List<PlanWarning> warnings, Route route) {
            if (hazards.isEmpty()) {
                return;
            }
            for (Cell cell : route.cells(this)) {
                for (Hazard hazard : hazards) {
                    if (hazard.contains(this, cell.x, cell.z) && !route.crossesAt(cell.x, cell.z)) {
                        warnings.add(new PlanWarning(file.toString(), "route-missing-crossing",
                                cell.x, cell.z, "route " + quote(route.name)
                                        + " enters " + hazard.block + " without a declared crossing"));
                        return;
                    }
                }
            }
        }

        private void diagnoseCandidateCounts(Path file, List<PlanWarning> warnings) {
            for (Action action : actions) {
                if (action instanceof ScatterAction scatter) {
                    int available = candidates(this, scatter.mask, scatter.seed,
                            scatter.avoidRoutes, scatter.avoidReservations).size();
                    if (available < scatter.count) {
                        warnings.add(new PlanWarning(file.toString(), "scatter-unsatisfied-count",
                                area.minX, area.minZ, "scatter_in " + scatter.mask + " requested "
                                        + scatter.count + " placement(s) but only " + available + " are available"));
                    }
                } else if (action instanceof LootAction loot) {
                    int available = candidates(this, loot.mask, loot.seed,
                            loot.avoidRoutes, loot.avoidReservations).size();
                    if (available < loot.count) {
                        warnings.add(new PlanWarning(file.toString(), "loot-unsatisfied-count",
                                area.minX, area.minZ, "loot " + loot.mask + " requested " + loot.count
                                        + " anchor(s) but only " + available + " are available"));
                    }
                }
            }
        }
    }

    private record CircleMask(int centerX, int centerZ, int radius) implements Mask {
        @Override
        public boolean contains(int x, int z, Plan plan) {
            int dx = x - centerX;
            int dz = z - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    private record RectMask(int x1, int z1, int x2, int z2) implements Mask {
        @Override
        public boolean contains(int x, int z, Plan plan) {
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }

    private record RadialMask(int centerX, int centerZ, int radius, double noise, int seed) implements Mask {
        @Override
        public boolean contains(int x, int z, Plan plan) {
            double dx = x - centerX;
            double dz = z - centerZ;
            double angleNoise = smoothNoise((int) Math.round(dx), (int) Math.round(dz), seed, 48, 3) - 0.5;
            double localRadius = radius * (1.0 + angleNoise * noise);
            return dx * dx + dz * dz <= localRadius * localRadius;
        }
    }

    private record DifferenceMask(String left, String right) implements Mask {
        @Override
        public boolean contains(int x, int z, Plan plan) {
            return plan.inMask(left, x, z) && !plan.inMask(right, x, z);
        }
    }

    private record UnionMask(String left, String right) implements Mask {
        @Override
        public boolean contains(int x, int z, Plan plan) {
            return plan.inMask(left, x, z) || plan.inMask(right, x, z);
        }
    }

    private record IntersectionMask(String left, String right) implements Mask {
        @Override
        public boolean contains(int x, int z, Plan plan) {
            return plan.inMask(left, x, z) && plan.inMask(right, x, z);
        }
    }

    private record NotMask(String name) implements Mask {
        @Override
        public boolean contains(int x, int z, Plan plan) {
            return !plan.inMask(name, x, z);
        }
    }

    private record TerrainField(String mask, int base, int amplitude, int scale, int octaves, int seed)
            implements Field {
        @Override
        public int height(int x, int z, Plan plan) {
            return base + Math.round((float) (smoothNoise(x, z, seed, scale, octaves) * amplitude));
        }
    }

    private record RidgeField(List<Point> points, int width, int height) implements Field {
        @Override
        public int height(int x, int z, Plan plan) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i + 1 < points.size(); i++) {
                best = Math.min(best, distanceToSegment(x, z, points.get(i), points.get(i + 1)));
            }
            if (best >= width) {
                return 0;
            }
            return Math.round((float) (height * (1.0 - best / width)));
        }
    }

    private record MaxField(String left, String right) implements Field {
        @Override
        public int height(int x, int z, Plan plan) {
            return Math.max(plan.field(left).height(x, z, plan), plan.field(right).height(x, z, plan));
        }
    }

    private record TerrainAction(String mask, String field, int fromY, String top, String under) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            Field f = plan.field(field);
            out.add("  fill " + plan.area.minX + " " + (fromY - 1) + " " + plan.area.minZ
                    + " to " + plan.area.maxX + " " + (fromY - 1) + " " + plan.area.maxZ
                    + " minecraft:bedrock");
            if (f instanceof TerrainField terrain) {
                out.add("  smooth_terrain " + plan.area.minX + " " + plan.area.minZ + " "
                        + plan.area.maxX + " " + plan.area.maxZ
                        + " from " + fromY
                        + " base " + terrain.base
                        + " amplitude " + terrain.amplitude
                        + " scale " + terrain.scale
                        + " octaves " + terrain.octaves
                        + " seed " + terrain.seed
                        + " " + top + " " + under);
                return;
            }
            forMaskRuns(plan, mask, false, (x1, x2, z) -> {
                int topY = plan.field(field).height(x1, z, plan);
                out.add("  fill " + x1 + " " + fromY + " " + z + " to " + x2 + " " + (topY - 1)
                        + " " + z + " " + under);
                out.add("  fill " + x1 + " " + topY + " " + z + " to " + x2 + " " + topY
                        + " " + z + " " + top);
            });
        }
    }

    private record FillAction(String mask, boolean outside, int fromY, int toY, String block) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            forMaskRuns(plan, mask, outside, (x1, x2, z) ->
                    out.add("  fill " + x1 + " " + fromY + " " + z + " to " + x2 + " " + toY
                            + " " + z + " " + block));
        }
    }

    private record BiomeAction(String mask, int y1, int y2, String biome) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            forMaskRuns(plan, mask, false, (x1, x2, z) ->
                    out.add("  fill_biome " + x1 + " " + y1 + " " + z + " " + x2 + " " + y2
                            + " " + z + " " + biome));
        }
    }

    private record Reservation(String name, Mask shape, String field, String floor, String floorPalette,
                               int floorSeed, String edge, String edgePalette, int edgeWidth, int edgeSeed,
                               int clearTo, int requiredRouteDistance) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            out.add("  # reserve " + quote(name));
            if (shape instanceof CircleMask circle) {
                int y = plan.field(field).height(circle.centerX, circle.centerZ, plan) + 1;
                out.add("  cylinder " + circle.centerX + " 1 " + circle.centerZ + " "
                        + circle.radius + " " + (y - 1) + " minecraft:dirt");
                writeSurface(plan, out, y, (x, z) -> shape.contains(x, z, plan), floor, floorPalette, floorSeed);
                writeEdge(plan, out, y);
                out.add("  cylinder " + circle.centerX + " " + (y + 1) + " " + circle.centerZ + " "
                        + circle.radius + " " + clearTo + " minecraft:air");
            } else if (shape instanceof RectMask rect) {
                int cx = (rect.x1 + rect.x2) / 2;
                int cz = (rect.z1 + rect.z2) / 2;
                int y = plan.field(field).height(cx, cz, plan) + 1;
                out.add("  fill " + rect.x1 + " 1 " + rect.z1 + " to " + rect.x2 + " " + (y - 1)
                        + " " + rect.z2 + " minecraft:dirt");
                writeSurface(plan, out, y, (x, z) -> shape.contains(x, z, plan), floor, floorPalette, floorSeed);
                writeEdge(plan, out, y);
                out.add("  fill " + rect.x1 + " " + (y + 1) + " " + rect.z1 + " to " + rect.x2
                        + " " + clearTo + " " + rect.z2 + " minecraft:air");
            }
        }

        private void writeEdge(Plan plan, List<String> out, int y) {
            if (edgeWidth <= 0 || (edge == null && edgePalette == null)) {
                return;
            }
            writeSurface(plan, out, y, (x, z) -> edgeContains(x, z, plan),
                    edge == null ? floor : edge, edgePalette, edgeSeed);
        }

        private void writeSurface(Plan plan, List<String> out, int y, ShapePredicate selected,
                                  String block, String palette, int seed) {
            forShapeRuns(plan, selected, (x1, x2, z) -> {
                if (palette == null) {
                    out.add("  fill " + x1 + " " + y + " " + z + " to " + x2 + " " + y
                            + " " + z + " " + block);
                } else {
                    out.add("  fill_palette " + x1 + " " + y + " " + z + " " + x2 + " " + y
                            + " " + z + " " + palette + " seed " + (seed + z));
                }
            });
        }

        private boolean edgeContains(int x, int z, Plan plan) {
            if (!shape.contains(x, z, plan)) {
                return false;
            }
            if (shape instanceof CircleMask circle) {
                int innerRadius = Math.max(0, circle.radius - edgeWidth);
                int dx = x - circle.centerX;
                int dz = z - circle.centerZ;
                return dx * dx + dz * dz > innerRadius * innerRadius;
            }
            if (shape instanceof RectMask rect) {
                int minX = Math.min(rect.x1, rect.x2);
                int maxX = Math.max(rect.x1, rect.x2);
                int minZ = Math.min(rect.z1, rect.z2);
                int maxZ = Math.max(rect.z1, rect.z2);
                return x < minX + edgeWidth || x > maxX - edgeWidth
                        || z < minZ + edgeWidth || z > maxZ - edgeWidth;
            }
            return false;
        }

        private boolean touchesRoute(Plan plan) {
            int distance = requiredRouteDistance;
            int squared = distance * distance;
            for (Cell cell : plan.routeCells) {
                for (int dz = -distance; dz <= distance; dz++) {
                    for (int dx = -distance; dx <= distance; dx++) {
                        if (dx * dx + dz * dz <= squared
                                && shape.contains(cell.x + dx, cell.z + dz, plan)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private Point anchor() {
            if (shape instanceof CircleMask circle) {
                return new Point(circle.centerX, circle.centerZ);
            }
            if (shape instanceof RectMask rect) {
                return new Point((rect.x1 + rect.x2) / 2, (rect.z1 + rect.z2) / 2);
            }
            return new Point(0, 0);
        }

        private int surfaceY(Plan plan) {
            Point anchor = anchor();
            return plan.field(field).height(anchor.x, anchor.z, plan) + 1;
        }
    }

    private record Route(String name, int width, String palette, String field, int seed,
                         String centerline, String edge,
                         List<Point> points, List<Crossing> crossings)
            implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            Set<Cell> cells = cells(plan);
            plan.routeCells.addAll(cells);
            out.add("  # route " + quote(name));
            forCellRuns(cells, (x1, x2, y, z) -> {
                out.add("  fill " + x1 + " 1 " + z + " to " + x2 + " " + (y - 1)
                        + " " + z + " minecraft:dirt");
                out.add("  fill_palette " + x1 + " " + y + " " + z + " " + x2 + " " + y
                        + " " + z + " " + palette + " seed " + (seed + z));
            });
            if (centerline != null) {
                Set<Cell> spine = new HashSet<>();
                for (int i = 0; i + 1 < points.size(); i++) {
                    addSegmentCells(plan, spine, points.get(i), points.get(i + 1), 0);
                }
                for (Cell cell : spine) {
                    out.add("  set " + cell.x + " " + cell.y + " " + cell.z + " " + centerline);
                }
            }
            if (edge != null) {
                Set<Point> footprint = new HashSet<>();
                for (Cell cell : cells) {
                    footprint.add(new Point(cell.x, cell.z));
                }
                for (Cell cell : cells) {
                    if (crossesAt(cell.x, cell.z)) {
                        continue;
                    }
                    boolean boundary = !footprint.contains(new Point(cell.x + 1, cell.z))
                            || !footprint.contains(new Point(cell.x - 1, cell.z))
                            || !footprint.contains(new Point(cell.x, cell.z + 1))
                            || !footprint.contains(new Point(cell.x, cell.z - 1));
                    if (boundary) {
                        out.add("  set " + cell.x + " " + (cell.y + 1) + " " + cell.z + " " + edge);
                    }
                }
            }
        }

        private Set<Cell> cells(Plan plan) {
            Set<Cell> cells = new HashSet<>();
            int radius = Math.max(0, width / 2);
            for (int i = 0; i + 1 < points.size(); i++) {
                addSegmentCells(plan, cells, points.get(i), points.get(i + 1), radius);
            }
            return cells;
        }

        private boolean crossesAt(int x, int z) {
            for (Crossing crossing : crossings) {
                if (crossing.covers(x, z)) {
                    return true;
                }
            }
            return false;
        }

        private void addSegmentCells(Plan plan, Set<Cell> cells, Point a, Point b, int radius) {
            int dx = b.x - a.x;
            int dz = b.z - a.z;
            int steps = Math.max(Math.abs(dx), Math.abs(dz));
            if (steps == 0) {
                addDisc(plan, cells, a.x, a.z, radius);
                return;
            }
            for (int i = 0; i <= steps; i++) {
                int x = a.x + Math.round(dx * (i / (float) steps));
                int z = a.z + Math.round(dz * (i / (float) steps));
                addDisc(plan, cells, x, z, radius);
            }
        }

        private void addDisc(Plan plan, Set<Cell> cells, int centerX, int centerZ, int radius) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    int dx = x - centerX;
                    int dz = z - centerZ;
                    if (dx * dx + dz * dz <= radius * radius) {
                        cells.add(new Cell(x, plan.field(field).height(x, z, plan) + 1, z));
                    }
                }
            }
        }
    }

    private static final java.util.regex.Pattern COMPONENT_DEF = java.util.regex.Pattern
            .compile("^\\s*component\\s+\\\"?([A-Za-z0-9_:-]+)\\\"?\\s*\\{\\s*$");
    private static final java.util.regex.Pattern BOUNDS_DEF = java.util.regex.Pattern
            .compile("^\\s*bounds\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+to\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)");
    private static final java.util.regex.Pattern PLACE_DEF = java.util.regex.Pattern
            .compile("^\\s*place\\s+([A-Za-z0-9_:-]+)\\s+at\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)(?:\\s+rotate\\s+(\\d+))?");
    private static final java.util.regex.Pattern FLORA_NAME = java.util.regex.Pattern
            .compile(".*(tree|bush|leaf|foliage|grass|fern|mushroom|flower|sapling|driftwood|crop|vine|kelp).*");

    private record PlanPlacement(String component, int anchorX, int anchorZ, int rotation,
                                 boolean flora, Path file) {
    }

    private static void collectComponentBounds(List<String> source, Map<String, int[]> bounds) {
        String current = null;
        for (String line : source) {
            var component = COMPONENT_DEF.matcher(line);
            if (component.matches()) {
                current = component.group(1);
                continue;
            }
            if (current != null) {
                var b = BOUNDS_DEF.matcher(line);
                if (b.find()) {
                    int[] box = new int[6];
                    for (int i = 0; i < 6; i++) {
                        box[i] = Integer.parseInt(b.group(i + 1));
                    }
                    bounds.putIfAbsent(current, box);
                }
                if (line.contains("}")) {
                    current = null;
                }
            }
        }
    }

    private static void collectFreePlacements(Path file, List<String> source, List<PlanPlacement> out) {
        int originX = 0;
        int originZ = 0;
        boolean inPlan = false;
        int depth = 0;
        for (String line : source) {
            String stripped = stripComment(line).trim();
            if (stripped.startsWith("origin ")) {
                List<String> tokens = tokenize(stripped);
                if (tokens.size() >= 4) {
                    originX = intAt(tokens, 1);
                    originZ = intAt(tokens, 3);
                }
            }
            if (!inPlan && line.trim().equals("plan {")) {
                inPlan = true;
                depth = 1;
                continue;
            }
            if (inPlan) {
                depth += braceDelta(line);
                if (depth <= 0) {
                    inPlan = false;
                }
                continue;
            }
            var place = PLACE_DEF.matcher(line);
            if (place.matches()) {
                int rotation = place.group(5) == null ? 0 : Integer.parseInt(place.group(5));
                out.add(new PlanPlacement(place.group(1),
                        originX + Integer.parseInt(place.group(2)),
                        originZ + Integer.parseInt(place.group(4)),
                        rotation, false, file));
            }
        }
    }

    private static int[] placedBox(Map<String, int[]> bounds, PlanPlacement placement) {
        int[] box = bounds.get(placement.component);
        if (box == null) {
            return null;
        }
        int x0 = box[0];
        int z0 = box[2];
        int x1 = box[3];
        int z1 = box[5];
        // matches DslParser.rotate: CW_90 (x,z)->(-z,x), CW_180 ->(-x,-z), CW_270 ->(z,-x)
        if (placement.rotation == 90) {
            x0 = -box[5];
            z0 = box[0];
            x1 = -box[2];
            z1 = box[3];
        } else if (placement.rotation == 270) {
            x0 = box[2];
            z0 = -box[3];
            x1 = box[5];
            z1 = -box[0];
        } else if (placement.rotation == 180) {
            x0 = -box[3];
            z0 = -box[5];
            x1 = -box[0];
            z1 = -box[2];
        }
        return new int[]{placement.anchorX + x0, placement.anchorZ + z0,
                placement.anchorX + x1, placement.anchorZ + z1};
    }

    private static PlanPlacement placed(String component, int anchorX, int anchorZ, int rotation,
                                        int originX, int originZ, Path file) {
        return new PlanPlacement(component, originX + anchorX, originZ + anchorZ,
                rotation, FLORA_NAME.matcher(component).matches(), file);
    }

    private void diagnosePlacementOverlaps(List<Plan> plans, List<PlanPlacement> freePlacements,
                                           Map<String, int[]> componentBounds, List<PlanWarning> warnings) {
        List<PlanPlacement> all = new ArrayList<>(freePlacements);
        for (Plan plan : plans) {
            for (LandmarkAction landmark : plan.landmarks) {
                all.add(placed(landmark.component, landmark.x, landmark.z, landmark.rotation,
                        plan.overlayOriginX, plan.overlayOriginZ, null));
            }
            for (ClusterAction cluster : plan.clusters) {
                Point anchor = cluster.anchor(plan);
                for (ClusterMember member : cluster.members) {
                    Point memberAnchor = member.anchor(anchor);
                    all.add(placed(member.component, memberAnchor.x, memberAnchor.z, member.rotation,
                            plan.overlayOriginX, plan.overlayOriginZ, null));
                }
            }
        }
        List<int[]> boxes = new ArrayList<>();
        List<PlanPlacement> resolved = new ArrayList<>();
        for (PlanPlacement placement : all) {
            int[] box = placedBox(componentBounds, placement);
            if (box != null) {
                resolved.add(placement);
                boxes.add(box);
            }
        }
        Set<String> reported = new HashSet<>();
        for (int i = 0; i < resolved.size(); i++) {
            for (int j = i + 1; j < resolved.size(); j++) {
                PlanPlacement a = resolved.get(i);
                PlanPlacement b = resolved.get(j);
                if (a.flora && b.flora) {
                    continue;
                }
                int[] ab = boxes.get(i);
                int[] bb = boxes.get(j);
                // flora gets clipped by reservation clears or harmlessly overhangs — flag it
                // only when its anchor column lands inside a structure footprint
                if (a.flora || b.flora) {
                    int f = a.flora ? i : j;
                    int st = a.flora ? j : i;
                    PlanPlacement flora = resolved.get(f);
                    int[] sb = boxes.get(st);
                    if (big(sb) && flora.anchorX >= sb[0] && flora.anchorX <= sb[2]
                            && flora.anchorZ >= sb[1] && flora.anchorZ <= sb[3]) {
                        String key = flora.component + "@" + flora.anchorX + "," + flora.anchorZ
                                + "|" + resolved.get(st).component;
                        if (reported.add(key)) {
                            warnings.add(new PlanWarning(String.valueOf(flora.file),
                                    "flora-inside-structure", flora.anchorX, flora.anchorZ,
                                    "flora " + quote(flora.component)
                                            + " anchor sits inside "
                                            + quote(resolved.get(st).component) + " footprint"));
                        }
                    }
                    continue;
                }
                // only structure-scale pairs warn: stalls, posts, crates and other small
                // props hugging a wall are intended density, and same-component runs tile
                if (!big(ab) || !big(bb) || a.component.equals(b.component)) {
                    continue;
                }
                int ox = Math.min(ab[2], bb[2]) - Math.max(ab[0], bb[0]);
                int oz = Math.min(ab[3], bb[3]) - Math.max(ab[1], bb[1]);
                if (ox < 2 || oz < 2) {
                    continue;
                }
                String key = a.component + "|" + b.component + "|" + Math.max(ab[0], bb[0])
                        + "," + Math.max(ab[1], bb[1]);
                if (reported.add(key)) {
                    warnings.add(new PlanWarning(String.valueOf(b.file), "placement-overlap",
                            Math.max(ab[0], bb[0]), Math.max(ab[1], bb[1]),
                            "placement " + quote(a.component) + " footprint overlaps "
                                    + quote(b.component) + " by " + ox + "x" + oz));
                }
            }
        }
    }

    private static boolean big(int[] box) {
        // structure-scale footprint: buildings and towers pass, stalls/crates/boats/props don't
        return (long) (box[2] - box[0] + 1) * (box[3] - box[1] + 1) >= 80;
    }


    private record LandmarkAction(String name, String component, int x, int z, String field, String reservation,
                                  int radius, int height, int rotation) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            Reservation resolved = plan.reservation(reservation);
            int y = resolved == null ? plan.field(field).height(x, z, plan) + 1 : resolved.surfaceY(plan);
            out.add("  # landmark " + quote(name));
            out.add("  place " + component + " at " + x + " " + y + " " + z + " rotate " + rotation);
        }

        private Point firstFootprintPointOutside(Mask reservationShape, Plan plan) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz <= radius * radius
                            && !reservationShape.contains(x + dx, z + dz, plan)) {
                        return new Point(x + dx, z + dz);
                    }
                }
            }
            return null;
        }
    }

    private record ClusterAction(String name, int x, int z, boolean anchorOnReservation, String field,
                                 String reservation, int spacing,
                                 List<ClusterMember> members) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            out.add("  # cluster " + quote(name));
            Point clusterAnchor = anchor(plan);
            Reservation resolved = plan.reservation(reservation);
            int y = resolved == null ? Integer.MIN_VALUE : resolved.surfaceY(plan);
            for (ClusterMember member : members) {
                Point anchor = member.anchor(clusterAnchor);
                int memberY = y == Integer.MIN_VALUE ? plan.field(field).height(anchor.x, anchor.z, plan) + 1 : y;
                out.add("  place " + member.component + " at " + anchor.x + " " + memberY + " " + anchor.z
                        + " rotate " + member.rotation);
            }
        }

        private Point anchor(Plan plan) {
            if (!anchorOnReservation) {
                return new Point(x, z);
            }
            Reservation resolved = plan.reservation(reservation);
            if (resolved == null) {
                return new Point(x, z);
            }
            return resolved.anchor();
        }
    }

    private record ClusterMember(String component, int dx, int dz, int radius, int height, int rotation) {
        private Point anchor(Point clusterAnchor) {
            return new Point(clusterAnchor.x + dx, clusterAnchor.z + dz);
        }

        private Point firstFootprintPointOutside(Point clusterAnchor, Mask reservationShape, Plan plan) {
            Point anchor = anchor(clusterAnchor);
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz <= radius * radius
                            && !reservationShape.contains(anchor.x + dx, anchor.z + dz, plan)) {
                        return new Point(anchor.x + dx, anchor.z + dz);
                    }
                }
            }
            return null;
        }
    }

    private record ScatterAction(String mask, int count, int seed, int avoidRoutes, int avoidReservations,
                                 String field, List<ScatterChoice> choices) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            List<Point> candidates = candidates(plan, mask, seed, avoidRoutes, avoidReservations);
            int emitted = 0;
            int totalWeight = choices.stream().mapToInt(ScatterChoice::weight).sum();
            out.add("  # scatter_in " + mask);
            for (Point point : candidates) {
                if (emitted >= count) {
                    return;
                }
                int slot = Math.floorMod(mix(point.x, 0, point.z, seed + 17), totalWeight);
                int cursor = 0;
                ScatterChoice choice = choices.get(choices.size() - 1);
                for (ScatterChoice candidate : choices) {
                    cursor += candidate.weight;
                    if (slot < cursor) {
                        choice = candidate;
                        break;
                    }
                }
                int y = plan.field(field).height(point.x, point.z, plan) + 1;
                out.add("  fill " + point.x + " 1 " + point.z + " to " + point.x + " " + (y - 1)
                        + " " + point.z + " minecraft:dirt");
                if ("component".equals(choice.kind)) {
                    int rotate = Math.floorMod(mix(point.x, y, point.z, seed + 31), 4) * 90;
                    out.add("  place " + choice.value + " at " + point.x + " " + y + " " + point.z
                            + " rotate " + rotate);
                } else {
                    out.add("  set " + point.x + " " + y + " " + point.z + " " + choice.value);
                }
                emitted++;
            }
        }
    }

    private record LootAction(String mask, int tier, int count, int seed, int avoidRoutes, int avoidReservations,
                              String field) implements Action {
        @Override
        public void expand(Plan plan, List<String> out) {
            List<Point> candidates = candidates(plan, mask, seed, avoidRoutes, avoidReservations);
            out.add("  # loot tier " + tier);
            for (int i = 0; i < Math.min(count, candidates.size()); i++) {
                Point point = candidates.get(i);
                int y = plan.field(field).height(point.x, point.z, plan) + 1;
                out.add("  fill " + point.x + " 1 " + point.z + " to " + point.x + " " + (y - 1)
                        + " " + point.z + " minecraft:dirt");
                out.add("  set " + point.x + " " + y + " " + point.z
                        + " minecraft:chest[facing=north,type=single,waterlogged=false]");
                out.add("  block_entity " + point.x + " " + y + " " + point.z + " minecraft:chest {");
                out.add("    string CustomName \"Tier " + tier + " Cache\"");
                out.add("    item Items slot 0 minecraft:bread count " + (tier + 2));
                out.add("    item Items slot 1 minecraft:arrow count " + (tier * 4));
                out.add("  }");
            }
        }
    }

    private record ScatterChoice(String kind, String value, int weight) {
    }

    private interface RunConsumer {
        void accept(int x1, int x2, int z);
    }

    private interface ShapePredicate {
        boolean contains(int x, int z);
    }

    private interface CellRunConsumer {
        void accept(int x1, int x2, int y, int z);
    }

    private static void forMaskRuns(Plan plan, String mask, boolean outside, RunConsumer consumer) {
        for (int z = plan.area.minZ; z <= plan.area.maxZ; z++) {
            int start = Integer.MIN_VALUE;
            for (int x = plan.area.minX; x <= plan.area.maxX + 1; x++) {
                boolean inside = x <= plan.area.maxX && plan.inMask(mask, x, z);
                boolean selected = outside ? !inside : inside;
                if (selected && start == Integer.MIN_VALUE) {
                    start = x;
                } else if (!selected && start != Integer.MIN_VALUE) {
                    consumer.accept(start, x - 1, z);
                    start = Integer.MIN_VALUE;
                }
            }
        }
    }

    private static void forShapeRuns(Plan plan, Mask shape, RunConsumer consumer) {
        forShapeRuns(plan, (x, z) -> shape.contains(x, z, plan), consumer);
    }

    private static void forShapeRuns(Plan plan, ShapePredicate selected, RunConsumer consumer) {
        for (int z = plan.area.minZ; z <= plan.area.maxZ; z++) {
            int start = Integer.MIN_VALUE;
            for (int x = plan.area.minX; x <= plan.area.maxX + 1; x++) {
                boolean contains = x <= plan.area.maxX && selected.contains(x, z);
                if (contains && start == Integer.MIN_VALUE) {
                    start = x;
                } else if (!contains && start != Integer.MIN_VALUE) {
                    consumer.accept(start, x - 1, z);
                    start = Integer.MIN_VALUE;
                }
            }
        }
    }

    private static void forCellRuns(Set<Cell> cells, CellRunConsumer consumer) {
        Map<Integer, Map<Integer, List<Integer>>> byZAndY = new TreeMap<>();
        for (Cell cell : cells) {
            byZAndY.computeIfAbsent(cell.z, key -> new TreeMap<>())
                    .computeIfAbsent(cell.y, key -> new ArrayList<>())
                    .add(cell.x);
        }
        for (Map.Entry<Integer, Map<Integer, List<Integer>>> byZ : byZAndY.entrySet()) {
            int z = byZ.getKey();
            for (Map.Entry<Integer, List<Integer>> byY : byZ.getValue().entrySet()) {
                int y = byY.getKey();
                List<Integer> xs = byY.getValue().stream().distinct().sorted().toList();
                int start = xs.get(0);
                int previous = xs.get(0);
                for (int i = 1; i < xs.size(); i++) {
                    int x = xs.get(i);
                    if (x == previous + 1) {
                        previous = x;
                        continue;
                    }
                    consumer.accept(start, previous, y, z);
                    start = x;
                    previous = x;
                }
                consumer.accept(start, previous, y, z);
            }
        }
    }

    private static List<Point> candidates(Plan plan, String mask, int seed, int avoidRoutes, int avoidReservations) {
        List<Point> points = new ArrayList<>();
        for (int z = plan.area.minZ; z <= plan.area.maxZ; z += 4) {
            for (int x = plan.area.minX; x <= plan.area.maxX; x += 4) {
                if (!plan.inMask(mask, x, z)) {
                    continue;
                }
                if (nearRoute(plan, x, z, avoidRoutes) || nearReservation(plan, x, z, avoidReservations)) {
                    continue;
                }
                points.add(new Point(x, z));
            }
        }
        points.sort(Comparator.comparingInt(point -> mix(point.x, 0, point.z, seed)));
        return points;
    }

    private static boolean nearRoute(Plan plan, int x, int z, int distance) {
        if (distance <= 0) {
            return false;
        }
        int squared = distance * distance;
        for (Cell cell : plan.routeCells) {
            int dx = x - cell.x;
            int dz = z - cell.z;
            if (dx * dx + dz * dz <= squared) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearReservation(Plan plan, int x, int z, int distance) {
        for (Reservation reservation : plan.reservations) {
            for (int dz = -distance; dz <= distance; dz++) {
                for (int dx = -distance; dx <= distance; dx++) {
                    if (reservation.shape.contains(x + dx, z + dz, plan)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double distanceToSegment(int x, int z, Point a, Point b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0.0) {
            return Math.hypot(x - a.x, z - a.z);
        }
        double t = ((x - a.x) * dx + (z - a.z) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double px = a.x + t * dx;
        double pz = a.z + t * dz;
        return Math.hypot(x - px, z - pz);
    }

    private static String quote(String value) {
        return '"' + value + '"';
    }

    private static int mix(int x, int y, int z, int seed) {
        int hash = seed ^ 0x7f4a7c15;
        hash = hash * 0x45d9f3b + x;
        hash = hash * 0x45d9f3b + y;
        hash = hash * 0x45d9f3b + z;
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        hash ^= hash >>> 16;
        return hash;
    }

    private static double smoothNoise(int x, int z, int seed, int scale, int octaves) {
        double frequency = 1.0 / scale;
        double amplitude = 1.0;
        double total = 0.0;
        double weight = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            total += valueNoise(x * frequency, z * frequency, seed + octave * 1013) * amplitude;
            weight += amplitude;
            frequency *= 2.0;
            amplitude *= 0.5;
        }
        return total / weight;
    }

    private static double valueNoise(double x, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = fade(x - x0);
        double tz = fade(z - z0);
        double north = lerp(latticeNoise(x0, z0, seed), latticeNoise(x0 + 1, z0, seed), tx);
        double south = lerp(latticeNoise(x0, z0 + 1, seed), latticeNoise(x0 + 1, z0 + 1, seed), tx);
        return lerp(north, south, tz);
    }

    private static double latticeNoise(int x, int z, int seed) {
        return Integer.toUnsignedLong(mix(x, 0, z, seed)) / (double) 0x1_0000_0000L;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double amount) {
        return a + (b - a) * amount;
    }
}
