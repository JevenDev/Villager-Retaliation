package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.resources.BiomeStoryResources;
import com.jvn.villagerretaliation.dialogue.resources.DangerousStructureStoryResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.item.OminousBannerRecognition;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.raid.PlayerRaidSavedData;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public final class VillagerReputationAdvancements {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int VILLAGE_HAS_EYES_WITNESSES = 3;
    private static final int REGULAR_CUSTOMER_TRADES = 10;
    private static final int COMMUNITY_SUPPORT_VILLAGERS = 5;
    private static final int VILLAGE_ENEMY_HOSTILES = 5;
    private static final int MOB_JUSTICE_HOSTILES = 8;
    private static final int MULTI_VILLAGE_THRESHOLD = 3;
    private static final int PEOPLE_CHAMPION_VILLAGERS = 10;
    private static final int LARGE_RAID_DEFENSE = 15;
    private static final long DIRECT_HIT_MEMORY_TICKS = 20L * 40L;
    private static final double HOSTILITY_SCAN_RADIUS = 64.0D;
    private static final double DIALOGUE_MAP_FOUND_RADIUS = 64.0D;
    private static final double STORY_HINT_FOUND_RADIUS = 256.0D;
    private static final double DANGEROUS_STORY_VILLAGER_RADIUS = 64.0D;
    private static final long DISCOVERY_SCAN_INTERVAL_TICKS = 20L;
    private static final long BIOME_STORY_SCAN_INTERVAL_TICKS = 20L * 5L;
    private static final long DANGEROUS_STORY_SHARE_TICKS = 20L * 60L * 60L * 6L;

    private static final Map<UUID, Map<UUID, Integer>> TRADE_COUNTS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> TRADED_VILLAGERS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> RECENT_DIRECT_VILLAGER_HITS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> HOSTILE_OR_WORSE_HISTORY = new HashMap<>();
    private static final Map<UUID, Long> NEXT_DISCOVERY_SCAN = new HashMap<>();
    private static final Map<UUID, Long> NEXT_BIOME_STORY_SCAN = new HashMap<>();

    private static final Set<VillagerProfession> SUPPORTED_WORK_PROFESSIONS = Set.of(
            VillagerProfession.ARMORER,
            VillagerProfession.BUTCHER,
            VillagerProfession.CARTOGRAPHER,
            VillagerProfession.CLERIC,
            VillagerProfession.FARMER,
            VillagerProfession.FISHERMAN,
            VillagerProfession.FLETCHER,
            VillagerProfession.LEATHERWORKER,
            VillagerProfession.LIBRARIAN,
            VillagerProfession.MASON,
            VillagerProfession.NITWIT,
            VillagerProfession.SHEPHERD,
            VillagerProfession.TOOLSMITH,
            VillagerProfession.WEAPONSMITH);

    private static final ResourceLocation ROOT = advancementId("reputation/root");
    private static final ResourceLocation COMMONFOLK = advancementId("reputation/commonfolk");
    private static final ResourceLocation IM_SORRY = advancementId("reputation/im_sorry");
    private static final ResourceLocation BAD_FIRST_IMPRESSION = advancementId("reputation/bad_first_impression");
    private static final ResourceLocation HANDS_OFF = advancementId("reputation/hands_off");
    private static final ResourceLocation THE_VILLAGE_HAS_EYES = advancementId("reputation/the_village_has_eyes");
    private static final ResourceLocation MARKED = advancementId("reputation/marked");
    private static final ResourceLocation VILLAGE_ENEMY = advancementId("reputation/village_enemy");
    private static final ResourceLocation MOB_JUSTICE = advancementId("reputation/mob_justice");
    private static final ResourceLocation REGULAR_CUSTOMER = advancementId("reputation/regular_customer");
    private static final ResourceLocation COMMUNITY_SUPPORT = advancementId("reputation/community_support");
    private static final ResourceLocation PRICE_OF_TRUST = advancementId("reputation/price_of_trust");
    private static final ResourceLocation REFUSED_SERVICE = advancementId("reputation/refused_service");
    private static final ResourceLocation HERO_NOT_MENACE = advancementId("reputation/hero_not_menace");
    private static final ResourceLocation AN_UNWISE_DECISION = advancementId("reputation/an_unwise_decision");
    private static final ResourceLocation PEACE_OFFERING = advancementId("reputation/peace_offering");
    private static final ResourceLocation ACCIDENTALLY_OF_COURSE = advancementId("reputation/accidentally_of_course");
    private static final ResourceLocation FAMILIAR_FACE = advancementId("reputation/familiar_face");
    private static final ResourceLocation RESPECT_IS_EARNED = advancementId("reputation/respect_is_earned");
    private static final ResourceLocation FRIEND_OF_THE_VILLAGE = advancementId("reputation/friend_of_the_village");
    private static final ResourceLocation LOCAL_LEGEND = advancementId("reputation/local_legend");
    private static final ResourceLocation COVER_THEM_IN_DEBRIS = advancementId("reputation/cover_them_in_debris");
    private static final ResourceLocation CROWNED_BY_THE_VILLAGE = advancementId("reputation/crowned_by_the_village");
    private static final ResourceLocation SECOND_CHANCE = advancementId("reputation/second_chance");
    private static final ResourceLocation THE_VILLAGE_REMEMBERS = advancementId("reputation/the_village_remembers");
    private static final ResourceLocation NO_REST_FOR_THE_WICKED = advancementId("reputation/no_rest_for_the_wicked");
    private static final ResourceLocation TRUSTED_DIRECTIONS = advancementId("reputation/trusted_directions");
    private static final ResourceLocation BAIT_AND_BETRAYAL = advancementId("reputation/bait_and_betrayal");
    private static final ResourceLocation CHANGED_MY_MIND = advancementId("reputation/changed_my_mind");
    private static final ResourceLocation ONCE_UPON_A_TIME = advancementId("reputation/once_upon_a_time");
    private static final ResourceLocation STORY_KEEPER = advancementId("reputation/story_keeper");
    private static final ResourceLocation VILLAGE_CHRONICLER = advancementId("reputation/village_chronicler");
    private static final ResourceLocation LEGEND_TRADER = advancementId("reputation/legend_trader");
    private static final ResourceLocation STEADY_GAZE = advancementId("reputation/steady_gaze");
    private static final ResourceLocation THE_MARK_YOU_CHOSE = advancementId("reputation/the_mark_you_chose");
    private static final ResourceLocation SOUND_THE_HORN = advancementId("reputation/sound_the_horn");
    private static final ResourceLocation THE_VILLAGE_FALLS = advancementId("reputation/the_village_falls");
    private static final ResourceLocation QUARTER_GIVEN = advancementId("reputation/quarter_given");
    private static final ResourceLocation ARMY_OF_ONE = advancementId("reputation/army_of_one");
    private static final ResourceLocation ET_TU_BRUTE = advancementId("reputation/et_tu_brute");
    private static final ResourceLocation WAR_PARTY = advancementId("reputation/war_party");
    private static final ResourceLocation YOU_AND_WHAT_ARMY = advancementId("reputation/you_and_what_army");
    private static final ResourceLocation HONEST_WORK = advancementId("reputation/honest_work");
    private static final ResourceLocation FULL_EMPLOYMENT = advancementId("reputation/full_employment");
    private static final ResourceLocation VILLAGE_HOPPER = advancementId("reputation/village_hopper");
    private static final ResourceLocation PEOPLES_CHAMPION = advancementId("reputation/peoples_champion");
    private static final ResourceLocation LIVING_LEGEND = advancementId("reputation/living_legend");

    private VillagerReputationAdvancements() {
    }

    public static void clearRuntimeState() {
        TRADE_COUNTS.clear();
        TRADED_VILLAGERS.clear();
        RECENT_DIRECT_VILLAGER_HITS.clear();
        HOSTILE_OR_WORSE_HISTORY.clear();
        NEXT_DISCOVERY_SCAN.clear();
        NEXT_BIOME_STORY_SCAN.clear();
    }

    public static void onVillagerInteraction(ServerPlayer player) {
        award(player, COMMONFOLK);
    }

    public static void onVillagerConversationStarted(ServerPlayer player) {
        if (OminousBannerRecognition.isDisplaying(player)) {
            award(player, THE_MARK_YOU_CHOSE);
        }
    }

    public static void onVillagerPacified(ServerPlayer player) {
        award(player, IM_SORRY);
    }

    public static void onLuredVillagerKilled(ServerPlayer player) {
        award(player, BAIT_AND_BETRAYAL);
    }

    public static void onGiftTakenBack(ServerPlayer player) {
        award(player, CHANGED_MY_MIND);
    }

    public static void onSleepingVillagerBedBroken(ServerPlayer player) {
        award(player, NO_REST_FOR_THE_WICKED);
    }

    public static void onVillagerMouseStared(ServerPlayer player) {
        award(player, STEADY_GAZE);
    }

    public static void onPlayerRaidDeclared(ServerPlayer player) {
        award(player, SOUND_THE_HORN);
    }

    public static void onPlayerRaidDeclared(ServerPlayer player, PlayerRaidSavedData.RaidRecord raid) {
        onPlayerRaidDeclared(player);
        if (raid != null && !raid.defectors().isEmpty()) {
            award(player, ET_TU_BRUTE);
        }
    }

    public static void onPlayerRaidWon(ServerPlayer player) {
        award(player, THE_VILLAGE_FALLS);
    }

    public static void onPlayerRaidWon(ServerPlayer player, PlayerRaidSavedData.RaidRecord raid) {
        onPlayerRaidWon(player);
        if (raid == null) {
            return;
        }
        int defenseSize = raid.initialDefenderCount() + raid.initialMercyCandidateCount();
        if (defenseSize >= LARGE_RAID_DEFENSE) {
            award(player, YOU_AND_WHAT_ARMY);
        }
        if (defenseSize >= 20 && raid.raiderPlayers().size() == 1 && raid.raiderVillagers().isEmpty()) {
            award(player, ARMY_OF_ONE);
        }
        if (raid.raiderPlayers().size() == 3 && raid.raiderVillagers().size() == 4) {
            award(player, WAR_PARTY);
        }
    }

    public static void onVillagerSparedDuringRaid(ServerPlayer player) {
        award(player, QUARTER_GIVEN);
    }

    public static void onHiredAssignmentCompleted(ServerPlayer player, Villager villager) {
        award(player, HONEST_WORK);
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!SUPPORTED_WORK_PROFESSIONS.contains(profession)) {
            return;
        }
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        if (professionId != null) {
            awardCriterion(player, FULL_EMPLOYMENT, professionId.getPath());
        }
    }

    public static void onQuestCompleted(ServerPlayer player) {
        Map<String, Set<UUID>> villagersByVillage = new HashMap<>();
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry
                : VillagerQuestSavedData.get(player.serverLevel()).progress(player.getUUID())) {
            for (VillagerQuestSavedData.CompletionHistoryEntry completion : entry.getValue().completionHistory()) {
                if (completion.issuerId() == null || !VillageScopeKeys.isVillageKey(completion.issuerVillageKey())) {
                    continue;
                }
                Set<UUID> villagers = villagersByVillage.computeIfAbsent(
                        completion.issuerVillageKey(), ignored -> new HashSet<>());
                villagers.add(completion.issuerId());
                if (villagers.size() >= PEOPLE_CHAMPION_VILLAGERS) {
                    award(player, PEOPLES_CHAMPION);
                    return;
                }
            }
        }
    }

    public static void onSharedStory(ServerPlayer player, int sharedStoryCount) {
        if (sharedStoryCount >= 1) {
            award(player, ONCE_UPON_A_TIME);
        }
        if (sharedStoryCount >= 5) {
            award(player, STORY_KEEPER);
        }
        if (sharedStoryCount >= 10) {
            award(player, VILLAGE_CHRONICLER);
        }
        if (sharedStoryCount >= 25) {
            award(player, LEGEND_TRADER);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        UUID playerId = player.getUUID();
        ResourceLocation currentBiomeId = null;
        if (consumePlayerScanSlot(playerId, NEXT_DISCOVERY_SCAN, gameTime, DISCOVERY_SCAN_INTERVAL_TICKS)) {
            currentBiomeId = currentBiomeId(level, player.blockPosition());
            VillagerInteractionTracker.DiscoveryReports discoveries = VillagerInteractionTracker.markDiscoveriesNear(
                    level,
                    player,
                    currentBiomeId,
                    DIALOGUE_MAP_FOUND_RADIUS,
                    STORY_HINT_FOUND_RADIUS
            );
            if (!discoveries.cartographerMapReports().isEmpty()) {
                award(player, TRUSTED_DIRECTIONS);
                discoveries.cartographerMapReports().forEach(discovery -> sendDialogueMapFoundNotice(player, discovery));
            }
            if (!discoveries.storyHintReports().isEmpty()) {
                award(player, TRUSTED_DIRECTIONS);
                discoveries.storyHintReports().forEach(discovery -> sendStoryHintFoundNotice(player, discovery));
            }
            rememberDangerousStructureStories(player);
        }

        if (consumePlayerScanSlot(playerId, NEXT_BIOME_STORY_SCAN, gameTime, BIOME_STORY_SCAN_INTERVAL_TICKS)) {
            if (currentBiomeId == null) {
                currentBiomeId = currentBiomeId(level, player.blockPosition());
            }
            rememberBiomeStories(player, currentBiomeId);
        }
    }

    private static void rememberBiomeStories(ServerPlayer player, ResourceLocation currentBiomeId) {
        if (currentBiomeId == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();

        BiomeStoryResources.Entry storyBiome = BiomeStoryResources.entriesByBiome(level.getServer()).get(currentBiomeId);
        if (storyBiome == null) {
            return;
        }
        List<Villager> nearbyVillagers = nearbyVillagers(level, player.blockPosition());
        if (nearbyVillagers.isEmpty()) {
            return;
        }
        nearbyVillagers = shareableStoryCandidates(
                level,
                player,
                nearbyVillagers,
                VillagerInteractionTracker.StoryHintKind.BIOME,
                storyBiome.biomeId(),
                player.blockPosition()
        );
        if (nearbyVillagers.isEmpty()) {
            return;
        }

        long expiresAt = gameTime + DANGEROUS_STORY_SHARE_TICKS;
        for (Villager villager : nearbyVillagers) {
            VillagerInteractionTracker.rememberShareableStory(
                    level,
                    villager,
                    player,
                    VillagerInteractionTracker.StoryHintKind.BIOME,
                    storyBiome.biomeId(),
                    storyBiome.targetName(),
                    player.blockPosition(),
                    expiresAt
            );
        }
    }

    private static void rememberDangerousStructureStories(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<DangerousStructureStoryResources.Entry> storyStructures = DangerousStructureStoryResources.entries(level.getServer());
        if (storyStructures.isEmpty()) {
            return;
        }
        /*
         * Do not use vanilla nearest/generated-structure location APIs here.
         * Nearest generated structure scans can synchronously enter worldgen/StructureCheck, and this
         * tick-time story path caused 40+ second integrated-server freezes while players explored.
         * Dangerous structure stories must be driven only by the existing structure-entry detection result.
         */
        VillagerInteractionTracker.StructureVisit visit = VillagerInteractionTracker.currentStructureVisit(
                level,
                player,
                storyStructures.stream().map(DangerousStructureStoryResources.Entry::structureId).toList()
        ).orElse(null);
        if (visit == null) {
            return;
        }
        DangerousStructureStoryResources.Entry storyStructure = storyStructures.stream()
                .filter(entry -> entry.structureId().equals(visit.structureId()))
                .findFirst()
                .orElse(null);
        if (storyStructure == null) {
            return;
        }

        List<Villager> nearbyVillagers = nearbyVillagers(level, player.blockPosition());
        if (nearbyVillagers.isEmpty()) {
            return;
        }
        rememberDangerousStructureStory(level, player, nearbyVillagers, storyStructure, visit);
    }

    private static List<Villager> nearbyVillagers(ServerLevel level, BlockPos origin) {
        return VillageMembership.villagersForLocalVillage(level, origin, DANGEROUS_STORY_VILLAGER_RADIUS);
    }

    private static void rememberDangerousStructureStory(
            ServerLevel level,
            ServerPlayer player,
            List<Villager> nearbyVillagers,
            DangerousStructureStoryResources.Entry storyStructure,
            VillagerInteractionTracker.StructureVisit visit) {
        if (!storyStructure.structureId().equals(visit.structureId())) {
            return;
        }
        BlockPos targetPos = visit.targetPos();

        nearbyVillagers = shareableStoryCandidates(
                level,
                player,
                nearbyVillagers,
                VillagerInteractionTracker.StoryHintKind.STRUCTURE,
                storyStructure.structureId(),
                targetPos
        );
        if (nearbyVillagers.isEmpty()) {
            return;
        }

        long expiresAt = level.getGameTime() + DANGEROUS_STORY_SHARE_TICKS;
        for (Villager villager : nearbyVillagers) {
            VillagerInteractionTracker.rememberShareableStory(
                    level,
                    villager,
                    player,
                    VillagerInteractionTracker.StoryHintKind.STRUCTURE,
                    storyStructure.structureId(),
                    storyStructure.targetName(),
                    targetPos,
                    expiresAt
            );
        }
        LOGGER.debug(
                "Recorded dangerous structure story {} for {} nearby villager(s) from structure-entry detection at {}.",
                storyStructure.structureId(),
                nearbyVillagers.size(),
                targetPos);
    }

    private static List<Villager> shareableStoryCandidates(
            ServerLevel level,
            ServerPlayer player,
            List<Villager> villagers,
            VillagerInteractionTracker.StoryHintKind kind,
            ResourceLocation targetId,
            BlockPos targetPos) {
        return villagers.stream()
                .filter(villager -> VillagerInteractionTracker.canRememberShareableStory(level, villager, player, kind, targetId, targetPos))
                .toList();
    }

    private static ResourceLocation currentBiomeId(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
    }

    private static boolean consumePlayerScanSlot(
            UUID playerId,
            Map<UUID, Long> nextScanTicks,
            long gameTime,
            long intervalTicks
    ) {
        return TickThrottle.consume(playerId, nextScanTicks, gameTime, intervalTicks);
    }

    public static void onVillagerDirectlyDamaged(ServerLevel level, ServerPlayer player, AbstractVillager villager) {
        award(player, HANDS_OFF);
        rememberDirectHit(level, player, villager);
        if (hasWitnesses(level, villager, VILLAGE_HAS_EYES_WITNESSES)) {
            award(player, THE_VILLAGE_HAS_EYES);
        }
    }

    public static void onVillagerEnvironmentalDamage(ServerLevel level, ServerPlayer player, AbstractVillager villager) {
        award(player, HANDS_OFF);
        if (hasWitnesses(level, villager, VILLAGE_HAS_EYES_WITNESSES)) {
            award(player, THE_VILLAGE_HAS_EYES);
        }
    }

    public static void onVillagerDeath(ServerLevel level, AbstractVillager villager, ServerPlayer player, boolean directDamageSource) {
        if (hasWitnesses(level, villager, VILLAGE_HAS_EYES_WITNESSES)) {
            award(player, THE_VILLAGE_HAS_EYES);
        }

        if (!directDamageSource && !hasRecentDirectHit(level, player, villager)) {
            award(player, ACCIDENTALLY_OF_COURSE);
        }
    }

    public static void onIronGolemDamaged(ServerLevel level, ServerPlayer player, IronGolem ironGolem) {
        if (VillageMembership.isVillagePosition(level, ironGolem.blockPosition())) {
            award(player, AN_UNWISE_DECISION);
        }
    }

    public static void onTradeCompleted(ServerLevel level, ServerPlayer player, AbstractVillager villager) {
        UUID playerId = player.getUUID();
        UUID villagerId = villager.getUUID();

        Map<UUID, Integer> playerTradeCounts = TRADE_COUNTS.computeIfAbsent(playerId, ignored -> new HashMap<>());
        int tradeCount = playerTradeCounts.merge(villagerId, 1, Integer::sum);
        TRADED_VILLAGERS.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(villagerId);

        if (tradeCount >= REGULAR_CUSTOMER_TRADES) {
            award(player, REGULAR_CUSTOMER);
        }

        if (villager instanceof Villager villageResident
                && VillageMembership.resolve(level, villageResident).isPresent()
                && countTradedVillagersInVillage(level, villageResident, player) >= COMMUNITY_SUPPORT_VILLAGERS) {
            award(player, COMMUNITY_SUPPORT);
        }
    }

    public static void onTradeRefusedDueToLowReputation(ServerPlayer player) {
        award(player, REFUSED_SERVICE);
    }

    public static void onHeroicDefenseReputationGain(ServerPlayer player) {
        award(player, HERO_NOT_MENACE);
    }

    public static void onPlayerHostilityCheck(ServerLevel level, ServerPlayer player) {
        int hostileCount = countVillagersTargetingPlayer(level, player);
        if (hostileCount >= VILLAGE_ENEMY_HOSTILES) {
            award(player, VILLAGE_ENEMY);
        }
        if (hostileCount >= MOB_JUSTICE_HOSTILES) {
            award(player, MOB_JUSTICE);
        }
    }

    public static boolean hasDistrustedVillagerNearby(ServerLevel level, BlockPos origin, ServerPlayer player) {
        AABB area = AABB.ofSize(origin.getCenter(),
                VillagerRetaliationConfig.WITNESS_RADIUS.get() * 2.0D,
                VillagerRetaliationConfig.WITNESS_RADIUS.get() * 2.0D,
                VillagerRetaliationConfig.WITNESS_RADIUS.get() * 2.0D);

        for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, area)) {
            VillagerReputationLevel levelForPlayer = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
            if (levelForPlayer.trustRank() <= VillagerReputationLevel.SUSPICIOUS.trustRank()) {
                return true;
            }
        }
        return false;
    }

    public static void onVillagePresenceCheck(ServerPlayer player) {
        if (VillageMembership.isVillagePosition(player.serverLevel(), player.blockPosition())) {
            award(player, COMMONFOLK);
        }
    }

    public static void onReputationTierChanged(
            ServerLevel level,
            AbstractVillager villager,
            ServerPlayer player,
            VillagerReputationLevel previousLevel,
            VillagerReputationLevel newLevel) {
        if (crossedInto(previousLevel, newLevel, VillagerReputationLevel.TRUSTED)) {
            award(player, FAMILIAR_FACE);
            if (countTrackedVillagesAtOrAbove(level, player.getUUID(), VillagerReputationLevel.TRUSTED)
                    >= MULTI_VILLAGE_THRESHOLD) {
                award(player, VILLAGE_HOPPER);
            }
            if (hasTradeHistory(player, villager)) {
                award(player, PRICE_OF_TRUST);
            }
            if (villager instanceof Villager villageResident
                    && hasTrustedVillageCore(level, villageResident, player)) {
                award(player, FRIEND_OF_THE_VILLAGE);
            }
        }

        if (crossedInto(previousLevel, newLevel, VillagerReputationLevel.RESPECTED)) {
            award(player, RESPECT_IS_EARNED);
        }
        if (crossedInto(previousLevel, newLevel, VillagerReputationLevel.REVERED)) {
            award(player, LOCAL_LEGEND);
            if (countTrackedVillagesAtOrAbove(level, player.getUUID(), VillagerReputationLevel.REVERED)
                    >= MULTI_VILLAGE_THRESHOLD) {
                award(player, LIVING_LEGEND);
            }
        }
        if (crossedInto(previousLevel, newLevel, VillagerReputationLevel.ROYALTY)) {
            award(player, CROWNED_BY_THE_VILLAGE);
        }

        if (crossedDownInto(previousLevel, newLevel, VillagerReputationLevel.SUSPICIOUS)) {
            award(player, BAD_FIRST_IMPRESSION);
        }

        if (crossedDownInto(previousLevel, newLevel, VillagerReputationLevel.FEARED)) {
            award(player, MARKED);
        }

        if (newLevel.trustRank() <= VillagerReputationLevel.HOSTILE.trustRank()) {
            HOSTILE_OR_WORSE_HISTORY
                    .computeIfAbsent(player.getUUID(), ignored -> new HashSet<>())
                    .add(villager.getUUID());
        }

        if (previousLevel.trustRank() <= VillagerReputationLevel.SUSPICIOUS.trustRank()
                && newLevel.trustRank() >= VillagerReputationLevel.NEUTRAL.trustRank()) {
            award(player, THE_VILLAGE_REMEMBERS);
        }

        if (newLevel.trustRank() >= VillagerReputationLevel.NEUTRAL.trustRank()
            && HOSTILE_OR_WORSE_HISTORY.getOrDefault(player.getUUID(), Set.of()).contains(villager.getUUID())) {
            award(player, PEACE_OFFERING);
        }
    }

    public static void onCuredKnownZombieVillager(ServerPlayer player) {
        award(player, SECOND_CHANCE);
    }

    public static void onVillagerEquipmentChanged(ServerPlayer player, AbstractVillager villager) {
        if (isCoveredInNetherite(villager)) {
            award(player, COVER_THEM_IN_DEBRIS);
        }
    }

    private static boolean crossedInto(
            VillagerReputationLevel previousLevel,
            VillagerReputationLevel newLevel,
            VillagerReputationLevel threshold) {
        return previousLevel.trustRank() < threshold.trustRank()
                && newLevel.trustRank() >= threshold.trustRank();
    }

    private static boolean crossedDownInto(
            VillagerReputationLevel previousLevel,
            VillagerReputationLevel newLevel,
            VillagerReputationLevel threshold) {
        return previousLevel.trustRank() > threshold.trustRank()
                && newLevel.trustRank() <= threshold.trustRank();
    }

    static int countTrackedVillagesAtOrAbove(
            ServerLevel level,
            UUID playerId,
            VillagerReputationLevel threshold) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillagerReputationSavedData reputations = VillagerReputationSavedData.get(level);
        return (int) registry.records().stream()
                .filter(record -> registry.canonical(record.id())
                        .map(record.id()::equals)
                        .orElse(false))
                .filter(record -> record.residents().keySet().stream().anyMatch(villagerId -> {
                    VillagerReputationSavedData.ReputationEntry entry = reputations.get(villagerId, playerId);
                    return entry != null
                            && VillagerReputationLevel.fromReputation(entry.reputation()).trustRank()
                            >= threshold.trustRank();
                }))
                .count();
    }

    private static boolean hasTrustedVillageCore(ServerLevel level, Villager anchor, ServerPlayer player) {
        return VillageMembership.resolve(level, anchor)
                .map(area -> area.countMembers(candidate -> {
                    VillagerReputationLevel candidateLevel =
                            VillagerReputationManager.getReputationLevel(level, candidate, player.getUUID());
                    return candidateLevel.trustRank() >= VillagerReputationLevel.TRUSTED.trustRank();
                }, 5) >= 5)
                .orElse(false);
    }

    private static int countTradedVillagersInVillage(ServerLevel level, Villager anchor, ServerPlayer player) {
        Set<UUID> tradedVillagers = TRADED_VILLAGERS.getOrDefault(player.getUUID(), Set.of());
        if (tradedVillagers.isEmpty()) {
            return 0;
        }

        return VillageMembership.resolve(level, anchor)
                .map(area -> area.countMembers(candidate -> tradedVillagers.contains(candidate.getUUID()), COMMUNITY_SUPPORT_VILLAGERS))
                .orElse(0);
    }

    private static boolean hasWitnesses(ServerLevel level, Entity victim, int requiredWitnesses) {
        AABB witnessArea = victim.getBoundingBox().inflate(VillagerRetaliationConfig.WITNESS_RADIUS.get());
        int witnesses = 0;
        for (Villager witness : level.getEntitiesOfClass(Villager.class, witnessArea)) {
            if (!witness.isAlive() || witness.isBaby() || witness == victim || !witness.hasLineOfSight(victim)) {
                continue;
            }
            if (++witnesses >= requiredWitnesses) {
                return true;
            }
        }
        return false;
    }

    private static int countVillagersTargetingPlayer(ServerLevel level, ServerPlayer player) {
        AABB scanArea = player.getBoundingBox().inflate(HOSTILITY_SCAN_RADIUS);
        int hostileVillagers = 0;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, scanArea)) {
            if (!villager.isAlive() || villager.isBaby()) {
                continue;
            }
            if (villager.getTarget() == player) {
                hostileVillagers++;
            }
        }
        return hostileVillagers;
    }

    private static void rememberDirectHit(ServerLevel level, ServerPlayer player, AbstractVillager villager) {
        RECENT_DIRECT_VILLAGER_HITS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(villager.getUUID(), level.getGameTime());
    }

    private static boolean hasRecentDirectHit(ServerLevel level, ServerPlayer player, AbstractVillager villager) {
        Map<UUID, Long> playerHits = RECENT_DIRECT_VILLAGER_HITS.get(player.getUUID());
        if (playerHits == null) {
            return false;
        }
        Long gameTime = playerHits.get(villager.getUUID());
        return gameTime != null && level.getGameTime() - gameTime <= DIRECT_HIT_MEMORY_TICKS;
    }

    private static boolean isCoveredInNetherite(AbstractVillager villager) {
        return villager.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
                && villager.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
                && villager.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
                && villager.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS);
    }

    private static boolean hasTradeHistory(ServerPlayer player, AbstractVillager villager) {
        return TRADE_COUNTS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(villager.getUUID(), 0) > 0;
    }

    private static void award(ServerPlayer player, ResourceLocation advancementId) {
        if (!advancementId.equals(ROOT)) {
            awardDirect(player, ROOT);
        }
        awardDirect(player, advancementId);
    }

    private static void awardCriterion(
            ServerPlayer player,
            ResourceLocation advancementId,
            String criterion) {
        if (!advancementId.equals(ROOT)) {
            awardDirect(player, ROOT);
        }
        AdvancementHolder advancement = player.server.getAdvancements().get(advancementId);
        if (advancement == null) {
            return;
        }
        player.getAdvancements().award(advancement, criterion);
    }

    private static void awardDirect(ServerPlayer player, ResourceLocation advancementId) {
        AdvancementHolder advancement = player.server.getAdvancements().get(advancementId);
        if (advancement == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        List<String> remainingCriteria = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remainingCriteria::add);
        for (String criterion : remainingCriteria) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private static ResourceLocation advancementId(String path) {
        return VillagerRetaliation.id(path);
    }

    private static void sendDialogueMapFoundNotice(ServerPlayer player, VillagerInteractionTracker.CartographerMapReport discovery) {
        ServerLevel level = player.serverLevel();
        String targetName = discovery.targetName() == null || discovery.targetName().isBlank()
                ? VillagerInteractionTextUtil.resourcePathName(discovery.structureId())
                : discovery.targetName();
        String fallbackText = "Found map destination: " + targetName;
        Entity entity = level.getEntity(discovery.villagerId());
        if (entity instanceof AbstractVillager villager) {
            VillagerNotifications.sendHud(
                    player,
                    level,
                    villager,
                    "dialogue.map.found",
                    VillagerNotifications.replacements("target", targetName),
                    fallbackText,
                    VillagerReputationNoticeKind.MAP_DISCOVERY
            );
            return;
        }
        VillagerReputationNetworking.sendNotice(player, fallbackText, VillagerReputationNoticeKind.MAP_DISCOVERY);
    }

    private static void sendStoryHintFoundNotice(ServerPlayer player, VillagerInteractionTracker.StoryHintReport discovery) {
        ServerLevel level = player.serverLevel();
        String targetName = discovery.targetName() == null || discovery.targetName().isBlank()
                ? VillagerInteractionTextUtil.resourcePathName(discovery.targetId())
                : discovery.targetName();
        String fallbackText = "Found rumored place: " + targetName;
        Entity entity = level.getEntity(discovery.villagerId());
        if (entity instanceof AbstractVillager villager) {
            VillagerNotifications.sendHud(
                    player,
                    level,
                    villager,
                    "dialogue.rumor.found",
                    VillagerNotifications.replacements("target", targetName),
                    fallbackText,
                    VillagerReputationNoticeKind.MAP_DISCOVERY
            );
            return;
        }
        VillagerReputationNetworking.sendNotice(player, fallbackText, VillagerReputationNoticeKind.MAP_DISCOVERY);
    }

}
