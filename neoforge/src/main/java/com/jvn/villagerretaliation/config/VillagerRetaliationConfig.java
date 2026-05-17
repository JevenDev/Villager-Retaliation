package com.jvn.villagerretaliation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VillagerRetaliationConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_DROPS;
    public static final ModConfigSpec.BooleanValue ENABLE_WANDERING_TRADER_DROPS;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_RETALIATION;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_REPUTATION;
    public static final ModConfigSpec.BooleanValue ENABLE_VANILLA_GOSSIP_INTEGRATION;
    public static final ModConfigSpec.BooleanValue ENABLE_DESPISED_KILL_ON_SIGHT;
    public static final ModConfigSpec.BooleanValue ENABLE_INTERACTION_SCREEN;
    public static final ModConfigSpec.BooleanValue SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN;
    public static final ModConfigSpec.BooleanValue ENABLE_DIALOGUE_REPUTATION_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_DIALOGUE_CAMERA_FOCUS;
    public static final ModConfigSpec.DoubleValue DIALOGUE_CAMERA_ZOOM_AMOUNT;
    public static final ModConfigSpec.IntValue DIALOGUE_CAMERA_TRANSITION_TICKS;
    public static final ModConfigSpec.BooleanValue FREEZE_VILLAGER_DURING_DIALOGUE;
    public static final ModConfigSpec.DoubleValue MAX_DIALOGUE_DISTANCE;

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
    public static final ModConfigSpec.BooleanValue RETALIATION_WITNESSES_REQUIRE_LINE_OF_SIGHT;
    public static final ModConfigSpec.IntValue AGGRO_DURATION_TICKS;
    public static final ModConfigSpec.BooleanValue NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS;

    public static final ModConfigSpec.IntValue DIRECT_HIT_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_HIT_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_KILL_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_BABY_KILL_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_IRON_GOLEM_KILL_PENALTY;
    public static final ModConfigSpec.IntValue TRADE_REPUTATION_GAIN;
    public static final ModConfigSpec.IntValue MAX_TRADE_REPUTATION_GAIN_PER_VILLAGER_PER_DAY;
    public static final ModConfigSpec.IntValue DIALOGUE_POSITIVE_REPUTATION_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue DIALOGUE_NEGATIVE_REPUTATION_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue GREETING_REPUTATION_GAIN;
    public static final ModConfigSpec.IntValue QUESTION_REPUTATION_GAIN;
    public static final ModConfigSpec.IntValue STORY_REPUTATION_GAIN;
    public static final ModConfigSpec.IntValue JOKE_REPUTATION_GAIN;
    public static final ModConfigSpec.IntValue JOKE_REPUTATION_LOSS;
    public static final ModConfigSpec.IntValue INSULT_REPUTATION_LOSS;
    public static final ModConfigSpec.IntValue FIRST_GREETING_REPUTATION_GAIN;
    public static final ModConfigSpec.IntValue FIRST_INSULT_REPUTATION_LOSS;
    public static final ModConfigSpec.IntValue HEAL_VILLAGER_GAIN;
    public static final ModConfigSpec.IntValue SAVE_VILLAGER_GAIN;
    public static final ModConfigSpec.IntValue POSITIVE_WITNESS_GAIN;
    public static final ModConfigSpec.DoubleValue HOSTILE_MOB_ASSIST_REPUTATION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GOSSIP_REPUTATION_MULTIPLIER;
    public static final ModConfigSpec.IntValue ROYALTY_THRESHOLD;
    public static final ModConfigSpec.IntValue REVERED_THRESHOLD;
    public static final ModConfigSpec.IntValue RESPECTED_THRESHOLD;
    public static final ModConfigSpec.IntValue TRUSTED_THRESHOLD;
    public static final ModConfigSpec.IntValue SUSPICIOUS_THRESHOLD;
    public static final ModConfigSpec.IntValue HOSTILE_THRESHOLD;
    public static final ModConfigSpec.IntValue DESPISED_THRESHOLD;
    public static final ModConfigSpec.IntValue FEARED_THRESHOLD;
    public static final ModConfigSpec.DoubleValue WITNESS_RADIUS;
    public static final ModConfigSpec.DoubleValue GOSSIP_RADIUS;
    public static final ModConfigSpec.DoubleValue DESPISED_SIGHT_RADIUS;
    public static final ModConfigSpec.BooleanValue REPUTATION_DECAY_ENABLED;
    public static final ModConfigSpec.IntValue REPUTATION_DECAY_INTERVAL;
    public static final ModConfigSpec.IntValue REPUTATION_DECAY_AMOUNT;
    public static final ModConfigSpec.IntValue PRUNE_NEUTRAL_ENTRIES_AFTER_DAYS;
    public static final ModConfigSpec.BooleanValue VANILLA_GOSSIP_REQUIRES_LINE_OF_SIGHT;
    public static final ModConfigSpec.BooleanValue ENABLE_REPUTATION_TRADE_PRICING;
    public static final ModConfigSpec.DoubleValue REPUTATION_TRADE_PRICE_SCALE;

    public static final ModConfigSpec.BooleanValue SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY;
    public static final ModConfigSpec.DoubleValue REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_SHOW_TIER;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_SHOW_NUMBER;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_REQUIRE_ADVANCED_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_ONLY_WHEN_SNEAKING;

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
    public static final ModConfigSpec.BooleanValue HOSTILE_TIER_HARASS_THROW_ENABLED;
    public static final ModConfigSpec.IntValue HOSTILE_TIER_HARASS_THROW_MIN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue HOSTILE_TIER_HARASS_THROW_MAX_INTERVAL_TICKS;

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
        ENABLE_VILLAGER_REPUTATION = BUILDER
                .comment("Enables persistent per-villager, per-player reputation memory.")
                .translation("villagerretaliation.configuration.general.enableVillagerReputation")
                .define("enableVillagerReputation", true);
        ENABLE_VANILLA_GOSSIP_INTEGRATION = BUILDER
                .comment("Allows reputation events to supplement vanilla villager gossip where integration is available.")
                .translation("villagerretaliation.configuration.general.enableVanillaGossipIntegration")
                .define("enableVanillaGossipIntegration", true);
        ENABLE_DESPISED_KILL_ON_SIGHT = BUILDER
                .comment("Allows villagers who personally DESPISE a player to target that player on sight.")
                .translation("villagerretaliation.configuration.general.enableDespisedKillOnSight")
                .define("enableDespisedKillOnSight", true);
        ENABLE_INTERACTION_SCREEN = BUILDER
                .comment("Opens Villager Retaliation's interaction screen before adult villager trading.")
                .translation("villagerretaliation.configuration.general.enableInteractionScreen")
                .define("enableInteractionScreen", true);
        SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN = BUILDER
                .comment("Allows sneaking while right-clicking an adult villager to bypass the interaction screen and open normal trading.")
                .translation("villagerretaliation.configuration.general.shiftRightClickBypassesInteractionScreen")
                .define("shiftRightClickBypassesInteractionScreen", true);
        ENABLE_DIALOGUE_REPUTATION_EFFECTS = BUILDER
                .comment("Allows villager dialogue choices to apply small reputation changes.")
                .translation("villagerretaliation.configuration.general.enableDialogueReputationEffects")
                .define("enableDialogueReputationEffects", true);
        ENABLE_DIALOGUE_CAMERA_FOCUS = BUILDER
                .comment("Reserved for subtle client-side dialogue camera polish. Phase 1 keeps this conservative and does not force player view changes.")
                .translation("villagerretaliation.configuration.general.enableDialogueCameraFocus")
                .define("enableDialogueCameraFocus", true);
        DIALOGUE_CAMERA_ZOOM_AMOUNT = BUILDER
                .comment("Reserved subtle dialogue zoom amount for future camera polish.")
                .translation("villagerretaliation.configuration.general.dialogueCameraZoomAmount")
                .defineInRange("dialogueCameraZoomAmount", 0.04D, 0.0D, 0.25D);
        DIALOGUE_CAMERA_TRANSITION_TICKS = BUILDER
                .comment("Reserved transition duration for future dialogue camera polish.")
                .translation("villagerretaliation.configuration.general.dialogueCameraTransitionTicks")
                .defineInRange("dialogueCameraTransitionTicks", 10, 1, 40);
        FREEZE_VILLAGER_DURING_DIALOGUE = BUILDER
                .comment("Stops villager navigation while a Villager Retaliation dialogue conversation is active.")
                .translation("villagerretaliation.configuration.general.freezeVillagerDuringDialogue")
                .define("freezeVillagerDuringDialogue", true);
        MAX_DIALOGUE_DISTANCE = BUILDER
                .comment("Maximum player-to-villager distance in blocks before a dialogue conversation closes.")
                .translation("villagerretaliation.configuration.general.maxDialogueDistance")
                .defineInRange("maxDialogueDistance", 7.0D, 3.0D, 16.0D);
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
        RETALIATION_WITNESSES_REQUIRE_LINE_OF_SIGHT = BUILDER
                .comment("When true, nearby villagers and wandering traders only aggro from witnessed attacks or kills they can see.")
                .translation("villagerretaliation.configuration.retaliation.retaliationWitnessesRequireLineOfSight")
                .define("retaliationWitnessesRequireLineOfSight", true);
        AGGRO_DURATION_TICKS = BUILDER
                .comment("How long, in ticks, temporary villager anger lasts.")
                .translation("villagerretaliation.configuration.retaliation.aggroDurationTicks")
                .defineInRange("aggroDurationTicks", 600, 1, 20 * 60 * 10);
        NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS = BUILDER
                .comment("When true, creative and spectator players are ignored as retaliation targets.")
                .translation("villagerretaliation.configuration.retaliation.nearbyVillagersIgnoreCreativePlayers")
                .define("nearbyVillagersIgnoreCreativePlayers", true);
        BUILDER.pop();

        BUILDER.push("reputation");
        DIRECT_HIT_PENALTY = BUILDER.comment("Reputation change applied to a villager directly hit by a player.")
                .translation("villagerretaliation.configuration.reputation.directHitPenalty")
                .defineInRange("directHitPenalty", -25, -1000, 1000);
        WITNESSED_HIT_PENALTY = BUILDER.comment("Reputation change applied to nearby villagers witnessing a player hit another villager.")
                .translation("villagerretaliation.configuration.reputation.witnessedHitPenalty")
                .defineInRange("witnessedHitPenalty", -8, -1000, 1000);
        WITNESSED_KILL_PENALTY = BUILDER.comment("Reputation change applied to nearby villagers witnessing an adult villager kill.")
                .translation("villagerretaliation.configuration.reputation.witnessedKillPenalty")
                .defineInRange("witnessedKillPenalty", -60, -1000, 1000);
        WITNESSED_BABY_KILL_PENALTY = BUILDER.comment("Reputation change applied to nearby villagers witnessing a baby villager kill.")
                .translation("villagerretaliation.configuration.reputation.witnessedBabyKillPenalty")
                .defineInRange("witnessedBabyKillPenalty", -120, -1000, 1000);
        WITNESSED_IRON_GOLEM_KILL_PENALTY = BUILDER.comment("Reputation change applied to nearby villagers witnessing an iron golem kill.")
                .translation("villagerretaliation.configuration.reputation.witnessedIronGolemKillPenalty")
                .defineInRange("witnessedIronGolemKillPenalty", -60, -1000, 1000);
        TRADE_REPUTATION_GAIN = BUILDER.comment("Reputation gained by trading with a specific villager.")
                .translation("villagerretaliation.configuration.reputation.tradeReputationGain")
                .defineInRange("tradeReputationGain", 2, -1000, 1000);
        MAX_TRADE_REPUTATION_GAIN_PER_VILLAGER_PER_DAY = BUILDER.comment("Maximum positive trade reputation gain per villager per Minecraft day.")
                .translation("villagerretaliation.configuration.reputation.maxTradeReputationGainPerVillagerPerDay")
                .defineInRange("maxTradeReputationGainPerVillagerPerDay", 8, 0, 1000);
        DIALOGUE_POSITIVE_REPUTATION_COOLDOWN_TICKS = BUILDER.comment("Minimum ticks between positive dialogue reputation gains for the same player and villager.")
                .translation("villagerretaliation.configuration.reputation.dialoguePositiveReputationCooldownTicks")
                .defineInRange("dialoguePositiveReputationCooldownTicks", 12000, 20, 24000 * 7);
        DIALOGUE_NEGATIVE_REPUTATION_COOLDOWN_TICKS = BUILDER.comment("Minimum ticks between negative dialogue reputation losses for the same player and villager.")
                .translation("villagerretaliation.configuration.reputation.dialogueNegativeReputationCooldownTicks")
                .defineInRange("dialogueNegativeReputationCooldownTicks", 600, 20, 24000);
        GREETING_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible friendly greeting.")
                .translation("villagerretaliation.configuration.reputation.greetingReputationGain")
                .defineInRange("greetingReputationGain", 1, 0, 1000);
        QUESTION_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible question.")
                .translation("villagerretaliation.configuration.reputation.questionReputationGain")
                .defineInRange("questionReputationGain", 1, 0, 1000);
        STORY_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible story interaction.")
                .translation("villagerretaliation.configuration.reputation.storyReputationGain")
                .defineInRange("storyReputationGain", 1, 0, 1000);
        JOKE_REPUTATION_GAIN = BUILDER.comment("Reputation gained when a joke lands well.")
                .translation("villagerretaliation.configuration.reputation.jokeReputationGain")
                .defineInRange("jokeReputationGain", 1, 0, 1000);
        JOKE_REPUTATION_LOSS = BUILDER.comment("Reputation lost when a joke lands badly.")
                .translation("villagerretaliation.configuration.reputation.jokeReputationLoss")
                .defineInRange("jokeReputationLoss", -1, -1000, 0);
        INSULT_REPUTATION_LOSS = BUILDER.comment("Reputation lost from an insult.")
                .translation("villagerretaliation.configuration.reputation.insultReputationLoss")
                .defineInRange("insultReputationLoss", -3, -1000, 0);
        FIRST_GREETING_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible first greeting.")
                .translation("villagerretaliation.configuration.reputation.firstGreetingReputationGain")
                .defineInRange("firstGreetingReputationGain", 1, 0, 1000);
        FIRST_INSULT_REPUTATION_LOSS = BUILDER.comment("Reputation lost when the first conversation starts with an insult.")
                .translation("villagerretaliation.configuration.reputation.firstInsultReputationLoss")
                .defineInRange("firstInsultReputationLoss", -5, -1000, 0);
        HEAL_VILLAGER_GAIN = BUILDER.comment("Reserved gain for detectable villager healing hooks.")
                .translation("villagerretaliation.configuration.reputation.healVillagerGain")
                .defineInRange("healVillagerGain", 10, -1000, 1000);
        SAVE_VILLAGER_GAIN = BUILDER.comment("Reserved gain for detectable villager rescue hooks.")
                .translation("villagerretaliation.configuration.reputation.saveVillagerGain")
                .defineInRange("saveVillagerGain", 15, -1000, 1000);
        POSITIVE_WITNESS_GAIN = BUILDER.comment("Reserved gain for witnessing village defense by a player.")
                .translation("villagerretaliation.configuration.reputation.positiveWitnessGain")
                .defineInRange("positiveWitnessGain", 10, -1000, 1000);
        HOSTILE_MOB_ASSIST_REPUTATION_MULTIPLIER = BUILDER.comment("Multiplier for positive reputation when the player damaged a hostile mob but did not receive kill credit.")
                .translation("villagerretaliation.configuration.reputation.hostileMobAssistReputationMultiplier")
                .defineInRange("hostileMobAssistReputationMultiplier", 0.5D, 0.0D, 1.0D);
        GOSSIP_REPUTATION_MULTIPLIER = BUILDER.comment("Multiplier applied when reputation spreads by gossip.")
                .translation("villagerretaliation.configuration.reputation.gossipReputationMultiplier")
                .defineInRange("gossipReputationMultiplier", 0.25D, 0.0D, 1.0D);
        ROYALTY_THRESHOLD = BUILDER.comment("Reputation at or above this value is ROYALTY.")
                .translation("villagerretaliation.configuration.reputation.royaltyThreshold")
                .defineInRange("royaltyThreshold", 750, -10000, 10000);
        REVERED_THRESHOLD = BUILDER.comment("Reputation at or above this value is REVERED unless ROYALTY.")
                .translation("villagerretaliation.configuration.reputation.reveredThreshold")
                .defineInRange("reveredThreshold", 400, -10000, 10000);
        RESPECTED_THRESHOLD = BUILDER.comment("Reputation at or above this value is RESPECTED unless REVERED.")
                .translation("villagerretaliation.configuration.reputation.respectedThreshold")
                .defineInRange("respectedThreshold", 250, -10000, 10000);
        TRUSTED_THRESHOLD = BUILDER.comment("Reputation at or above this value is TRUSTED unless RESPECTED.")
                .translation("villagerretaliation.configuration.reputation.trustedThreshold")
                .defineInRange("trustedThreshold", 75, -10000, 10000);
        SUSPICIOUS_THRESHOLD = BUILDER.comment("Reputation at or below this value is SUSPICIOUS unless lower.")
                .translation("villagerretaliation.configuration.reputation.suspiciousThreshold")
                .defineInRange("suspiciousThreshold", -75, -10000, 10000);
        HOSTILE_THRESHOLD = BUILDER.comment("Reputation at or below this value is HOSTILE unless DESPISED.")
                .translation("villagerretaliation.configuration.reputation.hostileThreshold")
                .defineInRange("hostileThreshold", -100, -10000, 10000);
        DESPISED_THRESHOLD = BUILDER.comment("Reputation at or below this value is DESPISED unless FEARED.")
                .translation("villagerretaliation.configuration.reputation.despisedThreshold")
                .defineInRange("despisedThreshold", -250, -10000, 10000);
        FEARED_THRESHOLD = BUILDER.comment("Reputation at or below this value is FEARED.")
                .translation("villagerretaliation.configuration.reputation.fearedThreshold")
                .defineInRange("fearedThreshold", -750, -10000, 10000);
        WITNESS_RADIUS = BUILDER.comment("Radius in blocks for witnessed reputation events.")
                .translation("villagerretaliation.configuration.reputation.witnessRadius")
                .defineInRange("witnessRadius", 24.0D, 0.0D, 128.0D);
        GOSSIP_RADIUS = BUILDER.comment("Radius in blocks for villager-to-villager reputation gossip.")
                .translation("villagerretaliation.configuration.reputation.gossipRadius")
                .defineInRange("gossipRadius", 16.0D, 0.0D, 128.0D);
        DESPISED_SIGHT_RADIUS = BUILDER.comment("Radius in blocks for DESPISED kill-on-sight checks.")
                .translation("villagerretaliation.configuration.reputation.despisedSightRadius")
                .defineInRange("despisedSightRadius", 24.0D, 0.0D, 128.0D);
        REPUTATION_DECAY_ENABLED = BUILDER.comment("Enables lightweight pruning of old neutral reputation entries.")
                .translation("villagerretaliation.configuration.reputation.reputationDecayEnabled")
                .define("reputationDecayEnabled", true);
        REPUTATION_DECAY_INTERVAL = BUILDER.comment("Tick interval for reputation maintenance.")
                .translation("villagerretaliation.configuration.reputation.reputationDecayInterval")
                .defineInRange("reputationDecayInterval", 24000, 20, 24000 * 30);
        REPUTATION_DECAY_AMOUNT = BUILDER.comment("Reserved amount for future active reputation decay.")
                .translation("villagerretaliation.configuration.reputation.reputationDecayAmount")
                .defineInRange("reputationDecayAmount", 1, 0, 1000);
        PRUNE_NEUTRAL_ENTRIES_AFTER_DAYS = BUILDER.comment("Old neutral entries are pruned after this many Minecraft days.")
                .translation("villagerretaliation.configuration.reputation.pruneNeutralEntriesAfterDays")
                .defineInRange("pruneNeutralEntriesAfterDays", 30, 0, 3650);
        VANILLA_GOSSIP_REQUIRES_LINE_OF_SIGHT = BUILDER.comment("When true, witnessed reputation changes require line of sight.")
                .translation("villagerretaliation.configuration.reputation.witnessReputationRequiresLineOfSight")
                .define("witnessReputationRequiresLineOfSight", false);
        ENABLE_REPUTATION_TRADE_PRICING = BUILDER.comment("When true, per-villager reputation dynamically adjusts trade prices for that player.")
                .translation("villagerretaliation.configuration.reputation.enableReputationTradePricing")
                .define("enableReputationTradePricing", true);
        REPUTATION_TRADE_PRICE_SCALE = BUILDER.comment("Multiplier converting mod reputation into vanilla-style special price adjustments.")
                .translation("villagerretaliation.configuration.reputation.reputationTradePriceScale")
                .defineInRange("reputationTradePriceScale", 0.25D, 0.0D, 10.0D);
        BUILDER.pop();

        BUILDER.push("debugOverlay");
        SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY = BUILDER
                .comment("Renders per-villager reputation toward the local player above villager heads. Disabled by default.")
                .translation("villagerretaliation.configuration.debugOverlay.showVillagerReputationDebugOverlay")
                .define("showVillagerReputationDebugOverlay", false);
        REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE = BUILDER
                .comment("Maximum distance in blocks for the reputation debug overlay.")
                .translation("villagerretaliation.configuration.debugOverlay.reputationDebugOverlayMaxDistance")
                .defineInRange("reputationDebugOverlayMaxDistance", 32.0D, 0.0D, 128.0D);
        REPUTATION_DEBUG_OVERLAY_SHOW_TIER = BUILDER
                .comment("Shows the reputation tier in the debug overlay.")
                .translation("villagerretaliation.configuration.debugOverlay.reputationDebugOverlayShowTier")
                .define("reputationDebugOverlayShowTier", true);
        REPUTATION_DEBUG_OVERLAY_SHOW_NUMBER = BUILDER
                .comment("Shows the reputation number in the debug overlay.")
                .translation("villagerretaliation.configuration.debugOverlay.reputationDebugOverlayShowNumber")
                .define("reputationDebugOverlayShowNumber", true);
        REPUTATION_DEBUG_OVERLAY_REQUIRE_ADVANCED_TOOLTIPS = BUILDER
                .comment("Requires advanced tooltips (F3+H) for the reputation debug overlay.")
                .translation("villagerretaliation.configuration.debugOverlay.reputationDebugOverlayRequireAdvancedTooltips")
                .define("reputationDebugOverlayRequireAdvancedTooltips", false);
        REPUTATION_DEBUG_OVERLAY_ONLY_WHEN_SNEAKING = BUILDER
                .comment("Requires the local player to be sneaking for the reputation debug overlay.")
                .translation("villagerretaliation.configuration.debugOverlay.reputationDebugOverlayOnlyWhenSneaking")
                .define("reputationDebugOverlayOnlyWhenSneaking", false);
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
        HOSTILE_TIER_HARASS_THROW_ENABLED = BUILDER.comment("Allows villagers who view a player as HOSTILE or worse to occasionally throw harmless eggs or poisonous potatoes.")
                .translation("villagerretaliation.configuration.combat.hostileTierHarassThrowEnabled")
                .define("hostileTierHarassThrowEnabled", true);
        HOSTILE_TIER_HARASS_THROW_MIN_INTERVAL_TICKS = BUILDER.comment("Minimum ticks between hostile-tier harassment throws.")
                .translation("villagerretaliation.configuration.combat.hostileTierHarassThrowMinIntervalTicks")
                .defineInRange("hostileTierHarassThrowMinIntervalTicks", 200, 20, 20 * 60 * 10);
        HOSTILE_TIER_HARASS_THROW_MAX_INTERVAL_TICKS = BUILDER.comment("Maximum ticks between hostile-tier harassment throws.")
                .translation("villagerretaliation.configuration.combat.hostileTierHarassThrowMaxIntervalTicks")
                .defineInRange("hostileTierHarassThrowMaxIntervalTicks", 360, 20, 20 * 60 * 10);
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
