package com.jvn.villagerretaliation.compat.emi;

import com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen;
import com.jvn.villagerretaliation.client.inventory.VillagerItemFilterScreen;
import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import com.jvn.villagerretaliation.client.ui.ClientScreenArea;
import com.jvn.villagerretaliation.network.ItemFilterGhostSlotPayload;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

@EmiEntrypoint
public final class VillagerRetaliationEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(VillagerInventoryScreen.class, (screen, consumer) ->
                screen.additionalRecipeViewerExclusionAreas().stream()
                        .map(VillagerRetaliationEmiPlugin::toEmiBounds)
                        .forEach(consumer));
        registry.addExclusionArea(InventoryScreen.class, (screen, consumer) ->
                PartyInventoryOverlay.recipeViewerExclusionAreas(screen).stream()
                        .map(VillagerRetaliationEmiPlugin::toEmiBounds)
                        .forEach(consumer));
        registry.addDragDropHandler(VillagerItemFilterScreen.class, new EmiDragDropHandler<>() {
            @Override
            public boolean dropStack(VillagerItemFilterScreen screen, dev.emi.emi.api.stack.EmiIngredient ingredient,
                    int mouseX, int mouseY) {
                int slot = screen.ghostSlotAt(mouseX, mouseY);
                if (slot < 0 || ingredient.getEmiStacks().isEmpty()) {
                    return false;
                }
                ItemStack stack = ingredient.getEmiStacks().getFirst().getItemStack();
                if (stack.isEmpty()) {
                    return false;
                }
                screen.getMenu().setGhostEntry(slot, stack);
                PacketDistributor.sendToServer(new ItemFilterGhostSlotPayload(slot, stack));
                return true;
            }
        });
    }

    private static Bounds toEmiBounds(ClientScreenArea area) {
        return new Bounds(area.left(), area.top(), area.width(), area.height());
    }
}
