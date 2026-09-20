package com.anviltf.dsl;

import com.anviltf.BlockState;
import com.anviltf.ChunkPos;
import com.anviltf.MutableWorld;
import com.anviltf.NbtValue;
import com.anviltf.WorldSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesWorldMetadata() {
        WorldSpec world = new DslParser().parse("""
                world "Metadata" {
                  min_y 0
                  height 32
                  time 24000
                  day_time 6000
                  game_mode survival
                  difficulty hard
                  hardcore true
                  allow_commands false
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
                  game_rule doMobSpawning false
                  game_rule keepInventory true
                }
                """);

        assertEquals(24000L, world.time());
        assertEquals(6000L, world.dayTime());
        assertEquals(0, world.gameType());
        assertEquals(3, world.difficulty());
        assertEquals(true, world.hardcore());
        assertEquals(false, world.allowCommands());
        assertEquals(true, world.raining());
        assertEquals(1200, world.rainTime());
        assertEquals(true, world.thundering());
        assertEquals(600, world.thunderTime());
        assertEquals(0, world.clearWeatherTime());
        assertEquals(12.5d, world.worldBorder().centerX());
        assertEquals(-7.25d, world.worldBorder().centerZ());
        assertEquals(5000.0d, world.worldBorder().size());
        assertEquals(8000.0d, world.worldBorder().sizeLerpTarget());
        assertEquals(40L, world.worldBorder().sizeLerpTime());
        assertEquals(9, world.worldBorder().warningBlocks());
        assertEquals(20, world.worldBorder().warningTime());
        assertEquals(6.5d, world.worldBorder().damageBuffer());
        assertEquals(0.4d, world.worldBorder().damagePerBlock());
        assertEquals("false", world.gameRules().get("doMobSpawning"));
        assertEquals("true", world.gameRules().get("keepInventory"));
    }

    @Test
    void parsesDimensionScopedWorldContent() {
        WorldSpec world = new DslParser().parse("""
                world "Dimensions" {
                  min_y 0
                  height 32
                  set 0 1 0 minecraft:grass_block
                  dimension minecraft:the_nether {
                    min_y 0
                    height 128
                    default_biome minecraft:nether_wastes
                    set 0 8 0 minecraft:netherrack
                  }
                  dimension minecraft:the_end {
                    min_y 0
                    height 128
                    default_biome minecraft:the_end
                    set 0 20 0 minecraft:end_stone
                  }
                }
                """);

        assertEquals("minecraft:grass_block", world.overworld().chunks()
                .get(new ChunkPos(0, 0)).get(0, 1, 0).name());
        assertEquals("minecraft:netherrack", world.dimension(WorldSpec.NETHER).chunks()
                .get(new ChunkPos(0, 0)).get(0, 8, 0).name());
        assertEquals("minecraft:end_stone", world.dimension(WorldSpec.END).chunks()
                .get(new ChunkPos(0, 0)).get(0, 20, 0).name());
        assertEquals("minecraft:nether_wastes", world.dimension(WorldSpec.NETHER).defaultBiome());
        assertEquals("minecraft:the_end", world.dimension(WorldSpec.END).defaultBiome());
    }

    @Test
    void parsesWorldWithRepeatAndExpressions() {
        WorldSpec world = new DslParser().parse("""
                world "Tiny" {
                  min_y 0
                  height 16
                  spawn 1 2 3
                  repeat x 0 3 {
                    set $x+1 4 0 minecraft:oak_log[axis=y]
                  }
                }
                """);

        assertEquals("Tiny", world.name());
        assertEquals(1, world.chunks().size());
        assertEquals(new BlockState("minecraft:oak_log", java.util.Map.of("axis", "y")),
                world.chunks().get(new ChunkPos(0, 0)).get(4, 4, 0));
    }

    @Test
    void defaultsBareBlockNamesToMinecraftNamespace() {
        WorldSpec world = new DslParser().parse("""
                world "Bare Blocks" {
                  min_y 0
                  height 16
                  set 0 4 0 stone
                  set 1 4 0 oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                  block custom = example_mod:polished_panel[variant=carved]
                  set 2 4 0 $custom
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:stone", chunk.get(0, 4, 0).name());
        assertEquals("minecraft:oak_stairs", chunk.get(1, 4, 0).name());
        assertEquals("example_mod:polished_panel", chunk.get(2, 4, 0).name());
    }

    @Test
    void parsesIncludedComponentsAndPlacesThem() throws Exception {
        Path components = tempDir.resolve("components");
        Files.createDirectories(components);
        Files.writeString(components.resolve("lamp-post.atf"), """
                component "lamp-post" {
                  bounds 0 0 0 to 0 2 0
                  fill 0 0 0 to 0 1 0 minecraft:dark_oak_fence
                  set 0 2 0 minecraft:lantern[hanging=true]
                }
                """);
        Path worldAtf = tempDir.resolve("world.atf");
        Files.writeString(worldAtf, """
                world "Components" {
                  min_y 0
                  height 32
                  include "components/lamp-post.atf"
                  group "plaza" {
                    origin 10 4 10
                    place lamp-post at 2 0 3
                  }
                }
                """);

        WorldSpec world = new DslParser().parse(worldAtf);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:dark_oak_fence", chunk.get(12, 4, 13).name());
        assertEquals("minecraft:dark_oak_fence", chunk.get(12, 5, 13).name());
        assertEquals("minecraft:lantern", chunk.get(12, 6, 13).name());
    }

    @Test
    void parsesConditionalsAndModuloExpressions() {
        WorldSpec world = new DslParser().parse("""
                world "Conditionals" {
                  min_y 0
                  height 32
                  repeat x 0 4 {
                    if $x%2 == 0 {
                      set $x 4 0 minecraft:quartz_block
                    } else {
                      set $x 4 0 minecraft:coal_block
                    }
                  }
                  if 0 {
                    set 5 4 0 minecraft:diamond_block
                  } else {
                    set 5 4 0 minecraft:gold_block
                  }
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:quartz_block", chunk.get(0, 4, 0).name());
        assertEquals("minecraft:coal_block", chunk.get(1, 4, 0).name());
        assertEquals("minecraft:quartz_block", chunk.get(4, 4, 0).name());
        assertEquals("minecraft:gold_block", chunk.get(5, 4, 0).name());
    }

    @Test
    void parsesSeededScatterBlocks() {
        WorldSpec first = new DslParser().parse("""
                world "Scatter" {
                  min_y 0
                  height 32
                  scatter 0 4 0 8 4 8 chance 1 1 step 4 {
                    if $x == 4 {
                      set 0 0 0 minecraft:gold_block
                    } else {
                      set 0 0 0 minecraft:iron_block
                    }
                  }
                }
                """);
        WorldSpec second = new DslParser().parse("""
                world "Scatter" {
                  min_y 0
                  height 32
                  scatter 0 4 0 8 4 8 chance 1 1 step 4 {
                    if $x == 4 {
                      set 0 0 0 minecraft:gold_block
                    } else {
                      set 0 0 0 minecraft:iron_block
                    }
                  }
                }
                """);

        var chunk = first.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:iron_block", chunk.get(0, 4, 0).name());
        assertEquals("minecraft:gold_block", chunk.get(4, 4, 4).name());
        assertEquals("minecraft:iron_block", chunk.get(8, 4, 8).name());
        assertEquals(first.chunks().get(new ChunkPos(0, 0)).get(4, 4, 4),
                second.chunks().get(new ChunkPos(0, 0)).get(4, 4, 4));
    }

    @Test
    void parsesWeightedChooseInsideScatterDeterministically() {
        WorldSpec first = new DslParser().parse("""
                world "Weighted Scatter" {
                  min_y 0
                  height 32
                  scatter 0 4 0 8 4 8 chance 1 1 step 4 {
                    choose seed 9 {
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
        WorldSpec second = new DslParser().parse("""
                world "Weighted Scatter" {
                  min_y 0
                  height 32
                  scatter 0 4 0 8 4 8 chance 1 1 step 4 {
                    choose seed 9 {
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

        int gold = 0;
        int iron = 0;
        for (int z = 0; z <= 8; z += 4) {
            for (int x = 0; x <= 8; x += 4) {
                BlockState block = first.chunks().get(new ChunkPos(0, 0)).get(x, 4, z);
                assertEquals(block, second.chunks().get(new ChunkPos(0, 0)).get(x, 4, z));
                if ("minecraft:gold_block".equals(block.name())) {
                    gold++;
                } else if ("minecraft:iron_block".equals(block.name())) {
                    iron++;
                }
            }
        }
        assertEquals(9, gold + iron);
    }

    @Test
    void parsesDeterministicTerrainColumns() {
        WorldSpec world = new DslParser().parse("""
                world "Terrain" {
                  min_y 0
                  height 32
                  terrain 0 0 3 3 from 1 base 4 amplitude 0 seed 5 minecraft:grass_block[snowy=false] minecraft:dirt
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:dirt", chunk.get(0, 1, 0).name());
        assertEquals("minecraft:dirt", chunk.get(3, 3, 3).name());
        assertEquals("minecraft:grass_block", chunk.get(0, 4, 0).name());
        assertEquals("minecraft:air", chunk.get(0, 5, 0).name());
    }

    @Test
    void parsesSmoothTerrainColumnsDeterministically() {
        WorldSpec first = new DslParser().parse("""
                world "Smooth Terrain" {
                  min_y 0
                  height 32
                  smooth_terrain 0 0 15 15 from 1 base 4 amplitude 5 scale 8 octaves 3 seed 11 minecraft:grass_block[snowy=false] minecraft:dirt
                }
                """);
        WorldSpec second = new DslParser().parse("""
                world "Smooth Terrain" {
                  min_y 0
                  height 32
                  smooth_terrain 0 0 15 15 from 1 base 4 amplitude 5 scale 8 octaves 3 seed 11 minecraft:grass_block[snowy=false] minecraft:dirt
                }
                """);

        var firstChunk = first.chunks().get(new ChunkPos(0, 0));
        var secondChunk = second.chunks().get(new ChunkPos(0, 0));
        for (int z = 0; z <= 15; z++) {
            for (int x = 0; x <= 15; x++) {
                int topY = topY(firstChunk, x, z, 1, 10);
                assertEquals(firstChunk.get(x, topY, z), secondChunk.get(x, topY, z));
                assertEquals("minecraft:grass_block", firstChunk.get(x, topY, z).name());
                assertEquals("minecraft:air", firstChunk.get(x, topY + 1, z).name());
                if (topY > 1) {
                    assertEquals("minecraft:dirt", firstChunk.get(x, topY - 1, z).name());
                }
            }
        }
    }

    @Test
    void smoothTerrainAcceptsPaletteTopAndUnder() {
        WorldSpec world = new DslParser().parse("""
                world "Terrain Palette" {
                  min_y 0
                  height 32
                  block_palette ground_mix {
                    block minecraft:grass_block[snowy=false] weight 9
                    block minecraft:coarse_dirt weight 1
                  }
                  block_palette under_mix {
                    block minecraft:dirt weight 8
                    block minecraft:gravel weight 2
                  }
                  smooth_terrain 0 0 15 15 from 1 base 4 amplitude 4 scale 8 octaves 2 seed 11 ground_mix under_mix
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        Set<String> topNames = new HashSet<>();
        Set<String> underNames = new HashSet<>();
        for (int z = 0; z <= 15; z++) {
            for (int x = 0; x <= 15; x++) {
                int topY = topY(chunk, x, z, 1, 10);
                topNames.add(chunk.get(x, topY, z).name());
                assertEquals("minecraft:air", chunk.get(x, topY + 1, z).name());
                if (topY > 1) {
                    underNames.add(chunk.get(x, topY - 1, z).name());
                }
            }
        }
        assertTrue(topNames.contains("minecraft:grass_block"));
        assertTrue(topNames.stream().allMatch(n -> n.equals("minecraft:grass_block") || n.equals("minecraft:coarse_dirt")));
        assertTrue(underNames.stream().allMatch(n -> n.equals("minecraft:dirt") || n.equals("minecraft:gravel")));
    }

    @Test
    void fillAcceptsPaletteName() {
        WorldSpec world = new DslParser().parse("""
                world "Fill Palette" {
                  min_y 0
                  height 16
                  block_palette floor_mix {
                    block minecraft:stone weight 1
                    block minecraft:andesite weight 1
                  }
                  fill 0 2 0 to 15 2 15 floor_mix seed 9
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        Set<String> names = new HashSet<>();
        for (int z = 0; z <= 15; z++) {
            for (int x = 0; x <= 15; x++) {
                names.add(chunk.get(x, 2, z).name());
            }
        }
        assertEquals(Set.of("minecraft:stone", "minecraft:andesite"), names);
    }

    @Test
    void rejectsPaletteNameWhereSingleBlockRequired() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new DslParser().parse("""
                world "Bad Palette" {
                  min_y 0
                  height 16
                  block_palette floor_mix {
                    block minecraft:stone weight 1
                  }
                  set 1 2 1 floor_mix
                }
                """));
        assertTrue(error.getMessage().contains("block_palette"));
    }

    @Test
    void parsesLineCommandsWithRotation() {
        WorldSpec world = new DslParser().parse("""
                world "Line" {
                  min_y 0
                  height 32
                  at 10 0 10 rotate 90 {
                    line 0 4 0 4 4 0 minecraft:chain[axis=x,waterlogged=false]
                  }
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:chain", chunk.get(10, 4, 10).name());
        assertEquals("minecraft:chain", chunk.get(10, 4, 14).name());
        assertEquals("z", chunk.get(10, 4, 14).properties().get("axis"));
    }

    @Test
    void parsesWallsWithFacingAndRotation() {
        WorldSpec world = new DslParser().parse("""
                world "Walls" {
                  min_y 0
                  height 32
                  wall 1 4 1 3 2 east minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                  at 10 0 10 rotate 90 {
                    wall 0 4 0 2 1 north minecraft:glass_pane[east=false,north=true,south=false,waterlogged=false,west=false]
                  }
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        BlockState stair = chunk.get(1, 4, 1);
        assertEquals("minecraft:oak_stairs", stair.name());
        assertEquals("east", stair.properties().get("facing"));
        assertEquals("minecraft:oak_stairs", chunk.get(1, 5, 3).name());
        assertEquals("minecraft:air", chunk.get(2, 4, 1).name());
        BlockState pane = chunk.get(10, 4, 11);
        assertEquals("minecraft:glass_pane", pane.name());
        assertEquals("true", pane.properties().get("east"));
        assertEquals("false", pane.properties().get("north"));
    }

    @Test
    void parsesDoorShortcutWithRotation() {
        WorldSpec world = new DslParser().parse("""
                world "Doors" {
                  min_y 0
                  height 32
                  block entry = minecraft:oak_door
                  door 1 4 1 south left closed $entry
                  at 10 0 10 rotate 90 {
                    door 0 4 0 north right open minecraft:birch_door
                  }
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        BlockState lower = chunk.get(1, 4, 1);
        BlockState upper = chunk.get(1, 5, 1);
        assertEquals("minecraft:oak_door", lower.name());
        assertEquals("lower", lower.properties().get("half"));
        assertEquals("south", lower.properties().get("facing"));
        assertEquals("false", lower.properties().get("open"));
        assertEquals("upper", upper.properties().get("half"));
        BlockState rotated = chunk.get(10, 4, 10);
        assertEquals("minecraft:birch_door", rotated.name());
        assertEquals("east", rotated.properties().get("facing"));
        assertEquals("right", rotated.properties().get("hinge"));
        assertEquals("true", rotated.properties().get("open"));
    }

    @Test
    void parsesBedShortcutWithRotation() {
        WorldSpec world = new DslParser().parse("""
                world "Beds" {
                  min_y 0
                  height 32
                  block guest_bed = minecraft:red_bed
                  bed 1 4 1 east $guest_bed
                  at 10 0 10 rotate 90 {
                    bed 0 4 0 north minecraft:blue_bed
                  }
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        BlockState foot = chunk.get(1, 4, 1);
        BlockState head = chunk.get(2, 4, 1);
        assertEquals("minecraft:red_bed", foot.name());
        assertEquals("foot", foot.properties().get("part"));
        assertEquals("east", foot.properties().get("facing"));
        assertEquals("head", head.properties().get("part"));
        assertEquals("false", head.properties().get("occupied"));
        BlockState rotatedFoot = chunk.get(10, 4, 10);
        BlockState rotatedHead = chunk.get(11, 4, 10);
        assertEquals("minecraft:blue_bed", rotatedFoot.name());
        assertEquals("east", rotatedFoot.properties().get("facing"));
        assertEquals("head", rotatedHead.properties().get("part"));
    }

    @Test
    void parsesSurfaceScatterOnGeneratedTerrain() {
        WorldSpec world = new DslParser().parse("""
                world "Surface Scatter" {
                  min_y 0
                  height 32
                  terrain 0 0 3 3 from 1 base 4 amplitude 0 seed 5 minecraft:grass_block[snowy=false] minecraft:dirt
                  surface_scatter 0 0 3 3 from 1 to 10 chance 1 1 step 2 {
                    if $surface_y == 4 {
                      set 0 0 0 minecraft:poppy
                    }
                  }
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:grass_block", chunk.get(0, 4, 0).name());
        assertEquals("minecraft:poppy", chunk.get(0, 5, 0).name());
        assertEquals("minecraft:poppy", chunk.get(2, 5, 2).name());
        assertEquals("minecraft:air", chunk.get(1, 5, 1).name());
    }

    private static int topY(MutableWorld.ChunkBlocks chunk, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (!"minecraft:air".equals(chunk.get(x, y, z).name())) {
                return y;
            }
        }
        throw new AssertionError("No terrain column at " + x + "," + z);
    }

    @Test
    void resolvesBlockAliasesAcrossTemplatesAndRotation() {
        WorldSpec world = new DslParser().parse("""
                world "Aliases" {
                  min_y 0
                  height 32
                  block seat = minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]

                  template "chair" {
                    set 0 0 0 $seat
                  }

                  use "chair" at 1 2 3 rotate 180
                }
                """);

        BlockState state = world.chunks().get(new ChunkPos(0, 0)).get(1, 2, 3);
        assertEquals("minecraft:oak_stairs", state.name());
        assertEquals("south", state.properties().get("facing"));
    }

    @Test
    void useOverridesVariablesAndAliasesWithoutLeaking() {
        WorldSpec world = new DslParser().parse("""
                world "Overrides" {
                  min_y 0
                  height 32
                  block material = minecraft:oak_planks

                  template "beam" {
                    fill 0 0 0 $length 0 0 $material
                  }

                  use "beam" at 0 1 0 with {
                    let length = 2
                    block material = minecraft:birch_planks
                  }

                  let length = 1
                  use "beam" at 0 2 0
                }
                """);

        assertEquals("minecraft:birch_planks", world.chunks().get(new ChunkPos(0, 0)).get(2, 1, 0).name());
        assertEquals("minecraft:oak_planks", world.chunks().get(new ChunkPos(0, 0)).get(1, 2, 0).name());
        assertEquals("minecraft:air", world.chunks().get(new ChunkPos(0, 0)).get(2, 2, 0).name());
    }

    @Test
    void parameterizedTemplatesBindArgumentsLocally() {
        WorldSpec world = new DslParser().parse("""
                world "Parameterized Modules" {
                  min_y 0
                  height 32

                  template "shelf" params width layers {
                    repeat y 0 $layers-1 {
                      fill 0 $y 0 $width-1 $y 0 minecraft:bookshelf
                    }
                  }

                  let width = 9
                  use "shelf" at 1 4 1 args 3 2
                  use "shelf" at 1 8 1 args 2 1 rotate 90
                }
                """);

        assertEquals("minecraft:bookshelf", world.chunks().get(new ChunkPos(0, 0)).get(1, 4, 1).name());
        assertEquals("minecraft:bookshelf", world.chunks().get(new ChunkPos(0, 0)).get(3, 5, 1).name());
        assertEquals("minecraft:air", world.chunks().get(new ChunkPos(0, 0)).get(4, 4, 1).name());
        assertEquals("minecraft:bookshelf", world.chunks().get(new ChunkPos(0, 0)).get(1, 8, 2).name());
        assertEquals("minecraft:air", world.chunks().get(new ChunkPos(0, 0)).get(1, 8, 3).name());
    }

    @Test
    void blockAliasesCanReferenceExistingAliases() {
        WorldSpec world = new DslParser().parse("""
                world "Alias Chains" {
                  min_y 0
                  height 32
                  block base = minecraft:red_carpet
                  block accent = $base
                  set 1 2 3 $accent
                }
                """);

        assertEquals("minecraft:red_carpet", world.chunks().get(new ChunkPos(0, 0)).get(1, 2, 3).name());
    }


    @Test
    void placesTemplatesWithRelativeOffsets() {
        WorldSpec world = new DslParser().parse("""
                world "Modules" {
                  min_y 0
                  height 32

                  template "chair" {
                    set 0 0 0 minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                    set 0 1 1 minecraft:oak_sign[rotation=0,waterlogged=false]
                  }

                  at 10 5 10 {
                    use "chair" at 2 0 3
                  }
                }
                """);

        assertEquals("minecraft:oak_stairs",
                world.chunks().get(new ChunkPos(0, 0)).get(12, 5, 13).name());
        assertEquals("minecraft:oak_sign",
                world.chunks().get(new ChunkPos(0, 0)).get(12, 6, 14).name());
    }

    @Test
    void rotatesTemplateCoordinatesAndBlockStates() {
        WorldSpec world = new DslParser().parse("""
                world "Rotated" {
                  min_y 0
                  height 32

                  template "seat" {
                    set 1 0 0 minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                    set 0 0 -1 minecraft:glass_pane[east=false,north=true,south=false,waterlogged=false,west=false]
                    set 0 1 0 minecraft:oak_sign[rotation=0,waterlogged=false]
                  }

                  use "seat" at 10 5 10 rotate 90
                }
                """);

        BlockState stair = world.chunks().get(new ChunkPos(0, 0)).get(10, 5, 11);
        assertEquals("east", stair.properties().get("facing"));
        BlockState pane = world.chunks().get(new ChunkPos(0, 0)).get(11, 5, 10);
        assertEquals("true", pane.properties().get("east"));
        assertEquals("false", pane.properties().get("north"));
        BlockState sign = world.chunks().get(new ChunkPos(0, 0)).get(10, 6, 10);
        assertEquals("4", sign.properties().get("rotation"));
    }


    @Test
    void parsesBlockEntityData() {
        WorldSpec world = new DslParser().parse("""
                world "Block Entity" {
                  min_y 0
                  height 32
                  set 1 2 3 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 1 2 3 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Supplies\\"}"
                    float LidAngle 0.5
                    double ExactValue 1.25
                    byte_array Flags { 1 0 1 }
                    int_array Color { 255 128 64 }
                    long_array Seeds { 123456789 987654321 }
                    list Items compound { }
                    list Empty end { }
                  }
                }
                """);

        var entity = world.blockEntities().get(new ChunkPos(0, 0))
                .get(new WorldSpec.BlockPos(1, 2, 3));
        assertEquals("minecraft:chest", entity.id());
        assertEquals(new NbtValue.StringValue("{\"text\":\"Supplies\"}"), entity.data().get("CustomName"));
        assertEquals(new NbtValue.FloatValue(0.5f), entity.data().get("LidAngle"));
        assertEquals(new NbtValue.DoubleValue(1.25d), entity.data().get("ExactValue"));
        assertEquals(new NbtValue.ListValue(0, java.util.List.of()), entity.data().get("Empty"));
    }

    @Test
    void mergesBlockEntityData() {
        WorldSpec world = new DslParser().parse("""
                world "Merged Block Entity" {
                  min_y 0
                  height 32
                  block_entity 1 2 3 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Supplies\\"}"
                    byte Keep 1
                    item Items slot 0 minecraft:torch count 16
                    compound display {
                      string Name "{\\"text\\":\\"Cache\\"}"
                      list Lore string { "first layer" }
                    }
                  }
                  merge_block_entity 1 2 3 minecraft:chest {
                    item Items slot 1 minecraft:bread count 8
                    byte Keep 2
                    compound display {
                      list Lore string { "second layer" }
                      int color 255
                    }
                  }
                }
                """);

        var entity = world.blockEntities().get(new ChunkPos(0, 0))
                .get(new WorldSpec.BlockPos(1, 2, 3));
        assertEquals("minecraft:chest", entity.id());
        assertEquals(new NbtValue.StringValue("{\"text\":\"Supplies\"}"), entity.data().get("CustomName"));
        assertEquals(new NbtValue.ByteValue(2), entity.data().get("Keep"));
        NbtValue.ListValue items = (NbtValue.ListValue) entity.data().get("Items");
        assertEquals(2, items.values().size());
        NbtValue.CompoundValue firstItem = (NbtValue.CompoundValue) items.values().get(0);
        assertEquals(new NbtValue.StringValue("minecraft:torch"), firstItem.values().get("id"));
        NbtValue.CompoundValue secondItem = (NbtValue.CompoundValue) items.values().get(1);
        assertEquals(new NbtValue.StringValue("minecraft:bread"), secondItem.values().get("id"));
        NbtValue.CompoundValue display = (NbtValue.CompoundValue) entity.data().get("display");
        assertEquals(new NbtValue.StringValue("{\"text\":\"Cache\"}"), display.values().get("Name"));
        assertEquals(new NbtValue.IntValue(255), display.values().get("color"));
        NbtValue.ListValue lore = (NbtValue.ListValue) display.values().get("Lore");
        assertEquals(2, lore.values().size());
        assertEquals(new NbtValue.StringValue("first layer"), lore.values().get(0));
        assertEquals(new NbtValue.StringValue("second layer"), lore.values().get(1));
    }

    @Test
    void parsesSignTextShortcut() {
        WorldSpec world = new DslParser().parse("""
                world "Sign Text" {
                  min_y 0
                  height 32
                  set 1 4 1 minecraft:oak_sign[rotation=0,waterlogged=false]
                  block_entity 1 4 1 minecraft:sign {
                    byte Keep 1
                  }
                  sign_text 1 4 1 front "Welcome" "to" "anvil-tf" "!"
                  sign_text 1 4 1 back "Back" "side" "" ""
                }
                """);

        var entity = world.blockEntities().get(new ChunkPos(0, 0))
                .get(new WorldSpec.BlockPos(1, 4, 1));
        assertEquals("minecraft:sign", entity.id());
        assertEquals(new NbtValue.ByteValue(1), entity.data().get("Keep"));
        NbtValue.CompoundValue front = (NbtValue.CompoundValue) entity.data().get("front_text");
        NbtValue.ListValue messages = (NbtValue.ListValue) front.values().get("messages");
        assertEquals(new NbtValue.StringValue("Welcome"), messages.values().get(0));
        assertEquals(new NbtValue.StringValue("black"), front.values().get("color"));
        assertEquals(new NbtValue.ByteValue(0), front.values().get("has_glowing_text"));
        NbtValue.CompoundValue back = (NbtValue.CompoundValue) entity.data().get("back_text");
        NbtValue.ListValue backMessages = (NbtValue.ListValue) back.values().get("messages");
        assertEquals(new NbtValue.StringValue("Back"), backMessages.values().get(0));
    }

    @Test
    void parsesItemStackShortcutsInNbtBodies() {
        WorldSpec world = new DslParser().parse("""
                world "Interior Items" {
                  min_y 0
                  height 32
                  set 1 2 3 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 1 2 3 chest {
                    item Items slot 0 bread count 8
                    item Items slot 4 diamond {
                      string display_name "keepsake"
                    }
                  }
                  entity 2.5 2 3.5 item_frame {
                    item_stack Item map count 1
                  }
                }
                """);

        var blockEntity = world.blockEntities().get(new ChunkPos(0, 0))
                .get(new WorldSpec.BlockPos(1, 2, 3));
        NbtValue.ListValue items = (NbtValue.ListValue) blockEntity.data().get("Items");
        assertEquals(2, items.values().size());
        NbtValue.CompoundValue first = (NbtValue.CompoundValue) items.values().get(0);
        assertEquals(new NbtValue.ByteValue(0), first.values().get("Slot"));
        assertEquals(new NbtValue.StringValue("minecraft:bread"), first.values().get("id"));
        assertEquals(new NbtValue.ByteValue(8), first.values().get("Count"));
        NbtValue.CompoundValue second = (NbtValue.CompoundValue) items.values().get(1);
        assertEquals(new NbtValue.ByteValue(4), second.values().get("Slot"));
        assertEquals(new NbtValue.CompoundValue(java.util.Map.of(
                "display_name", new NbtValue.StringValue("keepsake"))), second.values().get("tag"));

        var entity = world.entities().get(new ChunkPos(0, 0)).get(0);
        NbtValue.CompoundValue heldItem = (NbtValue.CompoundValue) entity.data().get("Item");
        assertEquals(new NbtValue.StringValue("minecraft:map"), heldItem.values().get("id"));
    }

    @Test
    void parsesBiomeCommandsAndAliases() {
        WorldSpec world = new DslParser().parse("""
                world "Biomes" {
                  min_y 0
                  height 32
                  default_biome forest
                  biome_alias warm = desert
                  set 0 0 0 minecraft:stone
                  fill_biome 0 0 0 15 15 15 $warm
                }
                """);

        assertEquals("minecraft:forest", world.defaultBiome());
        assertEquals("minecraft:desert", world.biomes().get(new ChunkPos(0, 0)).get(0, 0, 0));
        assertEquals("minecraft:desert", world.biomes().get(new ChunkPos(0, 0)).get(3, 12, 3));
    }

    @Test
    void parsesEntitiesWithDecimalPositionsAndData() {
        WorldSpec world = new DslParser().parse("""
                world "Entities" {
                  min_y 0
                  height 32
                  set 0 0 0 minecraft:stone
                  entity 1.5 2 3.5 armor_stand {
                    byte Invisible 1
                    list Rotation float { 90 0 }
                  }
                }
                """);

        var entity = world.entities().get(new ChunkPos(0, 0)).get(0);
        assertEquals("minecraft:armor_stand", entity.id());
        assertEquals(1.5d, entity.x(), 0.0d);
        assertEquals(new NbtValue.ByteValue(1), entity.data().get("Invisible"));
    }

    @Test
    void parsesRoundedShapeCommandsWithAliasesAndRotation() {
        WorldSpec world = new DslParser().parse("""
                world "Shapes" {
                  min_y 0
                  height 32
                  block wall = minecraft:stone_bricks
                  block glass = minecraft:glass

                  sphere 4 8 4 2 $glass
                  hollow_sphere 12 8 4 2 $glass minecraft:air
                  at 20 0 4 rotate 90 {
                    hollow_cylinder 1 4 0 2 8 $wall minecraft:air
                  }
                }
                """);

        assertEquals("minecraft:glass", world.chunks().get(new ChunkPos(0, 0)).get(4, 8, 4).name());
        assertEquals("minecraft:air", world.chunks().get(new ChunkPos(0, 0)).get(12, 8, 4).name());
        assertEquals("minecraft:stone_bricks", world.chunks().get(new ChunkPos(1, 0)).get(4, 4, 5).name());
        assertEquals("minecraft:air", world.chunks().get(new ChunkPos(1, 0)).get(4, 6, 5).name());
    }

    @Test
    void parsesPatternBlocksWithLayersAliasesAndRotation() {
        WorldSpec world = new DslParser().parse("""
                world "Patterns" {
                  min_y 0
                  height 32
                  block floor = minecraft:spruce_planks

                  pattern 2 3 4 {
                    block F = $floor
                    block G = minecraft:gold_block
                    row "FG."
                    row ".F."
                    layer
                    row "..G"
                  }

                  at 10 0 10 rotate 90 {
                    pattern 0 5 0 {
                      block S = minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                      row "S."
                      row ".S"
                    }
                  }
                }
                """);

        assertEquals("minecraft:spruce_planks", world.chunks().get(new ChunkPos(0, 0)).get(2, 3, 4).name());
        assertEquals("minecraft:gold_block", world.chunks().get(new ChunkPos(0, 0)).get(3, 3, 4).name());
        assertEquals("minecraft:air", world.chunks().get(new ChunkPos(0, 0)).get(4, 3, 4).name());
        assertEquals("minecraft:gold_block", world.chunks().get(new ChunkPos(0, 0)).get(4, 4, 4).name());
        BlockState stair = world.chunks().get(new ChunkPos(0, 0)).get(9, 5, 11);
        assertEquals("minecraft:oak_stairs", stair.name());
        assertEquals("east", stair.properties().get("facing"));
    }

    @Test
    void reusesPatternTemplatesWithScopedOverrides() {
        WorldSpec world = new DslParser().parse("""
                world "Pattern Templates" {
                  min_y 0
                  height 32

                  pattern_template "motif" {
                    block A = $accent
                    row "AA"
                    layer
                    row ".A"
                  }

                  use_pattern "motif" at 2 3 4 with {
                    block accent = minecraft:gold_block
                  }

                  at 10 0 10 rotate 90 {
                    use_pattern "motif" at 0 5 0 with {
                      block accent = minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                    }
                  }
                }
                """);

        assertEquals("minecraft:gold_block", world.chunks().get(new ChunkPos(0, 0)).get(2, 3, 4).name());
        assertEquals("minecraft:gold_block", world.chunks().get(new ChunkPos(0, 0)).get(3, 3, 4).name());
        assertEquals("minecraft:gold_block", world.chunks().get(new ChunkPos(0, 0)).get(3, 4, 4).name());
        BlockState stair = world.chunks().get(new ChunkPos(0, 0)).get(10, 5, 10);
        assertEquals("minecraft:oak_stairs", stair.name());
        assertEquals("east", stair.properties().get("facing"));
        assertEquals("minecraft:oak_stairs", world.chunks().get(new ChunkPos(0, 0)).get(10, 6, 11).name());
    }

    @Test
    void parsesDeterministicBlockPaletteFills() {
        String source = """
                world "Palette Fill" {
                  min_y 0
                  height 32
                  block_palette road {
                    block minecraft:blackstone weight 2
                    block minecraft:polished_blackstone
                  }
                  fill_palette 0 4 0 15 4 15 road seed 42
                }
                """;

        WorldSpec first = new DslParser().parse(source);
        WorldSpec second = new DslParser().parse(source);
        var firstChunk = first.chunks().get(new ChunkPos(0, 0));
        var secondChunk = second.chunks().get(new ChunkPos(0, 0));
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(firstChunk.get(x, 4, z), secondChunk.get(x, 4, z));
                names.add(firstChunk.get(x, 4, z).name());
            }
        }

        assertEquals(java.util.Set.of("minecraft:blackstone", "minecraft:polished_blackstone"), names);
    }

    @Test
    void parsesReplaceCommands() {
        WorldSpec world = new DslParser().parse("""
                world "Replace" {
                  min_y 0
                  height 32
                  block wall = minecraft:stone_bricks
                  fill 0 4 0 15 4 15 $wall
                  replace 4 4 4 7 4 7 $wall minecraft:air
                  replace 8 4 8 10 4 10 $wall minecraft:mossy_stone_bricks
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:stone_bricks", chunk.get(3, 4, 3).name());
        assertEquals("minecraft:air", chunk.get(4, 4, 4).name());
        assertEquals("minecraft:mossy_stone_bricks", chunk.get(8, 4, 8).name());
    }

    @Test
    void parsesDeterministicReplacePaletteCommands() {
        String source = """
                world "Replace Palette" {
                  min_y 0
                  height 32
                  block_palette weathered {
                    block minecraft:stone_bricks weight 3
                    block minecraft:cracked_stone_bricks
                    block minecraft:mossy_stone_bricks
                  }
                  fill 0 4 0 15 4 15 minecraft:stone_bricks
                  set 0 4 0 minecraft:gold_block
                  replace_palette 0 4 0 15 4 15 minecraft:stone_bricks weathered seed 7
                }
                """;

        WorldSpec first = new DslParser().parse(source);
        WorldSpec second = new DslParser().parse(source);
        var firstChunk = first.chunks().get(new ChunkPos(0, 0));
        var secondChunk = second.chunks().get(new ChunkPos(0, 0));
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(firstChunk.get(x, 4, z), secondChunk.get(x, 4, z));
                names.add(firstChunk.get(x, 4, z).name());
            }
        }

        assertEquals("minecraft:gold_block", firstChunk.get(0, 4, 0).name());
        assertEquals(java.util.Set.of(
                "minecraft:gold_block",
                "minecraft:stone_bricks",
                "minecraft:cracked_stone_bricks",
                "minecraft:mossy_stone_bricks"), names);
    }

    @Test
    void parsesCopyCommandsWithRotationAndBlockEntities() {
        WorldSpec world = new DslParser().parse("""
                world "Copy" {
                  min_y 0
                  height 32
                  set 1 4 0 minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
                  set 0 4 1 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 0 4 1 minecraft:chest {
                    string CustomName "{\\"text\\":\\"Copied\\"}"
                    list Items compound { }
                  }
                  copy 0 4 0 1 4 1 to 10 4 10 rotate 90 skip_air
                }
                """);

        BlockState stair = world.chunks().get(new ChunkPos(0, 0)).get(10, 4, 11);
        assertEquals("minecraft:oak_stairs", stair.name());
        assertEquals("east", stair.properties().get("facing"));
        assertEquals("minecraft:chest", world.chunks().get(new ChunkPos(0, 0)).get(9, 4, 10).name());
        assertEquals("minecraft:chest", world.blockEntities().get(new ChunkPos(0, 0))
                .get(new WorldSpec.BlockPos(9, 4, 10)).id());
    }

    @Test
    void copyCanIncludeEntitiesWhenRequested() {
        WorldSpec world = new DslParser().parse("""
                world "Copy Entities" {
                  min_y 0
                  height 32
                  entity 0.5 4 1.5 minecraft:armor_stand {
                    byte Marker 1
                    list Rotation float { 45.0 0.0 }
                    int_array UUID { 1 2 3 4 }
                  }
                  copy 0 4 0 1 4 1 to 10 4 10 rotate 90 skip_air with_entities
                }
                """);

        var entities = world.entities().get(new ChunkPos(0, 0));
        assertEquals(2, entities.size());
        assertEquals(8.5, entities.get(1).x());
        assertEquals(10.5, entities.get(1).z());
        assertEquals(false, entities.get(1).data().containsKey("UUID"));
        NbtValue.ListValue rotation = (NbtValue.ListValue) entities.get(1).data().get("Rotation");
        assertEquals(new NbtValue.FloatValue(135.0f), rotation.values().get(0));
    }

    @Test
    void copyCanMirrorCoordinatesAndBlockStates() {
        WorldSpec world = new DslParser().parse("""
                world "Mirror Copy" {
                  min_y 0
                  height 32
                  set 0 4 0 minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=false]
                  set 2 4 0 minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]
                  copy 0 4 0 2 4 0 to 10 4 10 mirror_x skip_air
                }
                """);

        var chunk = world.chunks().get(new ChunkPos(0, 0));
        BlockState stair = chunk.get(12, 4, 10);
        assertEquals("west", stair.properties().get("facing"));
        assertEquals("inner_right", stair.properties().get("shape"));
        BlockState door = chunk.get(10, 4, 10);
        assertEquals("west", door.properties().get("facing"));
        assertEquals("right", door.properties().get("hinge"));
    }

    @Test
    void copyCanIncludeBiomesWhenRequested() {
        WorldSpec world = new DslParser().parse("""
                world "Copy Biomes" {
                  min_y 0
                  height 32
                  default_biome minecraft:plains
                  fill_biome 0 4 0 7 7 7 minecraft:flower_forest
                  copy 0 4 0 7 7 7 to 20 4 20 skip_air with_biomes
                }
                """);

        assertEquals("minecraft:flower_forest", world.biomes().get(new ChunkPos(1, 1)).get(1, 4, 1));
        assertEquals("minecraft:plains", world.biomes().get(new ChunkPos(1, 1)).get(1, 8, 1));
    }

    @Test
    void includesModulesRelativeToInputFile() throws Exception {
        Path moduleDir = Files.createDirectories(tempDir.resolve("modules"));
        Files.writeString(moduleDir.resolve("furniture.atf"), """
                template "lamp" {
                  set 0 0 0 minecraft:lantern[hanging=false,waterlogged=false]
                }
                """);
        Path worldFile = tempDir.resolve("world.atf");
        Files.writeString(worldFile, """
                world "Included" {
                  min_y 0
                  height 32
                  include "modules/furniture.atf"
                  use "lamp" at 3 4 5
                }
                """);

        WorldSpec world = new DslParser().parse(worldFile);

        assertEquals("minecraft:lantern",
                world.chunks().get(new ChunkPos(0, 0)).get(3, 4, 5).name());
    }

    @Test
    void parsesLargeDistrictExample() throws Exception {
        WorldSpec world = new DslParser().parse(Path.of("examples/large-district.atf"));

        assertEquals("Large District Demo", world.name());
        assertEquals("minecraft:stone", world.chunks().get(new ChunkPos(0, 0)).get(0, 4, 0).name());
        assertEquals("minecraft:black_concrete", world.chunks().get(new ChunkPos(0, 0)).get(0, 6, 0).name());
        assertEquals("minecraft:lantern", world.chunks().get(new ChunkPos(-8, -8)).get(8, 11, 8).name());
    }
}
