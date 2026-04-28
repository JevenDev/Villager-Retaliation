package com.jvn.commonfolk.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonfolkConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_DROPS;
    public static final ModConfigSpec.BooleanValue ENABLE_WANDERING_TRADER_DROPS;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_RETALIATION;

    public static final ModConfigSpec.BooleanValue BABY_VILLAGERS_DROP_LOOT;
    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT;
    public static final ModConfigSpec.DoubleValue VILLAGER_EMERALD_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue VILLAGER_BREAD_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue PROFESSION_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue RARE_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue VERY_RARE_DROP_CHANCE;

    public static final ModConfigSpec.BooleanValue ATTACK_AGGROS_ONLY_HIT_VILLAGER;
    public static final ModConfigSpec.BooleanValue KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS;
    public static final ModConfigSpec.DoubleValue VILLAGER_KILL_AGGRO_RADIUS;
    public static final ModConfigSpec.IntValue AGGRO_DURATION_TICKS;
    public static final ModConfigSpec.BooleanValue NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS;

    public static final ModConfigSpec.BooleanValue WEAPONSMITHS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue TOOLSMITHS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue ARMORERS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue FLETCHERS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue BUTCHERS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue FARMERS_USE_BREAD;
    public static final ModConfigSpec.BooleanValue CLERICS_USE_POTIONS;

    public static final ModConfigSpec.BooleanValue WANDERER_DROP_EMERALDS;
    public static final ModConfigSpec.BooleanValue WANDERER_DROP_INVISIBILITY_POTION;
    public static final ModConfigSpec.BooleanValue WANDERER_DROP_RANDOM_CURRENT_TRADE;
    public static final ModConfigSpec.DoubleValue WANDERER_RANDOM_TRADE_DROP_CHANCE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");
        ENABLE_VILLAGER_DROPS = BUILDER
                .comment("Enables Commonfolk custom adult villager drops.")
                .define("enableVillagerDrops", true);
        ENABLE_WANDERING_TRADER_DROPS = BUILDER
                .comment("Enables Commonfolk custom wandering trader drops.")
                .define("enableWanderingTraderDrops", true);
        ENABLE_VILLAGER_RETALIATION = BUILDER
                .comment("Enables temporary villager retaliation behavior.")
                .define("enableVillagerRetaliation", true);
        BUILDER.pop();

        BUILDER.push("balance");
        BABY_VILLAGERS_DROP_LOOT = BUILDER
                .comment("Reserved for pack authors. Commonfolk v1 hard-enforces no custom loot from baby villagers.")
                .define("babyVillagersDropLoot", false);
        REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT = BUILDER
                .comment("Requires a player-caused kill before profession-specific villager loot can roll.")
                .define("requirePlayerKillForProfessionLoot", true);
        BUILDER.comment("Chance for adult villagers to drop 1-5 emeralds.");
        VILLAGER_EMERALD_DROP_CHANCE = chance("villagerEmeraldDropChance", 0.35);
        BUILDER.comment("Chance for adult villagers to drop 1-3 bread.");
        VILLAGER_BREAD_DROP_CHANCE = chance("villagerBreadDropChance", 0.60);
        BUILDER.comment("Chance for adult villagers to roll their profession-specific loot pool.");
        PROFESSION_DROP_CHANCE = chance("professionDropChance", 0.50);
        BUILDER.comment("Chance used by rare profession drops.");
        RARE_DROP_CHANCE = chance("rareDropChance", 0.05);
        BUILDER.comment("Chance used by very rare profession drops.");
        VERY_RARE_DROP_CHANCE = chance("veryRareDropChance", 0.01);
        BUILDER.pop();

        BUILDER.push("retaliation");
        ATTACK_AGGROS_ONLY_HIT_VILLAGER = BUILDER
                .comment("When true, damaging a villager only angers that villager.")
                .define("attackAggrosOnlyHitVillager", true);
        KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS = BUILDER
                .comment("When true, killing an adult villager angers nearby adult villagers.")
                .define("killingVillagerAggrosNearbyVillagers", true);
        VILLAGER_KILL_AGGRO_RADIUS = BUILDER
                .comment("Radius in blocks for nearby villager aggro after a villager is killed.")
                .defineInRange("villagerKillAggroRadius", 24.0D, 0.0D, 128.0D);
        AGGRO_DURATION_TICKS = BUILDER
                .comment("How long, in ticks, temporary villager anger lasts.")
                .defineInRange("aggroDurationTicks", 600, 1, 20 * 60 * 10);
        NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS = BUILDER
                .comment("When true, creative and spectator players are ignored as retaliation targets.")
                .define("nearbyVillagersIgnoreCreativePlayers", true);
        BUILDER.pop();

        BUILDER.push("combat");
        WEAPONSMITHS_FIGHT_BACK = BUILDER.comment("Allows weaponsmiths to retaliate with stronger melee behavior.").define("weaponsmithsFightBack", true);
        TOOLSMITHS_FIGHT_BACK = BUILDER.comment("Allows toolsmiths to retaliate with melee behavior.").define("toolsmithsFightBack", true);
        ARMORERS_FIGHT_BACK = BUILDER.comment("Allows armorers to retaliate and gain short defensive resistance.").define("armorersFightBack", true);
        FLETCHERS_FIGHT_BACK = BUILDER.comment("Allows fletchers to retaliate, including simple ranged shots.").define("fletchersFightBack", true);
        BUTCHERS_FIGHT_BACK = BUILDER.comment("Allows butchers to retaliate with melee behavior.").define("butchersFightBack", true);
        FARMERS_USE_BREAD = BUILDER.comment("Allows farmers to retaliate defensively and heal themselves when hurt.").define("farmersUseBread", true);
        CLERICS_USE_POTIONS = BUILDER.comment("Allows clerics to retaliate defensively and use regeneration when hurt.").define("clericsUsePotions", true);
        BUILDER.pop();

        BUILDER.push("wanderer");
        WANDERER_DROP_EMERALDS = BUILDER.comment("Allows wandering traders to drop 1-5 emeralds.").define("dropEmeralds", true);
        WANDERER_DROP_INVISIBILITY_POTION = BUILDER.comment("Allows wandering traders to drop one invisibility potion.").define("dropInvisibilityPotion", true);
        WANDERER_DROP_RANDOM_CURRENT_TRADE = BUILDER.comment("Allows wandering traders to drop a safe copy of one current trade result.").define("dropRandomCurrentTrade", true);
        BUILDER.comment("Chance for the wandering trader random trade result drop.");
        WANDERER_RANDOM_TRADE_DROP_CHANCE = chance("randomTradeDropChance", 0.50);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private CommonfolkConfig() {
    }

    private static ModConfigSpec.DoubleValue chance(String name, double defaultValue) {
        return BUILDER.defineInRange(name, defaultValue, 0.0D, 1.0D);
    }
}
