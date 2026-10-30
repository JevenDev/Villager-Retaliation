package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class SettlementProjectScreen extends VillagerWindowScreen {
    private final SettlementManagementScreen parent;
    private final SettlementManagementPayload previousSnapshot;
    private final SettlementViewScreen view;
    private SettlementManagementPayload.Project project;
    private final LinkedHashMap<UUID, String> assistants = new LinkedHashMap<>();
    private final List<Button> rows = new ArrayList<>();
    private int priority;
    private boolean choosing;
    private int offset;
    private String query = "";
    private EditBox search;
    private Button priorityButton;
    private Button previous;
    private Button next;
    private Button save;
    private Component feedback = Component.empty();
    private boolean farmPaused;
    private UUID farmWorker;
    private String farmWorkerName;
    private Button farmPause;
    private Button farmCancel;
    private boolean openingFarmSettings;

    public SettlementProjectScreen(SettlementManagementScreen parent, SettlementManagementPayload.Project project, SettlementViewScreen view) {
        super(Component.literal(project.farm() == null ? "Construction crew" : "Farm preparation"));
        this.parent = parent;
        this.previousSnapshot = parent.snapshot();
        this.project = project;
        this.view = view;
        resetDraft();
        request();
    }

    SettlementManagementScreen parent() { return this.parent; }
    SettlementManagementPayload.Project project() { return this.project; }

    private void resetDraft() {
        this.priority = this.project.priority();
        if (this.project.farm() != null) {
            this.farmPaused = this.project.farm().paused();
            this.farmWorker = this.project.farm().requested();
            this.farmWorkerName = this.farmWorker.equals(com.jvn.villagerretaliation.settlement.SettlementFarmJob.AUTOMATIC)
                    ? "Next available farmer" : this.project.builderName();
        }
        this.assistants.clear();
        this.project.assistants().forEach(member -> this.assistants.put(member.id(), member.name()));
    }

    private void request() {
        this.parent.navigate(this.choosing ? Page.RESIDENTS : Page.PROJECTS,
                this.choosing ? this.offset / SettlementManagementRequestPayload.PAGE_SIZE : 0,
                this.choosing ? this.query : this.project.id().toString());
    }

    @Override
    protected void init() {
        if (this.project.farm() != null) {
            initFarm();
            return;
        }
        initWindow(480, 344);
        this.priorityButton = addRenderableWidget(VillagerUi.buttonBuilder(Component.empty(), ignored -> {
            this.priority = (this.priority + 1) % 10;
            updateRows();
        }).bounds(this.left, this.top + 20, 128, 20).build());
        this.priorityButton.setTooltip(Tooltip.create(Component.literal("Set the project work priority. Drag pending requests to change their queue order.")));
        int tabWidth = (this.panelWidth - 4) / 2;
        for (int index = 0; index < 2; index++) {
            boolean picking = index == 1;
            addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(picking ? "Add builders" : "Current crew"), ignored -> {
                this.choosing = picking;
                this.offset = 0;
                request();
                updateRows();
            }).bounds(this.left + index * (tabWidth + 4), this.top + 44, tabWidth, 20).build());
        }
        this.search = new EditBox(this.font, this.left, this.top + 68, this.panelWidth, 20, Component.literal("Find builder"));
        this.search.setHint(Component.literal("Find builder"));
        this.search.setMaxLength(64);
        this.search.setValue(this.query);
        this.search.setResponder(value -> {
            this.query = value;
            this.offset = 0;
            request();
        });
        addRenderableWidget(this.search);
        this.rows.clear();
        int count = 1;
        for (int candidate : new int[] { 2, 3, 4, 6 }) if (candidate * 22 <= this.panelHeight - 144) count = candidate;
        this.offset -= this.offset % count;
        for (int index = 0; index < count; index++) {
            int row = index;
            this.rows.add(addRenderableWidget(VillagerUi.buttonBuilder(Component.empty(), ignored -> toggle(row))
                    .bounds(this.left, this.top + 92 + index * 22, this.panelWidth, 20).build()));
        }
        int bottom = this.top + this.panelHeight;
        this.previous = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("<"), ignored -> move(-this.rows.size()))
                .bounds(this.left, bottom - 46, 24, 20).build());
        this.next = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(">"), ignored -> move(this.rows.size()))
                .bounds(this.left + 28, bottom - 46, 24, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, bottom - 20, 94, 20).build());
        this.save = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Apply crew"), ignored -> this.parent.submit(
                new SettlementManagementActionPayload.ConfigureProject(this.project.id(), this.project.crewRevision(), this.priority,
                        List.copyOf(this.assistants.keySet())))).bounds(this.left + this.panelWidth - 110, bottom - 20, 110, 20).build());
        updateRows();
    }

    private void initFarm() {
        initWindow(480, 344);
        int half = (this.panelWidth - 6) / 2;
        int third = (this.panelWidth - 12) / 3;
        this.priorityButton = addRenderableWidget(VillagerUi.buttonBuilder(Component.empty(), ignored -> {
            this.priority = (this.priority + 1) % 10;
            updateFarm();
        }).bounds(this.left, this.top + 24, half, 20).build());
        this.farmPause = addRenderableWidget(VillagerUi.buttonBuilder(Component.empty(), ignored -> {
            this.farmPaused = !this.farmPaused;
            updateFarm();
        }).bounds(this.left + half + 6, this.top + 24, half, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Choose farmer"), ignored -> {
            this.choosing = true;
            this.offset = 0;
            request();
            updateFarm();
        }).bounds(this.left, this.top + 54, half, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Next available farmer"), ignored -> {
            this.farmWorker = com.jvn.villagerretaliation.settlement.SettlementFarmJob.AUTOMATIC;
            this.farmWorkerName = "Next available farmer";
            this.choosing = false;
            request();
            updateFarm();
        }).bounds(this.left + half + 6, this.top + 54, half, 20).build());
        this.search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 82, this.panelWidth - 60, 20, Component.literal("Find farmer")));
        this.search.setMaxLength(64);
        this.search.setValue(this.query);
        this.search.setResponder(value -> { this.query = value; this.offset = 0; request(); });
        this.rows.clear();
        int count = Math.clamp((this.panelHeight - 188) / 22, 1, 4);
        for (int row = 0; row < count; row++) {
            int index = row;
            this.rows.add(addRenderableWidget(VillagerUi.buttonBuilder(Component.empty(), ignored -> {
                var resident = candidate(index);
                if (!farmEditable() || resident == null || resident.baby() || !resident.roles().contains(HiredVillagerRole.FARMING)) return;
                this.farmWorker = resident.id();
                this.farmWorkerName = resident.name();
                this.choosing = false;
                request();
                updateFarm();
            }).bounds(this.left, this.top + 106 + row * 22, this.panelWidth, 20).build()));
        }
        this.previous = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("<"), ignored -> move(-this.rows.size()))
                .bounds(this.left + this.panelWidth - 54, this.top + 82, 24, 20).build());
        this.next = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(">"), ignored -> move(this.rows.size()))
                .bounds(this.left + this.panelWidth - 26, this.top + 82, 24, 20).build());
        this.farmCancel = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Cancel job"), ignored ->
                this.parent.submit(new SettlementManagementActionPayload.CancelProject(this.project.id())))
                .bounds(this.left, this.top + this.panelHeight - 76, third, 20).build());
        this.farmCancel.setTooltip(Tooltip.create(Component.literal("Stops this job. Completed soil, placed water and the farm plot are kept.")));
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Farm settings"), ignored -> {
            this.openingFarmSettings = true;
            this.parent.navigate(Page.AREAS, 0, this.project.area().toString());
        }).bounds(this.left + third + 6, this.top + this.panelHeight - 76, third, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Supplies"), ignored -> {
            this.parent.navigate(Page.SUPPLIES, 0, "");
            this.minecraft.setScreen(this.parent);
        }).bounds(this.left + 2 * (third + 6), this.top + this.panelHeight - 76, third, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 20, 100, 20).build());
        this.save = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Apply changes"), ignored ->
                this.parent.submit(new SettlementManagementActionPayload.ConfigureFarmJob(this.project.id(), this.project.crewRevision(),
                        this.priority, this.farmPaused, this.farmWorker)))
                .bounds(this.left + this.panelWidth - 140, this.top + this.panelHeight - 20, 140, 20).build());
        updateFarm();
    }

    private boolean farmEditable() {
        return (this.project.status() == SettlementProject.Status.ACTIVE || this.project.status() == SettlementProject.Status.PENDING)
                && this.parent.permits(SettlementPermission.BUILD) && this.parent.permits(SettlementPermission.STAFF)
                && !this.parent.submitting() && !this.openingFarmSettings;
    }

    private void updateFarm() {
        if (this.save == null) return;
        var snapshot = this.parent.snapshot();
        if (this.openingFarmSettings && snapshot.page() == Page.AREAS && snapshot.query().equals(this.project.area().toString())) {
            var area = snapshot.entries().stream().filter(entry -> entry.id().equals(this.project.area()))
                    .filter(SettlementManagementPayload.Area.class::isInstance).map(SettlementManagementPayload.Area.class::cast).findFirst().orElse(null);
            if (area != null) {
                this.openingFarmSettings = false;
                this.minecraft.setScreen(new SettlementWorkerScreen(this.parent, area, this.view));
                return;
            }
        }
        if (snapshot.page() == Page.PROJECTS && snapshot.query().equals(this.project.id().toString())) {
            var current = snapshot.entries().stream().filter(entry -> entry.id().equals(this.project.id()))
                    .filter(SettlementManagementPayload.Project.class::isInstance).map(SettlementManagementPayload.Project.class::cast).findFirst().orElse(null);
            if (current != null) {
                boolean changed = current.crewRevision() != this.project.crewRevision();
                this.project = current;
                if (changed) { resetDraft(); this.feedback = Component.literal("Farm job changed. Review its current settings."); }
            }
        }
        this.priorityButton.setMessage(Component.literal("Priority: " + this.priority));
        this.priorityButton.active = farmEditable();
        this.farmPause.setMessage(Component.literal(this.farmPaused ? "Resume on apply" : "Pause on apply"));
        this.farmPause.active = farmEditable();
        this.save.active = farmEditable();
        this.farmCancel.active = farmEditable();
        this.search.visible = this.choosing;
        this.search.active = this.choosing && farmEditable();
        this.previous.visible = this.choosing;
        this.next.visible = this.choosing;
        this.previous.active = ready() && this.offset > 0;
        this.next.active = ready() && this.offset + this.rows.size() < snapshot.total();
        for (int index = 0; index < this.rows.size(); index++) {
            var resident = candidate(index);
            var button = this.rows.get(index);
            button.visible = this.choosing && resident != null;
            button.active = farmEditable() && resident != null && !resident.baby() && resident.roles().contains(HiredVillagerRole.FARMING);
            if (resident != null) button.setMessage(Component.literal((resident.id().equals(this.farmWorker) ? "[x] " : "[ ] ") + resident.name()));
        }
    }

    private void move(int amount) {
        this.offset = Math.max(0, this.offset + amount);
        if (this.choosing && !ready()) request();
        updateRows();
    }

    private boolean ready() {
        var snapshot = this.parent.snapshot();
        return !this.choosing || snapshot.page() == Page.RESIDENTS && snapshot.query().equals(this.query)
                && snapshot.index() == this.offset / SettlementManagementRequestPayload.PAGE_SIZE;
    }

    private SettlementManagementPayload.Resident candidate(int row) {
        var snapshot = this.parent.snapshot();
        int index = this.offset % SettlementManagementRequestPayload.PAGE_SIZE + row;
        return this.choosing && ready() && index < snapshot.entries().size()
                && snapshot.entries().get(index) instanceof SettlementManagementPayload.Resident resident ? resident : null;
    }

    private SettlementManagementPayload.CrewMember selected(int row) {
        var members = this.assistants.entrySet().stream().map(member -> new SettlementManagementPayload.CrewMember(member.getKey(), member.getValue())).toList();
        int index = this.offset + row;
        return !this.choosing && index < members.size() ? members.get(index) : null;
    }

    private static boolean eligible(SettlementManagementPayload.Resident resident) {
        return resident != null && !resident.baby() && resident.area() != null && resident.worksitePlan()
                && resident.settings() != null && resident.settings().role() == HiredVillagerRole.BUILDER
                && resident.settings().enabled() && resident.roles().contains(HiredVillagerRole.BUILDER);
    }

    private boolean editable() {
        return this.project.status() == SettlementProject.Status.ACTIVE && this.project.crewAvailable()
                && this.parent.permits(SettlementPermission.BUILD) && this.parent.permits(SettlementPermission.STAFF)
                && !this.parent.submitting();
    }

    private void toggle(int row) {
        if (!editable()) return;
        var candidate = candidate(row);
        var selected = selected(row);
        UUID id = candidate == null ? selected == null ? null : selected.id() : candidate.id();
        if (id == null || id.equals(this.project.builder())) return;
        if (this.assistants.remove(id) == null && eligible(candidate) && this.assistants.size() < 15) {
            this.assistants.put(id, candidate.name());
        }
        updateRows();
    }

    private void updateRows() {
        if (this.project.farm() != null) {
            updateFarm();
            return;
        }
        if (this.save == null) return;
        var snapshot = this.parent.snapshot();
        if (snapshot.page() == Page.PROJECTS && snapshot.query().equals(this.project.id().toString())) {
            var current = snapshot.entries().stream().filter(entry -> entry.id().equals(this.project.id()))
                    .filter(SettlementManagementPayload.Project.class::isInstance).map(SettlementManagementPayload.Project.class::cast).findFirst().orElse(null);
            if (current != null) {
                if (current.crewRevision() != this.project.crewRevision()) {
                    this.project = current;
                    resetDraft();
                    this.feedback = Component.literal("Project changed. Review the current crew.");
                } else this.project = current;
            }
        }
        int total = this.choosing ? ready() ? snapshot.total() : this.offset + this.rows.size() : this.assistants.size();
        if (ready() && this.offset >= total) this.offset = Math.max(0, (total - 1) / this.rows.size() * this.rows.size());
        this.search.visible = this.choosing;
        this.search.active = this.choosing && !this.parent.submitting();
        this.priorityButton.setMessage(Component.literal("Priority: " + this.priority));
        this.priorityButton.active = editable();
        this.save.active = editable();
        this.previous.active = ready() && this.offset > 0;
        this.next.active = ready() && this.offset + this.rows.size() < total;
        for (int index = 0; index < this.rows.size(); index++) {
            var button = this.rows.get(index);
            var resident = candidate(index);
            var member = selected(index);
            button.visible = resident != null || member != null;
            if (!button.visible) continue;
            var id = resident == null ? member.id() : resident.id();
            var name = resident == null ? member.name() : resident.name();
            boolean selected = this.assistants.containsKey(id);
            String label = (selected ? "[x] " : "[ ] ") + name;
            button.setMessage(Component.literal(this.font.plainSubstrByWidth(label, this.panelWidth - 12)));
            button.active = editable() && !id.equals(this.project.builder()) && (selected || eligible(resident) && this.assistants.size() < 15);
            button.setTooltip(Tooltip.create(Component.literal(id.equals(this.project.builder()) ? "The lead builder keeps project payment."
                    : resident != null && !eligible(resident) ? "Assign this resident to an adopted construction worksite first."
                    : "Click to " + (selected ? "remove" : "add") + " this assistant. Their home assignment is retained.")));
        }
    }

    void result(SettlementManagementActionResultPayload payload) {
        if (payload.applied() && !this.parent.resultWasLate()) {
            onClose();
        } else {
            this.feedback = SettlementManagementScreen.resultMessage(payload);
            this.choosing = false;
            this.offset = 0;
            request();
            updateRows();
        }
    }

    @Override
    public void tick() { updateRows(); }

    @Override
    public void onClose() {
        this.parent.navigate(this.previousSnapshot.page(), this.previousSnapshot.index(), this.previousSnapshot.query());
        this.parent.restoreSelection(this.project.id());
        if (this.view == null || !SettlementViewClient.resume(this.parent)) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top, 0xFFFFFF);
        if (this.project.farm() != null) {
            if (!this.choosing) {
                var farm = this.project.farm();
                graphics.drawString(this.font, "Soil: " + farm.soilDone() + "/" + farm.soilTotal()
                        + "   Water: " + farm.waterDone() + "/" + farm.waterTotal(), this.left, this.top + 82, 0xCCCCCC);
                graphics.drawString(this.font, this.font.plainSubstrByWidth("Farmer: " + this.project.builderName(), this.panelWidth),
                        this.left, this.top + 96, 0xCCCCCC);
                graphics.drawString(this.font, this.font.plainSubstrByWidth("Requested: " + this.farmWorkerName, this.panelWidth),
                        this.left, this.top + 110, 0xCCCCCC);
                var blocker = Component.translatableWithFallback(this.project.blocker(), this.project.blocker());
                var lines = this.font.split(blocker, this.panelWidth);
                int limit = Math.max(1, (this.panelHeight - 202) / this.font.lineHeight);
                for (int line = 0; line < Math.min(limit, lines.size()); line++)
                    graphics.drawString(this.font, lines.get(line), this.left, this.top + 124 + line * this.font.lineHeight, 0xE7C77A);
            }
            SettlementManagementScreen.renderFeedback(graphics, this.font, this.feedback, this.left, this.top + this.panelHeight - 44,
                    this.panelWidth, mouseX, mouseY);
            return;
        }
        graphics.drawString(this.font, "Crew: " + (this.assistants.size() + 1) + " / 16", this.left + 138, this.top + 26, 0xAAAAAA);
        if (!this.choosing) graphics.drawString(this.font,
                this.font.plainSubstrByWidth("Lead: " + this.project.builderName() + " (project payment)", this.panelWidth), this.left, this.top + 73, 0xCCCCCC);
        Component status = !this.project.crewAvailable() ? Component.literal("Adopt the construction worksite first.")
                : this.parent.unconfirmed() ? Component.literal("Confirmation delayed. Review the current crew before applying again.")
                : !this.feedback.getString().isEmpty() ? this.feedback
                : !ready() ? Component.literal("Loading builders...")
                : !this.choosing && this.assistants.isEmpty() ? Component.literal("No assistants") : Component.empty();
        SettlementManagementScreen.renderFeedback(graphics, this.font, status, this.left + 60, this.top + this.panelHeight - 40,
                this.panelWidth - 60, mouseX, mouseY);
    }
}
