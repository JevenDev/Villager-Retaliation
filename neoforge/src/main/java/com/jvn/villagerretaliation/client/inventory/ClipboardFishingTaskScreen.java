package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClipboardFishingTaskScreen extends Screen {
    private final Screen parent;
    private final UUID worker;
    private boolean append;
    private String quantity = "1";
    private String feedback = "";
    private int left;
    private int top;
    private int panelWidth;

    public ClipboardFishingTaskScreen(Screen parent, UUID worker, boolean append) {
        super(Component.literal("Catch fish in draft bounds"));
        this.parent = parent;
        this.worker = worker;
        this.append = append;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(300, this.width - 32);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - 146) / 2;
        var count = new EditBox(this.font, this.left + this.panelWidth - 72, this.top + 26, 72, 20, Component.literal("Catches"));
        count.setMaxLength(4);
        count.setValue(this.quantity);
        count.setResponder(value -> this.quantity = value);
        addRenderableWidget(count);
        addRenderableWidget(Button.builder(Component.literal(this.append ? "Append to queue" : "Replace pending tasks"), button -> {
            this.append = !this.append;
            button.setMessage(Component.literal(this.append ? "Append to queue" : "Replace pending tasks"));
        }).bounds(this.left, this.top + 58, this.panelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), ignored -> submit())
                .bounds(this.left, this.top + 116, this.panelWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(this.left + this.panelWidth / 2 + 2, this.top + 116, this.panelWidth / 2 - 2, 20).build());
    }

    private void submit() {
        try {
            int count = Integer.parseInt(this.quantity);
            if (count < 1 || count > HiredTemporaryTask.MAX_CATCHES) throw new IllegalArgumentException("Invalid catch count");
            PacketDistributor.sendToServer(new ClipboardWorkAreaActionPayload(this.worker,
                    ClipboardWorkAreaActionPayload.Action.TEMPORARY_TASK, this.append ? 5 : 1, count));
            onClose();
        } catch (IllegalArgumentException exception) {
            this.feedback = "Enter a catch count from 1 to 4096";
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + 146, 0xEF151A20);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top + 4, 0xFFFFFF, false);
        graphics.drawString(this.font, "Number of catches", this.left, this.top + 32, 0xBBC5D0, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.feedback, this.panelWidth), this.left, this.top + 94, 0xE6B86C, false);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
