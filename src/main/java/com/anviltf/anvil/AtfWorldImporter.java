package com.anviltf.anvil;

import com.anviltf.ChunkPos;
import com.anviltf.WorldSpec;
import org.jglrxavpok.hephaistos.mca.ChunkColumn;
import org.jglrxavpok.hephaistos.mca.RegionFile;
import org.jglrxavpok.hephaistos.nbt.CompressedProcesser;
import org.jglrxavpok.hephaistos.nbt.NBT;
import org.jglrxavpok.hephaistos.nbt.NBTByte;
import org.jglrxavpok.hephaistos.nbt.NBTByteArray;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTDouble;
import org.jglrxavpok.hephaistos.nbt.NBTFloat;
import org.jglrxavpok.hephaistos.nbt.NBTInt;
import org.jglrxavpok.hephaistos.nbt.NBTIntArray;
import org.jglrxavpok.hephaistos.nbt.NBTList;
import org.jglrxavpok.hephaistos.nbt.NBTLong;
import org.jglrxavpok.hephaistos.nbt.NBTLongArray;
import org.jglrxavpok.hephaistos.nbt.NBTReader;
import org.jglrxavpok.hephaistos.nbt.NBTShort;
import org.jglrxavpok.hephaistos.nbt.NBTString;

import java.io.BufferedWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AtfWorldImporter {
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final String AIR = "minecraft:air";
    private static final Set<String> BLOCK_ENTITY_STRUCTURAL_KEYS = Set.of("id", "x", "y", "z");
    private static final Set<String> ENTITY_STRUCTURAL_KEYS = Set.of("id", "Pos", "UUID", "UUIDMost", "UUIDLeast");

    public ImportReport importWorld(Path worldDir, Path outputAtf, ImportOptions options) throws Exception {
        if (!Files.isRegularFile(worldDir.resolve("level.dat"))) {
            throw new IllegalArgumentException("Missing level.dat: " + worldDir.resolve("level.dat"));
        }
        Path regionDir = worldDir.resolve("region");
        if (!Files.isDirectory(regionDir)) {
            throw new IllegalArgumentException("Missing region directory: " + regionDir);
        }

        WorldMetadata metadata = readMetadata(worldDir.resolve("level.dat"));
        List<RegionCoordinate> regions = regions(regionDir);
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("No Anvil region files found in " + regionDir);
        }
        List<ChunkCoordinate> chunks = chunkCoordinates(worldDir, regions, metadata);

        Scan scan = scan(worldDir, metadata, chunks, options);
        if (scan.chunkCount == 0) {
            throw new IllegalArgumentException("No chunks found to import in " + regionDir);
        }

        ImportReport report = new ImportReport(scan.chunkCount, 0, 0, 0, 0, 0,
                scan.minY, scan.maxYExclusive, scan.regionsTruncated, unsupportedWorldFolders(worldDir));
        if (options.layout == ImportLayout.REVIEWABLE) {
            importReviewable(worldDir, outputAtf, metadata, chunks, scan, options, report);
            return report;
        }

        Files.createDirectories(outputAtf.toAbsolutePath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(outputAtf, StandardCharsets.UTF_8)) {
            writeHeader(out, worldDir, metadata, scan, report, options);
            writeBody(out, worldDir, metadata, chunks, scan.defaultBiome, options, report);
            out.write("}\n");
        }
        return report;
    }

    private WorldMetadata readMetadata(Path levelDat) throws Exception {
        try (NBTReader reader = new NBTReader(levelDat, CompressedProcesser.GZIP)) {
            NBT root = reader.readNamed().getSecond();
            if (!(root instanceof NBTCompound compound)) {
                throw new IllegalArgumentException("level.dat root is not an NBT compound");
            }
            NBTCompound data = compound.getCompound("Data");
            if (data == null) {
                throw new IllegalArgumentException("level.dat is missing Data compound");
            }
            Map<String, String> gameRules = new TreeMap<>();
            NBTCompound gameRulesTag = data.getCompound("GameRules");
            if (gameRulesTag != null) {
                for (Map.Entry<String, NBT> entry : gameRulesTag.asMapView().entrySet()) {
                    if (entry.getValue() instanceof NBTString value) {
                        gameRules.put(entry.getKey(), value.getValue());
                    }
                }
            }
            return new WorldMetadata(
                    Optional.ofNullable(data.getString("LevelName")).orElse("Imported World"),
                    Optional.ofNullable(data.getInt("DataVersion")).orElse(3465),
                    data.getInt("SpawnX"),
                    data.getInt("SpawnY"),
                    data.getInt("SpawnZ"),
                    data.getLong("Time"),
                    data.getLong("DayTime"),
                    data.getInt("GameType"),
                    data.getInt("Difficulty"),
                    data.getByte("hardcore"),
                    data.getByte("allowCommands"),
                    data.getByte("raining"),
                    data.getInt("rainTime"),
                    data.getByte("thundering"),
                    data.getInt("thunderTime"),
                    data.getInt("clearWeatherTime"),
                    readWorldBorder(data),
                    gameRules);
        }
    }

    private static WorldSpec.WorldBorder readWorldBorder(NBTCompound data) {
        boolean present = data.asMapView().containsKey("BorderCenterX")
                || data.asMapView().containsKey("BorderCenterZ")
                || data.asMapView().containsKey("BorderSize")
                || data.asMapView().containsKey("BorderSizeLerpTarget")
                || data.asMapView().containsKey("BorderSizeLerpTime")
                || data.asMapView().containsKey("BorderWarningBlocks")
                || data.asMapView().containsKey("BorderWarningTime")
                || data.asMapView().containsKey("BorderSafeZone")
                || data.asMapView().containsKey("BorderDamagePerBlock");
        if (!present) {
            return null;
        }
        WorldSpec.WorldBorder defaults = WorldSpec.WorldBorder.defaults();
        return new WorldSpec.WorldBorder(
                doubleTag(data, "BorderCenterX", defaults.centerX()),
                doubleTag(data, "BorderCenterZ", defaults.centerZ()),
                doubleTag(data, "BorderSize", defaults.size()),
                doubleTag(data, "BorderSizeLerpTarget", defaults.sizeLerpTarget()),
                longTag(data, "BorderSizeLerpTime", defaults.sizeLerpTime()),
                (int) Math.round(doubleTag(data, "BorderWarningBlocks", defaults.warningBlocks())),
                (int) Math.round(doubleTag(data, "BorderWarningTime", defaults.warningTime())),
                doubleTag(data, "BorderSafeZone", defaults.damageBuffer()),
                doubleTag(data, "BorderDamagePerBlock", defaults.damagePerBlock()));
    }

    private static double doubleTag(NBTCompound compound, String name, double fallback) {
        NBT value = compound.asMapView().get(name);
        if (value instanceof NBTDouble typed) {
            return typed.getValue();
        }
        if (value instanceof NBTFloat typed) {
            return typed.getValue();
        }
        if (value instanceof NBTLong typed) {
            return typed.getValue();
        }
        if (value instanceof NBTInt typed) {
            return typed.getValue();
        }
        if (value instanceof NBTShort typed) {
            return typed.getValue();
        }
        if (value instanceof NBTByte typed) {
            return typed.getValue();
        }
        return fallback;
    }

    private static long longTag(NBTCompound compound, String name, long fallback) {
        NBT value = compound.asMapView().get(name);
        if (value instanceof NBTLong typed) {
            return typed.getValue();
        }
        if (value instanceof NBTInt typed) {
            return typed.getValue();
        }
        if (value instanceof NBTShort typed) {
            return typed.getValue();
        }
        if (value instanceof NBTByte typed) {
            return typed.getValue();
        }
        return fallback;
    }

    private List<RegionCoordinate> regions(Path regionDir) throws Exception {
        try (var paths = Files.list(regionDir)) {
            return paths
                    .map(path -> path.getFileName().toString())
                    .map(this::parseRegionName)
                    .flatMap(Optional::stream)
                    .sorted()
                    .toList();
        }
    }

    private Optional<RegionCoordinate> parseRegionName(String name) {
        Matcher matcher = REGION_NAME.matcher(name);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new RegionCoordinate(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))));
    }

    private List<ChunkCoordinate> chunkCoordinates(Path worldDir, List<RegionCoordinate> regions,
                                                   WorldMetadata metadata) throws Exception {
        List<ChunkCoordinate> chunks = new ArrayList<>();
        for (RegionCoordinate coordinate : regions) {
            Path file = worldDir.resolve("region/r." + coordinate.x + "." + coordinate.z + ".mca");
            try (RegionFile region = new RegionFile(new RandomAccessFile(file.toFile(), "rw"),
                    coordinate.x, coordinate.z)) {
                for (int localZ = 0; localZ < 32; localZ++) {
                    for (int localX = 0; localX < 32; localX++) {
                        int chunkX = coordinate.x * 32 + localX;
                        int chunkZ = coordinate.z * 32 + localZ;
                        if (region.hasChunk(chunkX, chunkZ)) {
                            chunks.add(new ChunkCoordinate(coordinate, chunkX, chunkZ));
                        }
                    }
                }
            }
        }
        if (metadata.spawnX != null && metadata.spawnZ != null) {
            int spawnChunkX = Math.floorDiv(metadata.spawnX, 16);
            int spawnChunkZ = Math.floorDiv(metadata.spawnZ, 16);
            chunks.sort(Comparator
                    .comparingInt((ChunkCoordinate chunk) -> Math.max(Math.abs(chunk.x - spawnChunkX),
                            Math.abs(chunk.z - spawnChunkZ)))
                    .thenComparingLong(chunk -> squaredDistance(chunk.x, chunk.z, spawnChunkX, spawnChunkZ))
                    .thenComparing(ChunkCoordinate::compareTo));
        } else {
            chunks.sort(ChunkCoordinate::compareTo);
        }
        return chunks;
    }

    private static long squaredDistance(int x, int z, int targetX, int targetZ) {
        long dx = (long) x - targetX;
        long dz = (long) z - targetZ;
        return dx * dx + dz * dz;
    }

    private Scan scan(Path worldDir, WorldMetadata metadata, List<ChunkCoordinate> chunks, ImportOptions options)
            throws Exception {
        Scan scan = new Scan();
        scan.minY = Integer.MAX_VALUE;
        scan.maxYExclusive = Integer.MIN_VALUE;
        try (RegionCache cache = new RegionCache(worldDir, "region");
             RegionCache entityCache = new RegionCache(worldDir, "entities")) {
            for (ChunkCoordinate coordinate : chunks) {
                ChunkColumn chunk = loadChunk(cache.region(coordinate.region), coordinate.x, coordinate.z,
                        metadata.dataVersion);
                if (!hasImportableContent(chunk) && externalEntities(entityCache, coordinate).isEmpty()) {
                    continue;
                }
                if (options.chunkLimit > 0 && scan.chunkCount >= options.chunkLimit) {
                    scan.regionsTruncated = true;
                    break;
                }
                scan.importedChunks.add(new ChunkPos(coordinate.x, coordinate.z));
                scan.chunkCount++;
                scan.minY = Math.min(scan.minY, Math.floorDiv(chunk.getMinY(), 16) * 16);
                scan.maxYExclusive = Math.max(scan.maxYExclusive,
                        Math.floorDiv(chunk.getMaxY(), 16) * 16 + 16);
                for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y += 4) {
                    for (int z = 0; z < 16; z += 4) {
                        for (int x = 0; x < 16; x += 4) {
                            scan.biomeCounts.merge(chunk.getBiome(x, y, z), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        scan.defaultBiome = scan.biomeCounts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse("minecraft:plains");
        return scan;
    }

    private List<String> unsupportedWorldFolders(Path worldDir) {
        List<String> folders = new ArrayList<>();
        for (String name : List.of("DIM-1", "DIM1", "poi", "data", "datapacks",
                "serverconfig", "playerdata", "stats", "advancements", "resources",
                "icon.png", "resources.zip")) {
            if (Files.exists(worldDir.resolve(name))) {
                folders.add(name);
            }
        }
        return folders;
    }

    private void writeHeader(BufferedWriter out, Path worldDir, WorldMetadata metadata, Scan scan,
                             ImportReport report, ImportOptions options) throws Exception {
        out.write("# Generated by anvil-tf import from " + worldDir.toAbsolutePath() + "\n");
        if (!report.unsupportedFolders.isEmpty()) {
            out.write("# Import note: these save assets are preserved as raw passthrough assets: "
                    + String.join(", ", report.unsupportedFolders) + "\n");
        }
        if (scan.regionsTruncated) {
            out.write("# Import note: output truncated at --chunk-limit " + options.chunkLimit + "\n");
        }
        out.write("world " + quote(metadata.name) + " {\n");
        writeMetadataCoreDirectives(out, metadata, scan);
        writePassthroughMetadata(out, worldDir, report);
        out.write("\n");
    }

    private void writeMetadataCoreDirectives(BufferedWriter out, WorldMetadata metadata, Scan scan) throws Exception {
        out.write("  version " + metadata.dataVersion + "\n");
        out.write("  min_y " + scan.minY + "\n");
        out.write("  height " + (scan.maxYExclusive - scan.minY) + "\n");
        if (metadata.spawnX != null && metadata.spawnY != null && metadata.spawnZ != null
                && metadata.spawnY >= scan.minY && metadata.spawnY < scan.maxYExclusive) {
            out.write("  spawn " + metadata.spawnX + " " + metadata.spawnY + " " + metadata.spawnZ + "\n");
        }
        if (metadata.time != null) {
            out.write("  time " + metadata.time + "\n");
        }
        if (metadata.dayTime != null) {
            out.write("  day_time " + metadata.dayTime + "\n");
        }
        if (metadata.gameType != null) {
            out.write("  game_mode " + metadata.gameType + "\n");
        }
        if (metadata.difficulty != null) {
            out.write("  difficulty " + metadata.difficulty + "\n");
        }
        if (metadata.hardcore != null) {
            out.write("  hardcore " + (metadata.hardcore != 0) + "\n");
        }
        if (metadata.allowCommands != null) {
            out.write("  allow_commands " + (metadata.allowCommands != 0) + "\n");
        }
        if (hasNonDefaultWeather(metadata)) {
            out.write("  weather {\n");
            if (metadata.raining != null) {
                out.write("    raining " + (metadata.raining != 0) + "\n");
            }
            if (metadata.rainTime != null) {
                out.write("    rain_time " + metadata.rainTime + "\n");
            }
            if (metadata.thundering != null) {
                out.write("    thundering " + (metadata.thundering != 0) + "\n");
            }
            if (metadata.thunderTime != null) {
                out.write("    thunder_time " + metadata.thunderTime + "\n");
            }
            if (metadata.clearWeatherTime != null) {
                out.write("    clear_weather_time " + metadata.clearWeatherTime + "\n");
            }
            out.write("  }\n");
        }
        if (hasNonDefaultWorldBorder(metadata)) {
            WorldSpec.WorldBorder border = metadata.worldBorder;
            out.write("  world_border {\n");
            out.write("    center " + doubleLiteral(border.centerX()) + " " + doubleLiteral(border.centerZ()) + "\n");
            out.write("    size " + doubleLiteral(border.size()) + "\n");
            out.write("    size_lerp_target " + doubleLiteral(border.sizeLerpTarget()) + "\n");
            out.write("    size_lerp_time " + border.sizeLerpTime() + "\n");
            out.write("    warning_blocks " + border.warningBlocks() + "\n");
            out.write("    warning_time " + border.warningTime() + "\n");
            out.write("    damage_buffer " + doubleLiteral(border.damageBuffer()) + "\n");
            out.write("    damage_per_block " + doubleLiteral(border.damagePerBlock()) + "\n");
            out.write("  }\n");
        }
        out.write("  default_biome " + scan.defaultBiome + "\n");
        for (Map.Entry<String, String> entry : metadata.gameRules.entrySet()) {
            if (entry.getKey().matches("[A-Za-z0-9_]+")) {
                out.write("  game_rule " + entry.getKey() + " " + quote(entry.getValue()) + "\n");
            }
        }
    }

    private static boolean hasNonDefaultWeather(WorldMetadata metadata) {
        return (metadata.raining != null && metadata.raining != 0)
                || (metadata.rainTime != null && metadata.rainTime != 0)
                || (metadata.thundering != null && metadata.thundering != 0)
                || (metadata.thunderTime != null && metadata.thunderTime != 0)
                || (metadata.clearWeatherTime != null && metadata.clearWeatherTime != 0);
    }

    private static boolean hasNonDefaultWorldBorder(WorldMetadata metadata) {
        return metadata.worldBorder != null && !WorldSpec.WorldBorder.defaults().equals(metadata.worldBorder);
    }

    private void writePassthroughMetadata(BufferedWriter out, Path worldDir, ImportReport report) throws Exception {
        for (String folder : report.unsupportedFolders) {
            Path source = worldDir.resolve(folder);
            if (Files.exists(source)) {
                writePassthroughDirectives(out, folder, source, report);
            }
        }
    }

    private void writePassthroughDirectives(BufferedWriter out, String target, Path source, ImportReport report)
            throws Exception {
        if (!Files.isDirectory(source) || invalidGzipDataFiles(source).isEmpty()) {
            out.write("  passthrough " + quote(target) + " from " + quote(source.toString()) + "\n");
            return;
        }
        Set<Path> invalidDataFiles = invalidGzipDataFiles(source);
        for (Path invalid : invalidDataFiles) {
            report.skippedNbt++;
            out.write("  # Skipped corrupt passthrough NBT data file "
                    + quote(target + "/" + source.relativize(invalid)) + "\n");
        }
        try (var paths = Files.walk(source)) {
            for (Path file : paths
                    .filter(Files::isRegularFile)
                    .filter(file -> !invalidDataFiles.contains(file))
                    .sorted()
                    .toList()) {
                String relative = source.relativize(file).toString();
                out.write("  passthrough " + quote(target + "/" + relative)
                        + " from " + quote(file.toString()) + "\n");
            }
        }
    }

    private Set<Path> invalidGzipDataFiles(Path source) throws Exception {
        Set<Path> invalid = new HashSet<>();
        try (var paths = Files.walk(source)) {
            for (Path file : paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".dat"))
                    .sorted()
                    .toList()) {
                if (!canReadNbt(file)) {
                    invalid.add(file);
                }
            }
        }
        return invalid;
    }

    private boolean canReadNbt(Path file) {
        try (NBTReader reader = new NBTReader(file, CompressedProcesser.GZIP)) {
            return reader.readNamed().getSecond() instanceof NBTCompound;
        } catch (Exception ignored) {
            try (NBTReader reader = new NBTReader(file)) {
                return reader.readNamed().getSecond() instanceof NBTCompound;
            } catch (Exception error) {
                return false;
            }
        }
    }

    private void importReviewable(Path worldDir, Path outputDir, WorldMetadata metadata,
                                  List<ChunkCoordinate> chunks, Scan scan, ImportOptions options,
                                  ImportReport report) throws Exception {
        Files.createDirectories(outputDir);
        Files.createDirectories(outputDir.resolve("groups"));
        Files.createDirectories(outputDir.resolve("components"));
        ComponentExtraction components = extractComponents(worldDir, metadata, chunks, scan, options);
        List<String> includes = new ArrayList<>();
        includes.add("meta.atf");
        for (ExtractedComponent component : components.components) {
            String include = "components/" + component.name + ".atf";
            includes.add(include);
            writeComponent(outputDir.resolve(include), component, report);
        }
        try (BufferedWriter meta = Files.newBufferedWriter(outputDir.resolve("meta.atf"), StandardCharsets.UTF_8)) {
            meta.write("# Imported world metadata from " + worldDir.toAbsolutePath() + "\n");
            if (!report.unsupportedFolders.isEmpty()) {
                meta.write("# Skipped non-block save data: " + String.join(", ", report.unsupportedFolders) + "\n");
            }
            meta.write("# Component analysis: " + components.candidatePlacements + " candidate placement(s), "
                    + components.components.size() + " promoted component(s), "
                    + components.promotedPlacements + " promoted placement(s)\n");
            writeMetadataCoreDirectives(meta, metadata, scan);
        }
        report.componentCandidates += components.candidatePlacements;
        report.promotedComponents += components.components.size();
        report.componentPlacements += components.promotedPlacements;

        int chunksSeen = 0;
        try (RegionCache cache = new RegionCache(worldDir, "region");
             RegionCache entityCache = new RegionCache(worldDir, "entities")) {
            for (ChunkCoordinate coordinate : chunks) {
                ChunkColumn chunk = loadChunk(cache.region(coordinate.region), coordinate.x, coordinate.z,
                        metadata.dataVersion);
                List<NBTCompound> externalEntities = externalEntities(entityCache, coordinate);
                if (!hasImportableContent(chunk) && externalEntities.isEmpty()) {
                    continue;
                }
                if (options.chunkLimit > 0 && chunksSeen >= options.chunkLimit) {
                    break;
                }
                chunksSeen++;
                String include = "groups/imported-" + coordinate.x + "-" + coordinate.z + ".atf";
                includes.add(include);
                List<ComponentPlacement> placements = components.placementsByChunk
                        .getOrDefault(new ChunkPos(coordinate.x, coordinate.z), List.of());
                try (BufferedWriter out = Files.newBufferedWriter(outputDir.resolve(include), StandardCharsets.UTF_8)) {
                    writeReviewableChunk(out, chunk, externalEntities, scan.defaultBiome, placements,
                            components.allPlacements, report);
                }
            }
        }

        try (BufferedWriter world = Files.newBufferedWriter(outputDir.resolve("world.atf"), StandardCharsets.UTF_8)) {
            world.write("# Generated by anvil-tf reviewable import from " + worldDir.toAbsolutePath() + "\n");
            if (scan.regionsTruncated) {
                world.write("# Import note: output truncated at --chunk-limit " + options.chunkLimit + "\n");
            }
            world.write("world " + quote(metadata.name) + " {\n");
            for (String include : includes) {
                world.write("  include " + quote(include) + "\n");
            }
            world.write("}\n");
        }
    }

    private void writeReviewableChunk(BufferedWriter out, ChunkColumn chunk, List<NBTCompound> externalEntities,
                                      String defaultBiome, List<ComponentPlacement> placements,
                                      List<ComponentPlacement> allPlacements,
                                      ImportReport report) throws Exception {
        int originX = chunk.getX() * 16;
        int originY = chunk.getMinY();
        int originZ = chunk.getZ() * 16;
        Set<BlockCoordinate> componentBlocks = componentBlocks(chunk, allPlacements);
        out.write("group " + quote("imported " + chunk.getX() + "," + chunk.getZ()) + " {\n");
        out.write("  origin " + originX + " " + originY + " " + originZ + "\n");
        out.write("  bounds 0 0 0 to 15 " + (chunk.getMaxY() - chunk.getMinY()) + " 15\n\n");
        writeComponentPlacements(out, placements, report);
        writeReviewableBlockGeometry(out, chunk, componentBlocks, report);
        writeReviewableBiomes(out, chunk, defaultBiome, report);
        writeReviewableBlockEntities(out, chunk, originX, originY, originZ, report);
        writeReviewableEntities(out, chunk, originX, originY, originZ, report);
        writeReviewableExternalEntities(out, externalEntities, originX, originY, originZ, report);
        out.write("}\n");
    }

    private void writeComponentPlacements(BufferedWriter out, List<ComponentPlacement> placements, ImportReport report)
            throws Exception {
        if (placements.isEmpty()) {
            return;
        }
        for (ComponentPlacement placement : placements.stream().sorted().toList()) {
            out.write("  place " + placement.component.name + " at "
                    + placement.localX + " " + placement.localY + " " + placement.localZ + "\n");
            report.blockCommands++;
        }
        out.write("\n");
    }

    private Set<BlockCoordinate> componentBlocks(ChunkColumn chunk, List<ComponentPlacement> placements) {
        Set<BlockCoordinate> blocks = new HashSet<>();
        int chunkOriginX = chunk.getX() * 16;
        int chunkOriginZ = chunk.getZ() * 16;
        for (ComponentPlacement placement : placements) {
            int placementOriginX = placement.chunk.x() * 16 + placement.localX;
            int placementOriginZ = placement.chunk.z() * 16 + placement.localZ;
            for (ComponentBlock block : placement.component.blocks) {
                int localX = placementOriginX + block.x - chunkOriginX;
                int localZ = placementOriginZ + block.z - chunkOriginZ;
                if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                    blocks.add(new BlockCoordinate(localX, placement.localY + block.y, localZ));
                }
            }
        }
        return blocks;
    }

    private void writeReviewableBlockGeometry(BufferedWriter out, ChunkColumn chunk,
                                              Set<BlockCoordinate> componentBlocks, ImportReport report)
            throws Exception {
        int height = chunk.getMaxY() - chunk.getMinY() + 1;
        boolean[][][] consumed = new boolean[height][16][16];
        for (BlockCoordinate block : componentBlocks) {
            if (block.y >= 0 && block.y < height && block.x >= 0 && block.x < 16 && block.z >= 0 && block.z < 16) {
                consumed[block.y][block.z][block.x] = true;
            }
        }
        for (int localY = 0; localY < height; localY++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (consumed[localY][z][x]) {
                        continue;
                    }
                    String state = importBlockState(chunk, localY, z, x);
                    if (AIR.equals(state)) {
                        consumed[localY][z][x] = true;
                        continue;
                    }
                    int endX = importBlockRunEndX(chunk, consumed, state, localY, z, x);
                    int endZ = importBlockRunEndZ(chunk, consumed, state, localY, z, x, endX);
                    int endY = importBlockRunEndY(chunk, consumed, state, localY, z, x, endX, endZ);
                    for (int markY = localY; markY <= endY; markY++) {
                        for (int markZ = z; markZ <= endZ; markZ++) {
                            for (int markX = x; markX <= endX; markX++) {
                                consumed[markY][markZ][markX] = true;
                            }
                        }
                    }
                    writeReviewableBlockCuboid(out, x, localY, z, endX, endY, endZ, state);
                    report.blockCommands++;
                }
            }
        }
    }

    private int importBlockRunEndX(ChunkColumn chunk, boolean[][][] consumed, String state,
                                   int localY, int z, int startX) {
        int endX = startX;
        while (endX + 1 < 16 && importBlockMatches(chunk, consumed, state, localY, z, endX + 1)) {
            endX++;
        }
        return endX;
    }

    private int importBlockRunEndZ(ChunkColumn chunk, boolean[][][] consumed, String state,
                                   int localY, int startZ, int startX, int endX) {
        int endZ = startZ;
        while (endZ + 1 < 16 && importBlockRowMatches(chunk, consumed, state, localY, endZ + 1, startX, endX)) {
            endZ++;
        }
        return endZ;
    }

    private int importBlockRunEndY(ChunkColumn chunk, boolean[][][] consumed, String state,
                                   int startY, int startZ, int startX, int endX, int endZ) {
        int endY = startY;
        while (endY + 1 < consumed.length
                && importBlockLayerRectangleMatches(chunk, consumed, state, endY + 1,
                startZ, endZ, startX, endX)) {
            endY++;
        }
        return endY;
    }

    private boolean importBlockLayerRectangleMatches(ChunkColumn chunk, boolean[][][] consumed, String state,
                                                     int localY, int startZ, int endZ, int startX, int endX) {
        for (int z = startZ; z <= endZ; z++) {
            if (!importBlockRowMatches(chunk, consumed, state, localY, z, startX, endX)) {
                return false;
            }
        }
        return true;
    }

    private boolean importBlockRowMatches(ChunkColumn chunk, boolean[][][] consumed, String state,
                                          int localY, int z, int startX, int endX) {
        for (int x = startX; x <= endX; x++) {
            if (!importBlockMatches(chunk, consumed, state, localY, z, x)) {
                return false;
            }
        }
        return true;
    }

    private boolean importBlockMatches(ChunkColumn chunk, boolean[][][] consumed, String state,
                                       int localY, int z, int x) {
        return !consumed[localY][z][x] && state.equals(importBlockState(chunk, localY, z, x));
    }

    private String importBlockState(ChunkColumn chunk, int localY, int z, int x) {
        return blockState(chunk.getBlockState(x, chunk.getMinY() + localY, z));
    }

    private void writeReviewableBlockCuboid(BufferedWriter out, int x1, int y1, int z1,
                                            int x2, int y2, int z2, String state) throws Exception {
        if (x1 == x2 && y1 == y2 && z1 == z2) {
            out.write("  set " + x1 + " " + y1 + " " + z1 + " " + state + "\n");
            return;
        }
        out.write("  fill " + x1 + " " + y1 + " " + z1 + " to "
                + x2 + " " + y2 + " " + z2 + " " + state + "\n");
    }

    private String solidLayerState(ChunkColumn chunk, int y, int localY, Set<BlockCoordinate> componentBlocks) {
        if (layerHasComponentBlocks(localY, componentBlocks)) {
            return null;
        }
        String first = blockState(chunk.getBlockState(0, y, 0));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (!first.equals(blockState(chunk.getBlockState(x, y, z)))) {
                    return null;
                }
            }
        }
        return first;
    }

    private boolean layerHasComponentBlocks(int localY, Set<BlockCoordinate> componentBlocks) {
        for (BlockCoordinate block : componentBlocks) {
            if (block.y == localY) {
                return true;
            }
        }
        return false;
    }

    private boolean layerHasBlocks(ChunkColumn chunk, int y, int localY, Set<BlockCoordinate> componentBlocks) {
        for (int z = 0; z < 16; z++) {
            if (!rowIsAir(chunk, y, localY, z, componentBlocks)) {
                return true;
            }
        }
        return false;
    }

    private boolean rowIsAir(ChunkColumn chunk, int y, int localY, int z, Set<BlockCoordinate> componentBlocks) {
        for (int x = 0; x < 16; x++) {
            if (componentBlocks.contains(new BlockCoordinate(x, localY, z))) {
                continue;
            }
            if (!AIR.equals(blockState(chunk.getBlockState(x, y, z)))) {
                return false;
            }
        }
        return true;
    }

    private void writeReviewableSliceRow(BufferedWriter out, ChunkColumn chunk, int y, int localY, int z,
                                         Set<BlockCoordinate> componentBlocks, ImportReport report)
            throws Exception {
        int x = 0;
        while (x < 16) {
            if (componentBlocks.contains(new BlockCoordinate(x, localY, z))) {
                x++;
                continue;
            }
            String state = blockState(chunk.getBlockState(x, y, z));
            if (AIR.equals(state)) {
                x++;
                continue;
            }
            int startX = x++;
            while (x < 16
                    && !componentBlocks.contains(new BlockCoordinate(x, localY, z))
                    && state.equals(blockState(chunk.getBlockState(x, y, z)))) {
                x++;
            }
            int endX = x - 1;
            if (startX == endX) {
                out.write("    set " + startX + " " + z + " " + state + "\n");
            } else {
                out.write("    fill " + startX + " " + z + " to " + endX + " " + z + " " + state + "\n");
            }
            report.blockCommands++;
        }
    }

    private void writeReviewableBiomes(BufferedWriter out, ChunkColumn chunk, String defaultBiome, ImportReport report)
            throws Exception {
        int yCount = 0;
        for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y += 4) {
            yCount++;
        }
        boolean[][][] visited = new boolean[yCount][4][4];
        for (int yi = 0; yi < yCount; yi++) {
            for (int zi = 0; zi < 4; zi++) {
                for (int xi = 0; xi < 4; xi++) {
                    if (visited[yi][zi][xi]) {
                        continue;
                    }
                    String biome = biomeSample(chunk, yi, zi, xi);
                    if (defaultBiome.equals(biome)) {
                        visited[yi][zi][xi] = true;
                        continue;
                    }
                    int endX = biomeRunEndX(chunk, visited, biome, yi, zi, xi);
                    int endZ = biomeRunEndZ(chunk, visited, biome, yi, zi, xi, endX);
                    int endY = biomeRunEndY(chunk, visited, biome, yi, zi, xi, endX, endZ);
                    for (int markY = yi; markY <= endY; markY++) {
                        for (int markZ = zi; markZ <= endZ; markZ++) {
                            for (int markX = xi; markX <= endX; markX++) {
                                visited[markY][markZ][markX] = true;
                            }
                        }
                    }
                    out.write("  fill_biome "
                            + biomeSampleCoordinate(xi) + " " + biomeSampleLocalY(yi) + " "
                            + biomeSampleCoordinate(zi) + " "
                            + biomeSampleCoordinate(endX) + " " + biomeSampleLocalY(endY) + " "
                            + biomeSampleCoordinate(endZ) + " " + biome + "\n");
                    report.biomeCommands++;
                }
            }
        }
    }

    private int biomeRunEndX(ChunkColumn chunk, boolean[][][] visited, String biome, int yi, int zi, int startX) {
        int endX = startX;
        while (endX + 1 < 4
                && !visited[yi][zi][endX + 1]
                && biome.equals(biomeSample(chunk, yi, zi, endX + 1))) {
            endX++;
        }
        return endX;
    }

    private int biomeRunEndZ(ChunkColumn chunk, boolean[][][] visited, String biome, int yi, int startZ,
                             int startX, int endX) {
        int endZ = startZ;
        while (endZ + 1 < 4 && biomeRectangleMatches(chunk, visited, biome, yi, endZ + 1, startX, endX)) {
            endZ++;
        }
        return endZ;
    }

    private int biomeRunEndY(ChunkColumn chunk, boolean[][][] visited, String biome, int startY, int startZ,
                             int startX, int endX, int endZ) {
        int endY = startY;
        while (endY + 1 < visited.length
                && biomeLayerRectangleMatches(chunk, visited, biome, endY + 1, startZ, endZ, startX, endX)) {
            endY++;
        }
        return endY;
    }

    private boolean biomeLayerRectangleMatches(ChunkColumn chunk, boolean[][][] visited, String biome, int yi,
                                               int startZ, int endZ, int startX, int endX) {
        for (int zi = startZ; zi <= endZ; zi++) {
            if (!biomeRectangleMatches(chunk, visited, biome, yi, zi, startX, endX)) {
                return false;
            }
        }
        return true;
    }

    private boolean biomeRectangleMatches(ChunkColumn chunk, boolean[][][] visited, String biome, int yi, int zi,
                                          int startX, int endX) {
        for (int xi = startX; xi <= endX; xi++) {
            if (visited[yi][zi][xi] || !biome.equals(biomeSample(chunk, yi, zi, xi))) {
                return false;
            }
        }
        return true;
    }

    private String biomeSample(ChunkColumn chunk, int yi, int zi, int xi) {
        return chunk.getBiome(biomeSampleCoordinate(xi), chunk.getMinY() + yi * 4, biomeSampleCoordinate(zi));
    }

    private int biomeSampleCoordinate(int index) {
        return index * 4;
    }

    private int biomeSampleLocalY(int yi) {
        return yi * 4;
    }

    private void writeReviewableBlockEntities(BufferedWriter out, ChunkColumn chunk, int originX, int originY,
                                              int originZ, ImportReport report) throws Exception {
        for (NBTCompound blockEntity : chunk.getTileEntities()) {
            String id = blockEntity.getString("id");
            Integer x = blockEntity.getInt("x");
            Integer y = blockEntity.getInt("y");
            Integer z = blockEntity.getInt("z");
            if (id == null || x == null || y == null || z == null) {
                report.skippedNbt++;
                out.write("  # Skipped block entity with missing id/x/y/z\n");
                continue;
            }
            try {
                Map<String, NBT> payload = withoutKeys(blockEntity, BLOCK_ENTITY_STRUCTURAL_KEYS);
                out.write("  block_entity " + (x - originX) + " " + (y - originY) + " " + (z - originZ)
                        + " " + id);
                writeCompoundPayload(out, payload, "  ");
                out.write("\n");
                report.blockEntities++;
            } catch (UnsupportedNbtException error) {
                report.skippedNbt++;
                out.write("  # Skipped block entity " + id + " at " + x + "," + y + "," + z
                        + ": " + error.getMessage() + "\n");
            }
        }
    }

    private void writeReviewableEntities(BufferedWriter out, ChunkColumn chunk, int originX, int originY,
                                         int originZ, ImportReport report) throws Exception {
        for (NBTCompound entity : chunk.getEntities()) {
            writeReviewableEntity(out, entity, originX, originY, originZ, report);
        }
    }

    private void writeReviewableExternalEntities(BufferedWriter out, List<NBTCompound> entities, int originX,
                                                 int originY, int originZ, ImportReport report) throws Exception {
        for (NBTCompound entity : entities) {
            writeReviewableEntity(out, entity, originX, originY, originZ, report);
        }
    }

    private void writeReviewableEntity(BufferedWriter out, NBTCompound entity, int originX, int originY, int originZ,
                                       ImportReport report) throws Exception {
        String id = entity.getString("id");
        NBTList<NBT> pos = entity.getList("Pos");
        if (id == null || pos == null || pos.getSize() < 3
                || !(pos.get(0) instanceof NBTDouble x)
                || !(pos.get(1) instanceof NBTDouble y)
                || !(pos.get(2) instanceof NBTDouble z)) {
            report.skippedNbt++;
            out.write("  # Skipped entity with missing id or Pos\n");
            return;
        }
        try {
            Map<String, NBT> payload = withoutKeys(entity, ENTITY_STRUCTURAL_KEYS);
            out.write("  entity " + doubleLiteral(x.getValue() - originX) + " "
                    + doubleLiteral(y.getValue() - originY) + " "
                    + doubleLiteral(z.getValue() - originZ) + " " + id);
            writeCompoundPayload(out, payload, "  ");
            out.write("\n");
            report.entities++;
        } catch (UnsupportedNbtException error) {
            report.skippedNbt++;
            out.write("  # Skipped entity " + id + ": " + error.getMessage() + "\n");
        }
    }

    private ComponentExtraction extractComponents(Path worldDir, WorldMetadata metadata,
                                                  List<ChunkCoordinate> chunks, Scan scan, ImportOptions options)
            throws Exception {
        Map<String, List<ComponentCandidate>> candidatesByFingerprint = new HashMap<>();
        int candidatePlacements = 0;
        int chunksSeen = 0;
        Set<ChunkPos> chunkPositions = new HashSet<>();
        for (ChunkCoordinate coordinate : chunks) {
            chunkPositions.add(new ChunkPos(coordinate.x, coordinate.z));
        }
        try (RegionCache cache = new RegionCache(worldDir, "region");
             RegionCache entityCache = new RegionCache(worldDir, "entities")) {
            for (ChunkCoordinate coordinate : chunks) {
                ChunkColumn chunk = loadChunk(cache.region(coordinate.region), coordinate.x, coordinate.z,
                        metadata.dataVersion);
                if (!hasImportableContent(chunk) && externalEntities(entityCache, coordinate).isEmpty()) {
                    continue;
                }
                if (options.chunkLimit > 0 && chunksSeen >= options.chunkLimit) {
                    break;
                }
                chunksSeen++;
                for (ComponentCandidate candidate : componentCandidates(chunk)) {
                    candidatesByFingerprint
                            .computeIfAbsent(candidate.fingerprint, ignored -> new ArrayList<>())
                            .add(candidate);
                    candidatePlacements++;
                }
                ChunkPos east = new ChunkPos(coordinate.x + 1, coordinate.z);
                if (chunkPositions.contains(east)) {
                    RegionCoordinate eastRegion = new RegionCoordinate(Math.floorDiv(east.x(), 32),
                            Math.floorDiv(east.z(), 32));
                    ChunkColumn eastChunk = loadChunk(cache.region(eastRegion), east.x(), east.z(),
                            metadata.dataVersion);
                    for (ComponentCandidate candidate : crossChunkComponentCandidates(chunk, eastChunk)) {
                        candidatesByFingerprint
                                .computeIfAbsent(candidate.fingerprint, ignored -> new ArrayList<>())
                                .add(candidate);
                        candidatePlacements++;
                    }
                }
            }
        }

        List<Map.Entry<String, List<ComponentCandidate>>> repeated = candidatesByFingerprint.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 3)
                .filter(entry -> componentSavings(entry.getValue()) >= 4)
                .sorted(Comparator
                        .<Map.Entry<String, List<ComponentCandidate>>>comparingInt(
                                entry -> componentSavings(entry.getValue()))
                        .reversed()
                        .thenComparing(Comparator
                        .<Map.Entry<String, List<ComponentCandidate>>>comparingInt(entry -> entry.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey)))
                .limit(128)
                .toList();

        List<ExtractedComponent> components = new ArrayList<>();
        Map<ChunkPos, List<ComponentPlacement>> placementsByChunk = new HashMap<>();
        List<ComponentPlacement> allPlacements = new ArrayList<>();
        Set<WorldBlockCoordinate> promotedBlocks = new HashSet<>();
        int promotedPlacements = 0;
        int componentIndex = 1;
        for (Map.Entry<String, List<ComponentCandidate>> entry : repeated) {
            List<ComponentCandidate> placements = entry.getValue().stream()
                    .filter(placement -> !overlapsPromotedBlocks(placement, promotedBlocks))
                    .toList();
            if (placements.size() < 3 || componentSavings(placements) < 4) {
                continue;
            }
            ExtractedComponent component = new ExtractedComponent(
                    "component-%04d".formatted(componentIndex++), placements.get(0).blocks);
            components.add(component);
            promotedPlacements += placements.size();
            for (ComponentCandidate placement : placements) {
                ComponentPlacement promoted = new ComponentPlacement(component, placement.chunk,
                        placement.localX, placement.localY, placement.localZ);
                allPlacements.add(promoted);
                placementsByChunk.computeIfAbsent(placement.chunk, ignored -> new ArrayList<>())
                        .add(promoted);
                promotedBlocks.addAll(worldBlocks(placement));
            }
        }
        for (List<ComponentPlacement> placements : placementsByChunk.values()) {
            placements.sort(Comparator.naturalOrder());
        }
        allPlacements.sort(Comparator.naturalOrder());
        return new ComponentExtraction(components, placementsByChunk, allPlacements,
                candidatePlacements, promotedPlacements);
    }

    private int componentSavings(List<ComponentCandidate> placements) {
        int inlineCommands = placements.size() * placements.get(0).blocks.size();
        int extractedCommands = componentCommandCount(placements.get(0).blocks) + placements.size();
        return inlineCommands - extractedCommands;
    }

    private boolean overlapsPromotedBlocks(ComponentCandidate placement, Set<WorldBlockCoordinate> promotedBlocks) {
        for (WorldBlockCoordinate block : worldBlocks(placement)) {
            if (promotedBlocks.contains(block)) {
                return true;
            }
        }
        return false;
    }

    private List<WorldBlockCoordinate> worldBlocks(ComponentCandidate placement) {
        int originX = placement.chunk.x() * 16 + placement.localX;
        int originZ = placement.chunk.z() * 16 + placement.localZ;
        return placement.blocks.stream()
                .map(block -> new WorldBlockCoordinate(originX + block.x, placement.localY + block.y,
                        originZ + block.z))
                .toList();
    }

    private List<ComponentCandidate> componentCandidates(ChunkColumn chunk) {
        Set<BlockCoordinate> blockEntities = blockEntityCoordinates(chunk);
        List<ComponentCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        candidates.addAll(connectedComponentCandidates(chunk, blockEntities));
        for (int z = 1; z < 15; z++) {
            for (int x = 1; x < 15; x++) {
                int y = chunk.getMinY();
                while (y <= chunk.getMaxY()) {
                    if (AIR.equals(blockState(chunk.getBlockState(x, y, z))) || !isLaterallyIsolated(chunk, x, y, z)) {
                        y++;
                        continue;
                    }
                    int startY = y;
                    List<String> stackBlocks = new ArrayList<>();
                    while (y <= chunk.getMaxY()
                            && !AIR.equals(blockState(chunk.getBlockState(x, y, z)))
                            && isLaterallyIsolated(chunk, x, y, z)) {
                        stackBlocks.add(blockState(chunk.getBlockState(x, y, z)));
                        y++;
                    }
                    int endY = y - 1;
                    int localY = startY - chunk.getMinY();
                    if (isPromotableVerticalStack(chunk, x, z, startY, endY, blockEntities)) {
                        List<ComponentBlock> blocks = new ArrayList<>();
                        for (int blockY = 0; blockY < endY - startY + 1; blockY++) {
                            blocks.add(new ComponentBlock(0, blockY, 0, blockState(chunk.getBlockState(x,
                                    startY + blockY, z))));
                        }
                        candidates.add(new ComponentCandidate(
                                new ChunkPos(chunk.getX(), chunk.getZ()), x, localY, z,
                                List.copyOf(blocks), fingerprint(blocks)));
                    }
                }
            }
        }
        List<ComponentCandidate> unique = new ArrayList<>();
        for (ComponentCandidate candidate : candidates) {
            String key = candidate.localX + "," + candidate.localY + "," + candidate.localZ + "\n"
                    + candidate.fingerprint;
            if (seen.add(key)) {
                unique.add(candidate);
            }
        }
        return unique;
    }

    private List<ComponentCandidate> connectedComponentCandidates(ChunkColumn chunk, Set<BlockCoordinate> blockEntities) {
        Set<BlockCoordinate> visited = new HashSet<>();
        List<ComponentCandidate> candidates = new ArrayList<>();
        for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
            int localY = y - chunk.getMinY();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockCoordinate start = new BlockCoordinate(x, localY, z);
                    if (visited.contains(start) || AIR.equals(blockState(chunk.getBlockState(x, y, z)))) {
                        continue;
                    }
                    List<BlockCoordinate> component = collectConnectedComponent(chunk, start, visited);
                    ComponentCandidate candidate = connectedComponentCandidate(chunk, component, blockEntities);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return candidates;
    }

    private List<BlockCoordinate> collectConnectedComponent(ChunkColumn chunk, BlockCoordinate start,
                                                            Set<BlockCoordinate> visited) {
        List<BlockCoordinate> component = new ArrayList<>();
        ArrayDeque<BlockCoordinate> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockCoordinate current = queue.removeFirst();
            component.add(current);
            for (BlockCoordinate neighbor : neighbors(current)) {
                if (neighbor.x < 0 || neighbor.x >= 16 || neighbor.z < 0 || neighbor.z >= 16
                        || neighbor.y < 0 || neighbor.y > chunk.getMaxY() - chunk.getMinY()
                        || visited.contains(neighbor)) {
                    continue;
                }
                if (isSupportCut(chunk, current, neighbor)) {
                    continue;
                }
                int worldY = chunk.getMinY() + neighbor.y;
                if (AIR.equals(blockState(chunk.getBlockState(neighbor.x, worldY, neighbor.z)))) {
                    continue;
                }
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }
        return component;
    }

    private boolean isSupportCut(ChunkColumn chunk, BlockCoordinate current, BlockCoordinate neighbor) {
        if (current.x != neighbor.x || current.z != neighbor.z || Math.abs(current.y - neighbor.y) != 1) {
            return false;
        }
        if (neighbor.y > current.y) {
            return isBroadSupport(chunk, current);
        }
        return isBroadSupport(chunk, neighbor);
    }

    private boolean isBroadSupport(ChunkColumn chunk, BlockCoordinate coordinate) {
        int worldY = chunk.getMinY() + coordinate.y;
        int neighbors = 0;
        if (coordinate.x > 0 && !AIR.equals(blockState(chunk.getBlockState(coordinate.x - 1, worldY, coordinate.z)))) {
            neighbors++;
        }
        if (coordinate.x < 15 && !AIR.equals(blockState(chunk.getBlockState(coordinate.x + 1, worldY, coordinate.z)))) {
            neighbors++;
        }
        if (coordinate.z > 0 && !AIR.equals(blockState(chunk.getBlockState(coordinate.x, worldY, coordinate.z - 1)))) {
            neighbors++;
        }
        if (coordinate.z < 15 && !AIR.equals(blockState(chunk.getBlockState(coordinate.x, worldY, coordinate.z + 1)))) {
            neighbors++;
        }
        return neighbors >= 3;
    }

    private List<BlockCoordinate> neighbors(BlockCoordinate coordinate) {
        return List.of(
                new BlockCoordinate(coordinate.x + 1, coordinate.y, coordinate.z),
                new BlockCoordinate(coordinate.x - 1, coordinate.y, coordinate.z),
                new BlockCoordinate(coordinate.x, coordinate.y + 1, coordinate.z),
                new BlockCoordinate(coordinate.x, coordinate.y - 1, coordinate.z),
                new BlockCoordinate(coordinate.x, coordinate.y, coordinate.z + 1),
                new BlockCoordinate(coordinate.x, coordinate.y, coordinate.z - 1));
    }

    private ComponentCandidate connectedComponentCandidate(ChunkColumn chunk, List<BlockCoordinate> component,
                                                           Set<BlockCoordinate> blockEntities) {
        if (component.size() < 4 || component.size() > 512) {
            return null;
        }
        int minX = component.stream().mapToInt(BlockCoordinate::x).min().orElse(0);
        int maxX = component.stream().mapToInt(BlockCoordinate::x).max().orElse(0);
        int minY = component.stream().mapToInt(BlockCoordinate::y).min().orElse(0);
        int maxY = component.stream().mapToInt(BlockCoordinate::y).max().orElse(0);
        int minZ = component.stream().mapToInt(BlockCoordinate::z).min().orElse(0);
        int maxZ = component.stream().mapToInt(BlockCoordinate::z).max().orElse(0);
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        int volume = width * height * depth;
        if (width > 16 || height > 16 || depth > 16 || volume > 2048) {
            return null;
        }
        if (touchesChunkBoundary(minX, maxX, minZ, maxZ) && !isBoundaryObjectLike(component, volume)) {
            return null;
        }
        if (minY == 0 || maxY == chunk.getMaxY() - chunk.getMinY()) {
            return null;
        }
        if (component.stream().anyMatch(blockEntities::contains)) {
            return null;
        }
        if (component.size() > volume * 0.85 && volume > 64) {
            return null;
        }
        List<ComponentBlock> blocks = component.stream()
                .map(block -> new ComponentBlock(block.x - minX, block.y - minY, block.z - minZ,
                        blockState(chunk.getBlockState(block.x, chunk.getMinY() + block.y, block.z))))
                .sorted()
                .toList();
        return new ComponentCandidate(new ChunkPos(chunk.getX(), chunk.getZ()), minX, minY, minZ,
                blocks, fingerprint(blocks));
    }

    private boolean touchesChunkBoundary(int minX, int maxX, int minZ, int maxZ) {
        return minX == 0 || maxX == 15 || minZ == 0 || maxZ == 15;
    }

    private boolean isBoundaryObjectLike(List<BlockCoordinate> component, int volume) {
        if (component.size() > 192 || volume > 768) {
            return false;
        }
        int boundaryBlocks = 0;
        for (BlockCoordinate block : component) {
            if (block.x == 0 || block.x == 15 || block.z == 0 || block.z == 15) {
                boundaryBlocks++;
            }
        }
        return boundaryBlocks <= Math.max(32, component.size() / 2);
    }

    private List<ComponentCandidate> crossChunkComponentCandidates(ChunkColumn west, ChunkColumn east) {
        Set<BlockCoordinate> visited = new HashSet<>();
        Set<BlockCoordinate> blockEntities = crossChunkBlockEntityCoordinates(west, east);
        List<ComponentCandidate> candidates = new ArrayList<>();
        for (int y = west.getMinY(); y <= west.getMaxY(); y++) {
            int localY = y - west.getMinY();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 32; x++) {
                    BlockCoordinate start = new BlockCoordinate(x, localY, z);
                    if (visited.contains(start) || AIR.equals(crossChunkBlockState(west, east, x, y, z))) {
                        continue;
                    }
                    List<BlockCoordinate> component = collectCrossChunkComponent(west, east, start, visited);
                    ComponentCandidate candidate = crossChunkComponentCandidate(west, east, component, blockEntities);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return candidates;
    }

    private List<BlockCoordinate> collectCrossChunkComponent(ChunkColumn west, ChunkColumn east,
                                                             BlockCoordinate start,
                                                             Set<BlockCoordinate> visited) {
        List<BlockCoordinate> component = new ArrayList<>();
        ArrayDeque<BlockCoordinate> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockCoordinate current = queue.removeFirst();
            component.add(current);
            for (BlockCoordinate neighbor : neighbors(current)) {
                if (neighbor.x < 0 || neighbor.x >= 32 || neighbor.z < 0 || neighbor.z >= 16
                        || neighbor.y < 0 || neighbor.y > west.getMaxY() - west.getMinY()
                        || visited.contains(neighbor)) {
                    continue;
                }
                if (isCrossChunkSupportCut(west, east, current, neighbor)) {
                    continue;
                }
                int worldY = west.getMinY() + neighbor.y;
                if (AIR.equals(crossChunkBlockState(west, east, neighbor.x, worldY, neighbor.z))) {
                    continue;
                }
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }
        return component;
    }

    private ComponentCandidate crossChunkComponentCandidate(ChunkColumn west, ChunkColumn east,
                                                            List<BlockCoordinate> component,
                                                            Set<BlockCoordinate> blockEntities) {
        if (component.size() < 4 || component.size() > 768) {
            return null;
        }
        int minX = component.stream().mapToInt(BlockCoordinate::x).min().orElse(0);
        int maxX = component.stream().mapToInt(BlockCoordinate::x).max().orElse(0);
        int minY = component.stream().mapToInt(BlockCoordinate::y).min().orElse(0);
        int maxY = component.stream().mapToInt(BlockCoordinate::y).max().orElse(0);
        int minZ = component.stream().mapToInt(BlockCoordinate::z).min().orElse(0);
        int maxZ = component.stream().mapToInt(BlockCoordinate::z).max().orElse(0);
        if (minX >= 16 || maxX < 16) {
            return null;
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        int volume = width * height * depth;
        if (width > 32 || height > 16 || depth > 16 || volume > 4096) {
            return null;
        }
        if (minY == 0 || maxY == west.getMaxY() - west.getMinY()) {
            return null;
        }
        if (component.stream().anyMatch(blockEntities::contains)) {
            return null;
        }
        if (component.size() > volume * 0.95 && volume > 512) {
            return null;
        }
        List<ComponentBlock> blocks = component.stream()
                .map(block -> new ComponentBlock(block.x - minX, block.y - minY, block.z - minZ,
                        crossChunkBlockState(west, east, block.x, west.getMinY() + block.y, block.z)))
                .sorted()
                .toList();
        return new ComponentCandidate(new ChunkPos(west.getX(), west.getZ()), minX, minY, minZ,
                blocks, fingerprint(blocks));
    }

    private boolean isCrossChunkSupportCut(ChunkColumn west, ChunkColumn east,
                                           BlockCoordinate current, BlockCoordinate neighbor) {
        if (current.x != neighbor.x || current.z != neighbor.z || Math.abs(current.y - neighbor.y) != 1) {
            return false;
        }
        if (neighbor.y > current.y) {
            return isCrossChunkBroadSupport(west, east, current);
        }
        return isCrossChunkBroadSupport(west, east, neighbor);
    }

    private boolean isCrossChunkBroadSupport(ChunkColumn west, ChunkColumn east, BlockCoordinate coordinate) {
        int worldY = west.getMinY() + coordinate.y;
        int neighbors = 0;
        if (coordinate.x > 0 && !AIR.equals(crossChunkBlockState(west, east,
                coordinate.x - 1, worldY, coordinate.z))) {
            neighbors++;
        }
        if (coordinate.x < 31 && !AIR.equals(crossChunkBlockState(west, east,
                coordinate.x + 1, worldY, coordinate.z))) {
            neighbors++;
        }
        if (coordinate.z > 0 && !AIR.equals(crossChunkBlockState(west, east,
                coordinate.x, worldY, coordinate.z - 1))) {
            neighbors++;
        }
        if (coordinate.z < 15 && !AIR.equals(crossChunkBlockState(west, east,
                coordinate.x, worldY, coordinate.z + 1))) {
            neighbors++;
        }
        return neighbors >= 3;
    }

    private String crossChunkBlockState(ChunkColumn west, ChunkColumn east, int windowX, int y, int z) {
        if (windowX < 16) {
            return blockState(west.getBlockState(windowX, y, z));
        }
        return blockState(east.getBlockState(windowX - 16, y, z));
    }

    private Set<BlockCoordinate> crossChunkBlockEntityCoordinates(ChunkColumn west, ChunkColumn east) {
        Set<BlockCoordinate> coordinates = new HashSet<>();
        int westOriginX = west.getX() * 16;
        int westOriginZ = west.getZ() * 16;
        for (NBTCompound blockEntity : west.getTileEntities()) {
            Integer x = blockEntity.getInt("x");
            Integer y = blockEntity.getInt("y");
            Integer z = blockEntity.getInt("z");
            if (x != null && y != null && z != null) {
                coordinates.add(new BlockCoordinate(x - westOriginX, y - west.getMinY(), z - westOriginZ));
            }
        }
        for (NBTCompound blockEntity : east.getTileEntities()) {
            Integer x = blockEntity.getInt("x");
            Integer y = blockEntity.getInt("y");
            Integer z = blockEntity.getInt("z");
            if (x != null && y != null && z != null) {
                coordinates.add(new BlockCoordinate(x - westOriginX, y - west.getMinY(), z - westOriginZ));
            }
        }
        return coordinates;
    }

    private String fingerprint(List<ComponentBlock> blocks) {
        StringBuilder result = new StringBuilder();
        for (ComponentBlock block : blocks.stream().sorted().toList()) {
            result.append(block.x).append(',').append(block.y).append(',').append(block.z)
                    .append('=').append(block.state).append('\n');
        }
        return result.toString();
    }

    private boolean isPromotableVerticalStack(ChunkColumn chunk, int x, int z, int startY, int endY,
                                             Set<BlockCoordinate> blockEntities) {
        int height = endY - startY + 1;
        if (height < 2 || height > 12 || startY <= chunk.getMinY() || endY >= chunk.getMaxY()) {
            return false;
        }
        if (!AIR.equals(blockState(chunk.getBlockState(x, endY + 1, z)))) {
            return false;
        }
        for (int y = startY; y <= endY; y++) {
            int localY = y - chunk.getMinY();
            if (blockEntities.contains(new BlockCoordinate(x, localY, z))) {
                return false;
            }
            if (!isLaterallyIsolated(chunk, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private boolean isLaterallyIsolated(ChunkColumn chunk, int x, int y, int z) {
        return AIR.equals(blockState(chunk.getBlockState(x - 1, y, z)))
                && AIR.equals(blockState(chunk.getBlockState(x + 1, y, z)))
                && AIR.equals(blockState(chunk.getBlockState(x, y, z - 1)))
                && AIR.equals(blockState(chunk.getBlockState(x, y, z + 1)));
    }

    private Set<BlockCoordinate> blockEntityCoordinates(ChunkColumn chunk) {
        Set<BlockCoordinate> coordinates = new HashSet<>();
        int originX = chunk.getX() * 16;
        int originZ = chunk.getZ() * 16;
        for (NBTCompound blockEntity : chunk.getTileEntities()) {
            Integer x = blockEntity.getInt("x");
            Integer y = blockEntity.getInt("y");
            Integer z = blockEntity.getInt("z");
            if (x != null && y != null && z != null) {
                coordinates.add(new BlockCoordinate(x - originX, y - chunk.getMinY(), z - originZ));
            }
        }
        return coordinates;
    }

    private void writeComponent(Path path, ExtractedComponent component, ImportReport report) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("component " + quote(component.name) + " {\n");
            out.write("  bounds 0 0 0 to " + component.maxX() + " " + component.maxY()
                    + " " + component.maxZ() + "\n");
            writeComponentBlocks(out, component.blocks, "  ", report);
            out.write("}\n");
        }
    }

    private void writeComponentBlocks(BufferedWriter out, List<ComponentBlock> blocks, String indent, ImportReport report)
            throws Exception {
        if (isSingleVerticalColumn(blocks)) {
            writeVerticalComponentBlocks(out, blocks, indent, report);
            return;
        }
        Map<Integer, Map<Integer, List<ComponentBlock>>> byLayer = new TreeMap<>();
        for (ComponentBlock block : blocks) {
            byLayer.computeIfAbsent(block.y, ignored -> new TreeMap<>())
                    .computeIfAbsent(block.z, ignored -> new ArrayList<>())
                    .add(block);
        }
        for (Map.Entry<Integer, Map<Integer, List<ComponentBlock>>> layer : byLayer.entrySet()) {
            out.write(indent + "slice y=" + layer.getKey() + " {\n");
            for (Map.Entry<Integer, List<ComponentBlock>> row : layer.getValue().entrySet()) {
                List<ComponentBlock> rowBlocks = row.getValue().stream().sorted().toList();
                int index = 0;
                while (index < rowBlocks.size()) {
                    ComponentBlock first = rowBlocks.get(index++);
                    int endX = first.x;
                    while (index < rowBlocks.size()
                            && rowBlocks.get(index).x == endX + 1
                            && rowBlocks.get(index).state.equals(first.state)) {
                        endX = rowBlocks.get(index++).x;
                    }
                    if (first.x == endX) {
                        out.write(indent + "  set " + first.x + " " + row.getKey() + " " + first.state + "\n");
                    } else {
                        out.write(indent + "  fill " + first.x + " " + row.getKey()
                                + " to " + endX + " " + row.getKey() + " " + first.state + "\n");
                    }
                    report.blockCommands++;
                }
            }
            out.write(indent + "}\n");
        }
    }

    private boolean isSingleVerticalColumn(List<ComponentBlock> blocks) {
        return blocks.stream().allMatch(block -> block.x == 0 && block.z == 0);
    }

    private void writeVerticalComponentBlocks(BufferedWriter out, List<ComponentBlock> blocks, String indent,
                                              ImportReport report) throws Exception {
        List<ComponentBlock> sorted = blocks.stream().sorted().toList();
        int index = 0;
        while (index < sorted.size()) {
            ComponentBlock first = sorted.get(index++);
            int endY = first.y;
            while (index < sorted.size()
                    && sorted.get(index).y == endY + 1
                    && sorted.get(index).state.equals(first.state)) {
                endY = sorted.get(index++).y;
            }
            if (first.y == endY) {
                out.write(indent + "set 0 " + first.y + " 0 " + first.state + "\n");
            } else {
                out.write(indent + "fill 0 " + first.y + " 0 to 0 " + endY + " 0 " + first.state + "\n");
            }
            report.blockCommands++;
        }
    }

    private int componentCommandCount(List<ComponentBlock> blocks) {
        if (isSingleVerticalColumn(blocks)) {
            int count = 0;
            List<ComponentBlock> sorted = blocks.stream().sorted().toList();
            int index = 0;
            while (index < sorted.size()) {
                ComponentBlock first = sorted.get(index++);
                int endY = first.y;
                while (index < sorted.size()
                        && sorted.get(index).y == endY + 1
                        && sorted.get(index).state.equals(first.state)) {
                    endY = sorted.get(index++).y;
                }
                count++;
            }
            return count;
        }
        int count = 0;
        Map<Integer, Map<Integer, List<ComponentBlock>>> byLayer = new TreeMap<>();
        for (ComponentBlock block : blocks) {
            byLayer.computeIfAbsent(block.y, ignored -> new TreeMap<>())
                    .computeIfAbsent(block.z, ignored -> new ArrayList<>())
                    .add(block);
        }
        for (Map<Integer, List<ComponentBlock>> rows : byLayer.values()) {
            for (List<ComponentBlock> row : rows.values()) {
                List<ComponentBlock> rowBlocks = row.stream().sorted().toList();
                int index = 0;
                while (index < rowBlocks.size()) {
                    ComponentBlock first = rowBlocks.get(index++);
                    int endX = first.x;
                    while (index < rowBlocks.size()
                            && rowBlocks.get(index).x == endX + 1
                            && rowBlocks.get(index).state.equals(first.state)) {
                        endX = rowBlocks.get(index++).x;
                    }
                    count++;
                }
            }
        }
        return count;
    }

    private void writeBody(BufferedWriter out, Path worldDir, WorldMetadata metadata,
                           List<ChunkCoordinate> chunks, String defaultBiome, ImportOptions options,
                           ImportReport report) throws Exception {
        int chunksSeen = 0;
        try (RegionCache cache = new RegionCache(worldDir, "region");
             RegionCache entityCache = new RegionCache(worldDir, "entities")) {
            for (ChunkCoordinate coordinate : chunks) {
                ChunkColumn chunk = loadChunk(cache.region(coordinate.region), coordinate.x, coordinate.z,
                        metadata.dataVersion);
                List<NBTCompound> externalEntities = externalEntities(entityCache, coordinate);
                if (!hasImportableContent(chunk) && externalEntities.isEmpty()) {
                    continue;
                }
                if (options.chunkLimit > 0 && chunksSeen >= options.chunkLimit) {
                    break;
                }
                chunksSeen++;
                writeChunk(out, chunk, externalEntities, defaultBiome, report);
            }
        }
    }

    private boolean hasImportableContent(ChunkColumn chunk) {
        if (chunk.getTileEntities().isNotEmpty() || chunk.getEntities().isNotEmpty()) {
            return true;
        }
        for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!AIR.equals(blockState(chunk.getBlockState(x, y, z)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private ChunkColumn loadChunk(RegionFile region, int chunkX, int chunkZ, int fallbackDataVersion) throws Exception {
        try {
            return region.getChunk(chunkX, chunkZ);
        } catch (Exception error) {
            if (error.getMessage() == null || !(error.getMessage().contains("DataVersion")
                    || error.getMessage().contains("Status")
                    || error.getMessage().contains("xPos")
                    || error.getMessage().contains("zPos"))) {
                throw error;
            }
            NBTCompound raw = region.getChunkData(chunkX, chunkZ);
            NBTCompound chunkData = raw.getCompound("Level") != null ? raw.getCompound("Level") : raw;
            Map<String, NBT> patched = new HashMap<>(chunkData.asMapView());
            patched.putIfAbsent("DataVersion", new NBTInt(fallbackDataVersion));
            patched.putIfAbsent("xPos", new NBTInt(chunkX));
            patched.putIfAbsent("zPos", new NBTInt(chunkZ));
            patched.putIfAbsent("Status", new NBTString("full"));
            try {
                return new ChunkColumn(new NBTCompound(patched));
            } catch (Exception patchedError) {
                throw new IllegalArgumentException("Unsupported chunk " + chunkX + "," + chunkZ
                        + ": the reference Anvil reader could not decode this chunk after metadata normalization. "
                        + "This usually means a legacy pre-flattening chunk layout; open and save the map in a "
                        + "modern Minecraft version or run it through a world upgrader before importing. Cause: "
                        + patchedError.getMessage(), patchedError);
            }
        }
    }

    private void writeChunk(BufferedWriter out, ChunkColumn chunk, List<NBTCompound> externalEntities,
                            String defaultBiome, ImportReport report) throws Exception {
        out.write("  # chunk " + chunk.getX() + "," + chunk.getZ() + "\n");
        for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
            if (writeFullLayer(out, chunk, y, report)) {
                continue;
            }
            writeRowRuns(out, chunk, y, report);
        }
        writeBiomes(out, chunk, defaultBiome, report);
        writeBlockEntities(out, chunk, report);
        writeEntities(out, chunk, report);
        writeExternalEntities(out, externalEntities, report);
        out.write("\n");
    }

    private boolean writeFullLayer(BufferedWriter out, ChunkColumn chunk, int y, ImportReport report)
            throws Exception {
        String first = blockState(chunk.getBlockState(0, y, 0));
        if (AIR.equals(first)) {
            return false;
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (!first.equals(blockState(chunk.getBlockState(x, y, z)))) {
                    return false;
                }
            }
        }
        int worldX = chunk.getX() * 16;
        int worldZ = chunk.getZ() * 16;
        out.write("  fill " + worldX + " " + y + " " + worldZ + " "
                + (worldX + 15) + " " + y + " " + (worldZ + 15) + " " + first + "\n");
        report.blockCommands++;
        return true;
    }

    private void writeRowRuns(BufferedWriter out, ChunkColumn chunk, int y, ImportReport report) throws Exception {
        int worldX = chunk.getX() * 16;
        int worldZ = chunk.getZ() * 16;
        for (int z = 0; z < 16; z++) {
            int x = 0;
            while (x < 16) {
                String state = blockState(chunk.getBlockState(x, y, z));
                if (AIR.equals(state)) {
                    x++;
                    continue;
                }
                int startX = x;
                x++;
                while (x < 16 && state.equals(blockState(chunk.getBlockState(x, y, z)))) {
                    x++;
                }
                int endX = x - 1;
                if (startX == endX) {
                    out.write("  set " + (worldX + startX) + " " + y + " " + (worldZ + z)
                            + " " + state + "\n");
                } else {
                    out.write("  fill " + (worldX + startX) + " " + y + " " + (worldZ + z)
                            + " " + (worldX + endX) + " " + y + " " + (worldZ + z)
                            + " " + state + "\n");
                }
                report.blockCommands++;
            }
        }
    }

    private void writeBiomes(BufferedWriter out, ChunkColumn chunk, String defaultBiome, ImportReport report)
            throws Exception {
        for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int x = 0; x < 16; x += 4) {
                    String biome = chunk.getBiome(x, y, z);
                    if (!defaultBiome.equals(biome)) {
                        out.write("  biome " + (chunk.getX() * 16 + x) + " " + y + " "
                                + (chunk.getZ() * 16 + z) + " " + biome + "\n");
                        report.biomeCommands++;
                    }
                }
            }
        }
    }

    private void writeBlockEntities(BufferedWriter out, ChunkColumn chunk, ImportReport report) throws Exception {
        for (NBTCompound blockEntity : chunk.getTileEntities()) {
            String id = blockEntity.getString("id");
            Integer x = blockEntity.getInt("x");
            Integer y = blockEntity.getInt("y");
            Integer z = blockEntity.getInt("z");
            if (id == null || x == null || y == null || z == null) {
                report.skippedNbt++;
                out.write("  # Skipped block entity with missing id/x/y/z\n");
                continue;
            }
            try {
                Map<String, NBT> payload = withoutKeys(blockEntity, BLOCK_ENTITY_STRUCTURAL_KEYS);
                out.write("  block_entity " + x + " " + y + " " + z + " " + id);
                writeCompoundPayload(out, payload, "  ");
                out.write("\n");
                report.blockEntities++;
            } catch (UnsupportedNbtException error) {
                report.skippedNbt++;
                out.write("  # Skipped block entity " + id + " at " + x + "," + y + "," + z
                        + ": " + error.getMessage() + "\n");
            }
        }
    }

    private void writeEntities(BufferedWriter out, ChunkColumn chunk, ImportReport report) throws Exception {
        for (NBTCompound entity : chunk.getEntities()) {
            writeEntity(out, entity, report);
        }
    }

    private void writeExternalEntities(BufferedWriter out, List<NBTCompound> entities, ImportReport report)
            throws Exception {
        for (NBTCompound entity : entities) {
            writeEntity(out, entity, report);
        }
    }

    private void writeEntity(BufferedWriter out, NBTCompound entity, ImportReport report) throws Exception {
        String id = entity.getString("id");
        NBTList<NBT> pos = entity.getList("Pos");
        if (id == null || pos == null || pos.getSize() < 3
                || !(pos.get(0) instanceof NBTDouble x)
                || !(pos.get(1) instanceof NBTDouble y)
                || !(pos.get(2) instanceof NBTDouble z)) {
            report.skippedNbt++;
            out.write("  # Skipped entity with missing id or Pos\n");
            return;
        }
        try {
            Map<String, NBT> payload = withoutKeys(entity, ENTITY_STRUCTURAL_KEYS);
            out.write("  entity " + doubleLiteral(x.getValue()) + " " + doubleLiteral(y.getValue()) + " "
                    + doubleLiteral(z.getValue()) + " " + id);
            writeCompoundPayload(out, payload, "  ");
            out.write("\n");
            report.entities++;
        } catch (UnsupportedNbtException error) {
            report.skippedNbt++;
            out.write("  # Skipped entity " + id + ": " + error.getMessage() + "\n");
        }
    }

    private List<NBTCompound> externalEntities(RegionCache entityCache, ChunkCoordinate coordinate) throws Exception {
        RegionFile region = entityCache.regionIfExists(coordinate.region);
        if (region == null || !region.hasChunk(coordinate.x, coordinate.z)) {
            return List.of();
        }
        NBTCompound data = region.getChunkData(coordinate.x, coordinate.z);
        NBTList<NBT> entities = data.getList("Entities");
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<NBTCompound> result = new ArrayList<>();
        for (NBT entity : entities) {
            if (entity instanceof NBTCompound compound) {
                result.add(compound);
            }
        }
        return result;
    }

    private Map<String, NBT> withoutKeys(NBTCompound compound, Set<String> skipped) {
        Map<String, NBT> payload = new TreeMap<>();
        for (Map.Entry<String, NBT> entry : compound.asMapView().entrySet()) {
            if (!skipped.contains(entry.getKey())) {
                payload.put(entry.getKey(), entry.getValue());
            }
        }
        return payload;
    }

    private void writeCompoundPayload(BufferedWriter out, Map<String, NBT> payload, String indent) throws Exception {
        if (payload.isEmpty()) {
            out.write(" {}");
            return;
        }
        out.write(" {\n");
        for (Map.Entry<String, NBT> entry : payload.entrySet()) {
            writeNamedTag(out, entry.getKey(), entry.getValue(), indent + "  ");
        }
        out.write(indent + "}");
    }

    private void writeNamedTag(BufferedWriter out, String name, NBT value, String indent) throws Exception {
        switch (value.getID().getOrdinal()) {
            case NbtWriter.TAG_BYTE -> out.write(indent + "byte " + quote(name) + " "
                    + ((NBTByte) value).getValue() + "\n");
            case NbtWriter.TAG_SHORT -> out.write(indent + "short " + quote(name) + " "
                    + ((NBTShort) value).getValue() + "\n");
            case NbtWriter.TAG_INT -> out.write(indent + "int " + quote(name) + " "
                    + ((NBTInt) value).getValue() + "\n");
            case NbtWriter.TAG_LONG -> out.write(indent + "long " + quote(name) + " "
                    + ((NBTLong) value).getValue() + "\n");
            case NbtWriter.TAG_FLOAT -> out.write(indent + "float " + quote(name) + " "
                    + floatLiteral(((NBTFloat) value).getValue()) + "\n");
            case NbtWriter.TAG_DOUBLE -> out.write(indent + "double " + quote(name) + " "
                    + doubleLiteral(((NBTDouble) value).getValue()) + "\n");
            case NbtWriter.TAG_BYTE_ARRAY -> writeByteArray(out, name, (NBTByteArray) value, indent);
            case NbtWriter.TAG_STRING -> out.write(indent + "string " + quote(name) + " "
                    + quote(((NBTString) value).getValue()) + "\n");
            case NbtWriter.TAG_LIST -> writeList(out, name, (NBTList<?>) value, indent);
            case NbtWriter.TAG_COMPOUND -> {
                out.write(indent + "compound " + quote(name));
                writeCompoundPayload(out, new TreeMap<>(((NBTCompound) value).asMapView()), indent);
                out.write("\n");
            }
            case NbtWriter.TAG_INT_ARRAY -> writeIntArray(out, name, (NBTIntArray) value, indent);
            case NbtWriter.TAG_LONG_ARRAY -> writeLongArray(out, name, (NBTLongArray) value, indent);
            default -> throw new UnsupportedNbtException("unsupported tag type " + value.getID().getReadableName());
        }
    }

    private void writeList(BufferedWriter out, String name, NBTList<?> list, String indent) throws Exception {
        int elementType = list.getSubtagType().getOrdinal();
        String typeName = nbtTypeName(elementType);
        out.write(indent + "list " + quote(name) + " " + typeName + " {");
        if (list.isEmpty()) {
            out.write(" }\n");
            return;
        }
        out.write("\n");
        for (NBT value : list) {
            writeListElement(out, value, elementType, indent + "  ");
        }
        out.write(indent + "}\n");
    }

    private void writeListElement(BufferedWriter out, NBT value, int elementType, String indent) throws Exception {
        if (value.getID().getOrdinal() != elementType) {
            throw new UnsupportedNbtException("mixed list tag types");
        }
        switch (elementType) {
            case NbtWriter.TAG_BYTE -> out.write(indent + ((NBTByte) value).getValue() + "\n");
            case NbtWriter.TAG_SHORT -> out.write(indent + ((NBTShort) value).getValue() + "\n");
            case NbtWriter.TAG_INT -> out.write(indent + ((NBTInt) value).getValue() + "\n");
            case NbtWriter.TAG_LONG -> out.write(indent + ((NBTLong) value).getValue() + "\n");
            case NbtWriter.TAG_FLOAT -> out.write(indent + floatLiteral(((NBTFloat) value).getValue()) + "\n");
            case NbtWriter.TAG_DOUBLE -> out.write(indent + doubleLiteral(((NBTDouble) value).getValue()) + "\n");
            case NbtWriter.TAG_STRING -> out.write(indent + quote(((NBTString) value).getValue()) + "\n");
            case NbtWriter.TAG_COMPOUND -> {
                out.write(indent + "{\n");
                for (Map.Entry<String, NBT> entry : new TreeMap<>(((NBTCompound) value).asMapView()).entrySet()) {
                    writeNamedTag(out, entry.getKey(), entry.getValue(), indent + "  ");
                }
                out.write(indent + "}\n");
            }
            default -> throw new UnsupportedNbtException("unsupported list element type " + nbtTypeName(elementType));
        }
    }

    private String nbtTypeName(int type) {
        return switch (type) {
            case NbtWriter.TAG_END -> "end";
            case NbtWriter.TAG_BYTE -> "byte";
            case NbtWriter.TAG_SHORT -> "short";
            case NbtWriter.TAG_INT -> "int";
            case NbtWriter.TAG_LONG -> "long";
            case NbtWriter.TAG_FLOAT -> "float";
            case NbtWriter.TAG_DOUBLE -> "double";
            case NbtWriter.TAG_STRING -> "string";
            case NbtWriter.TAG_COMPOUND -> "compound";
            case NbtWriter.TAG_BYTE_ARRAY -> "byte_array";
            case NbtWriter.TAG_INT_ARRAY -> "int_array";
            case NbtWriter.TAG_LONG_ARRAY -> "long_array";
            default -> "tag_" + type;
        };
    }

    private void writeByteArray(BufferedWriter out, String name, NBTByteArray value, String indent) throws Exception {
        out.write(indent + "byte_array " + quote(name) + " {");
        for (Byte item : value) {
            out.write(" " + item);
        }
        out.write(" }\n");
    }

    private void writeIntArray(BufferedWriter out, String name, NBTIntArray value, String indent) throws Exception {
        out.write(indent + "int_array " + quote(name) + " {");
        for (Integer item : value) {
            out.write(" " + item);
        }
        out.write(" }\n");
    }

    private void writeLongArray(BufferedWriter out, String name, NBTLongArray value, String indent) throws Exception {
        out.write(indent + "long_array " + quote(name) + " {");
        for (Long item : value) {
            out.write(" " + item);
        }
        out.write(" }\n");
    }

    private String blockState(org.jglrxavpok.hephaistos.mca.BlockState state) {
        if (state.getProperties().isEmpty()) {
            return state.getName();
        }
        StringBuilder text = new StringBuilder(state.getName()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(state.getProperties()).entrySet()) {
            if (!first) {
                text.append(',');
            }
            first = false;
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.append(']').toString();
    }

    private String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                result.append('\\');
            }
            result.append(c);
        }
        return result.append('"').toString();
    }

    private String floatLiteral(float value) {
        if (!Float.isFinite(value)) {
            throw new UnsupportedNbtException("non-finite float");
        }
        return String.format(Locale.ROOT, "%s", value);
    }

    private String doubleLiteral(double value) {
        if (!Double.isFinite(value)) {
            throw new UnsupportedNbtException("non-finite double");
        }
        return String.format(Locale.ROOT, "%s", value);
    }

    public enum ImportLayout {
        FLAT,
        REVIEWABLE
    }

    public record ImportOptions(int chunkLimit, ImportLayout layout) {
        public ImportOptions(int chunkLimit) {
            this(chunkLimit, ImportLayout.FLAT);
        }

        public static ImportOptions allChunks() {
            return new ImportOptions(0, ImportLayout.FLAT);
        }
    }

    public static final class ImportReport {
        private int chunkCount;
        private int blockCommands;
        private int biomeCommands;
        private int blockEntities;
        private int entities;
        private int skippedNbt;
        private int componentCandidates;
        private int promotedComponents;
        private int componentPlacements;
        private final int minY;
        private final int maxYExclusive;
        private final boolean truncated;
        private final List<String> unsupportedFolders;

        ImportReport(int chunkCount, int blockCommands, int biomeCommands, int blockEntities,
                     int entities, int skippedNbt, int minY, int maxYExclusive, boolean truncated,
                     List<String> unsupportedFolders) {
            this.chunkCount = chunkCount;
            this.blockCommands = blockCommands;
            this.biomeCommands = biomeCommands;
            this.blockEntities = blockEntities;
            this.entities = entities;
            this.skippedNbt = skippedNbt;
            this.minY = minY;
            this.maxYExclusive = maxYExclusive;
            this.truncated = truncated;
            this.unsupportedFolders = List.copyOf(unsupportedFolders);
        }

        public int chunkCount() {
            return chunkCount;
        }

        public int blockCommands() {
            return blockCommands;
        }

        public int biomeCommands() {
            return biomeCommands;
        }

        public int blockEntities() {
            return blockEntities;
        }

        public int entities() {
            return entities;
        }

        public int skippedNbt() {
            return skippedNbt;
        }

        public int componentCandidates() {
            return componentCandidates;
        }

        public int promotedComponents() {
            return promotedComponents;
        }

        public int componentPlacements() {
            return componentPlacements;
        }

        public int minY() {
            return minY;
        }

        public int maxYExclusive() {
            return maxYExclusive;
        }

        public boolean truncated() {
            return truncated;
        }

        public List<String> unsupportedFolders() {
            return unsupportedFolders;
        }
    }

    private static final class Scan {
        int chunkCount;
        int minY;
        int maxYExclusive;
        boolean regionsTruncated;
        String defaultBiome;
        final Map<String, Integer> biomeCounts = new HashMap<>();
        final Set<ChunkPos> importedChunks = new HashSet<>();
    }

    private record WorldMetadata(String name, int dataVersion, Integer spawnX, Integer spawnY, Integer spawnZ,
                                 Long time, Long dayTime, Integer gameType, Integer difficulty, Byte hardcore,
                                 Byte allowCommands, Byte raining, Integer rainTime, Byte thundering,
                                 Integer thunderTime, Integer clearWeatherTime,
                                 WorldSpec.WorldBorder worldBorder, Map<String, String> gameRules) {
    }

    private record RegionCoordinate(int x, int z) implements Comparable<RegionCoordinate> {
        @Override
        public int compareTo(RegionCoordinate other) {
            int zCompare = Integer.compare(z, other.z);
            return zCompare != 0 ? zCompare : Integer.compare(x, other.x);
        }
    }

    private record ChunkCoordinate(RegionCoordinate region, int x, int z) implements Comparable<ChunkCoordinate> {
        @Override
        public int compareTo(ChunkCoordinate other) {
            int zCompare = Integer.compare(z, other.z);
            return zCompare != 0 ? zCompare : Integer.compare(x, other.x);
        }
    }

    private record BlockCoordinate(int x, int y, int z) {
    }

    private record WorldBlockCoordinate(int x, int y, int z) {
    }

    private record ComponentBlock(int x, int y, int z, String state) implements Comparable<ComponentBlock> {
        @Override
        public int compareTo(ComponentBlock other) {
            int yCompare = Integer.compare(y, other.y);
            if (yCompare != 0) {
                return yCompare;
            }
            int zCompare = Integer.compare(z, other.z);
            if (zCompare != 0) {
                return zCompare;
            }
            return Integer.compare(x, other.x);
        }
    }

    private record ComponentCandidate(ChunkPos chunk, int localX, int localY, int localZ,
                                      List<ComponentBlock> blocks, String fingerprint) {
    }

    private record ExtractedComponent(String name, List<ComponentBlock> blocks) {
        int maxX() {
            return blocks.stream().mapToInt(ComponentBlock::x).max().orElse(0);
        }

        int maxY() {
            return blocks.stream().mapToInt(ComponentBlock::y).max().orElse(0);
        }

        int maxZ() {
            return blocks.stream().mapToInt(ComponentBlock::z).max().orElse(0);
        }
    }

    private record ComponentPlacement(ExtractedComponent component, ChunkPos chunk, int localX, int localY, int localZ)
            implements Comparable<ComponentPlacement> {
        @Override
        public int compareTo(ComponentPlacement other) {
            int chunkZCompare = Integer.compare(chunk.z(), other.chunk.z());
            if (chunkZCompare != 0) {
                return chunkZCompare;
            }
            int chunkXCompare = Integer.compare(chunk.x(), other.chunk.x());
            if (chunkXCompare != 0) {
                return chunkXCompare;
            }
            int yCompare = Integer.compare(localY, other.localY);
            if (yCompare != 0) {
                return yCompare;
            }
            int zCompare = Integer.compare(localZ, other.localZ);
            if (zCompare != 0) {
                return zCompare;
            }
            int xCompare = Integer.compare(localX, other.localX);
            if (xCompare != 0) {
                return xCompare;
            }
            return component.name.compareTo(other.component.name);
        }
    }

    private record ComponentExtraction(List<ExtractedComponent> components,
                                       Map<ChunkPos, List<ComponentPlacement>> placementsByChunk,
                                       List<ComponentPlacement> allPlacements,
                                       int candidatePlacements, int promotedPlacements) {
    }

    private static final class RegionCache implements AutoCloseable {
        private final Path worldDir;
        private final Map<RegionCoordinate, RegionFile> openRegions = new HashMap<>();

        private final String folderName;

        RegionCache(Path worldDir, String folderName) {
            this.worldDir = worldDir;
            this.folderName = folderName;
        }

        RegionFile region(RegionCoordinate coordinate) throws Exception {
            RegionFile region = regionIfExists(coordinate);
            if (region == null) {
                Path file = worldDir.resolve(folderName + "/r." + coordinate.x + "." + coordinate.z + ".mca");
                throw new IllegalArgumentException("Missing region file: " + file);
            }
            return region;
        }

        RegionFile regionIfExists(RegionCoordinate coordinate) throws Exception {
            RegionFile existing = openRegions.get(coordinate);
            if (existing != null) {
                return existing;
            }
            Path file = worldDir.resolve(folderName + "/r." + coordinate.x + "." + coordinate.z + ".mca");
            if (!Files.isRegularFile(file) || Files.size(file) == 0L) {
                return null;
            }
            RegionFile region = new RegionFile(new RandomAccessFile(file.toFile(), "rw"),
                    coordinate.x, coordinate.z);
            openRegions.put(coordinate, region);
            return region;
        }

        @Override
        public void close() throws Exception {
            Exception failure = null;
            for (RegionFile region : openRegions.values()) {
                try {
                    region.close();
                } catch (Exception error) {
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class UnsupportedNbtException extends RuntimeException {
        UnsupportedNbtException(String message) {
            super(message);
        }
    }
}
