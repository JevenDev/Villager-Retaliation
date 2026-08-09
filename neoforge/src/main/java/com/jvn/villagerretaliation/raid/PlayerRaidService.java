package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.allegiance.VillageLifecycleState;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.item.BannerHelmetData;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owns activation and reconciliation of player-created village raids. */
public final class PlayerRaidService {
    private static final long DAY_TICKS = 24_000L;
    private static final long OUTCOME_DISPLAY_TICKS = 100L;
    private static final double HORN_REVEAL_RADIUS = 48.0D;
    private static final int HORN_REVEAL_TICKS = 60;
    private static final float RAID_VICTORY_HORN_VOLUME = 1.2F;
    private static final float RAID_VICTORY_HORN_PITCH = 0.85F;
    private static final float RAID_VICTORY_FANFARE_VOLUME = 0.8F;
    private static final float RAID_VICTORY_FANFARE_PITCH = 1.0F;
    private static final int RAID_WIN_GUTS_CHANGE = 10;
    private static final int RAID_LOSS_GUTS_CHANGE = -5;
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();
    private static final PlayerRaidConfirmationTracker RAID_CONFIRMATIONS = new PlayerRaidConfirmationTracker();

    private PlayerRaidService() {
    }

    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getItem().is(Items.GOAT_HORN)
                || !BannerHelmetData.hasAttachedBanner(player.getItemBySlot(EquipmentSlot.HEAD))) {
            return;
        }
        if (tryRevealDefenders(player)) return;
        tryDeclare(player);
    }

    static boolean tryRevealDefenders(ServerPlayer player) {
        PlayerRaidSavedData.RaidRecord raid = PlayerRaidSavedData.get(player.serverLevel())
                .activeForParticipant(player.getUUID());
        if (raid == null
                || (raid.phase() != PlayerRaidSavedData.Phase.ACTIVE
                    && raid.phase() != PlayerRaidSavedData.Phase.MERCY)
                || !raid.raiderPlayers().contains(player.getUUID())
                || !raid.dimension().equals(player.serverLevel().dimension().location())) {
            return false;
        }
        AABB revealArea = player.getBoundingBox().inflate(HORN_REVEAL_RADIUS);
        int revealed = 0;
        Set<UUID> revealTargets = raid.phase() == PlayerRaidSavedData.Phase.MERCY
                ? raid.mercyCandidates()
                : raid.defenders();
        for (UUID defenderId : revealTargets) {
            Entity entity = player.serverLevel().getEntity(defenderId);
            if (entity instanceof Villager villager && villager.isAlive() && revealArea.contains(villager.position())) {
                villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, HORN_REVEAL_TICKS));
                revealed++;
            }
        }
        boolean mercy = raid.phase() == PlayerRaidSavedData.Phase.MERCY;
        String messageKey = mercy
                ? revealed == 0
                        ? "villagerretaliation.player_raid.horn_reveal_mercy_none"
                        : "villagerretaliation.player_raid.horn_reveal_mercy"
                : revealed == 0
                        ? "villagerretaliation.player_raid.horn_reveal_none"
                        : "villagerretaliation.player_raid.horn_reveal";
        player.sendSystemMessage(revealed == 0
                ? Component.translatable(messageKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                : Component.translatable(messageKey, revealed).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return true;
    }

    public static boolean tryDeclare(ServerPlayer initiator) {
        if (initiator == null || !VillagerRetaliationConfig.ENABLE_PLAYER_RAIDS.get()) {
            return false;
        }
        ServerLevel level = initiator.serverLevel();
        BlockPos position = initiator.blockPosition();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId villageId = registry.discoverAt(level, position).orElse(null);
        VillageAllegianceRegistrySavedData.AllegianceRecord village =
                villageId == null ? null : registry.canonicalRecord(villageId).orElse(null);
        if (!isRaidableVillage(village) || !village.footprintSections().contains(SectionPos.asLong(position))) {
            return false;
        }
        villageId = village.id();
        long now = level.getServer().overworld().getGameTime();
        PlayerRaidSavedData data = PlayerRaidSavedData.get(level);
        boolean overlappingPlayerRaid = data.raids().stream().anyMatch(raid -> raid.running()
                && raid.dimension().equals(level.dimension().location())
                && raid.footprint().stream().anyMatch(village.footprintSections()::contains));
        if (level.getRaidAt(position) != null
                || data.activeAt(villageId) != null
                || overlappingPlayerRaid
                || data.activeForParticipant(initiator.getUUID()) != null) {
            initiator.sendSystemMessage(Component.translatable("villagerretaliation.player_raid.unavailable"));
            return false;
        }
        if (data.cooldownUntil(registry, villageId) > now) {
            initiator.sendSystemMessage(Component.translatable("villagerretaliation.player_raid.cooldown"));
            return false;
        }

        List<Villager> loadedResidents = loadedVillageVillagers(level, villageId);
        Map<UUID, Villager> loadedById = new HashMap<>();
        loadedResidents.forEach(villager -> loadedById.put(villager.getUUID(), villager));
        Set<UUID> defenders = new LinkedHashSet<>();
        Set<UUID> mercyCandidates = new LinkedHashSet<>();
        Set<UUID> babyMercyCandidates = new LinkedHashSet<>();
        for (VillageAllegianceRegistrySavedData.ResidentRecord resident : village.activeResidents(now)) {
            Villager loaded = loadedById.get(resident.id());
            if (loaded != null) classifyResident(loaded, defenders, mercyCandidates, babyMercyCandidates);
            else classifyResident(resident, defenders, mercyCandidates, babyMercyCandidates);
        }
        for (Villager loaded : loadedResidents) {
            defenders.remove(loaded.getUUID());
            mercyCandidates.remove(loaded.getUUID());
            babyMercyCandidates.remove(loaded.getUUID());
            classifyResident(loaded, defenders, mercyCandidates, babyMercyCandidates);
        }
        if (defenders.isEmpty() && mercyCandidates.isEmpty()) {
            initiator.sendSystemMessage(Component.translatable("villagerretaliation.player_raid.no_defenders"));
            return false;
        }

        PartyRecord party = PartyService.getPartyForPlayer(level, initiator.getUUID()).orElse(null);
        Set<UUID> raiderPlayers = new LinkedHashSet<>();
        Set<UUID> raiderVillagers = new LinkedHashSet<>();
        Set<UUID> defectors = new LinkedHashSet<>();
        if (party == null) {
            raiderPlayers.add(initiator.getUUID());
        } else {
            raiderPlayers.addAll(party.playerIds());
            for (PartyVillagerRecord member : party.villagers()) {
                if (village.residents().containsKey(member.villagerId())) {
                    defectors.add(member.villagerId());
                    if (!defenders.contains(member.villagerId()) && !mercyCandidates.contains(member.villagerId())) {
                        Villager loaded = loadedById.get(member.villagerId());
                        VillageAllegianceRegistrySavedData.ResidentRecord resident =
                                village.residents().get(member.villagerId());
                        if (loaded != null) classifyResident(loaded, defenders, mercyCandidates, babyMercyCandidates);
                        else classifyResident(resident, defenders, mercyCandidates, babyMercyCandidates);
                    }
                }
                else raiderVillagers.add(member.villagerId());
            }
        }
        Set<UUID> participants = new LinkedHashSet<>(raiderPlayers);
        participants.addAll(raiderVillagers);
        participants.addAll(defenders);
        participants.addAll(mercyCandidates);
        for (UUID participant : participants) {
            if (data.activeForParticipant(participant) != null) {
                initiator.sendSystemMessage(Component.translatable("villagerretaliation.player_raid.party_busy"));
                return false;
            }
        }
        if (VillagerRetaliationConfig.CONFIRM_RAID_HORN.get()
                && !RAID_CONFIRMATIONS.consumeOrArm(initiator.getUUID(), villageId, now)) {
            initiator.sendSystemMessage(Component.translatable("villagerretaliation.player_raid.horn_confirmation")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return false;
        }

        PlayerRaidSavedData.RaidRecord raid = data.create(
                villageId, level.dimension().location(), village.center(), village.footprintSections(),
                village.displayName(), initiator.getUUID(), party == null ? null : party.id(),
                raiderPlayers, raiderVillagers, defenders, mercyCandidates, babyMercyCandidates, defectors, now);
        Set<UUID> villageWitnesses = new LinkedHashSet<>(village.residents().keySet());
        villageWitnesses.addAll(defenders);
        villageWitnesses.addAll(mercyCandidates);
        applyBetrayal(level, raid, villageWitnesses);
        defectors.forEach(id -> PartyVillagerContractService.releaseForHomeVillageRaid(level.getServer(), id));
        initiator.sendSystemMessage(Component.translatable(
                "villagerretaliation.player_raid.declared",
                village.displayName(), defenders.size() + mercyCandidates.size()));
        VillagerReputationAdvancements.onPlayerRaidDeclared(initiator);
        if (!PlayerRaidDialogueService.begin(initiator, raid)) {
            beginPreparation(level.getServer(), raid.id());
        }
        return true;
    }

    public static void beginPreparation(MinecraftServer server, UUID raidId) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(server.overworld());
        PlayerRaidSavedData.RaidRecord raid = data.raid(raidId);
        if (raid == null || raid.phase() != PlayerRaidSavedData.Phase.DECLARATION) return;
        PlayerRaidDialogueService.endSessionsForRaid(server, raidId);
        long now = server.overworld().getGameTime();
        raid.setPhase(PlayerRaidSavedData.Phase.PREPARING, now);
        raid.setAbsenceStarted(-1L);
        prepareDefenders(server, data, raid);
        data.changed();
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 5L != 0L) return;
        RAID_CONFIRMATIONS.pruneExpired(now);
        PlayerRaidDialogueService.reconcile(server);
        PlayerRaidMercyService.reconcile(server);
        PlayerRaidSavedData data = PlayerRaidSavedData.get(server.overworld());
        for (PlayerRaidSavedData.RaidRecord raid : new ArrayList<>(data.raids())) {
            tickRaid(server, data, raid, now);
        }
    }

    static void tickRaid(
            MinecraftServer server, PlayerRaidSavedData data, PlayerRaidSavedData.RaidRecord raid, long now) {
        ServerLevel level = level(server, raid);
        if (!raid.running()) {
            if (level != null) updateBossBar(server, raid, now);
            if (raid.outcomeCleanupAt() > 0L && now >= raid.outcomeCleanupAt()) {
                hideBossBar(raid.id());
                data.remove(raid.id());
            }
            return;
        }
        if (level == null) {
            finish(server, data, raid, false, now);
            return;
        }
        updateBossBar(server, raid, now);
        suppressVanillaRaidOverlap(level, raid);
        if (raid.phase() == PlayerRaidSavedData.Phase.DECLARATION) {
            if ((!PlayerRaidDialogueService.hasSession(raid.id()) && now - raid.phaseStarted() >= 20L)
                    || now - raid.phaseStarted() >= 1_200L) beginPreparation(server, raid.id());
            return;
        }
        if (hasPresentRaiderPlayer(server, raid)) {
            if (raid.absenceStarted() != -1L) {
                raid.setAbsenceStarted(-1L);
                data.changed();
            }
        } else if (raid.absenceStarted() == -1L) {
            raid.setAbsenceStarted(now);
            data.changed();
        } else if (now - raid.absenceStarted() >= VillagerRetaliationConfig.PLAYER_RAID_ABANDONMENT_TICKS.get()) {
            finish(server, data, raid, false, now);
            return;
        }
        if (raid.phase() == PlayerRaidSavedData.Phase.PREPARING) {
            if (now % 20L == 0L) prepareDefenders(server, data, raid);
            if (now - raid.phaseStarted() >= VillagerRetaliationConfig.PLAYER_RAID_PREPARATION_TICKS.get()) {
                activate(level, data, raid, now);
            }
            return;
        }
        if (raid.phase() == PlayerRaidSavedData.Phase.MERCY) {
            if (raid.mercyCandidates().isEmpty()) finish(server, data, raid, true, now);
            else PlayerRaidMercyService.tick(server, data, raid, now);
            return;
        }
        reclassifyLoadedNoncombatants(server, data, raid);
        if (resolveDefenderObjective(server, data, raid, now)) return;
        if (VillagerRetaliationConfig.HIGHLIGHT_RAID_DEFENDERS.get() && now % 20L == 0L) {
            highlightTrackedDefenders(server, raid);
        }
        reconcileCombat(server, raid);
        reconcileGolemMilestones(level, data, raid);
    }

    private static void activate(
            ServerLevel level, PlayerRaidSavedData data, PlayerRaidSavedData.RaidRecord raid, long now) {
        reclassifyLoadedNoncombatants(level.getServer(), data, raid);
        if (resolveDefenderObjective(level.getServer(), data, raid, now)) return;
        int raiderPlayers = raid.raiderPlayers().size();
        raid.setGolemBudget(calculateGolemBudget(
                raid.initialDefenderCount(), raiderPlayers, existingAlignedGolems(level, raid),
                VillagerRetaliationConfig.PLAYER_RAID_DEFENDERS_PER_GOLEM.get(),
                VillagerRetaliationConfig.PLAYER_RAID_RAIDERS_PER_BONUS_GOLEM.get(),
                VillagerRetaliationConfig.PLAYER_RAID_MINIMUM_GOLEMS.get(),
                VillagerRetaliationConfig.PLAYER_RAID_MAXIMUM_GOLEMS.get()));
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, now);
        data.changed();
        reconcileGolemMilestones(level, data, raid);
        reconcileCombat(level.getServer(), raid);
    }

    private static void prepareDefenders(
            MinecraftServer server,
            PlayerRaidSavedData data,
            PlayerRaidSavedData.RaidRecord raid) {
        ServerLevel level = level(server, raid);
        if (level == null) return;
        reclassifyLoadedNoncombatants(server, data, raid);
        for (UUID defenderId : raid.defenders()) {
            Entity entity = find(server, defenderId);
            if (!(entity instanceof Villager villager) || villager.level() != level) continue;
            if (!raid.mercyEnabled()
                    && (villager.isBaby()
                        || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT)) {
                hideVillager(level, villager);
            } else {
                PlayerRaidLoadoutService.equip(villager, raid.id());
            }
        }
        for (UUID candidateId : raid.mercyCandidates()) {
            Entity entity = find(server, candidateId);
            if (entity instanceof Villager villager && villager.level() == level) hideVillager(level, villager);
        }
    }

    private static void hideVillager(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
        BlockPos hiding = villager.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(home -> home.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .or(() -> level.getPoiManager().findClosest(
                        type -> type.is(PoiTypes.HOME), villager.blockPosition(), 48, PoiManager.Occupancy.ANY))
                .orElse(villager.blockPosition());
        villager.getBrain().setMemory(MemoryModuleType.HIDING_PLACE, GlobalPos.of(level.dimension(), hiding));
        villager.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.HIDE);
    }

    private static void reclassifyLoadedNoncombatants(
            MinecraftServer server,
            PlayerRaidSavedData data,
            PlayerRaidSavedData.RaidRecord raid) {
        if (!raid.mercyEnabled()) return;
        boolean changed = false;
        for (UUID defenderId : raid.defenders()) {
            Entity entity = find(server, defenderId);
            if (!(entity instanceof Villager villager)
                    || (!villager.isBaby()
                        && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT)) {
                continue;
            }
            PlayerRaidSavedData.MercyKind kind = villager.isBaby()
                    ? PlayerRaidSavedData.MercyKind.BABY
                    : PlayerRaidSavedData.MercyKind.NITWIT;
            if (raid.reclassifyDefenderAsMercyCandidate(defenderId, kind)) {
                hideVillager((ServerLevel) villager.level(), villager);
                changed = true;
            }
        }
        if (changed) data.changed();
    }

    private static void enterMercy(
            MinecraftServer server,
            PlayerRaidSavedData data,
            PlayerRaidSavedData.RaidRecord raid,
            long now) {
        raid.setPhase(PlayerRaidSavedData.Phase.MERCY, now);
        releaseRaidCombatState(server, raid);
        PlayerRaidMercyService.initialize(server, data, raid, now);
        data.changed();
    }

    static boolean resolveDefenderObjective(
            MinecraftServer server,
            PlayerRaidSavedData data,
            PlayerRaidSavedData.RaidRecord raid,
            long now) {
        if (!raid.defenders().isEmpty()) return false;
        if (raid.mercyCandidates().isEmpty()) finish(server, data, raid, true, now);
        else enterMercy(server, data, raid, now);
        return true;
    }

    static void releaseMercyCandidate(MinecraftServer server, Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        VillagerRetaliationHandler.clearCustomTarget(villager);
        villager.setAggressive(false);
        VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
    }

    static void releaseRaidCombatState(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        for (UUID raiderId : raid.raiderVillagers()) {
            Entity entity = find(server, raiderId);
            if (entity instanceof Villager villager) VillagerRetaliationHandler.clearCustomTarget(villager);
        }
        for (UUID defenderId : raid.defenders()) {
            Entity entity = find(server, defenderId);
            if (entity instanceof Villager villager) VillagerRetaliationHandler.clearCustomTarget(villager);
        }
        for (UUID candidateId : raid.mercyCandidates()) {
            Entity entity = find(server, candidateId);
            if (entity instanceof Villager villager) releaseMercyCandidate(server, villager);
        }
        ServerLevel level = level(server, raid);
        if (level == null) return;
        AABB area = AABB.ofSize(Vec3.atCenterOf(raid.center()), 192.0D, 96.0D, 192.0D);
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, area, IronGolem::isAlive)) {
            LivingEntity target = golem.getTarget();
            UUID persistentTarget = golem.getPersistentAngerTarget();
            boolean targetingRaider = target != null && isRaidRaider(raid, target.getUUID());
            boolean angryAtRaider = isRaidRaider(raid, persistentTarget);
            if ((targetingRaider || angryAtRaider) && isDefendingGolem(level, raid, golem)) {
                golem.stopBeingAngry();
            }
        }
    }

    private static boolean isRaidRaider(PlayerRaidSavedData.RaidRecord raid, UUID entityId) {
        return entityId != null
                && (raid.raiderPlayers().contains(entityId) || raid.raiderVillagers().contains(entityId));
    }

    static void completeMercyIfResolved(MinecraftServer server, UUID raidId) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(server.overworld());
        PlayerRaidSavedData.RaidRecord raid = data.raid(raidId);
        if (raid != null
                && raid.phase() == PlayerRaidSavedData.Phase.MERCY
                && raid.mercyCandidates().isEmpty()) {
            finish(server, data, raid, true, server.overworld().getGameTime());
        }
    }

    public static boolean shouldHandleMercyInteraction(
            Villager villager,
            ServerPlayer player,
            InteractionHand hand) {
        return PlayerRaidMercyService.shouldHandleInteraction(villager, player, hand);
    }

    public static InteractionResult handleMercyInteraction(Villager villager, ServerPlayer player) {
        return PlayerRaidMercyService.openVerdict(player, villager);
    }

    static void reconcileCombat(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        List<LivingEntity> raiders = livingRaiders(server, raid);
        List<LivingEntity> defenders = livingDefenders(server, raid);
        ServerLevel level = level(server, raid);
        List<IronGolem> defendingGolems = level == null ? List.of() : livingDefendingGolems(level, raid);
        defenders.addAll(defendingGolems);
        for (LivingEntity defender : defenders) {
            LivingEntity target = retainedOrNearest(defender, raiders);
            if (target == null) continue;
            if (defender instanceof Villager villager && !villager.isBaby()
                    && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT) {
                PlayerRaidLoadoutService.equip(villager, raid.id());
                VillagerRetaliationHandler.forceAngerSilently(villager, target);
            } else if (defender instanceof Villager villager) {
                hideVillager((ServerLevel) villager.level(), villager);
            }
        }
        for (LivingEntity raider : raiders) {
            LivingEntity target = retainedOrNearest(raider, defenders);
            if (target == null) continue;
            if (raider instanceof Villager villager) VillagerRetaliationHandler.forceAngerSilently(villager, target);
        }
        if (level != null) {
            for (IronGolem golem : defendingGolems) {
                LivingEntity target = nearest(golem, raiders);
                if (target != null) golem.setTarget(target);
            }
        }
    }

    private static void reconcileGolemMilestones(
            ServerLevel level, PlayerRaidSavedData data, PlayerRaidSavedData.RaidRecord raid) {
        if (raid.golemBudget() <= 0) return;
        float remaining = raid.defenders().size() / (float) Math.max(1, raid.initialDefenderCount());
        int reached = remaining <= 0.25F ? 4 : remaining <= 0.5F ? 3 : remaining <= 0.75F ? 2 : 1;
        int targetSpawned = (int) Math.ceil(raid.golemBudget() * reached / 4.0D);
        int missing = Math.max(0, targetSpawned - raid.golemsSpawned());
        if (missing == 0) return;
        int spawned = 0;
        for (int i = 0; i < missing; i++) if (spawnGolem(level, raid)) spawned++;
        if (spawned > 0) {
            raid.addSpawnedGolems(spawned);
            raid.markMilestone(1 << (reached - 1));
            data.changed();
        }
    }

    private static boolean spawnGolem(ServerLevel level, PlayerRaidSavedData.RaidRecord raid) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = level.random.nextInt(33) - 16;
            int dz = level.random.nextInt(33) - 16;
            BlockPos sample = raid.center().offset(dx, 0, dz);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample);
            if (!raid.footprint().contains(SectionPos.asLong(pos)) || nearRaider(level.getServer(), raid, pos, 8.0D)) continue;
            IronGolem golem = EntityType.IRON_GOLEM.create(level);
            if (golem == null) return false;
            golem.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            if (!level.noCollision(golem)) { golem.discard(); continue; }
            golem.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            VillageAllegianceApi.assignKnown(level, golem, raid.villageId(), AllegianceAssignmentSource.EXPLICIT_API);
            golem.setPersistenceRequired();
            if (level.addFreshEntity(golem)) return true;
        }
        return false;
    }

    private static void applyBetrayal(
            ServerLevel level, PlayerRaidSavedData.RaidRecord raid, Set<UUID> villageWitnesses) {
        for (UUID defender : villageWitnesses) {
            for (UUID raider : raid.raiderPlayers()) {
                int current = VillagerReputationManager.getReputation(level, defender, raider);
                int changed = betrayalReputation(current);
                VillagerReputationManager.setReputation(level, defender, raider, changed, raid.center());
            }
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        removeDefender(level, event.getEntity().getUUID());
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level) removeDefender(level, event.getEntity().getUUID());
    }

    /** A protected villager who is downed has been neutralized for the current raid objective. */
    public static void onVillagerDowned(Villager villager) {
        if (villager != null && villager.level() instanceof ServerLevel level) {
            VillagerRetaliationHandler.clearCustomTarget(villager);
            removeDefender(level, villager.getUUID());
        }
    }

    /** Covers permanent removals which do not emit a usable living-death event, such as explicit discards. */
    public static void onEntityPermanentlyRemoved(Entity entity) {
        if (entity != null && entity.level() instanceof ServerLevel level) {
            removeDefender(level, entity.getUUID());
        }
    }

    private static void removeDefender(ServerLevel level, UUID id) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(level);
        for (PlayerRaidSavedData.RaidRecord raid : data.raids()) {
            if (!raid.running()) continue;
            boolean removed = raid.removeDefender(id) | raid.removeMercyCandidate(id);
            if (!removed) continue;
            data.changed();
            if (raid.phase() == PlayerRaidSavedData.Phase.MERCY && raid.mercyCandidates().isEmpty()) {
                finish(level.getServer(), data, raid, true, level.getServer().overworld().getGameTime());
            } else if (raid.phase() == PlayerRaidSavedData.Phase.ACTIVE && raid.defenders().isEmpty()) {
                resolveDefenderObjective(
                        level.getServer(), data, raid, level.getServer().overworld().getGameTime());
            }
        }
    }

    private static void finish(
            MinecraftServer server, PlayerRaidSavedData data, PlayerRaidSavedData.RaidRecord raid,
            boolean raidersWon, long now) {
        releaseRaidCombatState(server, raid);
        adjustRaiderVillagerGuts(server, raid, raidersWon ? RAID_WIN_GUTS_CHANGE : RAID_LOSS_GUTS_CHANGE);
        PlayerRaidDialogueService.endSessionsForRaid(server, raid.id());
        PlayerRaidMercyService.onRaidFinished(server, raid.id());
        raid.setPhase(raidersWon ? PlayerRaidSavedData.Phase.RAIDER_VICTORY : PlayerRaidSavedData.Phase.DEFENDER_VICTORY, now);
        raid.setOutcomeCleanupAt(now + OUTCOME_DISPLAY_TICKS);
        long cooldown = Math.max(0, VillagerRetaliationConfig.PLAYER_RAID_VILLAGE_COOLDOWN_DAYS.get()) * DAY_TICKS;
        data.setCooldown(raid.villageId(), now + cooldown);
        data.changed();
        Component message = Component.translatable(raidersWon
                ? "villagerretaliation.player_raid.victory"
                : "villagerretaliation.player_raid.defended", raid.villageName());
        server.getPlayerList().broadcastSystemMessage(message, false);
        if (raidersWon) {
            for (UUID raiderId : raid.raiderPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(raiderId);
                if (player != null) {
                    VillagerReputationAdvancements.onPlayerRaidWon(player);
                }
            }
            playRaiderVictorySound(server, raid);
        }
        PlayerRaidDialogueService.announceOutcome(server, raid, raidersWon);
    }

    private static void adjustRaiderVillagerGuts(
            MinecraftServer server, PlayerRaidSavedData.RaidRecord raid, int change) {
        ServerLevel raidLevel = level(server, raid);
        if (raidLevel == null) return;
        VillagerProfileSavedData profiles = VillagerProfileSavedData.get(raidLevel);
        boolean changed = false;
        for (UUID villagerId : raid.raiderVillagers()) {
            VillagerProfile profile = VillagerProfileManager.getProfile(raidLevel, villagerId).orElse(null);
            if (profile == null) {
                Entity entity = find(server, villagerId);
                if (entity instanceof Villager villager && villager.level() instanceof ServerLevel villagerLevel) {
                    profile = VillagerProfileManager.getOrCreateProfile(villagerLevel, villager);
                }
            }
            if (profile != null) {
                changed |= profile.setSocialAttribute(
                        VillagerSocialAttribute.GUTS,
                        profile.socialAttributes().guts() + change, raidLevel.getGameTime());
            }
        }
        if (changed) profiles.setDirty();
    }

    private static void playRaiderVictorySound(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        for (UUID raiderId : raid.raiderPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(raiderId);
            if (player != null) {
                player.serverLevel().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.GOAT_HORN_PLAY,
                        SoundSource.PLAYERS,
                        RAID_VICTORY_HORN_VOLUME,
                        RAID_VICTORY_HORN_PITCH);
                player.serverLevel().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.PLAYERS,
                        RAID_VICTORY_FANFARE_VOLUME,
                        RAID_VICTORY_FANFARE_PITCH);
            }
        }
    }

    /** Operator hook used by the debug command to settle the relevant running Player Raid. */
    public static PlayerRaidSavedData.RaidRecord debugFinishRaid(
            ServerLevel sourceLevel, BlockPos sourcePosition, UUID participant, boolean raidersWon) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(sourceLevel);
        PlayerRaidSavedData.RaidRecord raid = participant == null ? null : data.activeForParticipant(participant);
        if (raid == null) {
            long sourceSection = SectionPos.asLong(sourcePosition);
            raid = data.raids().stream()
                    .filter(PlayerRaidSavedData.RaidRecord::running)
                    .filter(candidate -> candidate.dimension().equals(sourceLevel.dimension().location()))
                    .filter(candidate -> candidate.footprint().contains(sourceSection))
                    .findFirst()
                    .orElse(null);
        }
        if (raid == null) return null;
        finish(sourceLevel.getServer(), data, raid, raidersWon,
                sourceLevel.getServer().overworld().getGameTime());
        return raid;
    }

    private static void updateBossBar(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid, long now) {
        if (raid.phase() == PlayerRaidSavedData.Phase.DECLARATION) {
            hideBossBar(raid.id());
            return;
        }
        ServerBossEvent bar = BOSS_BARS.computeIfAbsent(raid.id(), ignored -> new ServerBossEvent(
                Component.translatable("villagerretaliation.player_raid.title", raid.villageName()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
        float progress;
        if (raid.phase() == PlayerRaidSavedData.Phase.PREPARING) {
            progress = Math.min(1.0F, (now - raid.phaseStarted())
                    / (float) Math.max(1, VillagerRetaliationConfig.PLAYER_RAID_PREPARATION_TICKS.get()));
            bar.setName(Component.translatable("villagerretaliation.player_raid.preparing", raid.villageName()));
        } else if (raid.phase() == PlayerRaidSavedData.Phase.RAIDER_VICTORY) {
            progress = 0.0F;
            bar.setName(Component.translatable("villagerretaliation.player_raid.bar_victory", raid.villageName()));
        } else if (raid.phase() == PlayerRaidSavedData.Phase.DEFENDER_VICTORY) {
            progress = raid.defenders().size() / (float) Math.max(1, raid.initialDefenderCount());
            bar.setName(Component.translatable("villagerretaliation.player_raid.bar_defended", raid.villageName()));
        } else if (raid.phase() == PlayerRaidSavedData.Phase.MERCY) {
            progress = raid.mercyCandidates().size() / (float) Math.max(1, raid.initialMercyCandidateCount());
            bar.setName(Component.translatable(
                    "villagerretaliation.player_raid.mercy_remaining",
                    raid.villageName(),
                    raid.mercyCandidates().size()));
        } else {
            progress = raid.defenders().size() / (float) Math.max(1, raid.initialDefenderCount());
            bar.setName(Component.translatable("villagerretaliation.player_raid.remaining", raid.villageName(), raid.defenders().size()));
        }
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));
        for (ServerPlayer viewer : new ArrayList<>(bar.getPlayers())) {
            if (!canSeeBossBar(viewer, raid)) bar.removePlayer(viewer);
        }
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (canSeeBossBar(viewer, raid)) bar.addPlayer(viewer);
        }
        bar.setVisible(true);
    }

    private static boolean canSeeBossBar(ServerPlayer player, PlayerRaidSavedData.RaidRecord raid) {
        if (player.isSpectator() || !player.serverLevel().dimension().location().equals(raid.dimension())) return false;
        int sections = (int) Math.ceil(VillagerRetaliationConfig.PLAYER_RAID_BOSS_BAR_RANGE.get() / 16.0D);
        SectionPos playerSection = SectionPos.of(player.blockPosition());
        for (long packed : raid.footprint()) {
            if (Math.abs(playerSection.x() - SectionPos.x(packed)) <= sections
                    && Math.abs(playerSection.y() - SectionPos.y(packed)) <= sections
                    && Math.abs(playerSection.z() - SectionPos.z(packed)) <= sections) return true;
        }
        return false;
    }

    private static boolean hasPresentRaiderPlayer(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        for (UUID id : raid.raiderPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive() && !player.isSpectator()
                    && player.serverLevel().dimension().location().equals(raid.dimension())
                    && raid.footprint().contains(SectionPos.asLong(player.blockPosition()))) return true;
        }
        return false;
    }

    private static void suppressVanillaRaidOverlap(ServerLevel level, PlayerRaidSavedData.RaidRecord raid) {
        net.minecraft.world.entity.raid.Raid vanillaRaid = level.getRaidAt(raid.center());
        if (vanillaRaid != null) vanillaRaid.stop();
        for (ServerPlayer player : level.players()) {
            if (raid.footprint().contains(SectionPos.asLong(player.blockPosition()))) {
                player.removeEffect(MobEffects.RAID_OMEN);
                player.removeEffect(MobEffects.BAD_OMEN);
            }
        }
    }

    private static List<LivingEntity> livingRaiders(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        List<LivingEntity> result = new ArrayList<>();
        for (UUID id : raid.raiderPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive() && !player.isSpectator()) result.add(player);
        }
        for (UUID id : raid.raiderVillagers()) {
            Entity entity = find(server, id);
            if (entity instanceof LivingEntity living && living.isAlive()) result.add(living);
        }
        return result;
    }

    private static List<LivingEntity> livingDefenders(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        List<LivingEntity> result = new ArrayList<>();
        for (UUID id : raid.defenders()) {
            Entity entity = find(server, id);
            if (entity instanceof LivingEntity living && living.isAlive()) result.add(living);
        }
        return result;
    }

    static void highlightTrackedDefenders(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        for (UUID id : raid.defenders()) {
            Entity entity = find(server, id);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30));
            }
        }
    }

    static int betrayalReputation(int current) {
        return current < -250 ? current - 250 : -250;
    }

    static int calculateGolemBudget(
            int defenders, int raiderPlayers, int existing, int defendersPerGolem,
            int raidersPerBonus, int minimum, int maximum) {
        int desired = (int) Math.ceil(Math.max(0, defenders) / (double) Math.max(1, defendersPerGolem))
                + Math.max(0, raiderPlayers - 1) / Math.max(1, raidersPerBonus);
        desired = Math.max(minimum, desired);
        desired = Math.min(maximum, desired);
        return Math.max(0, desired - Math.max(0, existing));
    }

    /** Narrow exception to the normal villager/golem friendly-fire guard for raid-side combat. */
    public static boolean allowsVillagerGolemCombat(Entity first, Entity second) {
        IronGolem golem;
        Villager villager;
        if (first instanceof IronGolem firstGolem && second instanceof Villager secondVillager) {
            golem = firstGolem;
            villager = secondVillager;
        } else if (second instanceof IronGolem secondGolem && first instanceof Villager firstVillager) {
            golem = secondGolem;
            villager = firstVillager;
        } else {
            return false;
        }
        if (!(golem.level() instanceof ServerLevel level) || villager.level() != level) return false;
        PlayerRaidSavedData data = PlayerRaidSavedData.get(level);
        for (PlayerRaidSavedData.RaidRecord raid : data.raids()) {
            if (raid.phase() == PlayerRaidSavedData.Phase.ACTIVE
                    && raid.raiderVillagers().contains(villager.getUUID())
                    && isDefendingGolem(level, raid, golem)) {
                return true;
            }
        }
        return false;
    }

    /** True when two entities are living members of opposite sides of the same active Player Raid. */
    public static boolean areOpposingParticipants(Entity first, Entity second) {
        if (first == null || second == null || first == second
                || !(first.level() instanceof ServerLevel level) || second.level() != level) {
            return false;
        }
        PlayerRaidSavedData data = PlayerRaidSavedData.get(level);
        for (PlayerRaidSavedData.RaidRecord raid : data.raids()) {
            if (raid.phase() != PlayerRaidSavedData.Phase.ACTIVE) continue;
            boolean firstRaider = raid.raiderPlayers().contains(first.getUUID())
                    || raid.raiderVillagers().contains(first.getUUID());
            boolean secondRaider = raid.raiderPlayers().contains(second.getUUID())
                    || raid.raiderVillagers().contains(second.getUUID());
            boolean firstDefender = raid.defenders().contains(first.getUUID())
                    || isDefendingGolem(level, raid, first);
            boolean secondDefender = raid.defenders().contains(second.getUUID())
                    || isDefendingGolem(level, raid, second);
            if ((firstRaider && secondDefender) || (secondRaider && firstDefender)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldForceHide(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) return false;
        PlayerRaidSavedData.RaidRecord raid = PlayerRaidSavedData.get(level).activeForParticipant(villager.getUUID());
        if (raid == null
                || (raid.phase() != PlayerRaidSavedData.Phase.PREPARING
                    && raid.phase() != PlayerRaidSavedData.Phase.ACTIVE)) {
            return false;
        }
        if (raid.mercyEnabled()) return raid.mercyCandidates().contains(villager.getUUID());
        return raid.defenders().contains(villager.getUUID())
                && (villager.isBaby()
                    || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT);
    }

    private static LivingEntity nearest(LivingEntity origin, List<LivingEntity> choices) {
        return choices.stream().filter(candidate -> candidate.level() == origin.level() && candidate != origin)
                .min(Comparator.comparingDouble(origin::distanceToSqr)).orElse(null);
    }

    private static LivingEntity retainedOrNearest(LivingEntity origin, List<LivingEntity> choices) {
        if (origin instanceof Villager villager) {
            for (LivingEntity candidate : choices) {
                if (candidate.level() == origin.level()
                        && VillagerRetaliationHandler.hasRetaliationTarget(villager, candidate)) {
                    return candidate;
                }
            }
        }
        return nearest(origin, choices);
    }

    private static int existingAlignedGolems(ServerLevel level, PlayerRaidSavedData.RaidRecord raid) {
        return livingDefendingGolems(level, raid).size();
    }

    private static List<IronGolem> livingDefendingGolems(
            ServerLevel level,
            PlayerRaidSavedData.RaidRecord raid) {
        AABB area = AABB.ofSize(Vec3.atCenterOf(raid.center()), 192.0D, 96.0D, 192.0D);
        return level.getEntitiesOfClass(
                IronGolem.class,
                area,
                golem -> golem.isAlive() && isDefendingGolem(level, raid, golem));
    }

    private static boolean isDefendingGolem(
            ServerLevel level,
            PlayerRaidSavedData.RaidRecord raid,
            Entity entity) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId raidVillage = registry.canonical(raid.villageId()).orElse(raid.villageId());
        return entity instanceof IronGolem golem
                && raid.dimension().equals(level.dimension().location())
                && VillageAllegianceApi.canonicalPrimary(level, golem)
                .filter(raidVillage::equals)
                .isPresent();
    }

    private static boolean nearRaider(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid, BlockPos pos, double radius) {
        double radiusSqr = radius * radius;
        return livingRaiders(server, raid).stream().anyMatch(entity -> entity.blockPosition().distSqr(pos) < radiusSqr);
    }

    private static List<Villager> loadedVillageVillagers(ServerLevel level, VillageAllegianceId villageId) {
        VillageAllegianceRegistrySavedData.AllegianceRecord village =
                VillageAllegianceRegistrySavedData.get(level).canonicalRecord(villageId).orElse(null);
        if (village == null) return List.of();
        return level.getEntitiesOfClass(Villager.class,
                AABB.ofSize(Vec3.atCenterOf(village.center()), 320.0D, 128.0D, 320.0D),
                villager -> villager.isAlive()
                        && VillageAllegianceApi.canonicalPrimary(level, villager).filter(villageId::equals).isPresent());
    }
    static boolean isRaidableVillage(VillageAllegianceRegistrySavedData.AllegianceRecord village) {
        return village != null && village.lifecycleState() == VillageLifecycleState.ACTIVE;
    }

    static void classifyResident(
            Villager villager,
            Set<UUID> defenders,
            Set<UUID> mercyCandidates,
            Set<UUID> babyMercyCandidates) {
        if (villager == null) return;
        UUID id = villager.getUUID();
        if (villager.isBaby()) {
            mercyCandidates.add(id);
            babyMercyCandidates.add(id);
        } else if (villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
            mercyCandidates.add(id);
        } else {
            defenders.add(id);
        }
    }

    static void classifyResident(
            VillageAllegianceRegistrySavedData.ResidentRecord resident,
            Set<UUID> defenders,
            Set<UUID> mercyCandidates,
            Set<UUID> babyMercyCandidates) {
        if (resident == null) return;
        if (!resident.adult()) {
            mercyCandidates.add(resident.id());
            babyMercyCandidates.add(resident.id());
        } else if (resident.nitwit()) {
            mercyCandidates.add(resident.id());
        } else {
            defenders.add(resident.id());
        }
    }

    private static ServerLevel level(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, raid.dimension()));
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void hideBossBar(UUID id) {
        ServerBossEvent bar = BOSS_BARS.remove(id);
        if (bar != null) bar.removeAllPlayers();
    }

    public static void clearRuntimeState() {
        BOSS_BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BOSS_BARS.clear();
        RAID_CONFIRMATIONS.clear();
        PlayerRaidDialogueService.clearRuntimeState();
        PlayerRaidMercyService.clearRuntimeState();
    }
}
