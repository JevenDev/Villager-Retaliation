package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class VillagerRetaliationRetaliationUtil {
    private static final String PERSISTENT_TARGET_UUID = "Target";
    private static final String PERSISTENT_LAST_SEEN_TICK = "LastSeenTick";
    private static final double MAX_RETALIATION_PURSUIT_DISTANCE_SQR = 1024.0D;
    private static final int MIN_PATH_RECALCULATION_TICKS = 4;
    private static final int RANDOM_PATH_RECALCULATION_TICKS = 7;
    private static final double PATHED_TARGET_MOVED_DISTANCE_SQR = 1.0D;
    private static final Map<UUID, RetaliationPathState> PATH_STATES = new HashMap<>();
    private static final ResourceLocation COMBAT_MOVEMENT_SPEED_MODIFIER_ID =
            VillagerRetaliation.id("combat_movement_speed");
    private static final AttributeModifier COMBAT_MOVEMENT_SPEED_MODIFIER =
            new AttributeModifier(COMBAT_MOVEMENT_SPEED_MODIFIER_ID, 0.25D, AttributeModifier.Operation.ADD_VALUE);

    private VillagerRetaliationRetaliationUtil() {
    }

    public static <T extends AbstractVillager> boolean tryAnger(
            T villager,
            LivingEntity attacker,
            Map<UUID, AngerTarget> angerTargets,
            String persistentTagRoot
    ) {
        if (VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(attacker) || !villager.isAlive() || attacker == villager) {
            return false;
        }

        long gameTime = villager.level().getGameTime();
        AngerTarget angerTarget = new AngerTarget(attacker.getUUID(), gameTime);
        angerTargets.put(villager.getUUID(), angerTarget);
        persistAnger(villager, persistentTagRoot, angerTarget);
        return true;
    }

    public static <T extends AbstractVillager> boolean tryAcquireGroundWeapon(
            T villager,
            double movementSpeed,
            Runnable beforeEquip
    ) {
        Optional<ItemEntity> nearestWeapon = VillagerRetaliationVillagerWeapons.findNearestWeapon(villager);
        if (nearestWeapon.isEmpty()) {
            return false;
        }

        return tryAcquireGroundWeapon(villager, nearestWeapon.get(), movementSpeed, beforeEquip);
    }

    public static <T extends AbstractVillager> boolean tryAcquireGroundWeapon(
            T villager,
            ItemEntity itemEntity,
            double movementSpeed,
            Runnable beforeEquip
    ) {
        if (!itemEntity.isAlive()
                || itemEntity.hasPickUpDelay()
                || itemEntity.getItem().isEmpty()
                || !VillagerRetaliationVillagerWeapons.shouldPathfindForWeapon(villager, itemEntity.getItem())) {
            return false;
        }

        if (villager.distanceToSqr(itemEntity) <= VillagerRetaliationVillagerWeapons.WEAPON_PICKUP_REACH_SQR) {
            beforeEquip.run();
            VillagerRetaliationVillagerWeapons.equipGroundWeapon(villager, itemEntity);
            VillagerRangedCombatHelper.seedInitialAttackDelay(villager, villager.getMainHandItem());
            return false;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(villager, itemEntity, (float) movementSpeed, 0);
        return true;
    }

    public static <T extends AbstractVillager> void restorePersistedAngerIfNeeded(
            T villager,
            Map<UUID, AngerTarget> angerTargets,
            String persistentTagRoot
    ) {
        if (angerTargets.containsKey(villager.getUUID())) {
            return;
        }

        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(persistentTagRoot, CompoundTag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag hostilityTag = persistentData.getCompound(persistentTagRoot);
        if (!hostilityTag.hasUUID(PERSISTENT_TARGET_UUID) || !hostilityTag.contains(PERSISTENT_LAST_SEEN_TICK)) {
            clearPersistentAnger(villager, persistentTagRoot);
            return;
        }

        long lastSeenTick = hostilityTag.getLong(PERSISTENT_LAST_SEEN_TICK);
        long gameTime = villager.level().getGameTime();
        if (gameTime - lastSeenTick >= VillagerRetaliationConfig.AGGRO_DURATION_TICKS.get()) {
            clearPersistentAnger(villager, persistentTagRoot);
            return;
        }

        angerTargets.put(villager.getUUID(), new AngerTarget(hostilityTag.getUUID(PERSISTENT_TARGET_UUID), lastSeenTick));
    }

    public static <T extends AbstractVillager> void refreshAngerTarget(
            T villager,
            AngerTarget angerTarget,
            long gameTime,
            Map<UUID, AngerTarget> angerTargets,
            String persistentTagRoot
    ) {
        AngerTarget refreshedTarget = angerTarget.withLastSeenGameTick(gameTime);
        angerTargets.put(villager.getUUID(), refreshedTarget);
        persistAnger(villager, persistentTagRoot, refreshedTarget);
    }

    public static <T extends AbstractVillager> boolean isHostileTowards(
            T villager,
            Player player,
            Map<UUID, AngerTarget> angerTargets,
            String persistentTagRoot,
            Runnable clearAnger
    ) {
        restorePersistedAngerIfNeeded(villager, angerTargets, persistentTagRoot);
        AngerTarget angerTarget = angerTargets.get(villager.getUUID());
        if (angerTarget == null) {
            return false;
        }

        if (angerTarget.targetId().equals(player.getUUID()) && VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(player)) {
            return false;
        }

        long gameTime = villager.level().getGameTime();
        double durationMultiplier = VillagerAggressionPolicy.getAngerDurationMultiplier(villager, player);
        if (gameTime - angerTarget.lastSeenGameTick() >= Math.max(1L, Math.round(VillagerRetaliationConfig.AGGRO_DURATION_TICKS.get() * durationMultiplier))) {
            clearAnger.run();
            return false;
        }

        return angerTarget.targetId().equals(player.getUUID());
    }

    public static <T extends AbstractVillager> void clearPersistentAnger(T villager, String persistentTagRoot) {
        villager.getPersistentData().remove(persistentTagRoot);
    }

    public static void spawnMadParticles(AbstractVillager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        double y = villager.getY() + villager.getBbHeight() + 0.2D;
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), y, villager.getZ(), 5, 0.25D, 0.15D, 0.25D, 0.01D);
    }

    public static void spawnPacifySuccessParticles(AbstractVillager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        double y = villager.getY() + villager.getBbHeight() + 0.2D;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), y, villager.getZ(), 6, 0.3D, 0.2D, 0.3D, 0.01D);
    }

    public static void spawnPacifyFailureParticles(AbstractVillager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        double y = villager.getY() + villager.getBbHeight() + 0.2D;
        level.sendParticles(ParticleTypes.SMOKE, villager.getX(), y, villager.getZ(), 6, 0.2D, 0.15D, 0.2D, 0.01D);
    }

    public static boolean isAttackReady(AbstractVillager villager, Map<UUID, Long> nextAttackTicks, long gameTime) {
        return gameTime >= nextAttackTicks.getOrDefault(villager.getUUID(), 0L);
    }

    @Nullable
    public static <T extends AbstractVillager> ActiveRetaliationTarget resolveActiveRetaliationTarget(
            T villager,
            VillagerRetaliationRetaliationRuntime<T> retaliationRuntime,
            Predicate<T> canFightBack,
            Runnable clearAnger
    ) {
        retaliationRuntime.restorePersistedAngerIfNeeded(villager);
        AngerTarget angerTarget = retaliationRuntime.angerTarget(villager);
        if (angerTarget == null) {
            return null;
        }

        if (!(villager.level() instanceof ServerLevel level)) {
            clearAnger.run();
            return null;
        }

        long gameTime = level.getGameTime();
        var entity = level.getEntity(angerTarget.targetId());
        if (!(entity instanceof LivingEntity target)) {
            if (hasExpiredAnger(angerTarget, gameTime)) {
                clearAnger.run();
            }
            return null;
        }
        if (!target.isAlive()) {
            clearAnger.run();
            return null;
        }
        if (!canFightBack.test(villager)) {
            clearAnger.run();
            return null;
        }

        boolean targetCurrentlyHostile = !VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(target);
        if (!targetCurrentlyHostile) {
            if (villager.hasLineOfSight(target)) {
                retaliationRuntime.refreshAngerTarget(villager, angerTarget, gameTime);
            }
            return new ActiveRetaliationTarget(level, target, gameTime, false);
        }

        if (villager.hasLineOfSight(target)) {
            retaliationRuntime.refreshAngerTarget(villager, angerTarget, gameTime);
        } else if (hasExpiredAnger(villager, target, angerTarget, gameTime)) {
            clearAnger.run();
            return null;
        }

        return new ActiveRetaliationTarget(level, target, gameTime, true);
    }

    public static boolean isUsingRangedCombatMode(AbstractVillager villager) {
        return VillagerRetaliationVillagerWeapons.isRangedWeapon(VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager));
    }

    public static boolean canUseMeleeCombatMode(AbstractVillager villager) {
        return !isUsingRangedCombatMode(villager);
    }

    public static boolean canMeleeHit(AbstractVillager villager, LivingEntity target) {
        if (!target.isAlive() || !villager.hasLineOfSight(target)) {
            return false;
        }

        boolean inMeleeRange;
        if (villager instanceof Villager) {
            inMeleeRange = villager.isWithinMeleeAttackRange(target);
        } else {
            double reachInflation = VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager) ? 1.0D : 0.6D;
            inMeleeRange = villager.getBoundingBox().inflate(reachInflation).intersects(target.getBoundingBox());
        }

        return inMeleeRange && hasClearMeleeLine(villager, target);
    }

    public static boolean isWithinRetaliationPursuitRange(AbstractVillager villager, LivingEntity target) {
        return villager.distanceToSqr(target) <= MAX_RETALIATION_PURSUIT_DISTANCE_SQR;
    }

    public static boolean moveTowardReachableRetaliationTarget(AbstractVillager villager, LivingEntity target, double movementSpeed) {
        if (!isWithinRetaliationPursuitRange(villager, target)) {
            villager.getNavigation().stop();
            clearPathingState(villager);
            return false;
        }

        UUID villagerId = villager.getUUID();
        UUID targetId = target.getUUID();
        RetaliationPathState state = PATH_STATES.get(villagerId);
        boolean targetChanged = state == null || !state.targetId().equals(targetId);
        int ticksUntilNextPathRecalculation = targetChanged
                ? 0
                : Math.max(state.ticksUntilNextPathRecalculation() - 1, 0);
        boolean targetMoved = targetChanged
                || target.distanceToSqr(state.pathedTargetX(), state.pathedTargetY(), state.pathedTargetZ()) >= PATHED_TARGET_MOVED_DISTANCE_SQR;
        boolean shouldRecalculatePath = ticksUntilNextPathRecalculation <= 0
                && (targetMoved || villager.getNavigation().isDone() || villager.getRandom().nextFloat() < 0.05F);

        if (!shouldRecalculatePath) {
            PATH_STATES.put(villagerId, state.withTicksUntilNextPathRecalculation(ticksUntilNextPathRecalculation));
            return !villager.getNavigation().isDone();
        }

        int failedPathFindingPenalty = targetChanged ? 0 : state.failedPathFindingPenalty();
        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null) {
            Node endNode = currentPath.getEndNode();
            if (endNode != null && target.distanceToSqr(endNode.x, endNode.y, endNode.z) < PATHED_TARGET_MOVED_DISTANCE_SQR) {
                failedPathFindingPenalty = 0;
            } else {
                failedPathFindingPenalty += 10;
            }
        } else if (!targetChanged) {
            failedPathFindingPenalty += 10;
        }

        ticksUntilNextPathRecalculation = MIN_PATH_RECALCULATION_TICKS + villager.getRandom().nextInt(RANDOM_PATH_RECALCULATION_TICKS);
        ticksUntilNextPathRecalculation += failedPathFindingPenalty;
        double distanceSqr = villager.distanceToSqr(target);
        if (distanceSqr > 1024.0D) {
            ticksUntilNextPathRecalculation += 10;
        } else if (distanceSqr > 256.0D) {
            ticksUntilNextPathRecalculation += 5;
        }

        boolean moved = villager.getNavigation().moveTo(target, movementSpeed);
        if (!moved) {
            ticksUntilNextPathRecalculation += 15;
        }

        PATH_STATES.put(villagerId, new RetaliationPathState(
                targetId,
                target.getX(),
                target.getY(),
                target.getZ(),
                ticksUntilNextPathRecalculation,
                failedPathFindingPenalty
        ));
        return moved || !villager.getNavigation().isDone();
    }

    public static void clearPathingState(AbstractVillager villager) {
        PATH_STATES.remove(villager.getUUID());
    }

    private static boolean hasClearMeleeLine(AbstractVillager villager, LivingEntity target) {
        Vec3 origin = villager.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        // Validate both center mass and lower body to reject corner-peek wall hits.
        Vec3 targetLowerBody = target.position().add(0.0D, target.getBbHeight() * 0.35D, 0.0D);
        return !isWallBlocking(villager, origin, targetCenter)
                && !isWallBlocking(villager, origin, targetLowerBody);
    }

    private static boolean isWallBlocking(AbstractVillager villager, Vec3 start, Vec3 end) {
        BlockHitResult hitResult = villager.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, villager));
        return hitResult.getType() == HitResult.Type.BLOCK;
    }

    public static <T extends AbstractVillager> boolean maintainTemporaryWeapon(
            T villager,
            Map<UUID, TemporaryWeaponState> temporaryWeapons
    ) {
        TemporaryWeaponState state = temporaryWeapons.get(villager.getUUID());
        if (state == null) {
            return false;
        }

        if (!VillagerRetaliationVillagerEquipment.mainHandMatchesItem(villager, state.equippedWeapon())) {
            VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, state.equippedWeapon(), currentCombatWeaponDropChance());
        }
        return true;
    }

    public static <T extends AbstractVillager> void equipTemporaryWeapon(
            T villager,
            Map<UUID, TemporaryWeaponState> temporaryWeapons,
            ItemStack weapon
    ) {
        ItemStack previousMainHand = villager.getMainHandItem().copy();
        ItemStack equippedWeapon = VillagerRetaliationCombatWeaponFactory.prepareEquippedCombatWeapon(villager, weapon.copy());
        float previousDropChance = Mob.DEFAULT_EQUIPMENT_DROP_CHANCE;
        temporaryWeapons.put(villager.getUUID(), new TemporaryWeaponState(previousMainHand, equippedWeapon.copy(), previousDropChance));
        VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, equippedWeapon, currentCombatWeaponDropChance());
        VillagerRangedCombatHelper.seedInitialAttackDelay(villager, equippedWeapon);
    }

    public static <T extends AbstractVillager> void restoreTemporaryWeapon(
            T villager,
            Map<UUID, TemporaryWeaponState> temporaryWeapons
    ) {
        TemporaryWeaponState state = temporaryWeapons.remove(villager.getUUID());
        if (state == null) {
            return;
        }

        if (VillagerRetaliationVillagerWeapons.maintainAcquiredWeaponAuthority(villager)) {
            return;
        }

        boolean restoredPrevious = false;
        if (VillagerRetaliationVillagerEquipment.mainHandMatchesStack(villager, state.equippedWeapon())) {
            VillagerRetaliationVillagerEquipment.restoreMainHand(villager, state.previousMainHand());
            restoredPrevious = true;
        }
        if (!restoredPrevious || state.previousMainHand().isEmpty()) {
            VillagerRetaliationVillagerEquipment.setMainHandDropChance(villager, state.previousDropChance());
        }
    }

    public static <T extends AbstractVillager> void discardTemporaryWeapon(
            T villager,
            Map<UUID, TemporaryWeaponState> temporaryWeapons
    ) {
        TemporaryWeaponState state = temporaryWeapons.remove(villager.getUUID());
        if (state != null) {
            VillagerRetaliationVillagerEquipment.setMainHandDropChance(villager, state.previousDropChance());
        }
    }

    public static <T extends AbstractVillager> void boostCombatMovement(
            T villager
    ) {
        AttributeInstance movementSpeed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        movementSpeed.addOrUpdateTransientModifier(COMBAT_MOVEMENT_SPEED_MODIFIER);
    }

    public static <T extends AbstractVillager> void restoreCombatMovement(T villager) {
        AttributeInstance movementSpeed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(COMBAT_MOVEMENT_SPEED_MODIFIER_ID);
        }
    }

    private static void persistAnger(AbstractVillager villager, String persistentTagRoot, AngerTarget angerTarget) {
        CompoundTag hostilityTag = new CompoundTag();
        hostilityTag.putUUID(PERSISTENT_TARGET_UUID, angerTarget.targetId());
        hostilityTag.putLong(PERSISTENT_LAST_SEEN_TICK, angerTarget.lastSeenGameTick());
        villager.getPersistentData().put(persistentTagRoot, hostilityTag);
    }

    private static float currentCombatWeaponDropChance() {
        return VillagerRetaliationConfig.COMBAT_WEAPON_DROP_CHANCE.get().floatValue();
    }

    private static boolean hasExpiredAnger(AngerTarget angerTarget, long gameTime) {
        return gameTime - angerTarget.lastSeenGameTick() >= VillagerRetaliationConfig.AGGRO_DURATION_TICKS.get();
    }

    private static boolean hasExpiredAnger(AbstractVillager villager, LivingEntity target, AngerTarget angerTarget, long gameTime) {
        double durationMultiplier = target instanceof Player player
            ? VillagerAggressionPolicy.getAngerDurationMultiplier(villager, player)
            : 1.0D;
        return gameTime - angerTarget.lastSeenGameTick() >= Math.max(1L, Math.round(VillagerRetaliationConfig.AGGRO_DURATION_TICKS.get() * durationMultiplier));
    }

    public record AngerTarget(UUID targetId, long lastSeenGameTick) {
        public AngerTarget withLastSeenGameTick(long gameTime) {
            if (gameTime == this.lastSeenGameTick) {
                return this;
            }
            return new AngerTarget(this.targetId, gameTime);
        }
    }

    public record TemporaryWeaponState(ItemStack previousMainHand, ItemStack equippedWeapon, float previousDropChance) {
    }

    public record ActiveRetaliationTarget(ServerLevel level, LivingEntity target, long gameTime, boolean targetCurrentlyHostile) {
    }

    private record RetaliationPathState(
            UUID targetId,
            double pathedTargetX,
            double pathedTargetY,
            double pathedTargetZ,
            int ticksUntilNextPathRecalculation,
            int failedPathFindingPenalty
    ) {
        RetaliationPathState withTicksUntilNextPathRecalculation(int ticksUntilNextPathRecalculation) {
            return new RetaliationPathState(
                    this.targetId,
                    this.pathedTargetX,
                    this.pathedTargetY,
                    this.pathedTargetZ,
                    ticksUntilNextPathRecalculation,
                    this.failedPathFindingPenalty
            );
        }
    }
}
