package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
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
        open(player, villager, VillagerInventoryMenu.ViewMode.PERSONAL);
    }

    public static void openJobInventory(ServerPlayer player, Villager villager) {
        open(player, villager, VillagerInventoryMenu.ViewMode.JOB);
    }

    private static void open(ServerPlayer player, Villager villager, VillagerInventoryMenu.ViewMode viewMode) {
        Component title = Component.translatable(
                viewMode == VillagerInventoryMenu.ViewMode.JOB
                        ? "container.villagerretaliation.job_inventory"
                        : "container.villagerretaliation.villager_inventory",
                VillagerPresetNameRegistry.resolveDisplayName(villager)
        );
        player.openMenu(
                new SimpleMenuProvider((containerId, inventory, owner) -> new VillagerInventoryMenu(containerId, inventory, villager, viewMode), title),
                buffer -> {
                    buffer.writeVarInt(villager.getId());
                    buffer.writeEnum(viewMode);
                }
        );
    }

    public static void dropExtraInventory(Villager villager) {
        VillagerInventoryContainer.dropExtraInventory(villager);
    }

    public static ItemStack addItem(Villager villager, ItemStack stack) {
        return VillagerInventoryContainer.addItem(villager, stack);
    }

    public static boolean canAddItems(Villager villager, List<ItemStack> stacks) {
        return VillagerInventoryContainer.canAddItems(villager, stacks);
    }

    public static boolean hasUsableWeapon(Villager villager) {
        return VillagerInventoryContainer.hasUsableWeapon(villager);
    }

    public static boolean hasBorrowedCombatWeapon(Villager villager) {
        return VillagerInventoryContainer.hasBorrowedCombatWeapon(villager);
    }

    public static boolean maintainBorrowedCombatWeapon(Villager villager) {
        return VillagerInventoryContainer.maintainBorrowedCombatWeapon(villager);
    }

    public static boolean tryBorrowCombatWeapon(Villager villager) {
        return VillagerInventoryContainer.tryBorrowCombatWeapon(villager);
    }

    public static void returnBorrowedCombatWeapon(Villager villager) {
        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
    }

    public static void clearBorrowedCombatWeapon(Villager villager) {
        VillagerInventoryContainer.clearBorrowedCombatWeapon(villager);
    }

    public static void dropAllInventoryAndEquipment(Villager villager, LivingDropsEvent event) {
        VillagerInventoryContainer.dropAllInventoryAndEquipment(villager, event);
    }

    public static boolean hasOpenInventory(Villager villager) {
        return VillagerInventoryContainer.hasOpenInventory(villager);
    }

    public static void maybeOffloadInventoryOverflow(Villager villager) {
        VillagerInventoryOverflowService.maybeOffloadInventoryOverflow(villager);
    }
}
