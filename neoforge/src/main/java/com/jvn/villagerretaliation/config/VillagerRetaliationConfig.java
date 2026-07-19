package com.jvn.villagerretaliation.config;

import com.mojang.logging.LogUtils;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import io.wispforest.owo.config.ConfigWrapper;
import io.wispforest.owo.config.Option;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VillagerRetaliationConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ConfigWrapper<?> CONFIG = loadConfig();

    public static final ConfigValue<Boolean> ENABLE_VILLAGER_DROPS = bind("general.enableVillagerDrops", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_WANDERING_TRADER_DROPS = bind("general.enableWanderingTraderDrops", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VILLAGER_RETALIATION = bind("general.enableVillagerRetaliation", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VILLAGER_REPUTATION = bind("general.enableVillagerReputation", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VANILLA_GOSSIP_INTEGRATION = bind("general.enableVanillaGossipIntegration", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_DESPISED_KILL_ON_SIGHT = bind("general.enableDespisedKillOnSight", Boolean.class);
    public static final ConfigValue<Boolean> DESPISED_KILL_ON_SIGHT_INTERRUPTS_HIRED_WORK = bind("general.despisedKillOnSightInterruptsHiredWork", Boolean.class);
    public static final ConfigValue<ReputationChangeDisplayMode> REPUTATION_CHANGE_DISPLAY_MODE = bind("general.reputationChangeDisplayMode", ReputationChangeDisplayMode.class);
    public static final ConfigValue<ReputationChangeNotificationStyle> REPUTATION_CHANGE_NOTIFICATION_STYLE = bind("general.reputationChangeNotificationStyle", ReputationChangeNotificationStyle.class);
    public static final ConfigValue<ReputationChangeHudPosition> REPUTATION_CHANGE_HUD_POSITION = bind("general.reputationChangeHudPosition", ReputationChangeHudPosition.class);
    public static final ConfigValue<Boolean> COLLAPSE_REPUTATION_CHANGE_NOTIFICATIONS = bind("general.collapseReputationChangeNotifications", Boolean.class);
    public static final ConfigValue<Boolean> SHOW_VILLAGER_NAME_TAGS = bind("general.showVillagerNameTags", Boolean.class);
    public static final ConfigValue<VillagerStatDisplayMode> VILLAGER_STAT_DISPLAY_MODE = bind("general.villagerStatDisplayMode", VillagerStatDisplayMode.class);
    public static final ConfigValue<Boolean> VILLAGER_REPUTATION_HOVER_TOOLTIP_REQUIRES_EMERALD = bind("general.villagerReputationHoverTooltipRequiresEmerald", Boolean.class);
    public static final ConfigValue<Boolean> SHOW_TRADE_GUI_REPUTATION_ICON = bind("general.showTradeGuiReputationIcon", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VILLAGER_DEATH_MESSAGES = bind("general.enableVillagerDeathMessages", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_INTERACTION_SCREEN = bind("dialogue.enableInteractionScreen", Boolean.class);
    public static final ConfigValue<Boolean> SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN = bind("dialogue.shiftRightClickBypassesInteractionScreen", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_DIALOGUE_REPUTATION_EFFECTS = bind("dialogue.enableDialogueReputationEffects", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_DIALOGUE_CAMERA_FOCUS = bind("dialogue.enableDialogueCameraFocus", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_DIALOGUE_CINEMATIC_BARS = bind("dialogue.enableDialogueCinematicBars", Boolean.class);
    public static final ConfigValue<Integer> DIALOGUE_CINEMATIC_BAR_HEIGHT = bind("dialogue.dialogueCinematicBarHeight", Integer.class);
    public static final ConfigValue<Integer> DIALOGUE_CINEMATIC_BAR_MIN_SLANT = bind("dialogue.dialogueCinematicBarMinSlant", Integer.class);
    public static final ConfigValue<Integer> DIALOGUE_CINEMATIC_BAR_MAX_SLANT = bind("dialogue.dialogueCinematicBarMaxSlant", Integer.class);
    public static final ConfigValue<Boolean> ANIMATE_DIALOGUE_CINEMATIC_BARS = bind("dialogue.animateDialogueCinematicBars", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_FORCED_DIALOGUE = bind("dialogue.enableForcedDialogue", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_CONTAINER_FORCED_DIALOGUE = bind("dialogue.enableContainerForcedDialogue", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_CONTAINER_OPEN_REACTION = bind("dialogue.enableContainerOpenReaction", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_RETALIATION_FORCED_DIALOGUE = bind("dialogue.enableRetaliationForcedDialogue", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_PLAYER_ITEM_PROXIMITY_FORCED_DIALOGUE = bind("dialogue.enablePlayerItemProximityForcedDialogue", Boolean.class);
    public static final ConfigValue<Boolean> SEPARATE_VILLAGER_CHAT_MESSAGES = bind("dialogue.separateVillagerChatMessages", Boolean.class);
    public static final ConfigValue<Boolean> SEPARATE_VILLAGER_CHAT_SPEAKERS = bind("dialogue.separateVillagerChatSpeakers", Boolean.class);
    public static final ConfigValue<InteractionChatPosition> INTERACTION_CHAT_POSITION = bind("dialogue.interactionChatPosition", InteractionChatPosition.class);
    public static final ConfigValue<VillagerChatBroadcastMode> VILLAGER_CHAT_BROADCAST_MODE = bind("dialogue.villagerChatBroadcastMode", VillagerChatBroadcastMode.class);
    public static final ConfigValue<Integer> VILLAGER_CHAT_BROADCAST_RADIUS = bind("dialogue.villagerChatBroadcastRadius", Integer.class);
    public static final ConfigValue<Boolean> SHOW_PERSONAL_INTERACTION_DIALOGUE_TO_NEARBY_PLAYERS = bind("dialogue.showPersonalInteractionDialogueToNearbyPlayers", Boolean.class);
    public static final ConfigValue<DialogueTextSpeed> DIALOGUE_TEXT_SPEED = bind("dialogue.dialogueTextSpeed", DialogueTextSpeed.class);
    public static final ConfigValue<Boolean> ENABLE_DIALOGUE_BLIP_AUDIO = bind("dialogue.enableDialogueBlipAudio", Boolean.class);
    public static final ConfigValue<Double> DIALOGUE_BLIP_VOLUME = bind("dialogue.dialogueBlipVolume", Double.class);
    public static final ConfigValue<Double> DIALOGUE_BLIP_MIN_PITCH = bind("dialogue.dialogueBlipMinPitch", Double.class);
    public static final ConfigValue<Double> DIALOGUE_BLIP_MAX_PITCH = bind("dialogue.dialogueBlipMaxPitch", Double.class);
    public static final ConfigValue<Double> DIALOGUE_CAMERA_ZOOM_AMOUNT = bind("dialogue.dialogueCameraZoomAmount", Double.class);
    public static final ConfigValue<Boolean> ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS = bind("dialogue.enableNormalDialogueCameraFocus", Boolean.class);
    public static final ConfigValue<Double> NORMAL_DIALOGUE_CAMERA_ZOOM_AMOUNT = bind("dialogue.normalDialogueCameraZoomAmount", Double.class);
    public static final ConfigValue<Integer> DIALOGUE_CAMERA_TRANSITION_TICKS = bind("dialogue.dialogueCameraTransitionTicks", Integer.class);
    public static final ConfigValue<Boolean> FREEZE_VILLAGER_DURING_DIALOGUE = bind("dialogue.freezeVillagerDuringDialogue", Boolean.class);
    public static final ConfigValue<Double> MAX_DIALOGUE_DISTANCE = bind("dialogue.maxDialogueDistance", Double.class);
    public static final ConfigValue<Double> MAX_FORCED_DIALOGUE_DISTANCE = bind("dialogue.maxForcedDialogueDistance", Double.class);
    public static final ConfigValue<ContainerForcedDialogueTrigger> CONTAINER_FORCED_DIALOGUE_TRIGGER = bind("dialogue.containerForcedDialogueTrigger", ContainerForcedDialogueTrigger.class);
    public static final ConfigValue<ContainerWatchMode> CONTAINER_WATCH_MODE = bind("dialogue.containerWatchMode", ContainerWatchMode.class);
    public static final ConfigValue<Integer> DIALOGUE_POSITIVE_REPUTATION_COOLDOWN_DAYS = bind("dialogue.dialoguePositiveReputationCooldownDays", Integer.class);
    public static final ConfigValue<Integer> REPEATED_QUESTION_POSITIVE_LIMIT = bind("dialogue.repeatedQuestionPositiveLimit", Integer.class);
    public static final ConfigValue<Integer> TRUSTED_REPEATED_DIALOGUE_LIMIT_BONUS = bind("dialogue.trustedRepeatedDialogueLimitBonus", Integer.class);
    public static final ConfigValue<Integer> RESPECTED_REPEATED_DIALOGUE_LIMIT_BONUS = bind("dialogue.respectedRepeatedDialogueLimitBonus", Integer.class);
    public static final ConfigValue<Integer> REVERED_REPEATED_DIALOGUE_LIMIT_BONUS = bind("dialogue.reveredRepeatedDialogueLimitBonus", Integer.class);
    public static final ConfigValue<Integer> ROYALTY_REPEATED_DIALOGUE_LIMIT_BONUS = bind("dialogue.royaltyRepeatedDialogueLimitBonus", Integer.class);
    public static final ConfigValue<Integer> REPEATED_QUESTION_REPUTATION_LOSS = bind("dialogue.repeatedQuestionReputationLoss", Integer.class);
    public static final ConfigValue<Integer> REPEATED_DIALOGUE_OPTION_RESET_TICKS = bind("dialogue.repeatedDialogueOptionResetTicks", Integer.class);
    public static final ConfigValue<Integer> GIFT_ANNOYANCE_REDUCTION_DIVISOR = bind("dialogue.giftAnnoyanceReductionDivisor", Integer.class);
    public static final ConfigValue<Double> MAX_FOLLOW_DISTANCE = bind("dialogue.maxFollowDistance", Double.class);
    public static final ConfigValue<Integer> GREETING_REPUTATION_GAIN = bind("dialogue.greetingReputationGain", Integer.class);
    public static final ConfigValue<Integer> QUESTION_REPUTATION_GAIN = bind("dialogue.questionReputationGain", Integer.class);
    public static final ConfigValue<Integer> STORY_REPUTATION_GAIN = bind("dialogue.storyReputationGain", Integer.class);
    public static final ConfigValue<Integer> JOKE_REPUTATION_GAIN = bind("dialogue.jokeReputationGain", Integer.class);
    public static final ConfigValue<Integer> JOKE_REPUTATION_LOSS = bind("dialogue.jokeReputationLoss", Integer.class);
    public static final ConfigValue<Integer> INSULT_REPUTATION_LOSS = bind("dialogue.insultReputationLoss", Integer.class);
    public static final ConfigValue<Integer> FIRST_GREETING_REPUTATION_GAIN = bind("dialogue.firstGreetingReputationGain", Integer.class);
    public static final ConfigValue<Integer> FIRST_INSULT_REPUTATION_LOSS = bind("dialogue.firstInsultReputationLoss", Integer.class);
    public static final ConfigValue<Boolean> ENABLE_WORLD_TEXT_NOTIFICATIONS = bind("notifications.enableWorldTextNotifications", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_AMBIENT_MURMURS = bind("notifications.enableAmbientMurmurs", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SLEEP_INDICATORS = bind("notifications.enableSleepIndicators", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_DAMAGE_ALERTS = bind("notifications.enableDamageAlerts", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_COMBAT_ALERTS = bind("notifications.enableCombatAlerts", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_TRADE_AND_GIFT_WORLD_TEXT = bind("notifications.enableTradeAndGiftWorldText", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VILLAGER_GIFTS = bind("gifts.enableVillagerGifts", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_HIGH_REPUTATION_GIFTS = bind("gifts.enableHighReputationGifts", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_GIFT_KEEPSAKES = bind("gifts.enableGiftKeepsakes", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VILLAGER_SOCIAL_GRAPH = bind("social.enableVillagerSocialGraph", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VILLAGER_MOODS = bind("social.enableVillagerMoods", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SOCIAL_ATTRIBUTE_BEHAVIOR = bind("social.enableSocialAttributeBehavior", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SOCIAL_ATTRIBUTE_MOOD_EFFECTS = bind("social.enableSocialAttributeMoodEffects", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS = bind("social.enableSocialAttributeDialogueEffects", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SOCIAL_ATTRIBUTE_REPUTATION_EFFECTS = bind("social.enableSocialAttributeReputationEffects", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS = bind("social.enableSocialAttributeRetaliationEffects", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SOCIAL_ATTRIBUTE_GOSSIP_EFFECTS = bind("social.enableSocialAttributeGossipEffects", Boolean.class);
    public static final ConfigValue<Double> SOCIAL_ATTRIBUTE_EFFECT_SCALE = bind("social.socialAttributeEffectScale", Double.class);
    public static final ConfigValue<Boolean> ENABLE_FAMILY_BREEDING_RULES = bind("social.enableFamilyBreedingRules", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_OPPOSITE_GENDER_BREEDING_RULES = bind("social.enableOppositeGenderBreedingRules", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_PARENT_REPUTATION_INHERITANCE = bind("social.enableParentReputationInheritance", Boolean.class);
    public static final ConfigValue<Boolean> BABY_VILLAGERS_DROP_LOOT = bind("balance.babyVillagersDropLoot", Boolean.class);
    public static final ConfigValue<Boolean> REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT = bind("balance.requirePlayerKillForProfessionLoot", Boolean.class);
    public static final ConfigValue<Double> VILLAGER_EMERALD_DROP_CHANCE = bind("balance.villagerEmeraldDropChance", Double.class);
    public static final ConfigValue<Double> VILLAGER_BREAD_DROP_CHANCE = bind("balance.villagerBreadDropChance", Double.class);
    public static final ConfigValue<Double> PROFESSION_DROP_CHANCE = bind("balance.professionDropChance", Double.class);
    public static final ConfigValue<Double> RARE_DROP_CHANCE = bind("balance.rareDropChance", Double.class);
    public static final ConfigValue<Double> VERY_RARE_DROP_CHANCE = bind("balance.veryRareDropChance", Double.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_BASE_DAILY_COST = bind("balance.hiredContractBaseDailyCost", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_MINIMUM_DAILY_COST = bind("balance.hiredContractMinimumDailyCost", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_MAXIMUM_DAILY_COST = bind("balance.hiredContractMaximumDailyCost", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_SKILL_PREMIUM_PER_TEN = bind("balance.hiredContractSkillPremiumPerTen", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_ROYALTY_COST_MODIFIER = bind("balance.hiredContractRoyaltyCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_REVERED_COST_MODIFIER = bind("balance.hiredContractReveredCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_RESPECTED_COST_MODIFIER = bind("balance.hiredContractRespectedCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_TRUSTED_COST_MODIFIER = bind("balance.hiredContractTrustedCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_NEUTRAL_COST_MODIFIER = bind("balance.hiredContractNeutralCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_SUSPICIOUS_COST_MODIFIER = bind("balance.hiredContractSuspiciousCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_HOSTILE_COST_MODIFIER = bind("balance.hiredContractHostileCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_DESPISED_COST_MODIFIER = bind("balance.hiredContractDespisedCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_FEARED_COST_MODIFIER = bind("balance.hiredContractFearedCostModifier", Integer.class);
    public static final ConfigValue<Integer> HIRED_CONTRACT_EARLY_END_REFUND_PERCENT = bind("balance.hiredContractEarlyEndRefundPercent", Integer.class);
    public static final ConfigValue<Integer> HIRED_WORK_TICK_INTERVAL = bind("balance.hiredWorkTickInterval", Integer.class);
    public static final ConfigValue<Integer> HIRED_WORK_NOTICE_COOLDOWN_SECONDS = bind("balance.hiredWorkNoticeCooldownSeconds", Integer.class);
    public static final ConfigValue<Integer> HIRED_WORK_DEFAULT_RADIUS = bind("balance.hiredWorkDefaultRadius", Integer.class);
    public static final ConfigValue<Integer> HIRED_WORK_MAX_RADIUS = bind("balance.hiredWorkMaxRadius", Integer.class);
    public static final ConfigValue<Integer> HIRED_WORK_BASE_EFFICIENCY_PERCENT = bind("balance.hiredWorkBaseEfficiencyPercent", Integer.class);
    public static final ConfigValue<Integer> HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT = bind("balance.hiredWorkMinimumEfficiencyPercent", Integer.class);
    public static final ConfigValue<Integer> HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT = bind("balance.hiredWorkMaximumEfficiencyPercent", Integer.class);
    public static final ConfigValue<Boolean> ENABLE_HIRED_WORK_SKILL_GROWTH = bind("balance.enableHiredWorkSkillGrowth", Boolean.class);
    public static final ConfigValue<Integer> HIRED_BUILDER_MAX_BLOCKS = bind("balance.hiredBuilderMaxBlocks", Integer.class);
    public static final ConfigValue<Integer> HIRED_BUILDER_MAX_SITE_DISTANCE = bind("balance.hiredBuilderMaxSiteDistance", Integer.class);
    public static final ConfigValue<Integer> HIRED_BUILDER_MATERIAL_STORAGE_RADIUS = bind("balance.hiredBuilderMaterialStorageRadius", Integer.class);
    public static final ConfigValue<Integer> HIRED_BUILDER_BASE_EMERALD_COST = bind("balance.hiredBuilderBaseEmeraldCost", Integer.class);
    public static final ConfigValue<Integer> HIRED_BUILDER_EMERALDS_PER_64_BLOCKS = bind("balance.hiredBuilderEmeraldsPer64Blocks", Integer.class);
    public static final ConfigValue<Boolean> HIRED_BUILDER_CAN_REPLACE_SOFT_BLOCKS = bind("balance.hiredBuilderCanReplaceSoftBlocks", Boolean.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_COMBAT = bind("balance.hiredWorkSkillGrowth.combat", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_MINING = bind("balance.hiredWorkSkillGrowth.mining", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_LOGGING = bind("balance.hiredWorkSkillGrowth.logging", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_FARMING = bind("balance.hiredWorkSkillGrowth.farming", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_BREWING = bind("balance.hiredWorkSkillGrowth.brewing", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_COOKING = bind("balance.hiredWorkSkillGrowth.cooking", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_BUILDER = bind("balance.hiredWorkSkillGrowth.builder", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_NAVIGATION = bind("balance.hiredWorkSkillGrowth.navigation", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_ANIMAL_HANDLING = bind("balance.hiredWorkSkillGrowth.animalHandling", Double.class);
    public static final ConfigValue<Double> HIRED_WORK_SKILL_GROWTH_NITWIT = bind("balance.hiredWorkSkillGrowth.nitwit", Double.class);
    public static final ConfigValue<Boolean> ATTACK_AGGROS_ONLY_HIT_VILLAGER = bind("retaliation.attackAggrosOnlyHitVillager", Boolean.class);
    public static final ConfigValue<Boolean> KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS = bind("retaliation.killingVillagerAggrosNearbyVillagers", Boolean.class);
    public static final ConfigValue<Boolean> BABY_VILLAGERS_FLEE_WITNESSED_DEATHS = bind("retaliation.babyVillagersFleeWitnessedDeaths", Boolean.class);
    public static final ConfigValue<Double> VILLAGER_KILL_AGGRO_RADIUS = bind("retaliation.villagerKillAggroRadius", Double.class);
    public static final ConfigValue<Boolean> RETALIATION_WITNESSES_REQUIRE_LINE_OF_SIGHT = bind("retaliation.retaliationWitnessesRequireLineOfSight", Boolean.class);
    public static final ConfigValue<Integer> AGGRO_DURATION_TICKS = bind("retaliation.aggroDurationTicks", Integer.class);
    public static final ConfigValue<Boolean> NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS = bind("retaliation.nearbyVillagersIgnoreCreativePlayers", Boolean.class);
    public static final ConfigValue<Integer> DIRECT_HIT_PENALTY = bind("reputation.directHitPenalty", Integer.class);
    public static final ConfigValue<Integer> WITNESSED_HIT_PENALTY = bind("reputation.witnessedHitPenalty", Integer.class);
    public static final ConfigValue<Integer> WITNESSED_KILL_PENALTY = bind("reputation.witnessedKillPenalty", Integer.class);
    public static final ConfigValue<Integer> WITNESSED_BABY_KILL_PENALTY = bind("reputation.witnessedBabyKillPenalty", Integer.class);
    public static final ConfigValue<Integer> WITNESSED_IRON_GOLEM_KILL_PENALTY = bind("reputation.witnessedIronGolemKillPenalty", Integer.class);
    public static final ConfigValue<Integer> CONTAINER_BREAK_REPUTATION_LOSS = bind("reputation.containerBreakReputationLoss", Integer.class);
    public static final ConfigValue<Integer> GENERATED_CONTAINER_BREAK_ITEM_REPUTATION_LOSS = bind("reputation.generatedContainerBreakItemReputationLoss", Integer.class);
    public static final ConfigValue<Integer> TRADE_REPUTATION_GAIN = bind("reputation.tradeReputationGain", Integer.class);
    public static final ConfigValue<Integer> MAX_TRADE_REPUTATION_GAIN_PER_VILLAGER_PER_DAY = bind("reputation.maxTradeReputationGainPerVillagerPerDay", Integer.class);
    public static final ConfigValue<Integer> SLEEPING_VILLAGER_BOTHER_REPUTATION_LOSS = bind("reputation.sleepingVillagerBotherReputationLoss", Integer.class);
    public static final ConfigValue<Integer> SLEEPING_VILLAGER_BED_BREAK_REPUTATION_LOSS = bind("reputation.sleepingVillagerBedBreakReputationLoss", Integer.class);
    public static final ConfigValue<Integer> HEAL_VILLAGER_GAIN = bind("reputation.healVillagerGain", Integer.class);
    public static final ConfigValue<Integer> SAVE_VILLAGER_GAIN = bind("reputation.saveVillagerGain", Integer.class);
    public static final ConfigValue<Integer> POSITIVE_WITNESS_GAIN = bind("reputation.positiveWitnessGain", Integer.class);
    public static final ConfigValue<Double> HOSTILE_MOB_ASSIST_REPUTATION_MULTIPLIER = bind("reputation.hostileMobAssistReputationMultiplier", Double.class);
    public static final ConfigValue<Double> GOSSIP_REPUTATION_MULTIPLIER = bind("reputation.gossipReputationMultiplier", Double.class);
    public static final ConfigValue<Integer> ROYALTY_THRESHOLD = bind("reputation.royaltyThreshold", Integer.class);
    public static final ConfigValue<Integer> REVERED_THRESHOLD = bind("reputation.reveredThreshold", Integer.class);
    public static final ConfigValue<Integer> RESPECTED_THRESHOLD = bind("reputation.respectedThreshold", Integer.class);
    public static final ConfigValue<Integer> TRUSTED_THRESHOLD = bind("reputation.trustedThreshold", Integer.class);
    public static final ConfigValue<Integer> SUSPICIOUS_THRESHOLD = bind("reputation.suspiciousThreshold", Integer.class);
    public static final ConfigValue<Integer> HOSTILE_THRESHOLD = bind("reputation.hostileThreshold", Integer.class);
    public static final ConfigValue<Integer> DESPISED_THRESHOLD = bind("reputation.despisedThreshold", Integer.class);
    public static final ConfigValue<Integer> FEARED_THRESHOLD = bind("reputation.fearedThreshold", Integer.class);
    public static final ConfigValue<Boolean> ENABLE_PLAYER_RAIDS = bind("playerRaids.enabled", Boolean.class);
    public static final ConfigValue<Integer> PLAYER_RAID_PREPARATION_TICKS = bind("playerRaids.preparationTicks", Integer.class);
    public static final ConfigValue<Integer> PLAYER_RAID_ABANDONMENT_TICKS = bind("playerRaids.abandonmentTicks", Integer.class);
    public static final ConfigValue<Integer> PLAYER_RAID_VILLAGE_COOLDOWN_DAYS = bind("playerRaids.villageCooldownDays", Integer.class);
    public static final ConfigValue<Integer> PLAYER_RAID_BOSS_BAR_RANGE = bind("playerRaids.bossBarRange", Integer.class);
    public static final ConfigValue<Integer> PLAYER_RAID_DEFENDERS_PER_GOLEM = bind("playerRaids.defendersPerGolem", Integer.class);
    public static final ConfigValue<Integer> PLAYER_RAID_MINIMUM_GOLEMS = bind("playerRaids.minimumGolems", Integer.class);
    public static final ConfigValue<Integer> PLAYER_RAID_MAXIMUM_GOLEMS = bind("playerRaids.maximumGolems", Integer.class);
    public static final ConfigValue<Integer> PLAYER_RAID_RAIDERS_PER_BONUS_GOLEM = bind("playerRaids.raidersPerBonusGolem", Integer.class);
    public static final ConfigValue<Double> WITNESS_RADIUS = bind("reputation.witnessRadius", Double.class);
    public static final ConfigValue<Double> GOSSIP_RADIUS = bind("reputation.gossipRadius", Double.class);
    public static final ConfigValue<Double> DESPISED_SIGHT_RADIUS = bind("reputation.despisedSightRadius", Double.class);
    public static final ConfigValue<Boolean> REPUTATION_DECAY_ENABLED = bind("reputation.reputationDecayEnabled", Boolean.class);
    public static final ConfigValue<Integer> REPUTATION_DECAY_INTERVAL = bind("reputation.reputationDecayInterval", Integer.class);
    public static final ConfigValue<Integer> REPUTATION_DECAY_AMOUNT = bind("reputation.reputationDecayAmount", Integer.class);
    public static final ConfigValue<Integer> PRUNE_NEUTRAL_ENTRIES_AFTER_DAYS = bind("reputation.pruneNeutralEntriesAfterDays", Integer.class);
    public static final ConfigValue<Boolean> VANILLA_GOSSIP_REQUIRES_LINE_OF_SIGHT = bind("reputation.witnessReputationRequiresLineOfSight", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_REPUTATION_TRADE_PRICING = bind("reputation.enableReputationTradePricing", Boolean.class);
    public static final ConfigValue<Double> REPUTATION_TRADE_PRICE_SCALE = bind("reputation.reputationTradePriceScale", Double.class);
    public static final ConfigValue<Boolean> ENABLE_SKILL_TRADE_OVERHAUL = bind("trade.enableSkillTradeOverhaul", Boolean.class);
    public static final ConfigValue<Boolean> DISABLE_VILLAGER_WALLET_LIMIT = bind("trade.disableVillagerWalletLimit", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SPECIAL_ORDERS = bind("trade.enableSpecialOrders", Boolean.class);
    public static final ConfigValue<VillagerReputationLevel> SPECIAL_ORDER_MIN_REPUTATION = bind("trade.specialOrderMinReputation", VillagerReputationLevel.class);
    public static final ConfigValue<Integer> SPECIAL_ORDER_WAIT_DAYS = bind("trade.specialOrderWaitDays", Integer.class);
    public static final ConfigValue<Integer> SPECIAL_ORDER_COOLDOWN_DAYS = bind("trade.specialOrderCooldownDays", Integer.class);
    public static final ConfigValue<Boolean> SPECIAL_ORDER_EXTRA_COST_ENABLED = bind("trade.specialOrderExtraCostEnabled", Boolean.class);
    public static final ConfigValue<Integer> SPECIAL_ORDER_MAX_ACTIVE_PER_PLAYER = bind("trade.specialOrderMaxActivePerPlayer", Integer.class);
    public static final ConfigValue<Boolean> SKILL_TRADE_QUALITY_SCALING = bind("trade.skillTradeQualityScaling", Boolean.class);
    public static final ConfigValue<Boolean> SKILL_TRADE_LOW_SKILL_PENALTIES = bind("trade.skillTradeLowSkillPenalties", Boolean.class);
    public static final ConfigValue<Integer> SKILL_TRADE_MAX_ENCHANTMENT_LEVEL = bind("trade.skillTradeMaxEnchantmentLevel", Integer.class);
    public static final ConfigValue<Double> SKILL_TRADE_RARE_CHANCE_MULTIPLIER = bind("trade.skillTradeRareChanceMultiplier", Double.class);
    public static final ConfigValue<Boolean> SKILL_TRADE_ALLOW_HIGH_TIER_EQUIPMENT = bind("trade.skillTradeAllowHighTierEquipment", Boolean.class);
    public static final ConfigValue<Boolean> SKILL_TRADE_ALLOW_SPECIAL_ARROWS = bind("trade.skillTradeAllowSpecialArrows", Boolean.class);
    public static final ConfigValue<Boolean> SKILL_TRADE_ALLOW_RARE_SPECIALTY_TRADES = bind("trade.skillTradeAllowRareSpecialtyTrades", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_SKILL_GROWTH_FROM_TRADING_LEVELS = bind("trade.enableSkillGrowthFromTradingLevels", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_REGULAR_TRADE_SKILL_GROWTH = bind("trade.enableRegularTradeSkillGrowth", Boolean.class);
    public static final ConfigValue<Double> REGULAR_TRADE_SKILL_GROWTH_AMOUNT = bind("trade.regularTradeSkillGrowthAmount", Double.class);
    public static final ConfigValue<Boolean> ENABLE_SKILL_BASED_TRADE_LEVELING = bind("trade.enableSkillBasedTradeLeveling", Boolean.class);
    public static final ConfigValue<Double> SKILL_BASED_TRADE_LEVELING_MIN_MULTIPLIER = bind("trade.skillBasedTradeLevelingMinMultiplier", Double.class);
    public static final ConfigValue<Double> SKILL_BASED_TRADE_LEVELING_MAX_MULTIPLIER = bind("trade.skillBasedTradeLevelingMaxMultiplier", Double.class);
    public static final ConfigValue<Boolean> ENABLE_SKILL_GROWTH_FEEDBACK = bind("trade.enableSkillGrowthFeedback", Boolean.class);
    public static final ConfigValue<Integer> SKILL_GROWTH_PRIMARY_MIN = bind("trade.skillGrowthPrimaryMin", Integer.class);
    public static final ConfigValue<Integer> SKILL_GROWTH_PRIMARY_MAX = bind("trade.skillGrowthPrimaryMax", Integer.class);
    public static final ConfigValue<Boolean> SHOW_VILLAGE_BOUNDS = bind("debugOverlay.showVillageBounds", Boolean.class);
    public static final ConfigValue<Boolean> HIGHLIGHT_RAID_DEFENDERS = bind("debugOverlay.highlightRaidDefenders", Boolean.class);
    public static final ConfigValue<Boolean> SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY = bind("debugOverlay.showVillagerReputationDebugOverlay", Boolean.class);
    public static final ConfigValue<Double> REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE = bind("debugOverlay.reputationDebugOverlayMaxDistance", Double.class);
    public static final ConfigValue<Boolean> REPUTATION_DEBUG_OVERLAY_SHOW_TIER = bind("debugOverlay.reputationDebugOverlayShowTier", Boolean.class);
    public static final ConfigValue<Boolean> REPUTATION_DEBUG_OVERLAY_SHOW_NUMBER = bind("debugOverlay.reputationDebugOverlayShowNumber", Boolean.class);
    public static final ConfigValue<Boolean> REPUTATION_DEBUG_OVERLAY_SHOW_HEALTH = bind("debugOverlay.reputationDebugOverlayShowHealth", Boolean.class);
    public static final ConfigValue<Boolean> REPUTATION_DEBUG_OVERLAY_SHOW_ARMOR = bind("debugOverlay.reputationDebugOverlayShowArmor", Boolean.class);
    public static final ConfigValue<Boolean> REPUTATION_DEBUG_OVERLAY_REQUIRE_ADVANCED_TOOLTIPS = bind("debugOverlay.reputationDebugOverlayRequireAdvancedTooltips", Boolean.class);
    public static final ConfigValue<Boolean> REPUTATION_DEBUG_OVERLAY_ONLY_WHEN_SNEAKING = bind("debugOverlay.reputationDebugOverlayOnlyWhenSneaking", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_VILLAGER_DOWNED_STATE = bind("combat.enableVillagerDownedState", Boolean.class);
    public static final ConfigValue<Boolean> PARTY_VILLAGERS_USE_DOWNED_STATE = bind("combat.partyVillagersUseDownedState", Boolean.class);
    public static final ConfigValue<Integer> DOWNED_MINIMUM_TICKS = bind("combat.downedMinimumTicks", Integer.class);
    public static final ConfigValue<Double> DOWNED_RECOVERY_HEALTH_PERCENT = bind("combat.downedRecoveryHealthPercent", Double.class);
    public static final ConfigValue<Double> DOWNED_THREAT_RADIUS = bind("combat.downedThreatRadius", Double.class);
    public static final ConfigValue<Integer> DOWNED_QUIET_TICKS = bind("combat.downedQuietTicks", Integer.class);
    public static final ConfigValue<Boolean> WEAPONSMITHS_FIGHT_BACK = bind("combat.weaponsmithsFightBack", Boolean.class);
    public static final ConfigValue<Boolean> TOOLSMITHS_FIGHT_BACK = bind("combat.toolsmithsFightBack", Boolean.class);
    public static final ConfigValue<Boolean> ARMORERS_FIGHT_BACK = bind("combat.armorersFightBack", Boolean.class);
    public static final ConfigValue<Boolean> FLETCHERS_FIGHT_BACK = bind("combat.fletchersFightBack", Boolean.class);
    public static final ConfigValue<Boolean> BUTCHERS_FIGHT_BACK = bind("combat.butchersFightBack", Boolean.class);
    public static final ConfigValue<Boolean> VILLAGERS_TARGET_HOSTILE_MOBS = bind("combat.villagersTargetHostileMobs", Boolean.class);
    public static final ConfigValue<Boolean> WANDERING_TRADERS_TARGET_HOSTILE_MOBS = bind("combat.wanderingTradersTargetHostileMobs", Boolean.class);
    public static final ConfigValue<Boolean> VILLAGERS_RETALIATE_AGAINST_HOSTILE_MOBS = bind("combat.villagersRetaliateAgainstHostileMobs", Boolean.class);
    public static final ConfigValue<Boolean> WANDERING_TRADERS_RETALIATE_AGAINST_HOSTILE_MOBS = bind("combat.wanderingTradersRetaliateAgainstHostileMobs", Boolean.class);
    public static final ConfigValue<Boolean> VILLAGERS_STAND_GROUND_AGAINST_HOSTILE_MOBS = bind("combat.villagersStandGroundAgainstHostileMobs", Boolean.class);
    public static final ConfigValue<Boolean> VILLAGERS_FLEE_VISIBLE_CREEPERS = bind("combat.villagersFleeVisibleCreepers", Boolean.class);
    public static final ConfigValue<Boolean> VILLAGERS_PICK_UP_GROUND_WEAPONS = bind("combat.villagersPickUpGroundWeapons", Boolean.class);
    public static final ConfigValue<Boolean> WANDERING_TRADERS_PICK_UP_GROUND_WEAPONS = bind("combat.wanderingTradersPickUpGroundWeapons", Boolean.class);
    public static final ConfigValue<Double> NATURAL_HOSTILE_TARGET_RADIUS = bind("combat.naturalHostileTargetRadius", Double.class);
    public static final ConfigValue<Double> COMBAT_WEAPON_DROP_CHANCE = bind("combat.combatWeaponDropChance", Double.class);
    public static final ConfigValue<Double> COMBAT_WEAPON_ENCHANT_CHANCE = bind("combat.combatWeaponEnchantChance", Double.class);
    public static final ConfigValue<Double> ARMORER_SHIELD_CHANCE_HARD = bind("combat.armorerShieldChanceHard", Double.class);
    public static final ConfigValue<Boolean> CLERICS_USE_POTIONS = bind("combat.clericsUsePotions", Boolean.class);
    public static final ConfigValue<Double> PASSIVE_CLERIC_ALLY_HEAL_RANGE = bind("combat.passiveClericAllyHealRange", Double.class);
    public static final ConfigValue<Double> PASSIVE_CLERIC_ALLY_HEAL_HEALTH_THRESHOLD = bind("combat.passiveClericAllyHealHealthThreshold", Double.class);
    public static final ConfigValue<Boolean> PASSIVE_CLERIC_ALLY_HEAL_REQUIRES_LINE_OF_SIGHT = bind("combat.passiveClericAllyHealRequiresLineOfSight", Boolean.class);
    public static final ConfigValue<Boolean> HOSTILE_TIER_HARASS_THROW_ENABLED = bind("combat.hostileTierHarassThrowEnabled", Boolean.class);
    public static final ConfigValue<Integer> HOSTILE_TIER_HARASS_THROW_MIN_INTERVAL_TICKS = bind("combat.hostileTierHarassThrowMinIntervalTicks", Integer.class);
    public static final ConfigValue<Integer> HOSTILE_TIER_HARASS_THROW_MAX_INTERVAL_TICKS = bind("combat.hostileTierHarassThrowMaxIntervalTicks", Integer.class);
    public static final ConfigValue<Boolean> WANDERER_DROP_EMERALDS = bind("wanderer.dropEmeralds", Boolean.class);
    public static final ConfigValue<Boolean> WANDERER_DROP_INVISIBILITY_POTION = bind("wanderer.dropInvisibilityPotion", Boolean.class);
    public static final ConfigValue<Boolean> WANDERER_DROP_RANDOM_CURRENT_TRADE = bind("wanderer.dropRandomCurrentTrade", Boolean.class);
    public static final ConfigValue<Double> WANDERER_RANDOM_TRADE_DROP_CHANCE = bind("wanderer.randomTradeDropChance", Double.class);
    public static final ConfigValue<Boolean> DISABLE_DIALOGUE_TEXT_EFFECTS = bind("dialogue.disableDialogueTextEffects", Boolean.class);
    public static final ConfigValue<Boolean> ENABLE_QUEST_ITEM_SHADER_HIGHLIGHTS = bind("quest.enableQuestItemShaderHighlights", Boolean.class);
    public static final ConfigValue<QuestItemHighlightMode> QUEST_ITEM_HIGHLIGHT_MODE = bind("quest.questItemHighlightMode", QuestItemHighlightMode.class);

    private VillagerRetaliationConfig() {
    }

    public static void init() {
        // beta.12 used -750 as the default. Move only that exact legacy value so
        // deliberately customized thresholds remain untouched.
        if (FEARED_THRESHOLD.get() == -750) {
            FEARED_THRESHOLD.set(-1000);
            CONFIG.save();
        }
    }

    private static ConfigWrapper<?> loadConfig() {
        ConfigWrapper<?> config = instantiateConfigWrapper();
        if (!migrateLegacyTomlIfNeeded(config)) {
            config.load();
        }
        return config;
    }

    private static ConfigWrapper<?> instantiateConfigWrapper() {
        try {
            Class<?> wrapperClass = Class.forName("com.jvn.villagerretaliation.config.VillagerRetaliationOwoConfig");
            var constructor = wrapperClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (ConfigWrapper<?>) constructor.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | NoSuchMethodException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to initialize generated owo config wrapper", exception);
        }
    }

    private static boolean migrateLegacyTomlIfNeeded(ConfigWrapper<?> config) {
        if (Files.exists(config.fileLocation())) {
            return false;
        }

        Path configDir = config.fileLocation().getParent();
        boolean migrated = migrateLegacyToml(config, configDir.resolve("villagerretaliation-common.toml"));
        migrated |= migrateLegacyToml(config, configDir.resolve("villagerretaliation-client.toml"));

        if (migrated) {
            config.save();
        }

        return migrated;
    }

    private static boolean migrateLegacyToml(ConfigWrapper<?> config, Path path) {
        if (!Files.exists(path)) {
            return false;
        }

        boolean migratedAny = false;
        String currentSection = null;

        try {
            for (String rawLine : Files.readAllLines(path)) {
                String line = stripTomlComment(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim();
                    continue;
                }

                if (currentSection == null) {
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator < 0) {
                    continue;
                }

                String key = currentSection + "." + line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                migratedAny |= applyLegacyValue(config, key, value);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to read legacy config file {}", path, exception);
        }

        return migratedAny;
    }

    private static String stripTomlComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean applyLegacyValue(ConfigWrapper<?> config, String key, String rawValue) {
        Option option = config.optionForKey(new Option.Key(key));
        if (option == null) {
            return false;
        }

        try {
            Object parsedValue = parseLegacyValue(option.clazz(), rawValue);
            option.set(parsedValue);
            return true;
        } catch (Exception exception) {
            LOGGER.warn("Failed to migrate legacy config value {}={} from TOML", key, rawValue, exception);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object parseLegacyValue(Class<?> valueType, String rawValue) {
        String normalized = rawValue.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        if (valueType == boolean.class || valueType == Boolean.class) {
            return Boolean.parseBoolean(normalized);
        }
        if (valueType == int.class || valueType == Integer.class) {
            return Integer.parseInt(normalized);
        }
        if (valueType == double.class || valueType == Double.class) {
            return Double.parseDouble(normalized);
        }
        if (valueType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) valueType.asSubclass(Enum.class), normalized);
        }
        if (valueType == String.class) {
            return normalized;
        }

        throw new IllegalArgumentException("Unsupported legacy config value type: " + valueType.getName());
    }

    private static <T> ConfigValue<T> bind(String key, Class<T> expectedType) {
        Option<?> option = CONFIG.optionForKey(new Option.Key(key));
        if (option == null) {
            throw new IllegalStateException("Missing config option: " + key);
        }
        if (!isCompatibleType(option.clazz(), expectedType)) {
            throw new IllegalStateException("Config option " + key + " expected " + expectedType.getName()
                    + " but found " + option.clazz().getName());
        }
        @SuppressWarnings("unchecked")
        Option<T> typedOption = (Option<T>) option;
        return new ConfigValue<>(typedOption);
    }

    private static boolean isCompatibleType(Class<?> actualType, Class<?> expectedType) {
        return boxed(actualType) == boxed(expectedType);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type;
    }

    public static final class ConfigValue<T> {
        private final Option<T> option;

        private ConfigValue(Option<T> option) {
            this.option = option;
        }

        public T get() {
            return this.option.value();
        }

        public void set(T value) {
            this.option.set(value);
        }

        public Option<T> option() {
            return this.option;
        }
    }
}
