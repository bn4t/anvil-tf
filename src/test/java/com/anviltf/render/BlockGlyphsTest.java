package com.anviltf.render;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockGlyphsTest {

    @Test
    void curatedMappingIsDeterministicAcrossInstances() {
        BlockGlyphs a = new BlockGlyphs(List.of("minecraft:stone"));
        BlockGlyphs b = new BlockGlyphs(List.of("minecraft:stone", "minecraft:water"));

        assertEquals(a.glyph("minecraft:stone"), b.glyph("minecraft:stone"));
        assertEquals(a.color("minecraft:stone"), b.color("minecraft:stone"));
        assertEquals('#', a.glyph("minecraft:stone"));
        assertEquals('~', b.glyph("minecraft:water"));
    }

    @Test
    void airIsSpaceAndTransparent() {
        BlockGlyphs glyphs = new BlockGlyphs(Set.of());

        assertEquals(' ', glyphs.glyph("minecraft:air"));
        assertEquals(0, glyphs.color("minecraft:air") >>> 24, "air alpha is zero (transparent)");
    }

    @Test
    void distinctNonCuratedBlocksGetDistinctGlyphs() {
        List<String> names = List.of(
                "minecraft:beacon", "minecraft:sponge", "minecraft:tnt", "minecraft:bookshelf");
        BlockGlyphs glyphs = new BlockGlyphs(names);

        Set<Character> seen = new HashSet<>();
        for (String name : names) {
            char glyph = glyphs.glyph(name);
            assertNotEquals(' ', glyph);
            assertTrue(seen.add(glyph), "glyph for " + name + " collided");
        }
    }

    @Test
    void colorsAreOpaqueForNonAir() {
        BlockGlyphs glyphs = new BlockGlyphs(List.of("minecraft:beacon"));
        assertEquals(0xFF, glyphs.color("minecraft:beacon") >>> 24);
    }

    @Test
    void legendListsPresentNamesSortedWithoutAir() {
        BlockGlyphs glyphs = new BlockGlyphs(List.of("minecraft:stone", "minecraft:air", "minecraft:dirt"));
        assertEquals(List.of("minecraft:dirt", "minecraft:stone"), glyphs.sortedNames());
    }
}
