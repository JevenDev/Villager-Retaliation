package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementAidPayload;
import com.jvn.villagerretaliation.network.SettlementAidRequestPayload;
import com.jvn.villagerretaliation.network.SettlementAidRequestPayload.Action;
import com.jvn.villagerretaliation.network.SettlementAidRequestPayload.Policy;
import com.jvn.villagerretaliation.settlement.SettlementFoodRequest;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementAidScreen extends VillagerWindowScreen {
    private final Screen parent;
    private final UUID village;
    private UUID target;
    private final String targetName;
    private SettlementAidPayload snapshot;
    private SettlementAidPayload.Entry selected;
    private Rows rows;
    private boolean policyView, automatic, draftInitialized, submitting;
    private int requestId, page;
    private long refreshAt;
    private String reserve = "3", maximum = "128", standing = "0", interval = "3", quantity = "100";
    private Component feedback = Component.empty(), tooltip;
    private final Button[] actions = new Button[5];
    private Button previous, next, send;
    private Action sentAction = Action.VIEW;

    public SettlementAidScreen(Screen parent, UUID village, UUID target, String targetName) {
        super(text("title")); this.parent = parent; this.village = village; this.target = target; this.targetName = targetName;
    }
    public static Component text(String key, Object... values) { return Component.translatable("villagerretaliation.gui.aid." + key, values); }
    private static Component label(Enum<?> value) { return text(value.name().toLowerCase(Locale.ROOT)); }
    public static void receive(SettlementAidPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.isAlive() && minecraft.screen instanceof SettlementAidScreen screen) screen.accept(payload);
    }
    SettlementAidPayload snapshot() { return this.snapshot; }
    private void accept(SettlementAidPayload value) {
        if (value.requestId() != requestId || !value.village().equals(village)) return;
        snapshot = value; page = value.page(); submitting = false;
        if (sentAction != Action.VIEW) {
            feedback = text("result." + value.result().name().toLowerCase(Locale.ROOT));
            if (value.result() == com.jvn.villagerretaliation.settlement.SettlementAidService.Result.APPLIED && sentAction == Action.REQUEST) target = null;
        }
        if (!draftInitialized || sentAction == Action.POLICY && value.result() == com.jvn.villagerretaliation.settlement.SettlementAidService.Result.APPLIED) {
            automatic = value.policy().automatic(); reserve = Integer.toString(value.policy().reserveDays()); maximum = Integer.toString(value.policy().maximumItems());
            standing = Integer.toString(value.policy().minimumStanding()); interval = Integer.toString(value.policy().intervalTicks() / 24000);
            draftInitialized = true;
        }
        var previousSelection = selected;
        selected = previousSelection == null ? null : value.entries().stream().filter(entry -> entry.id().equals(previousSelection.id())).findFirst().orElse(null);
        if (policyView) { updateActions(); return; }
        if (getFocused() instanceof EditBox) { updateActions(); return; }
        double scroll = rows == null ? 0 : rows.getScrollAmount(); rebuildWidgets();
        if (rows != null) rows.setClampedScrollAmount(scroll);
    }
    @Override protected void init() {
        initWindow(700, 420, false);
        addRenderableWidget(VillagerUi.buttonBuilder(text(policyView ? "requests" : "policy"), ignored -> {
            policyView = !policyView; selected = null; rebuildWidgets();
        }).bounds(left, top + 25, 110, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(left, top + panelHeight - 20, 80, 20).build());
        rows = null;
        if (policyView) {
            addRenderableWidget(VillagerUi.buttonBuilder(text(automatic ? "automatic_on" : "automatic_off"), button -> {
                automatic = !automatic; button.setMessage(text(automatic ? "automatic_on" : "automatic_off"));
            }).bounds(left, top + 55, Math.min(260, panelWidth), 20).build());
            field(reserve, policyY(0), value -> reserve = value, "reserve_days");
            field(maximum, policyY(1), value -> maximum = value, "maximum_items");
            field(standing, policyY(2), value -> standing = value, "minimum_standing");
            field(interval, policyY(3), value -> interval = value, "interval_days");
            send = addRenderableWidget(VillagerUi.buttonBuilder(text("save"), ignored -> request(Action.POLICY))
                    .bounds(left + panelWidth - 110, top + panelHeight - 20, 110, 20).build());
        } else {
            rows = addRenderableWidget(new Rows(left, top + 54, panelWidth, Math.max(36, panelHeight - 158)));
            if (snapshot != null) snapshot.entries().forEach(rows::add);
            previous = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("<"), ignored -> { page = Math.max(0, page - 1); request(Action.VIEW); })
                    .bounds(left, top + panelHeight - 100, 24, 20).build());
            next = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(">"), ignored -> { page++; request(Action.VIEW); })
                    .bounds(left + 28, top + panelHeight - 100, 24, 20).build());
            var quantityField = addRenderableWidget(new EditBox(font, left + panelWidth - 80, top + panelHeight - 100, 80, 20, text(target == null ? "offer_items" : "nutrition")));
            quantityField.setMaxLength(8); quantityField.setValue(quantity); quantityField.setResponder(value -> { quantity = value; updateActions(); });
            int width = (panelWidth - 16) / 5;
            Action[] types = {Action.APPROVE, Action.ACCEPT, Action.DECLINE, Action.RETRY, Action.CANCEL};
            for (int i = 0; i < actions.length; i++) {
                Action action = types[i];
                actions[i] = addRenderableWidget(VillagerUi.buttonBuilder(label(action), ignored -> request(action))
                        .bounds(left + i * (width + 4), top + panelHeight - 74, width, 20).build());
            }
            send = addRenderableWidget(VillagerUi.buttonBuilder(text("request"), ignored -> request(Action.REQUEST))
                    .bounds(left + panelWidth - 110, top + panelHeight - 20, 110, 20).build());
            send.visible = target != null;
        }
        updateActions();
    }
    private int policyY(int index) { return (panelHeight < 270 ? 77 : 81) + index * (panelHeight < 270 ? 22 : 26); }
    private void field(String value, int y, java.util.function.Consumer<String> change, String name) {
        var box = addRenderableWidget(new EditBox(font, left + Math.min(250, panelWidth - 84), top + y, 80, 20, text(name)));
        box.setMaxLength(5); box.setValue(value); box.setResponder(next -> { change.accept(next); updateActions(); });
    }
    private static int number(String value, int minimum, int maximum) {
        try { int result = Integer.parseInt(value); return result >= minimum && result <= maximum ? result : -1; }
        catch (NumberFormatException ignored) { return -1; }
    }
    private Policy draft() {
        int days = number(reserve, 1, 30), items = number(maximum, 1, 1728), minimum = number(standing, 0, 100), frequency = number(interval, 1, 100);
        return days < 0 || items < 0 || minimum < 0 || frequency < 0 ? null : new Policy(automatic, days, items, minimum, frequency * 24000);
    }
    private void updateActions() {
        if (send == null) return;
        boolean enabled = !submitting && snapshot != null && snapshot.authorized() && !snapshot.blocker().equals("read_only") && !snapshot.blocker().equals("disabled");
        if (policyView) { send.active = enabled && draft() != null; return; }
        previous.active = !submitting && page > 0;
        next.active = !submitting && snapshot != null && (page + 1) * SettlementAidPayload.PAGE_SIZE < snapshot.total();
        boolean donor = selected != null && selected.donating();
        var state = selected == null ? null : selected.state();
        actions[0].active = enabled && donor && state == SettlementFoodRequest.State.AWAITING_OFFER && number(quantity, 1, 1728) > 0;
        actions[1].active = enabled && !donor && state == SettlementFoodRequest.State.AWAITING_ACCEPTANCE;
        actions[2].active = enabled && (donor && (state == SettlementFoodRequest.State.AWAITING_OFFER || state == SettlementFoodRequest.State.OFFER_PREPARING)
                || !donor && state == SettlementFoodRequest.State.AWAITING_ACCEPTANCE);
        actions[3].active = enabled && donor && state == SettlementFoodRequest.State.OFFER_PREPARING && !selected.blocker().isEmpty();
        actions[4].active = enabled && selected != null && switch (selected.state()) {
            case REQUEST_SENT, AWAITING_OFFER, OFFER_PREPARING, OFFER_SENT, AWAITING_ACCEPTANCE, ACCEPTANCE_SENT, PREPARING -> true;
            default -> false;
        };
        send.active = enabled && target != null && number(quantity, 1, 10000000) > 0 && snapshot.neededNutrition() > 0;
    }
    private void request(Action action) {
        if (minecraft.player == null) return;
        var policy = snapshot == null ? new Policy(false, 3, 128, 0, 72000) : snapshot.policy();
        if (action == Action.POLICY) { policy = draft(); if (policy == null) return; }
        if (action == Action.APPROVE) {
            int items = number(quantity, 1, 1728); if (items < 0) return;
            policy = new Policy(policy.automatic(), policy.reserveDays(), items, policy.minimumStanding(), policy.intervalTicks());
        }
        long revision = snapshot == null ? 0 : action == Action.POLICY || action == Action.REQUEST || action == Action.VIEW
                ? snapshot.policyRevision() : selected == null ? 0 : selected.revision();
        long nutrition = action == Action.REQUEST ? number(quantity, 1, 10000000) : 0;
        if (nutrition < 0) return;
        sentAction = action; submitting = action != Action.VIEW; requestId = VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new SettlementAidRequestPayload(village, target, selected == null ? null : selected.id(), action, page,
                revision, nutrition, policy, requestId));
        refreshAt = Util.getMillis() + 5000; updateActions();
    }
    @Override public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive()) { minecraft.setScreen(null); return; }
        if (Util.getMillis() >= refreshAt) {
            if (submitting) feedback = text("no_reply");
            request(Action.VIEW);
        }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        tooltip = null; super.render(graphics, mouseX, mouseY, partial);
        graphics.drawString(font, snapshot == null ? title : text("for_village", snapshot.name()), left, top, 0xffffff);
        if (policyView) {
            String[] labels = {"reserve_days", "maximum_items", "minimum_standing", "interval_days"};
            for (int i = 0; i < labels.length; i++) graphics.drawString(font, text(labels[i]), left, top + policyY(i) + 6, 0xbfbfbf);
            if (panelHeight >= 270) graphics.drawWordWrap(font, text("policy_help"), left, top + 187, panelWidth, 0xbfbfbf);
        } else {
            graphics.drawString(font, text(target == null ? "offer_items" : "nutrition"), left + panelWidth - 240, top + panelHeight - 94, 0xbfbfbf);
            if (snapshot == null || snapshot.entries().isEmpty()) graphics.drawString(font, text(snapshot == null ? "loading" : "empty"), left + 5, top + 60, 0xbfbfbf);
            if (target != null) graphics.drawString(font, font.plainSubstrByWidth(text("request_to", targetName).getString(), panelWidth - 125), left + 120, top + 31, 0xffffff);
        }
        Component message = snapshot != null && !snapshot.blocker().isEmpty() ? text("blocker." + snapshot.blocker()) : feedback;
        graphics.drawString(font, font.plainSubstrByWidth(message.getString(), panelWidth), left, top + panelHeight - 44, 0xffd49b);
        if (tooltip != null) graphics.renderTooltip(font, font.split(tooltip, Math.min(350, panelWidth)), mouseX, mouseY);
    }
    private Component detail(SettlementAidPayload.Entry entry) {
        var value = text("summary", label(entry.state()), entry.offered(), entry.requested(), entry.delivered());
        if (entry.arrangement() != null) value = value.copy().append(". ").append(label(entry.arrangement()));
        if (!entry.blocker().isEmpty()) value = value.copy().append(". ").append(text("blocker." + entry.blocker()));
        return value;
    }
    private final class Rows extends ObjectSelectionList<Row> {
        Rows(int x, int y, int width, int height) { super(SettlementAidScreen.this.minecraft, width, height, y, 44); setX(x); centerListVertically = false; }
        void add(SettlementAidPayload.Entry entry) { var row = new Row(entry); addEntry(row); if (selected != null && selected.id().equals(entry.id())) setSelected(row); }
        @Override public void setSelected(Row row) { super.setSelected(row); if (row != null) { selected = row.entry; updateActions(); } }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        @Override protected void renderListBackground(GuiGraphics graphics) { }
        @Override protected void renderListSeparators(GuiGraphics graphics) { }
    }
    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final SettlementAidPayload.Entry entry;
        Row(SettlementAidPayload.Entry entry) { this.entry = entry; }
        @Override public Component getNarration() { return Component.literal(entry.name()).append(". ").append(detail(entry)); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; rows.setSelected(this); return true; }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partial) {
            graphics.drawString(font, font.plainSubstrByWidth(text(entry.donating() ? "to" : "from", entry.name()).getString(), width - 6), x + 3, y + 2, 0xffffff);
            graphics.drawString(font, font.plainSubstrByWidth(detail(entry).getString(), width - 6), x + 3, y + 15, 0xbfbfbf);
            if (entry.arrives() > snapshot.time()) graphics.drawString(font, text("arrives", (entry.arrives() - snapshot.time()) / 20), x + 3, y + 28, 0xbfbfbf);
            if (hovered) tooltip = getNarration();
        }
    }
}
