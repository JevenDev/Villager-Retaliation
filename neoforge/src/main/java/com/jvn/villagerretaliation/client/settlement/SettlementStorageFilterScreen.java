package com.jvn.villagerretaliation.client.settlement;

import com.jvn.toucanlib.client.interaction.ToucanInputModifiers;
import com.jvn.toucanlib.client.interaction.ToucanSlotAmounts;
import com.jvn.villagerretaliation.client.ui.VillagerItemSlot;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class SettlementStorageFilterScreen extends VillagerWindowScreen {
    private final SettlementStorageScreen parent;
    private final UUID scope;
    private final BlockPos position;
    private final SettlementManagementPayload.StorageFilter original;
    private final ItemStack draft;
    private final List<Button> rows = new ArrayList<>();
    private List<RecipeHolder<?>> catalog = List.of();
    private List<RecipeHolder<?>> recipes = List.of();
    private List<VillagerAttributeFilterData.Attribute> attributes = List.of();
    private int sampleSlot;
    private int selected;
    private int offset;
    private boolean ingredients;
    private boolean submitted;
    private boolean uncertain;
    private String query = "";
    private Component feedback = Component.literal("Draft filter. Saving changes this physical filter for every container user.");
    private final List<VillagerItemSlot> slots = new ArrayList<>();
    private final List<VillagerItemSlot> inventory = new ArrayList<>();
    private Button primary;
    private Button clear;
    private Button less;
    private Button more;
    private Button previous;
    private Button next;
    private Button save;
    private VillagerItemSlot output;

    SettlementStorageFilterScreen(SettlementStorageScreen parent, UUID scope, BlockPos position, SettlementManagementPayload.StorageFilter filter) {
        super(Component.literal("Edit framed " + filter.item().getHoverName().getString()));
        this.parent = parent;
        this.scope = scope;
        this.position = position;
        this.original = filter;
        this.draft = filter.item().copy();
    }

    SettlementStorageScreen parent() { return this.parent; }

    @Override
    protected void init() {
        initWindow(340, 280);
        this.rows.clear();
        this.slots.clear();
        this.inventory.clear();
        boolean recipe = VillagerRetaliationItems.isRecipeFilter(this.draft);
        boolean item = VillagerRetaliationItems.isItemFilter(this.draft);
        if (recipe) {
            this.catalog = VillagerRecipeFilterData.catalog(this.minecraft.level);
            var search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 22, this.panelWidth, 18, Component.literal("Search recipe")));
            search.setHint(Component.literal("Search recipe name or ID"));
            search.setValue(this.query);
            search.setResponder(value -> { this.query = value; this.offset = 0; refreshChoices(); update(); });
        }
        if (!item) {
            int count = Math.max(1, (this.panelHeight - (recipe ? 154 : 194)) / 22);
            for (int index = 0; index < count; index++) {
                int row = index;
                this.rows.add(button("", this.left, this.top + (recipe ? 46 : 50) + index * 22, this.panelWidth, () -> select(this.offset + row)));
            }
        }
        int bottom = this.top + this.panelHeight;
        int controls = recipe ? bottom - 104 : this.top + (item ? 52 : 24);
        this.previous = button("<", this.left, controls, 24, () -> { this.offset = Math.max(0, this.offset - this.rows.size()); update(); });
        this.next = button(">", this.left + 28, controls, 24, () -> { this.offset += this.rows.size(); update(); });
        this.primary = button("Ingredients", this.left + 56, controls, this.panelWidth - 122, () -> {
            this.ingredients = !this.ingredients;
            this.offset = 0;
            update();
        });
        this.clear = button("Clear", this.left + this.panelWidth - 62, controls, 62, () -> {
            if (item) VillagerItemFilterData.setEntry(this.draft, this.selected, ItemStack.EMPTY);
            else if (!recipe) VillagerAttributeFilterData.clear(this.draft);
            else VillagerRecipeFilterData.clear(this.draft);
            refreshChoices();
            update();
        });
        if (item || recipe) {
            for (int index = 0; index < 9; index++) {
                int slot = index;
                int x = item ? this.left + (this.panelWidth - 180) / 2 + index * 20 : this.left + 12 + index % 3 * 20;
                int y = this.top + (item ? 24 : 46 + index / 3 * 20);
                this.slots.add(addRenderableWidget(new VillagerItemSlot(x, y, () -> slotItem(slot), button -> {
                    this.selected = slot;
                    if (item) {
                        if (!VillagerItemFilterData.setEntry(this.draft, slot, button == 1 ? ItemStack.EMPTY : sample()))
                            this.feedback = Component.literal("That entry is unchanged, duplicated, or exceeds the nesting limit.");
                    } else cycleIngredient(slot, button == 1 ? -1 : 1);
                    update();
                }, direction -> {
                    if (item) adjustAmount(slot, direction * ToucanSlotAmounts.step(ToucanInputModifiers.current()));
                    else cycleIngredient(slot, direction);
                    update();
                })));
            }
        }
        if (recipe) this.output = addRenderableWidget(new VillagerItemSlot(this.left + this.panelWidth - 24, this.top + 46,
                () -> {
                    var resolution = VillagerRecipeFilterData.resolve(this.minecraft.level, this.draft);
                    return resolution.valid() ? resolution.recipe().value().getResultItem(this.minecraft.level.registryAccess()) : ItemStack.EMPTY;
                }, ignored -> { }, null));
        this.less = button("Limit -", this.left, this.top + 52, 70, () -> adjustAmount(this.selected, -1));
        this.more = button("Limit +", this.left + 74, this.top + 52, 70, () -> adjustAmount(this.selected, 1));
        if (!recipe) {
            int x = this.left + (this.panelWidth - 180) / 2;
            for (int index = 0; index < 36; index++) {
                int slot = index;
                this.inventory.add(addRenderableWidget(new VillagerItemSlot(x + index % 9 * 20, bottom - 132 + index / 9 * 20,
                        () -> this.minecraft.player.getInventory().getItem(slot), button -> {
                    this.sampleSlot = slot;
                    refreshChoices();
                    update();
                }, null)));
            }
        }
        button("Cancel", this.left, bottom - 20, 90, this::onClose);
        this.save = button("Save physical filter", this.left + this.panelWidth - 140, bottom - 20, 140, this::save);
        refreshChoices();
        update();
    }

    private ItemStack slotItem(int slot) {
        if (VillagerRetaliationItems.isItemFilter(this.draft)) return VillagerItemFilterData.entry(this.draft, slot);
        var resolution = VillagerRecipeFilterData.resolve(this.minecraft.level, this.draft);
        if (!resolution.valid() || slot >= resolution.recipe().value().getIngredients().size()) return ItemStack.EMPTY;
        var choices = VillagerRecipeFilterData.ingredientChoices(resolution.recipe().value().getIngredients().get(slot));
        var chosen = resolution.configuration().narrowedIngredients().get(slot);
        return choices.stream().filter(stack -> chosen == null || BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(chosen))
                .findFirst().orElse(ItemStack.EMPTY);
    }

    private void cycleIngredient(int slot, int direction) {
        var resolution = VillagerRecipeFilterData.resolve(this.minecraft.level, this.draft);
        if (!resolution.valid() || slot >= resolution.recipe().value().getIngredients().size()) return;
        var choices = VillagerRecipeFilterData.ingredientChoices(resolution.recipe().value().getIngredients().get(slot)).stream()
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())).toList();
        var current = resolution.configuration().narrowedIngredients().get(slot);
        int next = Math.floorMod((current == null ? -1 : choices.indexOf(current)) + direction + 1, choices.size() + 1) - 1;
        VillagerRecipeFilterData.setIngredient(this.draft, this.minecraft.level, slot, next < 0 ? null : choices.get(next));
    }

    private void adjustAmount(int slot, int delta) {
        if (VillagerFilterPolicy.read(this.draft).state() == VillagerFilterPolicy.PolicyState.LEGACY
                && VillagerItemFilterData.isAmountEntry(VillagerItemFilterData.entry(this.draft, slot)))
            VillagerItemFilterData.adjustAmount(this.draft, slot, delta);
        update();
    }

    private void fit(Button button, String label) {
        boolean clipped = this.font.width(label) > button.getWidth() - 8;
        button.setMessage(Component.literal(clipped ? this.font.plainSubstrByWidth(label, button.getWidth() - 20) + "..." : label));
        button.setTooltip(Tooltip.create(Component.literal(label)));
    }

    private Button button(String text, int x, int y, int width, Runnable action) {
        var button = addRenderableWidget(VillagerUi.buttonBuilder(Component.empty(), ignored -> { if (!this.parent.parent().submitting()) action.run(); })
                .bounds(x, y, width, 20).build());
        fit(button, text);
        return button;
    }

    private ItemStack sample() { return this.minecraft.player.getInventory().getItem(this.sampleSlot); }

    private void refreshChoices() {
        if (VillagerRetaliationItems.isAttributeFilter(this.draft)) {
            var choices = new java.util.LinkedHashSet<>(VillagerAttributeFilterData.read(this.draft).attributes());
            choices.addAll(VillagerAttributeFilterData.availableAttributes(sample(), this.minecraft.level));
            this.attributes = List.copyOf(choices);
        } else if (VillagerRetaliationItems.isRecipeFilter(this.draft)) {
            String needle = this.query.strip().toLowerCase(java.util.Locale.ROOT);
            this.recipes = this.catalog.stream().filter(recipe -> recipe.id().toString().contains(needle)
                    || recipe.value().getResultItem(this.minecraft.level.registryAccess()).getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
        }
    }

    private int count() {
        return VillagerRetaliationItems.isAttributeFilter(this.draft) ? this.attributes.size() : this.recipes.size();
    }

    private void select(int index) {
        if (index >= count()) return;
        this.selected = index;
        if (VillagerRetaliationItems.isAttributeFilter(this.draft)) {
            VillagerAttributeFilterData.toggleSelected(this.draft, this.attributes.get(index));
        } else if (VillagerRetaliationItems.isRecipeFilter(this.draft)) {
            VillagerRecipeFilterData.setRecipe(this.draft, this.minecraft.level, this.recipes.get(index).id());
        }
        update();
    }

    private String label(int index) {
        if (VillagerRetaliationItems.isAttributeFilter(this.draft)) return this.attributes.get(index).display().getString();
        return this.recipes.get(index).value().getResultItem(this.minecraft.level.registryAccess()).getHoverName().getString();
    }

    private void update() {
        if (this.save == null) return;
        boolean allowed = !this.parent.parent().submitting() && this.parent.parent().permits(SettlementPermission.STAFF)
                && this.parent.parent().permits(SettlementPermission.LOGISTICS) && !this.uncertain;
        boolean recipe = VillagerRetaliationItems.isRecipeFilter(this.draft);
        if (!this.rows.isEmpty()) this.offset = Math.min(this.offset, Math.max(0, (count() - 1) / this.rows.size() * this.rows.size()));
        for (int index = 0; index < this.rows.size(); index++) {
            int row = this.offset + index;
            var button = this.rows.get(index);
            button.visible = row < count() && (!recipe || !this.ingredients);
            button.active = allowed;
            if (button.visible) {
                var label = label(row);
                button.setMessage(Component.literal(this.font.plainSubstrByWidth(label, this.panelWidth - 12)));
                button.setTooltip(Tooltip.create(Component.literal(recipe ? label + "\n" + this.recipes.get(row).id() : label)));
                var choice = (VillagerUi.OptionButton) button;
                if (!recipe) choice.checkbox(true, VillagerAttributeFilterData.read(this.draft).attributes().contains(this.attributes.get(row)));
                else {
                    choice.selected(this.recipes.get(row).id().equals(VillagerRecipeFilterData.read(this.draft).recipeId()));
                    choice.icon(this.recipes.get(row).value().getResultItem(this.minecraft.level.registryAccess()));
                }
            }
        }
        for (int index = 0; index < this.inventory.size(); index++) {
            var slot = this.inventory.get(index);
            slot.active = allowed;
            slot.selected(index == this.sampleSlot);
            var stack = this.minecraft.player.getInventory().getItem(index);
            slot.setMessage(stack.isEmpty() ? Component.literal("Empty inventory slot") : stack.getHoverName());
            slot.setTooltip(Tooltip.create(slot.getMessage().copy().append("\nSelect as the filter sample")));
        }
        for (int index = 0; index < this.slots.size(); index++) {
            var slot = this.slots.get(index);
            slot.visible = !recipe || this.ingredients;
            slot.active = allowed && (!recipe || !slotItem(index).isEmpty());
            var resolution = recipe ? VillagerRecipeFilterData.resolve(this.minecraft.level, this.draft) : null;
            var narrowed = resolution != null && resolution.valid() ? resolution.configuration().narrowedIngredients().get(index) : null;
            slot.selected(recipe ? narrowed != null : index == this.selected);
            var stack = slotItem(index);
            Component label = stack.isEmpty() ? Component.literal("Empty filter entry") : stack.getHoverName().copy();
            if (!recipe) label = label.copy().append("\nLimit: " + VillagerItemFilterData.formatAmount(VillagerItemFilterData.amount(this.draft, index))
                    + "\nLeft-click uses the inventory sample. Right-click clears. Scroll adjusts the limit.");
            else label = label.copy().append(narrowed == null ? "\nAny matching item" : "\nOnly this item")
                    .append("\nClick or scroll to cycle alternatives, including any matching item.");
            slot.setMessage(label);
            slot.setTooltip(Tooltip.create(label));
        }
        if (this.output != null) {
            this.output.visible = this.ingredients;
            this.output.setMessage(Component.literal("Selected recipe output"));
            this.output.setTooltip(Tooltip.create(this.output.getMessage()));
        }
        this.previous.visible = this.next.visible = !this.rows.isEmpty() && (!recipe || !this.ingredients);
        this.previous.active = this.offset > 0;
        this.next.active = this.offset + this.rows.size() < count();
        this.primary.visible = recipe;
        this.primary.active = this.clear.active = this.less.active = this.more.active = allowed;
        if (VillagerRetaliationItems.isRecipeFilter(this.draft)) fit(this.primary, this.ingredients ? "Recipes" : "Ingredients");
        boolean amounts = VillagerRetaliationItems.isItemFilter(this.draft)
                && VillagerFilterPolicy.read(this.draft).state() == VillagerFilterPolicy.PolicyState.LEGACY
                && VillagerItemFilterData.isAmountEntry(VillagerItemFilterData.entry(this.draft, this.selected));
        this.less.visible = this.more.visible = amounts;
        this.save.active = allowed && !ItemStack.matches(this.original.item(), this.draft);
    }

    private void save() {
        if (!this.save.active) return;
        this.submitted = true;
        this.parent.parent().submit(new SettlementManagementActionPayload.SaveStorageFilter(this.scope, this.position, this.original.frame(), this.original.item(), this.draft));
        this.feedback = Component.literal("Saving the physical framed filter...");
        update();
    }

    void result(SettlementManagementActionResultPayload payload) {
        this.submitted = false;
        this.feedback = SettlementManagementScreen.resultMessage(payload);
        if (payload.applied()) onClose();
        update();
    }

    @Override
    public void tick() {
        if (this.submitted && !this.parent.parent().submitting()) {
            this.submitted = false;
            this.uncertain = true;
            this.feedback = Component.literal("Outcome unconfirmed. Close and reopen the filter to check its saved state.");
        }
        update();
    }

    @Override
    public void onClose() {
        if (this.parent.parent().submitting()) return;
        this.minecraft.setScreen(this.parent);
        this.parent.request();
    }
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF);
        if (!VillagerRetaliationItems.isRecipeFilter(this.draft)) {
            graphics.drawString(this.font, "Inventory sample", this.left + (this.panelWidth - 180) / 2,
                    this.top + this.panelHeight - 144, 0xBBBBBB, false);
            if (VillagerRetaliationItems.isAttributeFilter(this.draft)) {
                graphics.renderItem(sample(), this.left + 60, this.top + 25);
                if (mouseX >= this.left + 60 && mouseX < this.left + 76 && mouseY >= this.top + 25 && mouseY < this.top + 41)
                    graphics.renderTooltip(this.font, sample(), mouseX, mouseY);
            }
        }
        SettlementManagementScreen.renderFeedback(graphics, this.font, this.feedback, this.left, this.top + this.panelHeight - 48, this.panelWidth, mouseX, mouseY);
    }
}
