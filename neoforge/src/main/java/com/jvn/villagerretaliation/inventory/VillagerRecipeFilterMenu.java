package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeSemantics;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Server-authoritative editor for exact recipe identity and ingredient alternatives. */
public final class VillagerRecipeFilterMenu extends AbstractContainerMenu
        implements VillagerFilterPolicyMenu {
    public static final int RESULT_SLOT = 0;
    public static final int INGREDIENT_SLOT_START = 1;
    public static final int DISPLAY_SLOT_COUNT = 1 + VillagerRecipeFilterData.MAX_INGREDIENTS;
    private static final int PLAYER_INVENTORY_COUNT = 27;
    private static final int PLAYER_HOTBAR_COUNT = 9;
    private static final int PLAYER_SLOT_START = DISPLAY_SLOT_COUNT;
    private static final int PLAYER_HOTBAR_START = PLAYER_SLOT_START + PLAYER_INVENTORY_COUNT;
    private static final int SLOT_SIZE = 18;

    private final Inventory playerInventory;
    private final SimpleContainer displayInventory = new SimpleContainer(DISPLAY_SLOT_COUNT);
    private final ItemStack contentHolder;
    private List<RecipeHolder<?>> cachedCatalog = List.of();
    private long cachedCatalogRevision = Long.MIN_VALUE;

    public VillagerRecipeFilterMenu(
            int containerId,
            Inventory playerInventory,
            RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, decodeOpeningStack(data));
    }

    public VillagerRecipeFilterMenu(int containerId, Inventory playerInventory, ItemStack openingStack) {
        super(VillagerRetaliationMenus.RECIPE_FILTER.get(), containerId);
        this.playerInventory = playerInventory;
        ItemStack selected = playerInventory.getSelected();
        this.contentHolder = VillagerRetaliationItems.isRecipeFilter(selected) ? selected : openingStack;
        addSlot(new DisplaySlot(this.displayInventory, RESULT_SLOT, 8, 18));
        for (int slot = 0; slot < VillagerRecipeFilterData.MAX_INGREDIENTS; slot++) {
            addSlot(new DisplaySlot(
                    this.displayInventory,
                    INGREDIENT_SLOT_START + slot,
                    8 + slot * SLOT_SIZE,
                    42));
        }
        addPlayerSlots(playerInventory);
        refreshDisplay();
    }

    @Override
    public VillagerFilterPolicy.Policy filterPolicy() {
        return VillagerFilterPolicy.read(this.contentHolder);
    }

    @Override
    public boolean applyPolicyChange(VillagerFilterPolicy.PolicyField field, int value) {
        if (!isEditingHeldFilter()) {
            return false;
        }
        boolean changed = VillagerFilterPolicy.applyChange(this.contentHolder, field, value);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    @Override
    public void applyClientPolicyChange(VillagerFilterPolicy.PolicyField field, int value) {
        if (isEditingHeldFilter()) {
            VillagerFilterPolicy.applyChange(this.contentHolder, field, value);
        }
    }

    public ItemStack editedFilter() {
        return this.contentHolder;
    }

    public VillagerRecipeFilterData.Resolution resolution() {
        return VillagerRecipeFilterData.resolve(this.playerInventory.player.level(), this.contentHolder);
    }

    public List<RecipeHolder<?>> catalog() {
        long revision = VillagerRecipeSemantics.revision();
        if (this.cachedCatalogRevision != revision) {
            this.cachedCatalog = VillagerRecipeFilterData.catalog(this.playerInventory.player.level());
            this.cachedCatalogRevision = revision;
        }
        return this.cachedCatalog;
    }

    public ResourceLocation currentRecipeId() {
        return VillagerRecipeFilterData.read(this.contentHolder).recipeId();
    }

    public boolean selectRecipe(ResourceLocation recipeId) {
        if (!isEditingHeldFilter()) {
            return false;
        }
        boolean changed = VillagerRecipeFilterData.setRecipe(
                this.contentHolder, this.playerInventory.player.level(), recipeId);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public IngredientSelection cycleIngredient(int slot, int direction) {
        VillagerRecipeFilterData.Resolution resolution = resolution();
        if (!isEditingHeldFilter()
                || !resolution.valid()
                || slot < 0
                || slot >= resolution.recipe().value().getIngredients().size()
                || direction == 0) {
            return IngredientSelection.unchanged();
        }
        Ingredient ingredient = resolution.recipe().value().getIngredients().get(slot);
        List<ItemStack> choices = VillagerRecipeFilterData.ingredientChoices(ingredient);
        if (choices.isEmpty()) {
            return IngredientSelection.unchanged();
        }
        ResourceLocation current =
                resolution.configuration().narrowedIngredients().get(slot);
        int state = 0;
        if (current != null) {
            for (int index = 0; index < choices.size(); index++) {
                if (BuiltInRegistries.ITEM.getKey(choices.get(index).getItem()).equals(current)) {
                    state = index + 1;
                    break;
                }
            }
        }
        int next = Math.floorMod(state + Integer.signum(direction), choices.size() + 1);
        ResourceLocation selected = next == 0
                ? null
                : BuiltInRegistries.ITEM.getKey(choices.get(next - 1).getItem());
        if (!VillagerRecipeFilterData.setIngredient(
                this.contentHolder, this.playerInventory.player.level(), slot, selected)) {
            return IngredientSelection.unchanged();
        }
        markChanged();
        return new IngredientSelection(true, selected);
    }

    public boolean setIngredient(int slot, ResourceLocation itemId) {
        if (!isEditingHeldFilter()) {
            return false;
        }
        boolean changed = VillagerRecipeFilterData.setIngredient(
                this.contentHolder, this.playerInventory.player.level(), slot, itemId);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public boolean narrowed(int ingredientSlot) {
        return VillagerRecipeFilterData.read(this.contentHolder)
                .narrowedIngredients()
                .containsKey(ingredientSlot);
    }

    public int alternativeCount(int ingredientSlot) {
        VillagerRecipeFilterData.Resolution resolution = resolution();
        if (!resolution.valid()
                || ingredientSlot < 0
                || ingredientSlot >= resolution.recipe().value().getIngredients().size()) {
            return 0;
        }
        return VillagerRecipeFilterData
                .ingredientChoices(resolution.recipe().value().getIngredients().get(ingredientSlot))
                .size();
    }

    public boolean isEditingHeldFilter() {
        return VillagerRetaliationItems.isRecipeFilter(this.contentHolder)
                && this.playerInventory.getSelected() == this.contentHolder;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && isEditingHeldFilter();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!isEditingHeldFilter() || slotId >= 0 && slotId < DISPLAY_SLOT_COUNT) {
            return;
        }
        if (isHeldFilterMenuSlot(slotId)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container == this.playerInventory && !isHeldFilterMenuSlot(this.slots.indexOf(slot));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        int menuSlot = this.slots.indexOf(slot);
        return slot.container == this.playerInventory && !isHeldFilterMenuSlot(menuSlot);
    }

    private void markChanged() {
        refreshDisplay();
        this.playerInventory.setChanged();
        broadcastChanges();
    }

    private void refreshDisplay() {
        this.displayInventory.clearContent();
        VillagerRecipeFilterData.Resolution resolution = resolution();
        if (!resolution.valid()) {
            this.displayInventory.setChanged();
            return;
        }
        ItemStack result = resolution.recipe().value()
                .getResultItem(this.playerInventory.player.level().registryAccess());
        this.displayInventory.setItem(RESULT_SLOT, result.copy());
        List<Ingredient> ingredients = resolution.recipe().value().getIngredients();
        for (int slot = 0; slot < Math.min(ingredients.size(), VillagerRecipeFilterData.MAX_INGREDIENTS); slot++) {
            Ingredient ingredient = ingredients.get(slot);
            if (ingredient.isEmpty()) {
                continue;
            }
            ResourceLocation selected = resolution.configuration().narrowedIngredients().get(slot);
            ItemStack display;
            if (selected == null) {
                List<ItemStack> choices = VillagerRecipeFilterData.ingredientChoices(ingredient);
                display = choices.isEmpty() ? ItemStack.EMPTY : choices.getFirst().copyWithCount(1);
            } else {
                display = new ItemStack(BuiltInRegistries.ITEM.get(selected));
            }
            this.displayInventory.setItem(INGREDIENT_SLOT_START + slot, display);
        }
        this.displayInventory.setChanged();
    }

    private boolean isHeldFilterMenuSlot(int menuSlot) {
        return menuSlot == PLAYER_HOTBAR_START + this.playerInventory.selected;
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        9 + row * 9 + column,
                        8 + column * SLOT_SIZE,
                        84 + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < PLAYER_HOTBAR_COUNT; column++) {
            addSlot(new Slot(inventory, column, 8 + column * SLOT_SIZE, 142));
        }
    }

    private static ItemStack decodeOpeningStack(RegistryFriendlyByteBuf data) {
        return data == null || !data.isReadable() ? ItemStack.EMPTY : ItemStack.STREAM_CODEC.decode(data);
    }

    public record IngredientSelection(boolean changed, ResourceLocation itemId) {
        private static IngredientSelection unchanged() {
            return new IngredientSelection(false, null);
        }
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
