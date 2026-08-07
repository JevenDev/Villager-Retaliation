package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;

/** Accrual, release, and refund policy for hire-payment escrow. */
public final class HirePaymentEscrow {
    private HirePaymentEscrow() { }

    public static int earlyEndRefund(ServerLevel level, Villager villager) {
        HireContract contract = HireContractStore.load(villager).orElse(null);
        if (contract == null || contract.oneOffBuilderJob()) return 0;
        int committed = Math.max(released(contract), accrued(level, contract));
        int remaining = Math.max(0, paid(contract) - committed);
        int percent = Mth.clamp(VillagerRetaliationConfig.HIRED_CONTRACT_EARLY_END_REFUND_PERCENT.get(), 0, 100);
        return (int) Math.floor(remaining * percent / 100.0D);
    }

    public static void releaseEarned(ServerLevel level, Villager villager) {
        HireContract contract = HireContractStore.load(villager).orElse(null);
        if (contract == null || contract.oneOffBuilderJob()) return;
        release(villager, contract, Math.max(0, accrued(level, contract) - released(contract)));
    }

    public static void settle(ServerLevel level, Villager villager, int refund) {
        HireContract contract = HireContractStore.load(villager).orElse(null);
        if (contract == null || contract.oneOffBuilderJob()) return;
        releaseEarned(level, villager);
        int paid = paid(contract);
        int released = released(contract);
        int safeRefund = Mth.clamp(refund, 0, Math.max(0, paid - released));
        release(villager, contract, Math.max(0, paid - released - safeRefund));
        contract.emeraldsRefunded(safeRefund);
        HireContractStore.save(villager, contract);
    }

    private static int accrued(ServerLevel level, HireContract contract) {
        int paid = paid(contract);
        long start = contract.startGameTime();
        long end = Math.max(start, contract.endGameTime());
        long duration = end - start;
        if (paid <= 0 || duration <= 0L) return paid;
        long elapsed = Mth.clamp(level.getGameTime() - start, 0L, duration);
        return Mth.clamp((int) Math.floor(paid * (elapsed / (double) duration)), 0, paid);
    }

    private static int paid(HireContract contract) { return Math.max(0, contract.emeraldsPaid()); }

    private static int released(HireContract contract) {
        int paid = paid(contract);
        if (!contract.hasEmeraldsReleased()) {
            contract.emeraldsReleased(paid);
            return paid;
        }
        return Mth.clamp(contract.emeraldsReleased(), 0, paid);
    }

    private static void release(Villager villager, HireContract contract, int amount) {
        if (amount <= 0) return;
        VillagerWalletService.addCurrency(villager, amount);
        contract.emeraldsReleased(released(contract) + amount);
        HireContractStore.save(villager, contract);
    }
}
