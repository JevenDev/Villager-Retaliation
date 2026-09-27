package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SettlementFoodService {
    private static final String METABOLISM_TIME = "VillagerRetaliationMetabolismTime";

    private SettlementFoodService() {
    }

    public static boolean isMeal(ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        return food != null && food.nutrition() > 0 && food.effects().isEmpty()
                && !stack.is(Items.GOLDEN_APPLE) && !stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    public static ItemStack takeMeal(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return ItemStack.EMPTY;
        }
        var town = SettlementWorkforceService.home(level, villager).orElse(null);
        return town != null && town.dimension().equals(level.dimension().location())
                && withinTown(town, villager)
                ? SettlementStock.take(town, SettlementFoodService::isMeal, 1) : ItemStack.EMPTY;
    }

    private static boolean withinTown(SettlementRecord town, Villager villager) {
        int radius = town.radius() + 16;
        return Math.abs(villager.getX() - town.center().getX()) <= radius
                && Math.abs(villager.getZ() - town.center().getZ()) <= radius;
    }

    public static long nutrition(SettlementRecord town) {
        long nutrition = 0L;
        for (SettlementDepot depot : SettlementStock.supplies(town)) {
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                ItemStack stack = depot.getItem(slot);
                if (isMeal(stack)) {
                    nutrition += (long) stack.getCount() * stack.get(DataComponents.FOOD).nutrition();
                }
            }
        }
        return nutrition;
    }

    public static double reserveDays(SettlementRecord town, int residents) {
        return residents <= 0 ? 0.0D : nutrition(town) / ((double) residents * dailyNutrition());
    }

    public static int dailyNutrition() {
        return Math.clamp(VillagerRetaliationConfig.SETTLEMENT_DAILY_NUTRITION.get(), 1, 100);
    }

    public static void resetMetabolism(Villager villager) {
        villager.getPersistentData().putLong(METABOLISM_TIME, villager.level().getGameTime());
    }

    public static float metabolism(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 0.0F;
        }
        long now = level.getGameTime();
        var tag = villager.getPersistentData();
        long previous = tag.contains(METABOLISM_TIME) ? tag.getLong(METABOLISM_TIME) : now;
        long elapsed = now - previous;
        if (!tag.contains(METABOLISM_TIME) || elapsed < 0L || elapsed >= 20L) {
            tag.putLong(METABOLISM_TIME, now);
        }
        if (elapsed < 20L || VillageAllegianceApi.canonicalPrimary(level, villager).isEmpty()) {
            return 0.0F;
        }
        return dailyNutrition() * 4.0F * Math.min(elapsed, 20L) / 24_000.0F;
    }

    public static boolean protectsFromFatalStarvation(Villager villager) {
        return villager.level() instanceof ServerLevel level
                && VillageAllegianceApi.canonicalPrimary(level, villager).isPresent();
    }

    public static double workMultiplier(ServerLevel level, Villager villager) {
        var town = SettlementWorkforceService.home(level, villager).orElse(null);
        if (town == null || !town.duties().containsKey(villager.getUUID())) {
            return 1.0D;
        }
        int food = VillagerRecoveryService.foodLevel(villager);
        double multiplier = food <= 0 ? 0.4D : food < 6 ? 0.7D : 1.0D;
        int population = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level)
                .residentRoster(town.id(), level.getGameTime()).size();
        return population > town.housingCapacity() ? multiplier * 0.75D : multiplier;
    }
}
