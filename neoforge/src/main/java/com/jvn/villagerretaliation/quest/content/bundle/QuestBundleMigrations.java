package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class QuestBundleMigrations {
    private QuestBundleMigrations() { }

    public static boolean accepts(MinecraftServer server, ResourceLocation quest, String previous, String current) {
        if (previous == null || current == null || !previous.matches("[a-f0-9]{64}") || !current.matches("[a-f0-9]{64}")) return false;
        for (var resource : DatapackResourceLoader.jsonResources(server, "quest_migrations")) {
            var value = DatapackResourceLoader.readObject(resource.location(), "quest migration", resource.resource());
            if (value.isPresent() && matches(value.get(), quest, previous, current)) return true;
        }
        return false;
    }

    public static boolean matches(JsonObject receipt, ResourceLocation quest, String previous, String current) {
        return text(receipt, "schema").equals("villagerretaliation:quest_migration/v1")
                && text(receipt, "quest").equals(quest.toString()) && text(receipt, "from_fingerprint").equals(previous)
                && text(receipt, "to_fingerprint").equals(current);
    }

    private static String text(JsonObject value, String name) {
        var field = value.get(name);
        return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString() ? field.getAsString() : "";
    }
}
