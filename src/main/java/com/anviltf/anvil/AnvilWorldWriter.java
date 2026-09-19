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
        boolean splitFormat = usesSplitLevelDat(world);
        writeLevelDat(world, output.resolve("level.dat"), splitFormat);
        writeWorldGenSettings(world, output.resolve("data/minecraft/world_gen_settings.dat"));
        if (splitFormat) {
            writeWeather(world, output.resolve("data/minecraft/weather.dat"));
            writeWorldClocks(world, output.resolve("data/minecraft/world_clocks.dat"));
            writeGameRules(world, output.resolve("data/minecraft/game_rules.dat"));
            for (WorldSpec.DimensionSpec dimension : world.dimensions().values()) {
                writeWorldBorder(world, dimensionPath(output, dimension.id())
                        .resolve("data/minecraft/world_border.dat"));
            }
        }
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
        Files.deleteIfExists(output.resolve("data/minecraft/weather.dat"));
        Files.deleteIfExists(output.resolve("data/minecraft/world_clocks.dat"));
        Files.deleteIfExists(output.resolve("data/minecraft/game_rules.dat"));
        deleteIfExistsUnder(output, "dimensions");
    }

    private void deleteIfExistsUnder(Path output, String name) throws IOException {
        Path namespaces = output.resolve(name);
        if (!Files.isDirectory(namespaces)) {
            return;
        }
        try (Stream<Path> nsStream = Files.list(namespaces)) {
            for (Path ns : nsStream.toList()) {
                try (Stream<Path> dimStream = Files.list(ns)) {
                    for (Path dim : dimStream.toList()) {
                        Files.deleteIfExists(dim.resolve("data/minecraft/world_border.dat"));
                    }
                }
            }
        }
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

    // Minecraft 26.x (dataVersion ~4700+) split world state out of level.dat:
    // spawn/difficulty moved to compounds, weather/gamerules/time/border moved to
    // data/minecraft/*.dat, and WorldGenSettings lives only in
    // data/minecraft/world_gen_settings.dat. A level.dat that still carries the
    // legacy fields is loaded via the datafixer upgrade path, which flags the
    // world as OLD_WORLD_GEN_SETTINGS and makes the client show the
    // "experimental settings" warning on every open.
    private static boolean usesSplitLevelDat(WorldSpec world) {
        return world.dataVersion() >= 4700;
    }

    private void writeLevelDat(WorldSpec world, Path path, boolean splitFormat) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path));
             NbtWriter nbt = new NbtWriter(gzip)) {
            nbt.namedRoot("", () -> {
                nbt.intTag("DataVersion", world.dataVersion());
                nbt.compound("Data", () -> {
                    nbt.intTag("DataVersion", world.dataVersion());
                    nbt.intTag("version", 19133);
                    nbt.stringTag("LevelName", world.name());
                    if (splitFormat) {
                        nbt.compound("spawn", () -> {
                            nbt.intArray("pos", new int[]{world.spawnX(), world.spawnY(), world.spawnZ()});
                            nbt.stringTag("dimension", spawnDimensionId(world));
                            nbt.floatTag("yaw", 0f);
                            nbt.floatTag("pitch", 0f);
                        });
                        nbt.compound("difficulty_settings", () -> {
                            nbt.stringTag("difficulty", difficultyName(world.difficulty()));
                            nbt.byteTag("hardcore", world.hardcore() ? 1 : 0);
                            nbt.byteTag("locked", 0);
                        });
                        nbt.compound("DataPacks", () -> {
                            nbt.list("Enabled", NbtWriter.TAG_STRING, 1);
                            nbt.stringPayload("vanilla");
                            nbt.list("Disabled", NbtWriter.TAG_STRING, 3);
                            nbt.stringPayload("minecart_improvements");
                            nbt.stringPayload("redstone_experiments");
                            nbt.stringPayload("trade_rebalance");
                        });
                        nbt.list("ServerBrands", NbtWriter.TAG_STRING, 1);
                        nbt.stringPayload("vanilla");
                        nbt.intArray("singleplayer_uuid", new int[]{0, 0, 0, 0});
                        nbt.byteTag("WasModded", 0);
                    } else {
                        nbt.intTag("SpawnX", world.spawnX());
                        nbt.intTag("SpawnY", world.spawnY());
                        nbt.intTag("SpawnZ", world.spawnZ());
                    }
                    nbt.longTag("Time", world.time());
                    nbt.longTag("LastPlayed", 0L);
                    if (!splitFormat) {
                        nbt.longTag("DayTime", world.dayTime());
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
                        nbt.byteTag("Difficulty", world.difficulty());
                        nbt.byteTag("DifficultyLocked", 0);
                    }
                    nbt.byteTag("hardcore", world.hardcore() ? 1 : 0);
                    nbt.intTag("GameType", world.gameType());
                    nbt.byteTag("allowCommands", world.allowCommands() ? 1 : 0);
                    nbt.byteTag("initialized", 1);
                    if (!splitFormat) {
                        // Legacy format only: 26.x loads level.dat WorldGenSettings
                        // via the OLD_WORLD_GEN_SETTINGS upgrade path.
                        nbt.compound("WorldGenSettings", () -> {
                            nbt.longTag("seed", 0L);
                            nbt.byteTag("generate_features", 0);
                            nbt.byteTag("bonus_chest", 0);
                            nbt.compound("dimensions", () -> {
                                writeDimensionEntries(world, nbt);
                            });
                        });
                        if (!world.gameRules().isEmpty()) {
                            nbt.compound("GameRules", () -> {
                                for (Map.Entry<String, String> rule : world.gameRules().entrySet()) {
                                    nbt.stringTag(rule.getKey(), rule.getValue());
                                }
                            });
                        }
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
                    nbt.byteTag("generate_structures", 1);
                    nbt.byteTag("bonus_chest", 0);
                    nbt.compound("dimensions", () -> {
                        writeDimensionEntries(world, nbt);
                    });
                });
            });
        }
    }

    // WorldDimensions.bake() marks a world "experimental" (client shows the
    // warning screen on every open) unless the saved dimensions cover all three
    // vanilla dimension keys. Declared dimensions get the flat-air generator so
    // only authored blocks exist; vanilla dimensions the source did not declare
    // get their vanilla noise generators so portals/nether/end still work.
    private void writeDimensionEntries(WorldSpec world, NbtWriter nbt) throws IOException {
        Set<String> declared = new LinkedHashSet<>(world.dimensions().keySet());
        for (WorldSpec.DimensionSpec dimension : world.dimensions().values()) {
            nbt.compound(dimension.id(), () -> {
                nbt.stringTag("type", dimensionTypeId(dimension.id()));
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
                        nbt.list("structure_overrides", NbtWriter.TAG_STRING, 0);
                    });
                });
            });
        }
        for (String vanillaDim : List.of(WorldSpec.OVERWORLD, WorldSpec.NETHER, WorldSpec.END)) {
            if (declared.contains(vanillaDim)) {
                continue;
            }
            nbt.compound(vanillaDim, () -> {
                nbt.stringTag("type", dimensionTypeId(vanillaDim));
                nbt.compound("generator", () -> writeVanillaNoiseGenerator(nbt, vanillaDim));
            });
        }
    }

    private void writeVanillaNoiseGenerator(NbtWriter nbt, String dimensionId) throws IOException {
        nbt.stringTag("type", "minecraft:noise");
        switch (dimensionId) {
            case WorldSpec.NETHER -> {
                nbt.stringTag("settings", "minecraft:nether");
                nbt.compound("biome_source", () -> {
                    nbt.stringTag("type", "minecraft:multi_noise");
                    nbt.stringTag("preset", "minecraft:nether");
                });
            }
            case WorldSpec.END -> {
                nbt.stringTag("settings", "minecraft:end");
                nbt.compound("biome_source", () -> {
                    nbt.stringTag("type", "minecraft:the_end");
                });
            }
            default -> {
                nbt.stringTag("settings", "minecraft:overworld");
                nbt.compound("biome_source", () -> {
                    nbt.stringTag("type", "minecraft:multi_noise");
                    nbt.stringTag("preset", "minecraft:overworld");
                });
            }
        }
    }

    private int flatSpawnHeight(WorldSpec world, WorldSpec.DimensionSpec dimension) {
        if (WorldSpec.OVERWORLD.equals(dimension.id())) {
            return Math.max(1, Math.min(384, world.spawnY() + 64));
        }
        return 1;
    }

    private String spawnDimensionId(WorldSpec world) {
        if (world.dimensions().containsKey(WorldSpec.OVERWORLD)) {
            return WorldSpec.OVERWORLD;
        }
        return world.dimensions().keySet().iterator().next();
    }

    private static String difficultyName(int difficulty) {
        return switch (difficulty) {
            case 0 -> "peaceful";
            case 1 -> "easy";
            case 3 -> "hard";
            default -> "normal";
        };
    }

    private interface DataFileWriter {
        void write(NbtWriter nbt) throws IOException;
    }

    private void writeDataFile(WorldSpec world, Path path, DataFileWriter body) throws IOException {
        Files.createDirectories(path.getParent());
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path));
             NbtWriter nbt = new NbtWriter(gzip)) {
            nbt.namedRoot("", () -> {
                nbt.intTag("DataVersion", world.dataVersion());
                nbt.compound("data", () -> body.write(nbt));
            });
        }
    }

    private void writeWeather(WorldSpec world, Path path) throws IOException {
        writeDataFile(world, path, nbt -> {
            nbt.intTag("rain_time", world.rainTime());
            nbt.byteTag("raining", world.raining() ? 1 : 0);
            nbt.byteTag("thundering", world.thundering() ? 1 : 0);
            nbt.intTag("thunder_time", world.thunderTime());
            nbt.intTag("clear_weather_time", world.clearWeatherTime());
        });
    }

    private void writeWorldClocks(WorldSpec world, Path path) throws IOException {
        writeDataFile(world, path, nbt -> {
            for (WorldSpec.DimensionSpec dimension : world.dimensions().values()) {
                if (WorldSpec.NETHER.equals(dimension.id())) {
                    continue;
                }
                nbt.compound(dimension.id(), () -> nbt.longTag("total_ticks", world.dayTime()));
            }
        });
    }

    private void writeWorldBorder(WorldSpec world, Path path) throws IOException {
        WorldSpec.WorldBorder border = world.worldBorder();
        writeDataFile(world, path, nbt -> {
            nbt.doubleTag("center_x", border.centerX());
            nbt.doubleTag("center_z", border.centerZ());
            nbt.doubleTag("damage_per_block", border.damagePerBlock());
            nbt.intTag("warning_time", (int) Math.round(border.warningTime()));
            nbt.doubleTag("size", border.size());
            nbt.doubleTag("safe_zone", border.damageBuffer());
            nbt.intTag("warning_blocks", (int) Math.round(border.warningBlocks()));
            nbt.doubleTag("lerp_target", border.sizeLerpTarget());
            nbt.longTag("lerp_time", border.sizeLerpTime());
        });
    }

    // 26.x gamerule ids are namespaced snake_case; several rules were split or
    // renamed (e.g. doDaylightCycle -> minecraft:advance_time, doFireTick ->
    // minecraft:fire_spread_radius_around_player). Map the legacy names authors
    // know from older versions onto the new ids; namespaced ids pass through.
    private static final Map<String, String> LEGACY_GAME_RULE_IDS = Map.ofEntries(
            Map.entry("announceAdvancements", "minecraft:show_advancement_messages"),
            Map.entry("blockExplosionDropDecay", "minecraft:block_explosion_drop_decay"),
            Map.entry("commandBlockOutput", "minecraft:command_block_output"),
            Map.entry("commandModificationBlockLimit", "minecraft:max_block_modifications"),
            Map.entry("disableElytraMovementCheck", "minecraft:elytra_movement_check"),
            Map.entry("disableRaids", "minecraft:raids"),
            Map.entry("doDaylightCycle", "minecraft:advance_time"),
            Map.entry("doEntityDrops", "minecraft:entity_drops"),
            Map.entry("doFireTick", "minecraft:fire_spread_radius_around_player"),
            Map.entry("doImmediateRespawn", "minecraft:immediate_respawn"),
            Map.entry("doInsomnia", "minecraft:spawn_phantoms"),
            Map.entry("doLimitedCrafting", "minecraft:limited_crafting"),
            Map.entry("doMobLoot", "minecraft:mob_drops"),
            Map.entry("doMobSpawning", "minecraft:spawn_mobs"),
            Map.entry("doPatrolSpawning", "minecraft:spawn_patrols"),
            Map.entry("doTileDrops", "minecraft:block_drops"),
            Map.entry("doTraderSpawning", "minecraft:spawn_wandering_traders"),
            Map.entry("doVinesSpread", "minecraft:spread_vines"),
            Map.entry("doWardenSpawning", "minecraft:spawn_wardens"),
            Map.entry("doWeatherCycle", "minecraft:advance_weather"),
            Map.entry("drowningDamage", "minecraft:drowning_damage"),
            Map.entry("enderPearlsVanishOnDeath", "minecraft:ender_pearls_vanish_on_death"),
            Map.entry("fallDamage", "minecraft:fall_damage"),
            Map.entry("fireDamage", "minecraft:fire_damage"),
            Map.entry("forgiveDeadPlayers", "minecraft:forgive_dead_players"),
            Map.entry("freezeDamage", "minecraft:freeze_damage"),
            Map.entry("globalSoundEvents", "minecraft:global_sound_events"),
            Map.entry("keepInventory", "minecraft:keep_inventory"),
            Map.entry("lavaSourceConversion", "minecraft:lava_source_conversion"),
            Map.entry("logAdminCommands", "minecraft:log_admin_commands"),
            Map.entry("maxCommandChainLength", "minecraft:max_command_sequence_length"),
            Map.entry("maxCommandForkCount", "minecraft:max_command_forks"),
            Map.entry("maxEntityCramming", "minecraft:max_entity_cramming"),
            Map.entry("mobExplosionDropDecay", "minecraft:mob_explosion_drop_decay"),
            Map.entry("mobGriefing", "minecraft:mob_griefing"),
            Map.entry("naturalRegeneration", "minecraft:natural_health_regeneration"),
            Map.entry("playersNetherPortalCreativeDelay", "minecraft:players_nether_portal_creative_delay"),
            Map.entry("playersNetherPortalDefaultDelay", "minecraft:players_nether_portal_default_delay"),
            Map.entry("playersSleepingPercentage", "minecraft:players_sleeping_percentage"),
            Map.entry("projectilesCanBreakBlocks", "minecraft:projectiles_can_break_blocks"),
            Map.entry("randomTickSpeed", "minecraft:random_tick_speed"),
            Map.entry("reducedDebugInfo", "minecraft:reduced_debug_info"),
            Map.entry("sendCommandFeedback", "minecraft:send_command_feedback"),
            Map.entry("showDeathMessages", "minecraft:show_death_messages"),
            Map.entry("snowAccumulationHeight", "minecraft:max_snow_accumulation_height"),
            Map.entry("spawnRadius", "minecraft:respawn_radius"),
            Map.entry("spectatorsGenerateChunks", "minecraft:spectators_generate_chunks"),
            Map.entry("tntExplosionDropDecay", "minecraft:tnt_explosion_drop_decay"),
            Map.entry("universalAnger", "minecraft:universal_anger"),
            Map.entry("waterSourceConversion", "minecraft:water_source_conversion"));

    private static final Set<String> INT_GAME_RULES = Set.of(
            "minecraft:max_entity_cramming",
            "minecraft:players_nether_portal_default_delay",
            "minecraft:players_nether_portal_creative_delay",
            "minecraft:max_command_sequence_length",
            "minecraft:max_command_forks",
            "minecraft:fire_spread_radius_around_player",
            "minecraft:max_block_modifications",
            "minecraft:players_sleeping_percentage",
            "minecraft:respawn_radius",
            "minecraft:random_tick_speed",
            "minecraft:max_snow_accumulation_height");

    private void writeGameRules(WorldSpec world, Path path) throws IOException {
        writeDataFile(world, path, nbt -> {
            for (Map.Entry<String, String> rule : world.gameRules().entrySet()) {
                String id = gameRuleId(rule.getKey());
                if (INT_GAME_RULES.contains(id)) {
                    nbt.intTag(id, parseIntRule(id, rule.getValue()));
                } else {
                    nbt.byteTag(id, parseBool(rule.getValue()) ? 1 : 0);
                }
            }
        });
    }

    private static String gameRuleId(String name) {
        if (name.indexOf(':') >= 0) {
            return name;
        }
        String mapped = LEGACY_GAME_RULE_IDS.get(name);
        if (mapped != null) {
            return mapped;
        }
        StringBuilder snake = new StringBuilder("minecraft:");
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                snake.append('_').append(Character.toLowerCase(c));
            } else {
                snake.append(c);
            }
        }
        return snake.toString();
    }

    private static boolean parseBool(String value) {
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    private static int parseIntRule(String id, String value) {
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            // Bool-valued rules that became numeric (doFireTick ->
            // fire_spread_radius_around_player): "true" restores the vanilla default.
            return "true".equalsIgnoreCase(trimmed) ? 128 : 0;
        }
        return Integer.parseInt(trimmed);
    }

    // WorldGenSettings dimensions reference a dimension type registry entry.
    // Vanilla ids keep their names; custom dimensions fall back to the
    // overworld dimension type so generated worlds stay loadable.
    private String dimensionTypeId(String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:overworld", "overworld" -> "minecraft:overworld";
            case "minecraft:the_nether", "the_nether" -> "minecraft:the_nether";
            case "minecraft:the_end", "the_end" -> "minecraft:the_end";
            default -> "minecraft:overworld";
        };
    }
}
