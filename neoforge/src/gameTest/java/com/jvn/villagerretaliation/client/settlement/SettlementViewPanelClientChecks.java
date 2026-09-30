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
