package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolSnapshotPayload;
import com.jvn.villagerretaliation.settlement.SettlementWorkerPool;
import com.jvn.villagerretaliation.settlement.SettlementWorksitePlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementWorkerPoolsScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child {
    private final Screen previous;
    private final SettlementWorkerPoolClientState state;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private Draft draft;
    private UUID selected;
    private UUID selectedResident;
    private UUID selectedSite;
    private Tab tab = Tab.RESIDENTS;
    private String query = "";
    private int page;
    private boolean initialized;
    private boolean loadSelected;
    private boolean conflict;
    private boolean updatingLimits;
    private long previewAt = Long.MAX_VALUE;
    private long nextRequest;
    private SettlementWorkerPool.Configuration previewed;
    private SettlementWorkerPoolRequestPayload.Command submitted;
    private Rows rows;
    private EditBox minimum;
    private EditBox maximum;

    public SettlementWorkerPoolsScreen(Screen previous, UUID town) {
        super(text("title"));
        this.previous = previous;
        this.state = new SettlementWorkerPoolClientState(town);
    }

    public static SettlementWorkerPoolsScreen forPool(Screen previous, UUID town, UUID pool) {
        var screen = new SettlementWorkerPoolsScreen(previous, town);
        screen.selected = java.util.Objects.requireNonNull(pool);
        screen.loadSelected = true;
        return screen;
    }

    @Override public Screen previousPolicy() { return this.previous; }
    SettlementWorkerPoolSnapshotPayload snapshot() { return this.state.snapshot(); }
    SettlementWorkerPool.Configuration configuration() { return this.draft == null ? null : this.draft.configuration(); }

    @Override protected void init() {
        initWindow(460, 390);
        this.buttons.clear();
        this.minimum = null;
        this.maximum = null;
        if (this.draft == null) {
            this.rows = addRenderableWidget(new Rows(this.top + 24, Math.max(34, this.panelHeight - 110)));
            int fourth = (this.panelWidth - 12) / 4;
            button("new", 0, this.panelHeight - 78, fourth, this::create);
            button("open", fourth + 4, this.panelHeight - 78, fourth, this::open);
            button("pause", 2 * fourth + 8, this.panelHeight - 78, fourth, this::toggle);
            button("remove", 3 * fourth + 12, this.panelHeight - 78, fourth, this::remove);
            button("back", 0, this.panelHeight - 24, 84, this::onClose);
            button("refresh", this.panelWidth - 100, this.panelHeight - 24, 100, () -> submit(new SettlementWorkerPoolRequestPayload.View()));
        } else {
            var name = addRenderableWidget(new EditBox(this.font, this.left, this.top + 24, this.panelWidth - 108, 20, text("name")));
            name.setMaxLength(64);
            name.setHint(text("name"));
            name.setValue(this.draft.name);
            name.setResponder(value -> { this.draft.name = value; changed(); });
            button("reload", this.panelWidth - 104, 24, 104, this::reload);
            int third = (this.panelWidth - 8) / 3;
            for (var value : Tab.values()) button(value.key(), value.ordinal() * (third + 4), 48, third, () -> tab(value));
            if (this.tab == Tab.RESIDENTS) {
                var search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 72, this.panelWidth - 64, 20, text("search")));
                search.setMaxLength(96);
                search.setHint(text("search"));
                search.setValue(this.query);
                search.setResponder(value -> { this.query = value; this.page = 0; changed(); });
                button("previous", this.panelWidth - 60, 72, 28, () -> page(this.page - 1));
                button("next", this.panelWidth - 28, 72, 28, () -> page(this.page + 1));
            }
            this.rows = addRenderableWidget(new Rows(this.top + (this.tab == Tab.RESIDENTS ? 96 : 72),
                    Math.max(34, this.panelHeight - (this.tab == Tab.RESIDENTS ? 170 : this.tab == Tab.WORKSITES ? 156 : 122))));
            if (this.tab == Tab.RESIDENTS) {
                button("include", 0, this.panelHeight - 68, third, this::includeResident);
                button("remove_member", third + 4, this.panelHeight - 68, third, this::removeMember).setTooltip(Tooltip.create(text("remove_member_help")));
                button("return_control", 2 * third + 8, this.panelHeight - 68, third, this::returnControl).setTooltip(Tooltip.create(text("return_control_help")));
            } else if (this.tab == Tab.WORKSITES) {
                button("include", 0, this.panelHeight - 68, third, this::includeSite);
                this.minimum = limit("minimum", third + 4, this.panelHeight - 68, third);
                this.maximum = limit("maximum", 2 * third + 8, this.panelHeight - 68, third);
                limits();
            }
            int fourth = (this.panelWidth - 12) / 4;
            button("back", 0, this.panelHeight - 24, fourth, this::backToList);
            button("preview", fourth + 4, this.panelHeight - 24, fourth, this::preview).setTooltip(Tooltip.create(text("preview_help")));
            button("save", 2 * fourth + 8, this.panelHeight - 24, fourth, () -> submit(new SettlementWorkerPoolRequestPayload.Save(configuration(), false)));
            button("activate", 3 * fourth + 12, this.panelHeight - 24, fourth, () -> submit(new SettlementWorkerPoolRequestPayload.Save(configuration(), true)));
        }
        this.rows.refresh();
        update();
        if (!this.initialized) {
            this.initialized = true;
            submit(new SettlementWorkerPoolRequestPayload.View());
        }
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        var button = addRenderableWidget(VillagerUi.buttonBuilder(text(key), ignored -> action.run())
                .bounds(this.left + x, this.top + y, width, 20).build());
        this.buttons.put(key, button);
        return button;
    }

    private EditBox limit(String key, int x, int y, int width) {
        var field = addRenderableWidget(new EditBox(this.font, this.left + x, this.top + y, width, 20, text(key)));
        field.setMaxLength(2);
        field.setFilter(value -> value.isEmpty() || value.chars().allMatch(character -> character >= '0' && character <= '9'));
        field.setTooltip(Tooltip.create(text(key + "_help")));
        field.setResponder(value -> {
            if (this.updatingLimits || this.draft == null) return;
            var limits = this.draft.worksites.get(this.selectedSite);
            if (limits == null) return;
            if (key.equals("minimum")) limits.minimum = value;
            else limits.maximum = value;
            changed();
        });
        return field;
    }

    private void limits() {
        if (this.minimum == null || this.maximum == null) return;
        var selected = this.draft.worksites.get(this.selectedSite);
        var site = selectedSite();
        this.updatingLimits = true;
        this.minimum.setValue(selected == null ? "0" : selected.minimum);
        this.maximum.setValue(selected == null ? site == null ? "" : Integer.toString(site.desired()) : selected.maximum);
        this.updatingLimits = false;
    }

    private void submit(SettlementWorkerPoolRequestPayload.Command command) {
        var selected = selectedPool();
        UUID id = this.draft == null ? this.selected : this.draft.id;
        long revision = this.draft == null ? selected == null ? -1 : selected.revision() : this.draft.revision;
        var request = this.state.request(id, revision, this.query, this.page, command, Util.getMillis());
        if (request == null) return;
        this.submitted = command;
        this.nextRequest = Util.getMillis() + 300;
        PacketDistributor.sendToServer(request);
        update();
    }

    void accept(SettlementWorkerPoolSnapshotPayload payload) {
        if (!this.state.accept(payload)) return;
        if (payload.feedback().equals("busy")) {
            if (this.submitted instanceof SettlementWorkerPoolRequestPayload.Preview) this.previewAt = Util.getMillis() + 500;
            update();
            return;
        }
        for (var resident : payload.residents()) this.names.put(resident.id(), resident.name());
        boolean saved = this.draft != null && payload.applied() && this.submitted instanceof SettlementWorkerPoolRequestPayload.Save;
        if (this.loadSelected || saved) {
            this.loadSelected = false;
            if (payload.selected() != null) {
                this.draft = new Draft(payload.selected());
                this.selected = this.draft.id;
                this.conflict = false;
                changed();
            }
            if (this.minecraft.screen == this) rebuildWidgets();
        } else if (this.draft != null && (payload.feedback().equals("pool_changed")
                || payload.selected() != null && payload.selected().id().equals(this.draft.id) && payload.selected().revision() != this.draft.revision))
            this.conflict = true;
        if (this.submitted instanceof SettlementWorkerPoolRequestPayload.Preview preview && payload.feedback().isEmpty())
            this.previewed = preview.configuration();
        if (this.rows != null) this.rows.refresh();
        update();
    }

    @Override public void tick() {
        this.state.expire(Util.getMillis());
        if (this.draft != null && !this.state.waiting() && Util.getMillis() >= this.nextRequest && Util.getMillis() >= this.previewAt) preview();
        update();
    }

    private void changed() {
        this.previewed = null;
        this.previewAt = Util.getMillis() + 350;
        if (this.rows != null) this.rows.refresh();
        update();
    }

    private void preview() {
        var configuration = configuration();
        if (configuration == null || this.state.busy()) return;
        this.previewAt = Long.MAX_VALUE;
        submit(new SettlementWorkerPoolRequestPayload.Preview(configuration));
    }

    private void create() {
        this.draft = new Draft(UUID.randomUUID());
        this.selected = this.draft.id;
        this.selectedResident = null;
        this.selectedSite = null;
        this.query = "";
        this.page = 0;
        this.conflict = false;
        this.tab = Tab.RESIDENTS;
        changed();
        rebuildWidgets();
    }

    private void open() {
        if (selectedPool() == null) return;
        this.loadSelected = true;
        this.query = "";
        this.page = 0;
        submit(new SettlementWorkerPoolRequestPayload.View());
    }

    private void reload() {
        if (this.draft.revision < 0) { preview(); return; }
        this.minecraft.setScreen(new SettlementConfirmScreen(this, text("reload"), text("reload_help"), () -> {
            this.loadSelected = true;
            submit(new SettlementWorkerPoolRequestPayload.View());
        }));
    }

    private void toggle() {
        var pool = selectedPool();
        if (pool != null) submit(new SettlementWorkerPoolRequestPayload.Change(pool.state() == SettlementWorkerPool.State.ACTIVE
                ? SettlementWorkerPoolRequestPayload.Action.PAUSE : SettlementWorkerPoolRequestPayload.Action.ACTIVATE));
    }

    private void remove() {
        if (selectedPool() == null) return;
        this.minecraft.setScreen(new SettlementConfirmScreen(this, text("remove"), text("remove_help"),
                () -> submit(new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.REMOVE))));
    }

    private void tab(Tab tab) {
        this.tab = tab;
        rebuildWidgets();
    }

    private void page(int page) {
        this.page = Math.max(0, page);
        this.selectedResident = null;
        changed();
    }

    private void includeResident() {
        var resident = selectedResident();
        if (resident == null) return;
        if (!this.draft.residents.remove(resident.id()) && (resident.pool() == null || resident.pool().equals(this.draft.id))
                && this.draft.residents.size() < SettlementWorkerPool.MAX_RESIDENTS) this.draft.residents.add(resident.id());
        changed();
    }

    private void removeMember() {
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, text("remove_member"), query -> this.draft.residents.stream()
                .map(id -> new SettlementPolicyChoiceScreen.Choice(Component.literal(this.names.getOrDefault(id, id.toString())),
                        Component.literal(id.toString()), ItemStack.EMPTY, () -> { this.draft.residents.remove(id); changed(); })).toList()));
    }

    private void returnControl() {
        var resident = selectedResident();
        if (resident == null || snapshot() == null) return;
        submit(new SettlementWorkerPoolRequestPayload.ReturnControl(resident.id(), snapshot().revision()));
    }

    private void includeSite() {
        if (this.selectedSite == null) return;
        if (this.draft.worksites.remove(this.selectedSite) == null) {
            var site = selectedSite();
            if (site != null) this.draft.worksites.put(site.id(), new Limits(0, site.desired()));
        }
        limits();
        changed();
    }

    private SettlementWorkerPoolSnapshotPayload.Summary selectedPool() {
        return snapshot() == null ? null : snapshot().pools().stream().filter(pool -> pool.id().equals(this.selected)).findFirst().orElse(null);
    }

    private SettlementWorkerPoolSnapshotPayload.Resident selectedResident() {
        return snapshot() == null || !currentPage() ? null : snapshot().residents().stream().filter(resident -> resident.id().equals(this.selectedResident)).findFirst().orElse(null);
    }

    private SettlementWorkerPoolSnapshotPayload.Worksite selectedSite() {
        return snapshot() == null ? null : snapshot().worksites().stream().filter(site -> site.id().equals(this.selectedSite)).findFirst().orElse(null);
    }

    private boolean currentPage() {
        return snapshot() != null && snapshot().page() == this.page && snapshot().query().equals(this.query);
    }

    private void update() {
        if (this.buttons.isEmpty() || (this.draft != null) != this.buttons.containsKey("preview")) return;
        boolean ready = !this.state.waiting() && Util.getMillis() >= this.nextRequest;
        for (var button : this.buttons.values()) button.active = !this.state.busy();
        for (var child : children()) if (child instanceof EditBox field) field.active = !this.state.busy();
        if (this.draft == null) {
            var pool = selectedPool();
            this.buttons.get("new").active &= ready && snapshot() != null && snapshot().pools().size() < SettlementWorkerPool.MAX_POOLS;
            this.buttons.get("open").active &= ready && pool != null;
            this.buttons.get("pause").active = ready && this.state.editable() && pool != null;
            this.buttons.get("pause").setMessage(text(pool != null && pool.state() == SettlementWorkerPool.State.ACTIVE ? "pause" : "activate"));
            this.buttons.get("remove").active = this.buttons.get("pause").active;
            this.buttons.get("refresh").active &= ready;
            return;
        }
        var configuration = configuration();
        this.buttons.get("save").active = ready && this.state.editable() && !this.conflict && configuration != null;
        this.buttons.get("activate").active = this.buttons.get("save").active && configuration.equals(this.previewed)
                && snapshot() != null && snapshot().blocker().isEmpty();
        this.buttons.get("preview").active = ready && configuration != null;
        this.buttons.get("reload").active &= ready;
        this.buttons.get("reload").setMessage(text(this.draft.revision < 0 ? "refresh" : "reload"));
        for (var tab : Tab.values()) this.buttons.get(tab.key()).active &= this.tab != tab;
        this.buttons.get("residents").setMessage(text("residents_count", this.draft.residents.size()));
        this.buttons.get("worksites").setMessage(text("worksites_count", this.draft.worksites.size()));
        this.buttons.get("save").setTooltip(Tooltip.create(text(this.state.editable() ? "save_help" : "view_only")));
        this.buttons.get("activate").setTooltip(Tooltip.create(text(this.state.editable() ? "activate_help" : "view_only")));
        if (this.tab == Tab.RESIDENTS) {
            var resident = selectedResident();
            boolean included = resident != null && this.draft.residents.contains(resident.id());
            this.buttons.get("include").setMessage(text(included ? "exclude" : "include"));
            this.buttons.get("include").active &= resident != null && (included || this.draft.residents.size() < SettlementWorkerPool.MAX_RESIDENTS
                    && (resident.pool() == null || resident.pool().equals(this.draft.id)));
            this.buttons.get("remove_member").active &= !this.draft.residents.isEmpty();
            this.buttons.get("return_control").active = ready && this.state.editable() && !this.conflict && resident != null && resident.pinned()
                    && this.draft.id.equals(resident.pool());
            this.buttons.get("previous").active = ready && this.page > 0;
            this.buttons.get("next").active = ready && currentPage() && snapshot().more();
            this.buttons.get("previous").setMessage(Component.literal("<"));
            this.buttons.get("next").setMessage(Component.literal(">"));
        } else if (this.tab == Tab.WORKSITES) {
            boolean included = this.draft.worksites.containsKey(this.selectedSite);
            this.buttons.get("include").setMessage(text(included ? "exclude" : "include"));
            this.buttons.get("include").active &= included || selectedSite() != null;
            this.minimum.active = this.maximum.active = !this.state.busy() && included;
        }
    }

    private void backToList() {
        this.selected = this.draft.id;
        this.draft = null;
        this.query = "";
        this.page = 0;
        this.previewAt = Long.MAX_VALUE;
        this.previewed = null;
        this.conflict = false;
        rebuildWidgets();
        submit(new SettlementWorkerPoolRequestPayload.View());
    }

    @Override public void onClose() { if (this.draft != null) backToList(); else this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        if (this.draft != null) {
            if (this.tab == Tab.WORKSITES) {
                int third = (this.panelWidth - 8) / 3;
                graphics.drawString(this.font, text("minimum"), this.left + third + 4, this.top + this.panelHeight - 80, 0xDDDDDD);
                graphics.drawString(this.font, text("maximum"), this.left + 2 * third + 8, this.top + this.panelHeight - 80, 0xDDDDDD);
            }
        }
        var feedback = this.conflict ? SettlementWorkerPoolClient.blocker("pool_changed")
                : snapshot() != null && !snapshot().editable() ? text("view_only")
                : this.draft != null && configuration() == null ? text("invalid_configuration")
                : this.draft != null && this.previewAt != Long.MAX_VALUE && !this.state.waiting() ? SettlementWorkerPoolClient.blocker("checking")
                : SettlementWorkerPoolClient.blocker(this.state.feedback());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(feedback.getString(), this.panelWidth), this.left,
                this.top + this.panelHeight - 40, 0xFFCC88);
        var row = this.rows.hovered();
        if (row != null) graphics.renderTooltip(this.font, this.font.split(row.name.copy().append("\n").append(row.detail).append("\n").append(row.help),
                Math.min(360, this.width - 24)), mouseX, mouseY);
        else if (mouseY >= this.top + this.panelHeight - 44 && mouseY < this.top + this.panelHeight - 26 && !feedback.getString().isEmpty())
            graphics.renderTooltip(this.font, this.font.split(feedback, Math.min(360, this.width - 24)), mouseX, mouseY);
    }

    private String areaName(UUID id) {
        return id == null ? text("unassigned").getString() : snapshot().worksites().stream().filter(site -> site.id().equals(id))
                .map(SettlementWorkerPoolSnapshotPayload.Worksite::name).findFirst().orElse(id.toString());
    }

    private static Component text(String key, Object... args) { return SettlementWorkerPoolClient.text(key, args); }
    private enum Tab { RESIDENTS, WORKSITES, HISTORY; String key() { return name().toLowerCase(Locale.ROOT); } }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows(int y, int height) { super(SettlementWorkerPoolsScreen.this.minecraft, panelWidth, height, y, 46); setX(left); }
        Row hovered() { return getHovered(); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }

        void refresh() {
            double scroll = getScrollAmount();
            clearEntries();
            if (snapshot() == null) row(null, text("loading"), Component.empty(), Component.empty(), () -> {});
            else if (draft == null) for (var pool : snapshot().pools())
                row(pool.id(), Component.literal(pool.name()), text("state." + pool.state().name().toLowerCase(Locale.ROOT)),
                        SettlementWorkerPoolClient.blocker(pool.blocker()), () -> { selected = pool.id(); update(); });
            else switch (tab) {
                case RESIDENTS -> {
                    if (!currentPage()) row(null, text("loading"), Component.empty(), Component.empty(), () -> {});
                    else for (var resident : snapshot().residents()) {
                        boolean included = draft.residents.contains(resident.id());
                        var detail = text("resident_assignment", text(included ? "enrolled" : "not_enrolled"), areaName(resident.duty()));
                        var status = resident.blocker().isEmpty() ? text("ready") : SettlementWorkerPoolClient.blocker(resident.blocker());
                        if (resident.cooldown() > 0) status = text("cooldown", (resident.cooldown() + 19) / 20);
                        row(resident.id(), Component.literal(resident.name()), detail, status,
                                () -> { selectedResident = resident.id(); update(); });
                    }
                }
                case WORKSITES -> {
                    for (var site : snapshot().worksites()) {
                        var limits = draft.worksites.get(site.id());
                        var detail = text("site_staffing", site.current(), limits == null ? "0" : limits.minimum,
                                limits == null ? Integer.toString(site.desired()) : limits.maximum);
                        var need = !site.available() ? SettlementWorkerPoolClient.blocker("pool_worksite_unavailable")
                                : site.active() ? text(site.protectedShortage() ? "protected_need" : "priority_need", site.priority()) : text("no_work");
                        row(site.id(), text("site_name", text(limits == null ? "not_included" : "included"), site.name()), detail,
                                need.copy().append(site.cause().isEmpty() ? "" : " / " + site.cause()), () -> { selectedSite = site.id(); limits(); update(); });
                    }
                    for (var entry : draft.worksites.entrySet()) if (snapshot().worksites().stream().noneMatch(site -> site.id().equals(entry.getKey())))
                        row(entry.getKey(), text("missing_site"), Component.literal(entry.getKey().toString()),
                                SettlementWorkerPoolClient.blocker("pool_worksite_unavailable"), () -> { selectedSite = entry.getKey(); limits(); update(); });
                }
                case HISTORY -> {
                    for (var move : snapshot().history()) row(null, text("history_name", move.resident(), move.to()),
                            text("history_reason", text("reason." + move.reason().name().toLowerCase(Locale.ROOT)), move.priority()),
                            text("history_detail", move.from().isEmpty() ? text("unassigned") : move.from(), move.at(), move.cause()), () -> {});
                }
            }
            if (children().isEmpty()) row(null, text(draft == null ? "empty" : tab == Tab.HISTORY ? "no_history" : "no_matches"),
                    Component.empty(), Component.empty(), () -> {});
            setClampedScrollAmount(scroll);
        }

        private void row(UUID id, Component name, Component detail, Component help, Runnable choose) {
            var row = new Row(name, detail, help, choose);
            addEntry(row);
            UUID selection = draft == null ? selected : tab == Tab.RESIDENTS ? selectedResident : selectedSite;
            if (id != null && id.equals(selection)) setSelected(row);
        }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final Component name;
        final Component detail;
        final Component help;
        final Runnable choose;
        Row(Component name, Component detail, Component help, Runnable choose) {
            this.name = name;
            this.detail = detail;
            this.help = help;
            this.choose = choose;
        }
        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, font.plainSubstrByWidth(this.name.getString(), width - 6), left + 3, top + 3, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(this.detail.getString(), width - 6), left + 3, top + 16, 0xCCCCCC);
            graphics.drawString(font, font.plainSubstrByWidth(this.help.getString(), width - 6), left + 3, top + 29, 0xAAAAAA);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0 || state.busy()) return false;
            rows.setSelected(this);
            this.choose.run();
            return true;
        }
        @Override public Component getNarration() { return this.name.copy().append(" ").append(this.detail).append(" ").append(this.help); }
    }

    private static final class Draft {
        final UUID id;
        long revision = -1;
        String name = text("new_name").getString();
        final LinkedHashSet<UUID> residents = new LinkedHashSet<>();
        final Map<UUID, Limits> worksites = new LinkedHashMap<>();
        Draft(UUID id) { this.id = id; }
        Draft(SettlementWorkerPool pool) {
            this(pool.id());
            this.revision = pool.revision();
            this.name = pool.configuration().name();
            this.residents.addAll(pool.configuration().residents());
            for (var site : pool.configuration().worksites()) this.worksites.put(site.area(), new Limits(site.minimum(), site.maximum()));
        }
        SettlementWorkerPool.Configuration configuration() {
            if (this.name.isBlank() || this.name.length() > 64) return null;
            var sites = new ArrayList<SettlementWorkerPool.Staffing>();
            for (var entry : this.worksites.entrySet()) {
                var limits = entry.getValue();
                if (!limits.valid()) return null;
                sites.add(new SettlementWorkerPool.Staffing(entry.getKey(), Integer.parseInt(limits.minimum), Integer.parseInt(limits.maximum)));
            }
            return new SettlementWorkerPool.Configuration(this.name, List.copyOf(this.residents), sites);
        }
    }

    private static final class Limits {
        String minimum;
        String maximum;
        Limits(int minimum, int maximum) { this.minimum = Integer.toString(minimum); this.maximum = Integer.toString(maximum); }
        boolean valid() {
            if (this.minimum.isEmpty() || this.maximum.isEmpty()) return false;
            int minimum = Integer.parseInt(this.minimum), maximum = Integer.parseInt(this.maximum);
            return minimum <= maximum && maximum <= SettlementWorksitePlan.MAX_STAFF;
        }
    }
}
