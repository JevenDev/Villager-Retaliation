package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Entry;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class SettlementManagementScreen extends Screen {
    private SettlementManagementPayload snapshot;
    private Page desiredPage;
    private int desiredIndex;
    private String query = "";
    private int requestId;
    private long refreshAt;
    private long nextRequestAt;
    private boolean submitting;
    private UUID selected;
    private Component feedback = Component.empty();
    private long feedbackUntil;
    private Button previous;
    private Button next;
    private Rows rows;
    private EditBox search;
    private final Button[] actions = new Button[3];
    private final SettlementManagementScreen parent;
    private final SettlementManagementPayload.Resident assigning;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int contentLeft;
    private int contentWidth;

    public SettlementManagementScreen(SettlementManagementPayload snapshot) {
        this(snapshot, null, null);
    }

    private SettlementManagementScreen(SettlementManagementPayload snapshot, SettlementManagementScreen parent,
                                       SettlementManagementPayload.Resident assigning) {
        super(text("title"));
        this.snapshot = snapshot;
        this.parent = parent;
        this.assigning = assigning;
        this.desiredPage = assigning == null ? snapshot.page() : Page.AREAS;
        this.desiredIndex = assigning == null ? snapshot.index() : 0;
        this.query = assigning == null ? snapshot.query() : "";
        this.requestId = snapshot.requestId();
        this.refreshAt = Util.getMillis() + (assigning == null ? 2_000L : 0L);
    }

    public SettlementManagementPayload snapshot() {
        return this.snapshot;
    }

    public void accept(SettlementManagementPayload payload) {
        if (!this.snapshot.dimension().equals(payload.dimension()) || payload.requestId() < this.requestId
                || payload.header() != null && this.snapshot.header() != null && !payload.header().id().equals(this.snapshot.header().id())) {
            return;
        }
        if (payload.page() == Page.TOWNS && payload.header() == null) {
            this.desiredPage = Page.TOWNS;
            this.desiredIndex = payload.index();
            this.query = payload.query();
        } else if (payload.page() != this.desiredPage || !payload.query().equals(this.query)) {
            return;
        }
        boolean changedAccess = (this.snapshot.header() == null) != (payload.header() == null);
        this.snapshot = payload;
        this.desiredIndex = payload.index();
        if (changedAccess) {
            rebuildWidgets();
        } else {
            populateRows();
            updateActions();
        }
    }

    public void result(SettlementManagementActionResultPayload payload) {
        if (this.snapshot.header() == null || !this.snapshot.header().id().equals(payload.town())) {
            return;
        }
        this.submitting = false;
        this.feedback = text(payload.applied() ? "applied" : "not_applied");
        this.feedbackUntil = Util.getMillis() + 4_000L;
        this.refreshAt = Util.getMillis();
        updateActions();
    }

    public void submit(SettlementManagementActionPayload.Command command) {
        if (this.submitting || this.snapshot.header() == null) {
            return;
        }
        this.submitting = true;
        this.feedback = text("saving");
        PacketDistributor.sendToServer(new SettlementManagementActionPayload(this.snapshot.header().id(), command));
        updateActions();
    }

    public boolean permits(SettlementPermission permission) {
        return this.snapshot.header() != null && this.snapshot.header().permissions().contains(permission);
    }

    public boolean leader() {
        return this.snapshot.header() != null && this.minecraft != null && this.minecraft.player != null
                && this.snapshot.header().leader().equals(this.minecraft.player.getUUID());
    }

    public boolean submitting() {
        return this.submitting;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(700, this.width - 24);
        this.panelHeight = Math.min(420, this.height - 24);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.contentLeft = this.left + 102;
        this.contentWidth = this.panelWidth - 102;
        int y = this.top + 28;
        for (Page page : Page.values()) {
            if (page != Page.TOWNS && this.snapshot.header() == null || this.assigning != null && page != Page.AREAS) {
                continue;
            }
            Button button = addRenderableWidget(Button.builder(enumText("page", page), ignored -> navigate(page, 0, ""))
                    .bounds(this.left, y, 94, 20).build());
            button.active = page != this.desiredPage;
            y += 22;
        }
        addRenderableWidget(Button.builder(Component.translatable(this.parent == null ? "gui.done" : "gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 20, 94, 20).build());
        this.search = new EditBox(this.font, this.contentLeft, this.top + 30, this.contentWidth, 20, text("search"));
        this.search.setMaxLength(64);
        this.search.setHint(text("search"));
        this.search.setValue(this.query);
        this.search.visible = this.desiredPage != Page.SUMMARY;
        this.search.active = this.search.visible;
        this.search.setResponder(value -> {
            this.query = value;
            this.desiredIndex = 0;
            this.refreshAt = Util.getMillis() + 350L;
            updateActions();
        });
        addRenderableWidget(this.search);
        this.rows = addRenderableWidget(new Rows(this.contentLeft, this.top + 56, this.contentWidth, Math.max(36, this.panelHeight - 112)));
        int bottom = this.top + this.panelHeight;
        this.previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> navigate(this.desiredPage, this.desiredIndex - 1, this.query))
                .bounds(this.contentLeft, bottom - 46, 24, 20).build());
        this.next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> navigate(this.desiredPage, this.desiredIndex + 1, this.query))
                .bounds(this.contentLeft + 28, bottom - 46, 24, 20).build());
        addRenderableWidget(Button.builder(text("refresh"), ignored -> {
            this.refreshAt = Util.getMillis();
        }).bounds(this.contentLeft + this.contentWidth - 64, bottom - 46, 64, 20).build());
        int actionWidth = (this.contentWidth - 8) / 3;
        for (int index = 0; index < this.actions.length; index++) {
            int action = index;
            this.actions[index] = addRenderableWidget(Button.builder(Component.empty(), ignored -> perform(action))
                    .bounds(this.contentLeft + (actionWidth + 4) * index, bottom - 20, actionWidth, 20).build());
        }
        populateRows();
        updateActions();
    }

    private boolean currentPage() {
        return this.snapshot.page() == this.desiredPage && this.snapshot.index() == this.desiredIndex
                && this.snapshot.query().equals(this.query);
    }

    private void populateRows() {
        double scroll = this.rows.getScrollAmount();
        this.rows.reset();
        if (!currentPage()) {
            this.rows.add(null, text("loading"), Component.empty());
        } else if (this.desiredPage == Page.SUMMARY) {
            addSummary();
        } else if (this.snapshot.entries().isEmpty()) {
            this.rows.add(null, text(this.desiredPage == Page.TOWNS ? "no_towns" : "empty"), Component.empty());
        } else {
            for (Entry entry : this.snapshot.entries()) {
                this.rows.add(entry, name(entry), detail(entry));
            }
        }
        this.rows.setClampedScrollAmount(scroll);
        this.previous.active = currentPage() && this.desiredIndex > 0;
        this.next.active = currentPage() && (long) (this.desiredIndex + 1)
                * SettlementManagementRequestPayload.PAGE_SIZE < this.snapshot.total();
    }

    private void addSummary() {
        var header = this.snapshot.header();
        if (header == null) {
            return;
        }
        this.rows.add(null, text("population", header.population(), header.housing()), text("housing_hint"));
        this.rows.add(null, text("food", decimal(header.foodDays())), text("food_hint"));
        this.rows.add(null, text("stage", enumText("stage", header.stage())), text("bounds", header.stage().radius()));
        this.rows.add(null, text("support", decimal(header.supportTicks() / 24_000.0D), decimal(header.requiredTicks() / 24_000.0D)),
                text(header.ready() ? "ready" : "support_hint"));
        if (header.shortageTicks() > 0L) {
            this.rows.add(null, text("shortage", decimal(header.shortageTicks() / 24_000.0D)), text("shortage_hint"));
        }
        for (var missing : header.missing()) {
            this.rows.add(null, enumText("requirement", missing), text("needed"));
        }
        this.rows.add(null, text("treasury_balance", header.balance().mixedFraction()), text("treasury_limits",
                header.reserve().mixedFraction(), header.spent().mixedFraction(), header.budget().mixedFraction()));
    }

    private void navigate(Page page, int index, String query) {
        this.desiredPage = page;
        this.desiredIndex = Math.max(0, index);
        this.query = query;
        this.selected = null;
        this.feedback = Component.empty();
        this.refreshAt = Util.getMillis();
        rebuildWidgets();
    }

    private Entry selectedEntry() {
        return this.snapshot.entries().stream().filter(entry -> entry.id().equals(this.selected)).findFirst().orElse(null);
    }

    private void updateActions() {
        if (this.actions[0] == null) {
            return;
        }
        for (Button action : this.actions) {
            action.visible = false;
            action.active = false;
        }
        Entry selected = selectedEntry();
        if (this.assigning != null) {
            boolean suitable = selected instanceof SettlementManagementPayload.Area area
                    && area.kind().role() != null && this.assigning.roles().contains(area.kind().role());
            action(0, "assign", suitable && permits(SettlementPermission.STAFF));
            return;
        }
        switch (this.desiredPage) {
            case TOWNS -> action(0, "open", selected != null);
            case SUMMARY -> {
                action(0, "advance", leader() && this.snapshot.header() != null && this.snapshot.header().ready());
                action(1, "treasury", permits(SettlementPermission.TREASURY));
                action(2, "budget", permits(SettlementPermission.TREASURY));
            }
            case RESIDENTS -> {
                action(0, "assign", permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Resident resident
                        && !resident.baby() && !resident.roles().isEmpty());
                action(1, "release", leader() && selected instanceof SettlementManagementPayload.Resident resident && resident.area() != null);
            }
            case AREAS -> {
                action(0, "add", permits(SettlementPermission.BUILD));
                action(1, "edit", selected != null && permits(SettlementPermission.BUILD));
                action(2, "remove", selected != null && permits(SettlementPermission.BUILD));
            }
            case DEPOTS -> {
                action(0, "edit", selected != null && permits(SettlementPermission.LOGISTICS));
                action(1, "stock_target", selected != null && permits(SettlementPermission.LOGISTICS));
            }
            case APPLICANTS -> {
                action(0, "approve", permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Applicant applicant && !applicant.approved());
                action(1, "decline", selected != null && permits(SettlementPermission.STAFF));
            }
            case TRADE -> {
                action(0, "add", permits(SettlementPermission.TRADE));
                action(1, "edit", selected != null && permits(SettlementPermission.TRADE));
                action(2, "remove", selected != null && permits(SettlementPermission.TRADE));
            }
            case DELEGATES -> {
                action(0, "add", leader());
                action(1, "edit", selected != null && leader());
                action(2, "transfer", selected != null && leader());
            }
        }
    }

    private void action(int index, String key, boolean active) {
        Button action = this.actions[index];
        action.visible = true;
        action.active = active && !this.submitting && currentPage();
        action.setMessage(text(key));
        action.setTooltip(Tooltip.create(text(key + "_hint")));
    }

    private void perform(int action) {
        Entry selected = selectedEntry();
        if (this.assigning != null && selected instanceof SettlementManagementPayload.Area area) {
            this.minecraft.setScreen(this.parent);
            this.parent.submit(new SettlementManagementActionPayload.Assign(this.assigning.id(), area.id()));
            return;
        }
        switch (this.desiredPage) {
            case TOWNS -> {
                if (selected != null) {
                    PacketDistributor.sendToServer(new SettlementManagementRequestPayload(selected.id(), Page.SUMMARY, 0, "", 0, true));
                }
            }
            case SUMMARY -> {
                if (action == 0) {
                    submit(new SettlementManagementActionPayload.Advance());
                } else {
                    edit(action == 1 ? SettlementEditScreen.Mode.TREASURY : SettlementEditScreen.Mode.BUDGET, null);
                }
            }
            case RESIDENTS -> {
                if (selected instanceof SettlementManagementPayload.Resident resident) {
                    if (action == 0) {
                        this.minecraft.setScreen(new SettlementManagementScreen(this.snapshot, this, resident));
                    } else {
                        submit(new SettlementManagementActionPayload.Release(resident.id()));
                    }
                }
            }
            case AREAS -> {
                if (action == 2 && selected != null) {
                    confirm("remove_area", selected, () -> submit(new SettlementManagementActionPayload.RemoveArea(selected.id())));
                } else {
                    edit(SettlementEditScreen.Mode.AREA, action == 0 ? null : selected);
                }
            }
            case DEPOTS -> edit(action == 0 ? SettlementEditScreen.Mode.DEPOT : SettlementEditScreen.Mode.STOCK, selected);
            case APPLICANTS -> {
                if (selected != null) {
                    submit(new SettlementManagementActionPayload.Applicant(selected.id(), action == 0));
                }
            }
            case TRADE -> {
                if (action == 2 && selected != null) {
                    submit(new SettlementManagementActionPayload.RemoveOrder(selected.id()));
                } else {
                    edit(SettlementEditScreen.Mode.ORDER, action == 0 ? null : selected);
                }
            }
            case DELEGATES -> {
                if (action == 2 && selected != null) {
                    confirm("transfer_leadership", selected, () -> submit(new SettlementManagementActionPayload.Transfer(selected.id())));
                } else {
                    edit(SettlementEditScreen.Mode.DELEGATE, action == 0 ? null : selected);
                }
            }
        }
    }

    private void edit(SettlementEditScreen.Mode mode, Entry selected) {
        this.minecraft.setScreen(new SettlementEditScreen(this, mode, selected));
    }

    private void confirm(String message, Entry entry, Runnable accepted) {
        this.minecraft.setScreen(new ConfirmScreen(result -> {
            this.minecraft.setScreen(this);
            if (result) {
                accepted.run();
            }
        }, text(message), text(message + "_hint", name(entry))) {
            @Override
            public void tick() {
                if (this.minecraft.player == null || !this.minecraft.player.isAlive() || this.minecraft.player.hurtTime > 0) {
                    this.minecraft.setScreen(null);
                }
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }
        });
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (!this.submitting && now >= this.feedbackUntil) {
            this.feedback = Component.empty();
        }
        if (now >= this.refreshAt && now >= this.nextRequestAt && this.minecraft != null && this.minecraft.player != null) {
            this.nextRequestAt = now + 300L;
            this.refreshAt = now + 2_000L;
            UUID town = this.desiredPage == Page.TOWNS || this.snapshot.header() == null ? null : this.snapshot.header().id();
            PacketDistributor.sendToServer(new SettlementManagementRequestPayload(town, this.desiredPage,
                    this.desiredIndex, this.query, ++this.requestId, false));
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER && this.rows.isFocused() && this.actions[0].active) {
            perform(0);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + this.panelHeight + 8, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component heading = this.assigning != null ? text("assign_resident", name(this.assigning))
                : this.snapshot.header() == null ? this.title : this.snapshot.header().name().isBlank()
                    ? text("unnamed") : Component.literal(this.snapshot.header().name());
        fitted(graphics, heading, this.left, this.top, this.panelWidth, 0xFFFFFF);
        if (this.desiredPage == Page.SUMMARY && this.snapshot.header() != null) {
            fitted(graphics, text("summary_hint"), this.contentLeft, this.top + 34, this.contentWidth, 0xD0D0D0);
        }
        int pages = Math.max(1, (this.snapshot.total() + SettlementManagementRequestPayload.PAGE_SIZE - 1) / SettlementManagementRequestPayload.PAGE_SIZE);
        fitted(graphics, this.feedback.getString().isEmpty() ? text("page_count", this.desiredIndex + 1, pages) : this.feedback,
                this.contentLeft + 60, this.top + this.panelHeight - 40, this.contentWidth - 132, 0xD0D0D0);
    }

    private void fitted(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
        String text = value.getString();
        if (this.font.width(text) > width) {
            text = this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
        }
        graphics.drawString(this.font, text, x, y, color);
    }

    public static MutableComponent text(String key, Object... arguments) {
        return Component.translatable("villagerretaliation.gui.settlement." + key, arguments);
    }

    public static MutableComponent enumText(String category, Enum<?> value) {
        return text(category + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component name(Entry entry) {
        return entry.name().isBlank() ? text(entry instanceof SettlementManagementPayload.Town ? "unnamed" : "unnamed_resident")
                : Component.literal(entry.name());
    }

    private static Component profession(net.minecraft.resources.ResourceLocation id) {
        return VillagerProfessionUtil.parse(id.toString()).<Component>map(profession -> Component.translatableWithFallback(
                VillagerProfessionUtil.translationKey(profession, "entity.minecraft.villager.none"),
                VillagerProfessionUtil.displayName(profession, text("unemployed").getString()))).orElse(text("unemployed"));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component detail(Entry entry) {
        return switch (entry) {
            case SettlementManagementPayload.Town town -> text("town_row", enumText("stage", town.stage()), town.population());
            case SettlementManagementPayload.Resident resident -> text("resident_row",
                    resident.baby() ? text("child") : profession(resident.profession()),
                    enumText("presence", resident.presence()), Component.translatableWithFallback("villagerretaliation.gui.settlement.work." + resident.status(), text("work.idle").getString()));
            case SettlementManagementPayload.Area area -> text("area_row", enumText("area", area.kind()), area.workers(),
                    area.kind() == com.jvn.villagerretaliation.settlement.SettlementAreaType.HOME ? text("beds", area.beds()) : text("fixtures", area.fixtures()));
            case SettlementManagementPayload.Depot depot -> text("depot_row", enumText("depot", depot.purpose()), depot.usedSlots(), depot.items());
            case SettlementManagementPayload.Applicant applicant -> text("applicant_row",
                    profession(applicant.profession()),
                    text(applicant.approved() ? "approved" : "awaiting_approval"));
            case SettlementManagementPayload.Order order -> text("order_row", enumText("direction", order.direction()), order.target(),
                    text(order.enabled() ? "enabled" : "disabled"));
            case SettlementManagementPayload.Delegate delegate -> Component.literal(String.join(", ",
                    delegate.permissions().stream().map(permission -> enumText("permission", permission).getString()).toList()));
        };
    }

    private final class Rows extends ObjectSelectionList<Row> {
        private Rows(int x, int y, int width, int height) {
            super(SettlementManagementScreen.this.minecraft, width, height, y, 32);
            setX(x);
            this.centerListVertically = false;
        }

        private void reset() {
            clearEntries();
        }

        private void add(SettlementManagementPayload.Entry entry, Component primary, Component secondary) {
            Row row = new Row(entry, primary, secondary);
            addEntry(row);
            if (entry != null && entry.id().equals(SettlementManagementScreen.this.selected)) {
                setSelected(row);
            }
        }

        @Override
        public void setSelected(Row row) {
            super.setSelected(row);
            if (row != null && row.entry != null) {
                SettlementManagementScreen.this.selected = row.entry.id();
                updateActions();
            }
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - 6;
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics graphics) {
        }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final Entry entry;
        private final Component primary;
        private final Component secondary;

        private Row(Entry entry, Component primary, Component secondary) {
            this.entry = entry;
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public Component getNarration() {
            return this.primary.copy().append(". ").append(this.secondary);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.entry != null) {
                rows.setSelected(this);
                return true;
            }
            return false;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            fitted(graphics, this.primary, x + 3, y + 3, width - 8, 0xFFFFFF);
            fitted(graphics, this.secondary, x + 3, y + 15, width - 8, 0xD0D0D0);
            if (hovered) {
                List<Component> lines = new ArrayList<>(List.of(this.primary, this.secondary));
                if (this.entry instanceof SettlementManagementPayload.Resident resident) {
                    lines.add(text("resident_care", resident.food() < 0 ? "?" : resident.food(),
                            resident.health() < 0 ? "?" : decimal(resident.health())));
                } else if (this.entry instanceof SettlementManagementPayload.Depot depot) {
                    depot.targets().forEach(target -> lines.add(text("target_row", target.name(), target.count())));
                } else if (this.entry instanceof SettlementManagementPayload.Order order) {
                    lines.add(text("order_limits", order.price().mixedFraction(), order.budget().mixedFraction()));
                    lines.add(text("trade_status." + order.result().toLowerCase(Locale.ROOT)));
                }
                setTooltipForNextRenderPass(lines.stream().flatMap(line -> font.split(line, Math.min(300, SettlementManagementScreen.this.width - 24)).stream()).toList());
            }
        }
    }
}
