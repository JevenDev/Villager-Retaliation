package com.jvn.villagerretaliation.authoring.client;

import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class EditorResourcePicker {
    private final ImString search = new ImString(256);
    private int selected;
    private String namespace = "", catalogKind = "";
    private long catalogGeneration = -1;
    private List<EditorResourceCatalog.Entry> entries = List.of();
    private final EditorResourcePreview preview = new EditorResourcePreview();
    private net.minecraft.client.resources.sounds.SoundInstance sound;
    private String soundOwner = "";
    private int soundOwnerFrame = -1;

    void beginFrame(boolean active) {
        preview.beginFrame(active);
        if (!active || soundOwnerFrame < ImGui.getFrameCount() - 1) stopSound();
    }

    void close() { preview.retire(); stopSound(); }

    private void stopSound() {
        if (sound != null) net.minecraft.client.Minecraft.getInstance().getSoundManager().stop(sound);
        sound = null; soundOwner = "";
    }

    private void listen(String owner, String id) {
        stopSound();
        var location = net.minecraft.resources.ResourceLocation.tryParse(id);
        var manager = net.minecraft.client.Minecraft.getInstance().getSoundManager();
        if (location == null || manager.getSoundEvent(location) == null) return;
        sound = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(location), 1, .5f);
        soundOwner = owner; soundOwnerFrame = ImGui.getFrameCount();
        manager.play(sound);
    }

    private static String modName(String namespace) {
        if (namespace.equals("minecraft")) return "Minecraft";
        return net.neoforged.fml.ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName()).orElse(namespace);
    }

    void renderRegistry(String id, String kind, String current, Consumer<String> choose,
                        EditorLibrary library, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (!EditorRegistryResources.supports(kind)) throw new IllegalArgumentException("Unknown resource picker: " + kind);
        if (id.equals(soundOwner)) soundOwnerFrame = ImGui.getFrameCount();
        String label = "Choose " + kind + "...";
        float scale = ImGui.getFontSize() / 16;
        ImGui.pushID(id);
        ImGui.setNextItemWidth(-1);
        float width = Math.min(560 * scale, ImGui.getMainViewport().getWorkSizeX() - 24);
        ImGui.setNextWindowSizeConstraints(width, 0, width, ImGui.getMainViewport().getWorkSizeY() - 24);
        if (ImGui.beginCombo("##registry_picker", label, imgui.flag.ImGuiComboFlags.HeightLargest)) {
            boolean opened = ImGui.isWindowAppearing();
            if (opened || !catalogKind.equals(kind) || catalogGeneration != EditorResourcePreview.generation()) {
                entries = EditorRegistryResources.entries(kind);
                catalogKind = kind; catalogGeneration = EditorResourcePreview.generation();
            }
            if (opened) { search.set(""); namespace = ""; selected = 0; ImGui.setKeyboardFocusHere(); }
            String before = search.get();
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextWithHint("##filter", "Search names or resource IDs...", search);
            boolean searching = ImGui.isItemActive();
            boolean enter = (searching || ImGui.isItemDeactivated()) && ImGui.isKeyPressed(ImGuiKey.Enter);
            boolean moved = !before.equals(search.get());
            if (moved) selected = 0;
            ImGui.setNextItemWidth(-1);
            if (ImGui.beginCombo("##mod", namespace.isEmpty() ? "All mods" : modName(namespace) + " (" + namespace + ")")) {
                if (ImGui.selectable("All mods", namespace.isEmpty())) { namespace = ""; selected = 0; moved = true; }
                for (String source : EditorResourceCatalog.namespaces(entries))
                    if (ImGui.selectable(modName(source) + " (" + source + ")", source.equals(namespace))) { namespace = source; selected = 0; moved = true; }
                ImGui.endCombo();
            }
            var favorites = library.resources(label, true);
            var recent = library.resources(label, false);
            var matches = EditorResourceCatalog.matching(entries, search.get(), namespace, favorites, recent);
            if (opened) for (int i = 0; i < matches.size(); i++) if (matches.get(i).id().equals(current)) { selected = i; moved = true; break; }
            if (searching && ImGui.isKeyPressed(ImGuiKey.DownArrow)) { selected++; moved = true; }
            if (searching && ImGui.isKeyPressed(ImGuiKey.UpArrow)) { selected--; moved = true; }
            selected = Math.clamp(selected, 0, Math.max(0, matches.size() - 1));
            if (enter && !matches.isEmpty()) {
                accept(label, matches.get(selected).id(), choose, library, run);
                ImGui.closeCurrentPopup();
            }
            ImGui.textDisabled(matches.size() + (matches.size() == 1 ? " match" : " matches"));
            float rowHeight = Math.max(32 * scale, ImGui.getTextLineHeight() * 2 + 8 * scale);
            float stride = rowHeight + ImGui.getStyle().getItemSpacingY();
            String previewId = matches.isEmpty() ? "" : matches.get(selected).id();
            ImGui.beginChild("##registry_choices", 0, Math.min(270 * scale, Math.max(1, matches.size()) * stride), false);
            if (moved) ImGui.setScrollY(selected * stride);
            var clipper = new imgui.ImGuiListClipper();
            try {
                clipper.begin(matches.size(), stride);
                while (clipper.step()) for (int i = clipper.getDisplayStart(); i < clipper.getDisplayEnd(); i++) {
                    var entry = matches.get(i);
                    ImGui.pushID(entry.id());
                    boolean favorite = favorites.contains(entry.id());
                    if (ImGui.button(favorite ? "Unstar" : "Star", 64 * scale, rowHeight))
                        run.accept(() -> library.resource(label, entry.id(), true));
                    ImGui.sameLine();
                    float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY(), available = ImGui.getContentRegionAvailX();
                    if (ImGui.selectable("##choice", entry.id().equals(current) || i == selected, 0, available, rowHeight)) {
                        accept(label, entry.id(), choose, library, run);
                        ImGui.closeCurrentPopup();
                    }
                    if (ImGui.isItemHovered()) { previewId = entry.id(); ImGui.setTooltip(entry.title() + "\n" + entry.id()); }
                    float iconWidth = kind.equals("item") || kind.equals("block") ? 36 * scale : 0;
                    if (iconWidth > 0) preview.icon(kind, entry.id(), x, y + (rowHeight - 32 * scale) / 2, 32 * scale);
                    var draw = ImGui.getWindowDrawList();
                    draw.pushClipRect(x + iconWidth, y, x + available, y + rowHeight, true);
                    draw.addText(x + iconWidth + 4 * scale, y + 4 * scale, ImGui.getColorU32(imgui.flag.ImGuiCol.Text), entry.title());
                    draw.addText(x + iconWidth + 4 * scale, y + 4 * scale + ImGui.getTextLineHeight(),
                            ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled), entry.id());
                    draw.popClipRect();
                    ImGui.popID();
                }
                clipper.end();
            } finally { clipper.destroy(); }
            if (matches.isEmpty()) ImGui.textWrapped("No matching resources.");
            ImGui.endChild();
            if (kind.equals("entity") && !previewId.isEmpty()) {
                ImGui.separator();
                ImGui.textUnformatted(previewId);
                preview.entity(previewId, 128 * scale, 144 * scale);
            }
            if (kind.equals("sound")) soundControls(id, previewId);
            ImGui.endCombo();
        }
        String shortcut = EditorRegistryResources.shortcutLabel(kind);
        if (!shortcut.isEmpty()) {
            String value = EditorRegistryResources.shortcut(kind);
            ImGui.beginDisabled(value.isEmpty());
            if (ImGui.smallButton(shortcut)) accept(label, value, choose, library, run);
            ImGui.endDisabled();
            if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled))
                ImGui.setTooltip(value.isEmpty() ? "Hold an item or aim at the matching object in the world first." : value);
        }
        if (kind.equals("sound")) soundControls(id, current);
        ImGui.popID();
    }

    private void soundControls(String owner, String id) {
        var location = net.minecraft.resources.ResourceLocation.tryParse(id);
        boolean available = location != null && net.minecraft.client.Minecraft.getInstance().getSoundManager().getSoundEvent(location) != null;
        ImGui.pushID("sound:" + id);
        ImGui.beginDisabled(!available);
        if (ImGui.smallButton("Listen")) listen(owner, id);
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(sound == null);
        if (ImGui.smallButton("Stop")) stopSound();
        ImGui.endDisabled();
        ImGui.popID();
    }

    private void accept(String label, String choice, Consumer<String> choose, EditorLibrary library,
                        Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        stopSound();
        choose.accept(choice);
        run.accept(() -> library.resource(label, choice, false));
    }

    static List<String> matching(List<String> choices, String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).contains(term))
                .sorted(java.util.Comparator.comparingInt(choice -> {
                    String id = choice.toLowerCase(Locale.ROOT);
                    return id.equals(term) || id.substring(id.indexOf(':') + 1).equals(term) ? 0 : 1;
                })).toList();
    }

    static List<String> ranked(List<String> choices, String query, List<String> favorites, List<String> recent) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return matching(choices, query).stream().sorted(java.util.Comparator.<String>comparingInt(choice -> {
            String normalized = choice.toLowerCase(Locale.ROOT);
            return normalized.equals(term) || normalized.substring(normalized.indexOf(':') + 1).equals(term) ? 0 : 1;
        }).thenComparingInt(choice -> favorites.contains(choice) ? 0 : recent.contains(choice) ? 1 + recent.indexOf(choice) : 100)).toList();
    }

    void render(String id, String label, String current, List<String> choices, Consumer<String> choose,
                EditorLibrary library, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (choices.isEmpty()) return;
        ImGui.pushID(id);
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##resource_picker", label, imgui.flag.ImGuiComboFlags.HeightLarge)) {
            if (ImGui.isWindowAppearing()) { search.set(""); selected = 0; ImGui.setKeyboardFocusHere(); }
            String before = search.get();
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextWithHint("##filter", "Type to filter...", search);
            boolean enter = ImGui.isKeyPressed(ImGuiKey.Enter);
            if (!before.equals(search.get())) selected = 0;
            var favorites = library.resources(label, true); var recent = library.resources(label, false);
            var matches = ranked(choices, search.get(), favorites, recent);
            boolean moved = false;
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) { selected++; moved = true; }
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) { selected--; moved = true; }
            selected = Math.clamp(selected, 0, Math.max(0, matches.size() - 1));
            if (enter && !matches.isEmpty()) { String choice = matches.get(selected); choose.accept(choice); run.accept(() -> library.resource(label, choice, false)); ImGui.closeCurrentPopup(); }
            if (matches.isEmpty()) ImGui.textDisabled("No matches");
            ImGui.beginChild("##choices", 0, Math.min(240, Math.max(1, matches.size()) * ImGui.getFrameHeightWithSpacing()), false);
            for (int i = 0; i < matches.size(); i++) {
                String choice = matches.get(i);
                ImGui.pushID(i);
                boolean favorite = favorites.contains(choice);
                if (ImGui.smallButton(favorite ? "Unstar" : "Star")) run.accept(() -> library.resource(label, choice, true));
                ImGui.sameLine();
                String prefix = favorite ? "[Favorite] " : recent.contains(choice) ? "[Recent] " : "";
                if (ImGui.selectable(prefix + choice, choice.equals(current) || i == selected)) {
                    choose.accept(choice); run.accept(() -> library.resource(label, choice, false)); ImGui.closeCurrentPopup();
                }
                if (moved && i == selected) ImGui.setScrollHereY();
                ImGui.popID();
            }
            ImGui.endChild();
            ImGui.endCombo();
        }
        ImGui.popID();
    }
}
