package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class WanderingTraderRetaliationHandler {
    private static final String PERSISTENT_TAG_ROOT = "CommonfolkPersistentTraderHostility";
    private static final String PERSISTENT_TARGET_UUID = "Target";
    private static final String PERSISTENT_LAST_SEEN_TICK = "LastSeenTick";
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();
    private static final Map<UUID, Double> ORIGINAL_MOVEMENT_SPEEDS = new HashMap<>();
    private static final Map<UUID, TemporaryWeaponState> TEMPORARY_WEAPONS = new HashMap<>();

    private WanderingTraderRetaliationHandler() {
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.WANDERING_TRADER, Attributes.ATTACK_DAMAGE)) {
            event.add(EntityType.WANDERING_TRADER, Attributes.ATTACK_DAMAGE, VillagerCombatRoles.PLAYER_FIST_DAMAGE);
        }
        if (!event.has(EntityType.WANDERING_TRADER, Attributes.ATTACK_KNOCKBACK)) {
            event.add(EntityType.WANDERING_TRADER, Attributes.ATTACK_KNOCKBACK, 0.0D);
        }
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get() || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getEntity() instanceof WanderingTrader trader) {
            resolveAttacker(event.getSource()).ifPresent(attacker -> {
                anger(trader, attacker);
                if (!CommonfolkConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                    angerNearbyTraders(trader, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
                }
            });
            return;
        }

        if (!(event.getEntity() instanceof TraderLlama traderLlama)
                || !(traderLlama.getLeashHolder() instanceof WanderingTrader trader)) {
            return;
        }

        resolveAttacker(event.getSource()).ifPresent(attacker -> {
            anger(trader, attacker);
            if (!CommonfolkConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                angerNearbyTraders(trader, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
            }
        });
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) {
            return;
        }

        clearAnger(trader, false);
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !CommonfolkConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()) {
            return;
        }

        resolveAttacker(event.getSource())
                .filter(attacker -> !shouldIgnoreAttacker(attacker))
                .ifPresent(attacker -> angerNearbyTraders(trader, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get()));
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof WanderingTrader trader) || trader.level().isClientSide) {
            return;
        }

        if (ANGER_TARGETS.containsKey(trader.getUUID())) {
            suppressVanillaPanic(trader);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) {
            return;
        }

        updateTraderSwing(trader);
        if (trader.level().isClientSide) {
            return;
        }

        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(trader);
            return;
        }

        restorePersistedAngerIfNeeded(trader);
        tryAcquireHostileTarget(trader);

        AngerTarget angerTarget = ANGER_TARGETS.get(trader.getUUID());
        if (angerTarget == null) {
            VillagerRangedCombatHelper.clearState(trader);
            restoreCombatMovement(trader);
            restoreTemporaryWeapon(trader);
            return;
        }

        if (!(trader.level() instanceof ServerLevel level)) {
            clearAnger(trader);
            return;
        }

        long gameTime = level.getGameTime();
        Entity entity = level.getEntity(angerTarget.targetId());
        if (!(entity instanceof LivingEntity target)) {
            if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
                clearAnger(trader);
            }
            return;
        }
        if (!target.isAlive() || shouldIgnoreAttacker(target)) {
            clearAnger(trader);
            return;
        }

        if (!WanderingTraderCombatRoles.canFightBack(trader)) {
            clearAnger(trader);
            return;
        }

        suppressVanillaPanic(trader);
        trader.setAggressive(true);
        trader.setTarget(target);

        if (trader.hasLineOfSight(target)) {
            AngerTarget refreshedTarget = angerTarget.withLastSeenGameTick(gameTime);
            ANGER_TARGETS.put(trader.getUUID(), refreshedTarget);
            persistAnger(trader, refreshedTarget);
        } else if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
            clearAnger(trader);
            return;
        }

        if (tryAcquireGroundWeapon(trader)) {
            return;
        }

        equipCombatWeapon(trader);

        double distanceSqr = trader.distanceToSqr(target);
        trader.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boostCombatMovement(trader);

        if (isUsingRangedCombatMode(trader) && VillagerRangedCombatHelper.tryAttack(trader, target, level, distanceSqr)) {
            return;
        }

        trader.getNavigation().moveTo(target, WanderingTraderCombatRoles.movementSpeed(trader));
        if (canUseMeleeCombatMode(trader) && canMeleeHit(trader, target) && attackReady(trader, gameTime)) {
            InteractionHand attackHand = selectAttackHand(trader);
            trader.swing(attackHand, true);
            syncMeleeAttackAttributes(trader);
            trader.doHurtTarget(target);
            NEXT_ATTACK_TICKS.put(trader.getUUID(), gameTime + WanderingTraderCombatRoles.attackCooldown(trader));
        }
    }

    public static boolean blockTradingIfHostile(WanderingTrader trader, Player player) {
        if (trader.level().isClientSide || !trader.isAlive() || !player.isAlive()) {
            return false;
        }

        if (!isHostileTowards(trader, player)) {
            return false;
        }

        spawnMadParticles(trader);
        return true;
    }

    private static void anger(WanderingTrader trader, LivingEntity attacker) {
        if (shouldIgnoreAttacker(attacker) || !trader.isAlive() || attacker == trader) {
            return;
        }

        long gameTime = trader.level().getGameTime();
        AngerTarget angerTarget = new AngerTarget(attacker.getUUID(), gameTime);
        ANGER_TARGETS.put(trader.getUUID(), angerTarget);
        persistAnger(trader, angerTarget);
    }

    private static void tryAcquireHostileTarget(WanderingTrader trader) {
        if (ANGER_TARGETS.containsKey(trader.getUUID())
                || !trader.isAlive()
                || !WanderingTraderCombatRoles.canFightBack(trader)) {
            return;
        }

        CommonfolkVillagerCombatUtil.getMemoryIfRegistered(trader, MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != trader)
                .ifPresent(target -> anger(trader, target));
    }

    private static boolean tryAcquireGroundWeapon(WanderingTrader trader) {
        if (!trader.isAlive()
                || !WanderingTraderCombatRoles.canScavengeGroundWeapons(trader)
                || CommonfolkVillagerWeapons.hasUsableWeapon(trader)
                || !CommonfolkVillagerCombatUtil.isThreatened(trader)) {
            return false;
        }

        Optional<ItemEntity> nearestWeapon = CommonfolkVillagerWeapons.findNearestWeapon(trader);
        if (nearestWeapon.isEmpty()) {
            return false;
        }

        ItemEntity itemEntity = nearestWeapon.get();
        if (trader.distanceToSqr(itemEntity) <= CommonfolkVillagerWeapons.WEAPON_PICKUP_REACH_SQR) {
            discardTemporaryWeapon(trader);
            CommonfolkVillagerWeapons.equipGroundWeapon(trader, itemEntity);
            VillagerRangedCombatHelper.seedInitialAttackDelay(trader, trader.getMainHandItem());
            return false;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(
                trader,
                itemEntity,
                (float) WanderingTraderCombatRoles.movementSpeed(trader),
                0
        );
        return true;
    }

    private static void clearAnger(WanderingTrader trader) {
        clearAnger(trader, true);
    }

    private static void clearAnger(WanderingTrader trader, boolean restoreWeapon) {
        ANGER_TARGETS.remove(trader.getUUID());
        clearPersistedAnger(trader);
        NEXT_ATTACK_TICKS.remove(trader.getUUID());
        VillagerRangedCombatHelper.clearState(trader);
        restoreCombatMovement(trader);
        if (restoreWeapon) {
            restoreTemporaryWeapon(trader);
        } else {
            TEMPORARY_WEAPONS.remove(trader.getUUID());
        }
        trader.setAggressive(false);
        trader.setTarget(null);
        trader.getNavigation().stop();
    }

    private static Optional<LivingEntity> resolveAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingEntity) {
            return Optional.of(livingEntity);
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity livingEntity) {
            return Optional.of(livingEntity);
        }

        return Optional.empty();
    }

    private static boolean shouldIgnoreAttacker(LivingEntity attacker) {
        return attacker instanceof Player player
                && (player.isSpectator()
                || CommonfolkConfig.NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS.get() && player.isCreative());
    }

    private static boolean attackReady(WanderingTrader trader, long gameTime) {
        return gameTime >= NEXT_ATTACK_TICKS.getOrDefault(trader.getUUID(), 0L);
    }

    private static boolean isUsingRangedCombatMode(WanderingTrader trader) {
        return CommonfolkVillagerWeapons.isRangedWeapon(CommonfolkVillagerWeapons.getPrimaryWeapon(trader));
    }

    private static boolean canUseMeleeCombatMode(WanderingTrader trader) {
        return !isUsingRangedCombatMode(trader);
    }

    private static boolean canMeleeHit(WanderingTrader trader, LivingEntity target) {
        double reachInflation = CommonfolkVillagerWeapons.hasUsableWeapon(trader) ? 1.0D : 0.6D;
        return trader.getBoundingBox().inflate(reachInflation).intersects(target.getBoundingBox());
    }

    private static void syncMeleeAttackAttributes(WanderingTrader trader) {
        AttributeInstance attackDamage = trader.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }

        double desiredBaseDamage = WanderingTraderCombatRoles.meleeAttackDamageBase(trader);
        if (attackDamage.getBaseValue() != desiredBaseDamage) {
            attackDamage.setBaseValue(desiredBaseDamage);
        }
    }

    private static void angerNearbyTraders(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (WanderingTrader nearby : level.getEntitiesOfClass(WanderingTrader.class, area)) {
            if (nearby != sourceEntity) {
                anger(nearby, attacker);
            }
        }
    }

    private static void suppressVanillaPanic(WanderingTrader trader) {
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(trader, MemoryModuleType.HURT_BY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(trader, MemoryModuleType.HURT_BY_ENTITY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(trader, MemoryModuleType.NEAREST_HOSTILE);
    }

    private static void restorePersistedAngerIfNeeded(WanderingTrader trader) {
        if (ANGER_TARGETS.containsKey(trader.getUUID())) {
            return;
        }

        CompoundTag persistentData = trader.getPersistentData();
        if (!persistentData.contains(PERSISTENT_TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag hostilityTag = persistentData.getCompound(PERSISTENT_TAG_ROOT);
        if (!hostilityTag.hasUUID(PERSISTENT_TARGET_UUID) || !hostilityTag.contains(PERSISTENT_LAST_SEEN_TICK)) {
            clearPersistedAnger(trader);
            return;
        }

        long lastSeenTick = hostilityTag.getLong(PERSISTENT_LAST_SEEN_TICK);
        long gameTime = trader.level().getGameTime();
        if (gameTime - lastSeenTick >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
            clearPersistedAnger(trader);
            return;
        }

        ANGER_TARGETS.put(trader.getUUID(), new AngerTarget(hostilityTag.getUUID(PERSISTENT_TARGET_UUID), lastSeenTick));
    }

    private static boolean isHostileTowards(WanderingTrader trader, Player player) {
        restorePersistedAngerIfNeeded(trader);
        AngerTarget angerTarget = ANGER_TARGETS.get(trader.getUUID());
        if (angerTarget == null) {
            return false;
        }

        long gameTime = trader.level().getGameTime();
        if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
            clearAnger(trader);
            return false;
        }

        return angerTarget.targetId().equals(player.getUUID());
    }

    private static void persistAnger(WanderingTrader trader, AngerTarget angerTarget) {
        CompoundTag hostilityTag = new CompoundTag();
        hostilityTag.putUUID(PERSISTENT_TARGET_UUID, angerTarget.targetId());
        hostilityTag.putLong(PERSISTENT_LAST_SEEN_TICK, angerTarget.lastSeenGameTick());
        trader.getPersistentData().put(PERSISTENT_TAG_ROOT, hostilityTag);
    }

    private static void clearPersistedAnger(WanderingTrader trader) {
        trader.getPersistentData().remove(PERSISTENT_TAG_ROOT);
    }

    private static void spawnMadParticles(WanderingTrader trader) {
        if (!(trader.level() instanceof ServerLevel level)) {
            return;
        }

        double y = trader.getY() + trader.getBbHeight() + 0.2D;
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, trader.getX(), y, trader.getZ(), 5, 0.25D, 0.15D, 0.25D, 0.01D);
    }

    private static InteractionHand selectAttackHand(WanderingTrader trader) {
        return trader.getMainHandItem().isEmpty() && !trader.getOffhandItem().isEmpty()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }

    private static void updateTraderSwing(WanderingTrader trader) {
        int swingDuration = Math.max(1, trader.getCurrentSwingDuration());
        if (trader.swinging) {
            trader.swingTime++;
            if (trader.swingTime >= swingDuration) {
                trader.swingTime = 0;
                trader.swinging = false;
            }
        } else {
            trader.swingTime = 0;
        }

        trader.attackAnim = (float) trader.swingTime / (float) swingDuration;
    }

    private static void equipCombatWeapon(WanderingTrader trader) {
        if (CommonfolkVillagerWeapons.maintainAcquiredWeaponAuthority(trader)) {
            discardTemporaryWeapon(trader);
            return;
        }

        TemporaryWeaponState state = TEMPORARY_WEAPONS.get(trader.getUUID());
        if (state != null) {
            if (!ItemStack.isSameItem(trader.getMainHandItem(), state.equippedWeapon())) {
                trader.setItemSlot(EquipmentSlot.MAINHAND, state.equippedWeapon().copy());
                trader.setDropChance(EquipmentSlot.MAINHAND, currentCombatWeaponDropChance());
            }
            return;
        }

        if (CommonfolkVillagerWeapons.hasUsableWeapon(trader)
                || !WanderingTraderCombatRoles.canUseTemporaryCombatLoadout(trader)) {
            return;
        }

        ItemStack weapon = WanderingTraderCombatRoles.preferredWeapon(trader);
        if (weapon.isEmpty()) {
            return;
        }

        ItemStack previousMainHand = trader.getMainHandItem().copy();
        ItemStack equippedWeapon = CommonfolkCombatWeaponFactory.prepareEquippedCombatWeapon(trader, weapon.copy());
        float previousDropChance = Mob.DEFAULT_EQUIPMENT_DROP_CHANCE;
        TEMPORARY_WEAPONS.put(trader.getUUID(), new TemporaryWeaponState(previousMainHand, equippedWeapon.copy(), previousDropChance));
        trader.setItemSlot(EquipmentSlot.MAINHAND, equippedWeapon);
        trader.setDropChance(EquipmentSlot.MAINHAND, currentCombatWeaponDropChance());
        VillagerRangedCombatHelper.seedInitialAttackDelay(trader, equippedWeapon);
    }

    private static void restoreTemporaryWeapon(WanderingTrader trader) {
        TemporaryWeaponState state = TEMPORARY_WEAPONS.remove(trader.getUUID());
        if (state == null) {
            return;
        }

        if (ItemStack.isSameItemSameComponents(trader.getMainHandItem(), state.equippedWeapon())) {
            trader.setItemSlot(EquipmentSlot.MAINHAND, state.previousMainHand().copy());
        }
        trader.setDropChance(EquipmentSlot.MAINHAND, state.previousDropChance());
    }

    private static void discardTemporaryWeapon(WanderingTrader trader) {
        TemporaryWeaponState state = TEMPORARY_WEAPONS.remove(trader.getUUID());
        if (state != null) {
            trader.setDropChance(EquipmentSlot.MAINHAND, state.previousDropChance());
        }
    }

    private static float currentCombatWeaponDropChance() {
        return CommonfolkConfig.COMBAT_WEAPON_DROP_CHANCE.get().floatValue();
    }

    private static void boostCombatMovement(WanderingTrader trader) {
        AttributeInstance movementSpeed = trader.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        ORIGINAL_MOVEMENT_SPEEDS.putIfAbsent(trader.getUUID(), movementSpeed.getBaseValue());
        movementSpeed.setBaseValue(0.75D);
    }

    private static void restoreCombatMovement(WanderingTrader trader) {
        AttributeInstance movementSpeed = trader.getAttribute(Attributes.MOVEMENT_SPEED);
        Double originalBaseSpeed = ORIGINAL_MOVEMENT_SPEEDS.remove(trader.getUUID());
        if (movementSpeed != null && originalBaseSpeed != null) {
            movementSpeed.setBaseValue(originalBaseSpeed);
        }
    }

    private record AngerTarget(UUID targetId, long lastSeenGameTick) {
        private AngerTarget withLastSeenGameTick(long gameTime) {
            if (gameTime == this.lastSeenGameTick) {
                return this;
            }
            return new AngerTarget(this.targetId, gameTime);
        }
    }

    private record TemporaryWeaponState(ItemStack previousMainHand, ItemStack equippedWeapon, float previousDropChance) {
    }
}
