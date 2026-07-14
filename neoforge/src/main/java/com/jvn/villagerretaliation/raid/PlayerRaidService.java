package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.item.BannerHelmetData;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
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
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();

    private PlayerRaidService() {
    }

    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getItem().is(Items.GOAT_HORN)
                || !BannerHelmetData.hasAttachedBanner(player.getItemBySlot(EquipmentSlot.HEAD))) {
            return;
        }
        tryDeclare(player);
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
        if (village == null || village.archived() || !village.footprintSections().contains(SectionPos.asLong(position))) {
            return false;
        }
        villageId = village.id();
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
        long now = level.getServer().overworld().getGameTime();
        if (data.cooldownUntil(registry, villageId) > now) {
            initiator.sendSystemMessage(Component.translatable("villagerretaliation.player_raid.cooldown"));
            return false;
        }

        Set<UUID> defenders = new LinkedHashSet<>();
        village.activeResidents(now).forEach(resident -> defenders.add(resident.id()));
        loadedVillageVillagers(level, villageId).forEach(villager -> defenders.add(villager.getUUID()));
        if (defenders.isEmpty()) {
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
                    defenders.add(member.villagerId());
                }
                else raiderVillagers.add(member.villagerId());
            }
        }
        Set<UUID> participants = new LinkedHashSet<>(raiderPlayers);
        participants.addAll(raiderVillagers);
        participants.addAll(defenders);
        for (UUID participant : participants) {
            if (data.activeForParticipant(participant) != null) {
                initiator.sendSystemMessage(Component.translatable("villagerretaliation.player_raid.party_busy"));
                return false;
            }
        }

        PlayerRaidSavedData.RaidRecord raid = data.create(
                villageId, level.dimension().location(), village.center(), village.footprintSections(),
                village.displayName(), initiator.getUUID(), party == null ? null : party.id(),
                raiderPlayers, raiderVillagers, defenders, defectors, now);
        Set<UUID> villageWitnesses = new LinkedHashSet<>(village.residents().keySet());
        villageWitnesses.addAll(defenders);
        applyBetrayal(level, raid, villageWitnesses);
        defectors.forEach(id -> PartyVillagerContractService.releaseForHomeVillageRaid(level.getServer(), id));
        initiator.sendSystemMessage(Component.translatable(
                "villagerretaliation.player_raid.declared", village.displayName(), defenders.size()));
        if (!PlayerRaidDialogueService.begin(initiator, raid)) {
            beginPreparation(level.getServer(), raid.id());
        }
        return true;
    }

    public static void beginPreparation(MinecraftServer server, UUID raidId) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(server.overworld());
        PlayerRaidSavedData.RaidRecord raid = data.raid(raidId);
        if (raid == null || raid.phase() != PlayerRaidSavedData.Phase.DECLARATION) return;
        long now = server.overworld().getGameTime();
        raid.setPhase(PlayerRaidSavedData.Phase.PREPARING, now);
        raid.setAbsenceStarted(-1L);
        prepareDefenders(server, raid);
        data.changed();
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 5L != 0L) return;
        PlayerRaidDialogueService.reconcile(server);
        PlayerRaidSavedData data = PlayerRaidSavedData.get(server.overworld());
        for (PlayerRaidSavedData.RaidRecord raid : new ArrayList<>(data.raids())) {
            tickRaid(server, data, raid, now);
        }
    }

    private static void tickRaid(
            MinecraftServer server, PlayerRaidSavedData data, PlayerRaidSavedData.RaidRecord raid, long now) {
        ServerLevel level = level(server, raid);
        if (level == null) return;
        updateBossBar(server, raid, now);
        if (!raid.running()) {
            if (raid.outcomeCleanupAt() > 0L && now >= raid.outcomeCleanupAt()) {
                hideBossBar(raid.id());
                data.remove(raid.id());
            }
            return;
        }
        suppressVanillaRaidOverlap(level, raid);
        if (raid.phase() == PlayerRaidSavedData.Phase.DECLARATION) {
            if ((!PlayerRaidDialogueService.hasSession(raid.id()) && now - raid.phaseStarted() >= 20L)
                    || now - raid.phaseStarted() >= 1_200L) beginPreparation(server, raid.id());
            return;
        }
        if (raid.defenders().isEmpty()) {
            finish(server, data, raid, true, now);
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
            if (now % 20L == 0L) prepareDefenders(server, raid);
            if (now - raid.phaseStarted() >= VillagerRetaliationConfig.PLAYER_RAID_PREPARATION_TICKS.get()) {
                activate(level, data, raid, now);
            }
            return;
        }
        reconcileCombat(server, raid);
        reconcileGolemMilestones(level, data, raid);
    }

    private static void activate(
            ServerLevel level, PlayerRaidSavedData data, PlayerRaidSavedData.RaidRecord raid, long now) {
        int combatants = raid.raiderPlayers().size() + raid.raiderVillagers().size();
        raid.setGolemBudget(calculateGolemBudget(
                raid.initialDefenderCount(), combatants, existingAlignedGolems(level, raid),
                VillagerRetaliationConfig.PLAYER_RAID_DEFENDERS_PER_GOLEM.get(),
                VillagerRetaliationConfig.PLAYER_RAID_RAIDERS_PER_BONUS_GOLEM.get(),
                VillagerRetaliationConfig.PLAYER_RAID_MINIMUM_GOLEMS.get(),
                VillagerRetaliationConfig.PLAYER_RAID_MAXIMUM_GOLEMS.get()));
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, now);
        data.changed();
        reconcileGolemMilestones(level, data, raid);
        reconcileCombat(level.getServer(), raid);
    }

    private static void prepareDefenders(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        ServerLevel level = level(server, raid);
        if (level == null) return;
        for (UUID defenderId : raid.defenders()) {
            Entity entity = find(server, defenderId);
            if (!(entity instanceof Villager villager) || villager.level() != level) continue;
            if (villager.isBaby() || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
                hideVillager(level, villager);
            } else {
                PlayerRaidLoadoutService.equip(villager);
            }
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

    private static void reconcileCombat(MinecraftServer server, PlayerRaidSavedData.RaidRecord raid) {
        List<LivingEntity> raiders = livingRaiders(server, raid);
        List<LivingEntity> defenders = livingDefenders(server, raid);
        for (LivingEntity defender : defenders) {
            LivingEntity target = nearest(defender, raiders);
            if (target == null) continue;
            if (defender instanceof Villager villager && !villager.isBaby()
                    && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT) {
                PlayerRaidLoadoutService.equip(villager);
                VillagerRetaliationHandler.forceAngerSilently(villager, target);
            } else if (defender instanceof Villager villager) {
                hideVillager((ServerLevel) villager.level(), villager);
            }
        }
        for (LivingEntity raider : raiders) {
            LivingEntity target = nearest(raider, defenders);
            if (target == null) continue;
            if (raider instanceof Villager villager) VillagerRetaliationHandler.forceAngerSilently(villager, target);
        }
        ServerLevel level = level(server, raid);
        if (level != null) {
            AABB area = AABB.ofSize(Vec3.atCenterOf(raid.center()), 192.0D, 96.0D, 192.0D);
            for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, area, IronGolem::isAlive)) {
                if (VillageAllegianceApi.canonicalPrimary(level, golem).filter(raid.villageId()::equals).isPresent()) {
                    LivingEntity target = nearest(golem, raiders);
                    if (target != null) golem.setTarget(target);
                }
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

    private static void removeDefender(ServerLevel level, UUID id) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(level);
        for (PlayerRaidSavedData.RaidRecord raid : data.raids()) {
            if (raid.running() && raid.removeDefender(id)) data.changed();
        }
    }

    private static void finish(
            MinecraftServer server, PlayerRaidSavedData data, PlayerRaidSavedData.RaidRecord raid,
            boolean raidersWon, long now) {
        raid.setPhase(raidersWon ? PlayerRaidSavedData.Phase.RAIDER_VICTORY : PlayerRaidSavedData.Phase.DEFENDER_VICTORY, now);
        raid.setOutcomeCleanupAt(now + OUTCOME_DISPLAY_TICKS);
        if (!raidersWon) {
            long cooldown = Math.max(0, VillagerRetaliationConfig.PLAYER_RAID_VILLAGE_COOLDOWN_DAYS.get()) * DAY_TICKS;
            data.setCooldown(raid.villageId(), now + cooldown);
        }
        data.changed();
        Component message = Component.translatable(raidersWon
                ? "villagerretaliation.player_raid.victory"
                : "villagerretaliation.player_raid.defended", raid.villageName());
        server.getPlayerList().broadcastSystemMessage(message, false);
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

    static int betrayalReputation(int current) {
        return current < -250 ? current - 250 : -250;
    }

    static int calculateGolemBudget(
            int defenders, int combatants, int existing, int defendersPerGolem,
            int raidersPerBonus, int minimum, int maximum) {
        int desired = (int) Math.ceil(Math.max(0, defenders) / (double) Math.max(1, defendersPerGolem))
                + Math.max(0, combatants - 1) / Math.max(1, raidersPerBonus);
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
                    && VillageAllegianceApi.canonicalPrimary(level, golem).filter(raid.villageId()::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldForceHide(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)
                || (!villager.isBaby() && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT)) {
            return false;
        }
        PlayerRaidSavedData.RaidRecord raid = PlayerRaidSavedData.get(level).activeForParticipant(villager.getUUID());
        return raid != null
                && raid.defenders().contains(villager.getUUID())
                && (raid.phase() == PlayerRaidSavedData.Phase.PREPARING
                    || raid.phase() == PlayerRaidSavedData.Phase.ACTIVE);
    }

    private static LivingEntity nearest(LivingEntity origin, List<LivingEntity> choices) {
        return choices.stream().filter(candidate -> candidate.level() == origin.level() && candidate != origin)
                .min(Comparator.comparingDouble(origin::distanceToSqr)).orElse(null);
    }

    private static int existingAlignedGolems(ServerLevel level, PlayerRaidSavedData.RaidRecord raid) {
        AABB area = AABB.ofSize(Vec3.atCenterOf(raid.center()), 192.0D, 96.0D, 192.0D);
        return (int) level.getEntitiesOfClass(IronGolem.class, area, IronGolem::isAlive).stream()
                .filter(golem -> VillageAllegianceApi.canonicalPrimary(level, golem).filter(raid.villageId()::equals).isPresent())
                .count();
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
        PlayerRaidDialogueService.clearRuntimeState();
    }
}
