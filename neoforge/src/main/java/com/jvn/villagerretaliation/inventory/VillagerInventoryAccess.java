package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class VillagerInventoryAccess {
    private VillagerInventoryAccess() {
    }

    public static boolean canAccess(ServerLevel level, Villager villager, ServerPlayer player) {
        return !villager.isBaby()
                && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.REVERED.trustRank();
    }

    public static void open(ServerPlayer player, Villager villager) {
        Component title = Component.translatable(
                "container.villagerretaliation.villager_inventory",
                VillagerPresetNameRegistry.resolveDisplayName(villager)
        );
        player.openMenu(
                new SimpleMenuProvider((containerId, inventory, owner) -> new VillagerInventoryMenu(containerId, inventory, villager), title),
                buffer -> buffer.writeVarInt(villager.getId())
        );
    }

    public static void dropExtraInventory(Villager villager) {
        VillagerInventoryContainer.dropExtraInventory(villager);
    }

    public static ItemStack addItem(Villager villager, ItemStack stack) {
        return VillagerInventoryContainer.addItem(villager, stack);
    }

    public static void dropAllInventoryAndEquipment(Villager villager, LivingDropsEvent event) {
        VillagerInventoryContainer.dropAllInventoryAndEquipment(villager, event);
    }

    public static boolean hasOpenInventory(Villager villager) {
        return VillagerInventoryContainer.hasOpenInventory(villager);
    }
}
