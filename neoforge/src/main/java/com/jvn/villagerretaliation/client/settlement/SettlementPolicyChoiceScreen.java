package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class SettlementPolicyChoiceScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child {
    record Choice(Component name, Component detail, ItemStack sample, Runnable select) { }
    private final Screen previous;
    private final Function<String, List<Choice>> choices;
    private String query = "";
    private Rows rows;

    SettlementPolicyChoiceScreen(Screen previous, Component title, Function<String, List<Choice>> choices) {
        super(title);
        this.previous = previous;
        this.choices = choices;
    }

    static SettlementPolicyChoiceScreen items(Screen previous, List<ItemStack> observed, java.util.function.Consumer<ItemStack> selected) {
        return new SettlementPolicyChoiceScreen(previous, SettlementPolicyClient.text("choose_item"), query -> {
            var result = new ArrayList<Choice>();
            var samples = new ArrayList<ItemStack>();
            var available = new ArrayList<>(observed);
            if (Minecraft.getInstance().player != null) available.addAll(Minecraft.getInstance().player.getInventory().items);
            if (query.length() >= 2) for (var item : BuiltInRegistries.ITEM) available.add(item.getDefaultInstance());
            for (var item : available) {
                if (item.isEmpty()) continue;
                var sample = HiredProductionStock.product(item).copyWithCount(1);
                var id = BuiltInRegistries.ITEM.getKey(sample.getItem());
                if (!sample.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query) && !id.toString().contains(query)) continue;
                if (samples.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, sample))) continue;
                samples.add(sample);
                result.add(new Choice(sample.getHoverName(), Component.literal(id.toString()), sample, () -> selected.accept(sample)));
                if (result.size() == 256) break;
            }
            return result;
        });
    }

    @Override public Screen previousPolicy() { return this.previous; }

    @Override protected void init() {
        initWindow(380, 300);
        var search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 24, this.panelWidth, 20, SettlementPolicyClient.text("search")));
        search.setMaxLength(96);
        search.setHint(SettlementPolicyClient.text("search"));
        search.setValue(this.query);
        this.rows = addRenderableWidget(new Rows());
        search.setResponder(value -> { this.query = value; this.rows.refresh(); });
        this.rows.refresh();
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 24, 84, 20).build());
        setInitialFocus(search);
    }

    @Override public void tick() { if (this.previous != null) this.previous.tick(); }
    @Override public void onClose() { this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        var row = this.rows.hovered();
        if (row != null && !row.choice.sample().isEmpty()) graphics.renderTooltip(this.font, row.choice.sample(), mouseX, mouseY);
    }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows() {
            super(SettlementPolicyChoiceScreen.this.minecraft, panelWidth, Math.max(28, panelHeight - 82), top + 48, 30);
            setX(left);
        }

        void refresh() {
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            for (var choice : choices.apply(needle)) if (choice.name().getString().toLowerCase(Locale.ROOT).contains(needle)
                    || choice.detail().getString().toLowerCase(Locale.ROOT).contains(needle)) addEntry(new Row(choice));
        }

        Row hovered() { return getHovered(); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final Choice choice;
        Row(Choice choice) { this.choice = choice; }
        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            int inset = this.choice.sample().isEmpty() ? 3 : 24;
            if (!this.choice.sample().isEmpty()) graphics.renderItem(this.choice.sample(), left + 3, top + 5);
            graphics.drawString(font, font.plainSubstrByWidth(this.choice.name().getString(), width - inset - 6), left + inset, top + 3, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(this.choice.detail().getString(), width - inset - 6), left + inset, top + 16, 0xAAAAAA);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            minecraft.setScreen(previous);
            this.choice.select().run();
            return true;
        }
        @Override public Component getNarration() { return this.choice.name().copy().append(" ").append(this.choice.detail()); }
    }
}
