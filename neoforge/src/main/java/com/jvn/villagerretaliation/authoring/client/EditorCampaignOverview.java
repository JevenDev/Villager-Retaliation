package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class EditorCampaignOverview {
    private final EditorCampaignGraph model = new EditorCampaignGraph();
    private final ImString search = new ImString(256);
    private AuthoringWorkspace owner;
    private EditorCampaignGraph.Snapshot graph;
    private EditorCampaignGraph.Node selected;
    private List<EditorCampaignGraph.Node> visible = List.of();
    private Map<EditorCampaignGraph.Node, EditorCampaignGraph.Position> positions = Map.of();
    private String scope = "";
    private boolean supporting, orphaned, untested, nearby, fit = true, focus, panning;
    private float panX = 25, panY = 25, zoom = 1, canvasX, canvasY, textScale = 1;

    void render(AuthoringWorkspace workspace, EditorContentBrowser browser, String locale, float scale, EditorCoverage coverage,
                AuthoringDocument current, Consumer<EditorReferences.Location> open) {
        if (textScale != scale) fit = true;
        textScale = scale;
        ImGui.textUnformatted("Campaign overview");
        if (workspace == null) { ImGui.textWrapped("Open a workspace to explore its content and references."); return; }
        if (owner != workspace) {
            owner = workspace; selected = null; scope = ""; search.set(""); nearby = false; fit = true; visible = List.of();
        }
        var latest = model.get(workspace, browser, locale);
        if (latest != graph) {
            var previous = selected == null ? current : selected.document();
            graph = latest;
            selected = graph.nodes().stream().filter(node -> node.document() == previous).findFirst().orElse(null);
        }
        boolean wide = ImGui.getContentRegionAvailX() >= 440 * scale;
        ImGui.setNextItemWidth(wide ? ImGui.getContentRegionAvailX() - 260 * scale : -1);
        ImGui.inputTextWithHint("##campaign_search", "Find a title, ID or file...", search);
        boolean find = (ImGui.isItemActive() || ImGui.isItemDeactivated()) && ImGui.isKeyPressed(imgui.flag.ImGuiKey.Enter);
        if (wide) ImGui.sameLine();
        ImGui.setNextItemWidth(-1);
        var groups = graph.nodes().stream().map(node -> node.content().groups().getFirst()).distinct().toList();
        if (!scope.isEmpty() && groups.stream().noneMatch(group -> group.key().equals(scope))) scope = "";
        String label = groups.stream().filter(group -> group.key().equals(scope)).map(EditorContentBrowser.Group::label).findFirst().orElse("All content");
        if (ImGui.beginCombo("##campaign_scope", label)) {
            if (ImGui.selectable("All content", scope.isEmpty())) scope = "";
            for (var group : groups) if (ImGui.selectable(group.label(), group.key().equals(scope))) scope = group.key();
            ImGui.endCombo();
        }
        if (ImGui.checkbox("Supporting files", supporting)) supporting = !supporting;
        EditorWidgets.sameLineFor("No incoming references");
        if (ImGui.checkbox("No incoming references", orphaned)) orphaned = !orphaned;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Files with no references from another file in this workspace. Automatically loaded content can still be valid.");
        EditorWidgets.sameLineFor("No recorded coverage");
        if (ImGui.checkbox("No recorded coverage", untested)) untested = !untested;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Dialogue and quests with no visited nodes or choices in this editor session. Logic edits reset coverage.");
        ImGui.beginDisabled(selected == null);
        if (ImGui.checkbox("Connections of selected", nearby)) nearby = !nearby;
        ImGui.endDisabled();
        EditorWidgets.sameLineFor("Fit");
        if (ImGui.button("Fit")) fit = true;
        EditorWidgets.sameLineFor("Focus selected");
        ImGui.beginDisabled(selected == null);
        if (ImGui.button("Focus selected")) {
            search.set(""); scope = ""; supporting |= selected.supporting(); orphaned = false; untested = false; focus = true;
        }
        ImGui.endDisabled();
        var shown = EditorCampaignGraph.filter(graph, new EditorCampaignGraph.Filter(search.get(), scope, supporting, orphaned, untested, nearby ? selected : null), coverage);
        if (!shown.equals(visible)) { positions = EditorCampaignGraph.arrange(graph, shown); fit = true; }
        visible = shown;
        if (find && !visible.isEmpty()) { selected = visible.getFirst(); focus = true; }
        ImGui.textDisabled(visible.size() + " of " + graph.nodes().size() + " files. Arrows point to referenced content.");
        ImGui.textWrapped("Click to inspect, double-click to open. Drag the background to pan, scroll to zoom.");
        if (visible.isEmpty()) { ImGui.textWrapped("No files match these filters."); return; }
        canvas(coverage, open);
    }

    private void canvas(EditorCoverage coverage, Consumer<EditorReferences.Location> open) {
        float width = Math.max(100, ImGui.getContentRegionAvailX()), height = Math.max(100, ImGui.getContentRegionAvailY());
        ImGui.beginChild("##campaign_map", width, height, true, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        canvasX = ImGui.getCursorScreenPosX(); canvasY = ImGui.getCursorScreenPosY();
        width = Math.max(100, ImGui.getContentRegionAvailX()); height = Math.max(100, ImGui.getContentRegionAvailY());
        ImGui.invisibleButton("##campaign_canvas", width, height);
        boolean hovered = ImGui.isItemHovered();
        if (fit) {
            float right = 260, bottom = 80;
            for (var point : positions.values()) { right = Math.max(right, point.x() + 260); bottom = Math.max(bottom, point.y() + 80); }
            zoom = Math.clamp(Math.min((width - 40) / (right * textScale), (height - 40) / (bottom * textScale)), .15f, 1f);
            panX = 20; panY = 20; fit = false;
        }
        if (focus && positions.containsKey(selected)) {
            var point = positions.get(selected); zoom = 1;
            panX = width / 2 - (point.x() + 130) * textScale; panY = height / 2 - (point.y() + 40) * textScale;
        }
        focus = false;
        EditorCampaignGraph.Node hit = null;
        float mouseX = (ImGui.getMousePosX() - canvasX - panX) / (zoom * textScale), mouseY = (ImGui.getMousePosY() - canvasY - panY) / (zoom * textScale);
        if (hovered) for (var node : visible) {
            var point = positions.get(node);
            if (mouseX >= point.x() && mouseX <= point.x() + 260 && mouseY >= point.y() && mouseY <= point.y() + 80) hit = node;
        }
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) panning = false;
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left) && hit == null) panning = true;
        if (hovered && (ImGui.isMouseDragging(ImGuiMouseButton.Middle) || panning && ImGui.isMouseDragging(ImGuiMouseButton.Left))) {
            panX += ImGui.getIO().getMouseDeltaX(); panY += ImGui.getIO().getMouseDeltaY();
        }
        if (hovered && ImGui.getIO().getMouseWheel() != 0) {
            float next = Math.clamp(zoom * (float) Math.pow(1.12, ImGui.getIO().getMouseWheel()), .15f, 2f);
            panX += mouseX * (zoom - next) * textScale; panY += mouseY * (zoom - next) * textScale; zoom = next;
        }
        if (hit != null) {
            ImGui.setTooltip(hit.title() + "\n" + hit.content().id() + "\n" + hit.path());
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) selected = hit;
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) open.accept(new EditorReferences.Location(hit.document(), ""));
        }
        float unit = zoom * textScale;
        var draw = ImGui.getWindowDrawList();
        draw.pushClipRect(canvasX, canvasY, canvasX + width, canvasY + height, true);
        var drawn = new java.util.HashSet<List<EditorCampaignGraph.Node>>();
        for (var link : graph.links()) {
            var a = positions.get(link.from()); var b = positions.get(link.to());
            if (a == null || b == null || !drawn.add(List.of(link.from(), link.to()))) continue;
            float x1 = canvasX + panX + (a.x() + 260) * unit, y1 = canvasY + panY + (a.y() + 40) * unit;
            float x2 = canvasX + panX + b.x() * unit, y2 = canvasY + panY + (b.y() + 40) * unit;
            float bend = Math.max(30, Math.abs(x2 - x1) * .5f);
            if (Math.max(x1 + bend, x2) < canvasX || Math.min(x1, x2 - bend) > canvasX + width
                    || Math.max(y1, y2) < canvasY || Math.min(y1, y2) > canvasY + height) continue;
            int color = selected == link.from() || selected == link.to() ? 0xffd5c392 : 0xff766b58;
            draw.addBezierCubic(x1, y1, x1 + bend, y1, x2 - bend, y2, x2, y2, color, 2);
            draw.addTriangleFilled(x2, y2, x2 - 8, y2 - 4, x2 - 8, y2 + 4, color);
        }
        for (var node : visible) {
            var point = positions.get(node);
            float x = canvasX + panX + point.x() * unit, y = canvasY + panY + point.y() * unit;
            float right = x + 260 * unit, bottom = y + 80 * unit;
            if (right < canvasX || x > canvasX + width || bottom < canvasY || y > canvasY + height) continue;
            draw.addRectFilled(x, y, right, bottom, selected == node ? 0xff65513d : 0xff342e25, 4);
            draw.addRect(x, y, right, bottom, selected == node ? 0xffd5c392 : 0xff766b58, 4, 0, selected == node ? 2 : 1);
            draw.pushClipRect(x + 7 * unit, y, right - 7 * unit, bottom, true);
            int size = Math.max(6, (int) (ImGui.getFontSize() * zoom));
            draw.addText(ImGui.getFont(), size, x + 9 * unit, y + 7 * unit, 0xffeeeeee, node.title());
            draw.addText(ImGui.getFont(), size, x + 9 * unit, y + 30 * unit, 0xffbcb4a5, node.category());
            String state = node.coverable() ? EditorCampaignGraph.recorded(node, coverage) ? "Coverage recorded" : "No recorded coverage"
                    : graph.incoming().get(node).size() + " incoming, " + graph.outgoing().get(node).size() + " outgoing";
            draw.addText(ImGui.getFont(), size, x + 9 * unit, y + 53 * unit,
                    node.coverable() && EditorCampaignGraph.recorded(node, coverage) ? 0xff94c697 : 0xffbcb4a5, state);
            draw.popClipRect();
        }
        draw.popClipRect();
        ImGui.endChild();
    }

    void details(EditorCoverage coverage, Consumer<EditorReferences.Location> open) {
        ImGui.textUnformatted("Content references");
        if (selected == null || graph == null) { ImGui.textWrapped("Select a file in the campaign overview."); return; }
        ImGui.textWrapped(selected.title());
        ImGui.textWrapped(selected.content().id());
        ImGui.textWrapped(selected.path());
        if (ImGui.button("Open file")) open.accept(new EditorReferences.Location(selected.document(), ""));
        if (selected.coverable()) {
            ImGui.textWrapped(coverage.summary(selected.document()));
            ImGui.textWrapped("Coverage is recorded for this editor session.");
        }
        if (!visible.contains(selected)) ImGui.textWrapped("This file is hidden by the current filters.");
        links("Referenced by", graph.incoming().get(selected), true, open);
        links("References", graph.outgoing().get(selected), false, open);
    }

    private void links(String label, List<EditorCampaignGraph.Link> links, boolean incoming, Consumer<EditorReferences.Location> open) {
        ImGui.separatorText(label + " (" + links.size() + ")");
        if (links.isEmpty()) ImGui.textDisabled("None in this workspace.");
        ImGui.pushID(label);
        for (int i = 0; i < links.size(); i++) {
            var link = links.get(i);
            var target = incoming ? link.from() : link.to();
            ImGui.pushID(i);
            if (ImGui.selectable(target.title())) { selected = target; focus = true; }
            if (ImGui.isItemHovered()) ImGui.setTooltip(target.path());
            ImGui.textWrapped(link.sourcePointer());
            if (ImGui.smallButton("Go to reference")) open.accept(new EditorReferences.Location(link.from().document(), link.sourcePointer()));
            if (!incoming) {
                EditorWidgets.sameLineFor("Go to definition");
                if (ImGui.smallButton("Go to definition")) open.accept(new EditorReferences.Location(link.to().document(), link.targetPointer()));
            }
            ImGui.popID();
        }
        ImGui.popID();
    }
}
