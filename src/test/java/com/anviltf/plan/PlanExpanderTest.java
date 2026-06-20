package com.anviltf.plan;

import com.anviltf.WorldDiagnostics;
import com.anviltf.WorldSpec;
import com.anviltf.dsl.DslParser;
import com.anviltf.render.RenderGrid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExpanderTest {
    @TempDir
    Path tempDir;

    @Test
    void expandsPlanBlocksDeterministicallyToDiagnosableAtf() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source.resolve("components"));
        Files.createDirectories(source.resolve("groups"));
        Files.writeString(source.resolve("world.atf"), """
                world "Plan Expansion" {
                  min_y 0
                  height 96
                  default_biome minecraft:plains
                  include "components/marker.atf"
                  include "groups/plan.atf"
                }
                """);
        Files.writeString(source.resolve("components/marker.atf"), """
                component "marker" {
                  bounds -1 0 -1 to 1 4 1
                  fill -1 0 -1 to 1 0 1 minecraft:stone_bricks
                  fill 0 1 0 to 0 3 0 minecraft:oak_fence
                  set 0 4 0 minecraft:lantern[hanging=false,waterlogged=false]
                }
                """);
        Files.writeString(source.resolve("groups/plan.atf"), """
                group "plan" {
                  origin 10 0 20
                  bounds 0 0 0 to 32 80 32
                  block_palette path_mix {
                    block minecraft:gravel weight 1
                  }
                  block_palette floor_mix {
                    block minecraft:smooth_stone weight 4
                    block minecraft:polished_andesite weight 1
                  }

                  plan {
                    area 0 0 32 32
                    mask island = radial 16 16 radius 15 noise 0.05 seed 1
                    mask bay = circle 30 30 radius 3
                    mask playable = island - bay
                    field ground = terrain playable base 16 amplitude 2 scale 12 octaves 2 seed 2
                    terrain playable field ground from 1 top minecraft:grass_block[snowy=false] under minecraft:dirt
                    water outside playable from 1 to 8 minecraft:water[level=0]
                    clear outside playable from 9 to 60
                    route "loop" width 3 palette path_mix conform ground seed 3 {
                      point 8 16
                      point 16 8
                      point 24 16
                    }
                    reserve "pad" circle 16 16 radius 4 conform ground floor minecraft:smooth_stone floor_palette floor_mix seed 11 edge minecraft:gold_block edge_width 1 clear_to 40 require_route 6
                    landmark "pad marker" component marker at 16 16 conform ground reserve "pad" radius 2 height 4 rotate 90
                    cluster "pad lamps" at reserve "pad" conform ground spacing 1 {
                      repeat component marker from -3 0 to 3 0 count 3 radius 1 height 4 rotate 0
                    }
                    scatter_in playable count 4 seed 4 avoid_routes 2 avoid_reservations 2 conform ground {
                      set minecraft:fern weight 1
                    }
                    loot playable tier 1 count 2 seed 5 avoid_routes 2 avoid_reservations 2 conform ground
                  }
                }
                """);

        Path outA = tempDir.resolve("out-a");
        Path outB = tempDir.resolve("out-b");
        PlanExpander.ExpansionReport reportA = new PlanExpander().expand(source.resolve("world.atf"), outA);
        PlanExpander.ExpansionReport reportB = new PlanExpander().expand(source.resolve("world.atf"), outB);

        String expandedA = Files.readString(outA.resolve("groups/plan.atf"));
        String expandedB = Files.readString(outB.resolve("groups/plan.atf"));
        assertEquals(expandedA, expandedB);
        assertEquals(1, reportA.planFiles());
        assertEquals(reportA.generatedCommands(), reportB.generatedCommands());
        assertFalse(expandedA.contains("plan {"));
        assertTrue(expandedA.contains("smooth_terrain"));
        assertTrue(expandedA.contains("fill_palette"));
        assertTrue(expandedA.contains("floor_mix seed"));
        assertTrue(expandedA.contains("minecraft:gold_block"));
        assertTrue(expandedA.contains("place marker at 16"));
        assertTrue(expandedA.contains("# cluster \"pad lamps\""));
        assertTrue(expandedA.contains("place marker at 13"));
        assertTrue(expandedA.contains("place marker at 16"));
        assertTrue(expandedA.contains("place marker at 19"));
        assertTrue(expandedA.contains("block_entity"));

        WorldSpec world = new DslParser().parse(outA.resolve("world.atf"));
        assertTrue(new WorldDiagnostics().diagnose(world).isEmpty());
        assertTrue(new PlanExpander().diagnose(source.resolve("world.atf")).isEmpty());

        RenderGrid overlay = new PlanExpander().overlay(source.resolve("world.atf"));
        assertEquals(33, overlay.rows());
        assertEquals(33, overlay.cols());
        assertEquals(20, overlay.rowTop());
        assertEquals(10, overlay.colLeft());
        assertTrue(hasCell(overlay, "plan:mask:playable"));
        assertTrue(hasCell(overlay, "plan:route"));
        assertTrue(hasCell(overlay, "plan:reservation"));
        assertTrue(hasCell(overlay, "plan:landmark"));
        assertTrue(hasCell(overlay, "plan:cluster"));
        assertTrue(hasCell(overlay, "plan:scatter"));
        assertTrue(hasCell(overlay, "plan:loot"));

        PlanExpander.PlanMetrics metrics = new PlanExpander().metrics(source.resolve("world.atf"));
        assertEquals(3, metrics.sourceFiles());
        assertEquals(3, metrics.atfFiles());
        assertEquals(1, metrics.componentFiles());
        assertEquals(1, metrics.planBlocks());
        assertTrue(metrics.planLines() > 0);
        assertEquals(reportA.generatedCommands(), metrics.generatedAtfLines());
        assertEquals(1089, metrics.planAreaCells());
        assertEquals(3, metrics.commandCounts().get("masks"));
        assertEquals(1, metrics.commandCounts().get("routes"));
        assertEquals(3, metrics.commandCounts().get("route_points"));
        assertEquals(1, metrics.commandCounts().get("reservations"));
        assertEquals(1, metrics.commandCounts().get("landmarks"));
        assertEquals(1, metrics.commandCounts().get("clusters"));
        assertEquals(3, metrics.commandCounts().get("cluster_members"));
        assertEquals(1, metrics.commandCounts().get("cluster_repeat_directives"));
        assertEquals(1, metrics.commandCounts().get("scatter_actions"));
        assertEquals(4, metrics.commandCounts().get("scatter_requested"));
        assertEquals(1, metrics.commandCounts().get("loot_actions"));
        assertEquals(2, metrics.commandCounts().get("loot_requested"));
        assertTrue(metrics.overlayCells().containsKey("plan:route"));
        assertTrue(metrics.overlayCells().containsKey("plan:loot"));

        List<PlanExpander.PlanIndexEntry> index = new PlanExpander().index(source.resolve("world.atf"));
        assertEquals(6, index.size());
        assertTrue(index.stream().anyMatch(entry ->
                entry.file().equals("groups/plan.atf")
                        && entry.groupName().equals("plan")
                        && entry.kind().equals("reserve")
                        && entry.name().equals("pad")
                        && entry.line() > 0));
        assertTrue(index.stream().anyMatch(entry ->
                entry.kind().equals("loot")
                        && entry.name().equals("loot playable tier 1")));
        assertTrue(index.stream().noneMatch(entry -> entry.summary().startsWith("component ")));

        Path additive = tempDir.resolve("source-additive");
        copyTree(source, additive);
        Path additivePlan = additive.resolve("groups/plan.atf");
        Files.writeString(additivePlan, Files.readString(additivePlan)
                .replace("    loot playable tier 1 count 2 seed 5 avoid_routes 2 avoid_reservations 2 conform ground",
                        "    loot playable tier 2 count 1 seed 6 avoid_routes 2 avoid_reservations 2 conform ground\n"
                                + "    loot playable tier 1 count 2 seed 5 avoid_routes 2 avoid_reservations 2 conform ground"));
        PlanExpander.PlanEditTrialReport additiveReport = new PlanExpander()
                .editTrial(source.resolve("world.atf"), additive.resolve("world.atf"), 2, 1);
        assertEquals(1, additiveReport.sourceChangedLines());
        assertTrue(additiveReport.expandedChangedLines() >= 1);
        assertEquals(0, additiveReport.planWarnings());
        assertEquals(0, additiveReport.coreWarnings());
        assertTrue(additiveReport.passed());
    }

    @Test
    void diagnosesPlanCompositionProblemsBeforeExpansion() throws Exception {
        Path source = tempDir.resolve("broken-source");
        Files.createDirectories(source.resolve("groups"));
        Files.writeString(source.resolve("world.atf"), """
                world "Broken Plan" {
                  min_y 0
                  height 96
                  include "groups/plan.atf"
                }
                """);
        Files.writeString(source.resolve("groups/plan.atf"), """
                group "plan" {
                  origin 0 0 0
                  bounds 0 0 0 to 32 80 32
                  block_palette path_mix {
                    block minecraft:gravel weight 1
                  }

                  plan {
                    area 0 0 32 32
                    mask island = circle 16 16 radius 15
                    field ground = terrain island base 16 amplitude 2 scale 12 octaves 2 seed 2
                    reserve "left" circle 16 16 radius 6 conform ground floor minecraft:smooth_stone clear_to 40
                    reserve "right" circle 18 16 radius 6 conform ground floor minecraft:smooth_stone clear_to 40
                    reserve "isolated" rect 4 24 8 28 conform ground floor minecraft:smooth_stone clear_to 40 require_route 4
                    landmark "bad tower" component marker at 30 30 conform ground reserve "left" radius 4 height 40 rotate 0
                    cluster "bad camp" at 16 16 conform ground reserve "left" spacing 6 {
                      component marker at 0 0 radius 3 height 4 rotate 0
                      component marker at 2 0 radius 3 height 4 rotate 0
                      component marker at 12 0 radius 3 height 4 rotate 0
                    }
                    route "bad route" width 3 palette path_mix conform ground seed 3 {
                      point 8 8
                      point 40 8
                    }
                    scatter_in island count 500 seed 4 avoid_routes 40 avoid_reservations 40 conform ground {
                      set minecraft:fern weight 1
                    }
                  }
                }
                """);

        List<PlanExpander.PlanWarning> warnings = new PlanExpander().diagnose(source.resolve("world.atf"));

        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("reservation-overlap")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("reservation-unreached")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("landmark-outside-reservation")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("cluster-outside-reservation")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("cluster-spacing-too-tight")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("route-outside-area")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("scatter-unsatisfied-count")));
    }

    @Test
    void diagnosesRoutesThatEnterHazardsWithoutCrossings() throws Exception {
        Path source = tempDir.resolve("hazard-source");
        Files.createDirectories(source.resolve("groups"));
        Files.writeString(source.resolve("world.atf"), """
                world "Hazard Plan" {
                  min_y 0
                  height 96
                  include "groups/plan.atf"
                }
                """);
        Files.writeString(source.resolve("groups/plan.atf"), hazardPlan(false));

        List<PlanExpander.PlanWarning> warnings = new PlanExpander().diagnose(source.resolve("world.atf"));

        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("route-missing-crossing")));

        Files.writeString(source.resolve("groups/plan.atf"), hazardPlan(true));
        assertTrue(new PlanExpander().diagnose(source.resolve("world.atf")).isEmpty());
    }

    @Test
    void styleSwapChangesMaterialsWithoutMovingGameplayAnchors() throws Exception {
        Path source = tempDir.resolve("style-source");
        Files.createDirectories(source.resolve("groups"));
        Files.writeString(source.resolve("world.atf"), """
                world "Style Plan" {
                  min_y 0
                  height 96
                  include "groups/plan.atf"
                }
                """);
        Files.writeString(source.resolve("groups/plan.atf"), stylePlan("soft"));

        Path outSoft = tempDir.resolve("out-soft");
        new PlanExpander().expand(source.resolve("world.atf"), outSoft);
        String soft = Files.readString(outSoft.resolve("groups/plan.atf"));

        Files.writeString(source.resolve("groups/plan.atf"), stylePlan("dark"));
        Path outDark = tempDir.resolve("out-dark");
        new PlanExpander().expand(source.resolve("world.atf"), outDark);
        String dark = Files.readString(outDark.resolve("groups/plan.atf"));

        assertTrue(soft.contains("minecraft:grass_block[snowy=false]"));
        assertTrue(soft.contains("soft_path"));
        assertTrue(dark.contains("minecraft:blackstone"));
        assertTrue(dark.contains("dark_path"));
        assertEquals(chestAnchors(soft), chestAnchors(dark));
        assertEquals(count(soft, "fill_palette"), count(dark, "fill_palette"));
    }

    @Test
    void largeDistrictSemanticMoveStaysSmallInPlanButLargeInExpandedAtf() throws Exception {
        Path source = Path.of("examples/large-map-validation");
        assertTrue(Files.exists(source.resolve("world.atf")), "large-map validation example is missing");

        PlanExpander.PlanGateReport gateReport = new PlanExpander().gates(source.resolve("world.atf"));
        assertEquals(0, gateReport.failed());
        assertEquals(4, gateReport.manual());
        assertTrue(gateReport.gates().stream()
                .anyMatch(gate -> gate.name().equals("minimum_authored_area") && gate.status().equals("pass")));
        assertTrue(gateReport.gates().stream()
                .anyMatch(gate -> gate.name().equals("theme_purity") && gate.status().equals("pass")));

        Path edited = tempDir.resolve("large-map-edit");
        copyTree(source, edited);

        Path originalCanal = source.resolve("groups/canal-448-plan.atf");
        Path editedCanal = edited.resolve("groups/canal-448-plan.atf");
        String editedPlan = Files.readString(editedCanal)
                .replace("reserve \"east keep island\" circle 338 224 radius 52",
                        "reserve \"east keep island\" circle 348 224 radius 52")
                .replace("landmark \"east keep core\" component style.keep_landmark at 338 224",
                        "landmark \"east keep core\" component style.keep_landmark at 348 224");
        Files.writeString(editedCanal, editedPlan);

        PlanExpander.PlanEditTrialReport editReport = new PlanExpander()
                .editTrial(source.resolve("world.atf"), edited.resolve("world.atf"));
        assertEquals(2, editReport.sourceChangedLines());
        assertTrue(editReport.expandedChangedLines() >= 300,
                "expected large expanded ATF churn, got " + editReport.expandedChangedLines());
        assertEquals(0, editReport.planWarnings());
        assertEquals(0, editReport.coreWarnings());
        assertTrue(editReport.passed());
    }

    @Test
    void largeMapBenchmarkMeetsPerGroupTargetClassThresholds() throws Exception {
        Path source = Path.of("examples/large-map-validation");
        assertTrue(Files.exists(source.resolve("world.atf")), "large-map validation example is missing");

        PlanExpander expander = new PlanExpander();
        assertTrue(expander.diagnose(source.resolve("world.atf")).isEmpty());

        List<PlanExpander.PlanOutline> outlines = expander.outline(source.resolve("world.atf"));
        assertEquals(4, outlines.size());
        for (PlanExpander.PlanOutline outline : outlines) {
            int width = outline.maxX() - outline.minX() + 1;
            int depth = outline.maxZ() - outline.minZ() + 1;
            int side = Math.min(width, depth);
            if (side >= 512) {
                assertTargetClassGroup(outline, 5, 2, 6, 6, 5, 8, 120, 35);
            } else if (side >= 448) {
                assertTargetClassGroup(outline, 5, 4, 6, 6, 5, 10, 180, 30);
            } else if (side >= 384) {
                assertTargetClassGroup(outline, 4, 1, 5, 5, 4, 5, 100, 25);
            } else {
                throw new AssertionError("unexpected non-target-class benchmark group: " + outline.groupName());
            }
        }
    }

    private static String hazardPlan(boolean crossing) {
        return """
                group "plan" {
                  origin 0 0 0
                  bounds 0 0 0 to 48 80 48
                  block_palette path_mix {
                    block minecraft:gravel weight 1
                  }

                  plan {
                    area 0 0 48 48
                    mask playable = rect 0 0 48 48
                    mask canal = rect 20 0 28 48
                    field ground = terrain playable base 16 amplitude 1 scale 12 octaves 1 seed 2
                    terrain playable field ground from 1 top minecraft:grass_block[snowy=false] under minecraft:dirt
                    water canal from 1 to 15 minecraft:water[level=0]
                    route "canal route" width 3 palette path_mix conform ground seed 3 {
                      point 8 24
                      point 40 24
                """ + (crossing ? "      crossing 24 24 radius 6\n" : "") + """
                    }
                  }
                }
                """;
    }

    private static String stylePlan(String style) {
        return """
                group "plan" {
                  origin 0 0 0
                  bounds 0 0 0 to 40 80 40
                  block_palette soft_path {
                    block minecraft:gravel weight 1
                  }
                  block_palette dark_path {
                    block minecraft:blackstone weight 1
                  }

                  plan {
                    area 0 0 40 40
                    style soft {
                      top minecraft:grass_block[snowy=false]
                      under minecraft:dirt
                      path soft_path
                      floor minecraft:smooth_stone
                      foliage minecraft:fern
                    }
                    style dark {
                      top minecraft:blackstone
                      under minecraft:basalt
                      path dark_path
                      floor minecraft:polished_blackstone
                      foliage minecraft:dead_bush
                    }
                """ + "    use_style " + style + "\n" + """
                    mask playable = rect 0 0 40 40
                    field ground = terrain playable base 16 amplitude 2 scale 12 octaves 2 seed 2
                    terrain playable field ground from 1 top style.top under style.under
                    route "loop" width 3 palette style.path conform ground seed 3 {
                      point 8 20
                      point 32 20
                    }
                    reserve "pad" circle 20 20 radius 4 conform ground floor style.floor clear_to 40
                    scatter_in playable count 4 seed 4 avoid_routes 2 avoid_reservations 2 conform ground {
                      set style.foliage weight 1
                    }
                    loot playable tier 1 count 3 seed 5 avoid_routes 2 avoid_reservations 2 conform ground
                  }
                }
                """;
    }

    private static List<String> chestAnchors(String text) {
        Pattern pattern = Pattern.compile("set (-?\\d+ -?\\d+ -?\\d+) minecraft:chest");
        return pattern.matcher(text).results()
                .map(match -> match.group(1))
                .toList();
    }

    private static long count(String text, String needle) {
        return Pattern.compile(Pattern.quote(needle)).matcher(text).results().count();
    }

    private static void assertTargetClassGroup(PlanExpander.PlanOutline outline,
                                               int routes,
                                               int crossings,
                                               int reservations,
                                               int landmarks,
                                               int distinctLandmarkComponents,
                                               int clusters,
                                               int repeatedDetail,
                                               int loot) {
        int crossingCount = outline.routes().stream().mapToInt(PlanExpander.RouteOutline::crossings).sum();
        int clusterMembers = outline.clusters().stream().mapToInt(PlanExpander.ClusterOutline::members).sum();
        int distinctComponents = (int) outline.landmarks().stream()
                .map(PlanExpander.LandmarkOutline::component)
                .distinct()
                .count();
        assertTrue(outline.routes().size() >= routes, outline.groupName() + " routes");
        assertTrue(crossingCount >= crossings, outline.groupName() + " crossings");
        assertTrue(outline.reservations().size() >= reservations, outline.groupName() + " reservations");
        assertTrue(outline.landmarks().size() >= landmarks, outline.groupName() + " landmarks");
        assertTrue(distinctComponents >= distinctLandmarkComponents,
                outline.groupName() + " distinct landmark components");
        assertTrue(outline.clusters().size() >= clusters, outline.groupName() + " clusters");
        assertTrue(clusterMembers + outline.scatterRequested() >= repeatedDetail,
                outline.groupName() + " repeated detail");
        assertTrue(outline.lootRequested() >= loot, outline.groupName() + " loot");
    }

    private static int alignedLineChanges(Path left, Path right) throws Exception {
        List<String> leftLines = Files.readAllLines(left);
        List<String> rightLines = Files.readAllLines(right);
        int changes = 0;
        int max = Math.max(leftLines.size(), rightLines.size());
        for (int i = 0; i < max; i++) {
            String leftLine = i < leftLines.size() ? leftLines.get(i) : null;
            String rightLine = i < rightLines.size() ? rightLines.get(i) : null;
            if (!java.util.Objects.equals(leftLine, rightLine)) {
                changes++;
            }
        }
        return changes;
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean hasCell(RenderGrid grid, String name) {
        for (String[] row : grid.cells()) {
            for (String cell : row) {
                if (name.equals(cell)) {
                    return true;
                }
            }
        }
        return false;
    }
}
