package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettlementPolicyHistoryScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child {
    private final SettlementPoliciesScreen previous;
    private final SettlementSupplyPolicy policy;
    private final long gameTime;
    private Rows rows;

    SettlementPolicyHistoryScreen(SettlementPoliciesScreen previous, SettlementSupplyPolicy policy, long gameTime) {
        super(SettlementPolicyClient.text("history"));
        this.previous = previous;
        this.policy = policy;
        this.gameTime = gameTime;
    }

    @Override public Screen previousPolicy() { return this.previous; }

    @Override protected void init() {
        initWindow(420, 330);
        this.rows = addRenderableWidget(new Rows());
        for (var event : this.policy.history().reversed()) this.rows.row(new Row(event));
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 24, 84, 20).build());
    }

    @Override public void onClose() { this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.policy.configuration().name(), this.panelWidth),
                this.left, this.top + 24, 0xBBBBBB);
        if (this.rows.children().isEmpty()) graphics.drawString(this.font, SettlementPolicyClient.text("history_empty"),
                this.left, this.top + 48, 0xAAAAAA);
        var hovered = this.rows.hovered();
        if (hovered != null) graphics.renderTooltip(this.font, this.font.split(hovered.getNarration(), Math.min(320, this.width - 24)), mouseX, mouseY);
    }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows() { super(SettlementPolicyHistoryScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 78), top + 42, 52); setX(left); }
        void row(Row row) { addEntry(row); }
        Row hovered() { return getHovered(); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final Component name;
        private final Component when;
        private final Component detail;

        Row(SettlementSupplyPolicy.Event event) {
            this.name = SettlementPolicyClient.text("history_" + event.kind().name().toLowerCase(java.util.Locale.ROOT));
            this.when = SettlementPolicyClient.text("history_ago", Math.max(0, gameTime - event.at()) / 20);
            this.detail = event.kind() == SettlementSupplyPolicy.EventKind.PURCHASED || event.kind() == SettlementSupplyPolicy.EventKind.STAFFED
                    ? Component.literal(event.detail()) : SettlementPolicyClient.blocker(event.detail());
        }

        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, font.plainSubstrByWidth(this.name.getString(), width - 6), left + 3, top + 3, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(this.when.getString(), width - 6), left + 3, top + 15, 0xAAAAAA);
            int y = top + 27;
            for (var line : font.split(this.detail, width - 6)) {
                if (y + font.lineHeight > top + height - 2) break;
                graphics.drawString(font, line, left + 3, y, 0xD0D0D0);
                y += 11;
            }
        }

        @Override public Component getNarration() { return this.name.copy().append("\n").append(this.when).append("\n").append(this.detail); }
    }
}
