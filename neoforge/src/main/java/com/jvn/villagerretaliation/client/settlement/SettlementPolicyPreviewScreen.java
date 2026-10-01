package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettlementPolicyPreviewScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child {
    private final SettlementPoliciesScreen previous;
    private final SettlementPolicySnapshotPayload snapshot;
    private Rows rows;

    SettlementPolicyPreviewScreen(SettlementPoliciesScreen previous, SettlementPolicySnapshotPayload snapshot) {
        super(SettlementPolicyClient.text("preview"));
        this.previous = previous;
        this.snapshot = snapshot;
    }

    @Override public Screen previousPolicy() { return this.previous; }

    @Override protected void init() {
        initWindow(440, 330);
        this.rows = addRenderableWidget(new Rows());
        for (var need : this.snapshot.needs()) {
            var unit = Component.translatable("villagerretaliation.gui.settlement.supply_unit." + need.unit().name().toLowerCase(java.util.Locale.ROOT));
            var quantity = SettlementPolicyClient.text("need_quantities", need.required(), unit, need.available(), need.committed(), need.incoming(),
                    need.known() ? Long.toString(need.missing()) : SettlementPolicyClient.text("unknown").getString());
            var sources = Component.literal(String.join("\n", need.sources().stream().map(position ->
                    SettlementPolicyClient.text("source_value", position.toShortString()).getString()).toList()));
            this.rows.row(new Row(this.previous.consumerName(need.step()).copy().append(": ").append(SettlementPolicyClient.needName(need)), quantity, sources));
        }
        for (var production : this.snapshot.production()) this.rows.row(new Row(this.previous.consumerName(production.id()),
                SettlementPolicyClient.text("preview_quantities", production.proposed(), production.requested(), production.retained()),
                SettlementPolicyClient.blocker(production.blocker())));
        if (!this.snapshot.purchases().approvals().isEmpty()) {
            this.rows.row(new Row(SettlementPolicyClient.text("purchases"), SettlementPolicyClient.text("purchase_account",
                    SettlementEditScreen.currency(this.snapshot.purchases().spent()), SettlementEditScreen.currency(this.snapshot.purchases().available())),
                    SettlementPolicyClient.text("purchase_budget_help")));
            for (var purchase : this.snapshot.purchases().approvals()) {
                var approval = this.previous.configuration().purchases().approvals().stream()
                        .filter(value -> value.id().equals(purchase.approval())).findFirst().orElse(null);
                if (approval != null) this.rows.row(new Row(approval.sample().getHoverName(),
                        SettlementPolicyPurchasesScreen.status(purchase), SettlementPolicyClient.text("purchase_prices",
                        SettlementEditScreen.currency(approval.unitPriceLimit()), SettlementEditScreen.currency(purchase.unitPrice()))));
            }
        }
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 24, 84, 20).build());
    }

    @Override public void onClose() { this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        var blocker = SettlementPolicyClient.blocker(this.snapshot.blocker());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(blocker.getString(), this.panelWidth), this.left, this.top + 24, 0xFFCC88);
        var row = this.rows.hovered();
        if (row != null) graphics.renderTooltip(this.font, this.font.split(row.name.copy().append("\n").append(row.detail).append("\n").append(row.help),
                Math.min(320, this.width - 24)), mouseX, mouseY);
    }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows() { super(SettlementPolicyPreviewScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 82), top + 42, 64); setX(left); }
        void row(Row value) { addEntry(value); }
        Row hovered() { return getHovered(); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final Component name;
        final Component detail;
        final Component help;
        Row(Component name, Component detail, Component help) { this.name = name; this.detail = detail; this.help = help; }
        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, font.plainSubstrByWidth(this.name.getString(), width - 6), left + 3, top + 4, 0xFFFFFF);
            int y = top + 18;
            for (var line : font.split(this.detail, width - 6)) {
                if (y + font.lineHeight > top + height - 2) break;
                graphics.drawString(font, line, left + 3, y, 0xAAAAAA);
                y += 11;
            }
        }
        @Override public Component getNarration() { return this.name.copy().append(" ").append(this.detail); }
    }
}
