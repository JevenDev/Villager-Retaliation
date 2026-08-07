package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Persistence and expiry boundary for post-contract job-inventory claims. */
public final class HireOverflowClaimService {
    private static final String CLAIM_TAG = "VillagerRetaliationJobInventoryOverflowClaim";
    private static final String CONTRACT_ID = "ContractId";
    private static final String OWNER = "Owner";
    private static final String CREATED_GAME_TIME = "CreatedGameTime";
    private static final String EXPIRES_GAME_TIME = "ExpiresGameTime";
    private static final String LAST_REMINDER_DAY = "LastReminderDay";
    private static final long CLAIM_TICKS = 3L * VillagerContractTime.DAY_TICKS;

    private HireOverflowClaimService() { }

    public static void remember(ServerLevel level, Villager villager, UUID contractId, UUID ownerId) {
        if (level == null || villager == null || contractId == null || ownerId == null) return;
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.markRemovableItemsForContract(contractId);
        if (inventory.countRemovableItemsForContract(contractId) <= 0) {
            clear(villager);
            return;
        }
        Claim claim = new Claim(contractId, ownerId, level.getGameTime(),
                level.getGameTime() + CLAIM_TICKS, -1L);
        save(villager, claim);
    }

    public static Optional<Claim> active(ServerLevel level, Villager villager) {
        if (level == null || villager == null
                || !villager.getPersistentData().contains(CLAIM_TAG, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag tag = villager.getPersistentData().getCompound(CLAIM_TAG);
        if (!tag.hasUUID(CONTRACT_ID) || !tag.hasUUID(OWNER)) {
            clear(villager);
            return Optional.empty();
        }
        Claim claim = decode(tag);
        if (!HiredJobInventory.getJobInventory(villager).hasRemovableItemsForContract(claim.contractId())
                || level.getGameTime() >= claim.expiresGameTime()) {
            clear(villager);
            return Optional.empty();
        }
        return Optional.of(claim);
    }

    public static boolean transferOwner(ServerLevel level, Villager villager, UUID newOwnerId) {
        if (newOwnerId == null) {
            return false;
        }
        Claim claim = active(level, villager).orElse(null);
        if (claim == null || claim.ownerId().equals(newOwnerId)) {
            return false;
        }
        save(villager, new Claim(claim.contractId(), newOwnerId, claim.createdGameTime(),
                claim.expiresGameTime(), claim.lastReminderDay()));
        return true;
    }

    public static void markReminded(Villager villager, Claim claim, long day) {
        save(villager, new Claim(claim.contractId(), claim.ownerId(), claim.createdGameTime(),
                claim.expiresGameTime(), day));
    }

    public static int remainingDays(ServerLevel level, Claim claim) {
        long ticks = Math.max(0L, claim.expiresGameTime() - level.getGameTime());
        return (int) Math.max(1L, (ticks + VillagerContractTime.DAY_TICKS - 1L) / VillagerContractTime.DAY_TICKS);
    }

    public static int itemCount(Villager villager, Claim claim) {
        return HiredJobInventory.getJobInventory(villager).countRemovableItemsForContract(claim.contractId());
    }

    public static void clear(Villager villager) {
        if (villager != null && villager.getPersistentData().contains(CLAIM_TAG)) {
            villager.getPersistentData().remove(CLAIM_TAG);
            villager.setPersistenceRequired();
        }
    }

    private static Claim decode(CompoundTag tag) {
        return new Claim(tag.getUUID(CONTRACT_ID), tag.getUUID(OWNER), tag.getLong(CREATED_GAME_TIME),
                tag.getLong(EXPIRES_GAME_TIME), tag.getLong(LAST_REMINDER_DAY));
    }

    private static void save(Villager villager, Claim claim) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(CONTRACT_ID, claim.contractId());
        tag.putUUID(OWNER, claim.ownerId());
        tag.putLong(CREATED_GAME_TIME, claim.createdGameTime());
        tag.putLong(EXPIRES_GAME_TIME, claim.expiresGameTime());
        tag.putLong(LAST_REMINDER_DAY, claim.lastReminderDay());
        villager.getPersistentData().put(CLAIM_TAG, tag);
        villager.setPersistenceRequired();
    }

    public record Claim(UUID contractId, UUID ownerId, long createdGameTime, long expiresGameTime,
                        long lastReminderDay) { }
}
