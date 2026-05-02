package com.jvn.villagerretaliation.util;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class VillagerRetaliationLootUtil {
    private VillagerRetaliationLootUtil() {
    }

    public static void addDrop(LivingDropsEvent event, ItemStack stack) {
        LivingEntity entity = event.getEntity();
        if (!stack.isEmpty()) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
        }
    }

    public static boolean addDropIfNoMatchingItem(LivingDropsEvent event, ItemStack stack) {
        if (stack.isEmpty() || hasDrop(event, dropped -> ItemStack.isSameItem(dropped, stack))) {
            return false;
        }

        addDrop(event, stack);
        return true;
    }

    public static boolean hasDropWithSameItem(LivingDropsEvent event, ItemStack stack) {
        return !stack.isEmpty() && hasDrop(event, dropped -> ItemStack.isSameItem(dropped, stack));
    }

    public static boolean hasDropWithSameItemAndComponents(LivingDropsEvent event, ItemStack stack) {
        return !stack.isEmpty() && hasDrop(event, dropped -> ItemStack.isSameItemSameComponents(dropped, stack));
    }

    public static void drop(LivingEntity entity, ItemStack stack) {
        if (!stack.isEmpty()) {
            entity.spawnAtLocation(stack);
        }
    }

    private static boolean hasDrop(LivingDropsEvent event, Predicate<ItemStack> matcher) {
        return event.getDrops().stream()
                .map(ItemEntity::getItem)
                .anyMatch(matcher);
    }
}
