package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop;
import com.jvn.villagerretaliation.settlement.SettlementCropPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class SettlementFarmPlanScreen extends VillagerWindowScreen {
    private final SettlementViewScreen parent;
    private final List<String> percentages = new ArrayList<>();
    private SettlementCropPlan draft;
    private Component error = Component.empty();

    SettlementFarmPlanScreen(SettlementViewScreen parent, SettlementCropPlan plan) {
        super(Component.literal("Farm crop plan"));
        this.parent = parent;
        this.draft = plan;
        plan.percentages().forEach(value -> this.percentages.add(Integer.toString(value)));
    }

    @Override
    protected void init() {
        initWindow(420, 260);
        int third = (this.panelWidth - 12) / 3;
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Prepare only"), ignored -> preset(SettlementCropPlan.NONE))
                .bounds(this.left, this.top + 24, third, 20).build());
        var demand = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Demand: " + (this.draft.demand() ? "On" : "Off")), ignored -> {
            if (capture()) preset(new SettlementCropPlan(this.draft.percentages(), !this.draft.demand(), this.draft.cycles()));
        }).bounds(this.left + third + 6, this.top + 24, third, 20).build());
        demand.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("villagerretaliation.gui.settlement.crop_demand_hint")));
        for (int index = 0; index < 6; index++) {
            int crop = index;
            int x = this.left + index % 3 * (third + 6);
            int y = this.top + 56 + index / 3 * 54;
            addRenderableWidget(VillagerUi.buttonBuilder(label(Crop.values()[index + 1]), ignored -> {
                var values = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0));
                values.set(crop, 100);
                preset(new SettlementCropPlan(values, false, this.draft.cycles()));
            }).bounds(x, y, third, 20).build());
            var input = new EditBox(this.font, x, y + 23, third, 18, label(Crop.values()[index + 1]));
            input.setMaxLength(3);
            input.setFilter(value -> value.matches("[0-9]*"));
            input.setValue(this.percentages.get(index));
            input.setResponder(value -> this.percentages.set(crop, value));
            addRenderableWidget(input);
        }
        var reserve = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Reserve: " + (this.draft.cycles() == 0 ? 8 : this.draft.cycles())), button -> {
            if (!capture()) return;
            this.draft = new SettlementCropPlan(this.draft.percentages(), this.draft.demand(), (this.draft.cycles() + 1) % 3);
            button.setMessage(Component.literal("Reserve: " + (this.draft.cycles() == 0 ? 8 : this.draft.cycles())));
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                    "villagerretaliation.gui.settlement.crop_reserve_" + this.draft.cycles())));
        }).bounds(this.left + 2 * (third + 6), this.top + 24, third, 20).build());
        reserve.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                "villagerretaliation.gui.settlement.crop_reserve_" + this.draft.cycles())));
        int bottom = this.top + this.panelHeight;
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, bottom - 20, 100, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Apply crop plan"), ignored -> {
            if (!capture()) return;
            this.parent.farmCrops(this.draft);
            onClose();
        }).bounds(this.left + this.panelWidth - 140, bottom - 20, 140, 20).build());
    }

    private static Component label(Crop crop) {
        return Component.translatable("villagerretaliation.gui.settlement.crop." + crop.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void preset(SettlementCropPlan plan) {
        this.draft = plan;
        this.percentages.clear();
        plan.percentages().forEach(value -> this.percentages.add(Integer.toString(value)));
        this.error = Component.empty();
        rebuildWidgets();
    }

    private boolean capture() {
        try {
            var values = this.percentages.stream().map(value -> value.isEmpty() ? 0 : Integer.parseInt(value)).toList();
            this.draft = new SettlementCropPlan(values, this.draft.demand(), this.draft.cycles());
            this.error = Component.empty();
            return true;
        } catch (IllegalArgumentException exception) {
            this.error = Component.literal("Use percentages totalling 100, or all zero.");
            return false;
        }
    }

    SettlementManagementScreen parent() { return this.parent.parent(); }

    @Override public void onClose() {
        if (!SettlementViewClient.resume(this.parent.parent())) this.minecraft.setScreen(this.parent.parent());
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top + 4, 0xFFFFFF);
        graphics.drawString(this.font, this.draft.demand() ? "Demand planting" : "Crop mix percentages", this.left, this.top + 13, 0xAAAAAA);
        graphics.drawWordWrap(this.font, this.error, this.left, this.top + this.panelHeight - 43, this.panelWidth, 0xFF9999);
    }
}
