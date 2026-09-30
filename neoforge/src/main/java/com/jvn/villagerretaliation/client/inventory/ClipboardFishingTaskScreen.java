package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.ui.VillagerTaskScreen;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClipboardFishingTaskScreen extends VillagerTaskScreen {
    private final Screen parent;
    private final UUID worker;
    private boolean append;
    private String quantity = "1";
    private String feedback = "";

    public ClipboardFishingTaskScreen(Screen parent, UUID worker, boolean append) {
        super(Component.literal("Catch fish in draft bounds"));
        this.parent = parent;
        this.worker = worker;
        this.append = append;
    }

    @Override
    protected void init() {
        initWindow(300, 146);
        var count = new EditBox(this.font, this.left + this.panelWidth - 72, this.top + 26, 72, 20, Component.literal("Catches"));
        count.setMaxLength(4);
        count.setValue(this.quantity);
        count.setResponder(value -> this.quantity = value);
        addRenderableWidget(count);
        queueMode(this.top + 58, () -> this.append, () -> this.append = !this.append);
        taskActions(this.top + 116, Component.literal("Apply"), this::submit);
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
