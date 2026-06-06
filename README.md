# anvil-tf

`anvil-tf` is a Java compiler for Minecraft Java Edition worlds. It turns ATF
source into valid Anvil region files, and it can import existing Anvil worlds
back into reviewable ATF source.

The design goal is "Terraform for Minecraft worlds" in spirit, not syntax:

- ATF is the source of truth.
- Generated Anvil files are build output.
- World source should be checkable into Git.
- Diffs should be meaningful to humans and LLMs.
- Imported handmade maps and generated maps use the same language.

## Source Layout

The reviewable layout is intentionally small:

```text
world.atf
meta.atf
groups/
  spawn-plaza.atf
  imported-111-12.atf
components/
  lamp-post.atf
```

`world.atf` is the entry point. `meta.atf` carries world metadata. `groups/`
contains placed edits with local coordinates. `components/` contains reusable
structures that can be included and placed.

Reviewable imports do not store datapacks, scoreboards, playerdata, map item
data, icons, resource packs, or arbitrary passthrough assets yet. The importer
reports skipped non-block save data and focuses on blocks, biomes, entities,
block entities, and metadata needed to generate a valid world.

## CLI

```text
java -jar anvil-tf.jar <input.atf> <output-world-dir> [--validate]
java -jar anvil-tf.jar diagnose <input.atf>
java -jar anvil-tf.jar validate <world-dir>
java -jar anvil-tf.jar compare <source-world-dir> <regenerated-world-dir>
java -jar anvil-tf.jar import <world-dir> <output.atf-or-dir> [--chunk-limit n] [--layout flat|reviewable]
java -jar anvil-tf.jar render top <input.atf> <y> [--dim id] [--out file.png] [--scale n] [--max n]
java -jar anvil-tf.jar render map <input.atf> [--biome] [--dim id] [--out file.png] [--scale n] [--max n]
java -jar anvil-tf.jar render slice <input.atf> x|z <coord> [--dim id] [--out file.png] [--scale n] [--max n]
```

### Inspecting a generated world

The `render` commands let an author (human or LLM) *see* a world without launching Minecraft, so gaps,
mistakes, and ugliness become visible and can be corrected in a generate → render → look → fix loop:

- `render map` — top-down surface projection ("what you'd see from above"). Holes read as empty space.
- `render top <y>` — a top-down floor plan at one height.
- `render slice x|z <coord>` — a vertical cross-section to check wall heights, floors, ceilings, and
  floating structures.

With no `--out`, each command prints an ASCII grid with a glyph legend (token-cheap, readable by any
model). With `--out file.png` it writes a colored PNG with a color legend for a multimodal model to view.
Views auto-crop to the authored bounds; `--scale` sets pixels per block and `--max` caps the longest side.

`diagnose` parses ATF source and reports generic authoring warnings before
generation, such as block entities anchored to the wrong block type or
wall-mounted blocks with no authored support behind them.

## Format Sketch

ATF is whitespace-oriented. Strings use quotes, comments start with `#`, and
most commands are one directive followed by arguments. Coordinates inside a
`group`, `component`, or `at` block are local to that block unless stated
otherwise.

```atf
world "Example" {
  include "meta.atf"
  include "components/lamp-post.atf"
  include "groups/spawn-plaza.atf"
}
```

```atf
version 3465
min_y -64
height 384
spawn 0 80 0
weather {
  raining false
  rain_time 0
  thundering false
  thunder_time 0
  clear_weather_time 0
}
world_border {
  center 0.0 0.0
  size 5.9999968E7
  size_lerp_target 5.9999968E7
  size_lerp_time 0
  warning_blocks 5
  warning_time 15
  damage_buffer 5.0
  damage_per_block 0.2
}
default_biome minecraft:plains
```

```atf
component "lamp-post" {
  bounds 0 0 0 to 0 4 0
  fill 0 0 0 to 0 3 0 minecraft:dark_oak_fence
  set 0 4 0 minecraft:lantern[hanging=true]
}
```

```atf
group "spawn plaza" {
  origin -16 64 -16
  bounds 0 0 0 to 31 12 31

  fill 0 0 0 to 31 0 31 minecraft:stone_bricks
  place lamp-post at 4 1 4
  place lamp-post at 27 1 4

  slice y=1 {
    fill 8 8 to 23 8 minecraft:polished_andesite
    set 15 15 minecraft:gold_block
  }

  block_entity 15 2 15 minecraft:chest {
    item Items slot 0 minecraft:bread count 3
  }
}
```

## ATF v1 Reference

`world "name" { ... }` is the root entry point. It may contain metadata,
includes, groups, components, or direct edits. In the reviewable source layout,
prefer keeping `world.atf` as a short list of includes.

`include "path.atf"` parses another ATF file relative to the current file. The
parser is single-pass, so include reusable components before groups that place
them.

Metadata directives:

```atf
version 3465
min_y -64
height 384
spawn 0 80 0
time 0
day_time 6000
game_mode creative
difficulty peaceful
hardcore false
allow_commands true
default_biome minecraft:plains
game_rule doMobSpawning false
```

`group "name" { ... }` defines placed world edits. `origin x y z` moves the
group's local coordinate system to world coordinates. `bounds x y z to x y z`
documents the intended local extent; it is checked by humans and tools, not
stored in the generated Anvil data.

`component "name" { ... }` defines reusable local blocks. A component does not
write anything until a group or another local scope places it:

```atf
place lamp-post at 20 1 8
place lamp-post at 28 1 8 rotate 90
```

`dimension minecraft:the_nether { ... }` creates or edits another dimension
using the same directives as the overworld. Dimensions are not a second block
language; groups, components, entities, block entities, biomes, fills, slices,
and control flow all work the same way:

```atf
dimension minecraft:the_nether {
  min_y 0
  height 256
  default_biome minecraft:nether_wastes

  group "nether spawn" {
    origin 0 64 0
    fill -8 0 -8 to 8 0 8 minecraft:blackstone
  }
}
```

Block directives:

```atf
set 1 2 3 minecraft:stone
fill 0 0 0 to 15 0 15 minecraft:stone_bricks
replace 0 0 0 15 8 15 minecraft:stone minecraft:mossy_stone_bricks
```

`slice y=12 { ... }` is a 2D convenience inside local scopes. Its inner
coordinates are `x z`, which keeps imported handmade layers readable:

```atf
slice y=12 {
  fill 0 4 to 15 4 minecraft:oak_planks
  set 7 7 minecraft:lantern[hanging=false]
}
```

Entities and block entities use local coordinates and typed NBT payloads:

```atf
block_entity 4 2 4 minecraft:chest {
  item Items slot 0 minecraft:bread count 3
}

entity 4.5 2.0 4.5 minecraft:armor_stand {
  byte Marker 1
}
```

Blocks are written as normal resource IDs, with optional state properties:
`minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]`.
Bare vanilla names such as `stone` are accepted as `minecraft:stone`.

The important rule: ATF does not expose Anvil internals as the review surface.
There are no numeric block palettes, packed arrays, heightmaps, chunk sections,
or region coordinates in normal source. If the importer falls back to
chunk-sized files, those files are still ordinary `group`s with local
coordinates.

## Supported Directives

### Metadata

These directives affect `level.dat` or generated world defaults:

```atf
version 3465
min_y -64
height 384
spawn 0 80 0
time 24000
day_time 6000
game_mode survival
difficulty hard
hardcore false
allow_commands true
default_biome minecraft:plains
game_rule doDaylightCycle false
weather {
  raining true
  rain_time 12000
  thundering false
  thunder_time 0
  clear_weather_time 0
}
world_border {
  center 0.0 0.0
  size 5000.0
  size_lerp_target 5000.0
  size_lerp_time 0
  warning_blocks 5
  warning_time 15
  damage_buffer 5.0
  damage_per_block 0.2
}
```

`time` writes the world's total `Time`. `day_time` writes `DayTime`, the value
used for the visible day/night cycle.

`weather` writes `raining`, `rainTime`, `thundering`, `thunderTime`, and
`clearWeatherTime` in `level.dat`. The importer emits the block only when the
world has non-default weather state.

`world_border` writes the vanilla border center, size/interpolation,
warning-distance/time, and damage settings in `level.dat`. Default border
settings are derivable and are normally omitted from imported ATF.

`game_mode` accepts `survival`, `creative`, `adventure`, `spectator`, or `0..3`.
`difficulty` accepts `peaceful`, `easy`, `normal`, `hard`, or `0..3`.
Boolean directives accept `true/false`, `yes/no`, `on/off`, or `1/0`.

`min_y` must be a multiple of 16. `height` must be positive and a multiple of
16. All authored block, biome, block-entity, and entity coordinates must fall
inside that dimension's Y range.

### Dimensions

The overworld is the implicit default dimension. Use `dimension id { ... }` for
Nether, End, or custom dimension content:

```atf
dimension minecraft:the_nether {
  min_y 0
  height 256
  default_biome minecraft:nether_wastes
  set 0 64 0 minecraft:netherrack
}

dimension minecraft:the_end {
  min_y 0
  height 256
  default_biome minecraft:the_end
  set 0 64 0 minecraft:end_stone
}
```

Dimension bodies accept the same source directives as the overworld. `min_y`,
`height`, and `default_biome` inside a dimension apply to that dimension only.
World-level metadata such as spawn, time, weather, world border, game mode, and
game rules still belongs to `level.dat` and is not dimension-scoped in ATF v1.

The writer emits:

- overworld regions to `region/` and `dimensions/minecraft/overworld/region/`,
- Nether regions to `DIM-1/region/` and
  `dimensions/minecraft/the_nether/region/`,
- End regions to `DIM1/region/` and
  `dimensions/minecraft/the_end/region/`,
- custom dimension regions to `dimensions/<namespace>/<path>/region/`.

The reference validator currently validates the primary overworld `region/`
folder and vanilla `DIM-1`/`DIM1` auxiliary dimension folders.

### File Structure

```atf
include "relative/path.atf"
group "name" { ... }
component "name" { ... }
bounds 0 0 0 to 15 15 15
origin 100 64 200
```

`include` is resolved relative to the file containing it. Cyclic includes are
rejected.

`group` consumes a quoted name, an optional `origin`, optional `bounds`, and a
body of ordinary ATF directives. The name and bounds are review/documentation
data; the generated Anvil output is determined by the body after applying the
origin.

`component` defines a reusable body. It has no effect until placed with
`place component-name at x y z`. Components currently reuse the same parser
machinery as parameterless templates.

### Coordinates And Scope

Coordinates are local inside `group`, `component`, `at`, `template`, `repeat`,
`scatter`, and similar nested bodies. X/Z coordinates are rotated by the active
`rotate` scope. Y coordinates are offset but not rotated.

```atf
at 20 0 20 rotate 90 {
  set 0 4 0 minecraft:stone
}
```

`rotate` accepts `0`, `90`, `180`, or `270`. Rotation applies to known
directional block properties through the internal block-state rotation helper.

### Blocks And Shapes

```atf
set x y z block
fill x1 y1 z1 to x2 y2 z2 block
box x1 y1 z1 to x2 y2 z2 block
line x1 y1 z1 x2 y2 z2 block
wall x y z width height north|east|south|west block
hollow_box x1 y1 z1 x2 y2 z2 shell_block inside_block
sphere x y z radius block
hollow_sphere x y z radius shell_block inside_block
cylinder x y z radius y2 block
hollow_cylinder x y z radius y2 shell_block inside_block
replace x1 y1 z1 x2 y2 z2 target_block replacement_block
```

`fill`/`box`, `hollow_box`, and `replace` operate over inclusive coordinate
ranges. `line` draws an inclusive 3D line. `wall` is a compact one-block-thick
vertical surface helper. `door` and `bed` are block-state helpers:

```atf
door x y z north left closed minecraft:oak_door
bed x y z south minecraft:red_bed
```

### Slices And Patterns

`slice` is local 2D syntax for a single Y layer:

```atf
slice y=12 {
  set x z block
  fill x1 z1 to x2 z2 block
}
```

`pattern` maps characters to blocks. `.` and spaces are skipped:

```atf
pattern x y z {
  block A = minecraft:stone
  block B = minecraft:gold_block
  row "AAA"
  row "ABA"
  row "AAA"
  layer
  row ".A."
}
```

`pattern_template "name" { ... }` stores a pattern body. Use it with
`use_pattern name at x y z [rotate degrees]`.

### Reuse, Variables, And Control Flow

```atf
let name = 12
block alias = minecraft:stone_bricks
biome_alias warm = minecraft:plains

component "name" { ... }
place name at x y z [rotate degrees]

template "name" params width height { ... }
use name at x y z [rotate degrees] [args ...]

repeat i start end [step n] { ... }
if expr == expr { ... } else { ... }
choose [seed n] {
  weight 1 { ... }
  weight 3 { ... }
}
```

Integer expressions support literals, variables such as `$i`, parentheses, and
basic arithmetic/comparison where the directive expects it. `repeat` exposes the
loop variable. `scatter` and `surface_scatter` expose `$x`, `$y`, `$z`;
`surface_scatter` also exposes `$surface_y`.

### Palettes And Procedural Placement

These are source-authoring conveniences, not Anvil palettes:

```atf
block_palette path {
  block minecraft:stone weight 3
  block minecraft:mossy_cobblestone weight 1
}

fill_palette x1 y1 z1 x2 y2 z2 path [seed n]
replace_palette x1 y1 z1 x2 y2 z2 target_block path [seed n]
scatter x1 y1 z1 x2 y2 z2 chance numerator denominator [seed n] [step n] { ... }
surface_scatter x1 z1 x2 z2 from y1 to y2 chance numerator denominator [seed n] [step n] { ... }
terrain x1 z1 x2 z2 from y base y amplitude n [seed n] top_block under_block
smooth_terrain x1 z1 x2 z2 from y base y amplitude n [scale n] [octaves n] [seed n] top_block under_block
```

Palette picks are deterministic from coordinates and seed.
`surface_scatter` detects the top non-air block in each sampled column. Inside
the body, `0 0 0` is one block above the surface and `0 -1 0` is the surface
block itself.

### Biomes

```atf
biome x y z minecraft:forest
fill_biome x1 y1 z1 x2 y2 z2 minecraft:plains
```

Biomes are authored at block coordinates but generated into Minecraft's 4x4x4
biome grid. `default_biome` fills un-authored biome cells.

### Entities, Block Entities, And NBT

```atf
block_entity x y z minecraft:chest { ... }
merge_block_entity x y z minecraft:chest { ... }
entity x.y y.y z.y minecraft:armor_stand { ... }
sign_text x y z front "line 1" "line 2" "line 3" "line 4"
```

`block_entity` replaces the authored block-entity payload for that position.
`merge_block_entity` merges into existing authored data. `sign_text` writes the
modern sign text compound for the chosen `front` or `back` side.

NBT payload syntax supports:

```atf
byte "name" 1
short "name" 2
int "name" 3
long "name" 4
float "name" 1.5
double "name" 1.5
string "name" "value"
byte_array "name" { 1 2 3 }
int_array "name" { 1 2 3 }
long_array "name" { 1 2 3 }
compound "name" { ... }
list "name" string { "a" "b" }
item Items slot 0 minecraft:bread count 3
item_stack Item minecraft:diamond_sword count 1 { ... }
```

Supported list element types are byte, short, int, long, float, double, string,
and compound.

### Flat Import Passthrough

The parser supports:

```atf
passthrough "target/path" from "source/path"
```

This is used by flat imports to preserve raw save assets. Reviewable imports do
not currently emit passthrough directives because the v1 reviewable scope is
focused on editable world geometry and entity/block-entity state.

## Missing Map Features

ATF v1 can already express blocks, block states, biomes, entities, block
entities, generic NBT payloads, overworld/Nether/End/custom dimension block
content, spawn, total time, day time, weather, world border, game mode,
difficulty, hardcore, commands-enabled, default biome, and string game rules.

Useful Minecraft map/save features not yet modeled as first-class reviewable
ATF:

- Per-dimension metadata beyond Y range and default biome.
- Cross-dimension imports for reviewable source output. The writer supports
  authored dimensions; the importer still focuses on overworld region imports.
- World generation settings beyond the generated flat-air dimension stubs:
  seed, generator type, biome source, structures, bonus chest.
- Datapacks and enabled/disabled pack lists.
- Scoreboards, teams, objectives, display slots, and command/stat integrations.
- Resource packs and server resource-pack metadata.
- Map item data (`data/map_*.dat`), banners/markers stored in map data.
- Player data, inventories, ender chests, spawn overrides, recipes, and
  per-player state.
- Advancements and statistics.
- POI data and villager memory/profession infrastructure outside entity NBT.
- Forced chunks and tickets.
- Scheduled block ticks and fluid ticks.
- Wandering trader, patrol/raid, dragon fight, custom boss event, and similar
  miscellaneous `level.dat`/save-data state.
- Structure references/start data and generated-structure metadata.
- Region-level light data as source; lighting is currently generated/left to the
  game rather than reviewed as ATF.
- Heightmaps as source; heightmaps are derived output, not authored text.
- Command-function/datapack workflows beyond command blocks as normal block
  entities.
- Named component libraries/packages and semantic importer naming.

Some of these can already be represented indirectly with raw NBT on entities or
block entities. They are still listed here because the format does not yet have
reviewable first-class directives for them.

## Component Extraction

Reviewable imports run a conservative exact-match component pass before writing
groups. The current importer has two promotion paths:

- repeated isolated vertical stacks such as posts, lamps, and banners,
- repeated bounded connected block volumes up to 16x16x16, with a support cut
  so small objects can be separated from broad floors.

Both paths require exact block-state fingerprints, at least 3 placements, and
positive source-size savings. The current pass skips components containing block
entities, keeps chunk-boundary structures inline, and rejects large dense masses
that look like terrain or building shells rather than reusable objects.

Promoted components are written as normal files such as
`components/component-0001.atf`, included from `world.atf`, and placed from
groups with `place component-0001 at x y z`.

`meta.atf` records a component-analysis comment with candidate and promotion
counts. If a detailed map imports with `0 promoted component(s)`, that means the
current conservative pass did not find repeated exact stacks or bounded
connected volumes that passed the filters; it does not prove the map has no
reusable structures. Future extraction passes should add facade/window/furniture
volume matching across larger connected structures, with round-trip comparison
as the gate.

## Commands

Build:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk /opt/homebrew/bin/mvn -Dmaven.repo.local=.m2/repository test
JAVA_HOME=/opt/homebrew/opt/openjdk /opt/homebrew/bin/mvn -Dmaven.repo.local=.m2/repository -DskipTests package
```

Generate a world:

```sh
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar path/to/world.atf path/to/output-world --validate
```

Import a world into reviewable source:

```sh
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar import path/to/source-world path/to/atf-dir --layout reviewable
```

Compare a source world with a regenerated world using the reference Anvil
reader:

```sh
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar compare path/to/source-world path/to/regenerated-world
```

Use `--chunk-limit n` on import when probing very large maps quickly. The limit
is not a format restriction; it is only a bounded test/import option.

## Validation

The project uses the Hephaistos Anvil/NBT library as a reference reader. Tests
and CLI validation read generated worlds back through that library and check:

- valid `level.dat`,
- readable region files,
- exact authored block states,
- exact authored biomes,
- block entity anchors,
- entity anchors,
- external entity regions where present.

Round-trip comparison is:

```text
source Anvil world -> ATF source -> generated Anvil world -> reference compare
```

Blocks and biomes compare exactly. Entity positions are compared numerically at
nanoblock precision so harmless double string-format noise does not fail an
otherwise exact round trip.
