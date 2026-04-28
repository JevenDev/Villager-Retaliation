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
        ENABLE_VILLAGER_DROPS = BUILDER.define("enableVillagerDrops", true);
        ENABLE_WANDERING_TRADER_DROPS = BUILDER.define("enableWanderingTraderDrops", true);
        ENABLE_VILLAGER_RETALIATION = BUILDER.define("enableVillagerRetaliation", true);
        BUILDER.pop();

        BUILDER.push("balance");
        BABY_VILLAGERS_DROP_LOOT = BUILDER.define("babyVillagersDropLoot", false);
        REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT = BUILDER.define("requirePlayerKillForProfessionLoot", true);
        VILLAGER_EMERALD_DROP_CHANCE = chance("villagerEmeraldDropChance", 0.35);
        VILLAGER_BREAD_DROP_CHANCE = chance("villagerBreadDropChance", 0.60);
        PROFESSION_DROP_CHANCE = chance("professionDropChance", 0.50);
        RARE_DROP_CHANCE = chance("rareDropChance", 0.05);
        VERY_RARE_DROP_CHANCE = chance("veryRareDropChance", 0.01);
        BUILDER.pop();

        BUILDER.push("retaliation");
        ATTACK_AGGROS_ONLY_HIT_VILLAGER = BUILDER.define("attackAggrosOnlyHitVillager", true);
        KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS = BUILDER.define("killingVillagerAggrosNearbyVillagers", true);
        VILLAGER_KILL_AGGRO_RADIUS = BUILDER.defineInRange("villagerKillAggroRadius", 24.0D, 0.0D, 128.0D);
        AGGRO_DURATION_TICKS = BUILDER.defineInRange("aggroDurationTicks", 600, 1, 20 * 60 * 10);
        NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS = BUILDER.define("nearbyVillagersIgnoreCreativePlayers", true);
        BUILDER.pop();

        BUILDER.push("combat");
        WEAPONSMITHS_FIGHT_BACK = BUILDER.define("weaponsmithsFightBack", true);
        TOOLSMITHS_FIGHT_BACK = BUILDER.define("toolsmithsFightBack", true);
        ARMORERS_FIGHT_BACK = BUILDER.define("armorersFightBack", true);
        FLETCHERS_FIGHT_BACK = BUILDER.define("fletchersFightBack", true);
        BUTCHERS_FIGHT_BACK = BUILDER.define("butchersFightBack", true);
        FARMERS_USE_BREAD = BUILDER.define("farmersUseBread", true);
        CLERICS_USE_POTIONS = BUILDER.define("clericsUsePotions", true);
        BUILDER.pop();

        BUILDER.push("wanderer");
        WANDERER_DROP_EMERALDS = BUILDER.define("dropEmeralds", true);
        WANDERER_DROP_INVISIBILITY_POTION = BUILDER.define("dropInvisibilityPotion", true);
        WANDERER_DROP_RANDOM_CURRENT_TRADE = BUILDER.define("dropRandomCurrentTrade", true);
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
