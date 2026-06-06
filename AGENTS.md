# AGENTS.md

## Product Direction

Anvil TF is a source format and Java compiler for Minecraft Java Edition worlds.
The goal is not to mirror Terraform syntax literally, but to provide the same
kind of reviewable source-of-truth workflow:

- world source can be checked into Git,
- diffs should be meaningful to humans and LLMs,
- generated Anvil files are build output,
- handmade maps can be imported into the same source format,
- generated and imported content should not become separate dialects.

The core language should describe block-world structure with generic primitives.
Avoid baking city-specific concepts such as roads, districts, houses, or rooms
into the core language. Those can be written as reusable components or libraries.

## Source Tree Shape

For now, keep the source tree simple:

```text
world.atf
meta.atf
groups/
  spawn-plaza.atf
  imported-111-12.atf
components/
  lamp-post.atf
  fountain.atf
```

Use `world.atf` as the entry point. `meta.atf` contains world metadata. `groups/`
contains placed world edits. `components/` contains reusable local structures.

Do not introduce special top-level folders such as `areas/`, `chunks/`,
`detected/`, or `residual/` unless there is a concrete user-facing need. If an
importer falls back to grouping by Minecraft chunk, that chunk is still just a
normal `group` file in `groups/`.

## Current Scope

Focus ATF v1 on:

- blocks,
- biomes,
- entities,
- block entities,
- dimensions,
- weather,
- world border,
- metadata needed to generate a valid world.

Leave out save assets for now:

- datapacks,
- scoreboards,
- map item data,
- playerdata,
- stats,
- advancements,
- icons,
- resource packs,
- arbitrary passthrough folders.

The importer may report that these were skipped, but reviewable source imports
should not copy them or encode them yet. Keep the project centered on editable
world geometry and block/entity state.

## Core Format Principles

Prefer a small generic core:

```text
world
include
dimension
group
component
origin
bounds
fill
set
slice
replace
place
repeat
choose
scatter
entity
block_entity
biome
```

The source format should not expose internal Anvil concepts as the primary
review surface. Avoid making users review:

- chunk sections,
- numeric block palettes,
- packed arrays,
- heightmaps,
- region-file coordinates,
- generated chunk storage.

Those are compiler/output concerns. It is acceptable to use chunk-aligned groups
as an importer fallback, but they should be expressed with ordinary ATF commands
and local coordinates.

## Import Style

Imported handmade maps do not need to become perfect semantic descriptions. They
do need to become readable enough for Git review and LLM edits.

Importer output should prefer:

1. ordinary `group` files,
2. local coordinates via `origin`,
3. `fill` for large cuboids and layers,
4. `slice` or similar local 2D grouping when it improves readability,
5. explicit `set` for irregular handmade leftovers,
6. reusable `component` files only when repeated structures are detected with
   high confidence.

Do not label content as "human", "imported", "detected", or "residual" in the
language. Everything is just ATF source. Comments may mention import provenance,
but semantics should remain neutral.

Example importer fallback:

```atf
group "imported 111,12" {
  origin 1776 -64 192
  bounds 0 0 0 to 15 383 15

  fill 0 0 0 to 15 0 15 minecraft:bedrock

  slice y=1 {
    fill 0 0 to 2 0 minecraft:bedrock
    set 3 0 minecraft:deepslate[axis=y]
    fill 4 0 to 13 0 minecraft:bedrock
    set 14 0 minecraft:deepslate[axis=y]
    set 15 0 minecraft:bedrock
  }

  block_entity 8 130 10 minecraft:chest {
    item Items slot 0 minecraft:bread count 3
  }
}
```

## Repeated Structure Extraction Plan

Repeated-structure extraction should be conservative. Exact round-trip must
always win over cleverness.

### Phase 1: Candidate Discovery

The importer currently implements a conservative first pass: exact repeated
isolated 1x1 vertical stacks plus bounded connected block volumes. This should
still be treated as component extraction v0, not proof that a map has no
reusable components.

Scan imported chunks for small non-air connected components or bounded volumes.
Good initial candidates:

- 1x1xN or 3x3xN vertical objects such as lamps, posts, trees, banners,
- repeated decorative clusters between 2x2x2 and 16x16x16,
- repeated furniture/interior clusters,
- repeated facade/window patterns.

Use connected-component extraction over non-air blocks, but avoid enormous
terrain masses. Ignore components that touch too much of a chunk boundary unless
they are small and clearly object-like.

### Phase 2: Canonical Fingerprints

For each candidate volume:

- normalize coordinates to local origin,
- include full block state names/properties,
- include block entities inside the volume,
- optionally normalize rotations only after exact non-rotated matching works,
- hash the normalized representation.

Start with exact matching only. Do not infer approximate components until exact
components are proven useful.

### Phase 3: Component Promotion

Promote a candidate to `components/` only when:

- it repeats at least 3 times,
- it saves meaningful source size,
- all instances match exactly,
- replacing instances with `place` preserves round-trip comparison.

A promoted component should be normal ATF:

```atf
component "lamp-post-01" {
  bounds 0 0 0 to 2 5 2
  fill 1 0 1 to 1 3 1 minecraft:dark_oak_fence
  set 1 4 1 minecraft:lantern[hanging=true]
}
```

Instances should be normal placements after the component file has been
included:

```atf
include "../components/lamp-post-01.atf"

place lamp-post-01 at 1780 64 210
place lamp-post-01 at 1792 64 210
```

### Phase 4: Verification

Every extraction pass must run:

```text
source world -> ATF source -> generated Anvil -> reference comparator
```

The comparator should read with the reference library and verify exact blocks,
biomes, block-entity anchors, and entity anchors for imported chunks. If any
component extraction changes output, fall back to inline group commands.

### Phase 5: Refactorability

Names generated by the importer should be stable but boring:

```text
components/component-0001.atf
groups/imported-111-12.atf
```

Humans or LLMs can later rename them to meaningful names. Do not make importer
confidence labels part of the language.

## Inspecting Generated Worlds

ATF source is reviewable, but source review alone has a blind spot: the same mistake
that produced bad geometry is usually invisible when you re-read the code that
produced it. Reading the `.atf` back does not tell you whether walls actually
enclose a room, whether a floor has holes, or whether a build simply looks wrong.

Use the `render` command to *see* a world without launching Minecraft. It reads the
parsed world (no Anvil write needed) and emits a top-down or cross-section view. This
is the intended self-inspection loop for an agent authoring ATF:

```text
generate ATF -> render -> look at the output -> fix the source -> render again
```

Three views are available:

```text
java -jar anvil-tf.jar render map   <input.atf>          [--biome] [--dim id] [--out file.png] [--scale n] [--max n]
java -jar anvil-tf.jar render top   <input.atf> <y>       [--dim id] [--out file.png] [--scale n] [--max n]
java -jar anvil-tf.jar render slice <input.atf> x|z <c>   [--dim id] [--out file.png] [--scale n] [--max n]
```

- `render map` — top-down surface projection ("what you'd see from above"). Best for
  footprint, layout, and spotting gaps: air reads as empty space.
- `render top <y>` — top-down floor plan at a single height. Best for per-layer
  correctness (one course of walls, a specific floor).
- `render slice x|z <coord>` — vertical cross-section. Best for wall heights, floors,
  ceilings, and floating structures.

Output modes:

- No `--out` prints an ASCII grid plus a glyph legend. It is token-cheap and readable
  by any model — prefer it for quick checks and when reasoning over the result in text.
- `--out file.png` writes a colored PNG plus a color legend, for a multimodal model to
  view directly. Prefer it for judging whether a build looks right.

Behavior worth knowing when interpreting output:

- Views auto-crop to the authored bounds, so empty space outside the build is never
  shown and the grid coordinates start at the build, not the origin.
- A blank cell (space glyph in ASCII, transparent in PNG) is always air. Unexpected
  blanks inside a structure are holes to fix.
- Axis labels are directional: top-down rows run north-to-south (increasing Z),
  slice rows run top-to-bottom (decreasing Y). The header states the exact range.
- `--dim id` selects a dimension (default `minecraft:overworld`); `--scale` is pixels
  per block and `--max` caps the longest PNG side, downsampling if a build is large.

`render` is read-only and never writes Anvil output, so it is safe to run repeatedly
while iterating on source.

## Reviewability Rules

- Prefer local coordinates inside `group` and `component`.
- Prefer `fill` over many `set` commands.
- Prefer normal block names over numeric palettes.
- Prefer includes over giant monolithic files.
- Keep generated Anvil output out of source control.
- Keep exactness testable with reference-library round trips.
