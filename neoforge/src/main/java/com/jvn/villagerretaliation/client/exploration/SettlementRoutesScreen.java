package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapLighting;
import com.jvn.villagerretaliation.exploration.MapSnapshot;
import com.jvn.villagerretaliation.exploration.MapView;
import com.jvn.villagerretaliation.network.SettlementRouteRequest;
import com.jvn.villagerretaliation.network.SettlementRouteRequest.Action;
import com.jvn.villagerretaliation.network.SettlementRoutesPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementRoutesScreen extends Screen {
    private final Screen parent;
    private final UUID journey;
    private final ExploredMapRenderer tiles = new ExploredMapRenderer();
    private final List<BlockPos> points = new ArrayList<>();
    private final LinkedHashMap<UUID, Boolean> members = new LinkedHashMap<>();
    private CompoundTag data = new CompoundTag();
    private UUID origin, destination, route;
    private long revision, journeyRevision;
    private int sequence, section, candidate, panel;
    private double x, z, scale = 1;
    private String name = "Custom route", message = "Loading settlements";
    private boolean pending, initialized, loadRoute, configured;
    private long sent;
    private Action queued;
    private long[] path = new long[0];
    private final java.util.Map<net.minecraft.client.gui.components.AbstractWidget, Integer> layout = new LinkedHashMap<>();
    private int scroll;
    private EditBox nameField;
    private Button origins, destinations, routes, build, save, remove, preview, assign, role, traveler, configure;

    public SettlementRoutesScreen(Screen parent) { this(parent, null); }
    public SettlementRoutesScreen(Screen parent, UUID journey) { super(Component.literal(journey == null ? "Settlement routes" : "Caravan departure")); this.parent = parent; this.journey = journey; }
    public static void receive(SettlementRoutesPayload payload) {
        if (Minecraft.getInstance().screen instanceof SettlementRoutesScreen screen && screen.sequence == payload.sequence()) screen.accept(payload.data());
    }
    private void accept(CompoundTag value) {
        boolean first = !initialized;
        data = value; pending = false; message = value.getString("Message");
        if (!initialized) {
            var own = towns().stream().filter(row -> row.getBoolean("Manage")).findFirst().orElse(null);
            if (own != null) { origin = own.getUUID("Id"); center(own); }
            if (journey != null && value.hasUUID("Home")) { origin = value.getUUID("Home"); destination = value.getUUID("Other"); }
            if (destination == null) destination = towns().stream().filter(row -> !row.getUUID("Id").equals(origin)).map(row -> row.getUUID("Id")).findFirst().orElse(null);
            initialized = true;
        }
        if (destination == null) destination = towns().stream().filter(row -> !row.getUUID("Id").equals(origin)).map(row -> row.getUUID("Id")).findFirst().orElse(null);
        journeyRevision = value.getLong("JourneyRevision");
        if (journey != null && !configured && value.contains("Members")) {
            members.clear(); candidates().stream().filter(row -> row.getBoolean("Selected")).forEach(row -> members.put(row.getUUID("Id"), row.getBoolean("Guard")));
            if (value.hasUUID("SelectedRoute")) route = value.getUUID("SelectedRoute"); configured = true;
        }
        if (value.contains("Preview")) path = value.getLongArray("Preview");
        else if (value.contains("Path")) path = value.getLongArray("Path");
        var current = routeRows().stream().filter(row -> row.getUUID("Id").equals(route)).findFirst().orElse(null);
        if (current != null) {
            revision = current.getLong("Revision");
            if (loadRoute) {
                name = current.getString("Name"); nameField.setValue(name);
                if (journey == null) { origin = current.getUUID("Origin"); destination = current.getUUID("Destination"); }
                points.clear(); java.util.Arrays.stream(value.getLongArray("Points")).mapToObj(BlockPos::of).forEach(points::add); loadRoute = false;
            }
        }
        section = Math.min(section, Math.max(0, data.getList("Sections", Tag.TAG_COMPOUND).size() - 1)); update();
        if (first && origin != null) send(Action.VIEW);
    }
    private List<CompoundTag> rows(String key) { return data.getList(key, Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList(); }
    private List<CompoundTag> towns() { return rows("Towns"); }
    private List<CompoundTag> routeRows() {
        return rows("Routes").stream().filter(row -> journey == null || origin != null && destination != null
                && (row.getUUID("Origin").equals(origin) && row.getUUID("Destination").equals(destination)
                    || row.getUUID("Origin").equals(destination) && row.getUUID("Destination").equals(origin))).toList();
    }
    private List<CompoundTag> candidates() { return rows("Members"); }
    private CompoundTag member() { var list = candidates(); return list.isEmpty() ? null : list.get(Math.floorMod(candidate, list.size())); }
    private void center(CompoundTag town) { var position = BlockPos.of(town.getLong("Position")); x = position.getX(); z = position.getZ(); }
    private String townName(UUID id) { return towns().stream().filter(row -> row.getUUID("Id").equals(id)).map(row -> row.getString("Name")).findFirst().orElse("Choose settlement"); }
    private Button button(String label, int y, Runnable action) {
        var widget = addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(panel + 8, y, width - panel - 16, 20).build());
        if (!label.equals("Back")) layout.put(widget, y);
        return widget;
    }
    @Override protected void init() {
        layout.clear(); panel = Math.max(100, width - 230);
        origins = button("Origin", 28, () -> cycleTown(true)); destinations = button("Destination", 52, () -> cycleTown(false));
        routes = button("Route", 76, this::cycleRoute);
        nameField = addRenderableWidget(new EditBox(font, panel + 8, 102, width - panel - 16, 20, Component.literal("Route name")));
        nameField.setMaxLength(48); nameField.setEditable(journey == null); nameField.setValue(name); nameField.setResponder(value -> { name = value; if (journey == null) update(); }); layout.put(nameField, 102);
        if (journey == null) {
            button("New route", 128, () -> { route = UUID.randomUUID(); revision = 0; points.clear(); path = new long[0]; nameField.setValue("Custom route"); data.remove("Sections"); update(); });
            preview = button("Preview route", 152, () -> send(Action.PREVIEW));
            save = button("Save route", 176, () -> send(Action.SAVE));
            remove = button("Remove saved route", 200, () -> send(Action.REMOVE));
            button("Next section", 224, () -> { section = (section + 1) % Math.max(1, data.getList("Sections", Tag.TAG_COMPOUND).size()); update(); });
            build = button("Build selected section", 248, () -> send(Action.BUILD));
            button("Undo last map point", 272, () -> { if (!points.isEmpty()) points.removeLast(); path = new long[0]; });
        } else {
            traveler = button("Traveler", 128, () -> { candidate++; update(); });
            assign = button("Add traveler", 152, () -> {
                var member = member(); if (member == null) return; UUID id = member.getUUID("Id");
                if (members.containsKey(id)) members.remove(id); else if (members.size() < 7) members.put(id, false); update();
            });
            role = button("Role", 176, () -> { var member = member(); if (member != null && members.containsKey(member.getUUID("Id"))) members.computeIfPresent(member.getUUID("Id"), (id, guard) -> !guard); update(); });
            configure = button("Save departure selection", 200, () -> send(Action.CONFIGURE));
            button("Recall travelers", 224, () -> send(Action.RECALL));
            button("Refresh status", 248, () -> send(Action.VIEW));
        }
        button("Back", height - 26, this::onClose); update(); arrange();
        if (!initialized && !pending) send(Action.VIEW);
    }
    private void arrange() {
        scroll = Math.clamp(scroll, 0, Math.max(0, 460 - height));
        layout.forEach((widget, y) -> { widget.setY(y - scroll); widget.visible = widget.getY() >= 26 && widget.getY() + widget.getHeight() <= height - 32; });
    }
    private void cycleTown(boolean first) {
        var choices = towns().stream().filter(row -> !first || row.getBoolean("Manage")).toList(); if (choices.isEmpty()) return;
        UUID current = first ? origin : destination; int index = -1;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).getUUID("Id").equals(current)) index = i;
        var next = choices.get((index + 1) % choices.size());
        if (first) { origin = next.getUUID("Id"); center(next); } else destination = next.getUUID("Id");
        route = null; revision = 0; points.clear(); path = new long[0]; send(Action.VIEW);
    }
    private void cycleRoute() {
        var choices = routeRows(); int index = -1;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).getUUID("Id").equals(route)) index = i;
        route = index + 1 >= choices.size() ? null : choices.get(index + 1).getUUID("Id");
        revision = 0; points.clear(); path = new long[0]; section = 0; loadRoute = route != null; send(Action.VIEW);
    }
    private void update() {
        if (origins == null) return;
        origins.setMessage(Component.literal("From: " + townName(origin))); destinations.setMessage(Component.literal("To: " + townName(destination)));
        origins.active = destinations.active = !pending && journey == null;
        var selected = routeRows().stream().filter(row -> row.getUUID("Id").equals(route)).findFirst().orElse(null);
        routes.setMessage(Component.literal(selected == null ? journey == null ? "Choose saved route" : "Route: Default" : selected.getString("Name"))); routes.active = !pending;
        if (journey == null) {
            preview.active = save.active = !pending && origin != null && destination != null && !origin.equals(destination) && !name.isBlank();
            remove.active = build.active = !pending && selected != null;
            build.setMessage(Component.literal("Build section " + (section + 1)));
        } else {
            nameField.setValue(selected == null ? "Default route" : selected.getString("Name"));
            var member = member(); boolean editable = !pending && !data.getBoolean("Departed");
            traveler.setMessage(Component.literal(member == null ? "No available travelers" : member.getString("Name") + (member.getBoolean("Leader") ? " (leader)" : "")));
            traveler.active = !pending && member != null;
            boolean chosen = member != null && members.containsKey(member.getUUID("Id"));
            assign.setMessage(Component.literal(chosen ? "Remove traveler" : "Add traveler")); assign.active = editable && member != null && !member.getBoolean("Leader");
            role.setMessage(Component.literal(chosen && members.get(member.getUUID("Id")) ? "Role: Guard" : "Role: Companion"));
            role.active = editable && chosen && member.getBoolean("Armed"); configure.active = editable && data.contains("JourneyRevision"); routes.active = editable;
        }
    }
    private void send(Action action) {
        if (pending) return;
        if (net.minecraft.Util.getMillis() - sent < 300) { queued = action; pending = true; update(); return; }
        if ((action == Action.PREVIEW || action == Action.SAVE) && route == null) route = UUID.randomUUID();
        sequence = com.jvn.villagerretaliation.client.settlement.VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new SettlementRouteRequest(sequence, action, origin, destination, route, journey,
                (action == Action.CONFIGURE || action == Action.RECALL) ? journeyRevision : revision, name, points, members, section));
        pending = true; sent = net.minecraft.Util.getMillis(); message = "Waiting for server"; update();
    }
    private MapView view() { return new MapView(panel, height, x, z, scale); }
    private BlockPos observed(int x, int z) {
        var repository = ExploredMapClient.repository(); if (repository == null) return null;
        var tile = repository.getSurface(MapSnapshot.tile(x), MapSnapshot.tile(z)); if (tile == null) return null;
        int index = Math.floorMod(z, 8) * 8 + Math.floorMod(x, 8);
        var column = tile.columns().stream().filter(value -> value.index() == index && !value.layers().isEmpty()).findFirst().orElse(null);
        return column == null ? null : new BlockPos(x, column.layers().getFirst().block().y() + 1, z);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        var view = view(); graphics.enableScissor(0, 0, panel, height); tiles.render(graphics, view, MapLighting.DAY);
        var sections = data.getList("Sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < path.length; i++) {
            var pos = BlockPos.of(path[i]); int px = (int) view.screenX(pos.getX() + .5), py = (int) view.screenY(pos.getZ() + .5);
            if (px < 0 || px >= panel || py < 0 || py >= height || observed(pos.getX(), pos.getZ()) == null) continue;
            int part = Math.max(0, i - 1) / 16;
            int color = part == section ? 0xffffdf70 : part < sections.size() && sections.getCompound(part).getDouble("Built") > 0 ? 0xffb8c7dc : 0xffa3bd80;
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
        }
        for (var point : points) graphics.drawCenteredString(font, "+", (int) view.screenX(point.getX()), (int) view.screenY(point.getZ()) - 4, 0xffd170);
        for (var town : towns()) {
            var pos = BlockPos.of(town.getLong("Position")); int px = (int) view.screenX(pos.getX()), py = (int) view.screenY(pos.getZ());
            graphics.drawCenteredString(font, town.getString("Name"), px, py, 0xffffff);
        }
        graphics.disableScissor(); graphics.fill(panel, 0, width, height, 0xf0202020);
        graphics.drawString(font, title, panel + 8, 10, 0xffffff);
        super.render(graphics, mouseX, mouseY, partial);
        graphics.enableScissor(panel, 26, width, height - 32);
        int textY = (journey == null ? 300 : 276) - scroll;
        if (journey == null && !sections.isEmpty()) {
            var part = sections.getCompound(section);
            String detail = "Section " + (section + 1) + "/" + sections.size() + "  Trail " + Math.round(part.getDouble("Trail") * 100) + "%  Road " + Math.round(part.getDouble("Built") * 100) + "%";
            graphics.drawWordWrap(font, Component.literal(detail + "\nEstimated crossing: " + Math.round(data.getDouble("Seconds")) + "s\n" + part.getString("Status")), panel + 8, textY, width - panel - 16, 0xcccccc); textY += 60;
        } else if (journey != null) {
            var member = member();
            graphics.drawWordWrap(font, Component.literal("Selected: leader + " + members.size() + "\n" + status(data.getString("JourneyStatus")) + "\nCargo: " + data.getString("Cargo")
                    + (member == null ? "" : "\n" + status(member.getString("Status")))), panel + 8, textY, width - panel - 16, 0xcccccc); textY += 74;
        }
        graphics.drawWordWrap(font, Component.literal(message), panel + 8, textY, width - panel - 16, 0xffd49b);
        graphics.disableScissor();
        var hint = Component.literal(journey == null ? "Right-click explored ground to add points. Drag to pan. Scroll the map to zoom or the panel for more controls. Saved edits affect future departures." : "Scroll the panel for status. Guards retain their equipment. Encounter protection is not implemented.");
        int hintHeight = font.split(hint, panel - 16).size() * font.lineHeight + 8;
        graphics.fill(0, height - hintHeight, panel, height, 0xd0202020);
        graphics.drawWordWrap(font, hint, 8, height - hintHeight + 4, panel - 16, 0xffffff);
    }
    private static String status(String value) { return value.toLowerCase(java.util.Locale.ROOT).replace('_', ' '); }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx < panel && button == 1 && journey == null && !pending && points.size() < 12) {
            var pos = observed((int) Math.floor(view().worldX(mx)), (int) Math.floor(view().worldZ(my)));
            if (pos == null) message = "Choose explored terrain";
            else { points.add(pos); path = new long[0]; message = "Point added. Preview to check the adjusted path"; }
            return true;
        }
        return mx < panel;
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx < panel && button == 0) { x -= dx / scale; z -= dy / scale; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx < panel) { scale = Math.clamp(scale * Math.pow(1.2, vertical), .05, 8); return true; }
        scroll -= (int) (vertical * 24); arrange(); return true;
    }
    @Override public void tick() {
        if (queued != null && net.minecraft.Util.getMillis() - sent >= 300) {
            var action = queued; queued = null; pending = false; send(action);
        }
        if (pending && net.minecraft.Util.getMillis() - sent > 10000) { pending = false; message = "No reply. Refresh or retry"; update(); }
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        PacketDistributor.sendToServer(new SettlementRouteRequest(com.jvn.villagerretaliation.client.settlement.VillageRelationsClient.nextRequest(),
                Action.CLOSE, origin, destination, route, journey, 0, name, List.of(), java.util.Map.of(), 0));
        minecraft.setScreen(parent);
    }
    @Override public void removed() { tiles.close(); }
}
