package com.jvn.villagerretaliation.party;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Separate, versioned ambient format. Proposed authoring JSON is never fed to dialogue loaders. */
public final class CompanionAmbientResources {
    public record Line(String id, String text, int weight) { }
    public record Profile(String id, List<String> structures, List<String> tags, boolean enabled, String pool, String recognizer) { }
    public record Snapshot(int cooldown, int combatQuiet, int dialogueQuiet, int range, int dwell,
            List<Profile> profiles, Map<String, List<Line>> pools) { }
    private static volatile Snapshot snapshot = new Snapshot(2400, 200, 100, 48, 20, List.of(), Map.of());
    private CompanionAmbientResources() { }
    public static Snapshot get() { return snapshot; }
    public static void reload(ResourceManager manager) {
        var profiles = new LinkedHashMap<String, Profile>();
        var pools = new LinkedHashMap<String, List<Line>>();
        Snapshot settings = snapshot;
        for (var entry : new TreeMap<>(manager.listResources("companion_ambient", id -> id.getPath().endsWith(".json"))).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var next = parse(JsonParser.parseReader(reader).getAsJsonObject());
                next.profiles().forEach(p -> profiles.put(p.id(), p)); pools.putAll(next.pools()); settings = next;
            } catch (Exception e) { throw new IllegalStateException("Invalid companion ambient data " + entry.getKey(), e); }
        }
        for (Profile p : profiles.values()) if (p.enabled() && !pools.containsKey(p.pool())) throw new IllegalArgumentException("Missing ambient pool " + p.pool());
        snapshot = new Snapshot(settings.cooldown(), settings.combatQuiet(), settings.dialogueQuiet(), settings.range(), settings.dwell(), List.copyOf(profiles.values()), Map.copyOf(pools));
        CompanionStructurePerception.mappingsReloaded();
    }
    public static Snapshot parse(JsonObject root) {
        if (!root.keySet().equals(Set.of("version", "cooldown_ticks", "combat_quiet_ticks", "dialogue_quiet_ticks", "range", "dwell_ticks", "profiles", "pools"))
                || root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown ambient fields/version");
        var profiles = new ArrayList<Profile>();
        var ids = new HashSet<String>();
        for (var raw : root.getAsJsonArray("profiles")) {
            var p = raw.getAsJsonObject();
            if (!p.keySet().equals(Set.of("id", "structures", "tags", "enabled", "pool", "recognizer"))) throw new IllegalArgumentException("Invalid profile fields");
            var profile = new Gson().fromJson(p, Profile.class);
            if (!ids.add(profile.id())) throw new IllegalArgumentException("Duplicate profile");
            profile.structures().forEach(ResourceLocation::parse); profile.tags().forEach(ResourceLocation::parse);
            profiles.add(profile);
        }
        var pools = new LinkedHashMap<String, List<Line>>();
        for (var entry : root.getAsJsonObject("pools").entrySet()) {
            var lines = new ArrayList<Line>(); var lineIds = new HashSet<String>();
            for (var raw : entry.getValue().getAsJsonArray()) {
                var value = raw.getAsJsonObject();
                if (!value.keySet().equals(Set.of("id", "text", "weight"))) throw new IllegalArgumentException("Invalid line fields");
                var line = new Gson().fromJson(value, Line.class);
                if (!lineIds.add(line.id()) || line.weight() < 1 || line.weight() > 10000
                        || !com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.safe(line.text().replace("{direction}", "nearby"))) throw new IllegalArgumentException("Invalid ambient text");
                lines.add(line);
            }
            if (lines.isEmpty()) throw new IllegalArgumentException("Empty ambient pool");
            pools.put(entry.getKey(), List.copyOf(lines));
        }
        return new Snapshot(integer(root, "cooldown_ticks", 200, 72000), integer(root, "combat_quiet_ticks", 200, 12000),
                integer(root, "dialogue_quiet_ticks", 100, 12000), integer(root, "range", 8, 64), integer(root, "dwell_ticks", 20, 60), List.copyOf(profiles), Map.copyOf(pools));
    }
    private static int integer(JsonObject root, String key, int min, int max) {
        int v = root.get(key).getAsInt(); if (v < min || v > max) throw new IllegalArgumentException("Invalid " + key); return v;
    }
}
