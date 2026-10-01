package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettlementPurchaseMarketScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child {
    record Selection(SettlementPolicySnapshotPayload.MarketPage page, SettlementPolicySnapshotPayload.Commodity commodity) { }
    private final SettlementPolicyPurchasesScreen owner;
    private final Screen previous;
    private final UUID consumer;
    private final UUID requirement;
    private final Consumer<Selection> selected;
    private String query = "";
    private int page;
    private boolean initialized;
    private SettlementPolicySnapshotPayload.MarketPage market;
    private Rows rows;
    private Button earlier;
    private Button later;
    private String feedback = "";

    SettlementPurchaseMarketScreen(SettlementPolicyPurchasesScreen owner, Screen previous, UUID consumer, UUID requirement, Consumer<Selection> selected) {
        super(SettlementPolicyClient.text("purchase_commodity"));
        this.owner = owner;
        this.previous = previous;
        this.consumer = consumer;
        this.requirement = requirement;
        this.selected = selected;
    }

    @Override public Screen previousPolicy() { return this.previous; }

    @Override protected void init() {
        initWindow(400, 300);
        var search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 24, this.panelWidth, 20, SettlementPolicyClient.text("search")));
        search.setMaxLength(96);
        search.setHint(SettlementPolicyClient.text("search"));
        search.setValue(this.query);
        this.rows = addRenderableWidget(new Rows());
        search.setResponder(value -> {
            if (!this.query.equals(value)) { this.query = value; this.page = 0; request(); }
        });
        int third = (this.panelWidth - 8) / 3;
        addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text("back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 24, third, 20).build());
        this.earlier = addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text("purchase_previous"), ignored -> { this.page--; request(); })
                .bounds(this.left + third + 4, this.top + this.panelHeight - 24, third, 20).build());
        this.later = addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text("purchase_next"), ignored -> { this.page++; request(); })
                .bounds(this.left + 2 * third + 8, this.top + this.panelHeight - 24, third, 20).build());
        this.rows.refresh();
        update();
        if (!this.initialized) { this.initialized = true; request(); }
        setInitialFocus(search);
    }

    private void request() {
        this.market = null;
        this.feedback = "checking";
        this.rows.refresh();
        String query = this.query;
        int page = this.page;
        this.owner.market(this.consumer, this.requirement, query, page, payload -> {
            if (!this.query.equals(query) || this.page != page) return;
            this.market = payload.market();
            this.feedback = payload.feedback().isEmpty() ? payload.blocker() : payload.feedback();
            this.rows.refresh();
            update();
        });
        update();
    }

    private void update() {
        if (this.earlier == null) return;
        this.earlier.active = this.owner.ready() && this.page > 0;
        this.later.active = this.owner.ready() && this.market != null && this.market.more() && this.page < 4096;
    }

    @Override public void tick() { this.owner.tick(); update(); }
    @Override public void onClose() { this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        Component footer = this.market == null && this.owner.ready() ? SettlementPolicyClient.blocker(this.owner.feedback())
                : !this.feedback.isEmpty() ? SettlementPolicyClient.blocker(this.feedback)
                : this.market == null ? SettlementPolicyClient.blocker("checking")
                : this.market.commodities().isEmpty() ? SettlementPolicyClient.text("purchase_no_matches") : SettlementPolicyClient.text("purchase_page", this.page + 1);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(footer.getString(), this.panelWidth), this.left,
                this.top + this.panelHeight - 44, 0xCCCCCC);
        var row = this.rows.hovered();
        if (row != null) graphics.renderTooltip(this.font, row.commodity.sample(), mouseX, mouseY);
    }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows() { super(SettlementPurchaseMarketScreen.this.minecraft, panelWidth, Math.max(28, panelHeight - 102), top + 48, 32); setX(left); }
        void refresh() {
            clearEntries();
            if (market != null) for (var commodity : market.commodities()) addEntry(new Row(commodity));
        }
        Row hovered() { return getHovered(); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final SettlementPolicySnapshotPayload.Commodity commodity;
        Row(SettlementPolicySnapshotPayload.Commodity commodity) { this.commodity = commodity; }
        private Component price() { return SettlementPolicyClient.text("purchase_quote", SettlementEditScreen.currency(this.commodity.unitPrice())); }
        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.renderItem(this.commodity.sample(), left + 3, top + 6);
            graphics.drawString(font, font.plainSubstrByWidth(this.commodity.sample().getHoverName().getString(), width - 30), left + 24, top + 4, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(price().getString(), width - 30), left + 24, top + 18, 0xAAAAAA);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0 || !owner.ready() || !owner.editable() || market == null) return false;
            minecraft.setScreen(previous);
            selected.accept(new Selection(market, this.commodity));
            return true;
        }
        @Override public Component getNarration() {
            return this.commodity.sample().getHoverName().copy().append(" ").append(this.commodity.item().toString()).append(" ").append(price());
        }
    }
}
