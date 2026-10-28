package com.jvn.villagerretaliation.client.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class SettlementViewPopup {
    private static final int PADDING = 4;
    private static final int HIT_PADDING = 2;
    private final Target target;
    private final int anchorX;
    private final int anchorY;
    private final List<Button> buttons = new ArrayList<>();
    private List<FormattedCharSequence> lines = List.of();
    private List<FormattedCharSequence> hints = List.of();
    private List<Action> actions = List.of();
    private Component narration = Component.empty();
    private int left;
    private int top;
    private int width;
    private int height;
    private int textHeight;
    private int contentHeight;
    private int bodyHeight;
    private int bodyWidth;
    private int lineHeight;
    private int scroll;
    private int focus = -1;
    private boolean navigationSelection;
    private int mouseX = Integer.MIN_VALUE;
    private int mouseY = Integer.MIN_VALUE;

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
        return this.focus < 0 ? this.narration : this.narration.copy().append(". ").append(this.actions.get(this.focus).label());
    }

    void update(Font font, int viewportWidth, int viewportHeight, List<Component> details, List<Action> actions) {
        Object focused = this.focus >= 0 && this.focus < this.actions.size() ? this.actions.get(this.focus).key() : null;
        this.actions = List.copyOf(actions);
        if (focused != null) {
            this.focus = -1;
            for (int index = 0; index < this.actions.size(); index++) {
                if (focused.equals(this.actions.get(index).key())) {
                    this.focus = index;
                    break;
                }
            }
            if (this.focus < 0) this.navigationSelection = false;
        }
        Component hint = Component.translatable("villagerretaliation.gui.settlement_view.action_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        int textWidth = details.stream().mapToInt(font::width).max().orElse(0);
        for (var action : actions) textWidth = Math.max(textWidth, font.width(action.label()) + font.width("-> ") + (action.icon().isEmpty() ? 0 : 20));
        if (!actions.isEmpty()) textWidth = Math.max(textWidth, font.width(hint));
        this.width = Math.min(Math.min(260, viewportWidth - 16), textWidth + PADDING * 2);
        this.lineHeight = font.lineHeight + 1;
        var lines = new ArrayList<FormattedCharSequence>();
        for (int index = 0; index < details.size(); index++) {
            lines.addAll(font.split(details.get(index).copy().withStyle(index == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY), this.width - PADDING * 2));
        }
        this.lines = List.copyOf(lines);
        this.bodyWidth = this.lines.stream().mapToInt(font::width).max().orElse(0);
        this.textHeight = this.lines.size() * this.lineHeight + (this.lines.isEmpty() ? 0 : 2);
        if (!actions.isEmpty()) this.textHeight += 4;
        this.hints = actions.isEmpty() ? List.of() : font.split(hint, this.width - PADDING * 2);
        this.buttons.clear();
        this.contentHeight = this.textHeight;
        for (var action : actions) {
            var label = font.split(action.label(), this.width - PADDING * 2 - font.width("-> ") - (action.icon().isEmpty() ? 0 : 20));
            int labelWidth = label.stream().mapToInt(font::width).max().orElse(0) + font.width("-> ") + (action.icon().isEmpty() ? 0 : 20);
            Button button = new ActionButton(font, action, label, Math.min(this.width - PADDING * 2, labelWidth + HIT_PADDING), Math.max(label.size() * this.lineHeight, action.icon().isEmpty() ? 0 : 18));
            this.buttons.add(button);
            this.bodyWidth = Math.max(this.bodyWidth, labelWidth);
            this.contentHeight += button.getHeight();
        }
        int hintHeight = this.hints.isEmpty() ? 0 : this.hints.size() * this.lineHeight + 4;
        this.height = Math.min(this.contentHeight + PADDING * 2 + hintHeight, viewportHeight - 8);
        this.bodyHeight = this.height - PADDING * 2 - hintHeight;
        this.left = Math.clamp(this.anchorX, 4, viewportWidth - this.width - 4);
        this.top = Math.clamp(this.anchorY, 4, viewportHeight - this.height - 4);
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.contentHeight - this.bodyHeight));
        if (this.focus >= actions.size() || this.focus < 0 || !actions.get(this.focus).enabled()) {
            this.focus = -1;
            moveFocus(1, false);
        }
        var narration = Component.empty();
        for (var line : details) narration.append(line).append(". ");
        for (var action : actions) narration.append(action.label()).append(". ");
        if (!actions.isEmpty()) narration.append(hint);
        this.narration = narration;
        layoutButtons();
    }

    private void layoutButtons() {
        int y = this.top + PADDING + this.textHeight - this.scroll;
        for (int index = 0; index < this.buttons.size(); index++) {
            var button = this.buttons.get(index);
            button.setPosition(this.left + PADDING, y);
            button.setFocused(index == this.focus);
            y += button.getHeight();
        }
    }

    private void moveFocus(int direction, boolean wrap) {
        int next = this.focus < 0 ? direction > 0 ? -1 : this.actions.size() : this.focus;
        for (int count = 0; count < this.actions.size(); count++) {
            next += direction;
            if (!wrap && (next < 0 || next >= this.actions.size())) return;
            next = Math.floorMod(next, this.actions.size());
            if (!this.actions.get(next).enabled()) continue;
            this.focus = next;
            int actionTop = this.textHeight;
            for (int index = 0; index < next; index++) actionTop += this.buttons.get(index).getHeight();
            int actionBottom = actionTop + this.buttons.get(next).getHeight();
            if (actionTop < this.scroll) this.scroll = actionTop;
            if (actionBottom > this.scroll + this.bodyHeight) this.scroll = actionBottom - this.bodyHeight;
            if (direction < 0 && this.actions.subList(0, next).stream().noneMatch(Action::enabled)) this.scroll = 0;
            this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.contentHeight - this.bodyHeight));
            layoutButtons();
            return;
        }
    }

    boolean contains(double x, double y) {
        return x >= this.left && x < this.left + this.width && y >= this.top && y < this.top + this.height;
    }

    void hover(double x, double y) {
        if (this.mouseX == (int) Math.floor(x) && this.mouseY == (int) Math.floor(y)) return;
        this.mouseX = (int) Math.floor(x);
        this.mouseY = (int) Math.floor(y);
        this.navigationSelection = false;
        if (!contains(x, y) || y < this.top + PADDING || y >= this.top + PADDING + this.bodyHeight) return;
        for (int index = 0; index < this.buttons.size(); index++) {
            if (this.buttons.get(index).active && this.buttons.get(index).isMouseOver(x, y)) {
                this.focus = index;
                layoutButtons();
                return;
            }
        }
    }

    boolean click(double x, double y, int button) {
        if (!contains(x, y)) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.navigationSelection) {
                activate();
                return true;
            }
            if (y >= this.top + PADDING && y < this.top + PADDING + this.bodyHeight) {
                for (var action : this.buttons) {
                    if (x >= action.getX() && x < action.getRight() && y >= action.getY() && y < action.getBottom()) {
                        action.mouseClicked(x, y, button);
                        return true;
                    }
                }
            }
        }
        return true;
    }

    boolean scroll(double x, double y, double amount) {
        if (!contains(x, y)) return false;
        if (x < this.left + PADDING - HIT_PADDING || x >= this.left + PADDING + this.bodyWidth + HIT_PADDING
                || y < this.top + PADDING - HIT_PADDING || y >= this.top + PADDING + this.bodyHeight + HIT_PADDING) return true;
        if (amount != 0) {
            this.mouseX = (int) Math.floor(x);
            this.mouseY = (int) Math.floor(y);
            if (this.actions.stream().anyMatch(Action::enabled)) {
                this.navigationSelection = true;
                moveFocus(amount > 0 ? -1 : 1, false);
            } else {
                this.scroll = (int) Math.clamp(this.scroll - amount * this.lineHeight, 0, Math.max(0, this.contentHeight - this.bodyHeight));
                layoutButtons();
            }
        }
        return true;
    }

    boolean key(int key) {
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int direction = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_TAB && net.minecraft.client.gui.screens.Screen.hasShiftDown() ? -1 : 1;
            this.navigationSelection = true;
            moveFocus(direction, true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            activate();
            return true;
        }
        return false;
    }

    private void activate() {
        if (this.focus >= 0 && this.actions.get(this.focus).enabled()) this.buttons.get(this.focus).onPress();
    }

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1400);
        TooltipRenderUtil.renderTooltipBackground(graphics, this.left + PADDING, this.top + PADDING,
                this.width - PADDING * 2, this.height - PADDING * 2, 0);
        graphics.enableScissor(this.left + PADDING, this.top + PADDING, this.left + this.width - PADDING,
                this.top + PADDING + this.bodyHeight);
        int y = this.top + PADDING - this.scroll;
        for (int index = 0; index < this.lines.size(); index++) {
            graphics.drawString(font, this.lines.get(index), this.left + PADDING, y, 0xFFFFFF);
            y += this.lineHeight + (index == 0 ? 2 : 0);
        }
        for (var button : this.buttons) button.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        y = this.top + PADDING + this.bodyHeight + 4;
        for (var hint : this.hints) {
            graphics.drawString(font, hint, this.left + PADDING, y, 0xFFFFFF);
            y += this.lineHeight;
        }
        if (this.contentHeight > this.bodyHeight) {
            int thumb = Math.max(4, this.bodyHeight * this.bodyHeight / this.contentHeight);
            int offset = (this.bodyHeight - thumb) * this.scroll / (this.contentHeight - this.bodyHeight);
            graphics.fill(this.left + this.width - 2, this.top + PADDING + offset, this.left + this.width - 1,
                    this.top + PADDING + offset + thumb, 0xFFAAAAAA);
        }
        graphics.pose().popPose();
    }

    private static final class ActionButton extends Button {
        private final Font font;
        private final net.minecraft.world.item.ItemStack icon;
        private final List<FormattedCharSequence> lines;

        private ActionButton(Font font, Action action, List<FormattedCharSequence> lines, int width, int height) {
            super(0, 0, width, height, action.label(), ignored -> action.run().run(), DEFAULT_NARRATION);
            this.font = font;
            this.icon = action.icon();
            this.lines = lines;
            this.active = action.enabled();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = !this.active ? 0x555555 : isFocused() ? 0xFFFFFF : 0xAAAAAA;
            graphics.drawString(this.font, isFocused() ? "->" : ">", getX(), getY(), color);
            int textX = getX() + this.font.width("-> ");
            if (!this.icon.isEmpty()) {
                graphics.renderItem(this.icon, textX, getY());
                textX += 20;
            }
            int y = getY() + (this.icon.isEmpty() ? 0 : 4);
            for (var line : this.lines) {
                graphics.drawString(this.font, line, textX, y, color);
                y += this.font.lineHeight + 1;
            }
        }
    }

    enum Kind {
        BEDS, DEPOT, RESOURCE, RESIDENT, AREA, DRAFT, CHOOSER, ACTIONS, ASSIGN, CONFIRM_ASSIGN, RESULTS, OVERLAYS, OVERVIEW, TREASURY, FOOD, NAVIGATION, FILTERS
    }

    record Target(Kind kind, UUID id, List<UUID> choices) {
        Target {
            choices = List.copyOf(choices);
        }

        static Target of(Kind kind, UUID id) {
            return new Target(kind, id, List.of());
        }
    }

    record Action(Component label, boolean enabled, Runnable run, Object key, net.minecraft.world.item.ItemStack icon) {
        Action(Component label, boolean enabled, Runnable run, Object key) {
            this(label, enabled, run, key, net.minecraft.world.item.ItemStack.EMPTY);
        }
        Action(Component label, boolean enabled, Runnable run) {
            this(label, enabled, run, null);
        }
    }
}
