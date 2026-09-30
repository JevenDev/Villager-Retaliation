package com.jvn.villagerretaliation.client.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;

public abstract class VillagerTaskScreen extends VillagerWindowScreen {
    protected VillagerTaskScreen(Component title) { super(title); }

    protected void queueMode(int y, BooleanSupplier append, Runnable toggle) {
        var button = addRenderableWidget(VillagerUi.buttonBuilder(queueLabel(append.getAsBoolean()), ignored -> {
            toggle.run();
            rebuildWidgets();
        }).bounds(this.left, y, this.panelWidth, 20).build());
        ((VillagerUi.OptionButton) button).checkbox(true, append.getAsBoolean());
    }

    private static Component queueLabel(boolean append) {
        return Component.literal(append ? "Append to queue" : "Replace pending tasks");
    }

    protected void taskActions(int y, Component label, Runnable submit) {
        addRenderableWidget(VillagerUi.buttonBuilder(label, ignored -> submit.run())
                .bounds(this.left, y, this.panelWidth / 2 - 2, 20).build());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(this.left + this.panelWidth / 2 + 2, y, this.panelWidth / 2 - 2, 20).build());
    }
}
