package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;

/** A schema-designated player-facing message reference. */
public record LocalizedReference(String value) {
    public LocalizedReference {
        value = value == null ? "" : value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("localized reference must not be blank");
        }
    }

    public static Optional<LocalizedReference> read(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement key = object.get("key");
        if (key == null || !key.isJsonPrimitive() || !key.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        String value = key.getAsString().trim();
        return value.isBlank() ? Optional.empty() : Optional.of(new LocalizedReference(value));
    }

    public String expand(String localizationPrefix) {
        if (!this.value.startsWith("#")) {
            return this.value;
        }
        String suffix = this.value.substring(1);
        String prefix = localizationPrefix == null ? "" : localizationPrefix.trim();
        if (prefix.isBlank() || suffix.isBlank()) {
            throw new IllegalArgumentException("relative localized reference requires a prefix and non-empty suffix");
        }
        return prefix + (prefix.endsWith(".") ? "" : ".") + suffix;
    }

    public boolean relative() {
        return this.value.startsWith("#");
    }
}
