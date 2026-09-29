package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.HuntingWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public final class SettlementHuntingTargetService {
    private static final Map<SettlementArea, Integer> CURSORS = new WeakHashMap<>();

    private SettlementHuntingTargetService() {
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area) {
        if (area.type() != SettlementAreaType.HUNTING_GROUNDS || !town.dimension().equals(level.dimension().location())) return;
        var bounds = new AABB(area.min().getX(), area.min().getY(), area.min().getZ(),
                area.max().getX() + 1.0D, area.max().getY() + 1.0D, area.max().getZ() + 1.0D);
        var targets = level.getEntitiesOfClass(LivingEntity.class, bounds, SettlementHuntingTargetService::recordable);
        if (targets.isEmpty()) return;
        int cursor = Math.floorMod(CURSORS.getOrDefault(area, 0), targets.size());
        int count = Math.min(16, targets.size());
        for (int index = 0; index < count; index++) observe(level, town, targets.get((cursor + index) % targets.size()), false);
        CURSORS.put(area, (cursor + count) % targets.size());
    }

    private static boolean recordable(LivingEntity target) {
        return target.getHealth() > 0.0F && (!target.isRemoved() || target.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK)
                && !(target instanceof Player || target instanceof AbstractVillager || target instanceof IronGolem);
    }

    static com.jvn.villagerretaliation.interaction.work.HiredWorkAssignment assignment(SettlementRecord town, SettlementArea area) {
        var plan = town.worksitePlans().get(area.id());
        var route = plan == null || plan.route() == null ? com.jvn.villagerretaliation.interaction.HiredRoute.empty()
                : new com.jvn.villagerretaliation.interaction.HiredRoute(plan.route().points(), false);
        return com.jvn.villagerretaliation.interaction.work.HiredWorkAssignment.of(
                com.jvn.villagerretaliation.interaction.HiredJobSite.fromWorkArea(HiredWorkArea.fromBounds(area.min(), area.max(), true)), route);
    }

    static boolean contains(SettlementRecord town, SettlementArea area, BlockPos position) {
        return area != null && town.contains(position) && assignment(town, area).isInsideWorkAreaOrRoute(position);
    }

    private static boolean contains(SettlementRecord town, BlockPos position) {
        return town.areas().values().stream().anyMatch(area -> area.type() == SettlementAreaType.HUNTING_GROUNDS && contains(town, area, position));
    }

    private static void observe(ServerLevel level, SettlementRecord town, LivingEntity target, boolean stopped) {
        if (!recordable(target) || target.level() != level || !contains(town, target.blockPosition())) return;
        for (var other : SettlementSavedData.get(level).settlements()) {
            if (other != town && other.huntingTargets().containsKey(target.getUUID())) return;
        }
        var state = town.huntingTargets().get(target.getUUID());
        if (state == null || !state.matches(target)) town.recordHuntingTarget(SettlementHuntingTarget.capture(target, level.getGameTime()));
        else {
            state.observe(target, stopped || level.isPositionEntityTicking(target.blockPosition()) ? level.getGameTime() : state.observedAt());
            town.changed();
        }
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var state : List.copyOf(town.huntingTargets().values())) {
            var existing = SettlementAnimalBirthService.existing(level, state.id());
            if (town.entityDeaths().containsKey(state.id())) {
                town.removeHuntingTarget(state.id());
            } else if (existing instanceof LivingEntity target && target.level() == level && state.matches(target)
                    && recordable(target) && (contains(town, target.blockPosition()) || state.modified())) {
                state.observe(target, level.isPositionEntityTicking(target.blockPosition()) ? now : state.observedAt());
                town.changed();
            } else if (existing != null || level.areEntitiesLoaded(ChunkPos.asLong(state.nativePosition())) || !contains(town, state.position()) && !state.modified()) {
                if (existing instanceof LivingEntity target && state.matches(target) && recordable(target)) {
                    state.restore(target, target.level().getGameTime());
                }
                town.removeHuntingTarget(state.id());
            }
        }
    }

    public static void onUnloaded(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level) || target instanceof Player) return;
        var reason = target.getRemovalReason();
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) continue;
            var state = town.huntingTargets().get(target.getUUID());
            if (state != null && state.modified() && reason == Entity.RemovalReason.CHANGED_DIMENSION) {
                state.restore(target, level.getGameTime());
                town.changed();
                continue;
            }
            if (reason != null && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK || !recordable(target)
                    || !contains(town, target.blockPosition()) && (state == null || !state.modified())) {
                town.removeHuntingTarget(target.getUUID());
            } else if (state != null && state.modified() && !contains(town, target.blockPosition())) {
                state.observe(target, level.getGameTime());
                town.changed();
            } else {
                observe(level, town, target, true);
            }
        }
    }

    static java.util.UUID observeEngagement(ServerLevel level, SettlementWorkforceService.Duty duty, Villager villager) {
        var area = duty.area();
        if (area.type() != SettlementAreaType.HUNTING_GROUNDS || !area.enabled() || !contains(duty.town(), area, villager.blockPosition())) return null;
        var target = HuntingWorker.activeTarget(villager, id -> {
            var observed = duty.town().huntingTargets().get(id);
            return observed == null ? null : observed.context(level);
        }).orElse(null);
        if (target == null || target.level() != level || !recordable(target) || !contains(duty.town(), area, target.blockPosition())
                || villager.getHealth() <= 0.0F || villager.isRemoved() && villager.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) return null;
        var owner = villager;
        if (villager.isRemoved()) {
            owner = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            if (owner == null) return null;
            owner.load(villager.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        }
        var candidate = target.isRemoved() ? SettlementHuntingTarget.capture(target, level.getGameTime()).context(level) : target;
        if (candidate == null || !HuntingWorker.isEligibleTarget(owner, candidate,
                HiredHuntingTargets.fromState(com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager)))
                || !VillagerRetaliationHandler.canEngageCustomTarget(owner, candidate)) return null;
        observe(level, duty.town(), target, true);
        return duty.town().huntingTargets().containsKey(target.getUUID()) ? target.getUUID() : null;
    }

    public static boolean claimedByOther(ServerLevel level, java.util.UUID workerId, java.util.UUID targetId) {
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) continue;
            var own = town.workers().get(workerId);
            boolean continuing = own != null && targetId.equals(claimedTarget(level, own));
            for (var other : town.workers().values()) {
                if (other.id().equals(workerId) || other.role() != HiredVillagerRole.HUNTING
                        || !other.area().equals(town.duties().get(other.id()))) continue;
                if (targetId.equals(claimedTarget(level, other)) && (!continuing || other.id().compareTo(workerId) < 0)) return true;
            }
        }
        return false;
    }

    public static void releaseClaim(Villager villager) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null) return;
        var worker = duty.town().workers().get(villager.getUUID());
        if (worker != null && worker.huntingTarget() != null) {
            worker.huntingTarget(null);
            duty.town().changed();
        }
    }

    private static java.util.UUID claimedTarget(ServerLevel level, SettlementWorkerState worker) {
        if (worker.settings().contains("Enabled") && !worker.settings().getBoolean("Enabled")) return null;
        return level.getEntity(worker.id()) instanceof Villager villager
                ? villager.isAlive() ? HuntingWorker.activeTarget(villager).map(LivingEntity::getUUID).orElse(null) : null
                : worker.huntingTarget();
    }

    static LivingEntity select(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, Villager owner) {
        var area = town.areas().get(worker.area());
        if (area == null || !area.enabled() || area.type() != SettlementAreaType.HUNTING_GROUNDS || !contains(town, area, worker.position())) return null;
        var selection = HiredHuntingTargets.fromState(worker.settings());
        if (selection.none()) return null;
        observeArea(level, town, area);
        var work = assignment(town, area);
        if (work.hasRoute()) {
            var nearby = HuntingWorker.targetSearchBounds(owner, work.workArea().horizontalRadius(), work.workArea().verticalRadius());
            level.getEntitiesOfClass(LivingEntity.class, nearby, target -> recordable(target) && contains(town, area, target.blockPosition()))
                    .stream().limit(16).forEach(target -> observe(level, town, target, false));
        }
        var candidates = new ArrayList<LivingEntity>();
        for (var state : town.huntingTargets().values()) {
            if (town.entityDeaths().containsKey(state.id())) continue;
            var existing = SettlementAnimalBirthService.existing(level, state.id());
            LivingEntity target;
            if (existing instanceof LivingEntity actual && actual.level() == level && state.matches(actual)) {
                state.restore(actual, level.getGameTime());
                target = actual;
            } else if (existing == null && !level.areEntitiesLoaded(ChunkPos.asLong(state.nativePosition()))) {
                var livestock = town.livestock().get(state.id());
                target = livestock == null ? state.context(level) : livestock.context(level);
            } else {
                continue;
            }
            if (target != null) candidates.add(target);
        }
        if (selection.players()) candidates.addAll(level.players());
        var workArea = HiredWorkArea.fromBounds(area.min(), area.max(), true);
        var bounds = HuntingWorker.targetSearchBounds(owner, workArea.horizontalRadius(), workArea.verticalRadius());
        var terrain = new SettlementExperienceTerrain(level, town);
        LivingEntity nearest = null;
        for (var target : candidates) {
            if (!contains(town, area, target.blockPosition()) || !bounds.intersects(target.getBoundingBox())
                    || !com.jvn.villagerretaliation.interaction.work.HiredEntityClaims.available(level, owner, target)
                    || !HuntingWorker.isEligibleTarget(owner, target, selection)
                    || !VillagerRetaliationHandler.canEngageCustomTarget(owner, target)
                    || !visible(terrain, owner, target)) continue;
            if (target.getUUID().equals(worker.huntingTarget())) return target;
            if (nearest == null || owner.distanceToSqr(target) < owner.distanceToSqr(nearest)) nearest = target;
        }
        return nearest;
    }

    public static void onActive(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level) || target instanceof Player) return;
        for (var town : SettlementSavedData.get(level).settlements()) {
            var state = town.huntingTargets().get(target.getUUID());
            if (state == null) continue;
            if (state.restore(target, level.getGameTime())) town.changed();
            if (!town.dimension().equals(level.dimension().location()) && state.matches(target)) {
                town.removeHuntingTarget(target.getUUID());
            }
        }
    }

    public static boolean beforeNativeDamage(LivingEntity target) {
        if (target instanceof Player || !(target.level() instanceof ServerLevel level) || level.getEntity(target.getUUID()) != target
                || SettlementDeathCapture.active(target)) return true;
        if (!SettlementEntityDeathService.onActive(target)) return false;
        if (target instanceof net.minecraft.world.entity.animal.Animal animal) SettlementLivestockService.onActive(animal);
        onActive(target);
        if (target instanceof Villager villager) {
            SettlementBackgroundService.onActive(villager);
            SettlementCareService.onActive(villager);
        }
        SettlementHuntingTarget.resumeNativeTimers(target, level.getGameTime());
        return !target.isRemoved();
    }

    public static void afterNativeDamage(LivingEntity target) {
        if (target instanceof Player || !(target.level() instanceof ServerLevel level) || level.getEntity(target.getUUID()) != target
                || SettlementDeathCapture.active(target) || !target.isAlive()) return;
        boolean ticking = level.isPositionEntityTicking(target.blockPosition());
        for (var town : SettlementSavedData.get(level).settlements()) {
            var state = town.huntingTargets().get(target.getUUID());
            if (state != null && state.matches(target)) {
                state.nativeDamage(target, level.getGameTime(), ticking);
                town.changed();
            }
            if (target instanceof Villager villager && town.dimension().equals(level.dimension().location())) {
                var care = town.residents().get(villager.getUUID());
                if (care != null) {
                    care.observe(villager, town, level.getGameTime());
                    town.changed();
                }
                var worker = town.workers().get(villager.getUUID());
                var area = town.areas().get(town.duties().get(villager.getUUID()));
                if (worker != null && area != null && area.id().equals(worker.area())) {
                    worker.observe(level, villager, new SettlementWorkforceService.Duty(town, area), level.getGameTime());
                    worker.huntingNativeDamage(villager, level.getGameTime(), ticking);
                    town.changed();
                }
            }
        }
    }

    public static void onNativeTick(LivingEntity target) {
        if (target.level() instanceof ServerLevel level && level.getEntity(target.getUUID()) == target
                && level.isPositionEntityTicking(target.blockPosition())) {
            SettlementHuntingTarget.resumeNativeTimers(target, level.getGameTime());
        }
    }

    private static boolean visible(SettlementExperienceTerrain terrain, Villager owner, LivingEntity target) {
        try {
            return VillagerRetaliationRetaliationUtil.hasClearLineOfSight(terrain, owner, target);
        } catch (SettlementExperienceTerrain.Unobserved unavailable) {
            return false;
        }
    }

    public static Optional<LivingEntity> requestedNativeTarget(Villager villager, Predicate<LivingEntity> eligible) {
        if (!(villager.level() instanceof ServerLevel level)) return Optional.empty();
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null || duty.area().type().role() != HiredVillagerRole.HUNTING || !duty.area().enabled()) return Optional.empty();
        var town = duty.town();
        var worker = town.workers().get(villager.getUUID());
        if (worker == null || worker.role() != HiredVillagerRole.HUNTING || !worker.area().equals(duty.area().id())
                || worker.huntingTarget() == null) return Optional.empty();
        var id = worker.huntingTarget();
        var state = town.huntingTargets().get(id);
        var existing = SettlementAnimalBirthService.existing(level, id);
        if (existing == null && state != null && !level.areEntitiesLoaded(ChunkPos.asLong(state.nativePosition()))) return Optional.empty();
        worker.huntingTarget(null);
        town.changed();
        if (existing instanceof LivingEntity target && target.level() == level && (state == null || state.matches(target))
                && eligible.test(target) && VillagerRetaliationHandler.canEngageCustomTarget(villager, target)) return Optional.of(target);
        return Optional.empty();
    }
}
