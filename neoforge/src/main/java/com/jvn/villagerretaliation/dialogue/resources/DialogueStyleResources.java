package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueStyle;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DialogueStyleResources {
    public static final int MAX_STYLES = 256;
    public static final int MAX_CATALOG_CUES = 1024;
    private static volatile Map<String, DialogueStyle> serverStyles = Map.of();
    private static volatile Map<String, DialogueStyle> clientStyles = Map.of();
    private static final ThreadLocal<Map<String, DialogueStyle>> PREVIEW = new ThreadLocal<>();

    private DialogueStyleResources() { }

    public static Map<String, DialogueStyle> current() {
        var preview = PREVIEW.get();
        if (preview != null) return preview;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread() ? serverStyles : clientStyles;
    }

    public static Map<String, DialogueStyle> server() { return serverStyles; }

    public static void replaceClient(Map<String, DialogueStyle> definitions) {
        validateCatalog(definitions);
        clientStyles = Map.copyOf(definitions);
    }

    public static void validateCatalog(Map<String, DialogueStyle> definitions) {
        if (definitions.size() > MAX_STYLES || definitions.keySet().stream().anyMatch(tag -> !DialogueStyle.validTag(tag))
                || definitions.values().stream().mapToInt(style -> style.onEnter().size() + style.onExit().size()).sum() > MAX_CATALOG_CUES)
            throw new IllegalArgumentException("Invalid dialogue style catalog");
    }

    public static Scope preview(Map<String, DialogueStyle> definitions) {
        var previous = PREVIEW.get();
        PREVIEW.set(Map.copyOf(definitions));
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final Map<String, DialogueStyle> previous;
        private Scope(Map<String, DialogueStyle> previous) { this.previous = previous; }
        @Override public void close() {
            if (previous == null) PREVIEW.remove();
            else PREVIEW.set(previous);
        }
    }

    public static void reload(ResourceManager manager) {
        var documents = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (var entry : new TreeMap<>(manager.listResources("dialogue_styles", id -> id.getPath().endsWith(".json"))).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                documents.put(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | com.google.gson.JsonParseException | IllegalStateException exception) {
                throw new IllegalArgumentException("Invalid dialogue style at " + entry.getKey(), exception);
            }
        }
        serverStyles = compile(documents);
    }

    public static Map<String, DialogueStyle> compile(Map<ResourceLocation, JsonObject> documents) {
        var result = new LinkedHashMap<String, DialogueStyle>();
        for (var entry : documents.entrySet()) {
            DialogueStyle style;
            try { style = DialogueStyle.read(entry.getValue()); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(entry.getKey() + ": " + invalid.getMessage(), invalid); }
            String tag = entry.getValue().get("tag").getAsString();
            if (result.putIfAbsent(tag, style) != null) throw new IllegalArgumentException(entry.getKey() + ": duplicate style tag " + tag);
        }
        validateCatalog(result);
        return Map.copyOf(result);
    }

    public static void sync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        var payload = new com.jvn.villagerretaliation.network.DialogueStylesPayload(serverStyles);
        event.getRelevantPlayers().forEach(player -> com.jvn.villagerretaliation.network.VillagerReputationNetworking.sendDialogueStyles(player, payload));
    }
}
