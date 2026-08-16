package com.jvn.villagerretaliation.interaction;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-side inventory payment helper shared by hired and party contracts. */
public final class VillagerCurrencyPayment {
    private VillagerCurrencyPayment() {
    }

    public static int count(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int count = count(player, player.getInventory().items);
        return count + count(player, player.getInventory().offhand);
    }

    public static boolean tryRemove(ServerPlayer player, int amount) {
        if (player == null || amount < 0 || count(player) < amount) {
            return false;
        }
        int remaining = removeFrom(player, player.getInventory().items, amount);
        if (remaining > 0) {
            remaining = removeFrom(player, player.getInventory().offhand, remaining);
        }
        if (remaining != 0) {
            throw new IllegalStateException("Validated villager contract payment changed during server transaction");
        }
        player.getInventory().setChanged();
        return true;
    }

    public static void give(ServerPlayer player, int amount) {
        int remaining = Math.max(0, amount);
        while (remaining > 0) {
            int chunk = Math.min(VillagerCurrencyResources.maxStackSize(player.serverLevel().getServer()), remaining);
            ItemStack stack = VillagerCurrencyResources.createStack(player.serverLevel().getServer(), chunk);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= chunk;
        }
    }

    private static int count(ServerPlayer player, List<ItemStack> stacks) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (VillagerCurrencyResources.isCurrency(player.serverLevel().getServer(), stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int removeFrom(ServerPlayer player, List<ItemStack> stacks, int amount) {
        int remaining = amount;
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (!VillagerCurrencyResources.isCurrency(player.serverLevel().getServer(), stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        return remaining;
    }
}
