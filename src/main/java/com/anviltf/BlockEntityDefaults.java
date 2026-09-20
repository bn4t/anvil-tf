package com.anviltf;

import java.util.Map;
import java.util.Set;

/**
 * Maps block ids that require a block entity to render or function to their
 * block-entity ids. When such a block is authored without an explicit
 * block_entity, the compiler emits an empty default payload so the block
 * actually exists in-game (banners, signs, and skulls render only via their
 * block entity).
 */
public final class BlockEntityDefaults {
    private BlockEntityDefaults() {
    }

    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("minecraft:barrel", "minecraft:barrel"),
            Map.entry("minecraft:beacon", "minecraft:beacon"),
            Map.entry("minecraft:beehive", "minecraft:beehive"),
            Map.entry("minecraft:bee_nest", "minecraft:beehive"),
            Map.entry("minecraft:bell", "minecraft:bell"),
            Map.entry("minecraft:blast_furnace", "minecraft:blast_furnace"),
            Map.entry("minecraft:brewing_stand", "minecraft:brewing_stand"),
            Map.entry("minecraft:campfire", "minecraft:campfire"),
            Map.entry("minecraft:soul_campfire", "minecraft:campfire"),
            Map.entry("minecraft:chest", "minecraft:chest"),
            Map.entry("minecraft:trapped_chest", "minecraft:chest"),
            Map.entry("minecraft:ender_chest", "minecraft:ender_chest"),
            Map.entry("minecraft:chiseled_bookshelf", "minecraft:chiseled_bookshelf"),
            Map.entry("minecraft:command_block", "minecraft:command_block"),
            Map.entry("minecraft:chain_command_block", "minecraft:command_block"),
            Map.entry("minecraft:repeating_command_block", "minecraft:command_block"),
            Map.entry("minecraft:comparator", "minecraft:comparator"),
            Map.entry("minecraft:conduit", "minecraft:conduit"),
            Map.entry("minecraft:crafter", "minecraft:crafter"),
            Map.entry("minecraft:creaking_heart", "minecraft:creaking_heart"),
            Map.entry("minecraft:daylight_detector", "minecraft:daylight_detector"),
            Map.entry("minecraft:decorated_pot", "minecraft:decorated_pot"),
            Map.entry("minecraft:dispenser", "minecraft:dispenser"),
            Map.entry("minecraft:dropper", "minecraft:dropper"),
            Map.entry("minecraft:enchanting_table", "minecraft:enchanting_table"),
            Map.entry("minecraft:end_gateway", "minecraft:end_gateway"),
            Map.entry("minecraft:end_portal", "minecraft:end_portal"),
            Map.entry("minecraft:furnace", "minecraft:furnace"),
            Map.entry("minecraft:hopper", "minecraft:hopper"),
            Map.entry("minecraft:jigsaw", "minecraft:jigsaw"),
            Map.entry("minecraft:jukebox", "minecraft:jukebox"),
            Map.entry("minecraft:lectern", "minecraft:lectern"),
            Map.entry("minecraft:lodestone", "minecraft:lodestone"),
            Map.entry("minecraft:mob_spawner", "minecraft:mob_spawner"),
            Map.entry("minecraft:sculk_sensor", "minecraft:sculk_sensor"),
            Map.entry("minecraft:calibrated_sculk_sensor", "minecraft:calibrated_sculk_sensor"),
            Map.entry("minecraft:sculk_catalyst", "minecraft:sculk_catalyst"),
            Map.entry("minecraft:sculk_shrieker", "minecraft:sculk_shrieker"),
            Map.entry("minecraft:smoker", "minecraft:smoker"),
            Map.entry("minecraft:structure_block", "minecraft:structure_block"),
            Map.entry("minecraft:structure_void", "minecraft:structure_block"),
            Map.entry("minecraft:suspicious_sand", "minecraft:brushable_block"),
            Map.entry("minecraft:suspicious_gravel", "minecraft:brushable_block"),
            Map.entry("minecraft:trial_spawner", "minecraft:trial_spawner"),
            Map.entry("minecraft:vault", "minecraft:vault"),
            Map.entry("minecraft:moving_piston", "minecraft:piston"));

    // Longest suffixes first so e.g. wall_hanging_sign wins over hanging_sign.
    private static final Map<String, String> SUFFIX_FAMILIES = Map.ofEntries(
            Map.entry("wall_hanging_sign", "minecraft:hanging_sign"),
            Map.entry("hanging_sign", "minecraft:hanging_sign"),
            Map.entry("wall_banner", "minecraft:banner"),
            Map.entry("wall_sign", "minecraft:sign"),
            Map.entry("shulker_box", "minecraft:shulker_box"),
            Map.entry("banner", "minecraft:banner"),
            Map.entry("sign", "minecraft:sign"),
            Map.entry("bed", "minecraft:bed"));

    private static final Set<String> SKULLS = Set.of(
            "minecraft:skeleton_skull", "minecraft:skeleton_wall_skull",
            "minecraft:wither_skeleton_skull", "minecraft:wither_skeleton_wall_skull",
            "minecraft:zombie_head", "minecraft:zombie_wall_head",
            "minecraft:creeper_head", "minecraft:creeper_wall_head",
            "minecraft:player_head", "minecraft:player_wall_head",
            "minecraft:dragon_head", "minecraft:dragon_wall_head",
            "minecraft:piglin_head", "minecraft:piglin_wall_head");

    /** Returns the block-entity id for this block, or null if it has none. */
    public static String forBlock(String blockName) {
        String id = EXACT.get(blockName);
        if (id != null) {
            return id;
        }
        if (SKULLS.contains(blockName)) {
            return "minecraft:skull";
        }
        String local = blockName.startsWith("minecraft:") ? blockName.substring(10) : blockName;
        for (Map.Entry<String, String> family : SUFFIX_FAMILIES.entrySet()) {
            String suffix = family.getKey();
            if (local.equals(suffix) || local.endsWith("_" + suffix)) {
                return family.getValue();
            }
        }
        return null;
    }
}
