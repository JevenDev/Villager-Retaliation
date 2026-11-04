package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementBuildingInfoPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingRequestPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingRequestPayload.Action;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementCompletedBuildingScreen extends VillagerWindowScreen {
    private final SettlementManagementScreen parent;
    private final SettlementViewScreen view;
    private final UUID building;
    private SettlementBuildingRequestPayload request;
    private SettlementBuildingInfoPayload info;
    private Details details;
    private Button queue;
    private boolean waiting;
    private long sentAt;

    SettlementCompletedBuildingScreen(SettlementManagementScreen parent, SettlementManagementPayload.Project project, SettlementViewScreen view) {
        super(Component.literal(project.name())); this.parent = parent; this.view = view; this.building = project.id();
        review(Action.INSPECT);
    }

    SettlementManagementScreen parent() { return this.parent; }

    private void review(Action action) {
        this.info = null;
        this.request = new SettlementBuildingRequestPayload(this.parent.snapshot().header().id(), this.building, UUID.randomUUID(), action, false, "");
        send();
    }

    private void send() { this.waiting = true; this.sentAt = Util.getMillis(); PacketDistributor.sendToServer(this.request); }

    public static void accept(SettlementBuildingInfoPayload payload) {
        if (Minecraft.getInstance().screen instanceof SettlementCompletedBuildingScreen screen && screen.request != null
                && screen.request.request().equals(payload.request()) && screen.request.town().equals(payload.town()) && screen.building.equals(payload.building())) {
            screen.info = payload; screen.waiting = false;
            if (screen.details != null) screen.details.refresh();
        }
    }

    @Override protected void init() {
        initWindow(520, Math.min(380, this.height - 20));
        this.details = addRenderableWidget(new Details()); this.details.refresh();
        int third = (this.panelWidth - 8) / 3;
        for (int i = 0; i < 3; i++) {
            var action = Action.values()[i + 1];
            addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(label(action)), ignored -> review(action))
                    .bounds(this.left + i * (third + 4), this.top + this.panelHeight - 72, third, 20).build());
        }
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 20, 90, 20).build());
        this.queue = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Queue reviewed work"), ignored -> {
            if (this.info == null || this.info.fingerprint().isEmpty() || this.waiting) return;
            this.request = new SettlementBuildingRequestPayload(this.request.town(), this.building, this.request.request(), this.request.action(), true, this.info.fingerprint());
            send();
        }).bounds(this.left + 96, this.top + this.panelHeight - 20, this.panelWidth - 96, 20).build());
    }

    static String label(Action action) { return switch (action) { case INSPECT -> "Building details"; case REPAIR -> "Repair missing blocks";
        case RENOVATE -> "Restore original design"; case DISMANTLE -> "Dismantle building"; }; }

    @Override public void tick() {
        if (this.waiting && Util.getMillis() - this.sentAt > 3_000L) send();
        this.queue.active = !this.waiting && this.info != null && !this.info.queued() && !this.info.fingerprint().isEmpty();
        this.queue.setMessage(Component.literal(this.info != null && this.info.queued() ? "Work queued" : this.request.action() == Action.DISMANTLE ? "Confirm dismantling" : "Queue reviewed work"));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF);
        graphics.drawString(this.font, label(this.request.action()), this.left, this.top + 16, 0xB8C7CC);
        graphics.drawWordWrap(this.font, Component.literal(this.waiting ? "Checking building..." : this.info == null ? "" : this.info.feedback()),
                this.left, this.top + this.panelHeight - 47, this.panelWidth, this.info != null && this.info.queued() ? 0xA5DBAD : 0xE7C77A);
    }

    @Override public void onClose() { if (this.view != null) SettlementViewClient.resume(this.parent); else this.minecraft.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    private final class Details extends ObjectSelectionList<Line> {
        Details() { super(SettlementCompletedBuildingScreen.this.minecraft, panelWidth, Math.max(24, panelHeight - 120), top + 36, 15); setX(left); }
        void refresh() {
            double scroll = getScrollAmount(); clearEntries();
            if (info != null) for (var line : info.lines()) for (var part : font.split(Component.literal(line), panelWidth - 24)) addEntry(new Line(part));
            setScrollAmount(scroll);
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }
    private final class Line extends ObjectSelectionList.Entry<Line> {
        private final net.minecraft.util.FormattedCharSequence text;
        Line(net.minecraft.util.FormattedCharSequence text) { this.text = text; }
        @Override public Component getNarration() { return Component.literal("Building details"); }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, this.text, x + 3, y + 2, 0xDDDDDD);
        }
    }
}
