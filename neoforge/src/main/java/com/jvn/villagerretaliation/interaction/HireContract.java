package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Typed view of a persisted villager hire contract. */
public final class HireContract {
    private static final String CONTRACT_ID = "ContractId";
    private static final String HIRER = "Hirer";
    private static final String START_GAME_TIME = "StartGameTime";
    private static final String END_GAME_TIME = "EndGameTime";
    private static final String DURATION_DAYS = "DurationDays";
    private static final String DAILY_COST = "DailyCost";
    private static final String EMERALDS_PAID = "EmeraldsPaid";
    private static final String EMERALDS_RELEASED = "EmeraldsReleased";
    private static final String EMERALDS_REFUNDED = "EmeraldsRefunded";
    private static final String AUTO_PAYMENT = "AutoPayment";
    private static final String MOUNTED_TRAVEL = "MountedTravel";
    private static final String ONE_OFF_BUILDER_JOB = "OneOffBuilderJob";
    private static final String LAST_AUTO_PAYMENT_ATTEMPT = "LastAutoPaymentAttemptGameTime";
    private static final String AWAITING_AUTO_PAYMENT_START = "AwaitingAutoPaymentStartGameTime";
    private static final String ROLE = "Role";
    private static final String STATUS = "Status";
    private static final String PROFESSION_LOCK_ARTIFICIAL = "ProfessionLockArtificial";
    private static final String PROFESSION_LOCK_ORIGINAL_XP = "ProfessionLockOriginalXp";
    private static final String PROFESSION_LOCK_APPLIED_XP = "ProfessionLockAppliedXp";

    private final CompoundTag tag;

    HireContract(CompoundTag tag) { this.tag = tag; }

    public static HireContract regular(UUID owner, long startGameTime, int durationDays, int dailyCost,
            int emeraldsPaid, HiredVillagerRole role) {
        HireContract contract = empty(owner, startGameTime, role);
        contract.endGameTime(startGameTime + durationDays * VillagerContractTime.DAY_TICKS);
        contract.durationDays(durationDays);
        contract.dailyCost(dailyCost);
        contract.emeraldsPaid(emeraldsPaid);
        contract.mountedTravel(true);
        return contract;
    }

    public static HireContract oneOffBuilder(UUID owner, long startGameTime) {
        HireContract contract = empty(owner, startGameTime, HiredVillagerRole.BUILDER);
        contract.endGameTime(Long.MAX_VALUE);
        contract.durationDays(0);
        contract.dailyCost(0);
        contract.emeraldsPaid(0);
        contract.oneOffBuilderJob(true);
        return contract;
    }

    private static HireContract empty(UUID owner, long startGameTime, HiredVillagerRole role) {
        HireContract contract = new HireContract(new CompoundTag());
        contract.tag.putUUID(CONTRACT_ID, UUID.randomUUID());
        contract.tag.putUUID(HIRER, owner);
        contract.startGameTime(startGameTime);
        contract.emeraldsReleased(0);
        contract.emeraldsRefunded(0);
        contract.autoPayment(false);
        contract.oneOffBuilderJob(false);
        contract.role(role);
        contract.status(Status.ACTIVE);
        return contract;
    }

    public UUID id() {
        if (!tag.hasUUID(CONTRACT_ID)) tag.putUUID(CONTRACT_ID, UUID.randomUUID());
        return tag.getUUID(CONTRACT_ID);
    }
    public Optional<UUID> owner() { return tag.hasUUID(HIRER) ? Optional.of(tag.getUUID(HIRER)) : Optional.empty(); }
    public void owner(UUID value) { if (value == null) tag.remove(HIRER); else tag.putUUID(HIRER, value); }
    public long startGameTime() { return tag.getLong(START_GAME_TIME); }
    public void startGameTime(long value) { tag.putLong(START_GAME_TIME, value); }
    public long endGameTime() { return tag.getLong(END_GAME_TIME); }
    public void endGameTime(long value) { tag.putLong(END_GAME_TIME, value); }
    public int durationDays() { return tag.getInt(DURATION_DAYS); }
    public void durationDays(int value) { tag.putInt(DURATION_DAYS, value); }
    public int dailyCost() { return tag.getInt(DAILY_COST); }
    public void dailyCost(int value) { tag.putInt(DAILY_COST, value); }
    public int emeraldsPaid() { return tag.getInt(EMERALDS_PAID); }
    public void emeraldsPaid(int value) { tag.putInt(EMERALDS_PAID, value); }
    public int emeraldsReleased() { return tag.getInt(EMERALDS_RELEASED); }
    public void emeraldsReleased(int value) { tag.putInt(EMERALDS_RELEASED, value); }
    public boolean hasEmeraldsReleased() { return tag.contains(EMERALDS_RELEASED, Tag.TAG_INT); }
    public void emeraldsRefunded(int value) { tag.putInt(EMERALDS_REFUNDED, value); }
    public boolean autoPayment() { return tag.getBoolean(AUTO_PAYMENT); }
    public void autoPayment(boolean value) { tag.putBoolean(AUTO_PAYMENT, value); }
    public boolean mountedTravel() { return !tag.contains(MOUNTED_TRAVEL) || tag.getBoolean(MOUNTED_TRAVEL); }
    public void mountedTravel(boolean value) { tag.putBoolean(MOUNTED_TRAVEL, value); }
    public boolean oneOffBuilderJob() { return tag.getBoolean(ONE_OFF_BUILDER_JOB); }
    public void oneOffBuilderJob(boolean value) { tag.putBoolean(ONE_OFF_BUILDER_JOB, value); }
    public long lastAutoPaymentAttemptGameTime() { return tag.getLong(LAST_AUTO_PAYMENT_ATTEMPT); }
    public void lastAutoPaymentAttemptGameTime(long value) { tag.putLong(LAST_AUTO_PAYMENT_ATTEMPT, value); }
    public long awaitingAutoPaymentStartGameTime() { return tag.getLong(AWAITING_AUTO_PAYMENT_START); }
    public void awaitingAutoPaymentStartGameTime(long value) { tag.putLong(AWAITING_AUTO_PAYMENT_START, value); }
    public HiredVillagerRole role() { return HiredVillagerRole.bySerializedName(tag.getString(ROLE)); }
    public void role(HiredVillagerRole value) { if (value == null) tag.remove(ROLE); else tag.putString(ROLE, value.serializedName()); }
    public Status status() { return Status.fromSerializedName(tag.getString(STATUS)); }
    public void status(Status value) { tag.putString(STATUS, value.serializedName); }
    public boolean isActive() { return status() == Status.ACTIVE; }
    public boolean isAwaitingAutoPayment() { return status() == Status.AWAITING_AUTO_PAYMENT; }
    public boolean isActiveOrAwaitingAutoPayment() { return isActive() || isAwaitingAutoPayment(); }
    public boolean hasArtificialProfessionLock() { return tag.getBoolean(PROFESSION_LOCK_ARTIFICIAL); }
    public int originalProfessionXp() { return tag.getInt(PROFESSION_LOCK_ORIGINAL_XP); }
    public int appliedProfessionXp() { return tag.getInt(PROFESSION_LOCK_APPLIED_XP); }
    public void artificialProfessionLock(int originalXp, int appliedXp) {
        tag.putBoolean(PROFESSION_LOCK_ARTIFICIAL, true);
        tag.putInt(PROFESSION_LOCK_ORIGINAL_XP, originalXp);
        tag.putInt(PROFESSION_LOCK_APPLIED_XP, appliedXp);
    }
    public void clearProfessionLock() {
        tag.remove(PROFESSION_LOCK_ARTIFICIAL);
        tag.remove(PROFESSION_LOCK_ORIGINAL_XP);
        tag.remove(PROFESSION_LOCK_APPLIED_XP);
    }
    CompoundTag encoded() { return tag; }

    public enum Status {
        ACTIVE("active"), ENDED("ended"), EXPIRED("expired"), AWAITING_AUTO_PAYMENT("awaiting_auto_payment");
        private final String serializedName;
        Status(String serializedName) { this.serializedName = serializedName; }
        private static Status fromSerializedName(String value) {
            for (Status status : values()) if (status.serializedName.equals(value)) return status;
            return ENDED;
        }
    }
}
