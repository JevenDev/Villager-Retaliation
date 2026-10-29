package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class SettlementViewPanelClientChecks {
    private SettlementViewPanelClientChecks() { }

    static void verify(SettlementViewScreen view) {
        verifyFade();
        verifyBuildOverlay();
        verifyPopup();
        verifyMovementMarkers();
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
        verifyBuildMenu(view);
        verifyIssueActions(view);
        verifyBookmarks(view);
    }

    private static void verifyIssueActions(SettlementViewScreen view) {
        var selected = view.inspectorSelection();
        var issue = new com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue(java.util.UUID.randomUUID(), "Trade needs attention",
                com.jvn.villagerretaliation.network.SettlementManagementPayload.IssueKind.TRADE, "missing_stock", "Stock is unavailable",
                com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.TRADE, java.util.UUID.randomUUID(), null, null, false);
        double x = view.focusX(), z = view.focusZ();
        view.inspect(issue);
        button(view, "Resolve issue").onPress();
        require(view.contextPopup().target().kind() == SettlementViewPopup.Kind.ISSUE_ACTIONS
                && view.contextPopup().buttons().stream().anyMatch(action -> action.getMessage().getString().equals("Review trade order")),
                "issue inspector offers a specific resolution action");
        require(view.contextPopup().buttons().stream().noneMatch(action -> action.getMessage().getString().equals("Link storage")),
                "issues without a resolved worksite do not offer invalid storage actions");
        view.contextPopup().buttons().stream().filter(action -> action.getMessage().getString().equals("Review trade order")).findFirst().orElseThrow().onPress();
        require(Minecraft.getInstance().screen instanceof SettlementManagementScreen, "issue resolution opens the existing management form");
        Minecraft.getInstance().screen.onClose();
        require(Minecraft.getInstance().screen == view, "closing issue settings returns to the same editor");
        require(view.inspectorSelection().equals(issue) && view.focusX() == x && view.focusZ() == z, "issue actions preserve selection and camera");
        if (selected != null) view.inspect(selected);
        else button(view, "Browse settlement").onPress();
    }

    private static void verifyBookmarks(SettlementViewScreen view) {
        var minecraft = Minecraft.getInstance();
        double x = view.focusX(), z = view.focusZ();
        button(view, "Settlement menu").onPress();
        view.contextPopup().buttons().stream().filter(action -> action.getMessage().getString().equals("Camera bookmarks")).findFirst().orElseThrow().onPress();
        require(minecraft.screen instanceof SettlementBookmarkScreen && SettlementViewClient.connectedPanel(), "bookmarks remain linked to the world-view session");
        var screen = minecraft.screen;
        var name = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
        name.setValue("Smoke camera");
        button(screen, "Save current").onPress();
        screen.resize(minecraft, 320, 180);
        var widgets = screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
        for (int i = 0; i < widgets.size(); i++) for (int j = i + 1; j < widgets.size(); j++) {
            var a = widgets.get(i);
            var b = widgets.get(j);
            require(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                    "bookmark controls do not overlap on small screens");
        }
        button(screen, "Go").onPress();
        require(minecraft.screen == view && view.focusX() == x && view.focusZ() == z, "bookmark recall returns to the same editor and camera");
    }

    private static void verifyMovementMarkers() {
        var first = java.util.UUID.randomUUID();
        var second = java.util.UUID.randomUUID();
        var target = new net.minecraft.core.BlockPos(20, 65, 30);
        var rows = new java.util.HashMap<java.util.UUID, com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident>();
        for (var id : java.util.List.of(first, second)) rows.put(id,
                new com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident(id, "Mover",
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("none"), false,
                        com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence.HERE, net.minecraft.core.BlockPos.ZERO,
                        null, -1, -1, "unassigned", java.util.List.of(), null, null, null, "", false, false, 0, "", null, target));
        require(SettlementMoveMarkers.destinations(java.util.List.of(first, second), rows, java.util.Map.of()).equals(java.util.Set.of(target)),
                "selected villagers sharing a target produce one marker");
        require(SettlementMoveMarkers.destinations(java.util.List.of(), rows, java.util.Map.of()).isEmpty(), "unselected villagers do not clutter the world with markers");
        rows.remove(first);
        require(SettlementMoveMarkers.destinations(java.util.List.of(first), rows, java.util.Map.of()).isEmpty(), "removed residents leave no marker");
        rows.put(second, new com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident(second, "Arrived",
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("none"), false,
                com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence.HERE, target, null, -1, -1, "unassigned", java.util.List.of()));
        require(SettlementMoveMarkers.destinations(java.util.List.of(second), rows, java.util.Map.of()).isEmpty(), "a completed order disappears on the next snapshot");
        var tracker = new SettlementViewCommandTracker();
        var session = java.util.UUID.randomUUID();
        var operation = com.jvn.villagerretaliation.network.SettlementViewBatchPayload.operation(session, 1);
        tracker.sent(new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(session, operation, SettlementViewAction.MOVE_TO,
                java.util.List.of(new com.jvn.villagerretaliation.network.SettlementViewBatchPayload.Target(first, null),
                        new com.jvn.villagerretaliation.network.SettlementViewBatchPayload.Target(second, null)), null,
                new com.jvn.villagerretaliation.settlement.SettlementGuardOrder(com.jvn.villagerretaliation.settlement.SettlementGuardOrder.Mode.MOVE,
                        java.util.List.of(target))), 0);
        require(tracker.confirmedMoves().isEmpty(), "pending orders do not claim a confirmed destination");
        require(tracker.accept(new com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload(session, operation, java.util.List.of(
                new com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Outcome(first,
                        com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status.APPLIED, ""),
                new com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Outcome(second,
                        com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status.FAILED, "unavailable")))), "matching result is accepted");
        require(tracker.confirmedMoves().equals(java.util.Map.of(first, target)), "only successful orders show immediately before the overview refresh");
        require(SettlementMoveMarkers.destinations(java.util.List.of(first), rows, tracker.confirmedMoves()).contains(target),
                "confirmed movement appears even while the roster snapshot is stale");
        tracker.overviewRefreshed();
        require(tracker.confirmedMoves().isEmpty(), "the next complete server snapshot takes over marker lifetime");
    }

    private static void verifyBuildMenu(SettlementViewScreen view) {
        var minecraft = Minecraft.getInstance();
        int width = view.width, height = view.height;
        var windows = view.uiWindows();
        var positions = windows.stream().map(SettlementUiWindow::position).toList();
        for (int[] size : new int[][]{{280, 180}, {320, 180}, {width, height}}) {
            view.resize(minecraft, size[0], size[1]);
            var build = button(view, "Build");
            for (int repeat = 0; repeat < 3; repeat++) {
                view.mouseClicked(build.getX() + 2, build.getY() + 2, 0);
                view.mouseReleased(build.getX() + 2, build.getY() + 2, 0);
                require(view.contextPopup() != null && view.contextPopup().target().kind() == SettlementViewPopup.Kind.BUILD,
                        "Build opens consistently with mouse input");
                require(!view.contextPopup().contains(build.getX() + 2, build.getY() + 2), "Build popup never overlaps its toggle");
                view.mouseClicked(build.getX() + 2, build.getY() + 2, 0);
                view.mouseReleased(build.getX() + 2, build.getY() + 2, 0);
                require(view.contextPopup() == null, "Build closes consistently with mouse input");
            }
            build.onPress();
            for (var option : view.contextPopup().buttons()) {
                var reason = view.contextPopup().disabledReasonAt(option.getX() + 2, option.getY() + 2);
                require(option.active ? reason == null : reason != null && !reason.getString().isBlank(),
                        "Build options expose a reason exactly when unavailable");
            }
            boolean buildingAvailable = view.contextPopup().buttons().stream()
                    .filter(button -> button.getMessage().equals(SettlementManagementScreen.text("new_building"))).findFirst().orElseThrow().active;
            view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            view.beginWall();
            build.onPress();
            var building = view.contextPopup().buttons().stream()
                    .filter(button -> button.getMessage().equals(SettlementManagementScreen.text("new_building"))).findFirst().orElseThrow();
            require(building.active == buildingAvailable, "editing walls preserves the building option availability");
            view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.visible && button.getMessage().getString().equals("Cancel"))
                    .findFirst().orElseThrow().onPress();
            require(!view.buildMode(), "cancelling the wall tool restores ordinary world-view input");
        }
        for (int index = 0; index < windows.size(); index++) windows.get(index).restore(positions.get(index));
        view.resize(minecraft, width, height);
        view.setFocused(null);
        view.clearControls();
    }

    static void verifyBuildPanels(SettlementViewScreen view) {
        require(view.buildMode() && view.panelOpacity() == 0, "build mode fades the map and info viewer out");
        var controls = view.buildOverlay().controls();
        var hint = view.buildOverlay().hint();
        require(view.buildOverlay().toggle().left() == 6 && hint.top() + hint.height() == view.height - 6, "help stays pinned to the bottom left");
        if (controls != null) {
            require(Math.abs(controls.left() * 2 + controls.width() - view.width) <= 1, "build controls are centered on the screen");
            require(controls.top() + controls.height() <= hint.top() - 8, "build controls leave the help tooltip unobstructed");
        }
        require(view.uiWindows().stream().flatMap(window -> window.widgets().stream())
                .noneMatch(view.children()::contains), "hidden panel controls cannot intercept build input");
    }

    private static void verifyBuildOverlay() {
        var font = Minecraft.getInstance().font;
        for (int width : new int[]{320, 640, 1280}) {
            int height = width * 9 / 16;
            var overlay = new SettlementBuildOverlay();
            var help = net.minecraft.network.chat.Component.literal("Hold to peek inside. Drag selects beds and residents. Shift adds. Ctrl toggles. Ctrl+Shift removes.");
            overlay.hint(font, help, width, height, true);
            var widgets = new java.util.ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
            for (String label : new String[]{"Width 7 -", "+", "Natural", "Curves", "Undo", "Redo", "Edit", "Confirm", "Cancel"})
                widgets.add(new SettlementBuildOverlay.ActionButton(net.minecraft.network.chat.Component.literal(label), ignored -> { }));
            var layout = new SettlementViewLayout(width, height, true, true);
            overlay.layout(font, widgets, width, height, layout.worldLeft);
            var bounds = overlay.controls();
            require(Math.abs(bounds.left() * 2 + bounds.width() - width) <= 1, "wrapped toolbars remain centered");
            require(bounds.left() >= layout.worldLeft && bounds.left() + bounds.width() <= width - layout.worldLeft,
                    "toolbar wrapping respects both screen edges");
            require(bounds.top() > SettlementViewLayout.HEADER_HEIGHT && bounds.top() + bounds.height() < overlay.hint().top() - 4,
                    "compact screens keep controls between the header and help");
            require(SettlementViewLayout.HEADER_HEIGHT + 6 + (layout.railSize + 2) * 7 - 2 < overlay.hint().top() - 4,
                    "the tool rail leaves room for the bottom-left help");
            require(overlay.contains(bounds.left() - 2, bounds.top()) && overlay.contains(overlay.hint().left(), overlay.hint().top()),
                    "tooltip padding and help capture world input");
            for (var widget : widgets) require(widget.getX() >= bounds.left() && widget.getRight() <= bounds.left() + bounds.width()
                    && widget.getY() >= bounds.top() && widget.getBottom() <= bounds.top() + bounds.height(), "all controls fit their tooltip");
            var confirm = widgets.get(widgets.size() - 2);
            int confirmX = confirm.getX(), confirmY = confirm.getY();
            for (String feedback : new String[]{"Ready", "Ready\nReview terrain\nConfirm placement", "Preview feedback ".repeat(60)}) {
                overlay.hint(font, net.minecraft.network.chat.Component.literal(feedback), width, height, true);
                confirm.active = !confirm.active;
                overlay.layout(font, widgets, width, height, layout.worldLeft);
                require(bounds.equals(overlay.controls()) && confirm.getX() == confirmX && confirm.getY() == confirmY,
                        "feedback wrapping and confirmation readiness cannot move the toolbar under the cursor");
            }
            var hintBounds = overlay.hint();
            overlay.hint(font, help, width, height, false);
            overlay.layout(font, widgets, width, height, layout.worldLeft);
            require(!overlay.hintsVisible() && !overlay.contains(hintBounds.left() + hintBounds.width() - 1, height - 8),
                    "hidden hints no longer block the world");
            require(overlay.contains(overlay.toggle().left(), overlay.toggle().top()), "the hint toggle remains available when hints are hidden");
        }
    }

    static void verifyHintToggle(SettlementViewScreen view) {
        var toggle = button(view, "Hide hints");
        int x = toggle.getX(), y = toggle.getY();
        require(x == 6 && toggle.getBottom() == view.height - 6, "the help button is pinned to the bottom left");
        require(view.mouseClicked(x + 8, y + 8, GLFW.GLFW_MOUSE_BUTTON_LEFT), "the hint toggle consumes its click");
        view.mouseReleased(x + 8, y + 8, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(!view.buildOverlay().hintsVisible(), "the help button hides hint text");
        require(!SettlementViewPreferences.forSettlement(view.parent().snapshot().header().id()).hints, "hidden hints survive a preference reload");
        view.resize(Minecraft.getInstance(), view.width, view.height);
        toggle = button(view, "Show hints");
        require(toggle.getX() == x && toggle.getY() == y && !view.buildOverlay().hintsVisible(), "rebuilding controls preserves the toggle and its selection");
        view.mouseClicked(x + 8, y + 8, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        view.mouseReleased(x + 8, y + 8, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(view.buildOverlay().hintsVisible() && SettlementViewPreferences.forSettlement(view.parent().snapshot().header().id()).hints,
                "the same button restores and saves hint visibility");
    }

    private static void verifyFade() {
        var fade = new SettlementPanelFade();
        fade.update(false, 1000);
        require(fade.opacity() == 1, "panels start fully visible");
        fade.update(true, 1000);
        fade.update(true, 1100);
        require(fade.opacity() == 0.5F, "panels fade gradually");
        fade.update(false, 1100);
        require(fade.opacity() == 0.5F, "reversing a fade preserves its current opacity");
        fade.update(false, 1300);
        require(fade.opacity() == 1, "reversed fades return to full visibility");
        fade.update(true, 1300);
        fade.update(true, 1500);
        require(fade.opacity() == 0, "hidden panels finish fading completely");
        fade.update(false, 1500);
        fade.update(false, 1700);
        require(fade.opacity() == 1, "leaving build mode restores the panels");
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
            verifyResizing(view, window);
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

    private static void verifyResizing(SettlementViewScreen view, SettlementUiWindow window) {
        var minecraft = Minecraft.getInstance();
        var original = window.position();
        int width = window.width(), height = window.height();
        var search = window.widgets().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElse(null);
        String query = search == null ? "" : search.getValue();
        if (search != null) search.setValue("resize");
        var selected = view.selectedResidents();
        int x = window.left() + width - 2, y = window.top() + height - 2;
        require(view.mouseClicked(x, y, 0), "the bottom right resize handle captures the pointer");
        view.mouseDragged(x + 24, y + 20, 0, 24, 20);
        view.mouseDragged(x + 48, y + 40, 0, 24, 20);
        view.mouseReleased(x + 48, y + 40, 0);
        require(window.width() == Math.min(width + 48, view.width - window.left() - 2)
                        && window.height() == Math.min(height + 40, view.height - window.top() - 2),
                "successive resize events reflow the window without losing the drag");
        require(window.widgets().stream().filter(widget -> widget.visible).allMatch(widget ->
                        widget.getX() >= window.left() && widget.getRight() <= window.left() + window.width()
                                && widget.getY() >= window.top() && widget.getBottom() <= window.top() + window.height()),
                "resized controls stay inside their window");
        require(view.selectedResidents().equals(selected), "resizing does not alter the world selection");
        if (search != null) {
            var resizedSearch = window.widgets().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            require(resizedSearch.getValue().equals("resize") && resizedSearch.getWidth() == window.width() - 10,
                    "resizing preserves the browser query and expands its input");
            resizedSearch.setValue(query);
        } else {
            var map = view.overviewMap();
            require(map.width == window.width() - 4 && map.height == window.height() - SettlementUiWindow.TITLE_HEIGHT - 4,
                    "the map viewport follows the independently resized dimensions");
            require(map.contains(map.left + map.width - 1, map.top + map.height - 1),
                    "resized map input covers the expanded viewport");
        }
        var resized = window.position();
        var restored = SettlementViewPreferences.forSettlement(view.parent().snapshot().header().id());
        require(resized.equals(search == null ? restored.mapWindow : restored.sidebarWindow),
                "window dimensions and position survive preference reload");
        view.resize(minecraft, view.width, view.height);
        require(window.width() == resized.width() && window.height() == resized.height(),
                "rebuilding controls retains the resized dimensions");
        int right = window.left() + window.width();
        x = window.left() + 2;
        y = window.top() + window.height() - 2;
        view.mouseClicked(x, y, 0);
        view.mouseDragged(right + 1000, -1000, 0, 1000, -1000);
        view.mouseReleased(right + 1000, -1000, 0);
        require(window.left() + window.width() == right
                        && window.width() == (search == null ? 68 : 140)
                        && window.height() == (search == null ? 82 : 140),
                "left corner resizing keeps the opposite edge fixed and enforces usable minimum sizes");
        x = window.left() + 2;
        y = window.top() + window.height() - 2;
        view.mouseClicked(x, y, 0);
        view.mouseDragged(-1000, 10000, 0, -1000, 10000);
        view.mouseReleased(-1000, 10000, 0);
        require(window.left() == 2 && window.left() + window.width() == right
                        && window.top() + window.height() == view.height - 2,
                "expanding from the left corner stops at the screen edges");
        int screenWidth = view.width, screenHeight = view.height;
        view.resize(minecraft, 320, 180);
        require(window.left() >= 2 && window.top() >= SettlementViewLayout.HEADER_HEIGHT + 2
                        && window.left() + window.width() <= 318 && window.top() + window.height() <= 178,
                "saved window bounds fit after reducing the screen size");
        window.restore(original);
        view.resize(minecraft, screenWidth, screenHeight);
        require(window.width() == width && window.height() == height,
                "legacy position-only preferences retain the automatic panel sizes");
    }

    public static void verifyPopup() {
        var font = Minecraft.getInstance().font;
        var build = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.BUILD, null), 42, 60);
        build.update(font, 280, 180, java.util.List.of(net.minecraft.network.chat.Component.literal("Build")),
                java.util.List.of(new SettlementViewPopup.Action(net.minecraft.network.chat.Component.literal(
                        "A construction action long enough to fill the available popup width"), true, () -> {})));
        require(!build.contains(30, 65) && build.buttons().getFirst().getX() >= 42,
                "long Build menus remain to the right of the tool rail on narrow screens");
        var popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.ACTIONS, null), 630, 350);
        int[] selected = {-1};
        var details = java.util.List.<net.minecraft.network.chat.Component>of(net.minecraft.network.chat.Component.literal("Resident"),
                net.minecraft.network.chat.Component.literal("Health 20 | Food 10"));
        var actions = new java.util.ArrayList<SettlementViewPopup.Action>();
        for (int index = 0; index < 20; index++) {
            int action = index;
            actions.add(new SettlementViewPopup.Action(net.minecraft.network.chat.Component.literal("Action " + index),
                    index != 1, () -> selected[0] = action, null, net.minecraft.world.item.ItemStack.EMPTY,
                    net.minecraft.network.chat.Component.literal(index == 1 ? "Requires Build permission. Ask the settlement leader to grant it." : "")));
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
        require(popup.disabledReasonAt(disabled.getX() + 4, disabled.getY() + 2).getString().contains("Build permission"),
                "hovering a disabled action exposes the reason and remedy");
        require(popup.disabledReasonAt(x, y) == null && popup.disabledReasonAt(-1, -1) == null,
                "enabled actions and empty space have no disabled tooltip");
        require(popup.narration().getString().contains("Build permission"), "disabled reasons are available to narration");
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
