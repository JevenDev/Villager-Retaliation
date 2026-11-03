package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.settlement.SettlementConfirmScreen;
import com.jvn.villagerretaliation.client.settlement.SettlementTradePickerScreen;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
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
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private Action queued, sentAction = Action.VIEW;
    private long[] path = new long[0];
    private final java.util.Map<net.minecraft.client.gui.components.AbstractWidget, Integer> layout = new LinkedHashMap<>();
    private int scroll, tab, buildingTab;
    private final java.util.Map<net.minecraft.client.gui.components.AbstractWidget, Integer> groups = new LinkedHashMap<>();
    private Button recall;
    private boolean dirty, fitRequested = true;
    private Component hover;
    private EditBox nameField;
    private Button origins, destinations, routes, build, save, remove, preview, assign, role, traveler, configure;

    public SettlementRoutesScreen(Screen parent) { this(parent, null); }
    public SettlementRoutesScreen(Screen parent, UUID journey) { super(Component.literal(journey == null ? "Settlement routes" : "Caravan departure")); this.parent = parent; this.journey = journey; }
    public static void receive(SettlementRoutesPayload payload) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof SettlementTradePickerScreen picker) current = picker.previous();
        if (current instanceof SettlementRoutesScreen screen && screen.sequence == payload.sequence()) screen.accept(payload.data());
    }
    private void accept(CompoundTag value) {
        boolean first = !initialized;
        data = value; pending = false; message = value.getString("Message");
        if (sentAction == Action.SAVE && message.startsWith("Saved.") || sentAction == Action.CONFIGURE
                && (message.startsWith("Departure configured.") || message.startsWith("Transport terms updated."))) dirty = false;
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
                name = current.getString("Name"); nameField.setValue(name); dirty = false;
                if (journey == null) { origin = current.getUUID("Origin"); destination = current.getUUID("Destination"); }
                points.clear(); java.util.Arrays.stream(value.getLongArray("Points")).mapToObj(BlockPos::of).forEach(points::add); loadRoute = false;
            }
        }
        section = Math.min(section, Math.max(0, data.getList("Sections", Tag.TAG_COMPOUND).size() - 1)); update();
        if (first || fitRequested || value.contains("Preview")) { fitRoute(); fitRequested = first; }
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
    private Button button(String label, int y, Runnable action, String help) {
        var widget = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(label), ignored -> action.run()).bounds(panel + 8, y, width - panel - 16, 20).build());
        widget.setTooltip(Tooltip.create(Component.literal(help)));
        if (!label.equals("Back")) { layout.put(widget, y); groups.put(widget, buildingTab); }
        return widget;
    }
    @Override protected void init() {
        layout.clear(); groups.clear(); panel = Math.max(120, width - Math.min(246, width / 2 + 20));
        String[] tabs = journey == null ? new String[]{"Route", "Roads"} : new String[]{"Route", "Crew", "Delivery"};
        for (int i = 0; i < tabs.length; i++) {
            int index = i, w = (width - panel - 20) / tabs.length;
            var button = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(tabs[i]), ignored -> {
                tab = index; scroll = 0; arrange();
            }).bounds(panel + 8 + i * (w + 2), 30, w, 20).build());
            button.setTooltip(Tooltip.create(Component.literal("Inspect " + tabs[i].toLowerCase(java.util.Locale.ROOT))));
        }
        buildingTab = 0;
        origins = button("Origin", 68, () -> chooseTown(true), "Choose the settlement that sends the caravan");
        destinations = button("Destination", 94, () -> chooseTown(false), "Choose the other settlement");
        routes = button("Route", 120, this::chooseRoute, "Browse saved paths between settlements");
        nameField = addRenderableWidget(new EditBox(font, panel + 8, 160, width - panel - 16, 20, Component.literal("Route name")));
        nameField.setMaxLength(48); nameField.setEditable(journey == null); nameField.setValue(name);
        nameField.setResponder(value -> { name = value; if (journey == null) { dirty = true; update(); } });
        layout.put(nameField, 160); groups.put(nameField, 0);
        if (journey == null) {
            button("New route", 186, () -> {
                route = UUID.randomUUID(); revision = 0; points.clear(); invalidate();
                nameField.setValue("Custom route"); data.remove("Sections"); update();
            }, "Start a new path between the chosen settlements");
            preview = button("Preview route", 212, () -> send(Action.PREVIEW), "Check terrain and show the path before saving");
            save = button("Save route", 238, () -> send(Action.SAVE), "Save this path for future departures");
            button("Undo last map point", 264, () -> {
                if (!points.isEmpty()) points.removeLast(); invalidate(); update();
            }, "Remove the last numbered waypoint and preview again");
            buildingTab = 1;
            button("Choose road section", 68, this::chooseSection, "Inspect a section and its construction progress");
            build = button("Build selected section", 94, () -> send(Action.BUILD), "Request road construction for the highlighted section");
            remove = button("Remove saved route", 120, () -> confirm(Action.REMOVE), "Delete the saved route. Active departures keep their route.");
        } else {
            configure = button("Save departure selection", 190, () -> send(Action.CONFIGURE),
                    "Apply the selected path and crew before departure");
            buildingTab = 1;
            traveler = button("Choose traveler", 68, this::chooseTraveler, "Browse residents, equipment eligibility, and assigned roles");
            assign = button("Add traveler", 94, () -> {
                var member = member(); if (member == null) return; UUID id = member.getUUID("Id");
                if (members.containsKey(id)) members.remove(id); else if (members.size() < 7) members.put(id, false);
                dirty = true; update();
            }, "Add up to seven companions alongside the caravan leader");
            role = button("Role", 120, () -> {
                var member = member();
                if (member != null && members.containsKey(member.getUUID("Id"))) members.computeIfPresent(member.getUUID("Id"), (id, guard) -> !guard);
                dirty = true; update();
            }, "Armed residents can serve as guards. All travelers retain their equipment.");
            button("Save crew", 146, () -> send(Action.CONFIGURE), "Apply these assignments before departure");
            buildingTab = 2;
            recall = button("Recall travelers", 68, () -> confirm(Action.RECALL), "Recall the caravan and return surviving cargo");
            button("Refresh status", 94, () -> send(Action.VIEW), "Request the latest journey and cargo status");
        }
        button("Back", height - 26, this::onClose, "Return to the previous screen");
        var fit = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Fit route"), ignored -> fitRoute()).bounds(10, 10, 76, 20).build());
        fit.setTooltip(Tooltip.create(Component.literal("Show both settlements and the selected path")));
        update(); arrange();
        if (!initialized && !pending) send(Action.VIEW);
    }

    private int contentHeight() {
        int bottom = tab == 0 ? journey == null ? 354 : 310 : journey == null ? 244 : tab == 1 ? 228 + (members.size() + 1) * 14 : 250;
        return bottom;
    }

    private void arrange() {
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() - (height - 40)));
        layout.forEach((widget, y) -> {
            widget.setY(y - scroll);
            widget.visible = groups.get(widget) == tab && widget.getY() >= 58 && widget.getBottom() <= height - 40;
        });
    }

    private void chooseTown(boolean first) {
        var choices = towns().stream().filter(row -> (!first || row.getBoolean("Manage"))
                && !row.getUUID("Id").equals(first ? destination : origin)).map(row ->
                new SettlementTradePickerScreen.Choice(Component.literal(row.getString("Name")),
                        Component.literal(first ? "Caravan origin" : "Caravan destination"), new ItemStack(Items.BELL), 1, ignored -> {
                    if (first) origin = row.getUUID("Id"); else destination = row.getUUID("Id");
                    route = null; revision = 0; points.clear(); invalidate(); fitRoute(); send(Action.VIEW);
                })).toList();
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal(first ? "Choose origin" : "Choose destination"), choices, false));
    }

    private void chooseRoute() {
        var choices = new ArrayList<SettlementTradePickerScreen.Choice>();
        if (journey != null) choices.add(new SettlementTradePickerScreen.Choice(Component.literal("Automatic route"),
                Component.literal("Find a feasible path at departure"), new ItemStack(Items.COMPASS), 1, ignored -> selectRoute(null)));
        for (var row : routeRows()) choices.add(new SettlementTradePickerScreen.Choice(Component.literal(row.getString("Name")),
                Component.literal(townName(row.getUUID("Origin")) + " → " + townName(row.getUUID("Destination"))),
                new ItemStack(Items.MAP), 1, ignored -> selectRoute(row.getUUID("Id"))));
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal("Choose saved route"), choices, false));
    }

    private void selectRoute(UUID id) {
        route = id; revision = 0; points.clear(); path = new long[0]; section = 0;
        data.remove("Sections"); loadRoute = route != null; fitRequested = true; dirty = journey != null; send(Action.VIEW);
    }

    private void chooseTraveler() {
        var choices = new ArrayList<SettlementTradePickerScreen.Choice>();
        for (int i = 0; i < candidates().size(); i++) {
            int index = i; var row = candidates().get(i);
            String role = row.getBoolean("Leader") ? "Caravan leader" : members.containsKey(row.getUUID("Id"))
                    ? members.get(row.getUUID("Id")) ? "Assigned guard" : "Assigned companion" : "Not assigned";
            choices.add(new SettlementTradePickerScreen.Choice(Component.literal(row.getString("Name")),
                    Component.literal(role + " • " + (row.getBoolean("Armed") ? "Guard eligible" : "Companion only")),
                    new ItemStack(row.getBoolean("Armed") ? Items.IRON_SWORD : Items.LEATHER_BOOTS), 1, ignored -> { candidate = index; update(); }));
        }
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal("Choose traveler"), choices, false));
    }

    private void chooseSection() {
        var choices = new ArrayList<SettlementTradePickerScreen.Choice>();
        var sections = rows("Sections");
        for (int i = 0; i < sections.size(); i++) {
            int index = i; var part = sections.get(i);
            choices.add(new SettlementTradePickerScreen.Choice(Component.literal("Section " + (i + 1)),
                    Component.literal("Road " + Math.round(part.getDouble("Built") * 100) + "% • " + status(part.getString("Status"))),
                    new ItemStack(Items.COBBLESTONE), 1, ignored -> { section = index; update(); }));
        }
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal("Choose road section"), choices, false));
    }

    private void confirm(Action action) {
        minecraft.setScreen(new SettlementConfirmScreen(this, Component.literal(action == Action.REMOVE ? "Remove route?" : "Recall caravan?"),
                Component.literal(action == Action.REMOVE ? "Remove this saved path from future departure choices?"
                        : "Turn the travelers back and return their surviving cargo?"), () -> send(action)));
    }

    private void invalidate() { path = new long[0]; data.remove("Sections"); dirty = true; }

    private void fitRoute() {
        var positions = new ArrayList<BlockPos>();
        towns().stream().filter(row -> row.getUUID("Id").equals(origin) || row.getUUID("Id").equals(destination))
                .forEach(row -> positions.add(BlockPos.of(row.getLong("Position"))));
        for (long position : path) positions.add(BlockPos.of(position));
        if (positions.isEmpty()) return;
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0), maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0), maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        x = (minX + maxX) / 2.0; z = (minZ + maxZ) / 2.0;
        scale = Math.clamp(Math.min((panel - 70.0) / Math.max(32, maxX - minX), (height - 120.0) / Math.max(32, maxZ - minZ)), .05, 3);
    }
    private void update() {
        if (origins == null) return;
        for (var widget : layout.keySet()) widget.active = !pending;
        nameField.setEditable(journey == null && !pending);
        origins.setMessage(Component.literal("From: " + townName(origin))); destinations.setMessage(Component.literal("To: " + townName(destination)));
        origins.active = destinations.active = !pending && journey == null;
        var selected = routeRows().stream().filter(row -> row.getUUID("Id").equals(route)).findFirst().orElse(null);
        routes.setMessage(Component.literal(selected == null ? journey == null ? "Choose saved route" : "Route: Default" : selected.getString("Name"))); routes.active = !pending;
        if (journey == null) {
            preview.active = save.active = !pending && origin != null && destination != null && !origin.equals(destination) && !name.isBlank();
            remove.active = !pending && selected != null;
            build.active = remove.active && !rows("Sections").isEmpty();
            save.active &= path.length > 0;
            build.setMessage(Component.literal("Build section " + (section + 1)));
            layout.keySet().stream().filter(widget -> widget.getMessage().getString().equals("Choose road section")).forEach(widget -> widget.active = !pending && !rows("Sections").isEmpty());
        } else {
            nameField.setValue(selected == null ? "Default route" : selected.getString("Name"));
            var member = member(); boolean editable = !pending && data.getBoolean("CanConfigure");
            traveler.setMessage(Component.literal(member == null ? "No available travelers" : member.getString("Name") + (member.getBoolean("Leader") ? " (leader)" : "")));
            traveler.active = !pending && member != null;
            boolean chosen = member != null && members.containsKey(member.getUUID("Id"));
            assign.setMessage(Component.literal(chosen ? "Remove traveler" : "Add traveler")); assign.active = editable && member != null && !member.getBoolean("Leader") && (chosen || members.size() < 7);
            role.setMessage(Component.literal(chosen && members.get(member.getUUID("Id")) ? "Role: Guard" : "Role: Companion"));
            role.active = editable && chosen && member.getBoolean("Armed"); configure.active = editable && data.contains("JourneyRevision"); routes.active = editable;
            recall.active = !pending && data.getBoolean("CanRecall");
            layout.keySet().stream().filter(widget -> widget.getMessage().getString().equals("Save crew")).forEach(widget -> widget.active = configure.active);
        }
    }
    private void send(Action action) {
        if (pending) return;

        if (net.minecraft.Util.getMillis() - sent < 300) { queued = action; pending = true; update(); return; }
        if ((action == Action.PREVIEW || action == Action.SAVE) && route == null) route = UUID.randomUUID();
        sequence = com.jvn.villagerretaliation.client.settlement.VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new SettlementRouteRequest(sequence, action, origin, destination, route, journey,
                (action == Action.CONFIGURE || action == Action.RECALL) ? journeyRevision : revision, name, points, members, section));
        sentAction = action; pending = true; sent = net.minecraft.Util.getMillis(); message = "Waiting for server"; update();
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
        hover = null;
        var view = view();
        graphics.fill(0, 0, width, height, 0xFF17121E);
        graphics.enableScissor(0, 0, panel, height);
        tiles.render(graphics, view, MapLighting.DAY);
        var sections = rows("Sections");
        for (int i = 0; i < path.length; i++) {
            var pos = BlockPos.of(path[i]);
            if (observed(pos.getX(), pos.getZ()) == null) continue;
            int px = (int) view.screenX(pos.getX() + .5), py = (int) view.screenY(pos.getZ() + .5);
            int part = Math.max(0, i - 1) / 16;
            int color = journey == null && tab == 1 && part == section ? 0xffffdf70
                    : part < sections.size() && sections.get(part).getDouble("Built") >= 1 ? 0xffb8c7dc : 0xffa3bd80;
            if (i > 0) {
                var previous = BlockPos.of(path[i - 1]);
                if (observed(previous.getX(), previous.getZ()) != null)
                    VillageRelationsMap.line(graphics, view, view.screenX(previous.getX() + .5), view.screenY(previous.getZ() + .5), px, py, color);
                if (i % 32 == 0 && px > 8 && px < panel - 8 && py > 40 && py < height - 65) {
                    double angle = Math.atan2(pos.getZ() - previous.getZ(), pos.getX() - previous.getX());
                    if (data.getString("Leg").equals("HOMEBOUND") || data.getString("Leg").equals("RETURN_APPROACH")) angle += Math.PI;
                    for (double wing : new double[]{-.6, .6}) VillageRelationsMap.line(graphics, view, px, py,
                            px - Math.cos(angle + wing) * 7, py - Math.sin(angle + wing) * 7, 0xFFFFFFFF);
                }
            }
            if (px >= 0 && px < panel && py >= 0 && py < height) graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
        }
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            marker(graphics, view, point, Integer.toString(i + 1), "Waypoint " + (i + 1), 0xFFFFDF70, mouseX, mouseY);
        }
        for (var town : towns()) {
            boolean from = town.getUUID("Id").equals(origin), to = town.getUUID("Id").equals(destination);
            marker(graphics, view, BlockPos.of(town.getLong("Position")), from ? "A" : to ? "B" : "•",
                    town.getString("Name"), from ? 0xFFA3BD80 : to ? 0xFFFFDF70 : 0xFFB9C1CB, mouseX, mouseY);
        }
        if (data.contains("CaravanPosition")) {
            var position = BlockPos.of(data.getLong("CaravanPosition"));
            if (observed(position.getX(), position.getZ()) != null) marker(graphics, view, position, "C", "Caravan", 0xFFFFFFFF, mouseX, mouseY);
        }
        graphics.disableScissor();
        TooltipRenderUtil.renderTooltipBackground(graphics, panel + 5, 7, width - panel - 12, height - 14, 0);
        graphics.drawString(font, title, panel + 10, 12, 0xFFFFFF);
        String[] labels = journey == null ? new String[]{"Route", "Roads"} : new String[]{"Route", "Crew", "Delivery"};
        int tabWidth = (width - panel - 20) / labels.length;
        graphics.fill(panel + 10 + tab * (tabWidth + 2), 52, panel + 6 + tab * (tabWidth + 2) + tabWidth, 53, 0xFFB9C1CB);
        super.render(graphics, mouseX, mouseY, partial);
        graphics.enableScissor(panel + 4, 58, width - 4, height - 40);
        int textY;
        if (tab == 0) {
            text(graphics, "Route name", 147, 0xB9C1CB);
            textY = journey == null ? 296 : 220;
            paragraph(graphics, journey == null ? "1. Choose endpoints\n2. Add optional map points\n3. Preview, then save"
                    : !data.getBoolean("CanConfigure") ? "Route and crew cannot be changed here. Use Delivery to inspect the caravan."
                    : "Choose the route and Crew, then save. Transport changes to a trade require the other settlement to accept.", textY, 0xB9C1CB);
            textY += 48;
        } else if (journey == null) {
            textY = 154;
            if (sections.isEmpty()) paragraph(graphics, "Choose a saved route in the Route tab to inspect its road sections.", textY, 0xB9C1CB);
            else {
                var part = sections.get(section);
                paragraph(graphics, "Section " + (section + 1) + " of " + sections.size()
                        + "\nTrail " + Math.round(part.getDouble("Trail") * 100) + "% • Road " + Math.round(part.getDouble("Built") * 100)
                        + "%\nEstimated crossing: " + Math.round(data.getDouble("Seconds")) + "s\n"
                        + status(part.getString("Status")), textY, 0xB9C1CB);
            }
            textY += 70;
        } else if (tab == 1) {
            textY = 180;
            text(graphics, "Crew: leader + " + members.size() + " / 7 companions", textY, 0xFFFFFF);
            textY += 18;
            for (var resident : candidates()) {
                UUID id = resident.getUUID("Id");
                if (!resident.getBoolean("Leader") && !members.containsKey(id)) continue;
                String role = resident.getBoolean("Leader") ? "Leader" : members.get(id) ? "Guard" : "Companion";
                text(graphics, resident.getString("Name") + " • " + role, textY, 0xB9C1CB); textY += 14;
            }
            if (members.isEmpty()) { text(graphics, "No companions assigned", textY, 0xB9C1CB); textY += 14; }
        } else {
            textY = 130;
            paragraph(graphics, status(data.getString("JourneyStatus")) + "\n" + status(data.getString("Leg")) + "\n\nCargo: " + status(data.getString("Cargo")), textY, 0xB9C1CB);
            textY += 85;
        }
        graphics.disableScissor();
        String feedback = dirty ? "Unsaved selection" + (message.isEmpty() ? "" : " • " + message) : message;
        graphics.drawString(font, font.plainSubstrByWidth(feedback, width - panel - 20), panel + 10, height - 40, 0xFFE1A0);
        if (mouseX > panel && mouseY >= height - 42 && mouseY < height - 28 && !feedback.isEmpty()) hover = Component.literal(feedback);
        int maxScroll = Math.max(0, contentHeight() - (height - 40));
        if (maxScroll > 0) {
            int track = height - 104, thumb = Math.max(12, track * track / (track + maxScroll));
            int sy = 60 + scroll * (track - thumb) / maxScroll;
            graphics.fill(width - 4, sy, width - 3, sy + thumb, 0xFFB9C1CB);
        }
        var hint = Component.literal(journey == null ? "Drag to pan • Scroll to zoom\nRight-click explored ground to add a waypoint" : "Drag to pan • Scroll to zoom\nA: caravan origin • B: destination");
        int hintWidth = Math.max(40, panel - 24), hintHeight = font.split(hint, hintWidth).size() * font.lineHeight;
        TooltipRenderUtil.renderTooltipBackground(graphics, 10, height - hintHeight - 13, hintWidth, hintHeight, 0);
        graphics.drawWordWrap(font, hint, 10, height - hintHeight - 13, hintWidth, 0xB9C1CB);
        if (panel > 220) {
            TooltipRenderUtil.renderTooltipBackground(graphics, 10, 40, 156, 35, 0);
            graphics.drawString(font, "A  Origin     B  Destination", 10, 40, 0xFFFFFF);
            graphics.drawString(font, "Green: planned path", 10, 52, 0xA3BD80);
            graphics.drawString(font, tab == 1 && journey == null ? "Gold: selected road section" : "Gray: completed road", 10, 64, tab == 1 && journey == null ? 0xFFDF70 : 0xB8C7DC);
        }
        if (hover != null) graphics.renderTooltip(font, font.split(hover, 260), mouseX, mouseY);
    }

    private void text(GuiGraphics graphics, String value, int y, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, width - panel - 20), panel + 10, y - scroll, color);
    }

    private void paragraph(GuiGraphics graphics, String value, int y, int color) {
        graphics.drawWordWrap(font, Component.literal(value), panel + 10, y - scroll, width - panel - 20, color);
    }

    private void marker(GuiGraphics graphics, MapView view, BlockPos pos, String symbol, String label, int color, int mouseX, int mouseY) {
        int px = (int) view.screenX(pos.getX()), py = (int) view.screenY(pos.getZ());
        if (px < 10 || px > panel - 10 || py < 36 || py > height - 60) return;
        graphics.fill(px - 7, py - 7, px + 8, py + 8, 0xF0100010);
        graphics.renderOutline(px - 7, py - 7, 15, 15, color);
        graphics.drawCenteredString(font, symbol, px, py - 4, color);
        if (!symbol.equals("•")) {
            String name = font.plainSubstrByWidth(label, 120);
            int labelWidth = font.width(name);
            TooltipRenderUtil.renderTooltipBackground(graphics, px - labelWidth / 2, py + 12, labelWidth, font.lineHeight, 0);
            graphics.drawCenteredString(font, name, px, py + 12, 0xFFFFFF);
        }
        if (Math.abs(mouseX - px) <= 10 && Math.abs(mouseY - py) <= 10)
            hover = Component.literal(label + "\n" + pos.getX() + ", " + pos.getZ());
    }
    private static String status(String value) { return value.toLowerCase(java.util.Locale.ROOT).replace('_', ' '); }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx < panel && my > 80 && my < height - 60 && button == 1 && journey == null && tab == 0 && !pending && points.size() < 12) {
            var pos = observed((int) Math.floor(view().worldX(mx)), (int) Math.floor(view().worldZ(my)));
            if (pos == null) message = "Choose explored terrain";
            else { points.add(pos); invalidate(); update(); message = "Point added. Preview to check the adjusted path"; }
            return true;
        }
        return mx < panel;
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx < panel && button == 0) { x -= dx / scale; z -= dy / scale; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scroll += key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 72 : -72; arrange(); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx < panel) { scale = Math.clamp(scale * Math.pow(1.2, vertical), .05, 8); return true; }
        scroll -= (int) (vertical * 24); arrange(); return true;
    }
    @Override public void tick() {
        if (queued != null && net.minecraft.Util.getMillis() - sent >= 300) {
            var action = queued; queued = null; pending = false; send(action);
        }
        if (journey != null && !pending && !dirty && initialized && net.minecraft.Util.getMillis() - sent > 5000) send(Action.VIEW);
        if (pending && net.minecraft.Util.getMillis() - sent > 10000) { pending = false; message = "No reply. Refresh or retry"; update(); }
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (dirty) {
            minecraft.setScreen(new SettlementConfirmScreen(this, Component.literal("Discard unsaved selection?"),
                    Component.literal("Leave without saving the route or crew changes?"), this::close));
        } else close();
    }
    private void close() {
        PacketDistributor.sendToServer(new SettlementRouteRequest(com.jvn.villagerretaliation.client.settlement.VillageRelationsClient.nextRequest(),
                Action.CLOSE, origin, destination, route, journey, 0, name, List.of(), java.util.Map.of(), 0));
        minecraft.setScreen(parent);
    }
    @Override public void removed() { tiles.close(); }
}
