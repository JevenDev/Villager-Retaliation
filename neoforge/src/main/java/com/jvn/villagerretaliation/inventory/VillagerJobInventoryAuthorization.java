package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Shared authorization and contract identity for the existing villager job inventory. */
public final class VillagerJobInventoryAuthorization {
    private VillagerJobInventoryAuthorization() {
    }

    public static boolean canAccess(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredVillagerContractService.canAccessJobInventory(level, villager, player)
                || PartyVillagerContractService.canAccessJobInventory(level, villager, player);
    }

    public static Optional<UUID> activeContractId(ServerLevel level, Villager villager) {
        Optional<UUID> hiredContract = HiredVillagerContractService.currentContractId(villager);
        return hiredContract.isPresent()
                ? hiredContract
                : PartyVillagerContractService.currentContractId(level, villager);
    }
}
