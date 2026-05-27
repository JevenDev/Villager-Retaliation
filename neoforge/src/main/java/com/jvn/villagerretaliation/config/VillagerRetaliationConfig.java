package com.jvn.villagerretaliation.config;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
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
    public static final ModConfigSpec.BooleanValue ENABLE_FORCED_DIALOGUE;
    public static final ModConfigSpec.BooleanValue ENABLE_CONTAINER_FORCED_DIALOGUE;
    public static final ModConfigSpec.BooleanValue ENABLE_RETALIATION_FORCED_DIALOGUE;
    public static final ModConfigSpec.BooleanValue ENABLE_PLAYER_ITEM_PROXIMITY_FORCED_DIALOGUE;
    public static final ModConfigSpec.BooleanValue SEPARATE_VILLAGER_CHAT_MESSAGES;
    public static final ModConfigSpec.BooleanValue SEPARATE_VILLAGER_CHAT_SPEAKERS;
    public static final ModConfigSpec.EnumValue<InteractionChatPosition> INTERACTION_CHAT_POSITION;
    public static final ModConfigSpec.DoubleValue DIALOGUE_CAMERA_ZOOM_AMOUNT;
    public static final ModConfigSpec.IntValue DIALOGUE_CAMERA_TRANSITION_TICKS;
    public static final ModConfigSpec.EnumValue<ReputationChangeDisplayMode> REPUTATION_CHANGE_DISPLAY_MODE;
    public static final ModConfigSpec.EnumValue<ReputationChangeHudPosition> REPUTATION_CHANGE_HUD_POSITION;
    public static final ModConfigSpec.BooleanValue COLLAPSE_REPUTATION_CHANGE_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue FREEZE_VILLAGER_DURING_DIALOGUE;
    public static final ModConfigSpec.DoubleValue MAX_DIALOGUE_DISTANCE;
    public static final ModConfigSpec.DoubleValue MAX_FORCED_DIALOGUE_DISTANCE;
    public static final ModConfigSpec.EnumValue<ContainerForcedDialogueTrigger> CONTAINER_FORCED_DIALOGUE_TRIGGER;
    public static final ModConfigSpec.EnumValue<ContainerWatchMode> CONTAINER_WATCH_MODE;
    public static final ModConfigSpec.BooleanValue VILLAGER_REPUTATION_HOVER_TOOLTIP_REQUIRES_EMERALD;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_DEATH_MESSAGES;
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_TEXT_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue ENABLE_AMBIENT_MURMURS;
    public static final ModConfigSpec.BooleanValue ENABLE_SLEEP_INDICATORS;
    public static final ModConfigSpec.BooleanValue ENABLE_DAMAGE_ALERTS;
    public static final ModConfigSpec.BooleanValue ENABLE_COMBAT_ALERTS;
    public static final ModConfigSpec.BooleanValue ENABLE_TRADE_AND_GIFT_WORLD_TEXT;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_GIFTS;
    public static final ModConfigSpec.BooleanValue ENABLE_HIGH_REPUTATION_GIFTS;
    public static final ModConfigSpec.BooleanValue ENABLE_GIFT_KEEPSAKES;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_SOCIAL_GRAPH;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_MOODS;
    public static final ModConfigSpec.BooleanValue ENABLE_SOCIAL_ATTRIBUTE_BEHAVIOR;
    public static final ModConfigSpec.BooleanValue ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_SOCIAL_ATTRIBUTE_REPUTATION_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS;
    public static final ModConfigSpec.DoubleValue SOCIAL_ATTRIBUTE_EFFECT_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLE_FAMILY_BREEDING_RULES;
    public static final ModConfigSpec.BooleanValue ENABLE_PARENT_REPUTATION_INHERITANCE;

    public static final ModConfigSpec.BooleanValue BABY_VILLAGERS_DROP_LOOT;
    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT;
    public static final ModConfigSpec.DoubleValue VILLAGER_EMERALD_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue VILLAGER_BREAD_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue PROFESSION_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue RARE_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue VERY_RARE_DROP_CHANCE;

    public static final ModConfigSpec.BooleanValue ATTACK_AGGROS_ONLY_HIT_VILLAGER;
    public static final ModConfigSpec.BooleanValue KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS;
    public static final ModConfigSpec.BooleanValue BABY_VILLAGERS_FLEE_WITNESSED_DEATHS;
    public static final ModConfigSpec.DoubleValue VILLAGER_KILL_AGGRO_RADIUS;
    public static final ModConfigSpec.BooleanValue RETALIATION_WITNESSES_REQUIRE_LINE_OF_SIGHT;
    public static final ModConfigSpec.IntValue AGGRO_DURATION_TICKS;
    public static final ModConfigSpec.BooleanValue NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS;

    public static final ModConfigSpec.IntValue DIRECT_HIT_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_HIT_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_KILL_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_BABY_KILL_PENALTY;
    public static final ModConfigSpec.IntValue WITNESSED_IRON_GOLEM_KILL_PENALTY;
    public static final ModConfigSpec.IntValue CONTAINER_BREAK_REPUTATION_LOSS;
    public static final ModConfigSpec.IntValue GENERATED_CONTAINER_BREAK_ITEM_REPUTATION_LOSS;
    public static final ModConfigSpec.IntValue TRADE_REPUTATION_GAIN;
    public static final ModConfigSpec.IntValue MAX_TRADE_REPUTATION_GAIN_PER_VILLAGER_PER_DAY;
    public static final ModConfigSpec.IntValue DIALOGUE_POSITIVE_REPUTATION_COOLDOWN_DAYS;
    public static final ModConfigSpec.IntValue REPEATED_QUESTION_POSITIVE_LIMIT;
    public static final ModConfigSpec.IntValue TRUSTED_REPEATED_DIALOGUE_LIMIT_BONUS;
    public static final ModConfigSpec.IntValue RESPECTED_REPEATED_DIALOGUE_LIMIT_BONUS;
    public static final ModConfigSpec.IntValue REVERED_REPEATED_DIALOGUE_LIMIT_BONUS;
    public static final ModConfigSpec.IntValue ROYALTY_REPEATED_DIALOGUE_LIMIT_BONUS;
    public static final ModConfigSpec.IntValue REPEATED_QUESTION_REPUTATION_LOSS;
    public static final ModConfigSpec.IntValue REPEATED_DIALOGUE_OPTION_RESET_TICKS;
    public static final ModConfigSpec.IntValue GIFT_ANNOYANCE_REDUCTION_DIVISOR;
    public static final ModConfigSpec.DoubleValue MAX_FOLLOW_DISTANCE;
    public static final ModConfigSpec.IntValue SLEEPING_VILLAGER_BOTHER_REPUTATION_LOSS;
    public static final ModConfigSpec.IntValue SLEEPING_VILLAGER_BED_BREAK_REPUTATION_LOSS;
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
    public static final ModConfigSpec.BooleanValue ENABLE_SKILL_TRADE_OVERHAUL;
    public static final ModConfigSpec.BooleanValue ENABLE_SPECIAL_ORDERS;
    public static final ModConfigSpec.EnumValue<VillagerReputationLevel> SPECIAL_ORDER_MIN_REPUTATION;
    public static final ModConfigSpec.IntValue SPECIAL_ORDER_WAIT_DAYS;
    public static final ModConfigSpec.IntValue SPECIAL_ORDER_COOLDOWN_DAYS;
    public static final ModConfigSpec.BooleanValue SPECIAL_ORDER_EXTRA_COST_ENABLED;
    public static final ModConfigSpec.IntValue SPECIAL_ORDER_MAX_ACTIVE_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue SKILL_TRADE_QUALITY_SCALING;
    public static final ModConfigSpec.BooleanValue SKILL_TRADE_LOW_SKILL_PENALTIES;
    public static final ModConfigSpec.IntValue SKILL_TRADE_MAX_ENCHANTMENT_LEVEL;
    public static final ModConfigSpec.DoubleValue SKILL_TRADE_RARE_CHANCE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue SKILL_TRADE_ALLOW_HIGH_TIER_EQUIPMENT;
    public static final ModConfigSpec.BooleanValue SKILL_TRADE_ALLOW_SPECIAL_ARROWS;
    public static final ModConfigSpec.BooleanValue SKILL_TRADE_ALLOW_RARE_SPECIALTY_TRADES;
    public static final ModConfigSpec.BooleanValue ENABLE_SKILL_GROWTH_FROM_TRADING_LEVELS;
    public static final ModConfigSpec.BooleanValue ENABLE_REGULAR_TRADE_SKILL_GROWTH;
    public static final ModConfigSpec.DoubleValue REGULAR_TRADE_SKILL_GROWTH_AMOUNT;
    public static final ModConfigSpec.BooleanValue ENABLE_SKILL_BASED_TRADE_LEVELING;
    public static final ModConfigSpec.DoubleValue SKILL_BASED_TRADE_LEVELING_MIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SKILL_BASED_TRADE_LEVELING_MAX_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_SKILL_GROWTH_FEEDBACK;
    public static final ModConfigSpec.IntValue SKILL_GROWTH_PRIMARY_MIN;
    public static final ModConfigSpec.IntValue SKILL_GROWTH_PRIMARY_MAX;
    public static final ModConfigSpec.DoubleValue SKILL_GROWTH_SECONDARY_CHANCE;
    public static final ModConfigSpec.IntValue SKILL_GROWTH_SECONDARY_MAX;

    public static final ModConfigSpec.BooleanValue SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY;
    public static final ModConfigSpec.DoubleValue REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_SHOW_TIER;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_SHOW_NUMBER;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_SHOW_HEALTH;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_SHOW_ARMOR;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_REQUIRE_ADVANCED_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue REPUTATION_DEBUG_OVERLAY_ONLY_WHEN_SNEAKING;

    public static final ModConfigSpec.BooleanValue WEAPONSMITHS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue TOOLSMITHS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue ARMORERS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue FLETCHERS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue BUTCHERS_FIGHT_BACK;
    public static final ModConfigSpec.BooleanValue VILLAGERS_TARGET_HOSTILE_MOBS;
    public static final ModConfigSpec.BooleanValue WANDERING_TRADERS_TARGET_HOSTILE_MOBS;
    public static final ModConfigSpec.BooleanValue VILLAGERS_RETALIATE_AGAINST_HOSTILE_MOBS;
    public static final ModConfigSpec.BooleanValue WANDERING_TRADERS_RETALIATE_AGAINST_HOSTILE_MOBS;
    public static final ModConfigSpec.BooleanValue VILLAGERS_STAND_GROUND_AGAINST_HOSTILE_MOBS;
    public static final ModConfigSpec.BooleanValue VILLAGERS_FLEE_VISIBLE_CREEPERS;
    public static final ModConfigSpec.BooleanValue VILLAGERS_PICK_UP_GROUND_WEAPONS;
    public static final ModConfigSpec.BooleanValue WANDERING_TRADERS_PICK_UP_GROUND_WEAPONS;
    public static final ModConfigSpec.DoubleValue NATURAL_HOSTILE_TARGET_RADIUS;
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
        REPUTATION_CHANGE_DISPLAY_MODE = BUILDER
                .comment("Where villager reputation tier change messages appear: HUD, Chat, or Off.")
                .translation("villagerretaliation.configuration.general.reputationChangeDisplayMode")
                .defineEnum("reputationChangeDisplayMode", ReputationChangeDisplayMode.HUD);
        REPUTATION_CHANGE_HUD_POSITION = BUILDER
                .comment("Where the HUD reputation change feed is anchored on screen.")
                .translation("villagerretaliation.configuration.general.reputationChangeHudPosition")
                .defineEnum("reputationChangeHudPosition", ReputationChangeHudPosition.TOP_LEFT);
        COLLAPSE_REPUTATION_CHANGE_NOTIFICATIONS = BUILDER
                .comment("Combines same-type villager reputation tier notifications into one counted line.")
                .translation("villagerretaliation.configuration.general.collapseReputationChangeNotifications")
                .define("collapseReputationChangeNotifications", true);
        VILLAGER_REPUTATION_HOVER_TOOLTIP_REQUIRES_EMERALD = BUILDER
                .comment("Requires the player to hold an emerald before the villager reputation hover tooltip appears.")
                .translation("villagerretaliation.configuration.general.villagerReputationHoverTooltipRequiresEmerald")
                .define("villagerReputationHoverTooltipRequiresEmerald", true);
        ENABLE_VILLAGER_DEATH_MESSAGES = BUILDER
                .comment("Allows named villager death messages to be broadcast when vanilla death messages are enabled.")
                .translation("villagerretaliation.configuration.general.enableVillagerDeathMessages")
                .define("enableVillagerDeathMessages", true);
        BUILDER.pop();

        BUILDER.push("dialogue");
        ENABLE_INTERACTION_SCREEN = BUILDER
                .comment("Opens Villager Retaliation's interaction screen before adult villager trading.")
                .translation("villagerretaliation.configuration.dialogue.enableInteractionScreen")
                .define("enableInteractionScreen", true);
        SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN = BUILDER
                .comment("Allows sneaking while right-clicking an adult villager to bypass the interaction screen and open normal trading.")
                .translation("villagerretaliation.configuration.dialogue.shiftRightClickBypassesInteractionScreen")
                .define("shiftRightClickBypassesInteractionScreen", true);
        ENABLE_DIALOGUE_REPUTATION_EFFECTS = BUILDER
                .comment("Allows villager dialogue choices to apply small reputation changes.")
                .translation("villagerretaliation.configuration.dialogue.enableDialogueReputationEffects")
                .define("enableDialogueReputationEffects", true);
        ENABLE_DIALOGUE_CAMERA_FOCUS = BUILDER
                .comment("Enables a subtle client-side FOV zoom while the Villager Retaliation dialogue screen is open.")
                .translation("villagerretaliation.configuration.dialogue.enableDialogueCameraFocus")
                .define("enableDialogueCameraFocus", true);
        ENABLE_FORCED_DIALOGUE = BUILDER
                .comment("Enables event-driven forced dialogue such as witnessed retaliation and watched-container confrontations.")
                .translation("villagerretaliation.configuration.dialogue.enableForcedDialogue")
                .define("enableForcedDialogue", true);
        ENABLE_CONTAINER_FORCED_DIALOGUE = BUILDER
                .comment("Enables watched-container forced dialogue and theft/opening chat.")
                .translation("villagerretaliation.configuration.dialogue.enableContainerForcedDialogue")
                .define("enableContainerForcedDialogue", true);
        ENABLE_RETALIATION_FORCED_DIALOGUE = BUILDER
                .comment("Enables forced dialogue and combat barks when villagers begin retaliation.")
                .translation("villagerretaliation.configuration.dialogue.enableRetaliationForcedDialogue")
                .define("enableRetaliationForcedDialogue", true);
        ENABLE_PLAYER_ITEM_PROXIMITY_FORCED_DIALOGUE = BUILDER
                .comment("Enables forced dialogue and chat callouts when nearby players carry matching held or worn items.")
                .translation("villagerretaliation.configuration.dialogue.enablePlayerItemProximityForcedDialogue")
                .define("enablePlayerItemProximityForcedDialogue", true);
        SEPARATE_VILLAGER_CHAT_MESSAGES = BUILDER
                .comment("Adds a blank chat line between consecutive villager dialogue messages for readability.")
                .translation("villagerretaliation.configuration.dialogue.separateVillagerChatMessages")
                .define("separateVillagerChatMessages", false);
        SEPARATE_VILLAGER_CHAT_SPEAKERS = BUILDER
                .comment("Adds a blank chat line before a villager dialogue header when the speaker changes.")
                .translation("villagerretaliation.configuration.dialogue.separateVillagerChatSpeakers")
                .define("separateVillagerChatSpeakers", true);
        INTERACTION_CHAT_POSITION = BUILDER
                .comment("Where chat is anchored while the Villager Retaliation interaction menu is open.")
                .translation("villagerretaliation.configuration.dialogue.interactionChatPosition")
                .defineEnum("interactionChatPosition", InteractionChatPosition.BOTTOM_LEFT);
        DIALOGUE_CAMERA_ZOOM_AMOUNT = BUILDER
                .comment("FOV zoom amount used while the Villager Retaliation dialogue screen is open.")
                .translation("villagerretaliation.configuration.dialogue.dialogueCameraZoomAmount")
                .defineInRange("dialogueCameraZoomAmount", 0.15D, 0.0D, 0.25D);
        DIALOGUE_CAMERA_TRANSITION_TICKS = BUILDER
                .comment("Ticks used to ease into the dialogue FOV zoom.")
                .translation("villagerretaliation.configuration.dialogue.dialogueCameraTransitionTicks")
                .defineInRange("dialogueCameraTransitionTicks", 3, 1, 40);
        FREEZE_VILLAGER_DURING_DIALOGUE = BUILDER
                .comment("Stops villager navigation while a Villager Retaliation dialogue conversation is active.")
                .translation("villagerretaliation.configuration.dialogue.freezeVillagerDuringDialogue")
                .define("freezeVillagerDuringDialogue", true);
        MAX_DIALOGUE_DISTANCE = BUILDER
                .comment("Maximum player-to-villager distance in blocks before a dialogue conversation closes.")
                .translation("villagerretaliation.configuration.dialogue.maxDialogueDistance")
                .defineInRange("maxDialogueDistance", 7.0D, 3.0D, 16.0D);
        MAX_FORCED_DIALOGUE_DISTANCE = BUILDER
                .comment("Maximum player-to-villager distance in blocks before a forced dialogue conversation closes.")
                .translation("villagerretaliation.configuration.dialogue.maxForcedDialogueDistance")
                .defineInRange("maxForcedDialogueDistance", 16.0D, 3.0D, 32.0D);
        CONTAINER_FORCED_DIALOGUE_TRIGGER = BUILDER
                .comment("When watched containers should trigger forced dialogue: THEFT_ONLY waits until items are removed, OPENING triggers as soon as the container is opened.")
                .translation("villagerretaliation.configuration.dialogue.containerForcedDialogueTrigger")
                .defineEnum("containerForcedDialogueTrigger", ContainerForcedDialogueTrigger.OPENING);
        CONTAINER_WATCH_MODE = BUILDER
                .comment("Which containers can trigger watched-container dialogue: GENERATED_LOOT_ONLY only watches containers with remembered loot tables, ALL_WATCHED_CONTAINERS also watches chests, barrels, and shulker boxes without loot tables.")
                .translation("villagerretaliation.configuration.dialogue.containerWatchMode")
                .defineEnum("containerWatchMode", ContainerWatchMode.GENERATED_LOOT_ONLY);
        DIALOGUE_POSITIVE_REPUTATION_COOLDOWN_DAYS = BUILDER.comment("Minimum Minecraft day changes between positive dialogue reputation gains for the same player and villager.")
                .translation("villagerretaliation.configuration.dialogue.dialoguePositiveReputationCooldownDays")
                .defineInRange("dialoguePositiveReputationCooldownDays", 1, 0, 30);
        REPEATED_QUESTION_POSITIVE_LIMIT = BUILDER.comment("Consecutive Question dialogue uses that can still grant positive reputation before the villager gets tired of it.")
                .translation("villagerretaliation.configuration.dialogue.repeatedQuestionPositiveLimit")
                .defineInRange("repeatedQuestionPositiveLimit", 5, 0, 100);
        TRUSTED_REPEATED_DIALOGUE_LIMIT_BONUS = BUILDER.comment("Extra repeated dialogue uses allowed before Trusted villagers get annoyed.")
                .translation("villagerretaliation.configuration.dialogue.trustedRepeatedDialogueLimitBonus")
                .defineInRange("trustedRepeatedDialogueLimitBonus", 2, 0, 100);
        RESPECTED_REPEATED_DIALOGUE_LIMIT_BONUS = BUILDER.comment("Extra repeated dialogue uses allowed before Respected villagers get annoyed.")
                .translation("villagerretaliation.configuration.dialogue.respectedRepeatedDialogueLimitBonus")
                .defineInRange("respectedRepeatedDialogueLimitBonus", 4, 0, 100);
        REVERED_REPEATED_DIALOGUE_LIMIT_BONUS = BUILDER.comment("Extra repeated dialogue uses allowed before Revered villagers get annoyed.")
                .translation("villagerretaliation.configuration.dialogue.reveredRepeatedDialogueLimitBonus")
                .defineInRange("reveredRepeatedDialogueLimitBonus", 7, 0, 100);
        ROYALTY_REPEATED_DIALOGUE_LIMIT_BONUS = BUILDER.comment("Extra repeated dialogue uses allowed before Royalty-tier villagers get annoyed.")
                .translation("villagerretaliation.configuration.dialogue.royaltyRepeatedDialogueLimitBonus")
                .defineInRange("royaltyRepeatedDialogueLimitBonus", 10, 0, 100);
        REPEATED_QUESTION_REPUTATION_LOSS = BUILDER.comment("Reputation lost when the player keeps repeating Question dialogue after the positive limit.")
                .translation("villagerretaliation.configuration.dialogue.repeatedQuestionReputationLoss")
                .defineInRange("repeatedQuestionReputationLoss", -1, -1000, 0);
        REPEATED_DIALOGUE_OPTION_RESET_TICKS = BUILDER.comment("Game ticks before repeated dialogue option usage resets. Usage also resets at the start of each Minecraft day.")
                .translation("villagerretaliation.configuration.dialogue.repeatedDialogueOptionResetTicks")
                .defineInRange("repeatedDialogueOptionResetTicks", 6000, 1, 24000);
        GIFT_ANNOYANCE_REDUCTION_DIVISOR = BUILDER.comment("Positive gift reputation value needed to reduce repeated dialogue annoyance by one use. Set to 0 to disable gift annoyance reduction.")
                .translation("villagerretaliation.configuration.dialogue.giftAnnoyanceReductionDivisor")
                .defineInRange("giftAnnoyanceReductionDivisor", 8, 0, 1000);
        MAX_FOLLOW_DISTANCE = BUILDER.comment("Maximum player-to-villager distance in blocks before a following villager stops following.")
                .translation("villagerretaliation.configuration.dialogue.maxFollowDistance")
                .defineInRange("maxFollowDistance", 64.0D, 1.0D, 1024.0D);
        GREETING_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible friendly greeting.")
                .translation("villagerretaliation.configuration.dialogue.greetingReputationGain")
                .defineInRange("greetingReputationGain", 1, 0, 1000);
        QUESTION_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible question.")
                .translation("villagerretaliation.configuration.dialogue.questionReputationGain")
                .defineInRange("questionReputationGain", 1, 0, 1000);
        STORY_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible story interaction.")
                .translation("villagerretaliation.configuration.dialogue.storyReputationGain")
                .defineInRange("storyReputationGain", 1, 0, 1000);
        JOKE_REPUTATION_GAIN = BUILDER.comment("Reputation gained when a joke lands well.")
                .translation("villagerretaliation.configuration.dialogue.jokeReputationGain")
                .defineInRange("jokeReputationGain", 1, 0, 1000);
        JOKE_REPUTATION_LOSS = BUILDER.comment("Reputation lost when a joke lands badly.")
                .translation("villagerretaliation.configuration.dialogue.jokeReputationLoss")
                .defineInRange("jokeReputationLoss", -1, -1000, 0);
        INSULT_REPUTATION_LOSS = BUILDER.comment("Reputation lost from an insult.")
                .translation("villagerretaliation.configuration.dialogue.insultReputationLoss")
                .defineInRange("insultReputationLoss", -3, -1000, 0);
        FIRST_GREETING_REPUTATION_GAIN = BUILDER.comment("Reputation gained from an eligible first greeting.")
                .translation("villagerretaliation.configuration.dialogue.firstGreetingReputationGain")
                .defineInRange("firstGreetingReputationGain", 1, 0, 1000);
        FIRST_INSULT_REPUTATION_LOSS = BUILDER.comment("Reputation lost when the first conversation starts with an insult.")
                .translation("villagerretaliation.configuration.dialogue.firstInsultReputationLoss")
                .defineInRange("firstInsultReputationLoss", -5, -1000, 0);
        BUILDER.pop();

        BUILDER.push("notifications");
        ENABLE_WORLD_TEXT_NOTIFICATIONS = BUILDER.comment("Enables villager world-text indicators from notifications, dialogue, gifts, trade, and combat.")
                .translation("villagerretaliation.configuration.notifications.enableWorldTextNotifications")
                .define("enableWorldTextNotifications", true);
        ENABLE_AMBIENT_MURMURS = BUILDER.comment("Enables idle villager murmurs near players.")
                .translation("villagerretaliation.configuration.notifications.enableAmbientMurmurs")
                .define("enableAmbientMurmurs", true);
        ENABLE_SLEEP_INDICATORS = BUILDER.comment("Enables sleeping villager world-text indicators.")
                .translation("villagerretaliation.configuration.notifications.enableSleepIndicators")
                .define("enableSleepIndicators", true);
        ENABLE_DAMAGE_ALERTS = BUILDER.comment("Enables villager damage and death alert world-text indicators.")
                .translation("villagerretaliation.configuration.notifications.enableDamageAlerts")
                .define("enableDamageAlerts", true);
        ENABLE_COMBAT_ALERTS = BUILDER.comment("Enables retaliation, flee, attack-landed, and player-killed combat world-text indicators.")
                .translation("villagerretaliation.configuration.notifications.enableCombatAlerts")
                .define("enableCombatAlerts", true);
        ENABLE_TRADE_AND_GIFT_WORLD_TEXT = BUILDER.comment("Enables trade, gift, and trade-refusal world-text indicators.")
                .translation("villagerretaliation.configuration.notifications.enableTradeAndGiftWorldText")
                .define("enableTradeAndGiftWorldText", true);
        BUILDER.pop();

        BUILDER.push("gifts");
        ENABLE_VILLAGER_GIFTS = BUILDER.comment("Enables the gift flow in the Villager Retaliation interaction screen.")
                .translation("villagerretaliation.configuration.gifts.enableVillagerGifts")
                .define("enableVillagerGifts", true);
        ENABLE_HIGH_REPUTATION_GIFTS = BUILDER.comment("Allows revered and royalty-tier villagers to occasionally give the player reward gifts.")
                .translation("villagerretaliation.configuration.gifts.enableHighReputationGifts")
                .define("enableHighReputationGifts", true);
        ENABLE_GIFT_KEEPSAKES = BUILDER.comment("Allows trusted-or-better villagers to equip liked or loved gifts as keepsakes when possible.")
                .translation("villagerretaliation.configuration.gifts.enableGiftKeepsakes")
                .define("enableGiftKeepsakes", true);
        BUILDER.pop();

        BUILDER.push("social");
        ENABLE_VILLAGER_SOCIAL_GRAPH = BUILDER.comment("Tracks villager family and relationship profiles used by the interaction screen and dialogue conditions.")
                .translation("villagerretaliation.configuration.social.enableVillagerSocialGraph")
                .define("enableVillagerSocialGraph", true);
        ENABLE_VILLAGER_MOODS = BUILDER.comment("Tracks temporary event-driven villager moods used by dialogue and the interaction screen.")
                .translation("villagerretaliation.configuration.social.enableVillagerMoods")
                .define("enableVillagerMoods", true);
        ENABLE_SOCIAL_ATTRIBUTE_BEHAVIOR = BUILDER.comment("Allows villager Social Attributes to subtly affect moods, dialogue, reputation recovery, retaliation decisions, and gossip.")
                .translation("villagerretaliation.configuration.social.enableSocialAttributeBehavior")
                .define("enableSocialAttributeBehavior", true);
        ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS = BUILDER.comment("Allows Social Attributes to shape temporary villager mood intensity and decay.")
                .translation("villagerretaliation.configuration.social.enableSocialAttributeMoodEffects")
                .define("enableSocialAttributeMoodEffects", true);
        ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS = BUILDER.comment("Allows Social Attributes and moods to lightly affect dialogue selection.")
                .translation("villagerretaliation.configuration.social.enableSocialAttributeDialogueEffects")
                .define("enableSocialAttributeDialogueEffects", true);
        ENABLE_SOCIAL_ATTRIBUTE_REPUTATION_EFFECTS = BUILDER.comment("Allows Social Attributes to subtly affect gifts, apologies, and reputation recovery.")
                .translation("villagerretaliation.configuration.social.enableSocialAttributeReputationEffects")
                .define("enableSocialAttributeReputationEffects", true);
        ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS = BUILDER.comment("Allows Social Attributes to subtly affect retaliation and combat readiness decisions.")
                .translation("villagerretaliation.configuration.social.enableSocialAttributeRetaliationEffects")
                .define("enableSocialAttributeRetaliationEffects", true);
        ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS = BUILDER.comment("Allows Social Attributes to subtly affect gossip and social reputation ripple.")
                .translation("villagerretaliation.configuration.social.enableSocialAttributeGossipEffects")
                .define("enableSocialAttributeGossipEffects", true);
        SOCIAL_ATTRIBUTE_EFFECT_SCALE = BUILDER.comment("Scales all Social Attribute behavior modifiers. 1.0 keeps the intended subtle Beta.12 values.")
                .translation("villagerretaliation.configuration.social.socialAttributeEffectScale")
                .defineInRange("socialAttributeEffectScale", 1.0D, 0.0D, 2.0D);
        ENABLE_FAMILY_BREEDING_RULES = BUILDER.comment("Prevents villager breeding pairs that violate tracked family relationship rules.")
                .translation("villagerretaliation.configuration.social.enableFamilyBreedingRules")
                .define("enableFamilyBreedingRules", true);
        ENABLE_PARENT_REPUTATION_INHERITANCE = BUILDER.comment("Allows baby villagers to inherit parent reputation entries when born or adopted.")
                .translation("villagerretaliation.configuration.social.enableParentReputationInheritance")
                .define("enableParentReputationInheritance", true);
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
        BABY_VILLAGERS_FLEE_WITNESSED_DEATHS = BUILDER
                .comment("When true, baby villagers that witness a villager death panic and flee like nitwit alarm witnesses. Disable to keep the original adult/nitwit-only witness behavior.")
                .translation("villagerretaliation.configuration.retaliation.babyVillagersFleeWitnessedDeaths")
                .define("babyVillagersFleeWitnessedDeaths", true);
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
        CONTAINER_BREAK_REPUTATION_LOSS = BUILDER.comment("Base reputation change applied to a villager witnessing a player break a watched container.")
                .translation("villagerretaliation.configuration.reputation.containerBreakReputationLoss")
                .defineInRange("containerBreakReputationLoss", -30, -1000, 0);
        GENERATED_CONTAINER_BREAK_ITEM_REPUTATION_LOSS = BUILDER.comment("Additional reputation change per item stack count dropped from a generated watched container when broken.")
                .translation("villagerretaliation.configuration.reputation.generatedContainerBreakItemReputationLoss")
                .defineInRange("generatedContainerBreakItemReputationLoss", -1, -1000, 0);
        TRADE_REPUTATION_GAIN = BUILDER.comment("Reputation gained by trading with a specific villager.")
                .translation("villagerretaliation.configuration.reputation.tradeReputationGain")
                .defineInRange("tradeReputationGain", 1, -1000, 1000);
        MAX_TRADE_REPUTATION_GAIN_PER_VILLAGER_PER_DAY = BUILDER.comment("Maximum positive trade reputation gain per villager per Minecraft day.")
                .translation("villagerretaliation.configuration.reputation.maxTradeReputationGainPerVillagerPerDay")
                .defineInRange("maxTradeReputationGainPerVillagerPerDay", 8, 0, 1000);
        SLEEPING_VILLAGER_BOTHER_REPUTATION_LOSS = BUILDER.comment("Reputation lost when a player tries to interact with a sleeping villager.")
                .translation("villagerretaliation.configuration.reputation.sleepingVillagerBotherReputationLoss")
                .defineInRange("sleepingVillagerBotherReputationLoss", -2, -1000, 0);
        SLEEPING_VILLAGER_BED_BREAK_REPUTATION_LOSS = BUILDER.comment("Reputation lost when a player breaks the bed of a sleeping villager.")
                .translation("villagerretaliation.configuration.reputation.sleepingVillagerBedBreakReputationLoss")
                .defineInRange("sleepingVillagerBedBreakReputationLoss", -15, -1000, 0);
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

        BUILDER.push("trade");
        ENABLE_SKILL_TRADE_OVERHAUL = BUILDER.comment("When true, villager skill ranks add profession-specific quality and specialty trade offers.")
                .translation("villagerretaliation.configuration.trade.enableSkillTradeOverhaul")
                .define("enableSkillTradeOverhaul", true);
        ENABLE_SPECIAL_ORDERS = BUILDER.comment("When true, high-reputation players can place targeted Special Orders for targetable skill-trade definitions.")
                .translation("villagerretaliation.configuration.trade.enableSpecialOrders")
                .define("enableSpecialOrders", true);
        SPECIAL_ORDER_MIN_REPUTATION = BUILDER.comment("Minimum per-villager reputation tier needed to place Special Orders.")
                .translation("villagerretaliation.configuration.trade.specialOrderMinReputation")
                .defineEnum("specialOrderMinReputation", VillagerReputationLevel.REVERED);
        SPECIAL_ORDER_WAIT_DAYS = BUILDER.comment("Default Minecraft days before an accepted Special Order becomes ready when a trade entry does not override wait_days.")
                .translation("villagerretaliation.configuration.trade.specialOrderWaitDays")
                .defineInRange("specialOrderWaitDays", 2, 1, 30);
        SPECIAL_ORDER_COOLDOWN_DAYS = BUILDER.comment("Default Minecraft days before the same player can place another Special Order with the same villager when a trade entry does not override cooldown_days.")
                .translation("villagerretaliation.configuration.trade.specialOrderCooldownDays")
                .defineInRange("specialOrderCooldownDays", 3, 0, 30);
        SPECIAL_ORDER_EXTRA_COST_ENABLED = BUILDER.comment("When true, Special Order request metadata can charge an extra item cost when the request is accepted.")
                .translation("villagerretaliation.configuration.trade.specialOrderExtraCostEnabled")
                .define("specialOrderExtraCostEnabled", false);
        SPECIAL_ORDER_MAX_ACTIVE_PER_PLAYER = BUILDER.comment("Maximum active Special Orders each player can have with one villager at a time.")
                .translation("villagerretaliation.configuration.trade.specialOrderMaxActivePerPlayer")
                .defineInRange("specialOrderMaxActivePerPlayer", 3, 1, 3);
        SKILL_TRADE_QUALITY_SCALING = BUILDER.comment("When true, skill trade entries with quality_scaling enabled adjust count, base emerald cost, stock, rare chance, XP, and enchantment quality by skill rank.")
                .translation("villagerretaliation.configuration.trade.skillTradeQualityScaling")
                .define("skillTradeQualityScaling", true);
        SKILL_TRADE_LOW_SKILL_PENALTIES = BUILDER.comment("When true, low skill ranks can reduce skill trade counts, stock, rare chance, and increase base emerald costs for quality-scaled entries.")
                .translation("villagerretaliation.configuration.trade.skillTradeLowSkillPenalties")
                .define("skillTradeLowSkillPenalties", true);
        SKILL_TRADE_MAX_ENCHANTMENT_LEVEL = BUILDER.comment("Maximum enchantment level used by skill-based trade rewards.")
                .translation("villagerretaliation.configuration.trade.skillTradeMaxEnchantmentLevel")
                .defineInRange("skillTradeMaxEnchantmentLevel", 3, 1, 5);
        SKILL_TRADE_RARE_CHANCE_MULTIPLIER = BUILDER.comment("Multiplier applied to rare skill-based offer chances.")
                .translation("villagerretaliation.configuration.trade.skillTradeRareChanceMultiplier")
                .defineInRange("skillTradeRareChanceMultiplier", 1.0D, 0.0D, 10.0D);
        SKILL_TRADE_ALLOW_HIGH_TIER_EQUIPMENT = BUILDER.comment("Allows Expert and Master smithing/crafting trades to offer diamond-tier equipment.")
                .translation("villagerretaliation.configuration.trade.skillTradeAllowHighTierEquipment")
                .define("skillTradeAllowHighTierEquipment", true);
        SKILL_TRADE_ALLOW_SPECIAL_ARROWS = BUILDER.comment("Allows high Archery fletcher trades to offer spectral arrows.")
                .translation("villagerretaliation.configuration.trade.skillTradeAllowSpecialArrows")
                .define("skillTradeAllowSpecialArrows", true);
        SKILL_TRADE_ALLOW_RARE_SPECIALTY_TRADES = BUILDER.comment("Allows rare Master-level skill specialty offers such as saddles, nautilus shells, and golden apples.")
                .translation("villagerretaliation.configuration.trade.skillTradeAllowRareSpecialtyTrades")
                .define("skillTradeAllowRareSpecialtyTrades", true);
        ENABLE_SKILL_GROWTH_FROM_TRADING_LEVELS = BUILDER.comment("When true, villagers gain small profession skill increases once per newly reached vanilla trade level.")
                .translation("villagerretaliation.configuration.trade.enableSkillGrowthFromTradingLevels")
                .define("enableSkillGrowthFromTradingLevels", true);
        ENABLE_REGULAR_TRADE_SKILL_GROWTH = BUILDER.comment("When true, normal villager trades add slow fractional progress toward the villager's primary profession skill.")
                .translation("villagerretaliation.configuration.trade.enableRegularTradeSkillGrowth")
                .define("enableRegularTradeSkillGrowth", true);
        REGULAR_TRADE_SKILL_GROWTH_AMOUNT = BUILDER.comment("Primary profession skill progress added by each normal villager trade. Whole points are awarded when saved progress reaches 1.0.")
                .translation("villagerretaliation.configuration.trade.regularTradeSkillGrowthAmount")
                .defineInRange("regularTradeSkillGrowthAmount", 0.1D, 0.0D, 10.0D);
        ENABLE_SKILL_BASED_TRADE_LEVELING = BUILDER.comment("When true, a villager's primary profession skill slows or restores vanilla villager trade-level XP gain.")
                .translation("villagerretaliation.configuration.trade.enableSkillBasedTradeLeveling")
                .define("enableSkillBasedTradeLeveling", true);
        SKILL_BASED_TRADE_LEVELING_MIN_MULTIPLIER = BUILDER.comment("Villager trade-level XP multiplier at the lowest primary profession skill. 0.2 means very low-skill villagers level about five times slower.")
                .translation("villagerretaliation.configuration.trade.skillBasedTradeLevelingMinMultiplier")
                .defineInRange("skillBasedTradeLevelingMinMultiplier", 0.2D, 0.0D, 1.0D);
        SKILL_BASED_TRADE_LEVELING_MAX_MULTIPLIER = BUILDER.comment("Villager trade-level XP multiplier at the highest primary profession skill. Keep at 1.0 for high-skill villagers to level at vanilla speed.")
                .translation("villagerretaliation.configuration.trade.skillBasedTradeLevelingMaxMultiplier")
                .defineInRange("skillBasedTradeLevelingMaxMultiplier", 1.0D, 0.0D, 1.0D);
        ENABLE_SKILL_GROWTH_FEEDBACK = BUILDER.comment("Shows a subtle actionbar message to the trading player when a villager improves a skill through trade growth.")
                .translation("villagerretaliation.configuration.trade.enableSkillGrowthFeedback")
                .define("enableSkillGrowthFeedback", true);
        SKILL_GROWTH_PRIMARY_MIN = BUILDER.comment("Minimum primary skill points awarded by a trade-level milestone.")
                .translation("villagerretaliation.configuration.trade.skillGrowthPrimaryMin")
                .defineInRange("skillGrowthPrimaryMin", 1, 0, 10);
        SKILL_GROWTH_PRIMARY_MAX = BUILDER.comment("Maximum primary skill points awarded by a trade-level milestone.")
                .translation("villagerretaliation.configuration.trade.skillGrowthPrimaryMax")
                .defineInRange("skillGrowthPrimaryMax", 5, 0, 10);
        SKILL_GROWTH_SECONDARY_CHANCE = BUILDER.comment("Chance that a newly reached trade-level milestone also improves one related secondary skill.")
                .translation("villagerretaliation.configuration.trade.skillGrowthSecondaryChance")
                .defineInRange("skillGrowthSecondaryChance", 0.35D, 0.0D, 1.0D);
        SKILL_GROWTH_SECONDARY_MAX = BUILDER.comment("Maximum secondary skill points awarded by a trade-level milestone.")
                .translation("villagerretaliation.configuration.trade.skillGrowthSecondaryMax")
                .defineInRange("skillGrowthSecondaryMax", 1, 0, 5);
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
        REPUTATION_DEBUG_OVERLAY_SHOW_HEALTH = BUILDER
                .comment("Shows current and maximum health under the reputation debug overlay.")
                .translation("villagerretaliation.configuration.debugOverlay.reputationDebugOverlayShowHealth")
                .define("reputationDebugOverlayShowHealth", false);
        REPUTATION_DEBUG_OVERLAY_SHOW_ARMOR = BUILDER
                .comment("Shows the current armor value under the reputation debug overlay.")
                .translation("villagerretaliation.configuration.debugOverlay.reputationDebugOverlayShowArmor")
                .define("reputationDebugOverlayShowArmor", false);
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
        VILLAGERS_TARGET_HOSTILE_MOBS = BUILDER.comment("Allows adult villagers to proactively target visible hostile mobs.")
                .translation("villagerretaliation.configuration.combat.villagersTargetHostileMobs")
                .define("villagersTargetHostileMobs", true);
        WANDERING_TRADERS_TARGET_HOSTILE_MOBS = BUILDER.comment("Allows wandering traders to proactively target visible hostile mobs.")
                .translation("villagerretaliation.configuration.combat.wanderingTradersTargetHostileMobs")
                .define("wanderingTradersTargetHostileMobs", true);
        VILLAGERS_RETALIATE_AGAINST_HOSTILE_MOBS = BUILDER.comment("Allows adult villagers damaged by hostile mobs to fight back. Disable to keep monster encounters closer to vanilla fleeing behavior.")
                .translation("villagerretaliation.configuration.combat.villagersRetaliateAgainstHostileMobs")
                .define("villagersRetaliateAgainstHostileMobs", true);
        WANDERING_TRADERS_RETALIATE_AGAINST_HOSTILE_MOBS = BUILDER.comment("Allows wandering traders damaged by hostile mobs to fight back.")
                .translation("villagerretaliation.configuration.combat.wanderingTradersRetaliateAgainstHostileMobs")
                .define("wanderingTradersRetaliateAgainstHostileMobs", true);
        VILLAGERS_STAND_GROUND_AGAINST_HOSTILE_MOBS = BUILDER.comment("Allows armed villagers to suppress vanilla panic and stand their ground against hostile mobs. Disable if villagers should usually keep fleeing monsters.")
                .translation("villagerretaliation.configuration.combat.villagersStandGroundAgainstHostileMobs")
                .define("villagersStandGroundAgainstHostileMobs", true);
        VILLAGERS_FLEE_VISIBLE_CREEPERS = BUILDER.comment("Makes villagers avoid visible creepers instead of trying to fight them.")
                .translation("villagerretaliation.configuration.combat.villagersFleeVisibleCreepers")
                .define("villagersFleeVisibleCreepers", true);
        VILLAGERS_PICK_UP_GROUND_WEAPONS = BUILDER.comment("Allows villagers to pick up nearby dropped weapons while threatened.")
                .translation("villagerretaliation.configuration.combat.villagersPickUpGroundWeapons")
                .define("villagersPickUpGroundWeapons", true);
        WANDERING_TRADERS_PICK_UP_GROUND_WEAPONS = BUILDER.comment("Allows wandering traders to pick up nearby dropped weapons while threatened.")
                .translation("villagerretaliation.configuration.combat.wanderingTradersPickUpGroundWeapons")
                .define("wanderingTradersPickUpGroundWeapons", true);
        NATURAL_HOSTILE_TARGET_RADIUS = BUILDER.comment("Maximum range in blocks for villagers and wandering traders to proactively target visible hostile mobs. The explicit hostile mob targeting toggles must also be enabled.")
                .translation("villagerretaliation.configuration.combat.naturalHostileTargetRadius")
                .defineInRange("naturalHostileTargetRadius", 16.0D, 0.0D, 64.0D);
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
