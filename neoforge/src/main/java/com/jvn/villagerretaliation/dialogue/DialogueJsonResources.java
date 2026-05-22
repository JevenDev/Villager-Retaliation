package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.packs.resources.Resource;

final class DialogueJsonResources {
    private DialogueJsonResources() {
    }

    static void readEntryObjects(Resource resource, Consumer<JsonObject> entryConsumer) {
        readEntryObjects(resource, ignored -> null, (entry, ignored) -> entryConsumer.accept(entry));
    }

    static <T> void readEntryObjects(
            Resource resource,
            Function<JsonObject, T> rootContextFactory,
            BiConsumer<JsonObject, T> entryConsumer
    ) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            T rootContext = rootContextFactory.apply(root);

            JsonArray entries = root.getAsJsonArray("entries");
            if (entries != null) {
                for (JsonElement element : entries) {
                    if (element.isJsonObject()) {
                        entryConsumer.accept(element.getAsJsonObject(), rootContext);
                    }
                }
                return;
            }

            entryConsumer.accept(root, rootContext);
        } catch (IOException | IllegalStateException | JsonParseException ignored) {
            // Invalid datapack files are ignored so one custom story list cannot break dialogue.
        }
    }

    static List<String> readStringList(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }
}
