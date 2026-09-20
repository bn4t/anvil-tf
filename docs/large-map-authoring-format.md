# Large Map Authoring Format

This note is a working proposal for making ATF practical for very large,
high-detail Minecraft maps such as Mineplex-scale Survival Games arenas. The
current ATF core is useful as an exact, reviewable block-world IR, but it is too
low-level for an LLM to ergonomically author a 512x512 island, castle town, or
temple map with hundreds of deliberate details.

The goal is not to replace ATF v1. The goal is to add a generic planning layer
that compiles to ordinary ATF groups and components, then through the existing
Anvil writer and validators.

## Target Evidence

Public descriptions and user-named target maps imply a higher bar than the
current examples:

- Kikoshi Islands: Oriental-themed Survival Games map, 512x512, approximately
  170 hours of human build time.
- Riverton Castle: riverside town, snowy mountains, four sectors, central castle,
  fortified south wall, approximately 80 hours of human build time.
- Mineplex Survival Games: 24+ players, cornucopia spawn, scattered loot, supply
  drops, shrinking border pressure, and large maps where exploration matters.
- Akushi Island and Aztec Island: user-named Mineplex-style targets that should
  be covered by the same generic island, hazard, landmark, style, and route
  machinery once a lawful source artifact or reliable public reference is
  available.

Reference pages:

- https://www.planetminecraft.com/project/survival-games-map-for-the-mineplex-network/
- https://www.planetminecraft.com/project/riverton-castle---survival-games-map-for-mineplex/
- https://mineplex.fandom.com/wiki/Survival_Games

Availability audit:

- The Kikoshi Islands page describes the map as Oriental themed, 512x512, built
  for Mineplex, with about 170 hours of build time. It does not expose an
  obvious world download in the public page text, and public comments ask for a
  download link.
- The Riverton Castle page describes a riverside town, snowy mountains, four
  sectors, central castle, fortified south wall, and about 80 hours of build
  time. It also does not expose an obvious world download in the public page
  text, and public comments ask where the download is.
- Therefore the current validation uses these pages as target-class
  requirements, not as imported reference worlds. Calling the format truly
  validated against real Mineplex maps still requires obtaining a lawful world
  artifact or manually reconstructing a representative target-class source.

Those targets require source that can express composition, terrain, routes,
landmarks, reserved build pads, repeated local detail, loot placement, and
iterative visual inspection. A format that only reduces block count is not
enough.

## Target Coverage Matrix

This matrix turns the public target evidence into concrete validation pressure.
It is deliberately phrased in generic plan/core terms, not theme-specific
language features.

| Target trait | Public or user evidence | Required generic format support | Current benchmark evidence | Remaining gap |
| --- | --- | --- | --- | --- |
| 512x512 authored island scale | Kikoshi page says the map is 512x512 and built for Mineplex. | `plan area`, masks, terrain fields, deterministic expansion, source gates. | `groups/kikoshi-512-plan.atf` is 513x513; dense report passes `minimum_authored_area` and `expanded_ratio`. | Not an imported Kikoshi world; still a representative benchmark. |
| Oriental/island identity with harbor/water details | Kikoshi page describes an Oriental Survival Games map and tags include ship, water, lantern, dojo. | Theme in `style`, palettes, and components; no `dojo`/`ship` core syntax. Water hazards and crossings remain generic. | Kikoshi group uses style keys, bay/water masks, harbor pad, routes, crossings, landmarks, clusters, scatter, and loot. | Component library needs more human-quality theme architecture before final claim. |
| Riverside castle town with sectors and wall | Riverton page describes a riverside town, snowy mountains, four sectors, central castle, and fortified south wall. | Multi-district groups, route network, reservations, landmark components, clusters, terrain boundaries. | Canal/castle group plus ridge group cover waterways, bridge routes, keep island, market/village/dock/garden pads, walls/retaining clusters, and target-class audit. | Need a hand-reconstructed Riverton-like source or lawful world artifact for direct comparison. |
| Survival Games gameplay scale | Mineplex wiki lists 24+ players; gameplay depends on map scouting, scattered chests, tiered chests, supply drops, border pressure. | Spawn landmark, route network, `loot` tiers, scatter/detail passes, world border metadata, render/diagnose loop. | Dense report passes route, loot, target-class, render, reference validation, and import round-trip gates; benchmark has 128 loot anchors and 19 routes. | Supply-drop behavior and border timing are gameplay/server concepts, not encoded in ATF v1 yet. |
| Lava/hazard island target | User named Aztec Island; public source not yet found in this pass. | Generic `lava`, hazard masks, crossings, route diagnostics, vertical landmarks. | Ridge/canal/polish benchmark includes lava/water hazard diagnostics and crossing checks without theme-specific syntax. | Need sourced Aztec/Akushi reference or a lawful artifact/manual reconstruction. |
| LLM edit ergonomics | Core goal requires source an LLM can author and edit. | Workbench, index, snippet, recipe, edit-trial, target-class gates. | `plan-workbench`, `plan-snippet`, `plan-recipe`, `plan-edit-trial`, `plan-edit-suite`, and 22-gate dense `plan-report` all pass on the benchmark. | Need independent model-authored edit trials beyond the current controlled examples. |

The matrix is not a claim that the benchmark visually equals Kikoshi, Riverton,
Akushi, or Aztec Island. It is a claim that the candidate format now has
executable pressure for the generic authoring capabilities those maps demand.

## Current ATF Failure Mode

ATF v1 has the right exact primitives:

- `group`, `origin`, `bounds`
- `component`, `place`, `template`
- `fill`, `set`, `line`, `cylinder`, `sphere`, `replace`
- `block_palette`, `fill_palette`, `scatter`, `surface_scatter`
- `biome`, `entity`, `block_entity`
- `render` and `diagnose`

The larger examples show the ergonomic limit. A map author has to manually hold
too many concerns in one file:

- island or arena silhouette,
- biome areas,
- route network,
- landmarks,
- terrain pads,
- scatter exclusions,
- chest tiers,
- rotations,
- local visual style,
- and the raw coordinates for every repeated placement.

Comments often carry the real design intent while executable ATF remains mostly
coordinate operations. That is survivable for a 320x320 or 384x384 playtest map;
it is brittle for a 512x512, 170-hour-build target.

## Candidate Formats

### Candidate A: Bigger ATF v1

Add more primitive shapes and loops directly to ATF v1.

Rejected as the main answer. It helps with isolated annoyances, but it still
asks the LLM to manage composition through coordinates. More shapes do not solve
path networks, reservations, terrain conformance, or "avoid placing trees on the
dojo roof."

### Candidate B: Domain-Specific Map Concepts

Add directives such as `road`, `district`, `castle`, `dojo`, `village`, or
`harbor`.

Rejected for the core language. It conflicts with the product direction that
the core should stay generic. These concepts can exist as reusable components,
templates, or libraries, but not as the primary review surface.

### Candidate C: External Procedural Code

Use Java, Python, Kotlin, or another general-purpose language to generate ATF.

Rejected as the primary authoring format. It is powerful, but most diffs become
algorithm diffs rather than map diffs. It also makes imports and LLM edits feel
like programming a generator instead of editing a world source of truth.

### Candidate D: Generic Plan Layer Compiling To ATF

Accepted as the direction to validate. The original sketch was intentionally
broader than the current implementation; the validated candidate shape below is
the narrower surface that has evidence behind it.

The plan layer introduces generic spatial concepts:

- 2D `mask`s for areas,
- numeric `field`s for terrain and influence,
- named `route`s for paths and sightlines,
- `reservation`s for pads and no-overlap constraints,
- surface-aware landmark, cluster, scatter, and loot passes,
- style palettes that select block palettes and components,
- diagnostics that understand masks, routes, pads, and placements.

It compiles deterministically to ordinary ATF v1. The generated ATF remains
boring: `groups/`, `components/`, local coordinates, `fill`, `set`, `slice`,
`place`, entities, block entities, and biomes. The plan source is the editable
artifact; expanded ATF and Anvil worlds are build output.

## Validated Candidate Shape

The current validation work supports a narrower, more concrete candidate than
the first sketch above:

```text
ATF v1 core remains the exact block-world IR.
plan { ... } is the large-map authoring layer.
components/ and style libraries carry theme, architecture, and map identity.
plan-expand emits ordinary ATF; Anvil generation never consumes plan constructs directly.
```

This should be treated as the candidate format until a validation gate proves it
wrong. The split matters:

- **Core ATF v1:** `world`, `include`, `dimension`, `group`, `component`,
  `origin`, `bounds`, `fill`, `set`, `fill_palette`, `biome`, `entity`,
  `block_entity`, `place`, and generated-world validation. This layer stays
  exact and import-friendly.
- **Plan core:** generic spatial authoring constructs that an LLM can edit
  semantically: `style`, `use_style`, `mask`, `field`, `terrain`, `water`,
  `lava`, `clear`, `fill_biome`, `route`, `crossing`, `reserve`, `landmark`,
  `cluster`, `scatter_in`, and `loot`.
- **Libraries and examples:** reusable components, palettes, naming
  conventions, material styles, and map-specific composition patterns. This is
  where a Kikoshi-like harbor, Riverton-like castle wall, Aztec-like temple, or
  Akushi-like island identity belongs.

The candidate deliberately excludes these from the plan core:

- `castle`, `town`, `district`, `road`, `harbor`, `temple`, `dojo`, `ship`,
  `interior`, `room`, or other map-theme nouns,
- Anvil internals such as chunk sections, palettes, packed arrays, heightmaps,
  or region coordinates,
- arbitrary procedural code as the primary source of truth,
- importer-only labels such as `detected`, `residual`, or `human`.

The validation evidence so far favors this split for three reasons:

1. **Scale compression:** the current four-plan validation world covers 761,220
   plan cells and expands to 29,283 ordinary ATF lines from 733 plan lines.
2. **Edit locality:** moving a major pad in the dense polish benchmark is a
   two-line plan edit but a 771-line expanded-ATF diff.
3. **Round-trip compatibility:** generated worlds import back into ordinary
   reviewable ATF and exact-compare across 3,180 chunks, including blocks,
   biomes, and block-entity anchors.

### Candidate Grammar Surface

This is the implemented plan surface that should be considered "validated
enough to keep testing":

```atf
plan {
  area min_x min_z max_x max_z

  style name {
    key block_or_palette_or_component
  }
  use_style name

  mask name = radial x z radius r noise n seed s
  mask name = circle x z radius r
  mask name = rect min_x min_z max_x max_z
  mask name = outside other
  mask name = left + right
  mask name = left - right
  mask name = left & right

  field name = terrain mask base y amplitude a scale s octaves n seed s
  field name = ridge x1 z1 x2 z2 ... width w height h
  field name = max left right

  terrain mask field field_name from y top block_or_style under block_or_style
  water [outside] mask from y1 to y2 block_or_style
  lava [outside] mask from y1 to y2 block_or_style
  clear [outside] mask from y1 to y2
  fill_biome mask y1 y2 biome

  route "name" width n palette palette_or_style conform field [centerline block_or_style] seed s {
    point x z
    crossing x z radius r
    close
  }

  reserve "name" circle x z radius r conform field floor block_or_style [floor_palette palette_or_style seed s] [edge block_or_style edge_width n] clear_to y require_route n
  reserve "name" rect min_x min_z max_x max_z conform field floor block_or_style [floor_palette palette_or_style seed s] [edge block_or_style edge_width n] clear_to y require_route n

  landmark "name" component component_or_style at x z conform field reserve "reservation" radius r height h rotate deg

  cluster "name" at reserve "reservation" conform field spacing n {
    component component_or_style at dx dz radius r height h rotate deg
    repeat component component_or_style from dx1 dz1 to dx2 dz2 count n radius r height h rotate deg
  }

  scatter_in mask count n seed s avoid_routes r avoid_reservations r conform field {
    component component_or_style weight n
    set block_or_style weight n
  }

  loot mask tier n count n seed s avoid_routes r avoid_reservations r conform field
}
```

Some syntax in earlier sketches, such as `region`, `stamp`, `scatter_along`,
`material`, `surface`, `terrace`, and nested `choose`, remains useful but
unvalidated. It should not be promoted until it passes the same ergonomics,
diagnostics, render, import, and exact-compare gates.

## Source Layout

Do not add special top-level folders. A large map still uses:

```text
world.atf
meta.atf
groups/
  terrain.atf
  spawn.atf
  east-temple.atf
  harbor.atf
components/
  lantern-post.atf
  bridge-small.atf
  temple-roof-cap.atf
```

High-level plan directives live in normal group files. A group may contain core
ATF directives, plan directives, or both. The compiler expansion still emits
ordinary group edits.

## Proposed Generic Directives

The exact syntax should be tested, but the shape should stay close to this:

```atf
group "kikoshi scale island terrain" {
  origin -256 0 -256
  bounds 0 0 0 to 512 160 512

  let sea_y = 58
  let base_y = 64

  style kikoshi {
    top minecraft:grass_block[snowy=false]
    under minecraft:dirt
    path path_mix
    spawn_floor minecraft:smooth_stone
    light lantern-post
  }

  style volcanic {
    top minecraft:blackstone
    under minecraft:basalt
    path volcanic_path_mix
    spawn_floor minecraft:polished_blackstone
    light lantern-post
  }
  use_style kikoshi

  mask island = radial 256 256 radius 238 noise 0.28 seed 100
  mask bay = circle 260 438 radius 68
  mask playable = island - bay

  field height = terrain playable base $base_y amplitude 24 scale 42 octaves 4 seed 101
  field ridge = ridge through 76 220 160 138 260 92 360 132 width 56 height 32
  field ground = max height ridge

  material playable from 0 to ground-4 style.under
  surface playable at ground style.top seed 102
  water bay from 40 to $sea_y minecraft:water[level=0]

  route "main loop" width 7 palette path conform ground {
    point 256 256
    point 166 208
    point 112 318
    point 220 432
    point 360 392
    point 414 246
    point 318 164
    crossing 260 420 radius 84
    close
  }

  reserve "spawn plaza" circle 256 256 radius 46 clear_to ground require_route 8
  reserve "harbor pad" rect 208 398 314 470 clear_to $sea_y+2 require_route 10
}
```

Notes:

- `mask`, `field`, `route`, and `reserve` are generic. They do not encode
  city, island, castle, or temple concepts.
- `material` and `surface` compile to fills, palette fills, and terrain columns.
- `route` compiles to path masks, palette fills, edging, and optional ramps.
- `crossing` marks an intentional hazard crossing inside a route. Water, lava,
  moat, river, bay, and canal areas stay generic hazards; route diagnostics can
  reject accidental crossings without adding map-theme concepts.
- `reserve` creates explicit no-overlap and clearing constraints before details
  are placed.
- `require_route n` on a reservation asks diagnostics to reject isolated
  landmark pads that have no route within `n` blocks.
- `landmark` places a reusable component on a reserved pad, conforming the
  placement height to a field and using generic footprint/height metadata for
  diagnostics. The component can be a castle tower, temple spire, harbor watch,
  village house, or anything else; that theme remains in `components/`, not the
  core plan vocabulary.
- `cluster` places several reusable components around an anchor inside one
  reservation. It uses local offsets plus generic radius/height metadata to
  check reservation fit, vertical clearance, and internal spacing without adding
  city, dock, ruin, or village concepts to the language. A cluster can anchor to
  the reservation center with `at reserve "name"`, so moving the reservation can
  move the whole local component group without changing every member offset.
  Inside a cluster, `repeat component ... from dx dz to dx dz count n` expands a
  line of repeated local members for walls, docks, market rows, ruins, and other
  rhythmic details.
- `style` is a generic bundle of block, palette, and component values. A command
  may refer to `style.key`, and `use_style name` selects the active value map.
  Style swaps should preserve route, reservation, scatter, and loot anchors
  unless the author intentionally edits those structures.

## Future Extension: Region-Level Authoring

This is not part of the validated candidate yet. It records a possible future
extension if group-level plans become too large. A landmark group should still
read as intent plus constraints, not a wall of coordinates:

```atf
group "east temple cliffs" {
  origin -256 0 -256
  bounds 320 56 112 to 484 132 276

  use_style kikoshi

  region "east temple cliffs" within circle 400 188 radius 82 {
    terrace ground levels 3 step_height 4 retaining minecraft:mossy_stone_bricks
    route "stair approach" width 5 palette path conform ground {
      point 342 242
      point 370 220
      point 392 188
    }

    reserve "temple pad" rect 374 166 430 218 clear_to ground+1 margin 10 require_route 8
    stamp temple-foundation at 374 ground 166 size 56 52 palette path
    place temple-main at 386 ground+1 176 rotate 270

    scatter_in region count 34 seed 310 avoid_routes 4 avoid_reservations 6 {
      choose {
        weight 4 { place pine-tree on_surface rotate random_90 }
        weight 2 { place lantern-post on_surface face nearest_route }
        weight 1 { set 0 0 0 minecraft:fern }
      }
    }

    loot tier 1 count 5 avoid_routes 3 visible true seed 311
    loot tier 2 count 1 within "temple pad" visible false seed 312
  }
}
```

This would still be generic if validated: `temple-main` is just a component,
and the language would know about ownership regions, paths, reservations,
surface placement, and loot anchors. Until then, major zones should be expressed
with ordinary group files, masks, reservations, landmarks, clusters, and styles.

## Validation Gates

A candidate format only works if it passes all of these gates.

### Gate 1: Source Ergonomics

For a 512x512 Survival Games map brief:

- `world.atf` stays mostly includes.
- Each major zone fits in one reviewable group file.
- A group file is organized by composition pass: terrain, routes, reservations,
  structures, transitions, details, gameplay.
- Repeated placements are expressed by `route`, `cluster`, `scatter_in`, or
  component variants, not by long coordinate lists.
- Arbitrary literals are localized. A reviewer can identify why an anchor exists
  from its group, route, or reservation name.

Initial measurable thresholds for a validation benchmark:

- no more than 12 authored group files for a 512x512 arena skeleton,
- no authored group file over 300 non-comment lines,
- no contiguous list of more than 10 raw `place`/`set` coordinate anchors unless
  the list is an intentionally named gameplay fixture such as spawn pads,
- at least 80 percent of repeated detail placements generated from named routes,
  masks, clusters, or reservations,
- every major landmark has a named reservation and route relationship.

Failure signal: the source looks like `examples/playtest-survival-games` scaled
up to thousands of lines of manual `place` and `fill` calls.

### Gate 2: Expressiveness

The format must express these benchmark maps without special-case directives:

- Kikoshi-like 512x512 island: irregular coastline, bay, main loop, bridges,
  water features, ship/harbor, multiple landmark compounds, dense lantern/tree
  detail, hidden and visible loot.
- Riverton-like castle town: river valley, snowy mountain boundary, four town
  sectors, central castle, fortified wall, bridges, streets, interiors, loot.
- Aztec-like lava island: stepped temples, lava fields/channels, parkour routes,
  hazard-aware loot, ruins, vertical silhouettes.

Failure signal: adding any of these requires new concepts such as `castle`,
`dojo`, `aztec_temple`, or `district` in the core language.

### Gate 3: Determinism And Diffs

- Plan expansion is deterministic from source plus seeds.
- Same source produces byte-equivalent expanded ATF and equivalent Anvil output.
- Changing one group or zone does not churn unrelated generated groups.
- Generated names are stable and boring.
- Reviewers can diff the plan source and understand the design change.

Validation commands should prove this by expanding the same plan twice and
comparing the expanded ATF directory before generating Anvil output.

Failure signal: small edits reshuffle scatter, rename generated artifacts, or
rewrite unrelated groups or zones.

### Gate 4: Visual And Gameplay Diagnostics

Diagnostics must understand plan concepts:

- component placed outside its reservation,
- reservation overlaps another reservation,
- route slope too steep,
- route interrupted by water/lava without a crossing,
- scatter placed on route, roof, water, lava, or reserved pad,
- chest unsupported, unreachable, hidden when marked visible, or too clustered,
- landmark has no route within a configured distance,
- player spawn route choices are unclear or blocked.

The existing `render map`, `render top`, and `render slice` loop remains the
generated-world visual proof path. `plan-render` adds the matching pre-expansion
composition overlay for masks, hazards, routes, crossings, reservations,
scatter, and loot anchors.

Failure signal: a map compiles but the only way to find composition errors is to
manually inspect thousands of blocks.

### Gate 5: Round-Trip Compatibility

The plan layer does not have to round-trip from an arbitrary imported map on day
one. The expanded ATF and generated Anvil output must still be compatible with
existing validation:

```text
plan source -> expanded ATF -> generated Anvil -> reference validation
```

For imported maps, the importer should continue to emit ordinary ATF first. A
future summarizer may propose plan-layer refactors, but exact round-trip must
win over clever reconstruction.

Failure signal: the plan layer creates a separate dialect that imported maps
cannot share or that bypasses exact block/entity/block-entity comparison.

### Gate 6: LLM Edit Trials

Run adversarial editing tasks against a benchmark map:

- add a new landmark without breaking route flow,
- move a route and preserve reachable loot,
- increase tree density without placing trees on roads or structures,
- add a bridge over a hazard channel,
- replace a group/zone style while keeping gameplay anchors stable,
- fix a render-detected hole or overlap.

Pass condition: the LLM can make the edit in the intended group file, with a
small diff and passing diagnostics, without needing to manually inspect the
whole map.

Failure signal: edits require global coordinate reasoning or produce unrelated
layout churn.

## Practical Acceptance Gates

Before calling the format practical for Akushi/Kikoshi/Riverton/Aztec-class
maps, the candidate must pass these concrete gates on at least three distinct
large-map benchmarks:

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

These gates intentionally mix qualitative and quantitative evidence. A format
that only hits the line-count targets but fails render review is not practical.
A format that looks good but requires hundreds of manual coordinate edits is
also not practical.

The current validation world satisfies these gates as a validation skeleton:

```text
authored_area: 512x512 + 384x384 + 448x448 + 384x384 plan groups
plan_diagnostics: pass
core_diagnostics: pass
determinism: pass
reference_validation: pass
render_review: pass for map/overlay/slice artifacts
import_round_trip: pass across 3180 chunks
edit_locality: pass on the dense polish east-overlook move
theme_purity: pass; theme lives in component/style names
```

It is still incomplete as final proof because the examples are controlled
validation benchmarks, not imported or hand-reconstructed target-class maps.
The remaining proof needs richer real-map content and independent LLM edit
trials, not a different core language. The standard for those trials is defined
in `docs/large-map-independent-eval-protocol.md`.

`plan-gates <input.atf>` reports the automatic subset of these gates directly.
It expands twice to check determinism, diagnoses the expanded ATF, and reports
manual gates when evidence is not supplied. `--profile dense` adds stricter
finished-map stress gates for detail density, cluster density, landmark count,
loot spread, and visible cluster footprint. The same command can attach
evidence with `--edit-trial`, `--reference-world`, `--render-artifact`, and
`--compare-worlds`, turning the generated-world and edit-locality gates into
measured pass/fail lines.
`plan-edit-trial <base.atf> <edited.atf>` measures the edit-locality gate by
comparing changed authored ATF lines against changed expanded ATF lines and by
checking plan/core diagnostics on the edited source.
By default it measures a tight move edit (`<=5` source lines and `>=100`
expanded lines). For richer additive edits, pass `--max-source-lines` and
`--min-expanded-lines` so the trial can validate compact source growth without
pretending that adding a new set piece is as small as moving one coordinate.
`plan-edit-suite <base.atf> <edited.atf>...` repeats that check across several
semantic edits and fails if any edit breaks locality or diagnostics. It accepts
the same `--max-source-lines` and `--min-expanded-lines` flags for additive edit
suites.
`plan-edit-manifest <base.atf> <manifest.tsv>` scores a mixed edit-eval batch
where each row can set its own thresholds. The manifest format is tab-separated:
`name`, edited `world.atf`, max source lines, min expanded lines, and optionally
the expected changed source file. This is the intended bridge from controlled
examples to independent model-authored edit trials: each model output can be
added as a row and scored by the same harness, including whether it changed the
intended group file.
`plan-edit-manifest-report <base.atf> <manifest.tsv> <out.md>` runs the same
full scorer and writes a Markdown score table for preserving model-eval
evidence.
`plan-eval-status <base.atf> <manifest.tsv>` checks a model-eval manifest
without expanding worlds. It reports `missing`, `unedited`, `wrong_file`, or
`ready` for each row by comparing changed source files against the expected file
column, so an operator can triage partial independent-model batches before
running the heavier scorer.
`plan-eval-status-report <base.atf> <manifest.tsv> <out.md>` writes that same
readiness check as a Markdown table for attaching model-eval progress to the
validation record.
`examples/large-map-validation/edit-eval-prompts.md` contains prompt-ready
tasks that pair model instructions with workbench/snippet/recipe context
commands and matching manifest rows.
`plan-eval-pack <base.atf> <tasks.tsv> <out-dir> [--run-prefix name] [--with-context] [--context-lines n] [--prepare-runs] [--overwrite-runs]`
writes one Markdown brief per task, a pack-level `README.md`, a `run-notes.md`
metadata template, and a batch `manifest.tsv`. This is the intended handoff
artifact for independent model-authored trials: the pack gives each model run
bounded context, exact output paths, a direct diagnostics command, and a
manifest row that can later be scored with `plan-edit-manifest`.
`--prepare-runs` also copies the base source tree into each task's run directory
so models can edit in place; existing run directories are refused unless
`--overwrite-runs` is passed.
`plan-eval-brief <base.atf> <tasks.tsv> <task-id> [--run name] [--with-context] [--context-lines n]`
renders one row from `examples/large-map-validation/edit-eval-tasks.tsv` as a
ready-to-send edit brief, including the model prompt, context commands, output
contract, direct `plan-diagnose` command, manifest row, and direct
`plan-eval-score` command for the named output run. With `--with-context`, it
embeds live `plan-workbench`, `plan-snippet`, or `plan-recipe` output so the
brief can be handed to a model without a separate context-gathering step. The
embedded context is capped at 160 lines per command by default to keep large-map
briefs prompt-sized; use `--context-lines n` to tune the cap or
`--context-lines 0` for full context.
`plan-eval-score <base.atf> <tasks.tsv> <task-id> <edited.atf>` scores one model
output directly against the task's thresholds and expected changed source file,
without editing the batch manifest.
`source-metrics <input.atf-or-dir>` and `source-gates <input.atf-or-dir>` check
the ordinary ATF source shape after authoring or import: group file size,
component file size, entrypoint size, ordinary group layout, repeated-structure
extraction, and fill/set compression.
`plan-outline <input.atf>` prints a compact composition outline for LLM editing:
each plan group's routes, reservations, landmarks, clusters, scatter, and loot
without expanding into raw block commands.
`plan-index <input.atf>` prints the matching source handles as `file:line`
entries for every top-level route, reservation, landmark, cluster, scatter pass,
and loot pass. Use it after `plan-outline` when an edit needs to touch the
actual source: the model can search for a named feature, jump to one line, and
avoid scanning the generated ATF body.
`plan-workbench <input.atf>` is the compact LLM edit context. It combines the
composition summary, filtered editable handles, reusable component/style
catalog, quality guardrails, optional automatic gates, and validation commands.
Use `--group`, `--kind`, `--limit`, and `--catalog-limit` to scope the context
to one district, one command type, or a small number of source handles before
asking an LLM to edit. It skips gate evaluation by default so context gathering
stays fast; add `--with-gates` when the edit brief should include the automatic
scoreboard inline.
`plan-snippet <input.atf>` prints one line-numbered source block selected by a
handle name or direct `file:line` pair. Use it after `plan-workbench` when a
route, cluster, scatter pass, or other multi-line handle needs its complete
bounded source in context before editing. It can also inspect catalog entries
with `--style style.key` or `--component name`, which lets an LLM verify aliases,
component bounds, safe cluster radius/height hints, and component bodies before
adding detail.
`plan-recipe <input.atf>` turns one component or component-valued style alias
into ready-to-paste `component` and `repeat component` rows using the safe
radius/height hints from the catalog. It is intentionally scaffolding, not new
syntax: the generated rows are ordinary plan `cluster` members.
`plan-target-audit <input.atf>` validates each 384x384-or-larger plan group
individually against target-class authoring thresholds. It checks that every
large district has its own route network, hazard crossings, reservations,
landmarks, distinct landmark components, clusters, repeated detail, loot, and
clean plan diagnostics. This catches a failure mode that aggregate gates can
miss: a huge source can look strong overall while one district is still too
thin for LLM editing. The same check is included as the `target_class_groups`
gate in `plan-gates` and `plan-report`.
`plan-report <input.atf>` is the preferred reproducible validation entry point.
It combines scale metrics, composition summaries, editable index counts, and the
same gate scoreboard used by `plan-gates`. It accepts the same evidence flags as
`plan-gates`, so a report can be upgraded from `status: evidence_pending` to
`status: pass` only when edit-locality, render, reference-world, and import
round-trip evidence have all been attached.

## Validation Scoreboard

Each candidate format should produce a small report for every benchmark:

```text
candidate: generic-plan-layer
benchmark: kikoshi-512-skeleton
source_files: 9
source_lines: 1370
max_group_lines: 244
manual_anchor_runs_over_10: 0
route_count: 7
reservation_count: 19
zone_count: 6
deterministic_expand: pass
diagnose_plan: pass
diagnose_core: pass
render_map: pass
plan_overlay: pass
render_top_spawn: pass
render_slice_landmark: pass
generate_validate: pass
llm_edit_trials: 5/6 pass
known_failures: []
```

This report is the validation artifact. A candidate is not "found" just because
the syntax looks pleasant; it is found when it repeatedly produces reports like
this for maps in the target class.

## Benchmark Runbook

The first validation campaign should use three authored benchmarks and one
import-oriented benchmark.

### Benchmark 1: Kikoshi-512 Skeleton

Required features:

- 512x512 island or island cluster with irregular coast.
- Central spawn/cornucopia with 24 player starts.
- At least six named zones with distinct silhouettes.
- Main route loop plus at least four branch routes.
- Harbor or ship area, highland/waterfall area, temple/compound area, forest
  area, bridge or boardwalk area.
- At least 120 repeated details from scatter or route placement.
- At least 35 loot anchors split by tier and visibility.

Pass evidence:

- plan source passes the Gate 1 thresholds,
- expanded ATF is deterministic,
- `diagnose` has no unsupported chests, floating wall attachments, or plan
  overlap warnings,
- `render map` shows coherent island silhouette, routes, and landmark spacing,
- at least one vertical slice confirms terrain/support under a landmark.

### Benchmark 2: Riverton Sector And Castle

Required features:

- 384x384 or larger riverside town sector.
- Snowy mountain boundary.
- River crossing network.
- Four named town sectors or neighborhoods expressed as ordinary groups, masks,
  reservations, and route networks.
- Central castle compound with walls, gate, courtyards, and at least two
  vertical landmark silhouettes.
- Fortified edge wall.

Pass evidence:

- the same generic directives used for Kikoshi also express the town/castle map,
- no new core directive mentions `castle`, `town`, `road`, or `district`,
- route diagnostics catch blocked bridges and unreachable loot.

### Benchmark 3: Aztec Lava Arena

Required features:

- Temple or stepped-pyramid components on prepared reservations.
- Lava field or lava channels as hazards.
- Parkour or bridge routes over hazards.
- Hazard-aware loot placement.
- Vertical silhouettes visible in `render slice`.

Pass evidence:

- the route/reservation diagnostics can distinguish valid hazard crossings from
  blocked routes,
- scatter can avoid lava, roofs, routes, and reserved pads,
- the same terrain/mask/field primitives still work.

### Benchmark 4: Imported Detailed Map Analysis

Required features:

- import a legally available detailed map into ordinary ATF,
- run existing exact comparison after regeneration,
- measure what current component extraction finds,
- manually identify 10 repeated higher-level patterns that the importer does
  not yet promote.

Pass evidence:

- exact import/regeneration remains the source of truth,
- proposed plan-layer abstractions can describe the manually identified
  patterns without changing imported output,
- no semantic labels such as "human", "detected", or "residual" enter the core
  language.

This benchmark validates compatibility with handmade maps without pretending
the first plan-layer importer can infer full human intent.

## MVP To Implement First

The smallest useful validation slice is:

1. `mask` primitives: `circle`, `rect`, `polygon`, `radial`, union, subtract,
   intersect.
2. `field` primitives: constant, noise terrain, ridge along polyline, max/min.
3. `surface`/`material` terrain expansion into v1 block fills.
4. `route` as named polyline with width, path palette, conform-to-field height.
5. `reserve` with overlap diagnostics.
6. `scatter_in` with `avoid_routes`, `avoid_reservations`, surface conformance,
   deterministic random rotation, and component choices.
7. `landmark` component placement on reserved pads with footprint and clearance
   diagnostics.
8. `cluster` multi-component placement on reserved pads with spacing,
   footprint, and clearance diagnostics.
9. Render overlays for masks, routes, reservations, landmarks, clusters, and
   loot anchors.

This is enough to validate a Kikoshi-like 512x512 island skeleton: coastline,
heightfield, route loop, pads, landmark components, small component clusters,
wall/dock/ruin/garden detail kits, facade/building frontage components, trees,
lanterns, and loot. It is deliberately not enough to infer beautiful buildings
from theme words; those remain reusable components and templates.

## First Concrete Experiment

Build one deliberately narrow prototype before expanding the language:

```text
examples/large-map-validation/
  world.atf
  meta.atf
  groups/
    kikoshi-512-plan.atf
  components/
    marker-tower.atf
    lantern-post.atf
    bridge-small.atf
```

The prototype should include only the MVP directives above and should expand to
ordinary ATF before generation. The validation target is not beauty yet; it is
whether a 512x512 map skeleton can be authored through named masks, fields,
routes, reservations, and scatter instead of hand-managed coordinate grids.

Acceptance checklist for the first experiment:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/expanded-large-map-validation-a
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-expand examples/large-map-validation/world.atf target/expanded-large-map-validation-b
diff -ru target/expanded-large-map-validation-a target/expanded-large-map-validation-b
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-render examples/large-map-validation/world.atf --out target/large-map-validation-plan-overlay.png
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar diagnose target/expanded-large-map-validation-a/world.atf
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render map target/expanded-large-map-validation-a/world.atf --out target/large-map-validation-map.png
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar render slice target/expanded-large-map-validation-a/world.atf x 256 --out target/large-map-validation-slice.png
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar target/expanded-large-map-validation-a/world.atf target/playtest-worlds/large-map-validation --validate
```

`plan-expand` writes expanded ATF only, not Anvil output, so reviewers can
inspect the lowered source and determinism can be tested before world
generation. `plan-render` reads the plan layer directly, so composition can be
inspected before the source is lowered into thousands of ordinary block edits.

## Implementation Shape

Prefer a separate expansion phase before the current parser mutates
`MutableWorld`.

```text
plan source
  -> parse plan directives into an intermediate plan model
  -> expand deterministic terrain/routes/scatter into ATF v1 operations
  -> parse/apply expanded ATF with existing machinery
  -> render/diagnose/generate as today
```

This keeps the existing writer, validator, importer, and core directives useful.
It also means the first implementation can live beside `DslParser` without
forcing every current command to understand masks and fields.

## Decision

Validate Candidate D: a generic, deterministic plan layer that compiles to ATF
v1.

Do not validate domain-specific core concepts. Do not replace ATF with a general
programming language. Do not consider the format successful until it passes the
benchmark gates above on at least one 512x512 Kikoshi-like map and one
Riverton-like sector/castle map.
