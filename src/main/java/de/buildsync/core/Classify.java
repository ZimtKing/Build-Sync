package de.buildsync.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Classifies block states (as text "minecraft:id[prop=value,…]") for the visibility rules.
 * Anything NOT explicitly listed here as transparent/natural counts as "ground" or "do not show" –
 * so unknown blocks always lead to the safer (concealing) result.
 */
public final class Classify {
    private Classify() {}

    public static final int AIR = 1;          // air (all variants)
    public static final int TRANSPARENT = 2;  // skipped when searching for the ground (air, fluids, plants, natural leaves …)
    public static final int LOGLIKE = 4;      // log-like (tree trunk, cactus …): skipped if it stands directly on natural ground
    public static final int HATCH = 16;       // hatches/doors: as the top block they would reveal a hidden opening, so they are not shown as real blocks
    public static final int NATURAL = 8;      // may be shown as a real block directly below the surface

    static Set<String> set(String... a) { return new HashSet<>(Arrays.asList(a)); }

    private static final Set<String> AIRS = set("air", "cave_air", "void_air");
    private static final Set<String> FLUIDS = set("water", "lava", "bubble_column");
    private static final Set<String> PLANTS = set(
        "short_grass", "grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush", "seagrass", "tall_seagrass", "kelp", "kelp_plant",
        "sugar_cane", "vine", "glow_lichen", "sweet_berry_bush", "brown_mushroom", "red_mushroom", "crimson_roots", "warped_roots",
        "nether_sprouts", "crimson_fungus", "warped_fungus", "hanging_roots", "spore_blossom", "small_dripleaf", "big_dripleaf", "big_dripleaf_stem",
        "cave_vines", "cave_vines_plant", "twisting_vines", "twisting_vines_plant", "weeping_vines", "weeping_vines_plant",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
        "cornflower", "lily_of_the_valley", "torchflower", "wither_rose", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant", "lily_pad",
        "pink_petals", "wildflowers", "leaf_litter", "firefly_bush", "closed_eyeblossom", "open_eyeblossom", "snow", "fire", "soul_fire",
        "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch", "light");
    private static final Set<String> LOGS_EXACT = set("cactus", "bamboo", "mangrove_roots", "muddy_mangrove_roots",
        "red_mushroom_block", "brown_mushroom_block", "mushroom_stem", "chorus_plant", "chorus_flower");
    /** Natural terrain that may be shown as real blocks directly below the surface (NOT ores, chests, spawners …). */
    private static final Set<String> NATURALS = set(
        "grass_block", "dirt", "coarse_dirt", "podzol", "mycelium", "mud", "rooted_dirt", "moss_block", "sand", "red_sand", "gravel", "clay",
        "stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "dripstone_block", "basalt", "smooth_basalt", "netherrack",
        "soul_sand", "soul_soil", "blackstone", "end_stone", "bedrock", "sandstone", "red_sandstone", "terracotta", "snow_block", "powder_snow",
        "ice", "packed_ice", "blue_ice", "dirt_path", "farmland", "crimson_nylium", "warped_nylium", "magma_block", "obsidian", "gravel");

    public static String idOf(String state) {
        int k = state.indexOf('[');
        String id = k < 0 ? state : state.substring(0, k);
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    public static boolean hasProp(String state, String prop, String value) {
        int k = state.indexOf('[');
        if (k < 0) return false;
        return ("," + state.substring(k + 1, state.length() - (state.endsWith("]") ? 1 : 0)) + ",").contains("," + prop + "=" + value + ",");
    }

    public static int flags(String state) {
        String id = idOf(state);
        int f = 0;
        if (AIRS.contains(id)) return AIR | TRANSPARENT;
        if (FLUIDS.contains(id) || PLANTS.contains(id) || id.endsWith("_sapling")) f |= TRANSPARENT;
        // leaves: only "natural" ones (persistent=false) are transparent; leaves placed by a player count as ground (camouflage roof!)
        if (id.endsWith("_leaves") && hasProp(state, "persistent", "false")) f |= TRANSPARENT;
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae") || LOGS_EXACT.contains(id)) f |= LOGLIKE;
        if (id.endsWith("_trapdoor") || id.endsWith("_door")) f |= HATCH;
        if (NATURALS.contains(id) || id.endsWith("_terracotta") && !id.contains("glazed")) f |= NATURAL;
        return f;
    }
}
