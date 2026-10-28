package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class SettlementViewPanelClientChecks {
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
        verifyFloatingWindows(view);
        var filters = view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith("All (")).findFirst().orElseThrow();
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

    private static void verifyFloatingWindows(SettlementViewScreen view) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.getCameraEntity();
        double focusX = view.focusX(), focusZ = view.focusZ();
        for (var window : view.uiWindows()) {
            int left = window.left(), top = window.top();
            var positions = window.widgets().stream().map(widget -> new int[]{widget.getX(), widget.getY()}).toList();
            require(view.mouseClicked(left + 4, top + 4, 0), "a floating window title captures the pointer");
            require(view.mouseDragged(-1000, -1000, 0, -1000, -1000), "floating windows can be dragged across the view");
            view.mouseReleased(-1000, -1000, 0);
            int dx = window.left() - left, dy = window.top() - top;
            require(window.left() >= 0 && window.top() >= SettlementViewLayout.HEADER_HEIGHT,
                    "window dragging keeps titles below the status bar");
            for (int index = 0; index < window.widgets().size(); index++) {
                var widget = window.widgets().get(index);
                require(widget.getX() == positions.get(index)[0] + dx && widget.getY() == positions.get(index)[1] + dy,
                        "floating controls move with their window hit targets");
            }
            int movedLeft = window.left(), movedTop = window.top();
            view.resize(minecraft, view.width, view.height);
            require(window.left() == movedLeft && window.top() == movedTop,
                    "rebuilding controls retains a dragged window position");
            view.mouseClicked(window.left() + 4, window.top() + 4, 0);
            view.mouseDragged(left + 4, top + 4, 0, left - window.left(), top - window.top());
            view.mouseReleased(left + 4, top + 4, 0);
            require(window.left() == left && window.top() == top, "floating windows can return to their original position");
        }
        require(view.focusX() == focusX && view.focusZ() == focusZ && minecraft.getCameraEntity() == camera,
                "dragging and rebuilding windows leave the world camera unchanged");
        var restored = SettlementViewPreferences.forSettlement(view.parent().snapshot().header().id());
        require(restored.sidebarWindow != null && restored.mapWindow != null, "dragged window positions survive preference reload");
    }

    public static void verifyPopup() {
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
        popup.hover(x, y);
        popup.scroll(x + 0.25, y + 0.25, -1);
        popup.hover(x, y);
        require(popup.buttons().get(2).isFocused() && selected[0] == -1,
                "the wheel selects the next enabled action without running it");
        popup.update(font, 640, 360, details, actions.subList(0, 3));
        require(popup.buttons().get(2).isFocused(), "live tooltip refresh preserves wheel selection");
        popup.click(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(selected[0] == 2, "click confirms the wheel selection even over a different row");
        selected[0] = -1;
        double blankX = first.getX() + Math.max(first.getWidth(), details.stream().mapToInt(font::width).max().orElseThrow()) + 8;
        require(popup.contains(blankX, y), "the tooltip has whitespace beyond its short action labels");
        popup.hover(blankX, y);
        require(popup.buttons().get(2).isFocused(), "blank row space does not change the hovered selection");
        popup.click(blankX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(selected[0] == -1, "blank row space does not activate an action");
        popup.scroll(blankX, y, 1);
        require(popup.buttons().get(2).isFocused(), "scrolling beyond the padded text area does not select an action");
        double hintY = popup.buttons().getLast().getBottom() + 6;
        require(popup.contains(x, hintY) && popup.scroll(x, hintY, 1), "footer scrolling stays inside the tooltip");
        require(popup.buttons().get(2).isFocused(), "the help footer is outside the scroll area");
        popup.scroll(first.getX() - 1, y, 1);
        require(popup.buttons().getFirst().isFocused(), "slight padding around the text accepts scrolling");
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
        var startRow = popup.buttons().get(2);
        double scrollX = startRow.getX() + 2, scrollY = startRow.getY() + 2;
        for (int index = 3; index < actions.size(); index++) {
            popup.scroll(scrollX, scrollY, -1);
            popup.hover(scrollX, scrollY);
            require(popup.buttons().get(index).isFocused(), "stationary hover cannot pull overflow scrolling back to another row");
        }
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
