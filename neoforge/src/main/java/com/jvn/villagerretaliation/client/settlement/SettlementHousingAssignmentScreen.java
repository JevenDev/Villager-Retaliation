package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerTaskScreen;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Bed;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident;
import com.jvn.villagerretaliation.settlement.SettlementHousingDistribution;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class SettlementHousingAssignmentScreen extends VillagerTaskScreen {
    private final SettlementViewScreen view;
    private final List<Resident> residents;
    private final List<Bed> beds;
    private final SettlementSelectionSet<UUID> chosenResidents = new SettlementSelectionSet<>(SettlementViewSelection.LIMIT);
    private final SettlementSelectionSet<BlockPos> chosenBeds = new SettlementSelectionSet<>(SettlementViewSelection.LIMIT);
    private int tab;
    private int page;
    private int rowCount;
    private int pageCount;
    private SettlementHousingDistribution.Plan plan;
    private String feedback = "";

    SettlementHousingAssignmentScreen(SettlementViewScreen view, List<Resident> residents, List<Bed> beds,
                                     List<UUID> selectedResidents, List<BlockPos> selectedBeds) {
        super(Component.literal("Assign beds"));
        this.view = view;
        this.residents = List.copyOf(residents);
        this.beds = List.copyOf(beds);
        this.chosenResidents.apply(selectedResidents, SettlementSelectionSet.Operation.REPLACE);
        this.chosenBeds.apply(selectedBeds.isEmpty() ? beds.stream()
                .filter(bed -> bed.available() || bed.resident() != null && selectedResidents.contains(bed.resident())).map(Bed::position).toList()
                : selectedBeds, SettlementSelectionSet.Operation.REPLACE);
        this.tab = selectedResidents.isEmpty() ? 0 : selectedBeds.isEmpty() ? 1 : 2;
    }

    SettlementManagementScreen parent() { return this.view.parent(); }

    @Override
    protected void init() {
        initWindow(400, 286);
        this.plan = SettlementHousingDistribution.plan(this.chosenResidents.values(), this.chosenBeds.values(), this.beds);
        int tabWidth = (this.panelWidth - 8) / 3;
        String[] tabs = {"Residents (" + this.chosenResidents.size() + ")", "Beds (" + this.chosenBeds.size() + ")", "Review (" + this.plan.targets().size() + ")"};
        for (int index = 0; index < tabs.length; index++) {
            int next = index;
            var button = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(tabs[index]), ignored -> {
                this.tab = next;
                this.page = 0;
                rebuildWidgets();
            }).bounds(this.left + index * (tabWidth + 4), this.top + 22, tabWidth, 20).build());
            button.active = index != this.tab;
        }
        this.rowCount = Math.max(1, (this.panelHeight - 142) / 22);
        var rows = rows();
        int pages = Math.max(1, (rows.size() + this.rowCount - 1) / this.rowCount);
        this.pageCount = pages;
        this.page = Math.clamp(this.page, 0, pages - 1);
        for (int index = 0; index < this.rowCount; index++) {
            int position = this.page * this.rowCount + index;
            if (position >= rows.size()) break;
            var row = rows.get(position);
            String label = row.label();
            var button = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(this.font.plainSubstrByWidth(label, this.panelWidth - 10)), ignored -> row.action().run())
                    .bounds(this.left, this.top + 48 + index * 22, this.panelWidth, 20).build());
            if (this.tab != 2) ((VillagerUi.OptionButton) button).checkbox(true, row.chosen());
            button.active = this.tab != 2;
            button.setTooltip(Tooltip.create(Component.literal(row.label())));
        }
        int footer = this.top + this.panelHeight - 84;
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Previous"), ignored -> { this.page--; rebuildWidgets(); })
                .bounds(this.left, footer, 68, 18).build()).active = this.page > 0;
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Next"), ignored -> { this.page++; rebuildWidgets(); })
                .bounds(this.left + 72, footer, 56, 18).build()).active = this.page + 1 < pages;
        if (this.tab != 2) {
            addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Select all"), ignored -> {
                if (this.tab == 0) {
                    if (!this.chosenResidents.apply(this.residents.stream().map(Resident::id).toList(), SettlementSelectionSet.Operation.REPLACE)) this.feedback = "Selection limit: 128 residents";
                } else if (!this.chosenBeds.apply(this.beds.stream().filter(bed -> bed.available() || this.chosenResidents.contains(bed.resident()))
                        .map(Bed::position).toList(), SettlementSelectionSet.Operation.REPLACE)) this.feedback = "Selection limit: 128 beds";
                rebuildWidgets();
            }).bounds(this.left + this.panelWidth - 132, footer, 78, 18).build());
            addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Clear"), ignored -> {
                if (this.tab == 0) this.chosenResidents.apply(List.of(), SettlementSelectionSet.Operation.REPLACE);
                else this.chosenBeds.apply(List.of(), SettlementSelectionSet.Operation.REPLACE);
                rebuildWidgets();
            }).bounds(this.left + this.panelWidth - 50, footer, 50, 18).build());
        }
        var apply = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(this.tab == 2 ? "Assign " + this.plan.targets().size() + (this.plan.targets().size() == 1 ? " bed" : " beds") : "Review assignments"), ignored -> {
            if (this.tab != 2) { this.tab = 2; this.page = 0; rebuildWidgets(); }
            else if (this.view.assignHomes(this.plan.targets())) onClose();
        }).bounds(this.left, this.top + this.panelHeight - 20, this.panelWidth / 2 - 2, 20).build());
        apply.active = this.tab != 2 || !this.plan.targets().isEmpty();
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(this.left + this.panelWidth / 2 + 2, this.top + this.panelHeight - 20, this.panelWidth / 2 - 2, 20).build());
    }

    private List<Row> rows() {
        var rows = new ArrayList<Row>();
        if (this.tab == 0) {
            for (var resident : this.residents) {
                var home = this.beds.stream().filter(bed -> resident.id().equals(bed.resident())).findFirst().orElse(null);
                rows.add(new Row(resident.name() + (home == null ? " / no assigned bed" : " / bed " + home.position().toShortString()),
                        this.chosenResidents.contains(resident.id()), () -> {
                    if (!this.chosenResidents.apply(List.of(resident.id()), SettlementSelectionSet.Operation.TOGGLE)) this.feedback = "Selection limit: 128 residents";
                    rebuildWidgets();
                }));
            }
        } else if (this.tab == 1) {
            for (var bed : this.beds) rows.add(new Row(bed.position().toShortString() + " / "
                    + (bed.resident() != null ? bed.residentName().isEmpty() ? "Assigned" : bed.residentName() : bed.available() ? "Available" : "Unavailable"),
                    this.chosenBeds.contains(bed.position()), () -> {
                if (!this.chosenBeds.apply(List.of(bed.position()), SettlementSelectionSet.Operation.TOGGLE)) this.feedback = "Selection limit: 128 beds";
                rebuildWidgets();
            }));
        } else {
            for (var target : this.plan.targets()) {
                var name = this.residents.stream().filter(resident -> resident.id().equals(target.resident())).map(Resident::name).findFirst().orElse("Resident");
                rows.add(new Row(name + " -> " + target.bed().toShortString()
                        + (target.expectedBed() == null ? "" : " / replaces " + target.expectedBed().toShortString()), false, () -> { }));
            }
        }
        return rows;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top + 4, 0xFFFFFF, false);
        String pages = (this.page + 1) + "/" + this.pageCount;
        graphics.drawString(this.font, pages, this.left + this.panelWidth - this.font.width(pages), this.top + 4, 0xBBC5D0, false);
        String summary = this.plan.targets().size() + (this.plan.targets().size() == 1 ? " assignment, " : " assignments, ") + this.plan.unchanged() + " kept, " + this.plan.unassigned() + " without a free bed";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary, this.panelWidth), this.left, this.top + this.panelHeight - 60, 0xBBC5D0, false);
        String notice = this.feedback.isEmpty() ? "Existing owners keep their beds. Limit: 128 each." : this.feedback;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(notice, this.panelWidth), this.left, this.top + this.panelHeight - 42, 0xE6B86C, false);
    }

    @Override
    public void onClose() { SettlementViewClient.resume(parent()); }

    @Override
    public boolean isPauseScreen() { return false; }

    private record Row(String label, boolean chosen, Runnable action) { }
}
