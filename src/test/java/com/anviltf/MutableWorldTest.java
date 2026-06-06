package com.anviltf;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MutableWorldTest {
    @Test
    void chunkAlignedFillsUseCompactLayers() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);

        world.fill(0, 4, 0, 31, 4, 31, BlockState.parse("minecraft:stone"));

        assertEquals(4, world.spec().chunks().size());
        for (MutableWorld.ChunkBlocks chunk : world.spec().chunks().values()) {
            assertEquals(1, chunk.layerCount());
            assertEquals(0, chunk.explicitBlockCount());
            assertEquals("minecraft:stone", chunk.get(0, 4, 0).name());
            assertEquals("minecraft:air", chunk.get(0, 5, 0).name());
        }
    }

    @Test
    void explicitBlocksCanOverrideCompactLayers() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);
        BlockState stone = BlockState.parse("minecraft:stone");

        world.fill(0, 4, 0, 15, 4, 15, stone);
        world.setBlock(1, 4, 1, BlockState.AIR);

        MutableWorld.ChunkBlocks chunk = world.spec().chunks().get(new ChunkPos(0, 0));
        assertEquals(1, chunk.layerCount());
        assertEquals(1, chunk.explicitBlockCount());
        assertEquals("minecraft:air", chunk.get(1, 4, 1).name());

        world.setBlock(1, 4, 1, stone);

        assertEquals(0, chunk.explicitBlockCount());
        assertEquals("minecraft:stone", chunk.get(1, 4, 1).name());
    }

    @Test
    void replaceCanEditCompactLayersAndAirOverrides() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);
        BlockState stone = BlockState.parse("minecraft:stone");
        BlockState moss = BlockState.parse("minecraft:mossy_cobblestone");

        world.fill(-16, 4, -16, 15, 4, 15, stone);
        world.replace(-2, 4, -2, 2, 4, 2, stone, moss);
        world.replace(0, 4, 0, 1, 4, 1, moss, BlockState.AIR);

        assertEquals("minecraft:mossy_cobblestone", world.getBlock(-2, 4, -2).name());
        assertEquals("minecraft:air", world.getBlock(0, 4, 0).name());
        assertEquals("minecraft:stone", world.getBlock(3, 4, 3).name());
        assertEquals("minecraft:air", world.getBlock(0, 5, 0).name());
    }

    @Test
    void linePlacesDiagonalEndpointsAndInterior() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);

        world.line(-2, 4, -2, 2, 8, 2, BlockState.parse("minecraft:copper_block"));

        assertEquals("minecraft:copper_block", world.getBlock(-2, 4, -2).name());
        assertEquals("minecraft:copper_block", world.getBlock(0, 6, 0).name());
        assertEquals("minecraft:copper_block", world.getBlock(2, 8, 2).name());
        assertEquals("minecraft:air", world.getBlock(0, 6, 1).name());
    }

    @Test
    void copyDuplicatesBlocksAndBlockEntitiesWithRotation() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);

        world.setBlock(1, 4, 0, BlockState.parse(
                "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"));
        world.setBlock(0, 4, 1, BlockState.parse("minecraft:chest[facing=north,type=single,waterlogged=false]"));
        world.setBlockEntity(0, 4, 1, "minecraft:chest",
                java.util.Map.of("CustomName", NbtValue.stringValue("{\"text\":\"Copied\"}")));

        world.copy(0, 4, 0, 1, 4, 1, 10, 4, 10, 1, true);

        BlockState copiedStair = world.getBlock(10, 4, 11);
        assertEquals("minecraft:oak_stairs", copiedStair.name());
        assertEquals("east", copiedStair.properties().get("facing"));
        assertEquals("minecraft:chest", world.getBlock(9, 4, 10).name());
        assertEquals(2, world.spec().blockEntities().values().stream().mapToInt(Map::size).sum());
        assertEquals("minecraft:chest", world.spec().blockEntities().get(new ChunkPos(0, 0))
                .get(new WorldSpec.BlockPos(9, 4, 10)).id());
    }

    @Test
    void copyCanMirrorBlocksAndBlockStates() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);

        world.setBlock(0, 4, 0, BlockState.parse(
                "minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=false]"));
        world.setBlock(2, 4, 0, BlockState.parse(
                "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=false]"));

        world.copy(0, 4, 0, 2, 4, 0, 10, 4, 10, 0, true, false, false, true, false);

        BlockState stair = world.getBlock(12, 4, 10);
        assertEquals("west", stair.properties().get("facing"));
        assertEquals("inner_right", stair.properties().get("shape"));
        BlockState pane = world.getBlock(10, 4, 10);
        assertEquals("false", pane.properties().get("east"));
        assertEquals("true", pane.properties().get("west"));
    }

    @Test
    void copyCanIncludeEntitiesWithFreshPositionAndRotation() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);
        world.addEntity(0.5, 4.0, 1.5, "minecraft:armor_stand", java.util.Map.of(
                "Rotation", NbtValue.listValue(com.anviltf.anvil.NbtWriter.TAG_FLOAT,
                        java.util.List.of(NbtValue.floatValue(45.0f), NbtValue.floatValue(0.0f))),
                "UUID", NbtValue.intArrayValue(new int[] {1, 2, 3, 4})));

        world.copy(0, 4, 0, 1, 4, 1, 10, 4, 10, 1, true, true);

        var entities = world.spec().entities().get(new ChunkPos(0, 0));
        assertEquals(2, entities.size());
        Entity copied = entities.get(1);
        assertEquals(8.5, copied.x());
        assertEquals(4.0, copied.y());
        assertEquals(10.5, copied.z());
        assertEquals(false, copied.data().containsKey("UUID"));
        NbtValue.ListValue rotation = (NbtValue.ListValue) copied.data().get("Rotation");
        assertEquals(new NbtValue.FloatValue(135.0f), rotation.values().get(0));
    }

    @Test
    void copyMirrorsEntityPositionsAcrossWholeSourceVolume() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);
        world.addEntity(0.5, 4.0, 0.5, "minecraft:armor_stand", java.util.Map.of(
                "Rotation", NbtValue.listValue(com.anviltf.anvil.NbtWriter.TAG_FLOAT,
                        java.util.List.of(NbtValue.floatValue(90.0f), NbtValue.floatValue(0.0f)))));

        world.copy(0, 4, 0, 2, 4, 2, 10, 4, 10, 0, true, true, false, true, true);

        var entities = world.spec().entities().get(new ChunkPos(0, 0));
        assertEquals(2, entities.size());
        Entity copied = entities.get(1);
        assertEquals(12.5, copied.x());
        assertEquals(4.0, copied.y());
        assertEquals(12.5, copied.z());
        NbtValue.ListValue rotation = (NbtValue.ListValue) copied.data().get("Rotation");
        assertEquals(new NbtValue.FloatValue(-90.0f), rotation.values().get(0));
    }

    @Test
    void copyCanIncludeBiomeCells() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);
        world.spec().defaultBiome("minecraft:plains");
        world.fillBiome(0, 4, 0, 7, 7, 7, "minecraft:flower_forest");

        world.copy(0, 4, 0, 7, 7, 7, 20, 4, 20, 0, true, false, true);

        assertEquals("minecraft:flower_forest", world.getBiome(20, 4, 20));
        assertEquals("minecraft:flower_forest", world.getBiome(24, 4, 24));
        assertEquals("minecraft:plains", world.getBiome(20, 8, 20));
    }

    @Test
    void spherePlacesRoundedVolume() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);

        world.sphere(8, 8, 8, 2, BlockState.parse("minecraft:glass"));

        MutableWorld.ChunkBlocks chunk = world.spec().chunks().get(new ChunkPos(0, 0));
        assertEquals("minecraft:glass", chunk.get(8, 8, 8).name());
        assertEquals("minecraft:glass", chunk.get(10, 8, 8).name());
        assertEquals("minecraft:air", chunk.get(10, 10, 10).name());
    }

    @Test
    void hollowShapesPreserveInteriorSpace() {
        MutableWorld world = new MutableWorld();
        world.spec().minY(0);
        world.spec().height(32);

        world.hollowSphere(8, 8, 8, 2, BlockState.parse("minecraft:glass"), BlockState.AIR);
        world.hollowCylinder(20, 4, 4, 2, 8,
                BlockState.parse("minecraft:stone_bricks"), BlockState.AIR);

        assertEquals("minecraft:air", world.spec().chunks().get(new ChunkPos(0, 0)).get(8, 8, 8).name());
        assertEquals("minecraft:glass", world.spec().chunks().get(new ChunkPos(0, 0)).get(10, 8, 8).name());
        assertEquals("minecraft:stone_bricks", world.spec().chunks().get(new ChunkPos(1, 0)).get(4, 4, 4).name());
        assertEquals("minecraft:air", world.spec().chunks().get(new ChunkPos(1, 0)).get(4, 6, 4).name());
    }
}
