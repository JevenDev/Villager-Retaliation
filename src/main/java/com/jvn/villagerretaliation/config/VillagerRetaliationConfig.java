package com.jvn.villagerretaliation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VillagerRetaliationConfig {
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
    public static final ModConfigSpec.DoubleValue COMBAT_WEAPON_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue COMBAT_WEAPON_ENCHANT_CHANCE;
    public static final ModConfigSpec.DoubleValue ARMORER_SHIELD_CHANCE_HARD;
    public static final ModConfigSpec.BooleanValue FARMERS_USE_BREAD;
    public static final ModConfigSpec.BooleanValue CLERICS_USE_POTIONS;
    public static final ModConfigSpec.DoubleValue PASSIVE_CLERIC_ALLY_HEAL_RANGE;
    public static final ModConfigSpec.DoubleValue PASSIVE_CLERIC_ALLY_HEAL_HEALTH_THRESHOLD;
    public static final ModConfigSpec.BooleanValue PASSIVE_CLERIC_ALLY_HEAL_REQUIRES_LINE_OF_SIGHT;

    public static final ModConfigSpec.BooleanValue WANDERER_DROP_EMERALDS;
    public static final ModConfigSpec.BooleanValue WANDERER_DROP_INVISIBILITY_POTION;
    public static final ModConfigSpec.BooleanValue WANDERER_DROP_RANDOM_CURRENT_TRADE;
    public static final ModConfigSpec.DoubleValue WANDERER_RANDOM_TRADE_DROP_CHANCE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");
        ENABLE_VILLAGER_DROPS = BUILDER
                .comment("Enables VillagerRetaliation custom adult villager drops.")
                .translation("villagerretaliation.configuration.general.enableVillagerDrops")
                .define("enableVillagerDrops", true);
        ENABLE_WANDERING_TRADER_DROPS = BUILDER
                .comment("Enables VillagerRetaliation custom wandering trader drops.")
                .translation("villagerretaliation.configuration.general.enableWanderingTraderDrops")
                .define("enableWanderingTraderDrops", true);
        ENABLE_VILLAGER_RETALIATION = BUILDER
                .comment("Enables temporary villager retaliation behavior.")
                .translation("villagerretaliation.configuration.general.enableVillagerRetaliation")
                .define("enableVillagerRetaliation", true);
        BUILDER.pop();

        BUILDER.push("balance");
        BABY_VILLAGERS_DROP_LOOT = BUILDER
                .comment("Reserved for pack authors. VillagerRetaliation v1 hard-enforces no custom loot from baby villagers.")
                .translation("villagerretaliation.configuration.balance.babyVillagersDropLoot")
                .define("babyVillagersDropLoot", false);
        REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT = BUILDER
                .comment("Requires a player-caused kill before profession-specific villager loot can roll.")
                .translation("villagerretaliation.configuration.balance.requirePlayerKillForProfessionLoot")
                .define("requirePlayerKillForProfessionLoot", true);
        BUILDER.comment("Chance for adult villagers to drop 1-5 emeralds.");
        VILLAGER_EMERALD_DROP_CHANCE = chance("villagerEmeraldDropChance", "villagerretaliation.configuration.balance.villagerEmeraldDropChance", 0.35);
        BUILDER.comment("Chance for adult villagers to drop 1-3 bread.");
        VILLAGER_BREAD_DROP_CHANCE = chance("villagerBreadDropChance", "villagerretaliation.configuration.balance.villagerBreadDropChance", 0.60);
        BUILDER.comment("Chance for adult villagers to roll their profession-specific loot pool.");
        PROFESSION_DROP_CHANCE = chance("professionDropChance", "villagerretaliation.configuration.balance.professionDropChance", 0.50);
        BUILDER.comment("Chance used by rare profession drops.");
        RARE_DROP_CHANCE = chance("rareDropChance", "villagerretaliation.configuration.balance.rareDropChance", 0.05);
        BUILDER.comment("Chance used by very rare profession drops.");
        VERY_RARE_DROP_CHANCE = chance("veryRareDropChance", "villagerretaliation.configuration.balance.veryRareDropChance", 0.01);
        BUILDER.pop();

        BUILDER.push("retaliation");
        ATTACK_AGGROS_ONLY_HIT_VILLAGER = BUILDER
                .comment("When true, damaging a villager only angers that villager.")
                .translation("villagerretaliation.configuration.retaliation.attackAggrosOnlyHitVillager")
                .define("attackAggrosOnlyHitVillager", true);
        KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS = BUILDER
                .comment("When true, killing an adult villager angers nearby adult villagers.")
                .translation("villagerretaliation.configuration.retaliation.killingVillagerAggrosNearbyVillagers")
                .define("killingVillagerAggrosNearbyVillagers", true);
        VILLAGER_KILL_AGGRO_RADIUS = BUILDER
                .comment("Radius in blocks for nearby villager aggro after a villager is killed.")
                .translation("villagerretaliation.configuration.retaliation.villagerKillAggroRadius")
                .defineInRange("villagerKillAggroRadius", 24.0D, 0.0D, 128.0D);
        AGGRO_DURATION_TICKS = BUILDER
                .comment("How long, in ticks, temporary villager anger lasts.")
                .translation("villagerretaliation.configuration.retaliation.aggroDurationTicks")
                .defineInRange("aggroDurationTicks", 600, 1, 20 * 60 * 10);
        NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS = BUILDER
                .comment("When true, creative and spectator players are ignored as retaliation targets.")
                .translation("villagerretaliation.configuration.retaliation.nearbyVillagersIgnoreCreativePlayers")
                .define("nearbyVillagersIgnoreCreativePlayers", true);
        BUILDER.pop();

        BUILDER.push("combat");
        WEAPONSMITHS_FIGHT_BACK = BUILDER.comment("Allows weaponsmiths to retaliate with stronger melee behavior.")
                .translation("villagerretaliation.configuration.combat.weaponsmithsFightBack")
                .define("weaponsmithsFightBack", true);
        TOOLSMITHS_FIGHT_BACK = BUILDER.comment("Allows toolsmiths to retaliate with melee behavior.")
                .translation("villagerretaliation.configuration.combat.toolsmithsFightBack")
                .define("toolsmithsFightBack", true);
        ARMORERS_FIGHT_BACK = BUILDER.comment("Allows armorers to retaliate and gain short defensive resistance.")
                .translation("villagerretaliation.configuration.combat.armorersFightBack")
                .define("armorersFightBack", true);
        FLETCHERS_FIGHT_BACK = BUILDER.comment("Allows fletchers to retaliate, including simple ranged shots.")
                .translation("villagerretaliation.configuration.combat.fletchersFightBack")
                .define("fletchersFightBack", true);
        BUTCHERS_FIGHT_BACK = BUILDER.comment("Allows butchers to retaliate with melee behavior.")
                .translation("villagerretaliation.configuration.combat.butchersFightBack")
                .define("butchersFightBack", true);
        BUILDER.comment("Chance for temporary retaliation main-hand weapons to drop on villager death.");
        COMBAT_WEAPON_DROP_CHANCE = BUILDER
                .translation("villagerretaliation.configuration.combat.combatWeaponDropChance")
                .defineInRange("combatWeaponDropChance", 0.085D, 0.0D, 1.0D);
        BUILDER.comment("Chance for temporary retaliation main-hand weapons to be enchanted (hard mode only).");
        COMBAT_WEAPON_ENCHANT_CHANCE = BUILDER
                .translation("villagerretaliation.configuration.combat.combatWeaponEnchantChance")
                .defineInRange("combatWeaponEnchantChance", 0.25D, 0.0D, 1.0D);
        BUILDER.comment("Chance for armorers to spawn with an offhand shield (hard mode only).");
        ARMORER_SHIELD_CHANCE_HARD = BUILDER
                .translation("villagerretaliation.configuration.combat.armorerShieldChanceHard")
                .defineInRange("armorerShieldChanceHard", 0.35D, 0.0D, 1.0D);
        FARMERS_USE_BREAD = BUILDER.comment("Allows farmers to retaliate defensively and heal themselves when hurt.")
                .translation("villagerretaliation.configuration.combat.farmersUseBread")
                .define("farmersUseBread", true);
        CLERICS_USE_POTIONS = BUILDER.comment("Allows clerics to retaliate defensively and use regeneration when hurt.")
                .translation("villagerretaliation.configuration.combat.clericsUsePotions")
                .define("clericsUsePotions", true);
        PASSIVE_CLERIC_ALLY_HEAL_RANGE = BUILDER.comment("Maximum range in blocks for idle clerics to look for injured villagers or wandering traders.")
                .translation("villagerretaliation.configuration.combat.passiveClericAllyHealRange")
                .defineInRange("passiveClericAllyHealRange", 12.0D, 1.0D, 64.0D);
        PASSIVE_CLERIC_ALLY_HEAL_HEALTH_THRESHOLD = BUILDER.comment("Health ratio below which idle clerics consider an ally injured enough to heal.")
                .translation("villagerretaliation.configuration.combat.passiveClericAllyHealHealthThreshold")
                .defineInRange("passiveClericAllyHealHealthThreshold", 0.60D, 0.05D, 1.0D);
        PASSIVE_CLERIC_ALLY_HEAL_REQUIRES_LINE_OF_SIGHT = BUILDER.comment("When true, idle clerics only heal allies they can already see, preventing long wall-hugging path attempts.")
                .translation("villagerretaliation.configuration.combat.passiveClericAllyHealRequiresLineOfSight")
                .define("passiveClericAllyHealRequiresLineOfSight", true);
        BUILDER.pop();

        BUILDER.push("wanderer");
        WANDERER_DROP_EMERALDS = BUILDER.comment("Allows wandering traders to drop 1-5 emeralds.")
                .translation("villagerretaliation.configuration.wanderer.dropEmeralds")
                .define("dropEmeralds", true);
        WANDERER_DROP_INVISIBILITY_POTION = BUILDER.comment("Allows wandering traders to drop one invisibility potion.")
                .translation("villagerretaliation.configuration.wanderer.dropInvisibilityPotion")
                .define("dropInvisibilityPotion", true);
        WANDERER_DROP_RANDOM_CURRENT_TRADE = BUILDER.comment("Allows wandering traders to drop a safe copy of one current trade result.")
                .translation("villagerretaliation.configuration.wanderer.dropRandomCurrentTrade")
                .define("dropRandomCurrentTrade", true);
        BUILDER.comment("Chance for the wandering trader random trade result drop.");
        WANDERER_RANDOM_TRADE_DROP_CHANCE = chance("randomTradeDropChance", "villagerretaliation.configuration.wanderer.randomTradeDropChance", 0.50);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private VillagerRetaliationConfig() {
    }

    private static ModConfigSpec.DoubleValue chance(String name, String translationKey, double defaultValue) {
        return BUILDER.translation(translationKey).defineInRange(name, defaultValue, 0.0D, 1.0D);
    }
}
