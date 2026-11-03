package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.settlement.SettlementPathGizmo;
import com.jvn.villagerretaliation.settlement.SettlementPath;
import com.jvn.villagerretaliation.settlement.SettlementRouteShape;
import net.minecraft.world.phys.Vec3;
import com.jvn.villagerretaliation.exploration.MapSnapshot;
import com.jvn.villagerretaliation.exploration.MapSurfaceSnapshot;
import com.jvn.villagerretaliation.exploration.MapView;
import com.jvn.villagerretaliation.network.SettlementRouteRequest.Action;
import com.jvn.villagerretaliation.network.SettlementRoutesPayload;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.lwjgl.glfw.GLFW;

final class SettlementRouteMapClientChecks {
    private static int phase;
    private static SettlementRoutesScreen screen;
    private static final UUID ORIGIN = UUID.randomUUID(), DESTINATION = UUID.randomUUID(), ROUTE = UUID.randomUUID();
    private static final int BASE = 100000;

    static boolean tick(ExploredMapScreen parent) {
        var mc = Minecraft.getInstance();
        switch (phase++) {
            case 0 -> {
                var diagonal = MapRouteLine.pixels(List.of(new Vec3(.5, 0, .5), new Vec3(1.5, 0, .5), new Vec3(1.5, 0, 1.5),
                        new Vec3(2.5, 0, 1.5), new Vec3(2.5, 0, 2.5)), 1, ignored -> 0xFFFFFFFF);
                require(diagonal.keySet().equals(java.util.Set.of(BlockPos.asLong(0, 0, 0), BlockPos.asLong(1, 0, 1), BlockPos.asLong(2, 0, 2))),
                        "pixel-perfect strokes remove diagonal elbow doubles");
                var horizontal = MapRouteLine.pixels(List.of(new Vec3(.5, 0, .5), new Vec3(4.5, 0, .5)), 1, ignored -> 0xFFFFFFFF);
                require(horizontal.size() == 5 && horizontal.containsKey(BlockPos.asLong(2, 0, 0)), "straight pixel runs stay complete");
                var coarse = MapRouteLine.pixels(List.of(new Vec3(.5, 0, .5), new Vec3(4.5, 0, 4.5)), 2, ignored -> 0xFFFFFFFF);
                require(coarse.size() == 3, "pixel-perfect diagonals follow the map's coarse grid too");
                var repository = ExploredMapClient.repository();
                @SuppressWarnings("unchecked")
                var surfaces = (java.util.Map<Long, MapSurfaceSnapshot>) field(repository, "surfaces");
                for (int z = 0; z < 12; z++) for (int x = 0; x < 12; x++) {
                    var columns = new ArrayList<MapSurfaceSnapshot.Column>();
                    for (int i = 0; i < 64; i++) columns.add(new MapSurfaceSnapshot.Column(i, List.of(new MapSurfaceSnapshot.Layer(
                            new MapSnapshot.Block(63, "minecraft:grass_block[snowy=false]", 0x83AF55, 0x3F76E4, 2, 15), 63))));
                    var tile = new MapSurfaceSnapshot(BASE / 8 + x, BASE / 8 + z, 1, columns, List.of());
                    surfaces.put(tile.key(), tile);
                }
                screen = new SettlementRoutesScreen(parent);
                set(screen, "initialized", true); set(screen, "origin", ORIGIN); set(screen, "destination", DESTINATION);
                set(screen, "data", data()); set(screen, "x", BASE + 48.0); set(screen, "z", BASE + 48.0); set(screen, "scale", 3.0);
                set(screen, "editor", new SettlementRouteMapEditor(new SettlementRouteShape(List.of(
                        new SettlementPath.Node(Vec3.atBottomCenterOf(pos(10, 30))), new SettlementPath.Node(Vec3.atBottomCenterOf(pos(80, 65)))), false)));
                mc.setScreen(screen);
                place(20, 30); place(60, 40);
                require(editor().points().size() == 2 && editor().draft().selected() == 2, "left click places and selects route nodes");
                click(20, 30, 0); release(20, 30);
                button("Lines").onPress();
                require(editor().draft().smoothMode() && editor().shape().nodes().get(1).outgoing().lengthSqr() > 0, "Curves exposes handles using the world-view draft");
                var node = editor().shape().nodes().get(1); var handle = node.position().add(node.outgoing());
                pointer(handle, 0); move(node.position().add(12, 0, 8)); up(node.position().add(12, 0, 8));
                node = editor().shape().nodes().get(1);
                require(node.incoming().add(node.outgoing()).lengthSqr() == 0, "dragging a handle mirrors the opposite tangent");
                quiet();
                set(screen, "path", editor().shape().columns().stream().mapToLong(point -> new BlockPos(point.getX(), 64, point.getZ()).asLong()).toArray()); call(screen, "update");
                require(button("Save route").active, "a checked shape can be saved");
            }
            case 1 -> {
                capture("route-map-preview.png");
                var before = editor().shape();
                var node = before.nodes().get(1); var incoming = node.incoming();
                var start = node.position().add(node.outgoing()); var finish = node.position().add(15, 0, 12);
                pointer(start, 0);
                editor().drag(finish, view().screenX(finish.x), view().screenY(finish.z), true); up(finish);
                require(editor().shape().nodes().get(1).incoming().equals(incoming), "Alt-drag keeps the other handle independent");
                for (var control : editor().shape().nodes()) {
                    for (var point : List.of(control.position(), control.position().add(control.incoming()), control.position().add(control.outgoing())))
                        require(point.x == Math.floor(point.x) + .5 && point.z == Math.floor(point.z) + .5, "nodes and both curve handles stay on map-cell centers");
                }
                require(((long[]) field(screen, "path")).length == 0 && !button("Save route").active, "handle edits invalidate the checked path");
                screen.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(editor().shape().equals(before), "Ctrl+Z undoes the complete handle gesture");
                screen.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(!editor().shape().equals(before), "Ctrl+Y restores the complete handle gesture");
                before = editor().shape(); node = before.nodes().get(1); start = node.position().add(node.outgoing());
                pointer(start, 0); move(node.position().add(18, 0, 15)); screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(editor().shape().equals(before), "escape rolls back a handle drag");
                pointer(start, 0); move(node.position().add(18, 0, 15)); screen.mouseReleased(screen.width + 20, 100, 0);
                require(editor().shape().equals(before), "releasing outside the map rolls back a handle drag");
                var endpoint = before.nodes().getFirst().position(); pointer(endpoint, 0); move(endpoint.add(3, 0, 3)); up(endpoint.add(3, 0, 3));
                require(editor().shape().nodes().getFirst().position().equals(endpoint), "village endpoints stay anchored while their handles remain editable");
                editor().draft().select(1); call(screen, "edit", new Class<?>[]{int.class}, 3); place(40, 55);
                require(editor().points().get(1).equals(pos(40, 55)), "Insert places after the selected node");
                var savedShape = editor().shape(); var saved = data();
                var row = new CompoundTag(); row.putUUID("Id", ROUTE); row.putUUID("Origin", ORIGIN); row.putUUID("Destination", DESTINATION);
                row.putString("Name", "Valley crossing"); row.putLong("Revision", 1); var routes = new ListTag(); routes.add(row); saved.put("Routes", routes);
                saved.put("Shape", savedShape.save()); saved.putLongArray("Points", savedShape.points().stream().mapToLong(BlockPos::asLong).toArray());
                set(screen, "loadRoute", true); set(screen, "route", ROUTE); set(screen, "sentAction", Action.VIEW);
                SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), saved));
                require(editor().shape().equals(savedShape), "reloading a saved route retains every handle and curve mode");
                editor().draft().select(1); set(screen, "x", BASE + 48.0); set(screen, "z", BASE + 48.0); set(screen, "scale", 3.0); quiet();
            }
            case 2 -> {
                capture("route-map-draft.png");
                click(20, 30, 1);
                require(field(screen, "context") != null, "right-clicking a node opens the world-view Edit menu");
                double scale = view().scale(); screen.mouseScrolled(10, 100, 0, -1);
                require(view().scale() == scale, "Edit menu scrolling cannot zoom the map");
            }
            case 3 -> {
                capture("route-map-actions.png"); screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(field(screen, "context") == null && mc.screen == screen, "escape dismisses Edit before leaving the editor");
                click(45, 20, 1); require(!editor().draft().extending(), "right-click stops extending like the world view");
                int count = editor().points().size(); place(45, 20);
                require(editor().points().size() == count, "stopped drafts cannot accidentally append nodes");
                button("Draw").onPress(); place(45, 20);
                require(editor().points().size() == count + 1, "Extend resumes drawing");
                double center = (double) field(screen, "x");
                click(50, 75, 2); screen.mouseDragged(view().screenX(BASE + 50.5) + 12, view().screenY(BASE + 75.5), 2, 12, 0); screen.mouseReleased(0, 0, 2);
                require((double) field(screen, "x") != center, "middle drag pans without editing the route");
                set(screen, "x", center);
                set(screen, "pending", true); set(screen, "sentAction", Action.PREVIEW); int stale = (int) field(screen, "sequence");
                place(65, 70);
                require((int) field(screen, "sequence") != stale && !(boolean) field(screen, "pending"), "editing invalidates an in-flight preview");
                SettlementRoutesScreen.receive(new SettlementRoutesPayload(stale, new CompoundTag()));
                require(editor().points().size() == count + 2, "stale preview replies cannot replace newer geometry");
                set(screen, "pending", true); set(screen, "sentAction", Action.SAVE); place(70, 75);
                require(editor().points().size() == count + 2, "save requests lock geometry until acknowledged");
                set(screen, "pending", false); quiet();
                screen.resize(mc, 420, 270); screen.mouseScrolled(419, 150, 0, -30);
                require(button("Undo").visible && button("Undo").getBottom() <= 230, "world-view edit controls remain reachable on a small screen");
                set(screen, "editor", new SettlementRouteMapEditor(new SettlementRouteShape(List.of(new SettlementPath.Node(Vec3.atBottomCenterOf(pos(48, 0))),
                        new SettlementPath.Node(Vec3.atBottomCenterOf(pos(48, 95)))), false))); call(screen, "fitRoute");
                require(view().screenY(BASE + .5) > 65 && view().screenY(BASE + 95.5) < screen.height - 60, "Fit keeps endpoint handles editable");
            }
            case 4 -> {
                screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                set(screen, "editor", new SettlementRouteMapEditor(new SettlementRouteShape(List.of(
                        new SettlementPath.Node(Vec3.atBottomCenterOf(pos(10, 30))), new SettlementPath.Node(Vec3.atBottomCenterOf(pos(40, 40))),
                        new SettlementPath.Node(Vec3.atBottomCenterOf(pos(80, 65)))), false)));
                set(screen, "x", BASE + 48.0); set(screen, "z", BASE + 48.0); set(screen, "scale", 3.0);
                button("Edit").onPress(); quiet();
                int count = editor().points().size(); place(50, 20);
                require(editor().points().size() == count && editor().draft().selected() == -1, "Edit never places nodes on empty ground");
                click(40, 40, 0); release(40, 40);
                require(editor().draft().selected() == 1 && button("Smooth").active, "selecting a node enables its toolbar");
                button("Smooth").onPress();
                require(editor().hasHandles() && button("Straighten").active && !button("Smooth").active, "Smooth exposes editable handles");
                button("Linked").onPress();
                require(editor().independentHandles() && button("Independent").active, "the toolbar exposes independent handle editing without Alt");
                var node = editor().shape().nodes().get(1); var incoming = node.incoming();
                pointer(node.position().add(node.outgoing()), 0); move(node.position().add(8, 0, 4)); up(node.position().add(8, 0, 4));
                require(editor().shape().nodes().get(1).incoming().equals(incoming), "Independent keeps the opposite handle fixed");
                button("Independent").onPress();
                require(!editor().independentHandles() && editor().shape().nodes().get(1).incoming().add(editor().shape().nodes().get(1).outgoing()).lengthSqr() == 0,
                        "Linked mirrors the current handles");
                button("Straighten").onPress();
                require(!editor().hasHandles() && !editor().draft().smoothMode(), "Straighten removes handles consistently with Lines mode");
                button("Undo").onPress(); require(editor().hasHandles(), "straightening a node is undoable");
                button("Pan").onPress(); var shape = editor().shape(); double center = (double) field(screen, "x");
                click(40, 40, 0); screen.mouseDragged(view().screenX(BASE + 40.5) + 12, view().screenY(BASE + 40.5), 0, 12, 0); screen.mouseReleased(0, 0, 0);
                require(editor().shape().equals(shape) && (double) field(screen, "x") != center, "Pan drags over nodes without changing geometry");
                require(!button("Smooth").visible, "Pan hides the selected-node toolbar");
                set(screen, "x", center); button("Edit").onPress();
                editor().select(1); call(screen, "update");
                count = editor().points().size();
                var toolbar = button("Remove");
                screen.mouseClicked(toolbar.getX() - 1, toolbar.getY() + 2, 0); screen.mouseReleased(0, 0, 0);
                require(editor().points().size() == count, "clicking toolbar padding cannot edit the map behind it");
                button("Insert").onPress();
                require(field(screen, "tool").toString().equals("DRAW") && editor().draft().inserting(), "Insert visibly switches to Draw");
                place(55, 30); require(editor().points().get(1).equals(pos(55, 30)), "toolbar insertion follows the selected node");
                button("Edit").onPress(); quiet();
                var checked = data(); checked.putLongArray("Preview", checkedPath());
                checked.putString("Message", "Crossing estimate ready"); set(screen, "sentAction", Action.PREVIEW);
                SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), checked));
                require(call(screen, "previewStatus").equals("Ready to save") && button("Save route").active, "current validated geometry reports Ready to save");
                screen.mouseScrolled(screen.width - 10, 150, 0, -20);
                require(button("Save route").visible && button("Check route").visible, "preview and save remain fixed while the panel scrolls");
                set(screen, "pending", true); set(screen, "sentAction", Action.SAVE); call(screen, "update");
                require(call(screen, "previewStatus").equals("Saving route...") && !button("Save route").active, "saving has a distinct locked status");
                var rejected = data(); rejected.putString("Message", "Route changed, refresh first");
                SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), rejected));
                require(call(screen, "previewStatus").equals("Save not confirmed") && !button("Save route").active, "a rejected save cannot appear ready or saved");
                set(screen, "sentAction", Action.PREVIEW);
                SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), checked));
                require(call(screen, "previewStatus").equals("Ready to save"), "a fresh successful check clears the old save failure");
                var blocked = data(); blocked.putString("Message", "Curve crosses blocked ground"); blocked.putLong("Blocked", editor().shape().columns().get(editor().shape().columns().size() / 2).asLong());
                SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), blocked));
                require(call(screen, "previewStatus").equals("Blocked") && button("Show problem").visible && !button("Save route").active,
                        "blocked geometry has a visible problem action and cannot be saved");
                quiet();
            }
            case 5 -> {
                capture("route-tools-blocked.png"); var problem = BlockPos.of(((CompoundTag) field(screen, "data")).getLong("Blocked")); button("Show problem").onPress();
                require((boolean) call(screen, "editableMap", new Class<?>[]{double.class, double.class}, view().screenX(problem.getX() + .5), view().screenY(problem.getZ() + .5)),
                        "Show problem focuses terrain in the unobscured map area");
                var surveying = data(); surveying.putString("Message", "Surveying route, preview again shortly"); set(screen, "sentAction", Action.PREVIEW);
                SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), surveying));
                require(call(screen, "previewStatus").equals("Checking terrain...") && !button("Show problem").visible, "surveying does not retain a stale blocked state");
                quiet();
                var checked = data(); checked.putLongArray("Preview", checkedPath()); checked.putString("Message", "Crossing estimate ready");
                SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), checked));
                set(screen, "x", BASE + 48.0); set(screen, "z", BASE + 48.0); set(screen, "scale", 3.0); editor().select(1); call(screen, "update");
            }
            case 6 -> {
                capture("route-tools-selected.png");
                var saved = data(); saved.putString("Message", "Saved. Route established"); saved.putLongArray("Path", checkedPath());
                set(screen, "sentAction", Action.SAVE); SettlementRoutesScreen.receive(new SettlementRoutesPayload((int) field(screen, "sequence"), saved));
                require(call(screen, "previewStatus").equals("Saved route") && !(boolean) field(screen, "dirty"), "acknowledged saves have a distinct clean status");
                mc.setScreen(parent); return true;
            }
            default -> { return true; }
        }
        return false;
    }

    private static CompoundTag data() {
        var data = new CompoundTag(); var towns = new ListTag();
        for (int i = 0; i < 2; i++) {
            var town = new CompoundTag(); town.putUUID("Id", i == 0 ? ORIGIN : DESTINATION);
            town.putString("Name", i == 0 ? "Oakbridge" : "Riverbend"); town.putBoolean("Manage", true);
            town.putLong("Position", pos(i == 0 ? 10 : 80, i == 0 ? 30 : 65).asLong()); towns.add(town);
        }
        data.put("Towns", towns); return data;
    }
    private static SettlementRouteMapEditor editor() { return (SettlementRouteMapEditor) field(screen, "editor"); }
    private static long[] checkedPath() { return editor().shape().columns().stream().mapToLong(point -> new BlockPos(point.getX(), 64, point.getZ()).asLong()).toArray(); }
    private static void quiet() { set(screen, "previewWanted", false); set(screen, "pending", false); }
    private static BlockPos pos(int x, int z) { return new BlockPos(BASE + x, 64, BASE + z); }
    private static MapView view() { return (MapView) call(screen, "view"); }
    private static void place(int x, int z) { click(x, z, 0); release(x, z); }
    private static void click(int x, int z, int button) { pointer(Vec3.atBottomCenterOf(pos(x, z)), button); }
    private static void release(int x, int z) { up(Vec3.atBottomCenterOf(pos(x, z))); }
    private static void pointer(Vec3 point, int button) { screen.mouseClicked(view().screenX(point.x), view().screenY(point.z), button); }
    private static void move(Vec3 point) { screen.mouseDragged(view().screenX(point.x), view().screenY(point.z), 0, 6, 6); }
    private static void up(Vec3 point) { screen.mouseReleased(view().screenX(point.x), view().screenY(point.z), 0); }
    private static Button button(String label) {
        return screen.children().stream().filter(child -> child instanceof Button b && b.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Object target, String name, Object value) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object call(Object target, String name) { return call(target, name, new Class<?>[0]); }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) {
        try { var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void capture(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), name));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void require(boolean valid, String reason) { if (!valid) throw new AssertionError(reason); }
}
