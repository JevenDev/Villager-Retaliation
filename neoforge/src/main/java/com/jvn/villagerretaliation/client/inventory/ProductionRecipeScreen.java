package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.network.ProductionRequestPayload;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ProductionRecipeScreen extends Screen implements ProductionClient.Receiver {
    private final ProductionOrdersScreen previous;
    private final Map<Integer, ResourceLocation> alternatives = new LinkedHashMap<>();
    private final List<Button> ingredients = new ArrayList<>();
    private List<Choice> catalog;
    private Choice selected;
    private HiredProductionOrder.Mode mode = HiredProductionOrder.Mode.BATCH;
    private BlockPos storage;
    private String query = "";
    private String quantity = "1";
    private Recipes list;
    private EditBox amount;
    private Button modeButton;
    private Button storageButton;
    private Button save;
    private int left;
    private int panelWidth;

    public ProductionRecipeScreen(ProductionOrdersScreen previous) {
        super(Component.literal("Choose a production recipe"));
        this.previous = previous;
    }

    public ProductionOrdersScreen previous() { return this.previous; }

    @Override
    protected void init() {
        this.panelWidth = Math.min(580, this.width - 24);
        this.left = (this.width - this.panelWidth) / 2;
        if (this.catalog == null) {
            var role = this.previous.state().snapshot().role();
            if (role == HiredVillagerRole.BREWING) {
                this.catalog = HiredBrewingRecipeCatalog.routes(this.minecraft.level).stream()
                        .map(route -> new Choice(HiredProductionRecipe.brewing(route.itemId(), route.potionId()), route.output(), route.potionId() + " / " + route.itemId())).toList();
            } else {
                this.catalog = VillagerRecipeFilterData.catalog(this.minecraft.level).stream()
                        .map(recipe -> new Choice(HiredProductionRecipe.exact(recipe.id(), Map.of()),
                                recipe.value().getResultItem(this.minecraft.level.registryAccess()), recipe.id().toString()))
                        .filter(choice -> choice.recipe().supports(this.minecraft.level, role)).toList();
            }
        }
        var search = addRenderableWidget(new EditBox(this.font, this.left, 34, this.panelWidth, 20, Component.literal("Search recipes")));
        search.setMaxLength(96);
        search.setHint(Component.literal("Search by item or recipe"));
        search.setValue(this.query);
        this.list = addRenderableWidget(new Recipes());
        search.setResponder(value -> {
            this.query = value;
            this.list.refresh();
        });
        this.ingredients.clear();
        int ingredientWidth = (this.panelWidth - 8) / 3;
        for (int index = 0; index < 9; index++) {
            int slot = index;
            this.ingredients.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleIngredient(slot))
                    .bounds(this.left + (index % 3) * (ingredientWidth + 4), this.height - 148 + (index / 3) * 20, ingredientWidth, 18).build()));
        }
        this.modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            this.mode = HiredProductionOrder.Mode.values()[(this.mode.ordinal() + 1) % HiredProductionOrder.Mode.values().length];
            updateActions();
        }).bounds(this.left, this.height - 88, this.panelWidth - 124, 20).build());
        this.amount = addRenderableWidget(new EditBox(this.font, this.left + this.panelWidth - 72, this.height - 88, 72, 20, Component.literal("Quantity")));
        this.amount.setMaxLength(4);
        this.amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.amount.setValue(this.quantity);
        this.amount.setResponder(value -> {
            this.quantity = value;
            updateActions();
        });
        this.storageButton = addRenderableWidget(Button.builder(Component.empty(), ignored ->
                this.minecraft.setScreen(new ProductionStorageScreen(this)))
                .bounds(this.left, this.height - 64, this.panelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.height - 28, 84, 20).build());
        this.save = addRenderableWidget(Button.builder(Component.literal("Add order"), ignored -> submit())
                .bounds(this.left + this.panelWidth - 136, this.height - 28, 136, 20).build());
        this.list.refresh();
        updateActions();
        setInitialFocus(search);
    }

    void storage(BlockPos position) {
        this.storage = position;
    }

    private HiredProductionRecipe recipe() {
        if (this.selected == null) return null;
        return this.selected.recipe().recipe() == null ? this.selected.recipe() : HiredProductionRecipe.exact(this.selected.recipe().recipe(), this.alternatives);
    }

    private List<ItemStack> ingredientChoices(int slot) {
        if (this.selected == null || this.selected.recipe().recipe() == null) return List.of();
        var holder = this.minecraft.level.getRecipeManager().byKey(this.selected.recipe().recipe()).orElse(null);
        if (holder == null || slot >= holder.value().getIngredients().size()) return List.of();
        return java.util.Arrays.stream(holder.value().getIngredients().get(slot).getItems())
                .filter(stack -> !stack.isEmpty()).toList();
    }

    private void cycleIngredient(int slot) {
        var choices = ingredientChoices(slot);
        if (choices.isEmpty()) return;
        var current = this.alternatives.get(slot);
        int index = -1;
        for (int choice = 0; choice < choices.size(); choice++) {
            if (BuiltInRegistries.ITEM.getKey(choices.get(choice).getItem()).equals(current)) index = choice;
        }
        if (index + 1 >= choices.size()) this.alternatives.remove(slot);
        else this.alternatives.put(slot, BuiltInRegistries.ITEM.getKey(choices.get(index + 1).getItem()));
        updateActions();
    }

    private int amount() {
        try {
            return Integer.parseInt(this.quantity);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void submit() {
        if (this.selected != null && this.save.active) this.previous.submit(new ProductionRequestPayload.Append(
                recipe(), this.mode, this.mode == HiredProductionOrder.Mode.CONTINUOUS ? 1 : amount(), this.storage));
        updateActions();
    }

    private void updateActions() {
        if (this.save == null) return;
        var state = this.previous.state();
        boolean editable = state.editable();
        for (int slot = 0; slot < this.ingredients.size(); slot++) {
            var button = this.ingredients.get(slot);
            var choices = ingredientChoices(slot);
            var chosen = this.alternatives.get(slot);
            String name = chosen == null ? "Any" : BuiltInRegistries.ITEM.get(chosen).getDescription().getString();
            button.visible = !choices.isEmpty();
            button.active = editable && !choices.isEmpty();
            button.setMessage(Component.literal((slot + 1) + ": " + this.font.plainSubstrByWidth(name, button.getWidth() - 24)));
            button.setTooltip(Tooltip.create(Component.literal("Ingredient " + (slot + 1) + ": " + name
                    + ". Click to choose an item or Any. " + String.join(", ", choices.stream().map(stack -> stack.getHoverName().getString()).distinct().toList()))));
        }
        this.modeButton.setMessage(Component.literal(ProductionOrdersScreen.mode(this.mode)));
        this.modeButton.active = editable;
        this.amount.active = editable && this.mode != HiredProductionOrder.Mode.CONTINUOUS;
        var destination = state.snapshot() == null ? null : state.snapshot().storages().stream().filter(value -> value.position().equals(this.storage)).findFirst().orElse(null);
        String storageLabel = this.storage == null ? "Output storage: automatic"
                : "Output: " + (destination == null ? this.storage.toShortString() + " (unavailable)" : destination.name());
        this.storageButton.setMessage(Component.literal(this.font.plainSubstrByWidth(storageLabel, this.panelWidth - 12)));
        this.storageButton.setTooltip(Tooltip.create(Component.literal(storageLabel)));
        this.storageButton.active = editable;
        this.save.setMessage(Component.literal(state.snapshot() != null && state.snapshot().legacyCount() > 0 ? "Convert and add" : "Add order"));
        this.save.active = editable && this.selected != null && (state.queue() == null || state.queue().orders().size() < 32)
                && (this.mode == HiredProductionOrder.Mode.CONTINUOUS || amount() >= 1 && amount() <= HiredProductionOrder.MAX_AMOUNT)
                && (this.storage == null || destination != null && destination.available())
                && (this.mode != HiredProductionOrder.Mode.MAINTAIN_STOCK || this.storage != null);
    }

    @Override
    public void tick() {
        this.previous.tick();
        updateActions();
    }

    @Override
    public void acceptProduction(ProductionSnapshotPayload payload) {
        if (!this.previous.acceptSnapshot(payload)) return;
        if (payload.applied()) this.minecraft.setScreen(this.previous);
        else updateActions();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        graphics.drawString(this.font, "Quantity", this.left + this.panelWidth - 120, this.height - 82, 0xFFFFFF);
        String feedback = this.previous.state().feedback();
        if (feedback.isEmpty() && this.selected != null) feedback = this.selected.recipe().recipe() == null
                ? "Brew up to 3 bottles per native batch." : "Each complete recipe makes " + this.selected.output().getCount() + ". Final batches stay whole.";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(feedback, this.panelWidth), this.left, this.height - 40, 0xCCCCCC);
        var hovered = this.list.hovered();
        if (hovered != null) graphics.renderTooltip(this.font, hovered.choice.output(), mouseX, mouseY);
    }

    private record Choice(HiredProductionRecipe recipe, ItemStack output, String identity) {
    }

    private final class Recipes extends ObjectSelectionList<Row> {
        private Recipes() {
            super(ProductionRecipeScreen.this.minecraft, panelWidth, Math.max(24, ProductionRecipeScreen.this.height - 214), 60, 32);
            setX(left);
        }

        private void refresh() {
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            for (var choice : catalog) {
                if (choice.output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || choice.identity().toLowerCase(Locale.ROOT).contains(needle)) addEntry(new Row(choice));
            }
            var row = children().stream().filter(value -> value.choice == selected).findFirst().orElse(null);
            setSelected(row);
        }

        private Row hovered() { return getHovered(); }

        @Override
        public void setSelected(Row row) {
            super.setSelected(row);
            var choice = row == null ? null : row.choice;
            if (choice != selected) alternatives.clear();
            selected = choice;
            updateActions();
        }

        @Override
        public int getRowWidth() { return getWidth() - 12; }

        @Override
        protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final Choice choice;

        private Row(Choice choice) { this.choice = choice; }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int rowLeft, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.renderItem(this.choice.output(), rowLeft + 3, top + 4);
            graphics.renderItemDecorations(font, this.choice.output(), rowLeft + 3, top + 4);
            graphics.drawString(font, font.plainSubstrByWidth(this.choice.output().getHoverName().getString(), rowWidth - 28), rowLeft + 24, top + 3, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(this.choice.identity(), rowWidth - 28), rowLeft + 24, top + 16, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            list.setSelected(this);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.choice.output().getHoverName().getString() + ", " + this.choice.identity());
        }
    }
}
