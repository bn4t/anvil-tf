package com.anviltf.anvil;

import com.anviltf.BlockState;
import com.anviltf.BlockEntity;
import com.anviltf.ChunkPos;
import com.anviltf.Entity;
import com.anviltf.MutableWorld;
import com.anviltf.NbtValue;
import com.anviltf.WorldSpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public final class AnvilWorldWriter {
    public void write(WorldSpec world, Path output) throws IOException {
        Files.createDirectories(output);
        cleanGeneratedAnvilFiles(output);
        writeLevelDat(world, output.resolve("level.dat"));
        writeWorldGenSettings(world, output.resolve("data/minecraft/world_gen_settings.dat"));
        for (WorldSpec.DimensionSpec dimension : world.dimensions().values()) {
            for (Path regionDir : regionDirectories(output, dimension.id())) {
                Files.createDirectories(regionDir);
                writeRegions(world, dimension, regionDir);
            }
        }
        copyPassthroughPaths(world, output);
    }

    private void cleanGeneratedAnvilFiles(Path output) throws IOException {
        Files.deleteIfExists(output.resolve("level.dat"));
        Files.deleteIfExists(output.resolve("data/minecraft/world_gen_settings.dat"));
        cleanMcaDirectory(output.resolve("region"));
        cleanMcaDirectory(output.resolve("entities"));
        cleanMcaDirectory(output.resolve("poi"));
        cleanMcaDirectory(output.resolve("DIM-1/region"));
        cleanMcaDirectory(output.resolve("DIM-1/entities"));
        cleanMcaDirectory(output.resolve("DIM-1/poi"));
        cleanMcaDirectory(output.resolve("DIM1/region"));
        cleanMcaDirectory(output.resolve("DIM1/entities"));
        cleanMcaDirectory(output.resolve("DIM1/poi"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/overworld/region"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/overworld/entities"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/overworld/poi"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/the_nether/region"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/the_nether/entities"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/the_nether/poi"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/the_end/region"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/the_end/entities"));
        cleanMcaDirectory(output.resolve("dimensions/minecraft/the_end/poi"));
    }

    private void cleanMcaDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().endsWith(".mca")).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void copyPassthroughPaths(WorldSpec world, Path output) throws IOException {
        Path outputRoot = output.toAbsolutePath().normalize();
        for (WorldSpec.PassthroughPath passthrough : world.passthroughPaths()) {
            Path target = outputRoot.resolve(passthrough.target()).normalize();
            if (!target.startsWith(outputRoot)) {
                throw new IOException("Passthrough target escapes output directory: " + passthrough.target());
            }
            Path source = passthrough.source();
            if (!Files.exists(source)) {
                throw new IOException("Passthrough source does not exist: " + source);
            }
            deleteRecursively(target);
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString()).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path candidate : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private List<Path> regionDirectories(Path output, String dimensionId) {
        Path modern = dimensionPath(output, dimensionId).resolve("region");
        if (WorldSpec.OVERWORLD.equals(dimensionId)) {
            return List.of(output.resolve("region"), modern);
        }
        if (WorldSpec.NETHER.equals(dimensionId)) {
            return List.of(output.resolve("DIM-1/region"), modern);
        }
        if (WorldSpec.END.equals(dimensionId)) {
            return List.of(output.resolve("DIM1/region"), modern);
        }
        return List.of(modern);
    }

    private Path dimensionPath(Path output, String dimensionId) {
        int separator = dimensionId.indexOf(':');
        if (separator < 0) {
            return output.resolve("dimensions/minecraft").resolve(dimensionId);
        }
        return output.resolve("dimensions")
                .resolve(dimensionId.substring(0, separator))
                .resolve(dimensionId.substring(separator + 1));
    }

    private void writeRegions(WorldSpec world, WorldSpec.DimensionSpec dimension, Path regionDir) throws IOException {
        Map<ChunkPos, List<ChunkPos>> chunksByRegion = new TreeMap<>();
        for (ChunkPos chunk : writableChunks(dimension)) {
            ChunkPos region = new ChunkPos(Math.floorDiv(chunk.x(), 32), Math.floorDiv(chunk.z(), 32));
            chunksByRegion.computeIfAbsent(region, ignored -> new ArrayList<>()).add(chunk);
        }

        RegionWriter writer = new RegionWriter();
        for (Map.Entry<ChunkPos, List<ChunkPos>> region : chunksByRegion.entrySet()) {
            Map<Integer, byte[]> chunks = new TreeMap<>();
            for (ChunkPos chunk : region.getValue()) {
                int localX = Math.floorMod(chunk.x(), 32);
                int localZ = Math.floorMod(chunk.z(), 32);
                int index = localX + localZ * 32;
                chunks.put(index, chunkNbt(world, dimension, chunk, dimension.chunks().get(chunk),
                        dimension.blockEntities().getOrDefault(chunk, Map.of()),
                        dimension.entities().getOrDefault(chunk, List.of()),
                        dimension.biomes().get(chunk)));
            }
            Path file = regionDir.resolve("r." + region.getKey().x() + "." + region.getKey().z() + ".mca");
            writer.write(file, chunks);
        }
    }

    private Set<ChunkPos> writableChunks(WorldSpec.DimensionSpec dimension) {
        Set<ChunkPos> chunkPositions = new LinkedHashSet<>();
        chunkPositions.addAll(dimension.chunks().keySet());
        chunkPositions.addAll(dimension.biomes().keySet());
        chunkPositions.addAll(dimension.blockEntities().keySet());
        chunkPositions.addAll(dimension.entities().keySet());

        Set<ChunkPos> writable = new LinkedHashSet<>();
        for (ChunkPos chunk : chunkPositions) {
            MutableWorld.ChunkBlocks blocks = dimension.chunks().get(chunk);
            if ((blocks == null || blocks.isEmpty())
                    && !dimension.biomes().containsKey(chunk)
                    && !dimension.blockEntities().containsKey(chunk)
                    && !dimension.entities().containsKey(chunk)) {
                continue;
            }
            writable.add(chunk);
        }
        return writable;
    }

    private byte[] chunkNbt(WorldSpec world, WorldSpec.DimensionSpec dimension, ChunkPos pos,
                            MutableWorld.ChunkBlocks blocks,
                            Map<WorldSpec.BlockPos, BlockEntity> blockEntities,
                            List<Entity> entities,
                            MutableWorld.ChunkBiomes biomes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NbtWriter nbt = new NbtWriter(bytes)) {
            nbt.namedRoot("", () -> {
                nbt.intTag("DataVersion", world.dataVersion());
                nbt.intTag("xPos", pos.x());
                nbt.intTag("yPos", dimension.minSectionY());
                nbt.intTag("zPos", pos.z());
                nbt.longTag("LastUpdate", 0L);
                nbt.longTag("InhabitedTime", 0L);
                nbt.stringTag("Status", "minecraft:full");
                heightmaps(nbt, dimension, blocks);
                entities(nbt, entities);
                blockEntities(nbt, blockEntities);
                sections(nbt, dimension, blocks, biomes);
            });
        }
        return bytes.toByteArray();
    }

    private void entities(NbtWriter nbt, List<Entity> entities) throws IOException {
        nbt.list("entities", NbtWriter.TAG_COMPOUND, entities.size());
        for (Entity entity : entities) {
            nbt.compoundPayload(() -> {
                writeDefaultEntityTags(nbt, entity);
                for (Map.Entry<String, NbtValue> entry : new TreeMap<>(entity.data()).entrySet()) {
                    if (!isDefaultEntityTag(entry.getKey())) {
                        entry.getValue().writeNamed(nbt, entry.getKey());
                    }
                }
            });
        }
    }

    private void writeDefaultEntityTags(NbtWriter nbt, Entity entity) throws IOException {
        Map<String, NbtValue> data = entity.data();
        nbt.stringTag("id", entity.id());
        NbtValue.listValue(NbtWriter.TAG_DOUBLE, List.of(
                NbtValue.doubleValue(entity.x()),
                NbtValue.doubleValue(entity.y()),
                NbtValue.doubleValue(entity.z()))).writeNamed(nbt, "Pos");
        if (data.containsKey("Motion")) {
            data.get("Motion").writeNamed(nbt, "Motion");
        } else {
            NbtValue.listValue(NbtWriter.TAG_DOUBLE, List.of(
                    NbtValue.doubleValue(0.0d),
                    NbtValue.doubleValue(0.0d),
                    NbtValue.doubleValue(0.0d))).writeNamed(nbt, "Motion");
        }
        if (data.containsKey("Rotation")) {
            data.get("Rotation").writeNamed(nbt, "Rotation");
        } else {
            NbtValue.listValue(NbtWriter.TAG_FLOAT, List.of(
                    NbtValue.floatValue(0.0f),
                    NbtValue.floatValue(0.0f))).writeNamed(nbt, "Rotation");
        }
        nbt.intArray("UUID", uuid(entity));
    }

    private boolean isDefaultEntityTag(String name) {
        return Set.of("id", "Pos", "Motion", "Rotation", "UUID").contains(name);
    }

    private int[] uuid(Entity entity) {
        int hash = Objects.hash(entity.id(), entity.x(), entity.y(), entity.z());
        return new int[] {0x41544600, hash, Arrays.hashCode(entity.data().keySet().stream()
                .filter(key -> !isDefaultEntityTag(key)).sorted().toArray()), 0x00000001};
    }

    private void blockEntities(NbtWriter nbt, Map<WorldSpec.BlockPos, BlockEntity> blockEntities) throws IOException {
        nbt.list("block_entities", NbtWriter.TAG_COMPOUND, blockEntities.size());
        for (BlockEntity entity : blockEntities.values()) {
            nbt.compoundPayload(() -> {
                nbt.stringTag("id", entity.id());
                nbt.intTag("x", entity.x());
                nbt.intTag("y", entity.y());
                nbt.intTag("z", entity.z());
                for (Map.Entry<String, NbtValue> entry : new TreeMap<>(entity.data()).entrySet()) {
                    if (!isDefaultBlockEntityTag(entry.getKey())) {
                        entry.getValue().writeNamed(nbt, entry.getKey());
                    }
                }
            });
        }
    }

    private boolean isDefaultBlockEntityTag(String name) {
        return Set.of("id", "x", "y", "z").contains(name);
    }

    private void heightmaps(NbtWriter nbt, WorldSpec.DimensionSpec dimension, MutableWorld.ChunkBlocks blocks)
            throws IOException {
        int[] values = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = dimension.minY();
                for (int y = dimension.maxYExclusive() - 1; y >= dimension.minY(); y--) {
                    if (blocks != null && !BlockState.AIR.equals(blocks.get(x, y, z))) {
                        top = y + 1;
                        break;
                    }
                }
                values[z * 16 + x] = Math.max(0, top - dimension.minY());
            }
        }
        int bits = Math.max(9, PackedBits.bitsNeeded(dimension.height() + 1));
        long[] packed = PackedBits.packPadded(values, bits);
        nbt.compound("Heightmaps", () -> {
            nbt.longArray("MOTION_BLOCKING", packed);
            nbt.longArray("WORLD_SURFACE", packed);
        });
    }

    private void sections(NbtWriter nbt, WorldSpec.DimensionSpec dimension, MutableWorld.ChunkBlocks blocks,
                          MutableWorld.ChunkBiomes biomes) throws IOException {
        nbt.list("sections", NbtWriter.TAG_COMPOUND, dimension.sectionCount());
        for (int section = 0; section < dimension.sectionCount(); section++) {
            int sectionY = dimension.minSectionY() + section;
            nbt.compoundPayload(() -> {
                nbt.byteTag("Y", sectionY);
                writeBlockStates(nbt, sectionY, blocks);
                writeBiomes(nbt, dimension, sectionY, biomes);
            });
        }
    }

    private void writeBlockStates(NbtWriter nbt, int sectionY, MutableWorld.ChunkBlocks blocks) throws IOException {
        Map<BlockState, Integer> paletteIndexes = new LinkedHashMap<>();
        int[] values = new int[4096];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = blocks == null ? BlockState.AIR : blocks.get(x, sectionY * 16 + y, z);
                    int index = paletteIndexes.computeIfAbsent(state, ignored -> paletteIndexes.size());
                    values[(y << 8) | (z << 4) | x] = index;
                }
            }
        }

        List<BlockState> palette = new ArrayList<>(paletteIndexes.keySet());
        nbt.compound("block_states", () -> {
            nbt.list("palette", NbtWriter.TAG_COMPOUND, palette.size());
            for (BlockState state : palette) {
                nbt.compoundPayload(() -> {
                    nbt.stringTag("Name", state.name());
                    if (!state.properties().isEmpty()) {
                        nbt.compound("Properties", () -> {
                            for (Map.Entry<String, String> property : new TreeMap<>(state.properties()).entrySet()) {
                                nbt.stringTag(property.getKey(), property.getValue());
                            }
                        });
                    }
                });
            }
            if (palette.size() > 1) {
                int bits = Math.max(4, PackedBits.bitsNeeded(palette.size()));
                nbt.longArray("data", PackedBits.packPadded(values, bits));
            }
        });
    }

    private void writeBiomes(NbtWriter nbt, WorldSpec.DimensionSpec dimension, int sectionY,
                             MutableWorld.ChunkBiomes biomes)
            throws IOException {
        Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        int[] values = new int[64];
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int worldY = sectionY * 16 + y * 4;
                    String biome = biomes == null
                            ? dimension.defaultBiome()
                            : biomes.get(x, worldY, z);
                    int index = paletteIndexes.computeIfAbsent(biome, ignored -> paletteIndexes.size());
                    values[(y << 4) | (z << 2) | x] = index;
                }
            }
        }

        List<String> palette = new ArrayList<>(paletteIndexes.keySet());
        nbt.compound("biomes", () -> {
            nbt.list("palette", NbtWriter.TAG_STRING, palette.size());
            for (String biome : palette) {
                nbt.stringPayload(biome);
            }
            if (palette.size() > 1) {
                int bits = Math.max(1, PackedBits.bitsNeeded(palette.size()));
                nbt.longArray("data", PackedBits.packPadded(values, bits));
            }
        });
    }

    private void writeLevelDat(WorldSpec world, Path path) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path));
             NbtWriter nbt = new NbtWriter(gzip)) {
            nbt.namedRoot("", () -> {
                nbt.intTag("DataVersion", world.dataVersion());
                nbt.compound("Data", () -> {
                    nbt.intTag("DataVersion", world.dataVersion());
                    nbt.intTag("version", 19133);
                    nbt.stringTag("LevelName", world.name());
                    nbt.intTag("SpawnX", world.spawnX());
                    nbt.intTag("SpawnY", world.spawnY());
                    nbt.intTag("SpawnZ", world.spawnZ());
                    nbt.longTag("Time", world.time());
                    nbt.longTag("DayTime", world.dayTime());
                    nbt.longTag("LastPlayed", 0L);
                    nbt.byteTag("raining", world.raining() ? 1 : 0);
                    nbt.intTag("rainTime", world.rainTime());
                    nbt.byteTag("thundering", world.thundering() ? 1 : 0);
                    nbt.intTag("thunderTime", world.thunderTime());
                    nbt.intTag("clearWeatherTime", world.clearWeatherTime());
                    WorldSpec.WorldBorder border = world.worldBorder();
                    nbt.doubleTag("BorderCenterX", border.centerX());
                    nbt.doubleTag("BorderCenterZ", border.centerZ());
                    nbt.doubleTag("BorderSize", border.size());
                    nbt.doubleTag("BorderSizeLerpTarget", border.sizeLerpTarget());
                    nbt.longTag("BorderSizeLerpTime", border.sizeLerpTime());
                    nbt.doubleTag("BorderSafeZone", border.damageBuffer());
                    nbt.doubleTag("BorderDamagePerBlock", border.damagePerBlock());
                    nbt.doubleTag("BorderWarningBlocks", border.warningBlocks());
                    nbt.doubleTag("BorderWarningTime", border.warningTime());
                    nbt.intTag("GameType", world.gameType());
                    nbt.byteTag("Difficulty", world.difficulty());
                    nbt.byteTag("DifficultyLocked", 0);
                    nbt.byteTag("hardcore", world.hardcore() ? 1 : 0);
                    nbt.byteTag("allowCommands", world.allowCommands() ? 1 : 0);
                    nbt.byteTag("initialized", 1);
                    if (!world.gameRules().isEmpty()) {
                        nbt.compound("GameRules", () -> {
                            for (Map.Entry<String, String> rule : world.gameRules().entrySet()) {
                                nbt.stringTag(rule.getKey(), rule.getValue());
                            }
                        });
                    }
                    nbt.compound("Version", () -> {
                        nbt.stringTag("Name", effectiveVersionName(world));
                        nbt.intTag("Id", world.dataVersion());
                        nbt.byteTag("Snapshot", 0);
                        nbt.stringTag("Series", "main");
                    });
                });
            });
        }
    }

    // Minecraft uses the level.dat Version.Name/Series to identify the world; an
    // unrecognized name makes it treat the world as an unknown version and reset
    // metadata on load. Honor an explicit version_name, otherwise derive a name
    // that matches the data version so Name and Id stay consistent.
    private static String effectiveVersionName(WorldSpec world) {
        String explicit = world.versionName();
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return switch (world.dataVersion()) {
            case 3465 -> "1.20.1";
            case 4790 -> "26.1.2";
            default -> "anvil-tf";
        };
    }

    private void writeWorldGenSettings(WorldSpec world, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path));
             NbtWriter nbt = new NbtWriter(gzip)) {
            nbt.namedRoot("", () -> {
                nbt.intTag("DataVersion", world.dataVersion());
                nbt.compound("data", () -> {
                    nbt.longTag("seed", 0L);
                    nbt.byteTag("generate_structures", 0);
                    nbt.byteTag("bonus_chest", 0);
                    nbt.compound("dimensions", () -> {
                        for (WorldSpec.DimensionSpec dimension : world.dimensions().values()) {
                            nbt.compound(dimension.id(), () -> {
                                nbt.stringTag("type", dimension.id());
                                nbt.compound("generator", () -> {
                                    nbt.stringTag("type", "minecraft:flat");
                                    nbt.compound("settings", () -> {
                                        nbt.stringTag("biome", dimension.defaultBiome());
                                        nbt.byteTag("lakes", 0);
                                        nbt.byteTag("features", 0);
                                        nbt.list("layers", NbtWriter.TAG_COMPOUND, 1);
                                        nbt.compoundPayload(() -> {
                                            nbt.intTag("height", flatSpawnHeight(world, dimension));
                                            nbt.stringTag("block", "minecraft:air");
                                        });
                                    });
                                });
                            });
                        }
                    });
                });
            });
        }
    }

    private int flatSpawnHeight(WorldSpec world, WorldSpec.DimensionSpec dimension) {
        if (WorldSpec.OVERWORLD.equals(dimension.id())) {
            return Math.max(1, Math.min(384, world.spawnY() + 64));
        }
        return 1;
    }
}
