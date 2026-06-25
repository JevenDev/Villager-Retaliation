package com.jvn.villagerretaliation.interaction.work.builder;

import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class BuilderPaymentEscrowService {
    private static final String TAG = "VillagerRetaliationBuilderPaymentEscrow";
    private static final String JOBS_TAG = "Jobs";
    private static final String SETTLED_TAG = "Settled";

    private BuilderPaymentEscrowService() {
    }

    public static void escrow(Villager villager, UUID jobId, int amount) {
        if (villager == null || jobId == null || amount <= 0) {
            return;
        }
        jobs(villager).putInt(jobId.toString(), amount);
        villager.setPersistenceRequired();
    }

    public static int refund(ServerPlayer player, Villager villager, Optional<UUID> jobId, int fallbackAmount) {
        if (player == null) {
            return 0;
        }
        if (isSettled(villager, jobId)) {
            return 0;
        }
        int amount = claim(villager, jobId);
        if (amount <= 0) {
            amount = refundLegacyWalletPayment(player, villager, fallbackAmount);
            if (amount <= 0) {
                return 0;
            }
        }
        markSettled(villager, jobId);
        giveCurrency(player, amount);
        return amount;
    }

    public static int releaseToWallet(Villager villager, Optional<UUID> jobId) {
        if (isSettled(villager, jobId)) {
            return 0;
        }
        int amount = claim(villager, jobId);
        markSettled(villager, jobId);
        if (amount <= 0) {
            return 0;
        }
        VillagerWalletService.addCurrency(villager, amount, VillagerWalletService.WalletSource.TASK_REWARD);
        return amount;
    }

    private static int claim(Villager villager, Optional<UUID> jobId) {
        if (villager == null || jobId == null || jobId.isEmpty()) {
            return 0;
        }
        CompoundTag jobs = jobs(villager);
        String key = jobId.get().toString();
        if (!jobs.contains(key, Tag.TAG_INT)) {
            return 0;
        }
        int amount = Math.max(0, jobs.getInt(key));
        jobs.remove(key);
        villager.setPersistenceRequired();
        return amount;
    }

    private static boolean isSettled(Villager villager, Optional<UUID> jobId) {
        if (villager == null || jobId == null || jobId.isEmpty()) {
            return false;
        }
        return settled(villager).getBoolean(jobId.get().toString());
    }

    private static void markSettled(Villager villager, Optional<UUID> jobId) {
        if (villager == null || jobId == null || jobId.isEmpty()) {
            return;
        }
        settled(villager).putBoolean(jobId.get().toString(), true);
        villager.setPersistenceRequired();
    }

    private static int refundLegacyWalletPayment(ServerPlayer player, Villager villager, int amount) {
        if (player == null
                || villager == null
                || amount <= 0
                || !VillagerWalletService.spendCurrency(villager, amount, VillagerWalletService.WalletSource.DEPOSIT_ADJUSTMENT)) {
            return 0;
        }
        return amount;
    }

    private static void giveCurrency(ServerPlayer player, int count) {
        if (player == null || count <= 0) {
            return;
        }
        int remaining = count;
        while (remaining > 0) {
            int chunk = Math.min(VillagerCurrencyResources.maxStackSize(player.serverLevel().getServer()), remaining);
            ItemStack stack = VillagerCurrencyResources.createStack(player.serverLevel().getServer(), chunk);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= chunk;
        }
    }

    private static CompoundTag jobs(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_COMPOUND)) {
            data.put(TAG, new CompoundTag());
        }
        CompoundTag root = data.getCompound(TAG);
        if (!root.contains(JOBS_TAG, Tag.TAG_COMPOUND)) {
            root.put(JOBS_TAG, new CompoundTag());
        }
        return root.getCompound(JOBS_TAG);
    }

    private static CompoundTag settled(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_COMPOUND)) {
            data.put(TAG, new CompoundTag());
        }
        CompoundTag root = data.getCompound(TAG);
        if (!root.contains(SETTLED_TAG, Tag.TAG_COMPOUND)) {
            root.put(SETTLED_TAG, new CompoundTag());
        }
        return root.getCompound(SETTLED_TAG);
    }
}
