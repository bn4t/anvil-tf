package com.anviltf;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public final class WorldPlanner {
    public Plan plan(WorldSpec world) {
        Set<ChunkPos> chunks = new TreeSet<>();
        Set<ChunkPos> regions = new TreeSet<>();
        Set<BlockState> blockStates = new TreeSet<>((left, right) -> {
            int name = left.name().compareTo(right.name());
            return name != 0 ? name : left.properties().toString().compareTo(right.properties().toString());
        });

        int compactLayers = 0;
        int explicitBlocks = 0;
        long nonAirBlocks = 0;
        for (var entry : world.chunks().entrySet()) {
            ChunkPos chunk = entry.getKey();
            chunks.add(chunk);
            MutableWorld.ChunkBlocks blocks = entry.getValue();
            compactLayers += blocks.layerCount();
            explicitBlocks += blocks.explicitBlockCount();
            nonAirBlocks += blocks.nonAirBlockCount();
            blocks.collectBlockStates(blockStates);
        }
        chunks.addAll(world.biomes().keySet());
        chunks.addAll(world.blockEntities().keySet());
        chunks.addAll(world.entities().keySet());
        for (ChunkPos chunk : chunks) {
            regions.add(new ChunkPos(Math.floorDiv(chunk.x(), 32), Math.floorDiv(chunk.z(), 32)));
        }

        int explicitBiomes = world.biomes().values().stream()
                .mapToInt(MutableWorld.ChunkBiomes::explicitBiomeCount)
                .sum();
        int blockEntities = world.blockEntities().values().stream().mapToInt(map -> map.size()).sum();
        int entities = world.entities().values().stream().mapToInt(list -> list.size()).sum();

        Bounds chunkBounds = Bounds.fromChunks(chunks);
        Bounds regionBounds = Bounds.fromChunks(regions);
        return new Plan(world.name(), world.dataVersion(), world.minY(), world.maxYExclusive(),
                world.spawnX(), world.spawnY(), world.spawnZ(), chunks.size(), regions.size(),
                world.sectionCount(), compactLayers, explicitBlocks, nonAirBlocks, world.biomes().size(),
                explicitBiomes, blockEntities, entities, blockStates.size(), chunkBounds, regionBounds);
    }

    public String render(WorldSpec world) {
        return plan(world).render();
    }

    public record Plan(String name, int dataVersion, int minY, int maxYExclusive, int spawnX, int spawnY,
                       int spawnZ, int chunks, int regions, int sectionsPerChunk, int compactLayers,
                       int explicitBlocks, long nonAirBlocks, int biomeChunks, int explicitBiomes,
                       int blockEntities, int entities, int blockStatePaletteSize, Bounds chunkBounds,
                       Bounds regionBounds) {
        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("Plan for world '").append(name).append("'\n");
            out.append("  Data version: ").append(dataVersion).append('\n');
            out.append("  Y range: [").append(minY).append(", ").append(maxYExclusive).append(")\n");
            out.append("  Spawn: ").append(spawnX).append(' ').append(spawnY).append(' ').append(spawnZ).append('\n');
            out.append("  Chunks: ").append(chunks).append(" (bounds ").append(chunkBounds).append(")\n");
            out.append("  Regions: ").append(regions).append(" (bounds ").append(regionBounds).append(")\n");
            out.append("  Sections per chunk: ").append(sectionsPerChunk).append('\n');
            out.append("  Non-air blocks: ").append(nonAirBlocks).append('\n');
            out.append("  Storage: ").append(compactLayers).append(" compact layer(s), ")
                    .append(explicitBlocks).append(" explicit block override(s)\n");
            out.append("  Block state palette entries: ").append(blockStatePaletteSize).append('\n');
            out.append("  Biomes: ").append(explicitBiomes).append(" explicit quart-cell assignment(s) across ")
                    .append(biomeChunks).append(" chunk(s)\n");
            out.append("  Block entities: ").append(blockEntities).append('\n');
            out.append("  Entities: ").append(entities);
            return out.toString();
        }
    }

    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public static Bounds fromChunks(Set<ChunkPos> chunks) {
            if (chunks.isEmpty()) {
                return new Bounds(0, 0, 0, 0);
            }
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (ChunkPos chunk : new LinkedHashSet<>(chunks)) {
                minX = Math.min(minX, chunk.x());
                minZ = Math.min(minZ, chunk.z());
                maxX = Math.max(maxX, chunk.x());
                maxZ = Math.max(maxZ, chunk.z());
            }
            return new Bounds(minX, minZ, maxX, maxZ);
        }

        @Override
        public String toString() {
            return minX + "," + minZ + " to " + maxX + "," + maxZ;
        }
    }
}
