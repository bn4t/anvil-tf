package com.anviltf.render;

import com.anviltf.WorldSpec;
import com.anviltf.dsl.DslParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldImageWriterTest {

    @Test
    void writesPngWithExpectedDimensionsAndColors(@TempDir Path dir) throws Exception {
        WorldSpec world = new DslParser().parse("""
                world "Img" {
                  min_y 0
                  height 16
                  fill 0 1 0 3 1 3 minecraft:stone
                }
                """);
        RenderGrid grid = new WorldRenderer().top(world, WorldSpec.OVERWORLD, 1);
        Path out = dir.resolve("img.png");

        WorldImageWriter.WriteResult result = new WorldImageWriter().write(grid, out, 4, 1024);

        assertEquals(grid.cols() * 4, result.width());
        assertEquals(grid.rows() * 4, result.height());

        BufferedImage image = ImageIO.read(out.toFile());
        assertEquals(grid.cols() * 4, image.getWidth());
        assertEquals(grid.rows() * 4, image.getHeight());
        // center of stone block (0,0) -> pixel (2,2)
        assertEquals(0xFF8C8C8C, image.getRGB(2, 2));
        // an air block far from the fill is transparent
        assertEquals(0, image.getRGB(10 * 4 + 2, 10 * 4 + 2) >>> 24);
    }

    @Test
    void maxClampDownsamplesOversizedGrid(@TempDir Path dir) throws Exception {
        WorldSpec world = new DslParser().parse("""
                world "Big" {
                  min_y 0
                  height 16
                  fill 0 1 0 15 1 15 minecraft:stone
                }
                """);
        RenderGrid grid = new WorldRenderer().top(world, WorldSpec.OVERWORLD, 1);
        Path out = dir.resolve("big.png");

        WorldImageWriter.WriteResult result = new WorldImageWriter().write(grid, out, 1, 10);

        assertTrue(result.downsampled(), "expected downsampling at scale 1 with max 10");
        assertTrue(result.sample() >= 2);
        assertTrue(Math.max(result.width(), result.height()) <= 10);
    }
}
