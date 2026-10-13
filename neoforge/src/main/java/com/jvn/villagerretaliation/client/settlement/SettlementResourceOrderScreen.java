package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerTaskScreen;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.interaction.work.ResourceWorkOrders;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

final class SettlementResourceOrderScreen extends VillagerTaskScreen {
    private final SettlementViewScreen view;
    private final BlockPos start;
    private final BlockPos end;
    private HiredTemporaryTask.Kind kind;
    private Direction direction = Direction.NORTH;
    private final String[] dimensions = {"12", "3", "3"};
    private String material = "";
    private String feedback = "";
    private boolean append;

    SettlementResourceOrderScreen(SettlementViewScreen view, HiredTemporaryTask.Kind kind, BlockPos start, BlockPos end, boolean append) {
        super(Component.literal(kind.label()));
        this.view = view;
        this.kind = kind;
        this.start = start.immutable();
        this.end = end.immutable();
        this.append = append;
    }

    SettlementManagementScreen parent() { return this.view.parent(); }

    @Override
    protected void init() {
        boolean shaped = shaped();
        initWindow(310, shaped ? 230 : this.kind.forest() ? 132 : filterable() ? 180 : 142);
        if (shaped) addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Direction: " + this.direction.getName()), button -> {
            this.direction = this.direction.getClockWise();
            button.setMessage(Component.literal("Direction: " + this.direction.getName()));
            updatePreview();
        }).bounds(this.left, this.top + 26, this.panelWidth, 20).build());
        String[] labels = {this.kind.staircase() ? "Steps down" : this.kind == HiredTemporaryTask.Kind.TUNNEL ? "Length" : "Depth", "Width", "Height"};
        int fields = this.kind.forest() ? 0 : shaped ? (this.kind.staircase() ? 2 : 3) : 1;
        for (int i = 0; i < fields; i++) {
            int index = i;
            var field = new EditBox(this.font, this.left + i * (this.panelWidth / fields), this.top + (shaped ? 67 : 45),
                    this.panelWidth / fields - 5, 20, Component.literal(labels[i]));
            field.setMaxLength(2);
            field.setValue(this.dimensions[i]);
            field.setResponder(value -> { this.dimensions[index] = value; updatePreview(); });
            addRenderableWidget(field);
        }
        if (filterable()) {
            var filter = new EditBox(this.font, this.left, this.top + (shaped ? 110 : 88), this.panelWidth, 20, Component.literal("Ore block or #tag"));
            filter.setMaxLength(128);
            filter.setValue(this.material);
            filter.setHint(Component.literal("Any ore, or #c:ores/iron"));
            filter.setResponder(value -> { this.material = value.trim(); updatePreview(); });
            addRenderableWidget(filter);
        }
        if (this.kind.forest()) addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(this.kind == HiredTemporaryTask.Kind.FOREST_REPLANT ? "Replant: on" : "Replant: off"), button -> {
            this.kind = this.kind == HiredTemporaryTask.Kind.FOREST ? HiredTemporaryTask.Kind.FOREST_REPLANT : HiredTemporaryTask.Kind.FOREST;
            button.setMessage(Component.literal(this.kind == HiredTemporaryTask.Kind.FOREST_REPLANT ? "Replant: on" : "Replant: off"));
            updatePreview();
        }).bounds(this.left, this.top + 32, this.panelWidth, 20).build());
        else if (this.kind.staircase()) addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(this.kind == HiredTemporaryTask.Kind.STAIRCASE_PROSPECT ? "Seek ores at bottom: on" : "Seek ores at bottom: off"), button -> {
            this.kind = this.kind == HiredTemporaryTask.Kind.STAIRCASE ? HiredTemporaryTask.Kind.STAIRCASE_PROSPECT : HiredTemporaryTask.Kind.STAIRCASE;
            button.setMessage(Component.literal(this.kind == HiredTemporaryTask.Kind.STAIRCASE_PROSPECT ? "Seek ores at bottom: on" : "Seek ores at bottom: off"));
            updatePreview();
        }).bounds(this.left, this.top + 137, this.panelWidth, 20).build());
        queueMode(this.top + this.panelHeight - 67, () -> this.append, () -> this.append = !this.append);
        taskActions(this.top + this.panelHeight - 23, Component.literal("Issue order"), this::submit);
        updatePreview();
    }

    private boolean shaped() { return this.kind == HiredTemporaryTask.Kind.TUNNEL || this.kind.staircase(); }

    private boolean filterable() { return this.kind == HiredTemporaryTask.Kind.ORE_AREA || this.kind.staircase(); }

    private HiredTemporaryTask task() {
        return ResourceWorkOrders.create(this.kind, this.start, this.end, this.direction,
                Integer.parseInt(this.dimensions[0]), Integer.parseInt(this.dimensions[1]), Integer.parseInt(this.dimensions[2]),
                this.material, this.minecraft.level.getMinBuildHeight(), this.minecraft.level.getMaxBuildHeight());
    }

    private void updatePreview() {
        if (this.minecraft == null || this.minecraft.level == null) return;
        try {
            this.view.previewResourceOrder(task());
            this.feedback = "";
        } catch (IllegalArgumentException exception) {
            this.view.previewResourceOrder(null);
            this.feedback = "Check dimensions, world height, and material";
        }
    }

    private void submit() {
        try {
            var task = task();
            if (!this.view.issueResourceOrder(task, this.append)) {
                this.feedback = "Select available workers for this job";
                return;
            }
            onClose();
        } catch (IllegalArgumentException exception) {
            this.feedback = "Check dimensions, world height, and material";
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.kind.label(), this.left, this.top + 5, 0xFFFFFF, false);
        String[] labels = {this.kind.staircase() ? "Steps down" : this.kind == HiredTemporaryTask.Kind.TUNNEL ? "Length" : "Depth", "Width", "Height"};
        int fields = this.kind.forest() ? 0 : shaped() ? (this.kind.staircase() ? 2 : 3) : 1;
        for (int i = 0; i < fields; i++) graphics.drawString(this.font, labels[i], this.left + i * (this.panelWidth / fields), this.top + (shaped() ? 54 : 32), 0xBBC5D0, false);
        if (filterable()) graphics.drawString(this.font, "Ore block or #tag (optional)", this.left, this.top + (shaped() ? 97 : 75), 0xBBC5D0, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.feedback.isEmpty() ? this.view.resourceSurveySummary() : this.feedback, this.panelWidth),
                this.left, this.top + this.panelHeight - 39, 0xE6B86C, false);
    }

    @Override
    public void onClose() {
        this.view.previewResourceOrder(null);
        SettlementViewClient.resume(parent());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
