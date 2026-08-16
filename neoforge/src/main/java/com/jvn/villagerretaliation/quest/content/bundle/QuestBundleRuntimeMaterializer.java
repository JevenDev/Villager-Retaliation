package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Rehydrates localized bundle references for the unchanged v2 runtime compiler. */
public final class QuestBundleRuntimeMaterializer {
    private QuestBundleRuntimeMaterializer() {
    }

    public static Result materialize(QuestBundleTransactions.EffectiveBundle bundle) {
        if (bundle == null) {
            return new Result(null, List.of("bundle has no quest definition"));
        }
        DefinitionResult definition = materializeDefinition(
                bundle, QuestBundlePath.Kind.QUEST, bundle.questId());
        return new Result(definition.definition(), definition.errors());
    }

    public static DefinitionResult materializeDefinition(
            QuestBundleTransactions.EffectiveBundle bundle,
            QuestBundlePath.Kind kind,
            ResourceLocation id) {
        if (bundle == null || kind == null || id == null) {
            return new DefinitionResult(null, List.of("bundle definition identity is incomplete"));
        }
        JsonObject source = bundle.definitions()
                .getOrDefault(kind, Map.of())
                .get(id);
        if (source == null) {
            return new DefinitionResult(
                    null, List.of("bundle has no " + kind + " definition for " + id));
        }
        List<String> errors = new ArrayList<>();
        JsonElement materialized = materialize(
                source, "", bundle.localizationPrefix(), bundle.locales(), "", false, errors);
        return new DefinitionResult(
                materialized != null && materialized.isJsonObject() ? materialized.getAsJsonObject() : null,
                List.copyOf(errors));
    }

    private static JsonElement materialize(
            JsonElement value,
            String field,
            String prefix,
            QuestLocaleCatalog locales,
            String path,
            boolean localizedContext,
            List<String> errors) {
        if (value == null || value.isJsonNull()) {
            return value == null ? null : value.deepCopy();
        }
        LocalizedReference reference = localizedContext
                ? LocalizedReference.read(value).orElse(null)
                : null;
        if (reference != null) {
            String messageId;
            try {
                messageId = reference.expand(prefix);
            } catch (IllegalArgumentException exception) {
                errors.add(location(path) + " " + exception.getMessage());
                return value.deepCopy();
            }
            JsonElement payload = locales.payload(QuestLocaleCatalog.ENGLISH, messageId).orElse(null);
            if (payload == null) {
                errors.add(location(path) + " has no effective en_us payload for " + messageId);
                return value.deepCopy();
            }
            return legacyPayload(field, payload);
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            JsonArray array = value.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                result.add(materialize(array.get(index), field, prefix, locales,
                        path + "/" + index, localizedContext, errors));
            }
            return result;
        }
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                boolean localized = localizedContext || isPlayerFacingField(entry.getKey());
                result.add(entry.getKey(), materialize(entry.getValue(), entry.getKey(), prefix, locales,
                        path + "/" + entry.getKey(), localized, errors));
            }
            return result;
        }
        return value.deepCopy();
    }

    private static JsonElement legacyPayload(String field, JsonElement payload) {
        if (!payload.isJsonObject()) {
            return payload.deepCopy();
        }
        JsonObject object = payload.getAsJsonObject();
        JsonElement lines = object.get("lines");
        if (lines != null && lines.isJsonArray()) {
            return "lines".equals(field) ? lines.deepCopy() : firstText(lines.getAsJsonArray());
        }
        JsonElement variants = object.get("variants");
        if (variants != null && variants.isJsonArray()) {
            JsonArray texts = new JsonArray();
            for (JsonElement variant : variants.getAsJsonArray()) {
                JsonElement text = variantText(variant);
                if (text != null) {
                    texts.add(text);
                }
            }
            return "lines".equals(field) ? texts : firstText(texts);
        }
        JsonElement text = variantText(object);
        return text == null ? payload.deepCopy() : text;
    }

    private static JsonElement firstText(JsonArray values) {
        for (JsonElement value : values) {
            JsonElement text = variantText(value);
            if (text != null) {
                return text;
            }
        }
        return new JsonPrimitive("");
    }

    private static JsonElement variantText(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.deepCopy();
        }
        if (!value.isJsonObject()) {
            return null;
        }
        JsonObject object = value.getAsJsonObject();
        for (String key : List.of("text", "line")) {
            JsonElement text = object.get(key);
            if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                return text.deepCopy();
            }
        }
        return null;
    }

    private static boolean isPlayerFacingField(String field) {
        return switch (field) {
            case "title", "description", "label", "text", "lines",
                    "tracker_text", "tracker_complete_text", "complete_text",
                    "custom_name", "trophy_name", "boss_bar_title",
                    "location_message" -> true;
            default -> false;
        };
    }

    private static String location(String path) {
        return path == null || path.isBlank() ? "/" : path;
    }

    public record DefinitionResult(JsonObject definition, List<String> errors) {
        public DefinitionResult {
            definition = definition == null ? null : definition.deepCopy();
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public boolean valid() {
            return this.definition != null && this.errors.isEmpty();
        }

        public JsonObject definition() {
            return this.definition == null ? null : this.definition.deepCopy();
        }
    }

    public record Result(JsonObject quest, List<String> errors) {
        public Result {
            quest = quest == null ? null : quest.deepCopy();
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public boolean valid() {
            return this.quest != null && this.errors.isEmpty();
        }

        public JsonObject quest() {
            return this.quest == null ? null : this.quest.deepCopy();
        }
    }
}
