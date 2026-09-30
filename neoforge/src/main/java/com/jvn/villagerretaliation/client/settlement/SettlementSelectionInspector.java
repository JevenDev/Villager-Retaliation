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
    private final Runnable editRoute;
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
    private Button routeEditor;
    private Button back;
    private int left;
    private int width;
    private int top;
    private int contentTop;
    private boolean compact;
    private int scroll;
    private int totalLines;
    private int visibleLines;
    private boolean suppressed;
    private boolean visible;

    SettlementSelectionInspector(Consumer<Section> action, Runnable browse, Consumer<UUID> selectResident, Runnable editRoute) {
        this.action = action;
        this.browse = browse;
        this.selectResident = selectResident;
        this.editRoute = editRoute;
    }

    void init(int left, int width, int top, int bottom, Consumer<AbstractWidget> add) {
        this.left = left + 8;
        this.width = width - 16;
        this.top = top;
        this.compact = bottom - top < 150;
        this.contentTop = top + (this.compact ? 40 : 82);
        this.tabs.clear();
        for (var section : Section.values()) {
            int index = section.ordinal();
            int count = this.compact ? 5 : index < 2 ? 2 : 3;
            int column = this.compact ? index : index < 2 ? index : index - 2;
            int buttonWidth = (this.width - (count - 1) * 2) / count;
            String label = section.name().charAt(0) + section.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            var icon = switch (section) {
                case OVERVIEW -> net.minecraft.world.item.Items.MAP;
                case STAFF -> net.minecraft.world.item.Items.VILLAGER_SPAWN_EGG;
                case ORDERS -> net.minecraft.world.item.Items.BOOK;
                case SETTINGS -> net.minecraft.world.item.Items.COMPARATOR;
                case STORAGE -> net.minecraft.world.item.Items.CHEST;
            };
            boolean showLabel = Minecraft.getInstance().font.width(label) <= buttonWidth - 6;
            var button = new SettlementViewUi.IconButton(Component.literal(label), this.left + column * (buttonWidth + 2),
                    top + (this.compact || index < 2 ? 4 : 24), buttonWidth, 18,
                    showLabel ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(icon),
                    showLabel, !showLabel && section == Section.STAFF, () -> this.section == section, () -> {
                this.section = section;
                this.scroll = 0;
                update();
            });
            this.tabs.add(button);
            add.accept(button);
        }
        this.primary = Button.builder(Component.empty(), ignored -> this.action.accept(this.section))
                .bounds(this.left, bottom - 66, this.width, 18).build();
        this.routeEditor = Button.builder(Component.literal("Edit persistent route"), ignored -> this.editRoute.run())
                .bounds(this.left, bottom - 66, this.width, 18).build();
        add.accept(this.routeEditor);
        add.accept(this.primary);
        this.back = Button.builder(Component.literal("Browse settlement"), ignored -> {
            this.suppressed = true;
            this.browse.run();
        }).bounds(this.left, bottom - 44, this.width, 18).build();
        add.accept(this.back);
    }

    boolean visible() { return this.visible; }
    Section section() { return this.section; }
    ProductionClientState production() { return this.production; }
    com.jvn.villagerretaliation.client.inventory.TransferClientState transfers() { return this.transfers; }
    boolean sharedWorksite() { return this.worksite != null && this.worksite.plan() != null; }
    void reveal() { this.suppressed = false; }
    void browse() { this.suppressed = true; }

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
        this.routeEditor.active &= !busy;
    }

    private boolean available(Section section) {
        return switch (section) {
            case OVERVIEW -> true;
            case STAFF -> !this.staff.isEmpty() || this.worksite != null || this.selected instanceof Project;
            case ORDERS -> this.worksite != null || this.selected instanceof Resident;
            case SETTINGS -> this.worksite != null || this.selected instanceof Resident resident && resident.settings() != null;
            case STORAGE -> this.worksite != null && this.worksite.kind().role() != null;
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
        this.routeEditor.visible = this.visible && this.worksite != null
                && com.jvn.villagerretaliation.settlement.SettlementGuardOrder.supports(this.worksite.kind().role());
        this.primary.setWidth(this.routeEditor.visible ? (this.width - 2) / 2 : this.width);
        this.routeEditor.setX(this.primary.getRight() + 2);
        this.routeEditor.setWidth((this.width - 2) / 2);
        this.routeEditor.setMessage(Component.literal("Edit route"));
        this.routeEditor.active = this.header.permissions().contains(SettlementPermission.STAFF);
        this.routeEditor.setTooltip(Tooltip.create(Component.literal(this.selected instanceof Resident
                ? "Edit this worker's route override. Reset restores worksite inheritance." : "Edit the default route for this worksite.")));
        this.back.visible = this.visible;
        String label = switch (this.section) {
            case OVERVIEW -> "Focus";
            case STAFF -> this.selected instanceof Project ? "Crew and priority"
                    : this.selected instanceof Area && this.worksite != null && this.worksite.plan() != null ? "Fill staffing" : "Staff actions";
            case ORDERS -> this.transfers != null ? "Edit transfers" : this.production != null ? "Edit production" : this.worksite != null && this.worksite.kind() == SettlementAreaType.TRADING_POST
                    ? "Trade orders" : this.worksite != null && this.worksite.kind() == SettlementAreaType.FISHERY ? "Fishing settings"
                    : this.selected instanceof Resident ? "Worker actions"
                    : this.worksite != null && (this.worksite.kind() == SettlementAreaType.GUARD_POST
                    || this.worksite.kind() == SettlementAreaType.HUNTING_GROUNDS) ? "Shared route"
                    : this.worksite != null && this.worksite.kind() == SettlementAreaType.CONSTRUCTION ? "Project details"
                    : this.worksite != null && this.worksite.kind() == SettlementAreaType.DEPOT ? "Manage logistics" : "Temporary task";
            case SETTINGS -> this.selected instanceof Resident ? "Worker settings" : this.worksite != null && this.worksite.plan() == null
                    ? "Preview adoption" : "Worksite settings";
            case STORAGE -> "Edit storage links";
        };
        this.primary.setMessage(Component.literal(label));
        this.primary.active = this.section == Section.OVERVIEW || (this.production != null || this.transfers != null) && this.section == Section.ORDERS
                || this.header.permissions().contains(this.section == Section.STORAGE ? SettlementPermission.STAFF
                : this.worksite != null && this.worksite.kind() == SettlementAreaType.TRADING_POST && this.section == Section.ORDERS
                ? SettlementPermission.VIEW : SettlementPermission.STAFF);
        if (this.section == Section.STAFF && this.selected instanceof Project) this.primary.active = this.header.permissions().contains(SettlementPermission.VIEW);
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
            line("Project priority: " + project.priority() + " / crew: " + (project.assistants().size() + 1));
            line("Lead: " + project.builderName());
            line("Materials: " + project.materials());
            if (!project.missingMaterials().isEmpty()) line("Missing: " + project.missingMaterials());
            if (!project.blocker().isEmpty()) line(project.blocker().replace('_', ' '));
        } else if (this.selected instanceof Issue issue) {
            line(issue.detail());
        }
    }

    private void staffing() {
        if (this.selected instanceof Project project) {
            line("Lead: " + project.builderName() + " / project payment");
            for (var member : project.assistants()) this.lines.add(new Line(Component.literal(member.name() + " / assistant"), member.id()));
            if (project.assistants().isEmpty()) line("No assistants");
            line("Assistants retain their home assignments.");
            return;
        }
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
        var settings = this.selected instanceof Resident resident && resident.settings() != null ? resident.settings() : plan == null ? null : plan.settings();
        var storage = settings == null ? com.jvn.villagerretaliation.settlement.SettlementStorageLinks.AUTOMATIC : settings.storage();
        line(this.selected instanceof Resident && settings != null ? (settings.overrides() & com.jvn.villagerretaliation.settlement.SettlementStorageLinks.OVERRIDE) != 0
                ? "Worker storage override" : "Inherits worksite storage" : plan == null ? "Adoption preview: automatic links" : "Worksite storage defaults");
        if (!storage.automatic()) {
            if (storage.entries().isEmpty()) line("No assigned storage. Automatic depot discovery is disabled.");
            for (var link : storage.entries()) line(link.purpose().label() + " / " + link.position().toShortString() + " / priority " + link.priority());
            line("Edit storage links to review live availability and framed filters.");
            line("Ordinary containers require loaded workers and reachable storage.");
            line(links.stations().isEmpty() ? "Stations: automatic" : "Permitted stations: " + links.stations().size());
            return;
        }
        line("Automatic depot assignments");
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
        int titleTop = this.top + (this.compact ? 26 : 46);
        int textLeft = this.left;
        if (!this.compact && this.selected instanceof Resident resident) {
            SettlementViewUi.frame(graphics, this.left, titleTop, 30, 30);
            SettlementViewUi.portrait(graphics, this.left + 5, titleTop + 5, 20);
            textLeft += 36;
            graphics.drawString(font, font.plainSubstrByWidth(SettlementViewUi.profession(resident),
                    this.width - 36), textLeft, titleTop + 13, 0xB9C1CB);
            graphics.fill(textLeft, titleTop + 25, this.left + this.width, titleTop + 28, 0xFF10151A);
            graphics.fill(textLeft, titleTop + 25, textLeft + Math.clamp(Math.round(SettlementViewUi.healthFraction(resident) * (this.width - 36)), 0, this.width - 36),
                    titleTop + 28, 0xFF67CF75);
        }
        graphics.drawString(font, font.plainSubstrByWidth(name, this.left + this.width - textLeft), textLeft, titleTop + 2, 0xFFFFFF);
        var wrapped = new ArrayList<Wrapped>();
        for (var line : this.lines) for (var part : font.split(line.text(), this.width - 6)) wrapped.add(new Wrapped(part, line));
        this.totalLines = wrapped.size();
        this.visibleLines = Math.max(0, (this.primary.getY() - this.contentTop) / (font.lineHeight + 2));
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.totalLines - this.visibleLines));
        this.hits.clear();
        for (int row = 0; row < this.visibleLines && this.scroll + row < wrapped.size(); row++) {
            int y = this.contentTop + row * (font.lineHeight + 2);
            var part = wrapped.get(this.scroll + row);
            graphics.drawString(font, part.text(), this.left, y, part.line().resident() == null ? 0xCCCCCC : 0x99DDEE);
            this.hits.add(new Hit(y, part.line()));
        }
        if (this.totalLines > this.visibleLines) {
            int track = Math.max(12, this.visibleLines * (font.lineHeight + 2));
            int thumb = Math.max(4, track * this.visibleLines / this.totalLines);
            int y = this.contentTop + (track - thumb) * this.scroll / Math.max(1, this.totalLines - this.visibleLines);
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
