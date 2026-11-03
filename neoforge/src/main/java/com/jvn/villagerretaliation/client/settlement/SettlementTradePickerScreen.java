package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class SettlementTradePickerScreen extends VillagerWindowScreen {
    public record Choice(Component name, Component detail, ItemStack icon, int maximum, IntConsumer choose) { }
    private final Screen parent;
    private final List<Choice> choices;
    private final boolean quantities, readOnly;
    private Choice selected;
    private String query = "", quantity = "1";
    private Rows rows;
    private Button confirm;
    private Component hover;

    public SettlementTradePickerScreen(Screen parent, Component title, List<Choice> choices, boolean quantities) {
        super(title);
        this.parent = parent;
        this.choices = List.copyOf(choices);
        this.quantities = quantities;
        this.readOnly = !choices.isEmpty() && choices.stream().allMatch(choice -> choice.choose() == null);
    }

    @Override protected void init() {
        initWindow(400, 330, false);
        var search = addRenderableWidget(new EditBox(font, left, top + 22, panelWidth, 20, Component.literal("Search")));
        search.setHint(Component.literal("Search"));
        search.setValue(query);
        rows = addRenderableWidget(new Rows());
        search.setResponder(value -> { query = value; rows.refresh(); });
        rows.refresh();
        int bottom = top + panelHeight - 20;
        if (quantities) {
            var amount = addRenderableWidget(new EditBox(font, left + 65, bottom - 28, 70, 20, Component.literal("Quantity")));
            amount.setMaxLength(3);
            amount.setValue(quantity);
            amount.setResponder(value -> { quantity = value; update(); });
        }
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left, bottom, (panelWidth - 6) / 2, 20).build());
        confirm = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(readOnly ? "Done" : quantities ? "Add goods" : "Choose"), ignored -> choose())
                .bounds(left + (panelWidth + 6) / 2, bottom, (panelWidth - 6) / 2, 20).build());
        update();
        setInitialFocus(search);
    }

    private int amount() {
        try { return Integer.parseInt(quantity); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void update() {
        if (confirm != null) confirm.active = readOnly || selected != null && (!quantities || amount() > 0 && amount() <= selected.maximum());
    }

    private void choose() {
        if (readOnly) { onClose(); return; }
        if (confirm.active) {
            var choice = selected;
            int amount = quantities ? amount() : 1;
            minecraft.setScreen(parent);
            choice.choose().accept(amount);
        }
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && confirm.active) { choose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override protected void renderWindowFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        TooltipRenderUtil.renderTooltipBackground(graphics, x, y, width, height, 0);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hover = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left, top + 4, 0xFFFFFF);
        if (rows.children().isEmpty()) graphics.drawString(font, "No matching choices", left + 8, top + 60, 0xB9C1CB);
        if (quantities) {
            graphics.drawString(font, "Quantity", left, top + panelHeight - 42, 0xB9C1CB);
            if (selected != null) graphics.drawString(font, "1–" + selected.maximum(), left + 145, top + panelHeight - 42, 0xB9C1CB);
        }
        if (hover != null) graphics.renderTooltip(font, font.split(hover, Math.min(320, width - 24)), mouseX, mouseY);
    }

    public Screen previous() { return parent; }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows() { super(SettlementTradePickerScreen.this.minecraft, panelWidth, Math.max(30, panelHeight - (quantities ? 106 : 78)), top + 48, 34); setX(left); }
        void refresh() {
            Choice previous = selected;
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            choices.stream().filter(choice -> choice.name().getString().toLowerCase(Locale.ROOT).contains(needle)
                    || choice.detail().getString().toLowerCase(Locale.ROOT).contains(needle)).forEach(choice -> {
                var row = new Row(choice);
                addEntry(row);
                if (choice == previous) setSelected(row);
            });
            setScrollAmount(0);
        }
        @Override public void setSelected(Row row) {
            super.setSelected(row);
            selected = row == null ? null : row.choice;
            update();
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final Choice choice;
        Row(Choice choice) { this.choice = choice; }
        @Override public Component getNarration() { return choice.name().copy().append(". ").append(choice.detail()); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            selected = choice; rows.setSelected(this); update(); return true;
        }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int textX = x + 4;
            if (!choice.icon().isEmpty()) { graphics.renderItem(choice.icon(), x + 4, y + 7); textX += 22; }
            graphics.drawString(font, font.plainSubstrByWidth(choice.name().getString(), x + width - textX - 4), textX, y + 4, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(choice.detail().getString(), x + width - textX - 4), textX, y + 17, 0xB9C1CB);
            if (hovered) hover = getNarration();
        }
    }
}
