package com.anviltf;

import com.anviltf.dsl.DslParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPlannerTest {
    @Test
    void summarizesGeneratedWorldScaleAndStorage() {
        WorldSpec world = new DslParser().parse("""
                world "Planned" {
                  min_y 0
                  height 64
                  spawn 1 8 1
                  fill 0 8 0 31 8 31 minecraft:stone
                  set 17 8 17 minecraft:air
                  fill_biome 0 0 0 31 15 31 minecraft:forest
                  set 2 9 2 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 2 9 2 minecraft:chest {
                    list Items compound { }
                  }
                  entity 5.5 9 5.5 minecraft:armor_stand {
                    byte Marker 1
                  }
                }
                """);

        WorldPlanner.Plan plan = new WorldPlanner().plan(world);

        assertEquals("Planned", plan.name());
        assertEquals(4, plan.chunks());
        assertEquals(1, plan.regions());
        assertEquals(4, plan.compactLayers());
        assertEquals(2, plan.explicitBlocks());
        assertEquals(1024, plan.nonAirBlocks());
        assertEquals(4, plan.biomeChunks());
        assertEquals(256, plan.explicitBiomes());
        assertEquals(1, plan.blockEntities());
        assertEquals(1, plan.entities());
        assertEquals(2, plan.blockStatePaletteSize());
    }

    @Test
    void rendersStableHumanReadablePlan() {
        WorldSpec world = new DslParser().parse("""
                world "Readable Plan" {
                  min_y 0
                  height 32
                  set -1 3 -1 minecraft:gold_block
                  set 32 3 32 minecraft:diamond_block
                }
                """);

        String plan = new WorldPlanner().render(world);

        assertTrue(plan.contains("Plan for world 'Readable Plan'"));
        assertTrue(plan.contains("Chunks: 2 (bounds -1,-1 to 2,2)"));
        assertTrue(plan.contains("Regions: 2 (bounds -1,-1 to 0,0)"));
        assertTrue(plan.contains("Non-air blocks: 2"));
    }

    @Test
    void includesNonBlockContentChunksInScaleSummary() {
        WorldSpec world = new DslParser().parse("""
                world "Content Only Chunks" {
                  min_y 0
                  height 64
                  fill_biome 32 0 0 32 0 0 minecraft:forest
                  block_entity 0 8 48 minecraft:chest {
                    list Items compound { }
                  }
                  entity -17.5 8.0 -17.5 minecraft:armor_stand {
                    byte Marker 1
                  }
                }
                """);

        WorldPlanner.Plan plan = new WorldPlanner().plan(world);

        assertEquals(3, plan.chunks());
        assertEquals(2, plan.regions());
        assertEquals(0, plan.nonAirBlocks());
        assertEquals(1, plan.biomeChunks());
        assertEquals(1, plan.blockEntities());
        assertEquals(1, plan.entities());
        assertEquals("-2,-2 to 2,3", plan.chunkBounds().toString());
        assertEquals("-1,-1 to 0,0", plan.regionBounds().toString());
    }
}
