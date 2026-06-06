package com.anviltf.render;

/**
 * A 2-D grid of block/biome names extracted from a world for rendering.
 *
 * <p>Each cell holds a normalized resource name; {@code minecraft:air} represents an empty cell.
 * Both the ASCII and PNG emitters consume only this record, so the two views can never disagree.
 * The axis fields are <em>directional</em> — they give the world coordinate at each edge of the image
 * ({@code rowTop} is the world coordinate of row 0, {@code colLeft} of column 0) — because top-down
 * views run rows north-to-south (increasing Z) while vertical slices run rows top-to-bottom
 * (decreasing Y). Columns always run left-to-right with increasing X or Z.
 */
public record RenderGrid(
        String[][] cells,
        int rowTop,
        int rowBottom,
        int colLeft,
        int colRight,
        String rowAxisLabel,
        String colAxisLabel,
        String title) {

    /** An empty grid (no authored content); callers should report a message and write no file. */
    public static RenderGrid empty(String title) {
        return new RenderGrid(new String[0][0], 0, 0, 0, 0, "", "", title);
    }

    public int rows() {
        return cells.length;
    }

    public int cols() {
        return cells.length == 0 ? 0 : cells[0].length;
    }
}
