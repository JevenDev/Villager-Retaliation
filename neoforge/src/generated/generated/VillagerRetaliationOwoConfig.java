package com.jvn.villagerretaliation.config;

import blue.endless.jankson.Jankson;
import io.wispforest.owo.config.ConfigWrapper;
import io.wispforest.owo.config.Option;
import io.wispforest.owo.util.Observable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class VillagerRetaliationOwoConfig extends ConfigWrapper<com.jvn.villagerretaliation.config.VillagerRetaliationConfigModel> {

    public final Keys keys = new Keys();

    private final Option<java.lang.Boolean> general_enableVillagerDrops = this.optionForKey(this.keys.general_enableVillagerDrops);
    private final Option<java.lang.Boolean> general_enableWanderingTraderDrops = this.optionForKey(this.keys.general_enableWanderingTraderDrops);
    private final Option<java.lang.Boolean> general_enableVillagerRetaliation = this.optionForKey(this.keys.general_enableVillagerRetaliation);
    private final Option<java.lang.Boolean> general_enableVillagerReputation = this.optionForKey(this.keys.general_enableVillagerReputation);
    private final Option<java.lang.Boolean> general_enableVanillaGossipIntegration = this.optionForKey(this.keys.general_enableVanillaGossipIntegration);
    private final Option<java.lang.Boolean> general_enableDespisedKillOnSight = this.optionForKey(this.keys.general_enableDespisedKillOnSight);
    private final Option<java.lang.Boolean> general_despisedKillOnSightInterruptsHiredWork = this.optionForKey(this.keys.general_despisedKillOnSightInterruptsHiredWork);
    private final Option<com.jvn.villagerretaliation.config.ReputationChangeDisplayMode> general_reputationChangeDisplayMode = this.optionForKey(this.keys.general_reputationChangeDisplayMode);
    private final Option<com.jvn.villagerretaliation.config.ReputationChangeNotificationStyle> general_reputationChangeNotificationStyle = this.optionForKey(this.keys.general_reputationChangeNotificationStyle);
    private final Option<com.jvn.villagerretaliation.config.ReputationChangeHudPosition> general_reputationChangeHudPosition = this.optionForKey(this.keys.general_reputationChangeHudPosition);
    private final Option<java.lang.Boolean> general_collapseReputationChangeNotifications = this.optionForKey(this.keys.general_collapseReputationChangeNotifications);
    private final Option<java.lang.Boolean> general_showVillagerNameTags = this.optionForKey(this.keys.general_showVillagerNameTags);
    private final Option<com.jvn.villagerretaliation.config.VillagerStatDisplayMode> general_villagerStatDisplayMode = this.optionForKey(this.keys.general_villagerStatDisplayMode);
    private final Option<java.lang.Boolean> general_villagerReputationHoverTooltipRequiresEmerald = this.optionForKey(this.keys.general_villagerReputationHoverTooltipRequiresEmerald);
    private final Option<java.lang.Boolean> general_showTradeGuiReputationIcon = this.optionForKey(this.keys.general_showTradeGuiReputationIcon);
    private final Option<java.lang.Boolean> general_enableVillagerDeathMessages = this.optionForKey(this.keys.general_enableVillagerDeathMessages);
    private final Option<java.lang.Boolean> dialogue_enableInteractionScreen = this.optionForKey(this.keys.dialogue_enableInteractionScreen);
    private final Option<java.lang.Boolean> dialogue_shiftRightClickBypassesInteractionScreen = this.optionForKey(this.keys.dialogue_shiftRightClickBypassesInteractionScreen);
    private final Option<java.lang.Boolean> dialogue_enableDialogueReputationEffects = this.optionForKey(this.keys.dialogue_enableDialogueReputationEffects);
    private final Option<java.lang.Boolean> dialogue_enableDialogueCameraFocus = this.optionForKey(this.keys.dialogue_enableDialogueCameraFocus);
    private final Option<java.lang.Boolean> dialogue_enableDialogueCinematicBars = this.optionForKey(this.keys.dialogue_enableDialogueCinematicBars);
    private final Option<java.lang.Integer> dialogue_dialogueCinematicBarHeight = this.optionForKey(this.keys.dialogue_dialogueCinematicBarHeight);
    private final Option<java.lang.Integer> dialogue_dialogueCinematicBarMinSlant = this.optionForKey(this.keys.dialogue_dialogueCinematicBarMinSlant);
    private final Option<java.lang.Integer> dialogue_dialogueCinematicBarMaxSlant = this.optionForKey(this.keys.dialogue_dialogueCinematicBarMaxSlant);
    private final Option<java.lang.Boolean> dialogue_animateDialogueCinematicBars = this.optionForKey(this.keys.dialogue_animateDialogueCinematicBars);
    private final Option<java.lang.Boolean> dialogue_enableForcedDialogue = this.optionForKey(this.keys.dialogue_enableForcedDialogue);
    private final Option<java.lang.Boolean> dialogue_enableContainerForcedDialogue = this.optionForKey(this.keys.dialogue_enableContainerForcedDialogue);
    private final Option<java.lang.Boolean> dialogue_enableContainerOpenReaction = this.optionForKey(this.keys.dialogue_enableContainerOpenReaction);
    private final Option<java.lang.Boolean> dialogue_enableRetaliationForcedDialogue = this.optionForKey(this.keys.dialogue_enableRetaliationForcedDialogue);
    private final Option<java.lang.Boolean> dialogue_enablePlayerItemProximityForcedDialogue = this.optionForKey(this.keys.dialogue_enablePlayerItemProximityForcedDialogue);
    private final Option<java.lang.Boolean> dialogue_separateVillagerChatMessages = this.optionForKey(this.keys.dialogue_separateVillagerChatMessages);
    private final Option<java.lang.Boolean> dialogue_separateVillagerChatSpeakers = this.optionForKey(this.keys.dialogue_separateVillagerChatSpeakers);
    private final Option<com.jvn.villagerretaliation.config.InteractionChatPosition> dialogue_interactionChatPosition = this.optionForKey(this.keys.dialogue_interactionChatPosition);
    private final Option<com.jvn.villagerretaliation.config.VillagerChatBroadcastMode> dialogue_villagerChatBroadcastMode = this.optionForKey(this.keys.dialogue_villagerChatBroadcastMode);
    private final Option<java.lang.Integer> dialogue_villagerChatBroadcastRadius = this.optionForKey(this.keys.dialogue_villagerChatBroadcastRadius);
    private final Option<java.lang.Boolean> dialogue_showPersonalInteractionDialogueToNearbyPlayers = this.optionForKey(this.keys.dialogue_showPersonalInteractionDialogueToNearbyPlayers);
    private final Option<com.jvn.villagerretaliation.config.DialogueTextSpeed> dialogue_dialogueTextSpeed = this.optionForKey(this.keys.dialogue_dialogueTextSpeed);
    private final Option<java.lang.Boolean> dialogue_enableDialogueBlipAudio = this.optionForKey(this.keys.dialogue_enableDialogueBlipAudio);
    private final Option<java.lang.Double> dialogue_dialogueBlipVolume = this.optionForKey(this.keys.dialogue_dialogueBlipVolume);
    private final Option<java.lang.Double> dialogue_dialogueBlipMinPitch = this.optionForKey(this.keys.dialogue_dialogueBlipMinPitch);
    private final Option<java.lang.Double> dialogue_dialogueBlipMaxPitch = this.optionForKey(this.keys.dialogue_dialogueBlipMaxPitch);
    private final Option<java.lang.Double> dialogue_dialogueCameraZoomAmount = this.optionForKey(this.keys.dialogue_dialogueCameraZoomAmount);
    private final Option<java.lang.Boolean> dialogue_enableNormalDialogueCameraFocus = this.optionForKey(this.keys.dialogue_enableNormalDialogueCameraFocus);
    private final Option<java.lang.Double> dialogue_normalDialogueCameraZoomAmount = this.optionForKey(this.keys.dialogue_normalDialogueCameraZoomAmount);
    private final Option<java.lang.Integer> dialogue_dialogueCameraTransitionTicks = this.optionForKey(this.keys.dialogue_dialogueCameraTransitionTicks);
    private final Option<java.lang.Boolean> dialogue_freezeVillagerDuringDialogue = this.optionForKey(this.keys.dialogue_freezeVillagerDuringDialogue);
    private final Option<java.lang.Double> dialogue_maxDialogueDistance = this.optionForKey(this.keys.dialogue_maxDialogueDistance);
    private final Option<java.lang.Double> dialogue_maxForcedDialogueDistance = this.optionForKey(this.keys.dialogue_maxForcedDialogueDistance);
    private final Option<com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger> dialogue_containerForcedDialogueTrigger = this.optionForKey(this.keys.dialogue_containerForcedDialogueTrigger);
    private final Option<com.jvn.villagerretaliation.config.ContainerWatchMode> dialogue_containerWatchMode = this.optionForKey(this.keys.dialogue_containerWatchMode);
    private final Option<java.lang.Integer> dialogue_dialoguePositiveReputationCooldownDays = this.optionForKey(this.keys.dialogue_dialoguePositiveReputationCooldownDays);
    private final Option<java.lang.Integer> dialogue_repeatedQuestionPositiveLimit = this.optionForKey(this.keys.dialogue_repeatedQuestionPositiveLimit);
    private final Option<java.lang.Integer> dialogue_trustedRepeatedDialogueLimitBonus = this.optionForKey(this.keys.dialogue_trustedRepeatedDialogueLimitBonus);
    private final Option<java.lang.Integer> dialogue_respectedRepeatedDialogueLimitBonus = this.optionForKey(this.keys.dialogue_respectedRepeatedDialogueLimitBonus);
    private final Option<java.lang.Integer> dialogue_reveredRepeatedDialogueLimitBonus = this.optionForKey(this.keys.dialogue_reveredRepeatedDialogueLimitBonus);
    private final Option<java.lang.Integer> dialogue_royaltyRepeatedDialogueLimitBonus = this.optionForKey(this.keys.dialogue_royaltyRepeatedDialogueLimitBonus);
    private final Option<java.lang.Integer> dialogue_repeatedQuestionReputationLoss = this.optionForKey(this.keys.dialogue_repeatedQuestionReputationLoss);
    private final Option<java.lang.Integer> dialogue_repeatedDialogueOptionResetTicks = this.optionForKey(this.keys.dialogue_repeatedDialogueOptionResetTicks);
    private final Option<java.lang.Integer> dialogue_giftAnnoyanceReductionDivisor = this.optionForKey(this.keys.dialogue_giftAnnoyanceReductionDivisor);
    private final Option<java.lang.Double> dialogue_maxFollowDistance = this.optionForKey(this.keys.dialogue_maxFollowDistance);
    private final Option<java.lang.Integer> dialogue_greetingReputationGain = this.optionForKey(this.keys.dialogue_greetingReputationGain);
    private final Option<java.lang.Integer> dialogue_questionReputationGain = this.optionForKey(this.keys.dialogue_questionReputationGain);
    private final Option<java.lang.Integer> dialogue_storyReputationGain = this.optionForKey(this.keys.dialogue_storyReputationGain);
    private final Option<java.lang.Integer> dialogue_jokeReputationGain = this.optionForKey(this.keys.dialogue_jokeReputationGain);
    private final Option<java.lang.Integer> dialogue_jokeReputationLoss = this.optionForKey(this.keys.dialogue_jokeReputationLoss);
    private final Option<java.lang.Integer> dialogue_insultReputationLoss = this.optionForKey(this.keys.dialogue_insultReputationLoss);
    private final Option<java.lang.Integer> dialogue_firstGreetingReputationGain = this.optionForKey(this.keys.dialogue_firstGreetingReputationGain);
    private final Option<java.lang.Integer> dialogue_firstInsultReputationLoss = this.optionForKey(this.keys.dialogue_firstInsultReputationLoss);
    private final Option<java.lang.Boolean> dialogue_disableDialogueTextEffects = this.optionForKey(this.keys.dialogue_disableDialogueTextEffects);
    private final Option<java.lang.Boolean> notifications_enableWorldTextNotifications = this.optionForKey(this.keys.notifications_enableWorldTextNotifications);
    private final Option<java.lang.Boolean> notifications_enableAmbientMurmurs = this.optionForKey(this.keys.notifications_enableAmbientMurmurs);
    private final Option<java.lang.Boolean> notifications_enableSleepIndicators = this.optionForKey(this.keys.notifications_enableSleepIndicators);
    private final Option<java.lang.Boolean> notifications_enableDamageAlerts = this.optionForKey(this.keys.notifications_enableDamageAlerts);
    private final Option<java.lang.Boolean> notifications_enableCombatAlerts = this.optionForKey(this.keys.notifications_enableCombatAlerts);
    private final Option<java.lang.Boolean> notifications_enableTradeAndGiftWorldText = this.optionForKey(this.keys.notifications_enableTradeAndGiftWorldText);
    private final Option<java.lang.Boolean> gifts_enableVillagerGifts = this.optionForKey(this.keys.gifts_enableVillagerGifts);
    private final Option<java.lang.Boolean> gifts_enableHighReputationGifts = this.optionForKey(this.keys.gifts_enableHighReputationGifts);
    private final Option<java.lang.Boolean> gifts_enableGiftKeepsakes = this.optionForKey(this.keys.gifts_enableGiftKeepsakes);
    private final Option<java.lang.Boolean> gifts_showGiftReactionTooltip = this.optionForKey(this.keys.gifts_showGiftReactionTooltip);
    private final Option<java.lang.Boolean> gifts_giftReactionTooltipRequiresKnownGift = this.optionForKey(this.keys.gifts_giftReactionTooltipRequiresKnownGift);
    private final Option<java.lang.Double> gifts_repeatedGiftReputationMultiplier = this.optionForKey(this.keys.gifts_repeatedGiftReputationMultiplier);
    private final Option<java.lang.Integer> gifts_dailyGiftReputationCap = this.optionForKey(this.keys.gifts_dailyGiftReputationCap);
    private final Option<java.lang.Integer> gifts_giftRequestCooldownTicks = this.optionForKey(this.keys.gifts_giftRequestCooldownTicks);
    private final Option<java.lang.Boolean> social_enableVillagerSocialGraph = this.optionForKey(this.keys.social_enableVillagerSocialGraph);
    private final Option<java.lang.Boolean> social_enableVillagerMoods = this.optionForKey(this.keys.social_enableVillagerMoods);
    private final Option<java.lang.Boolean> social_enableSocialAttributeBehavior = this.optionForKey(this.keys.social_enableSocialAttributeBehavior);
    private final Option<java.lang.Boolean> social_enableSocialAttributeMoodEffects = this.optionForKey(this.keys.social_enableSocialAttributeMoodEffects);
    private final Option<java.lang.Boolean> social_enableSocialAttributeDialogueEffects = this.optionForKey(this.keys.social_enableSocialAttributeDialogueEffects);
    private final Option<java.lang.Boolean> social_enableSocialAttributeReputationEffects = this.optionForKey(this.keys.social_enableSocialAttributeReputationEffects);
    private final Option<java.lang.Boolean> social_enableSocialAttributeRetaliationEffects = this.optionForKey(this.keys.social_enableSocialAttributeRetaliationEffects);
    private final Option<java.lang.Boolean> social_enableSocialAttributeGossipEffects = this.optionForKey(this.keys.social_enableSocialAttributeGossipEffects);
    private final Option<java.lang.Double> social_socialAttributeEffectScale = this.optionForKey(this.keys.social_socialAttributeEffectScale);
    private final Option<java.lang.Boolean> social_enableFamilyBreedingRules = this.optionForKey(this.keys.social_enableFamilyBreedingRules);
    private final Option<java.lang.Boolean> social_enableOppositeGenderBreedingRules = this.optionForKey(this.keys.social_enableOppositeGenderBreedingRules);
    private final Option<java.lang.Boolean> social_enableParentReputationInheritance = this.optionForKey(this.keys.social_enableParentReputationInheritance);
    private final Option<java.lang.Boolean> balance_babyVillagersDropLoot = this.optionForKey(this.keys.balance_babyVillagersDropLoot);
    private final Option<java.lang.Boolean> balance_requirePlayerKillForProfessionLoot = this.optionForKey(this.keys.balance_requirePlayerKillForProfessionLoot);
    private final Option<java.lang.Boolean> balance_hungerEffectAffectsVillagers = this.optionForKey(this.keys.balance_hungerEffectAffectsVillagers);
    private final Option<java.lang.Double> balance_villagerEmeraldDropChance = this.optionForKey(this.keys.balance_villagerEmeraldDropChance);
    private final Option<java.lang.Double> balance_villagerBreadDropChance = this.optionForKey(this.keys.balance_villagerBreadDropChance);
    private final Option<java.lang.Double> balance_professionDropChance = this.optionForKey(this.keys.balance_professionDropChance);
    private final Option<java.lang.Double> balance_rareDropChance = this.optionForKey(this.keys.balance_rareDropChance);
    private final Option<java.lang.Double> balance_veryRareDropChance = this.optionForKey(this.keys.balance_veryRareDropChance);
    private final Option<java.lang.Integer> balance_hiredContractBaseDailyCost = this.optionForKey(this.keys.balance_hiredContractBaseDailyCost);
    private final Option<java.lang.Integer> balance_hiredContractMinimumDailyCost = this.optionForKey(this.keys.balance_hiredContractMinimumDailyCost);
    private final Option<java.lang.Integer> balance_hiredContractMaximumDailyCost = this.optionForKey(this.keys.balance_hiredContractMaximumDailyCost);
    private final Option<java.lang.Integer> balance_hiredContractSkillPremiumPerTen = this.optionForKey(this.keys.balance_hiredContractSkillPremiumPerTen);
    private final Option<java.lang.Integer> balance_hiredContractRoyaltyCostModifier = this.optionForKey(this.keys.balance_hiredContractRoyaltyCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractReveredCostModifier = this.optionForKey(this.keys.balance_hiredContractReveredCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractRespectedCostModifier = this.optionForKey(this.keys.balance_hiredContractRespectedCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractTrustedCostModifier = this.optionForKey(this.keys.balance_hiredContractTrustedCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractNeutralCostModifier = this.optionForKey(this.keys.balance_hiredContractNeutralCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractSuspiciousCostModifier = this.optionForKey(this.keys.balance_hiredContractSuspiciousCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractHostileCostModifier = this.optionForKey(this.keys.balance_hiredContractHostileCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractDespisedCostModifier = this.optionForKey(this.keys.balance_hiredContractDespisedCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractFearedCostModifier = this.optionForKey(this.keys.balance_hiredContractFearedCostModifier);
    private final Option<java.lang.Integer> balance_hiredContractEarlyEndRefundPercent = this.optionForKey(this.keys.balance_hiredContractEarlyEndRefundPercent);
    private final Option<java.lang.Integer> balance_hiredWorkTickInterval = this.optionForKey(this.keys.balance_hiredWorkTickInterval);
    private final Option<java.lang.Integer> balance_hiredWorkNoticeCooldownSeconds = this.optionForKey(this.keys.balance_hiredWorkNoticeCooldownSeconds);
    private final Option<java.lang.Integer> balance_hiredWorkDefaultRadius = this.optionForKey(this.keys.balance_hiredWorkDefaultRadius);
    private final Option<java.lang.Integer> balance_hiredWorkMaxRadius = this.optionForKey(this.keys.balance_hiredWorkMaxRadius);
    private final Option<java.lang.Integer> balance_hiredWorkBaseEfficiencyPercent = this.optionForKey(this.keys.balance_hiredWorkBaseEfficiencyPercent);
    private final Option<java.lang.Integer> balance_hiredWorkMinimumEfficiencyPercent = this.optionForKey(this.keys.balance_hiredWorkMinimumEfficiencyPercent);
    private final Option<java.lang.Integer> balance_hiredWorkMaximumEfficiencyPercent = this.optionForKey(this.keys.balance_hiredWorkMaximumEfficiencyPercent);
    private final Option<java.lang.Boolean> balance_enableHiredWorkSkillGrowth = this.optionForKey(this.keys.balance_enableHiredWorkSkillGrowth);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_combat = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_combat);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_mining = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_mining);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_logging = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_logging);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_craftsman = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_craftsman);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_farming = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_farming);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_brewing = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_brewing);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_cooking = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_cooking);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_builder = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_builder);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_navigation = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_navigation);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_animalHandling = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_animalHandling);
    private final Option<java.lang.Double> balance_hiredWorkSkillGrowth_nitwit = this.optionForKey(this.keys.balance_hiredWorkSkillGrowth_nitwit);
    private final Option<java.lang.Integer> balance_hiredBuilderMaxBlocks = this.optionForKey(this.keys.balance_hiredBuilderMaxBlocks);
    private final Option<java.lang.Integer> balance_hiredBuilderMaxSiteDistance = this.optionForKey(this.keys.balance_hiredBuilderMaxSiteDistance);
    private final Option<java.lang.Integer> balance_hiredBuilderMaterialStorageRadius = this.optionForKey(this.keys.balance_hiredBuilderMaterialStorageRadius);
    private final Option<java.lang.Integer> balance_hiredBuilderBaseEmeraldCost = this.optionForKey(this.keys.balance_hiredBuilderBaseEmeraldCost);
    private final Option<java.lang.Integer> balance_hiredBuilderEmeraldsPer64Blocks = this.optionForKey(this.keys.balance_hiredBuilderEmeraldsPer64Blocks);
    private final Option<java.lang.Boolean> balance_hiredBuilderCanReplaceSoftBlocks = this.optionForKey(this.keys.balance_hiredBuilderCanReplaceSoftBlocks);
    private final Option<java.lang.Boolean> retaliation_attackAggrosOnlyHitVillager = this.optionForKey(this.keys.retaliation_attackAggrosOnlyHitVillager);
    private final Option<java.lang.Boolean> retaliation_killingVillagerAggrosNearbyVillagers = this.optionForKey(this.keys.retaliation_killingVillagerAggrosNearbyVillagers);
    private final Option<java.lang.Boolean> retaliation_babyVillagersFleeWitnessedDeaths = this.optionForKey(this.keys.retaliation_babyVillagersFleeWitnessedDeaths);
    private final Option<java.lang.Double> retaliation_villagerKillAggroRadius = this.optionForKey(this.keys.retaliation_villagerKillAggroRadius);
    private final Option<java.lang.Boolean> retaliation_retaliationWitnessesRequireLineOfSight = this.optionForKey(this.keys.retaliation_retaliationWitnessesRequireLineOfSight);
    private final Option<java.lang.Integer> retaliation_aggroDurationTicks = this.optionForKey(this.keys.retaliation_aggroDurationTicks);
    private final Option<java.lang.Boolean> retaliation_nearbyVillagersIgnoreCreativePlayers = this.optionForKey(this.keys.retaliation_nearbyVillagersIgnoreCreativePlayers);
    private final Option<java.lang.Integer> reputation_directHitPenalty = this.optionForKey(this.keys.reputation_directHitPenalty);
    private final Option<java.lang.Integer> reputation_witnessedHitPenalty = this.optionForKey(this.keys.reputation_witnessedHitPenalty);
    private final Option<java.lang.Integer> reputation_witnessedKillPenalty = this.optionForKey(this.keys.reputation_witnessedKillPenalty);
    private final Option<java.lang.Integer> reputation_witnessedBabyKillPenalty = this.optionForKey(this.keys.reputation_witnessedBabyKillPenalty);
    private final Option<java.lang.Integer> reputation_witnessedIronGolemKillPenalty = this.optionForKey(this.keys.reputation_witnessedIronGolemKillPenalty);
    private final Option<java.lang.Integer> reputation_containerBreakReputationLoss = this.optionForKey(this.keys.reputation_containerBreakReputationLoss);
    private final Option<java.lang.Integer> reputation_generatedContainerBreakItemReputationLoss = this.optionForKey(this.keys.reputation_generatedContainerBreakItemReputationLoss);
    private final Option<java.lang.Integer> reputation_tradeReputationGain = this.optionForKey(this.keys.reputation_tradeReputationGain);
    private final Option<java.lang.Integer> reputation_maxTradeReputationGainPerVillagerPerDay = this.optionForKey(this.keys.reputation_maxTradeReputationGainPerVillagerPerDay);
    private final Option<java.lang.Integer> reputation_sleepingVillagerBotherReputationLoss = this.optionForKey(this.keys.reputation_sleepingVillagerBotherReputationLoss);
    private final Option<java.lang.Integer> reputation_sleepingVillagerBedBreakReputationLoss = this.optionForKey(this.keys.reputation_sleepingVillagerBedBreakReputationLoss);
    private final Option<java.lang.Integer> reputation_healVillagerGain = this.optionForKey(this.keys.reputation_healVillagerGain);
    private final Option<java.lang.Integer> reputation_saveVillagerGain = this.optionForKey(this.keys.reputation_saveVillagerGain);
    private final Option<java.lang.Integer> reputation_positiveWitnessGain = this.optionForKey(this.keys.reputation_positiveWitnessGain);
    private final Option<java.lang.Double> reputation_hostileMobAssistReputationMultiplier = this.optionForKey(this.keys.reputation_hostileMobAssistReputationMultiplier);
    private final Option<java.lang.Double> reputation_gossipReputationMultiplier = this.optionForKey(this.keys.reputation_gossipReputationMultiplier);
    private final Option<java.lang.Integer> reputation_royaltyThreshold = this.optionForKey(this.keys.reputation_royaltyThreshold);
    private final Option<java.lang.Integer> reputation_reveredThreshold = this.optionForKey(this.keys.reputation_reveredThreshold);
    private final Option<java.lang.Integer> reputation_respectedThreshold = this.optionForKey(this.keys.reputation_respectedThreshold);
    private final Option<java.lang.Integer> reputation_trustedThreshold = this.optionForKey(this.keys.reputation_trustedThreshold);
    private final Option<java.lang.Integer> reputation_suspiciousThreshold = this.optionForKey(this.keys.reputation_suspiciousThreshold);
    private final Option<java.lang.Integer> reputation_hostileThreshold = this.optionForKey(this.keys.reputation_hostileThreshold);
    private final Option<java.lang.Integer> reputation_despisedThreshold = this.optionForKey(this.keys.reputation_despisedThreshold);
    private final Option<java.lang.Integer> reputation_fearedThreshold = this.optionForKey(this.keys.reputation_fearedThreshold);
    private final Option<java.lang.Double> reputation_witnessRadius = this.optionForKey(this.keys.reputation_witnessRadius);
    private final Option<java.lang.Double> reputation_gossipRadius = this.optionForKey(this.keys.reputation_gossipRadius);
    private final Option<java.lang.Double> reputation_despisedSightRadius = this.optionForKey(this.keys.reputation_despisedSightRadius);
    private final Option<java.lang.Boolean> reputation_reputationDecayEnabled = this.optionForKey(this.keys.reputation_reputationDecayEnabled);
    private final Option<java.lang.Integer> reputation_reputationDecayInterval = this.optionForKey(this.keys.reputation_reputationDecayInterval);
    private final Option<java.lang.Integer> reputation_reputationDecayAmount = this.optionForKey(this.keys.reputation_reputationDecayAmount);
    private final Option<java.lang.Integer> reputation_pruneNeutralEntriesAfterDays = this.optionForKey(this.keys.reputation_pruneNeutralEntriesAfterDays);
    private final Option<java.lang.Boolean> reputation_witnessReputationRequiresLineOfSight = this.optionForKey(this.keys.reputation_witnessReputationRequiresLineOfSight);
    private final Option<java.lang.Boolean> reputation_enableReputationTradePricing = this.optionForKey(this.keys.reputation_enableReputationTradePricing);
    private final Option<java.lang.Double> reputation_reputationTradePriceScale = this.optionForKey(this.keys.reputation_reputationTradePriceScale);
    private final Option<java.lang.Boolean> playerRaids_enabled = this.optionForKey(this.keys.playerRaids_enabled);
    private final Option<java.lang.Boolean> playerRaids_confirmRaidHorn = this.optionForKey(this.keys.playerRaids_confirmRaidHorn);
    private final Option<java.lang.Integer> playerRaids_preparationTicks = this.optionForKey(this.keys.playerRaids_preparationTicks);
    private final Option<java.lang.Integer> playerRaids_abandonmentTicks = this.optionForKey(this.keys.playerRaids_abandonmentTicks);
    private final Option<java.lang.Integer> playerRaids_villageCooldownDays = this.optionForKey(this.keys.playerRaids_villageCooldownDays);
    private final Option<java.lang.Integer> playerRaids_bossBarRange = this.optionForKey(this.keys.playerRaids_bossBarRange);
    private final Option<java.lang.Integer> playerRaids_defendersPerGolem = this.optionForKey(this.keys.playerRaids_defendersPerGolem);
    private final Option<java.lang.Integer> playerRaids_minimumGolems = this.optionForKey(this.keys.playerRaids_minimumGolems);
    private final Option<java.lang.Integer> playerRaids_maximumGolems = this.optionForKey(this.keys.playerRaids_maximumGolems);
    private final Option<java.lang.Integer> playerRaids_raidersPerBonusGolem = this.optionForKey(this.keys.playerRaids_raidersPerBonusGolem);
    private final Option<java.lang.Boolean> trade_enableSkillTradeOverhaul = this.optionForKey(this.keys.trade_enableSkillTradeOverhaul);
    private final Option<java.lang.Boolean> trade_disableVillagerWalletLimit = this.optionForKey(this.keys.trade_disableVillagerWalletLimit);
    private final Option<java.lang.Boolean> trade_enableSpecialOrders = this.optionForKey(this.keys.trade_enableSpecialOrders);
    private final Option<com.jvn.villagerretaliation.reputation.VillagerReputationLevel> trade_specialOrderMinReputation = this.optionForKey(this.keys.trade_specialOrderMinReputation);
    private final Option<java.lang.Integer> trade_specialOrderWaitDays = this.optionForKey(this.keys.trade_specialOrderWaitDays);
    private final Option<java.lang.Integer> trade_specialOrderCooldownDays = this.optionForKey(this.keys.trade_specialOrderCooldownDays);
    private final Option<java.lang.Boolean> trade_specialOrderExtraCostEnabled = this.optionForKey(this.keys.trade_specialOrderExtraCostEnabled);
    private final Option<java.lang.Integer> trade_specialOrderMaxActivePerPlayer = this.optionForKey(this.keys.trade_specialOrderMaxActivePerPlayer);
    private final Option<java.lang.Boolean> trade_skillTradeQualityScaling = this.optionForKey(this.keys.trade_skillTradeQualityScaling);
    private final Option<java.lang.Boolean> trade_skillTradeLowSkillPenalties = this.optionForKey(this.keys.trade_skillTradeLowSkillPenalties);
    private final Option<java.lang.Integer> trade_skillTradeMaxEnchantmentLevel = this.optionForKey(this.keys.trade_skillTradeMaxEnchantmentLevel);
    private final Option<java.lang.Double> trade_skillTradeRareChanceMultiplier = this.optionForKey(this.keys.trade_skillTradeRareChanceMultiplier);
    private final Option<java.lang.Boolean> trade_skillTradeAllowHighTierEquipment = this.optionForKey(this.keys.trade_skillTradeAllowHighTierEquipment);
    private final Option<java.lang.Boolean> trade_skillTradeAllowSpecialArrows = this.optionForKey(this.keys.trade_skillTradeAllowSpecialArrows);
    private final Option<java.lang.Boolean> trade_skillTradeAllowRareSpecialtyTrades = this.optionForKey(this.keys.trade_skillTradeAllowRareSpecialtyTrades);
    private final Option<java.lang.Boolean> trade_enableSkillGrowthFromTradingLevels = this.optionForKey(this.keys.trade_enableSkillGrowthFromTradingLevels);
    private final Option<java.lang.Boolean> trade_enableRegularTradeSkillGrowth = this.optionForKey(this.keys.trade_enableRegularTradeSkillGrowth);
    private final Option<java.lang.Double> trade_regularTradeSkillGrowthAmount = this.optionForKey(this.keys.trade_regularTradeSkillGrowthAmount);
    private final Option<java.lang.Boolean> trade_enableSkillBasedTradeLeveling = this.optionForKey(this.keys.trade_enableSkillBasedTradeLeveling);
    private final Option<java.lang.Double> trade_skillBasedTradeLevelingMinMultiplier = this.optionForKey(this.keys.trade_skillBasedTradeLevelingMinMultiplier);
    private final Option<java.lang.Double> trade_skillBasedTradeLevelingMaxMultiplier = this.optionForKey(this.keys.trade_skillBasedTradeLevelingMaxMultiplier);
    private final Option<java.lang.Boolean> trade_enableSkillGrowthFeedback = this.optionForKey(this.keys.trade_enableSkillGrowthFeedback);
    private final Option<java.lang.Integer> trade_skillGrowthPrimaryMin = this.optionForKey(this.keys.trade_skillGrowthPrimaryMin);
    private final Option<java.lang.Integer> trade_skillGrowthPrimaryMax = this.optionForKey(this.keys.trade_skillGrowthPrimaryMax);
    private final Option<java.lang.Boolean> debugOverlay_showVillageBounds = this.optionForKey(this.keys.debugOverlay_showVillageBounds);
    private final Option<java.lang.Boolean> debugOverlay_highlightRaidDefenders = this.optionForKey(this.keys.debugOverlay_highlightRaidDefenders);
    private final Option<java.lang.Integer> debugOverlay_debugPreviewMaxVisibleNodes = this.optionForKey(this.keys.debugOverlay_debugPreviewMaxVisibleNodes);
    private final Option<java.lang.Integer> debugOverlay_debugPreviewMaxVisibleLabels = this.optionForKey(this.keys.debugOverlay_debugPreviewMaxVisibleLabels);
    private final Option<java.lang.Integer> debugOverlay_debugPreviewMaxVisibleSegments = this.optionForKey(this.keys.debugOverlay_debugPreviewMaxVisibleSegments);
    private final Option<java.lang.Boolean> debugOverlay_showVillagerReputationDebugOverlay = this.optionForKey(this.keys.debugOverlay_showVillagerReputationDebugOverlay);
    private final Option<java.lang.Double> debugOverlay_reputationDebugOverlayMaxDistance = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayMaxDistance);
    private final Option<java.lang.Boolean> debugOverlay_reputationDebugOverlayShowTier = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayShowTier);
    private final Option<java.lang.Boolean> debugOverlay_reputationDebugOverlayShowNumber = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayShowNumber);
    private final Option<java.lang.Boolean> debugOverlay_reputationDebugOverlayShowHealth = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayShowHealth);
    private final Option<java.lang.Boolean> debugOverlay_reputationDebugOverlayShowArmor = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayShowArmor);
    private final Option<java.lang.Boolean> debugOverlay_reputationDebugOverlayShowHunger = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayShowHunger);
    private final Option<java.lang.Boolean> debugOverlay_reputationDebugOverlayRequireAdvancedTooltips = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayRequireAdvancedTooltips);
    private final Option<java.lang.Boolean> debugOverlay_reputationDebugOverlayOnlyWhenSneaking = this.optionForKey(this.keys.debugOverlay_reputationDebugOverlayOnlyWhenSneaking);
    private final Option<java.lang.Boolean> combat_enableVillagerSleepHealing = this.optionForKey(this.keys.combat_enableVillagerSleepHealing);
    private final Option<java.lang.Double> combat_villagerSleepHealingMaxHealthPercent = this.optionForKey(this.keys.combat_villagerSleepHealingMaxHealthPercent);
    private final Option<java.lang.Boolean> combat_enableVillagerDownedState = this.optionForKey(this.keys.combat_enableVillagerDownedState);
    private final Option<java.lang.Boolean> combat_allVillagersUseDownedState = this.optionForKey(this.keys.combat_allVillagersUseDownedState);
    private final Option<java.lang.Boolean> combat_raidVillagersUseDownedState = this.optionForKey(this.keys.combat_raidVillagersUseDownedState);
    private final Option<java.lang.Boolean> combat_hiredVillagersUseDownedState = this.optionForKey(this.keys.combat_hiredVillagersUseDownedState);
    private final Option<java.lang.Boolean> combat_partyVillagersUseDownedState = this.optionForKey(this.keys.combat_partyVillagersUseDownedState);
    private final Option<java.lang.Boolean> combat_playerDamageDownsEligibleVillagers = this.optionForKey(this.keys.combat_playerDamageDownsEligibleVillagers);
    private final Option<java.lang.Boolean> combat_mobDamageDownsEligibleVillagers = this.optionForKey(this.keys.combat_mobDamageDownsEligibleVillagers);
    private final Option<java.lang.Boolean> combat_environmentalDamageDownsEligibleVillagers = this.optionForKey(this.keys.combat_environmentalDamageDownsEligibleVillagers);
    private final Option<java.lang.Integer> combat_downedMinimumTicks = this.optionForKey(this.keys.combat_downedMinimumTicks);
    private final Option<java.lang.Double> combat_downedRecoveryHealthPercent = this.optionForKey(this.keys.combat_downedRecoveryHealthPercent);
    private final Option<java.lang.Double> combat_downedThreatRadius = this.optionForKey(this.keys.combat_downedThreatRadius);
    private final Option<java.lang.Integer> combat_downedQuietTicks = this.optionForKey(this.keys.combat_downedQuietTicks);
    private final Option<java.lang.Boolean> combat_weaponsmithsFightBack = this.optionForKey(this.keys.combat_weaponsmithsFightBack);
    private final Option<java.lang.Boolean> combat_toolsmithsFightBack = this.optionForKey(this.keys.combat_toolsmithsFightBack);
    private final Option<java.lang.Boolean> combat_armorersFightBack = this.optionForKey(this.keys.combat_armorersFightBack);
    private final Option<java.lang.Boolean> combat_fletchersFightBack = this.optionForKey(this.keys.combat_fletchersFightBack);
    private final Option<java.lang.Boolean> combat_butchersFightBack = this.optionForKey(this.keys.combat_butchersFightBack);
    private final Option<java.lang.Boolean> combat_villagersTargetHostileMobs = this.optionForKey(this.keys.combat_villagersTargetHostileMobs);
    private final Option<java.lang.Boolean> combat_wanderingTradersTargetHostileMobs = this.optionForKey(this.keys.combat_wanderingTradersTargetHostileMobs);
    private final Option<java.lang.Boolean> combat_villagersRetaliateAgainstHostileMobs = this.optionForKey(this.keys.combat_villagersRetaliateAgainstHostileMobs);
    private final Option<java.lang.Boolean> combat_wanderingTradersRetaliateAgainstHostileMobs = this.optionForKey(this.keys.combat_wanderingTradersRetaliateAgainstHostileMobs);
    private final Option<java.lang.Boolean> combat_villagersStandGroundAgainstHostileMobs = this.optionForKey(this.keys.combat_villagersStandGroundAgainstHostileMobs);
    private final Option<java.lang.Boolean> combat_villagersFleeVisibleCreepers = this.optionForKey(this.keys.combat_villagersFleeVisibleCreepers);
    private final Option<java.lang.Boolean> combat_villagersPickUpGroundWeapons = this.optionForKey(this.keys.combat_villagersPickUpGroundWeapons);
    private final Option<java.lang.Boolean> combat_wanderingTradersPickUpGroundWeapons = this.optionForKey(this.keys.combat_wanderingTradersPickUpGroundWeapons);
    private final Option<java.lang.Double> combat_naturalHostileTargetRadius = this.optionForKey(this.keys.combat_naturalHostileTargetRadius);
    private final Option<java.lang.Double> combat_combatWeaponDropChance = this.optionForKey(this.keys.combat_combatWeaponDropChance);
    private final Option<java.lang.Double> combat_combatWeaponEnchantChance = this.optionForKey(this.keys.combat_combatWeaponEnchantChance);
    private final Option<java.lang.Double> combat_armorerShieldChanceHard = this.optionForKey(this.keys.combat_armorerShieldChanceHard);
    private final Option<java.lang.Boolean> combat_clericsUsePotions = this.optionForKey(this.keys.combat_clericsUsePotions);
    private final Option<java.lang.Double> combat_passiveClericAllyHealRange = this.optionForKey(this.keys.combat_passiveClericAllyHealRange);
    private final Option<java.lang.Double> combat_passiveClericAllyHealHealthThreshold = this.optionForKey(this.keys.combat_passiveClericAllyHealHealthThreshold);
    private final Option<java.lang.Boolean> combat_passiveClericAllyHealRequiresLineOfSight = this.optionForKey(this.keys.combat_passiveClericAllyHealRequiresLineOfSight);
    private final Option<java.lang.Boolean> combat_hostileTierHarassThrowEnabled = this.optionForKey(this.keys.combat_hostileTierHarassThrowEnabled);
    private final Option<java.lang.Integer> combat_hostileTierHarassThrowMinIntervalTicks = this.optionForKey(this.keys.combat_hostileTierHarassThrowMinIntervalTicks);
    private final Option<java.lang.Integer> combat_hostileTierHarassThrowMaxIntervalTicks = this.optionForKey(this.keys.combat_hostileTierHarassThrowMaxIntervalTicks);
    private final Option<java.lang.Boolean> duels_enabled = this.optionForKey(this.keys.duels_enabled);
    private final Option<java.lang.Boolean> duels_allowBringYourOwnLoadout = this.optionForKey(this.keys.duels_allowBringYourOwnLoadout);
    private final Option<java.lang.Integer> duels_minimumGuts = this.optionForKey(this.keys.duels_minimumGuts);
    private final Option<java.lang.Integer> duels_cooldownDays = this.optionForKey(this.keys.duels_cooldownDays);
    private final Option<java.lang.Integer> duels_refusalLosses = this.optionForKey(this.keys.duels_refusalLosses);
    private final Option<java.lang.Integer> duels_arenaRadius = this.optionForKey(this.keys.duels_arenaRadius);
    private final Option<java.lang.Boolean> duels_showArenaParticles = this.optionForKey(this.keys.duels_showArenaParticles);
    private final Option<java.lang.Integer> duels_boundaryGraceTicks = this.optionForKey(this.keys.duels_boundaryGraceTicks);
    private final Option<java.lang.Integer> duels_timeoutTicks = this.optionForKey(this.keys.duels_timeoutTicks);
    private final Option<java.lang.Integer> duels_spectatorRadius = this.optionForKey(this.keys.duels_spectatorRadius);
    private final Option<java.lang.Integer> duels_spectatorCap = this.optionForKey(this.keys.duels_spectatorCap);
    private final Option<java.lang.Integer> duels_watcherReputation = this.optionForKey(this.keys.duels_watcherReputation);
    private final Option<java.lang.Boolean> wanderer_dropEmeralds = this.optionForKey(this.keys.wanderer_dropEmeralds);
    private final Option<java.lang.Boolean> wanderer_dropInvisibilityPotion = this.optionForKey(this.keys.wanderer_dropInvisibilityPotion);
    private final Option<java.lang.Boolean> wanderer_dropRandomCurrentTrade = this.optionForKey(this.keys.wanderer_dropRandomCurrentTrade);
    private final Option<java.lang.Double> wanderer_randomTradeDropChance = this.optionForKey(this.keys.wanderer_randomTradeDropChance);
    private final Option<java.lang.Boolean> quest_showQuestIndicators = this.optionForKey(this.keys.quest_showQuestIndicators);
    private final Option<java.lang.Boolean> quest_enableQuestItemShaderHighlights = this.optionForKey(this.keys.quest_enableQuestItemShaderHighlights);
    private final Option<com.jvn.villagerretaliation.config.QuestItemHighlightMode> quest_questItemHighlightMode = this.optionForKey(this.keys.quest_questItemHighlightMode);

    private VillagerRetaliationOwoConfig() {
        super(com.jvn.villagerretaliation.config.VillagerRetaliationConfigModel.class);
    }

    private VillagerRetaliationOwoConfig(Consumer<Jankson.Builder> janksonBuilder) {
        super(com.jvn.villagerretaliation.config.VillagerRetaliationConfigModel.class, janksonBuilder);
    }

    public static VillagerRetaliationOwoConfig createAndLoad() {
        var wrapper = new VillagerRetaliationOwoConfig();
        wrapper.load();
        return wrapper;
    }

    public static VillagerRetaliationOwoConfig createAndLoad(Consumer<Jankson.Builder> janksonBuilder) {
        var wrapper = new VillagerRetaliationOwoConfig(janksonBuilder);
        wrapper.load();
        return wrapper;
    }

    public final General_ general = new General_();
    public class General_ implements General {
        public boolean enableVillagerDrops() {
            return general_enableVillagerDrops.value();
        }

        public void enableVillagerDrops(boolean value) {
            general_enableVillagerDrops.set(value);
        }

        public boolean enableWanderingTraderDrops() {
            return general_enableWanderingTraderDrops.value();
        }

        public void enableWanderingTraderDrops(boolean value) {
            general_enableWanderingTraderDrops.set(value);
        }

        public boolean enableVillagerRetaliation() {
            return general_enableVillagerRetaliation.value();
        }

        public void enableVillagerRetaliation(boolean value) {
            general_enableVillagerRetaliation.set(value);
        }

        public boolean enableVillagerReputation() {
            return general_enableVillagerReputation.value();
        }

        public void enableVillagerReputation(boolean value) {
            general_enableVillagerReputation.set(value);
        }

        public boolean enableVanillaGossipIntegration() {
            return general_enableVanillaGossipIntegration.value();
        }

        public void enableVanillaGossipIntegration(boolean value) {
            general_enableVanillaGossipIntegration.set(value);
        }

        public boolean enableDespisedKillOnSight() {
            return general_enableDespisedKillOnSight.value();
        }

        public void enableDespisedKillOnSight(boolean value) {
            general_enableDespisedKillOnSight.set(value);
        }

        public boolean despisedKillOnSightInterruptsHiredWork() {
            return general_despisedKillOnSightInterruptsHiredWork.value();
        }

        public void despisedKillOnSightInterruptsHiredWork(boolean value) {
            general_despisedKillOnSightInterruptsHiredWork.set(value);
        }

        public com.jvn.villagerretaliation.config.ReputationChangeDisplayMode reputationChangeDisplayMode() {
            return general_reputationChangeDisplayMode.value();
        }

        public void reputationChangeDisplayMode(com.jvn.villagerretaliation.config.ReputationChangeDisplayMode value) {
            general_reputationChangeDisplayMode.set(value);
        }

        public com.jvn.villagerretaliation.config.ReputationChangeNotificationStyle reputationChangeNotificationStyle() {
            return general_reputationChangeNotificationStyle.value();
        }

        public void reputationChangeNotificationStyle(com.jvn.villagerretaliation.config.ReputationChangeNotificationStyle value) {
            general_reputationChangeNotificationStyle.set(value);
        }

        public com.jvn.villagerretaliation.config.ReputationChangeHudPosition reputationChangeHudPosition() {
            return general_reputationChangeHudPosition.value();
        }

        public void reputationChangeHudPosition(com.jvn.villagerretaliation.config.ReputationChangeHudPosition value) {
            general_reputationChangeHudPosition.set(value);
        }

        public boolean collapseReputationChangeNotifications() {
            return general_collapseReputationChangeNotifications.value();
        }

        public void collapseReputationChangeNotifications(boolean value) {
            general_collapseReputationChangeNotifications.set(value);
        }

        public boolean showVillagerNameTags() {
            return general_showVillagerNameTags.value();
        }

        public void showVillagerNameTags(boolean value) {
            general_showVillagerNameTags.set(value);
        }

        public com.jvn.villagerretaliation.config.VillagerStatDisplayMode villagerStatDisplayMode() {
            return general_villagerStatDisplayMode.value();
        }

        public void villagerStatDisplayMode(com.jvn.villagerretaliation.config.VillagerStatDisplayMode value) {
            general_villagerStatDisplayMode.set(value);
        }

        public boolean villagerReputationHoverTooltipRequiresEmerald() {
            return general_villagerReputationHoverTooltipRequiresEmerald.value();
        }

        public void villagerReputationHoverTooltipRequiresEmerald(boolean value) {
            general_villagerReputationHoverTooltipRequiresEmerald.set(value);
        }

        public boolean showTradeGuiReputationIcon() {
            return general_showTradeGuiReputationIcon.value();
        }

        public void showTradeGuiReputationIcon(boolean value) {
            general_showTradeGuiReputationIcon.set(value);
        }

        public boolean enableVillagerDeathMessages() {
            return general_enableVillagerDeathMessages.value();
        }

        public void enableVillagerDeathMessages(boolean value) {
            general_enableVillagerDeathMessages.set(value);
        }

    }
    public final Dialogue_ dialogue = new Dialogue_();
    public class Dialogue_ implements Dialogue {
        public boolean enableInteractionScreen() {
            return dialogue_enableInteractionScreen.value();
        }

        public void enableInteractionScreen(boolean value) {
            dialogue_enableInteractionScreen.set(value);
        }

        public boolean shiftRightClickBypassesInteractionScreen() {
            return dialogue_shiftRightClickBypassesInteractionScreen.value();
        }

        public void shiftRightClickBypassesInteractionScreen(boolean value) {
            dialogue_shiftRightClickBypassesInteractionScreen.set(value);
        }

        public boolean enableDialogueReputationEffects() {
            return dialogue_enableDialogueReputationEffects.value();
        }

        public void enableDialogueReputationEffects(boolean value) {
            dialogue_enableDialogueReputationEffects.set(value);
        }

        public boolean enableDialogueCameraFocus() {
            return dialogue_enableDialogueCameraFocus.value();
        }

        public void enableDialogueCameraFocus(boolean value) {
            dialogue_enableDialogueCameraFocus.set(value);
        }

        public boolean enableDialogueCinematicBars() {
            return dialogue_enableDialogueCinematicBars.value();
        }

        public void enableDialogueCinematicBars(boolean value) {
            dialogue_enableDialogueCinematicBars.set(value);
        }

        public int dialogueCinematicBarHeight() {
            return dialogue_dialogueCinematicBarHeight.value();
        }

        public void dialogueCinematicBarHeight(int value) {
            dialogue_dialogueCinematicBarHeight.set(value);
        }

        public int dialogueCinematicBarMinSlant() {
            return dialogue_dialogueCinematicBarMinSlant.value();
        }

        public void dialogueCinematicBarMinSlant(int value) {
            dialogue_dialogueCinematicBarMinSlant.set(value);
        }

        public int dialogueCinematicBarMaxSlant() {
            return dialogue_dialogueCinematicBarMaxSlant.value();
        }

        public void dialogueCinematicBarMaxSlant(int value) {
            dialogue_dialogueCinematicBarMaxSlant.set(value);
        }

        public boolean animateDialogueCinematicBars() {
            return dialogue_animateDialogueCinematicBars.value();
        }

        public void animateDialogueCinematicBars(boolean value) {
            dialogue_animateDialogueCinematicBars.set(value);
        }

        public boolean enableForcedDialogue() {
            return dialogue_enableForcedDialogue.value();
        }

        public void enableForcedDialogue(boolean value) {
            dialogue_enableForcedDialogue.set(value);
        }

        public boolean enableContainerForcedDialogue() {
            return dialogue_enableContainerForcedDialogue.value();
        }

        public void enableContainerForcedDialogue(boolean value) {
            dialogue_enableContainerForcedDialogue.set(value);
        }

        public boolean enableContainerOpenReaction() {
            return dialogue_enableContainerOpenReaction.value();
        }

        public void enableContainerOpenReaction(boolean value) {
            dialogue_enableContainerOpenReaction.set(value);
        }

        public boolean enableRetaliationForcedDialogue() {
            return dialogue_enableRetaliationForcedDialogue.value();
        }

        public void enableRetaliationForcedDialogue(boolean value) {
            dialogue_enableRetaliationForcedDialogue.set(value);
        }

        public boolean enablePlayerItemProximityForcedDialogue() {
            return dialogue_enablePlayerItemProximityForcedDialogue.value();
        }

        public void enablePlayerItemProximityForcedDialogue(boolean value) {
            dialogue_enablePlayerItemProximityForcedDialogue.set(value);
        }

        public boolean separateVillagerChatMessages() {
            return dialogue_separateVillagerChatMessages.value();
        }

        public void separateVillagerChatMessages(boolean value) {
            dialogue_separateVillagerChatMessages.set(value);
        }

        public boolean separateVillagerChatSpeakers() {
            return dialogue_separateVillagerChatSpeakers.value();
        }

        public void separateVillagerChatSpeakers(boolean value) {
            dialogue_separateVillagerChatSpeakers.set(value);
        }

        public com.jvn.villagerretaliation.config.InteractionChatPosition interactionChatPosition() {
            return dialogue_interactionChatPosition.value();
        }

        public void interactionChatPosition(com.jvn.villagerretaliation.config.InteractionChatPosition value) {
            dialogue_interactionChatPosition.set(value);
        }

        public com.jvn.villagerretaliation.config.VillagerChatBroadcastMode villagerChatBroadcastMode() {
            return dialogue_villagerChatBroadcastMode.value();
        }

        public void villagerChatBroadcastMode(com.jvn.villagerretaliation.config.VillagerChatBroadcastMode value) {
            dialogue_villagerChatBroadcastMode.set(value);
        }

        public int villagerChatBroadcastRadius() {
            return dialogue_villagerChatBroadcastRadius.value();
        }

        public void villagerChatBroadcastRadius(int value) {
            dialogue_villagerChatBroadcastRadius.set(value);
        }

        public boolean showPersonalInteractionDialogueToNearbyPlayers() {
            return dialogue_showPersonalInteractionDialogueToNearbyPlayers.value();
        }

        public void showPersonalInteractionDialogueToNearbyPlayers(boolean value) {
            dialogue_showPersonalInteractionDialogueToNearbyPlayers.set(value);
        }

        public com.jvn.villagerretaliation.config.DialogueTextSpeed dialogueTextSpeed() {
            return dialogue_dialogueTextSpeed.value();
        }

        public void dialogueTextSpeed(com.jvn.villagerretaliation.config.DialogueTextSpeed value) {
            dialogue_dialogueTextSpeed.set(value);
        }

        public boolean enableDialogueBlipAudio() {
            return dialogue_enableDialogueBlipAudio.value();
        }

        public void enableDialogueBlipAudio(boolean value) {
            dialogue_enableDialogueBlipAudio.set(value);
        }

        public double dialogueBlipVolume() {
            return dialogue_dialogueBlipVolume.value();
        }

        public void dialogueBlipVolume(double value) {
            dialogue_dialogueBlipVolume.set(value);
        }

        public double dialogueBlipMinPitch() {
            return dialogue_dialogueBlipMinPitch.value();
        }

        public void dialogueBlipMinPitch(double value) {
            dialogue_dialogueBlipMinPitch.set(value);
        }

        public double dialogueBlipMaxPitch() {
            return dialogue_dialogueBlipMaxPitch.value();
        }

        public void dialogueBlipMaxPitch(double value) {
            dialogue_dialogueBlipMaxPitch.set(value);
        }

        public double dialogueCameraZoomAmount() {
            return dialogue_dialogueCameraZoomAmount.value();
        }

        public void dialogueCameraZoomAmount(double value) {
            dialogue_dialogueCameraZoomAmount.set(value);
        }

        public boolean enableNormalDialogueCameraFocus() {
            return dialogue_enableNormalDialogueCameraFocus.value();
        }

        public void enableNormalDialogueCameraFocus(boolean value) {
            dialogue_enableNormalDialogueCameraFocus.set(value);
        }

        public double normalDialogueCameraZoomAmount() {
            return dialogue_normalDialogueCameraZoomAmount.value();
        }

        public void normalDialogueCameraZoomAmount(double value) {
            dialogue_normalDialogueCameraZoomAmount.set(value);
        }

        public int dialogueCameraTransitionTicks() {
            return dialogue_dialogueCameraTransitionTicks.value();
        }

        public void dialogueCameraTransitionTicks(int value) {
            dialogue_dialogueCameraTransitionTicks.set(value);
        }

        public boolean freezeVillagerDuringDialogue() {
            return dialogue_freezeVillagerDuringDialogue.value();
        }

        public void freezeVillagerDuringDialogue(boolean value) {
            dialogue_freezeVillagerDuringDialogue.set(value);
        }

        public double maxDialogueDistance() {
            return dialogue_maxDialogueDistance.value();
        }

        public void maxDialogueDistance(double value) {
            dialogue_maxDialogueDistance.set(value);
        }

        public double maxForcedDialogueDistance() {
            return dialogue_maxForcedDialogueDistance.value();
        }

        public void maxForcedDialogueDistance(double value) {
            dialogue_maxForcedDialogueDistance.set(value);
        }

        public com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger containerForcedDialogueTrigger() {
            return dialogue_containerForcedDialogueTrigger.value();
        }

        public void containerForcedDialogueTrigger(com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger value) {
            dialogue_containerForcedDialogueTrigger.set(value);
        }

        public com.jvn.villagerretaliation.config.ContainerWatchMode containerWatchMode() {
            return dialogue_containerWatchMode.value();
        }

        public void containerWatchMode(com.jvn.villagerretaliation.config.ContainerWatchMode value) {
            dialogue_containerWatchMode.set(value);
        }

        public int dialoguePositiveReputationCooldownDays() {
            return dialogue_dialoguePositiveReputationCooldownDays.value();
        }

        public void dialoguePositiveReputationCooldownDays(int value) {
            dialogue_dialoguePositiveReputationCooldownDays.set(value);
        }

        public int repeatedQuestionPositiveLimit() {
            return dialogue_repeatedQuestionPositiveLimit.value();
        }

        public void repeatedQuestionPositiveLimit(int value) {
            dialogue_repeatedQuestionPositiveLimit.set(value);
        }

        public int trustedRepeatedDialogueLimitBonus() {
            return dialogue_trustedRepeatedDialogueLimitBonus.value();
        }

        public void trustedRepeatedDialogueLimitBonus(int value) {
            dialogue_trustedRepeatedDialogueLimitBonus.set(value);
        }

        public int respectedRepeatedDialogueLimitBonus() {
            return dialogue_respectedRepeatedDialogueLimitBonus.value();
        }

        public void respectedRepeatedDialogueLimitBonus(int value) {
            dialogue_respectedRepeatedDialogueLimitBonus.set(value);
        }

        public int reveredRepeatedDialogueLimitBonus() {
            return dialogue_reveredRepeatedDialogueLimitBonus.value();
        }

        public void reveredRepeatedDialogueLimitBonus(int value) {
            dialogue_reveredRepeatedDialogueLimitBonus.set(value);
        }

        public int royaltyRepeatedDialogueLimitBonus() {
            return dialogue_royaltyRepeatedDialogueLimitBonus.value();
        }

        public void royaltyRepeatedDialogueLimitBonus(int value) {
            dialogue_royaltyRepeatedDialogueLimitBonus.set(value);
        }

        public int repeatedQuestionReputationLoss() {
            return dialogue_repeatedQuestionReputationLoss.value();
        }

        public void repeatedQuestionReputationLoss(int value) {
            dialogue_repeatedQuestionReputationLoss.set(value);
        }

        public int repeatedDialogueOptionResetTicks() {
            return dialogue_repeatedDialogueOptionResetTicks.value();
        }

        public void repeatedDialogueOptionResetTicks(int value) {
            dialogue_repeatedDialogueOptionResetTicks.set(value);
        }

        public int giftAnnoyanceReductionDivisor() {
            return dialogue_giftAnnoyanceReductionDivisor.value();
        }

        public void giftAnnoyanceReductionDivisor(int value) {
            dialogue_giftAnnoyanceReductionDivisor.set(value);
        }

        public double maxFollowDistance() {
            return dialogue_maxFollowDistance.value();
        }

        public void maxFollowDistance(double value) {
            dialogue_maxFollowDistance.set(value);
        }

        public int greetingReputationGain() {
            return dialogue_greetingReputationGain.value();
        }

        public void greetingReputationGain(int value) {
            dialogue_greetingReputationGain.set(value);
        }

        public int questionReputationGain() {
            return dialogue_questionReputationGain.value();
        }

        public void questionReputationGain(int value) {
            dialogue_questionReputationGain.set(value);
        }

        public int storyReputationGain() {
            return dialogue_storyReputationGain.value();
        }

        public void storyReputationGain(int value) {
            dialogue_storyReputationGain.set(value);
        }

        public int jokeReputationGain() {
            return dialogue_jokeReputationGain.value();
        }

        public void jokeReputationGain(int value) {
            dialogue_jokeReputationGain.set(value);
        }

        public int jokeReputationLoss() {
            return dialogue_jokeReputationLoss.value();
        }

        public void jokeReputationLoss(int value) {
            dialogue_jokeReputationLoss.set(value);
        }

        public int insultReputationLoss() {
            return dialogue_insultReputationLoss.value();
        }

        public void insultReputationLoss(int value) {
            dialogue_insultReputationLoss.set(value);
        }

        public int firstGreetingReputationGain() {
            return dialogue_firstGreetingReputationGain.value();
        }

        public void firstGreetingReputationGain(int value) {
            dialogue_firstGreetingReputationGain.set(value);
        }

        public int firstInsultReputationLoss() {
            return dialogue_firstInsultReputationLoss.value();
        }

        public void firstInsultReputationLoss(int value) {
            dialogue_firstInsultReputationLoss.set(value);
        }

        public boolean disableDialogueTextEffects() {
            return dialogue_disableDialogueTextEffects.value();
        }

        public void disableDialogueTextEffects(boolean value) {
            dialogue_disableDialogueTextEffects.set(value);
        }

    }
    public final Notifications_ notifications = new Notifications_();
    public class Notifications_ implements Notifications {
        public boolean enableWorldTextNotifications() {
            return notifications_enableWorldTextNotifications.value();
        }

        public void enableWorldTextNotifications(boolean value) {
            notifications_enableWorldTextNotifications.set(value);
        }

        public boolean enableAmbientMurmurs() {
            return notifications_enableAmbientMurmurs.value();
        }

        public void enableAmbientMurmurs(boolean value) {
            notifications_enableAmbientMurmurs.set(value);
        }

        public boolean enableSleepIndicators() {
            return notifications_enableSleepIndicators.value();
        }

        public void enableSleepIndicators(boolean value) {
            notifications_enableSleepIndicators.set(value);
        }

        public boolean enableDamageAlerts() {
            return notifications_enableDamageAlerts.value();
        }

        public void enableDamageAlerts(boolean value) {
            notifications_enableDamageAlerts.set(value);
        }

        public boolean enableCombatAlerts() {
            return notifications_enableCombatAlerts.value();
        }

        public void enableCombatAlerts(boolean value) {
            notifications_enableCombatAlerts.set(value);
        }

        public boolean enableTradeAndGiftWorldText() {
            return notifications_enableTradeAndGiftWorldText.value();
        }

        public void enableTradeAndGiftWorldText(boolean value) {
            notifications_enableTradeAndGiftWorldText.set(value);
        }

    }
    public final Gifts_ gifts = new Gifts_();
    public class Gifts_ implements Gifts {
        public boolean enableVillagerGifts() {
            return gifts_enableVillagerGifts.value();
        }

        public void enableVillagerGifts(boolean value) {
            gifts_enableVillagerGifts.set(value);
        }

        public boolean enableHighReputationGifts() {
            return gifts_enableHighReputationGifts.value();
        }

        public void enableHighReputationGifts(boolean value) {
            gifts_enableHighReputationGifts.set(value);
        }

        public boolean enableGiftKeepsakes() {
            return gifts_enableGiftKeepsakes.value();
        }

        public void enableGiftKeepsakes(boolean value) {
            gifts_enableGiftKeepsakes.set(value);
        }

        public boolean showGiftReactionTooltip() {
            return gifts_showGiftReactionTooltip.value();
        }

        public void showGiftReactionTooltip(boolean value) {
            gifts_showGiftReactionTooltip.set(value);
        }

        public boolean giftReactionTooltipRequiresKnownGift() {
            return gifts_giftReactionTooltipRequiresKnownGift.value();
        }

        public void giftReactionTooltipRequiresKnownGift(boolean value) {
            gifts_giftReactionTooltipRequiresKnownGift.set(value);
        }

        public double repeatedGiftReputationMultiplier() {
            return gifts_repeatedGiftReputationMultiplier.value();
        }

        public void repeatedGiftReputationMultiplier(double value) {
            gifts_repeatedGiftReputationMultiplier.set(value);
        }

        public int dailyGiftReputationCap() {
            return gifts_dailyGiftReputationCap.value();
        }

        public void dailyGiftReputationCap(int value) {
            gifts_dailyGiftReputationCap.set(value);
        }

        public int giftRequestCooldownTicks() {
            return gifts_giftRequestCooldownTicks.value();
        }

        public void giftRequestCooldownTicks(int value) {
            gifts_giftRequestCooldownTicks.set(value);
        }

    }
    public final Social_ social = new Social_();
    public class Social_ implements Social {
        public boolean enableVillagerSocialGraph() {
            return social_enableVillagerSocialGraph.value();
        }

        public void enableVillagerSocialGraph(boolean value) {
            social_enableVillagerSocialGraph.set(value);
        }

        public boolean enableVillagerMoods() {
            return social_enableVillagerMoods.value();
        }

        public void enableVillagerMoods(boolean value) {
            social_enableVillagerMoods.set(value);
        }

        public boolean enableSocialAttributeBehavior() {
            return social_enableSocialAttributeBehavior.value();
        }

        public void enableSocialAttributeBehavior(boolean value) {
            social_enableSocialAttributeBehavior.set(value);
        }

        public boolean enableSocialAttributeMoodEffects() {
            return social_enableSocialAttributeMoodEffects.value();
        }

        public void enableSocialAttributeMoodEffects(boolean value) {
            social_enableSocialAttributeMoodEffects.set(value);
        }

        public boolean enableSocialAttributeDialogueEffects() {
            return social_enableSocialAttributeDialogueEffects.value();
        }

        public void enableSocialAttributeDialogueEffects(boolean value) {
            social_enableSocialAttributeDialogueEffects.set(value);
        }

        public boolean enableSocialAttributeReputationEffects() {
            return social_enableSocialAttributeReputationEffects.value();
        }

        public void enableSocialAttributeReputationEffects(boolean value) {
            social_enableSocialAttributeReputationEffects.set(value);
        }

        public boolean enableSocialAttributeRetaliationEffects() {
            return social_enableSocialAttributeRetaliationEffects.value();
        }

        public void enableSocialAttributeRetaliationEffects(boolean value) {
            social_enableSocialAttributeRetaliationEffects.set(value);
        }

        public boolean enableSocialAttributeGossipEffects() {
            return social_enableSocialAttributeGossipEffects.value();
        }

        public void enableSocialAttributeGossipEffects(boolean value) {
            social_enableSocialAttributeGossipEffects.set(value);
        }

        public double socialAttributeEffectScale() {
            return social_socialAttributeEffectScale.value();
        }

        public void socialAttributeEffectScale(double value) {
            social_socialAttributeEffectScale.set(value);
        }

        public boolean enableFamilyBreedingRules() {
            return social_enableFamilyBreedingRules.value();
        }

        public void enableFamilyBreedingRules(boolean value) {
            social_enableFamilyBreedingRules.set(value);
        }

        public boolean enableOppositeGenderBreedingRules() {
            return social_enableOppositeGenderBreedingRules.value();
        }

        public void enableOppositeGenderBreedingRules(boolean value) {
            social_enableOppositeGenderBreedingRules.set(value);
        }

        public boolean enableParentReputationInheritance() {
            return social_enableParentReputationInheritance.value();
        }

        public void enableParentReputationInheritance(boolean value) {
            social_enableParentReputationInheritance.set(value);
        }

    }
    public final Balance_ balance = new Balance_();
    public class Balance_ implements Balance {
        public boolean babyVillagersDropLoot() {
            return balance_babyVillagersDropLoot.value();
        }

        public void babyVillagersDropLoot(boolean value) {
            balance_babyVillagersDropLoot.set(value);
        }

        public boolean requirePlayerKillForProfessionLoot() {
            return balance_requirePlayerKillForProfessionLoot.value();
        }

        public void requirePlayerKillForProfessionLoot(boolean value) {
            balance_requirePlayerKillForProfessionLoot.set(value);
        }

        public boolean hungerEffectAffectsVillagers() {
            return balance_hungerEffectAffectsVillagers.value();
        }

        public void hungerEffectAffectsVillagers(boolean value) {
            balance_hungerEffectAffectsVillagers.set(value);
        }

        public double villagerEmeraldDropChance() {
            return balance_villagerEmeraldDropChance.value();
        }

        public void villagerEmeraldDropChance(double value) {
            balance_villagerEmeraldDropChance.set(value);
        }

        public double villagerBreadDropChance() {
            return balance_villagerBreadDropChance.value();
        }

        public void villagerBreadDropChance(double value) {
            balance_villagerBreadDropChance.set(value);
        }

        public double professionDropChance() {
            return balance_professionDropChance.value();
        }

        public void professionDropChance(double value) {
            balance_professionDropChance.set(value);
        }

        public double rareDropChance() {
            return balance_rareDropChance.value();
        }

        public void rareDropChance(double value) {
            balance_rareDropChance.set(value);
        }

        public double veryRareDropChance() {
            return balance_veryRareDropChance.value();
        }

        public void veryRareDropChance(double value) {
            balance_veryRareDropChance.set(value);
        }

        public int hiredContractBaseDailyCost() {
            return balance_hiredContractBaseDailyCost.value();
        }

        public void hiredContractBaseDailyCost(int value) {
            balance_hiredContractBaseDailyCost.set(value);
        }

        public int hiredContractMinimumDailyCost() {
            return balance_hiredContractMinimumDailyCost.value();
        }

        public void hiredContractMinimumDailyCost(int value) {
            balance_hiredContractMinimumDailyCost.set(value);
        }

        public int hiredContractMaximumDailyCost() {
            return balance_hiredContractMaximumDailyCost.value();
        }

        public void hiredContractMaximumDailyCost(int value) {
            balance_hiredContractMaximumDailyCost.set(value);
        }

        public int hiredContractSkillPremiumPerTen() {
            return balance_hiredContractSkillPremiumPerTen.value();
        }

        public void hiredContractSkillPremiumPerTen(int value) {
            balance_hiredContractSkillPremiumPerTen.set(value);
        }

        public int hiredContractRoyaltyCostModifier() {
            return balance_hiredContractRoyaltyCostModifier.value();
        }

        public void hiredContractRoyaltyCostModifier(int value) {
            balance_hiredContractRoyaltyCostModifier.set(value);
        }

        public int hiredContractReveredCostModifier() {
            return balance_hiredContractReveredCostModifier.value();
        }

        public void hiredContractReveredCostModifier(int value) {
            balance_hiredContractReveredCostModifier.set(value);
        }

        public int hiredContractRespectedCostModifier() {
            return balance_hiredContractRespectedCostModifier.value();
        }

        public void hiredContractRespectedCostModifier(int value) {
            balance_hiredContractRespectedCostModifier.set(value);
        }

        public int hiredContractTrustedCostModifier() {
            return balance_hiredContractTrustedCostModifier.value();
        }

        public void hiredContractTrustedCostModifier(int value) {
            balance_hiredContractTrustedCostModifier.set(value);
        }

        public int hiredContractNeutralCostModifier() {
            return balance_hiredContractNeutralCostModifier.value();
        }

        public void hiredContractNeutralCostModifier(int value) {
            balance_hiredContractNeutralCostModifier.set(value);
        }

        public int hiredContractSuspiciousCostModifier() {
            return balance_hiredContractSuspiciousCostModifier.value();
        }

        public void hiredContractSuspiciousCostModifier(int value) {
            balance_hiredContractSuspiciousCostModifier.set(value);
        }

        public int hiredContractHostileCostModifier() {
            return balance_hiredContractHostileCostModifier.value();
        }

        public void hiredContractHostileCostModifier(int value) {
            balance_hiredContractHostileCostModifier.set(value);
        }

        public int hiredContractDespisedCostModifier() {
            return balance_hiredContractDespisedCostModifier.value();
        }

        public void hiredContractDespisedCostModifier(int value) {
            balance_hiredContractDespisedCostModifier.set(value);
        }

        public int hiredContractFearedCostModifier() {
            return balance_hiredContractFearedCostModifier.value();
        }

        public void hiredContractFearedCostModifier(int value) {
            balance_hiredContractFearedCostModifier.set(value);
        }

        public int hiredContractEarlyEndRefundPercent() {
            return balance_hiredContractEarlyEndRefundPercent.value();
        }

        public void hiredContractEarlyEndRefundPercent(int value) {
            balance_hiredContractEarlyEndRefundPercent.set(value);
        }

        public int hiredWorkTickInterval() {
            return balance_hiredWorkTickInterval.value();
        }

        public void hiredWorkTickInterval(int value) {
            balance_hiredWorkTickInterval.set(value);
        }

        public int hiredWorkNoticeCooldownSeconds() {
            return balance_hiredWorkNoticeCooldownSeconds.value();
        }

        public void hiredWorkNoticeCooldownSeconds(int value) {
            balance_hiredWorkNoticeCooldownSeconds.set(value);
        }

        public int hiredWorkDefaultRadius() {
            return balance_hiredWorkDefaultRadius.value();
        }

        public void hiredWorkDefaultRadius(int value) {
            balance_hiredWorkDefaultRadius.set(value);
        }

        public int hiredWorkMaxRadius() {
            return balance_hiredWorkMaxRadius.value();
        }

        public void hiredWorkMaxRadius(int value) {
            balance_hiredWorkMaxRadius.set(value);
        }

        public int hiredWorkBaseEfficiencyPercent() {
            return balance_hiredWorkBaseEfficiencyPercent.value();
        }

        public void hiredWorkBaseEfficiencyPercent(int value) {
            balance_hiredWorkBaseEfficiencyPercent.set(value);
        }

        public int hiredWorkMinimumEfficiencyPercent() {
            return balance_hiredWorkMinimumEfficiencyPercent.value();
        }

        public void hiredWorkMinimumEfficiencyPercent(int value) {
            balance_hiredWorkMinimumEfficiencyPercent.set(value);
        }

        public int hiredWorkMaximumEfficiencyPercent() {
            return balance_hiredWorkMaximumEfficiencyPercent.value();
        }

        public void hiredWorkMaximumEfficiencyPercent(int value) {
            balance_hiredWorkMaximumEfficiencyPercent.set(value);
        }

        public boolean enableHiredWorkSkillGrowth() {
            return balance_enableHiredWorkSkillGrowth.value();
        }

        public void enableHiredWorkSkillGrowth(boolean value) {
            balance_enableHiredWorkSkillGrowth.set(value);
        }

        public final HiredWorkSkillGrowth_ hiredWorkSkillGrowth = new HiredWorkSkillGrowth_();
        public class HiredWorkSkillGrowth_ implements HiredWorkSkillGrowth {
            public double combat() {
                return balance_hiredWorkSkillGrowth_combat.value();
            }

            public void combat(double value) {
                balance_hiredWorkSkillGrowth_combat.set(value);
            }

            public double mining() {
                return balance_hiredWorkSkillGrowth_mining.value();
            }

            public void mining(double value) {
                balance_hiredWorkSkillGrowth_mining.set(value);
            }

            public double logging() {
                return balance_hiredWorkSkillGrowth_logging.value();
            }

            public void logging(double value) {
                balance_hiredWorkSkillGrowth_logging.set(value);
            }

            public double craftsman() {
                return balance_hiredWorkSkillGrowth_craftsman.value();
            }

            public void craftsman(double value) {
                balance_hiredWorkSkillGrowth_craftsman.set(value);
            }

            public double farming() {
                return balance_hiredWorkSkillGrowth_farming.value();
            }

            public void farming(double value) {
                balance_hiredWorkSkillGrowth_farming.set(value);
            }

            public double brewing() {
                return balance_hiredWorkSkillGrowth_brewing.value();
            }

            public void brewing(double value) {
                balance_hiredWorkSkillGrowth_brewing.set(value);
            }

            public double cooking() {
                return balance_hiredWorkSkillGrowth_cooking.value();
            }

            public void cooking(double value) {
                balance_hiredWorkSkillGrowth_cooking.set(value);
            }

            public double builder() {
                return balance_hiredWorkSkillGrowth_builder.value();
            }

            public void builder(double value) {
                balance_hiredWorkSkillGrowth_builder.set(value);
            }

            public double navigation() {
                return balance_hiredWorkSkillGrowth_navigation.value();
            }

            public void navigation(double value) {
                balance_hiredWorkSkillGrowth_navigation.set(value);
            }

            public double animalHandling() {
                return balance_hiredWorkSkillGrowth_animalHandling.value();
            }

            public void animalHandling(double value) {
                balance_hiredWorkSkillGrowth_animalHandling.set(value);
            }

            public double nitwit() {
                return balance_hiredWorkSkillGrowth_nitwit.value();
            }

            public void nitwit(double value) {
                balance_hiredWorkSkillGrowth_nitwit.set(value);
            }

        }
        public int hiredBuilderMaxBlocks() {
            return balance_hiredBuilderMaxBlocks.value();
        }

        public void hiredBuilderMaxBlocks(int value) {
            balance_hiredBuilderMaxBlocks.set(value);
        }

        public int hiredBuilderMaxSiteDistance() {
            return balance_hiredBuilderMaxSiteDistance.value();
        }

        public void hiredBuilderMaxSiteDistance(int value) {
            balance_hiredBuilderMaxSiteDistance.set(value);
        }

        public int hiredBuilderMaterialStorageRadius() {
            return balance_hiredBuilderMaterialStorageRadius.value();
        }

        public void hiredBuilderMaterialStorageRadius(int value) {
            balance_hiredBuilderMaterialStorageRadius.set(value);
        }

        public int hiredBuilderBaseEmeraldCost() {
            return balance_hiredBuilderBaseEmeraldCost.value();
        }

        public void hiredBuilderBaseEmeraldCost(int value) {
            balance_hiredBuilderBaseEmeraldCost.set(value);
        }

        public int hiredBuilderEmeraldsPer64Blocks() {
            return balance_hiredBuilderEmeraldsPer64Blocks.value();
        }

        public void hiredBuilderEmeraldsPer64Blocks(int value) {
            balance_hiredBuilderEmeraldsPer64Blocks.set(value);
        }

        public boolean hiredBuilderCanReplaceSoftBlocks() {
            return balance_hiredBuilderCanReplaceSoftBlocks.value();
        }

        public void hiredBuilderCanReplaceSoftBlocks(boolean value) {
            balance_hiredBuilderCanReplaceSoftBlocks.set(value);
        }

    }
    public final Retaliation_ retaliation = new Retaliation_();
    public class Retaliation_ implements Retaliation {
        public boolean attackAggrosOnlyHitVillager() {
            return retaliation_attackAggrosOnlyHitVillager.value();
        }

        public void attackAggrosOnlyHitVillager(boolean value) {
            retaliation_attackAggrosOnlyHitVillager.set(value);
        }

        public boolean killingVillagerAggrosNearbyVillagers() {
            return retaliation_killingVillagerAggrosNearbyVillagers.value();
        }

        public void killingVillagerAggrosNearbyVillagers(boolean value) {
            retaliation_killingVillagerAggrosNearbyVillagers.set(value);
        }

        public boolean babyVillagersFleeWitnessedDeaths() {
            return retaliation_babyVillagersFleeWitnessedDeaths.value();
        }

        public void babyVillagersFleeWitnessedDeaths(boolean value) {
            retaliation_babyVillagersFleeWitnessedDeaths.set(value);
        }

        public double villagerKillAggroRadius() {
            return retaliation_villagerKillAggroRadius.value();
        }

        public void villagerKillAggroRadius(double value) {
            retaliation_villagerKillAggroRadius.set(value);
        }

        public boolean retaliationWitnessesRequireLineOfSight() {
            return retaliation_retaliationWitnessesRequireLineOfSight.value();
        }

        public void retaliationWitnessesRequireLineOfSight(boolean value) {
            retaliation_retaliationWitnessesRequireLineOfSight.set(value);
        }

        public int aggroDurationTicks() {
            return retaliation_aggroDurationTicks.value();
        }

        public void aggroDurationTicks(int value) {
            retaliation_aggroDurationTicks.set(value);
        }

        public boolean nearbyVillagersIgnoreCreativePlayers() {
            return retaliation_nearbyVillagersIgnoreCreativePlayers.value();
        }

        public void nearbyVillagersIgnoreCreativePlayers(boolean value) {
            retaliation_nearbyVillagersIgnoreCreativePlayers.set(value);
        }

    }
    public final Reputation_ reputation = new Reputation_();
    public class Reputation_ implements Reputation {
        public int directHitPenalty() {
            return reputation_directHitPenalty.value();
        }

        public void directHitPenalty(int value) {
            reputation_directHitPenalty.set(value);
        }

        public int witnessedHitPenalty() {
            return reputation_witnessedHitPenalty.value();
        }

        public void witnessedHitPenalty(int value) {
            reputation_witnessedHitPenalty.set(value);
        }

        public int witnessedKillPenalty() {
            return reputation_witnessedKillPenalty.value();
        }

        public void witnessedKillPenalty(int value) {
            reputation_witnessedKillPenalty.set(value);
        }

        public int witnessedBabyKillPenalty() {
            return reputation_witnessedBabyKillPenalty.value();
        }

        public void witnessedBabyKillPenalty(int value) {
            reputation_witnessedBabyKillPenalty.set(value);
        }

        public int witnessedIronGolemKillPenalty() {
            return reputation_witnessedIronGolemKillPenalty.value();
        }

        public void witnessedIronGolemKillPenalty(int value) {
            reputation_witnessedIronGolemKillPenalty.set(value);
        }

        public int containerBreakReputationLoss() {
            return reputation_containerBreakReputationLoss.value();
        }

        public void containerBreakReputationLoss(int value) {
            reputation_containerBreakReputationLoss.set(value);
        }

        public int generatedContainerBreakItemReputationLoss() {
            return reputation_generatedContainerBreakItemReputationLoss.value();
        }

        public void generatedContainerBreakItemReputationLoss(int value) {
            reputation_generatedContainerBreakItemReputationLoss.set(value);
        }

        public int tradeReputationGain() {
            return reputation_tradeReputationGain.value();
        }

        public void tradeReputationGain(int value) {
            reputation_tradeReputationGain.set(value);
        }

        public int maxTradeReputationGainPerVillagerPerDay() {
            return reputation_maxTradeReputationGainPerVillagerPerDay.value();
        }

        public void maxTradeReputationGainPerVillagerPerDay(int value) {
            reputation_maxTradeReputationGainPerVillagerPerDay.set(value);
        }

        public int sleepingVillagerBotherReputationLoss() {
            return reputation_sleepingVillagerBotherReputationLoss.value();
        }

        public void sleepingVillagerBotherReputationLoss(int value) {
            reputation_sleepingVillagerBotherReputationLoss.set(value);
        }

        public int sleepingVillagerBedBreakReputationLoss() {
            return reputation_sleepingVillagerBedBreakReputationLoss.value();
        }

        public void sleepingVillagerBedBreakReputationLoss(int value) {
            reputation_sleepingVillagerBedBreakReputationLoss.set(value);
        }

        public int healVillagerGain() {
            return reputation_healVillagerGain.value();
        }

        public void healVillagerGain(int value) {
            reputation_healVillagerGain.set(value);
        }

        public int saveVillagerGain() {
            return reputation_saveVillagerGain.value();
        }

        public void saveVillagerGain(int value) {
            reputation_saveVillagerGain.set(value);
        }

        public int positiveWitnessGain() {
            return reputation_positiveWitnessGain.value();
        }

        public void positiveWitnessGain(int value) {
            reputation_positiveWitnessGain.set(value);
        }

        public double hostileMobAssistReputationMultiplier() {
            return reputation_hostileMobAssistReputationMultiplier.value();
        }

        public void hostileMobAssistReputationMultiplier(double value) {
            reputation_hostileMobAssistReputationMultiplier.set(value);
        }

        public double gossipReputationMultiplier() {
            return reputation_gossipReputationMultiplier.value();
        }

        public void gossipReputationMultiplier(double value) {
            reputation_gossipReputationMultiplier.set(value);
        }

        public int royaltyThreshold() {
            return reputation_royaltyThreshold.value();
        }

        public void royaltyThreshold(int value) {
            reputation_royaltyThreshold.set(value);
        }

        public int reveredThreshold() {
            return reputation_reveredThreshold.value();
        }

        public void reveredThreshold(int value) {
            reputation_reveredThreshold.set(value);
        }

        public int respectedThreshold() {
            return reputation_respectedThreshold.value();
        }

        public void respectedThreshold(int value) {
            reputation_respectedThreshold.set(value);
        }

        public int trustedThreshold() {
            return reputation_trustedThreshold.value();
        }

        public void trustedThreshold(int value) {
            reputation_trustedThreshold.set(value);
        }

        public int suspiciousThreshold() {
            return reputation_suspiciousThreshold.value();
        }

        public void suspiciousThreshold(int value) {
            reputation_suspiciousThreshold.set(value);
        }

        public int hostileThreshold() {
            return reputation_hostileThreshold.value();
        }

        public void hostileThreshold(int value) {
            reputation_hostileThreshold.set(value);
        }

        public int despisedThreshold() {
            return reputation_despisedThreshold.value();
        }

        public void despisedThreshold(int value) {
            reputation_despisedThreshold.set(value);
        }

        public int fearedThreshold() {
            return reputation_fearedThreshold.value();
        }

        public void fearedThreshold(int value) {
            reputation_fearedThreshold.set(value);
        }

        public double witnessRadius() {
            return reputation_witnessRadius.value();
        }

        public void witnessRadius(double value) {
            reputation_witnessRadius.set(value);
        }

        public double gossipRadius() {
            return reputation_gossipRadius.value();
        }

        public void gossipRadius(double value) {
            reputation_gossipRadius.set(value);
        }

        public double despisedSightRadius() {
            return reputation_despisedSightRadius.value();
        }

        public void despisedSightRadius(double value) {
            reputation_despisedSightRadius.set(value);
        }

        public boolean reputationDecayEnabled() {
            return reputation_reputationDecayEnabled.value();
        }

        public void reputationDecayEnabled(boolean value) {
            reputation_reputationDecayEnabled.set(value);
        }

        public int reputationDecayInterval() {
            return reputation_reputationDecayInterval.value();
        }

        public void reputationDecayInterval(int value) {
            reputation_reputationDecayInterval.set(value);
        }

        public int reputationDecayAmount() {
            return reputation_reputationDecayAmount.value();
        }

        public void reputationDecayAmount(int value) {
            reputation_reputationDecayAmount.set(value);
        }

        public int pruneNeutralEntriesAfterDays() {
            return reputation_pruneNeutralEntriesAfterDays.value();
        }

        public void pruneNeutralEntriesAfterDays(int value) {
            reputation_pruneNeutralEntriesAfterDays.set(value);
        }

        public boolean witnessReputationRequiresLineOfSight() {
            return reputation_witnessReputationRequiresLineOfSight.value();
        }

        public void witnessReputationRequiresLineOfSight(boolean value) {
            reputation_witnessReputationRequiresLineOfSight.set(value);
        }

        public boolean enableReputationTradePricing() {
            return reputation_enableReputationTradePricing.value();
        }

        public void enableReputationTradePricing(boolean value) {
            reputation_enableReputationTradePricing.set(value);
        }

        public double reputationTradePriceScale() {
            return reputation_reputationTradePriceScale.value();
        }

        public void reputationTradePriceScale(double value) {
            reputation_reputationTradePriceScale.set(value);
        }

    }
    public final PlayerRaids_ playerRaids = new PlayerRaids_();
    public class PlayerRaids_ implements PlayerRaids {
        public boolean enabled() {
            return playerRaids_enabled.value();
        }

        public void enabled(boolean value) {
            playerRaids_enabled.set(value);
        }

        public boolean confirmRaidHorn() {
            return playerRaids_confirmRaidHorn.value();
        }

        public void confirmRaidHorn(boolean value) {
            playerRaids_confirmRaidHorn.set(value);
        }

        public int preparationTicks() {
            return playerRaids_preparationTicks.value();
        }

        public void preparationTicks(int value) {
            playerRaids_preparationTicks.set(value);
        }

        public int abandonmentTicks() {
            return playerRaids_abandonmentTicks.value();
        }

        public void abandonmentTicks(int value) {
            playerRaids_abandonmentTicks.set(value);
        }

        public int villageCooldownDays() {
            return playerRaids_villageCooldownDays.value();
        }

        public void villageCooldownDays(int value) {
            playerRaids_villageCooldownDays.set(value);
        }

        public int bossBarRange() {
            return playerRaids_bossBarRange.value();
        }

        public void bossBarRange(int value) {
            playerRaids_bossBarRange.set(value);
        }

        public int defendersPerGolem() {
            return playerRaids_defendersPerGolem.value();
        }

        public void defendersPerGolem(int value) {
            playerRaids_defendersPerGolem.set(value);
        }

        public int minimumGolems() {
            return playerRaids_minimumGolems.value();
        }

        public void minimumGolems(int value) {
            playerRaids_minimumGolems.set(value);
        }

        public int maximumGolems() {
            return playerRaids_maximumGolems.value();
        }

        public void maximumGolems(int value) {
            playerRaids_maximumGolems.set(value);
        }

        public int raidersPerBonusGolem() {
            return playerRaids_raidersPerBonusGolem.value();
        }

        public void raidersPerBonusGolem(int value) {
            playerRaids_raidersPerBonusGolem.set(value);
        }

    }
    public final Trade_ trade = new Trade_();
    public class Trade_ implements Trade {
        public boolean enableSkillTradeOverhaul() {
            return trade_enableSkillTradeOverhaul.value();
        }

        public void enableSkillTradeOverhaul(boolean value) {
            trade_enableSkillTradeOverhaul.set(value);
        }

        public boolean disableVillagerWalletLimit() {
            return trade_disableVillagerWalletLimit.value();
        }

        public void disableVillagerWalletLimit(boolean value) {
            trade_disableVillagerWalletLimit.set(value);
        }

        public boolean enableSpecialOrders() {
            return trade_enableSpecialOrders.value();
        }

        public void enableSpecialOrders(boolean value) {
            trade_enableSpecialOrders.set(value);
        }

        public com.jvn.villagerretaliation.reputation.VillagerReputationLevel specialOrderMinReputation() {
            return trade_specialOrderMinReputation.value();
        }

        public void specialOrderMinReputation(com.jvn.villagerretaliation.reputation.VillagerReputationLevel value) {
            trade_specialOrderMinReputation.set(value);
        }

        public int specialOrderWaitDays() {
            return trade_specialOrderWaitDays.value();
        }

        public void specialOrderWaitDays(int value) {
            trade_specialOrderWaitDays.set(value);
        }

        public int specialOrderCooldownDays() {
            return trade_specialOrderCooldownDays.value();
        }

        public void specialOrderCooldownDays(int value) {
            trade_specialOrderCooldownDays.set(value);
        }

        public boolean specialOrderExtraCostEnabled() {
            return trade_specialOrderExtraCostEnabled.value();
        }

        public void specialOrderExtraCostEnabled(boolean value) {
            trade_specialOrderExtraCostEnabled.set(value);
        }

        public int specialOrderMaxActivePerPlayer() {
            return trade_specialOrderMaxActivePerPlayer.value();
        }

        public void specialOrderMaxActivePerPlayer(int value) {
            trade_specialOrderMaxActivePerPlayer.set(value);
        }

        public boolean skillTradeQualityScaling() {
            return trade_skillTradeQualityScaling.value();
        }

        public void skillTradeQualityScaling(boolean value) {
            trade_skillTradeQualityScaling.set(value);
        }

        public boolean skillTradeLowSkillPenalties() {
            return trade_skillTradeLowSkillPenalties.value();
        }

        public void skillTradeLowSkillPenalties(boolean value) {
            trade_skillTradeLowSkillPenalties.set(value);
        }

        public int skillTradeMaxEnchantmentLevel() {
            return trade_skillTradeMaxEnchantmentLevel.value();
        }

        public void skillTradeMaxEnchantmentLevel(int value) {
            trade_skillTradeMaxEnchantmentLevel.set(value);
        }

        public double skillTradeRareChanceMultiplier() {
            return trade_skillTradeRareChanceMultiplier.value();
        }

        public void skillTradeRareChanceMultiplier(double value) {
            trade_skillTradeRareChanceMultiplier.set(value);
        }

        public boolean skillTradeAllowHighTierEquipment() {
            return trade_skillTradeAllowHighTierEquipment.value();
        }

        public void skillTradeAllowHighTierEquipment(boolean value) {
            trade_skillTradeAllowHighTierEquipment.set(value);
        }

        public boolean skillTradeAllowSpecialArrows() {
            return trade_skillTradeAllowSpecialArrows.value();
        }

        public void skillTradeAllowSpecialArrows(boolean value) {
            trade_skillTradeAllowSpecialArrows.set(value);
        }

        public boolean skillTradeAllowRareSpecialtyTrades() {
            return trade_skillTradeAllowRareSpecialtyTrades.value();
        }

        public void skillTradeAllowRareSpecialtyTrades(boolean value) {
            trade_skillTradeAllowRareSpecialtyTrades.set(value);
        }

        public boolean enableSkillGrowthFromTradingLevels() {
            return trade_enableSkillGrowthFromTradingLevels.value();
        }

        public void enableSkillGrowthFromTradingLevels(boolean value) {
            trade_enableSkillGrowthFromTradingLevels.set(value);
        }

        public boolean enableRegularTradeSkillGrowth() {
            return trade_enableRegularTradeSkillGrowth.value();
        }

        public void enableRegularTradeSkillGrowth(boolean value) {
            trade_enableRegularTradeSkillGrowth.set(value);
        }

        public double regularTradeSkillGrowthAmount() {
            return trade_regularTradeSkillGrowthAmount.value();
        }

        public void regularTradeSkillGrowthAmount(double value) {
            trade_regularTradeSkillGrowthAmount.set(value);
        }

        public boolean enableSkillBasedTradeLeveling() {
            return trade_enableSkillBasedTradeLeveling.value();
        }

        public void enableSkillBasedTradeLeveling(boolean value) {
            trade_enableSkillBasedTradeLeveling.set(value);
        }

        public double skillBasedTradeLevelingMinMultiplier() {
            return trade_skillBasedTradeLevelingMinMultiplier.value();
        }

        public void skillBasedTradeLevelingMinMultiplier(double value) {
            trade_skillBasedTradeLevelingMinMultiplier.set(value);
        }

        public double skillBasedTradeLevelingMaxMultiplier() {
            return trade_skillBasedTradeLevelingMaxMultiplier.value();
        }

        public void skillBasedTradeLevelingMaxMultiplier(double value) {
            trade_skillBasedTradeLevelingMaxMultiplier.set(value);
        }

        public boolean enableSkillGrowthFeedback() {
            return trade_enableSkillGrowthFeedback.value();
        }

        public void enableSkillGrowthFeedback(boolean value) {
            trade_enableSkillGrowthFeedback.set(value);
        }

        public int skillGrowthPrimaryMin() {
            return trade_skillGrowthPrimaryMin.value();
        }

        public void skillGrowthPrimaryMin(int value) {
            trade_skillGrowthPrimaryMin.set(value);
        }

        public int skillGrowthPrimaryMax() {
            return trade_skillGrowthPrimaryMax.value();
        }

        public void skillGrowthPrimaryMax(int value) {
            trade_skillGrowthPrimaryMax.set(value);
        }

    }
    public final DebugOverlay_ debugOverlay = new DebugOverlay_();
    public class DebugOverlay_ implements DebugOverlay {
        public boolean showVillageBounds() {
            return debugOverlay_showVillageBounds.value();
        }

        public void showVillageBounds(boolean value) {
            debugOverlay_showVillageBounds.set(value);
        }

        public boolean highlightRaidDefenders() {
            return debugOverlay_highlightRaidDefenders.value();
        }

        public void highlightRaidDefenders(boolean value) {
            debugOverlay_highlightRaidDefenders.set(value);
        }

        public int debugPreviewMaxVisibleNodes() {
            return debugOverlay_debugPreviewMaxVisibleNodes.value();
        }

        public void debugPreviewMaxVisibleNodes(int value) {
            debugOverlay_debugPreviewMaxVisibleNodes.set(value);
        }

        public int debugPreviewMaxVisibleLabels() {
            return debugOverlay_debugPreviewMaxVisibleLabels.value();
        }

        public void debugPreviewMaxVisibleLabels(int value) {
            debugOverlay_debugPreviewMaxVisibleLabels.set(value);
        }

        public int debugPreviewMaxVisibleSegments() {
            return debugOverlay_debugPreviewMaxVisibleSegments.value();
        }

        public void debugPreviewMaxVisibleSegments(int value) {
            debugOverlay_debugPreviewMaxVisibleSegments.set(value);
        }

        public boolean showVillagerReputationDebugOverlay() {
            return debugOverlay_showVillagerReputationDebugOverlay.value();
        }

        public void showVillagerReputationDebugOverlay(boolean value) {
            debugOverlay_showVillagerReputationDebugOverlay.set(value);
        }

        public double reputationDebugOverlayMaxDistance() {
            return debugOverlay_reputationDebugOverlayMaxDistance.value();
        }

        public void reputationDebugOverlayMaxDistance(double value) {
            debugOverlay_reputationDebugOverlayMaxDistance.set(value);
        }

        public boolean reputationDebugOverlayShowTier() {
            return debugOverlay_reputationDebugOverlayShowTier.value();
        }

        public void reputationDebugOverlayShowTier(boolean value) {
            debugOverlay_reputationDebugOverlayShowTier.set(value);
        }

        public boolean reputationDebugOverlayShowNumber() {
            return debugOverlay_reputationDebugOverlayShowNumber.value();
        }

        public void reputationDebugOverlayShowNumber(boolean value) {
            debugOverlay_reputationDebugOverlayShowNumber.set(value);
        }

        public boolean reputationDebugOverlayShowHealth() {
            return debugOverlay_reputationDebugOverlayShowHealth.value();
        }

        public void reputationDebugOverlayShowHealth(boolean value) {
            debugOverlay_reputationDebugOverlayShowHealth.set(value);
        }

        public boolean reputationDebugOverlayShowArmor() {
            return debugOverlay_reputationDebugOverlayShowArmor.value();
        }

        public void reputationDebugOverlayShowArmor(boolean value) {
            debugOverlay_reputationDebugOverlayShowArmor.set(value);
        }

        public boolean reputationDebugOverlayShowHunger() {
            return debugOverlay_reputationDebugOverlayShowHunger.value();
        }

        public void reputationDebugOverlayShowHunger(boolean value) {
            debugOverlay_reputationDebugOverlayShowHunger.set(value);
        }

        public boolean reputationDebugOverlayRequireAdvancedTooltips() {
            return debugOverlay_reputationDebugOverlayRequireAdvancedTooltips.value();
        }

        public void reputationDebugOverlayRequireAdvancedTooltips(boolean value) {
            debugOverlay_reputationDebugOverlayRequireAdvancedTooltips.set(value);
        }

        public boolean reputationDebugOverlayOnlyWhenSneaking() {
            return debugOverlay_reputationDebugOverlayOnlyWhenSneaking.value();
        }

        public void reputationDebugOverlayOnlyWhenSneaking(boolean value) {
            debugOverlay_reputationDebugOverlayOnlyWhenSneaking.set(value);
        }

    }
    public final Combat_ combat = new Combat_();
    public class Combat_ implements Combat {
        public boolean enableVillagerSleepHealing() {
            return combat_enableVillagerSleepHealing.value();
        }

        public void enableVillagerSleepHealing(boolean value) {
            combat_enableVillagerSleepHealing.set(value);
        }

        public double villagerSleepHealingMaxHealthPercent() {
            return combat_villagerSleepHealingMaxHealthPercent.value();
        }

        public void villagerSleepHealingMaxHealthPercent(double value) {
            combat_villagerSleepHealingMaxHealthPercent.set(value);
        }

        public boolean enableVillagerDownedState() {
            return combat_enableVillagerDownedState.value();
        }

        public void enableVillagerDownedState(boolean value) {
            combat_enableVillagerDownedState.set(value);
        }

        public boolean allVillagersUseDownedState() {
            return combat_allVillagersUseDownedState.value();
        }

        public void allVillagersUseDownedState(boolean value) {
            combat_allVillagersUseDownedState.set(value);
        }

        public boolean raidVillagersUseDownedState() {
            return combat_raidVillagersUseDownedState.value();
        }

        public void raidVillagersUseDownedState(boolean value) {
            combat_raidVillagersUseDownedState.set(value);
        }

        public boolean hiredVillagersUseDownedState() {
            return combat_hiredVillagersUseDownedState.value();
        }

        public void hiredVillagersUseDownedState(boolean value) {
            combat_hiredVillagersUseDownedState.set(value);
        }

        public boolean partyVillagersUseDownedState() {
            return combat_partyVillagersUseDownedState.value();
        }

        public void partyVillagersUseDownedState(boolean value) {
            combat_partyVillagersUseDownedState.set(value);
        }

        public boolean playerDamageDownsEligibleVillagers() {
            return combat_playerDamageDownsEligibleVillagers.value();
        }

        public void playerDamageDownsEligibleVillagers(boolean value) {
            combat_playerDamageDownsEligibleVillagers.set(value);
        }

        public boolean mobDamageDownsEligibleVillagers() {
            return combat_mobDamageDownsEligibleVillagers.value();
        }

        public void mobDamageDownsEligibleVillagers(boolean value) {
            combat_mobDamageDownsEligibleVillagers.set(value);
        }

        public boolean environmentalDamageDownsEligibleVillagers() {
            return combat_environmentalDamageDownsEligibleVillagers.value();
        }

        public void environmentalDamageDownsEligibleVillagers(boolean value) {
            combat_environmentalDamageDownsEligibleVillagers.set(value);
        }

        public int downedMinimumTicks() {
            return combat_downedMinimumTicks.value();
        }

        public void downedMinimumTicks(int value) {
            combat_downedMinimumTicks.set(value);
        }

        public double downedRecoveryHealthPercent() {
            return combat_downedRecoveryHealthPercent.value();
        }

        public void downedRecoveryHealthPercent(double value) {
            combat_downedRecoveryHealthPercent.set(value);
        }

        public double downedThreatRadius() {
            return combat_downedThreatRadius.value();
        }

        public void downedThreatRadius(double value) {
            combat_downedThreatRadius.set(value);
        }

        public int downedQuietTicks() {
            return combat_downedQuietTicks.value();
        }

        public void downedQuietTicks(int value) {
            combat_downedQuietTicks.set(value);
        }

        public boolean weaponsmithsFightBack() {
            return combat_weaponsmithsFightBack.value();
        }

        public void weaponsmithsFightBack(boolean value) {
            combat_weaponsmithsFightBack.set(value);
        }

        public boolean toolsmithsFightBack() {
            return combat_toolsmithsFightBack.value();
        }

        public void toolsmithsFightBack(boolean value) {
            combat_toolsmithsFightBack.set(value);
        }

        public boolean armorersFightBack() {
            return combat_armorersFightBack.value();
        }

        public void armorersFightBack(boolean value) {
            combat_armorersFightBack.set(value);
        }

        public boolean fletchersFightBack() {
            return combat_fletchersFightBack.value();
        }

        public void fletchersFightBack(boolean value) {
            combat_fletchersFightBack.set(value);
        }

        public boolean butchersFightBack() {
            return combat_butchersFightBack.value();
        }

        public void butchersFightBack(boolean value) {
            combat_butchersFightBack.set(value);
        }

        public boolean villagersTargetHostileMobs() {
            return combat_villagersTargetHostileMobs.value();
        }

        public void villagersTargetHostileMobs(boolean value) {
            combat_villagersTargetHostileMobs.set(value);
        }

        public boolean wanderingTradersTargetHostileMobs() {
            return combat_wanderingTradersTargetHostileMobs.value();
        }

        public void wanderingTradersTargetHostileMobs(boolean value) {
            combat_wanderingTradersTargetHostileMobs.set(value);
        }

        public boolean villagersRetaliateAgainstHostileMobs() {
            return combat_villagersRetaliateAgainstHostileMobs.value();
        }

        public void villagersRetaliateAgainstHostileMobs(boolean value) {
            combat_villagersRetaliateAgainstHostileMobs.set(value);
        }

        public boolean wanderingTradersRetaliateAgainstHostileMobs() {
            return combat_wanderingTradersRetaliateAgainstHostileMobs.value();
        }

        public void wanderingTradersRetaliateAgainstHostileMobs(boolean value) {
            combat_wanderingTradersRetaliateAgainstHostileMobs.set(value);
        }

        public boolean villagersStandGroundAgainstHostileMobs() {
            return combat_villagersStandGroundAgainstHostileMobs.value();
        }

        public void villagersStandGroundAgainstHostileMobs(boolean value) {
            combat_villagersStandGroundAgainstHostileMobs.set(value);
        }

        public boolean villagersFleeVisibleCreepers() {
            return combat_villagersFleeVisibleCreepers.value();
        }

        public void villagersFleeVisibleCreepers(boolean value) {
            combat_villagersFleeVisibleCreepers.set(value);
        }

        public boolean villagersPickUpGroundWeapons() {
            return combat_villagersPickUpGroundWeapons.value();
        }

        public void villagersPickUpGroundWeapons(boolean value) {
            combat_villagersPickUpGroundWeapons.set(value);
        }

        public boolean wanderingTradersPickUpGroundWeapons() {
            return combat_wanderingTradersPickUpGroundWeapons.value();
        }

        public void wanderingTradersPickUpGroundWeapons(boolean value) {
            combat_wanderingTradersPickUpGroundWeapons.set(value);
        }

        public double naturalHostileTargetRadius() {
            return combat_naturalHostileTargetRadius.value();
        }

        public void naturalHostileTargetRadius(double value) {
            combat_naturalHostileTargetRadius.set(value);
        }

        public double combatWeaponDropChance() {
            return combat_combatWeaponDropChance.value();
        }

        public void combatWeaponDropChance(double value) {
            combat_combatWeaponDropChance.set(value);
        }

        public double combatWeaponEnchantChance() {
            return combat_combatWeaponEnchantChance.value();
        }

        public void combatWeaponEnchantChance(double value) {
            combat_combatWeaponEnchantChance.set(value);
        }

        public double armorerShieldChanceHard() {
            return combat_armorerShieldChanceHard.value();
        }

        public void armorerShieldChanceHard(double value) {
            combat_armorerShieldChanceHard.set(value);
        }

        public boolean clericsUsePotions() {
            return combat_clericsUsePotions.value();
        }

        public void clericsUsePotions(boolean value) {
            combat_clericsUsePotions.set(value);
        }

        public double passiveClericAllyHealRange() {
            return combat_passiveClericAllyHealRange.value();
        }

        public void passiveClericAllyHealRange(double value) {
            combat_passiveClericAllyHealRange.set(value);
        }

        public double passiveClericAllyHealHealthThreshold() {
            return combat_passiveClericAllyHealHealthThreshold.value();
        }

        public void passiveClericAllyHealHealthThreshold(double value) {
            combat_passiveClericAllyHealHealthThreshold.set(value);
        }

        public boolean passiveClericAllyHealRequiresLineOfSight() {
            return combat_passiveClericAllyHealRequiresLineOfSight.value();
        }

        public void passiveClericAllyHealRequiresLineOfSight(boolean value) {
            combat_passiveClericAllyHealRequiresLineOfSight.set(value);
        }

        public boolean hostileTierHarassThrowEnabled() {
            return combat_hostileTierHarassThrowEnabled.value();
        }

        public void hostileTierHarassThrowEnabled(boolean value) {
            combat_hostileTierHarassThrowEnabled.set(value);
        }

        public int hostileTierHarassThrowMinIntervalTicks() {
            return combat_hostileTierHarassThrowMinIntervalTicks.value();
        }

        public void hostileTierHarassThrowMinIntervalTicks(int value) {
            combat_hostileTierHarassThrowMinIntervalTicks.set(value);
        }

        public int hostileTierHarassThrowMaxIntervalTicks() {
            return combat_hostileTierHarassThrowMaxIntervalTicks.value();
        }

        public void hostileTierHarassThrowMaxIntervalTicks(int value) {
            combat_hostileTierHarassThrowMaxIntervalTicks.set(value);
        }

    }
    public final Duels_ duels = new Duels_();
    public class Duels_ implements Duels {
        public boolean enabled() {
            return duels_enabled.value();
        }

        public void enabled(boolean value) {
            duels_enabled.set(value);
        }

        public boolean allowBringYourOwnLoadout() {
            return duels_allowBringYourOwnLoadout.value();
        }

        public void allowBringYourOwnLoadout(boolean value) {
            duels_allowBringYourOwnLoadout.set(value);
        }

        public int minimumGuts() {
            return duels_minimumGuts.value();
        }

        public void minimumGuts(int value) {
            duels_minimumGuts.set(value);
        }

        public int cooldownDays() {
            return duels_cooldownDays.value();
        }

        public void cooldownDays(int value) {
            duels_cooldownDays.set(value);
        }

        public int refusalLosses() {
            return duels_refusalLosses.value();
        }

        public void refusalLosses(int value) {
            duels_refusalLosses.set(value);
        }

        public int arenaRadius() {
            return duels_arenaRadius.value();
        }

        public void arenaRadius(int value) {
            duels_arenaRadius.set(value);
        }

        public boolean showArenaParticles() {
            return duels_showArenaParticles.value();
        }

        public void showArenaParticles(boolean value) {
            duels_showArenaParticles.set(value);
        }

        public int boundaryGraceTicks() {
            return duels_boundaryGraceTicks.value();
        }

        public void boundaryGraceTicks(int value) {
            duels_boundaryGraceTicks.set(value);
        }

        public int timeoutTicks() {
            return duels_timeoutTicks.value();
        }

        public void timeoutTicks(int value) {
            duels_timeoutTicks.set(value);
        }

        public int spectatorRadius() {
            return duels_spectatorRadius.value();
        }

        public void spectatorRadius(int value) {
            duels_spectatorRadius.set(value);
        }

        public int spectatorCap() {
            return duels_spectatorCap.value();
        }

        public void spectatorCap(int value) {
            duels_spectatorCap.set(value);
        }

        public int watcherReputation() {
            return duels_watcherReputation.value();
        }

        public void watcherReputation(int value) {
            duels_watcherReputation.set(value);
        }

    }
    public final Wanderer_ wanderer = new Wanderer_();
    public class Wanderer_ implements Wanderer {
        public boolean dropEmeralds() {
            return wanderer_dropEmeralds.value();
        }

        public void dropEmeralds(boolean value) {
            wanderer_dropEmeralds.set(value);
        }

        public boolean dropInvisibilityPotion() {
            return wanderer_dropInvisibilityPotion.value();
        }

        public void dropInvisibilityPotion(boolean value) {
            wanderer_dropInvisibilityPotion.set(value);
        }

        public boolean dropRandomCurrentTrade() {
            return wanderer_dropRandomCurrentTrade.value();
        }

        public void dropRandomCurrentTrade(boolean value) {
            wanderer_dropRandomCurrentTrade.set(value);
        }

        public double randomTradeDropChance() {
            return wanderer_randomTradeDropChance.value();
        }

        public void randomTradeDropChance(double value) {
            wanderer_randomTradeDropChance.set(value);
        }

    }
    public final Quest_ quest = new Quest_();
    public class Quest_ implements Quest {
        public boolean showQuestIndicators() {
            return quest_showQuestIndicators.value();
        }

        public void showQuestIndicators(boolean value) {
            quest_showQuestIndicators.set(value);
        }

        public boolean enableQuestItemShaderHighlights() {
            return quest_enableQuestItemShaderHighlights.value();
        }

        public void enableQuestItemShaderHighlights(boolean value) {
            quest_enableQuestItemShaderHighlights.set(value);
        }

        public com.jvn.villagerretaliation.config.QuestItemHighlightMode questItemHighlightMode() {
            return quest_questItemHighlightMode.value();
        }

        public void questItemHighlightMode(com.jvn.villagerretaliation.config.QuestItemHighlightMode value) {
            quest_questItemHighlightMode.set(value);
        }

    }
    public interface General {
        boolean enableVillagerDrops();
        void enableVillagerDrops(boolean value);
        boolean enableWanderingTraderDrops();
        void enableWanderingTraderDrops(boolean value);
        boolean enableVillagerRetaliation();
        void enableVillagerRetaliation(boolean value);
        boolean enableVillagerReputation();
        void enableVillagerReputation(boolean value);
        boolean enableVanillaGossipIntegration();
        void enableVanillaGossipIntegration(boolean value);
        boolean enableDespisedKillOnSight();
        void enableDespisedKillOnSight(boolean value);
        boolean despisedKillOnSightInterruptsHiredWork();
        void despisedKillOnSightInterruptsHiredWork(boolean value);
        com.jvn.villagerretaliation.config.ReputationChangeDisplayMode reputationChangeDisplayMode();
        void reputationChangeDisplayMode(com.jvn.villagerretaliation.config.ReputationChangeDisplayMode value);
        com.jvn.villagerretaliation.config.ReputationChangeNotificationStyle reputationChangeNotificationStyle();
        void reputationChangeNotificationStyle(com.jvn.villagerretaliation.config.ReputationChangeNotificationStyle value);
        com.jvn.villagerretaliation.config.ReputationChangeHudPosition reputationChangeHudPosition();
        void reputationChangeHudPosition(com.jvn.villagerretaliation.config.ReputationChangeHudPosition value);
        boolean collapseReputationChangeNotifications();
        void collapseReputationChangeNotifications(boolean value);
        boolean showVillagerNameTags();
        void showVillagerNameTags(boolean value);
        com.jvn.villagerretaliation.config.VillagerStatDisplayMode villagerStatDisplayMode();
        void villagerStatDisplayMode(com.jvn.villagerretaliation.config.VillagerStatDisplayMode value);
        boolean villagerReputationHoverTooltipRequiresEmerald();
        void villagerReputationHoverTooltipRequiresEmerald(boolean value);
        boolean showTradeGuiReputationIcon();
        void showTradeGuiReputationIcon(boolean value);
        boolean enableVillagerDeathMessages();
        void enableVillagerDeathMessages(boolean value);
    }
    public interface Dialogue {
        boolean enableInteractionScreen();
        void enableInteractionScreen(boolean value);
        boolean shiftRightClickBypassesInteractionScreen();
        void shiftRightClickBypassesInteractionScreen(boolean value);
        boolean enableDialogueReputationEffects();
        void enableDialogueReputationEffects(boolean value);
        boolean enableDialogueCameraFocus();
        void enableDialogueCameraFocus(boolean value);
        boolean enableDialogueCinematicBars();
        void enableDialogueCinematicBars(boolean value);
        int dialogueCinematicBarHeight();
        void dialogueCinematicBarHeight(int value);
        int dialogueCinematicBarMinSlant();
        void dialogueCinematicBarMinSlant(int value);
        int dialogueCinematicBarMaxSlant();
        void dialogueCinematicBarMaxSlant(int value);
        boolean animateDialogueCinematicBars();
        void animateDialogueCinematicBars(boolean value);
        boolean enableForcedDialogue();
        void enableForcedDialogue(boolean value);
        boolean enableContainerForcedDialogue();
        void enableContainerForcedDialogue(boolean value);
        boolean enableContainerOpenReaction();
        void enableContainerOpenReaction(boolean value);
        boolean enableRetaliationForcedDialogue();
        void enableRetaliationForcedDialogue(boolean value);
        boolean enablePlayerItemProximityForcedDialogue();
        void enablePlayerItemProximityForcedDialogue(boolean value);
        boolean separateVillagerChatMessages();
        void separateVillagerChatMessages(boolean value);
        boolean separateVillagerChatSpeakers();
        void separateVillagerChatSpeakers(boolean value);
        com.jvn.villagerretaliation.config.InteractionChatPosition interactionChatPosition();
        void interactionChatPosition(com.jvn.villagerretaliation.config.InteractionChatPosition value);
        com.jvn.villagerretaliation.config.VillagerChatBroadcastMode villagerChatBroadcastMode();
        void villagerChatBroadcastMode(com.jvn.villagerretaliation.config.VillagerChatBroadcastMode value);
        int villagerChatBroadcastRadius();
        void villagerChatBroadcastRadius(int value);
        boolean showPersonalInteractionDialogueToNearbyPlayers();
        void showPersonalInteractionDialogueToNearbyPlayers(boolean value);
        com.jvn.villagerretaliation.config.DialogueTextSpeed dialogueTextSpeed();
        void dialogueTextSpeed(com.jvn.villagerretaliation.config.DialogueTextSpeed value);
        boolean enableDialogueBlipAudio();
        void enableDialogueBlipAudio(boolean value);
        double dialogueBlipVolume();
        void dialogueBlipVolume(double value);
        double dialogueBlipMinPitch();
        void dialogueBlipMinPitch(double value);
        double dialogueBlipMaxPitch();
        void dialogueBlipMaxPitch(double value);
        double dialogueCameraZoomAmount();
        void dialogueCameraZoomAmount(double value);
        boolean enableNormalDialogueCameraFocus();
        void enableNormalDialogueCameraFocus(boolean value);
        double normalDialogueCameraZoomAmount();
        void normalDialogueCameraZoomAmount(double value);
        int dialogueCameraTransitionTicks();
        void dialogueCameraTransitionTicks(int value);
        boolean freezeVillagerDuringDialogue();
        void freezeVillagerDuringDialogue(boolean value);
        double maxDialogueDistance();
        void maxDialogueDistance(double value);
        double maxForcedDialogueDistance();
        void maxForcedDialogueDistance(double value);
        com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger containerForcedDialogueTrigger();
        void containerForcedDialogueTrigger(com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger value);
        com.jvn.villagerretaliation.config.ContainerWatchMode containerWatchMode();
        void containerWatchMode(com.jvn.villagerretaliation.config.ContainerWatchMode value);
        int dialoguePositiveReputationCooldownDays();
        void dialoguePositiveReputationCooldownDays(int value);
        int repeatedQuestionPositiveLimit();
        void repeatedQuestionPositiveLimit(int value);
        int trustedRepeatedDialogueLimitBonus();
        void trustedRepeatedDialogueLimitBonus(int value);
        int respectedRepeatedDialogueLimitBonus();
        void respectedRepeatedDialogueLimitBonus(int value);
        int reveredRepeatedDialogueLimitBonus();
        void reveredRepeatedDialogueLimitBonus(int value);
        int royaltyRepeatedDialogueLimitBonus();
        void royaltyRepeatedDialogueLimitBonus(int value);
        int repeatedQuestionReputationLoss();
        void repeatedQuestionReputationLoss(int value);
        int repeatedDialogueOptionResetTicks();
        void repeatedDialogueOptionResetTicks(int value);
        int giftAnnoyanceReductionDivisor();
        void giftAnnoyanceReductionDivisor(int value);
        double maxFollowDistance();
        void maxFollowDistance(double value);
        int greetingReputationGain();
        void greetingReputationGain(int value);
        int questionReputationGain();
        void questionReputationGain(int value);
        int storyReputationGain();
        void storyReputationGain(int value);
        int jokeReputationGain();
        void jokeReputationGain(int value);
        int jokeReputationLoss();
        void jokeReputationLoss(int value);
        int insultReputationLoss();
        void insultReputationLoss(int value);
        int firstGreetingReputationGain();
        void firstGreetingReputationGain(int value);
        int firstInsultReputationLoss();
        void firstInsultReputationLoss(int value);
        boolean disableDialogueTextEffects();
        void disableDialogueTextEffects(boolean value);
    }
    public interface Notifications {
        boolean enableWorldTextNotifications();
        void enableWorldTextNotifications(boolean value);
        boolean enableAmbientMurmurs();
        void enableAmbientMurmurs(boolean value);
        boolean enableSleepIndicators();
        void enableSleepIndicators(boolean value);
        boolean enableDamageAlerts();
        void enableDamageAlerts(boolean value);
        boolean enableCombatAlerts();
        void enableCombatAlerts(boolean value);
        boolean enableTradeAndGiftWorldText();
        void enableTradeAndGiftWorldText(boolean value);
    }
    public interface Gifts {
        boolean enableVillagerGifts();
        void enableVillagerGifts(boolean value);
        boolean enableHighReputationGifts();
        void enableHighReputationGifts(boolean value);
        boolean enableGiftKeepsakes();
        void enableGiftKeepsakes(boolean value);
        boolean showGiftReactionTooltip();
        void showGiftReactionTooltip(boolean value);
        boolean giftReactionTooltipRequiresKnownGift();
        void giftReactionTooltipRequiresKnownGift(boolean value);
        double repeatedGiftReputationMultiplier();
        void repeatedGiftReputationMultiplier(double value);
        int dailyGiftReputationCap();
        void dailyGiftReputationCap(int value);
        int giftRequestCooldownTicks();
        void giftRequestCooldownTicks(int value);
    }
    public interface Social {
        boolean enableVillagerSocialGraph();
        void enableVillagerSocialGraph(boolean value);
        boolean enableVillagerMoods();
        void enableVillagerMoods(boolean value);
        boolean enableSocialAttributeBehavior();
        void enableSocialAttributeBehavior(boolean value);
        boolean enableSocialAttributeMoodEffects();
        void enableSocialAttributeMoodEffects(boolean value);
        boolean enableSocialAttributeDialogueEffects();
        void enableSocialAttributeDialogueEffects(boolean value);
        boolean enableSocialAttributeReputationEffects();
        void enableSocialAttributeReputationEffects(boolean value);
        boolean enableSocialAttributeRetaliationEffects();
        void enableSocialAttributeRetaliationEffects(boolean value);
        boolean enableSocialAttributeGossipEffects();
        void enableSocialAttributeGossipEffects(boolean value);
        double socialAttributeEffectScale();
        void socialAttributeEffectScale(double value);
        boolean enableFamilyBreedingRules();
        void enableFamilyBreedingRules(boolean value);
        boolean enableOppositeGenderBreedingRules();
        void enableOppositeGenderBreedingRules(boolean value);
        boolean enableParentReputationInheritance();
        void enableParentReputationInheritance(boolean value);
    }
    public interface Balance {
        boolean babyVillagersDropLoot();
        void babyVillagersDropLoot(boolean value);
        boolean requirePlayerKillForProfessionLoot();
        void requirePlayerKillForProfessionLoot(boolean value);
        boolean hungerEffectAffectsVillagers();
        void hungerEffectAffectsVillagers(boolean value);
        double villagerEmeraldDropChance();
        void villagerEmeraldDropChance(double value);
        double villagerBreadDropChance();
        void villagerBreadDropChance(double value);
        double professionDropChance();
        void professionDropChance(double value);
        double rareDropChance();
        void rareDropChance(double value);
        double veryRareDropChance();
        void veryRareDropChance(double value);
        int hiredContractBaseDailyCost();
        void hiredContractBaseDailyCost(int value);
        int hiredContractMinimumDailyCost();
        void hiredContractMinimumDailyCost(int value);
        int hiredContractMaximumDailyCost();
        void hiredContractMaximumDailyCost(int value);
        int hiredContractSkillPremiumPerTen();
        void hiredContractSkillPremiumPerTen(int value);
        int hiredContractRoyaltyCostModifier();
        void hiredContractRoyaltyCostModifier(int value);
        int hiredContractReveredCostModifier();
        void hiredContractReveredCostModifier(int value);
        int hiredContractRespectedCostModifier();
        void hiredContractRespectedCostModifier(int value);
        int hiredContractTrustedCostModifier();
        void hiredContractTrustedCostModifier(int value);
        int hiredContractNeutralCostModifier();
        void hiredContractNeutralCostModifier(int value);
        int hiredContractSuspiciousCostModifier();
        void hiredContractSuspiciousCostModifier(int value);
        int hiredContractHostileCostModifier();
        void hiredContractHostileCostModifier(int value);
        int hiredContractDespisedCostModifier();
        void hiredContractDespisedCostModifier(int value);
        int hiredContractFearedCostModifier();
        void hiredContractFearedCostModifier(int value);
        int hiredContractEarlyEndRefundPercent();
        void hiredContractEarlyEndRefundPercent(int value);
        int hiredWorkTickInterval();
        void hiredWorkTickInterval(int value);
        int hiredWorkNoticeCooldownSeconds();
        void hiredWorkNoticeCooldownSeconds(int value);
        int hiredWorkDefaultRadius();
        void hiredWorkDefaultRadius(int value);
        int hiredWorkMaxRadius();
        void hiredWorkMaxRadius(int value);
        int hiredWorkBaseEfficiencyPercent();
        void hiredWorkBaseEfficiencyPercent(int value);
        int hiredWorkMinimumEfficiencyPercent();
        void hiredWorkMinimumEfficiencyPercent(int value);
        int hiredWorkMaximumEfficiencyPercent();
        void hiredWorkMaximumEfficiencyPercent(int value);
        boolean enableHiredWorkSkillGrowth();
        void enableHiredWorkSkillGrowth(boolean value);
        int hiredBuilderMaxBlocks();
        void hiredBuilderMaxBlocks(int value);
        int hiredBuilderMaxSiteDistance();
        void hiredBuilderMaxSiteDistance(int value);
        int hiredBuilderMaterialStorageRadius();
        void hiredBuilderMaterialStorageRadius(int value);
        int hiredBuilderBaseEmeraldCost();
        void hiredBuilderBaseEmeraldCost(int value);
        int hiredBuilderEmeraldsPer64Blocks();
        void hiredBuilderEmeraldsPer64Blocks(int value);
        boolean hiredBuilderCanReplaceSoftBlocks();
        void hiredBuilderCanReplaceSoftBlocks(boolean value);
    }
    public interface HiredWorkSkillGrowth {
        double combat();
        void combat(double value);
        double mining();
        void mining(double value);
        double logging();
        void logging(double value);
        double craftsman();
        void craftsman(double value);
        double farming();
        void farming(double value);
        double brewing();
        void brewing(double value);
        double cooking();
        void cooking(double value);
        double builder();
        void builder(double value);
        double navigation();
        void navigation(double value);
        double animalHandling();
        void animalHandling(double value);
        double nitwit();
        void nitwit(double value);
    }
    public interface Retaliation {
        boolean attackAggrosOnlyHitVillager();
        void attackAggrosOnlyHitVillager(boolean value);
        boolean killingVillagerAggrosNearbyVillagers();
        void killingVillagerAggrosNearbyVillagers(boolean value);
        boolean babyVillagersFleeWitnessedDeaths();
        void babyVillagersFleeWitnessedDeaths(boolean value);
        double villagerKillAggroRadius();
        void villagerKillAggroRadius(double value);
        boolean retaliationWitnessesRequireLineOfSight();
        void retaliationWitnessesRequireLineOfSight(boolean value);
        int aggroDurationTicks();
        void aggroDurationTicks(int value);
        boolean nearbyVillagersIgnoreCreativePlayers();
        void nearbyVillagersIgnoreCreativePlayers(boolean value);
    }
    public interface Reputation {
        int directHitPenalty();
        void directHitPenalty(int value);
        int witnessedHitPenalty();
        void witnessedHitPenalty(int value);
        int witnessedKillPenalty();
        void witnessedKillPenalty(int value);
        int witnessedBabyKillPenalty();
        void witnessedBabyKillPenalty(int value);
        int witnessedIronGolemKillPenalty();
        void witnessedIronGolemKillPenalty(int value);
        int containerBreakReputationLoss();
        void containerBreakReputationLoss(int value);
        int generatedContainerBreakItemReputationLoss();
        void generatedContainerBreakItemReputationLoss(int value);
        int tradeReputationGain();
        void tradeReputationGain(int value);
        int maxTradeReputationGainPerVillagerPerDay();
        void maxTradeReputationGainPerVillagerPerDay(int value);
        int sleepingVillagerBotherReputationLoss();
        void sleepingVillagerBotherReputationLoss(int value);
        int sleepingVillagerBedBreakReputationLoss();
        void sleepingVillagerBedBreakReputationLoss(int value);
        int healVillagerGain();
        void healVillagerGain(int value);
        int saveVillagerGain();
        void saveVillagerGain(int value);
        int positiveWitnessGain();
        void positiveWitnessGain(int value);
        double hostileMobAssistReputationMultiplier();
        void hostileMobAssistReputationMultiplier(double value);
        double gossipReputationMultiplier();
        void gossipReputationMultiplier(double value);
        int royaltyThreshold();
        void royaltyThreshold(int value);
        int reveredThreshold();
        void reveredThreshold(int value);
        int respectedThreshold();
        void respectedThreshold(int value);
        int trustedThreshold();
        void trustedThreshold(int value);
        int suspiciousThreshold();
        void suspiciousThreshold(int value);
        int hostileThreshold();
        void hostileThreshold(int value);
        int despisedThreshold();
        void despisedThreshold(int value);
        int fearedThreshold();
        void fearedThreshold(int value);
        double witnessRadius();
        void witnessRadius(double value);
        double gossipRadius();
        void gossipRadius(double value);
        double despisedSightRadius();
        void despisedSightRadius(double value);
        boolean reputationDecayEnabled();
        void reputationDecayEnabled(boolean value);
        int reputationDecayInterval();
        void reputationDecayInterval(int value);
        int reputationDecayAmount();
        void reputationDecayAmount(int value);
        int pruneNeutralEntriesAfterDays();
        void pruneNeutralEntriesAfterDays(int value);
        boolean witnessReputationRequiresLineOfSight();
        void witnessReputationRequiresLineOfSight(boolean value);
        boolean enableReputationTradePricing();
        void enableReputationTradePricing(boolean value);
        double reputationTradePriceScale();
        void reputationTradePriceScale(double value);
    }
    public interface PlayerRaids {
        boolean enabled();
        void enabled(boolean value);
        boolean confirmRaidHorn();
        void confirmRaidHorn(boolean value);
        int preparationTicks();
        void preparationTicks(int value);
        int abandonmentTicks();
        void abandonmentTicks(int value);
        int villageCooldownDays();
        void villageCooldownDays(int value);
        int bossBarRange();
        void bossBarRange(int value);
        int defendersPerGolem();
        void defendersPerGolem(int value);
        int minimumGolems();
        void minimumGolems(int value);
        int maximumGolems();
        void maximumGolems(int value);
        int raidersPerBonusGolem();
        void raidersPerBonusGolem(int value);
    }
    public interface Trade {
        boolean enableSkillTradeOverhaul();
        void enableSkillTradeOverhaul(boolean value);
        boolean disableVillagerWalletLimit();
        void disableVillagerWalletLimit(boolean value);
        boolean enableSpecialOrders();
        void enableSpecialOrders(boolean value);
        com.jvn.villagerretaliation.reputation.VillagerReputationLevel specialOrderMinReputation();
        void specialOrderMinReputation(com.jvn.villagerretaliation.reputation.VillagerReputationLevel value);
        int specialOrderWaitDays();
        void specialOrderWaitDays(int value);
        int specialOrderCooldownDays();
        void specialOrderCooldownDays(int value);
        boolean specialOrderExtraCostEnabled();
        void specialOrderExtraCostEnabled(boolean value);
        int specialOrderMaxActivePerPlayer();
        void specialOrderMaxActivePerPlayer(int value);
        boolean skillTradeQualityScaling();
        void skillTradeQualityScaling(boolean value);
        boolean skillTradeLowSkillPenalties();
        void skillTradeLowSkillPenalties(boolean value);
        int skillTradeMaxEnchantmentLevel();
        void skillTradeMaxEnchantmentLevel(int value);
        double skillTradeRareChanceMultiplier();
        void skillTradeRareChanceMultiplier(double value);
        boolean skillTradeAllowHighTierEquipment();
        void skillTradeAllowHighTierEquipment(boolean value);
        boolean skillTradeAllowSpecialArrows();
        void skillTradeAllowSpecialArrows(boolean value);
        boolean skillTradeAllowRareSpecialtyTrades();
        void skillTradeAllowRareSpecialtyTrades(boolean value);
        boolean enableSkillGrowthFromTradingLevels();
        void enableSkillGrowthFromTradingLevels(boolean value);
        boolean enableRegularTradeSkillGrowth();
        void enableRegularTradeSkillGrowth(boolean value);
        double regularTradeSkillGrowthAmount();
        void regularTradeSkillGrowthAmount(double value);
        boolean enableSkillBasedTradeLeveling();
        void enableSkillBasedTradeLeveling(boolean value);
        double skillBasedTradeLevelingMinMultiplier();
        void skillBasedTradeLevelingMinMultiplier(double value);
        double skillBasedTradeLevelingMaxMultiplier();
        void skillBasedTradeLevelingMaxMultiplier(double value);
        boolean enableSkillGrowthFeedback();
        void enableSkillGrowthFeedback(boolean value);
        int skillGrowthPrimaryMin();
        void skillGrowthPrimaryMin(int value);
        int skillGrowthPrimaryMax();
        void skillGrowthPrimaryMax(int value);
    }
    public interface DebugOverlay {
        boolean showVillageBounds();
        void showVillageBounds(boolean value);
        boolean highlightRaidDefenders();
        void highlightRaidDefenders(boolean value);
        int debugPreviewMaxVisibleNodes();
        void debugPreviewMaxVisibleNodes(int value);
        int debugPreviewMaxVisibleLabels();
        void debugPreviewMaxVisibleLabels(int value);
        int debugPreviewMaxVisibleSegments();
        void debugPreviewMaxVisibleSegments(int value);
        boolean showVillagerReputationDebugOverlay();
        void showVillagerReputationDebugOverlay(boolean value);
        double reputationDebugOverlayMaxDistance();
        void reputationDebugOverlayMaxDistance(double value);
        boolean reputationDebugOverlayShowTier();
        void reputationDebugOverlayShowTier(boolean value);
        boolean reputationDebugOverlayShowNumber();
        void reputationDebugOverlayShowNumber(boolean value);
        boolean reputationDebugOverlayShowHealth();
        void reputationDebugOverlayShowHealth(boolean value);
        boolean reputationDebugOverlayShowArmor();
        void reputationDebugOverlayShowArmor(boolean value);
        boolean reputationDebugOverlayShowHunger();
        void reputationDebugOverlayShowHunger(boolean value);
        boolean reputationDebugOverlayRequireAdvancedTooltips();
        void reputationDebugOverlayRequireAdvancedTooltips(boolean value);
        boolean reputationDebugOverlayOnlyWhenSneaking();
        void reputationDebugOverlayOnlyWhenSneaking(boolean value);
    }
    public interface Combat {
        boolean enableVillagerSleepHealing();
        void enableVillagerSleepHealing(boolean value);
        double villagerSleepHealingMaxHealthPercent();
        void villagerSleepHealingMaxHealthPercent(double value);
        boolean enableVillagerDownedState();
        void enableVillagerDownedState(boolean value);
        boolean allVillagersUseDownedState();
        void allVillagersUseDownedState(boolean value);
        boolean raidVillagersUseDownedState();
        void raidVillagersUseDownedState(boolean value);
        boolean hiredVillagersUseDownedState();
        void hiredVillagersUseDownedState(boolean value);
        boolean partyVillagersUseDownedState();
        void partyVillagersUseDownedState(boolean value);
        boolean playerDamageDownsEligibleVillagers();
        void playerDamageDownsEligibleVillagers(boolean value);
        boolean mobDamageDownsEligibleVillagers();
        void mobDamageDownsEligibleVillagers(boolean value);
        boolean environmentalDamageDownsEligibleVillagers();
        void environmentalDamageDownsEligibleVillagers(boolean value);
        int downedMinimumTicks();
        void downedMinimumTicks(int value);
        double downedRecoveryHealthPercent();
        void downedRecoveryHealthPercent(double value);
        double downedThreatRadius();
        void downedThreatRadius(double value);
        int downedQuietTicks();
        void downedQuietTicks(int value);
        boolean weaponsmithsFightBack();
        void weaponsmithsFightBack(boolean value);
        boolean toolsmithsFightBack();
        void toolsmithsFightBack(boolean value);
        boolean armorersFightBack();
        void armorersFightBack(boolean value);
        boolean fletchersFightBack();
        void fletchersFightBack(boolean value);
        boolean butchersFightBack();
        void butchersFightBack(boolean value);
        boolean villagersTargetHostileMobs();
        void villagersTargetHostileMobs(boolean value);
        boolean wanderingTradersTargetHostileMobs();
        void wanderingTradersTargetHostileMobs(boolean value);
        boolean villagersRetaliateAgainstHostileMobs();
        void villagersRetaliateAgainstHostileMobs(boolean value);
        boolean wanderingTradersRetaliateAgainstHostileMobs();
        void wanderingTradersRetaliateAgainstHostileMobs(boolean value);
        boolean villagersStandGroundAgainstHostileMobs();
        void villagersStandGroundAgainstHostileMobs(boolean value);
        boolean villagersFleeVisibleCreepers();
        void villagersFleeVisibleCreepers(boolean value);
        boolean villagersPickUpGroundWeapons();
        void villagersPickUpGroundWeapons(boolean value);
        boolean wanderingTradersPickUpGroundWeapons();
        void wanderingTradersPickUpGroundWeapons(boolean value);
        double naturalHostileTargetRadius();
        void naturalHostileTargetRadius(double value);
        double combatWeaponDropChance();
        void combatWeaponDropChance(double value);
        double combatWeaponEnchantChance();
        void combatWeaponEnchantChance(double value);
        double armorerShieldChanceHard();
        void armorerShieldChanceHard(double value);
        boolean clericsUsePotions();
        void clericsUsePotions(boolean value);
        double passiveClericAllyHealRange();
        void passiveClericAllyHealRange(double value);
        double passiveClericAllyHealHealthThreshold();
        void passiveClericAllyHealHealthThreshold(double value);
        boolean passiveClericAllyHealRequiresLineOfSight();
        void passiveClericAllyHealRequiresLineOfSight(boolean value);
        boolean hostileTierHarassThrowEnabled();
        void hostileTierHarassThrowEnabled(boolean value);
        int hostileTierHarassThrowMinIntervalTicks();
        void hostileTierHarassThrowMinIntervalTicks(int value);
        int hostileTierHarassThrowMaxIntervalTicks();
        void hostileTierHarassThrowMaxIntervalTicks(int value);
    }
    public interface Duels {
        boolean enabled();
        void enabled(boolean value);
        boolean allowBringYourOwnLoadout();
        void allowBringYourOwnLoadout(boolean value);
        int minimumGuts();
        void minimumGuts(int value);
        int cooldownDays();
        void cooldownDays(int value);
        int refusalLosses();
        void refusalLosses(int value);
        int arenaRadius();
        void arenaRadius(int value);
        boolean showArenaParticles();
        void showArenaParticles(boolean value);
        int boundaryGraceTicks();
        void boundaryGraceTicks(int value);
        int timeoutTicks();
        void timeoutTicks(int value);
        int spectatorRadius();
        void spectatorRadius(int value);
        int spectatorCap();
        void spectatorCap(int value);
        int watcherReputation();
        void watcherReputation(int value);
    }
    public interface Wanderer {
        boolean dropEmeralds();
        void dropEmeralds(boolean value);
        boolean dropInvisibilityPotion();
        void dropInvisibilityPotion(boolean value);
        boolean dropRandomCurrentTrade();
        void dropRandomCurrentTrade(boolean value);
        double randomTradeDropChance();
        void randomTradeDropChance(double value);
    }
    public interface Quest {
        boolean showQuestIndicators();
        void showQuestIndicators(boolean value);
        boolean enableQuestItemShaderHighlights();
        void enableQuestItemShaderHighlights(boolean value);
        com.jvn.villagerretaliation.config.QuestItemHighlightMode questItemHighlightMode();
        void questItemHighlightMode(com.jvn.villagerretaliation.config.QuestItemHighlightMode value);
    }
    public static class Keys {
        public final Option.Key general_enableVillagerDrops = new Option.Key("general.enableVillagerDrops");
        public final Option.Key general_enableWanderingTraderDrops = new Option.Key("general.enableWanderingTraderDrops");
        public final Option.Key general_enableVillagerRetaliation = new Option.Key("general.enableVillagerRetaliation");
        public final Option.Key general_enableVillagerReputation = new Option.Key("general.enableVillagerReputation");
        public final Option.Key general_enableVanillaGossipIntegration = new Option.Key("general.enableVanillaGossipIntegration");
        public final Option.Key general_enableDespisedKillOnSight = new Option.Key("general.enableDespisedKillOnSight");
        public final Option.Key general_despisedKillOnSightInterruptsHiredWork = new Option.Key("general.despisedKillOnSightInterruptsHiredWork");
        public final Option.Key general_reputationChangeDisplayMode = new Option.Key("general.reputationChangeDisplayMode");
        public final Option.Key general_reputationChangeNotificationStyle = new Option.Key("general.reputationChangeNotificationStyle");
        public final Option.Key general_reputationChangeHudPosition = new Option.Key("general.reputationChangeHudPosition");
        public final Option.Key general_collapseReputationChangeNotifications = new Option.Key("general.collapseReputationChangeNotifications");
        public final Option.Key general_showVillagerNameTags = new Option.Key("general.showVillagerNameTags");
        public final Option.Key general_villagerStatDisplayMode = new Option.Key("general.villagerStatDisplayMode");
        public final Option.Key general_villagerReputationHoverTooltipRequiresEmerald = new Option.Key("general.villagerReputationHoverTooltipRequiresEmerald");
        public final Option.Key general_showTradeGuiReputationIcon = new Option.Key("general.showTradeGuiReputationIcon");
        public final Option.Key general_enableVillagerDeathMessages = new Option.Key("general.enableVillagerDeathMessages");
        public final Option.Key dialogue_enableInteractionScreen = new Option.Key("dialogue.enableInteractionScreen");
        public final Option.Key dialogue_shiftRightClickBypassesInteractionScreen = new Option.Key("dialogue.shiftRightClickBypassesInteractionScreen");
        public final Option.Key dialogue_enableDialogueReputationEffects = new Option.Key("dialogue.enableDialogueReputationEffects");
        public final Option.Key dialogue_enableDialogueCameraFocus = new Option.Key("dialogue.enableDialogueCameraFocus");
        public final Option.Key dialogue_enableDialogueCinematicBars = new Option.Key("dialogue.enableDialogueCinematicBars");
        public final Option.Key dialogue_dialogueCinematicBarHeight = new Option.Key("dialogue.dialogueCinematicBarHeight");
        public final Option.Key dialogue_dialogueCinematicBarMinSlant = new Option.Key("dialogue.dialogueCinematicBarMinSlant");
        public final Option.Key dialogue_dialogueCinematicBarMaxSlant = new Option.Key("dialogue.dialogueCinematicBarMaxSlant");
        public final Option.Key dialogue_animateDialogueCinematicBars = new Option.Key("dialogue.animateDialogueCinematicBars");
        public final Option.Key dialogue_enableForcedDialogue = new Option.Key("dialogue.enableForcedDialogue");
        public final Option.Key dialogue_enableContainerForcedDialogue = new Option.Key("dialogue.enableContainerForcedDialogue");
        public final Option.Key dialogue_enableContainerOpenReaction = new Option.Key("dialogue.enableContainerOpenReaction");
        public final Option.Key dialogue_enableRetaliationForcedDialogue = new Option.Key("dialogue.enableRetaliationForcedDialogue");
        public final Option.Key dialogue_enablePlayerItemProximityForcedDialogue = new Option.Key("dialogue.enablePlayerItemProximityForcedDialogue");
        public final Option.Key dialogue_separateVillagerChatMessages = new Option.Key("dialogue.separateVillagerChatMessages");
        public final Option.Key dialogue_separateVillagerChatSpeakers = new Option.Key("dialogue.separateVillagerChatSpeakers");
        public final Option.Key dialogue_interactionChatPosition = new Option.Key("dialogue.interactionChatPosition");
        public final Option.Key dialogue_villagerChatBroadcastMode = new Option.Key("dialogue.villagerChatBroadcastMode");
        public final Option.Key dialogue_villagerChatBroadcastRadius = new Option.Key("dialogue.villagerChatBroadcastRadius");
        public final Option.Key dialogue_showPersonalInteractionDialogueToNearbyPlayers = new Option.Key("dialogue.showPersonalInteractionDialogueToNearbyPlayers");
        public final Option.Key dialogue_dialogueTextSpeed = new Option.Key("dialogue.dialogueTextSpeed");
        public final Option.Key dialogue_enableDialogueBlipAudio = new Option.Key("dialogue.enableDialogueBlipAudio");
        public final Option.Key dialogue_dialogueBlipVolume = new Option.Key("dialogue.dialogueBlipVolume");
        public final Option.Key dialogue_dialogueBlipMinPitch = new Option.Key("dialogue.dialogueBlipMinPitch");
        public final Option.Key dialogue_dialogueBlipMaxPitch = new Option.Key("dialogue.dialogueBlipMaxPitch");
        public final Option.Key dialogue_dialogueCameraZoomAmount = new Option.Key("dialogue.dialogueCameraZoomAmount");
        public final Option.Key dialogue_enableNormalDialogueCameraFocus = new Option.Key("dialogue.enableNormalDialogueCameraFocus");
        public final Option.Key dialogue_normalDialogueCameraZoomAmount = new Option.Key("dialogue.normalDialogueCameraZoomAmount");
        public final Option.Key dialogue_dialogueCameraTransitionTicks = new Option.Key("dialogue.dialogueCameraTransitionTicks");
        public final Option.Key dialogue_freezeVillagerDuringDialogue = new Option.Key("dialogue.freezeVillagerDuringDialogue");
        public final Option.Key dialogue_maxDialogueDistance = new Option.Key("dialogue.maxDialogueDistance");
        public final Option.Key dialogue_maxForcedDialogueDistance = new Option.Key("dialogue.maxForcedDialogueDistance");
        public final Option.Key dialogue_containerForcedDialogueTrigger = new Option.Key("dialogue.containerForcedDialogueTrigger");
        public final Option.Key dialogue_containerWatchMode = new Option.Key("dialogue.containerWatchMode");
        public final Option.Key dialogue_dialoguePositiveReputationCooldownDays = new Option.Key("dialogue.dialoguePositiveReputationCooldownDays");
        public final Option.Key dialogue_repeatedQuestionPositiveLimit = new Option.Key("dialogue.repeatedQuestionPositiveLimit");
        public final Option.Key dialogue_trustedRepeatedDialogueLimitBonus = new Option.Key("dialogue.trustedRepeatedDialogueLimitBonus");
        public final Option.Key dialogue_respectedRepeatedDialogueLimitBonus = new Option.Key("dialogue.respectedRepeatedDialogueLimitBonus");
        public final Option.Key dialogue_reveredRepeatedDialogueLimitBonus = new Option.Key("dialogue.reveredRepeatedDialogueLimitBonus");
        public final Option.Key dialogue_royaltyRepeatedDialogueLimitBonus = new Option.Key("dialogue.royaltyRepeatedDialogueLimitBonus");
        public final Option.Key dialogue_repeatedQuestionReputationLoss = new Option.Key("dialogue.repeatedQuestionReputationLoss");
        public final Option.Key dialogue_repeatedDialogueOptionResetTicks = new Option.Key("dialogue.repeatedDialogueOptionResetTicks");
        public final Option.Key dialogue_giftAnnoyanceReductionDivisor = new Option.Key("dialogue.giftAnnoyanceReductionDivisor");
        public final Option.Key dialogue_maxFollowDistance = new Option.Key("dialogue.maxFollowDistance");
        public final Option.Key dialogue_greetingReputationGain = new Option.Key("dialogue.greetingReputationGain");
        public final Option.Key dialogue_questionReputationGain = new Option.Key("dialogue.questionReputationGain");
        public final Option.Key dialogue_storyReputationGain = new Option.Key("dialogue.storyReputationGain");
        public final Option.Key dialogue_jokeReputationGain = new Option.Key("dialogue.jokeReputationGain");
        public final Option.Key dialogue_jokeReputationLoss = new Option.Key("dialogue.jokeReputationLoss");
        public final Option.Key dialogue_insultReputationLoss = new Option.Key("dialogue.insultReputationLoss");
        public final Option.Key dialogue_firstGreetingReputationGain = new Option.Key("dialogue.firstGreetingReputationGain");
        public final Option.Key dialogue_firstInsultReputationLoss = new Option.Key("dialogue.firstInsultReputationLoss");
        public final Option.Key dialogue_disableDialogueTextEffects = new Option.Key("dialogue.disableDialogueTextEffects");
        public final Option.Key notifications_enableWorldTextNotifications = new Option.Key("notifications.enableWorldTextNotifications");
        public final Option.Key notifications_enableAmbientMurmurs = new Option.Key("notifications.enableAmbientMurmurs");
        public final Option.Key notifications_enableSleepIndicators = new Option.Key("notifications.enableSleepIndicators");
        public final Option.Key notifications_enableDamageAlerts = new Option.Key("notifications.enableDamageAlerts");
        public final Option.Key notifications_enableCombatAlerts = new Option.Key("notifications.enableCombatAlerts");
        public final Option.Key notifications_enableTradeAndGiftWorldText = new Option.Key("notifications.enableTradeAndGiftWorldText");
        public final Option.Key gifts_enableVillagerGifts = new Option.Key("gifts.enableVillagerGifts");
        public final Option.Key gifts_enableHighReputationGifts = new Option.Key("gifts.enableHighReputationGifts");
        public final Option.Key gifts_enableGiftKeepsakes = new Option.Key("gifts.enableGiftKeepsakes");
        public final Option.Key gifts_showGiftReactionTooltip = new Option.Key("gifts.showGiftReactionTooltip");
        public final Option.Key gifts_giftReactionTooltipRequiresKnownGift = new Option.Key("gifts.giftReactionTooltipRequiresKnownGift");
        public final Option.Key gifts_repeatedGiftReputationMultiplier = new Option.Key("gifts.repeatedGiftReputationMultiplier");
        public final Option.Key gifts_dailyGiftReputationCap = new Option.Key("gifts.dailyGiftReputationCap");
        public final Option.Key gifts_giftRequestCooldownTicks = new Option.Key("gifts.giftRequestCooldownTicks");
        public final Option.Key social_enableVillagerSocialGraph = new Option.Key("social.enableVillagerSocialGraph");
        public final Option.Key social_enableVillagerMoods = new Option.Key("social.enableVillagerMoods");
        public final Option.Key social_enableSocialAttributeBehavior = new Option.Key("social.enableSocialAttributeBehavior");
        public final Option.Key social_enableSocialAttributeMoodEffects = new Option.Key("social.enableSocialAttributeMoodEffects");
        public final Option.Key social_enableSocialAttributeDialogueEffects = new Option.Key("social.enableSocialAttributeDialogueEffects");
        public final Option.Key social_enableSocialAttributeReputationEffects = new Option.Key("social.enableSocialAttributeReputationEffects");
        public final Option.Key social_enableSocialAttributeRetaliationEffects = new Option.Key("social.enableSocialAttributeRetaliationEffects");
        public final Option.Key social_enableSocialAttributeGossipEffects = new Option.Key("social.enableSocialAttributeGossipEffects");
        public final Option.Key social_socialAttributeEffectScale = new Option.Key("social.socialAttributeEffectScale");
        public final Option.Key social_enableFamilyBreedingRules = new Option.Key("social.enableFamilyBreedingRules");
        public final Option.Key social_enableOppositeGenderBreedingRules = new Option.Key("social.enableOppositeGenderBreedingRules");
        public final Option.Key social_enableParentReputationInheritance = new Option.Key("social.enableParentReputationInheritance");
        public final Option.Key balance_babyVillagersDropLoot = new Option.Key("balance.babyVillagersDropLoot");
        public final Option.Key balance_requirePlayerKillForProfessionLoot = new Option.Key("balance.requirePlayerKillForProfessionLoot");
        public final Option.Key balance_hungerEffectAffectsVillagers = new Option.Key("balance.hungerEffectAffectsVillagers");
        public final Option.Key balance_villagerEmeraldDropChance = new Option.Key("balance.villagerEmeraldDropChance");
        public final Option.Key balance_villagerBreadDropChance = new Option.Key("balance.villagerBreadDropChance");
        public final Option.Key balance_professionDropChance = new Option.Key("balance.professionDropChance");
        public final Option.Key balance_rareDropChance = new Option.Key("balance.rareDropChance");
        public final Option.Key balance_veryRareDropChance = new Option.Key("balance.veryRareDropChance");
        public final Option.Key balance_hiredContractBaseDailyCost = new Option.Key("balance.hiredContractBaseDailyCost");
        public final Option.Key balance_hiredContractMinimumDailyCost = new Option.Key("balance.hiredContractMinimumDailyCost");
        public final Option.Key balance_hiredContractMaximumDailyCost = new Option.Key("balance.hiredContractMaximumDailyCost");
        public final Option.Key balance_hiredContractSkillPremiumPerTen = new Option.Key("balance.hiredContractSkillPremiumPerTen");
        public final Option.Key balance_hiredContractRoyaltyCostModifier = new Option.Key("balance.hiredContractRoyaltyCostModifier");
        public final Option.Key balance_hiredContractReveredCostModifier = new Option.Key("balance.hiredContractReveredCostModifier");
        public final Option.Key balance_hiredContractRespectedCostModifier = new Option.Key("balance.hiredContractRespectedCostModifier");
        public final Option.Key balance_hiredContractTrustedCostModifier = new Option.Key("balance.hiredContractTrustedCostModifier");
        public final Option.Key balance_hiredContractNeutralCostModifier = new Option.Key("balance.hiredContractNeutralCostModifier");
        public final Option.Key balance_hiredContractSuspiciousCostModifier = new Option.Key("balance.hiredContractSuspiciousCostModifier");
        public final Option.Key balance_hiredContractHostileCostModifier = new Option.Key("balance.hiredContractHostileCostModifier");
        public final Option.Key balance_hiredContractDespisedCostModifier = new Option.Key("balance.hiredContractDespisedCostModifier");
        public final Option.Key balance_hiredContractFearedCostModifier = new Option.Key("balance.hiredContractFearedCostModifier");
        public final Option.Key balance_hiredContractEarlyEndRefundPercent = new Option.Key("balance.hiredContractEarlyEndRefundPercent");
        public final Option.Key balance_hiredWorkTickInterval = new Option.Key("balance.hiredWorkTickInterval");
        public final Option.Key balance_hiredWorkNoticeCooldownSeconds = new Option.Key("balance.hiredWorkNoticeCooldownSeconds");
        public final Option.Key balance_hiredWorkDefaultRadius = new Option.Key("balance.hiredWorkDefaultRadius");
        public final Option.Key balance_hiredWorkMaxRadius = new Option.Key("balance.hiredWorkMaxRadius");
        public final Option.Key balance_hiredWorkBaseEfficiencyPercent = new Option.Key("balance.hiredWorkBaseEfficiencyPercent");
        public final Option.Key balance_hiredWorkMinimumEfficiencyPercent = new Option.Key("balance.hiredWorkMinimumEfficiencyPercent");
        public final Option.Key balance_hiredWorkMaximumEfficiencyPercent = new Option.Key("balance.hiredWorkMaximumEfficiencyPercent");
        public final Option.Key balance_enableHiredWorkSkillGrowth = new Option.Key("balance.enableHiredWorkSkillGrowth");
        public final Option.Key balance_hiredWorkSkillGrowth_combat = new Option.Key("balance.hiredWorkSkillGrowth.combat");
        public final Option.Key balance_hiredWorkSkillGrowth_mining = new Option.Key("balance.hiredWorkSkillGrowth.mining");
        public final Option.Key balance_hiredWorkSkillGrowth_logging = new Option.Key("balance.hiredWorkSkillGrowth.logging");
        public final Option.Key balance_hiredWorkSkillGrowth_craftsman = new Option.Key("balance.hiredWorkSkillGrowth.craftsman");
        public final Option.Key balance_hiredWorkSkillGrowth_farming = new Option.Key("balance.hiredWorkSkillGrowth.farming");
        public final Option.Key balance_hiredWorkSkillGrowth_brewing = new Option.Key("balance.hiredWorkSkillGrowth.brewing");
        public final Option.Key balance_hiredWorkSkillGrowth_cooking = new Option.Key("balance.hiredWorkSkillGrowth.cooking");
        public final Option.Key balance_hiredWorkSkillGrowth_builder = new Option.Key("balance.hiredWorkSkillGrowth.builder");
        public final Option.Key balance_hiredWorkSkillGrowth_navigation = new Option.Key("balance.hiredWorkSkillGrowth.navigation");
        public final Option.Key balance_hiredWorkSkillGrowth_animalHandling = new Option.Key("balance.hiredWorkSkillGrowth.animalHandling");
        public final Option.Key balance_hiredWorkSkillGrowth_nitwit = new Option.Key("balance.hiredWorkSkillGrowth.nitwit");
        public final Option.Key balance_hiredBuilderMaxBlocks = new Option.Key("balance.hiredBuilderMaxBlocks");
        public final Option.Key balance_hiredBuilderMaxSiteDistance = new Option.Key("balance.hiredBuilderMaxSiteDistance");
        public final Option.Key balance_hiredBuilderMaterialStorageRadius = new Option.Key("balance.hiredBuilderMaterialStorageRadius");
        public final Option.Key balance_hiredBuilderBaseEmeraldCost = new Option.Key("balance.hiredBuilderBaseEmeraldCost");
        public final Option.Key balance_hiredBuilderEmeraldsPer64Blocks = new Option.Key("balance.hiredBuilderEmeraldsPer64Blocks");
        public final Option.Key balance_hiredBuilderCanReplaceSoftBlocks = new Option.Key("balance.hiredBuilderCanReplaceSoftBlocks");
        public final Option.Key retaliation_attackAggrosOnlyHitVillager = new Option.Key("retaliation.attackAggrosOnlyHitVillager");
        public final Option.Key retaliation_killingVillagerAggrosNearbyVillagers = new Option.Key("retaliation.killingVillagerAggrosNearbyVillagers");
        public final Option.Key retaliation_babyVillagersFleeWitnessedDeaths = new Option.Key("retaliation.babyVillagersFleeWitnessedDeaths");
        public final Option.Key retaliation_villagerKillAggroRadius = new Option.Key("retaliation.villagerKillAggroRadius");
        public final Option.Key retaliation_retaliationWitnessesRequireLineOfSight = new Option.Key("retaliation.retaliationWitnessesRequireLineOfSight");
        public final Option.Key retaliation_aggroDurationTicks = new Option.Key("retaliation.aggroDurationTicks");
        public final Option.Key retaliation_nearbyVillagersIgnoreCreativePlayers = new Option.Key("retaliation.nearbyVillagersIgnoreCreativePlayers");
        public final Option.Key reputation_directHitPenalty = new Option.Key("reputation.directHitPenalty");
        public final Option.Key reputation_witnessedHitPenalty = new Option.Key("reputation.witnessedHitPenalty");
        public final Option.Key reputation_witnessedKillPenalty = new Option.Key("reputation.witnessedKillPenalty");
        public final Option.Key reputation_witnessedBabyKillPenalty = new Option.Key("reputation.witnessedBabyKillPenalty");
        public final Option.Key reputation_witnessedIronGolemKillPenalty = new Option.Key("reputation.witnessedIronGolemKillPenalty");
        public final Option.Key reputation_containerBreakReputationLoss = new Option.Key("reputation.containerBreakReputationLoss");
        public final Option.Key reputation_generatedContainerBreakItemReputationLoss = new Option.Key("reputation.generatedContainerBreakItemReputationLoss");
        public final Option.Key reputation_tradeReputationGain = new Option.Key("reputation.tradeReputationGain");
        public final Option.Key reputation_maxTradeReputationGainPerVillagerPerDay = new Option.Key("reputation.maxTradeReputationGainPerVillagerPerDay");
        public final Option.Key reputation_sleepingVillagerBotherReputationLoss = new Option.Key("reputation.sleepingVillagerBotherReputationLoss");
        public final Option.Key reputation_sleepingVillagerBedBreakReputationLoss = new Option.Key("reputation.sleepingVillagerBedBreakReputationLoss");
        public final Option.Key reputation_healVillagerGain = new Option.Key("reputation.healVillagerGain");
        public final Option.Key reputation_saveVillagerGain = new Option.Key("reputation.saveVillagerGain");
        public final Option.Key reputation_positiveWitnessGain = new Option.Key("reputation.positiveWitnessGain");
        public final Option.Key reputation_hostileMobAssistReputationMultiplier = new Option.Key("reputation.hostileMobAssistReputationMultiplier");
        public final Option.Key reputation_gossipReputationMultiplier = new Option.Key("reputation.gossipReputationMultiplier");
        public final Option.Key reputation_royaltyThreshold = new Option.Key("reputation.royaltyThreshold");
        public final Option.Key reputation_reveredThreshold = new Option.Key("reputation.reveredThreshold");
        public final Option.Key reputation_respectedThreshold = new Option.Key("reputation.respectedThreshold");
        public final Option.Key reputation_trustedThreshold = new Option.Key("reputation.trustedThreshold");
        public final Option.Key reputation_suspiciousThreshold = new Option.Key("reputation.suspiciousThreshold");
        public final Option.Key reputation_hostileThreshold = new Option.Key("reputation.hostileThreshold");
        public final Option.Key reputation_despisedThreshold = new Option.Key("reputation.despisedThreshold");
        public final Option.Key reputation_fearedThreshold = new Option.Key("reputation.fearedThreshold");
        public final Option.Key reputation_witnessRadius = new Option.Key("reputation.witnessRadius");
        public final Option.Key reputation_gossipRadius = new Option.Key("reputation.gossipRadius");
        public final Option.Key reputation_despisedSightRadius = new Option.Key("reputation.despisedSightRadius");
        public final Option.Key reputation_reputationDecayEnabled = new Option.Key("reputation.reputationDecayEnabled");
        public final Option.Key reputation_reputationDecayInterval = new Option.Key("reputation.reputationDecayInterval");
        public final Option.Key reputation_reputationDecayAmount = new Option.Key("reputation.reputationDecayAmount");
        public final Option.Key reputation_pruneNeutralEntriesAfterDays = new Option.Key("reputation.pruneNeutralEntriesAfterDays");
        public final Option.Key reputation_witnessReputationRequiresLineOfSight = new Option.Key("reputation.witnessReputationRequiresLineOfSight");
        public final Option.Key reputation_enableReputationTradePricing = new Option.Key("reputation.enableReputationTradePricing");
        public final Option.Key reputation_reputationTradePriceScale = new Option.Key("reputation.reputationTradePriceScale");
        public final Option.Key playerRaids_enabled = new Option.Key("playerRaids.enabled");
        public final Option.Key playerRaids_confirmRaidHorn = new Option.Key("playerRaids.confirmRaidHorn");
        public final Option.Key playerRaids_preparationTicks = new Option.Key("playerRaids.preparationTicks");
        public final Option.Key playerRaids_abandonmentTicks = new Option.Key("playerRaids.abandonmentTicks");
        public final Option.Key playerRaids_villageCooldownDays = new Option.Key("playerRaids.villageCooldownDays");
        public final Option.Key playerRaids_bossBarRange = new Option.Key("playerRaids.bossBarRange");
        public final Option.Key playerRaids_defendersPerGolem = new Option.Key("playerRaids.defendersPerGolem");
        public final Option.Key playerRaids_minimumGolems = new Option.Key("playerRaids.minimumGolems");
        public final Option.Key playerRaids_maximumGolems = new Option.Key("playerRaids.maximumGolems");
        public final Option.Key playerRaids_raidersPerBonusGolem = new Option.Key("playerRaids.raidersPerBonusGolem");
        public final Option.Key trade_enableSkillTradeOverhaul = new Option.Key("trade.enableSkillTradeOverhaul");
        public final Option.Key trade_disableVillagerWalletLimit = new Option.Key("trade.disableVillagerWalletLimit");
        public final Option.Key trade_enableSpecialOrders = new Option.Key("trade.enableSpecialOrders");
        public final Option.Key trade_specialOrderMinReputation = new Option.Key("trade.specialOrderMinReputation");
        public final Option.Key trade_specialOrderWaitDays = new Option.Key("trade.specialOrderWaitDays");
        public final Option.Key trade_specialOrderCooldownDays = new Option.Key("trade.specialOrderCooldownDays");
        public final Option.Key trade_specialOrderExtraCostEnabled = new Option.Key("trade.specialOrderExtraCostEnabled");
        public final Option.Key trade_specialOrderMaxActivePerPlayer = new Option.Key("trade.specialOrderMaxActivePerPlayer");
        public final Option.Key trade_skillTradeQualityScaling = new Option.Key("trade.skillTradeQualityScaling");
        public final Option.Key trade_skillTradeLowSkillPenalties = new Option.Key("trade.skillTradeLowSkillPenalties");
        public final Option.Key trade_skillTradeMaxEnchantmentLevel = new Option.Key("trade.skillTradeMaxEnchantmentLevel");
        public final Option.Key trade_skillTradeRareChanceMultiplier = new Option.Key("trade.skillTradeRareChanceMultiplier");
        public final Option.Key trade_skillTradeAllowHighTierEquipment = new Option.Key("trade.skillTradeAllowHighTierEquipment");
        public final Option.Key trade_skillTradeAllowSpecialArrows = new Option.Key("trade.skillTradeAllowSpecialArrows");
        public final Option.Key trade_skillTradeAllowRareSpecialtyTrades = new Option.Key("trade.skillTradeAllowRareSpecialtyTrades");
        public final Option.Key trade_enableSkillGrowthFromTradingLevels = new Option.Key("trade.enableSkillGrowthFromTradingLevels");
        public final Option.Key trade_enableRegularTradeSkillGrowth = new Option.Key("trade.enableRegularTradeSkillGrowth");
        public final Option.Key trade_regularTradeSkillGrowthAmount = new Option.Key("trade.regularTradeSkillGrowthAmount");
        public final Option.Key trade_enableSkillBasedTradeLeveling = new Option.Key("trade.enableSkillBasedTradeLeveling");
        public final Option.Key trade_skillBasedTradeLevelingMinMultiplier = new Option.Key("trade.skillBasedTradeLevelingMinMultiplier");
        public final Option.Key trade_skillBasedTradeLevelingMaxMultiplier = new Option.Key("trade.skillBasedTradeLevelingMaxMultiplier");
        public final Option.Key trade_enableSkillGrowthFeedback = new Option.Key("trade.enableSkillGrowthFeedback");
        public final Option.Key trade_skillGrowthPrimaryMin = new Option.Key("trade.skillGrowthPrimaryMin");
        public final Option.Key trade_skillGrowthPrimaryMax = new Option.Key("trade.skillGrowthPrimaryMax");
        public final Option.Key debugOverlay_showVillageBounds = new Option.Key("debugOverlay.showVillageBounds");
        public final Option.Key debugOverlay_highlightRaidDefenders = new Option.Key("debugOverlay.highlightRaidDefenders");
        public final Option.Key debugOverlay_debugPreviewMaxVisibleNodes = new Option.Key("debugOverlay.debugPreviewMaxVisibleNodes");
        public final Option.Key debugOverlay_debugPreviewMaxVisibleLabels = new Option.Key("debugOverlay.debugPreviewMaxVisibleLabels");
        public final Option.Key debugOverlay_debugPreviewMaxVisibleSegments = new Option.Key("debugOverlay.debugPreviewMaxVisibleSegments");
        public final Option.Key debugOverlay_showVillagerReputationDebugOverlay = new Option.Key("debugOverlay.showVillagerReputationDebugOverlay");
        public final Option.Key debugOverlay_reputationDebugOverlayMaxDistance = new Option.Key("debugOverlay.reputationDebugOverlayMaxDistance");
        public final Option.Key debugOverlay_reputationDebugOverlayShowTier = new Option.Key("debugOverlay.reputationDebugOverlayShowTier");
        public final Option.Key debugOverlay_reputationDebugOverlayShowNumber = new Option.Key("debugOverlay.reputationDebugOverlayShowNumber");
        public final Option.Key debugOverlay_reputationDebugOverlayShowHealth = new Option.Key("debugOverlay.reputationDebugOverlayShowHealth");
        public final Option.Key debugOverlay_reputationDebugOverlayShowArmor = new Option.Key("debugOverlay.reputationDebugOverlayShowArmor");
        public final Option.Key debugOverlay_reputationDebugOverlayShowHunger = new Option.Key("debugOverlay.reputationDebugOverlayShowHunger");
        public final Option.Key debugOverlay_reputationDebugOverlayRequireAdvancedTooltips = new Option.Key("debugOverlay.reputationDebugOverlayRequireAdvancedTooltips");
        public final Option.Key debugOverlay_reputationDebugOverlayOnlyWhenSneaking = new Option.Key("debugOverlay.reputationDebugOverlayOnlyWhenSneaking");
        public final Option.Key combat_enableVillagerSleepHealing = new Option.Key("combat.enableVillagerSleepHealing");
        public final Option.Key combat_villagerSleepHealingMaxHealthPercent = new Option.Key("combat.villagerSleepHealingMaxHealthPercent");
        public final Option.Key combat_enableVillagerDownedState = new Option.Key("combat.enableVillagerDownedState");
        public final Option.Key combat_allVillagersUseDownedState = new Option.Key("combat.allVillagersUseDownedState");
        public final Option.Key combat_raidVillagersUseDownedState = new Option.Key("combat.raidVillagersUseDownedState");
        public final Option.Key combat_hiredVillagersUseDownedState = new Option.Key("combat.hiredVillagersUseDownedState");
        public final Option.Key combat_partyVillagersUseDownedState = new Option.Key("combat.partyVillagersUseDownedState");
        public final Option.Key combat_playerDamageDownsEligibleVillagers = new Option.Key("combat.playerDamageDownsEligibleVillagers");
        public final Option.Key combat_mobDamageDownsEligibleVillagers = new Option.Key("combat.mobDamageDownsEligibleVillagers");
        public final Option.Key combat_environmentalDamageDownsEligibleVillagers = new Option.Key("combat.environmentalDamageDownsEligibleVillagers");
        public final Option.Key combat_downedMinimumTicks = new Option.Key("combat.downedMinimumTicks");
        public final Option.Key combat_downedRecoveryHealthPercent = new Option.Key("combat.downedRecoveryHealthPercent");
        public final Option.Key combat_downedThreatRadius = new Option.Key("combat.downedThreatRadius");
        public final Option.Key combat_downedQuietTicks = new Option.Key("combat.downedQuietTicks");
        public final Option.Key combat_weaponsmithsFightBack = new Option.Key("combat.weaponsmithsFightBack");
        public final Option.Key combat_toolsmithsFightBack = new Option.Key("combat.toolsmithsFightBack");
        public final Option.Key combat_armorersFightBack = new Option.Key("combat.armorersFightBack");
        public final Option.Key combat_fletchersFightBack = new Option.Key("combat.fletchersFightBack");
        public final Option.Key combat_butchersFightBack = new Option.Key("combat.butchersFightBack");
        public final Option.Key combat_villagersTargetHostileMobs = new Option.Key("combat.villagersTargetHostileMobs");
        public final Option.Key combat_wanderingTradersTargetHostileMobs = new Option.Key("combat.wanderingTradersTargetHostileMobs");
        public final Option.Key combat_villagersRetaliateAgainstHostileMobs = new Option.Key("combat.villagersRetaliateAgainstHostileMobs");
        public final Option.Key combat_wanderingTradersRetaliateAgainstHostileMobs = new Option.Key("combat.wanderingTradersRetaliateAgainstHostileMobs");
        public final Option.Key combat_villagersStandGroundAgainstHostileMobs = new Option.Key("combat.villagersStandGroundAgainstHostileMobs");
        public final Option.Key combat_villagersFleeVisibleCreepers = new Option.Key("combat.villagersFleeVisibleCreepers");
        public final Option.Key combat_villagersPickUpGroundWeapons = new Option.Key("combat.villagersPickUpGroundWeapons");
        public final Option.Key combat_wanderingTradersPickUpGroundWeapons = new Option.Key("combat.wanderingTradersPickUpGroundWeapons");
        public final Option.Key combat_naturalHostileTargetRadius = new Option.Key("combat.naturalHostileTargetRadius");
        public final Option.Key combat_combatWeaponDropChance = new Option.Key("combat.combatWeaponDropChance");
        public final Option.Key combat_combatWeaponEnchantChance = new Option.Key("combat.combatWeaponEnchantChance");
        public final Option.Key combat_armorerShieldChanceHard = new Option.Key("combat.armorerShieldChanceHard");
        public final Option.Key combat_clericsUsePotions = new Option.Key("combat.clericsUsePotions");
        public final Option.Key combat_passiveClericAllyHealRange = new Option.Key("combat.passiveClericAllyHealRange");
        public final Option.Key combat_passiveClericAllyHealHealthThreshold = new Option.Key("combat.passiveClericAllyHealHealthThreshold");
        public final Option.Key combat_passiveClericAllyHealRequiresLineOfSight = new Option.Key("combat.passiveClericAllyHealRequiresLineOfSight");
        public final Option.Key combat_hostileTierHarassThrowEnabled = new Option.Key("combat.hostileTierHarassThrowEnabled");
        public final Option.Key combat_hostileTierHarassThrowMinIntervalTicks = new Option.Key("combat.hostileTierHarassThrowMinIntervalTicks");
        public final Option.Key combat_hostileTierHarassThrowMaxIntervalTicks = new Option.Key("combat.hostileTierHarassThrowMaxIntervalTicks");
        public final Option.Key duels_enabled = new Option.Key("duels.enabled");
        public final Option.Key duels_allowBringYourOwnLoadout = new Option.Key("duels.allowBringYourOwnLoadout");
        public final Option.Key duels_minimumGuts = new Option.Key("duels.minimumGuts");
        public final Option.Key duels_cooldownDays = new Option.Key("duels.cooldownDays");
        public final Option.Key duels_refusalLosses = new Option.Key("duels.refusalLosses");
        public final Option.Key duels_arenaRadius = new Option.Key("duels.arenaRadius");
        public final Option.Key duels_showArenaParticles = new Option.Key("duels.showArenaParticles");
        public final Option.Key duels_boundaryGraceTicks = new Option.Key("duels.boundaryGraceTicks");
        public final Option.Key duels_timeoutTicks = new Option.Key("duels.timeoutTicks");
        public final Option.Key duels_spectatorRadius = new Option.Key("duels.spectatorRadius");
        public final Option.Key duels_spectatorCap = new Option.Key("duels.spectatorCap");
        public final Option.Key duels_watcherReputation = new Option.Key("duels.watcherReputation");
        public final Option.Key wanderer_dropEmeralds = new Option.Key("wanderer.dropEmeralds");
        public final Option.Key wanderer_dropInvisibilityPotion = new Option.Key("wanderer.dropInvisibilityPotion");
        public final Option.Key wanderer_dropRandomCurrentTrade = new Option.Key("wanderer.dropRandomCurrentTrade");
        public final Option.Key wanderer_randomTradeDropChance = new Option.Key("wanderer.randomTradeDropChance");
        public final Option.Key quest_showQuestIndicators = new Option.Key("quest.showQuestIndicators");
        public final Option.Key quest_enableQuestItemShaderHighlights = new Option.Key("quest.enableQuestItemShaderHighlights");
        public final Option.Key quest_questItemHighlightMode = new Option.Key("quest.questItemHighlightMode");
    }
}

