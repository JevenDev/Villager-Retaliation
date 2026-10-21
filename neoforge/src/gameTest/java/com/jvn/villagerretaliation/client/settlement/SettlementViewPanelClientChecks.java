package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

final class SettlementViewPanelClientChecks {
    private SettlementViewPanelClientChecks() { }

    static void verify(SettlementViewScreen view) {
        verifyPopup();
        SettlementStatusBarClientChecks.verify(view.parent().snapshot().header());
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.getCameraEntity();
        double x = view.focusX(), z = view.focusZ();
        float height = view.viewHeight(), yaw = view.cameraYaw();
        float projection = SettlementViewClient.projectionOffset();
        button(view, "villagerretaliation.gui.settlement_view.overview").onPress();
        require(minecraft.screen == view && view.contextPopup().target().kind() == SettlementViewPopup.Kind.OVERVIEW,
                "overview opens over the world without switching screens");
        var action = view.contextPopup().buttons().getFirst();
        view.mouseScrolled(action.getX() + 2, action.getY() + 2, 0, -1);
        require(view.contextPopup().buttons().get(1).isFocused() && view.viewHeight() == height,
                "tooltip scrolling selects an action without zooming the world");
        view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        button(view, "Treasury").onPress();
        require(view.contextPopup().target().kind() == SettlementViewPopup.Kind.TREASURY, "treasury opens a status popover");
        view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals("Settlement budget"))
                .findFirst().orElseThrow().onPress();
        require(minecraft.screen instanceof SettlementEditScreen, "budget editing opens its existing validated form");
        require(SettlementViewClient.projectionOffset() == projection && minecraft.getCameraEntity() == camera,
                "editing retains the remote camera and projection framing");
        require(minecraft.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .allMatch(button -> button.getX() >= view.width / 3 && button.getY() > SettlementViewLayout.HEADER_HEIGHT),
                "the compact editor leaves the world and status bar visible");
        minecraft.screen.onClose();
        require(minecraft.screen == view, "closing a status editor returns directly to the same world view");
        button(view, "Food supply").onPress();
        view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals("Farms"))
                .findFirst().orElseThrow().onPress();
        require(minecraft.screen == view && view.contextPopup() == null && !view.inspector().visible(),
                "food actions open the sidebar browser without a screen transition");
        button(view, "Residents").onPress();
        require(view.focusX() == x && view.focusZ() == z && view.viewHeight() == height && view.cameraYaw() == yaw,
                "status navigation preserves the world camera position and zoom");
        var filters = view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() == 80).findFirst().orElseThrow();
        filters.onPress();
        require(view.contextPopup().target().kind() == SettlementViewPopup.Kind.FILTERS, "browser filters open a direct-choice popover");
        view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals("Needs attention"))
                .findFirst().orElseThrow().onPress();
        require(view.contextPopup() == null && filters.getMessage().getString().startsWith("Needs attention"),
                "choosing a filter applies it and closes the popover");
        filters.onPress();
        view.contextPopup().buttons().getFirst().onPress();
        require(view.focusX() == x && view.focusZ() == z && view.viewHeight() == height && view.cameraYaw() == yaw,
                "filter selection preserves the world camera");
    }

    private static void verifyPopup() {
        var font = Minecraft.getInstance().font;
        var popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.ACTIONS, null), 630, 350);
        int[] selected = {-1};
        var details = java.util.List.<net.minecraft.network.chat.Component>of(net.minecraft.network.chat.Component.literal("Resident"),
                net.minecraft.network.chat.Component.literal("Health 20 | Food 10"));
        var actions = new java.util.ArrayList<SettlementViewPopup.Action>();
        for (int index = 0; index < 20; index++) {
            int action = index;
            actions.add(new SettlementViewPopup.Action(net.minecraft.network.chat.Component.literal("Action " + index),
                    index != 1, () -> selected[0] = action));
        }
        popup.update(font, 640, 360, details, actions.subList(0, 3));
        var first = popup.buttons().getFirst();
        double x = first.getX() + 4, y = first.getY() + 2;
        require(first.getHeight() == font.lineHeight + 1, "tooltip actions use compact text rows");
        require(first.isFocused(), "the first enabled tooltip action is selected");
        require(!popup.scroll(-1, -1, -1), "scrolling outside the tooltip is left to the view");
        popup.scroll(x, y, 0);
        require(popup.buttons().getFirst().isFocused(), "horizontal scrolling preserves the selected action");
        popup.scroll(x, y, -1);
        require(popup.buttons().get(2).isFocused() && selected[0] == -1,
                "the wheel selects the next enabled action without running it");
        popup.update(font, 640, 360, details, actions.subList(0, 3));
        require(popup.buttons().get(2).isFocused(), "live tooltip refresh preserves wheel selection");
        popup.click(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(selected[0] == 2, "click confirms the wheel selection even over a different row");
        selected[0] = -1;
        var disabled = popup.buttons().get(1);
        popup.hover(disabled.getX() + 4, disabled.getY() + 2);
        popup.click(disabled.getX() + 4, disabled.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(selected[0] == -1, "clicking a disabled row cannot run another action");
        popup.hover(x, y);
        require(popup.buttons().getFirst().isFocused(), "moving the pointer selects the hovered action");
        popup.key(GLFW.GLFW_KEY_UP);
        popup.key(GLFW.GLFW_KEY_ENTER);
        require(selected[0] == 2, "keyboard navigation wraps past disabled actions and confirms");
        popup.update(font, 200, 120, details, actions);
        for (int index = 0; index < 30; index++) popup.key(GLFW.GLFW_KEY_DOWN);
        var focused = popup.buttons().stream().filter(Button::isFocused).findFirst().orElseThrow();
        require(focused.getY() >= 4 && focused.getBottom() <= 116,
                "overflow navigation keeps the selected row inside the viewport");
        require(popup.contains(focused.getX() + 2, focused.getY() + 2), "overflow selection remains clickable");
        popup.update(font, 200, 120, details, java.util.List.of(new SettlementViewPopup.Action(
                net.minecraft.network.chat.Component.literal("Unavailable"), false, () -> selected[0] = 99)));
        selected[0] = -1;
        popup.scroll(popup.buttons().getFirst().getX() + 2, popup.buttons().getFirst().getY() + 2, -1);
        popup.key(GLFW.GLFW_KEY_ENTER);
        require(selected[0] == -1 && popup.buttons().stream().noneMatch(Button::isFocused),
                "a tooltip with no enabled actions cannot activate an entry");
        popup.update(font, 200, 120, details, java.util.List.of(new SettlementViewPopup.Action(
                net.minecraft.network.chat.Component.literal("A workplace name that is too long to fit on one tooltip row"), true, () -> {})));
        require(popup.buttons().getFirst().getHeight() > font.lineHeight + 1, "long action names wrap without truncation");
        var changing = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.ACTIONS, null), 4, 4);
        var enable = new SettlementViewPopup.Action(net.minecraft.network.chat.Component.literal("Enable work (1/2)"), true,
                () -> {}, SettlementViewAction.ENABLE);
        var pause = new SettlementViewPopup.Action(net.minecraft.network.chat.Component.literal("Pause work (1/2)"), true,
                () -> {}, SettlementViewAction.PAUSE);
        var assign = new SettlementViewPopup.Action(net.minecraft.network.chat.Component.literal("Assign to area"), true,
                () -> {}, SettlementViewAction.ASSIGN);
        changing.update(font, 640, 360, details, java.util.List.of(enable, pause, assign));
        var start = changing.buttons().getFirst();
        changing.scroll(start.getX() + 2, start.getY() + 2, -1);
        require(changing.buttons().get(1).isFocused(), "the work command can be selected before a live refresh");
        changing.update(font, 640, 360, details, java.util.List.of(new SettlementViewPopup.Action(
                net.minecraft.network.chat.Component.literal("Pause work (2/2)"), true, () -> {}, SettlementViewAction.PAUSE), assign));
        require(changing.buttons().getFirst().isFocused(), "filtering and count updates preserve the selected command by identity");
        popup.update(font, 200, 120, details, java.util.List.of());
        require(popup.buttons().isEmpty() && popup.key(GLFW.GLFW_KEY_DOWN), "informational tooltips support empty action lists");
    }

    static void verifyWindow(com.jvn.villagerretaliation.client.ui.VillagerWindowScreen screen) {
        var widgets = screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).toList();
        var fields = widgets.stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
        var values = fields.stream().map(net.minecraft.client.gui.components.EditBox::getValue).toList();
        var positions = widgets.stream().map(widget -> new int[]{widget.getX(), widget.getY()}).toList();
        var bounds = screen.windowBounds();
        int x = bounds.left() + 4, y = bounds.top() + 4;
        require(screen.mouseClicked(x, y, 0), "the window title can be grabbed");
        screen.mouseDragged(-1000, -1000, 0, -1000, -1000);
        screen.mouseReleased(-1000, -1000, 0);
        int dx = widgets.getFirst().getX() - positions.getFirst()[0];
        int dy = widgets.getFirst().getY() - positions.getFirst()[1];
        for (int index = 0; index < widgets.size(); index++) {
            var widget = widgets.get(index);
            require(widget.getX() == positions.get(index)[0] + dx && widget.getY() == positions.get(index)[1] + dy,
                    "dragging translates every control by the same amount");
            if (widget.visible) require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width
                    && widget.getBottom() <= screen.height, "dragging keeps the controls on screen");
        }
        fields.stream().filter(field -> field.active && field.visible).findFirst().ifPresent(field -> {
            require(screen.mouseClicked(field.getX() + 4, field.getY() + 4, 0) && field.isFocused(),
                    "dragged inputs retain their click targets");
            screen.mouseReleased(field.getX() + 4, field.getY() + 4, 0);
        });
        require(fields.stream().map(net.minecraft.client.gui.components.EditBox::getValue).toList().equals(values),
                "dragging preserves form drafts");
    }

    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)
                        || button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals(label)).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
