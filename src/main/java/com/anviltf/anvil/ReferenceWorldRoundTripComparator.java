package com.anviltf.anvil;

import com.anviltf.ChunkPos;
import org.jglrxavpok.hephaistos.mca.ChunkColumn;
import org.jglrxavpok.hephaistos.mca.RegionFile;
import org.jglrxavpok.hephaistos.nbt.NBT;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTDouble;
import org.jglrxavpok.hephaistos.nbt.NBTList;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ReferenceWorldRoundTripComparator {
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    public ComparisonReport compare(Path sourceWorld, Path regeneratedWorld) throws Exception {
        Set<ChunkPos> chunks = chunkPositions(regeneratedWorld.resolve("region"));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No regenerated chunks found in " + regeneratedWorld.resolve("region"));
        }

        long blockChecks = 0L;
        long biomeChecks = 0L;
        int blockEntityChecks = 0;
        int entityChecks = 0;
        try (RegionCache source = new RegionCache(sourceWorld);
             RegionCache regenerated = new RegionCache(regeneratedWorld);
             EntityRegionCache sourceEntities = new EntityRegionCache(sourceWorld);
             EntityRegionCache regeneratedEntities = new EntityRegionCache(regeneratedWorld)) {
            for (ChunkPos pos : chunks.stream().sorted(Comparator.naturalOrder()).toList()) {
                ChunkColumn sourceChunk = source.chunk(pos);
                ChunkColumn regeneratedChunk = regenerated.chunk(pos);
                compareChunkEnvelope(pos, sourceChunk, regeneratedChunk);
                blockChecks += compareBlocks(pos, sourceChunk, regeneratedChunk);
                biomeChecks += compareBiomes(pos, sourceChunk, regeneratedChunk);
                blockEntityChecks += compareBlockEntities(pos, sourceChunk, regeneratedChunk);
                entityChecks += compareEntityAnchors(pos, sourceChunk, sourceEntities, regeneratedChunk,
                        regeneratedEntities);
            }
        }
        return new ComparisonReport(chunks.size(), blockChecks, biomeChecks, blockEntityChecks, entityChecks);
    }

    private Set<ChunkPos> chunkPositions(Path regionDir) throws Exception {
        if (!Files.isDirectory(regionDir)) {
            throw new IllegalArgumentException("Missing region directory: " + regionDir);
        }
        Set<ChunkPos> chunks = new TreeSet<>();
        try (Stream<Path> paths = Files.list(regionDir)) {
            for (Path regionFile : paths
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .sorted()
                    .toList()) {
                RegionCoordinate coordinate = parseRegionName(regionFile.getFileName().toString())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid region filename: "
                                + regionFile.getFileName()));
                try (RegionFile region = new RegionFile(new RandomAccessFile(regionFile.toFile(), "rw"),
                        coordinate.x, coordinate.z)) {
                    for (int localZ = 0; localZ < 32; localZ++) {
                        for (int localX = 0; localX < 32; localX++) {
                            int chunkX = coordinate.x * 32 + localX;
                            int chunkZ = coordinate.z * 32 + localZ;
                            if (region.hasChunk(chunkX, chunkZ)) {
                                chunks.add(new ChunkPos(chunkX, chunkZ));
                            }
                        }
                    }
                }
            }
        }
        return chunks;
    }

    private void compareChunkEnvelope(ChunkPos pos, ChunkColumn source, ChunkColumn regenerated) {
        if (source.getX() != regenerated.getX() || source.getZ() != regenerated.getZ()
                || source.getX() != pos.x() || source.getZ() != pos.z()) {
            throw mismatch(pos, "chunk coordinate mismatch");
        }
        if (source.getMinY() != regenerated.getMinY() || source.getMaxY() != regenerated.getMaxY()) {
            throw mismatch(pos, "Y range mismatch: source [" + source.getMinY() + ", " + source.getMaxY()
                    + "] regenerated [" + regenerated.getMinY() + ", " + regenerated.getMaxY() + "]");
        }
    }

    private long compareBlocks(ChunkPos pos, ChunkColumn source, ChunkColumn regenerated) {
        long checks = 0L;
        for (int y = source.getMinY(); y <= source.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    org.jglrxavpok.hephaistos.mca.BlockState expected = source.getBlockState(x, y, z);
                    org.jglrxavpok.hephaistos.mca.BlockState actual = regenerated.getBlockState(x, y, z);
                    if (!expected.getName().equals(actual.getName())
                            || !expected.getProperties().equals(actual.getProperties())) {
                        throw mismatch(pos, "block mismatch at local " + x + "," + y + "," + z
                                + ": source " + expected + " regenerated " + actual);
                    }
                    checks++;
                }
            }
        }
        return checks;
    }

    private long compareBiomes(ChunkPos pos, ChunkColumn source, ChunkColumn regenerated) {
        long checks = 0L;
        for (int y = source.getMinY(); y <= source.getMaxY(); y += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int x = 0; x < 16; x += 4) {
                    String expected = source.getBiome(x, y, z);
                    String actual = regenerated.getBiome(x, y, z);
                    if (!expected.equals(actual)) {
                        throw mismatch(pos, "biome mismatch at local " + x + "," + y + "," + z
                                + ": source " + expected + " regenerated " + actual);
                    }
                    checks++;
                }
            }
        }
        return checks;
    }

    private int compareBlockEntities(ChunkPos pos, ChunkColumn source, ChunkColumn regenerated) {
        Set<String> expected = blockEntityAnchors(source);
        Set<String> actual = blockEntityAnchors(regenerated);
        if (!expected.equals(actual)) {
            throw mismatch(pos, "block entity anchor mismatch: source " + expected + " regenerated " + actual);
        }
        return actual.size();
    }

    private Set<String> blockEntityAnchors(ChunkColumn chunk) {
        Set<String> anchors = new HashSet<>();
        for (NBTCompound entity : chunk.getTileEntities()) {
            anchors.add(entity.getString("id") + "@"
                    + entity.getInt("x") + "," + entity.getInt("y") + "," + entity.getInt("z"));
        }
        return anchors;
    }

    private int compareEntityAnchors(ChunkPos pos, ChunkColumn source, EntityRegionCache sourceEntities,
                                     ChunkColumn regenerated, EntityRegionCache regeneratedEntities)
            throws Exception {
        Set<EntityAnchor> expected = entityAnchors(source, sourceEntities, pos);
        Set<EntityAnchor> actual = entityAnchors(regenerated, regeneratedEntities, pos);
        if (!expected.equals(actual)) {
            throw mismatch(pos, "entity anchor mismatch: source " + expected + " regenerated " + actual);
        }
        return actual.size();
    }

    private Set<EntityAnchor> entityAnchors(ChunkColumn chunk, EntityRegionCache externalEntities, ChunkPos pos)
            throws Exception {
        Set<EntityAnchor> anchors = new HashSet<>();
        for (NBTCompound entity : chunk.getEntities()) {
            addEntityAnchor(anchors, entity);
        }
        for (NBTCompound entity : externalEntities.entities(pos)) {
            addEntityAnchor(anchors, entity);
        }
        return anchors;
    }

    private void addEntityAnchor(Set<EntityAnchor> anchors, NBTCompound entity) {
        String id = entity.getString("id");
        NBTList<NBT> pos = entity.getList("Pos");
        if (id != null && pos != null && pos.getSize() >= 3
                && pos.get(0) instanceof NBTDouble x
                && pos.get(1) instanceof NBTDouble y
                && pos.get(2) instanceof NBTDouble z) {
            anchors.add(new EntityAnchor(id, roundedCoordinate(x.getValue()),
                    roundedCoordinate(y.getValue()), roundedCoordinate(z.getValue())));
        }
    }

    private long roundedCoordinate(double value) {
        return Math.round(value * 1_000_000_000L);
    }

    private Optional<RegionCoordinate> parseRegionName(String name) {
        Matcher matcher = REGION_NAME.matcher(name);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new RegionCoordinate(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))));
    }

    private IllegalArgumentException mismatch(ChunkPos pos, String detail) {
        return new IllegalArgumentException("Round-trip mismatch in chunk " + pos.x() + "," + pos.z()
                + ": " + detail);
    }

    public record ComparisonReport(int chunkCount, long blockChecks, long biomeChecks,
                                   int blockEntityChecks, int entityChecks) {
    }

    private record RegionCoordinate(int x, int z) {
    }

    private record EntityAnchor(String id, long x, long y, long z) {
    }

    private static final class RegionCache implements AutoCloseable {
        private final Path worldDir;
        private final Map<RegionCoordinate, RegionFile> regions = new HashMap<>();

        RegionCache(Path worldDir) {
            this.worldDir = worldDir;
        }

        ChunkColumn chunk(ChunkPos pos) throws Exception {
            RegionCoordinate coordinate = new RegionCoordinate(Math.floorDiv(pos.x(), 32),
                    Math.floorDiv(pos.z(), 32));
            RegionFile region = regions.get(coordinate);
            if (region == null) {
                Path file = worldDir.resolve("region/r." + coordinate.x + "." + coordinate.z + ".mca");
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Missing source region file: " + file);
                }
                region = new RegionFile(new RandomAccessFile(file.toFile(), "rw"), coordinate.x, coordinate.z);
                regions.put(coordinate, region);
            }
            if (!region.hasChunk(pos.x(), pos.z())) {
                throw new IllegalArgumentException("Missing source chunk " + pos.x() + "," + pos.z()
                        + " in " + worldDir);
            }
            return region.getChunk(pos.x(), pos.z());
        }

        @Override
        public void close() throws Exception {
            Exception failure = null;
            for (RegionFile region : regions.values()) {
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

    private static final class EntityRegionCache implements AutoCloseable {
        private final Path worldDir;
        private final Map<RegionCoordinate, RegionFile> regions = new HashMap<>();

        EntityRegionCache(Path worldDir) {
            this.worldDir = worldDir;
        }

        List<NBTCompound> entities(ChunkPos pos) throws Exception {
            RegionCoordinate coordinate = new RegionCoordinate(Math.floorDiv(pos.x(), 32),
                    Math.floorDiv(pos.z(), 32));
            RegionFile region = regionIfExists(coordinate);
            if (region == null || !region.hasChunk(pos.x(), pos.z())) {
                return List.of();
            }
            NBTCompound chunkData = region.getChunkData(pos.x(), pos.z());
            NBTList<NBT> entities = chunkData.getList("Entities");
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

        private RegionFile regionIfExists(RegionCoordinate coordinate) throws Exception {
            RegionFile region = regions.get(coordinate);
            if (region != null) {
                return region;
            }
            Path file = worldDir.resolve("entities/r." + coordinate.x + "." + coordinate.z + ".mca");
            if (!Files.isRegularFile(file) || Files.size(file) == 0L) {
                return null;
            }
            region = new RegionFile(new RandomAccessFile(file.toFile(), "rw"), coordinate.x, coordinate.z);
            regions.put(coordinate, region);
            return region;
        }

        @Override
        public void close() throws Exception {
            Exception failure = null;
            for (RegionFile region : regions.values()) {
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
}
