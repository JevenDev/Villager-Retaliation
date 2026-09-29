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

    public static HiredVillagerRole activeRole(ServerLevel level, Villager villager) {
        HiredVillagerRole role = resolve(level, villager).role();
        return role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
    }

    public static boolean hasWork(Villager villager) {
        return HiredVillagerContractService.hasActiveOrPendingContract(villager)
                || SettlementWorkforceService.duty(villager).isPresent();
    }

    public HiredJobSite effectiveJobSite(Villager villager, net.minecraft.nbt.CompoundTag state,
            HiredVillagerRole requestedRole, HiredJobSite fallback) {
        return this.civic && this.role == requestedRole && requestedRole == HiredVillagerRole.BUILDER
                ? com.jvn.villagerretaliation.settlement.SettlementConstructionService.jobSite(villager, state, fallback)
                : fallback;
    }

    public HiredRoute effectiveRoute(Villager villager, HiredRoute fallback) {
        return this.civic ? com.jvn.villagerretaliation.settlement.SettlementGuardService.route(villager, fallback) : fallback;
    }

    public HiredCombatMode effectiveCombatMode(Villager villager, HiredCombatMode fallback) {
        return this.civic && this.role == HiredVillagerRole.COMBAT
                ? com.jvn.villagerretaliation.settlement.SettlementGuardService.mode(villager, fallback) : fallback;
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
