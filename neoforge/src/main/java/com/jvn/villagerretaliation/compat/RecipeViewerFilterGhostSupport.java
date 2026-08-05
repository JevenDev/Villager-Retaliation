package com.jvn.villagerretaliation.compat;

import com.jvn.villagerretaliation.client.inventory.VillagerAttributeFilterScreen;
import com.jvn.villagerretaliation.client.inventory.VillagerItemFilterScreen;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import com.jvn.villagerretaliation.network.ItemFilterGhostSlotPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared ghost-filter updates used by optional client recipe-viewer integrations. */
public final class RecipeViewerFilterGhostSupport {
    private RecipeViewerFilterGhostSupport() {
    }

    public static boolean setItemFilterSlot(
            VillagerItemFilterScreen screen,
            int slot,
            ItemStack stack) {
        if (slot < 0
                || slot >= VillagerItemFilterMenu.GHOST_SLOT_COUNT
                || stack == null
                || stack.isEmpty()) {
            return false;
        }
        screen.getMenu().setGhostEntry(slot, stack);
        PacketDistributor.sendToServer(new ItemFilterGhostSlotPayload(slot, stack));
        return true;
    }

    public static boolean setAttributeFilterReference(
            VillagerAttributeFilterScreen screen,
            ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        screen.getMenu().setReference(stack);
        PacketDistributor.sendToServer(new ItemFilterGhostSlotPayload(0, stack));
        return true;
    }
}
