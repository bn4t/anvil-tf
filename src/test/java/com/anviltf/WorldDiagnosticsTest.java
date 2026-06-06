package com.anviltf;

import com.anviltf.dsl.DslParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldDiagnosticsTest {
    @Test
    void warnsWhenBlockEntityDoesNotMatchAnchorBlock() {
        WorldSpec world = new DslParser().parse("""
                world "Diagnostics" {
                  min_y 0
                  height 32
                  set 1 4 1 minecraft:stone
                  block_entity 1 4 1 minecraft:chest {
                    item Items slot 0 minecraft:bread count 1
                  }
                }
                """);

        List<WorldDiagnostics.Warning> warnings = new WorldDiagnostics().diagnose(world);

        assertEquals(1, warnings.size());
        assertEquals("block-entity-anchor-mismatch", warnings.get(0).code());
        assertTrue(warnings.get(0).message().contains("minecraft:stone"));
    }

    @Test
    void acceptsMatchingBlockEntityAnchorBlock() {
        WorldSpec world = new DslParser().parse("""
                world "Diagnostics" {
                  min_y 0
                  height 32
                  set 1 4 1 minecraft:chest[facing=north,type=single,waterlogged=false]
                  block_entity 1 4 1 minecraft:chest {
                    item Items slot 0 minecraft:bread count 1
                  }
                }
                """);

        assertTrue(new WorldDiagnostics().diagnose(world).isEmpty());
    }

    @Test
    void warnsWhenWallAttachmentHasNoSupportBehindIt() {
        WorldSpec world = new DslParser().parse("""
                world "Diagnostics" {
                  min_y 0
                  height 32
                  set 4 5 4 minecraft:oak_wall_sign[facing=south,waterlogged=false]
                  sign_text 4 5 4 front "one" "two" "three" "four"
                }
                """);

        List<WorldDiagnostics.Warning> warnings = new WorldDiagnostics().diagnose(world);

        assertEquals(1, warnings.size());
        assertEquals("unsupported-wall-attachment", warnings.get(0).code());
        assertTrue(warnings.get(0).message().contains("4 5 3"));
    }

    @Test
    void acceptsSupportedWallAttachment() {
        WorldSpec world = new DslParser().parse("""
                world "Diagnostics" {
                  min_y 0
                  height 32
                  set 4 5 3 minecraft:stone_bricks
                  set 4 5 4 minecraft:oak_wall_sign[facing=south,waterlogged=false]
                  sign_text 4 5 4 front "one" "two" "three" "four"
                }
                """);

        assertTrue(new WorldDiagnostics().diagnose(world).isEmpty());
    }

    @Test
    void warnsWhenStructuralLayerFloatsWithoutAnySupport() {
        WorldSpec world = new DslParser().parse("""
                world "Diagnostics" {
                  min_y 0
                  height 32
                  fill 1 6 1 to 4 6 4 minecraft:spruce_planks
                }
                """);

        List<WorldDiagnostics.Warning> warnings = new WorldDiagnostics().diagnose(world);

        assertEquals(1, warnings.size());
        assertEquals("unsupported-floating-structure", warnings.get(0).code());
    }

    @Test
    void acceptsStructuralLayerWhenOneBlockRestsOnSupport() {
        WorldSpec world = new DslParser().parse("""
                world "Diagnostics" {
                  min_y 0
                  height 32
                  fill 1 4 1 to 1 6 1 minecraft:spruce_log[axis=y]
                  fill 1 6 1 to 4 6 4 minecraft:spruce_planks
                }
                """);

        assertTrue(new WorldDiagnostics().diagnose(world).isEmpty());
    }
}
