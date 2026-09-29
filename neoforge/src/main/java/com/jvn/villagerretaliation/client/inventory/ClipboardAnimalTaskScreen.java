package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload.Action;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClipboardAnimalTaskScreen extends Screen {
    private static final List<Action> ACTIONS = List.of(Action.BREED_TASK, Action.SHEAR_TASK, Action.MILK_TASK, Action.CULL_TASK, Action.COLLECT_TASK);
    private final Screen parent;
    private final UUID worker;
    private boolean append;
    private int selected;
    private int left;
    private int top;
    private int panelWidth;

    public ClipboardAnimalTaskScreen(Screen parent, UUID worker, boolean append) {
        super(Component.literal("Animal task"));
        this.parent = parent;
        this.worker = worker;
        this.append = append;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(320, this.width - 32);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - 154) / 2;
        addRenderableWidget(Button.builder(label(), button -> {
            this.selected = (this.selected + 1) % ACTIONS.size();
            button.setMessage(label());
        }).bounds(this.left, this.top + 28, this.panelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(this.append ? "Append to queue" : "Replace pending tasks"), button -> {
            this.append = !this.append;
            button.setMessage(Component.literal(this.append ? "Append to queue" : "Replace pending tasks"));
        }).bounds(this.left, this.top + 58, this.panelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), ignored -> {
            PacketDistributor.sendToServer(new ClipboardWorkAreaActionPayload(this.worker, ACTIONS.get(this.selected), this.append ? 5 : 1));
            onClose();
        }).bounds(this.left, this.top + 126, this.panelWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(this.left + this.panelWidth / 2 + 2, this.top + 126, this.panelWidth / 2 - 2, 20).build());
    }

    private Component label() { return Component.literal(ACTIONS.get(this.selected).taskKind().label()); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + 154, 0xEF151A20);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top + 4, 0xFFFFFF, false);
        String hint = ACTIONS.get(this.selected) == Action.COLLECT_TASK
                ? "Uses the Clipboard's draft bounds" : "Shift-click an animal with the Clipboard first";
        graphics.drawWordWrap(this.font, Component.literal(hint), this.left, this.top + 91, this.panelWidth, 0xBBC5D0);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
