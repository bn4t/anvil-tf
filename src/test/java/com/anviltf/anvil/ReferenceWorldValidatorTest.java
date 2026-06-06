package com.anviltf.anvil;

import com.anviltf.WorldSpec;
import com.anviltf.dsl.DslParser;
import org.jglrxavpok.hephaistos.mca.RegionFile;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTDouble;
import org.jglrxavpok.hephaistos.nbt.NBTInt;
import org.jglrxavpok.hephaistos.nbt.NBTIntArray;
import org.jglrxavpok.hephaistos.nbt.NBTList;
import org.jglrxavpok.hephaistos.nbt.NBTString;
import org.jglrxavpok.hephaistos.nbt.NBTType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReferenceWorldValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void validatesGeneratedWorldWithReferenceLibrary() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Runtime Validation" {
                  min_y 0
                  height 64
                  fill 0 8 0 31 8 31 minecraft:stone
                  hollow_cylinder 8 9 8 3 14 minecraft:bricks minecraft:air
                  entity 8.5 10 8.5 minecraft:armor_stand {
                    byte Marker 1
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(tempDir);

        assertEquals(1, report.regionCount());
        assertEquals(4, report.chunkCount());
        assertEquals(true, report.blockStateChecks() > 0);
        assertEquals(true, report.biomeChecks() > 0);
        assertEquals(1, report.entityCount());
    }

    @Test
    void validatesAuthoredBlocksAndBiomesAgainstReferenceLibrary() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Expected Content Validation" {
                  min_y 0
                  height 64
                  fill 0 8 0 15 8 15 minecraft:stone
                  set 1 8 1 minecraft:gold_block
                  set 2 8 2 minecraft:air
                  fill_biome 0 8 0 15 8 15 minecraft:forest
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(tempDir, spec);

        assertEquals(258, report.authoredBlockChecks());
        assertEquals(16, report.authoredBiomeChecks());
    }

    @Test
    void rejectsGeneratedWorldWhenAuthoredContentDoesNotMatchReferenceReadback() throws Exception {
        WorldSpec written = new DslParser().parse("""
                world "Written Content" {
                  min_y 0
                  height 64
                  set 1 8 1 minecraft:stone
                }
                """);
        WorldSpec expected = new DslParser().parse("""
                world "Expected Content" {
                  min_y 0
                  height 64
                  set 1 8 1 minecraft:diamond_block
                }
                """);
        new AnvilWorldWriter().write(written, tempDir);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ReferenceWorldValidator().validate(tempDir, expected));

        assertEquals(true, error.getMessage().contains("Block mismatch"));
    }

    @Test
    void validatesBlockEntityOnlyChunksWithReferenceLibrary() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Block Entity Runtime Validation" {
                  min_y 0
                  height 64
                  block_entity -17 8 32 minecraft:chest {
                    item Items slot 0 minecraft:bread count 2
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(tempDir);

        assertEquals(1, report.regionCount());
        assertEquals(1, report.chunkCount());
        assertEquals(1, report.blockEntityCount());
        assertEquals(true, report.blockStateChecks() > 0);
        assertEquals(true, report.biomeChecks() > 0);
    }

    @Test
    void validatesEntityStructuralOverridesAfterWriterNormalizesThem() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Entity Runtime Validation" {
                  min_y 0
                  height 64
                  entity 1.5 8.0 1.5 minecraft:armor_stand {
                    string id "minecraft:zombie"
                    list Pos double { 48.5 8.0 48.5 }
                    int_array UUID { 1 2 3 4 }
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(tempDir);

        assertEquals(1, report.entityCount());
    }

    @Test
    void validatesAuxiliaryDimensionEntityAndPoiRegions() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Auxiliary Validation" {
                  min_y 0
                  height 64
                  set 0 8 0 minecraft:stone
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);
        Files.createDirectories(tempDir.resolve("DIM-1/region"));
        Files.copy(tempDir.resolve("region/r.0.0.mca"), tempDir.resolve("DIM-1/region/r.0.0.mca"));
        writeEntityRegion(tempDir.resolve("entities/r.0.0.mca"));
        writePoiRegion(tempDir.resolve("poi/r.0.0.mca"));
        writeDataFile(tempDir.resolve("data/scoreboard.dat"));
        writeDataFile(tempDir.resolve("DIM1/data/raids_end.dat"));

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(tempDir);

        assertEquals(1, report.dimensionRegionCount());
        assertEquals(1, report.dimensionChunkCount());
        assertEquals(1, report.entityRegionCount());
        assertEquals(1, report.entityRegionChunkCount());
        assertEquals(1, report.poiRegionCount());
        assertEquals(1, report.poiChunkCount());
        assertEquals(1, report.entityCount());
        assertEquals(3, report.dataNbtFileCount());
    }

    @Test
    void rejectsInvalidSaveDataNbtFiles() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Invalid Data Validation" {
                  min_y 0
                  height 64
                  set 0 8 0 minecraft:stone
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);
        Files.createDirectories(tempDir.resolve("data"));
        Files.writeString(tempDir.resolve("data/broken.dat"), "not gzip nbt");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ReferenceWorldValidator().validate(tempDir));

        assertEquals(true, error.getMessage().contains("Invalid NBT data file"));
    }

    private void writeEntityRegion(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        NBTCompound entity = new NBTCompound(Map.of(
                "id", new NBTString("minecraft:zombie"),
                "Pos", new NBTList<>(NBTType.TAG_Double, List.of(
                        new NBTDouble(6.5d),
                        new NBTDouble(10.0d),
                        new NBTDouble(6.5d)))));
        NBTCompound data = new NBTCompound(Map.of(
                "Position", new NBTIntArray(0, 0),
                "DataVersion", new NBTInt(3465),
                "Entities", new NBTList<>(NBTType.TAG_Compound, List.of(entity))));
        try (RegionFile region = new RegionFile(new RandomAccessFile(file.toFile(), "rw"), 0, 0)) {
            region.writeColumnData(data, 0, 0);
        }
    }

    private void writePoiRegion(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        NBTCompound data = new NBTCompound(Map.of("DataVersion", new NBTInt(3465)));
        try (RegionFile region = new RegionFile(new RandomAccessFile(file.toFile(), "rw"), 0, 0)) {
            region.writeColumnData(data, 0, 0);
        }
    }

    private void writeDataFile(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(file));
             NbtWriter writer = new NbtWriter(gzip)) {
            writer.namedRoot("", () -> {
                writer.intTag("DataVersion", 3465);
                writer.compound("data", () -> writer.stringTag("name", "validation"));
            });
        }
    }

    @Test
    void rejectsFoldersWithoutLevelDat() throws Exception {
        Files.createDirectories(tempDir.resolve("region"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ReferenceWorldValidator().validate(tempDir));

        assertEquals(true, error.getMessage().contains("Missing level.dat"));
    }
}
