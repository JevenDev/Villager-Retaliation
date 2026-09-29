package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProductionStorageScreen extends Screen implements ProductionClient.Receiver {
    private final ProductionRecipeScreen previous;
    private String query = "";
    private Storages list;
    private int left;
    private int panelWidth;

    public ProductionStorageScreen(ProductionRecipeScreen previous) {
        super(Component.literal("Choose output storage"));
        this.previous = previous;
    }

    public ProductionRecipeScreen previous() { return this.previous; }

    @Override
    protected void init() {
        this.panelWidth = Math.min(480, this.width - 24);
        this.left = (this.width - this.panelWidth) / 2;
        var search = addRenderableWidget(new EditBox(this.font, this.left, 36, this.panelWidth, 20, Component.literal("Search storage")));
        search.setMaxLength(96);
        search.setHint(Component.literal("Search by name or position"));
        search.setValue(this.query);
        this.list = addRenderableWidget(new Storages());
        search.setResponder(value -> {
            this.query = value;
            this.list.refresh();
        });
        this.list.refresh();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose()).bounds(this.left, this.height - 28, 84, 20).build());
        setInitialFocus(search);
    }

    @Override
    public void tick() { this.previous.tick(); }

    @Override
    public void acceptProduction(ProductionSnapshotPayload payload) {
        this.previous.acceptProduction(payload);
        this.list.refresh();
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.previous); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
    }

    private final class Storages extends ObjectSelectionList<Row> {
        private Storages() {
            super(ProductionStorageScreen.this.minecraft, panelWidth, Math.max(32, ProductionStorageScreen.this.height - 104), 64, 28);
            setX(left);
        }

        private void refresh() {
            clearEntries();
            addEntry(new Row(null));
            var snapshot = previous.previous().state().snapshot();
            if (snapshot == null) return;
            String needle = query.strip().toLowerCase(Locale.ROOT);
            snapshot.storages().stream().filter(storage -> storage.name().toLowerCase(Locale.ROOT).contains(needle)
                    || storage.position().toShortString().contains(needle)).forEach(storage -> addEntry(new Row(storage)));
        }

        @Override
        public int getRowWidth() { return getWidth() - 12; }

        @Override
        protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final ProductionSnapshotPayload.Storage storage;

        private Row(ProductionSnapshotPayload.Storage storage) { this.storage = storage; }

        private String label() {
            return this.storage == null ? "Automatic output storage" : this.storage.name() + (this.storage.available() ? "" : " / unavailable");
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int rowLeft, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, font.plainSubstrByWidth(label(), rowWidth - 8), rowLeft + 4, top + 8,
                    this.storage == null || this.storage.available() ? 0xFFFFFF : 0x888888);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || this.storage != null && !this.storage.available()) return false;
            previous.storage(this.storage == null ? null : this.storage.position());
            onClose();
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(label()); }
    }
}
