package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

final class EditorSceneView {
    private record Entry(String pointer, String title, String summary) { }
    private final EditorResourcePreview preview = new EditorResourcePreview();
    private final ImString name = new ImString(128), outcome = new ImString("success", 64);
    private AuthoringDocument owner;
    private long revision = -1;
    private String section = "", selected = "", lastPointer = "", renamePath = "", replacement = "", spawnDimension = "";
    private int wave, party = 1;
    private boolean advanced, rehearsal;
    private EditorSceneDraft.Trace trace;
    private EncounterTemplate encounter;
    private List<String> encounterErrors = List.of();

    void beginFrame(boolean active) { preview.beginFrame(active); }
    void close() { preview.retire(); }
    void settings() { section = "Settings"; selected = ""; }
    boolean guided(AuthoringDocument document, JsonObject root) { return EditorSceneDraft.supports(root) && owner == document && !advanced; }

    void render(AuthoringWorkspace workspace, AuthoringDocument document, JsonObject root, String pointer, String locale, float scale,
                EditorResourceView.Fields fields, Runnable rawView, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        boolean scene = EditorSceneDraft.scene(root);
        if (owner != document) {
            owner = document; revision = -1; selected = ""; lastPointer = ""; renamePath = ""; spawnDimension = ""; wave = 0;
            section = scene ? "Sequence" : root.has("variants") ? "Variants" : "Roster"; advanced = false; rehearsal = false; trace = null;
        }
        if (revision != document.revision()) {
            revision = document.revision(); trace = null;
            if (!scene) {
                var errors = new ArrayList<String>();
                encounter = EncounterResources.parse(ResourceLocation.tryParse(document.id()), root, errors);
                encounterErrors = List.copyOf(errors);
            }
        }
        ImGui.textUnformatted(scene ? "Scene" : "Encounter");
        if (ImGui.checkbox("Advanced resource editor", advanced)) advanced = !advanced;
        if (advanced) { rawView.run(); return; }
        var sections = scene ? List.of("Sequence", "Actors", "Settings")
                : root.has("variants") ? List.of("Variants", "Settings") : List.of("Roster", "Waves", "Spawn plan", "Settings");
        for (String option : sections) {
            if (!option.equals(sections.getFirst())) EditorWidgets.sameLineFor(option);
            if (section.equals(option)) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.getColorU32(imgui.flag.ImGuiCol.Header));
            boolean clicked = ImGui.button(option);
            if (section.equals(option)) ImGui.popStyleColor();
            if (clicked) run.accept(() -> { section = option; selected = ""; renamePath = ""; });
        }
        if (!pointer.equals(lastPointer) && !pointer.isEmpty()) {
            if (pointer.startsWith("/steps/")) section = "Sequence";
            else if (pointer.startsWith("/actors/")) section = "Actors";
            else if (pointer.startsWith("/spawn_points/")) section = "Spawn plan";
            selected = pointer;
        }
        lastPointer = pointer;
        if (section.equals("Settings")) { fields.render(root, "", true); return; }
        if (section.equals("Spawn plan")) { spawnPlan(document, root, scale, fields, select, run); return; }
        if (section.equals("Sequence")) {
            sequenceTools(workspace, document, root, select, run);
            if (ImGui.checkbox("Sequence rehearsal", rehearsal)) { rehearsal = !rehearsal; trace = null; }
            if (rehearsal) rehearsal(workspace, document, root, locale, select);
        } else if (section.equals("Actors")) {
            if (ImGui.button("Add actor")) run.accept(() -> {
                String alias = EditorSceneDraft.addActor(document);
                choose(find(document.json(), "/actors", "alias", alias), select);
            });
        } else if (section.equals("Roster")) rosterTools(document, root, select, run);
        else if (section.equals("Waves")) {
            if (root.has("members")) {
                ImGui.textWrapped("This encounter uses one roster. Convert it to an authored wave to add separate rosters and delays.");
                if (ImGui.button("Make first wave")) run.accept(() -> { EditorSceneDraft.makeWaves(document); choose("/waves/0", select); });
            } else if (root.has("waves") && ImGui.button("Add wave")) run.accept(() -> {
                String id = EditorSceneDraft.addWave(document); choose(find(document.json(), "/waves", "id", id), select);
            });
        }
        var entries = entries(root);
        if (!selected.isEmpty()) selected = entries.stream().filter(entry -> selected.equals(entry.pointer()) || selected.startsWith(entry.pointer() + "/"))
                .map(Entry::pointer).findFirst().orElse(selected);
        if (entries.stream().noneMatch(entry -> entry.pointer().equals(selected))) selected = entries.isEmpty() ? "" : entries.getFirst().pointer();
        float width = ImGui.getContentRegionAvailX();
        boolean stacked = width < 660 * scale;
        ImGui.beginChild("##scene_outline", stacked ? 0 : Math.min(330 * scale, width * .4f), stacked ? 170 * scale : 0, true);
        for (var entry : entries) {
            ImGui.pushID(entry.pointer());
            boolean current = trace != null && section.equals("Sequence") && trace.current().equals(entry.title());
            if (ImGui.selectable((current ? "> " : "") + entry.title(), selected.equals(entry.pointer()))) run.accept(() -> choose(entry.pointer(), select));
            ImGui.textWrapped(entry.summary());
            if (section.equals("Sequence") && DialogueGraph.string(root, "entry_step").equals(entry.title())) ImGui.textDisabled("Entry step");
            ImGui.separator();
            ImGui.popID();
        }
        if (entries.isEmpty()) ImGui.textWrapped("No entries in this section.");
        ImGui.endChild();
        if (!stacked) ImGui.sameLine();
        ImGui.beginChild("##scene_fields", 0, 0, true);
        var entry = entries.stream().filter(value -> value.pointer().equals(selected)).findFirst().orElse(null);
        if (entry != null) {
            var value = JsonPointers.get(root, entry.pointer());
            ImGui.textWrapped(entry.title());
            int index = Integer.parseInt(entry.pointer().substring(entry.pointer().lastIndexOf('/') + 1));
            int count = JsonPointers.get(root, entry.pointer().substring(0, entry.pointer().lastIndexOf('/'))).getAsJsonArray().size();
            ImGui.beginDisabled(index == 0);
            if (ImGui.smallButton("Move up")) run.accept(() -> choose(EditorSceneDraft.move(document, entry.pointer(), -1), select));
            ImGui.endDisabled(); EditorWidgets.sameLineFor("Move down"); ImGui.beginDisabled(index + 1 >= count);
            if (ImGui.smallButton("Move down")) run.accept(() -> choose(EditorSceneDraft.move(document, entry.pointer(), 1), select));
            ImGui.endDisabled();
            if (section.equals("Sequence")) stepTools(document, root, entry, select, run);
            else if (section.equals("Actors")) actorTools(document, entry, value.getAsJsonObject(), select, run);
            else if (section.equals("Roster") && value.isJsonObject()) {
                String entity = DialogueGraph.string(value.getAsJsonObject(), "entity");
                if (!entity.isBlank()) preview.entity(entity, Math.min(120 * scale, ImGui.getContentRegionAvailX()), 135 * scale);
            }
            fields.render(value, entry.pointer(), false);
            if (section.equals("Roster") || section.equals("Waves")) {
                ImGui.separator();
                ImGui.beginDisabled(entries.size() <= 1);
                if (ImGui.button("Remove " + (section.equals("Roster") ? "member" : "wave"))) run.accept(() ->
                        document.edit("Remove encounter entry", json -> JsonPointers.remove(json, entry.pointer())));
                ImGui.endDisabled();
            }
        }
        ImGui.endChild();
    }

    private void sequenceTools(AuthoringWorkspace workspace, AuthoringDocument document, JsonObject root, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        ImGui.setNextItemWidth(280);
        if (ImGui.beginCombo("##new_scene_step", "Add step...")) {
            for (var descriptor : VillagerRetaliationRegistries.SCENE_STEPS.descriptors())
                if (ImGui.selectable(EditorContentBrowser.humanize(descriptor.id().getPath()))) run.accept(() -> {
                    String id = EditorSceneDraft.addStep(workspace, document, descriptor.id().toString());
                    choose(find(document.json(), "/steps", "id", id), select);
                });
            ImGui.endCombo();
        }
    }

    private void stepTools(AuthoringDocument document, JsonObject root, Entry entry, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        var step = EditorSceneDraft.steps(root).stream().filter(value -> value.pointer().equals(entry.pointer())).findFirst().orElseThrow();
        rename(entry, "Step ID", () -> EditorSceneDraft.renameStep(document, step.id(), name.get()), run);
        if (ImGui.button("Set entry")) run.accept(() -> document.edit("Set scene entry", json -> json.addProperty("entry_step", step.id())));
        EditorWidgets.sameLineFor("Duplicate");
        if (ImGui.button("Duplicate")) run.accept(() -> {
            String id = EditorSceneDraft.duplicateStep(document, step.id()); choose(find(document.json(), "/steps", "id", id), select);
        });
        ImGui.separatorText("Outcome links");
        for (var route : step.routes().entrySet()) ImGui.textWrapped(route.getKey() + " -> " + route.getValue());
        ImGui.setNextItemWidth(-1); ImGui.inputText("##scene_outcome", outcome);
        if (ImGui.beginCombo("Connect outcome", step.routes().getOrDefault(outcome.get(), "Choose a step..."))) {
            if (ImGui.selectable("No route")) run.accept(() -> EditorSceneDraft.route(document, step.id(), outcome.get(), ""));
            for (var target : EditorSceneDraft.steps(root)) if (ImGui.selectable(target.id())) run.accept(() -> EditorSceneDraft.route(document, step.id(), outcome.get(), target.id()));
            ImGui.endCombo();
        }
        EditorWidgets.sameLineFor("Failure policy");
        if (ImGui.smallButton("Failure policy")) outcome.set(EditorSceneDraft.FAILURE);
        if (outcome.get().equals(EditorSceneDraft.FAILURE)) ImGui.textWrapped("Failure links run when the scene failure policy is run_failure_step.");
        if (ImGui.treeNode("Remove step")) {
            if (ImGui.beginCombo("Redirect links to", replacement.isEmpty() ? "Choose replacement..." : replacement)) {
                for (var target : EditorSceneDraft.steps(root)) if (!target.id().equals(step.id()) && ImGui.selectable(target.id())) replacement = target.id();
                ImGui.endCombo();
            }
            ImGui.beginDisabled(EditorSceneDraft.steps(root).size() <= 1);
            if (ImGui.button("Remove selected step")) run.accept(() -> { EditorSceneDraft.removeStep(document, step.id(), replacement); selected = ""; });
            ImGui.endDisabled(); ImGui.treePop();
        }
        if (List.of("villagerretaliation:move_actor", "villagerretaliation:face_position", "villagerretaliation:start_encounter").contains(step.type()))
            positionButtons((dimension, x, y, z) -> run.accept(() -> document.edit("Set scene step destination", json -> {
                var owner = JsonPointers.get(json, step.pointer()).getAsJsonObject();
                var data = EditorSchema.object(owner.get("data")); data.remove("target_actor"); data.remove("anchor_actor");
                data.addProperty("dimension", dimension); data.addProperty("x", x); data.addProperty("y", y); data.addProperty("z", z); owner.add("data", data);
            })));
        ImGui.separatorText("Step properties");
    }

    private void actorTools(AuthoringDocument document, Entry entry, JsonObject value, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        String alias = DialogueGraph.string(value, "alias");
        rename(entry, "Actor alias", () -> EditorSceneDraft.renameActor(document, alias, name.get()), run);
        var minecraft = Minecraft.getInstance();
        var target = minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity ? hit.getEntity() : null;
        ImGui.beginDisabled(target == null);
        if (ImGui.button("Bind targeted entity")) run.accept(() -> {
            String type = target instanceof net.minecraft.world.entity.player.Player ? "player" : target instanceof net.minecraft.world.entity.npc.Villager ? "villager" : "living_entity";
            EditorSceneDraft.bindEntity(document, entry.pointer(), "villagerretaliation:" + type, target.getUUID().toString());
        });
        ImGui.endDisabled();
        positionButtons((dimension, x, y, z) -> run.accept(() -> EditorSceneDraft.bindPosition(document, entry.pointer(), dimension, x, y, z)));
        ImGui.textWrapped("Entity bindings use a UUID in this world. Position bindings store a dimension and block coordinates.");
        if (ImGui.button("Remove actor")) run.accept(() -> { EditorSceneDraft.removeActor(document, entry.pointer()); selected = ""; });
        ImGui.separatorText("Actor properties");
    }

    private void rename(Entry entry, String label, ImGuiAuthoringEditor.EditorOperation rename, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (!renamePath.equals(entry.pointer())) { renamePath = entry.pointer(); name.set(entry.title()); }
        ImGui.textUnformatted(label);
        ImGui.setNextItemWidth(-1); ImGui.inputText("##scene_rename", name);
        if (ImGui.button("Rename and update scene links")) run.accept(rename);
    }

    private void rosterTools(AuthoringDocument document, JsonObject root, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        var waves = DialogueGraph.array(root, "waves");
        if (!waves.isEmpty()) {
            wave = Math.clamp(wave, 0, waves.size() - 1);
            if (ImGui.beginCombo("Wave roster", DialogueGraph.string(waves.get(wave).getAsJsonObject(), "id"))) {
                for (int i = 0; i < waves.size(); i++) {
                    int index = i;
                    if (ImGui.selectable(DialogueGraph.string(waves.get(i).getAsJsonObject(), "id"))) run.accept(() -> { wave = index; selected = ""; });
                }
                ImGui.endCombo();
            }
        }
        String path = waves.isEmpty() ? "/members" : "/waves/" + wave + "/members";
        if ((root.has("members") || !waves.isEmpty()) && ImGui.button("Add member")) run.accept(() -> {
            String id = EditorSceneDraft.addMember(document, path); choose(find(document.json(), path, "id", id), select);
        });
        int[] size = {party}; ImGui.setNextItemWidth(180);
        if (ImGui.sliderInt("Preview party size", size, 1, 16)) party = size[0];
        if (encounter != null) {
            ImGui.textWrapped(encounter.totalCount(party) + " total entities across " + encounter.waveCount() + " wave(s).");
            ImGui.textWrapped("Selected wave: " + encounter.scaledCount(encounter.wave(Math.min(wave, encounter.waveCount() - 1)), party) + " entities.");
        }
        for (String error : encounterErrors) ImGui.textWrapped(error);
    }

    private List<Entry> entries(JsonObject root) {
        if (section.equals("Sequence")) return EditorSceneDraft.steps(root).stream().map(step -> new Entry(step.pointer(), step.id(),
                EditorContentBrowser.humanize(step.type().substring(step.type().indexOf(':') + 1)) + "\n"
                        + step.routes().entrySet().stream().map(route -> route.getKey() + " -> " + route.getValue()).collect(java.util.stream.Collectors.joining(", ")))).toList();
        String path = switch (section) {
            case "Actors" -> "/actors";
            case "Waves" -> "/waves";
            case "Variants" -> "/variants";
            default -> !DialogueGraph.array(root, "waves").isEmpty() ? "/waves/" + Math.clamp(wave, 0, DialogueGraph.array(root, "waves").size() - 1) + "/members" : "/members";
        };
        JsonElement values = path.startsWith("/waves/") ? DialogueGraph.array(EditorSchema.object(DialogueGraph.array(root, "waves").get(Math.clamp(wave, 0, DialogueGraph.array(root, "waves").size() - 1))), "members") : DialogueGraph.array(root, path.substring(1));
        if (!values.isJsonArray()) return List.of();
        var result = new ArrayList<Entry>();
        for (int i = 0; i < values.getAsJsonArray().size(); i++) if (values.getAsJsonArray().get(i).isJsonObject()) {
            var value = values.getAsJsonArray().get(i).getAsJsonObject();
            String id = DialogueGraph.string(value, section.equals("Actors") ? "alias" : "id");
            String summary = section.equals("Actors") ? DialogueGraph.string(value, "binding_source") + ": " + DialogueGraph.string(value, "binding")
                    : value.has("entity") ? DialogueGraph.string(value, "entity") + " x " + (value.has("count") ? value.get("count").getAsString() : "1")
                    : DialogueGraph.string(value, "template");
            result.add(new Entry(path + "/" + i, id.isEmpty() ? "Entry " + (i + 1) : id, summary));
        }
        return List.copyOf(result);
    }

    private void rehearsal(AuthoringWorkspace workspace, AuthoringDocument document, JsonObject root, String locale, Consumer<String> select) {
        if (trace == null) trace = EditorSceneDraft.start(root);
        if (ImGui.button("Restart rehearsal")) trace = EditorSceneDraft.start(root);
        ImGui.textWrapped(trace.status() + (trace.current().isEmpty() ? "" : ": " + trace.current()));
        var step = EditorSceneDraft.steps(root).stream().filter(value -> value.id().equals(trace.current())).findFirst().orElse(null);
        if (step != null) {
            var data = EditorSchema.object(step.value().get("data"));
            if (data.has("text")) ImGui.textWrapped(workspace.resolveText(document, data.get("text"), locale));
            if (data.has("ticks")) ImGui.textWrapped("Wait: " + data.get("ticks").getAsString() + " ticks");
            var routes = new java.util.LinkedHashSet<>(step.routes().keySet());
            if (routes.isEmpty()) routes.add("success");
            for (String route : routes) {
                if (!route.equals(routes.iterator().next())) EditorWidgets.sameLineFor("Follow " + route);
                if (ImGui.button("Follow " + route)) {
                    trace = EditorSceneDraft.advance(root, trace, route);
                    var next = EditorSceneDraft.steps(root).stream().filter(value -> value.id().equals(trace.current())).findFirst().orElse(null);
                    if (next != null) choose(next.pointer(), select);
                }
            }
        }
        if (!trace.visited().isEmpty()) ImGui.textWrapped("Visited: " + String.join(" > ", trace.visited()));
        ImGui.textWrapped("Rehearsal follows authored outcomes and shows planned steps. Use a test world to verify gameplay effects.");
    }

    private void spawnPlan(AuthoringDocument document, JsonObject root, float scale, EditorResourceView.Fields fields, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        positionButtons((dimension, x, y, z) -> run.accept(() -> EditorSceneDraft.addSpawn(document, dimension, x, y, z)));
        ImGui.textWrapped("Spawn points use absolute block coordinates. Placement still checks terrain and entity clearance at runtime.");
        var points = DialogueGraph.array(root, "spawn_points");
        var dimensions = points.asList().stream().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .filter(point -> point.has("x") && point.has("z")).map(point -> DialogueGraph.string(point, "dimension"))
                .map(value -> value.isEmpty() ? "Scene dimension" : value).distinct().sorted().toList();
        if (!dimensions.contains(spawnDimension)) spawnDimension = dimensions.isEmpty() ? "" : dimensions.getFirst();
        if (!dimensions.isEmpty() && ImGui.beginCombo("Map dimension", spawnDimension)) {
            for (String dimension : dimensions) if (ImGui.selectable(dimension, dimension.equals(spawnDimension))) spawnDimension = dimension;
            ImGui.endCombo();
        }
        float size = Math.min(320 * scale, ImGui.getContentRegionAvailX());
        float left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##spawn_map", size, size);
        var draw = ImGui.getWindowDrawList();
        draw.addRectFilled(left, top, left + size, top + size, 0xff282522);
        var absolute = points.asList().stream().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .filter(point -> java.util.stream.Stream.of("x", "z").allMatch(axis -> point.has(axis) && point.get(axis).isJsonPrimitive()
                        && point.getAsJsonPrimitive(axis).isNumber() && Double.isFinite(point.get(axis).getAsDouble())))
                .filter(point -> (DialogueGraph.string(point, "dimension").isEmpty() ? "Scene dimension" : DialogueGraph.string(point, "dimension")).equals(spawnDimension)).toList();
        double minX = absolute.stream().mapToDouble(point -> point.get("x").getAsDouble()).min().orElse(0);
        double maxX = absolute.stream().mapToDouble(point -> point.get("x").getAsDouble()).max().orElse(0);
        double minZ = absolute.stream().mapToDouble(point -> point.get("z").getAsDouble()).min().orElse(0);
        double maxZ = absolute.stream().mapToDouble(point -> point.get("z").getAsDouble()).max().orElse(0);
        double radius = root.has("spawn_radius") && root.get("spawn_radius").isJsonPrimitive() && root.getAsJsonPrimitive("spawn_radius").isNumber()
                ? Math.clamp(root.get("spawn_radius").getAsDouble(), 1, 32) : 8;
        double extent = Math.max(8, Math.max(maxX - minX, maxZ - minZ) + radius * 2);
        draw.pushClipRect(left, top, left + size, top + size, true);
        for (var point : absolute) {
            float x = left + 24 + (float) ((point.get("x").getAsDouble() - (minX + maxX) / 2) / extent + .5) * (size - 48);
            float y = top + 24 + (float) ((point.get("z").getAsDouble() - (minZ + maxZ) / 2) / extent + .5) * (size - 48);
            draw.addCircle(x, y, (float) (radius / extent) * (size - 48), 0xff766b58);
            draw.addCircleFilled(x, y, 5 * scale, 0xff94c697);
            draw.addText(x + 8, y, 0xffffffff, DialogueGraph.string(point, "id"));
        }
        if (points.isEmpty()) draw.addCircle(left + size / 2, top + size / 2, size * .35f, 0xff766b58);
        draw.popClipRect();
        ImGui.textWrapped("Top view: east to the right, south down. Circles show the " + (int) radius + "-block spawn radius.");
        if (points.isEmpty()) ImGui.textWrapped("No authored points. Spawning uses the scene anchor and spawn radius.");
        else if (absolute.isEmpty()) ImGui.textWrapped("Actor and marker points resolve their position in the test world.");
        for (int i = 0; i < points.size(); i++) {
            String path = "/spawn_points/" + i;
            if (points.get(i).isJsonObject() && ImGui.selectable(DialogueGraph.string(points.get(i).getAsJsonObject(), "id"))) choose(path, select);
        }
        if (selected.matches("/spawn_points/[0-9]+")) {
            int index = Integer.parseInt(selected.substring(selected.lastIndexOf('/') + 1));
            if (index < points.size() && points.get(index).isJsonObject()) {
                fields.render(points.get(index), selected, false);
                if (ImGui.button("Remove spawn point")) {
                    String path = selected;
                    run.accept(() -> document.edit("Remove encounter spawn point", value -> {
                        JsonPointers.remove(value, path);
                        if (value.getAsJsonArray("spawn_points").isEmpty()) { value.remove("spawn_points"); value.remove("spawn_selection"); }
                        selected = "";
                    }));
                }
            }
        }
        if (ImGui.treeNode("Spawn settings")) { fields.render(root, "", true); ImGui.treePop(); }
    }

    private interface Position { void accept(String dimension, int x, int y, int z); }

    private void positionButtons(Position picked) {
        var minecraft = Minecraft.getInstance();
        ImGui.beginDisabled(minecraft.player == null);
        if (ImGui.button("Use player position")) {
            var player = minecraft.player; var pos = player.blockPosition();
            picked.accept(player.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
        ImGui.endDisabled();
        EditorWidgets.sameLineFor("Use targeted block");
        var hit = minecraft.hitResult instanceof BlockHitResult block && block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? block : null;
        ImGui.beginDisabled(hit == null || minecraft.level == null);
        if (ImGui.button("Use targeted block")) {
            var pos = hit.getBlockPos().relative(hit.getDirection());
            picked.accept(minecraft.level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
        ImGui.endDisabled();
    }

    private void choose(String pointer, Consumer<String> select) { selected = pointer; lastPointer = pointer; renamePath = ""; select.accept(pointer); }

    private static String find(JsonObject root, String array, String field, String id) {
        var values = JsonPointers.get(root, array).getAsJsonArray();
        for (int i = 0; i < values.size(); i++) if (values.get(i).isJsonObject() && DialogueGraph.string(values.get(i).getAsJsonObject(), field).equals(id)) return array + "/" + i;
        throw new IllegalArgumentException("Entry no longer exists: " + id);
    }
}
