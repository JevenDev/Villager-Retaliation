package com.jvn.villagerretaliation.progress;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PlayerHistoryChecks {
    private PlayerHistoryChecks() { }

    public static int run() throws java.io.IOException {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var nether = id("minecraft:the_nether");
        var end = id("minecraft:the_end");
        var custom = id("absent_mod:damage");
        var data = new PlayerHistorySavedData();
        check(data.dimensions(first).isEmpty() && data.worldDimensions().isEmpty(), "history starts empty");
        data.visitDimension(first, nether);
        data.visitDimension(second, end);
        data.visitBiome(first, id("absent_mod:biome"));
        data.obtainWorldAdvancement(id("absent_mod:advancement"));
        var death = new PlayerHistorySavedData.Death(custom, id("minecraft:player"), second, "Other player",
                nether, new BlockPos(-4, 70, 12), 1234, "A saved death message");
        data.recordDeath(first, death);
        var loaded = PlayerHistorySavedData.load(data.save(new CompoundTag(), null), null);
        check(loaded.dimensions(first).equals(Set.of(nether)), "player dimensions stay isolated");
        check(loaded.dimensions(second).equals(Set.of(end)), "other player dimensions survive");
        check(loaded.worldDimensions().equals(Set.of(nether, end)), "world discoveries accumulate across players");
        check(loaded.biomes(first).contains(id("absent_mod:biome")), "missing biome IDs survive");
        check(loaded.worldAdvancements().contains(id("absent_mod:advancement")), "missing advancements survive");
        check(loaded.deathCauses(first).contains(custom), "modded death causes survive");
        check(death.equals(loaded.latestDeath(first)), "latest death retains its complete snapshot");
        check(loaded.hasPendingDeath(first), "pending delivery survives disconnect and reload");
        check(death.equals(loaded.takePendingDeath(first)), "pending delivery returns the snapshot");
        check(loaded.takePendingDeath(first) == null, "pending delivery happens once");
        check(death.equals(loaded.latestDeath(first)), "delivery does not consume dialogue history");
        var next = new PlayerHistorySavedData.Death(id("minecraft:fall"), null, null, "", end, BlockPos.ZERO, 1240, "Fell");
        loaded.recordDeath(first, next);
        loaded = PlayerHistorySavedData.load(loaded.save(new CompoundTag(), null), null);
        check(next.equals(loaded.latestDeath(first)) && loaded.deathCauses(first).equals(Set.of(custom, id("minecraft:fall"))),
                "latest death replaces while historical causes accumulate");
        check(loaded.latestDeath(second) == null && loaded.deathCauses(second).isEmpty(), "death history stays personal");
        for (var kind : PlayerHistoryConditions.Kind.values()) {
            var value = new com.google.gson.JsonObject();
            value.addProperty("type", kind.id());
            if (!kind.count()) value.add(kind.field(), JsonParser.parseString("[\"test:entry\",\"test:entry\",\"test:other\"]"));
            var parsed = PlayerHistoryConditions.read(id("test:checks"), "test", value, kind).orElseThrow();
            check(DialogueCondition.canonicalTypeId(parsed).equals(kind.id()), "registry identity: " + kind.id());
            var reference = ((PlayerHistoryConditions.Check) parsed).reference();
            check(reference.all() && reference.min() == 1, "defaults: " + kind.id());
            if (!kind.count()) check(reference.ids().size() == 2, "duplicate entries do not change semantics");
        }
        for (String invalid : new String[] {
                "{\"type\":\"player_died_from\",\"damage_types\":[]}",
                "{\"type\":\"player_killed_entity\",\"entities\":[\"BAD ID\"]}",
                "{\"type\":\"player_last_death\"}",
                "{\"type\":\"player_deaths\",\"min\":-1}",
                "{\"type\":\"player_deaths\",\"min\":5,\"max\":2}",
                "{\"type\":\"player_deaths\",\"min\":2147483648}"}) {
            var raw = JsonParser.parseString(invalid).getAsJsonObject();
            var conditions = DialogueCondition.readObjects(id("test:invalid"), "test", java.util.List.of(raw), null);
            check(conditions.size() == 1 && conditions.getFirst() instanceof DialogueCondition.Invalid, "invalid checks fail closed");
        }
        var root = java.nio.file.Path.of("").toAbsolutePath();
        while (root != null && !java.nio.file.Files.isDirectory(root.resolve("tools/schemas"))) root = root.getParent();
        check(root != null, "schema artifact directory is available");
        var artifacts = root.resolve("tools/schemas");
        var gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        check(java.nio.file.Files.readString(artifacts.resolve("quest-v2.schema.json")).replace("\r\n", "\n")
                .equals(com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema.exportJson().replace("\r\n", "\n")), "quest v2 artifact matches the exact runtime export");
        check(java.nio.file.Files.readString(artifacts.resolve("quest-v3.schema.json")).replace("\r\n", "\n")
                .equals(gson.toJson(com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export()) + "\n"), "quest v3 artifact matches the exact runtime export");
        check(java.nio.file.Files.readString(artifacts.resolve("quest-registry-metadata.json")).replace("\r\n", "\n")
                .equals(com.jvn.villagerretaliation.quest.QuestRegistryMetadata.exportJson().replace("\r\n", "\n")), "registry metadata matches the exact runtime export");
        return 58;
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
