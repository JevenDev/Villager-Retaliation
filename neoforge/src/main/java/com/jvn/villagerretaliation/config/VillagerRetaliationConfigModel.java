package com.jvn.villagerretaliation.config;

import com.jvn.villagerretaliation.VillagerRetaliation;
import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.Nest;
import io.wispforest.owo.config.annotation.RangeConstraint;
import io.wispforest.owo.config.annotation.SectionHeader;
import com.jvn.villagerretaliation.config.ContainerForcedDialogueTrigger;
import com.jvn.villagerretaliation.config.ContainerWatchMode;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.InteractionChatPosition;
import com.jvn.villagerretaliation.config.ReputationChangeDisplayMode;
import com.jvn.villagerretaliation.config.ReputationChangeHudPosition;
import com.jvn.villagerretaliation.config.ReputationChangeNotificationStyle;
import com.jvn.villagerretaliation.config.VillagerChatBroadcastMode;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;

@Modmenu(modId = VillagerRetaliation.MOD_ID)
@Config(name = VillagerRetaliation.MOD_ID, wrapperName = "VillagerRetaliationOwoConfig")
public final class VillagerRetaliationConfigModel {
    @Nest
    @SectionHeader("general")
    public General general = new General();

    @Nest
    @SectionHeader("dialogue")
    public Dialogue dialogue = new Dialogue();

    @Nest
    @SectionHeader("notifications")
    public Notifications notifications = new Notifications();

    @Nest
    @SectionHeader("gifts")
    public Gifts gifts = new Gifts();

    @Nest
    @SectionHeader("social")
    public Social social = new Social();

    @Nest
    @SectionHeader("balance")
    public Balance balance = new Balance();

    @Nest
    @SectionHeader("retaliation")
    public Retaliation retaliation = new Retaliation();

    @Nest
    @SectionHeader("reputation")
    public Reputation reputation = new Reputation();

    @Nest
    @SectionHeader("trade")
    public Trade trade = new Trade();

    @Nest
    @SectionHeader("debugOverlay")
    public DebugOverlay debugOverlay = new DebugOverlay();

    @Nest
    @SectionHeader("combat")
    public Combat combat = new Combat();

    @Nest
    @SectionHeader("wanderer")
    public Wanderer wanderer = new Wanderer();

    @Nest
    @SectionHeader("quest")
    public Quest quest = new Quest();

    public static final class General {
        public boolean enableVillagerDrops = true;

        public boolean enableWanderingTraderDrops = true;

        public boolean enableVillagerRetaliation = true;

        public boolean enableVillagerReputation = true;

        public boolean enableVanillaGossipIntegration = true;

        public boolean enableDespisedKillOnSight = true;

        public ReputationChangeDisplayMode reputationChangeDisplayMode = ReputationChangeDisplayMode.HUD;

        public ReputationChangeNotificationStyle reputationChangeNotificationStyle = ReputationChangeNotificationStyle.EXPERIMENTAL;

        public ReputationChangeHudPosition reputationChangeHudPosition = ReputationChangeHudPosition.TOP_LEFT;

        public boolean collapseReputationChangeNotifications = true;

        public boolean showVillagerNameTags = true;

        public VillagerStatDisplayMode villagerStatDisplayMode = VillagerStatDisplayMode.PARTY_ONLY;

        public boolean villagerReputationHoverTooltipRequiresEmerald = true;

        public boolean showTradeGuiReputationIcon = true;

        public boolean enableVillagerDeathMessages = true;
    }

    public static final class Dialogue {
        public boolean enableInteractionScreen = true;

        public boolean shiftRightClickBypassesInteractionScreen = true;

        public boolean enableDialogueReputationEffects = true;

        public boolean enableDialogueCameraFocus = true;

        public boolean enableDialogueCinematicBars = true;

        @RangeConstraint(min = 0, max = 96)
        public int dialogueCinematicBarHeight = 26;

        @RangeConstraint(min = -64, max = 64)
        public int dialogueCinematicBarMinSlant = -12;

        @RangeConstraint(min = -64, max = 64)
        public int dialogueCinematicBarMaxSlant = 12;

        public boolean animateDialogueCinematicBars = true;

        public boolean enableForcedDialogue = true;

        public boolean enableContainerForcedDialogue = true;

        public boolean enableContainerOpenReaction = true;

        public boolean enableRetaliationForcedDialogue = true;

        public boolean enablePlayerItemProximityForcedDialogue = true;

        public boolean separateVillagerChatMessages = false;

        public boolean separateVillagerChatSpeakers = true;

        public InteractionChatPosition interactionChatPosition = InteractionChatPosition.BOTTOM_LEFT;

        public VillagerChatBroadcastMode villagerChatBroadcastMode = VillagerChatBroadcastMode.LOCAL;

        @RangeConstraint(min = 1, max = 64)
        public int villagerChatBroadcastRadius = 16;

        public boolean showPersonalInteractionDialogueToNearbyPlayers = true;

        public DialogueTextSpeed dialogueTextSpeed = DialogueTextSpeed.MEDIUM;

        public boolean enableDialogueBlipAudio = true;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double dialogueBlipVolume = 0.5D;

        @RangeConstraint(min = 0.5D, max = 2.0D, decimalPlaces = 2)
        public double dialogueBlipMinPitch = 0.55D;

        @RangeConstraint(min = 0.5D, max = 2.0D, decimalPlaces = 2)
        public double dialogueBlipMaxPitch = 0.65D;

        @RangeConstraint(min = 0.0D, max = 0.25D, decimalPlaces = 2)
        public double dialogueCameraZoomAmount = 0.15D;

        public boolean enableNormalDialogueCameraFocus = true;

        @RangeConstraint(min = 0.0D, max = 0.25D, decimalPlaces = 2)
        public double normalDialogueCameraZoomAmount = 0.08D;

        @RangeConstraint(min = 1, max = 40)
        public int dialogueCameraTransitionTicks = 3;

        public boolean freezeVillagerDuringDialogue = true;

        @RangeConstraint(min = 3.0D, max = 16.0D, decimalPlaces = 1)
        public double maxDialogueDistance = 7.0D;

        @RangeConstraint(min = 3.0D, max = 32.0D, decimalPlaces = 1)
        public double maxForcedDialogueDistance = 16.0D;

        public ContainerForcedDialogueTrigger containerForcedDialogueTrigger = ContainerForcedDialogueTrigger.OPENING;

        public ContainerWatchMode containerWatchMode = ContainerWatchMode.GENERATED_LOOT_ONLY;

        @RangeConstraint(min = 0, max = 30)
        public int dialoguePositiveReputationCooldownDays = 1;

        @RangeConstraint(min = 0, max = 100)
        public int repeatedQuestionPositiveLimit = 5;

        @RangeConstraint(min = 0, max = 100)
        public int trustedRepeatedDialogueLimitBonus = 2;

        @RangeConstraint(min = 0, max = 100)
        public int respectedRepeatedDialogueLimitBonus = 4;

        @RangeConstraint(min = 0, max = 100)
        public int reveredRepeatedDialogueLimitBonus = 7;

        @RangeConstraint(min = 0, max = 100)
        public int royaltyRepeatedDialogueLimitBonus = 10;

        @RangeConstraint(min = -1000, max = 0)
        public int repeatedQuestionReputationLoss = -1;

        @RangeConstraint(min = 1, max = 24000)
        public int repeatedDialogueOptionResetTicks = 6000;

        @RangeConstraint(min = 0, max = 1000)
        public int giftAnnoyanceReductionDivisor = 8;

        @RangeConstraint(min = 1.0D, max = 1024.0D, decimalPlaces = 1)
        public double maxFollowDistance = 32.0D;

        @RangeConstraint(min = 0, max = 1000)
        public int greetingReputationGain = 1;

        @RangeConstraint(min = 0, max = 1000)
        public int questionReputationGain = 1;

        @RangeConstraint(min = 0, max = 1000)
        public int storyReputationGain = 1;

        @RangeConstraint(min = 0, max = 1000)
        public int jokeReputationGain = 1;

        @RangeConstraint(min = -1000, max = 0)
        public int jokeReputationLoss = -1;

        @RangeConstraint(min = -1000, max = 0)
        public int insultReputationLoss = -3;

        @RangeConstraint(min = 0, max = 1000)
        public int firstGreetingReputationGain = 1;

        @RangeConstraint(min = -1000, max = 0)
        public int firstInsultReputationLoss = -5;

        public boolean disableDialogueTextEffects = false;
    }

    public static final class Notifications {
        public boolean enableWorldTextNotifications = true;

        public boolean enableAmbientMurmurs = true;

        public boolean enableSleepIndicators = true;

        public boolean enableDamageAlerts = true;

        public boolean enableCombatAlerts = true;

        public boolean enableTradeAndGiftWorldText = true;
    }

    public static final class Gifts {
        public boolean enableVillagerGifts = true;

        public boolean enableHighReputationGifts = true;

        public boolean enableGiftKeepsakes = true;
    }

    public static final class Social {
        public boolean enableVillagerSocialGraph = true;

        public boolean enableVillagerMoods = true;

        public boolean enableSocialAttributeBehavior = true;

        public boolean enableSocialAttributeMoodEffects = true;

        public boolean enableSocialAttributeDialogueEffects = true;

        public boolean enableSocialAttributeReputationEffects = true;

        public boolean enableSocialAttributeRetaliationEffects = true;

        public boolean enableSocialAttributeGossipEffects = true;

        @RangeConstraint(min = 0.0D, max = 2.0D, decimalPlaces = 1)
        public double socialAttributeEffectScale = 1.0D;

        public boolean enableFamilyBreedingRules = true;

        public boolean enableOppositeGenderBreedingRules = true;

        public boolean enableParentReputationInheritance = true;
    }

    public static final class Balance {
        public boolean babyVillagersDropLoot = false;

        public boolean requirePlayerKillForProfessionLoot = true;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double villagerEmeraldDropChance = 0.35;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double villagerBreadDropChance = 0.60;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double professionDropChance = 0.50;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double rareDropChance = 0.05;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double veryRareDropChance = 0.01;

        @RangeConstraint(min = 1, max = 128)
        public int hiredContractBaseDailyCost = 12;

        @RangeConstraint(min = 1, max = 128)
        public int hiredContractMinimumDailyCost = 4;

        @RangeConstraint(min = 1, max = 512)
        public int hiredContractMaximumDailyCost = 128;

        @RangeConstraint(min = 0, max = 32)
        public int hiredContractSkillPremiumPerTen = 2;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractRoyaltyCostModifier = -5;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractReveredCostModifier = -3;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractRespectedCostModifier = 0;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractTrustedCostModifier = 2;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractNeutralCostModifier = 4;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractSuspiciousCostModifier = 8;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractHostileCostModifier = 14;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractDespisedCostModifier = 24;

        @RangeConstraint(min = -128, max = 128)
        public int hiredContractFearedCostModifier = 32;

        @RangeConstraint(min = 0, max = 100)
        public int hiredContractEarlyEndRefundPercent = 50;

        @RangeConstraint(min = 10, max = 200)
        public int hiredWorkTickInterval = 40;

        @RangeConstraint(min = 0, max = 600)
        public int hiredWorkNoticeCooldownSeconds = 300;

        @RangeConstraint(min = 4, max = 32)
        public int hiredWorkDefaultRadius = 16;

        @RangeConstraint(min = 4, max = 32)
        public int hiredWorkMaxRadius = 32;

        @RangeConstraint(min = 1, max = 300)
        public int hiredWorkBaseEfficiencyPercent = 100;

        @RangeConstraint(min = 1, max = 300)
        public int hiredWorkMinimumEfficiencyPercent = 25;

        @RangeConstraint(min = 1, max = 300)
        public int hiredWorkMaximumEfficiencyPercent = 175;

        public boolean enableHiredWorkSkillGrowth = true;

        @Nest
        public HiredWorkSkillGrowth hiredWorkSkillGrowth = new HiredWorkSkillGrowth();

        @RangeConstraint(min = 128, max = 16384)
        public int hiredBuilderMaxBlocks = 4096;

        @RangeConstraint(min = 8, max = 64)
        public int hiredBuilderMaxSiteDistance = 28;

        @RangeConstraint(min = 8, max = 128)
        public int hiredBuilderMaterialStorageRadius = 32;

        @RangeConstraint(min = 0, max = 512)
        public int hiredBuilderBaseEmeraldCost = 8;

        @RangeConstraint(min = 0, max = 128)
        public int hiredBuilderEmeraldsPer64Blocks = 3;

        public boolean hiredBuilderCanReplaceSoftBlocks = true;

    }

    public static final class HiredWorkSkillGrowth {
        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double combat = 0.2D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double mining = 0.1D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double logging = 0.2D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double farming = 0.2D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double brewing = 0.2D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double cooking = 0.2D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double builder = 0.15D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double navigation = 0.2D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double animalHandling = 0.2D;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double nitwit = 0.2D;
    }

    public static final class Retaliation {
        public boolean attackAggrosOnlyHitVillager = true;

        public boolean killingVillagerAggrosNearbyVillagers = true;

        public boolean babyVillagersFleeWitnessedDeaths = true;

        @RangeConstraint(min = 0.0D, max = 128.0D, decimalPlaces = 1)
        public double villagerKillAggroRadius = 24.0D;

        public boolean retaliationWitnessesRequireLineOfSight = true;

        @RangeConstraint(min = 1, max = 20 * 60 * 10)
        public int aggroDurationTicks = 600;

        public boolean nearbyVillagersIgnoreCreativePlayers = true;
    }

    public static final class Reputation {
        @RangeConstraint(min = -1000, max = 1000)
        public int directHitPenalty = -25;

        @RangeConstraint(min = -1000, max = 1000)
        public int witnessedHitPenalty = -8;

        @RangeConstraint(min = -1000, max = 1000)
        public int witnessedKillPenalty = -60;

        @RangeConstraint(min = -1000, max = 1000)
        public int witnessedBabyKillPenalty = -120;

        @RangeConstraint(min = -1000, max = 1000)
        public int witnessedIronGolemKillPenalty = -60;

        @RangeConstraint(min = -1000, max = 0)
        public int containerBreakReputationLoss = -30;

        @RangeConstraint(min = -1000, max = 0)
        public int generatedContainerBreakItemReputationLoss = -1;

        @RangeConstraint(min = -1000, max = 1000)
        public int tradeReputationGain = 1;

        @RangeConstraint(min = 0, max = 1000)
        public int maxTradeReputationGainPerVillagerPerDay = 8;

        @RangeConstraint(min = -1000, max = 0)
        public int sleepingVillagerBotherReputationLoss = -2;

        @RangeConstraint(min = -1000, max = 0)
        public int sleepingVillagerBedBreakReputationLoss = -15;

        @RangeConstraint(min = -1000, max = 1000)
        public int healVillagerGain = 10;

        @RangeConstraint(min = -1000, max = 1000)
        public int saveVillagerGain = 15;

        @RangeConstraint(min = -1000, max = 1000)
        public int positiveWitnessGain = 10;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 1)
        public double hostileMobAssistReputationMultiplier = 0.5D;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double gossipReputationMultiplier = 0.25D;

        @RangeConstraint(min = -10000, max = 10000)
        public int royaltyThreshold = 1000;

        @RangeConstraint(min = -10000, max = 10000)
        public int reveredThreshold = 400;

        @RangeConstraint(min = -10000, max = 10000)
        public int respectedThreshold = 250;

        @RangeConstraint(min = -10000, max = 10000)
        public int trustedThreshold = 75;

        @RangeConstraint(min = -10000, max = 10000)
        public int suspiciousThreshold = -75;

        @RangeConstraint(min = -10000, max = 10000)
        public int hostileThreshold = -100;

        @RangeConstraint(min = -10000, max = 10000)
        public int despisedThreshold = -400;

        @RangeConstraint(min = -10000, max = 10000)
        public int fearedThreshold = -750;

        @RangeConstraint(min = 0.0D, max = 128.0D, decimalPlaces = 1)
        public double witnessRadius = 24.0D;

        @RangeConstraint(min = 0.0D, max = 128.0D, decimalPlaces = 1)
        public double gossipRadius = 16.0D;

        @RangeConstraint(min = 0.0D, max = 128.0D, decimalPlaces = 1)
        public double despisedSightRadius = 24.0D;

        public boolean reputationDecayEnabled = true;

        @RangeConstraint(min = 20, max = 24000 * 30)
        public int reputationDecayInterval = 24000;

        @RangeConstraint(min = 0, max = 1000)
        public int reputationDecayAmount = 1;

        @RangeConstraint(min = 0, max = 3650)
        public int pruneNeutralEntriesAfterDays = 30;

        public boolean witnessReputationRequiresLineOfSight = false;

        public boolean enableReputationTradePricing = true;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 2)
        public double reputationTradePriceScale = 0.25D;
    }

    public static final class Trade {
        public boolean enableSkillTradeOverhaul = true;

        public boolean disableVillagerWalletLimit = false;

        public boolean enableSpecialOrders = true;

        public VillagerReputationLevel specialOrderMinReputation = VillagerReputationLevel.RESPECTED;

        @RangeConstraint(min = 1, max = 30)
        public int specialOrderWaitDays = 2;

        @RangeConstraint(min = 0, max = 30)
        public int specialOrderCooldownDays = 3;

        public boolean specialOrderExtraCostEnabled = false;

        @RangeConstraint(min = 1, max = 3)
        public int specialOrderMaxActivePerPlayer = 3;

        public boolean skillTradeQualityScaling = true;

        public boolean skillTradeLowSkillPenalties = true;

        @RangeConstraint(min = 1, max = 5)
        public int skillTradeMaxEnchantmentLevel = 3;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 1)
        public double skillTradeRareChanceMultiplier = 1.0D;

        public boolean skillTradeAllowHighTierEquipment = true;

        public boolean skillTradeAllowSpecialArrows = true;

        public boolean skillTradeAllowRareSpecialtyTrades = true;

        public boolean enableSkillGrowthFromTradingLevels = true;

        public boolean enableRegularTradeSkillGrowth = true;

        @RangeConstraint(min = 0.0D, max = 10.0D, decimalPlaces = 1)
        public double regularTradeSkillGrowthAmount = 0.5D;

        public boolean enableSkillBasedTradeLeveling = true;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 1)
        public double skillBasedTradeLevelingMinMultiplier = 0.2D;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 1)
        public double skillBasedTradeLevelingMaxMultiplier = 1.0D;

        public boolean enableSkillGrowthFeedback = true;

        @RangeConstraint(min = 0, max = 10)
        public int skillGrowthPrimaryMin = 1;

        @RangeConstraint(min = 0, max = 10)
        public int skillGrowthPrimaryMax = 5;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double skillGrowthSecondaryChance = 0.35D;

        @RangeConstraint(min = 0, max = 5)
        public int skillGrowthSecondaryMax = 1;
    }

    public static final class DebugOverlay {
        public boolean showVillagerReputationDebugOverlay = false;

        @RangeConstraint(min = 0.0D, max = 128.0D, decimalPlaces = 1)
        public double reputationDebugOverlayMaxDistance = 32.0D;

        public boolean reputationDebugOverlayShowTier = true;

        public boolean reputationDebugOverlayShowNumber = true;

        public boolean reputationDebugOverlayShowHealth = false;

        public boolean reputationDebugOverlayShowArmor = false;

        public boolean reputationDebugOverlayRequireAdvancedTooltips = false;

        public boolean reputationDebugOverlayOnlyWhenSneaking = false;
    }

    public static final class Combat {
        public boolean weaponsmithsFightBack = true;

        public boolean toolsmithsFightBack = true;

        public boolean armorersFightBack = true;

        public boolean fletchersFightBack = true;

        public boolean butchersFightBack = true;

        public boolean villagersTargetHostileMobs = true;

        public boolean wanderingTradersTargetHostileMobs = true;

        public boolean villagersRetaliateAgainstHostileMobs = true;

        public boolean wanderingTradersRetaliateAgainstHostileMobs = true;

        public boolean villagersStandGroundAgainstHostileMobs = true;

        public boolean villagersFleeVisibleCreepers = true;

        public boolean villagersPickUpGroundWeapons = true;

        public boolean wanderingTradersPickUpGroundWeapons = true;

        @RangeConstraint(min = 0.0D, max = 64.0D, decimalPlaces = 1)
        public double naturalHostileTargetRadius = 16.0D;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 3)
        public double combatWeaponDropChance = 0.085D;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double combatWeaponEnchantChance = 0.25D;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double armorerShieldChanceHard = 0.35D;

        public boolean farmersUseBread = true;

        public boolean clericsUsePotions = true;

        @RangeConstraint(min = 1.0D, max = 64.0D, decimalPlaces = 1)
        public double passiveClericAllyHealRange = 12.0D;

        @RangeConstraint(min = 0.05D, max = 1.0D, decimalPlaces = 2)
        public double passiveClericAllyHealHealthThreshold = 0.60D;

        public boolean passiveClericAllyHealRequiresLineOfSight = true;

        public boolean hostileTierHarassThrowEnabled = true;

        @RangeConstraint(min = 20, max = 20 * 60 * 10)
        public int hostileTierHarassThrowMinIntervalTicks = 200;

        @RangeConstraint(min = 20, max = 20 * 60 * 10)
        public int hostileTierHarassThrowMaxIntervalTicks = 360;
    }

    public static final class Wanderer {
        public boolean dropEmeralds = true;

        public boolean dropInvisibilityPotion = true;

        public boolean dropRandomCurrentTrade = true;

        @RangeConstraint(min = 0.0D, max = 1.0D, decimalPlaces = 2)
        public double randomTradeDropChance = 0.50;
    }

    public static final class Quest {
        public boolean enableQuestItemShaderHighlights = true;

        public QuestItemHighlightMode questItemHighlightMode = QuestItemHighlightMode.ALL_ACTIVE_QUESTS;
    }
}
