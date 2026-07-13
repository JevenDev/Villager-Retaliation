package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
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

    public static void clearRuntimeState() {
        VillagerInventoryContainer.clearRuntimeState();
        VillagerInventoryOverflowService.clearRuntimeState();
    }

    public static boolean canAccess(ServerLevel level, Villager villager, ServerPlayer player) {
        return !villager.isBaby()
                && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.REVERED.trustRank();
    }

    public static boolean open(ServerPlayer player, Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !canAccess(level, villager, player)) {
            return false;
        }
        open(
                player,
                villager,
                VillagerInventoryMenu.ViewMode.PERSONAL,
                true,
                VillagerJobInventoryAuthorization.canAccess(level, villager, player));
        return true;
    }

    public static boolean openPreferred(ServerPlayer player, Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        VillagerInventoryMenu.ViewMode viewMode = preferredViewMode(level, villager, player);
        boolean personalInventoryAccess = canAccess(level, villager, player);
        boolean jobInventoryAccess = VillagerJobInventoryAuthorization.canAccess(level, villager, player);
        if (viewMode == VillagerInventoryMenu.ViewMode.PERSONAL && !personalInventoryAccess) {
            return false;
        }
        open(player, villager, viewMode, personalInventoryAccess, jobInventoryAccess);
        return true;
    }

    public static boolean canOpenPreferred(ServerLevel level, Villager villager, ServerPlayer player) {
        return preferredViewMode(level, villager, player).isWorkInventory()
                || canAccess(level, villager, player);
    }

    public static boolean openJobInventory(ServerPlayer player, Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || !com.jvn.villagerretaliation.interaction.VillagerInteractionService.canUseInteractionSystem(player, villager)
                || !VillagerJobInventoryAuthorization.canAccess(level, villager, player)) {
            return false;
        }
        boolean personalInventoryAccess = canAccess(level, villager, player);
        open(player, villager, VillagerInventoryMenu.ViewMode.JOB, personalInventoryAccess, true);
        return true;
    }

    static VillagerInventoryMenu.ViewMode preferredViewMode(
            ServerLevel level,
            Villager villager,
            ServerPlayer player) {
        if (VillagerJobInventoryAuthorization.activeContractId(level, villager).isPresent()
                && VillagerJobInventoryAuthorization.canAccess(level, villager, player)) {
            return com.jvn.villagerretaliation.party.PartyVillagerContractService.isActivePartyVillager(level, villager)
                    ? VillagerInventoryMenu.ViewMode.PARTY
                    : VillagerInventoryMenu.ViewMode.JOB;
        }
        return VillagerInventoryMenu.ViewMode.PERSONAL;
    }

    private static void open(
            ServerPlayer player,
            Villager villager,
            VillagerInventoryMenu.ViewMode viewMode,
            boolean personalInventoryAccess,
            boolean jobInventoryAccess) {
        VillagerInventoryMenu.ViewMode workInventoryViewMode =
                com.jvn.villagerretaliation.party.PartyVillagerContractService.isActivePartyVillager(
                        player.serverLevel(),
                        villager)
                        ? VillagerInventoryMenu.ViewMode.PARTY
                        : VillagerInventoryMenu.ViewMode.JOB;
        VillagerInventoryMenu.ViewMode resolvedViewMode = viewMode.isWorkInventory()
                ? workInventoryViewMode
                : viewMode;
        Component title = Component.translatable(
                resolvedViewMode == VillagerInventoryMenu.ViewMode.PARTY
                        ? "container.villagerretaliation.party_inventory"
                        : resolvedViewMode == VillagerInventoryMenu.ViewMode.JOB
                                ? "container.villagerretaliation.job_inventory"
                                : "container.villagerretaliation.villager_inventory",
                VillagerPresetNameRegistry.resolveDisplayName(villager)
        );
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, owner) -> new VillagerInventoryMenu(
                                containerId,
                                inventory,
                                villager,
                                resolvedViewMode,
                                personalInventoryAccess,
                                jobInventoryAccess),
                        title),
                buffer -> {
                    buffer.writeVarInt(villager.getId());
                    buffer.writeEnum(resolvedViewMode);
                    buffer.writeEnum(workInventoryViewMode);
                    buffer.writeBoolean(personalInventoryAccess);
                    buffer.writeBoolean(jobInventoryAccess);
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
