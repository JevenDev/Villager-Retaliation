package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.inventory.ProductionClientState;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.ProductionManagementService;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import com.jvn.villagerretaliation.network.ProductionTarget;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.*;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementWorksitePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class SettlementSelectionInspector {
    enum Section { OVERVIEW, STAFF, ORDERS, SETTINGS, STORAGE }

    private final Consumer<Section> action;
    private final Runnable browse;
    private final List<Button> tabs = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Consumer<UUID> selectResident;
    private Header header;
    private Entry selected;
    private Area worksite;
    private List<Resident> staff = List.of();
    private List<Entry> depots = List.of();
    private List<Entry> trades = List.of();
    private List<UUID> key = List.of();
    private Section section = Section.OVERVIEW;
    private ProductionClientState production;
    private com.jvn.villagerretaliation.client.inventory.TransferClientState transfers;
    private Button primary;
    private Button back;
    private int left;
    private int width;
    private int height;
    private int scroll;
    private int totalLines;
    private int visibleLines;
    private boolean suppressed;
    private boolean visible;

    SettlementSelectionInspector(Consumer<Section> action, Runnable browse, Consumer<UUID> selectResident) {
        this.action = action;
        this.browse = browse;
        this.selectResident = selectResident;
    }

    void init(int left, int width, int height, Consumer<AbstractWidget> add) {
        this.left = left + 8;
        this.width = width - 16;
        this.height = height;
        this.tabs.clear();
        for (var section : Section.values()) {
            int index = section.ordinal();
            int count = index < 2 ? 2 : 3;
            int column = index < 2 ? index : index - 2;
            int buttonWidth = (this.width - (count - 1) * 2) / count;
            String label = section.name().charAt(0) + section.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            var button = Button.builder(Component.literal(label), ignored -> {
                this.section = section;
                this.scroll = 0;
                update();
            }).bounds(this.left + column * (buttonWidth + 2), index < 2 ? 24 : 46, buttonWidth, 18).build();
            this.tabs.add(button);
            add.accept(button);
        }
        this.primary = Button.builder(Component.empty(), ignored -> this.action.accept(this.section))
                .bounds(this.left, height - 114, this.width, 18).build();
        add.accept(this.primary);
        this.back = Button.builder(Component.literal("Browse settlement"), ignored -> {
            this.suppressed = true;
            this.browse.run();
        }).bounds(this.left, height - 92, this.width, 18).build();
        add.accept(this.back);
    }

    boolean visible() { return this.visible; }
    Section section() { return this.section; }
    ProductionClientState production() { return this.production; }
    com.jvn.villagerretaliation.client.inventory.TransferClientState transfers() { return this.transfers; }
    boolean sharedWorksite() { return this.worksite != null && this.worksite.plan() != null; }
    void reveal() { this.suppressed = false; }

    void update(Header header, Entry selected, Area worksite, List<Resident> staff,
                List<Entry> depots, List<Entry> trades, boolean sidebar, boolean busy) {
        var key = selected == null ? staff.stream().map(Resident::id).toList() : List.of(selected.id());
        if (!key.equals(this.key)) {
            this.key = key;
            this.section = Section.OVERVIEW;
            this.scroll = 0;
            this.suppressed = false;
        }
        this.header = header;
        this.selected = selected;
        this.worksite = worksite;
        this.staff = staff;
        this.depots = depots;
        this.trades = trades;
        this.visible = sidebar && !this.suppressed && !key.isEmpty();
        var target = worksite != null && ProductionManagementService.supports(worksite.kind().role())
                ? new ProductionTarget(header.id(), worksite.id()) : null;
        if (!Objects.equals(target, this.production == null ? null : this.production.target())) {
            this.production = target == null ? null : new ProductionClientState(target);
        }
        var transferTarget = worksite != null && worksite.kind() == SettlementAreaType.DEPOT
                ? new ProductionTarget(header.id(), worksite.id()) : null;
        if (!Objects.equals(transferTarget, this.transfers == null ? null : this.transfers.target())) {
            this.transfers = transferTarget == null ? null : new com.jvn.villagerretaliation.client.inventory.TransferClientState(transferTarget);
        }
        update();
        this.primary.active &= !busy;
    }

    private boolean available(Section section) {
        return switch (section) {
            case OVERVIEW -> true;
            case STAFF -> !this.staff.isEmpty() || this.worksite != null;
            case ORDERS -> this.worksite != null || this.selected instanceof Resident;
            case SETTINGS -> this.worksite != null || this.selected instanceof Resident resident && resident.settings() != null;
            case STORAGE -> this.worksite != null;
        };
    }

    private void update() {
        if (this.primary == null || this.header == null) return;
        if (!available(this.section)) this.section = Section.OVERVIEW;
        for (var section : Section.values()) {
            var button = this.tabs.get(section.ordinal());
            button.visible = this.visible;
            button.active = available(section) && section != this.section;
        }
        this.primary.visible = this.visible;
        this.back.visible = this.visible;
        String label = switch (this.section) {
            case OVERVIEW -> "Focus selection";
            case STAFF -> this.selected instanceof Area && this.worksite != null && this.worksite.plan() != null ? "Fill staffing" : "Staff actions";
            case ORDERS -> this.transfers != null ? "Edit transfers" : this.production != null ? "Edit production" : this.worksite != null && this.worksite.kind() == SettlementAreaType.TRADING_POST
                    ? "Trade orders" : this.worksite != null && this.worksite.kind() == SettlementAreaType.FISHERY ? "Fishing settings"
                    : this.selected instanceof Resident ? "Worker actions"
                    : this.worksite != null && (this.worksite.kind() == SettlementAreaType.GUARD_POST
                    || this.worksite.kind() == SettlementAreaType.HUNTING_GROUNDS) ? "Shared route"
                    : this.worksite != null && this.worksite.kind() == SettlementAreaType.CONSTRUCTION ? "Project details"
                    : this.worksite != null && this.worksite.kind() == SettlementAreaType.DEPOT ? "Manage logistics" : "Temporary task";
            case SETTINGS -> this.selected instanceof Resident ? "Worker settings" : this.worksite != null && this.worksite.plan() == null
                    ? "Preview adoption" : "Worksite settings";
            case STORAGE -> "Storage and stations";
        };
        this.primary.setMessage(Component.literal(label));
        this.primary.active = this.section == Section.OVERVIEW || (this.production != null || this.transfers != null) && this.section == Section.ORDERS
                || this.header.permissions().contains(this.section == Section.STORAGE ? SettlementPermission.STAFF
                : this.worksite != null && this.worksite.kind() == SettlementAreaType.TRADING_POST && this.section == Section.ORDERS
                ? SettlementPermission.VIEW : SettlementPermission.STAFF);
        if (this.section == Section.SETTINGS && this.selected instanceof Resident resident && resident.settings() == null) this.primary.active = false;
        if (this.section == Section.ORDERS && this.production == null && this.worksite != null
                && this.worksite.kind() != SettlementAreaType.TRADING_POST && this.worksite.kind() != SettlementAreaType.CONSTRUCTION
                && this.worksite.kind() != SettlementAreaType.DEPOT && this.worksite.kind() != SettlementAreaType.GUARD_POST
                && this.worksite.kind() != SettlementAreaType.HUNTING_GROUNDS && this.worksite.kind() != SettlementAreaType.FISHERY && this.staff.isEmpty()) this.primary.active = false;
        this.lines.clear();
        switch (this.section) {
            case OVERVIEW -> overview();
            case STAFF -> staffing();
            case ORDERS -> orders();
            case SETTINGS -> settings();
            case STORAGE -> storage();
        }
    }

    void tick() {
        if (this.visible && this.transfers != null) {
            var request = this.transfers.poll(Util.getMillis());
            if (request != null) PacketDistributor.sendToServer(request);
        }
        if (this.visible && this.production != null) {
            var request = this.production.poll(Util.getMillis());
            if (request != null) PacketDistributor.sendToServer(request);
        }
    }

    void acceptTransfers(com.jvn.villagerretaliation.network.TransferSnapshotPayload payload) {
        if (this.transfers != null && this.transfers.accept(payload, Minecraft.getInstance().level.registryAccess())) update();
    }

    void accept(ProductionSnapshotPayload payload) {
        if (this.production != null && this.production.accept(payload, Minecraft.getInstance().level.registryAccess())) update();
    }

    private void line(String text) { this.lines.add(new Line(Component.literal(text), null)); }
    private void line(Component text) { this.lines.add(new Line(text, null)); }

    private void overview() {
        if (this.selected != null) line(SettlementManagementScreen.detail(this.selected));
        else line(this.staff.size() + " residents selected");
        if (this.worksite != null) {
            line("Home worksite: " + this.worksite.name());
            line("Staff: " + this.worksite.workers() + (this.worksite.plan() == null ? " / individual settings" : " / target " + this.worksite.plan().desiredStaff()));
            line("Priority: " + this.worksite.priority() + (this.worksite.enabled() ? " / enabled" : " / paused"));
        }
        if (this.selected instanceof Resident resident) {
            line(resident.manualStaffing() ? "Manual assignment" : "Automatic staffing eligible");
            line(resident.worksitePlan() ? resident.overrideCount() + " individual setting overrides" : "Individual worker settings");
            if (!resident.temporary().isEmpty()) line("Temporary: " + resident.temporary());
            if (!resident.diagnostic().isEmpty()) line(resident.diagnostic());
        } else if (this.selected instanceof Project project) {
            line("Materials: " + project.materials());
            if (!project.missingMaterials().isEmpty()) line("Missing: " + project.missingMaterials());
            if (!project.blocker().isEmpty()) line(project.blocker().replace('_', ' '));
        } else if (this.selected instanceof Issue issue) {
            line(issue.detail());
        }
    }

    private void staffing() {
        line(this.worksite == null ? this.staff.size() + " selected" : "Assigned: " + this.worksite.workers()
                + (this.worksite.plan() == null ? " / no shared staffing target" : " / desired: " + this.worksite.plan().desiredStaff()));
        if (this.staff.isEmpty()) line("No assigned residents");
        for (var resident : this.staff) {
            this.lines.add(new Line(SettlementManagementScreen.name(resident).copy().append(" / ")
                    .append(resident.manualStaffing() ? "manual" : "automatic"), resident.id()));
            this.lines.add(new Line(Component.literal(resident.status().replace('_', ' ') + " / " + resident.overrideCount() + " overrides"
                    + (resident.temporary().isEmpty() ? "" : " / " + resident.temporary())), resident.id()));
        }
    }

    private void orders() {
        if (this.transfers != null) {
            if (this.transfers.snapshot() == null) { line("Loading transfer orders..."); return; }
            if (!this.transfers.feedback().isEmpty()) line(this.transfers.feedback());
            var queue = this.transfers.queue();
            if (queue == null || queue.orders().isEmpty()) line("No explicit transfers. Stock replenishment continues.");
            if (queue != null) for (var order : queue.orders()) {
                line(order.sample().getHoverName().copy().append(" / transfer " + order.amount()));
                line("Delivered " + order.delivered() + " / in transit " + order.pending());
                line(order.source().position().toShortString() + " to " + order.destination().position().toShortString());
                line(order.cancelled() ? "Cancelled" : order.delivered() >= order.amount() ? "Complete" : order.paused() ? "Paused"
                        : order.blocker().isEmpty() ? "Ready" : order.blocker().replace('_', ' '));
            }
        } else if (this.production != null) {
            if (this.production.snapshot() == null) { line("Loading production orders..."); return; }
            if (!this.production.feedback().isEmpty()) line(this.production.feedback());
            var queue = this.production.queue();
            if (queue == null || queue.orders().isEmpty()) line("No shared production orders");
            if (queue != null) for (var order : queue.orders()) {
                String mode = switch (order.mode()) {
                    case BATCH -> "Batch " + order.amount();
                    case CONTINUOUS -> "Continuous";
                    case MAINTAIN_STOCK -> "Stock " + order.amount();
                };
                line(order.output().getHoverName().copy().append(" / " + mode));
                line("Made " + order.produced() + " / pending " + queue.pending(order.id()));
                if (order.mode() == HiredProductionOrder.Mode.MAINTAIN_STOCK) {
                    long stock = this.production.snapshot().stock().getOrDefault(order.id(), -1L);
                    line("Stored and deliveries: " + (stock < 0 ? "unknown" : Long.toString(stock)));
                }
                line(order.cancelled() ? "Cancelled" : order.complete() ? "Complete" : order.paused() ? "Paused"
                        : order.blocker().isEmpty() ? "Ready" : order.blocker().replace('_', ' '));
            }
            for (var legacy : this.production.snapshot().legacy()) {
                line(legacy.recipe().output(Minecraft.getInstance().level).getHoverName().copy().append(" / legacy order"));
                line((legacy.continuous() ? "Continuous" : legacy.amount() + " remaining") + " / "
                        + (legacy.activeBottles() < 0 ? "needs station observation" : legacy.activeBottles() + " active bottles"));
            }
        } else if (this.worksite != null && this.worksite.kind() == SettlementAreaType.FISHERY) {
            var settings = this.selected instanceof Resident resident ? resident.settings()
                    : this.worksite.plan() == null ? null : this.worksite.plan().settings();
            if (settings == null) line("Adopt a worksite plan to share fishing priorities.");
            else fishing(settings);
            for (var resident : this.staff) if (!resident.temporary().isEmpty()) {
                line(SettlementManagementScreen.name(resident).copy().append(": " + resident.temporary()));
            }
        } else if (this.worksite != null && this.worksite.kind() == SettlementAreaType.TRADING_POST) {
            line("Communal trade orders");
            if (this.trades.isEmpty()) line("No buy or sell orders");
            for (var entry : this.trades) {
                var order = (Order) entry;
                line(order.name() + " / " + order.direction().name().toLowerCase(java.util.Locale.ROOT) + " / target " + order.target());
                line("Price limit " + order.price().decimal(2) + " / daily budget " + order.budget().decimal(2));
                line((order.enabled() ? "Enabled" : "Paused") + " / " + order.result().replace('_', ' '));
            }
        } else {
            if (this.worksite != null && this.worksite.plan() != null && this.worksite.plan().route() != null) {
                var route = this.worksite.plan().route();
                line("Shared " + route.mode().name().toLowerCase(java.util.Locale.ROOT) + " / " + route.points().size() + " points");
            }
            if (this.selected instanceof Resident resident && resident.guardOrder() != null) {
                line("Individual " + resident.guardOrder().mode().name().toLowerCase(java.util.Locale.ROOT));
            }
            boolean tasks = false;
            for (var resident : this.staff) if (!resident.temporary().isEmpty()) {
                line(SettlementManagementScreen.name(resident).copy().append(": " + resident.temporary()));
                tasks = true;
            }
            if (!tasks) line("No temporary tasks");
            line("Temporary work retains the home assignment. Shift appends.");
        }
    }

    private void settings() {
        var settings = this.selected instanceof Resident resident ? resident.settings()
                : this.worksite == null ? null : (this.worksite.plan() == null ? SettlementWorksitePlan.defaults(this.worksite.kind().role()) : this.worksite.plan()).settings();
        if (this.selected instanceof Resident resident) line(resident.worksitePlan()
                ? resident.overrideCount() + " overrides. Other settings inherit." : "Individual settings");
        else if (this.worksite != null && this.worksite.plan() == null) {
            line("Adoption preview");
            line(this.worksite.workers() + " current workers retain their settings as overrides.");
            line("New staff inherit the following defaults. Initial staffing target: 1.");
        }
        if (settings == null) { line("Loading settings..."); return; }
        settings.options().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            var label = SettlementManagementScreen.enumText("worker", entry.getKey()).append(": ")
                    .append(SettlementWorkerScreen.value(entry.getKey(), entry.getValue()));
            if (this.selected instanceof Resident resident && resident.worksitePlan()) {
                label.append(settings.overrides(entry.getKey()) ? " (override)" : " (inherited)");
            }
            line(label);
        });
        if (!settings.logs().isEmpty()) line("Tree species: " + settings.logs());
        if (!settings.animals().isEmpty()) line("Animal species: " + settings.animals());
        if (settings.role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.FISHING) fishing(settings);
    }

    private void fishing(com.jvn.villagerretaliation.settlement.SettlementWorkerSettings settings) {
        if (this.selected instanceof Resident resident && resident.worksitePlan()) {
            line((settings.overrides() & com.jvn.villagerretaliation.settlement.SettlementWorkerSettings.FISHING_SPOTS_OVERRIDE) == 0
                    ? "Fishing spots (inherited)" : "Fishing spots (override)");
        }
        var spots = settings.fishingSpots().positions();
        if (spots.isEmpty()) line("Fishing spots: automatic discovery");
        else for (int index = 0; index < spots.size(); index++) line((index + 1) + ". " + spots.get(index).toShortString());
        line("First available spot wins. Current casts finish before changing spots.");
    }

    private void storage() {
        var plan = this.worksite.plan();
        var links = plan == null ? com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks.AUTOMATIC : plan.links();
        line(plan == null ? "Adoption preview: automatic links" : "Worksite storage links");
        line(links.supplies().isEmpty() ? "Supplies: automatic" : "Supply depots: " + links.supplies().size());
        line(links.outputs().isEmpty() ? "Outputs: automatic" : "Output depots: " + links.outputs().size());
        line(links.stations().isEmpty() ? "Stations: automatic" : "Permitted stations: " + links.stations().size());
        for (var id : java.util.stream.Stream.concat(links.supplies().stream(), links.outputs().stream()).distinct().toList()) {
            var depot = this.depots.stream().filter(entry -> entry.id().equals(id)).map(Depot.class::cast).findFirst().orElse(null);
            if (depot == null) line("Unavailable depot: " + id);
        }
        for (var entry : this.depots) {
            var depot = (Depot) entry;
            if (!depot.area().equals(this.worksite.id()) && !links.supplies().contains(depot.id()) && !links.outputs().contains(depot.id())) continue;
            line(depot.name() + " / " + depot.purpose().name().toLowerCase(java.util.Locale.ROOT));
            line(depot.position().toShortString() + " / " + depot.items() + " items / " + depot.usedSlots() + " slots");
            for (var target : depot.targets()) line(target.name() + " / stock target " + target.count());
        }
        links.stations().stream().sorted().forEach(position -> line("Station: " + position.toShortString()));
    }

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!this.visible) return;
        String name = this.selected == null ? this.staff.size() + " residents" : SettlementManagementScreen.name(this.selected).getString();
        graphics.drawString(font, font.plainSubstrByWidth(name, this.width), this.left, 70, 0xFFFFFF);
        var wrapped = new ArrayList<Wrapped>();
        for (var line : this.lines) for (var part : font.split(line.text(), this.width - 6)) wrapped.add(new Wrapped(part, line));
        this.totalLines = wrapped.size();
        this.visibleLines = Math.max(1, (this.height - 204) / (font.lineHeight + 2));
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.totalLines - this.visibleLines));
        this.hits.clear();
        for (int row = 0; row < this.visibleLines && this.scroll + row < wrapped.size(); row++) {
            int y = 84 + row * (font.lineHeight + 2);
            var part = wrapped.get(this.scroll + row);
            graphics.drawString(font, part.text(), this.left, y, part.line().resident() == null ? 0xCCCCCC : 0x99DDEE);
            this.hits.add(new Hit(y, part.line()));
        }
        if (this.totalLines > this.visibleLines) {
            int track = Math.max(12, this.visibleLines * (font.lineHeight + 2));
            int thumb = Math.max(4, track * this.visibleLines / this.totalLines);
            int y = 84 + (track - thumb) * this.scroll / Math.max(1, this.totalLines - this.visibleLines);
            graphics.fill(this.left + this.width - 2, y, this.left + this.width, y + thumb, 0xFF999999);
        }
        this.primary.setTooltip(Tooltip.create(Component.literal(this.primary.getMessage().getString())));
        if (mouseX >= this.left && mouseX < this.left + this.width) for (var hit : this.hits) {
            if (mouseY >= hit.top() && mouseY < hit.top() + font.lineHeight) {
                graphics.renderTooltip(font, hit.line().text(), mouseX, mouseY);
                break;
            }
        }
    }

    boolean click(double x, double y, int button) {
        if (!this.visible || button != 0 || x < this.left || x >= this.left + this.width) return false;
        for (var hit : this.hits) if (y >= hit.top() && y < hit.top() + 11 && hit.line().resident() != null) {
            this.selectResident.accept(hit.line().resident());
            return true;
        }
        return false;
    }

    void scroll(double amount) { this.scroll = Math.clamp(this.scroll + (amount > 0 ? -3 : 3), 0, Math.max(0, this.totalLines - this.visibleLines)); }

    private record Line(Component text, UUID resident) { }
    private record Wrapped(net.minecraft.util.FormattedCharSequence text, Line line) { }
    private record Hit(int top, Line line) { }
}
