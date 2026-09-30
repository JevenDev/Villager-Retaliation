package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class SettlementTemporaryTaskScreen extends Screen {
    private final SettlementViewScreen view;
    private final List<SettlementViewBatchPayload.Target> targets;
    private final String[] values;
    private HiredTemporaryTask.Kind kind;
    private boolean append;
    private String feedback = "";
    private int left;
    private int top;
    private int panelWidth;
    private EditBox quantity;
    private final java.util.List<EditBox> bounds = new java.util.ArrayList<>();
    private final BlockPos selectedTarget;
    private final UUID selectedEntity;

    SettlementTemporaryTaskScreen(SettlementViewScreen view, List<SettlementViewBatchPayload.Target> targets,
                                  HiredTemporaryTask.Kind kind, BlockPos min, BlockPos max, boolean append) {
        this(view, targets, kind, min, max, append, min);
    }

    SettlementTemporaryTaskScreen(SettlementViewScreen view, List<SettlementViewBatchPayload.Target> targets,
                                  HiredTemporaryTask.Kind kind, BlockPos min, BlockPos max, boolean append, BlockPos selectedTarget) {
        this(view, targets, kind, min, max, append, selectedTarget, null);
    }

    SettlementTemporaryTaskScreen(SettlementViewScreen view, List<SettlementViewBatchPayload.Target> targets,
                                  HiredTemporaryTask.Kind kind, BlockPos min, BlockPos max, boolean append, BlockPos selectedTarget, UUID selectedEntity) {
        super(Component.literal("Temporary task"));
        this.selectedEntity = selectedEntity;
        this.selectedTarget = selectedTarget.immutable();
        this.view = view;
        this.targets = List.copyOf(targets);
        this.kind = kind;
        this.append = append;
        this.values = new String[] {Integer.toString(min.getX()), Integer.toString(min.getY()), Integer.toString(min.getZ()),
                Integer.toString(max.getX()), Integer.toString(max.getY()), Integer.toString(max.getZ()), "1"};
    }

    SettlementManagementScreen parent() { return this.view.parent(); }

    @Override
    protected void init() {
        this.bounds.clear();
        var layout = SettlementViewClient.panelLayout(this.width, this.height, Math.min(360, this.width - 32), 204, true);
        this.panelWidth = layout.width();
        this.left = layout.left();
        this.top = layout.top();
        addRenderableWidget(Button.builder(Component.literal(this.kind.label()), button -> {
            this.kind = HiredTemporaryTask.Kind.values()[(this.kind.ordinal() + 1) % HiredTemporaryTask.Kind.values().length];
            button.setMessage(Component.literal(this.kind.label()));
            this.quantity.active = this.kind == HiredTemporaryTask.Kind.FISH;
            this.bounds.forEach(field -> field.active = editableBounds());
        }).bounds(this.left, this.top + 22, this.panelWidth, 20).build());
        int cell = (this.panelWidth - 44) / 3;
        for (int i = 0; i < 6; i++) {
            int index = i;
            var field = new EditBox(this.font, this.left + 40 + i % 3 * cell, this.top + 56 + i / 3 * 28,
                    cell - 4, 20, Component.literal((i < 3 ? "Min " : "Max ") + "XYZ".charAt(i % 3)));
            field.setMaxLength(10);
            field.setValue(this.values[i]);
            field.setResponder(value -> this.values[index] = value);
            field.active = editableBounds();
            this.bounds.add(field);
            addRenderableWidget(field);
        }
        this.quantity = new EditBox(this.font, this.left + this.panelWidth - 72, this.top + 112, 72, 20, Component.literal("Catches"));
        this.quantity.setMaxLength(4);
        this.quantity.setValue(this.values[6]);
        this.quantity.setResponder(value -> this.values[6] = value);
        this.quantity.active = this.kind == HiredTemporaryTask.Kind.FISH;
        addRenderableWidget(this.quantity);
        addRenderableWidget(Button.builder(Component.literal(this.append ? "Append to queue" : "Replace pending tasks"), button -> {
            this.append = !this.append;
            button.setMessage(Component.literal(this.append ? "Append to queue" : "Replace pending tasks"));
        }).bounds(this.left, this.top + 140, this.panelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply to " + this.targets.size() + (this.targets.size() == 1 ? " worker" : " workers")), ignored -> submit())
                .bounds(this.left, this.top + 180, this.panelWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(this.left + this.panelWidth / 2 + 2, this.top + 180, this.panelWidth / 2 - 2, 20).build());
    }

    private boolean editableBounds() { return this.kind != HiredTemporaryTask.Kind.TREE && this.kind != HiredTemporaryTask.Kind.HUNT; }

    private void submit() {
        if (this.kind.entityTarget() && this.selectedEntity == null) {
            this.feedback = "Select a visible animal or eligible hunting target";
            return;
        }
        try {
            var min = new BlockPos(Integer.parseInt(this.values[0]), Integer.parseInt(this.values[1]), Integer.parseInt(this.values[2]));
            var max = new BlockPos(Integer.parseInt(this.values[3]), Integer.parseInt(this.values[4]), Integer.parseInt(this.values[5]));
            int quantity = this.kind == HiredTemporaryTask.Kind.FISH ? Integer.parseInt(this.values[6]) : 1;
            var task = this.kind == HiredTemporaryTask.Kind.TREE
                    ? new HiredTemporaryTask(UUID.randomUUID(), this.kind, this.selectedTarget, this.selectedTarget, 1, this.selectedTarget)
                    : new HiredTemporaryTask(UUID.randomUUID(), this.kind, min, max, quantity, null, this.kind.entityTarget() ? this.selectedEntity : null);
            this.view.submitTemporaryTask(this.targets, task, this.append);
            onClose();
        } catch (IllegalArgumentException exception) {
            this.feedback = "Check bounds (max 64) and catches (1-4096)";
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!SettlementViewClient.renderPanelBackground(graphics, this.left, this.top, this.panelWidth, 204)) {
            graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + 208, 0xEF151A20);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cell = (this.panelWidth - 44) / 3;
        for (int i = 0; i < 3; i++) graphics.drawString(this.font, String.valueOf("XYZ".charAt(i)),
                this.left + 40 + i * cell, this.top + 45, 0xBBC5D0, false);
        graphics.drawString(this.font, this.title, this.left, this.top + 4, 0xFFFFFF, false);
        graphics.drawString(this.font, "Min", this.left, this.top + 62, 0xBBC5D0, false);
        graphics.drawString(this.font, "Max", this.left, this.top + 90, 0xBBC5D0, false);
        graphics.drawString(this.font, "Catches for fishing", this.left, this.top + 118, 0xBBC5D0, false);
        String notice = this.feedback.isEmpty() && this.kind == HiredTemporaryTask.Kind.TREE ? "Tree bounds include branches and leaves" : this.feedback;
        if (notice.isEmpty() && this.kind.entityTarget()) notice = this.selectedEntity == null ? "Select an animal on the map first" : "Selected target, current job rules apply";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(notice, this.panelWidth), this.left, this.top + 166, 0xE6B86C, false);
    }

    @Override
    public void onClose() { SettlementViewClient.resume(parent()); }

    @Override
    public boolean isPauseScreen() { return false; }
}
