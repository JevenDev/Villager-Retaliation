package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.ui.VillagerTaskScreen;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload.Action;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClipboardAnimalTaskScreen extends VillagerTaskScreen {
    private static final List<Action> ACTIONS = List.of(Action.BREED_TASK, Action.SHEAR_TASK, Action.MILK_TASK, Action.CULL_TASK, Action.COLLECT_TASK);
    private final Screen parent;
    private final UUID worker;
    private boolean append;
    private int selected;

    public ClipboardAnimalTaskScreen(Screen parent, UUID worker, boolean append) {
        super(Component.literal("Animal task"));
        this.parent = parent;
        this.worker = worker;
        this.append = append;
    }

    @Override
    protected void init() {
        initWindow(320, 154);
        addRenderableWidget(VillagerUi.buttonBuilder(label(), button -> {
            this.selected = (this.selected + 1) % ACTIONS.size();
            button.setMessage(label());
        }).bounds(this.left, this.top + 28, this.panelWidth, 20).build());
        queueMode(this.top + 58, () -> this.append, () -> this.append = !this.append);
        taskActions(this.top + 126, Component.literal("Apply"), () -> {
            PacketDistributor.sendToServer(new ClipboardWorkAreaActionPayload(this.worker, ACTIONS.get(this.selected), this.append ? 5 : 1));
            onClose();
        });
    }

    private Component label() { return Component.literal(ACTIONS.get(this.selected).taskKind().label()); }

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
