package com.anviltf.render;

import com.anviltf.BlockState;
import com.anviltf.ChunkPos;
import com.anviltf.MutableWorld;
import com.anviltf.ResourceId;
import com.anviltf.WorldPlanner;
import com.anviltf.WorldSpec;

import java.util.Set;
import java.util.TreeSet;

/**
 * Extracts a 2-D {@link RenderGrid} from a world so an LLM author can "see" what it generated.
 *
 * <p>Read-only over {@link WorldSpec}, mirroring {@link WorldPlanner} / {@code WorldDiagnostics}. Three
 * views are offered: {@link #top} (a horizontal Y-layer), {@link #map} (a top-down surface projection),
 * and {@link #slice} (a vertical cross-section). All views auto-crop to the authored bounds, so empty
 * space outside the build is never rendered and holes inside it show up as air.
 */
public final class WorldRenderer {

    /** Horizontal Y-layer: top-down floor plan at a single height. */
    public RenderGrid top(WorldSpec world, String dimensionId, int y) {
        WorldSpec.DimensionSpec dim = resolve(world, dimensionId);
        Bounds b = bounds(dim);
        String title = "top-down y=" + y + " dim=" + dim.id();
        if (b.empty) {
            return RenderGrid.empty(title);
        }
        int cols = b.maxX - b.minX + 1;
        int rows = b.maxZ - b.minZ + 1;
        String[][] cells = new String[rows][cols];
        for (int z = b.minZ; z <= b.maxZ; z++) {
            for (int x = b.minX; x <= b.maxX; x++) {
                cells[z - b.minZ][x - b.minX] = blockAt(dim, x, y, z).name();
            }
        }
        return new RenderGrid(cells, b.minZ, b.maxZ, b.minX, b.maxX, "z", "x", title);
    }

    /** Top-down surface projection: the first non-air block seen looking straight down (or biome). */
    public RenderGrid map(WorldSpec world, String dimensionId, boolean biome) {
        WorldSpec.DimensionSpec dim = resolve(world, dimensionId);
        Bounds b = bounds(dim);
        String title = (biome ? "biome map" : "surface map") + " dim=" + dim.id();
        if (b.empty) {
            return RenderGrid.empty(title);
        }
        int cols = b.maxX - b.minX + 1;
        int rows = b.maxZ - b.minZ + 1;
        String[][] cells = new String[rows][cols];
        for (int z = b.minZ; z <= b.maxZ; z++) {
            for (int x = b.minX; x <= b.maxX; x++) {
                int surfaceY = Integer.MIN_VALUE;
                String name = BlockState.AIR.name();
                for (int y = dim.maxYExclusive() - 1; y >= dim.minY(); y--) {
                    BlockState state = blockAt(dim, x, y, z);
                    if (!BlockState.AIR.equals(state)) {
                        surfaceY = y;
                        name = state.name();
                        break;
                    }
                }
                if (biome) {
                    name = biomeAt(dim, x, surfaceY == Integer.MIN_VALUE ? dim.minY() : surfaceY, z);
                }
                cells[z - b.minZ][x - b.minX] = name;
            }
        }
        return new RenderGrid(cells, b.minZ, b.maxZ, b.minX, b.maxX, "z", "x", title);
    }

    /**
     * Vertical cross-section at a fixed X (a Z-Y plane) or fixed Z (an X-Y plane). Row 0 is the highest
     * authored Y so the image is right-side-up; lets the author check wall heights, floors and ceilings.
     */
    public RenderGrid slice(WorldSpec world, String dimensionId, char axis, int coord) {
        WorldSpec.DimensionSpec dim = resolve(world, dimensionId);
        Bounds b = bounds(dim);
        String title = "slice " + axis + "=" + coord + " dim=" + dim.id();
        if (b.empty || !b.hasBlocks) {
            return RenderGrid.empty(title);
        }
        int rows = b.yMax - b.yMin + 1;
        if (axis == 'x') {
            int cols = b.maxZ - b.minZ + 1;
            String[][] cells = new String[rows][cols];
            for (int y = b.yMax; y >= b.yMin; y--) {
                for (int z = b.minZ; z <= b.maxZ; z++) {
                    cells[b.yMax - y][z - b.minZ] = blockAt(dim, coord, y, z).name();
                }
            }
            return new RenderGrid(cells, b.yMax, b.yMin, b.minZ, b.maxZ, "y", "z", title);
        }
        int cols = b.maxX - b.minX + 1;
        String[][] cells = new String[rows][cols];
        for (int y = b.yMax; y >= b.yMin; y--) {
            for (int x = b.minX; x <= b.maxX; x++) {
                cells[b.yMax - y][x - b.minX] = blockAt(dim, x, y, coord).name();
            }
        }
        return new RenderGrid(cells, b.yMax, b.yMin, b.minX, b.maxX, "y", "x", title);
    }

    /** ASCII rendering of a grid with a trailing glyph legend. */
    public String toAscii(RenderGrid grid) {
        if (grid.rows() == 0) {
            return grid.title() + "\n(empty: no authored content in this view)";
        }
        Set<String> present = new TreeSet<>();
        for (String[] row : grid.cells()) {
            for (String cell : row) {
                present.add(cell);
            }
        }
        BlockGlyphs glyphs = new BlockGlyphs(present);
        StringBuilder out = new StringBuilder();
        out.append(grid.title()).append('\n');
        out.append("  ").append(grid.colAxisLabel()).append(' ').append(grid.colLeft())
                .append("..").append(grid.colRight()).append(" (left to right), ")
                .append(grid.rowAxisLabel()).append(' ').append(grid.rowTop())
                .append("..").append(grid.rowBottom()).append(" (top to bottom)\n");
        for (String[] row : grid.cells()) {
            for (String cell : row) {
                out.append(glyphs.glyph(cell));
            }
            out.append('\n');
        }
        out.append("Legend (space = air/empty):\n");
        for (String name : glyphs.sortedNames()) {
            out.append("  ").append(glyphs.glyph(name)).append(' ').append(name).append('\n');
        }
        return out.toString();
    }

    private WorldSpec.DimensionSpec resolve(WorldSpec world, String dimensionId) {
        String normalized = ResourceId.normalize(dimensionId);
        WorldSpec.DimensionSpec dim = world.dimensions().get(normalized);
        if (dim == null) {
            throw new IllegalArgumentException("unknown dimension '" + normalized
                    + "'; authored dimensions: " + world.dimensions().keySet());
        }
        return dim;
    }

    private BlockState blockAt(WorldSpec.DimensionSpec dim, int x, int y, int z) {
        if (y < dim.minY() || y >= dim.maxYExclusive()) {
            return BlockState.AIR;
        }
        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        MutableWorld.ChunkBlocks chunk = dim.chunks().get(chunkPos);
        if (chunk == null) {
            return BlockState.AIR;
        }
        return chunk.get(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    private String biomeAt(WorldSpec.DimensionSpec dim, int x, int y, int z) {
        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        MutableWorld.ChunkBiomes biomes = dim.biomes().get(chunkPos);
        if (biomes == null) {
            return dim.defaultBiome();
        }
        return biomes.get(Math.floorMod(x, 16) / 4, y, Math.floorMod(z, 16) / 4);
    }

    private Bounds bounds(WorldSpec.DimensionSpec dim) {
        Set<ChunkPos> areaChunks = new TreeSet<>(dim.chunks().keySet());
        areaChunks.addAll(dim.biomes().keySet());
        if (areaChunks.isEmpty()) {
            return Bounds.EMPTY;
        }
        WorldPlanner.Bounds chunkBounds = WorldPlanner.Bounds.fromChunks(areaChunks);
        int minX = chunkBounds.minX() * 16;
        int minZ = chunkBounds.minZ() * 16;
        int maxX = chunkBounds.maxX() * 16 + 15;
        int maxZ = chunkBounds.maxZ() * 16 + 15;

        int[] yRange = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (MutableWorld.ChunkBlocks blocks : dim.chunks().values()) {
            blocks.forEachLayer((y, state) -> {
                if (!BlockState.AIR.equals(state)) {
                    yRange[0] = Math.min(yRange[0], y);
                    yRange[1] = Math.max(yRange[1], y);
                }
            });
            blocks.forEachExplicitBlock((localX, y, localZ, state) -> {
                if (!BlockState.AIR.equals(state)) {
                    yRange[0] = Math.min(yRange[0], y);
                    yRange[1] = Math.max(yRange[1], y);
                }
            });
        }
        boolean hasBlocks = yRange[0] != Integer.MAX_VALUE;
        int yMin = hasBlocks ? yRange[0] : dim.minY();
        int yMax = hasBlocks ? yRange[1] : dim.minY();
        return new Bounds(minX, minZ, maxX, maxZ, yMin, yMax, hasBlocks, false);
    }

    /** Authored bounds in block coordinates (chunk-aligned in X/Z, content-tight in Y). */
    private record Bounds(int minX, int minZ, int maxX, int maxZ, int yMin, int yMax,
                          boolean hasBlocks, boolean empty) {
        static final Bounds EMPTY = new Bounds(0, 0, 0, 0, 0, 0, false, true);
    }
}
