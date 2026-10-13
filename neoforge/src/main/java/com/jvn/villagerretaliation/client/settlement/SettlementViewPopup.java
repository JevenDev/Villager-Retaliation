package com.jvn.villagerretaliation.client.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class SettlementViewPopup {
    private final Target target;
    private final int anchorX;
    private final int anchorY;
    private final List<Button> buttons = new ArrayList<>();
    private List<FormattedCharSequence> lines = List.of();
    private List<Action> actions = List.of();
    private Component narration = Component.empty();
    private int left;
    private int top;
    private int width;
    private int height;
    private int textHeight;
    private int contentHeight;
    private int scroll;
    private int focus = -1;

    SettlementViewPopup(Target target, double x, double y) {
        this.target = target;
        this.anchorX = (int) x;
        this.anchorY = (int) y;
    }

    int anchorX() {
        return this.anchorX;
    }

    int anchorY() {
        return this.anchorY;
    }

    Target target() {
        return this.target;
    }

    List<Button> buttons() {
        return List.copyOf(this.buttons);
    }

    Component narration() {
        return this.narration;
    }

    void update(Font font, int viewportWidth, int viewportHeight, List<Component> details, List<Action> actions) {
        this.width = Math.min(224, viewportWidth - 8);
        this.lines = details.stream().flatMap(line -> font.split(line, this.width - 16).stream()).toList();
        this.textHeight = this.lines.size() * (font.lineHeight + 2) + 12;
        this.actions = List.copyOf(actions);
        this.contentHeight = this.textHeight + actions.size() * 22 + 4;
        this.height = Math.min(this.contentHeight, viewportHeight - 8);
        this.left = Math.clamp(this.anchorX, 4, viewportWidth - this.width - 4);
        this.top = Math.clamp(this.anchorY, 4, viewportHeight - this.height - 4);
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.contentHeight - this.height));
        this.focus = Math.min(this.focus, actions.size() - 1);
        var narration = Component.empty();
        for (var line : details) narration.append(line).append(". ");
        for (var action : actions) narration.append(action.label()).append(". ");
        this.narration = narration;
        layoutButtons();
    }

    private void layoutButtons() {
        this.buttons.clear();
        for (int index = 0; index < this.actions.size(); index++) {
            var action = this.actions.get(index);
            Button button = com.jvn.villagerretaliation.client.ui.VillagerUi.buttonBuilder(action.label(), ignored -> action.run().run())
                    .bounds(this.left + 8, this.top + this.textHeight + index * 22 - this.scroll, this.width - 16, 20).build();
            button.active = action.enabled();
            button.setFocused(index == this.focus);
            this.buttons.add(button);
        }
    }

    boolean contains(double x, double y) {
        return x >= this.left && x < this.left + this.width && y >= this.top && y < this.top + this.height;
    }

    boolean click(double x, double y, int button) {
        if (!contains(x, y)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (var action : this.buttons) {
                if (y >= this.top + 4 && y < this.top + this.height - 4 && action.mouseClicked(x, y, button)) {
                    return true;
                }
            }
        }
        return true;
    }

    boolean scroll(double x, double y, double amount) {
        if (!contains(x, y)) {
            return false;
        }
        this.scroll = (int) Math.clamp(this.scroll - amount * 22, 0, Math.max(0, this.contentHeight - this.height));
        layoutButtons();
        return true;
    }

    boolean key(int key) {
        if (this.actions.isEmpty()) {
            return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_TAB;
        }
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int direction = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_TAB && net.minecraft.client.gui.screens.Screen.hasShiftDown() ? -1 : 1;
            this.focus = Math.floorMod(this.focus + direction, this.actions.size());
            int actionTop = this.textHeight + this.focus * 22;
            this.scroll = Math.clamp(this.scroll, Math.max(0, actionTop + 24 - this.height), actionTop);
            layoutButtons();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && this.focus >= 0) {
            var action = this.actions.get(this.focus);
            if (action.enabled()) action.run().run();
            return true;
        }
        return false;
    }

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        SettlementViewUi.frame(graphics, this.left, this.top, this.width, this.height);
        graphics.enableScissor(this.left, this.top + 4, this.left + this.width, this.top + this.height - 4);
        int y = this.top + 8 - this.scroll;
        for (var line : this.lines) {
            graphics.drawString(font, line, this.left + 8, y, 0xFFFFFF);
            y += font.lineHeight + 2;
        }
        for (var button : this.buttons) button.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        if (this.contentHeight > this.height) {
            int track = this.height - 8;
            int thumb = Math.max(8, track * this.height / this.contentHeight);
            int offset = (track - thumb) * this.scroll / (this.contentHeight - this.height);
            graphics.fill(this.left + this.width - 4, this.top + 4 + offset, this.left + this.width - 2,
                    this.top + 4 + offset + thumb, 0xFFBBBBBB);
        }
        graphics.pose().popPose();
    }

    enum Kind {
        RESOURCE, RESIDENT, AREA, DRAFT, CHOOSER, ACTIONS, ASSIGN, CONFIRM_ASSIGN, RESULTS, OVERLAYS, OVERVIEW, TREASURY, FOOD, NAVIGATION, FILTERS
    }

    record Target(Kind kind, UUID id, List<UUID> choices) {
        Target {
            choices = List.copyOf(choices);
        }

        static Target of(Kind kind, UUID id) {
            return new Target(kind, id, List.of());
        }
    }

    record Action(Component label, boolean enabled, Runnable run) {
    }
}
