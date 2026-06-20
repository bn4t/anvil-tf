package com.anviltf.anvil;

import com.anviltf.WorldSpec;
import com.anviltf.dsl.DslParser;
import org.jglrxavpok.hephaistos.mca.RegionFile;
import org.jglrxavpok.hephaistos.nbt.NBT;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AtfWorldImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void importsGeneratedWorldToParseableAtfAndRegeneratesValidAnvil() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Import Source" {
                  version 3465
                  min_y 0
                  height 64
                  spawn 1 8 1
                  weather {
                    raining true
                    rain_time 1200
                    thundering true
                    thunder_time 600
                    clear_weather_time 0
                  }
                  world_border {
                    center 12.5 -7.25
                    size 5000.0
                    size_lerp_target 8000.0
                    size_lerp_time 40
                    warning_blocks 9
                    warning_time 20
                    damage_buffer 6.5
                    damage_per_block 0.4
                  }
                  default_biome minecraft:forest
                  fill 0 8 0 15 8 15 minecraft:stone
                  set 2 9 2 minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                  biome 0 8 0 minecraft:flower_forest
                  set 4 9 4 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 4 9 4 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Imported\\"}"
                    item Items slot 0 minecraft:bread count 3
                  }
                  entity 5.5 10.0 5.5 minecraft:armor_stand {
                    byte Marker 1
                  }
                }
                """);
        Path sourceWorld = tempDir.resolve("source-world");
        new AnvilWorldWriter().write(source, sourceWorld);
        writeExternalEntity(sourceWorld);
        Files.createDirectories(sourceWorld.resolve("serverconfig"));
        Files.writeString(sourceWorld.resolve("serverconfig/example.toml"), "enabled=true");
        Files.write(sourceWorld.resolve("icon.png"), new byte[] {1, 2, 3, 4});

        Path importedAtf = tempDir.resolve("imported.atf");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedAtf,
                AtfWorldImporter.ImportOptions.allChunks());
        WorldSpec imported = new DslParser().parse(importedAtf);
        Path regeneratedWorld = tempDir.resolve("regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        ReferenceWorldValidator.ValidationReport validation =
                new ReferenceWorldValidator().validate(regeneratedWorld, imported);
        ReferenceWorldRoundTripComparator.ComparisonReport comparison =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);

        assertEquals(1, report.chunkCount());
        assertEquals(1, report.blockEntities());
        assertEquals(2, report.entities());
        assertEquals(0, report.skippedNbt());
        assertEquals(true, report.unsupportedFolders().contains("serverconfig"));
        assertEquals(true, report.unsupportedFolders().contains("icon.png"));
        assertEquals(true, Files.readString(importedAtf).contains("block_entity 4 9 4 minecraft:chest"));
        assertEquals(true, Files.readString(importedAtf).contains("entity 6.5 10.0 6.5 minecraft:zombie"));
        assertEquals(true, Files.readString(importedAtf).contains("weather {"));
        assertEquals(true, Files.readString(importedAtf).contains("world_border {"));
        assertEquals("enabled=true", Files.readString(regeneratedWorld.resolve("serverconfig/example.toml")));
        assertEquals(4, Files.size(regeneratedWorld.resolve("icon.png")));
        assertEquals(1, validation.blockEntityCount());
        assertEquals(2, validation.entityCount());
        assertEquals(true, validation.authoredBlockChecks() > 0);
        assertEquals(1, comparison.chunkCount());
        assertEquals(true, comparison.blockChecks() > 0);
        assertEquals(true, comparison.biomeChecks() > 0);
    }

    private void writeExternalEntity(Path worldDir) throws Exception {
        Files.createDirectories(worldDir.resolve("entities"));
        NBTCompound entity = new NBTCompound(Map.of(
                "id", new NBTString("minecraft:zombie"),
                "Pos", new NBTList<>(NBTType.TAG_Double, List.of(
                        new NBTDouble(6.5d),
                        new NBTDouble(10.0d),
                        new NBTDouble(6.5d))),
                "CustomName", new NBTString("{\"text\":\"External\"}")));
        NBTCompound data = new NBTCompound(Map.of(
                "Position", new NBTIntArray(0, 0),
                "DataVersion", new NBTInt(3465),
                "Entities", new NBTList<>(NBTType.TAG_Compound, List.of(entity))));
        try (RegionFile region = new RegionFile(new RandomAccessFile(
                worldDir.resolve("entities/r.0.0.mca").toFile(), "rw"), 0, 0)) {
            region.writeColumnData(data, 0, 0);
        }
    }

    @Test
    void chunkLimitProducesParseablePartialImport() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Import Limit Source" {
                  min_y 0
                  height 64
                  set 0 8 0 minecraft:stone
                  set 32 8 0 minecraft:gold_block
                }
                """);
        Path sourceWorld = tempDir.resolve("limited-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);

        Path importedAtf = tempDir.resolve("limited.atf");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedAtf,
                new AtfWorldImporter.ImportOptions(1));
        WorldSpec imported = new DslParser().parse(importedAtf);

        assertEquals(1, report.chunkCount());
        assertEquals(true, report.truncated());
        assertEquals(1, imported.chunks().size());
    }

    @Test
    void reviewableImportWritesChunkIncludesAndRoundTrips() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Reviewable Source" {
                  version 3465
                  min_y 0
                  height 64
                  default_biome minecraft:forest
                  fill 0 0 0 15 15 15 minecraft:stone
                  fill 0 16 0 15 16 15 minecraft:glass
                  set 2 17 2 minecraft:gold_block
                  fill_biome 0 0 0 15 15 15 minecraft:flower_forest
                  block_entity 2 17 2 minecraft:chest {
                    item Items slot 0 minecraft:bread count 3
                  }
                }
                """);
        Path sourceWorld = tempDir.resolve("reviewable-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);

        Path importedDir = tempDir.resolve("reviewable-import");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedDir,
                new AtfWorldImporter.ImportOptions(0, AtfWorldImporter.ImportLayout.REVIEWABLE));
        Path worldAtf = importedDir.resolve("world.atf");
        WorldSpec imported = new DslParser().parse(worldAtf);
        Path regeneratedWorld = tempDir.resolve("reviewable-regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        ReferenceWorldRoundTripComparator.ComparisonReport comparison =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);

        assertEquals(1, report.chunkCount());
        assertEquals(true, Files.readString(worldAtf).contains("include \"groups/imported-0-0.atf\""));
        String group = Files.readString(importedDir.resolve("groups/imported-0-0.atf"));
        assertEquals(true, group.contains("group \"imported 0,0\""));
        assertEquals(true, group.contains("origin 0 0 0"));
        assertEquals(true, group.contains("fill 0 0 0 to 15 15 15 minecraft:stone"));
        assertEquals(true, group.contains("set 2 17 2 minecraft:gold_block"));
        assertEquals(true, group.contains("fill_biome 0 0 0 12 12 12 minecraft:flower_forest"));
        assertEquals(1, report.biomeCommands());
        assertEquals(1, comparison.chunkCount());
        assertEquals(16384, comparison.blockChecks());
        assertEquals(true, comparison.biomeChecks() > 0);
    }

    @Test
    void reviewableImportCompressesPartialBlockCuboids() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Partial Cuboid Compression" {
                  version 3465
                  min_y 0
                  height 32
                  fill 3 2 4 to 10 6 9 minecraft:granite
                  set 12 7 12 minecraft:gold_block
                }
                """);
        Path sourceWorld = tempDir.resolve("partial-cuboid-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);

        Path importedDir = tempDir.resolve("partial-cuboid-import");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedDir,
                new AtfWorldImporter.ImportOptions(0, AtfWorldImporter.ImportLayout.REVIEWABLE));
        WorldSpec imported = new DslParser().parse(importedDir.resolve("world.atf"));
        Path regeneratedWorld = tempDir.resolve("partial-cuboid-regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        ReferenceWorldRoundTripComparator.ComparisonReport comparison =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);

        String group = Files.readString(importedDir.resolve("groups/imported-0-0.atf"));
        assertEquals(true, group.contains("fill 3 2 4 to 10 6 9 minecraft:granite"));
        assertEquals(true, group.contains("set 12 7 12 minecraft:gold_block"));
        assertEquals(2, report.blockCommands());
        assertEquals(1, comparison.chunkCount());
        assertEquals(8192, comparison.blockChecks());
    }

    @Test
    void reviewableImportPromotesRepeatedIsolatedStacksToComponents() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Repeated Components" {
                  version 3465
                  min_y 0
                  height 32
                  fill 0 0 0 to 15 0 15 minecraft:stone_bricks
                  fill 2 1 2 to 2 3 2 minecraft:dark_oak_fence
                  set 2 4 2 minecraft:lantern[hanging=true]
                  fill 6 1 2 to 6 3 2 minecraft:dark_oak_fence
                  set 6 4 2 minecraft:lantern[hanging=true]
                  fill 2 1 6 to 2 3 6 minecraft:dark_oak_fence
                  set 2 4 6 minecraft:lantern[hanging=true]
                  fill 6 1 6 to 6 3 6 minecraft:dark_oak_fence
                  set 6 4 6 minecraft:lantern[hanging=true]
                }
                """);
        Path sourceWorld = tempDir.resolve("component-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);

        Path importedDir = tempDir.resolve("component-import");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedDir,
                new AtfWorldImporter.ImportOptions(0, AtfWorldImporter.ImportLayout.REVIEWABLE));
        WorldSpec imported = new DslParser().parse(importedDir.resolve("world.atf"));
        Path regeneratedWorld = tempDir.resolve("component-regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        ReferenceWorldRoundTripComparator.ComparisonReport comparison =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);

        assertEquals(1, report.promotedComponents());
        assertEquals(4, report.componentPlacements());
        assertEquals(true, Files.exists(importedDir.resolve("components/component-0001.atf")));
        assertEquals(true, Files.readString(importedDir.resolve("world.atf"))
                .contains("include \"components/component-0001.atf\""));
        String group = Files.readString(importedDir.resolve("groups/imported-0-0.atf"));
        assertEquals(true, group.contains("place component-0001 at 2 1 2"));
        assertEquals(true, group.contains("place component-0001 at 6 1 6"));
        assertEquals(1, comparison.chunkCount());
    }

    @Test
    void reviewableImportPromotesRepeatedConnectedVolumesToComponents() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Repeated Volume Components" {
                  version 3465
                  min_y 0
                  height 32
                  fill 0 0 0 to 15 0 15 minecraft:stone_bricks
                  fill 2 1 2 to 3 2 3 minecraft:copper_block
                  fill 7 1 2 to 8 2 3 minecraft:copper_block
                  fill 2 1 7 to 3 2 8 minecraft:copper_block
                }
                """);
        Path sourceWorld = tempDir.resolve("volume-component-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);

        Path importedDir = tempDir.resolve("volume-component-import");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedDir,
                new AtfWorldImporter.ImportOptions(0, AtfWorldImporter.ImportLayout.REVIEWABLE));
        WorldSpec imported = new DslParser().parse(importedDir.resolve("world.atf"));
        Path regeneratedWorld = tempDir.resolve("volume-component-regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        ReferenceWorldRoundTripComparator.ComparisonReport comparison =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);

        assertEquals(1, report.promotedComponents());
        assertEquals(3, report.componentPlacements());
        String component = Files.readString(importedDir.resolve("components/component-0001.atf"));
        assertEquals(true, component.contains("bounds 0 0 0 to 1 1 1"));
        String group = Files.readString(importedDir.resolve("groups/imported-0-0.atf"));
        assertEquals(true, group.contains("place component-0001 at 2 1 2"));
        assertEquals(true, group.contains("place component-0001 at 7 1 2"));
        assertEquals(true, group.contains("place component-0001 at 2 1 7"));
        assertEquals(1, comparison.chunkCount());
    }

    @Test
    void reviewableImportPromotesRepeatedBoundaryTouchingVolumesToComponents() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Repeated Boundary Components" {
                  version 3465
                  min_y 0
                  height 32
                  fill 0 0 0 to 95 0 15 minecraft:stone_bricks
                  fill 14 1 2 to 15 2 3 minecraft:copper_block
                  fill 46 1 2 to 47 2 3 minecraft:copper_block
                  fill 78 1 2 to 79 2 3 minecraft:copper_block
                }
                """);
        Path sourceWorld = tempDir.resolve("boundary-component-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);

        Path importedDir = tempDir.resolve("boundary-component-import");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedDir,
                new AtfWorldImporter.ImportOptions(0, AtfWorldImporter.ImportLayout.REVIEWABLE));
        WorldSpec imported = new DslParser().parse(importedDir.resolve("world.atf"));
        Path regeneratedWorld = tempDir.resolve("boundary-component-regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        ReferenceWorldRoundTripComparator.ComparisonReport comparison =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);

        assertEquals(1, report.promotedComponents());
        assertEquals(3, report.componentPlacements());
        String component = Files.readString(importedDir.resolve("components/component-0001.atf"));
        assertEquals(true, component.contains("bounds 0 0 0 to 1 1 1"));
        assertEquals(true, Files.readString(importedDir.resolve("groups/imported-0-0.atf"))
                .contains("place component-0001 at 14 1 2"));
        assertEquals(true, Files.readString(importedDir.resolve("groups/imported-2-0.atf"))
                .contains("place component-0001 at 14 1 2"));
        assertEquals(true, Files.readString(importedDir.resolve("groups/imported-4-0.atf"))
                .contains("place component-0001 at 14 1 2"));
        assertEquals(6, comparison.chunkCount());
    }

    @Test
    void reviewableImportPromotesRepeatedCrossChunkVolumesToComponents() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Repeated Cross Chunk Components" {
                  version 3465
                  min_y 0
                  height 32
                  fill 0 0 0 to 95 0 15 minecraft:stone_bricks
                  fill 14 1 4 to 17 2 5 minecraft:copper_block
                  fill 46 1 4 to 49 2 5 minecraft:copper_block
                  fill 78 1 4 to 81 2 5 minecraft:copper_block
                }
                """);
        Path sourceWorld = tempDir.resolve("cross-chunk-component-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);

        Path importedDir = tempDir.resolve("cross-chunk-component-import");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedDir,
                new AtfWorldImporter.ImportOptions(0, AtfWorldImporter.ImportLayout.REVIEWABLE));
        WorldSpec imported = new DslParser().parse(importedDir.resolve("world.atf"));
        Path regeneratedWorld = tempDir.resolve("cross-chunk-component-regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        ReferenceWorldRoundTripComparator.ComparisonReport comparison =
                new ReferenceWorldRoundTripComparator().compare(sourceWorld, regeneratedWorld);

        assertEquals(1, report.promotedComponents());
        assertEquals(3, report.componentPlacements());
        String component = Files.readString(importedDir.resolve("components/component-0001.atf"));
        assertEquals(true, component.contains("bounds 0 0 0 to 3 1 1"));
        assertEquals(true, Files.readString(importedDir.resolve("groups/imported-0-0.atf"))
                .contains("place component-0001 at 14 1 4"));
        assertEquals(true, Files.readString(importedDir.resolve("groups/imported-2-0.atf"))
                .contains("place component-0001 at 14 1 4"));
        assertEquals(true, Files.readString(importedDir.resolve("groups/imported-4-0.atf"))
                .contains("place component-0001 at 14 1 4"));
        assertEquals(false, Files.readString(importedDir.resolve("groups/imported-1-0.atf"))
                .contains("minecraft:copper_block"));
        assertEquals(6, comparison.chunkCount());
    }

    @Test
    void skipsCorruptPassthroughDataFilesSoRegeneratedWorldValidates() throws Exception {
        WorldSpec source = new DslParser().parse("""
                world "Corrupt Data Source" {
                  min_y 0
                  height 64
                  set 0 8 0 minecraft:stone
                }
                """);
        Path sourceWorld = tempDir.resolve("corrupt-data-source-world");
        new AnvilWorldWriter().write(source, sourceWorld);
        Files.createDirectories(sourceWorld.resolve("data"));
        Files.write(sourceWorld.resolve("data/map_1.dat"), new byte[] {31, -117, 8});

        Path importedAtf = tempDir.resolve("corrupt-data.atf");
        AtfWorldImporter.ImportReport report = new AtfWorldImporter().importWorld(sourceWorld, importedAtf,
                AtfWorldImporter.ImportOptions.allChunks());
        WorldSpec imported = new DslParser().parse(importedAtf);
        Path regeneratedWorld = tempDir.resolve("corrupt-data-regenerated-world");
        new AnvilWorldWriter().write(imported, regeneratedWorld);
        new ReferenceWorldValidator().validate(regeneratedWorld, imported);

        assertEquals(1, report.skippedNbt());
        assertEquals(true, Files.readString(importedAtf).contains("Skipped corrupt passthrough NBT data file"));
        assertFalse(Files.exists(regeneratedWorld.resolve("data/map_1.dat")));
    }
}
