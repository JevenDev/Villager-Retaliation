package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementStorageLinks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class SettlementStorageScreen extends Screen {
    private final SettlementManagementScreen parent;
    private final SettlementViewScreen view;
    private final UUID scope;
    private final UUID area;
    private final UUID resident;
    private final boolean canInherit;
    private final List<Button> rows = new ArrayList<>();
    private final List<Button> edits = new ArrayList<>();
    private SettlementManagementPayload.Storage snapshot;
    private SettlementManagementPayload.Storage expected;
    private SettlementStorageLinks draft;
    private boolean reload = true;
    private boolean reset;
    private boolean submitted;
    private boolean uncertain;
    private boolean filterSubmission;
    private int selected = -1;
    private int offset;
    private int frameIndex;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private Component feedback = Component.empty();
    private Button save;
    private Button previous;
    private Button next;
    private Button filters;
    private Button matcher;
    private final List<Button> policyButtons = new ArrayList<>();

    public SettlementStorageScreen(SettlementManagementScreen parent, SettlementViewScreen view,
                                   SettlementManagementPayload.Area area, SettlementManagementPayload.Resident resident) {
        super(Component.literal("Storage: " + (resident == null ? area.name() + " worksite defaults" : resident.name() + " worker")));
        this.parent = parent;
        this.view = view;
        this.area = area.id();
        this.canInherit = area.plan() != null;
        this.resident = resident == null ? null : resident.id();
        this.scope = resident == null ? area.id() : resident.id();
        request();
    }

    SettlementManagementScreen parent() { return this.parent; }
    SettlementStorageLinks draft() { return this.draft; }

    void request() { this.parent.navigate(Page.STORAGE, 0, this.scope.toString()); }

    void accept(SettlementManagementPayload payload) {
        if (payload.page() != Page.STORAGE || !payload.query().equals(this.scope.toString()) || this.parent.snapshot() != payload) return;
        var updated = payload.entries().stream().filter(SettlementManagementPayload.Storage.class::isInstance)
                .map(SettlementManagementPayload.Storage.class::cast).findFirst().orElse(null);
        if (updated == null || !updated.area().equals(this.area) || !java.util.Objects.equals(updated.resident(), this.resident)) {
            this.feedback = Component.literal("This assignment is no longer available. Close and select it again.");
            this.snapshot = null;
            return;
        }
        if (this.reload || this.snapshot == null) {
            this.uncertain = false;
            this.expected = updated;
            this.draft = updated.links();
            this.reset = false;
            this.reload = false;
            this.selected = -1;
            this.offset = 0;
        }
        this.snapshot = updated;
        update();
    }

    @Override
    protected void init() {
        var layout = SettlementViewClient.panelLayout(this.width, this.height, Math.min(610, this.width - 24),
                Math.min(430, this.height - 20), true);
        this.panelWidth = layout.width();
        this.panelHeight = layout.height();
        this.left = layout.left();
        this.top = layout.top();
        this.rows.clear();
        this.edits.clear();
        this.policyButtons.clear();
        int count = Math.max(1, (this.panelHeight - 204) / 26);
        for (int index = 0; index < count; index++) {
            int row = index;
            this.rows.add(button("", this.left, this.top + 44 + index * 26, this.panelWidth, () -> {
                this.selected = this.offset + row;
                this.frameIndex = 0;
                update();
            }));
        }
        int bottom = this.top + this.panelHeight;
        this.previous = button("<", this.left, bottom - 152, 24, () -> { this.offset = Math.max(0, this.offset - this.rows.size()); update(); });
        this.next = button(">", this.left + 28, bottom - 152, 24, () -> { this.offset += this.rows.size(); update(); });
        int width = (this.panelWidth - 12) / 4;
        this.edits.add(button("Add on map", this.left, bottom - 126, width, () -> this.view.pickStorage(this)));
        this.edits.add(button("Purpose", this.left + width + 4, bottom - 126, width, this::cyclePurpose));
        this.edits.add(button("Priority +", this.left + (width + 4) * 2, bottom - 126, width, () -> priority(-1)));
        this.edits.add(button("Priority -", this.left + (width + 4) * 3, bottom - 126, width, () -> priority(1)));
        this.edits.add(button("Remove", this.left, bottom - 102, width, () -> {
            var entries = editable();
            if (this.selected >= 0 && this.selected < entries.size()) entries.remove(this.selected);
            change(entries);
        }));
        this.edits.add(button("Clear all", this.left + width + 4, bottom - 102, width, () -> change(List.of())));
        this.edits.add(button("Automatic depots", this.left + (width + 4) * 2, bottom - 102, width, () -> {
            this.draft = SettlementStorageLinks.AUTOMATIC;
            this.reset = false;
            this.selected = -1;
            update();
        }));
        this.edits.add(button(this.resident == null ? "Reload" : this.canInherit ? "Use worksite" : "Automatic", this.left + (width + 4) * 3, bottom - 102, width, () -> {
            if (this.resident == null) reload();
            else { this.reset = true; this.feedback = Component.literal(this.canInherit ? "Save resets only storage to the worksite defaults." : "Save restores automatic depot assignments."); update(); }
        }));
        this.filters = button("Framed filters", this.left + 60, bottom - 152, this.panelWidth - 158, () -> { this.frameIndex++; update(); });
        this.matcher = button("Edit contents", this.left + this.panelWidth - 94, bottom - 152, 94, () -> {
            var filter = selectedFilter();
            if (filter != null && !this.parent.submitting()) this.minecraft.setScreen(new SettlementStorageFilterScreen(this, this.scope, selectedStatus().link().position(), filter));
        });
        String[] labels = { "Direction", "Allow / deny", "Any / all", "Stock -", "Stock +" };
        int filterWidth = (this.panelWidth - 16) / 5;
        for (int index = 0; index < labels.length; index++) {
            int action = index;
            this.policyButtons.add(button(labels[index], this.left + index * (filterWidth + 4), bottom - 78, filterWidth, () -> policy(action)));
        }
        button("Cancel / Back", this.left, bottom - 20, width, this::onClose);
        button("Reload saved", this.left + width + 4, bottom - 20, width, this::reload)
                .setTooltip(Tooltip.create(Component.literal("Discard the draft and reload the latest assignment.")));
        this.save = button("Save storage", this.left + this.panelWidth - width, bottom - 20, width, this::save);
        update();
    }

    private void fit(Button button, String label) {
        boolean clipped = this.font.width(label) > button.getWidth() - 8;
        button.setMessage(Component.literal(clipped ? this.font.plainSubstrByWidth(label, button.getWidth() - 20) + "..." : label));
        button.setTooltip(Tooltip.create(Component.literal(label)));
    }

    private Button button(String text, int x, int y, int width, Runnable action) {
        var button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run()).bounds(x, y, width, 20).build());
        fit(button, text);
        return button;
    }

    private List<SettlementStorageLinks.Link> displayed() {
        if (this.draft == null) return List.of();
        return this.draft.automatic() ? this.snapshot.containers().stream().map(SettlementManagementPayload.StorageStatus::link).toList() : this.draft.entries();
    }

    private ArrayList<SettlementStorageLinks.Link> editable() { return new ArrayList<>(displayed()); }

    private void change(List<SettlementStorageLinks.Link> entries) {
        this.draft = new SettlementStorageLinks(false, entries);
        this.reset = false;
        this.feedback = Component.literal("Draft only. Save applies these links to " + (this.resident == null ? "workers using the worksite defaults." : "this worker."));
        update();
    }

    void picked(BlockPos position) {
        var entries = editable();
        if (entries.size() >= SettlementStorageLinks.MAX_LINKS) this.feedback = Component.literal("Storage link limit reached.");
        else if (entries.stream().anyMatch(link -> link.position().equals(position) && link.purpose() == SettlementStorageLinks.Purpose.GENERAL)) {
            this.feedback = Component.literal("This container already has a General link.");
        } else {
            entries.add(new SettlementStorageLinks.Link(position, SettlementStorageLinks.Purpose.GENERAL, 5));
            change(entries);
            this.selected = entries.size() - 1;
            this.offset = this.selected / this.rows.size() * this.rows.size();
        }
        update();
    }

    private void cyclePurpose() {
        var entries = editable();
        if (this.selected < 0 || this.selected >= entries.size()) return;
        var current = entries.get(this.selected);
        var purpose = SettlementStorageLinks.Purpose.values()[(current.purpose().ordinal() + 1) % 3];
        if (entries.stream().anyMatch(link -> link.position().equals(current.position()) && link.purpose() == purpose)) {
            this.feedback = Component.literal("That purpose is already linked at this position. Remove the duplicate first.");
            return;
        }
        entries.set(this.selected, new SettlementStorageLinks.Link(current.position(), purpose, current.priority()));
        change(entries);
    }

    private void priority(int amount) {
        var entries = editable();
        if (this.selected < 0 || this.selected >= entries.size()) return;
        var link = entries.get(this.selected);
        entries.set(this.selected, new SettlementStorageLinks.Link(link.position(), link.purpose(), Math.clamp(link.priority() + amount, 0, 9)));
        change(entries);
    }

    private SettlementManagementPayload.StorageStatus status(SettlementStorageLinks.Link link) {
        return this.snapshot.containers().stream().filter(row -> row.link().position().equals(link.position()) && row.link().purpose() == link.purpose()).findFirst().orElse(null);
    }

    private SettlementManagementPayload.StorageStatus selectedStatus() {
        var links = displayed();
        return this.selected < 0 || this.selected >= links.size() ? null : status(links.get(this.selected));
    }

    private SettlementManagementPayload.StorageFilter selectedFilter() {
        var status = selectedStatus();
        return status == null || status.filters().isEmpty() ? null : status.filters().get(Math.floorMod(this.frameIndex, status.filters().size()));
    }

    private void policy(int action) {
        var filter = selectedFilter();
        if (filter == null || this.parent.submitting()) return;
        var policy = VillagerFilterPolicy.read(filter.item());
        var field = switch (action) {
            case 0 -> VillagerFilterPolicy.PolicyField.DIRECTION;
            case 1 -> VillagerFilterPolicy.PolicyField.LIST_MODE;
            case 2 -> VillagerFilterPolicy.PolicyField.COMBINATION;
            default -> VillagerFilterPolicy.PolicyField.STOCK_DELTA;
        };
        int value = switch (action) {
            case 0 -> (policy.direction().networkId() + 1) % 3;
            case 1 -> 1 - policy.listMode().networkId();
            case 2 -> policy.combinationMode() == VillagerFilterPolicy.CombinationMode.MATCH_ALL ? 0 : 1;
            case 3 -> -1;
            default -> 1;
        };
        this.submitted = true;
        this.filterSubmission = true;
        this.parent.submit(new SettlementManagementActionPayload.StorageFilter(this.scope, selectedStatus().link().position(), filter.frame(), filter.item(), field, value));
        this.feedback = Component.literal("Updating the physical framed filter for every container user...");
        update();
    }

    private void save() {
        if (this.snapshot == null || this.uncertain || this.parent.submitting()) return;
        this.submitted = true;
        this.filterSubmission = false;
        this.parent.submit(new SettlementManagementActionPayload.SaveStorage(this.area, this.resident, this.expected.override(), this.expected.links(), this.reset ? null : this.draft));
        this.feedback = Component.literal("Saving storage...");
        update();
    }

    void result(SettlementManagementActionResultPayload payload) {
        this.submitted = false;
        this.feedback = SettlementManagementScreen.resultMessage(payload);
        if (payload.applied() && !this.filterSubmission) this.reload = true;
        request();
        update();
    }

    private void reload() {
        if (this.parent.submitting()) return;
        this.reload = true;
        this.feedback = Component.literal("Reloading saved storage...");
        request();
    }

    private void update() {
        if (this.save == null) return;
        var links = this.snapshot == null ? List.<SettlementStorageLinks.Link>of() : displayed();
        this.offset = Math.min(this.offset, Math.max(0, (links.size() - 1) / this.rows.size() * this.rows.size()));
        boolean allowed = this.snapshot != null && !this.reload && !this.parent.submitting()
                && this.parent.permits(SettlementPermission.STAFF) && this.parent.permits(SettlementPermission.LOGISTICS);
        for (int index = 0; index < this.rows.size(); index++) {
            int position = this.offset + index;
            var button = this.rows.get(index);
            button.visible = position < links.size();
            if (!button.visible) continue;
            var link = links.get(position);
            var status = status(link);
            String description = (status == null ? "Container" : status.name()) + " " + link.position().toShortString() + " | "
                    + link.purpose().label() + " | priority " + link.priority() + " | " + (status == null ? "Validate on save" : status.status().replace('_', ' '));
            button.setMessage(Component.literal(this.font.plainSubstrByWidth((position == this.selected ? "> " : "") + description, this.panelWidth - 12)));
            button.setTooltip(Tooltip.create(Component.literal(description + "\nPriority 0 is highest. Live workers still check access, filters and reachability.")));
            button.active = !this.parent.submitting();
        }
        this.previous.active = this.offset > 0;
        this.next.active = this.offset + this.rows.size() < links.size();
        this.edits.forEach(button -> button.active = allowed);
        this.edits.getFirst().active &= this.view != null;
        boolean selection = this.selected >= 0 && this.selected < links.size();
        for (int index : new int[] {1, 2, 3, 4}) this.edits.get(index).active &= selection;
        this.save.active = allowed && !this.uncertain;
        var filter = this.snapshot == null ? null : selectedFilter();
        this.filters.active = filter != null;
        fit(this.filters, filter == null ? "No loaded framed filter at the selected container" :
                "Filter " + (Math.floorMod(this.frameIndex, selectedStatus().filters().size()) + 1) + ": " + filter.item().getHoverName().getString());
        this.filters.setTooltip(Tooltip.create(Component.literal("Cycles physical framed filters. Policy changes below apply immediately to every user of this container. Edit contents opens a separate draft.")));
        this.matcher.active = allowed && filter != null && com.jvn.villagerretaliation.item.VillagerRetaliationItems.isFilter(filter.item());
        var policy = filter == null ? null : VillagerFilterPolicy.read(filter.item());
        for (var button : this.policyButtons) button.active = allowed && policy != null && policy.valid();
        if (policy != null && policy.valid()) {
            fit(this.policyButtons.get(0), policy.direction().id());
            fit(this.policyButtons.get(1), policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING ? "Allow matching" : "Deny matching");
            fit(this.policyButtons.get(2), policy.combinationMode().id().replace('_', ' '));
            fit(this.policyButtons.get(3), "Stock " + policy.stockTarget().orElse(0) + " -");
            this.policyButtons.get(3).active &= policy.stockTarget().orElse(0) > 0 && policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING;
            this.policyButtons.get(4).active &= policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING;
        }
    }

    @Override
    public void tick() {
        if (this.submitted && !this.parent.submitting()) {
            this.submitted = false;
            this.uncertain = true;
            this.feedback = Component.literal("Outcome unconfirmed. Reload saved storage before resubmitting.");
            request();
        }
        update();
    }

    @Override
    public void onClose() {
        if (!this.parent.submitting() && !SettlementViewClient.resume(this.parent)) this.minecraft.setScreen(this.parent);
    }
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!SettlementViewClient.renderPanelBackground(graphics, this.left, this.top, this.panelWidth, this.panelHeight)) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + this.panelHeight + 8, 0xE0101010);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF);
        String scope = this.snapshot == null ? "Loading saved storage..." : this.reset ? "Pending: inherit worksite storage" :
                (this.resident == null ? "Worksite defaults" : this.snapshot.override() ? "Worker override" : "Inherited from worksite")
                        + " | " + (this.draft.automatic() ? "Automatic depots" : this.draft.entries().size() + " explicit links");
        graphics.drawString(this.font, scope, this.left, this.top + 16, 0xBBBBBB);
        SettlementManagementScreen.renderFeedback(graphics, this.font, this.feedback, this.left,
                this.top + this.panelHeight - 52, this.panelWidth, mouseX, mouseY);
    }
}
