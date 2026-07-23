package com.jvn.villagerretaliation.compat.emi;

import com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen;
import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import com.jvn.villagerretaliation.client.ui.ClientScreenArea;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

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
    }

    private static Bounds toEmiBounds(ClientScreenArea area) {
        return new Bounds(area.left(), area.top(), area.width(), area.height());
    }
}
