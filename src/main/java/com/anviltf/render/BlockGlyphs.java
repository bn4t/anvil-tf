package com.anviltf.render;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Deterministic, stable mapping from a block/biome name to an ASCII glyph and an RGB color.
 *
 * <p>Instantiated per render: pool glyphs are assigned to non-curated names in sorted order of the
 * names actually present, guaranteeing (a) distinct glyphs within one render and (b) identical output
 * on re-run. {@code minecraft:air} is always a space glyph and a fully transparent color, so holes in a
 * structure read as background.
 */
public final class BlockGlyphs {
    public static final char AIR_GLYPH = ' ';
    /** Fully transparent ARGB so air reads as background in TYPE_INT_ARGB images. */
    public static final int AIR_COLOR = 0x00000000;

    private static final String AIR = "minecraft:air";

    /** Glyphs reserved by {@link #curatedGlyph}; the fallback pool avoids all of these. */
    private static final char[] FALLBACK_POOL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@$+?<>!".toCharArray();

    private final Map<String, Character> glyphs = new HashMap<>();
    private final TreeSet<String> presentNames = new TreeSet<>();

    public BlockGlyphs(Collection<String> present) {
        for (String name : new TreeSet<>(present)) {
            if (AIR.equals(name)) {
                continue;
            }
            presentNames.add(name);
        }
        int poolIndex = 0;
        for (String name : presentNames) {
            Character curated = curatedGlyph(name);
            if (curated != null) {
                glyphs.put(name, curated);
            } else {
                glyphs.put(name, FALLBACK_POOL[poolIndex % FALLBACK_POOL.length]);
                poolIndex++;
            }
        }
    }

    /** Names present in the render, sorted, excluding air. */
    public List<String> sortedNames() {
        return List.copyOf(presentNames);
    }

    public char glyph(String name) {
        if (AIR.equals(name)) {
            return AIR_GLYPH;
        }
        Character assigned = glyphs.get(name);
        if (assigned != null) {
            return assigned;
        }
        Character curated = curatedGlyph(name);
        return curated != null ? curated : FALLBACK_POOL[Math.floorMod(name.hashCode(), FALLBACK_POOL.length)];
    }

    /** Opaque ARGB color, or {@link #AIR_COLOR} (transparent) for air. */
    public int color(String name) {
        if (AIR.equals(name)) {
            return AIR_COLOR;
        }
        Integer curated = curatedColor(name);
        int rgb = curated != null ? curated : hashColor(name);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static Character curatedGlyph(String name) {
        if (name.contains("water")) {
            return '~';
        }
        if (name.contains("lava")) {
            return '&';
        }
        if (name.contains("glass")) {
            return 'o';
        }
        if (name.contains("leaves")) {
            return '*';
        }
        if (name.endsWith("_door") || name.endsWith("_trapdoor") || name.contains("stairs") || name.contains("slab")) {
            return '/';
        }
        if (name.contains("planks") || name.endsWith("_log") || name.endsWith("_wood")) {
            return '=';
        }
        if (name.contains("wool") || name.contains("concrete") || name.contains("terracotta")) {
            return '%';
        }
        if (name.contains("sand") || name.contains("gravel")) {
            return ':';
        }
        if (name.contains("grass_block") || name.contains("dirt") || name.endsWith(":mud") || name.contains("podzol")) {
            return ',';
        }
        if (name.contains("stone") || name.contains("cobble") || name.contains("deepslate")
                || name.contains("andesite") || name.contains("granite") || name.contains("diorite")
                || name.contains("bricks")) {
            return '#';
        }
        return null;
    }

    private static Integer curatedColor(String name) {
        if (name.contains("water")) {
            return 0x3A6EA5;
        }
        if (name.contains("lava")) {
            return 0xD45A12;
        }
        if (name.contains("grass_block")) {
            return 0x6A9A3B;
        }
        if (name.endsWith(":dirt") || name.contains("coarse_dirt") || name.contains("podzol")) {
            return 0x7A5A3A;
        }
        if (name.contains("sandstone")) {
            return 0xD8CFA0;
        }
        if (name.contains("sand") || name.contains("gravel")) {
            return 0xDBD3A0;
        }
        if (name.contains("deepslate")) {
            return 0x4A4A4F;
        }
        if (name.contains("cobble")) {
            return 0x6E6E6E;
        }
        if (name.contains("stone") || name.contains("andesite") || name.contains("granite") || name.contains("diorite")) {
            return 0x8C8C8C;
        }
        if (name.contains("bricks") || name.endsWith(":brick")) {
            return 0x9A5A4A;
        }
        if (name.contains("planks")) {
            return 0xB58A4A;
        }
        if (name.endsWith("_log") || name.endsWith("_wood") || name.contains("stripped")) {
            return 0x6E5230;
        }
        if (name.contains("leaves")) {
            return 0x3E7A2E;
        }
        if (name.contains("glass")) {
            return 0xBFE6E6;
        }
        if (name.contains("snow")) {
            return 0xF0F5FF;
        }
        if (name.contains("ice")) {
            return 0x99BBE0;
        }
        if (name.contains("wool") || name.contains("concrete") || name.contains("terracotta")) {
            return 0xB05A8A;
        }
        if (name.contains("netherrack")) {
            return 0x6A2A2A;
        }
        if (name.contains("end_stone")) {
            return 0xDDDCA8;
        }
        if (name.contains("obsidian")) {
            return 0x1A1430;
        }
        if (name.contains("gold")) {
            return 0xF2D24A;
        }
        if (name.contains("iron")) {
            return 0xD8D8D8;
        }
        if (name.contains("diamond")) {
            return 0x4AE0D0;
        }
        return null;
    }

    /** Stable saturated color derived from the name hash (HSV with fixed S/V). */
    private static int hashColor(String name) {
        return hsvToRgb(Math.floorMod(name.hashCode(), 360), 0.55f, 0.85f);
    }

    private static int hsvToRgb(int hueDeg, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((hueDeg / 60f) % 2 - 1));
        float m = v - c;
        float r;
        float g;
        float b;
        switch (hueDeg / 60) {
            case 0 -> {
                r = c;
                g = x;
                b = 0;
            }
            case 1 -> {
                r = x;
                g = c;
                b = 0;
            }
            case 2 -> {
                r = 0;
                g = c;
                b = x;
            }
            case 3 -> {
                r = 0;
                g = x;
                b = c;
            }
            case 4 -> {
                r = x;
                g = 0;
                b = c;
            }
            default -> {
                r = c;
                g = 0;
                b = x;
            }
        }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }
}
