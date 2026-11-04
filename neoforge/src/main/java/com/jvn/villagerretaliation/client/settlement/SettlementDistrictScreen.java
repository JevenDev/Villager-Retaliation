package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementDistrictRequestPayload;
import com.jvn.villagerretaliation.network.SettlementDistrictResultPayload;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementDistrictScreen extends VillagerWindowScreen {
    private final SettlementManagementScreen parent;
    private final SettlementViewScreen view;
    private final SettlementDistrictDraft draft;
    private SettlementDistrictRequestPayload request;
    private SettlementDistrictResultPayload result;
    private Rows rows;
    private Button review;
    private Button commission;
    private Button remove;
    private Button edit;
    private Button order;
    private boolean waiting;
    private long sentAt;

    SettlementDistrictScreen(SettlementManagementScreen parent, SettlementViewScreen view) {
        super(Component.literal("District plan")); this.parent = parent; this.view = view; this.draft = parent.districtDraft();
    }
    SettlementManagementScreen parent() { return this.parent; }

    @Override protected void init() {
        initWindow(560, Math.min(420, this.height - 20));
        this.rows = addRenderableWidget(new Rows()); this.rows.refresh();
        this.order = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(this.draft.pathsFirst() ? "Paths first" : "Independent projects"), button -> {
            this.draft.pathsFirst(!this.draft.pathsFirst()); this.result = null; this.request = null; this.rows.refresh();
            button.setMessage(Component.literal(this.draft.pathsFirst() ? "Paths first" : "Independent projects"));
        }).bounds(this.left + this.panelWidth - 156, this.top, 156, 20).build());
        int quarter = (this.panelWidth - 12) / 4;
        this.edit = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Edit selected"), ignored -> editSelected())
                .bounds(this.left, this.top + this.panelHeight - 74, quarter, 20).build());
        this.remove = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Remove"), ignored -> {
            var selected = this.rows.getSelected(); if (selected == null || selected.index < 0) return;
            this.draft.remove(selected.index); this.result = null; this.request = null; this.rows.refresh();
        }).bounds(this.left + quarter + 4, this.top + this.panelHeight - 74, quarter, 20).build());
        this.review = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Review combined cost"), ignored -> {
            this.request = this.draft.request(this.parent.snapshot().header().id(), false, ""); this.result = null; send();
        }).bounds(this.left + (quarter + 4) * 2, this.top + this.panelHeight - 74, quarter * 2 + 4, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 20, 90, 20).build());
        this.commission = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Commission district"), ignored -> {
            if (this.result == null || this.result.fingerprint().isEmpty() || this.waiting) return;
            this.request = this.draft.request(this.parent.snapshot().header().id(), true, this.result.fingerprint()); send();
        }).bounds(this.left + 96, this.top + this.panelHeight - 20, this.panelWidth - 96, 20).build());
    }

    private void send() { this.waiting = true; this.sentAt = Util.getMillis(); PacketDistributor.sendToServer(this.request); }

    public static void accept(SettlementDistrictResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof SettlementDistrictScreen screen && screen.request != null
                && screen.request.request().equals(payload.request()) && screen.request.town().equals(payload.town())) {
            screen.waiting = false; screen.result = payload;
            if (payload.queued()) screen.draft.clear();
            screen.rows.refresh();
        }
    }

    private void editSelected() {
        var selected = this.rows.getSelected(); if (selected == null || selected.index < 0) return;
        int index = selected.index;
        if (index < this.draft.buildings().size()) {
            var building = this.draft.buildings().get(index); this.draft.remove(index); this.parent.editBuilding(building);
        } else {
            var path = this.draft.paths().get(index - this.draft.buildings().size()); this.draft.remove(index); this.view.editDistrictPath(path.path());
        }
    }

    @Override public void tick() {
        if (this.waiting && Util.getMillis() - this.sentAt >= 3_000L) send();
        this.review.active = !this.waiting && this.draft.size() > 0;
        this.order.active = !this.waiting;
        this.commission.active = !this.waiting && this.result != null && !this.result.queued() && !this.result.fingerprint().isEmpty();
        var selected = this.rows.getSelected();
        this.remove.active = !this.waiting && selected != null && selected.index >= 0;
        this.edit.active = this.remove.active && this.view != null && this.view.districtEditAvailable();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top, 0xFFFFFF);
        graphics.drawString(this.font, "Draft projects: " + this.draft.size() + "/" + SettlementDistrictRequestPayload.MAX_PROJECTS, this.left, this.top + 16, 0xB8C7CC);
        String feedback = this.waiting ? "Checking the whole district..." : this.result != null ? this.result.queued()
                ? "District commissioned as separate construction projects" : this.result.failure().isEmpty() ? "Review the combined quote, then commission" : this.result.failure()
                : "Add buildings and paths from their placement controls. Drafts stay while this settlement screen is open.";
        graphics.drawWordWrap(this.font, Component.literal(feedback), this.left, this.top + this.panelHeight - 49, this.panelWidth, 0xE7C77A);
    }
    @Override public void onClose() { if (!this.waiting || this.request == null || !this.request.confirm()) SettlementViewClient.resume(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    private final class Rows extends ObjectSelectionList<Row> {
        Rows() { super(SettlementDistrictScreen.this.minecraft, panelWidth, Math.max(24, panelHeight - 123), top + 36, 15); setX(left); }
        void refresh() {
            clearEntries(); int index = 0;
            for (var building : draft.buildings()) addEntry(new Row(index++, Component.literal("Building: " + building.label).getVisualOrderText()));
            for (var path : draft.paths()) addEntry(new Row(index++, Component.literal("Path: " + path.path().material().label()).getVisualOrderText()));
            if (result != null) for (var line : result.lines()) for (var part : font.split(Component.literal(line), panelWidth - 24)) addEntry(new Row(-1, part));
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }
    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final int index; private final net.minecraft.util.FormattedCharSequence text;
        Row(int index, net.minecraft.util.FormattedCharSequence text) { this.index = index; this.text = text; }
        @Override public Component getNarration() { return Component.literal("District project"); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0 || waiting || this.index < 0) return false; rows.setSelected(this); return true; }
        @Override public void render(GuiGraphics graphics, int row, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, this.text, x + 3, y + 2, 0xDDDDDD);
        }
    }
}
