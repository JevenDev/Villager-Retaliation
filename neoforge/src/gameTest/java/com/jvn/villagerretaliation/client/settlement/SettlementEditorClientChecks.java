package com.jvn.villagerretaliation.client.settlement;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

final class SettlementEditorClientChecks {
    private final SettlementViewScreen view;
    private final BlockPos center;
    private final Consumer<String> capture;
    private int phase;
    private int ticks;

    SettlementEditorClientChecks(SettlementViewScreen view, BlockPos center, Consumer<String> capture) {
        this.view = view;
        this.center = center;
        this.capture = capture;
    }

    boolean tick() {
        if (++this.ticks > 500) throw new IllegalStateException("Editor checks timed out at " + this.phase + ": " + this.view.constructionOverlay().status());
        if (this.phase == 0) {
            SettlementViewPanelClientChecks.verify(this.view);
            overlay("Preset: Construction");
            overlay("  Structure previews: Off");
            this.phase++;
        } else if (this.phase == 1 && this.view.constructionOverlay().status().startsWith("Structure previews: 1/1")) {
            this.view.focus(this.center.getX() + 15, this.center.getZ() + 15);
            this.phase = 3;
            this.ticks = 0;
        } else if (this.phase == 3 && this.ticks > 25) {
            this.capture.accept("editor-structure-preview");
            String status = this.view.constructionOverlay().status();
            this.view.constructionOverlay().accept(new com.jvn.villagerretaliation.network.SettlementProjectPreviewPayload(
                    this.view.session(), java.util.UUID.randomUUID(), 1, null));
            require(this.view.constructionOverlay().status().equals(status), "unrelated preview responses cannot overwrite the visible structure");
            this.phase = 4;
        } else if (this.phase == 4) {
            this.view.focus(this.center.getX(), this.center.getZ());
            overlay("  Structure previews: On");
            require(this.view.constructionOverlay().status().startsWith("Structure previews: 0/0"), "disabling ghosts releases cached geometry");
            overlay("Preset: Logistics");
            button("Overlays").onPress();
            require(this.view.contextPopup().buttons().stream().anyMatch(button -> button.getMessage().getString().equals("Storage links: On")), "Logistics enables storage links");
            this.view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            button("Build").onPress();
            this.view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals("Path")).findFirst().orElseThrow().onPress();
            var draft = this.view.pathEditor().draft();
            draft.place(this.center.offset(2, -1, 7).getCenter().add(0, 0.48, 0));
            draft.finish();
            draft.place(this.center.offset(7, -1, 7).getCenter().add(0, 0.48, 0));
            draft.finish();
            draft.stopExtending();
            this.phase = 2;
            this.ticks = 0;
        } else if (this.phase == 2 && this.view.pathEditor().canConfirm() && this.ticks > 10) {
            require(this.view.buildOverlay().summary().stream().anyMatch(line -> line.getString().startsWith("Valid placement")), "validated path summary distinguishes queued work from rejection");
            require(this.view.buildOverlay().summary().stream().anyMatch(line -> line.getString().startsWith("Size:")), "summary includes footprint and elevation");
            require(button("Confirm").getY() == button("Cancel").getY(), "placement actions share the same row");
            this.capture.accept("editor-placement-summary");
            int width = this.view.width, height = this.view.height;
            this.view.resize(Minecraft.getInstance(), 320, 180);
            require(button("Confirm").getBottom() < 180 && button("Cancel").getY() >= SettlementViewLayout.HEADER_HEIGHT, "small-screen placement actions remain visible");
            this.view.resize(Minecraft.getInstance(), width, height);
            button("Cancel").onPress();
            return true;
        }
        return false;
    }

    private void overlay(String label) {
        button("Overlays").onPress();
        this.view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow().onPress();
    }

    private Button button(String label) {
        return this.view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new IllegalStateException("Missing button: " + label));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
