package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.ui.VillagerTooltips;
import com.jvn.villagerretaliation.client.settlement.SettlementConfirmScreen;
import com.jvn.villagerretaliation.client.settlement.SettlementTradePickerScreen;
import com.jvn.villagerretaliation.client.settlement.WorldViewMenu;
import com.jvn.villagerretaliation.client.settlement.SettlementBuildOverlay;
import com.jvn.villagerretaliation.client.settlement.SettlementPathGizmo;
import com.jvn.villagerretaliation.settlement.SettlementPath;
import com.jvn.villagerretaliation.settlement.SettlementRouteShape;
import net.minecraft.world.phys.Vec3;
import com.jvn.villagerretaliation.client.ui.RouteButton;
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

public final class SettlementRoutesScreen extends Screen implements VillagerTooltips.Provider {
    private enum Tool { DRAW, EDIT, PAN }
    private Tool tool = Tool.DRAW;
    private final SettlementBuildOverlay nodeToolbar = new SettlementBuildOverlay();
    private final List<net.minecraft.client.gui.components.AbstractWidget> nodeButtons = new ArrayList<>();
    private final List<SettlementBuildOverlay.ActionButton> toolButtons = new ArrayList<>();
    private final MapRouteCursor cursor = new MapRouteCursor();
    private String previewProblem = "", saveProblem = "";
    private Button smoothNode, straightenNode, linkHandles, insertNode, removeNode, showProblem;
    private final Screen parent;
    private final UUID journey;
    private final ExploredMapRenderer tiles = new ExploredMapRenderer();
    private SettlementRouteMapEditor editor;
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
    private Button recall, waypoint, undo, redo, curves;
    private boolean panning, previewWanted;
    private long previewAt;
    private boolean dirty, fitRequested = true;
    private Component hover;
    private WorldViewMenu context;
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
        if (sentAction == Action.SAVE) saveProblem = message.startsWith("Saved.") ? "" : message.isBlank() ? "Save was not confirmed" : message;
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
        else if (value.contains("Path") && (journey != null || loadRoute || sentAction == Action.SAVE && message.startsWith("Saved.") || sentAction == Action.BUILD)) path = value.getLongArray("Path");
        else if (sentAction == Action.PREVIEW) path = new long[0];
        var current = routeRows().stream().filter(row -> row.getUUID("Id").equals(route)).findFirst().orElse(null);
        if (current != null) {
            revision = current.getLong("Revision");
            if (loadRoute) {
                name = current.getString("Name"); nameField.setValue(name); dirty = false;
                if (journey == null) { origin = current.getUUID("Origin"); destination = current.getUUID("Destination"); }
                editor = createEditor(value); previewProblem = saveProblem = ""; loadRoute = false;
                if (editor != null) applyTool();
            }
        }
        if (journey == null && editor == null && origin != null && destination != null) { editor = createEditor(new CompoundTag()); applyTool(); }
        if (journey == null && sentAction == Action.PREVIEW) {
            data.remove("Sections"); previewWanted = message.startsWith("Surveying route"); previewAt = net.minecraft.Util.getMillis() + 500;
            previewProblem = value.contains("Preview") || previewWanted ? "" : message.isBlank() ? "No traversable route was found" : message;
            if (value.contains("Preview")) saveProblem = "";
        }
        section = Math.min(section, Math.max(0, data.getList("Sections", Tag.TAG_COMPOUND).size() - 1)); update();
        if (first || fitRequested) { fitRoute(); fitRequested = first; }
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
    private SettlementBuildOverlay.ActionButton action(String label, int y, Runnable run, String help) {
        var button = addRenderableWidget(new SettlementBuildOverlay.ActionButton(Component.literal(label), ignored -> run.run()));
        button.setX(panel + 8); button.setY(y); button.setWidth(width - panel - 16); button.setHeight(20);
        button.setTooltip(Tooltip.create(Component.literal(help))); layout.put(button, y); groups.put(button, 0); return button;
    }
    private List<BlockPos> points() { return editor == null ? List.of() : editor.points(); }
    private SettlementRouteMapEditor createEditor(CompoundTag value) {
        if (value.contains("Shape", Tag.TAG_COMPOUND)) return new SettlementRouteMapEditor(SettlementRouteShape.load(value.getCompound("Shape")));
        var from = townPosition(origin); var to = townPosition(destination);
        if (from == null || to == null) return null;
        var nodes = new ArrayList<SettlementPath.Node>(); nodes.add(new SettlementPath.Node(Vec3.atBottomCenterOf(from)));
        for (long point : value.getLongArray("Points")) nodes.add(new SettlementPath.Node(Vec3.atBottomCenterOf(BlockPos.of(point))));
        nodes.add(new SettlementPath.Node(Vec3.atBottomCenterOf(to)));
        return new SettlementRouteMapEditor(new SettlementRouteShape(nodes, false));
    }
    private void edit(int action) {
        if (editor == null || editor.dragging()) return;
        var before = editor.shape(); var draft = editor.draft();
        switch (action) {
            case 0 -> draft.smooth();
            case 1 -> editor.undo();
            case 2 -> editor.redo();
            case 3 -> {
                if (editor.insertable()) { setTool(Tool.DRAW); editor.insert(); }
            }
            case 4 -> editor.remove();
            case 5 -> setTool(tool == Tool.DRAW ? Tool.EDIT : Tool.DRAW);
            case 6 -> editor.curveSelected(true);
            case 7 -> editor.curveSelected(false);
            case 8 -> editor.toggleHandles();
            default -> throw new IllegalArgumentException("Unknown route edit");
        }
        if (!before.equals(editor.shape())) invalidate();
        context = null; update();
    }
    private void setTool(Tool value) {
        if (editor != null) editor.cancel();
        setFocused(null); tool = value; panning = false; context = null; applyTool(); update();
    }
    private void applyTool() {
        if (editor == null) return;
        editor.draft().inserting(false);
        if (tool == Tool.DRAW) editor.draft().extend(); else editor.draft().stopExtending();
    }
    private SettlementBuildOverlay.ActionButton nodeAction(String label, int action, String help) {
        var button = addRenderableWidget(new SettlementBuildOverlay.ActionButton(Component.literal(label), ignored -> edit(action)));
        button.setTooltip(Tooltip.create(Component.literal(help))); nodeButtons.add(button); return button;
    }
    private boolean routeEditing() { return journey == null && tab == 0; }
    private int contentBottom() { return height - (routeEditing() ? 110 : 40); }
    private double mapBottom() { return routeEditing() && nodeToolbar.controls() != null ? nodeToolbar.controls().top() - 12 : height - 60; }
    private void focus(Vec3 point) { x = point.x; z = point.z - ((68 + mapBottom()) / 2 - height / 2.0) / scale; }
    private Action pendingAction() { return pending ? queued != null ? queued : sentAction : null; }
    private boolean overNodeToolbar(double mx, double my) {
        return routeEditing() && nodeToolbar.controls() != null && nodeToolbar.controls().contains(mx, my);
    }
    @Override protected void init() {
        if (editor != null) editor.cancel();
        panning = false; context = null;
        layout.clear(); groups.clear(); nodeButtons.clear(); toolButtons.clear(); panel = Math.max(120, width - Math.min(246, width / 2 + 20));
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
        destinations = button("Destination", 92, () -> chooseTown(false), "Choose the other settlement");
        routes = button("Route", 116, this::chooseRoute, "Browse saved paths between settlements");
        nameField = addRenderableWidget(new EditBox(font, panel + 8, 154, width - panel - 16, 20, Component.literal("Route name")));
        nameField.setMaxLength(48); nameField.setEditable(journey == null); nameField.setValue(name);
        nameField.setResponder(value -> { name = value; if (journey == null) { dirty = true; update(); } });
        layout.put(nameField, 154); groups.put(nameField, 0);
        if (journey == null) {
            var newRoute = button("New route", 180, () -> {
                route = UUID.randomUUID(); revision = 0; editor = createEditor(new CompoundTag()); setTool(Tool.DRAW); invalidate();
                nameField.setValue("Custom route"); data.remove("Sections"); update();
            }, "Start a new path between the chosen settlements");
            preview = button("Check route", height - 72, () -> send(Action.PREVIEW), "Check the current draft against terrain");
            save = button("Save route", height - 72, () -> send(Action.SAVE), "Save this checked route for future departures");
            layout.remove(preview); groups.remove(preview); layout.remove(save); groups.remove(save);
            waypoint = action("Nodes", 180, this::chooseWaypoint, "Browse route nodes, including both village endpoints");
            curves = action("Lines", 204, () -> edit(0), "Choose lines or curves for drawing, using the world-view path controls");
            undo = action("Undo", 228, () -> edit(1), "Undo the last route edit. Ctrl+Z").shortcut("Ctrl+Z");
            redo = action("Redo", 228, () -> edit(2), "Redo the last route edit. Ctrl+Y").shortcut("Ctrl+Y");
            pair(newRoute, waypoint); pair(preview, save); pair(undo, redo);
            int toolWidth = Math.min(70, (panel - 24) / 3);
            for (var value : Tool.values()) {
                String label = switch (value) { case DRAW -> "Draw"; case EDIT -> "Edit"; case PAN -> "Pan"; };
                var button = addRenderableWidget(new SettlementBuildOverlay.ActionButton(Component.literal(label), ignored -> setTool(value)).selected(() -> tool == value));
                button.setX(10 + value.ordinal() * (toolWidth + 2)); button.setY(40); button.setWidth(toolWidth); button.setHeight(20);
                button.setTooltip(Tooltip.create(Component.literal(switch (value) {
                    case DRAW -> "Place route nodes and click-drag to draw curves";
                    case EDIT -> "Select and move existing nodes or curve handles without adding nodes";
                    case PAN -> "Drag the map without changing the route";
                }))); toolButtons.add(button);
            }
            smoothNode = nodeAction("Smooth", 6, "Give the selected node curve handles");
            straightenNode = nodeAction("Straighten", 7, "Remove both curve handles from the selected node");
            linkHandles = nodeAction("Linked", 8, "Toggle linked or independent handles. Alt-drag also adjusts one side.");
            insertNode = nodeAction("Insert", 3, "Switch to Draw and place the next node after the selection");
            removeNode = nodeAction("Remove", 4, "Remove the selected node. Village endpoints stay fixed.");
            showProblem = addRenderableWidget(new SettlementBuildOverlay.ActionButton(Component.literal("Show problem"), ignored -> {
                var problem = BlockPos.of(data.getLong("Blocked")); scale = Math.max(scale, 1); focus(Vec3.atBottomCenterOf(problem));
            }));
            showProblem.setTooltip(Tooltip.create(Component.literal("Center the map on the blocked terrain")));
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

    private void pair(Button first, Button second) {
        int w = (width - panel - 20) / 2;
        first.setWidth(w); second.setWidth(w); second.setX(panel + 12 + w);
    }

    private int contentHeight() {
        int bottom = tab == 0 ? journey == null ? 252 : 310 : journey == null ? 244 : tab == 1 ? 228 + (members.size() + 1) * 14 : 250;
        return bottom;
    }

    private void arrange() {
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() - contentBottom()));
        layout.forEach((widget, y) -> {
            widget.setY(y - scroll);
            widget.visible = groups.get(widget) == tab && widget.getY() >= 58 && widget.getBottom() <= contentBottom();
        });
        if (journey == null) {
            preview.visible = save.visible = routeEditing(); preview.setY(height - 72); save.setY(height - 72);
            showProblem.visible = routeEditing() && data.contains("Blocked") && previewStatus().equals("Blocked");
            showProblem.setX(width - 98); showProblem.setY(height - 102); showProblem.setWidth(86); showProblem.setHeight(18);
            boolean visible = routeEditing() && editor != null && tool != Tool.PAN;
            nodeButtons.forEach(button -> button.visible = visible);
            toolButtons.forEach(button -> button.visible = routeEditing());
            String selection = editor == null || editor.draft().selected() < 0 ? "Select a node to edit" : editor.draft().selected() == 0 ? "Origin node"
                    : editor.draft().selected() == editor.shape().nodes().size() - 1 ? "Destination node" : "Node " + editor.draft().selected() + " selected";
            nodeToolbar.summary(List.of(Component.literal(selection)), List.of());
            nodeToolbar.layout(font, nodeButtons, panel, height, 10);
        }
    }

    private void chooseTown(boolean first) {
        var choices = towns().stream().filter(row -> (!first || row.getBoolean("Manage"))
                && !row.getUUID("Id").equals(first ? destination : origin)).map(row ->
                new SettlementTradePickerScreen.Choice(Component.literal(row.getString("Name")),
                        Component.literal(first ? "Caravan origin" : "Caravan destination"), new ItemStack(Items.BELL), 1, ignored -> {
                    if (first) origin = row.getUUID("Id"); else destination = row.getUUID("Id");
                    route = null; revision = 0; editor = createEditor(new CompoundTag()); applyTool(); invalidate(); fitRoute(); send(Action.VIEW);
                })).toList();
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal(first ? "Choose origin" : "Choose destination"), choices, false));
    }

    private void chooseRoute() {
        var choices = new ArrayList<SettlementTradePickerScreen.Choice>();
        if (journey != null) choices.add(new SettlementTradePickerScreen.Choice(Component.literal("Automatic route"),
                Component.literal("Find a feasible path at departure"), new ItemStack(Items.COMPASS), 1, ignored -> selectRoute(null)));
        for (var row : routeRows()) choices.add(new SettlementTradePickerScreen.Choice(Component.literal(row.getString("Name")),
                Component.literal(townName(row.getUUID("Origin")) + " → " + townName(row.getUUID("Destination"))),
                new ItemStack(Items.FILLED_MAP), 1, ignored -> selectRoute(row.getUUID("Id"))));
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal("Choose saved route"), choices, false));
    }

    private void chooseWaypoint() {
        if (editor == null) return;
        var choices = new ArrayList<SettlementTradePickerScreen.Choice>();
        var nodes = editor.draft().path().nodes();
        for (int i = 0; i < nodes.size(); i++) {
            int index = i; var point = nodes.get(i).position();
            choices.add(new SettlementTradePickerScreen.Choice(Component.literal(i == 0 ? "Origin" : i == nodes.size() - 1 ? "Destination" : "Node " + i),
                    Component.literal((int) point.x + ", " + (int) point.z + " • Select to edit"), new ItemStack(Items.RECOVERY_COMPASS), 1, ignored -> {
                editor.select(index); setTool(Tool.EDIT); focus(point); update();
            }));
        }
        minecraft.setScreen(new SettlementTradePickerScreen(this, Component.literal("Route nodes"), choices, false));
    }
    private void selectRoute(UUID id) {
        route = id; revision = 0; editor = null; path = new long[0]; section = 0;
        data.remove("Sections"); loadRoute = route != null; fitRequested = true; dirty = journey != null; previewWanted = false; previewProblem = saveProblem = ""; send(Action.VIEW);
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

    private void invalidate() {
        path = new long[0]; data.remove("Sections"); data.remove("Blocked"); dirty = true; previewProblem = saveProblem = "";
        if (pending && pendingAction() == Action.PREVIEW) { pending = false; queued = null; sequence = com.jvn.villagerretaliation.client.settlement.VillageRelationsClient.nextRequest(); }
        previewWanted = true; previewAt = net.minecraft.Util.getMillis() + 150;
    }

    private void fitRoute() {
        var positions = new ArrayList<BlockPos>();
        towns().stream().filter(row -> row.getUUID("Id").equals(origin) || row.getUUID("Id").equals(destination))
                .forEach(row -> positions.add(BlockPos.of(row.getLong("Position"))));
        if (editor != null) for (var node : editor.shape().nodes()) {
            positions.add(BlockPos.containing(node.position())); positions.add(BlockPos.containing(node.position().add(node.incoming())));
            positions.add(BlockPos.containing(node.position().add(node.outgoing())));
        }
        for (long position : path) positions.add(BlockPos.of(position));
        if (positions.isEmpty()) return;
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0), maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0), maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        double bottom = mapBottom();
        scale = Math.clamp(Math.min((panel - 70.0) / Math.max(32, maxX - minX), Math.max(24, bottom - 92) / Math.max(32, maxZ - minZ)), .05, 3);
        x = (minX + maxX) / 2.0; z = (minZ + maxZ) / 2.0 - ((68 + bottom) / 2 - height / 2.0) / scale;
    }
    private void update() {
        if (origins == null) return;
        boolean busy = pending && pendingAction() != Action.PREVIEW || editor != null && editor.dragging();
        for (var widget : layout.keySet()) widget.active = !busy;
        nameField.setEditable(journey == null && !busy);
        origins.setMessage(Component.literal("From: " + townName(origin))); destinations.setMessage(Component.literal("To: " + townName(destination)));
        origins.active = destinations.active = !busy && !pending && journey == null;
        var selected = routeRows().stream().filter(row -> row.getUUID("Id").equals(route)).findFirst().orElse(null);
        routes.setMessage(Component.literal(selected == null ? journey == null ? "Choose saved route" : "Route: Default" : selected.getString("Name"))); routes.active = !busy && !pending;
        if (journey == null) {
            preview.active = save.active = !busy && origin != null && destination != null && !origin.equals(destination) && !name.isBlank();
            remove.active = !busy && !pending && selected != null;
            build.active = remove.active && !rows("Sections").isEmpty();
            save.active &= path.length > 0 && !pending && saveProblem.isEmpty();
            boolean editable = !busy && editor != null;
            preview.active &= editor != null && !pending;
            waypoint.active = curves.active = editable;
            undo.active = editable && editor.draft().canUndo(); redo.active = editable && editor.draft().canRedo();
            boolean selectedNode = editable && editor.draft().selected() >= 0;
            smoothNode.active = selectedNode && !editor.hasHandles(); straightenNode.active = selectedNode && editor.hasHandles();
            linkHandles.active = selectedNode && editor.hasHandles();
            linkHandles.setMessage(Component.literal(editor != null && editor.independentHandles() ? "Independent" : "Linked"));
            insertNode.active = editable && editor.insertable(); removeNode.active = editable && editor.removable();
            showProblem.active = !busy;
            toolButtons.forEach(button -> button.active = !busy);
            curves.setMessage(Component.literal(editor != null && editor.draft().smoothMode() ? "Curves" : "Lines"));
            waypoint.setMessage(Component.literal(editor == null ? "Nodes" : "Nodes: " + editor.shape().nodes().size()));
            build.setMessage(Component.literal("Build section " + (section + 1)));
            layout.keySet().stream().filter(widget -> widget.getMessage().getString().equals("Choose road section")).forEach(widget -> widget.active = !busy && !rows("Sections").isEmpty());
        } else {
            nameField.setValue(selected == null ? "Default route" : selected.getString("Name"));
            var member = member(); boolean editable = !busy && data.getBoolean("CanConfigure");
            traveler.setMessage(Component.literal(member == null ? "No available travelers" : member.getString("Name") + (member.getBoolean("Leader") ? " (leader)" : "")));
            traveler.active = !busy && member != null;
            boolean chosen = member != null && members.containsKey(member.getUUID("Id"));
            assign.setMessage(Component.literal(chosen ? "Remove traveler" : "Add traveler")); assign.active = editable && member != null && !member.getBoolean("Leader") && (chosen || members.size() < 7);
            role.setMessage(Component.literal(chosen && members.get(member.getUUID("Id")) ? "Role: Guard" : "Role: Companion"));
            role.active = editable && chosen && member.getBoolean("Armed"); configure.active = editable && data.contains("JourneyRevision"); routes.active = editable;
            recall.active = !busy && data.getBoolean("CanRecall");
            layout.keySet().stream().filter(widget -> widget.getMessage().getString().equals("Save crew")).forEach(widget -> widget.active = configure.active);
        }
        arrange();
    }
    private void send(Action action) {
        if (pending) return;

        if (net.minecraft.Util.getMillis() - sent < 300) { queued = action; pending = true; update(); return; }
        if ((action == Action.PREVIEW || action == Action.SAVE) && route == null) route = UUID.randomUUID();
        sequence = com.jvn.villagerretaliation.client.settlement.VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new SettlementRouteRequest(sequence, action, origin, destination, route, journey,
                (action == Action.CONFIGURE || action == Action.RECALL) ? journeyRevision : revision, name, points(), members, section, journey == null && editor != null && (action == Action.PREVIEW || action == Action.SAVE) ? editor.shape() : null));
        if (action == Action.PREVIEW) previewWanted = false;
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
        if ((editor == null || !editor.dragging()) && (journey != null || tab != 0)) {
            var line = new ArrayList<Vec3>();
            for (long value : path) {
                var position = BlockPos.of(value);
                line.add(observed(position.getX(), position.getZ()) == null ? null : Vec3.atBottomCenterOf(position));
            }
            MapRouteLine.render(graphics, view, line, index -> {
                int part = Math.max(0, index - 1) / 16;
                return journey == null && tab == 1 && part == section ? 0xFFFFDF70
                        : part < sections.size() && sections.get(part).getDouble("Built") >= 1 ? 0xFFB8C7DC
                        : journey == null && tab == 0 ? 0xFF40E633 : 0xFFA3BD80;
            });
        }
        renderDraft(graphics, view, mouseX, mouseY);
        renderNodes(graphics, view, mouseX, mouseY);
        for (var town : towns()) {
            boolean from = town.getUUID("Id").equals(origin), to = town.getUUID("Id").equals(destination);
            if ((from || to) && editor != null && journey == null && tab == 0) continue;
            marker(graphics, view, from || to ? townPosition(town.getUUID("Id")) : BlockPos.of(town.getLong("Position")), from ? "A" : to ? "B" : "•",
                    town.getString("Name"), from ? 0xFFA3BD80 : to ? 0xFFFFDF70 : 0xFFB9C1CB, mouseX, mouseY);
        }
        if (data.contains("CaravanPosition")) {
            var position = BlockPos.of(data.getLong("CaravanPosition"));
            if (observed(position.getX(), position.getZ()) != null) marker(graphics, view, position, "C", "Caravan", 0xFFFFFFFF, mouseX, mouseY);
        }
        graphics.disableScissor();
        TooltipRenderUtil.renderTooltipBackground(graphics, panel + 5, 7, width - panel - 12, height - 14, 0);
        RouteButton.icon(graphics, panel + 10, 11, 0xFFFFDF70);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width - panel - 42), panel + 30, 12, 0xFFFFFF);
        String[] labels = journey == null ? new String[]{"Route", "Roads"} : new String[]{"Route", "Crew", "Delivery"};
        int tabWidth = (width - panel - 20) / labels.length;
        graphics.fill(panel + 10 + tab * (tabWidth + 2), 52, panel + 6 + tab * (tabWidth + 2) + tabWidth, 53, 0xFFB9C1CB);
        if (routeEditing()) {
            TooltipRenderUtil.renderTooltipBackground(graphics, 10, 40, toolButtons.getLast().getRight() - 10, 20, 0);
            nodeToolbar.renderControls(graphics);
        }
        super.render(graphics, mouseX, mouseY, partial);
        graphics.enableScissor(panel + 4, 58, width - 4, contentBottom());
        int textY;
        if (tab == 0) {
            text(graphics, "Route name", 141, 0xB9C1CB);
            textY = 220;
            if (journey != null) paragraph(graphics, !data.getBoolean("CanConfigure") ? "Route and crew cannot be changed here. Use Delivery to inspect the caravan."
                    : "Choose the route and Crew, then save. Transport changes to a trade require the other settlement to accept.", textY, 0xB9C1CB);
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
        if (routeEditing()) renderStatus(graphics, mouseX, mouseY);
        else {
            graphics.drawString(font, font.plainSubstrByWidth(feedback, width - panel - 20), panel + 10, height - 40, 0xFFE1A0);
            if (mouseX > panel && mouseY >= height - 42 && mouseY < height - 28 && !feedback.isEmpty()) hover = Component.literal(feedback);
        }
        int maxScroll = Math.max(0, contentHeight() - contentBottom());
        if (maxScroll > 0) {
            int track = Math.max(12, contentBottom() - 64), thumb = Math.max(12, track * track / (track + maxScroll));
            int sy = 60 + scroll * (track - thumb) / maxScroll;
            graphics.fill(width - 4, sy, width - 3, sy + thumb, 0xFFB9C1CB);
        }
        var hint = Component.literal(routeEditing() ? switch (tool) {
            case DRAW -> "Click places a node • Drag draws a curve\nMiddle-drag pans • Right-click switches to Edit";
            case EDIT -> "Drag nodes or handles • Alt adjusts one side\nEmpty ground selects nothing • Middle-drag pans";
            case PAN -> "Drag to move the map • Scroll to zoom\nChoose Draw or Edit to change the route";
        } : "Drag to pan • Scroll to zoom\nA: caravan origin • B: destination");
        int hintWidth = Math.max(40, panel - 24), hintHeight = font.split(hint, hintWidth).size() * font.lineHeight;
        TooltipRenderUtil.renderTooltipBackground(graphics, 10, height - hintHeight - 13, hintWidth, hintHeight, 0);
        graphics.drawWordWrap(font, hint, 10, height - hintHeight - 13, hintWidth, 0xB9C1CB);
        if (panel > 220 && (journey != null || tab != 0)) {
            TooltipRenderUtil.renderTooltipBackground(graphics, 10, 40, 156, 35, 0);
            graphics.drawString(font, "A  Origin     B  Destination", 10, 40, 0xFFFFFF);
            graphics.drawString(font, "Green: planned path", 10, 52, 0xA3BD80);
            graphics.drawString(font, tab == 1 && journey == null ? "Gold: selected road section" : "Gray: completed road", 10, 64, tab == 1 && journey == null ? 0xFFDF70 : 0xB8C7DC);
        }
        int cursorShape = 0;
        if (routeEditing() && editableMap(mouseX, mouseY)) cursorShape = tool == Tool.DRAW ? org.lwjgl.glfw.GLFW.GLFW_CROSSHAIR_CURSOR
                : tool == Tool.PAN || editor != null && editor.hit(this::project, mouseX, mouseY) != null ? org.lwjgl.glfw.GLFW.GLFW_HAND_CURSOR : 0;
        cursor.set(minecraft.getWindow().getWindow(), cursorShape);
        if (context != null) { context.hover(mouseX, mouseY); context.render(graphics, font, mouseX, mouseY, partial); }
        else if (hover != null) graphics.renderTooltip(font, font.split(hover, 260), mouseX, mouseY);
    }

    private String previewStatus() {
        if (pendingAction() == Action.SAVE) return "Saving route...";
        if (pendingAction() == Action.VIEW) return "Loading route...";
        if (editor == null || origin == null || destination == null || origin.equals(destination)) return "Choose route endpoints";
        if (editor.dragging()) return "Editing route...";
        if (previewWanted || pendingAction() == Action.PREVIEW) return "Checking terrain...";
        if (!saveProblem.isEmpty()) return "Save not confirmed";
        if (!previewProblem.isEmpty()) return "Blocked";
        if (path.length == 0) return "Check route before saving";
        if (name.isBlank()) return "Enter a route name";
        return dirty ? "Ready to save" : "Saved route";
    }
    private void renderStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        String state = previewStatus();
        boolean problem = state.equals("Blocked") || state.equals("Save not confirmed");
        var icon = problem ? Items.BARRIER : state.equals("Checking terrain...") || state.equals("Saving route...") || state.equals("Loading route...") ? Items.CLOCK
                : path.length > 0 ? Items.WRITABLE_BOOK : Items.COMPASS;
        int y = height - 102, textWidth = width - panel - (showProblem.visible ? 124 : 42);
        graphics.renderItem(new ItemStack(icon), panel + 10, y - 2);
        graphics.drawString(font, font.plainSubstrByWidth(state, textWidth), panel + 30, y + 2, problem ? 0xFF8888 : path.length > 0 ? 0xA3BD80 : 0xFFE1A0);
        String detail = switch (state) {
            case "Loading route..." -> "Fetching village and route details";
            case "Saving route..." -> "Waiting for confirmation";
            case "Checking terrain..." -> "Checking the current draft";
            case "Save not confirmed" -> saveProblem;
            case "Blocked" -> previewProblem;
            case "Choose route endpoints" -> "Choose the sending and receiving villages";
            case "Enter a route name" -> "Name this route before saving";
            case "Saved route" -> "Saved for future departures";
            default -> dirty ? "Unsaved changes" : "Check the current draft";
        };
        graphics.drawString(font, font.plainSubstrByWidth(detail, width - panel - 20), panel + 10, y + 17, 0xB9C1CB);
        if (mouseX >= panel + 8 && mouseY >= y - 4 && mouseY < y + 28) hover = Component.literal(state + "\n" + detail);
    }
    private void pointMenu(double mx, double my) {
        if (editor == null) return;
        var draft = editor.draft();
        context = new WorldViewMenu(font, width, height, mx, my, List.of(Component.literal("Edit route"),
                Component.literal("Alt-drag adjusts one curve handle")), List.of(
                new WorldViewMenu.Action(Component.literal(draft.inserting() ? "Insert: on" : "Insert"), editor.insertable(), () -> edit(3)),
                new WorldViewMenu.Action(Component.literal("Remove"), editor.removable(), () -> edit(4)),
                new WorldViewMenu.Action(Component.literal(tool == Tool.DRAW ? "Switch to Edit" : "Switch to Draw"), true, () -> edit(5)),
                new WorldViewMenu.Action(Component.literal("Refresh"), true, () -> { context = null; invalidate(); update(); })));
    }
    private BlockPos townPosition(UUID id) {
        return towns().stream().filter(row -> row.getUUID("Id").equals(id)).map(row -> {
            String key = id.equals(origin) && row.contains("Departure") ? "Departure" : id.equals(destination) && row.contains("Arrival") ? "Arrival" : "Position";
            return BlockPos.of(row.getLong(key));
        }).findFirst().orElse(null);
    }
    private double grid(double value) { int stride = 1 << view().level(); return Math.floor(value / stride) * stride + stride / 2.0; }
    private double[] project(Vec3 point) { return new double[]{view().screenX(grid(point.x)), view().screenY(grid(point.z))}; }
    private void segment(GuiGraphics graphics, MapView view, Vec3 from, Vec3 to, int color) {
        MapRouteLine.render(graphics, view, List.of(from, to), color);
    }
    private void renderDraft(GuiGraphics graphics, MapView view, int mouseX, int mouseY) {
        if (journey != null || tab != 0 || editor == null) return;
        List<Vec3> samples;
        int color;
        if (path.length > 0 && !editor.dragging()) {
            samples = new ArrayList<>();
            for (long value : path) {
                var position = BlockPos.of(value);
                samples.add(observed(position.getX(), position.getZ()) == null ? null : Vec3.atBottomCenterOf(position));
            }
            color = 0xFF40E633;
        } else {
            try { samples = editor.samples(editor.shape().path()); }
            catch (IllegalArgumentException limit) { hover = Component.literal(limit.getMessage()); return; }
            color = 0xFF4DCCFF;
        }
        MapRouteLine.render(graphics, view, samples, color);
        if (data.contains("Blocked")) marker(graphics, view, BlockPos.of(data.getLong("Blocked")), "!", "Blocked terrain", 0xFFFF5959, mouseX, mouseY);
    }
    private void renderNodes(GuiGraphics graphics, MapView view, int mouseX, int mouseY) {
        if (editor == null || journey != null || tab != 0) return;
        var nodes = editor.shape().nodes(); var hit = tool == Tool.PAN ? null : editor.hit(this::project, mouseX, mouseY);
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i); var projected = project(node.position()); int px = (int) projected[0], py = (int) projected[1];
            boolean selected = i == editor.draft().selected();
            int color = selected || hit != null && hit.node() == i && hit.part() == 0 ? 0xFFFFCC4D : 0xFFFFFFFF;
            graphics.fill(px - 3, py - 3, px + 4, py + 4, 0xFF17121E); graphics.renderOutline(px - 3, py - 3, 7, 7, color);
            graphics.drawString(font, i == 0 ? "A" : i == nodes.size() - 1 ? "B" : Integer.toString(i), px + 5, py - 9, color);
            if (!selected || tool == Tool.PAN) continue;
            for (int part : new int[]{-1, 1}) {
                var offset = part < 0 ? node.incoming() : node.outgoing();
                if (offset.lengthSqr() < .01) continue;
                var handle = node.position().add(offset); segment(graphics, view, node.position(), handle, 0xFF66BFFF);
                var handlePoint = project(handle); int hx = (int) handlePoint[0], hy = (int) handlePoint[1];
                int handleColor = hit != null && hit.node() == i && hit.part() == part ? 0xFFFFBF4D : 0xFF66BFFF;
                graphics.fill(hx - 2, hy - 2, hx + 3, hy + 3, 0xFF17121E); graphics.renderOutline(hx - 2, hy - 2, 5, 5, handleColor);
            }
        }
        if (hit != null) hover = Component.literal(hit.part() == 0 ? "Drag node • Curves exposes handles" : "Drag curve handle • Alt adjusts one side");
    }

    private void text(GuiGraphics graphics, String value, int y, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, width - panel - 20), panel + 10, y - scroll, color);
    }

    private void paragraph(GuiGraphics graphics, String value, int y, int color) {
        graphics.drawWordWrap(font, Component.literal(value), panel + 10, y - scroll, width - panel - 20, color);
    }

    private void marker(GuiGraphics graphics, MapView view, BlockPos pos, String symbol, String label, int color, int mouseX, int mouseY) {
        int px = (int) view.screenX(pos.getX() + .5), py = (int) view.screenY(pos.getZ() + .5);
        if (px < 10 || px > panel - 10 || py < 36 || py > height - 60) return;
        graphics.fill(px - 7, py - 7, px + 8, py + 8, 0xF0100010);
        graphics.renderOutline(px - 7, py - 7, 15, 15, color);
        graphics.drawCenteredString(font, symbol, px, py - 4, color);
        if (symbol.equals("A") || symbol.equals("B") || symbol.equals("C")) {
            String name = font.plainSubstrByWidth(label.split("\\n", 2)[0], 120);
            int labelWidth = font.width(name);
            TooltipRenderUtil.renderTooltipBackground(graphics, px - labelWidth / 2, py + 12, labelWidth, font.lineHeight, 0);
            graphics.drawCenteredString(font, name, px, py + 12, 0xFFFFFF);
        }
        if (Math.abs(mouseX - px) <= 10 && Math.abs(mouseY - py) <= 10)
            hover = Component.literal(label + "\n" + pos.getX() + ", " + pos.getZ());
    }
    private static String status(String value) { return value.toLowerCase(java.util.Locale.ROOT).replace('_', ' '); }
    private boolean editableMap(double mx, double my) {
        return mx >= 0 && mx < panel && my > 65 && my < height - 60 && journey == null && tab == 0 && (!pending || pendingAction() == Action.PREVIEW) && !overNodeToolbar(mx, my);
    }

    private BlockPos cursorPosition(double mx, double my) {
        return observed((int) Math.floor(view().worldX(mx)), (int) Math.floor(view().worldZ(my)));
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        panning = false;
        if (context != null) { if (!context.click(mx, my, button)) context = null; return true; }
        if (editor != null && editor.dragging()) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        if (overNodeToolbar(mx, my)) return true;
        if (editableMap(mx, my) && editor != null) {
            if (tool == Tool.PAN && button == 0) { setFocused(null); panning = true; return true; }
            var hit = editor.hit(this::project, mx, my);
            if (button == 1) {
                if (hit != null && tool != Tool.PAN) { editor.select(hit.node()); pointMenu(mx, my); }
                else if (tool == Tool.DRAW) setTool(Tool.EDIT);
                return true;
            }
            if (button == 0) {
                setFocused(null);
                if (hit != null) editor.begin(hit, mx, my);
                else if (tool == Tool.DRAW) {
                    var point = cursorPosition(mx, my);
                    if (point != null) editor.place(Vec3.atBottomCenterOf(point), mx, my);
                    else message = "Choose explored ground";
                } else editor.select(-1);
                update(); return true;
            }
        }
        panning = mx >= 0 && mx < panel && (button == 2 || button == 0 && (journey != null || tab != 0));
        return mx < panel;
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (editor != null && editor.dragging() && button == 0) {
            Vec3 point = null;
            if (editableMap(mx, my)) {
                if (editor.dragPart() == 0) { var ground = cursorPosition(mx, my); if (ground != null) point = Vec3.atBottomCenterOf(ground); }
                else point = new Vec3(Math.floor(view().worldX(mx)) + .5, editor.shape().nodes().get(editor.draft().selected()).position().y, Math.floor(view().worldZ(my)) + .5);
            }
            editor.drag(point, mx, my, hasAltDown()); return true;
        }
        if (panning) { x -= dx / scale; z -= dy / scale; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        panning = false;
        if (editor != null && editor.dragging() && button == 0) {
            boolean valid = editableMap(mx, my) && (editor.dragPart() != 0 || cursorPosition(mx, my) != null);
            if (editor.finish(valid)) invalidate(); update(); return true;
        }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (context != null) { if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) context = null; else context.key(key); return true; }
        if (journey == null && tab == 0 && editor != null && !nameField.isFocused() && (!pending || pendingAction() == Action.PREVIEW)) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                if (editor.dragging()) editor.cancel(); else setTool(Tool.EDIT); update(); return true;
            }
            if (!editor.dragging()) {
                boolean control = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
                if (control && key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) { edit((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0 ? 2 : 1); return true; }
                if (control && key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) { edit(2); return true; }
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) { edit(4); return true; }
                if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && save.active) { send(Action.SAVE); return true; }
            }
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scroll += key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 72 : -72; arrange(); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (context != null) { context.scroll(mx, my, vertical); return true; }
        if (editor != null && editor.dragging()) return true;
        if (overNodeToolbar(mx, my)) return true;
        if (mx < panel) { scale = Math.clamp(scale * Math.pow(1.2, vertical), .05, 8); return true; }
        scroll -= (int) (vertical * 24); arrange(); return true;
    }
    @Override public void tick() {
        if (previewWanted && editor != null && !editor.dragging() && !pending && !name.isBlank() && net.minecraft.Util.getMillis() >= previewAt) send(Action.PREVIEW);
        if (queued != null && net.minecraft.Util.getMillis() - sent >= 300) {
            var action = queued; queued = null; pending = false; send(action);
        }
        if (journey != null && !pending && !dirty && initialized && net.minecraft.Util.getMillis() - sent > 5000) send(Action.VIEW);
        if (pending && net.minecraft.Util.getMillis() - sent > 10000) {
            pending = false; previewWanted = false; message = "No reply. Check the route or reload before retrying";
            if (sentAction == Action.SAVE) saveProblem = "Save outcome unconfirmed. Reload before retrying";
            else if (sentAction == Action.PREVIEW) previewProblem = message;
            update();
        }
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
    @Override public void removed() { cursor.close(); tiles.close(); }
}
