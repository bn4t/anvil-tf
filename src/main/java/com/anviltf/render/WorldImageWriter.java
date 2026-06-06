package com.anviltf.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.TreeSet;

/**
 * Renders a {@link RenderGrid} to a PNG a multimodal model can view. The only class touching
 * {@code java.awt} / {@code javax.imageio}, keeping the rest of the codebase headless-safe.
 *
 * <p>Air cells are left transparent so holes read as background. The longest side is clamped to
 * {@code maxPixels}: scale is auto-picked when not specified, and if a 1px/block render still overflows
 * the cap the grid is downsampled (every k-th block), which is reported back to the caller.
 */
public final class WorldImageWriter {
    private static final int GRIDLINE_ARGB = 0x40000000;

    public WriteResult write(RenderGrid grid, Path out, int scale, int maxPixels) throws IOException {
        System.setProperty("java.awt.headless", "true");
        int rows = grid.rows();
        int cols = grid.cols();
        if (rows == 0 || cols == 0) {
            throw new IllegalArgumentException("nothing to render: empty world view");
        }
        int max = Math.max(1, maxPixels);
        int longest = Math.max(rows, cols);
        int effScale = scale > 0 ? scale : Math.max(1, Math.min(16, max / longest));
        int sample = 1;
        while ((int) Math.ceil((double) longest / sample) * effScale > max) {
            sample++;
        }

        int outRows = (int) Math.ceil((double) rows / sample);
        int outCols = (int) Math.ceil((double) cols / sample);
        int width = outCols * effScale;
        int height = outRows * effScale;

        BlockGlyphs glyphs = new BlockGlyphs(present(grid));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int oy = 0; oy < outRows; oy++) {
            int r = Math.min(oy * sample, rows - 1);
            for (int ox = 0; ox < outCols; ox++) {
                int c = Math.min(ox * sample, cols - 1);
                fillBlock(image, ox * effScale, oy * effScale, effScale, glyphs.color(grid.cells()[r][c]));
            }
        }
        if (sample == 1 && effScale >= 3) {
            drawChunkColumns(image, grid, effScale, height);
        }
        ImageIO.write(image, "png", out.toFile());

        return new WriteResult(width, height, effScale, sample, sample > 1, legend(grid, glyphs));
    }

    private static void fillBlock(BufferedImage image, int x0, int y0, int size, int argb) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x0 + x, y0 + y, argb);
            }
        }
    }

    /** Faint vertical lines at chunk boundaries (world X/Z divisible by 16) to aid orientation. */
    private static void drawChunkColumns(BufferedImage image, RenderGrid grid, int scale, int height) {
        for (int c = 1; c < grid.cols(); c++) {
            if (Math.floorMod(grid.colLeft() + c, 16) == 0) {
                int x = c * scale;
                for (int y = 0; y < height; y++) {
                    image.setRGB(x, y, GRIDLINE_ARGB);
                }
            }
        }
    }

    private static TreeSet<String> present(RenderGrid grid) {
        TreeSet<String> names = new TreeSet<>();
        for (String[] row : grid.cells()) {
            for (String cell : row) {
                names.add(cell);
            }
        }
        return names;
    }

    private static String legend(RenderGrid grid, BlockGlyphs glyphs) {
        StringBuilder out = new StringBuilder("Legend (color RRGGBB -> block):\n");
        for (String name : glyphs.sortedNames()) {
            out.append("  ").append(String.format("%06X", glyphs.color(name) & 0xFFFFFF))
                    .append(' ').append(name).append('\n');
        }
        return out.toString();
    }

    public record WriteResult(int width, int height, int scale, int sample, boolean downsampled, String legend) {
    }
}
