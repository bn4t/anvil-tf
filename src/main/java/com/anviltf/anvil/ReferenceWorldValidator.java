package com.anviltf.anvil;

import com.anviltf.BlockState;
import com.anviltf.ChunkPos;
import com.anviltf.MutableWorld;
import com.anviltf.WorldSpec;
import org.jglrxavpok.hephaistos.mca.ChunkColumn;
import org.jglrxavpok.hephaistos.mca.RegionFile;
import org.jglrxavpok.hephaistos.nbt.CompressedProcesser;
import org.jglrxavpok.hephaistos.nbt.NBT;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTDouble;
import org.jglrxavpok.hephaistos.nbt.NBTIntArray;
import org.jglrxavpok.hephaistos.nbt.NBTList;
import org.jglrxavpok.hephaistos.nbt.NBTReader;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ReferenceWorldValidator {
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    public ValidationReport validate(Path worldDir) throws Exception {
        return validate(worldDir, null);
    }

    public ValidationReport validate(Path worldDir, WorldSpec expectedWorld) throws Exception {
        Path levelDat = worldDir.resolve("level.dat");
        if (!Files.isRegularFile(levelDat)) {
            throw new IllegalArgumentException("Missing level.dat: " + levelDat);
        }
        validateLevelDat(levelDat);

        Path regionDir = worldDir.resolve("region");
        if (!Files.isDirectory(regionDir)) {
            throw new IllegalArgumentException("Missing region directory: " + regionDir);
        }

        int regionCount = 0;
        MutableValidationReport report = new MutableValidationReport();
        try (Stream<Path> paths = Files.list(regionDir)) {
            for (Path regionFile : paths
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .sorted()
                    .toList()) {
                RegionCoordinate coordinate = parseRegionName(regionFile.getFileName().toString());
                regionCount++;
                validateRegion(regionFile, coordinate.x, coordinate.z, report, false);
            }
        }

        if (regionCount == 0) {
            throw new IllegalArgumentException("No Anvil region files found in " + regionDir);
        }
        if (report.chunkCount == 0) {
            throw new IllegalArgumentException("No chunks found in " + regionDir);
        }
        if (expectedWorld != null) {
            validateExpectedContent(worldDir, expectedWorld, report);
        }
        validateAuxiliaryRegions(worldDir, report);
        validateSaveDataNbt(worldDir, report);
        return new ValidationReport(regionCount, report.chunkCount, report.blockStateChecks,
                report.biomeChecks, report.blockEntityCount, report.entityCount,
                report.authoredBlockChecks, report.authoredBiomeChecks,
                report.dimensionRegionCount, report.dimensionChunkCount,
                report.entityRegionCount, report.entityRegionChunkCount,
                report.poiRegionCount, report.poiChunkCount,
                report.dataNbtFileCount);
    }

    private void validateLevelDat(Path levelDat) throws Exception {
        try (NBTReader reader = new NBTReader(levelDat, CompressedProcesser.GZIP)) {
            NBT root = reader.readNamed().getSecond();
            if (!(root instanceof NBTCompound compound)) {
                throw new IllegalArgumentException("level.dat root is not an NBT compound");
            }
            NBTCompound data = compound.getCompound("Data");
            if (data == null) {
                throw new IllegalArgumentException("level.dat is missing Data compound");
            }
            if (data.getString("LevelName") == null) {
                throw new IllegalArgumentException("level.dat is missing Data.LevelName");
            }
            if (data.getInt("DataVersion") == null) {
                throw new IllegalArgumentException("level.dat is missing Data.DataVersion");
            }
            Integer storageVersion = data.getInt("version");
            if (storageVersion == null) {
                throw new IllegalArgumentException("level.dat is missing Data.version");
            }
            if (storageVersion != 19132 && storageVersion != 19133) {
                throw new IllegalArgumentException("level.dat has unsupported Data.version: " + storageVersion);
            }
        }
    }

    private void validateRegion(Path regionFile, int regionX, int regionZ, MutableValidationReport report,
                                boolean auxiliary)
            throws Exception {
        try (RegionFile region = new RegionFile(new RandomAccessFile(regionFile.toFile(), "rw"), regionX, regionZ)) {
            for (int localZ = 0; localZ < 32; localZ++) {
                for (int localX = 0; localX < 32; localX++) {
                    int chunkX = regionX * 32 + localX;
                    int chunkZ = regionZ * 32 + localZ;
                    if (region.hasChunk(chunkX, chunkZ)) {
                        validateChunk(region.getChunk(chunkX, chunkZ), chunkX, chunkZ, report, auxiliary);
                    }
                }
            }
        }
    }

    private void validateChunk(ChunkColumn chunk, int expectedX, int expectedZ, MutableValidationReport report,
                               boolean auxiliary) {
        if (chunk.getX() != expectedX || chunk.getZ() != expectedZ) {
            throw new IllegalArgumentException("Chunk coordinate mismatch: expected "
                    + expectedX + "," + expectedZ + " but found " + chunk.getX() + "," + chunk.getZ());
        }
        if (auxiliary) {
            report.dimensionChunkCount++;
        } else {
            report.chunkCount++;
        }
        chunk.getMotionBlockingHeightMap();
        chunk.getWorldSurfaceHeightMap();
        validateBlockEntities(chunk, expectedX, expectedZ, report);
        validateEntities(chunk, expectedX, expectedZ, report);

        int minY = chunk.getMinY();
        int maxY = chunk.getMaxY();
        int[] sampleYs = sampleYs(minY, maxY);
        for (int y : sampleYs) {
            for (int z : new int[] {0, 15}) {
                for (int x : new int[] {0, 15}) {
                    chunk.getBlockState(x, y, z);
                    report.blockStateChecks++;
                }
            }
            chunk.getBiome(0, y, 0);
            chunk.getBiome(15, y, 15);
            report.biomeChecks += 2;
        }
    }

    private void validateAuxiliaryRegions(Path worldDir, MutableValidationReport report) throws Exception {
        validateDimensionRegionDirectory(worldDir.resolve("DIM-1/region"), report);
        validateDimensionRegionDirectory(worldDir.resolve("DIM1/region"), report);
        validateEntityRegionDirectory(worldDir.resolve("entities"), report);
        validateEntityRegionDirectory(worldDir.resolve("DIM-1/entities"), report);
        validateEntityRegionDirectory(worldDir.resolve("DIM1/entities"), report);
        validatePoiRegionDirectory(worldDir.resolve("poi"), report);
        validatePoiRegionDirectory(worldDir.resolve("DIM-1/poi"), report);
        validatePoiRegionDirectory(worldDir.resolve("DIM1/poi"), report);
    }

    private void validateSaveDataNbt(Path worldDir, MutableValidationReport report) throws Exception {
        validateDataDirectory(worldDir.resolve("data"), report);
        validateDataDirectory(worldDir.resolve("DIM-1/data"), report);
        validateDataDirectory(worldDir.resolve("DIM1/data"), report);
        validateDataDirectory(worldDir.resolve("playerdata"), report);
    }

    private void validateDataDirectory(Path dataDir, MutableValidationReport report) throws Exception {
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dataDir)) {
            for (Path dataFile : paths
                    .filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".dat"))
                    .sorted()
                    .toList()) {
                validateGzipNbtFile(dataFile);
                report.dataNbtFileCount++;
            }
        }
    }

    private void validateGzipNbtFile(Path dataFile) throws Exception {
        if (canReadNbtFile(dataFile, CompressedProcesser.GZIP) || canReadNbtFile(dataFile, null)) {
            return;
        }
        throw new IllegalArgumentException("Invalid NBT data file: " + dataFile);
    }

    private boolean canReadNbtFile(Path dataFile, CompressedProcesser<?, ?> compression) {
        try (NBTReader reader = compression == null
                ? new NBTReader(dataFile)
                : new NBTReader(dataFile, compression)) {
            NBT root = reader.readNamed().getSecond();
            return root instanceof NBTCompound;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void validateDimensionRegionDirectory(Path regionDir, MutableValidationReport report) throws Exception {
        for (Path regionFile : regionFiles(regionDir)) {
            RegionCoordinate coordinate = parseRegionName(regionFile.getFileName().toString());
            report.dimensionRegionCount++;
            validateRegion(regionFile, coordinate.x, coordinate.z, report, true);
        }
    }

    private void validateEntityRegionDirectory(Path entityDir, MutableValidationReport report) throws Exception {
        for (Path regionFile : regionFiles(entityDir)) {
            RegionCoordinate coordinate = parseRegionName(regionFile.getFileName().toString());
            report.entityRegionCount++;
            try (RegionFile region = new RegionFile(new RandomAccessFile(regionFile.toFile(), "rw"),
                    coordinate.x, coordinate.z)) {
                for (int localZ = 0; localZ < 32; localZ++) {
                    for (int localX = 0; localX < 32; localX++) {
                        int chunkX = coordinate.x * 32 + localX;
                        int chunkZ = coordinate.z * 32 + localZ;
                        if (region.hasChunk(chunkX, chunkZ)) {
                            validateEntityRegionChunk(region.getChunkData(chunkX, chunkZ), chunkX, chunkZ, report);
                        }
                    }
                }
            }
        }
    }

    private void validateEntityRegionChunk(NBTCompound chunkData, int expectedX, int expectedZ,
                                           MutableValidationReport report) {
        report.entityRegionChunkCount++;
        NBTIntArray position = chunkData.get("Position") instanceof NBTIntArray array ? array : null;
        if (position == null || position.getSize() < 2 || position.get(0) != expectedX || position.get(1) != expectedZ) {
            throw new IllegalArgumentException("Entity region chunk position mismatch for "
                    + expectedX + "," + expectedZ);
        }
        NBTList<NBT> entities = chunkData.getList("Entities");
        if (entities == null) {
            throw new IllegalArgumentException("Entity region chunk " + expectedX + "," + expectedZ
                    + " is missing Entities list");
        }
        for (NBT entity : entities) {
            if (!(entity instanceof NBTCompound compound)) {
                throw new IllegalArgumentException("Entity region chunk " + expectedX + "," + expectedZ
                        + " contains a non-compound entity tag");
            }
            report.entityCount++;
            validateEntity(compound, expectedX, expectedZ, null, null);
        }
    }

    private void validatePoiRegionDirectory(Path poiDir, MutableValidationReport report) throws Exception {
        for (Path regionFile : regionFiles(poiDir)) {
            RegionCoordinate coordinate = parseRegionName(regionFile.getFileName().toString());
            report.poiRegionCount++;
            try (RegionFile region = new RegionFile(new RandomAccessFile(regionFile.toFile(), "rw"),
                    coordinate.x, coordinate.z)) {
                for (int localZ = 0; localZ < 32; localZ++) {
                    for (int localX = 0; localX < 32; localX++) {
                        int chunkX = coordinate.x * 32 + localX;
                        int chunkZ = coordinate.z * 32 + localZ;
                        if (region.hasChunk(chunkX, chunkZ)) {
                            NBTCompound chunkData = region.getChunkData(chunkX, chunkZ);
                            if (chunkData.isEmpty()) {
                                throw new IllegalArgumentException("POI chunk " + chunkX + "," + chunkZ
                                        + " is empty");
                            }
                            report.poiChunkCount++;
                        }
                    }
                }
            }
        }
    }

    private Iterable<Path> regionFiles(Path regionDir) throws Exception {
        if (!Files.isDirectory(regionDir)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.list(regionDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0L;
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    })
                    .sorted()
                    .toList();
        }
    }

    private void validateExpectedContent(Path worldDir, WorldSpec expectedWorld, MutableValidationReport report)
            throws Exception {
        Map<RegionCoordinate, Set<ChunkPos>> chunksByRegion = new TreeMap<>();
        for (ChunkPos pos : expectedWorld.chunks().keySet()) {
            chunksByRegion.computeIfAbsent(regionCoordinate(pos), ignored -> new TreeSet<>()).add(pos);
        }
        for (ChunkPos pos : expectedWorld.biomes().keySet()) {
            chunksByRegion.computeIfAbsent(regionCoordinate(pos), ignored -> new TreeSet<>()).add(pos);
        }

        for (Map.Entry<RegionCoordinate, Set<ChunkPos>> entry : chunksByRegion.entrySet()) {
            RegionCoordinate regionCoordinate = entry.getKey();
            Path file = worldDir.resolve("region/r." + regionCoordinate.x + "." + regionCoordinate.z + ".mca");
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Missing expected region file: " + file);
            }
            try (RegionFile region = new RegionFile(new RandomAccessFile(file.toFile(), "rw"),
                    regionCoordinate.x, regionCoordinate.z)) {
                for (ChunkPos pos : entry.getValue()) {
                    if (!region.hasChunk(pos.x(), pos.z())) {
                        throw new IllegalArgumentException("Missing expected chunk " + pos.x() + "," + pos.z());
                    }
                    validateExpectedChunkContent(region.getChunk(pos.x(), pos.z()), pos, expectedWorld, report);
                }
            }
        }
    }

    private void validateExpectedChunkContent(ChunkColumn chunk, ChunkPos pos, WorldSpec expectedWorld,
                                              MutableValidationReport report) {
        MutableWorld.ChunkBlocks expectedBlocks = expectedWorld.chunks().get(pos);
        if (expectedBlocks != null) {
            expectedBlocks.forEachLayer((y, ignored) -> {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        assertBlock(chunk, pos, x, y, z, expectedBlocks.get(x, y, z));
                        report.authoredBlockChecks++;
                    }
                }
            });
            expectedBlocks.forEachExplicitBlock((x, y, z, block) -> {
                assertBlock(chunk, pos, x, y, z, block);
                report.authoredBlockChecks++;
            });
        }

        MutableWorld.ChunkBiomes expectedBiomes = expectedWorld.biomes().get(pos);
        if (expectedBiomes != null) {
            expectedBiomes.forEachExplicitBiome((x, y, z, biome) -> {
                String actual = chunk.getBiome(x * 4, y, z * 4);
                if (!biome.equals(actual)) {
                    throw new IllegalArgumentException("Biome mismatch at chunk " + pos.x() + "," + pos.z()
                            + " local " + x + "," + y + "," + z
                            + ": expected " + biome + " but found " + actual);
                }
                report.authoredBiomeChecks++;
            });
        }
    }

    private void assertBlock(ChunkColumn chunk, ChunkPos pos, int localX, int y, int localZ, BlockState expected) {
        org.jglrxavpok.hephaistos.mca.BlockState actual = chunk.getBlockState(localX, y, localZ);
        if (!expected.name().equals(actual.getName()) || !expected.properties().equals(actual.getProperties())) {
            throw new IllegalArgumentException("Block mismatch at chunk " + pos.x() + "," + pos.z()
                    + " local " + localX + "," + y + "," + localZ
                    + ": expected " + expected + " but found " + actual);
        }
    }

    private RegionCoordinate regionCoordinate(ChunkPos pos) {
        return new RegionCoordinate(Math.floorDiv(pos.x(), 32), Math.floorDiv(pos.z(), 32));
    }

    private void validateBlockEntities(ChunkColumn chunk, int expectedX, int expectedZ,
                                       MutableValidationReport report) {
        NBTList<NBTCompound> blockEntities = chunk.getTileEntities();
        report.blockEntityCount += blockEntities.getSize();
        for (NBTCompound blockEntity : blockEntities) {
            String id = blockEntity.getString("id");
            Integer x = blockEntity.getInt("x");
            Integer y = blockEntity.getInt("y");
            Integer z = blockEntity.getInt("z");
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Block entity in chunk " + expectedX + "," + expectedZ
                        + " is missing id");
            }
            if (x == null || y == null || z == null) {
                throw new IllegalArgumentException("Block entity " + id + " in chunk "
                        + expectedX + "," + expectedZ + " is missing x/y/z");
            }
            if (Math.floorDiv(x, 16) != expectedX || Math.floorDiv(z, 16) != expectedZ) {
                throw new IllegalArgumentException("Block entity " + id + " at "
                        + x + "," + y + "," + z + " is stored in wrong chunk "
                        + expectedX + "," + expectedZ);
            }
            if (y < chunk.getMinY() || y >= chunk.getMaxY()) {
                throw new IllegalArgumentException("Block entity " + id + " y " + y
                        + " outside chunk Y range [" + chunk.getMinY() + ", " + chunk.getMaxY() + ")");
            }
        }
    }

    private void validateEntities(ChunkColumn chunk, int expectedX, int expectedZ,
                                  MutableValidationReport report) {
        NBTList<NBTCompound> entities = chunk.getEntities();
        report.entityCount += entities.getSize();
        for (NBTCompound entity : entities) {
            validateEntity(entity, expectedX, expectedZ, chunk.getMinY(), chunk.getMaxY());
        }
    }

    private void validateEntity(NBTCompound entity, int expectedX, int expectedZ, Integer minY, Integer maxY) {
        String id = entity.getString("id");
        NBTList<NBT> pos = entity.getList("Pos");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Entity in chunk " + expectedX + "," + expectedZ
                    + " is missing id");
        }
        if (pos == null || pos.getSize() < 3) {
            throw new IllegalArgumentException("Entity " + id + " in chunk "
                    + expectedX + "," + expectedZ + " is missing Pos");
        }
        double x = coordinate(pos, 0, id);
        double y = coordinate(pos, 1, id);
        double z = coordinate(pos, 2, id);
        if (Math.floorDiv((int) Math.floor(x), 16) != expectedX
                || Math.floorDiv((int) Math.floor(z), 16) != expectedZ) {
            throw new IllegalArgumentException("Entity " + id + " at "
                    + x + "," + y + "," + z + " is stored in wrong chunk "
                    + expectedX + "," + expectedZ);
        }
        if (minY != null && maxY != null && (y < minY || y >= maxY)) {
            throw new IllegalArgumentException("Entity " + id + " y " + y
                    + " outside chunk Y range [" + minY + ", " + maxY + ")");
        }
    }

    private double coordinate(NBTList<NBT> pos, int index, String entityId) {
        NBT value = pos.get(index);
        if (!(value instanceof NBTDouble coordinate) || !Double.isFinite(coordinate.getValue())) {
            throw new IllegalArgumentException("Entity " + entityId + " has invalid Pos coordinate at index " + index);
        }
        return coordinate.getValue();
    }

    private int[] sampleYs(int minY, int maxY) {
        int topY = maxY - 1;
        if (minY <= 0 && topY >= 0 && minY != 0 && topY != 0) {
            return new int[] {minY, 0, topY};
        }
        if (minY == topY) {
            return new int[] {minY};
        }
        return new int[] {minY, topY};
    }

    private RegionCoordinate parseRegionName(String name) {
        Matcher matcher = REGION_NAME.matcher(name);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Anvil region filename: " + name);
        }
        return new RegionCoordinate(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    public record ValidationReport(int regionCount, int chunkCount, int blockStateChecks,
                                   int biomeChecks, int blockEntityCount, int entityCount,
                                   int authoredBlockChecks, int authoredBiomeChecks,
                                   int dimensionRegionCount, int dimensionChunkCount,
                                   int entityRegionCount, int entityRegionChunkCount,
                                   int poiRegionCount, int poiChunkCount,
                                   int dataNbtFileCount) {
    }

    private record RegionCoordinate(int x, int z) implements Comparable<RegionCoordinate> {
        @Override
        public int compareTo(RegionCoordinate other) {
            int zCompare = Integer.compare(z, other.z);
            return zCompare != 0 ? zCompare : Integer.compare(x, other.x);
        }
    }

    private static final class MutableValidationReport {
        int chunkCount;
        int blockStateChecks;
        int biomeChecks;
        int blockEntityCount;
        int entityCount;
        int authoredBlockChecks;
        int authoredBiomeChecks;
        int dimensionRegionCount;
        int dimensionChunkCount;
        int entityRegionCount;
        int entityRegionChunkCount;
        int poiRegionCount;
        int poiChunkCount;
        int dataNbtFileCount;
    }
}
