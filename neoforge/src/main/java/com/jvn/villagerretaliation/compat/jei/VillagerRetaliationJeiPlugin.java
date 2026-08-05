package com.jvn.villagerretaliation.compat.jei;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.inventory.VillagerAttributeFilterScreen;
import com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen;
import com.jvn.villagerretaliation.client.inventory.VillagerItemFilterScreen;
import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import com.jvn.villagerretaliation.client.ui.ClientScreenArea;
import com.jvn.villagerretaliation.compat.RecipeViewerFilterGhostSupport;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class VillagerRetaliationJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = VillagerRetaliation.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(
                VillagerInventoryScreen.class,
                new IGuiContainerHandler<>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(VillagerInventoryScreen screen) {
                        return screen.additionalRecipeViewerExclusionAreas().stream()
                                .map(VillagerRetaliationJeiPlugin::toJeiArea)
                                .toList();
                    }
                });
        registration.addGuiContainerHandler(
                InventoryScreen.class,
                new IGuiContainerHandler<>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                        return PartyInventoryOverlay.recipeViewerExclusionAreas(screen).stream()
                                .map(VillagerRetaliationJeiPlugin::toJeiArea)
                                .toList();
                    }
                });
        registration.addGhostIngredientHandler(
                VillagerItemFilterScreen.class,
                new IGhostIngredientHandler<>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(
                            VillagerItemFilterScreen screen,
                            ITypedIngredient<I> ingredient,
                            boolean doStart) {
                        ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                        if (stack.isEmpty()) {
                            return List.of();
                        }
                        List<ClientScreenArea> areas = screen.ghostSlotAreas();
                        List<Target<I>> targets = new ArrayList<>(areas.size());
                        for (int slot = 0; slot < areas.size(); slot++) {
                            targets.add(itemFilterTarget(screen, slot, areas.get(slot), stack));
                        }
                        return targets;
                    }

                    @Override
                    public void onComplete() {
                    }
                });
        registration.addGhostIngredientHandler(
                VillagerAttributeFilterScreen.class,
                new IGhostIngredientHandler<>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(
                            VillagerAttributeFilterScreen screen,
                            ITypedIngredient<I> ingredient,
                            boolean doStart) {
                        ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                        if (stack.isEmpty()) {
                            return List.of();
                        }
                        return List.of(new Target<>() {
                            @Override
                            public Rect2i getArea() {
                                return toJeiArea(screen.referenceSlotArea());
                            }

                            @Override
                            public void accept(I ignored) {
                                RecipeViewerFilterGhostSupport.setAttributeFilterReference(screen, stack);
                            }
                        });
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    private static <I> IGhostIngredientHandler.Target<I> itemFilterTarget(
            VillagerItemFilterScreen screen,
            int slot,
            ClientScreenArea area,
            ItemStack stack) {
        return new IGhostIngredientHandler.Target<>() {
            @Override
            public Rect2i getArea() {
                return toJeiArea(area);
            }

            @Override
            public void accept(I ignored) {
                RecipeViewerFilterGhostSupport.setItemFilterSlot(screen, slot, stack);
            }
        };
    }

    private static Rect2i toJeiArea(ClientScreenArea area) {
        return new Rect2i(area.left(), area.top(), area.width(), area.height());
    }
}
