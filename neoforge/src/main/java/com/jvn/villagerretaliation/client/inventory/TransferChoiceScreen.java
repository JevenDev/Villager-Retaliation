package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.network.TransferSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class TransferChoiceScreen extends Screen implements TransferClient.Receiver {
    enum Kind { SOURCE, DESTINATION, ITEM }
    private final TransferEditorScreen previous;
    private final Kind kind;
    private String query = "";
    private Choices list;
    private int left;
    private int panelWidth;

    TransferChoiceScreen(TransferEditorScreen previous, Kind kind) {
        super(Component.literal(switch (kind) {
            case SOURCE -> "Choose source storage";
            case DESTINATION -> "Choose destination storage";
            case ITEM -> "Choose an exact item";
        }));
        this.previous = previous;
        this.kind = kind;
    }

    public TransferEditorScreen previous() { return this.previous; }

    @Override
    protected void init() {
        this.panelWidth = Math.min(480, this.width - 24);
        this.left = (this.width - this.panelWidth) / 2;
        var search = addRenderableWidget(new EditBox(this.font, this.left, 36, this.panelWidth, 20, Component.literal("Search")));
        search.setMaxLength(96);
        search.setHint(Component.literal(this.kind == Kind.ITEM ? "Search items or choose observed stock below" : "Search by name or position"));
        search.setValue(this.query);
        this.list = addRenderableWidget(new Choices());
        search.setResponder(value -> { this.query = value; this.list.refresh(); });
        this.list.refresh();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose()).bounds(this.left, this.height - 28, 84, 20).build());
        setInitialFocus(search);
    }

    @Override
    public void acceptTransfer(TransferSnapshotPayload payload) {
        this.previous.acceptTransfer(payload);
        if (this.list != null) this.list.refresh();
    }

    @Override
    public void tick() { this.previous.tick(); }

    @Override
    public void onClose() { this.minecraft.setScreen(this.previous); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        var hovered = this.list.hovered();
        if (hovered != null && !hovered.sample.isEmpty()) graphics.renderTooltip(this.font, hovered.sample, mouseX, mouseY);
    }

    private final class Choices extends ObjectSelectionList<Row> {
        private Choices() {
            super(TransferChoiceScreen.this.minecraft, panelWidth, Math.max(32, TransferChoiceScreen.this.height - 104), 64, 28);
            setX(left);
        }

        private void refresh() {
            double scroll = getScrollAmount();
            clearEntries();
            var snapshot = previous.previous().state().snapshot();
            if (snapshot == null) return;
            String needle = query.strip().toLowerCase(Locale.ROOT);
            if (kind != Kind.ITEM) {
                snapshot.storages().stream().filter(storage -> kind == Kind.SOURCE ? storage.source() : storage.destination())
                        .filter(storage -> kind != Kind.DESTINATION || !storage.endpoint().equals(previous.source()))
                        .filter(storage -> storage.name().toLowerCase(Locale.ROOT).contains(needle)
                                || storage.endpoint().position().toShortString().contains(needle))
                        .forEach(storage -> addEntry(new Row(storage, ItemStack.EMPTY, "")));
            } else {
                var samples = new ArrayList<ItemStack>();
                for (var product : snapshot.products()) {
                    if (previous.source() == null || previous.source().equals(product.source())) addItem(samples, product.sample(), "Observed source stock", needle);
                }
                if (minecraft.player != null) {
                    for (var sample : minecraft.player.getInventory().items) addItem(samples, sample, "From your inventory", needle);
                }
                if (needle.length() >= 2) {
                    for (var item : BuiltInRegistries.ITEM) {
                        if (children().size() >= 256) break;
                        addItem(samples, item.getDefaultInstance(), "Default item components", needle);
                    }
                }
            }
            setScrollAmount(scroll);
        }

        private void addItem(List<ItemStack> samples, ItemStack raw, String origin, String needle) {
            if (raw.isEmpty() || children().size() >= 256) return;
            var sample = HiredProductionStock.product(raw).copyWithCount(1);
            if (!sample.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                    && !BuiltInRegistries.ITEM.getKey(sample.getItem()).toString().contains(needle)) return;
            if (samples.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, sample))) return;
            samples.add(sample);
            addEntry(new Row(null, sample, origin));
        }

        private Row hovered() { return getHovered(); }
        @Override
        public int getRowWidth() { return getWidth() - 12; }
        @Override
        protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final TransferSnapshotPayload.Storage storage;
        private final ItemStack sample;
        private final String origin;

        private Row(TransferSnapshotPayload.Storage storage, ItemStack sample, String origin) {
            this.storage = storage;
            this.sample = sample;
            this.origin = origin;
        }

        private String label() {
            return this.storage == null ? this.sample.getHoverName().getString()
                    : this.storage.name() + (this.storage.available() ? "" : " / needs observation");
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int rowLeft, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (this.storage != null) {
                graphics.drawString(font, font.plainSubstrByWidth(label(), rowWidth - 8), rowLeft + 4, top + 8, 0xFFFFFF);
            } else {
                graphics.renderItem(this.sample, rowLeft + 4, top + 5);
                graphics.drawString(font, font.plainSubstrByWidth(label(), rowWidth - 30), rowLeft + 26, top + 3, 0xFFFFFF);
                graphics.drawString(font, this.origin, rowLeft + 26, top + 15, 0xAAAAAA);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            if (this.storage == null) previous.item(this.sample);
            else previous.storage(this.storage.endpoint(), kind == Kind.SOURCE);
            onClose();
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(label()); }
    }
}
