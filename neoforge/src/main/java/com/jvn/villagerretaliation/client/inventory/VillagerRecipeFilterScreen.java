package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.inventory.VillagerRecipeFilterMenu;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.network.FilterPolicyChangePayload;
import com.jvn.villagerretaliation.network.RecipeFilterIngredientPayload;
import com.jvn.villagerretaliation.network.RecipeFilterSelectPayload;
import com.jvn.toucanlib.client.interaction.ToucanInputModifiers;
import com.jvn.toucanlib.client.interaction.ToucanSlotAmounts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact exact-recipe browser with per-ingredient alternative cycling. */
public final class VillagerRecipeFilterScreen extends AbstractContainerScreen<VillagerRecipeFilterMenu> {
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 166;

    private Button previousButton;
    private Button clearButton;
    private Button nextButton;
    private Button modeButton;
    private Button directionButton;
    private Button stockTargetButton;

    public VillagerRecipeFilterScreen(
            VillagerRecipeFilterMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        this.imageWidth = TEXTURE_WIDTH;
        this.imageHeight = TEXTURE_HEIGHT;
        this.inventoryLabelY = -100;
    }

    @Override
    protected void init() {
        super.init();
        this.previousButton = Button.builder(
                        Component.literal("<"),
                        button -> moveRecipe(-1))
                .bounds(this.leftPos + 8, this.topPos + 63, 18, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "villagerretaliation.gui.recipe_filter.previous.description")))
                .build();
        this.clearButton = Button.builder(
                        Component.literal("X"),
                        button -> selectRecipe(null))
                .bounds(this.leftPos + 28, this.topPos + 63, 18, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "villagerretaliation.gui.recipe_filter.clear.description")))
                .build();
        this.nextButton = Button.builder(
                        Component.literal(">"),
                        button -> moveRecipe(1))
                .bounds(this.leftPos + 48, this.topPos + 63, 18, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "villagerretaliation.gui.recipe_filter.next.description")))
                .build();
        this.modeButton = Button.builder(Component.empty(), button -> cycleMode())
                .bounds(this.leftPos + 68, this.topPos + 63, 48, 18)
                .build();
        this.directionButton = Button.builder(Component.empty(), button -> cycleDirection())
                .bounds(this.leftPos + 118, this.topPos + 63, 50, 18)
                .build();
        this.stockTargetButton = Button.builder(Component.empty(), button -> toggleStockTarget())
                .bounds(this.leftPos + 120, this.topPos + 18, 48, 18)
                .build();
        addRenderableWidget(this.previousButton);
        addRenderableWidget(this.clearButton);
        addRenderableWidget(this.nextButton);
        addRenderableWidget(this.modeButton);
        addRenderableWidget(this.directionButton);
        addRenderableWidget(this.stockTargetButton);
        refreshButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                VillagerRetaliationClientAssets.ITEM_FILTER_CONTAINER_TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight,
                this.imageWidth,
                this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        ResourceLocation recipeId = this.menu.currentRecipeId();
        Component label = recipeId == null
                ? Component.translatable("villagerretaliation.gui.recipe_filter.none")
                : Component.literal(this.font.plainSubstrByWidth(recipeId.toString(), 84));
        graphics.drawString(this.font, label, 30, 22, 0xFF404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = ingredientSlotAt((int) mouseX, (int) mouseY);
        if (slot >= 0 && (button == 0 || button == 1)) {
            cycleIngredient(slot, button == 0 ? 1 : -1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0
                && this.stockTargetButton != null
                && this.stockTargetButton.isMouseOver(mouseX, mouseY)
                && this.menu.filterPolicy().listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING) {
            int step = ToucanSlotAmounts.step(ToucanInputModifiers.current());
            adjustStock(scrollY > 0.0 ? step : -step);
            return true;
        }
        int slot = ingredientSlotAt((int) mouseX, (int) mouseY);
        if (slot >= 0 && scrollY != 0.0) {
            cycleIngredient(slot, scrollY > 0.0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (this.hoveredSlot == null
                || this.hoveredSlot.index < VillagerRecipeFilterMenu.INGREDIENT_SLOT_START
                || this.hoveredSlot.index >= VillagerRecipeFilterMenu.DISPLAY_SLOT_COUNT) {
            return tooltip;
        }
        int ingredient = this.hoveredSlot.index - VillagerRecipeFilterMenu.INGREDIENT_SLOT_START;
        int alternatives = this.menu.alternativeCount(ingredient);
        if (alternatives > 1) {
            tooltip.add(Component.translatable(
                            this.menu.narrowed(ingredient)
                                    ? "villagerretaliation.gui.recipe_filter.ingredient.exact"
                                    : "villagerretaliation.gui.recipe_filter.ingredient.any",
                            alternatives)
                    .withStyle(this.menu.narrowed(ingredient)
                            ? ChatFormatting.AQUA
                            : ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                            "villagerretaliation.gui.recipe_filter.ingredient.controls")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return tooltip;
    }

    private void moveRecipe(int delta) {
        List<RecipeHolder<?>> catalog = this.menu.catalog();
        if (catalog.isEmpty()) {
            return;
        }
        ResourceLocation current = this.menu.currentRecipeId();
        int index = -1;
        for (int candidate = 0; candidate < catalog.size(); candidate++) {
            if (catalog.get(candidate).id().equals(current)) {
                index = candidate;
                break;
            }
        }
        int next = index < 0
                ? delta > 0 ? 0 : catalog.size() - 1
                : Math.floorMod(index + Integer.signum(delta), catalog.size());
        selectRecipe(catalog.get(next).id());
    }

    private void selectRecipe(ResourceLocation recipeId) {
        if (this.menu.selectRecipe(recipeId)) {
            PacketDistributor.sendToServer(new RecipeFilterSelectPayload(
                    recipeId == null ? "" : recipeId.toString()));
        }
        refreshButtons();
    }

    private void cycleIngredient(int ingredient, int direction) {
        VillagerRecipeFilterMenu.IngredientSelection selection =
                this.menu.cycleIngredient(ingredient, direction);
        if (selection.changed()) {
            PacketDistributor.sendToServer(new RecipeFilterIngredientPayload(
                    ingredient,
                    selection.itemId() == null ? "" : selection.itemId().toString()));
        }
    }

    private void cycleMode() {
        VillagerFilterPolicy.ListMode current = this.menu.filterPolicy().listMode();
        VillagerFilterPolicy.ListMode next = current == VillagerFilterPolicy.ListMode.ALLOW_MATCHING
                ? VillagerFilterPolicy.ListMode.DENY_MATCHING
                : VillagerFilterPolicy.ListMode.ALLOW_MATCHING;
        changePolicy(VillagerFilterPolicy.PolicyField.LIST_MODE, next.networkId());
    }

    private void cycleDirection() {
        VillagerFilterPolicy.TransferDirection current = this.menu.filterPolicy().direction();
        VillagerFilterPolicy.TransferDirection next = switch (current) {
            case RECEIVE -> VillagerFilterPolicy.TransferDirection.PROVIDE;
            case PROVIDE -> VillagerFilterPolicy.TransferDirection.BOTH;
            case BOTH -> VillagerFilterPolicy.TransferDirection.RECEIVE;
        };
        changePolicy(VillagerFilterPolicy.PolicyField.DIRECTION, next.networkId());
    }

    private void toggleStockTarget() {
        int target = this.menu.filterPolicy().stockTarget().isPresent() ? 0 : 64;
        changePolicy(VillagerFilterPolicy.PolicyField.STOCK_TARGET, target);
    }

    private void adjustStock(int delta) {
        changePolicy(VillagerFilterPolicy.PolicyField.STOCK_DELTA, delta);
    }

    private void changePolicy(VillagerFilterPolicy.PolicyField field, int value) {
        this.menu.applyClientPolicyChange(field, value);
        PacketDistributor.sendToServer(new FilterPolicyChangePayload(field, value));
        refreshButtons();
    }

    private void refreshButtons() {
        if (this.previousButton == null || this.modeButton == null
                || this.directionButton == null || this.stockTargetButton == null) {
            return;
        }
        boolean hasRecipes = !this.menu.catalog().isEmpty();
        this.previousButton.active = hasRecipes;
        this.nextButton.active = hasRecipes;
        this.clearButton.active = this.menu.currentRecipeId() != null;
        VillagerFilterPolicy.Policy policy = this.menu.filterPolicy();
        this.modeButton.setMessage(Component.translatable(
                "villagerretaliation.gui.recipe_filter.mode." + policy.listMode().id()));
        this.modeButton.setTooltip(Tooltip.create(Component.translatable(
                "villagerretaliation.gui.filter_policy.mode." + policy.listMode().id() + ".description")));
        this.directionButton.setMessage(Component.translatable(
                "villagerretaliation.gui.filter_policy.direction." + policy.direction().id()));
        this.directionButton.setTooltip(Tooltip.create(Component.translatable(
                "villagerretaliation.gui.filter_policy.direction.description")));
        Component target = policy.stockTarget().isPresent()
                ? Component.literal(Integer.toString(policy.stockTarget().getAsInt()))
                : Component.translatable("villagerretaliation.gui.filter_policy.stock.unlimited");
        this.stockTargetButton.setMessage(target);
        Component summary = Component.translatable(
                "villagerretaliation.gui.filter_policy.stock." + policy.direction().id(), target);
        boolean quantitative = policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING;
        this.stockTargetButton.setTooltip(Tooltip.create(summary.copy()
                .append("\n")
                .append(Component.translatable(quantitative
                        ? "villagerretaliation.gui.recipe_filter.stock.description"
                        : "villagerretaliation.gui.filter_policy.stock.inactive_deny"))));
        this.stockTargetButton.active = quantitative;
    }

    private int ingredientSlotAt(int mouseX, int mouseY) {
        for (int menuSlot = VillagerRecipeFilterMenu.INGREDIENT_SLOT_START;
                menuSlot < VillagerRecipeFilterMenu.DISPLAY_SLOT_COUNT;
                menuSlot++) {
            Slot slot = this.menu.slots.get(menuSlot);
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                return menuSlot - VillagerRecipeFilterMenu.INGREDIENT_SLOT_START;
            }
        }
        return -1;
    }
}
