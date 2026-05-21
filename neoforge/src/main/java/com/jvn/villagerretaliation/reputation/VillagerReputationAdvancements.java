package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class VillagerReputationAdvancements {
    private static final double VILLAGE_TRUST_SCAN_RADIUS = 64.0D;
    private static final int VILLAGE_HAS_EYES_WITNESSES = 3;
    private static final int REGULAR_CUSTOMER_TRADES = 10;
    private static final int COMMUNITY_SUPPORT_VILLAGERS = 5;
    private static final int VILLAGE_ENEMY_HOSTILES = 5;
    private static final int MOB_JUSTICE_HOSTILES = 8;
    private static final long DIRECT_HIT_MEMORY_TICKS = 20L * 40L;
    private static final double HOSTILITY_SCAN_RADIUS = 64.0D;
    private static final double DIALOGUE_MAP_FOUND_RADIUS = 64.0D;

    private static final Map<UUID, Map<UUID, Integer>> TRADE_COUNTS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> TRADED_VILLAGERS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> RECENT_DIRECT_VILLAGER_HITS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> HOSTILE_OR_WORSE_HISTORY = new HashMap<>();

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

    private VillagerReputationAdvancements() {
    }

    public static void onVillagerInteraction(ServerPlayer player) {
        award(player, COMMONFOLK);
    }

    public static void onVillagerPacified(ServerPlayer player) {
        award(player, IM_SORRY);
    }

    public static void onLuredVillagerKilled(ServerPlayer player) {
        award(player, BAIT_AND_BETRAYAL);
    }

    public static void onSleepingVillagerBedBroken(ServerPlayer player) {
        award(player, NO_REST_FOR_THE_WICKED);
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (VillagerInteractionTracker.markCartographerMapDiscoveriesNear(player.serverLevel(), player, DIALOGUE_MAP_FOUND_RADIUS)) {
            award(player, TRUSTED_DIRECTIONS);
        }
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
        if (level.isVillage(ironGolem.blockPosition())) {
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
                && level.isVillage(villageResident.blockPosition())
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
        if (player.serverLevel().isVillage(player.blockPosition())) {
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

    private static boolean hasTrustedVillageCore(ServerLevel level, Villager anchor, ServerPlayer player) {
        if (!level.isVillage(anchor.blockPosition())) {
            return false;
        }

        AABB searchArea = anchor.getBoundingBox().inflate(VILLAGE_TRUST_SCAN_RADIUS);
        int trustedVillagers = 0;
        for (Villager candidate : level.getEntitiesOfClass(Villager.class, searchArea)) {
            if (!candidate.isAlive() || !level.isVillage(candidate.blockPosition())) {
                continue;
            }

            VillagerReputationLevel candidateLevel = VillagerReputationManager.getReputationLevel(level, candidate, player.getUUID());
            if (candidateLevel.trustRank() >= VillagerReputationLevel.TRUSTED.trustRank() && ++trustedVillagers >= 5) {
                return true;
            }
        }
        return false;
    }

    private static int countTradedVillagersInVillage(ServerLevel level, Villager anchor, ServerPlayer player) {
        Set<UUID> tradedVillagers = TRADED_VILLAGERS.getOrDefault(player.getUUID(), Set.of());
        if (tradedVillagers.isEmpty()) {
            return 0;
        }

        AABB searchArea = anchor.getBoundingBox().inflate(VILLAGE_TRUST_SCAN_RADIUS);
        int tradedInVillage = 0;
        for (Villager candidate : level.getEntitiesOfClass(Villager.class, searchArea)) {
            if (!candidate.isAlive() || !level.isVillage(candidate.blockPosition())) {
                continue;
            }
            if (tradedVillagers.contains(candidate.getUUID()) && ++tradedInVillage >= COMMUNITY_SUPPORT_VILLAGERS) {
                return tradedInVillage;
            }
        }
        return tradedInVillage;
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

}
