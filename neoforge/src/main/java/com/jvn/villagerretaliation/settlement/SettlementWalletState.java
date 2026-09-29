package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;

final class SettlementWalletState {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementWalletVersion";
    private final UUID owner;
    private final long version;
    private final CompoundTag wallet;
    private CompoundTag constructionPayments = new CompoundTag();

    boolean acknowledgesConstruction(SettlementProject project) {
        return this.constructionPayments.getBoolean(project.id().toString());
    }

    private SettlementWalletState(UUID owner, long version, CompoundTag wallet) {
        if (owner == null || version <= 0L) throw new IllegalArgumentException("Wallet identity or revision is missing");
        this.owner = owner;
        this.version = version;
        this.wallet = wallet.copy();
    }

    static SettlementWalletState capture(Villager villager, SettlementWalletState previous) {
        if (previous != null) previous.requireOwner(villager);
        var wallet = VillagerWalletService.captureWallet(villager);
        long version = Math.max(previous == null ? 0L : previous.version, villager.getPersistentData().getLong(VERSION_TAG));
        var payments = villager.getPersistentData().getCompound("SettlementConstructionPayments");
        if (previous == null || !previous.wallet.equals(wallet) || !previous.constructionPayments.equals(payments)) version = Math.incrementExact(version);
        villager.getPersistentData().putLong(VERSION_TAG, version);
        var captured = new SettlementWalletState(villager.getUUID(), version, wallet);
        captured.constructionPayments = payments.copy();
        return captured;
    }

    boolean needsUpdate(long day) {
        return !this.wallet.getBoolean("Initialized") || this.wallet.getLong("LastIncomeDay") < day
                || this.wallet.getLong("LastDepositDay") < day && this.wallet.getInt("CurrentEmeralds") > this.wallet.getInt("MaxEmeralds");
    }

    boolean restore(Villager villager) {
        requireOwner(villager);
        if (villager.getPersistentData().getLong(VERSION_TAG) >= this.version) return false;
        VillagerWalletService.restoreWallet(villager, this.wallet);
        villager.getPersistentData().put("SettlementConstructionPayments", this.constructionPayments.copy());
        villager.getPersistentData().putLong(VERSION_TAG, this.version);
        return true;
    }

    private void requireOwner(Villager villager) {
        if (!this.owner.equals(villager.getUUID())) throw new IllegalArgumentException("Wallet belongs to another resident");
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Owner", this.owner);
        tag.putLong("Version", this.version);
        tag.put("Wallet", this.wallet.copy());
        tag.put("ConstructionPayments", this.constructionPayments.copy());
        return tag;
    }

    static SettlementWalletState load(CompoundTag tag, UUID owner, CompoundTag entityContext) {
        if (!tag.hasUUID("Owner") || !owner.equals(tag.getUUID("Owner")) || !tag.contains("Wallet", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Saved wallet belongs to another resident or is incomplete");
        }
        if (tag.contains("ConstructionPayments") && !tag.contains("ConstructionPayments", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid construction payment acknowledgement snapshot");
        }
        var state = new SettlementWalletState(owner, tag.getLong("Version"), tag.getCompound("Wallet"));
        state.constructionPayments = (tag.contains("ConstructionPayments") ? tag.getCompound("ConstructionPayments")
                : entityContext.getCompound("NeoForgeData").getCompound("SettlementConstructionPayments")).copy();
        return state;
    }
}
