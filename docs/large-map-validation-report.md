# Large Map Validation Report

This report records the first executable validation slice for the large-map
authoring format described in `docs/large-map-authoring-format.md`.

## Candidate

`generic-plan-layer`

The prototype adds experimental `plan { ... }` blocks that are expanded by:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose <input.atf>
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand <input.atf> <output-dir>
```

`plan-diagnose` checks high-level composition constraints before expansion. The
expanded output is ordinary ATF v1. The current parser, diagnostics, render
commands, Anvil writer, and reference validator then run unchanged.

## Benchmark

`examples/large-map-validation`

This is a controlled large-map benchmark, not yet an imported or
hand-reconstructed target-class Mineplex map. It now uses four ordinary included
group files:

- `groups/kikoshi-512-plan.atf`, a 512x512 island composition.
- `groups/ridge-384-plan.atf`, a neighboring 384x384 ridge/chasm composition.
- `groups/canal-448-plan.atf`, a 448x448 canal/castle composition.
- `groups/polish-384-plan.atf`, a 384x384 dense-polish stress composition.

The benchmark covers:

- radial island mask,
- bay subtraction mask,
- composed chasm/ravine masks,
- terrain field,
- water/lava hazards and air carving outside playable masks,
- nineteen named routes,
- twenty-four named reservations,
- twenty-four reserved landmarks,
- thirty-eight repeated-detail clusters,
- four themeable style families expressed through generic style keys,
- deterministic route-aware scatter,
- deterministic loot anchors,
- ordinary components for local details.

## Target Coverage

The benchmark is not an imported target map, so target coverage is measured as
generic capability coverage rather than visual equivalence:

| Target pressure | Source evidence | Benchmark/gate evidence | Status |
| --- | --- | --- | --- |
| 512x512 island scale | Public Kikoshi page describes a 512x512 Mineplex Survival Games island. | `kikoshi-512-plan.atf` is 513x513; dense `plan-report` passes `minimum_authored_area`, `expanded_ratio`, diagnostics, reference validation, and import round-trip. | Covered as representative scale. |
| Themed island water/harbor detail | Kikoshi page describes an Oriental map and tags include water, ship, lantern, dojo. | Theme is expressed through style/component keys; bay, harbor pad, crossings, landmarks, clusters, scatter, and loot stay generic. | Covered structurally; visual polish remains incomplete. |
| Castle-town sectors and wall | Public Riverton page describes a riverside town, snowy mountains, four sectors, central castle, and fortified south wall. | Canal/ridge groups cover waterways, bridges, keep/court pads, town/garden/dock pads, retaining/wall clusters, and per-group target-class gates. | Covered structurally; not a direct reconstruction. |
| Survival Games gameplay spread | Mineplex wiki describes 24+ players, scouting large maps, scattered/tiered chests, supply drops, and border pressure. | Benchmark has 19 routes and 128 loot anchors; dense report passes route, loot, target-class, render, and reference gates. | Loot/scouting covered; supply-drop and timed border behavior remain outside ATF v1 scope. |
| Akushi/Aztec-like hazards and identity | User named Akushi Island and Aztec Island; no public source page was found in this pass. | Generic hazard masks, water/lava, route crossings, terrain, landmarks, and style libraries cover the required format shape without adding theme directives. | Needs sourced reference or lawful artifact/manual reconstruction. |
| LLM edit ergonomics | Goal requires source that LLMs can author/edit. | Workbench/index/snippet/recipe commands plus move/additive edit trials and 22-gate dense report. | Strong controlled evidence; independent model-authored trials still needed. |

## Validation Run

Commands run:

```text
mvn -q -Dmaven.repo.local=.m2/repository -DskipTests package
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/expanded-large-map-validation-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/expanded-large-map-validation-b
diff -ru target/expanded-large-map-validation-a target/expanded-large-map-validation-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/expanded-large-map-validation-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/expanded-large-map-validation-a/world.atf --out target/large-map-validation-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render slice target/expanded-large-map-validation-a/world.atf x 0 --out target/large-map-validation-slice.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/expanded-large-map-validation-a/world.atf target/playtest-worlds/large-map-validation --validate
```

Observed results:

```text
expanded_plan_files: 1
copied_files: 6
generated_atf_lines: 6419
plan_diagnose: pass
deterministic_expand: pass
diagnose: pass
render_map: pass
render_slice: pass
generate_validate: pass
region_files: 4
chunks: 1089
authored_block_checks: 17990247
authored_biome_checks: 160034
block_entity_tags: 35
```

The map render shows a coherent large island footprint with an open bay, a
central plaza, route loop, branch routes, reservations, scatter details, and
loot anchors. The slice render confirms substrate, water, and supported pads.

## What This Validates

- A generic plan layer can reduce a 512x512 composition skeleton to reviewable
  intent: named masks, fields, routes, reservations, scatter, and loot.
- The lowered output can remain ordinary ATF v1 and use the existing toolchain.
- Expansion can be deterministic.
- Plan-level diagnostics can catch composition problems before ATF expansion.
- Existing diagnostics are still useful once the expander emits support under
  generated routes, reservations, scatter, and loot.
- The source layout stays within `world.atf`, `meta.atf`, `groups/`, and
  `components/`.

## What This Does Not Validate Yet

- Human-quality landmark architecture comparable to Kikoshi Islands, Riverton
  Castle, Akushi Island, or Aztec Island.
- Plan diagnostics for gameplay pacing and visual-region balance.
- Style variants beyond simple palettes/components.
- Import summarization from detailed handmade maps into plan-layer concepts.
- Direct import or authored reconstruction of an actual target-class Mineplex
  map. The public Kikoshi and Riverton reference pages describe the target
  scale and features, but do not expose obvious world downloads in their public
  page text.

## Edit Trial 1: Move Harbor Route

Trial:

```text
move the harbor approach route and preserve clean diagnostics, deterministic
expansion, render coherence, and generated-world validation.
```

Only these source anchors changed in a temporary copy under `target/`:

```text
point 252 338 -> point 286 342
point 260 410 -> point 304 420
```

Commands run:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose target/large-map-validation-edit/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand target/large-map-validation-edit/world.atf target/expanded-large-map-validation-edit-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand target/large-map-validation-edit/world.atf target/expanded-large-map-validation-edit-b
diff -ru target/expanded-large-map-validation-edit-a target/expanded-large-map-validation-edit-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/expanded-large-map-validation-edit-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/expanded-large-map-validation-edit-a/world.atf --out target/large-map-validation-edit-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/expanded-large-map-validation-edit-a/world.atf target/playtest-worlds/large-map-validation-edit --validate
```

Observed results:

```text
plan_diagnose: pass
generated_atf_lines: 6367
deterministic_expand: pass
diagnose: pass
render_map: pass
generate_validate: pass
region_files: 4
chunks: 1089
authored_block_checks: 17988907
authored_biome_checks: 160034
block_entity_tags: 35
```

The edit moved the harbor branch visibly in the map render without requiring
manual coordinate changes to the surrounding reservations, scatter, or loot.

## Hazard Trial 2: Bay Crossings

Trial:

```text
add hazard-aware route diagnostics so routes that enter water/lava without a
declared crossing fail plan diagnostics, while intentional crossings still pass
the full validation loop.
```

The benchmark now declares crossings where the main loop and harbor route enter
the bay:

```text
crossing 260 420 radius 84
crossing 260 410 radius 56
```

Positive validation:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/expanded-large-map-validation-hazard-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/expanded-large-map-validation-hazard-b
diff -ru target/expanded-large-map-validation-hazard-a target/expanded-large-map-validation-hazard-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/expanded-large-map-validation-hazard-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/expanded-large-map-validation-hazard-a/world.atf --out target/large-map-validation-hazard-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/expanded-large-map-validation-hazard-a/world.atf target/playtest-worlds/large-map-validation-hazard --validate
```

Observed positive results:

```text
plan_diagnose: pass
generated_atf_lines: 6439
deterministic_expand: pass
diagnose: pass
render_map: pass
generate_validate: pass
region_files: 4
chunks: 1089
authored_block_checks: 17990247
authored_biome_checks: 160034
block_entity_tags: 35
```

Negative validation:

```text
remove all crossing declarations from a temporary copy under target/
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose target/large-map-validation-no-crossing/world.atf
```

Observed negative result:

```text
warning route-missing-crossing groups/kikoshi-512-plan.atf 262 407: route "harbor approach" enters minecraft:water[level=0] without a declared crossing
warning route-missing-crossing groups/kikoshi-512-plan.atf 202 412: route "main loop" enters minecraft:water[level=0] without a declared crossing
Plan diagnostics found 2 warning(s)
```

This validates a generic hazard/crossing concept that can apply to bays, rivers,
moats, lava channels, canals, and other route interruptions without adding
domain-specific map concepts.

## Style Trial 3: Volcanic Swap

Trial:

```text
replace the visual style of a region while keeping route, reservation, scatter,
and loot anchors stable.
```

The validation map now defines generic style keys:

```text
top
under
water
path
spawn_floor
harbor_floor
temple_floor
forest_floor
boardwalk_floor
garden_floor
landmark
spawn_landmark
harbor_landmark
temple_landmark
forest_landmark
boardwalk_landmark
garden_landmark
light
foliage
accent
```

The temporary style-swap copy changed only:

```text
use_style kikoshi -> use_style volcanic
```

Commands run:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose target/large-map-validation-style-volcanic/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand target/large-map-validation-style-volcanic/world.atf target/expanded-large-map-validation-style-volcanic-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand target/large-map-validation-style-volcanic/world.atf target/expanded-large-map-validation-style-volcanic-b
diff -ru target/expanded-large-map-validation-style-volcanic-a target/expanded-large-map-validation-style-volcanic-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/expanded-large-map-validation-style-volcanic-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/expanded-large-map-validation-style-volcanic-a/world.atf --out target/large-map-validation-style-volcanic-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/expanded-large-map-validation-style-volcanic-a/world.atf target/playtest-worlds/large-map-validation-style-volcanic --validate
```

Observed results:

```text
plan_diagnose: pass
generated_atf_lines: 6439
deterministic_expand: pass
diagnose: pass
render_map: pass
generate_validate: pass
chest_anchors_equal: true
route_runs_equal: true
route_runs: 1670
chest_anchors: 35
volcanic_has_blackstone: true
volcanic_has_lava: true
region_files: 4
chunks: 1089
authored_block_checks: 18188566
authored_biome_checks: 160034
block_entity_tags: 35
```

This validates that theme/material changes can be made without rewriting the
underlying gameplay composition. That matters for large-map families: a
Kikoshi-like, Aztec-like, or castle-town map can share route/reservation/loot
structure while changing block palettes and components.

## Landmark Reachability Trial

The benchmark now marks each major reserved pad with a generic `require_route`
distance. This validates that large landmarks can carry composition constraints
without introducing theme-specific concepts such as temples, castles, harbors, or
town districts into the core language.

Positive benchmark:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
```

Result:

```text
Plan diagnostics OK: no warnings
```

Negative trial:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose target/validation-trials/unreached-reservation/world.atf
```

Result:

```text
warning reservation-unreached groups/kikoshi-512-plan.atf 500 20: reservation "isolated overlook" has no route within 6 block(s)
Plan diagnostics found 1 warning(s)
```

The updated benchmark was also expanded twice and compared byte-for-byte:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/reachability-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/reachability-expanded-b
diff -ru target/validation-trials/reachability-expanded-a target/validation-trials/reachability-expanded-b
```

`diff` produced no output. The expanded ATF passed core diagnostics and
generated-world reference validation:

```text
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 17990247 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

## Plan Overlay Trial

The plan layer now has a direct visual/debug render before expansion:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-plan-overlay.png --max 1024
```

Result:

```text
Wrote plan overlay to /Users/ben/code/anvil-tf/target/large-map-validation-plan-overlay.png (513x513px, scale=1, 513x513 cells)
Legend (color RRGGBB -> block):
  C75CFF plan:cluster
  F4F1DE plan:crossing
  3A78C2 plan:hazard
  7C4DFF plan:landmark
  FFD84D plan:loot
  6F8F72 plan:mask:playable
  D64F7A plan:reservation
  E07A2F plan:route
  5FAF5A plan:scatter
```

The overlay shows the island silhouette, bay hazard, route loop and branches,
major reserved landmark pads, cluster footprints, crossing spans, scatter
points, and loot anchors without expanding to ordinary ATF first. This closes
the previous inspection gap between text diagnostics and rendered block output.

## Landmark Architecture Trial

The benchmark now places six reusable landmark components through generic plan
directives:

```text
landmark "east spire" component style.temple_landmark at 400 168 conform ground reserve "east temple pad" radius 16 height 28 rotate 270
```

The directive does not encode temple, castle, harbor, or island semantics. It
only connects a reusable component to an anchor, a terrain field, a reservation,
and footprint/height metadata. `plan-diagnose` checks that the footprint stays
inside the reservation and that the reservation clears enough vertical space.

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-landmark-overlay.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/landmark-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/landmark-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/landmark-expanded-a/world.atf --out target/large-map-validation-landmarks-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render slice target/validation-trials/landmark-expanded-a/world.atf x 144 --out target/large-map-validation-east-spire-slice.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/landmark-expanded-a/world.atf target/validation-trials/landmark-world --validate
```

Observed results:

```text
Plan diagnostics OK: no warnings
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 18000577 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

The plan overlay shows landmark footprints inside their reserved pads. The
surface render shows visible landmark materials, and the east-spire slice shows a
real vertical silhouette above the terrain, not just a top-down marker.

## Multi-Component Cluster Trial

The benchmark now places several coordinated component groups through generic
cluster directives:

```text
cluster "spawn market ring" at reserve "spawn plaza" conform ground spacing 5 {
  component style.market_piece at -26 -10 radius 4 height 4 rotate 90
  component style.market_piece at 24 -12 radius 4 height 4 rotate 270
  component style.arch_piece at -18 22 radius 5 height 8 rotate 0
  component style.arch_piece at 18 22 radius 5 height 8 rotate 180
}
```

The directive does not know what a market, dock, camp, garden, ruin, or village
is. It only knows a reservation, an anchor, local component offsets, and generic
radius/height metadata. `plan-diagnose` checks that member footprints stay
inside the reservation, that there is enough vertical clearance, and that member
radii respect the cluster spacing. The `at reserve "..."` form anchors the
cluster to the reservation center, so a zone can move by editing the reservation
instead of rewriting every component coordinate.

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-cluster-overlay.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/cluster-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/cluster-expanded-b
diff -ru target/validation-trials/cluster-expanded-a target/validation-trials/cluster-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/cluster-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/cluster-expanded-a/world.atf --out target/large-map-validation-clusters-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/cluster-expanded-a/world.atf target/validation-trials/cluster-world --validate
```

Observed results:

```text
Plan diagnostics OK: no warnings
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 18002111 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

`diff` produced no output. The cluster overlay shows multi-component groups
around major landmark anchors while keeping routes and reservations visible. The
generated map render shows additional component materials such as barrels, hay,
and wool around the pads.

## Route-Aware Cluster Edit Trial

This trial moved the north forest camp by editing the route endpoint,
reservation center/radius, and landmark anchor while leaving the cluster member
offsets unchanged:

```diff
-      point 86 112
+      point 104 128
-    reserve "north forest camp" circle 76 98 radius 30 conform ground floor style.forest_floor clear_to 94 require_route 10
+    reserve "north forest camp" circle 96 116 radius 32 conform ground floor style.forest_floor clear_to 94 require_route 10
-    landmark "north lookout" component style.forest_landmark at 76 98 conform ground reserve "north forest camp" radius 10 height 17 rotate 180
+    landmark "north lookout" component style.forest_landmark at 96 116 conform ground reserve "north forest camp" radius 10 height 17 rotate 180
```

The cluster remained:

```text
cluster "forest camp ring" at reserve "north forest camp" conform ground spacing 5 {
  component style.camp_piece at -15 6 radius 5 height 5 rotate 90
  component style.camp_piece at 13 -8 radius 5 height 5 rotate 270
  component style.market_piece at 4 17 radius 4 height 4 rotate 180
}
```

Expanded placement anchors moved from:

```text
place camp-tent at 61 69 104 rotate 90
place camp-tent at 89 69 90 rotate 270
place market-stall at 80 70 115 rotate 180
```

to:

```text
place camp-tent at 81 68 122 rotate 90
place camp-tent at 109 68 108 rotate 270
place market-stall at 100 68 133 rotate 180
```

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose target/validation-trials/cluster-move-edit/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render target/validation-trials/cluster-move-edit/world.atf --out target/large-map-validation-cluster-move-overlay.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand target/validation-trials/cluster-move-edit/world.atf target/validation-trials/cluster-move-expanded-floor
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/cluster-move-expanded-floor/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/cluster-move-expanded-floor/world.atf --out target/large-map-validation-cluster-move-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/cluster-move-expanded-floor/world.atf target/validation-trials/cluster-move-world --validate
```

Observed results:

```text
Plan diagnostics OK: no warnings
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 17981568 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

During this trial, core diagnostics initially caught an unsupported moved
structure when cluster members conformed to individual terrain heights. The
format was adjusted so landmarks and clusters with reservations place on the
prepared reservation floor height instead. That makes moved zones behave like
authored pads rather than scattered objects on uneven terrain.

## Repeated Cluster Member Trial

The benchmark now uses repeated cluster members for architectural rhythm:

```text
cluster "harbor cargo line" at reserve "harbor pad" conform ground spacing 4 {
  repeat component style.crate_piece from -30 -12 to 30 -12 count 4 radius 4 height 4 rotate 0
}
```

This replaces several hand-written component coordinates with one local line
definition. The same mechanism is used for spawn arches and garden arches. It is
generic enough for walls, docks, ribs, stalls, posts, ruins, and facade rhythms
without adding those domain concepts to the plan language.

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-repeat-cluster-overlay.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/repeat-cluster-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/repeat-cluster-expanded-b
diff -ru target/validation-trials/repeat-cluster-expanded-a target/validation-trials/repeat-cluster-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/repeat-cluster-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/repeat-cluster-expanded-a/world.atf --out target/large-map-validation-repeat-clusters-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/repeat-cluster-expanded-a/world.atf target/validation-trials/repeat-cluster-world --validate
```

Observed results:

```text
Plan diagnostics OK: no warnings
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 18002715 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

`diff` produced no output. The overlay shows clearer component runs around
spawn, harbor, and garden pads. The generated map render remains clean and
validates exactly with the reference reader.

## Richer Zone Library Trial

The benchmark now includes a small reusable zone-detail library:

```text
components/low-wall-segment.atf
components/ruin-column.atf
components/dock-post.atf
components/planter-box.atf
```

These components are used through existing generic cluster/repeat directives,
not through new city-, dock-, ruin-, or garden-specific language. The east pad
gets a low ruin perimeter, the harbor and boardwalk pads get post runs, and the
garden pad gets repeated planter rows:

```text
cluster "east ruin perimeter" at reserve "east temple pad" conform ground spacing 2 {
  repeat component style.wall_piece from -24 -22 to 24 -22 count 5 radius 4 height 3 rotate 0
  repeat component style.wall_piece from -24 22 to 24 22 count 5 radius 4 height 3 rotate 0
  repeat component style.column_piece from -29 -14 to -29 14 count 3 radius 2 height 7 rotate 0
  repeat component style.column_piece from 29 -14 to 29 14 count 3 radius 2 height 7 rotate 0
}
```

Plan diagnostics initially caught overly tight ruin-corner spacing. Moving the
column runs inward from z +/-18 to z +/-14 made the perimeter pass while keeping
the visual rhythm.

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-rich-zone-overlay.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/rich-zone-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/rich-zone-expanded-b
diff -ru target/validation-trials/rich-zone-expanded-a target/validation-trials/rich-zone-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/rich-zone-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/rich-zone-expanded-a/world.atf --out target/large-map-validation-rich-zones-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/rich-zone-expanded-a/world.atf target/validation-trials/rich-zone-world --validate
```

Observed results:

```text
Plan diagnostics OK: no warnings
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 18003322 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

`diff` produced no output. The overlay shows denser but still readable zone
rhythm on east, harbor, garden, and boardwalk pads. The generated map render
adds visible materials such as rooted dirt, azalea, flowering azalea, and more
stone detail while preserving exact reference validation.

## Facade Library Trial

The benchmark now includes reusable building/frontage components:

```text
components/facade-shop-narrow.atf
components/facade-house-wide.atf
components/facade-tower-cap.atf
components/facade-roof-rib.atf
```

These components are arranged through normal clusters, preserving the generic
plan layer:

```text
cluster "spawn frontage" at reserve "spawn plaza" conform ground spacing 1 {
  component style.facade_shop at -18 -28 radius 6 height 10 rotate 0
  component style.facade_house at 0 -30 radius 8 height 12 rotate 0
  component style.facade_shop at 18 -28 radius 6 height 10 rotate 0
}
```

The first pass was too tightly spaced, and `plan-diagnose` reported
`cluster-spacing-too-tight`. Reducing the explicit frontage spacing and removing
an overlapping roof-rib repeat made the facade cluster pass while retaining the
intended row shape.

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-facade-overlay.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/facade-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/facade-expanded-b
diff -ru target/validation-trials/facade-expanded-a target/validation-trials/facade-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/facade-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/facade-expanded-a/world.atf --out target/large-map-validation-facades-map.png --max 1024
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/facade-expanded-a/world.atf target/validation-trials/facade-world --validate
```

Observed results:

```text
Plan diagnostics OK: no warnings
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 18008140 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s)
```

`diff` produced no output. The overlay shows facade rows on spawn, harbor, and
east pads. The generated map render shows denser frontage blocks without
breaking route readability or exact reference validation.

## Quantitative Ergonomics Trial

The compiler now has a repeatable source/plan metrics command:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-metrics examples/large-map-validation/world.atf
```

Observed result for the single-group facade-rich validation map before the
multi-group scale trial:

```text
source_files: 24
atf_files: 24
component_files: 21
source_lines: 505
nonblank_source_lines: 492
component_lines: 247
nonblank_component_lines: 247
plan_blocks: 1
plan_lines: 159
plan_area_cells: 263169
generated_atf_lines: 6516
generated_atf_lines_per_plan_line: 40.98
plan_commands:
  biome_actions: 2
  cluster_members: 56
  cluster_repeat_directives: 12
  clusters: 9
  crossings: 2
  fields: 1
  fill_actions: 4
  hazards: 2
  landmarks: 6
  loot_actions: 2
  loot_requested: 35
  masks: 5
  reservations: 6
  route_points: 20
  routes: 5
  scatter_actions: 1
  scatter_requested: 120
  styles: 2
  terrain_actions: 1
visible_overlay_cells:
  plan:cluster: 3110
  plan:crossing: 1489
  plan:hazard: 90849
  plan:landmark: 2360
  plan:loot: 35
  plan:mask:playable: 143337
  plan:reservation: 15354
  plan:route: 6515
  plan:scatter: 120
```

Validation commands:

```text
mvn -q -Dmaven.repo.local=.m2/repository -Dtest=PlanExpanderTest test
mvn -q -Dmaven.repo.local=.m2/repository test
mvn -q -Dmaven.repo.local=.m2/repository -DskipTests package
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/metrics-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/metrics-expanded-b
diff -ru target/validation-trials/metrics-expanded-a target/validation-trials/metrics-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/metrics-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/metrics-expanded-a/world.atf target/validation-trials/metrics-world --validate
```

Observed validation result:

```text
Plan diagnostics OK: no warnings
Expanded 1 plan file(s) to target/validation-trials/metrics-expanded-a as 6516 generated ATF line(s); copied 24 file(s)
Expanded 1 plan file(s) to target/validation-trials/metrics-expanded-b as 6516 generated ATF line(s); copied 24 file(s)
deterministic_expand: pass
Diagnostics OK: no warnings
Reference validation OK: 4 region file(s), 1089 chunk(s), 8712 block-state sample(s), 4356 biome sample(s), 18008140 authored block check(s), 160034 authored biome check(s), 35 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

This gives the first hard ergonomics signal: 159 high-level plan lines control a
512x512 authored region, expand to 6,516 ordinary ATF lines, and still validate
exactly through the existing Anvil writer/reference reader path. The component
library remains reviewable separately at 247 nonblank lines across 21 ordinary
components, so architectural detail is not hidden inside opaque generation code.

## Multi-Group Scale Trial

The benchmark now adds a second large region as a normal included group:

```text
world "Large Map Validation" {
  include "groups/kikoshi-512-plan.atf"
  include "groups/ridge-384-plan.atf"
}
```

The new group uses the same generic plan vocabulary rather than adding
castle-, ridge-, bridge-, or district-specific core concepts. It adds a
neighboring 384x384 ridge/chasm layout with composed masks:

```text
mask ravine_north = circle 192 72 radius 36
mask ravine_mid = rect 174 82 210 302
mask ravine_south = circle 192 318 radius 36
mask ravine_upper = ravine_north + ravine_mid
mask ravine = ravine_upper + ravine_south
mask playable = plateau - ravine
```

During authoring, `plan-diagnose` caught three useful mistakes:

```text
cluster-outside-reservation: north wall and west frontage footprints exceeded prepared pads
route-missing-crossing: dock descent entered water without a declared crossing
route-missing-crossing: composed ravine boundaries widened beyond two crossing declarations
```

The fix was to enlarge the relevant reservations and explicitly widen the
intended crossing radii. No diagnostic was weakened.

Metrics after the second group:

```text
source_files: 25
atf_files: 25
component_files: 21
source_lines: 690
nonblank_source_lines: 663
component_lines: 247
nonblank_component_lines: 247
plan_blocks: 2
plan_lines: 299
plan_area_cells: 411394
generated_atf_lines: 11680
generated_atf_lines_per_plan_line: 39.06
plan_commands:
  biome_actions: 4
  cluster_members: 94
  cluster_repeat_directives: 21
  clusters: 14
  crossings: 6
  fields: 2
  fill_actions: 8
  hazards: 4
  landmarks: 11
  loot_actions: 4
  loot_requested: 60
  masks: 14
  reservations: 11
  route_points: 38
  routes: 9
  scatter_actions: 2
  scatter_requested: 200
  styles: 4
  terrain_actions: 2
visible_overlay_cells:
  plan:cluster: 4949
  plan:crossing: 3023
  plan:hazard: 149228
  plan:landmark: 4358
  plan:loot: 60
  plan:mask:playable: 210342
  plan:reservation: 29562
  plan:route: 9672
  plan:scatter: 200
```

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-metrics examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-multigroup-overlay.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/multigroup-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/multigroup-expanded-b
diff -ru target/validation-trials/multigroup-expanded-a target/validation-trials/multigroup-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/multigroup-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/multigroup-expanded-a/world.atf --out target/large-map-validation-multigroup-map.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/multigroup-expanded-a/world.atf target/validation-trials/multigroup-world --validate
```

Observed result:

```text
Plan diagnostics OK: no warnings
Expanded 2 plan file(s) to target/validation-trials/multigroup-expanded-a as 11680 generated ATF line(s); copied 25 file(s)
Expanded 2 plan file(s) to target/validation-trials/multigroup-expanded-b as 11680 generated ATF line(s); copied 25 file(s)
deterministic_expand: pass
Diagnostics OK: no warnings
render_map: pass
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29519653 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

This validates that the candidate format is not limited to one monolithic plan
file. Multiple ordinary groups can each carry high-level composition intent,
expand deterministically, render together, and validate through the same Anvil
writer/reference-reader path.

## Multi-Group Edit Ergonomics Trial

Trial:

```text
Make a small non-trivial edit across both large groups:
- switch the second group's visual style from ridge to basalt,
- reroute the second group's dock descent,
- nudge the first island's west garden pad and landmark.
```

The trial source was copied to `target/validation-trials/llm-edit-source` and
patched there. The source diff stayed small: two group files and six changed
source lines.

```diff
--- examples/large-map-validation/groups/kikoshi-512-plan.atf
+++ target/validation-trials/llm-edit-source/groups/kikoshi-512-plan.atf
@@
-    reserve "west garden pad" circle 120 300 radius 26 conform ground floor style.garden_floor clear_to 98 require_route 12
+    reserve "west garden pad" circle 132 292 radius 26 conform ground floor style.garden_floor clear_to 98 require_route 12
@@
-    landmark "west obelisk" component style.garden_landmark at 120 300 conform ground reserve "west garden pad" radius 12 height 20 rotate 0
+    landmark "west obelisk" component style.garden_landmark at 132 292 conform ground reserve "west garden pad" radius 12 height 20 rotate 0

--- examples/large-map-validation/groups/ridge-384-plan.atf
+++ target/validation-trials/llm-edit-source/groups/ridge-384-plan.atf
@@
-    use_style ridge
+    use_style basalt
@@
-      point 80 248
-      crossing 54 305 radius 36
-      point 48 314
+      point 92 258
+      crossing 64 316 radius 40
+      point 62 338
```

Metrics after the edit:

```text
source_files: 25
atf_files: 25
component_files: 21
source_lines: 690
nonblank_source_lines: 663
component_lines: 247
nonblank_component_lines: 247
plan_blocks: 2
plan_lines: 299
plan_area_cells: 411394
generated_atf_lines: 11824
generated_atf_lines_per_plan_line: 39.55
plan_commands:
  biome_actions: 4
  cluster_members: 94
  cluster_repeat_directives: 21
  clusters: 14
  crossings: 6
  fields: 2
  fill_actions: 8
  hazards: 4
  landmarks: 11
  loot_actions: 4
  loot_requested: 60
  masks: 14
  reservations: 11
  route_points: 38
  routes: 9
  scatter_actions: 2
  scatter_requested: 200
  styles: 4
  terrain_actions: 2
visible_overlay_cells:
  plan:cluster: 4949
  plan:crossing: 3098
  plan:hazard: 149228
  plan:landmark: 4382
  plan:loot: 60
  plan:mask:playable: 210280
  plan:reservation: 29524
  plan:route: 9673
  plan:scatter: 200
```

Validation commands:

```text
diff -ru examples/large-map-validation target/validation-trials/llm-edit-source
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose target/validation-trials/llm-edit-source/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-metrics target/validation-trials/llm-edit-source/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render target/validation-trials/llm-edit-source/world.atf --out target/large-map-validation-llm-edit-overlay.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand target/validation-trials/llm-edit-source/world.atf target/validation-trials/llm-edit-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand target/validation-trials/llm-edit-source/world.atf target/validation-trials/llm-edit-expanded-b
diff -ru target/validation-trials/llm-edit-expanded-a target/validation-trials/llm-edit-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/llm-edit-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/llm-edit-expanded-a/world.atf --out target/large-map-validation-llm-edit-map.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/llm-edit-expanded-a/world.atf target/validation-trials/llm-edit-world --validate
```

Observed result:

```text
source_diff: two files, six changed source lines
Plan diagnostics OK: no warnings
Expanded 2 plan file(s) to target/validation-trials/llm-edit-expanded-a as 11824 generated ATF line(s); copied 25 file(s)
Expanded 2 plan file(s) to target/validation-trials/llm-edit-expanded-b as 11824 generated ATF line(s); copied 25 file(s)
deterministic_expand: pass
Diagnostics OK: no warnings
render_map: pass
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29527799 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

The rendered edit shows the second group switching to basalt materials and the
dock route moving, while the first group's west garden pad shifts without
requiring manual edits to generated route, terrain, scatter, or loot output.
This is the strongest evidence so far that the format can support large
LLM-authored map edits with reviewable diffs.

## Vertical Architecture Density Trial

Trial:

```text
Add castle/temple-like vertical detail to the ridge group without introducing
new domain-specific plan commands or opaque generated coordinates.
```

The benchmark now includes three ordinary reusable components:

```text
components/ridge-keep-core.atf
components/ridge-stair-tower.atf
components/ridge-gallery-span.atf
```

The plan layer places them through normal style keys, `landmark`, and `cluster`
commands:

```text
keep_piece ridge-keep-core
stair_tower_piece ridge-stair-tower
gallery_piece ridge-gallery-span

landmark "east keep" component style.keep_piece at 274 190 conform ground reserve "east court" radius 18 height 34 rotate 90

cluster "east gate ribs" at reserve "east court" conform ground spacing 3 {
  component style.stair_tower_piece at -28 -16 radius 7 height 28 rotate 270
  component style.stair_tower_piece at 28 -16 radius 7 height 28 rotate 90
  component style.gallery_piece at 0 -24 radius 14 height 12 rotate 0
  repeat component style.rib_piece from -18 28 to 18 28 count 3 radius 3 height 8 rotate 0
}
```

This keeps the core language generic. The architectural meaning lives in
reviewable component files and style bindings.

One useful issue surfaced during validation: the first stair-tower component
used wall-attached ladders, and core diagnostics reported unsupported attachments
after rotated placement:

```text
unsupported-wall-attachment: rotated stair-tower ladders had no authored support
```

The fix was to replace that shaft detail with freestanding fence blocks in the
component. This is exactly the kind of visual/geometry bug that source review
alone is likely to miss.

Metrics after adding vertical architecture:

```text
source_files: 28
atf_files: 28
component_files: 24
source_lines: 817
nonblank_source_lines: 772
component_lines: 365
nonblank_component_lines: 347
plan_blocks: 2
plan_lines: 305
plan_area_cells: 411394
generated_atf_lines: 11680
generated_atf_lines_per_plan_line: 38.30
plan_commands:
  biome_actions: 4
  cluster_members: 94
  cluster_repeat_directives: 21
  clusters: 14
  crossings: 6
  fields: 2
  fill_actions: 8
  hazards: 4
  landmarks: 11
  loot_actions: 4
  loot_requested: 60
  masks: 14
  reservations: 11
  route_points: 38
  routes: 9
  scatter_actions: 2
  scatter_requested: 200
  styles: 4
  terrain_actions: 2
visible_overlay_cells:
  plan:cluster: 5437
  plan:crossing: 3023
  plan:hazard: 149228
  plan:landmark: 4674
  plan:loot: 60
  plan:mask:playable: 209014
  plan:reservation: 30103
  plan:route: 9655
  plan:scatter: 200
```

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-metrics examples/large-map-validation/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-vertical-overlay.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/vertical-expanded-a
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/validation-trials/vertical-expanded-b
diff -ru target/validation-trials/vertical-expanded-a target/validation-trials/vertical-expanded-b
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/vertical-expanded-a/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/validation-trials/vertical-expanded-a/world.atf --out target/large-map-validation-vertical-map.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render slice target/validation-trials/vertical-expanded-a/world.atf x 594 --out target/large-map-validation-vertical-slice-x594.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render slice target/validation-trials/vertical-expanded-a/world.atf z -2 --out target/large-map-validation-vertical-slice-z-2.png --max 1200
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/vertical-expanded-a/world.atf target/validation-trials/vertical-world --validate
```

Observed result:

```text
Plan diagnostics OK: no warnings
Expanded 2 plan file(s) to target/validation-trials/vertical-expanded-a as 11680 generated ATF line(s); copied 28 file(s)
Expanded 2 plan file(s) to target/validation-trials/vertical-expanded-b as 11680 generated ATF line(s); copied 28 file(s)
deterministic_expand: pass
Diagnostics OK: no warnings
render_map: pass
render_slice_x594: pass
render_slice_z-2: pass
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29549597 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

This validates an important split of responsibilities: the plan layer controls
large-scale placement and clearances, while ordinary components carry
multi-level architectural detail in reviewable source files. The generated ATF
line count did not grow because component definitions remain source library
content rather than being inlined into plan expansion.

## Import/Summarization Pressure Trial

Trial:

```text
Import the generated vertical large-map world back into reviewable ATF, regenerate
Anvil from that imported source, and compare the original and regenerated worlds.
```

This checks whether generated and imported content can use the same source tree
shape:

```text
world.atf
meta.atf
groups/imported-X-Z.atf
components/component-000N.atf
```

The first exact reviewable import worked, but exposed a clear reviewability
limit:

```text
Imported 1714 chunk(s) ... as 716322 block command(s), 251189 biome command(s), 60 block entity command(s), 0 entity command(s), 20 component(s), 112 component placement(s)
Component analysis: 150 candidate placement(s), 20 promoted component(s), 112 promoted placement(s)
imported_atf_files: 1736
imported_group_files: 1714
imported_component_files: 20
imported_atf_lines: 1048216
imported_group_lines: 1046175
imported_component_lines: 279
```

Exactness passed:

```text
Round-trip comparison OK: 1714 chunk(s), 70205440 exact block check(s), 1096960 exact biome check(s), 60 block entity anchor check(s), 0 entity anchor check(s)
```

The importer then gained a narrow fallback compression: consecutive identical
solid 16x16 chunk layers are now emitted as one vertical `fill` instead of one
fill per Y layer. For example:

```text
fill 0 1 0 to 15 83 15 minecraft:dirt
```

instead of 83 separate `fill ... minecraft:dirt` lines.

Compressed import result:

```text
Imported 1714 chunk(s) to target/validation-trials/imported-vertical-reviewable-compressed as 627749 block command(s), 251189 biome command(s), 60 block entity command(s), 0 entity command(s), 20 component(s), 112 component placement(s)
Component analysis: 150 candidate placement(s), 20 promoted component(s), 112 promoted placement(s)
Import notes: non-block save data skipped in reviewable layout: data
compressed_imported_atf_files: 1736
compressed_imported_atf_lines: 959643
compressed_imported_group_lines: 957602
```

Validation commands:

```text
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar import target/validation-trials/vertical-world target/validation-trials/imported-vertical-reviewable-compressed --layout reviewable
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/validation-trials/imported-vertical-reviewable-compressed/world.atf
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/validation-trials/imported-vertical-reviewable-compressed/world.atf target/validation-trials/imported-vertical-world-compressed --validate
/opt/homebrew/opt/openjdk/bin/java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar compare target/validation-trials/vertical-world target/validation-trials/imported-vertical-world-compressed
```

Observed result:

```text
Diagnostics OK: no warnings
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29250065 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 1714 chunk(s), 70205440 exact block check(s), 1096960 exact biome check(s), 60 block entity anchor check(s), 0 entity anchor check(s)
```

This proves the same source shape can represent imported and generated worlds,
and it proves exact large-map round-trip through reviewable ATF. It does not yet
prove that imports are ergonomic enough for MindPlex-scale handmade maps. The
current chunk fallback is exact and ordinary, but still too large for human or
LLM review at nearly one million ATF lines.

### Boundary-Touching Component Promotion

The importer originally rejected connected component candidates that touched a
chunk boundary. That was safe, but too conservative: repeated facade, tower,
wall, and trim fragments often lie near or on chunk edges. The candidate filter
now allows small bounded boundary-touching connected volumes while still
rejecting large terrain masses.

Focused test added:

```text
reviewableImportPromotesRepeatedBoundaryTouchingVolumesToComponents
```

The test builds three repeated 2x2x2 copper volumes at local chunk edge
coordinates, imports with reviewable layout, promotes one ordinary component,
places it three times, and exact-compares the regenerated world.

Large vertical-world import after boundary promotion:

```text
Imported 1714 chunk(s) to target/validation-trials/imported-vertical-reviewable-boundary as 627482 block command(s), 251189 biome command(s), 60 block entity command(s), 0 entity command(s), 32 component(s), 183 component placement(s)
Component analysis: 395 candidate placement(s), 32 promoted component(s), 183 promoted placement(s)
boundary_imported_atf_files: 1748
boundary_imported_atf_lines: 959188
boundary_imported_group_lines: 956985
boundary_imported_component_lines: 429
```

Validation:

```text
Diagnostics OK: no warnings
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29250065 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 1714 chunk(s), 70205440 exact block check(s), 1096960 exact biome check(s), 60 block entity anchor check(s), 0 entity anchor check(s)
```

This is a real but small source-size improvement. It expands exact component
candidate discovery around chunk boundaries, but it is not yet full semantic
cross-chunk extraction. The next step should combine adjacent per-chunk
fragments into larger repeated world-space volumes before promotion.

### Cross-Chunk Connected Volume Promotion

The importer now scans a two-chunk east/west window for small connected
components that genuinely straddle a chunk boundary. Promoted placements are
emitted once in the origin chunk group, while neighboring chunk groups skip the
component-covered blocks. Promotion selection now prefers higher source-savings
candidates and rejects overlapping promoted placements.

Focused test added:

```text
reviewableImportPromotesRepeatedCrossChunkVolumesToComponents
```

The test builds three repeated 4x2x2 copper volumes crossing chunk boundaries,
imports with reviewable layout, promotes one ordinary component, suppresses the
inline copper in adjacent chunks, regenerates, and exact-compares the world.

Large vertical-world import after cross-chunk promotion:

```text
Imported 1714 chunk(s) to target/validation-trials/imported-vertical-reviewable-crosschunk as 626689 block command(s), 251189 biome command(s), 60 block entity command(s), 0 entity command(s), 36 component(s), 211 component placement(s)
Component analysis: 625 candidate placement(s), 36 promoted component(s), 211 promoted placement(s)
crosschunk_imported_atf_files: 1752
crosschunk_imported_atf_lines: 958190
crosschunk_imported_group_lines: 955785
crosschunk_imported_component_lines: 627
```

Validation:

```text
Diagnostics OK: no warnings
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29250065 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 1714 chunk(s), 70205440 exact block check(s), 1096960 exact biome check(s), 60 block entity anchor check(s), 0 entity anchor check(s)
```

This proves cross-chunk component promotion can be exact and share the same
ordinary `components/` + `groups/` source shape. The source reduction is still
small because the current detector is deliberately conservative and finds small
props/fragments, not whole facades, buildings, or terrain motifs.

### Reviewable Biome Fill Compression

The next largest obvious source of import noise was biome data. The reviewable
importer previously emitted one `biome` command for every non-default 4x4x4
biome sample. On the vertical-world trial that meant 251,189 biome commands,
which made source review worse without adding useful local structure.

The importer now greedily merges identical non-default biome samples inside each
chunk into ordinary `fill_biome` cuboids. This is still generic ATF, not an
import-only dialect: the emitted source uses the same command an author would
write by hand.

Focused test updated:

```text
reviewableImportWritesChunkIncludesAndRoundTrips
```

The test now imports a multi-sample `fill_biome` region, verifies the reviewable
group contains a compressed `fill_biome`, regenerates, and exact-compares
biomes.

Large vertical-world import after biome compression:

```text
Imported 1714 chunk(s) to target/validation-trials/imported-vertical-reviewable-biome as 626689 block command(s), 1464 biome command(s), 60 block entity command(s), 0 entity command(s), 36 component(s), 211 component placement(s)
Component analysis: 625 candidate placement(s), 36 promoted component(s), 211 promoted placement(s)
biome_imported_atf_files: 1752
biome_imported_atf_lines: 708465
biome_imported_group_lines: 706060
biome_imported_component_lines: 627
```

Validation:

```text
Diagnostics OK: no warnings
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29250065 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 1714 chunk(s), 70205440 exact block check(s), 1096960 exact biome check(s), 60 block entity anchor check(s), 0 entity anchor check(s)
```

This reduced the compressed cross-chunk import from 958,190 ATF lines to
708,465 ATF lines while preserving exact block, biome, block-entity, and entity
anchor comparison. The win is substantial, but it also clarifies the remaining
problem: imported block geometry still dominates the review surface.

### Reviewable Block Cuboid Compression

The reviewable importer now greedily merges identical non-air block cells into
exact `fill` cuboids within each chunk. Component-covered cells and air cells
stop cuboid expansion, so promoted components are not overwritten and holes are
not accidentally filled. This keeps the import fallback in the generic ATF core:
large terrain masses, floors, walls, and solid architectural regions become
ordinary `fill` commands instead of per-layer row runs.

Focused test added:

```text
reviewableImportCompressesPartialBlockCuboids
```

The test imports a partial 8x5x6 granite volume plus an isolated block, verifies
the reviewable group contains one 3D `fill` and one `set`, regenerates, and
exact-compares the result.

Large vertical-world import after block cuboid compression:

```text
Imported 1714 chunk(s) to target/validation-trials/imported-vertical-reviewable-cuboid as 37215 block command(s), 1464 biome command(s), 60 block entity command(s), 0 entity command(s), 36 component(s), 211 component placement(s)
Component analysis: 625 candidate placement(s), 36 promoted component(s), 211 promoted placement(s)
cuboid_imported_atf_files: 1752
cuboid_imported_atf_lines: 50345
cuboid_imported_group_lines: 47940
cuboid_imported_component_lines: 627
```

Validation:

```text
Diagnostics OK: no warnings
Reference validation OK: 6 region file(s), 1714 chunk(s), 13712 block-state sample(s), 6856 biome sample(s), 29250065 authored block check(s), 251189 authored biome check(s), 60 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 1714 chunk(s), 70205440 exact block check(s), 1096960 exact biome check(s), 60 block entity anchor check(s), 0 entity anchor check(s)
```

This is the strongest import-side validation so far. Relative to the
biome-compressed import, block commands dropped from 626,689 to 37,215 and total
ATF lines dropped from 708,465 to 50,345 while preserving exact comparison. The
ordinary `groups/` + `components/` fallback is now plausible as a reviewable
source representation for large imported maps. It still does not prove that an
LLM can comfortably author a MinePlex-scale map by hand; that proof depends on
the higher-level authoring layer and reusable component libraries.

### Third Large Authoring Trial: Canal District

To test authoring ergonomics rather than import compression, a third large plan
group was added:

```text
examples/large-map-validation/groups/canal-448-plan.atf
```

It adds a 448x448 canal/castle district with a cross-shaped waterway, bridge
plaza, east keep island, west market island, north village, south docks,
northwest garden, routes, crossings, reservations, landmarks, repeated
architecture clusters, scatter, and loot. The group is 214 source lines. Its
actual layout is mostly carried by 44 high-level plan directives, not raw block
commands.

During authoring, `plan-diagnose` caught useful mistakes:

```text
cluster-outside-reservation: east keep ribs exceeded the keep pad
cluster-spacing-too-tight: bridge arches/lights were too dense
route-missing-crossing: routes entered water without declared crossings
```

The source was adjusted by enlarging/tuning the keep pad, simplifying bridge
detail, and adding explicit crossing coverage. Final diagnostics:

```text
Plan diagnostics OK: no warnings
Diagnostics OK: no warnings
```

Final metrics for the full three-plan validation source:

```text
source_files: 29
atf_files: 29
component_files: 24
source_lines: 1032
nonblank_source_lines: 972
component_lines: 365
nonblank_component_lines: 347
plan_blocks: 3
plan_lines: 471
plan_area_cells: 612995
generated_atf_lines: 19109
generated_atf_lines_per_plan_line: 40.57
routes: 14
reservations: 17
landmarks: 17
clusters: 21
cluster_members: 183
scatter_requested: 310
loot_requested: 94
```

Expansion was deterministic:

```text
Expanded 3 plan file(s) to target/validation-trials/canal-refined-expanded-a as 19109 generated ATF line(s); copied 29 file(s)
Expanded 3 plan file(s) to target/validation-trials/canal-refined-expanded-b as 19109 generated ATF line(s); copied 29 file(s)
diff -qr target/validation-trials/canal-refined-expanded-a target/validation-trials/canal-refined-expanded-b
```

The expanded ordinary ATF source has 19,593 total ATF lines and 19,177 group
lines. Reference validation passed:

```text
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39857709 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

Rendered artifacts:

```text
target/large-map-validation-canal-refined-overlay.png
target/large-map-validation-canal-refined-map.png
target/large-map-validation-canal-refined-slice-z544.png
```

The render confirms that the format supports quick composition-level iteration:
the new district has a readable canal cross, bridge network, distinct landmark
pads, dock/village/market zones, and loot/scatter coverage. It also exposes a
remaining quality gap: large landmark components such as the keep still need
richer roof and silhouette detail. The plan layer can place and validate them
ergonomically, but MinePlex-level visual quality depends on a stronger reusable
component library, not only on the map-level format.

### Component Fidelity Pass

The component library was then improved without changing the map-level plan
structure. Four high-impact reusable components gained stronger top-down and
vertical silhouettes:

```text
components/ridge-keep-core.atf
components/facade-house-wide.atf
components/facade-shop-narrow.atf
components/facade-tower-cap.atf
```

The pass added stepped roofs, visible roof-center accents, tower crowns, more
distinct facade awnings, and beacon/lantern markers. The associated plan
metadata was updated for the new component heights, but the authored plan
layout stayed the same.

Metrics after the component pass:

```text
source_files: 29
atf_files: 29
component_files: 24
source_lines: 1060
nonblank_source_lines: 1000
component_lines: 393
nonblank_component_lines: 375
plan_blocks: 3
plan_lines: 471
plan_area_cells: 612995
generated_atf_lines: 19109
generated_atf_lines_per_plan_line: 40.57
```

This is a useful ergonomics signal: richer visual components added 28 component
source lines, but did not increase plan lines or generated plan output. The map
source still describes layout intent through the same 471 plan lines.

Validation:

```text
Plan diagnostics OK: no warnings
Expanded 3 plan file(s) to target/validation-trials/fidelity-expanded-a as 19109 generated ATF line(s); copied 29 file(s)
Expanded 3 plan file(s) to target/validation-trials/fidelity-expanded-b as 19109 generated ATF line(s); copied 29 file(s)
diff -qr target/validation-trials/fidelity-expanded-a target/validation-trials/fidelity-expanded-b
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39862885 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

Rendered artifacts:

```text
target/large-map-validation-fidelity-overlay.png
target/large-map-validation-fidelity-map.png
target/large-map-validation-fidelity-slice-z544.png
```

The render shows clearer roof signatures and stronger landmark marks for the
keep, towers, houses, and shops. It also keeps the caveat visible: broad
reservation floors still read as large simple circles/rectangles in top-down
views. Component fidelity helps, but plaza/floor patterning and richer repeated
district-surface motifs are still needed for the map to feel closer to
Akushi/Kikoshi-level polish.

Finally, the generated fidelity world was imported back to reviewable ATF and
exact-compared:

```text
Imported 2555 chunk(s) to target/validation-trials/imported-fidelity-reviewable as 49369 block command(s), 2165 biome command(s), 94 block entity command(s), 0 entity command(s), 64 component(s), 511 component placement(s)
Component analysis: 1118 candidate placement(s), 64 promoted component(s), 511 promoted placement(s)
imported_fidelity_atf_lines: 69136
imported_fidelity_group_lines: 65408
imported_fidelity_component_lines: 1081
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39296793 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 2555 chunk(s), 104652800 exact block check(s), 1635200 exact biome check(s), 94 block entity anchor check(s), 0 entity anchor check(s)
```

This closes the largest loop so far:

```text
plan source -> expanded ordinary ATF -> generated Anvil -> reviewable import -> regenerated Anvil -> exact compare
```

The evidence now supports the current format direction more strongly: generic
ATF groups/components are viable as an exact source-of-truth layer, the `plan`
prototype is compact enough for large authored layouts, and visual fidelity can
improve through reusable components without bloating the map-level source.

### Patterned Reservation Floors

The plan layer now supports an optional generic reserve-floor palette:

```text
reserve "name" circle ... floor minecraft:smooth_stone floor_palette plaza_mix seed 701 clear_to 96
reserve "name" rect   ... floor minecraft:stone_bricks floor_palette bridge_mix seed 941 clear_to 104
```

This keeps `floor` as the fallback material and uses existing ATF
`block_palette` / `fill_palette` mechanics to render the visible reservation
surface as deterministic row runs inside the reserved shape. It is generic: the
core still sees ordinary fills and palettes, and the plan layer still describes
a prepared surface rather than city-specific plaza concepts.

Focused test updated:

```text
expandsPlanBlocksDeterministicallyToDiagnosableAtf
```

The test now defines a `floor_mix` palette, uses `floor_palette floor_mix seed
11` on a circular reservation, verifies deterministic expansion contains the
resolved palette fill, parses the expanded ATF, and checks diagnostics.

The large validation source now uses floor palettes for the broad pads in the
Kikoshi island, ridge, and canal groups. Metrics:

```text
source_files: 29
atf_files: 29
component_files: 24
source_lines: 1126
nonblank_source_lines: 1066
component_lines: 393
nonblank_component_lines: 375
plan_blocks: 3
plan_lines: 495
plan_area_cells: 612995
generated_atf_lines: 20007
generated_atf_lines_per_plan_line: 40.42
```

Compared with the component-fidelity pass, patterned floors added 24 plan lines
and 898 generated ATF lines. The authoring cost is small relative to the map
scale, and the generated output stays deterministic:

```text
Plan diagnostics OK: no warnings
Expanded 3 plan file(s) to target/validation-trials/surface-expanded-a as 20007 generated ATF line(s); copied 29 file(s)
Expanded 3 plan file(s) to target/validation-trials/surface-expanded-b as 20007 generated ATF line(s); copied 29 file(s)
diff -qr target/validation-trials/surface-expanded-a target/validation-trials/surface-expanded-b
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39860006 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

Rendered artifacts:

```text
target/large-map-validation-surface-overlay.png
target/large-map-validation-surface-map.png
target/large-map-validation-surface-top64.png
```

The render shows clear improvement on rectangular bridge/dock/plaza surfaces and
subtler improvement on stone-family circular pads. This is enough to validate
the generic mechanism, but it also proves that random material variation alone
is not a complete visual solution. Large circles still benefit from deliberate
edges, rings, inlays, or repeated border motifs.

The generated surface-pattern world was imported back to reviewable ATF and
exact-compared:

```text
Imported 2555 chunk(s) to target/validation-trials/imported-surface-reviewable as 76572 block command(s), 2165 biome command(s), 94 block entity command(s), 0 entity command(s), 64 component(s), 511 component placement(s)
Component analysis: 1118 candidate placement(s), 64 promoted component(s), 511 promoted placement(s)
imported_surface_atf_lines: 96339
imported_surface_group_lines: 92611
imported_surface_component_lines: 1081
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39296793 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 2555 chunk(s), 104652800 exact block check(s), 1635200 exact biome check(s), 94 block entity anchor check(s), 0 entity anchor check(s)
```

The import-size increase is expected: patterned floors introduce real block
variation, so exact reviewable import has more geometry to encode. The
authoring-side result is still favorable: 495 plan lines define three large,
detailed regions, and exact re-import remains under 100k ATF lines for 2,555
chunks.

### Reservation Edge And Ring Motifs

The plan layer now also supports optional generic reservation edges:

```text
reserve "name" circle ... floor minecraft:smooth_stone floor_palette plaza_mix seed 701 edge minecraft:stone_bricks edge_width 3 clear_to 96
reserve "name" rect   ... floor minecraft:stone_bricks floor_palette bridge_mix seed 941 edge minecraft:dark_prismarine edge_width 3 clear_to 104
```

`edge` writes a border/ring over the prepared floor. `edge_palette` is also
supported for palette-driven borders, and `edge_seed` can override the default
edge palette seed. This remains generic: a reservation is still just a prepared
surface, and the expanded output is ordinary `fill` / `fill_palette` ATF.

The focused plan expansion test now verifies both a deterministic floor palette
and an edge block on a circular reservation.

The large validation source applies edges to broad Kikoshi, ridge, and canal
reservations. Metrics:

```text
source_files: 29
atf_files: 29
component_files: 24
source_lines: 1150
nonblank_source_lines: 1090
component_lines: 393
nonblank_component_lines: 375
plan_blocks: 3
plan_lines: 519
plan_area_cells: 612995
generated_atf_lines: 22075
generated_atf_lines_per_plan_line: 42.53
```

Compared with patterned floors, edges added 24 plan lines and 2,068 generated
ATF lines. Validation:

```text
Plan diagnostics OK: no warnings
Expanded 3 plan file(s) to target/validation-trials/edge-expanded-a as 22075 generated ATF line(s); copied 29 file(s)
Expanded 3 plan file(s) to target/validation-trials/edge-expanded-b as 22075 generated ATF line(s); copied 29 file(s)
diff -qr target/validation-trials/edge-expanded-a target/validation-trials/edge-expanded-b
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39860006 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

Rendered artifacts:

```text
target/large-map-validation-edge-overlay.png
target/large-map-validation-edge-map.png
target/large-map-validation-edge-top64.png
```

The render shows that the mechanism works, especially on high-contrast bridge
and dock materials. Some stone-family circular pads still read subtly because
the renderer colors several stone variants similarly. The authoring conclusion
is useful: the format now has a generic edge/ring primitive, but style authors
must choose materials with enough visual contrast, and richer trim components
are still valuable.

The generated edge/ring world was imported back to reviewable ATF and
exact-compared:

```text
Imported 2555 chunk(s) to target/validation-trials/imported-edge-reviewable as 72785 block command(s), 2165 biome command(s), 94 block entity command(s), 0 entity command(s), 64 component(s), 511 component placement(s)
Component analysis: 1118 candidate placement(s), 64 promoted component(s), 511 promoted placement(s)
imported_edge_atf_lines: 92552
imported_edge_group_lines: 88824
imported_edge_component_lines: 1081
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39296793 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 2555 chunk(s), 104652800 exact block check(s), 1635200 exact biome check(s), 94 block entity anchor check(s), 0 entity anchor check(s)
```

The edge version imports slightly smaller than the palette-only version because
solid border runs replace some noisy palette cells. More importantly, the
complete authored-source loop remains exact:

```text
plan source -> expanded ordinary ATF -> generated Anvil -> reviewable import -> regenerated Anvil -> exact compare
```

### Reusable Trim And Support Clusters

The next validation step used only existing generic plan/component mechanisms:
ordinary `cluster` blocks with repeated reusable components around large
reservations. No core format or plan syntax was added.

The canal group gained four trim/support clusters:

```text
bridge corner anchors
east keep retaining trim
west market curb rhythm
south dock retaining edge
```

These reuse existing components such as `low-wall-segment`, `ruin-column`,
`dock-post`, `planter-box`, and `lantern-post`. During authoring,
`plan-diagnose` caught an overextended market trim, and the offsets were pulled
inside the reservation. Final diagnostics passed.

Metrics:

```text
source_files: 29
atf_files: 29
component_files: 24
source_lines: 1177
nonblank_source_lines: 1117
component_lines: 393
nonblank_component_lines: 375
plan_blocks: 3
plan_lines: 542
plan_area_cells: 612995
generated_atf_lines: 22140
generated_atf_lines_per_plan_line: 40.85
clusters: 25
cluster_members: 244
cluster_repeat_directives: 48
```

Relative to the edge/ring pass, the trim clusters added 23 plan lines and only
65 generated ATF lines. This is strong ergonomics evidence: repeated trim detail
is cheap at the authored source level because components carry the structure.

Validation:

```text
Plan diagnostics OK: no warnings
Expanded 3 plan file(s) to target/validation-trials/trim-expanded-a as 22140 generated ATF line(s); copied 29 file(s)
Expanded 3 plan file(s) to target/validation-trials/trim-expanded-b as 22140 generated ATF line(s); copied 29 file(s)
diff -qr target/validation-trials/trim-expanded-a target/validation-trials/trim-expanded-b
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39861151 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

Rendered artifacts:

```text
target/large-map-validation-trim-overlay.png
target/large-map-validation-trim-map.png
target/large-map-validation-trim-slice-z544.png
```

The render shows clearer pad identity in the canal district: market and keep
circles now have visible trim rhythm, the dock edge has more structural detail,
and bridge corners read as anchored. The improvement is most visible where trim
materials contrast with the floor. Subtle stone-on-stone areas still need better
style choices.

The generated trim world was imported back to reviewable ATF and exact-compared:

```text
Imported 2555 chunk(s) to target/validation-trials/imported-trim-reviewable as 72453 block command(s), 2165 biome command(s), 94 block entity command(s), 0 entity command(s), 64 component(s), 562 component placement(s)
Component analysis: 1208 candidate placement(s), 64 promoted component(s), 562 promoted placement(s)
imported_trim_atf_lines: 92241
imported_trim_group_lines: 88624
imported_trim_component_lines: 970
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39297893 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 2555 chunk(s), 104652800 exact block check(s), 1635200 exact biome check(s), 94 block entity anchor check(s), 0 entity anchor check(s)
```

The importer found more component placements after trim was added, which is
exactly the desired source-shape behavior: repeated authored motifs become
reviewable components rather than raw block noise.

### Shore/Retaining Transition Components

The next slice added a dedicated reusable component for pad-to-water and
pad-to-terrain transitions:

```text
components/retaining-bank-segment.atf
```

The canal styles now map `retaining_piece` to that component, and the canal plan
uses it in ordinary reservation-anchored clusters around the central bridge,
east keep island, west market island, and south docks. No terrain-specific or
city-specific core syntax was added; these are still just `component`, `style`,
and `cluster` authoring patterns.

The first placement pass intentionally went through diagnostics. It produced
108 warnings: conservative footprints exceeded reservations, and a few retaining
segments overlapped existing wall trim. The fix was source-level and readable:
pull the offsets inward, let retaining pieces replace duplicated outer wall runs,
and move dock walls inward so the edge reads as retaining bank plus inner curb.
Final plan diagnostics passed:

```text
Plan diagnostics OK: no warnings
```

Metrics:

```text
source_files: 30
atf_files: 30
component_files: 25
source_lines: 1208
nonblank_source_lines: 1145
component_lines: 410
nonblank_component_lines: 389
plan_blocks: 3
plan_lines: 554
plan_area_cells: 612995
generated_atf_lines: 22188
generated_atf_lines_per_plan_line: 40.05
clusters: 26
cluster_members: 291
cluster_repeat_directives: 57
```

Relative to the previous trim pass, this added one component file and 12 plan
lines while increasing generated ATF by only 48 lines. The authored source stayed
small because the visual complexity lives in a reusable component and repeated
cluster placements.

Validation:

```text
Expanded 3 plan file(s) to target/validation-trials/transition-expanded-a as 22188 generated ATF line(s); copied 30 file(s)
Expanded 3 plan file(s) to target/validation-trials/transition-expanded-b as 22188 generated ATF line(s); copied 30 file(s)
diff -qr target/validation-trials/transition-expanded-a target/validation-trials/transition-expanded-b
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39863201 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

Rendered artifacts:

```text
target/large-map-validation-transition-overlay.png
target/large-map-validation-transition-map.png
target/large-map-validation-transition-slice-z544.png
```

The top-down render shows the canal district still reads as broad composition:
central plaza, island pads, dock/keep edges, and routes remain legible. The new
retaining accents are visible around water-facing pads without covering the
routes. The vertical slice shows solid ground and supported edges rather than
floating trim. This is an improvement over plain rings, but the renderer still
makes several stone-family blocks visually similar; material contrast remains a
style-library concern.

The generated transition world was imported back to reviewable ATF and
exact-compared:

```text
Imported 2555 chunk(s) to target/validation-trials/imported-transition-reviewable as 72872 block command(s), 2165 biome command(s), 94 block entity command(s), 0 entity command(s), 67 component(s), 580 component placement(s)
Component analysis: 1298 candidate placement(s), 67 promoted component(s), 580 promoted placement(s)
imported_transition_atf_lines: 92683
imported_transition_group_lines: 89034
imported_transition_component_lines: 999
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39298403 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
Round-trip comparison OK: 2555 chunk(s), 104652800 exact block check(s), 1635200 exact biome check(s), 94 block entity anchor check(s), 0 entity anchor check(s)
```

This confirms the central format bet again: a high-level authored plan can
produce large detailed ordinary ATF, and the resulting Anvil world can be
imported back into the same normal source-tree shape with exact block, biome,
and block-entity preservation.

### Plan Layer Versus Expanded Ordinary ATF

The comparative ergonomics slice used the canal district as the medium-large
sample. It is a 448x448 district with water hazards, five routes, six reserved
pads, six landmarks, 12 clusters, 110 scatter requests, and 34 loot requests.

The authored plan file and its deterministic ordinary-ATF expansion differ
sharply:

```text
plan-authored source:  examples/large-map-validation/groups/canal-448-plan.atf
expanded ordinary ATF: target/validation-trials/transition-expanded-a/groups/canal-448-plan.atf

plan-authored lines: 290
expanded ordinary ATF lines: 8864
expansion ratio: 30.57x

expanded command mix:
  fill: 5093
  set: 103
  place: 244
  block_entity: 34
```

The expanded ATF is valid and reviewable as generated output, but it is not a
comfortable semantic authoring surface. The terrain alone becomes thousands of
row-wise `fill` commands before the reader reaches routes, reservations,
landmarks, clusters, scatter, or loot.

The edit-locality test moved the east keep reservation 10 blocks east in a
scratch plan source:

```text
reserve "east keep island" circle 338 224 ...
landmark "east keep core" ... at 338 224 ...
```

became:

```text
reserve "east keep island" circle 348 224 ...
landmark "east keep core" ... at 348 224 ...
```

That is a two-line semantic edit in the plan source. The equivalent expanded
ordinary-ATF diff was much larger:

```text
plan diff:
  2 insertions, 2 deletions

expanded ordinary ATF diff:
  371 insertions, 371 deletions
  742 changed lines total
```

The moved-plan scratch source still passed the same gates:

```text
Plan diagnostics OK: no warnings
Expanded 3 plan file(s) to target/validation-trials/ergonomics-plan-edit-expanded as 22188 generated ATF line(s); copied 30 file(s)
Diagnostics OK: no warnings
Reference validation OK: 8 region file(s), 2555 chunk(s), 20440 block-state sample(s), 10220 biome sample(s), 39863716 authored block check(s), 374684 authored biome check(s), 94 validated block entity tag(s), 0 validated entity tag(s), 1 save data NBT file(s)
```

This comparison is also covered by an automated regression:

```text
src/test/java/com/anviltf/plan/PlanExpanderTest.java
largeDistrictSemanticMoveStaysSmallInPlanButLargeInExpandedAtf
```

This is the strongest ergonomics evidence so far. Ordinary ATF remains the right
generic compiler and importer surface, but large authored maps need a compact
composition layer for edits that move pads, routes, landmarks, clusters, scatter,
and loot together. The plan layer is not city-specific; it is a small set of
generic spatial abstractions that expands to ordinary ATF and round-trips through
the existing importer path.

### Candidate Proposal And Acceptance Gates

The authoring-format proposal has been tightened around the validated evidence.
The candidate is now explicitly:

```text
ATF v1 core remains the exact block-world IR.
plan { ... } is the large-map authoring layer.
components/ and style libraries carry theme, architecture, and map identity.
plan-expand emits ordinary ATF; Anvil generation never consumes plan constructs directly.
```

The proposed plan core is limited to generic spatial constructs already covered
by validation work: `style`, `mask`, `field`, `terrain`, `water`/`lava`,
`route`, `reserve`, `landmark`, `cluster`, `scatter_in`, `loot`, biomes, and
diagnostics/rendering around those concepts. Theme nouns such as castle, town,
district, road, harbor, temple, ship, and dojo stay out of the core language and
belong in components, palettes, styles, and group names.

The proposal now defines concrete practical acceptance gates for declaring the
format suitable for Akushi/Kikoshi/Riverton/Aztec-class maps:

```text
minimum_authored_area: at least 512x512 for one benchmark, at least 384x384 for two others
major_zones: at least 6 per 512x512 benchmark, at least 4 per 384x384 benchmark
route_network: main loop or spine plus at least 4 branches/crossings
landmarks: at least 6 reserved landmarks with distinct silhouettes
repeated_detail: at least 120 scatter/cluster/detail placements generated from plan constructs
loot: at least 35 anchors split across at least 2 tiers
source_shape: no authored group file over 320 nonblank lines
expanded_ratio: at least 15x generated ATF lines per plan-authored line on large terrain groups
edit_locality: moving one landmark/reservation changes <= 5 plan lines and >= 100 expanded ATF lines
determinism: two expansions from the same source are directory-identical
plan_diagnostics: zero warnings
core_diagnostics: zero warnings after expansion
reference_validation: pass
render_review: map plus at least one top/slice render inspected and recorded
import_round_trip: generated world imports to ordinary ATF and exact-compares
theme_purity: zero new core directives named after map themes or structures
```

The current validation world satisfies those gates under the dense profile, but
this is not final proof yet. The examples are stronger than skeletons now, but
they are still controlled validation benchmarks rather than imported or
hand-reconstructed target-class maps. The remaining work is to keep applying
these gates to richer real-map content, not to add theme-specific syntax.

### Reproducible Format Report

`plan-report` is now the preferred one-command evidence surface for the
candidate format. It combines scale metrics, composition summaries,
line-addressable edit handles, and the same gate scoreboard used by
`plan-gates`. With the current dense evidence attached:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-report examples/large-map-validation/world.atf \
  --profile dense \
  --edit-trial target/validation-trials/dense-edit-source/world.atf \
  --reference-world target/validation-trials/dense-world \
  --render-artifact target/large-map-validation-dense-map.png \
  --render-artifact target/large-map-validation-dense-slice-z512.png \
  --compare-worlds \
    target/validation-trials/dense-world \
    target/validation-trials/imported-dense-world
```

Current output:

```text
Plan format report for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
candidate: generic-plan-layer
profile: dense
status: pass
plan_blocks: 4
plan_lines: 733
plan_area_cells: 761220
max_plan_area_cells: 263169
generated_atf_lines: 29283
editable_index_entries: 117
editable_index_kinds:
  cluster: 38
  landmark: 24
  loot: 8
  reserve: 24
  route: 19
  scatter_in: 4
composition_groups:
  groups/canal-448-plan.atf group="canal 448 validation skeleton" area=449x449 routes=5 reservations=6 landmarks=6 clusters=12 scatter_requested=110 loot_requested=34
  groups/kikoshi-512-plan.atf group="kikoshi 512 validation skeleton" area=513x513 routes=5 reservations=6 landmarks=6 clusters=9 scatter_requested=120 loot_requested=35
  groups/polish-384-plan.atf group="polish 384 density benchmark" area=385x385 routes=5 reservations=7 landmarks=7 clusters=12 scatter_requested=190 loot_requested=34
  groups/ridge-384-plan.atf group="ridge 384 validation skeleton" area=385x385 routes=4 reservations=5 landmarks=5 clusters=5 scatter_requested=80 loot_requested=25
gates:
  passed: 22
  failed: 0
  manual: 0
```

This output is intentionally terse enough to paste into review. The detailed
gate lines, including `target_class_groups`, are the same as the `plan-gates`
scoreboard below.

### Per-Group Target-Class Audit

Aggregate gates can hide a weak district inside a large source tree. The
`plan-target-audit` command checks every 384x384-or-larger plan group
individually for the handles a target-class large map needs: routes, hazard
crossings, reservations, landmarks, distinct landmark components, clusters,
repeated detail, loot, and clean plan diagnostics.

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-target-audit examples/large-map-validation/world.atf
```

Current output:

```text
Plan target-class audit for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
plan_blocks: 4
plan_warnings: 0
group "canal 448 validation skeleton" file=groups/canal-448-plan.atf profile=448 area=449x449 status=pass
  pass routes value=5 threshold=>=5
  pass crossings value=8 threshold=>=4
  pass reservations value=6 threshold=>=6
  pass landmarks value=6 threshold=>=6
  pass distinct_landmark_components value=6 threshold=>=5 note=Proxy for distinct landmark silhouettes.
  pass clusters value=12 threshold=>=10
  pass repeated_detail value=307 (197 cluster member(s), 110 scatter request(s)) threshold=>=180
  pass loot value=34 threshold=>=30
group "kikoshi 512 validation skeleton" file=groups/kikoshi-512-plan.atf profile=512 area=513x513 status=pass
  pass routes value=5 threshold=>=5
  pass crossings value=2 threshold=>=2
  pass reservations value=6 threshold=>=6
  pass landmarks value=6 threshold=>=6
  pass distinct_landmark_components value=6 threshold=>=5 note=Proxy for distinct landmark silhouettes.
  pass clusters value=9 threshold=>=8
  pass repeated_detail value=176 (56 cluster member(s), 120 scatter request(s)) threshold=>=120
  pass loot value=35 threshold=>=35
group "polish 384 density benchmark" file=groups/polish-384-plan.atf profile=384 area=385x385 status=pass
  pass routes value=5 threshold=>=4
  pass crossings value=3 threshold=>=1
  pass reservations value=7 threshold=>=5
  pass landmarks value=7 threshold=>=5
  pass distinct_landmark_components value=7 threshold=>=4 note=Proxy for distinct landmark silhouettes.
  pass clusters value=12 threshold=>=5
  pass repeated_detail value=446 (256 cluster member(s), 190 scatter request(s)) threshold=>=100
  pass loot value=34 threshold=>=25
group "ridge 384 validation skeleton" file=groups/ridge-384-plan.atf profile=384 area=385x385 status=pass
  pass routes value=4 threshold=>=4
  pass crossings value=4 threshold=>=1
  pass reservations value=5 threshold=>=5
  pass landmarks value=5 threshold=>=5
  pass distinct_landmark_components value=5 threshold=>=4 note=Proxy for distinct landmark silhouettes.
  pass clusters value=5 threshold=>=5
  pass repeated_detail value=118 (38 cluster member(s), 80 scatter request(s)) threshold=>=100
  pass loot value=25 threshold=>=25
summary: pass checked_groups=4 failed_groups=0 plan_warnings=0
```

This is a stronger format-validation claim than the aggregate scoreboard alone:
each large district now has its own line-addressable composition handles and
minimum content density, so an LLM can focus on one district without needing the
rest of the map to compensate for it.

### Measured Gate Scoreboard

The practical gates now have a CLI scoreboard. Without evidence flags it reports
only the automatically measurable source-format gates; with evidence flags it
also attaches the edit-locality, generated-world validation, render-artifact,
and import-round-trip gates. The current evidence run uses `--profile dense`,
which adds stricter finished-map stress gates for detail density, cluster
density, landmarks, loot, and visible cluster footprint:

```text
/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-gates examples/large-map-validation/world.atf \
  --profile dense \
  --edit-trial target/validation-trials/dense-edit-source/world.atf \
  --reference-world target/validation-trials/dense-world \
  --render-artifact target/large-map-validation-dense-map.png \
  --render-artifact target/large-map-validation-dense-slice-z512.png \
  --compare-worlds \
    target/validation-trials/dense-world \
    target/validation-trials/imported-dense-world
```

Current output:

```text
Plan gates for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
passed: 22
failed: 0
manual: 0
pass minimum_authored_area value=1x >=512, 4x >=384 threshold=>=1 plan area at 512x512 and >=3 plan areas at 384x384 or larger note=Uses plan block area as the measurable benchmark proxy.
pass major_zones value=24 threshold=>=6 reservations note=Reservations are the current measurable proxy for named major zones.
pass route_network value=19 route(s), 17 crossing(s) threshold=>=5 routes and >=4 crossings
pass landmarks value=24 threshold=>=6 reserved landmarks
pass repeated_detail value=1047 threshold=>=120 scatter requests plus cluster members
pass loot value=128 threshold=>=35 loot anchors
pass source_shape value=275 threshold=<=320 nonblank lines in the largest authored group file
pass expanded_ratio value=39.95 threshold=>=15.00 generated ATF lines per plan line
pass edit_locality value=2 source line(s), 771 expanded line(s), 0 plan warning(s), 0 core warning(s) threshold=<=5 plan lines and >=100 expanded ATF lines for a semantic move note=Measured against /Users/ben/code/anvil-tf/target/validation-trials/dense-edit-source/world.atf
pass determinism value=directory-identical threshold=two expansions from the same source are directory-identical
pass plan_diagnostics value=0 threshold=0 plan warnings
pass core_diagnostics value=0 threshold=0 expanded ATF warnings
pass reference_validation value=3180 chunk(s), 48123947 authored block check(s), 467429 authored biome check(s) threshold=generated Anvil world validates with the reference library note=Validated /Users/ben/code/anvil-tf/target/validation-trials/dense-world
pass render_review value=2 non-empty render artifact(s) threshold=map plus at least one top/slice render inspected and recorded note=Checks artifact presence; keep the visual inspection notes with the report.
pass import_round_trip value=3180 chunk(s), 130252800 block check(s), 2035200 biome check(s) threshold=generated world imports to ordinary ATF and exact-compares note=Compared /Users/ben/code/anvil-tf/target/validation-trials/dense-world to /Users/ben/code/anvil-tf/target/validation-trials/imported-dense-world
pass theme_purity value=generic plan directives threshold=0 plan directives named after map themes or structures
pass target_class_groups value=4 checked group(s), 0 failed group(s), 0 plan warning(s) threshold=each >=384x384 plan group passes per-group routes/reservations/landmarks/detail/loot audit note=Same thresholds as plan-target-audit.
pass dense_repeated_detail value=1047 threshold=>=1000 scatter requests plus cluster members note=Dense profile for finished-map stress testing.
pass dense_cluster_density value=38 cluster(s), 547 member(s) threshold=>=36 clusters and >=475 cluster members note=Dense profile for repeated architecture and local detail.
pass dense_landmarks value=24 threshold=>=24 reserved landmarks across benchmark groups note=Dense profile for stronger silhouette coverage.
pass dense_loot value=128 threshold=>=120 loot anchors note=Dense profile for survival-games scale loot spread.
pass dense_overlay_coverage value=17186 threshold=>=16000 visible cluster overlay cells note=Dense profile proxy for authored local-detail footprint.
```

This turns the denser four-group benchmark into a reproducible all-green
scoreboard: the source-format gates, dense-profile gates, edit-locality trial,
generated-world reference validation, render artifact presence, and reviewable
import exact comparison are all measured in one command. The render gate still
depends on the recorded visual inspection notes elsewhere in this report; the
CLI checks that the artifacts exist and are non-empty, not that they are
beautiful.

### Edit Locality Artifact

The edit-locality gate now has its own CLI measurement:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-trial \
  examples/large-map-validation/world.atf \
  target/validation-trials/dense-edit-source/world.atf
```

The edited source moves the dense polish benchmark's east overlook reservation
and landmark from x=314 to x=324. Current output:

```text
Plan edit trial for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf -> /Users/ben/code/anvil-tf/target/validation-trials/dense-edit-source/world.atf
source_changed_lines: 2
expanded_changed_lines: 771
plan_warnings: 0
core_warnings: 0
threshold: source_changed_lines <= 5, expanded_changed_lines >= 100, warnings = 0
result: pass
```

This turns the previous regression-only evidence into a reusable artifact. It
also clarifies what the gate measures: plan-authored source remains small and
semantic, while generated ATF changes are allowed to be large because they are
build output.

### Additive Detail Edit Artifact

The move-edit gate is deliberately strict, but large-map authoring also needs
compact additive edits: an LLM should be able to add a repeated-detail pass
without writing raw block commands. This trial copies the dense benchmark to
`target/validation-trials/additive-detail-edit` and adds one new cluster to the
east overlook:

```atf
cluster "east overlook balcony polish" at reserve "east overlook" conform ground spacing 0 {
  repeat component style.light from -40 -34 to 40 -34 count 24 radius 1 height 5 rotate 0
  repeat component style.planter_piece from -34 -14 to 34 -14 count 16 radius 2 height 3 rotate 0
  repeat component style.column_piece from -32 8 to 32 8 count 18 radius 1 height 7 rotate 0
  repeat component style.retaining_piece from -34 28 to 34 28 count 16 radius 2 height 3 rotate 0
  repeat component style.rib_piece from -22 40 to 22 40 count 14 radius 1 height 8 rotate 180
}
```

Command:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-trial \
  examples/large-map-validation/world.atf \
  target/validation-trials/additive-detail-edit/world.atf \
  --max-source-lines 8 \
  --min-expanded-lines 80
```

Current output:

```text
Plan edit trial for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf -> /Users/ben/code/anvil-tf/target/validation-trials/additive-detail-edit/world.atf
source_changed_lines: 7
expanded_changed_lines: 89
plan_warnings: 0
core_warnings: 0
threshold: source_changed_lines <= 8, expanded_changed_lines >= 80, warnings = 0
result: pass
```

This is a stronger ergonomics signal than coordinate movement alone: seven
authored plan lines add 88 requested repeated components through ordinary,
generic `cluster` and `repeat component` primitives, while the diagnostics still
reject overly tight or overlapping detail rows.

### Edit Suite Artifact

The edit-locality check now also runs as a small suite over multiple semantic
changes to the dense source. The suite covers four edits:

- move the east overlook reservation and landmark,
- move the north terrace reservation and landmark,
- move the south quay reservation and landmark,
- move the west garden reservation and landmark.

Command:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-suite \
  examples/large-map-validation/world.atf \
  target/validation-trials/dense-edit-source/world.atf \
  target/validation-trials/edit-suite-north-terrace/world.atf \
  target/validation-trials/edit-suite-south-quay/world.atf \
  target/validation-trials/edit-suite-west-garden/world.atf
```

Current output:

```text
Plan edit suite for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
threshold: source_changed_lines <= 5, expanded_changed_lines >= 100, warnings = 0
pass /Users/ben/code/anvil-tf/target/validation-trials/dense-edit-source/world.atf source_changed_lines=2 expanded_changed_lines=771 plan_warnings=0 core_warnings=0
pass /Users/ben/code/anvil-tf/target/validation-trials/edit-suite-north-terrace/world.atf source_changed_lines=2 expanded_changed_lines=654 plan_warnings=0 core_warnings=0
pass /Users/ben/code/anvil-tf/target/validation-trials/edit-suite-south-quay/world.atf source_changed_lines=2 expanded_changed_lines=620 plan_warnings=0 core_warnings=0
pass /Users/ben/code/anvil-tf/target/validation-trials/edit-suite-west-garden/world.atf source_changed_lines=2 expanded_changed_lines=505 plan_warnings=0 core_warnings=0
summary: 4/4 edit trial(s) passed
```

This is stronger than the single edit trial: several local semantic edits stay
within two authored source lines while changing hundreds of expanded ATF lines,
and every edited source remains clean under both plan and expanded-core
diagnostics.

### Target-Trait Additive Edit Suite

The move suite proves large semantic relocations. Target-class maps also need
small additive detail edits that match a prompt such as "add harbor mooring
detail" or "add keep parapet accents" without writing raw block commands.

Two scratch source copies were edited:

- `target-edit-kikoshi-harbor`: adds a harbor mooring/light cluster inside the
  existing Kikoshi harbor pad.
- `target-edit-riverton-keep`: adds parapet column accents inside the existing
  canal/castle east keep island.

Command:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-suite \
  examples/large-map-validation/world.atf \
  target/validation-trials/target-edit-kikoshi-harbor/world.atf \
  target/validation-trials/target-edit-riverton-keep/world.atf \
  --max-source-lines 4 \
  --min-expanded-lines 8
```

Current output:

```text
Plan edit suite for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
threshold: source_changed_lines <= 4, expanded_changed_lines >= 8, warnings = 0
pass /Users/ben/code/anvil-tf/target/validation-trials/target-edit-kikoshi-harbor/world.atf source_changed_lines=4 expanded_changed_lines=10 plan_warnings=0 core_warnings=0
pass /Users/ben/code/anvil-tf/target/validation-trials/target-edit-riverton-keep/world.atf source_changed_lines=4 expanded_changed_lines=9 plan_warnings=0 core_warnings=0
summary: 2/2 edit trial(s) passed
```

This is still controlled evidence, not an independent-model trial, but it
strengthens the practical ergonomics claim: target-map-flavored detail can be
added as compact plan clusters inside existing reservations and verified by the
same suite command.

### Mixed Edit Manifest

The individual edit commands are now consolidated into a manifest-style eval so
future independent model outputs can be scored by adding rows instead of
hand-writing new commands. The manifest lives at:

```text
examples/large-map-validation/edit-manifest.tsv
```

Prompt-ready edit tasks live at:

```text
examples/large-map-validation/edit-eval-prompts.md
```

The same tasks are available in machine-readable form for `plan-eval-brief`:

```text
examples/large-map-validation/edit-eval-tasks.tsv
```

They can also be exported as a batch handoff pack:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-pack \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  target/validation-briefs/large-map-model-pack \
  --run-prefix model-eval \
  --with-context \
  --context-lines 8
```

Output:

```text
Plan edit eval pack for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
tasks: 4
brief_dir: /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack
manifest: /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/manifest.tsv
readme: /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/README.md
run_notes: /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/run-notes.md
score_command:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest examples/large-map-validation/world.atf target/validation-briefs/large-map-model-pack/manifest.tsv
```

Generated pack contents:

```text
README.md
dense-overlook-additive.md
kikoshi-harbor-mooring.md
manifest.tsv
polish-recipe-arch-rhythm.md
riverton-keep-parapet.md
run-notes.md
score.md
```

Prepared-run smoke test:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-pack \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  target/validation-briefs/prepared-pack-smoke \
  --run-prefix prepared-smoke \
  --with-context \
  --context-lines 4 \
  --prepare-runs
```

Output:

```text
Plan edit eval pack for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
tasks: 4
brief_dir: /Users/ben/code/anvil-tf/target/validation-briefs/prepared-pack-smoke
manifest: /Users/ben/code/anvil-tf/target/validation-briefs/prepared-pack-smoke/manifest.tsv
readme: /Users/ben/code/anvil-tf/target/validation-briefs/prepared-pack-smoke/README.md
run_notes: /Users/ben/code/anvil-tf/target/validation-briefs/prepared-pack-smoke/run-notes.md
prepared_runs: 4
score_command:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest examples/large-map-validation/world.atf target/validation-briefs/prepared-pack-smoke/manifest.tsv
```

The pack-level `README.md` lists the workflow, score command, task briefs,
edited `world.atf` paths, expected changed files, and line thresholds. The
`run-notes.md` template records model/surface, operator, date, independence
notes, and retry counts. The generated run directories contain normal source
trees with `world.atf`, `meta.atf`, `groups/`, and `components/`. Re-running
with `--prepare-runs` against the same run prefix refuses to overwrite existing
directories unless `--overwrite-runs` is passed; the overwrite path was also
smoke-tested and reported `prepared_runs: 4 (overwritten)`.

Self-contained run-root smoke test:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-pack \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  target/validation-briefs/isolated-pack-smoke \
  --run-prefix isolated-smoke \
  --runs-dir target/validation-briefs/isolated-pack-smoke/runs \
  --with-context \
  --context-lines 4 \
  --prepare-runs \
  --overwrite-runs
```

Output:

```text
Plan edit eval pack for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
tasks: 4
brief_dir: /Users/ben/code/anvil-tf/target/validation-briefs/isolated-pack-smoke
runs_dir: /Users/ben/code/anvil-tf/target/validation-briefs/isolated-pack-smoke/runs
manifest: /Users/ben/code/anvil-tf/target/validation-briefs/isolated-pack-smoke/manifest.tsv
readme: /Users/ben/code/anvil-tf/target/validation-briefs/isolated-pack-smoke/README.md
run_notes: /Users/ben/code/anvil-tf/target/validation-briefs/isolated-pack-smoke/run-notes.md
prepared_runs: 4 (overwritten)
score_command:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest examples/large-map-validation/world.atf target/validation-briefs/isolated-pack-smoke/manifest.tsv
```

The generated manifest now uses paths relative to the pack directory when the
run root is nested under the pack:

```text
kikoshi-harbor-mooring	runs/isolated-smoke-kikoshi-harbor-mooring/world.atf	4	8	groups/kikoshi-512-plan.atf
riverton-keep-parapet	runs/isolated-smoke-riverton-keep-parapet/world.atf	4	8	groups/canal-448-plan.atf
polish-recipe-arch-rhythm	runs/isolated-smoke-polish-recipe-arch-rhythm/world.atf	4	6	groups/polish-384-plan.atf
dense-overlook-additive	runs/isolated-smoke-dense-overlook-additive/world.atf	8	80	groups/polish-384-plan.atf
```

One generated brief was spot-checked. Its output contract and scoring row point
at the same self-contained run tree:

```text
copy source_tree to: target/validation-briefs/isolated-pack-smoke/runs/isolated-smoke-riverton-keep-parapet
edited_world_atf: target/validation-briefs/isolated-pack-smoke/runs/isolated-smoke-riverton-keep-parapet/world.atf
riverton-keep-parapet	runs/isolated-smoke-riverton-keep-parapet/world.atf	4	8	groups/canal-448-plan.atf
```

Because this smoke pack contains copied but unedited source trees, the cheap
status check correctly reports it as pending while proving all four paths are
present and resolvable:

```text
summary: ready=0 missing=0 unedited=4 wrong_file=0 total=4
```

Independent handoff mode smoke test:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-pack \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  target/validation-briefs/independent-pack-smoke \
  --run-prefix independent-smoke \
  --independent \
  --with-context \
  --context-lines 4 \
  --overwrite-runs
```

Output:

```text
Plan edit eval pack for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
tasks: 4
brief_dir: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-smoke
runs_dir: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-smoke/runs
manifest: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-smoke/manifest.tsv
readme: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-smoke/README.md
run_notes: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-smoke/run-notes.md
prepared_runs: 4 (overwritten)
independent_handoff: pass 5 handoff file(s) clean
score_command:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest examples/large-map-validation/world.atf target/validation-briefs/independent-pack-smoke/manifest.tsv
```

The generated manifest uses pack-local run paths without requiring an explicit
`--runs-dir` argument:

```text
kikoshi-harbor-mooring	runs/independent-smoke-kikoshi-harbor-mooring/world.atf	4	8	groups/kikoshi-512-plan.atf
riverton-keep-parapet	runs/independent-smoke-riverton-keep-parapet/world.atf	4	8	groups/canal-448-plan.atf
polish-recipe-arch-rhythm	runs/independent-smoke-polish-recipe-arch-rhythm/world.atf	4	6	groups/polish-384-plan.atf
dense-overlook-additive	runs/independent-smoke-dense-overlook-additive/world.atf	8	80	groups/polish-384-plan.atf
```

The generated `README.md` and task briefs now include neutral handoff-scope
guardrails. The README says the model should receive one task brief and the
prepared source tree named by that brief, should not inspect earlier validation
examples or prior answer attempts, and should record model/surface, operator,
date, independence notes, and retries in `run-notes.md`. Each task brief
contains:

```text
handoff_scope:
  use only this brief and the prepared source tree named below
  do not inspect earlier validation examples or prior answer attempts
  record any retry or diagnostic-fix request in run-notes.md
```

Its status and audit artifacts prove the handoff is clean but still pending
model edits:

```text
Wrote plan edit eval status report to /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-smoke/status.md
summary: ready=0 missing=0 unedited=4 wrong_file=0 total=4
Wrote plan edit eval audit report to /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-smoke/audit.md
summary: 7/15 eval audit gate(s) passed
```

The passing audit gates include `independence_hygiene` with
`5 handoff file(s) clean`. This older smoke pack predates handoff locks,
baseline locks, and filtered prepared-source trees, so it also fails those
newer provenance gates alongside the expected pending-run gates.

Independent handoff with prefilled run metadata:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-pack \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  target/validation-briefs/independent-pack-metadata-smoke \
  --run-prefix independent-metadata-smoke \
  --independent \
  --isolated-handoffs \
  --model-surface metadata-fixture \
  --operator codex-smoke \
  --date 2026-06-13 \
  --independence-notes "metadata prefill smoke; no model edits yet" \
  --write-initial-reports \
  --initial-min-passed 3 \
  --with-context \
  --context-lines 4 \
  --overwrite-runs
```

Output:

```text
Plan edit eval pack for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
tasks: 4
brief_dir: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke
runs_dir: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/runs
handoffs_dir: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/handoffs
manifest: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/manifest.tsv
readme: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/README.md
run_notes: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/run-notes.md
handoff_lock: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/handoff-lock.tsv
baseline_lock: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/baseline-lock.tsv
prepared_runs: 4 (overwritten)
independent_handoff: pass 9 handoff file(s) clean
initial_status: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/status.md
summary: ready=0 missing=0 unedited=4 wrong_file=0 total=4
initial_audit: /Users/ben/code/anvil-tf/target/validation-briefs/independent-pack-metadata-smoke/audit.md
summary: 11/15 eval audit gate(s) passed
score_command:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest examples/large-map-validation/world.atf target/validation-briefs/independent-pack-metadata-smoke/manifest.tsv
```

The generated `run-notes.md` is prefilled:

```text
Model or surface: metadata-fixture
Operator: codex-smoke
Date: 2026-06-13
Independence notes: metadata prefill smoke; no model edits yet
```

Its initial status and audit are now written by `plan-eval-pack` itself:

```text
summary: ready=0 missing=0 unedited=4 wrong_file=0 total=4
summary: 11/15 eval audit gate(s) passed
```

The manifest now points each task at its isolated handoff source tree:

```text
kikoshi-harbor-mooring	handoffs/kikoshi-harbor-mooring/source/world.atf	4	8	groups/kikoshi-512-plan.atf
riverton-keep-parapet	handoffs/riverton-keep-parapet/source/world.atf	4	8	groups/canal-448-plan.atf
polish-recipe-arch-rhythm	handoffs/polish-recipe-arch-rhythm/source/world.atf	4	6	groups/polish-384-plan.atf
dense-overlook-additive	handoffs/dense-overlook-additive/source/world.atf	8	80	groups/polish-384-plan.atf
```

The audit passes `handoff_lock` with `10 file(s) match`, proving the README,
manifest, four pack task briefs, and four isolated task handoff READMEs still
match their generated hashes. It also passes `baseline_lock` with
`124 file(s) match or are expected edits`, proving the prepared run source
trees still match their copied baseline. It also passes
`prepared_source_scope`, proving the model-edit trees contain only
`world.atf`, `meta.atf`, `groups/*.atf`, and `components/*.atf`, not benchmark
helper prompts or manifests. This proves metadata and handoff/source provenance
can be made audit-ready at pack creation time; readiness, scoring, and
target-family coverage still correctly wait for actual model edits.

Cheap status checks:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-status \
  examples/large-map-validation/world.atf \
  target/validation-briefs/large-map-model-pack/manifest.tsv
```

Output:

```text
Plan edit eval status for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
manifest: /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/manifest.tsv
ready kikoshi-harbor-mooring edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-kikoshi-harbor-mooring/world.atf expected_changed_file=groups/kikoshi-512-plan.atf changed_files=groups/kikoshi-512-plan.atf
ready riverton-keep-parapet edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-riverton-keep-parapet/world.atf expected_changed_file=groups/canal-448-plan.atf changed_files=groups/canal-448-plan.atf
ready polish-recipe-arch-rhythm edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-polish-recipe-arch-rhythm/world.atf expected_changed_file=groups/polish-384-plan.atf changed_files=groups/polish-384-plan.atf
ready dense-overlook-additive edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-dense-overlook-additive/world.atf expected_changed_file=groups/polish-384-plan.atf changed_files=groups/polish-384-plan.atf
summary: ready=4 missing=0 unedited=0 wrong_file=0 total=4
```

The prepared smoke pack has copied but unedited source trees, so the same
status command reports them as pending:

```text
summary: ready=0 missing=0 unedited=4 wrong_file=0 total=4
```

Markdown status artifacts:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-status-report \
  examples/large-map-validation/world.atf \
  target/validation-briefs/large-map-model-pack/manifest.tsv \
  target/validation-briefs/large-map-model-pack/status.md
```

Output:

```text
Wrote plan edit eval status report to /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/status.md
summary: ready=4 missing=0 unedited=0 wrong_file=0 total=4
```

The prepared smoke pack also writes a pending-status artifact:

```text
Wrote plan edit eval status report to /Users/ben/code/anvil-tf/target/validation-briefs/prepared-pack-smoke/status.md
summary: ready=0 missing=0 unedited=4 wrong_file=0 total=4
```

Markdown score artifact:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-edit-manifest-report \
  examples/large-map-validation/world.atf \
  target/validation-briefs/large-map-model-pack/manifest.tsv \
  target/validation-briefs/large-map-model-pack/score.md
```

Output:

```text
Wrote plan edit manifest report to /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/score.md
summary: 4/4 edit manifest row(s) passed
```

The generated `score.md` records each task's pass/fail result, source-line
threshold, expanded-line threshold, plan/core warning counts, expected file
check, and changed files.

Evidence audit artifacts:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-audit \
  examples/large-map-validation/world.atf \
  target/validation-briefs/large-map-model-pack \
  --min-passed 3 \
  --out target/validation-briefs/large-map-model-pack/audit.md
```

Output:

```text
Wrote plan edit eval audit report to /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/audit.md
summary: 10/15 eval audit gate(s) passed
```

The controlled pack passes readiness, scoring, and target-family coverage, but
fails `handoff_lock` and `baseline_lock` because it predates the locked
independent-pack workflow, fails `prepared_source_scope` because its copied run
trees still contain helper prompt/manifest files, fails `independence_hygiene`
because its handoff Markdown points at `target/validation-trials/model-eval-*`
controlled solution paths, and fails `run_notes_metadata` because the
model/surface, operator, date, and independence notes are intentionally blank.
That prevents controlled Codex-authored edits from being counted as independent
evidence.

The self-contained unedited smoke pack was also audited:

```text
Wrote plan edit eval audit report to /Users/ben/code/anvil-tf/target/validation-briefs/isolated-pack-smoke/audit.md
summary: 7/15 eval audit gate(s) passed
```

Its `independence_hygiene` gate passes (`5 handoff file(s) clean`). Its
failures include missing lock files and source-scope cleanup because this pack
predates the locked/filtered independent workflow, plus the expected pending-run
gates: missing `score.md`, blank run metadata,
`ready=0 missing=0 unedited=4 wrong_file=0 total=4`, skipped scoring until
readiness passes, and no target-family coverage yet.

Cross-run completion audit:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-completion \
  examples/large-map-validation/world.atf \
  target/validation-briefs/large-map-model-pack \
  target/validation-briefs/isolated-pack-smoke \
  --min-runs 2 \
  --min-passed-per-run 3 \
  --out target/validation-briefs/current-completion.md
```

Output:

```text
Wrote plan edit eval completion report to /Users/ben/code/anvil-tf/target/validation-briefs/current-completion.md
summary: 1/4 eval completion gate(s) passed
```

Current completion gates:

```text
pass pack_count value=2 threshold=at least 2 eval pack(s)
fail completed_runs value=0 threshold=at least 2 completed audited run(s)
fail distinct_model_surfaces value=0 threshold=at least 2 distinct model/surface values
fail target_family_coverage value=false threshold=a completed run covers Kikoshi-scale, castle/canal, and dense polish tasks
```

This is the current proof that the final independent-eval standard is not yet
satisfied. The harness can now distinguish prepared packs, controlled
Codex-authored scoring success, and genuine multi-run independent completion.

Controlled batch score evidence:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-edit-manifest \
  examples/large-map-validation/world.atf \
  target/validation-briefs/large-map-model-pack/manifest.tsv
```

Output:

```text
Plan edit manifest for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
manifest: /Users/ben/code/anvil-tf/target/validation-briefs/large-map-model-pack/manifest.tsv
pass kikoshi-harbor-mooring edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-kikoshi-harbor-mooring/world.atf source_changed_lines=4 max_source_lines=4 expanded_changed_lines=10 min_expanded_lines=8 plan_warnings=0 core_warnings=0 changed_files=groups/kikoshi-512-plan.atf expected_changed_file=groups/kikoshi-512-plan.atf expected_file=pass
pass riverton-keep-parapet edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-riverton-keep-parapet/world.atf source_changed_lines=4 max_source_lines=4 expanded_changed_lines=9 min_expanded_lines=8 plan_warnings=0 core_warnings=0 changed_files=groups/canal-448-plan.atf expected_changed_file=groups/canal-448-plan.atf expected_file=pass
pass polish-recipe-arch-rhythm edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-polish-recipe-arch-rhythm/world.atf source_changed_lines=3 max_source_lines=4 expanded_changed_lines=7 min_expanded_lines=6 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
pass dense-overlook-additive edited=/Users/ben/code/anvil-tf/target/validation-trials/model-eval-dense-overlook-additive/world.atf source_changed_lines=7 max_source_lines=8 expanded_changed_lines=89 min_expanded_lines=80 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
summary: 4/4 edit manifest row(s) passed
```

This is controlled Codex-authored evidence, not independent external-model
evidence. It validates the end-to-end batch mechanics needed for that next
step: generated prompt pack, model-run output directories, per-task locality
thresholds, expected changed files, diagnostics, and generated-impact scoring.

Each non-comment row is:

```text
name<TAB>edited_world_atf<TAB>max_source_lines<TAB>min_expanded_lines[<TAB>expected_changed_file]
```

Command:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-manifest.tsv
```

Current output:

```text
Plan edit manifest for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
manifest: /Users/ben/code/anvil-tf/examples/large-map-validation/edit-manifest.tsv
pass dense-east-overlook-move edited=/Users/ben/code/anvil-tf/target/validation-trials/dense-edit-source/world.atf source_changed_lines=2 max_source_lines=5 expanded_changed_lines=771 min_expanded_lines=100 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
pass north-terrace-move edited=/Users/ben/code/anvil-tf/target/validation-trials/edit-suite-north-terrace/world.atf source_changed_lines=2 max_source_lines=5 expanded_changed_lines=654 min_expanded_lines=100 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
pass south-quay-move edited=/Users/ben/code/anvil-tf/target/validation-trials/edit-suite-south-quay/world.atf source_changed_lines=2 max_source_lines=5 expanded_changed_lines=620 min_expanded_lines=100 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
pass west-garden-move edited=/Users/ben/code/anvil-tf/target/validation-trials/edit-suite-west-garden/world.atf source_changed_lines=2 max_source_lines=5 expanded_changed_lines=505 min_expanded_lines=100 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
pass east-overlook-additive-detail edited=/Users/ben/code/anvil-tf/target/validation-trials/additive-detail-edit/world.atf source_changed_lines=7 max_source_lines=8 expanded_changed_lines=89 min_expanded_lines=80 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
pass recipe-arch-rhythm edited=/Users/ben/code/anvil-tf/target/validation-trials/recipe-edit-arch/world.atf source_changed_lines=3 max_source_lines=4 expanded_changed_lines=7 min_expanded_lines=6 plan_warnings=0 core_warnings=0 changed_files=groups/polish-384-plan.atf expected_changed_file=groups/polish-384-plan.atf expected_file=pass
pass kikoshi-harbor-mooring edited=/Users/ben/code/anvil-tf/target/validation-trials/target-edit-kikoshi-harbor/world.atf source_changed_lines=4 max_source_lines=4 expanded_changed_lines=10 min_expanded_lines=8 plan_warnings=0 core_warnings=0 changed_files=groups/kikoshi-512-plan.atf expected_changed_file=groups/kikoshi-512-plan.atf expected_file=pass
pass riverton-keep-parapet edited=/Users/ben/code/anvil-tf/target/validation-trials/target-edit-riverton-keep/world.atf source_changed_lines=4 max_source_lines=4 expanded_changed_lines=9 min_expanded_lines=8 plan_warnings=0 core_warnings=0 changed_files=groups/canal-448-plan.atf expected_changed_file=groups/canal-448-plan.atf expected_file=pass
summary: 8/8 edit manifest row(s) passed
```

This does not replace the need for independent model-authored edits. It makes
that next step measurable: the model gets a workbench/snippet/recipe context,
writes an edited source tree, and the manifest scorer enforces source locality,
expected changed file, generated impact, plan diagnostics, and expanded-core
diagnostics. The qualification rules for independent evidence are captured in
`docs/large-map-independent-eval-protocol.md`.

Generated brief evidence:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-eval-brief \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  riverton-keep-parapet \
  --run model-riverton-keep-01 \
  --with-context \
  --context-lines 160
```

Current output:

```text
Plan edit eval brief for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
task: riverton-keep-parapet
title: Riverton keep parapet accents
prompt:
Add a compact Riverton/castle-town keep parapet accent inside the existing east keep island. Do not move the keep, routes, or reservation. Do not add city/castle-specific core directives. Use existing generic cluster/component/style rows and keep diagnostics clean.
context_commands:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench examples/large-map-validation/world.atf --group "canal 448 validation skeleton" --kind cluster --limit 14 --catalog-limit 30
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet examples/large-map-validation/world.atf --name "east keep retaining trim" --kind cluster --context 2
embedded_context:
context_line_limit_per_command: 160
>>> java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench examples/large-map-validation/world.atf --group "canal 448 validation skeleton" --kind cluster --limit 14 --catalog-limit 30
Plan LLM workbench for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
...
>>> java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet examples/large-map-validation/world.atf --name "east keep retaining trim" --kind cluster --context 2
Plan source snippet for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
...
output_contract:
  source_tree: /Users/ben/code/anvil-tf/examples/large-map-validation
  copy source_tree to: target/validation-trials/model-riverton-keep-01
  edited_world_atf: target/validation-trials/model-riverton-keep-01/world.atf
  edit only the intended ATF source file
  run plan-diagnose before scoring
diagnose_command:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose target/validation-trials/model-riverton-keep-01/world.atf
manifest_row:
riverton-keep-parapet	../../target/validation-trials/model-riverton-keep-01/world.atf	4	8	groups/canal-448-plan.atf
score_command:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-eval-score examples/large-map-validation/world.atf examples/large-map-validation/edit-eval-tasks.tsv riverton-keep-parapet target/validation-trials/model-riverton-keep-01/world.atf
```

Embedded context is bounded per command so the same brief mechanism can be used
on large maps without flooding the model prompt. The brief also prints the
specific edited source path and direct `plan-eval-score` command for the named
run; it also prints the exact `plan-diagnose` command to run before scoring.
`--context-lines 0` preserves the full-output mode for manual inspection.

Direct score evidence:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-eval-score \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  riverton-keep-parapet \
  target/validation-trials/target-edit-riverton-keep/world.atf
```

Current output:

```text
Plan edit eval score for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
task: riverton-keep-parapet
title: Riverton keep parapet accents
pass edited=/Users/ben/code/anvil-tf/target/validation-trials/target-edit-riverton-keep/world.atf source_changed_lines=4 max_source_lines=4 expanded_changed_lines=9 min_expanded_lines=8 plan_warnings=0 core_warnings=0 changed_files=groups/canal-448-plan.atf expected_changed_file=groups/canal-448-plan.atf expected_file=pass
```

End-to-end eval-path smoke trial:

```text
copy examples/large-map-validation to target/validation-trials/codex-kikoshi-harbor-01
edit target/validation-trials/codex-kikoshi-harbor-01/groups/kikoshi-512-plan.atf
```

The edit added one four-line generic cluster using existing `style.post_piece`
and `style.light` keys inside the existing `harbor pad` reservation:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-diagnose target/validation-trials/codex-kikoshi-harbor-01/world.atf
```

Output:

```text
Plan diagnostics OK: no warnings
```

Score:

```text
/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java \
  -jar target/anvil-tf-0.1.0-SNAPSHOT.jar \
  plan-eval-score \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-eval-tasks.tsv \
  kikoshi-harbor-mooring \
  target/validation-trials/codex-kikoshi-harbor-01/world.atf
```

Output:

```text
Plan edit eval score for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
task: kikoshi-harbor-mooring
title: Kikoshi harbor mooring detail
pass edited=/Users/ben/code/anvil-tf/target/validation-trials/codex-kikoshi-harbor-01/world.atf source_changed_lines=4 max_source_lines=4 expanded_changed_lines=10 min_expanded_lines=8 plan_warnings=0 core_warnings=0 changed_files=groups/kikoshi-512-plan.atf expected_changed_file=groups/kikoshi-512-plan.atf expected_file=pass
```

This is still Codex-authored controlled evidence, not an independent external
model trial. It validates the mechanical eval loop that independent trials need:
a prompt/task row identifies the expected source file, a tiny plan edit produces
a larger generated ATF change, and the scorer verifies diagnostics, generated
impact, and source locality.

### Dense Benchmark Artifact

The richer benchmark adds `groups/polish-384-plan.atf`, a fourth ordinary group
file focused on finished-map density: prepared pads, retaining trim, arcade
rhythm, market frontage, quay supports, garden rows, and loot spread. The file
is 242 lines, while the largest authored group remains 275 nonblank lines.

Current dense metrics:

```text
plan_blocks: 4
plan_lines: 733
plan_area_cells: 761220
plan_areas_at_least_384: 4
plan_areas_at_least_512: 1
generated_atf_lines: 29283
generated_atf_lines_per_plan_line: 39.95
clusters: 38
cluster_members: 547
scatter_requested: 500
landmarks: 24
loot_requested: 128
```

Composition outline evidence:

```text
Plan outline for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
plan_blocks: 4
group "canal 448 validation skeleton" file=groups/canal-448-plan.atf ... routes=5 reservations=6 landmarks=6 clusters=12 scatter=110 loot=34
group "kikoshi 512 validation skeleton" file=groups/kikoshi-512-plan.atf ... routes=5 reservations=6 landmarks=6 clusters=9 scatter=120 loot=35
group "polish 384 density benchmark" file=groups/polish-384-plan.atf ... routes=5 reservations=7 landmarks=7 clusters=12 scatter=190 loot=34
group "ridge 384 validation skeleton" file=groups/ridge-384-plan.atf ... routes=4 reservations=5 landmarks=5 clusters=5 scatter=80 loot=25
```

The full `plan-outline` output lists every named route, reservation, landmark,
and cluster with anchors and counts. This gives an LLM a compact edit surface
before modifying coordinates, so edits can target names such as "east overlook",
"north terrace", or "south quay" instead of scanning expanded ATF.

Composition source-index evidence:

```text
Plan index for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
entries: 117
groups/canal-448-plan.atf:152 group="canal 448 validation skeleton" kind=route name="outer market ring" :: route "outer market ring" width 7 palette style.path conform ground seed 911 {
groups/kikoshi-512-plan.atf:178 group="kikoshi 512 validation skeleton" kind=reserve name="spawn plaza" :: reserve "spawn plaza" circle 256 256 radius 44 conform ground floor style.spawn_floor floor_palette style.spawn_floor_palette seed 701 edge style.spawn_edge edge_width 3 clear_to 96 require_route 8
groups/polish-384-plan.atf:139 group="polish 384 density benchmark" kind=reserve name="east overlook" :: reserve "east overlook" circle 314 202 radius 54 conform ground floor style.overlook_floor floor_palette style.overlook_floor_palette seed 1233 edge style.overlook_edge edge_width 3 clear_to 124 require_route 12
groups/polish-384-plan.atf:147 group="polish 384 density benchmark" kind=landmark name="east overlook keep" :: landmark "east overlook keep" component style.overlook_landmark at 314 202 conform ground reserve "east overlook" radius 18 height 38 rotate 90
```

The full `plan-index` output gives every editable composition handle a stable
`file:line` target. This is the practical bridge between compact map reasoning
and surgical source edits: an LLM can pick a named feature from the outline,
locate it with the index, and change only the authored command that owns the
feature.

LLM workbench evidence:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench \
  examples/large-map-validation/world.atf \
  --group "polish 384 density benchmark" \
  --kind cluster \
  --limit 5 \
  --catalog-limit 12
```

Current output:

```text
Plan LLM workbench for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
candidate: generic-plan-layer
group_filter: "polish 384 density benchmark"
kind_filter: cluster
composition_groups:
  groups/polish-384-plan.atf group="polish 384 density benchmark" origin=320,0,320 area=385x385 routes=5 reservations=7 landmarks=7 clusters=12 scatter_requested=190 loot_requested=34
editable_handles:
  total_matching: 12
  showing: 5
  groups/polish-384-plan.atf:153 group="polish 384 density benchmark" kind=cluster name="center arcade ring" :: cluster "center arcade ring" at reserve "central polished plaza" conform ground spacing 0 {
  groups/polish-384-plan.atf:162 group="polish 384 density benchmark" kind=cluster name="central micro detail" :: cluster "central micro detail" at reserve "central polished plaza" conform ground spacing 0 {
  groups/polish-384-plan.atf:168 group="polish 384 density benchmark" kind=cluster name="north terrace frontage" :: cluster "north terrace frontage" at reserve "north terrace" conform ground spacing 1 {
  ... 7 more handle(s); rerun with --limit 12 or filter with --group/--kind
authoring_catalog:
  total_matching: 69
  showing: 12
  groups/polish-384-plan.atf:78 group="polish 384 density benchmark" kind=style name=polish.accent :: minecraft:azalea
  groups/polish-384-plan.atf:61 group="polish 384 density benchmark" kind=style name=polish.arcade_landmark :: temple-spire
  groups/polish-384-plan.atf:64 group="polish 384 density benchmark" kind=style name=polish.arch_piece :: stone-arch
  groups/polish-384-plan.atf:63 group="polish 384 density benchmark" kind=style name=polish.column_piece :: ruin-column
  groups/polish-384-plan.atf:66 group="polish 384 density benchmark" kind=style name=polish.facade_house :: facade-house-wide
  groups/polish-384-plan.atf:65 group="polish 384 density benchmark" kind=style name=polish.facade_shop :: facade-shop-narrow
  ... 57 more catalog entries; rerun with --catalog-limit 69
quality_guardrails:
  reserve pads/clearings before placing landmarks or dense clusters
  keep routes readable and add crossings where routes enter water/lava hazards
  keep cluster members spaced by their radii; diagnostics reject cramped detail
  use generic style/component keys; do not add theme-specific core directives
current_gates:
  skipped: rerun with --with-gates or use plan-report for full validation
validation_commands:
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-diagnose examples/large-map-validation/world.atf
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-target-audit examples/large-map-validation/world.atf
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/plan-workbench-overlay.png --max 1024
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-trial examples/large-map-validation/world.atf <edited-world.atf>
  java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-report examples/large-map-validation/world.atf --profile dense
```

This is the intended pre-edit context for an LLM. It keeps the huge benchmark
out of the prompt, exposes the relevant source lines, lists the reusable
component/style vocabulary, and carries the quality constraints that protect
composition before detail.

Bounded source-snippet evidence:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet \
  examples/large-map-validation/world.atf \
  --name "east overlook ribs" \
  --kind cluster \
  --context 1
```

Current output:

```text
Plan source snippet for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
target: groups/polish-384-plan.atf:180 group="polish 384 density benchmark" kind=cluster name="east overlook ribs"
 179 |     }
 180 |     cluster "east overlook ribs" at reserve "east overlook" conform ground spacing 1 {
 181 |       component style.stair_tower_piece at -28 -18 radius 5 height 28 rotate 270
 182 |       component style.stair_tower_piece at 28 -18 radius 5 height 28 rotate 90
 183 |       component style.gallery_piece at 0 -28 radius 10 height 12 rotate 0
 184 |       repeat component style.wall_piece from -32 28 to 32 28 count 7 radius 2 height 3 rotate 0
 185 |       repeat component style.column_piece from -30 8 to 30 8 count 6 radius 1 height 7 rotate 0
 186 |       repeat component style.rib_piece from -20 36 to 20 36 count 4 radius 1 height 8 rotate 180
 187 |     }
 188 |     cluster "south quay cargo edge" at reserve "south quay" conform ground spacing 0 {
```

This is the intended second step after `plan-workbench`: pull exactly one
editable block into context, edit that block, then run diagnostics and the edit
trial. It keeps large-map source review local even when one semantic handle
expands into many generated ATF lines.

Catalog source-snippet evidence:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet \
  examples/large-map-validation/world.atf \
  --style polish.arch_piece \
  --group "polish 384 density benchmark" \
  --context 1
```

```text
Plan source snippet for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
target: groups/polish-384-plan.atf:64 group="polish 384 density benchmark" kind=style name="polish.arch_piece"
  63 |       column_piece ruin-column
  64 |       arch_piece stone-arch
  65 |       facade_shop facade-shop-narrow
```

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet \
  examples/large-map-validation/world.atf \
  --component stone-arch \
  --context 1
```

```text
Plan source snippet for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
target: components/stone-arch.atf:1 group="" kind=component name="stone-arch"
   1 | component "stone-arch" {
   2 |   bounds -4 0 -2 to 4 8 2
   3 |   fill -4 0 -2 to -2 6 2 minecraft:mossy_stone_bricks
   4 |   fill 2 0 -2 to 4 6 2 minecraft:mossy_stone_bricks
   5 |   fill -4 7 -2 to 4 8 2 minecraft:stone_bricks
   6 |   fill -1 6 -2 to 1 6 2 minecraft:chiseled_stone_bricks
   7 |   set 0 5 0 minecraft:lantern[hanging=true,waterlogged=false]
   8 | }
```

This closes another practical loop for LLM edits: the model can inspect a style
alias and then inspect the component bounds/body before reusing it in a cluster.
The workbench catalog also derives conservative cluster hints from component
bounds. For example, `stone-arch` is reported as:

```text
components/stone-arch.atf:1 group="" kind=component name=stone-arch :: bounds -4 0 -2 to 4 8 2, footprint=9x5, safe_cluster radius 4 height 8, source_lines=8
```

Those `safe_cluster` values are not new language semantics; they are bounded
authoring hints so an LLM can start with diagnostics-friendly radius/height
values when adding a new `component` or `repeat component` row.

Component recipe evidence:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-recipe \
  examples/large-map-validation/world.atf \
  --style polish.arch_piece \
  --group "polish 384 density benchmark" \
  --count 4
```

Current output:

```text
Plan component recipe for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf
style: groups/polish-384-plan.atf:64 polish.arch_piece -> stone-arch
component: components/stone-arch.atf:1 stone-arch
hint: bounds -4 0 -2 to 4 8 2, footprint=9x5, safe_cluster radius 4 height 8, source_lines=8
cluster_rows:
  component polish.arch_piece at 0 0 radius 4 height 8 rotate 0
  repeat component polish.arch_piece from -12 0 to 12 0 count 4 radius 4 height 8 rotate 0
```

This reduces another source of LLM error: the model can ask for a recipe from a
style alias and receive ordinary plan rows with safe radius/height values, then
adjust offsets/count/rotation for the local composition.

Recipe edit-trial evidence:

```text
cluster "recipe arch rhythm" at reserve "central polished plaza" conform ground spacing 0 {
  repeat component style.arch_piece from -24 38 to 24 38 count 6 radius 4 height 8 rotate 0
}
```

The first attempted paste used a wider row and `plan-diagnose` rejected it for
exceeding the circular plaza reservation. After tightening the span, diagnostics
passed:

```text
Plan diagnostics OK: no warnings
```

The same edit then passed the source-to-expanded edit gate:

```text
Plan edit trial for /Users/ben/code/anvil-tf/examples/large-map-validation/world.atf -> /Users/ben/code/anvil-tf/target/validation-trials/recipe-edit-arch/world.atf
source_changed_lines: 3
expanded_changed_lines: 7
plan_warnings: 0
core_warnings: 0
threshold: source_changed_lines <= 4, expanded_changed_lines >= 6, warnings = 0
result: pass
```

This is the intended ergonomics loop for recipe-assisted editing: ask for a
safe row, paste it into a reserved composition zone, let diagnostics catch an
overreach, adjust the local span, and keep the final edit small enough for code
review while still producing generated structure.

Generated-world evidence:

```text
Reference validation OK: 9 region file(s), 3180 chunk(s), 48123947 authored block check(s), 467429 authored biome check(s), 128 validated block entity tag(s)
Imported 3180 chunk(s) to target/validation-trials/imported-dense-reviewable as 100341 block command(s), 2674 biome command(s), 128 block entity command(s), 88 component(s), 980 component placement(s)
Round-trip comparison OK: 3180 chunk(s), 130252800 exact block check(s), 2035200 exact biome check(s), 128 block entity anchor check(s), 0 entity anchor check(s)
```

Imported-source reviewability metrics:

```text
Source metrics for /Users/ben/code/anvil-tf/target/validation-trials/imported-dense-reviewable
atf_files: 3270
group_files: 3180
component_files: 88
source_lines: 125363
nonblank_source_lines: 117633
include_lines: 3269
max_file_lines: 3272 (world.atf)
max_group_nonblank_lines: 258 (groups/imported-32-39.atf)
max_component_nonblank_lines: 154 (components/component-0005.atf)
```

Imported-source reviewability gates:

```text
Source gates for /Users/ben/code/anvil-tf/target/validation-trials/imported-dense-reviewable
passed: 6
failed: 0
pass group_file_size value=258 threshold=<=320 nonblank lines in largest group file note=groups/imported-32-39.atf
pass component_file_size value=154 threshold=<=220 nonblank lines in largest component file note=components/component-0005.atf
pass entrypoint_size value=3272 threshold=<=5000 lines in largest file note=world.atf
pass ordinary_groups value=3180 group file(s), 3180 group command(s) threshold=group files are ordinary ATF groups
pass component_extraction value=88 component file(s), 980 placement(s), 0 cluster(s) threshold=repeated structures are represented as components with place commands or plan clusters
pass block_compression value=66569 fill(s), 32792 set(s) threshold=fill commands are at least as common as raw set commands
```

The imported source is still large overall, which is expected for an exact
3,180-chunk reviewable import, but it is not a single monolith: every imported
chunk group remains under the same 320-line reviewability threshold, repeated
structures are promoted into 88 ordinary components, and exact round-trip
comparison still passes.

Render artifacts:

```text
target/large-map-validation-dense-overlay.png
target/large-map-validation-dense-map.png
target/large-map-validation-dense-slice-z512.png
```

Visual read: the dense quadrant preserves route readability while adding
frontage, quay, garden, arcade, and overlook detail inside reserved pads. The
vertical slice shows supported terrain and structures, and core diagnostics
report zero floating-structure warnings.

## Next Required Validation

The remaining risk is now mostly external validity: obtain a lawful target-class
world artifact or manually reconstruct a representative target-class source,
then run the same dense scoreboard, source gates, import/regenerate comparison,
and render review against that artifact. The public Kikoshi/Riverton reference
pages currently function as requirements evidence, not downloadable worlds. Also
run independent model-authored edit trials on the dense source to check whether
other models can make semantic changes without breaking diagnostics or visual
composition.
