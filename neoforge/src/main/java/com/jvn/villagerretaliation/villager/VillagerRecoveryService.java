package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationPotionUtil;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;

/** Universal, player-like recovery state for every villager. */
public final class VillagerRecoveryService {
    private static final String TAG = "VillagerRetaliationRecovery";
    private static final String FOOD = "Food";
    private static final String SATURATION = "Saturation";
    private static final String EXHAUSTION = "Exhaustion";
    private static final String HEAL_TIMER = "HealTimer";
    private static final int MAX_FOOD = 20;
    private static final long SHORTAGE_COOLDOWN = 20L * 60L * 5L;
    private static final double NOTICE_DISTANCE_SQR = 16.0D * 16.0D;
    private static final Map<UUID, Boolean> FORCED_RECOVERY = new HashMap<>();
    private static final Set<UUID> COMBAT_RECOVERY = new HashSet<>();
    private static final Map<UUID, UseState> ACTIVE_USES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SHORTAGE_NOTICE = new HashMap<>();
    private static final String[] SHORTAGE_LINES = {
            "I'm hurt and completely out of food.",
            "I can't recover without something to eat.",
            "No food left—and I really need it.",
            "I've got nothing left to heal with.",
            "I need food or a healing potion.",
            "My supplies are empty. I can't recover.",
            "I'm starving and still wounded.",
            "There isn't a bite of food left.",
            "I need recovery supplies, quickly.",
            "I'm too hungry to mend these wounds."
    };

    private VillagerRecoveryService() {
    }

    public static int foodLevel(Villager villager) {
        return state(villager).food();
    }

    public static float saturationLevel(Villager villager) {
        return state(villager).saturation();
    }

    public static boolean beginForcedRecovery(Villager villager, boolean urgent) {
        if (villager == null || !villager.isAlive() || villager.getHealth() >= villager.getMaxHealth()) {
            return false;
        }
        FORCED_RECOVERY.put(villager.getUUID(), urgent);
        COMBAT_RECOVERY.remove(villager.getUUID());
        return true;
    }

    public static void cancelForcedRecovery(Villager villager) {
        if (villager == null) {
            return;
        }
        FORCED_RECOVERY.remove(villager.getUUID());
        COMBAT_RECOVERY.remove(villager.getUUID());
        cancelUse(villager, true);
    }

    public static boolean isForcingRecovery(Villager villager) {
        return villager != null && (FORCED_RECOVERY.containsKey(villager.getUUID())
                || COMBAT_RECOVERY.contains(villager.getUUID())
                || ACTIVE_USES.containsKey(villager.getUUID()));
    }

    /** Returns true while recovery must own navigation/combat for this tick. */
    public static boolean onVillagerTickPost(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level) || !villager.isAlive()) {
            return false;
        }
        if (VillagerDownedService.isDowned(villager)) {
            return false;
        }

        RecoveryState recovery = state(villager);
        recovery = consumeExhaustion(recovery);
        recovery = tickNaturalRegeneration(level, villager, recovery);
        save(villager, recovery);

        UseState use = ACTIVE_USES.get(villager.getUUID());
        if (use != null) {
            tickUse(level, villager, use);
            return true;
        }
        if (VillagerRetaliationPotionUtil.shouldSuppressCombatWhileUsingPotion(villager)) {
            return false;
        }

        boolean hasCombatTarget = VillagerRetaliationHandler.hasActiveRetaliationTarget(villager)
                || villager.getTarget() != null;
        float healthRatio = villager.getHealth() / Math.max(1.0F, villager.getMaxHealth());
        if (hasCombatTarget && healthRatio < 0.5F) {
            COMBAT_RECOVERY.add(villager.getUUID());
            VillagerRetaliationHandler.clearCustomTarget(villager);
        }

        boolean forced = FORCED_RECOVERY.containsKey(villager.getUUID());
        boolean combatRecovery = COMBAT_RECOVERY.contains(villager.getUUID());
        if (forced && villager.getHealth() >= villager.getMaxHealth()) {
            FORCED_RECOVERY.remove(villager.getUUID());
            return false;
        }
        if (combatRecovery && healthRatio >= 0.5F) {
            COMBAT_RECOVERY.remove(villager.getUUID());
            return false;
        }

        boolean urgent = FORCED_RECOVERY.getOrDefault(villager.getUUID(), false) || combatRecovery;
        ItemStack consumable = selectConsumable(villager, recovery, urgent, healthRatio);
        if (!consumable.isEmpty()) {
            startUse(villager, consumable);
            return urgent || forced;
        }

        boolean canNaturallyHeal = level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                && recovery.food() > 0;
        if ((forced || combatRecovery) && !canNaturallyHeal) {
            FORCED_RECOVERY.remove(villager.getUUID());
            COMBAT_RECOVERY.remove(villager.getUUID());
            if (recovery.food() <= 0 && recovery.saturation() <= 0.0F) maybeReportShortage(level, villager);
            return false;
        }
        if (!forced && !combatRecovery && recovery.food() <= 0 && recovery.saturation() <= 0.0F
                && villager.getHealth() < villager.getMaxHealth()) {
            maybeReportShortage(level, villager);
        }
        return forced || combatRecovery;
    }

    public static void onVillagerUnloaded(Villager villager) {
        if (villager == null) {
            return;
        }
        cancelUse(villager, true);
        FORCED_RECOVERY.remove(villager.getUUID());
        COMBAT_RECOVERY.remove(villager.getUUID());
    }

    public static void clearRuntimeState() {
        FORCED_RECOVERY.clear();
        COMBAT_RECOVERY.clear();
        ACTIVE_USES.clear();
        NEXT_SHORTAGE_NOTICE.clear();
    }

    private static ItemStack selectConsumable(
            Villager villager,
            RecoveryState recovery,
            boolean urgent,
            float healthRatio) {
        if (urgent) {
            if (healthRatio < 0.25F) {
                ItemStack enchanted = take(villager, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
                if (!enchanted.isEmpty()) return enchanted;
            }
            if (healthRatio < 0.5F) {
                ItemStack apple = take(villager, stack -> stack.is(Items.GOLDEN_APPLE));
                if (!apple.isEmpty()) return apple;
            }
            ItemStack potion = take(villager, VillagerRecoveryService::isRecoveryPotion);
            if (!potion.isEmpty()) return potion;
            return take(villager, VillagerRecoveryService::isOrdinaryFood);
        }

        if (recovery.food() < MAX_FOOD) {
            ItemStack food = take(villager, VillagerRecoveryService::isOrdinaryFood);
            if (!food.isEmpty()) return food;
        }
        return take(villager, VillagerRecoveryService::isRecoveryPotion);
    }

    private static ItemStack take(Villager villager, Predicate<ItemStack> predicate) {
        return VillagerInventoryAccess.takeCarriedItem(villager, predicate);
    }

    private static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD);
    }

    private static boolean isOrdinaryFood(ItemStack stack) {
        return isFood(stack)
                && !stack.is(Items.GOLDEN_APPLE)
                && !stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private static boolean isRecoveryPotion(ItemStack stack) {
        return !stack.is(Items.LINGERING_POTION)
                && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION))
                && VillagerRetaliationPotionUtil.isHealingOrRegenerationPotion(stack);
    }

    private static void startUse(Villager villager, ItemStack stack) {
        ItemStack resumeMainHand = villager.getMainHandItem().copy();
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        VillagerRetaliationVillagerEquipment.setVisualMainHand(villager, stack.copy());
        int duration;
        if (stack.is(Items.SPLASH_POTION)) {
            duration = 10;
        } else {
            villager.startUsingItem(InteractionHand.MAIN_HAND);
            duration = Math.max(2, stack.getUseDuration(villager)) - 1;
        }
        ACTIVE_USES.put(villager.getUUID(), new UseState(stack.copy(), resumeMainHand, duration));
    }

    private static void tickUse(ServerLevel level, Villager villager, UseState use) {
        if (use.ticksRemaining() > 1) {
            ACTIVE_USES.put(villager.getUUID(), use.withTicks(use.ticksRemaining() - 1));
            return;
        }
        ACTIVE_USES.remove(villager.getUUID());
        villager.stopUsingItem();
        ItemStack used = use.stack().copy();
        if (isFood(used)) {
            FoodProperties properties = used.get(DataComponents.FOOD);
            RecoveryState recovery = state(villager);
            save(villager, recovery.addFood(properties.nutrition(), properties.saturation()));
            ItemStack remainder = used.finishUsingItem(level, villager);
            if (!remainder.isEmpty()) {
                ItemStack leftover = VillagerInventoryAccess.addItem(villager, remainder.copy());
                if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
            }
        } else if (used.is(Items.SPLASH_POTION)) {
            ThrownPotion potion = new ThrownPotion(level, villager);
            potion.setItem(used);
            potion.setPos(villager.getX(), villager.getEyeY() - 0.2D, villager.getZ());
            potion.shoot(0.0D, -1.0D, 0.0D, 0.35F, 0.0F);
            level.addFreshEntity(potion);
            villager.swing(InteractionHand.MAIN_HAND, true);
            villager.playSound(SoundEvents.WITCH_THROW, 1.0F, 0.9F + villager.getRandom().nextFloat() * 0.2F);
        } else {
            ItemStack remainder = used.finishUsingItem(level, villager);
            if (!remainder.isEmpty()) {
                ItemStack leftover = VillagerInventoryAccess.addItem(villager, remainder.copy());
                if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
            }
        }
        VillagerRetaliationVillagerEquipment.restoreVisualMainHand(villager, use.resumeMainHand());
    }

    private static void cancelUse(Villager villager, boolean restoreItem) {
        UseState use = ACTIVE_USES.remove(villager.getUUID());
        if (use == null) return;
        villager.stopUsingItem();
        if (restoreItem) {
            ItemStack remainder = VillagerInventoryAccess.addItem(villager, use.stack().copy());
            if (!remainder.isEmpty()) villager.spawnAtLocation(remainder);
        }
        VillagerRetaliationVillagerEquipment.restoreVisualMainHand(villager, use.resumeMainHand());
    }

    private static RecoveryState consumeExhaustion(RecoveryState state) {
        float exhaustion = state.exhaustion();
        int food = state.food();
        float saturation = state.saturation();
        while (exhaustion > 4.0F) {
            exhaustion -= 4.0F;
            if (saturation > 0.0F) saturation = Math.max(0.0F, saturation - 1.0F);
            else if (food > 0) food--;
        }
        return new RecoveryState(food, saturation, exhaustion, state.healTimer());
    }

    private static RecoveryState tickNaturalRegeneration(
            ServerLevel level,
            Villager villager,
            RecoveryState state) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                || villager.getHealth() >= villager.getMaxHealth()
                || state.food() <= 0) {
            return state.withHealTimer(0);
        }
        int timer = state.healTimer() + 1;
        if (state.saturation() > 0.0F && timer >= 10) {
            float healed = Math.min(state.saturation(), 6.0F) / 6.0F;
            villager.heal(healed);
            return new RecoveryState(state.food(), state.saturation(), state.exhaustion() + healed * 6.0F, 0);
        }
        if (state.saturation() <= 0.0F && timer >= 80) {
            villager.heal(1.0F);
            return new RecoveryState(state.food(), 0.0F, state.exhaustion() + 6.0F, 0);
        }
        return state.withHealTimer(timer);
    }

    private static RecoveryState state(Villager villager) {
        CompoundTag tag = villager.getPersistentData().getCompound(TAG);
        if (tag.isEmpty()) {
            RecoveryState initial = new RecoveryState(20, 5.0F, 0.0F, 0);
            save(villager, initial);
            return initial;
        }
        return new RecoveryState(
                Math.clamp(tag.getInt(FOOD), 0, MAX_FOOD),
                Math.clamp(tag.getFloat(SATURATION), 0.0F, MAX_FOOD),
                Math.max(0.0F, tag.getFloat(EXHAUSTION)),
                Math.max(0, tag.getInt(HEAL_TIMER)));
    }

    private static void save(Villager villager, RecoveryState state) {
        CompoundTag previous = villager.getPersistentData().getCompound(TAG);
        int previousFood = previous.isEmpty() ? -1 : previous.getInt(FOOD);
        CompoundTag tag = new CompoundTag();
        tag.putInt(FOOD, state.food());
        tag.putFloat(SATURATION, Math.min(state.saturation(), state.food()));
        tag.putFloat(EXHAUSTION, state.exhaustion());
        tag.putInt(HEAL_TIMER, state.healTimer());
        villager.getPersistentData().put(TAG, tag);
        if (previousFood != state.food() && villager.level() instanceof ServerLevel) {
            VillagerReputationNetworking.syncHungerToTracking(villager, state.food());
        }
    }

    private static void maybeReportShortage(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < NEXT_SHORTAGE_NOTICE.getOrDefault(villager.getUUID(), 0L)) return;
        ServerPlayer nearest = null;
        double nearestDistance = NOTICE_DISTANCE_SQR;
        for (ServerPlayer player : level.players()) {
            double distance = villager.distanceToSqr(player);
            if (distance <= nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            String line = SHORTAGE_LINES[villager.getRandom().nextInt(SHORTAGE_LINES.length)];
            VillagerInteractionService.sendVillagerNotice(nearest, villager, line, Map.of(), 16.0D);
            NEXT_SHORTAGE_NOTICE.put(villager.getUUID(), now + SHORTAGE_COOLDOWN);
        }
    }

    private record RecoveryState(int food, float saturation, float exhaustion, int healTimer) {
        RecoveryState addFood(int nutrition, float saturationModifier) {
            int updatedFood = Math.min(MAX_FOOD, this.food + Math.max(0, nutrition));
            float updatedSaturation = Math.min(updatedFood,
                    this.saturation + Math.max(0, nutrition) * Math.max(0.0F, saturationModifier) * 2.0F);
            return new RecoveryState(updatedFood, updatedSaturation, this.exhaustion, this.healTimer);
        }

        RecoveryState withHealTimer(int timer) {
            return new RecoveryState(this.food, this.saturation, this.exhaustion, timer);
        }
    }

    private record UseState(ItemStack stack, ItemStack resumeMainHand, int ticksRemaining) {
        UseState withTicks(int ticks) {
            return new UseState(this.stack, this.resumeMainHand, ticks);
        }
    }
}
