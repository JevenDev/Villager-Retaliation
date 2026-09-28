package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementBuildingScreen extends Screen {
    private final SettlementManagementScreen parent;
    private final SettlementBuildingDraft draft;
    private List<SettlementBuildingPreviewPayload.Builder> candidates = List.of();
    private List<SettlementBuildingPreviewPayload.Material> requirements = List.of();
    private Builders builders;
    private Materials materials;
    private Button request;
    private int left;
    private int panelWidth;
    private int materialTop;

    SettlementBuildingScreen(SettlementManagementScreen parent) {
        super(SettlementManagementScreen.text("choose_building_builder"));
        this.parent = parent;
        this.draft = parent.buildingDraft();
    }

    SettlementManagementScreen parent() {
        return this.parent;
    }

    void result(SettlementManagementActionResultPayload payload) {
        if (!this.draft.request.equals(payload.request())) return;
        this.draft.submitting = false;
        this.draft.failure = payload.failure();
        if (payload.applied()) {
            this.parent.clearBuildingDraft();
            this.minecraft.setScreen(this.parent);
            this.parent.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, "");
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(520, this.width - 24);
        this.left = (this.width - this.panelWidth) / 2;
        int builderHeight = Math.clamp((this.height - 170) / 2, 32, 100);
        this.materialTop = 86 + builderHeight + 22;
        this.builders = addRenderableWidget(new Builders(builderHeight));
        this.materials = addRenderableWidget(new Materials());
        int third = (this.panelWidth - 12) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.height - 28, third, 20).build());
        addRenderableWidget(Button.builder(SettlementManagementScreen.text("change_prefab"), ignored -> this.parent.newBuilding())
                .bounds(this.left + third + 6, this.height - 28, third, 20).build());
        this.request = addRenderableWidget(Button.builder(SettlementManagementScreen.text("request_build"), ignored -> {
            if (!this.draft.ready()) return;
            this.draft.submitting();
            PacketDistributor.sendToServer(new SettlementManagementActionPayload(this.parent.snapshot().header().id(),
                    new SettlementManagementActionPayload.CommissionBuilding(this.draft.request, this.draft.structure,
                            this.draft.anchor, this.draft.rotation, this.draft.builder)));
        }).bounds(this.left + (third + 6) * 2, this.height - 28, third, 20).build());
        refresh();
    }

    @Override
    public void tick() {
        this.draft.tick(this.parent.snapshot().header().id());
        refresh();
    }

    private void refresh() {
        if (this.draft.preview != null) {
            var next = this.draft.preview.builders();
            if (!this.candidates.equals(next) || this.builders.children().isEmpty() && !next.isEmpty()) {
                this.candidates = next;
                this.builders.refresh();
            }
            var materials = this.draft.preview.materials();
            if (this.requirements != materials) {
                this.requirements = materials;
                this.materials.refresh();
            }
        }
        this.request.active = this.draft.ready();
    }

    @Override
    public void onClose() {
        SettlementViewClient.resume(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.draft.label, this.panelWidth), this.left, 28, 0xD0D0D0);
        if (this.draft.preview != null) {
            graphics.drawString(this.font, SettlementManagementScreen.text("building_price", this.draft.preview.price(), this.draft.preview.funds().mixedFraction()), this.left, 44, 0xD0D0D0);
        }
        graphics.drawString(this.font, SettlementManagementScreen.text("building_builders"), this.left, 68, 0xFFFFFF);
        if (this.candidates.isEmpty()) graphics.drawString(this.font, SettlementManagementScreen.text("building_no_builders"), this.left + 6, 91, 0xB0B0B0);
        graphics.drawString(this.font, SettlementManagementScreen.text("building_materials"), this.left, this.materialTop - 14, 0xFFFFFF);
        String failure = this.draft.failure.isEmpty() && this.draft.preview != null ? this.draft.preview.failure() : this.draft.failure;
        if (failure.isEmpty() && this.draft.preview != null && this.draft.preview.funds().compareTo(com.jvn.villagerretaliation.sell.CurrencyAmount.of(this.draft.preview.price(), 1)) < 0) failure = "funds";
        Component hint = this.draft.submitting ? SettlementManagementScreen.text("saving") : failure.isEmpty()
                ? SettlementManagementScreen.text("building_supply_hint") : SettlementManagementScreen.text("building_failure_" + failure);
        graphics.drawWordWrap(this.font, hint, this.left, this.height - 63, this.panelWidth, failure.isEmpty() ? 0xB8B8B8 : 0xFF9999);
    }

    private final class Builders extends ObjectSelectionList<BuilderRow> {
        Builders(int height) {
            super(SettlementBuildingScreen.this.minecraft, panelWidth, height, 84, 22);
            setX(left);
        }
        void refresh() {
            clearEntries();
            for (var candidate : candidates) {
                var row = new BuilderRow(candidate);
                addEntry(row);
                if (candidate.id().equals(draft.builder)) setSelected(row);
            }
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class BuilderRow extends ObjectSelectionList.Entry<BuilderRow> {
        private final SettlementBuildingPreviewPayload.Builder builder;
        BuilderRow(SettlementBuildingPreviewPayload.Builder builder) { this.builder = builder; }
        @Override public Component getNarration() { return Component.literal(this.builder.name()); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0 || draft.submitting) return false;
            builders.setSelected(this);
            draft.chooseBuilder(this.builder.id());
            return true;
        }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, font.plainSubstrByWidth(this.builder.name(), width - 8), x + 4, y + 6, 0xFFFFFF);
        }
    }

    private final class Materials extends ObjectSelectionList<MaterialRow> {
        Materials() {
            super(SettlementBuildingScreen.this.minecraft, panelWidth, Math.max(32, SettlementBuildingScreen.this.height - materialTop - 76), materialTop, 20);
            setX(left);
        }
        void refresh() {
            double scroll = getScrollAmount();
            clearEntries();
            requirements.forEach(material -> addEntry(new MaterialRow(material)));
            setScrollAmount(scroll);
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class MaterialRow extends ObjectSelectionList.Entry<MaterialRow> {
        private final SettlementBuildingPreviewPayload.Material material;
        MaterialRow(SettlementBuildingPreviewPayload.Material material) { this.material = material; }
        @Override public Component getNarration() { return this.material.item().getHoverName(); }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.renderItem(this.material.item(), x + 3, y + 1);
            String count = this.material.available() + " / " + this.material.required();
            int countWidth = font.width(count);
            graphics.drawString(font, font.plainSubstrByWidth(this.material.item().getHoverName().getString(), width - countWidth - 34), x + 25, y + 5, 0xDDDDDD);
            graphics.drawString(font, count, x + width - countWidth - 4, y + 5, this.material.available() < this.material.required() ? 0xE5AD76 : 0x9BC59B);
        }
    }
}
