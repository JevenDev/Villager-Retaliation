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
        this.draft = resident.settings();
    }

    public SettlementManagementScreen parent() {
        return this.parent;
    }

    public void result(SettlementManagementActionResultPayload payload) {
        if (this.parent.snapshot().header() == null || !this.parent.snapshot().header().id().equals(payload.town())) {
            return;
        }
        if (payload.applied()) {
            onClose();
        } else {
            this.feedback = text("not_applied");
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
            int half = (this.panelWidth - 4) / 2;
            addRenderableWidget(Button.builder(text("worker_options"), ignored -> switchPage(false))
                    .bounds(this.left, this.top + 22, half, 20).build());
            addRenderableWidget(Button.builder(text(this.draft.role() == HiredVillagerRole.LOGGING ? "worker_logs" : "worker_animals"),
                    ignored -> switchPage(true)).bounds(this.left + half + 4, this.top + 22, half, 20).build());
        }
        if (this.draft.role() == HiredVillagerRole.BREWING) {
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
            this.draft = new SettlementWorkerSettings(this.draft.role(), this.draft.options(), Set.of(), Set.of());
            updateRows();
        }).bounds(this.left + this.panelWidth - 94, this.top + 48, 94, 20).build());
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
        this.save = addRenderableWidget(Button.builder(text("save"), ignored -> {
            this.parent.submit(new SettlementManagementActionPayload.SaveWorker(this.resident.id(), this.resident.area(), this.draft));
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
            this.draft = new SettlementWorkerSettings(this.draft.role(), this.draft.options(),
                    this.draft.role() == HiredVillagerRole.LOGGING ? selected : Set.of(),
                    this.draft.role() == HiredVillagerRole.ANIMAL_HANDLING ? selected : Set.of());
        } else if (index < this.settings.size()) {
            var setting = this.settings.get(index);
            this.draft = this.draft.with(setting, (this.draft.options().get(setting) + 1) % setting.choices());
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
                label = text("worker_value", SettlementManagementScreen.enumText("worker", setting), value(setting, this.draft.options().get(setting)));
            }
            button.setMessage(label);
            button.setTooltip(Tooltip.create(label));
        }
        this.previous.active = allowed && this.page > 0;
        this.next.active = allowed && (this.page + 1) * this.rows.size() < total;
        this.save.active = allowed && !this.draft.equals(this.resident.settings());
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
        updateRows();
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
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF);
        if (!this.filtering) {
            graphics.drawString(this.font, text("worker_options_hint"), this.left, this.top + 54, 0xD0D0D0);
        }
        Component status = !this.feedback.getString().isEmpty() ? this.feedback
                : this.filtering ? text(selectedFilters().isEmpty() ? "worker_all_hint" : "worker_selected", selectedFilters().size())
                : text("worker_schedule_hint");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), this.panelWidth - 60),
                this.left + 60, this.top + this.panelHeight - 42, 0xD0D0D0);
    }

    private static Component text(String key, Object... arguments) {
        return SettlementManagementScreen.text(key, arguments);
    }
}
