package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Canonical fingerprints: object order is ignored while behaviorally ordered arrays remain ordered. */
public final class QuestBundleFingerprints {
    private QuestBundleFingerprints() {
    }

    public static String structural(QuestBundleTransactions.EffectiveBundle bundle) {
        JsonObject root = definitions(bundle);
        return sha256(canonical(root));
    }

    public static String migrationEquivalent(QuestBundleTransactions.EffectiveBundle bundle) {
        JsonObject root = definitions(bundle);
        JsonObject locales = new JsonObject();
        for (String locale : bundle.locales().locales()) {
            JsonObject messages = new JsonObject();
            bundle.locales().messages(locale).forEach(messages::add);
            locales.add(locale, messages);
        }
        root.add("locales", locales);
        return sha256(canonical(root));
    }

    private static JsonObject definitions(QuestBundleTransactions.EffectiveBundle bundle) {
        JsonObject root = new JsonObject();
        bundle.definitions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(kind -> {
                    JsonObject values = new JsonObject();
                    kind.getValue().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> values.add(entry.getKey().toString(), entry.getValue()));
                    root.add(kind.getKey().name().toLowerCase(java.util.Locale.ROOT), values);
                });
        return root;
    }

    private static String canonical(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<String> values = new ArrayList<>(array.size());
            for (JsonElement value : array) {
                values.add(canonical(value));
            }
            return "[" + String.join(",", values) + "]";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return "{" + object.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(entry -> quote(entry.getKey()) + ":" + canonical(entry.getValue()))
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + "}";
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.toString();
    }

    private static String quote(String value) {
        return new JsonPrimitive(value).toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
