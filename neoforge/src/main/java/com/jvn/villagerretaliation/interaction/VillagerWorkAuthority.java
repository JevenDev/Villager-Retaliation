package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public record VillagerWorkAuthority(boolean active, HiredVillagerRole role, Optional<UUID> owner,
                                    boolean civic, boolean awaitingAutoPayment, boolean oneOffBuilderJob) {
    public static VillagerWorkAuthority resolve(ServerLevel level, Villager villager) {
        var contract = HiredVillagerContractService.snapshot(level, villager);
        if (contract.hired()) {
            return new VillagerWorkAuthority(true, contract.role(), contract.hirer(), false,
                    contract.awaitingAutoPayment(), contract.oneOffBuilderJob());
        }
        return SettlementWorkforceService.duty(villager)
                .map(duty -> new VillagerWorkAuthority(true, duty.area().type().role(),
                        Optional.of(duty.town().leader()), true, false, false))
                .orElseGet(() -> new VillagerWorkAuthority(false, null, Optional.empty(), false, false, false));
    }

    public static boolean hasWork(Villager villager) {
        return HiredVillagerContractService.hasActiveOrPendingContract(villager)
                || SettlementWorkforceService.duty(villager).isPresent();
    }

    public ServerPlayer onlineOwner(ServerLevel level) {
        return this.owner.map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
    }

    public boolean canWork(ServerLevel level, Villager villager) {
        return this.active && !this.awaitingAutoPayment && (this.civic
                ? SettlementWorkforceService.onDuty(level, villager)
                : onlineOwner(level) != null);
    }
}
