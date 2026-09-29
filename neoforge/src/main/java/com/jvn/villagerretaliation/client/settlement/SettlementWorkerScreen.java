package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.HiredCombatMode;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSetting;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSettings;
import com.jvn.villagerretaliation.settlement.SettlementWorksitePlan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SettlementWorkerScreen extends Screen {
    private final SettlementManagementScreen parent;
    private final SettlementManagementPayload.Resident resident;
    private SettlementWorkerSettings draft;
    private final SettlementManagementPayload.Area worksite;
    private final SettlementViewScreen view;
    private int desiredStaff;
    private com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks links;
    private Button connections;
    private EditBox staff;
    private Button reset;
    private final List<Button> rows = new ArrayList<>();
    private List<SettlementWorkerSetting> settings;
    private List<ResourceLocation> filters;
    private boolean filtering;
    private int page;
    private String query = "";
    private EditBox search;
    private Button previous;
    private Button next;
    private Button save;
    private Button all;
    private Button brewing;
    private Component feedback = Component.empty();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public SettlementWorkerScreen(SettlementManagementScreen parent, SettlementManagementPayload.Resident resident) {
        super(text("worker_title", resident.name().isBlank() ? text("unnamed_resident") : Component.literal(resident.name())));
        this.parent = parent;
        this.resident = resident;
        this.worksite = null;
        this.view = null;
        this.draft = resident.settings();
    }

    public SettlementWorkerScreen(SettlementManagementScreen parent, SettlementManagementPayload.Area area, SettlementViewScreen view) {
        super(text("worksite_title", area.name()));
        this.parent = parent;
        this.resident = null;
        this.worksite = area;
        this.view = view;
        var plan = area.plan() == null ? SettlementWorksitePlan.defaults(area.kind().role()) : area.plan();
        this.draft = plan.settings();
        this.desiredStaff = plan.desiredStaff();
        this.links = plan.links();
    }

    public SettlementManagementScreen parent() {
        return this.parent;
    }

    SettlementManagementPayload.Area worksite() { return this.worksite; }
    com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks links() { return this.links; }
    void links(com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks links) { this.links = links; }

    public void result(SettlementManagementActionResultPayload payload) {
        if (this.parent.snapshot().header() == null || !this.parent.snapshot().header().id().equals(payload.town())) {
            return;
        }
        if (payload.applied() && !this.parent.resultWasLate()) {
            onClose();
        } else {
            this.feedback = SettlementManagementScreen.resultMessage(payload);
            updateRows();
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(480, this.width - 32);
        this.panelHeight = Math.min(350, this.height - 24);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.settings = Arrays.stream(SettlementWorkerSetting.values()).filter(setting -> setting.supports(this.draft.role())).toList();
        this.filters = this.draft.role() == HiredVillagerRole.LOGGING ? HiredLoggingFilters.options()
                : this.draft.role() == HiredVillagerRole.ANIMAL_HANDLING ? HiredAnimalBreedingTargets.options() : List.of();
        if (!this.filters.isEmpty()) {
            int half = (this.panelWidth - 4 - (this.worksite == null ? 0 : 104)) / 2;
            addRenderableWidget(Button.builder(text("worker_options"), ignored -> switchPage(false))
                    .bounds(this.left, this.top + 22, half, 20).build());
            addRenderableWidget(Button.builder(text(this.draft.role() == HiredVillagerRole.LOGGING ? "worker_logs" : "worker_animals"),
                    ignored -> switchPage(true)).bounds(this.left + half + 4, this.top + 22, half, 20).build());
        }
        if (this.worksite != null) {
            this.connections = addRenderableWidget(Button.builder(text("worksite_links"), ignored ->
                    this.minecraft.setScreen(new SettlementWorksiteLinksScreen(this)))
                    .bounds(this.filters.isEmpty() ? this.left : this.left + this.panelWidth - 100,
                            this.top + 22, this.filters.isEmpty() ? this.panelWidth : 100, 20).build());
        }
        if (this.resident != null && this.draft.role() == HiredVillagerRole.BREWING) {
            this.brewing = addRenderableWidget(Button.builder(text("brewing_orders"), ignored ->
                    this.minecraft.setScreen(new SettlementBrewingScreen(this, this.resident)))
                    .bounds(this.left, this.top + 22, this.panelWidth, 20).build());
            this.brewing.setTooltip(Tooltip.create(text("brewing_open_hint")));
        }
        this.search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 48, this.panelWidth - 100, 20, text("search")));
        this.search.setMaxLength(64);
        this.search.setHint(text("search"));
        this.search.setValue(this.query);
        this.search.setResponder(value -> {
            this.query = value;
            this.page = 0;
            updateRows();
        });
        this.all = addRenderableWidget(Button.builder(text("worker_all_types"), ignored -> {
            this.draft = hasShiftDown() && this.resident != null && this.resident.worksitePlan()
                    ? this.draft.withOverrides(this.draft.overrides() & ~(SettlementWorkerSettings.LOGS_OVERRIDE | SettlementWorkerSettings.ANIMALS_OVERRIDE))
                    : this.draft.withFilters(Set.of(), Set.of());
            updateRows();
        }).bounds(this.left + this.panelWidth - 94, this.top + 48, 94, 20).build());
        if (this.worksite != null) {
            this.staff = addRenderableWidget(new EditBox(this.font, this.left + this.panelWidth - 70, this.top + 48, 70, 20,
                    text("worksite_staff")));
            this.staff.setMaxLength(2);
            this.staff.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            this.staff.setValue(Integer.toString(this.desiredStaff));
            this.staff.setResponder(value -> {
                this.desiredStaff = value.isEmpty() ? -1 : Integer.parseInt(value);
                updateRows();
            });
        }
        this.rows.clear();
        int count = Math.max(1, (this.panelHeight - 132) / 24);
        for (int index = 0; index < count; index++) {
            int row = index;
            this.rows.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> change(row))
                    .bounds(this.left, this.top + 74 + index * 24, this.panelWidth, 20).build()));
        }
        int bottom = this.top + this.panelHeight;
        this.previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            this.page--;
            updateRows();
        }).bounds(this.left, bottom - 48, 24, 20).build());
        this.next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            this.page++;
            updateRows();
        }).bounds(this.left + 28, bottom - 48, 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, bottom - 20, 94, 20).build());
        if (this.resident != null && this.resident.worksitePlan()) {
            this.reset = addRenderableWidget(Button.builder(text("worksite_reset"), ignored -> {
                this.draft = this.draft.withOverrides(0);
                updateRows();
            }).bounds(this.left + 100, bottom - 20, this.panelWidth - 200, 20).build());
        }
        this.save = addRenderableWidget(Button.builder(text(this.worksite != null && this.worksite.plan() == null ? "worksite_adopt" : "save"), ignored -> {
            this.parent.submit(this.worksite == null
                    ? new SettlementManagementActionPayload.SaveWorker(this.resident.id(), this.resident.area(), this.draft)
                    : new SettlementManagementActionPayload.SaveWorksite(this.worksite.id(),
                            new SettlementWorksitePlan(this.draft, this.desiredStaff, this.links), this.worksite.plan() == null));
            this.feedback = text("saving");
            updateRows();
        }).bounds(this.left + this.panelWidth - 94, bottom - 20, 94, 20).build());
        updateRows();
    }

    private void switchPage(boolean filtering) {
        this.filtering = filtering;
        this.page = 0;
        updateRows();
    }

    private List<ResourceLocation> visibleFilters() {
        String needle = this.query.toLowerCase(Locale.ROOT);
        return this.filters.stream().filter(id -> id.toString().contains(needle) || filterLabel(id).getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private Set<ResourceLocation> selectedFilters() {
        return this.draft.role() == HiredVillagerRole.LOGGING ? this.draft.logs() : this.draft.animals();
    }

    private Component filterLabel(ResourceLocation id) {
        return this.draft.role() == HiredVillagerRole.LOGGING
                ? Component.translatable("block." + id.getNamespace() + "." + id.getPath())
                : Component.translatable("entity." + id.getNamespace() + "." + id.getPath());
    }

    private void change(int row) {
        int index = this.page * this.rows.size() + row;
        if (this.filtering) {
            List<ResourceLocation> visible = visibleFilters();
            if (index >= visible.size()) {
                return;
            }
            Set<ResourceLocation> selected = new HashSet<>(selectedFilters());
            if (!selected.remove(visible.get(index)) && selected.size() < SettlementWorkerSettings.MAX_FILTERS) {
                selected.add(visible.get(index));
            }
            this.draft = this.draft.withFilters(
                    this.draft.role() == HiredVillagerRole.LOGGING ? selected : Set.of(),
                    this.draft.role() == HiredVillagerRole.ANIMAL_HANDLING ? selected : Set.of());
        } else if (index < this.settings.size()) {
            var setting = this.settings.get(index);
            this.draft = hasShiftDown() && this.resident != null && this.resident.worksitePlan()
                    ? this.draft.withOverrides(this.draft.overrides() & ~(1 << setting.ordinal()))
                    : this.draft.with(setting, (this.draft.options().get(setting) + 1) % setting.choices());
        }
        updateRows();
    }

    private void updateRows() {
        if (this.save == null) {
            return;
        }
        boolean allowed = this.parent.permits(SettlementPermission.STAFF) && !this.parent.submitting();
        this.search.visible = this.filtering;
        this.search.active = this.filtering && allowed;
        this.all.visible = this.filtering;
        this.all.active = allowed;
        if (this.staff != null) {
            this.staff.visible = !this.filtering;
            this.staff.active = allowed;
        }
        if (this.reset != null) this.reset.active = allowed && this.draft.overrides() != 0;
        if (this.connections != null) this.connections.active = allowed;
        List<ResourceLocation> visible = this.filtering ? visibleFilters() : List.of();
        int total = this.filtering ? visible.size() : this.settings.size();
        this.page = Math.clamp(this.page, 0, Math.max(0, (total - 1) / this.rows.size()));
        for (int row = 0; row < this.rows.size(); row++) {
            Button button = this.rows.get(row);
            int index = this.page * this.rows.size() + row;
            button.visible = index < total;
            button.active = button.visible && allowed;
            if (!button.visible) {
                continue;
            }
            Component label;
            if (this.filtering) {
                ResourceLocation id = visible.get(index);
                boolean selected = selectedFilters().contains(id);
                label = text("worker_value", filterLabel(id), text(selectedFilters().isEmpty() ? "worker_default_allowed"
                        : selected ? "worker_filter_selected" : "worker_filter_excluded"));
                button.active &= selected || selectedFilters().size() < SettlementWorkerSettings.MAX_FILTERS;
            } else {
                var setting = this.settings.get(index);
                label = text("worker_value", SettlementManagementScreen.enumText("worker", setting),
                        this.resident != null && this.resident.worksitePlan() && !this.draft.overrides(setting)
                                ? text("worksite_inherit") : value(setting, this.draft.options().get(setting)));
            }
            button.setMessage(label);
            button.setTooltip(Tooltip.create(this.resident != null && this.resident.worksitePlan()
                    ? label.copy().append("\n").append(text("worksite_inherit_hint")) : label));
        }
        this.previous.active = allowed && this.page > 0;
        this.next.active = allowed && (this.page + 1) * this.rows.size() < total;
        this.save.active = allowed && (this.worksite == null ? !this.draft.equals(this.resident.settings())
                : this.desiredStaff >= 0 && this.desiredStaff <= SettlementWorksitePlan.MAX_STAFF
                        && (this.worksite.plan() == null || this.desiredStaff != this.worksite.plan().desiredStaff()
                                || !this.draft.equals(this.worksite.plan().settings()) || !this.links.equals(this.worksite.plan().links())));
        if (this.brewing != null) {
            this.brewing.active = allowed && this.draft.equals(this.resident.settings());
        }
    }

    private Component value(SettlementWorkerSetting setting, int value) {
        return switch (setting) {
            case MINING_MODE -> SettlementManagementScreen.enumText("worker_mining", HiredMiningMode.values()[value]);
            case CRAFTING_MODE -> SettlementManagementScreen.enumText("worker_crafting", CraftsmanWorker.Mode.values()[value]);
            case GUARD_MODE -> SettlementManagementScreen.enumText("worker_guard", HiredCombatMode.values()[value]);
            case CULL_CAP -> value == 0 ? text("disabled") : Component.literal(Integer.toString(HiredAnimalCullSettings.capOptions()[value - 1]));
            default -> text(value == 0 ? "disabled" : "enabled");
        };
    }

    @Override
    public void tick() {
        if (this.parent.unconfirmed()) this.feedback = text("acknowledgement_timeout");
        updateRows();
    }

    @Override
    public void onClose() {
        if (this.view == null || !SettlementViewClient.resume(this.parent)) this.minecraft.setScreen(this.parent);
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
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF);
        if (!this.filtering) {
            graphics.drawString(this.font, text(this.worksite == null ? "worker_options_hint" : "worksite_staff"), this.left, this.top + 54, 0xD0D0D0);
        }
        int filterOverride = this.draft.role() == HiredVillagerRole.LOGGING
                ? SettlementWorkerSettings.LOGS_OVERRIDE : SettlementWorkerSettings.ANIMALS_OVERRIDE;
        Component status = !this.feedback.getString().isEmpty() ? this.feedback
                : this.filtering && this.resident != null && this.resident.worksitePlan()
                        && (this.draft.overrides() & filterOverride) == 0 ? text("worksite_inherit")
                : this.filtering ? text(selectedFilters().isEmpty() ? "worker_all_hint" : "worker_selected", selectedFilters().size())
                : this.worksite != null && this.worksite.plan() == null ? text("worksite_adopt_hint", this.worksite.workers())
                : text("worker_schedule_hint");
        SettlementManagementScreen.renderFeedback(graphics, this.font, status,
                this.left + 60, this.top + this.panelHeight - 42, this.panelWidth - 60, mouseX, mouseY);
    }

    private static Component text(String key, Object... arguments) {
        return SettlementManagementScreen.text(key, arguments);
    }
}
