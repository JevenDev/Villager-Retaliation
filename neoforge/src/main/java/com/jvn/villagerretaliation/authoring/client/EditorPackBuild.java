package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

final class EditorPackBuild {
    private AuthoringWorkspace workspace;
    private final ImString description = new ImString(16384);
    private final ImInt format = new ImInt(48);
    private String originalDescription = "";
    private int originalFormat;
    private boolean richDescription;
    private boolean zip;
    private JsonObject baseline;
    private List<String> files;
    private long bytes;

    void load(AuthoringWorkspace next) {
        if (workspace == next && (next == null || pending() || next.packMetadata().equals(baseline))) return;
        workspace = next;
        files = null;
        if (next == null) return;
        baseline = next.packMetadata();
        JsonObject pack = baseline.has("pack") && baseline.get("pack").isJsonObject() ? baseline.getAsJsonObject("pack") : new JsonObject();
        try { originalFormat = pack.has("pack_format") ? pack.get("pack_format").getAsInt() : 48; }
        catch (NumberFormatException | UnsupportedOperationException | IllegalStateException exception) { originalFormat = 48; }
        format.set(originalFormat);
        var value = pack.get("description");
        richDescription = value != null && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString());
        originalDescription = richDescription || value == null ? "" : value.getAsString();
        description.set(originalDescription);
    }

    void invalidate() { files = null; }
    boolean zip() { return zip; }
    boolean pending() { return workspace != null && (format.get() != originalFormat || !richDescription && !description.get().equals(originalDescription)); }

    void apply() {
        if (!pending()) return;
        JsonObject metadata = workspace.packMetadata();
        if (!metadata.equals(baseline)) throw new IllegalStateException("Pack metadata changed. Discard the settings and reload pack.mcmeta before applying them");
        if (!metadata.has("pack") || !metadata.get("pack").isJsonObject()) metadata.add("pack", new JsonObject());
        JsonObject pack = metadata.getAsJsonObject("pack");
        pack.addProperty("pack_format", format.get());
        if (!richDescription) pack.addProperty("description", description.get());
        workspace.setPackMetadata(metadata);
        baseline = metadata.deepCopy();
        originalFormat = format.get(); originalDescription = description.get(); files = null;
    }

    void reload() { var current = workspace; workspace = null; load(current); }

    JsonObject recovery() {
        JsonObject state = new JsonObject();
        if (pending()) { state.addProperty("description", description.get()); state.addProperty("format", format.get()); }
        return state;
    }

    void recover(JsonObject state) {
        if (state.has("description")) description.set(state.get("description").getAsString());
        if (state.has("format")) format.set(state.get("format").getAsInt());
    }

    void render(AuthoringWorkspace workspace, List<AuthoringDiagnostic> diagnostics, ImString destination, Consumer<String> open,
                Consumer<ImGuiAuthoringEditor.EditorOperation> schedule, Runnable check, ImGuiAuthoringEditor.EditorOperation build) throws IOException {
        load(workspace);
        if (files == null) {
            files = workspace.filePaths(); bytes = 0;
            for (String file : files) bytes += workspace.readFile(file).length;
        }
        ImGui.textUnformatted("Finalize and build pack");
        ImGui.textWrapped(files.size() + " files, " + bytes + " bytes. Every file in this workspace is included in the package.");
        ImGui.separator();
        ImGui.textUnformatted("1. Pack settings");
        if (richDescription) {
            ImGui.textWrapped("This pack uses a rich description. Open pack.mcmeta to edit it while preserving its formatting.");
        } else {
            ImGui.textUnformatted("Pack description");
            ImGui.inputTextMultiline("##pack_description", description, -1, 64);
        }
        ImGui.setNextItemWidth(160); ImGui.inputInt("Pack format", format);
        ImGui.textDisabled("Minecraft 1.21.1 uses pack format 48.");
        if (pending() && ImGui.button("Discard pack setting edits")) reload();
        if (ImGui.button("Open pack.mcmeta")) open.accept("pack.mcmeta");
        ImGui.separator();
        ImGui.textUnformatted("2. Check contents");
        long errors = diagnostics.stream().filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR).count();
        long warnings = diagnostics.size() - errors;
        ImGui.textWrapped(errors + " errors, " + warnings + " warnings. Builds run validation again before writing files.");
        if (ImGui.button("Check drafts")) schedule.accept(check::run);
        ImGui.sameLine();
        if (ImGui.button("Browse pack files")) open.accept("");
        if (ImGui.collapsingHeader("Included files (" + files.size() + ")")) {
            ImGui.beginChild("##build_files", 0, 160, true);
            for (String file : files) if (ImGui.selectable(file + "##build:" + file)) open.accept(file);
            ImGui.endChild();
        }
        ImGui.separator();
        ImGui.textUnformatted("3. Build output");
        if (ImGui.radioButton("Folder", !zip)) { zip = false; if (destination.get().endsWith(".zip")) destination.set(destination.get().substring(0, destination.get().length() - 4)); }
        ImGui.sameLine();
        if (ImGui.radioButton("ZIP package", zip)) { zip = true; if (!destination.get().endsWith(".zip")) destination.set(destination.get() + ".zip"); }
        ImGui.textUnformatted(zip ? "ZIP filename" : "Output folder"); ImGui.setNextItemWidth(Math.max(60, ImGui.getContentRegionAvailX() - 86));
        ImGui.inputText("##build_destination", destination);
        ImGui.sameLine();
        if (ImGui.button("Browse")) EditorFileDialog.choose("Pack folder or ZIP", destination.get(), path -> net.minecraft.client.Minecraft.getInstance().execute(() -> destination.set(path)));
        ImGui.textWrapped(zip ? "Creates a shareable ZIP with pack.mcmeta at its root. Choose a new filename for each release."
                : "Updates editor-managed folders with a backup of the previous build. External edits are preserved.");
        ImGui.beginDisabled(errors > 0);
        if (ImGui.button(zip ? "Save and build ZIP" : "Save and build folder")) schedule.accept(build);
        ImGui.endDisabled();
    }

}
