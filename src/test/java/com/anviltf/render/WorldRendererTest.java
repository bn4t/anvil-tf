package com.anviltf.render;

import com.anviltf.WorldSpec;
import com.anviltf.dsl.DslParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRendererTest {

    @Test
    void topRenderShowsHolesAsAir() {
        WorldSpec world = new DslParser().parse("""
                world "Hole" {
                  min_y 0
                  height 32
                  fill 0 8 0 15 8 15 minecraft:stone
                  set 4 8 7 minecraft:air
                }
                """);

        RenderGrid grid = new WorldRenderer().top(world, WorldSpec.OVERWORLD, 8);

        assertEquals(16, grid.cols());
        assertEquals(16, grid.rows());
        // cell at x=4, z=7 -> row = z - minZ = 7, col = x - minX = 4
        assertEquals("minecraft:air", grid.cells()[7][4]);
        assertEquals("minecraft:stone", grid.cells()[0][0]);

        String ascii = new WorldRenderer().toAscii(grid);
        assertTrue(ascii.contains("minecraft:stone"), "legend lists the floor block");
        assertTrue(ascii.contains("top-down y=8"), "title names the layer");
    }

    @Test
    void autoCropsToAuthoredChunkBounds() {
        WorldSpec world = new DslParser().parse("""
                world "Spread" {
                  min_y 0
                  height 32
                  set 1 8 1 minecraft:stone
                  set 33 8 33 minecraft:stone
                }
                """);

        RenderGrid grid = new WorldRenderer().top(world, WorldSpec.OVERWORLD, 8);

        // chunks (0,0) and (2,2) -> block bounds 0..47 on each axis
        assertEquals(0, grid.colLeft());
        assertEquals(47, grid.colRight());
        assertEquals(48, grid.cols());
        assertEquals(48, grid.rows());
    }

    @Test
    void sliceTopRowIsHighestY() {
        WorldSpec world = new DslParser().parse("""
                world "Column" {
                  min_y 0
                  height 32
                  set 5 3 5 minecraft:stone
                  set 5 10 5 minecraft:stone
                }
                """);

        RenderGrid grid = new WorldRenderer().slice(world, WorldSpec.OVERWORLD, 'x', 5);

        // authored Y bounds 3..10 -> 8 rows, row 0 is y=10 (top), row 7 is y=3 (bottom)
        assertEquals(8, grid.rows());
        assertEquals(10, grid.rowTop());
        assertEquals(3, grid.rowBottom());
        assertEquals("minecraft:stone", grid.cells()[0][5]);
        assertEquals("minecraft:stone", grid.cells()[7][5]);
        assertEquals("minecraft:air", grid.cells()[1][5]);
    }

    @Test
    void dimensionSelectionIsolatesContent() {
        WorldSpec world = new DslParser().parse("""
                world "Dims" {
                  min_y 0
                  height 16
                  set 0 1 0 minecraft:grass_block
                  dimension minecraft:the_nether {
                    min_y 0
                    height 128
                    set 0 8 0 minecraft:netherrack
                  }
                }
                """);

        String nether = new WorldRenderer().toAscii(
                new WorldRenderer().top(world, WorldSpec.NETHER, 8));
        String overworld = new WorldRenderer().toAscii(
                new WorldRenderer().top(world, WorldSpec.OVERWORLD, 1));

        assertTrue(nether.contains("minecraft:netherrack"));
        assertFalse(nether.contains("minecraft:grass_block"));
        assertTrue(overworld.contains("minecraft:grass_block"));
        assertFalse(overworld.contains("minecraft:netherrack"));
    }

    @Test
    void emptyWorldRendersNothing() {
        WorldSpec world = new DslParser().parse("""
                world "Empty" {
                  min_y 0
                  height 16
                }
                """);

        RenderGrid grid = new WorldRenderer().top(world, WorldSpec.OVERWORLD, 8);

        assertEquals(0, grid.rows());
        assertTrue(new WorldRenderer().toAscii(grid).contains("empty"));
    }
}
