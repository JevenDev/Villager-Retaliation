package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationHandler {
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SPECIAL_TICKS = new HashMap<>();
    private static final Map<UUID, TemporaryWeaponState> TEMPORARY_WEAPONS = new HashMap<>();
    private static final Map<UUID, Integer> FLETCHER_SEE_TIME = new HashMap<>();
    private static final Map<UUID, Integer> FLETCHER_ATTACK_DELAY = new HashMap<>();
    private static final Map<UUID, CrossbowState> FLETCHER_CROSSBOW_STATE = new HashMap<>();
    private static final double FLETCHER_MAX_RANGED_DISTANCE_SQR = 225.0D;
    private static final int FLETCHER_BOW_DRAW_TICKS = 20;
    private static final int FLETCHER_BOW_ATTACK_INTERVAL = 20;
    private static final int FLETCHER_INITIAL_RANGED_WINDUP_TICKS = 2;
    private static final int FLETCHER_CROSSBOW_POST_LOAD_DELAY_BASE_TICKS = 20;
    private static final int FLETCHER_CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS = 20;

    private VillagerRetaliationHandler() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof Villager villager)
                || villager.isBaby()) {
            return;
        }

        if (VillagerCombatRoles.isArmorer(villager) && CommonfolkConfig.ARMORERS_FIGHT_BACK.get()) {
            villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
        }

        resolveAttacker(event.getSource()).ifPresent(attacker -> anger(villager, attacker));
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        // Keep temporary combat weapons equipped on death so vanilla equipment drops can roll.
        clearAnger(villager, false);
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !CommonfolkConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()
                || villager.isBaby()
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        Optional<LivingEntity> attacker = resolveAttacker(event.getSource());
        if (attacker.isEmpty() || shouldIgnoreAttacker(attacker.get())) {
            return;
        }

        double radius = CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get();
        AABB area = villager.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != villager && !nearby.isBaby()) {
                anger(nearby, attacker.get());
            }
        }
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }

        if (ANGER_TARGETS.containsKey(villager.getUUID())) {
            suppressVanillaPanic(villager);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        updateVillagerSwing(villager);
        if (villager.level().isClientSide) {
            return;
        }

        AngerTarget angerTarget = ANGER_TARGETS.get(villager.getUUID());
        if (angerTarget == null) {
            clearFletcherRangedState(villager);
            return;
        }

        if (!(villager.level() instanceof ServerLevel level) || level.getGameTime() >= angerTarget.expiresAt()) {
            clearAnger(villager);
            return;
        }

        Entity entity = level.getEntity(angerTarget.targetId());
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || shouldIgnoreAttacker(target)) {
            clearAnger(villager);
            return;
        }

        if (!VillagerCombatRoles.canFightBack(villager)) {
            return;
        }

        suppressVanillaPanic(villager);
        equipCombatWeapon(villager);
        villager.setAggressive(true);
        villager.setChasing(true);
        villager.setTarget(target);
        if (villager.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.75D);
        }

        handleDefensiveRole(villager, level.getGameTime());

        double distanceSqr = villager.distanceToSqr(target);
        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (tryFletcherRangedAttack(villager, target, level, distanceSqr)) {
            return;
        }

        villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
        if (!VillagerCombatRoles.isFletcher(villager) && canMeleeHit(villager, target) && attackReady(villager, level.getGameTime())) {
            villager.swing(selectAttackHand(villager), true);
            target.hurt(villager.damageSources().mobAttack(villager), VillagerCombatRoles.meleeDamage(villager));
            NEXT_ATTACK_TICKS.put(villager.getUUID(), level.getGameTime() + VillagerCombatRoles.attackCooldown(villager));
        }
    }

    private static void anger(Villager villager, LivingEntity attacker) {
        if (shouldIgnoreAttacker(attacker) || !villager.isAlive() || villager.isBaby() || attacker == villager) {
            return;
        }

        long expiresAt = villager.level().getGameTime() + CommonfolkConfig.AGGRO_DURATION_TICKS.get();
        ANGER_TARGETS.put(villager.getUUID(), new AngerTarget(attacker.getUUID(), expiresAt));
    }

    private static void clearAnger(Villager villager) {
        clearAnger(villager, true);
    }

    private static void clearAnger(Villager villager, boolean restoreWeapon) {
        ANGER_TARGETS.remove(villager.getUUID());
        NEXT_ATTACK_TICKS.remove(villager.getUUID());
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        clearFletcherRangedState(villager);
        if (restoreWeapon) {
            restoreTemporaryWeapon(villager);
        } else {
            TEMPORARY_WEAPONS.remove(villager.getUUID());
        }
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
        villager.getNavigation().stop();
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

    private static boolean attackReady(Villager villager, long gameTime) {
        return gameTime >= NEXT_ATTACK_TICKS.getOrDefault(villager.getUUID(), 0L);
    }

    private static boolean canMeleeHit(Villager villager, LivingEntity target) {
        // Use hitbox-based reach so contact is consistent regardless of center-point offsets.
        return villager.getBoundingBox().inflate(1.0D).intersects(target.getBoundingBox());
    }

    private static void suppressVanillaPanic(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
        villager.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
    }

    private static void handleDefensiveRole(Villager villager, long gameTime) {
        if (gameTime < NEXT_SPECIAL_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        if (VillagerCombatRoles.isFarmer(villager)
                && CommonfolkConfig.FARMERS_USE_BREAD.get()
                && villager.getHealth() < villager.getMaxHealth() * 0.6F) {
            villager.heal(4.0F);
            NEXT_SPECIAL_TICKS.put(villager.getUUID(), gameTime + 120L);
        } else if (VillagerCombatRoles.isCleric(villager)
                && CommonfolkConfig.CLERICS_USE_POTIONS.get()
                && villager.getHealth() < villager.getMaxHealth() * 0.7F) {
            villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            NEXT_SPECIAL_TICKS.put(villager.getUUID(), gameTime + 180L);
        }
    }

    private static boolean tryFletcherRangedAttack(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (!VillagerCombatRoles.isFletcher(villager)
                || !CommonfolkConfig.FLETCHERS_FIGHT_BACK.get()
                || distanceSqr > FLETCHER_MAX_RANGED_DISTANCE_SQR) {
            return false;
        }

        boolean hasLineOfSight = villager.hasLineOfSight(target);
        int seeTime = updateFletcherSeeTime(villager, hasLineOfSight);

        ItemStack rangedWeapon = villager.getMainHandItem().isEmpty() ? villager.getOffhandItem() : villager.getMainHandItem();
        boolean usingCrossbow = rangedWeapon.is(Items.CROSSBOW);
        boolean usingBow = rangedWeapon.is(Items.BOW);
        if (!usingCrossbow && !usingBow) {
            return false;
        }

        if (usingCrossbow) {
            handleCrossbowAttack(villager, target, level, distanceSqr, hasLineOfSight, seeTime);
        } else {
            handleBowAttack(villager, target, level, distanceSqr, hasLineOfSight, seeTime);
        }
        return true;
    }

    private static int updateFletcherSeeTime(Villager villager, boolean hasLineOfSight) {
        int seeTime = FLETCHER_SEE_TIME.getOrDefault(villager.getUUID(), 0);
        boolean couldSeeLastTick = seeTime > 0;
        if (hasLineOfSight != couldSeeLastTick) {
            seeTime = 0;
        }

        seeTime += hasLineOfSight ? 1 : -1;
        FLETCHER_SEE_TIME.put(villager.getUUID(), seeTime);
        return seeTime;
    }

    private static void handleBowAttack(
            Villager villager,
            LivingEntity target,
            ServerLevel level,
            double distanceSqr,
            boolean hasLineOfSight,
            int seeTime
    ) {
        if (distanceSqr <= 100.0D && seeTime >= 20) {
            villager.getNavigation().stop();
        } else {
            villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
        }

        if (villager.isUsingItem()) {
            if (!hasLineOfSight && seeTime < -60) {
                villager.stopUsingItem();
                return;
            }

            if (hasLineOfSight) {
                int drawTicks = villager.getTicksUsingItem();
                if (drawTicks >= FLETCHER_BOW_DRAW_TICKS) {
                    villager.stopUsingItem();
                    fireBowLikeIllusioner(villager, target, level, BowItem.getPowerForTime(drawTicks));
                    FLETCHER_ATTACK_DELAY.put(villager.getUUID(), FLETCHER_BOW_ATTACK_INTERVAL);
                }
            }
            return;
        }

        int attackDelay = FLETCHER_ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            FLETCHER_ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
            return;
        }

        if (seeTime >= -60) {
            villager.startUsingItem(ProjectileUtil.getWeaponHoldingHand(villager, item -> item instanceof BowItem));
        }
    }

    private static void fireBowLikeIllusioner(Villager villager, LivingEntity target, ServerLevel level, float power) {
        ItemStack bowStack = villager.getItemInHand(ProjectileUtil.getWeaponHoldingHand(villager, item -> item instanceof BowItem));
        ItemStack ammo = villager.getProjectile(bowStack);
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW);
        }

        AbstractArrow arrow = ProjectileUtil.getMobArrow(villager, ammo, power, bowStack);
        if (bowStack.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow, ammo, bowStack);
        }

        double dx = target.getX() - villager.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, (float) (14 - level.getDifficulty().getId() * 4));
        villager.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
    }

    private static void handleCrossbowAttack(
            Villager villager,
            LivingEntity target,
            ServerLevel level,
            double distanceSqr,
            boolean hasLineOfSight,
            int seeTime
    ) {
        int attackDelay = FLETCHER_ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        CrossbowState state = FLETCHER_CROSSBOW_STATE.getOrDefault(villager.getUUID(), CrossbowState.UNCHARGED);
        boolean shouldMove = (distanceSqr > 64.0D || seeTime < 5) && attackDelay == 0;
        if (shouldMove) {
            villager.getNavigation().moveTo(target, state == CrossbowState.UNCHARGED ? VillagerCombatRoles.movementSpeed(villager) : 0.25D);
        } else {
            villager.getNavigation().stop();
        }

        if (state == CrossbowState.UNCHARGED) {
            if (!shouldMove) {
                villager.startUsingItem(ProjectileUtil.getWeaponHoldingHand(villager, item -> item instanceof CrossbowItem));
                FLETCHER_CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.CHARGING);
            }
            return;
        }

        if (state == CrossbowState.CHARGING) {
            if (!villager.isUsingItem()) {
                FLETCHER_CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.UNCHARGED);
                return;
            }

            ItemStack using = villager.getUseItem();
            int chargeTicks = villager.getTicksUsingItem();
            if (chargeTicks >= CrossbowItem.getChargeDuration(using, villager)) {
                villager.releaseUsingItem();
                ensureCrossbowMarkedCharged(villager);
                FLETCHER_CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.CHARGED);
                FLETCHER_ATTACK_DELAY.put(villager.getUUID(), nextCrossbowPostLoadDelay(villager));
            }
            return;
        }

        if (state == CrossbowState.CHARGED) {
            if (attackDelay > 0) {
                FLETCHER_ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
                return;
            }
            FLETCHER_CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.READY_TO_ATTACK);
            return;
        }

        if (FLETCHER_CROSSBOW_STATE.get(villager.getUUID()) == CrossbowState.READY_TO_ATTACK && hasLineOfSight) {
            fireCrossbowLikePillager(villager, target, level);
            FLETCHER_CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.UNCHARGED);
        }
    }

    private static void fireCrossbowLikePillager(Villager villager, LivingEntity target, ServerLevel level) {
        InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(villager, item -> item instanceof CrossbowItem);
        ItemStack weapon = villager.getItemInHand(hand);
        if (!(weapon.getItem() instanceof CrossbowItem crossbowItem)) {
            return;
        }

        if (!CrossbowItem.isCharged(weapon)) {
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(new ItemStack(Items.ARROW))));
        }

        crossbowItem.performShooting(level, villager, hand, weapon, 1.6F, (float) (14 - level.getDifficulty().getId() * 4), target);
    }

    private static InteractionHand selectAttackHand(Villager villager) {
        return villager.getMainHandItem().isEmpty() && !villager.getOffhandItem().isEmpty()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }

    private static void updateVillagerSwing(Villager villager) {
        int swingDuration = Math.max(1, villager.getCurrentSwingDuration());
        if (villager.swinging) {
            villager.swingTime++;
            if (villager.swingTime >= swingDuration) {
                villager.swingTime = 0;
                villager.swinging = false;
            }
        } else {
            villager.swingTime = 0;
        }

        villager.attackAnim = (float) villager.swingTime / (float) swingDuration;
    }

    private static void equipCombatWeapon(Villager villager) {
        TemporaryWeaponState state = TEMPORARY_WEAPONS.get(villager.getUUID());
        if (state != null) {
            // Preserve runtime components (charged projectiles, durability) while angry.
            if (!ItemStack.isSameItem(villager.getMainHandItem(), state.equippedWeapon())) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, state.equippedWeapon().copy());
                villager.setDropChance(EquipmentSlot.MAINHAND, currentCombatWeaponDropChance());
            }
            return;
        }

        ItemStack weapon = VillagerCombatRoles.preferredWeapon(villager);
        if (weapon.isEmpty()) {
            return;
        }

        ItemStack previousMainHand = villager.getMainHandItem().copy();
        ItemStack equippedWeapon = prepareCombatWeapon(villager, weapon.copy());
        float previousDropChance = Mob.DEFAULT_EQUIPMENT_DROP_CHANCE;
        TEMPORARY_WEAPONS.put(villager.getUUID(), new TemporaryWeaponState(previousMainHand, equippedWeapon.copy(), previousDropChance));
        villager.setItemSlot(EquipmentSlot.MAINHAND, equippedWeapon);
        villager.setDropChance(EquipmentSlot.MAINHAND, currentCombatWeaponDropChance());
        seedInitialFletcherRangedDelay(villager, equippedWeapon);
    }

    private static void restoreTemporaryWeapon(Villager villager) {
        TemporaryWeaponState state = TEMPORARY_WEAPONS.remove(villager.getUUID());
        if (state == null) {
            return;
        }

        if (ItemStack.isSameItemSameComponents(villager.getMainHandItem(), state.equippedWeapon())) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, state.previousMainHand().copy());
        }
        villager.setDropChance(EquipmentSlot.MAINHAND, state.previousDropChance());
    }

    private static ItemStack prepareCombatWeapon(Villager villager, ItemStack weapon) {
        if (weapon.is(Items.BOOK) || weapon.is(Items.BREAD)) {
            return weapon;
        }

        if (!(villager.level() instanceof ServerLevel level) || level.getDifficulty() != Difficulty.HARD) {
            return weapon;
        }

        DifficultyInstance difficulty = level.getCurrentDifficultyAt(villager.blockPosition());
        if (villager.getRandom().nextFloat() < currentCombatWeaponEnchantChance()) {
            EnchantmentHelper.enchantItemFromProvider(
                    weapon,
                    level.registryAccess(),
                    VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT,
                    difficulty,
                    villager.getRandom()
            );
        }

        return weapon;
    }

    private static float currentCombatWeaponDropChance() {
        return CommonfolkConfig.COMBAT_WEAPON_DROP_CHANCE.get().floatValue();
    }

    private static float currentCombatWeaponEnchantChance() {
        return CommonfolkConfig.COMBAT_WEAPON_ENCHANT_CHANCE.get().floatValue();
    }

    private static void clearFletcherRangedState(Villager villager) {
        FLETCHER_SEE_TIME.remove(villager.getUUID());
        FLETCHER_ATTACK_DELAY.remove(villager.getUUID());
        FLETCHER_CROSSBOW_STATE.remove(villager.getUUID());
        if (villager.isUsingItem()) {
            villager.stopUsingItem();
        }
    }

    private static void seedInitialFletcherRangedDelay(Villager villager, ItemStack equippedWeapon) {
        if (!VillagerCombatRoles.isFletcher(villager) || (!equippedWeapon.is(Items.BOW) && !equippedWeapon.is(Items.CROSSBOW))) {
            return;
        }

        FLETCHER_ATTACK_DELAY.put(villager.getUUID(), FLETCHER_INITIAL_RANGED_WINDUP_TICKS);
    }

    private static int nextCrossbowPostLoadDelay(Villager villager) {
        return FLETCHER_CROSSBOW_POST_LOAD_DELAY_BASE_TICKS
                + villager.getRandom().nextInt(FLETCHER_CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS);
    }

    private static void ensureCrossbowMarkedCharged(Villager villager) {
        InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(villager, item -> item instanceof CrossbowItem);
        ItemStack weapon = villager.getItemInHand(hand);
        if (weapon.is(Items.CROSSBOW) && !CrossbowItem.isCharged(weapon)) {
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(new ItemStack(Items.ARROW))));
        }
    }

    private record AngerTarget(UUID targetId, long expiresAt) {
    }

    private record TemporaryWeaponState(ItemStack previousMainHand, ItemStack equippedWeapon, float previousDropChance) {
    }

    private enum CrossbowState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK
    }
}
