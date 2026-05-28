package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

final class DialogueJsonResources {
    private DialogueJsonResources() {
    }

    static void readEntryObjects(
            ResourceLocation location,
            String systemName,
            Resource resource,
            Consumer<JsonObject> entryConsumer) {
        readEntryObjects(location, systemName, resource, ignored -> null, (entry, ignored) -> entryConsumer.accept(entry));
    }

    static <T> void readEntryObjects(
            ResourceLocation location,
            String systemName,
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
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            DatapackDiagnostics.warnSkippedFile(location, systemName, exception);
        }
    }

    static List<String> readStringList(JsonObject entry, String key) {
        return DatapackJsonReader.readStringList(entry, key);
    }

    static String readString(JsonObject entry, String key) {
        return DatapackJsonReader.readString(entry, key);
    }

    static int readInt(JsonObject entry, String key, int fallback) {
        return DatapackJsonReader.readInt(entry, key, fallback);
    }
}
