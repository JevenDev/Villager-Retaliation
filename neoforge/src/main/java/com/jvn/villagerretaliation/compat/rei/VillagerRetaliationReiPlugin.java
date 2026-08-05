package com.jvn.villagerretaliation.compat.rei;

import com.jvn.villagerretaliation.client.inventory.VillagerAttributeFilterScreen;
import com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen;
import com.jvn.villagerretaliation.client.inventory.VillagerItemFilterScreen;
import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import com.jvn.villagerretaliation.client.ui.ClientScreenArea;
import com.jvn.villagerretaliation.compat.RecipeViewerFilterGhostSupport;
import java.util.stream.Stream;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;

@REIPluginClient
public final class VillagerRetaliationReiPlugin implements REIClientPlugin {
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(VillagerInventoryScreen.class, screen ->
                screen.additionalRecipeViewerExclusionAreas().stream()
                        .map(VillagerRetaliationReiPlugin::toReiArea)
                        .toList());
        zones.register(InventoryScreen.class, screen ->
                PartyInventoryOverlay.recipeViewerExclusionAreas(screen).stream()
                        .map(VillagerRetaliationReiPlugin::toReiArea)
                        .toList());
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDraggableStackVisitor(new FilterGhostVisitor());
    }

    private static final class FilterGhostVisitor implements DraggableStackVisitor<Screen> {
        @Override
        public <R extends Screen> boolean isHandingScreen(R screen) {
            return screen instanceof VillagerItemFilterScreen
                    || screen instanceof VillagerAttributeFilterScreen;
        }

        @Override
        public DraggedAcceptorResult acceptDraggedStack(
                DraggingContext<Screen> context,
                DraggableStack draggedStack) {
            ItemStack stack = itemStack(draggedStack);
            Point position = context.getCurrentPosition();
            if (stack.isEmpty() || position == null) {
                return DraggedAcceptorResult.PASS;
            }
            Screen screen = context.getScreen();
            if (screen instanceof VillagerItemFilterScreen itemFilterScreen) {
                int slot = itemFilterScreen.ghostSlotAt(position.x, position.y);
                return RecipeViewerFilterGhostSupport.setItemFilterSlot(itemFilterScreen, slot, stack)
                        ? DraggedAcceptorResult.ACCEPTED
                        : DraggedAcceptorResult.PASS;
            }
            if (screen instanceof VillagerAttributeFilterScreen attributeFilterScreen
                    && attributeFilterScreen.isReferenceSlotAt(position.x, position.y)) {
                return RecipeViewerFilterGhostSupport.setAttributeFilterReference(attributeFilterScreen, stack)
                        ? DraggedAcceptorResult.ACCEPTED
                        : DraggedAcceptorResult.PASS;
            }
            return DraggedAcceptorResult.PASS;
        }

        @Override
        public Stream<BoundsProvider> getDraggableAcceptingBounds(
                DraggingContext<Screen> context,
                DraggableStack draggedStack) {
            if (itemStack(draggedStack).isEmpty()) {
                return Stream.empty();
            }
            Screen screen = context.getScreen();
            if (screen instanceof VillagerItemFilterScreen itemFilterScreen) {
                return itemFilterScreen.ghostSlotAreas().stream()
                        .map(VillagerRetaliationReiPlugin::toReiArea)
                        .map(BoundsProvider::ofRectangle);
            }
            if (screen instanceof VillagerAttributeFilterScreen attributeFilterScreen) {
                return Stream.of(BoundsProvider.ofRectangle(
                        toReiArea(attributeFilterScreen.referenceSlotArea())));
            }
            return Stream.empty();
        }
    }

    private static ItemStack itemStack(DraggableStack draggedStack) {
        EntryStack<?> entry = draggedStack.getStack();
        if (!entry.getType().equals(VanillaEntryTypes.ITEM)) {
            return ItemStack.EMPTY;
        }
        return entry.castValue();
    }

    private static Rectangle toReiArea(ClientScreenArea area) {
        return new Rectangle(area.left(), area.top(), area.width(), area.height());
    }
}
