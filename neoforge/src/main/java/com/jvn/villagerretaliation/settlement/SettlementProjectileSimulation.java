package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.AbstractArrowAccessor;
import com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor;
import com.jvn.villagerretaliation.mixin.ProjectileCombatAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SettlementProjectileSimulation {
    private static final ThreadLocal<SettlementProjectileSimulation> ACTIVE = new ThreadLocal<>();
    private AbstractArrow current;
    private long frame;
    private boolean advanced;
    private final ServerLevel level;
    private final SettlementRecord town;
    private final SettlementProjectile state;
    private final SettlementExperienceTerrain terrain;
    private final Villager owner;
    private final LivingEntity currentTarget;
    private final Map<UUID, LivingEntity> contexts = new LinkedHashMap<>();

    SettlementProjectileSimulation(ServerLevel level, SettlementRecord town, SettlementProjectile state,
                                   Villager owner, LivingEntity currentTarget) {
        this.level = level;
        this.town = town;
        this.state = state;
        this.terrain = new SettlementExperienceTerrain(level, town);
        this.owner = owner;
        this.currentTarget = currentTarget;
    }

    boolean step(Projectile projectile, long at) {
        if (!(projectile instanceof Arrow || projectile instanceof SpectralArrow)) return false;
        var previous = ACTIVE.get();
        this.current = (AbstractArrow) projectile;
        this.frame = at;
        this.advanced = false;
        ACTIVE.set(this);
        try {
            projectile.tick();
            return this.advanced;
        } finally {
            this.current = null;
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static boolean tickObserved(AbstractArrow arrow) {
        var simulation = ACTIVE.get();
        if (simulation == null || simulation.current != arrow) return false;
        simulation.advanced = simulation.tickArrow(arrow, simulation.frame);
        return true;
    }

    public static boolean captureDrop(net.minecraft.world.entity.item.ItemEntity item) {
        var simulation = ACTIVE.get();
        if (simulation == null || simulation.current == null || item.level() != simulation.level || item.getItem().isEmpty()) return false;
        simulation.town.recordItemDrop(SettlementItemDrop.create(item, simulation.state.area(), simulation.frame));
        return true;
    }

    public static java.util.Set<UUID> pierced(Projectile projectile) {
        var result = new java.util.LinkedHashSet<UUID>();
        for (var raw : projectile.getPersistentData().getList("VillagerRetaliationPiercedTargets", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            result.add(net.minecraft.nbt.NbtUtils.loadUUID(raw));
        }
        return result;
    }

    public static net.minecraft.world.level.block.state.BlockState blockState(Projectile projectile,
                                                                            net.minecraft.world.level.Level level, BlockPos position) {
        var simulation = ACTIVE.get();
        return simulation != null && simulation.current == projectile ? simulation.terrain.getBlockState(position) : level.getBlockState(position);
    }

    private boolean tickArrow(AbstractArrow arrow, long at) {
        var access = (AbstractArrowAccessor) arrow;
        var nativeAccess = (ProjectileCombatAccessor) arrow;
        var start = arrow.position();
        var velocity = arrow.getDeltaMovement();
        var bounds = arrow.getBoundingBox().expandTowards(velocity);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (!this.terrain.getFluidState(pos).isEmpty()) return false;
        }
        var block = this.terrain.getBlockState(arrow.blockPosition());
        boolean inside = !arrow.isNoPhysics() && block.getCollisionShape(this.terrain, arrow.blockPosition()).toAabbs().stream()
                .anyMatch(shape -> shape.move(arrow.blockPosition()).contains(start));
        if (inside) access.villagerretaliation$setInGround(true);
        if (access.villagerretaliation$isInGround() && !arrow.isNoPhysics()) {
            lifetime(arrow);
            if (!block.equals(access.villagerretaliation$lastState())
                    && !this.terrain.getBlockCollisions(arrow, new AABB(start, start).inflate(0.06D)).iterator().hasNext()) {
                access.villagerretaliation$startFalling();
            } else {
                access.villagerretaliation$tickDespawn();
            }
            access.villagerretaliation$setInGroundTime(access.villagerretaliation$inGroundTime() + 1);
            if (arrow.shakeTime > 0) arrow.shakeTime--;
            return true;
        }
        access.villagerretaliation$setInGroundTime(0);
        var blockHit = this.terrain.clip(new ClipContext(start, start.add(velocity),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
        var end = blockHit.getType() == HitResult.Type.MISS ? start.add(velocity) : blockHit.getLocation();
        var candidates = candidates(bounds.inflate(1.0D));
        if (!nativeAccess.villagerretaliation$leftOwner()
                && !bounds.inflate(1.0D).intersects(this.owner.getBoundingBox())) nativeAccess.villagerretaliation$setLeftOwner(true);
        var pierced = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        var priorHits = pierced(arrow);
        for (var candidate : candidates.values()) if (priorHits.contains(candidate.getUUID())) pierced.add(candidate.getId());
        access.villagerretaliation$setPiercingIgnoreEntityIds(pierced);
        EntityHitResult entityHit = hit(arrow, candidates, start, end);
        for (var candidate : candidates.values()) {
            if (this.level.getEntity(candidate.getUUID()) == candidate && this.level.isPositionEntityTicking(candidate.blockPosition())
                    && nativeAccess.villagerretaliation$canHitEntity(candidate)
                    && candidate.getBoundingBox().inflate((double) 0.3F).clip(start, end).isPresent()) return false;
        }
        if (entityHit == null && blockHit.getType() != HitResult.Type.MISS) {
            var hitBlock = this.terrain.getBlockState(blockHit.getBlockPos()).getBlock();
            var weapon = arrow.getWeaponItem();
            if (hitBlock.getClass() != net.minecraft.world.level.block.Block.class
                    || weapon != null && weapon.getEnchantments().keySet().stream()
                    .anyMatch(enchantment -> !enchantment.value().getEffects(net.minecraft.world.item.enchantment.EnchantmentEffectComponents.HIT_BLOCK).isEmpty())) {
                return false;
            }
        }
        lifetime(arrow);
        if (arrow.shakeTime > 0) arrow.shakeTime--;
        while (!arrow.isRemoved()) {
            var result = entityHit == null ? blockHit : entityHit;
            if (!arrow.isNoPhysics() && result.getType() != HitResult.Type.MISS) {
                if (net.neoforged.neoforge.event.EventHooks.onProjectileImpact(arrow, result)) break;
                ProjectileDeflection deflection;
                if (entityHit != null && entityHit.getEntity() instanceof LivingEntity victim) {
                    deflection = impact(arrow, victim, at);
                } else {
                    deflection = nativeAccess.villagerretaliation$hit(result);
                }
                arrow.hasImpulse = true;
                if (deflection != ProjectileDeflection.NONE) break;
            }
            if (entityHit == null || arrow.getPierceLevel() <= 0) break;
            entityHit = hit(arrow, candidates, start, end);
            if (entityHit == null) break;
        }
        velocity = arrow.getDeltaMovement();
        var destination = arrow.position().add(velocity);
        nativeAccess.villagerretaliation$updateRotation();
        arrow.setDeltaMovement(velocity.scale((double) 0.99F));
        if (!arrow.isNoPhysics()) arrow.setDeltaMovement(arrow.getDeltaMovement().add(0.0D, -arrow.getGravity(), 0.0D));
        arrow.setPos(destination);
        arrow.checkBelowWorld();
        return true;
    }

    private void lifetime(AbstractArrow arrow) {
        var access = (ProjectileCombatAccessor) arrow;
        if (!access.villagerretaliation$hasBeenShot()) {
            arrow.gameEvent(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_SHOOT, this.owner);
            access.villagerretaliation$setHasBeenShot(true);
        }
        arrow.tickCount++;
        arrow.xRotO = arrow.getXRot();
        arrow.yRotO = arrow.getYRot();
        if (arrow.getRemainingFireTicks() > 0) arrow.setRemainingFireTicks(arrow.getRemainingFireTicks() - 1);
    }

    private EntityHitResult hit(AbstractArrow arrow, Map<UUID, LivingEntity> candidates, Vec3 start, Vec3 end) {
        LivingEntity nearest = null;
        double distance = Double.MAX_VALUE;
        var access = (ProjectileCombatAccessor) arrow;
        for (var candidate : candidates.values()) {
            if (!candidate.isAlive() || pierced(arrow).contains(candidate.getUUID()) || !access.villagerretaliation$canHitEntity(candidate)) continue;
            var clip = candidate.getBoundingBox().inflate((double) 0.3F).clip(start, end);
            if (clip.isPresent() && start.distanceToSqr(clip.get()) < distance) {
                nearest = candidate;
                distance = start.distanceToSqr(clip.get());
            }
        }
        return nearest == null ? null : new EntityHitResult(nearest);
    }

    private Map<UUID, LivingEntity> candidates(AABB bounds) {
        var candidates = new LinkedHashMap<UUID, LivingEntity>();
        for (var actual : this.level.getEntitiesOfClass(LivingEntity.class, bounds)) candidates.put(actual.getUUID(), actual);
        for (var observed : this.town.huntingTargets().values()) {
            if (this.town.entityDeaths().containsKey(observed.id())) continue;
            LivingEntity target;
            if (this.currentTarget != null && this.currentTarget.getUUID().equals(observed.id())) target = this.currentTarget;
            else if (candidates.containsKey(observed.id())) target = candidates.get(observed.id());
            else {
                target = this.contexts.computeIfAbsent(observed.id(), id -> observed.context(this.level));
                if (target == null) continue;
            }
            if (bounds.intersects(target.getBoundingBox())) candidates.put(observed.id(), target);
        }
        if (bounds.intersects(this.owner.getBoundingBox())) candidates.put(this.owner.getUUID(), this.owner);
        return candidates;
    }

    private ProjectileDeflection impact(AbstractArrow arrow, LivingEntity victim, long at) {
        var worker = this.town.workers().get(this.state.owner());
        var observed = this.town.huntingTargets().get(victim.getUUID());
        if (observed == null && victim != this.owner) {
            observed = SettlementHuntingTarget.capture(victim, at);
            this.town.recordHuntingTarget(observed);
        }
        if (observed != null) observed.restoreTimers(victim, at);
        if (worker != null) worker.huntingTimers(this.owner, at);
        ((LivingEntityCombatAccessor) victim).villagerretaliation$detectEquipmentUpdates();
        ((LivingEntityCombatAccessor) this.owner).villagerretaliation$detectEquipmentUpdates();
        var before = victim.saveWithoutId(new CompoundTag());
        var ownerBefore = this.owner.saveWithoutId(new CompoundTag());
        try (var victimCapture = new SettlementDeathCapture(victim);
             var ownerCapture = new SettlementDeathCapture(this.owner)) {
            var result = ((ProjectileCombatAccessor) arrow).villagerretaliation$hit(new EntityHitResult(victim));
            var position = observed == null ? worker == null ? victim.blockPosition() : worker.nativePosition() : observed.nativePosition();
            boolean killed = outcome(victim, position, victim == this.owner ? ownerCapture : victimCapture, worker, at);
            if (killed) {
                this.town.removeHuntingTarget(victim.getUUID());
                this.town.removeLivestock(victim.getUUID());
                if (worker != null && victim.getUUID().equals(worker.huntingTarget())) worker.huntingTarget(null);
            }
            if (victim != this.owner) {
                if (!killed && observed != null) observed.interacted(victim, before, at);
                outcome(this.owner, worker == null ? this.owner.blockPosition() : worker.nativePosition(), ownerCapture, worker, at);
            }
            if (worker != null) {
                worker.huntingInjury(this.owner, ownerBefore, at);
                worker.inventorySnapshot(com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(this.owner).captureSnapshot());
                var care = this.town.residents().get(worker.id());
                if (care != null) care.combat(this.owner);
            }
            return result;
        }
    }

    private boolean outcome(LivingEntity victim, BlockPos nativePosition, SettlementDeathCapture capture,
                            SettlementWorkerState worker, long at) {
        if (worker != null) return SettlementHuntingCombat.outcome(this.level, this.town, worker, this.owner, victim, nativePosition, capture, at);
        boolean killed = !victim.isAlive() && capture.accepted();
        for (var item : capture.drops()) {
            if (!item.isRemoved() && !item.getItem().isEmpty()) this.town.recordItemDrop(SettlementItemDrop.create(item, this.state.area(), at));
        }
        if (killed || !capture.experience().isEmpty()) {
            this.town.recordEntityDeath(SettlementEntityDeath.create(victim, nativePosition, this.state.area(), this.state.owner(),
                    this.owner.blockPosition(), capture.experience(), at, killed));
        }
        if (killed) {
            this.town.removeHuntingTarget(victim.getUUID());
            this.town.removeLivestock(victim.getUUID());
        }
        return killed;
    }

}
