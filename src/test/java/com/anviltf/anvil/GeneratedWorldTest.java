package com.anviltf.anvil;

import com.anviltf.WorldSpec;
import com.anviltf.dsl.DslParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedWorldTest {
    @TempDir
    Path tempDir;

    @Test
    void writesReadableRegionAndChunkNbt() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Generated" {
                  min_y 0
                  height 16
                  spawn 1 2 3
                  fill 0 0 0 15 0 15 minecraft:bedrock
                  set 1 1 1 minecraft:diamond_block
                }
                """);

        new AnvilWorldWriter().write(spec, tempDir);

        Path region = tempDir.resolve("region/r.0.0.mca");
        assertTrue(Files.exists(region));
        assertTrue(Files.size(region) % 4096 == 0);

        byte[] chunkNbt = readChunk(region, 0);
        Map<String, Object> root = new TestNbtReader(chunkNbt).readRoot();

        assertEquals(0, root.get("xPos"));
        assertEquals(0, root.get("zPos"));
        assertTrue(root.containsKey("sections"));
        assertTrue(Files.exists(tempDir.resolve("level.dat")));
    }

    @Test
    void writesOverworldDimensionRegionsAndSpawnHeightSettings() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Modern Layout" {
                  min_y 0
                  height 96
                  spawn 4 70 5
                  set 4 68 5 minecraft:gold_block
                }
                """);

        new AnvilWorldWriter().write(spec, tempDir);

        Path legacyRegion = tempDir.resolve("region/r.0.0.mca");
        Path overworldRegion = tempDir.resolve("dimensions/minecraft/overworld/region/r.0.0.mca");
        assertTrue(Files.exists(legacyRegion));
        assertTrue(Files.exists(overworldRegion));
        assertArrayEquals(Files.readAllBytes(legacyRegion), Files.readAllBytes(overworldRegion));

        Map<String, Object> root = readGzipNbt(tempDir.resolve("data/minecraft/world_gen_settings.dat"));
        Map<String, Object> data = compound(root.get("data"));
        Map<String, Object> dimensions = compound(data.get("dimensions"));
        Map<String, Object> overworld = compound(dimensions.get("minecraft:overworld"));
        Map<String, Object> generator = compound(overworld.get("generator"));
        Map<String, Object> settings = compound(generator.get("settings"));
        @SuppressWarnings("unchecked")
        Map<String, Object> layer = (Map<String, Object>) ((java.util.List<Object>) settings.get("layers")).get(0);
        assertEquals(134, layer.get("height"));
        assertEquals("minecraft:air", layer.get("block"));
    }

    @Test
    void writesWeatherAndWorldBorderToLevelDat() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Weather Border" {
                  min_y 0
                  height 16
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
                  set 0 1 0 minecraft:stone
                }
                """);

        new AnvilWorldWriter().write(spec, tempDir);

        Map<String, Object> root = readGzipNbt(tempDir.resolve("level.dat"));
        Map<String, Object> data = compound(root.get("Data"));
        assertEquals((byte) 1, data.get("raining"));
        assertEquals(1200, data.get("rainTime"));
        assertEquals((byte) 1, data.get("thundering"));
        assertEquals(600, data.get("thunderTime"));
        assertEquals(0, data.get("clearWeatherTime"));
        assertEquals(12.5d, data.get("BorderCenterX"));
        assertEquals(-7.25d, data.get("BorderCenterZ"));
        assertEquals(5000.0d, data.get("BorderSize"));
        assertEquals(8000.0d, data.get("BorderSizeLerpTarget"));
        assertEquals(40L, data.get("BorderSizeLerpTime"));
        assertEquals(6.5d, data.get("BorderSafeZone"));
        assertEquals(0.4d, data.get("BorderDamagePerBlock"));
        assertEquals(9.0d, data.get("BorderWarningBlocks"));
        assertEquals(20.0d, data.get("BorderWarningTime"));
    }

    @Test
    void writesNetherAndEndRegions() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Dimension Files" {
                  min_y 0
                  height 16
                  set 0 1 0 minecraft:grass_block
                  dimension minecraft:the_nether {
                    min_y 0
                    height 128
                    set 0 8 0 minecraft:netherrack
                  }
                  dimension minecraft:the_end {
                    min_y 0
                    height 128
                    set 0 20 0 minecraft:end_stone
                  }
                }
                """);

        new AnvilWorldWriter().write(spec, tempDir);

        assertTrue(Files.exists(tempDir.resolve("DIM-1/region/r.0.0.mca")));
        assertTrue(Files.exists(tempDir.resolve("DIM1/region/r.0.0.mca")));
        assertTrue(Files.exists(tempDir.resolve("dimensions/minecraft/the_nether/region/r.0.0.mca")));
        assertTrue(Files.exists(tempDir.resolve("dimensions/minecraft/the_end/region/r.0.0.mca")));

        Map<String, Object> root = readGzipNbt(tempDir.resolve("data/minecraft/world_gen_settings.dat"));
        Map<String, Object> data = compound(root.get("data"));
        Map<String, Object> dimensions = compound(data.get("dimensions"));
        assertTrue(dimensions.containsKey("minecraft:the_nether"));
        assertTrue(dimensions.containsKey("minecraft:the_end"));

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(tempDir);
        assertEquals(2, report.dimensionRegionCount());
        assertEquals(2, report.dimensionChunkCount());
    }

    @Test
    void writesMultipleRegionFilesForFarApartChunks() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Regions" {
                  min_y 0
                  height 16
                  set -513 1 -513 minecraft:stone
                  set 512 1 512 minecraft:stone
                }
                """);

        new AnvilWorldWriter().write(spec, tempDir);

        assertTrue(Files.exists(tempDir.resolve("region/r.-2.-2.mca")));
        assertTrue(Files.exists(tempDir.resolve("region/r.1.1.mca")));
    }

    @Test
    void rewriteRemovesStaleGeneratedRegionFiles() throws Exception {
        WorldSpec large = new DslParser().parse("""
                world "Large First" {
                  min_y 0
                  height 16
                  set -513 1 -513 minecraft:stone
                  set 512 1 512 minecraft:stone
                }
                """);
        new AnvilWorldWriter().write(large, tempDir);
        Path note = tempDir.resolve("region/README.txt");
        Files.writeString(note, "preserve me");

        WorldSpec small = new DslParser().parse("""
                world "Small Second" {
                  min_y 0
                  height 16
                  set 0 1 0 minecraft:gold_block
                }
                """);
        new AnvilWorldWriter().write(small, tempDir);

        assertFalse(Files.exists(tempDir.resolve("region/r.-2.-2.mca")));
        assertFalse(Files.exists(tempDir.resolve("region/r.1.1.mca")));
        assertTrue(Files.exists(tempDir.resolve("region/r.0.0.mca")));
        assertEquals("preserve me", Files.readString(note));

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(tempDir);
        assertEquals(1, report.regionCount());
        assertEquals(1, report.chunkCount());
    }

    @Test
    void writesPassthroughSaveAssets() throws Exception {
        Path source = tempDir.resolve("source-assets");
        Files.createDirectories(source.resolve("datapacks/example/data/demo"));
        Files.writeString(source.resolve("datapacks/example/pack.mcmeta"), "{\"pack\":{\"pack_format\":26}}");
        Files.writeString(source.resolve("datapacks/example/data/demo/functions.mcfunction"), "say copied");

        WorldSpec spec = new DslParser().parse("""
                world "Passthrough" {
                  min_y 0
                  height 16
                  set 0 1 0 minecraft:stone
                  passthrough "datapacks" from "%s"
                }
                """.formatted(source.resolve("datapacks").toAbsolutePath()));

        Path output = tempDir.resolve("output-world");
        new AnvilWorldWriter().write(spec, output);

        assertEquals("{\"pack\":{\"pack_format\":26}}",
                Files.readString(output.resolve("datapacks/example/pack.mcmeta")));
        assertEquals("say copied",
                Files.readString(output.resolve("datapacks/example/data/demo/functions.mcfunction")));
    }

    @Test
    void writesByteStableOutputForSameSource() throws Exception {
        String source = """
                world "Stable" {
                  min_y 0
                  height 64
                  default_biome minecraft:forest
                  set 1 8 1 minecraft:oak_stairs[waterlogged=false,shape=straight,half=bottom,facing=north]
                  set 2 8 1 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 2 8 1 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Stable\\"}"
                    item Items slot 0 minecraft:bread count 3
                  }
                  entity 3.5 8 3.5 minecraft:armor_stand {
                    byte Marker 1
                    byte Invisible 1
                  }
                }
                """;
        Path first = tempDir.resolve("first");
        Path second = tempDir.resolve("second");

        new AnvilWorldWriter().write(new DslParser().parse(source), first);
        new AnvilWorldWriter().write(new DslParser().parse(source), second);

        assertArrayEquals(Files.readAllBytes(first.resolve("level.dat")),
                Files.readAllBytes(second.resolve("level.dat")));
        assertArrayEquals(Files.readAllBytes(first.resolve("region/r.0.0.mca")),
                Files.readAllBytes(second.resolve("region/r.0.0.mca")));
    }

    @Test
    void writesByteStableOutputAcrossMultipleRegions() throws Exception {
        String source = """
                world "Stable Regions" {
                  min_y 0
                  height 64
                  set -513 8 -513 stone
                  set 512 8 512 gold_block
                  fill_biome 0 8 512 0 8 512 forest
                  block_entity 512 8 -513 chest {
                    item Items slot 0 bread count 1
                  }
                  entity -513.5 8.0 512.5 armor_stand {
                    byte Marker 1
                  }
                }
                """;
        Path first = tempDir.resolve("first-multi");
        Path second = tempDir.resolve("second-multi");

        new AnvilWorldWriter().write(new DslParser().parse(source), first);
        new AnvilWorldWriter().write(new DslParser().parse(source), second);

        try (var firstFiles = Files.list(first.resolve("region"));
             var secondFiles = Files.list(second.resolve("region"))) {
            var firstNames = firstFiles.map(path -> path.getFileName().toString()).sorted().toList();
            var secondNames = secondFiles.map(path -> path.getFileName().toString()).sorted().toList();
            assertEquals(firstNames, secondNames);
            for (String name : firstNames) {
                assertArrayEquals(Files.readAllBytes(first.resolve("region").resolve(name)),
                        Files.readAllBytes(second.resolve("region").resolve(name)));
            }
        }

        ReferenceWorldValidator.ValidationReport report = new ReferenceWorldValidator().validate(first);
        assertEquals(5, report.chunkCount());
    }

    private static byte[] readChunk(Path region, int index) throws Exception {
        byte[] file = Files.readAllBytes(region);
        int headerOffset = index * 4;
        int sector = ((file[headerOffset] & 0xff) << 16)
                | ((file[headerOffset + 1] & 0xff) << 8)
                | (file[headerOffset + 2] & 0xff);
        int count = file[headerOffset + 3] & 0xff;
        assertTrue(sector >= 2);
        assertTrue(count >= 1);

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file, sector * 4096, count * 4096))) {
            int length = in.readInt();
            int compression = in.readUnsignedByte();
            assertEquals(2, compression);
            byte[] compressed = in.readNBytes(length - 1);
            return new InflaterInputStream(new ByteArrayInputStream(compressed)).readAllBytes();
        }
    }

    private static Map<String, Object> readGzipNbt(Path path) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(path))) {
            return new TestNbtReader(gzip.readAllBytes()).readRoot();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object value) {
        return (Map<String, Object>) value;
    }
}
