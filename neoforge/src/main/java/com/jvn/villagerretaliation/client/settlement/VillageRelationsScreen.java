package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.exploration.ExploredMapScreen;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.VillageRelationsPayload;
import com.jvn.villagerretaliation.network.VillageRelationsPayload.Entry;
import com.jvn.villagerretaliation.network.VillageRelationsRequestPayload;
import com.jvn.villagerretaliation.network.VillageRelationsRequestPayload.Action;
import com.jvn.villagerretaliation.network.VillageRelationsRequestPayload.View;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillageRelationsScreen extends VillagerWindowScreen {
    private final Screen parent;
    private UUID perspective;
    private UUID village;
    private final UUID ruler;
    private View view;
    private Action choosing;
    private Action sentAction = Action.VIEW;
    private boolean submitting;
    private UUID historyTarget;
    private Entry selected;
    private VillageRelationsPayload snapshot;
    private int index, requestId;
    private long refreshAt;
    private String query = "";
    private Component feedback = Component.empty();
    private Component tooltip;
    private Rows rows;
    private EditBox search;
    private final Button[] actions = new Button[6];
    private Button previous, next, foodRequest;

    public VillageRelationsScreen(Screen parent, UUID perspective, UUID village, UUID ruler) {
        super(text("title"));
        this.parent = parent;
        this.perspective = perspective;
        this.village = village;
        this.ruler = ruler;
        this.view = ruler != null || village == null ? View.KNOWLEDGE : View.CONTACTS;
    }

    public static Component text(String key, Object... arguments) {
        return Component.translatable("villagerretaliation.gui.relations." + key, arguments);
    }

    VillageRelationsPayload snapshot() { return snapshot; }

    private static Component label(Enum<?> value) { return text(value.name().toLowerCase(Locale.ROOT)); }

    @Override protected void init() {
        initWindow(700, 420, false);
        int y = top + 30;
        for (var tab : new View[]{View.CONTACTS, View.KNOWLEDGE, View.REPORTS, View.OUTBOX, View.HISTORY, View.PERSPECTIVES}) {
            var button = addRenderableWidget(VillagerUi.buttonBuilder(label(tab), ignored -> navigate(tab)).bounds(left, y, 96, 20).build());
            button.active = tab != view && (tab == View.KNOWLEDGE || tab == View.PERSPECTIVES || village != null)
                    && (tab != View.HISTORY || historyTarget != null)
                    && (tab != View.REPORTS && tab != View.OUTBOX || snapshot != null && snapshot.inspects());
            y += 24;
        }
        var food = addRenderableWidget(VillagerUi.buttonBuilder(SettlementAidScreen.text("title"), ignored ->
                minecraft.setScreen(new SettlementAidScreen(this, village, null, ""))).bounds(left, y, 96, 20).build());
        food.active = snapshot != null && snapshot.inspects() && village != null;
        foodRequest = addRenderableWidget(VillagerUi.buttonBuilder(SettlementAidScreen.text("request"), ignored ->
                minecraft.setScreen(new SettlementAidScreen(this, perspective, selected.id(), selected.name()))).bounds(left, y + 24, 96, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(left, top + panelHeight - 20, 96, 20).build());
        search = addRenderableWidget(new EditBox(font, left + 106, top + 30, panelWidth - 106, 20, text("search")));
        search.setMaxLength(64); search.setValue(query); search.setHint(text("search"));
        search.setResponder(value -> { query = value; index = 0; refreshAt = Util.getMillis() + 350; });
        rows = addRenderableWidget(new Rows(left + 106, top + 56, panelWidth - 106, Math.max(45, panelHeight - 148)));
        previous = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("<"), ignored -> page(-1))
                .bounds(left + 106, top + panelHeight - 88, 24, 20).build());
        next = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(">"), ignored -> page(1))
                .bounds(left + 134, top + panelHeight - 88, 24, 20).build());
        int buttonWidth = (panelWidth - 114) / 3;
        for (int i = 0; i < actions.length; i++) {
            int action = i;
            actions[i] = addRenderableWidget(VillagerUi.buttonBuilder(Component.empty(), ignored -> perform(action))
                    .bounds(left + 106 + (i % 3) * (buttonWidth + 4), top + panelHeight - 44 + (i / 3) * 24, buttonWidth, 20).build());
        }
        populate();
    }

    private void navigate(View target) {
        view = target; index = 0; query = ""; selected = null; choosing = null; snapshot = null; refreshAt = 0;
        rebuildWidgets();
    }

    private void page(int step) { index = Math.max(0, index + step); selected = null; refreshAt = 0; request(Action.VIEW, historyTarget); }

    public void accept(VillageRelationsPayload payload) {
        if (payload.requestId() != requestId || payload.view() != view) return;
        snapshot = payload; index = payload.index();
        submitting = false;
        if (sentAction != Action.VIEW) feedback = text(sentAction == Action.SEND_INTRODUCTION || sentAction == Action.SEND_REPORT ? "letter_sent" : "shared");
        if (payload.origin() != null) village = payload.origin().id();
        if (payload.result() != com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService.Result.APPLIED)
            feedback = text("result." + payload.result().name().toLowerCase(Locale.ROOT));
        var selection = selected;
        selected = selection == null ? null : payload.entries().stream().filter(entry -> entry.id().equals(selection.id())).findFirst().orElse(null);
        double scroll = rows == null ? 0 : rows.getScrollAmount();
        boolean searching = search != null && search.isFocused();
        int cursor = search == null ? 0 : search.getCursorPosition();
        rebuildWidgets();
        rows.setClampedScrollAmount(scroll);
        if (searching) { setFocused(search); search.setCursorPosition(Math.min(cursor, search.getValue().length())); }
    }

    private void populate() {
        if (snapshot != null && snapshot.view() == view) snapshot.entries().forEach(entry -> rows.add(entry));
        previous.active = snapshot != null && index > 0;
        next.active = snapshot != null && (index + 1L) * VillageRelationsRequestPayload.PAGE_SIZE < snapshot.total();
        updateActions();
    }

    private void updateActions() {
        if (actions[0] == null) return;
        boolean contact = selected != null && (view == View.CONTACTS || view == View.KNOWLEDGE || view == View.PERSPECTIVES);
        boolean editable = !submitting && snapshot != null && snapshot.represents() && snapshot.blocker().isEmpty();
        foodRequest.active = editable && contact && view == View.CONTACTS && perspective != null && perspective.equals(village)
                && !selected.id().equals(village);
        action(0, view == View.PERSPECTIVES ? "choose_perspective" : "view_relations", contact && choosing == null);
        action(1, "show_map", contact && choosing == null && minecraft.level != null
                && selected.dimension().equals(minecraft.level.dimension().location()));
        action(2, choosing == null ? "share" : "send", contact && !submitting && (choosing != null ? editable && !selected.id().equals(village)
                && (choosing != Action.SEND_REPORT || !selected.id().equals(perspective))
                : ruler != null ? selected.personal() && !selected.shared() && !selected.id().equals(village)
                : editable && perspective.equals(village) && selected.personal() && !selected.shared() && !selected.id().equals(village)));
        if (ruler != null && choosing == null) actions[2].setMessage(text("introduce"));
        action(3, "send_introduction", editable && village != null && !village.equals(perspective) && choosing == null);
        action(4, "send_report", editable && village != null && !village.equals(perspective) && choosing == null);
        action(5, "history", selected != null && selected.relationKnown() && choosing == null);
    }

    private void action(int index, String key, boolean enabled) { actions[index].setMessage(text(key)); actions[index].active = enabled; }

    private void perform(int action) {
        if (action == 0 && selected != null) {
            if (view == View.PERSPECTIVES) {
                perspective = selected.id(); village = perspective; navigate(View.CONTACTS);
            } else minecraft.setScreen(new VillageRelationsScreen(this, perspective, selected.id(), null));
        } else if (action == 1 && selected != null) {
            var map = parent instanceof ExploredMapScreen existing ? existing : new ExploredMapScreen(this);
            map.focusRelations(perspective, village, selected);
            minecraft.setScreen(map);
        } else if (action == 2 && selected != null) {
            request(choosing != null ? choosing : ruler != null ? Action.INTRODUCE : Action.SHARE, selected.id());
            feedback = text("submitted");
            choosing = null;
        } else if (action == 3 || action == 4) {
            navigate(View.KNOWLEDGE);
            choosing = action == 3 ? Action.SEND_INTRODUCTION : Action.SEND_REPORT;
            feedback = text("choose_subject");
        } else if (action == 5 && selected != null) {
            historyTarget = selected.id(); navigate(View.HISTORY);
        }
    }

    private void request(Action action, UUID target) {
        if (minecraft.player == null) return;
        sentAction = action; submitting = action != Action.VIEW;
        requestId = VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new VillageRelationsRequestPayload(perspective, village, target, ruler, action, view,
                index, query, snapshot == null ? 0 : snapshot.revision(), requestId));
        refreshAt = Util.getMillis() + 5000;
        updateActions();
    }

    @Override public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive()) { minecraft.setScreen(null); return; }
        if (Util.getMillis() >= refreshAt) {
            if (submitting) feedback = text("no_reply");
            request(Action.VIEW, historyTarget);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        tooltip = null;
        super.render(graphics, mouseX, mouseY, partial);
        var heading = snapshot == null || snapshot.origin() == null ? title : text("for_village", snapshot.origin().name());
        graphics.drawString(font, heading, left, top, 0xffffff);
        if (snapshot == null || snapshot.entries().isEmpty()) graphics.drawString(font,
                text(snapshot == null ? "loading" : "empty"), left + 112, top + 62, 0xbfbfbf);
        var message = snapshot != null && !snapshot.blocker().isEmpty() ? text("blocker." + snapshot.blocker()) : feedback;
        graphics.drawString(font, font.plainSubstrByWidth(message.getString(), panelWidth - 174), left + 168, top + panelHeight - 82, 0xffd49b);
        if (snapshot != null) graphics.drawString(font, text("page", index + 1, Math.max(1, (snapshot.total() + 23) / 24)),
                left + 106, top + panelHeight - 64, 0xbfbfbf);
        if (tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    public static void standingBar(GuiGraphics graphics, int x, int y, int width, int standing) {
        int center = x + width / 2;
        int extent = (width / 2 - 1) * Math.clamp(standing, -100, 100) / 100;
        graphics.fill(x, y, x + width, y + 5, 0xff353a40);
        if (extent != 0) graphics.fill(Math.min(center, center + extent), y, Math.max(center, center + extent), y + 5,
                standing < 0 ? 0xffd28473 : 0xff8dbe95);
        graphics.fill(center, y - 1, center + 1, y + 6, 0xffe0ddd0);
    }

    private Component detail(Entry entry) {
        if (view == View.HISTORY) return text("event", entry.standing() > 0 ? "+" + entry.standing() : entry.standing(),
                text("reason." + entry.reason()), entry.date() / 24000 + 1);
        if (view == View.REPORTS || view == View.OUTBOX) return text(view == View.OUTBOX ? "outgoing" : "reported",
                entry.attribution(), text(entry.reason()), entry.standing(), entry.date() / 24000 + 1,
                Math.max(0, entry.arrival() - snapshot.time()) / 20);
        return entry.relationKnown() ? text("standing", label(entry.band()), entry.standing(), label(entry.condition())).copy()
                .append(entry.established() ? Component.empty() : text("not_established"))
                : text(entry.shared() ? "known" : "personally_known");
    }

    private final class Rows extends ObjectSelectionList<Row> {
        Rows(int x, int y, int width, int height) { super(VillageRelationsScreen.this.minecraft, width, height, y, 40); setX(x); centerListVertically = false; }
        void add(VillageRelationsPayload.Entry entry) { var row = new Row(entry); addEntry(row); if (selected != null && selected.id().equals(entry.id())) setSelected(row); }
        @Override public void setSelected(Row row) { super.setSelected(row); if (row != null) { selected = row.entry; updateActions(); } }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        @Override protected void renderListBackground(GuiGraphics graphics) { }
        @Override protected void renderListSeparators(GuiGraphics graphics) { }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final VillageRelationsPayload.Entry entry;
        Row(VillageRelationsPayload.Entry entry) { this.entry = entry; }
        @Override public Component getNarration() { return Component.literal(entry.name()).append(". ").append(detail(entry)); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; rows.setSelected(this); return true; }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partial) {
            graphics.drawString(font, font.plainSubstrByWidth(entry.name(), width - 6), x + 3, y + 2, 0xffffff);
            graphics.drawString(font, font.plainSubstrByWidth(detail(entry).getString(), width - 6), x + 3, y + 14, 0xbfbfbf);
            if (hovered) tooltip = getNarration();
            if (entry.relationKnown()) {
                int barWidth = Math.min(180, width - 90);
                standingBar(graphics, x + 18, y + 29, barWidth, entry.standing());
                graphics.drawString(font, "-", x + 4, y + 27, 0xd28473);
                graphics.drawString(font, "+", x + 22 + barWidth, y + 27, 0x8dbe95);
                graphics.drawString(font, "0", x + 16 + barWidth / 2, y + 28, 0xffffff);
            }
        }
    }
}
