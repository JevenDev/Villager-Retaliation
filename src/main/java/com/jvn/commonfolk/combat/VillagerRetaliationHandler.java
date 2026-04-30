package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerRules;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationHandler {
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SPECIAL_TICKS = new HashMap<>();
    private static final Map<UUID, Double> ORIGINAL_MOVEMENT_SPEEDS = new HashMap<>();
    private static final Map<UUID, TemporaryWeaponState> TEMPORARY_WEAPONS = new HashMap<>();

    private VillagerRetaliationHandler() {
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (!VillagerCombatRoles.isCleric(villager) || !CommonfolkConfig.CLERICS_USE_POTIONS.get()) {
            return;
        }

        float damage = event.getAmount();
        DamageSource source = event.getSource();
        if (source.getEntity() == villager) {
            event.setAmount(0.0F);
            return;
        }
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            event.setAmount(damage * 0.15F);
        }
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

        resolveAttacker(event.getSource()).ifPresent(attacker -> {
            anger(villager, attacker);
            if (!CommonfolkConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                angerNearbyVillagers(villager, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
            }
        });
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        Entity deceased = event.getEntity();
        if (deceased instanceof Villager villager) {
            // Keep temporary combat weapons equipped on death so vanilla equipment drops can roll.
            clearAnger(villager, false);
        } else if (!(deceased instanceof WanderingTrader)) {
            return;
        }

        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !CommonfolkConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()
                || !(deceased.level() instanceof ServerLevel level)) {
            return;
        }
        if (deceased instanceof Villager villager && villager.isBaby()) {
            return;
        }

        Optional<LivingEntity> attacker = resolveAttacker(event.getSource());
        if (attacker.isEmpty() || shouldIgnoreAttacker(attacker.get())) {
            return;
        }

        angerNearbyVillagers(deceased, attacker.get(), CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }

        if (ANGER_TARGETS.containsKey(villager.getUUID()) && CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)) {
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

        if (CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            tryAcquireHostileTarget(villager);
        }

        AngerTarget angerTarget = ANGER_TARGETS.get(villager.getUUID());
        if (angerTarget == null) {
            VillagerRangedCombatHelper.clearState(villager);
            if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
                restoreCombatMovement(villager);
                return;
            }
            if (VillagerClericPotionHelper.tryOutOfCombatMilk(villager)) {
                restoreCombatMovement(villager);
                return;
            }
            VillagerClericPotionHelper.clearState(villager);
            restoreCombatMovement(villager);
            return;
        }

        if (!(villager.level() instanceof ServerLevel level)) {
            clearAnger(villager);
            return;
        }

        Entity entity = level.getEntity(angerTarget.targetId());
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || shouldIgnoreAttacker(target)) {
            clearAnger(villager);
            return;
        }

        if (!VillagerCombatRoles.canFightBack(villager)) {
            clearAnger(villager);
            return;
        }

        suppressVanillaPanic(villager);
        villager.setAggressive(true);
        villager.setChasing(true);
        villager.setTarget(target);

        long gameTime = level.getGameTime();
        if (villager.hasLineOfSight(target)) {
            ANGER_TARGETS.put(villager.getUUID(), angerTarget.withLastSeenGameTick(gameTime));
        } else if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
            clearAnger(villager);
            return;
        }

        if (tryAcquireGroundWeapon(villager)) {
            return;
        }

        equipCombatWeapon(villager);

        double distanceSqr = villager.distanceToSqr(target);
        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
            return;
        }

        boostCombatMovement(villager);

        handleDefensiveRole(villager, gameTime);

        if (VillagerClericPotionHelper.tryCombat(villager, target, level, distanceSqr)) {
            return;
        }
        if (isUsingRangedCombatMode(villager) && VillagerRangedCombatHelper.tryAttack(villager, target, level, distanceSqr)) {
            return;
        }

        if (CommonfolkPotionUtil.shouldSuppressCombatWhileUsingPotion(villager)) {
            villager.getNavigation().stop();
            return;
        }

        villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
        if (canUseMeleeCombatMode(villager) && canMeleeHit(villager, target) && attackReady(villager, gameTime)) {
            InteractionHand attackHand = selectAttackHand(villager);
            villager.swing(attackHand, true);
            performMeleeAttack(villager, target, attackHand);
            NEXT_ATTACK_TICKS.put(villager.getUUID(), gameTime + VillagerCombatRoles.attackCooldown(villager));
        }
    }

    private static void anger(Villager villager, LivingEntity attacker) {
        if (shouldIgnoreAttacker(attacker) || !villager.isAlive() || villager.isBaby() || attacker == villager) {
            return;
        }

        long gameTime = villager.level().getGameTime();
        ANGER_TARGETS.put(villager.getUUID(), new AngerTarget(attacker.getUUID(), gameTime));
    }

    private static void tryAcquireHostileTarget(Villager villager) {
        if (ANGER_TARGETS.containsKey(villager.getUUID())
                || !villager.isAlive()
                || !CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canFightBack(villager)) {
            return;
        }

        villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != villager)
                .ifPresent(target -> anger(villager, target));
    }

    private static boolean tryAcquireGroundWeapon(Villager villager) {
        if (!villager.isAlive()
                || !CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canScavengeGroundWeapons(villager)
                || CommonfolkVillagerWeapons.hasUsableWeapon(villager)
                || !CommonfolkVillagerCombatUtil.isThreatened(villager)) {
            return false;
        }

        Optional<ItemEntity> nearestWeapon = CommonfolkVillagerWeapons.findNearestWeapon(villager);
        if (nearestWeapon.isEmpty()) {
            return false;
        }

        ItemEntity itemEntity = nearestWeapon.get();
        if (villager.distanceToSqr(itemEntity) <= CommonfolkVillagerWeapons.WEAPON_PICKUP_REACH_SQR) {
            discardTemporaryWeapon(villager);
            CommonfolkVillagerWeapons.equipGroundWeapon(villager, itemEntity);
            VillagerRangedCombatHelper.seedInitialAttackDelay(villager, villager.getMainHandItem());
            return false;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(
                villager,
                itemEntity,
                (float) VillagerCombatRoles.movementSpeed(villager),
                0
        );
        return true;
    }

    private static void clearAnger(Villager villager) {
        clearAnger(villager, true);
    }

    private static void clearAnger(Villager villager, boolean restoreWeapon) {
        ANGER_TARGETS.remove(villager.getUUID());
        NEXT_ATTACK_TICKS.remove(villager.getUUID());
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        VillagerRangedCombatHelper.clearState(villager);
        boolean preservePotionUse = VillagerClericPotionHelper.tickDrinkingIfActive(villager);
        if (!preservePotionUse) {
            VillagerClericPotionHelper.clearState(villager);
        }
        restoreCombatMovement(villager);
        if (restoreWeapon && !preservePotionUse) {
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

    private static boolean isUsingRangedCombatMode(Villager villager) {
        return CommonfolkVillagerWeapons.isRangedWeapon(CommonfolkVillagerWeapons.getPrimaryWeapon(villager));
    }

    private static boolean canUseMeleeCombatMode(Villager villager) {
        return !isUsingRangedCombatMode(villager);
    }

    private static boolean canMeleeHit(Villager villager, LivingEntity target) {
        // Use hitbox-based reach so contact is consistent regardless of center-point offsets.
        return villager.getBoundingBox().inflate(1.0D).intersects(target.getBoundingBox());
    }

    private static void performMeleeAttack(Villager villager, LivingEntity target, InteractionHand attackHand) {
        ItemStack weapon = villager.getItemInHand(attackHand);
        if (weapon.isEmpty()) {
            weapon = villager.getWeaponItem();
        }

        DamageSource damageSource = villager.damageSources().mobAttack(villager);
        float damage = VillagerCombatRoles.meleeDamage(villager);
        float knockback = 0.0F;
        if (villager.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, weapon, target, damageSource, damage);
            knockback = EnchantmentHelper.modifyKnockback(serverLevel, weapon, target, damageSource, knockback);
        }

        boolean hit = target.hurt(damageSource, damage);
        if (!hit) {
            return;
        }

        if (knockback > 0.0F) {
            target.knockback(
                    knockback * 0.5F,
                    Mth.sin(villager.getYRot() * ((float) Math.PI / 180.0F)),
                    -Mth.cos(villager.getYRot() * ((float) Math.PI / 180.0F))
            );
            villager.setDeltaMovement(villager.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
        }

        if (villager.level() instanceof ServerLevel serverLevel) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, weapon);
        }
        villager.setLastHurtMob(target);
    }

    private static void angerNearbyVillagers(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != sourceEntity && !nearby.isBaby()) {
                anger(nearby, attacker);
            }
        }
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
        }
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
        if (VillagerClericPotionHelper.isActivelyHandlingPotion(villager)) {
            return;
        }

        if (CommonfolkVillagerWeapons.maintainAcquiredWeaponAuthority(villager)) {
            discardTemporaryWeapon(villager);
            return;
        }

        TemporaryWeaponState state = TEMPORARY_WEAPONS.get(villager.getUUID());
        if (state != null) {
            // Preserve runtime components (charged projectiles, durability) while angry.
            if (!ItemStack.isSameItem(villager.getMainHandItem(), state.equippedWeapon())) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, state.equippedWeapon().copy());
                villager.setDropChance(EquipmentSlot.MAINHAND, currentCombatWeaponDropChance());
            }
            return;
        }

        if (CommonfolkVillagerWeapons.hasUsableWeapon(villager)) {
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
        VillagerRangedCombatHelper.seedInitialAttackDelay(villager, equippedWeapon);
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

    private static void discardTemporaryWeapon(Villager villager) {
        TemporaryWeaponState state = TEMPORARY_WEAPONS.remove(villager.getUUID());
        if (state != null) {
            villager.setDropChance(EquipmentSlot.MAINHAND, state.previousDropChance());
        }
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

    private static void boostCombatMovement(Villager villager) {
        AttributeInstance movementSpeed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        ORIGINAL_MOVEMENT_SPEEDS.putIfAbsent(villager.getUUID(), movementSpeed.getBaseValue());
        movementSpeed.setBaseValue(0.75D);
    }

    private static void restoreCombatMovement(Villager villager) {
        AttributeInstance movementSpeed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        Double originalBaseSpeed = ORIGINAL_MOVEMENT_SPEEDS.remove(villager.getUUID());
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
