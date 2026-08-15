package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Immutable lossless locale payloads with per-message English fallback. */
public final class QuestLocaleCatalog {
    public static final String ENGLISH = "en_us";
    private final Map<String, Map<String, JsonElement>> locales;

    public QuestLocaleCatalog(Map<String, Map<String, JsonElement>> locales) {
        Map<String, Map<String, JsonElement>> frozen = new TreeMap<>();
        if (locales != null) {
            locales.forEach((locale, messages) -> {
                if (locale != null && !locale.isBlank()) {
                    frozen.put(locale, freeze(messages));
                }
            });
        }
        this.locales = Collections.unmodifiableMap(frozen);
    }

    public static QuestLocaleCatalog empty() {
        return new QuestLocaleCatalog(Map.of());
    }

    public Set<String> locales() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(this.locales.keySet()));
    }

    public Map<String, JsonElement> messages(String locale) {
        return freeze(this.locales.getOrDefault(normalize(locale), Map.of()));
    }

    public Optional<JsonElement> payload(String locale, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        JsonElement localized = messages(locale).get(messageId);
        if (localized != null) {
            return Optional.of(localized.deepCopy());
        }
        JsonElement english = messages(ENGLISH).get(messageId);
        return english == null ? Optional.empty() : Optional.of(english.deepCopy());
    }

    public Optional<String> plainText(String locale, String messageId) {
        return payload(locale, messageId).flatMap(QuestLocaleCatalog::plainText);
    }

    public QuestLocaleCatalog overlay(String locale, Map<String, JsonElement> overlay) {
        Map<String, Map<String, JsonElement>> copy = new LinkedHashMap<>(this.locales);
        Map<String, JsonElement> merged = new LinkedHashMap<>(messages(locale));
        if (overlay != null) {
            overlay.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isJsonNull()) {
                    merged.put(key, value.deepCopy());
                }
            });
        }
        copy.put(normalize(locale), merged);
        return new QuestLocaleCatalog(copy);
    }

    public static ReadResult read(JsonObject root) {
        if (root == null) {
            return new ReadResult(Map.of(), "locale root must be a JSON object");
        }
        JsonObject messages = root.has("messages") && root.get("messages").isJsonObject()
                ? root.getAsJsonObject("messages")
                : root;
        Map<String, JsonElement> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : messages.entrySet()) {
            if ("schema".equals(entry.getKey()) && messages == root) {
                continue;
            }
            if (entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isJsonNull()) {
                return new ReadResult(Map.of(), "locale message IDs and payloads must be non-empty");
            }
            values.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return new ReadResult(freeze(values), "");
    }

    private static Optional<String> plainText(JsonElement payload) {
        if (payload.isJsonPrimitive() && payload.getAsJsonPrimitive().isString()) {
            return Optional.of(payload.getAsString());
        }
        if (payload.isJsonObject()) {
            JsonElement text = payload.getAsJsonObject().get("text");
            if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                return Optional.of(text.getAsString());
            }
        }
        return Optional.empty();
    }

    private static Map<String, JsonElement> freeze(Map<String, JsonElement> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, JsonElement> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value.deepCopy());
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String normalize(String locale) {
        return locale == null || locale.isBlank() ? ENGLISH : locale.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record ReadResult(Map<String, JsonElement> messages, String error) {
        public ReadResult {
            messages = freeze(messages);
            error = error == null ? "" : error;
        }

        public boolean valid() {
            return this.error.isBlank();
        }

        public Map<String, JsonElement> messages() {
            return freeze(this.messages);
        }
    }
}
