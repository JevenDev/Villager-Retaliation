package com.jvn.villagerretaliation.client.settlement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

final class SettlementViewPanelClientChecks {
    private SettlementViewPanelClientChecks() { }

    static void verify(SettlementViewScreen view) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.getCameraEntity();
        double x = view.focusX(), z = view.focusZ();
        float height = view.viewHeight(), yaw = view.cameraYaw();
        float projection = SettlementViewClient.projectionOffset();
        button(view, "villagerretaliation.gui.settlement_view.overview").onPress();
        require(minecraft.screen == view && view.contextPopup().target().kind() == SettlementViewPopup.Kind.OVERVIEW,
                "overview opens over the world without switching screens");
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
