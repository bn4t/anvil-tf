package com.anviltf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldPostProcessor {
    public void apply(WorldSpec world) {
        for (WorldSpec.DimensionSpec dimension : world.dimensions().values()) {
            connectBlockStates(dimension);
        }
    }

    private void connectBlockStates(WorldSpec.DimensionSpec dimension) {
        List<BlockUpdate> updates = new ArrayList<>();
        for (Map.Entry<ChunkPos, MutableWorld.ChunkBlocks> entry : dimension.chunks().entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            MutableWorld.ChunkBlocks chunk = entry.getValue();
            int chunkX = chunkPos.x() * 16;
            int chunkZ = chunkPos.z() * 16;
            for (int y = dimension.minY(); y < dimension.maxYExclusive(); y++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        int x = chunkX + localX;
                        int z = chunkZ + localZ;
                        BlockState state = chunk.get(localX, y, localZ);
                        BlockState connected = connectedState(dimension, x, y, z, state);
                        if (!connected.equals(state)) {
                            updates.add(new BlockUpdate(x, y, z, connected));
                        }
                    }
                }
            }
        }
        for (BlockUpdate update : updates) {
            dimension.chunk(new ChunkPos(Math.floorDiv(update.x(), 16), Math.floorDiv(update.z(), 16)))
                    .set(Math.floorMod(update.x(), 16), update.y(), Math.floorMod(update.z(), 16), update.state());
        }
    }

    private BlockState connectedState(WorldSpec.DimensionSpec dimension, int x, int y, int z, BlockState state) {
        if (BlockState.AIR.equals(state)) {
            return state;
        }
        if (isFence(state)) {
            return withBooleanConnections(dimension, x, y, z, state, true);
        }
        if (isPane(state)) {
            return withBooleanConnections(dimension, x, y, z, state, true);
        }
        if (isWall(state)) {
            return withWallConnections(dimension, x, y, z, state);
        }
        return state;
    }

    private BlockState withBooleanConnections(WorldSpec.DimensionSpec dimension, int x, int y, int z,
                                              BlockState state, boolean waterloggable) {
        Map<String, String> properties = new LinkedHashMap<>(state.properties());
        putIfMissing(properties, "north", Boolean.toString(connectsTo(dimension, x, y, z - 1, state)));
        putIfMissing(properties, "east", Boolean.toString(connectsTo(dimension, x + 1, y, z, state)));
        putIfMissing(properties, "south", Boolean.toString(connectsTo(dimension, x, y, z + 1, state)));
        putIfMissing(properties, "west", Boolean.toString(connectsTo(dimension, x - 1, y, z, state)));
        if (waterloggable) {
            putIfMissing(properties, "waterlogged", "false");
        }
        return new BlockState(state.name(), properties);
    }

    private BlockState withWallConnections(WorldSpec.DimensionSpec dimension, int x, int y, int z, BlockState state) {
        Map<String, String> properties = new LinkedHashMap<>(state.properties());
        boolean north = connectsTo(dimension, x, y, z - 1, state);
        boolean east = connectsTo(dimension, x + 1, y, z, state);
        boolean south = connectsTo(dimension, x, y, z + 1, state);
        boolean west = connectsTo(dimension, x - 1, y, z, state);
        putIfMissing(properties, "north", north ? "low" : "none");
        putIfMissing(properties, "east", east ? "low" : "none");
        putIfMissing(properties, "south", south ? "low" : "none");
        putIfMissing(properties, "west", west ? "low" : "none");
        putIfMissing(properties, "up", Boolean.toString(!(north && east && south && west)));
        putIfMissing(properties, "waterlogged", "false");
        return new BlockState(state.name(), properties);
    }

    private boolean connectsTo(WorldSpec.DimensionSpec dimension, int x, int y, int z, BlockState source) {
        BlockState neighbor = blockAt(dimension, x, y, z);
        if (BlockState.AIR.equals(neighbor)) {
            return false;
        }
        if ((isFence(source) && isFence(neighbor))
                || (isWall(source) && isWall(neighbor))
                || (isPane(source) && isPane(neighbor))) {
            return true;
        }
        return isLikelySturdy(neighbor);
    }

    private BlockState blockAt(WorldSpec.DimensionSpec dimension, int x, int y, int z) {
        if (y < dimension.minY() || y >= dimension.maxYExclusive()) {
            return BlockState.AIR;
        }
        MutableWorld.ChunkBlocks chunk = dimension.chunks().get(new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (chunk == null) {
            return BlockState.AIR;
        }
        return chunk.get(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    private boolean isFence(BlockState state) {
        return state.name().endsWith("_fence") && !state.name().endsWith("_fence_gate");
    }

    private boolean isWall(BlockState state) {
        return state.name().endsWith("_wall");
    }

    private boolean isPane(BlockState state) {
        return state.name().endsWith("_glass_pane")
                || state.name().endsWith(":glass_pane")
                || state.name().endsWith(":iron_bars");
    }

    private boolean isLikelySturdy(BlockState state) {
        String name = state.name();
        if (name.endsWith("_fence_gate")) {
            return true;
        }
        if (name.endsWith("_slab") || name.endsWith("_stairs")) {
            return false;
        }
        for (String term : List.of("air", "water", "lava", "torch", "lantern", "sign", "button", "lever",
                "ladder", "chain", "rail", "pressure_plate", "carpet", "flower", "grass", "fern",
                "mushroom", "vine", "lily_pad", "sugar_cane", "wheat", "sapling", "leaves", "campfire")) {
            if (name.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private void putIfMissing(Map<String, String> properties, String key, String value) {
        properties.putIfAbsent(key, value);
    }

    private record BlockUpdate(int x, int y, int z, BlockState state) {
    }
}
