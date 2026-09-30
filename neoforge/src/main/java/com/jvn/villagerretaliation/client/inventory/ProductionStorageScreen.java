package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

public final class ProductionStorageScreen extends VillagerWindowScreen implements ProductionClient.Receiver {
    private final ProductionRecipeScreen previous;
    private String query = "";
    private Storages list;

    public ProductionStorageScreen(ProductionRecipeScreen previous) {
        super(Component.literal("Choose output storage"));
        this.previous = previous;
    }

    public ProductionRecipeScreen previous() { return this.previous; }

    @Override
    protected void init() {
        initWindow(360, 300);
        var search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 22, this.panelWidth, 20, Component.literal("Search storage")));
        search.setMaxLength(96);
        search.setHint(Component.literal("Search by name or position"));
        search.setValue(this.query);
        this.list = addRenderableWidget(new Storages());
        search.setResponder(value -> {
            this.query = value;
            this.list.refresh();
        });
        this.list.refresh();
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose()).bounds(this.left, this.top + this.panelHeight - 28, 84, 20).build());
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
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
    }

    private final class Storages extends VillagerSelectionList<Row> {
        private Storages() {
            super(ProductionStorageScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 82), top + 46, 28);
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
            graphics.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST), rowLeft + 4, top + 5);
            graphics.drawString(font, font.plainSubstrByWidth(label(), rowWidth - 30), rowLeft + 26, top + 3,
                    this.storage == null || this.storage.available() ? 0xFFFFFF : 0x888888);
            if (this.storage != null) graphics.drawString(font, this.storage.position().toShortString(), rowLeft + 26, top + 15, 0xAAAAAA);
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
