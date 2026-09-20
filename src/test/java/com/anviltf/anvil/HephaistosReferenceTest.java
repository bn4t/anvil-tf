package com.anviltf.anvil;

import com.anviltf.WorldSpec;
import com.anviltf.dsl.DslParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HephaistosReferenceTest {
    @TempDir
    Path tempDir;

    @Test
    void hephaistosCanReadGeneratedRegion() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Reference" {
                  min_y 0
                  height 256
                  set 1 1 1 minecraft:gold_block
                  set 2 1 1 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 2 1 1 minecraft:chest {
                    list Items compound { }
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:gold_block", referenceBlock(tempDir, 1, 1, 1));
        assertEquals(1, referenceBlockEntityCount(tempDir, 2, 1));
    }

    @Test
    void hephaistosCanReadGeneratedInventoryItemStacks() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Inventory Reference" {
                  min_y 0
                  height 64
                  set 2 8 2 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 2 8 2 chest {
                    item Items slot 0 bread count 8
                    item Items slot 5 diamond count 2
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:bread", referenceBlockEntityItemString(tempDir, 2, 2, "Items", 0, "id"));
        assertEquals((byte) 8, referenceBlockEntityItemByte(tempDir, 2, 2, "Items", 0, "Count"));
        assertEquals((byte) 5, referenceBlockEntityItemByte(tempDir, 2, 2, "Items", 1, "Slot"));
        assertEquals("minecraft:diamond", referenceBlockEntityItemString(tempDir, 2, 2, "Items", 1, "id"));
    }

    @Test
    void hephaistosCanReadBareBiomeAndEntityIds() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Bare Resource Reference" {
                  min_y 0
                  height 64
                  default_biome forest
                  fill_biome 0 8 0 15 8 15 desert
                  entity 1.5 8.0 1.5 armor_stand {
                    byte Marker 1
                  }
                  set 1 8 1 stone
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:desert", referenceBiome(tempDir, 1, 8, 1));
        assertEquals("minecraft:armor_stand", referenceEntityString(tempDir, 1, 1, "id"));
    }

    @Test
    void hephaistosCanReadBlockEntityOnlyChunks() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Block Entity Only Reference" {
                  min_y 0
                  height 64
                  block_entity 48 8 -33 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Detached\\"}"
                    item Items slot 0 minecraft:emerald count 4
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals(1, referenceBlockEntityCount(tempDir, 48, -33));
        assertEquals("minecraft:emerald", referenceBlockEntityItemString(tempDir, 48, -33, "Items", 0, "id"));
        assertEquals((byte) 4, referenceBlockEntityItemByte(tempDir, 48, -33, "Items", 0, "Count"));
    }

    @Test
    void blockEntityStructuralTagsStayAuthoritative() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Block Entity Structural Reference" {
                  min_y 0
                  height 64
                  set 2 8 2 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 2 8 2 minecraft:chest {
                    string id "minecraft:furnace"
                    int x 99
                    int y 99
                    int z 99
                    string CustomName "{\\"text\\":\\"Still a chest here\\"}"
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:chest", referenceBlockEntityString(tempDir, 2, 2, "id"));
        assertEquals(2, referenceBlockEntityInt(tempDir, 2, 2, "x"));
        assertEquals(8, referenceBlockEntityInt(tempDir, 2, 2, "y"));
        assertEquals(2, referenceBlockEntityInt(tempDir, 2, 2, "z"));
        assertEquals("{\"text\":\"Still a chest here\"}", referenceBlockEntityString(tempDir, 2, 2, "CustomName"));
    }

    @Test
    void hephaistosCanReadMergedBlockEntityOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Merged Block Entity Reference" {
                  min_y 0
                  height 64
                  set 2 8 2 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 2 8 2 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Layered\\"}"
                    byte Keep 1
                    item Items slot 0 minecraft:torch count 16
                  }
                  merge_block_entity 2 8 2 minecraft:chest {
                    item Items slot 1 minecraft:bread count 3
                    byte Keep 2
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals(1, referenceBlockEntityCount(tempDir, 2, 2));
        assertEquals("{\"text\":\"Layered\"}", referenceBlockEntityString(tempDir, 2, 2, "CustomName"));
        assertEquals((byte) 2, referenceBlockEntityByte(tempDir, 2, 2, "Keep"));
        assertEquals("minecraft:torch", referenceBlockEntityItemString(tempDir, 2, 2, "Items", 0, "id"));
        assertEquals((byte) 16, referenceBlockEntityItemByte(tempDir, 2, 2, "Items", 0, "Count"));
        assertEquals("minecraft:bread", referenceBlockEntityItemString(tempDir, 2, 2, "Items", 1, "id"));
        assertEquals((byte) 1, referenceBlockEntityItemByte(tempDir, 2, 2, "Items", 1, "Slot"));
        assertEquals((byte) 3, referenceBlockEntityItemByte(tempDir, 2, 2, "Items", 1, "Count"));
    }

    @Test
    void hephaistosCanReadSignTextShortcutOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Sign Reference" {
                  min_y 0
                  height 64
                  set 1 8 1 minecraft:oak_sign[rotation=0,waterlogged=false]
                  block_entity 1 8 1 minecraft:sign {
                    byte Keep 1
                  }
                  sign_text 1 8 1 front "Welcome" "to" "anvil-tf" "!"
                  sign_text 1 8 1 back "Back" "side" "" ""
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:oak_sign", referenceBlock(tempDir, 1, 8, 1));
        assertEquals((byte) 1, referenceBlockEntityByte(tempDir, 1, 1, "Keep"));
        assertEquals("Welcome", referenceSignMessage(tempDir, 1, 1, "front_text", 0));
        assertEquals("anvil-tf", referenceSignMessage(tempDir, 1, 1, "front_text", 2));
        assertEquals("Back", referenceSignMessage(tempDir, 1, 1, "back_text", 0));
    }

    @Test
    void hephaistosCanReadParameterizedTemplateOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Parameterized Reference" {
                  min_y 0
                  height 64
                  template "shelf" params width layers {
                    repeat y 0 $layers-1 {
                      fill 0 $y 0 $width-1 $y 0 minecraft:bookshelf
                    }
                  }
                  use "shelf" at 4 8 4 args 3 2
                  use "shelf" at 12 8 4 args 2 1 rotate 90
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:bookshelf", referenceBlock(tempDir, 6, 9, 4));
        assertEquals("minecraft:air", referenceBlock(tempDir, 7, 8, 4));
        assertEquals("minecraft:bookshelf", referenceBlock(tempDir, 12, 8, 5));
    }

    @Test
    void hephaistosCanReadGeneratedLevelDat() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Metadata Reference" {
                  min_y 0
                  height 64
                  spawn 4 8 12
                  time 24000
                  day_time 6000
                  game_mode adventure
                  difficulty hard
                  hardcore true
                  allow_commands false
                  game_rule doMobSpawning false
                  game_rule keepInventory true
                  set 4 7 12 minecraft:grass_block[snowy=false]
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        Object data = referenceLevelData(tempDir);
        Method getString = data.getClass().getMethod("getString", String.class);
        Method getInt = data.getClass().getMethod("getInt", String.class);
        Method getLong = data.getClass().getMethod("getLong", String.class);
        Method getByte = data.getClass().getMethod("getByte", String.class);
        Method getCompound = data.getClass().getMethod("getCompound", String.class);
        assertEquals("Metadata Reference", getString.invoke(data, "LevelName"));
        assertEquals(4, getInt.invoke(data, "SpawnX"));
        assertEquals(8, getInt.invoke(data, "SpawnY"));
        assertEquals(12, getInt.invoke(data, "SpawnZ"));
        assertEquals(24000L, getLong.invoke(data, "Time"));
        assertEquals(6000L, getLong.invoke(data, "DayTime"));
        assertEquals(2, getInt.invoke(data, "GameType"));
        assertEquals((byte) 3, getByte.invoke(data, "Difficulty"));
        assertEquals((byte) 1, getByte.invoke(data, "hardcore"));
        assertEquals((byte) 0, getByte.invoke(data, "allowCommands"));
        Object gameRules = getCompound.invoke(data, "GameRules");
        assertEquals("false", getString.invoke(gameRules, "doMobSpawning"));
        assertEquals("true", getString.invoke(gameRules, "keepInventory"));
    }

    @Test
    void hephaistosCanReadTemplateGeneratedInteriorDetails() throws Exception {
        Path source = Path.of("examples/cottage.atf");
        WorldSpec spec = new DslParser().parse(source);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:oak_stairs", referenceBlock(tempDir, 6, 66, 8));
        assertEquals("south", referenceBlockProperty(tempDir, 8, 66, 8, "facing"));
        assertEquals("minecraft:oak_pressure_plate", referenceBlock(tempDir, 7, 67, 8));
        assertEquals("minecraft:chest", referenceBlock(tempDir, 10, 66, 10));
        assertEquals("minecraft:flower_forest", referenceBiome(tempDir, 8, 66, 8));
        assertEquals(7, referenceBlockEntityCount(tempDir, 10, 10));
        assertEquals("Welcome", referenceSignMessage(tempDir, 7, 4, "front_text", 0));
        assertEquals(1, referenceEntityCount(tempDir, 8, 8));
    }

    @Test
    void hephaistosCanReadAcrossMultipleRegionsAndNegativeCoordinates() throws Exception {
        WorldSpec spec = new DslParser().parse(Path.of("examples/region-spanning.atf"));
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:lapis_block", referenceBlock(tempDir, -513, 10, -513));
        assertEquals("minecraft:gold_block", referenceBlock(tempDir, -1, 10, -1));
        assertEquals("minecraft:diamond_block", referenceBlock(tempDir, 0, 10, 0));
        assertEquals("minecraft:emerald_block", referenceBlock(tempDir, 512, 10, 512));
        assertEquals("minecraft:snowy_plains", referenceBiome(tempDir, -512, 10, -512));
        assertEquals(1, referenceEntityCount(tempDir, 512, 512));

        assertEquals(true, Files.exists(tempDir.resolve("region/r.-2.-2.mca")));
        assertEquals(true, Files.exists(tempDir.resolve("region/r.-1.-1.mca")));
        assertEquals(true, Files.exists(tempDir.resolve("region/r.0.0.mca")));
        assertEquals(true, Files.exists(tempDir.resolve("region/r.1.1.mca")));
    }

    @Test
    void hephaistosCanReadCompactLayerFillsAcrossManyChunks() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Compact Fill" {
                  min_y 0
                  height 64
                  fill 0 8 0 63 8 63 minecraft:stone
                  set 17 8 17 minecraft:air
                  set 63 9 63 minecraft:gold_block
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:stone", referenceBlock(tempDir, 0, 8, 0));
        assertEquals("minecraft:stone", referenceBlock(tempDir, 63, 8, 63));
        assertEquals("minecraft:air", referenceBlock(tempDir, 17, 8, 17));
        assertEquals("minecraft:gold_block", referenceBlock(tempDir, 63, 9, 63));
    }

    @Test
    void hephaistosCanReadGeneratedRoundedShapes() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Rounded Shapes" {
                  min_y 0
                  height 64
                  sphere 8 12 8 3 minecraft:glass
                  hollow_cylinder 24 8 8 3 14 minecraft:stone_bricks minecraft:air
                  hollow_sphere 40 12 8 3 minecraft:copper_block minecraft:air
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:glass", referenceBlock(tempDir, 8, 12, 8));
        assertEquals("minecraft:glass", referenceBlock(tempDir, 11, 12, 8));
        assertEquals("minecraft:air", referenceBlock(tempDir, 11, 15, 11));
        assertEquals("minecraft:stone_bricks", referenceBlock(tempDir, 24, 8, 8));
        assertEquals("minecraft:air", referenceBlock(tempDir, 24, 10, 8));
        assertEquals("minecraft:copper_block", referenceBlock(tempDir, 43, 12, 8));
        assertEquals("minecraft:air", referenceBlock(tempDir, 40, 12, 8));
    }

    @Test
    void hephaistosCanReadPatternGeneratedInteriorDetails() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Pattern Details" {
                  min_y 0
                  height 64
                  block dark = minecraft:deepslate_tiles
                  block light = minecraft:polished_diorite

                  pattern 4 8 4 {
                    block D = $dark
                    block L = $light
                    row "DLDL"
                    row "LDLD"
                    layer
                    block B = minecraft:bookshelf
                    row "BBBB"
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:deepslate_tiles", referenceBlock(tempDir, 4, 8, 4));
        assertEquals("minecraft:polished_diorite", referenceBlock(tempDir, 5, 8, 4));
        assertEquals("minecraft:polished_diorite", referenceBlock(tempDir, 4, 8, 5));
        assertEquals("minecraft:bookshelf", referenceBlock(tempDir, 7, 9, 4));
    }

    @Test
    void hephaistosCanReadReusablePatternTemplates() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Reusable Pattern Details" {
                  min_y 0
                  height 64

                  pattern_template "wall_panel" {
                    block T = $trim
                    block C = $center
                    row "TTT"
                    row "TCT"
                    row "TTT"
                  }

                  use_pattern "wall_panel" at 2 8 2 with {
                    block trim = minecraft:dark_oak_planks
                    block center = minecraft:bookshelf
                  }

                  use_pattern "wall_panel" at 8 8 2 with {
                    block trim = minecraft:smooth_quartz
                    block center = minecraft:glowstone
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:dark_oak_planks", referenceBlock(tempDir, 2, 8, 2));
        assertEquals("minecraft:bookshelf", referenceBlock(tempDir, 3, 8, 3));
        assertEquals("minecraft:smooth_quartz", referenceBlock(tempDir, 8, 8, 2));
        assertEquals("minecraft:glowstone", referenceBlock(tempDir, 9, 8, 3));
    }

    @Test
    void hephaistosCanReadDeterministicPaletteFills() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Palette Fill Reference" {
                  min_y 0
                  height 64
                  block_palette path {
                    block minecraft:stone_bricks weight 3
                    block minecraft:cracked_stone_bricks
                    block minecraft:mossy_stone_bricks
                  }
                  fill_palette 0 8 0 15 8 15 path seed 11
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        var chunk = spec.chunks().get(new com.anviltf.ChunkPos(0, 0));
        assertEquals(chunk.get(0, 8, 0).name(), referenceBlock(tempDir, 0, 8, 0));
        assertEquals(chunk.get(7, 8, 7).name(), referenceBlock(tempDir, 7, 8, 7));
        assertEquals(chunk.get(15, 8, 15).name(), referenceBlock(tempDir, 15, 8, 15));
    }

    @Test
    void hephaistosCanReadReplaceOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Replace Reference" {
                  min_y 0
                  height 64
                  block_palette aged {
                    block minecraft:stone_bricks weight 3
                    block minecraft:cracked_stone_bricks
                    block minecraft:mossy_stone_bricks
                  }
                  fill 0 8 0 15 8 15 minecraft:stone_bricks
                  replace 4 8 4 7 8 7 minecraft:stone_bricks minecraft:air
                  replace_palette 8 8 8 15 8 15 minecraft:stone_bricks aged seed 13
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        var chunk = spec.chunks().get(new com.anviltf.ChunkPos(0, 0));
        assertEquals("minecraft:air", referenceBlock(tempDir, 4, 8, 4));
        assertEquals("minecraft:stone_bricks", referenceBlock(tempDir, 3, 8, 3));
        assertEquals(chunk.get(8, 8, 8).name(), referenceBlock(tempDir, 8, 8, 8));
        assertEquals(chunk.get(15, 8, 15).name(), referenceBlock(tempDir, 15, 8, 15));
    }

    @Test
    void hephaistosCanReadCopiedBlocksAndBlockEntities() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Copy Reference" {
                  min_y 0
                  height 64
                  set 1 8 0 minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                  set 0 8 1 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 0 8 1 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Copied\\"}"
                    list Items compound { }
                  }
                  copy 0 8 0 1 8 1 to 10 8 10 rotate 90 skip_air
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:oak_stairs", referenceBlock(tempDir, 10, 8, 11));
        assertEquals("east", referenceBlockProperty(tempDir, 10, 8, 11, "facing"));
        assertEquals("minecraft:chest", referenceBlock(tempDir, 9, 8, 10));
        assertEquals(2, referenceBlockEntityCount(tempDir, 9, 10));
    }

    @Test
    void hephaistosCanReadCopiedEntities() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Copy Entity Reference" {
                  min_y 0
                  height 64
                  entity 0.5 8 1.5 minecraft:armor_stand {
                    byte Marker 1
                    byte Invisible 1
                    list Rotation float { 45.0 0.0 }
                    int_array UUID { 1 2 3 4 }
                  }
                  copy 0 8 0 1 8 1 to 40 8 40 rotate 90 skip_air with_entities
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals(1, referenceEntityCount(tempDir, 0, 0));
        assertEquals(1, referenceEntityCount(tempDir, 38, 40));
    }

    @Test
    void entityStructuralTagsStayAuthoritative() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Entity Structural Reference" {
                  min_y 0
                  height 64
                  entity 1.5 8.0 1.5 armor_stand {
                    string id "minecraft:zombie"
                    list Pos double { 48.5 8.0 48.5 }
                    list Rotation float { 90.0 0.0 }
                    int_array UUID { 1 2 3 4 }
                    byte Marker 1
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:armor_stand", referenceEntityString(tempDir, 1, 1, "id"));
        assertEquals(1.5d, referenceEntityDoubleListValue(tempDir, 1, 1, "Pos", 0));
        assertEquals(8.0d, referenceEntityDoubleListValue(tempDir, 1, 1, "Pos", 1));
        assertEquals(1.5d, referenceEntityDoubleListValue(tempDir, 1, 1, "Pos", 2));
        assertEquals(90.0f, referenceEntityFloatListValue(tempDir, 1, 1, "Rotation", 0));
        assertEquals((byte) 1, referenceEntityByte(tempDir, 1, 1, "Marker"));
    }

    @Test
    void hephaistosCanReadConditionalOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Conditional Reference" {
                  min_y 0
                  height 64
                  repeat x 0 4 {
                    if $x%2 == 0 {
                      set $x 8 0 minecraft:quartz_block
                    } else {
                      set $x 8 0 minecraft:coal_block
                    }
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:quartz_block", referenceBlock(tempDir, 0, 8, 0));
        assertEquals("minecraft:coal_block", referenceBlock(tempDir, 1, 8, 0));
        assertEquals("minecraft:quartz_block", referenceBlock(tempDir, 4, 8, 0));
    }

    @Test
    void hephaistosCanReadCopiedBiomes() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Copy Biome Reference" {
                  min_y 0
                  height 64
                  default_biome minecraft:plains
                  fill_biome 0 8 0 7 11 7 minecraft:flower_forest
                  copy 0 8 0 7 11 7 to 32 8 32 skip_air with_biomes
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:flower_forest", referenceBiome(tempDir, 32, 8, 32));
        assertEquals("minecraft:flower_forest", referenceBiome(tempDir, 36, 8, 36));
        assertEquals("minecraft:plains", referenceBiome(tempDir, 32, 12, 32));
    }

    @Test
    void hephaistosCanReadScatterOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Scatter Reference" {
                  min_y 0
                  height 64
                  scatter 0 8 0 8 8 8 chance 1 1 step 4 {
                    if $z == 4 {
                      set 0 0 0 minecraft:gold_block
                    } else {
                      set 0 0 0 minecraft:iron_block
                    }
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:iron_block", referenceBlock(tempDir, 0, 8, 0));
        assertEquals("minecraft:gold_block", referenceBlock(tempDir, 4, 8, 4));
        assertEquals("minecraft:iron_block", referenceBlock(tempDir, 8, 8, 8));
    }

    @Test
    void hephaistosCanReadWeightedChooseScatterOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Weighted Scatter Reference" {
                  min_y 0
                  height 64
                  scatter 0 8 0 8 8 8 chance 1 1 step 4 {
                    choose seed 19 {
                      weight 1 {
                        set 0 0 0 minecraft:gold_block
                      }
                      weight 3 {
                        set 0 0 0 minecraft:iron_block
                      }
                    }
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        var chunk = spec.chunks().get(new com.anviltf.ChunkPos(0, 0));
        assertEquals(chunk.get(0, 8, 0).name(), referenceBlock(tempDir, 0, 8, 0));
        assertEquals(chunk.get(4, 8, 4).name(), referenceBlock(tempDir, 4, 8, 4));
        assertEquals(chunk.get(8, 8, 8).name(), referenceBlock(tempDir, 8, 8, 8));
    }

    @Test
    void hephaistosCanReadTerrainOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Terrain Reference" {
                  min_y 0
                  height 64
                  terrain 0 0 15 15 from 1 base 8 amplitude 3 seed 99 minecraft:grass_block[snowy=false] minecraft:dirt
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        var chunk = spec.chunks().get(new com.anviltf.ChunkPos(0, 0));
        assertEquals(chunk.get(0, 8, 0).name(), referenceBlock(tempDir, 0, 8, 0));
        assertEquals(chunk.get(7, 9, 7).name(), referenceBlock(tempDir, 7, 9, 7));
        assertEquals(chunk.get(15, 10, 15).name(), referenceBlock(tempDir, 15, 10, 15));
    }

    @Test
    void hephaistosCanReadSmoothTerrainOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Smooth Terrain Reference" {
                  min_y 0
                  height 64
                  smooth_terrain 0 0 31 31 from 1 base 8 amplitude 6 scale 12 octaves 3 seed 77 minecraft:grass_block[snowy=false] minecraft:dirt
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        var chunk = spec.chunks().get(new com.anviltf.ChunkPos(0, 0));
        assertEquals(chunk.get(0, 8, 0).name(), referenceBlock(tempDir, 0, 8, 0));
        assertEquals(chunk.get(10, 10, 10).name(), referenceBlock(tempDir, 10, 10, 10));
        assertEquals(chunk.get(15, 12, 15).name(), referenceBlock(tempDir, 15, 12, 15));
    }

    @Test
    void hephaistosCanReadSurfaceScatterOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Surface Scatter Reference" {
                  min_y 0
                  height 64
                  terrain 0 0 8 8 from 1 base 8 amplitude 0 seed 12 minecraft:grass_block[snowy=false] minecraft:dirt
                  surface_scatter 0 0 8 8 from 1 to 16 chance 1 1 step 4 {
                    set 0 0 0 minecraft:poppy
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:grass_block", referenceBlock(tempDir, 0, 8, 0));
        assertEquals("minecraft:poppy", referenceBlock(tempDir, 0, 9, 0));
        assertEquals("minecraft:poppy", referenceBlock(tempDir, 8, 9, 8));
    }

    @Test
    void hephaistosCanReadWallOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Wall Reference" {
                  min_y 0
                  height 64
                  wall 1 8 1 3 2 east minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                  at 10 0 10 rotate 90 {
                    wall 0 8 0 2 1 north minecraft:glass_pane[east=false,north=true,south=false,waterlogged=false,west=false]
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:oak_stairs", referenceBlock(tempDir, 1, 8, 1));
        assertEquals("east", referenceBlockProperty(tempDir, 1, 8, 1, "facing"));
        assertEquals("minecraft:oak_stairs", referenceBlock(tempDir, 1, 9, 3));
        assertEquals("minecraft:glass_pane", referenceBlock(tempDir, 10, 8, 11));
        assertEquals("true", referenceBlockProperty(tempDir, 10, 8, 11, "east"));
    }

    @Test
    void hephaistosCanReadDoorShortcutOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Door Reference" {
                  min_y 0
                  height 64
                  door 1 8 1 south left closed minecraft:oak_door
                  at 10 0 10 rotate 90 {
                    door 0 8 0 north right open minecraft:birch_door
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:oak_door", referenceBlock(tempDir, 1, 8, 1));
        assertEquals("lower", referenceBlockProperty(tempDir, 1, 8, 1, "half"));
        assertEquals("upper", referenceBlockProperty(tempDir, 1, 9, 1, "half"));
        assertEquals("south", referenceBlockProperty(tempDir, 1, 8, 1, "facing"));
        assertEquals("minecraft:birch_door", referenceBlock(tempDir, 10, 8, 10));
        assertEquals("east", referenceBlockProperty(tempDir, 10, 8, 10, "facing"));
        assertEquals("true", referenceBlockProperty(tempDir, 10, 8, 10, "open"));
    }

    @Test
    void hephaistosCanReadBedShortcutOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Bed Reference" {
                  min_y 0
                  height 64
                  bed 1 8 1 east minecraft:red_bed
                  at 10 0 10 rotate 90 {
                    bed 0 8 0 north minecraft:blue_bed
                  }
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:red_bed", referenceBlock(tempDir, 1, 8, 1));
        assertEquals("foot", referenceBlockProperty(tempDir, 1, 8, 1, "part"));
        assertEquals("head", referenceBlockProperty(tempDir, 2, 8, 1, "part"));
        assertEquals("east", referenceBlockProperty(tempDir, 1, 8, 1, "facing"));
        assertEquals("minecraft:blue_bed", referenceBlock(tempDir, 10, 8, 10));
        assertEquals("east", referenceBlockProperty(tempDir, 10, 8, 10, "facing"));
        assertEquals("head", referenceBlockProperty(tempDir, 11, 8, 10, "part"));
    }

    @Test
    void hephaistosCanReadLineOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Line Reference" {
                  min_y 0
                  height 64
                  line -2 8 -2 2 12 2 minecraft:copper_block
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:copper_block", referenceBlock(tempDir, -2, 8, -2));
        assertEquals("minecraft:copper_block", referenceBlock(tempDir, 0, 10, 0));
        assertEquals("minecraft:copper_block", referenceBlock(tempDir, 2, 12, 2));
    }

    @Test
    void hephaistosCanReadBareVanillaBlockNames() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Bare Block Reference" {
                  min_y 0
                  height 64
                  set 1 8 1 stone
                  set 2 8 1 oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:stone", referenceBlock(tempDir, 1, 8, 1));
        assertEquals("minecraft:oak_stairs", referenceBlock(tempDir, 2, 8, 1));
        assertEquals("north", referenceBlockProperty(tempDir, 2, 8, 1, "facing"));
    }

    @Test
    void hephaistosCanReadMirroredCopyOutput() throws Exception {
        WorldSpec spec = new DslParser().parse("""
                world "Mirror Copy Reference" {
                  min_y 0
                  height 64
                  set 0 8 0 minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=false]
                  set 2 8 0 minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]
                  copy 0 8 0 2 8 0 to 10 8 10 mirror_x skip_air
                }
                """);
        new AnvilWorldWriter().write(spec, tempDir);

        assertEquals("minecraft:oak_stairs", referenceBlock(tempDir, 12, 8, 10));
        assertEquals("west", referenceBlockProperty(tempDir, 12, 8, 10, "facing"));
        assertEquals("inner_right", referenceBlockProperty(tempDir, 12, 8, 10, "shape"));
        assertEquals("west", referenceBlockProperty(tempDir, 10, 8, 10, "facing"));
        assertEquals("right", referenceBlockProperty(tempDir, 10, 8, 10, "hinge"));
    }

    private static Object referenceChunk(Path worldDir, int blockX, int blockZ) throws Exception {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);

        Class<?> regionType = Class.forName("org.jglrxavpok.hephaistos.mca.RegionFile");
        Constructor<?> constructor = regionType.getConstructor(RandomAccessFile.class, int.class, int.class);
        Path file = worldDir.resolve("region/r." + regionX + "." + regionZ + ".mca");
        Object region = constructor.newInstance(new RandomAccessFile(file.toFile(), "r"), regionX, regionZ);
        Method getChunk = regionType.getMethod("getChunk", int.class, int.class);
        return getChunk.invoke(region, chunkX, chunkZ);
    }

    private static Object referenceLevelData(Path worldDir) throws Exception {
        Class<?> processorType = Class.forName("org.jglrxavpok.hephaistos.nbt.CompressedProcesser");
        Object gzip = processorType.getField("GZIP").get(null);
        Class<?> readerType = Class.forName("org.jglrxavpok.hephaistos.nbt.NBTReader");
        try (AutoCloseable reader = (AutoCloseable) readerType
                .getConstructor(Path.class, processorType)
                .newInstance(worldDir.resolve("level.dat"), gzip)) {
            Object pair = readerType.getMethod("readNamed").invoke(reader);
            Object root = pair.getClass().getMethod("getSecond").invoke(pair);
            return root.getClass().getMethod("getCompound", String.class).invoke(root, "Data");
        }
    }

    private static String referenceBlock(Path worldDir, int x, int y, int z) throws Exception {
        Object block = referenceBlockState(worldDir, x, y, z);
        Method getName = block.getClass().getMethod("getName");
        return (String) getName.invoke(block);
    }

    private static String referenceBlockProperty(Path worldDir, int x, int y, int z, String property) throws Exception {
        Object block = referenceBlockState(worldDir, x, y, z);
        Method getProperties = block.getClass().getMethod("getProperties");
        Object properties = getProperties.invoke(block);
        Method get = properties.getClass().getMethod("get", Object.class);
        return (String) get.invoke(properties, property);
    }

    private static Object referenceBlockState(Path worldDir, int x, int y, int z) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getBlockState = chunk.getClass().getMethod("getBlockState", int.class, int.class, int.class);
        return getBlockState.invoke(chunk, Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    private static int referenceBlockEntityCount(Path worldDir, int x, int z) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getTileEntities = chunk.getClass().getMethod("getTileEntities");
        Object tileEntities = getTileEntities.invoke(chunk);
        Method size = tileEntities.getClass().getMethod("getSize");
        return (int) size.invoke(tileEntities);
    }

    private static byte referenceBlockEntityByte(Path worldDir, int x, int z, String key) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getTileEntities = chunk.getClass().getMethod("getTileEntities");
        Object tileEntities = getTileEntities.invoke(chunk);
        Method get = tileEntities.getClass().getMethod("get", int.class);
        Object blockEntity = get.invoke(tileEntities, 0);
        Method getByte = blockEntity.getClass().getMethod("getByte", String.class);
        return (Byte) getByte.invoke(blockEntity, key);
    }

    private static String referenceBlockEntityString(Path worldDir, int x, int z, String key) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getTileEntities = chunk.getClass().getMethod("getTileEntities");
        Object tileEntities = getTileEntities.invoke(chunk);
        Method get = tileEntities.getClass().getMethod("get", int.class);
        Object blockEntity = get.invoke(tileEntities, 0);
        Method getString = blockEntity.getClass().getMethod("getString", String.class);
        return (String) getString.invoke(blockEntity, key);
    }

    private static int referenceBlockEntityInt(Path worldDir, int x, int z, String key) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getTileEntities = chunk.getClass().getMethod("getTileEntities");
        Object tileEntities = getTileEntities.invoke(chunk);
        Method get = tileEntities.getClass().getMethod("get", int.class);
        Object blockEntity = get.invoke(tileEntities, 0);
        Method getInt = blockEntity.getClass().getMethod("getInt", String.class);
        return (Integer) getInt.invoke(blockEntity, key);
    }

    private static String referenceBlockEntityItemString(Path worldDir, int x, int z,
                                                        String listName, int index, String key) throws Exception {
        Object item = referenceBlockEntityItem(worldDir, x, z, listName, index);
        Method getString = item.getClass().getMethod("getString", String.class);
        return (String) getString.invoke(item, key);
    }

    private static byte referenceBlockEntityItemByte(Path worldDir, int x, int z,
                                                     String listName, int index, String key) throws Exception {
        Object item = referenceBlockEntityItem(worldDir, x, z, listName, index);
        Method getByte = item.getClass().getMethod("getByte", String.class);
        return (Byte) getByte.invoke(item, key);
    }

    private static Object referenceBlockEntityItem(Path worldDir, int x, int z,
                                                   String listName, int index) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getTileEntities = chunk.getClass().getMethod("getTileEntities");
        Object tileEntities = getTileEntities.invoke(chunk);
        Method get = tileEntities.getClass().getMethod("get", int.class);
        Object blockEntity = get.invoke(tileEntities, 0);
        Method getList = blockEntity.getClass().getMethod("getList", String.class);
        Object items = getList.invoke(blockEntity, listName);
        return get.invoke(items, index);
    }

    private static String referenceSignMessage(Path worldDir, int x, int z,
                                               String textKey, int index) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getTileEntities = chunk.getClass().getMethod("getTileEntities");
        Object tileEntities = getTileEntities.invoke(chunk);
        Method size = tileEntities.getClass().getMethod("getSize");
        Method get = tileEntities.getClass().getMethod("get", int.class);
        Object blockEntity = null;
        Object text = null;
        for (int i = 0; i < (int) size.invoke(tileEntities); i++) {
            Object candidate = get.invoke(tileEntities, i);
            Method getString = candidate.getClass().getMethod("getString", String.class);
            if ("minecraft:sign".equals(getString.invoke(candidate, "id"))) {
                Method getCompound = candidate.getClass().getMethod("getCompound", String.class);
                Object candidateText = getCompound.invoke(candidate, textKey);
                if (candidateText != null) {
                    blockEntity = candidate;
                    text = candidateText;
                    break;
                }
            }
        }
        if (blockEntity == null) {
            throw new AssertionError("no minecraft:sign block entity with " + textKey + " in chunk");
        }
        Method getList = text.getClass().getMethod("getList", String.class);
        Object messages = getList.invoke(text, "messages");
        Object message = get.invoke(messages, index);
        Method getValue = message.getClass().getMethod("getValue");
        return (String) getValue.invoke(message);
    }

    private static int referenceEntityCount(Path worldDir, int x, int z) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getEntities = chunk.getClass().getMethod("getEntities");
        Object entities = getEntities.invoke(chunk);
        Method size = entities.getClass().getMethod("getSize");
        return (int) size.invoke(entities);
    }

    private static String referenceEntityString(Path worldDir, int x, int z, String key) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getEntities = chunk.getClass().getMethod("getEntities");
        Object entities = getEntities.invoke(chunk);
        Method get = entities.getClass().getMethod("get", int.class);
        Object entity = get.invoke(entities, 0);
        Method getString = entity.getClass().getMethod("getString", String.class);
        return (String) getString.invoke(entity, key);
    }

    private static byte referenceEntityByte(Path worldDir, int x, int z, String key) throws Exception {
        Object entity = referenceEntity(worldDir, x, z);
        Method getByte = entity.getClass().getMethod("getByte", String.class);
        return (Byte) getByte.invoke(entity, key);
    }

    private static double referenceEntityDoubleListValue(Path worldDir, int x, int z,
                                                         String listName, int index) throws Exception {
        Object value = referenceEntityListValue(worldDir, x, z, listName, index);
        Method getValue = value.getClass().getMethod("getValue");
        return (Double) getValue.invoke(value);
    }

    private static float referenceEntityFloatListValue(Path worldDir, int x, int z,
                                                       String listName, int index) throws Exception {
        Object value = referenceEntityListValue(worldDir, x, z, listName, index);
        Method getValue = value.getClass().getMethod("getValue");
        return (Float) getValue.invoke(value);
    }

    private static Object referenceEntityListValue(Path worldDir, int x, int z,
                                                   String listName, int index) throws Exception {
        Object entity = referenceEntity(worldDir, x, z);
        Method getList = entity.getClass().getMethod("getList", String.class);
        Object list = getList.invoke(entity, listName);
        Method get = list.getClass().getMethod("get", int.class);
        return get.invoke(list, index);
    }

    private static Object referenceEntity(Path worldDir, int x, int z) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getEntities = chunk.getClass().getMethod("getEntities");
        Object entities = getEntities.invoke(chunk);
        Method get = entities.getClass().getMethod("get", int.class);
        return get.invoke(entities, 0);
    }

    private static String referenceBiome(Path worldDir, int x, int y, int z) throws Exception {
        Object chunk = referenceChunk(worldDir, x, z);
        Method getBiome = chunk.getClass().getMethod("getBiome", int.class, int.class, int.class);
        return (String) getBiome.invoke(chunk, Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }
}
