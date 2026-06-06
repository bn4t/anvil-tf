package com.anviltf;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class WorldDiagnostics {
    private static final Set<String> HORIZONTAL_DIRECTIONS = Set.of("north", "east", "south", "west");

    public List<Warning> diagnose(WorldSpec world) {
        List<Warning> warnings = new ArrayList<>();
        for (WorldSpec.DimensionSpec dimension : world.dimensions().values()) {
            diagnoseBlockEntities(dimension, warnings);
            diagnoseWallAttachments(dimension, warnings);
            diagnoseFloatingStructuralLayers(dimension, warnings);
        }
        warnings.sort(Comparator
                .comparing(Warning::dimension)
                .thenComparing(Warning::code)
                .thenComparingInt(Warning::y)
                .thenComparingInt(Warning::z)
                .thenComparingInt(Warning::x));
        return warnings;
    }

    private void diagnoseBlockEntities(WorldSpec.DimensionSpec dimension, List<Warning> warnings) {
        for (Map<WorldSpec.BlockPos, BlockEntity> chunkEntities : dimension.blockEntities().values()) {
            for (BlockEntity entity : chunkEntities.values()) {
                BlockState state = blockAt(dimension, entity.x(), entity.y(), entity.z());
                String expected = expectedAnchorTerm(entity.id());
                if (expected == null) {
                    continue;
                }
                if (BlockState.AIR.equals(state) || !state.name().contains(expected)) {
                    warnings.add(new Warning("block-entity-anchor-mismatch", dimension.id(),
                            entity.x(), entity.y(), entity.z(),
                            "block_entity " + entity.id() + " is anchored to "
                                    + state.name() + ", expected a block containing '" + expected + "'"));
                }
            }
        }
    }

    private String expectedAnchorTerm(String id) {
        String name = ResourceId.normalize(id);
        if (name.endsWith(":sign")) {
            return "sign";
        }
        if (name.endsWith(":hanging_sign")) {
            return "hanging_sign";
        }
        for (String term : List.of("chest", "barrel", "furnace", "blast_furnace", "smoker",
                "shulker_box", "beacon", "brewing_stand", "dispenser", "dropper", "hopper",
                "lectern", "campfire", "command_block", "banner", "bed", "skull")) {
            if (name.endsWith(":" + term)) {
                return term;
            }
        }
        return null;
    }

    private void diagnoseWallAttachments(WorldSpec.DimensionSpec dimension, List<Warning> warnings) {
        forEachAuthoredBlock(dimension, (x, y, z, state) -> {
            String facing = state.properties().get("facing");
            if (!requiresWallSupport(state) || !HORIZONTAL_DIRECTIONS.contains(facing)) {
                return;
            }
            WorldSpec.BlockPos support = supportBehind(x, y, z, facing);
            BlockState supportState = blockAt(dimension, support.x(), support.y(), support.z());
            if (BlockState.AIR.equals(supportState)) {
                warnings.add(new Warning("unsupported-wall-attachment", dimension.id(), x, y, z,
                        state.name() + " faces " + facing + " but has no authored support block at "
                                + support.x() + " " + support.y() + " " + support.z()));
            }
        });
    }

    private void diagnoseFloatingStructuralLayers(WorldSpec.DimensionSpec dimension, List<Warning> warnings) {
        Set<Long> visited = new HashSet<>();
        forEachAuthoredBlock(dimension, (x, y, z, state) -> {
            long start = key(x, y, z);
            if (visited.contains(start) || y <= dimension.minY() || !isStructural(state)
                    || !BlockState.AIR.equals(blockAt(dimension, x, y - 1, z))) {
                return;
            }
            FloatingLayer layer = floatingLayer(dimension, x, y, z, visited);
            if (!layer.supported() && layer.count() >= 2) {
                warnings.add(new Warning("unsupported-floating-structure", dimension.id(),
                        layer.sampleX(), layer.sampleY(), layer.sampleZ(),
                        layer.count() + " connected structural block(s) at y=" + layer.sampleY()
                                + " have no same-layer block resting on support below"));
            }
        });
    }

    private FloatingLayer floatingLayer(WorldSpec.DimensionSpec dimension, int startX, int y, int startZ,
                                        Set<Long> visited) {
        Queue<WorldSpec.BlockPos> queue = new ArrayDeque<>();
        queue.add(new WorldSpec.BlockPos(startX, y, startZ));
        visited.add(key(startX, y, startZ));
        boolean supported = false;
        int count = 0;
        int sampleX = startX;
        int sampleZ = startZ;
        while (!queue.isEmpty()) {
            WorldSpec.BlockPos current = queue.remove();
            count++;
            if (!BlockState.AIR.equals(blockAt(dimension, current.x(), y - 1, current.z()))) {
                supported = true;
            }
            for (WorldSpec.BlockPos neighbor : List.of(
                    new WorldSpec.BlockPos(current.x() + 1, y, current.z()),
                    new WorldSpec.BlockPos(current.x() - 1, y, current.z()),
                    new WorldSpec.BlockPos(current.x(), y, current.z() + 1),
                    new WorldSpec.BlockPos(current.x(), y, current.z() - 1))) {
                long key = key(neighbor.x(), y, neighbor.z());
                if (visited.contains(key) || !isStructural(blockAt(dimension, neighbor.x(), y, neighbor.z()))) {
                    continue;
                }
                visited.add(key);
                queue.add(neighbor);
            }
        }
        return new FloatingLayer(sampleX, y, sampleZ, count, supported);
    }

    private boolean requiresWallSupport(BlockState state) {
        String name = state.name();
        if (name.contains("wall_sign") || name.contains("wall_hanging_sign")
                || name.endsWith("_wall_banner") || name.contains("wall_torch")
                || name.endsWith(":ladder")) {
            return true;
        }
        return (name.endsWith("_button") || name.endsWith(":lever"))
                && "wall".equals(state.properties().get("face"));
    }

    private boolean isStructural(BlockState state) {
        if (BlockState.AIR.equals(state)) {
            return false;
        }
        String name = state.name();
        for (String term : List.of("water", "lava", "torch", "lantern", "sign", "button", "lever",
                "ladder", "chain", "rail", "pressure_plate", "carpet", "flower", "grass", "fern",
                "mushroom", "vine", "lily_pad", "sugar_cane", "wheat", "sapling", "leaves", "campfire")) {
            if (name.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private WorldSpec.BlockPos supportBehind(int x, int y, int z, String facing) {
        return switch (facing) {
            case "north" -> new WorldSpec.BlockPos(x, y, z + 1);
            case "south" -> new WorldSpec.BlockPos(x, y, z - 1);
            case "east" -> new WorldSpec.BlockPos(x - 1, y, z);
            case "west" -> new WorldSpec.BlockPos(x + 1, y, z);
            default -> new WorldSpec.BlockPos(x, y, z);
        };
    }

    private BlockState blockAt(WorldSpec.DimensionSpec dimension, int x, int y, int z) {
        if (y < dimension.minY() || y >= dimension.maxYExclusive()) {
            return BlockState.AIR;
        }
        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        MutableWorld.ChunkBlocks chunk = dimension.chunks().get(chunkPos);
        if (chunk == null) {
            return BlockState.AIR;
        }
        return chunk.get(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    private long key(int x, int y, int z) {
        long normalizedX = x & 0x3ffffffL;
        long normalizedZ = z & 0x3ffffffL;
        long normalizedY = y & 0xfffL;
        return (normalizedX << 38) | (normalizedZ << 12) | normalizedY;
    }

    private void forEachAuthoredBlock(WorldSpec.DimensionSpec dimension, AuthoredBlockConsumer consumer) {
        for (Map.Entry<ChunkPos, MutableWorld.ChunkBlocks> chunkEntry : dimension.chunks().entrySet()) {
            ChunkPos chunkPos = chunkEntry.getKey();
            MutableWorld.ChunkBlocks chunk = chunkEntry.getValue();
            int chunkX = chunkPos.x() * 16;
            int chunkZ = chunkPos.z() * 16;
            chunk.forEachLayer((y, state) -> {
                if (BlockState.AIR.equals(state)) {
                    return;
                }
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        consumer.accept(chunkX + localX, y, chunkZ + localZ, state);
                    }
                }
            });
            chunk.forEachExplicitBlock((localX, y, localZ, state) -> {
                if (!BlockState.AIR.equals(state)) {
                    consumer.accept(chunkX + localX, y, chunkZ + localZ, state);
                }
            });
        }
    }

    public record Warning(String code, String dimension, int x, int y, int z, String message) {
    }

    private record FloatingLayer(int sampleX, int sampleY, int sampleZ, int count, boolean supported) {
    }

    @FunctionalInterface
    private interface AuthoredBlockConsumer {
        void accept(int x, int y, int z, BlockState state);
    }
}
