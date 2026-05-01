package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class WanderingTraderRetaliationHandler {
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, TraderAttackPlan> ATTACK_PLANS = new HashMap<>();
    private static final Map<UUID, Integer> RETALIATION_CHARGES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();
    private static final Map<UUID, ItemStack> PREVIOUS_MAIN_HAND = new HashMap<>();
    private static final Map<UUID, Double> ORIGINAL_MOVEMENT_SPEEDS = new HashMap<>();
    private static final double TRADER_MOVE_SPEED = 0.65D;
    private static final int MAX_ATTACK_ATTEMPTS = 3;
    private static final float ATTACK_TRIGGER_CHANCE = 0.33F;
    private static final int BASE_ATTACK_COOLDOWN_TICKS = 20;
    private static final double MAX_BOW_OR_CROSSBOW_DISTANCE_SQR = 225.0D;
    private static final double TRIDENT_MAX_DISTANCE_SQR = 144.0D;
    private static final List<ItemStack> WEAPON_POOL = List.of(
            new ItemStack(Items.WOODEN_SWORD),
            new ItemStack(Items.STONE_SWORD),
            new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.GOLDEN_SWORD),
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.NETHERITE_SWORD),
            new ItemStack(Items.WOODEN_AXE),
            new ItemStack(Items.STONE_AXE),
            new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.GOLDEN_AXE),
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.NETHERITE_AXE),
            new ItemStack(Items.WOODEN_PICKAXE),
            new ItemStack(Items.STONE_PICKAXE),
            new ItemStack(Items.IRON_PICKAXE),
            new ItemStack(Items.GOLDEN_PICKAXE),
            new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.NETHERITE_PICKAXE),
            new ItemStack(Items.WOODEN_SHOVEL),
            new ItemStack(Items.STONE_SHOVEL),
            new ItemStack(Items.IRON_SHOVEL),
            new ItemStack(Items.GOLDEN_SHOVEL),
            new ItemStack(Items.DIAMOND_SHOVEL),
            new ItemStack(Items.NETHERITE_SHOVEL),
            new ItemStack(Items.WOODEN_HOE),
            new ItemStack(Items.STONE_HOE),
            new ItemStack(Items.IRON_HOE),
            new ItemStack(Items.GOLDEN_HOE),
            new ItemStack(Items.DIAMOND_HOE),
            new ItemStack(Items.NETHERITE_HOE),
            new ItemStack(Items.BOW),
            new ItemStack(Items.CROSSBOW),
            new ItemStack(Items.TRIDENT),
            new ItemStack(Items.MACE)
    );

    private WanderingTraderRetaliationHandler() {
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.WANDERING_TRADER, Attributes.ATTACK_DAMAGE)) {
            event.add(EntityType.WANDERING_TRADER, Attributes.ATTACK_DAMAGE, 1.0D);
        }
        if (!event.has(EntityType.WANDERING_TRADER, Attributes.ATTACK_KNOCKBACK)) {
            event.add(EntityType.WANDERING_TRADER, Attributes.ATTACK_KNOCKBACK, 0.0D);
        }
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getEntity() instanceof WanderingTrader trader) {
            resolveAttacker(event.getSource())
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .ifPresent(player -> anger(trader, player));
            return;
        }

        if (!(event.getEntity() instanceof TraderLlama traderLlama)) {
            return;
        }

        if (!(traderLlama.getLeashHolder() instanceof WanderingTrader trader)) {
            return;
        }

        resolveAttacker(event.getSource())
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .ifPresent(player -> anger(trader, player));
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) {
            return;
        }

        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(trader);
            return;
        }

        Optional<LivingEntity> attacker = resolveAttacker(event.getSource());
        if (attacker.isPresent()
                && attacker.get() instanceof Player player
                && !shouldIgnoreAttacker(player)) {
            angerNearbyTraders(trader, player, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
        }

        clearAnger(trader);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof WanderingTrader trader) || trader.level().isClientSide) {
            return;
        }

        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(trader);
            return;
        }

        AngerTarget angerTarget = ANGER_TARGETS.get(trader.getUUID());
        if (angerTarget == null) {
            restoreMovementSpeed(trader);
            return;
        }

        if (!(trader.level() instanceof ServerLevel level)) {
            clearAnger(trader);
            return;
        }

        Entity targetEntity = level.getEntity(angerTarget.targetId());
        if (!(targetEntity instanceof Player targetPlayer) || !targetPlayer.isAlive() || shouldIgnoreAttacker(targetPlayer)) {
            clearAnger(trader);
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
            clearAnger(trader);
            return;
        }

        if (trader.hasLineOfSight(targetPlayer)) {
            ANGER_TARGETS.put(trader.getUUID(), angerTarget.withLastSeenGameTick(gameTime));
        }

        trader.setAggressive(true);
        trader.setTarget(targetPlayer);
        trader.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);
        boostMovementSpeed(trader);

        TraderAttackPlan plan = ATTACK_PLANS.get(trader.getUUID());
        if (plan == null) {
            plan = TraderAttackPlan.randomized(trader);
            ATTACK_PLANS.put(trader.getUUID(), plan);
        }

        int availableCharges = RETALIATION_CHARGES.getOrDefault(trader.getUUID(), 0);
        if (availableCharges <= 0) {
            restoreMainHand(trader);
            trader.getNavigation().moveTo(targetPlayer, TRADER_MOVE_SPEED);
            return;
        }

        if (gameTime < NEXT_ATTACK_TICKS.getOrDefault(trader.getUUID(), 0L)) {
            trader.getNavigation().moveTo(targetPlayer, TRADER_MOVE_SPEED);
            return;
        }

        int attackIndex = chooseAttackIndex(plan, trader);
        ItemStack selectedWeapon = plan.attackAt(attackIndex).copy();
        equipMainHand(trader, selectedWeapon);

        boolean inPosition = isRangedWeapon(selectedWeapon)
                ? canPerformRangedAttack(trader, targetPlayer, selectedWeapon)
                : canMeleeHit(trader, targetPlayer);
        if (!inPosition) {
            trader.getNavigation().moveTo(targetPlayer, TRADER_MOVE_SPEED);
            NEXT_ATTACK_TICKS.put(trader.getUUID(), gameTime + 5L);
            return;
        }

        int remainingCharges = availableCharges - 1;
        if (trader.getRandom().nextFloat() >= ATTACK_TRIGGER_CHANCE) {
            RETALIATION_CHARGES.put(trader.getUUID(), remainingCharges);
            NEXT_ATTACK_TICKS.put(trader.getUUID(), gameTime + BASE_ATTACK_COOLDOWN_TICKS);
            return;
        }

        performAttack(trader, targetPlayer, level, selectedWeapon);
        ATTACK_PLANS.put(trader.getUUID(), plan.withLastAttackIndex(attackIndex));
        RETALIATION_CHARGES.put(trader.getUUID(), remainingCharges);
        NEXT_ATTACK_TICKS.put(trader.getUUID(), gameTime + attackCooldownFor(selectedWeapon));
    }

    public static boolean blockTradingIfHostile(WanderingTrader trader, Player player) {
        if (trader.level().isClientSide || !trader.isAlive() || !player.isAlive()) {
            return false;
        }

        AngerTarget angerTarget = ANGER_TARGETS.get(trader.getUUID());
        if (angerTarget == null) {
            return false;
        }

        if (!angerTarget.targetId().equals(player.getUUID())) {
            return false;
        }

        spawnMadParticles(trader);
        return true;
    }

    private static void anger(WanderingTrader trader, Player attacker) {
        if (!trader.isAlive() || shouldIgnoreAttacker(attacker)) {
            return;
        }

        long gameTime = trader.level().getGameTime();
        ANGER_TARGETS.put(trader.getUUID(), new AngerTarget(attacker.getUUID(), gameTime));
        RETALIATION_CHARGES.put(
                trader.getUUID(),
                Math.min(MAX_ATTACK_ATTEMPTS, RETALIATION_CHARGES.getOrDefault(trader.getUUID(), 0) + 1)
        );
        ATTACK_PLANS.computeIfAbsent(trader.getUUID(), ignored -> TraderAttackPlan.randomized(trader));
    }

    private static void clearAnger(WanderingTrader trader) {
        ANGER_TARGETS.remove(trader.getUUID());
        ATTACK_PLANS.remove(trader.getUUID());
        RETALIATION_CHARGES.remove(trader.getUUID());
        NEXT_ATTACK_TICKS.remove(trader.getUUID());
        trader.setAggressive(false);
        trader.setTarget(null);
        trader.getNavigation().stop();
        restoreMainHand(trader);
        restoreMovementSpeed(trader);
    }

    private static void equipMainHand(WanderingTrader trader, ItemStack weapon) {
        PREVIOUS_MAIN_HAND.computeIfAbsent(trader.getUUID(), ignored -> trader.getMainHandItem().copy());
        trader.setItemSlot(EquipmentSlot.MAINHAND, weapon);
    }

    private static void restoreMainHand(WanderingTrader trader) {
        ItemStack previous = PREVIOUS_MAIN_HAND.remove(trader.getUUID());
        if (previous != null) {
            trader.setItemSlot(EquipmentSlot.MAINHAND, previous);
        }
    }

    private static void boostMovementSpeed(WanderingTrader trader) {
        AttributeInstance movementSpeed = trader.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        ORIGINAL_MOVEMENT_SPEEDS.putIfAbsent(trader.getUUID(), movementSpeed.getBaseValue());
        movementSpeed.setBaseValue(0.75D);
    }

    private static void restoreMovementSpeed(WanderingTrader trader) {
        AttributeInstance movementSpeed = trader.getAttribute(Attributes.MOVEMENT_SPEED);
        Double original = ORIGINAL_MOVEMENT_SPEEDS.remove(trader.getUUID());
        if (movementSpeed != null && original != null) {
            movementSpeed.setBaseValue(original);
        }
    }

    private static int chooseAttackIndex(TraderAttackPlan plan, WanderingTrader trader) {
        int lastIndex = plan.lastAttackIndex();
        if (lastIndex < 0) {
            return trader.getRandom().nextInt(3);
        }

        float roll = trader.getRandom().nextFloat();
        if (roll < 0.5F) {
            return lastIndex;
        }

        int firstOther = (lastIndex + 1) % 3;
        int secondOther = (lastIndex + 2) % 3;
        return roll < 0.75F ? firstOther : secondOther;
    }

    private static boolean canMeleeHit(WanderingTrader trader, LivingEntity target) {
        return trader.getBoundingBox().inflate(1.0D).intersects(target.getBoundingBox());
    }

    private static boolean canPerformRangedAttack(WanderingTrader trader, LivingEntity target, ItemStack weapon) {
        if (!trader.hasLineOfSight(target)) {
            return false;
        }

        double distanceSqr = trader.distanceToSqr(target);
        if (isBowWeapon(weapon) || isCrossbowWeapon(weapon)) {
            return distanceSqr <= MAX_BOW_OR_CROSSBOW_DISTANCE_SQR;
        }
        if (isTridentWeapon(weapon)) {
            return distanceSqr <= TRIDENT_MAX_DISTANCE_SQR;
        }
        return false;
    }

    private static void performAttack(WanderingTrader trader, LivingEntity target, ServerLevel level, ItemStack weapon) {
        if (isBowWeapon(weapon)) {
            fireBowLikeIllusioner(trader, target, level);
            return;
        }
        if (isCrossbowWeapon(weapon)) {
            fireCrossbowLikePillager(trader, target, level);
            return;
        }
        if (isTridentWeapon(weapon)) {
            throwTridentLikeDrowned(trader, target, level);
            return;
        }

        trader.swing(InteractionHand.MAIN_HAND, true);
        trader.doHurtTarget(target);
    }

    private static void fireBowLikeIllusioner(WanderingTrader trader, LivingEntity target, ServerLevel level) {
        ItemStack bowStack = trader.getMainHandItem();
        ItemStack ammo = trader.getProjectile(bowStack);
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW);
        }

        AbstractArrow arrow = ProjectileUtil.getMobArrow(trader, ammo, 1.0F, bowStack);
        if (bowStack.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow, ammo, bowStack);
        }

        double dx = target.getX() - trader.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - trader.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, (float) (14 - level.getDifficulty().getId() * 4));
        trader.swing(InteractionHand.MAIN_HAND, true);
        trader.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (trader.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
    }

    private static void fireCrossbowLikePillager(WanderingTrader trader, LivingEntity target, ServerLevel level) {
        ItemStack weapon = trader.getMainHandItem();
        if (!(weapon.getItem() instanceof CrossbowItem crossbowItem)) {
            return;
        }

        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(resolveDefaultCrossbowProjectile(trader, weapon))));
        trader.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        weapon = trader.getMainHandItem();

        crossbowItem.performShooting(level, trader, InteractionHand.MAIN_HAND, weapon, 1.6F, (float) (14 - level.getDifficulty().getId() * 4), target);
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        trader.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        trader.swing(InteractionHand.MAIN_HAND, true);
    }

    private static ItemStack resolveDefaultCrossbowProjectile(WanderingTrader trader, ItemStack crossbow) {
        ItemStack projectile = trader.getProjectile(crossbow);
        if (projectile.isEmpty()) {
            projectile = new ItemStack(Items.ARROW);
        } else {
            projectile = projectile.copyWithCount(1);
        }
        projectile.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        return projectile;
    }

    private static void throwTridentLikeDrowned(WanderingTrader trader, LivingEntity target, ServerLevel level) {
        ItemStack thrownStack = new ItemStack(Items.TRIDENT);
        ThrownTrident thrownTrident = new ThrownTrident(level, trader, thrownStack);
        thrownTrident.pickup = AbstractArrow.Pickup.DISALLOWED;

        double dx = target.getX() - trader.getX();
        double dy = target.getY(0.3333333333333333D) - thrownTrident.getY();
        double dz = target.getZ() - trader.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        thrownTrident.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, (float) (14 - level.getDifficulty().getId() * 4));
        level.addFreshEntity(thrownTrident);

        trader.swing(InteractionHand.MAIN_HAND, true);
        trader.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (trader.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    private static int attackCooldownFor(ItemStack weapon) {
        if (isCrossbowWeapon(weapon) || isTridentWeapon(weapon)) {
            return 40;
        }
        if (isBowWeapon(weapon)) {
            return 20;
        }
        return 16;
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        return isBowWeapon(stack) || isCrossbowWeapon(stack) || isTridentWeapon(stack);
    }

    private static boolean isBowWeapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    private static boolean isCrossbowWeapon(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    private static boolean isTridentWeapon(ItemStack stack) {
        return stack.is(Items.TRIDENT);
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

    private static boolean shouldIgnoreAttacker(Player attacker) {
        return attacker.isSpectator()
                || CommonfolkConfig.NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS.get() && attacker.isCreative();
    }

    private static void spawnMadParticles(WanderingTrader trader) {
        if (!(trader.level() instanceof ServerLevel level)) {
            return;
        }

        double y = trader.getY() + trader.getBbHeight() + 0.2D;
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, trader.getX(), y, trader.getZ(), 5, 0.25D, 0.15D, 0.25D, 0.01D);
    }

    private static void angerNearbyTraders(WanderingTrader sourceTrader, Player attacker, double radius) {
        if (!(sourceTrader.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceTrader.getBoundingBox().inflate(radius);
        for (WanderingTrader nearby : level.getEntitiesOfClass(WanderingTrader.class, area)) {
            if (nearby != sourceTrader) {
                anger(nearby, attacker);
            }
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

    private record TraderAttackPlan(ItemStack attackOne, ItemStack attackTwo, ItemStack attackThree, int lastAttackIndex) {
        private static TraderAttackPlan randomized(WanderingTrader trader) {
            return new TraderAttackPlan(randomWeapon(trader), randomWeapon(trader), randomWeapon(trader), -1);
        }

        private ItemStack attackAt(int index) {
            return switch (index) {
                case 0 -> this.attackOne;
                case 1 -> this.attackTwo;
                default -> this.attackThree;
            };
        }

        private TraderAttackPlan withLastAttackIndex(int attackIndex) {
            return new TraderAttackPlan(this.attackOne, this.attackTwo, this.attackThree, attackIndex);
        }

        private static ItemStack randomWeapon(WanderingTrader trader) {
            return WEAPON_POOL.get(trader.getRandom().nextInt(WEAPON_POOL.size())).copy();
        }
    }
}
