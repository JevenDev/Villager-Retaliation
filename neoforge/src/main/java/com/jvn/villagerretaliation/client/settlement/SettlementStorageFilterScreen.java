package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class SettlementStorageFilterScreen extends Screen {
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
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private boolean ingredients;
    private boolean submitted;
    private boolean uncertain;
    private String query = "";
    private Component feedback = Component.literal("Draft filter. Saving changes this physical filter for every container user.");
    private Button sample;
    private Button primary;
    private Button less;
    private Button more;
    private Button previous;
    private Button next;
    private Button save;

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
        this.panelWidth = Math.min(580, this.width - 24);
        this.panelHeight = Math.min(390, this.height - 20);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.rows.clear();
        if (VillagerRetaliationItems.isRecipeFilter(this.draft)) {
            this.catalog = VillagerRecipeFilterData.catalog(this.minecraft.level);
            var search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 22, this.panelWidth, 18, Component.literal("Search recipe")));
            search.setHint(Component.literal("Search recipe name or ID"));
            search.setValue(this.query);
            search.setResponder(value -> { this.query = value; this.offset = 0; refreshChoices(); update(); });
        } else this.sample = button("", this.left, this.top + 22, this.panelWidth, this::cycleSample);
        int count = Math.max(1, (this.panelHeight - 138) / 22);
        for (int index = 0; index < count; index++) {
            int row = index;
            this.rows.add(button("", this.left, this.top + 46 + index * 22, this.panelWidth, () -> select(this.offset + row)));
        }
        int bottom = this.top + this.panelHeight;
        this.previous = button("<", this.left, bottom - 88, 24, () -> { this.offset = Math.max(0, this.offset - this.rows.size()); update(); });
        this.next = button(">", this.left + 28, bottom - 88, 24, () -> { this.offset += this.rows.size(); update(); });
        int width = (this.panelWidth - 76) / 4;
        this.primary = button(VillagerRetaliationItems.isRecipeFilter(this.draft) ? "Ingredients" : "Use sample",
                this.left + 60, bottom - 88, width, () -> {
                    if (VillagerRetaliationItems.isRecipeFilter(this.draft)) { this.ingredients = !this.ingredients; this.offset = 0; }
                    else if (!VillagerItemFilterData.setEntry(this.draft, this.selected, sample())) this.feedback = Component.literal("That entry is unchanged, duplicated, or exceeds the nesting limit.");
                    update();
                });
        button("Clear", this.left + 64 + width, bottom - 88, width, () -> {
            if (VillagerRetaliationItems.isItemFilter(this.draft)) VillagerItemFilterData.setEntry(this.draft, this.selected, ItemStack.EMPTY);
            else if (VillagerRetaliationItems.isAttributeFilter(this.draft)) VillagerAttributeFilterData.clear(this.draft);
            else VillagerRecipeFilterData.clear(this.draft);
            refreshChoices();
            update();
        });
        this.less = button("Limit -", this.left + 68 + width * 2, bottom - 88, width, () -> { VillagerItemFilterData.adjustAmount(this.draft, this.selected, -1); update(); });
        this.more = button("Limit +", this.left + 72 + width * 3, bottom - 88, width, () -> { VillagerItemFilterData.adjustAmount(this.draft, this.selected, 1); update(); });
        button("Cancel", this.left, bottom - 20, 90, this::onClose);
        this.save = button("Save physical filter", this.left + this.panelWidth - 140, bottom - 20, 140, this::save);
        refreshChoices();
        update();
    }

    private void fit(Button button, String label) {
        boolean clipped = this.font.width(label) > button.getWidth() - 8;
        button.setMessage(Component.literal(clipped ? this.font.plainSubstrByWidth(label, button.getWidth() - 20) + "..." : label));
        button.setTooltip(Tooltip.create(Component.literal(label)));
    }

    private Button button(String text, int x, int y, int width, Runnable action) {
        var button = addRenderableWidget(Button.builder(Component.empty(), ignored -> { if (!this.parent.parent().submitting()) action.run(); })
                .bounds(x, y, width, 20).build());
        fit(button, text);
        return button;
    }

    private ItemStack sample() { return this.minecraft.player.getInventory().getItem(this.sampleSlot); }

    private void cycleSample() {
        for (int count = 0; count < 36; count++) {
            this.sampleSlot = (this.sampleSlot + 1) % 36;
            if (!sample().isEmpty()) break;
        }
        refreshChoices();
        update();
    }

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
        return VillagerRetaliationItems.isItemFilter(this.draft) ? VillagerItemFilterData.ENTRY_COUNT
                : VillagerRetaliationItems.isAttributeFilter(this.draft) ? this.attributes.size()
                : this.ingredients ? 9 : this.recipes.size();
    }

    private void select(int index) {
        if (index >= count()) return;
        this.selected = index;
        if (VillagerRetaliationItems.isAttributeFilter(this.draft)) {
            VillagerAttributeFilterData.toggleSelected(this.draft, this.attributes.get(index));
        } else if (VillagerRetaliationItems.isRecipeFilter(this.draft)) {
            if (!this.ingredients) VillagerRecipeFilterData.setRecipe(this.draft, this.minecraft.level, this.recipes.get(index).id());
            else {
                var resolution = VillagerRecipeFilterData.resolve(this.minecraft.level, this.draft);
                if (resolution.valid() && index < resolution.recipe().value().getIngredients().size()) {
                    var choices = VillagerRecipeFilterData.ingredientChoices(resolution.recipe().value().getIngredients().get(index)).stream()
                            .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())).toList();
                    var current = resolution.configuration().narrowedIngredients().get(index);
                    int next = current == null ? 0 : choices.indexOf(current) + 1;
                    VillagerRecipeFilterData.setIngredient(this.draft, this.minecraft.level, index, next < choices.size() ? choices.get(next) : null);
                }
            }
        }
        update();
    }

    private String label(int index) {
        if (VillagerRetaliationItems.isItemFilter(this.draft)) {
            var item = VillagerItemFilterData.entry(this.draft, index);
            return (index == this.selected ? "> " : "") + (index + 1) + ". " + (item.isEmpty() ? "Empty" : item.getHoverName().getString())
                    + " / limit " + VillagerItemFilterData.formatAmount(VillagerItemFilterData.amount(this.draft, index));
        }
        if (VillagerRetaliationItems.isAttributeFilter(this.draft)) {
            var attribute = this.attributes.get(index);
            return (VillagerAttributeFilterData.read(this.draft).attributes().contains(attribute) ? "[x] " : "[ ] ") + attribute.display().getString();
        }
        var config = VillagerRecipeFilterData.read(this.draft);
        if (!this.ingredients) return (this.recipes.get(index).id().equals(config.recipeId()) ? "[x] " : "[ ] ") + this.recipes.get(index).id();
        var selected = config.narrowedIngredients().get(index);
        return "Ingredient " + (index + 1) + ": " + (selected == null ? "Any matching alternative" : selected.toString());
    }

    private void update() {
        if (this.save == null) return;
        this.offset = Math.min(this.offset, Math.max(0, (count() - 1) / this.rows.size() * this.rows.size()));
        for (int index = 0; index < this.rows.size(); index++) {
            int row = this.offset + index;
            var button = this.rows.get(index);
            button.visible = row < count();
            if (button.visible) {
                var label = label(row);
                button.setMessage(Component.literal(this.font.plainSubstrByWidth(label, this.panelWidth - 12)));
                button.setTooltip(Tooltip.create(Component.literal(label)));
            }
        }
        if (this.sample != null) fit(this.sample, "Inventory sample: " + (sample().isEmpty() ? "empty" : sample().getHoverName().getString()) + " (click to cycle)");
        this.previous.active = this.offset > 0;
        this.next.active = this.offset + this.rows.size() < count();
        this.primary.visible = !VillagerRetaliationItems.isAttributeFilter(this.draft);
        if (VillagerRetaliationItems.isRecipeFilter(this.draft)) fit(this.primary, this.ingredients ? "Recipes" : "Ingredients");
        boolean amounts = VillagerRetaliationItems.isItemFilter(this.draft)
                && VillagerFilterPolicy.read(this.draft).state() == VillagerFilterPolicy.PolicyState.LEGACY
                && VillagerItemFilterData.isAmountEntry(VillagerItemFilterData.entry(this.draft, this.selected));
        this.less.visible = this.more.visible = amounts;
        this.save.active = !this.parent.parent().submitting() && !this.uncertain && !ItemStack.matches(this.original.item(), this.draft);
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + this.panelHeight + 8, 0xE0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF);
        SettlementManagementScreen.renderFeedback(graphics, this.font, this.feedback, this.left, this.top + this.panelHeight - 60, this.panelWidth, mouseX, mouseY);
    }
}
