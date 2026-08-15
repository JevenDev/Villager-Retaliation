package com.jvn.villagerretaliation.quest.content.reward;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** A registry-aware, frozen quest reward table retaining its stable pre-migration ID. */
public record BundledQuestReward(ResourceLocation id, LootTable table, JsonObject tableJson) {
    public static final String SCHEMA = "villagerretaliation:quest_reward/v1";

    public BundledQuestReward {
        if (id == null || table == null || tableJson == null) {
            throw new IllegalArgumentException("bundled quest rewards require id, table, and source JSON");
        }
        tableJson = tableJson.deepCopy();
    }

    public JsonObject tableJson() {
        return this.tableJson.deepCopy();
    }

    public static ParseResult parse(JsonObject wrapper, HolderLookup.Provider registries) {
        List<String> errors = new ArrayList<>();
        if (wrapper == null) {
            return ParseResult.error("quest reward wrapper must be a JSON object");
        }
        if (!SCHEMA.equals(string(wrapper, "schema"))) {
            errors.add("quest reward schema must be " + SCHEMA);
        }
        ResourceLocation id = ResourceLocation.tryParse(string(wrapper, "id"));
        if (id == null) {
            errors.add("quest reward wrapper requires a valid explicit stable id");
        }
        JsonObject tableJson = wrapper.has("table") && wrapper.get("table").isJsonObject()
                ? wrapper.getAsJsonObject("table").deepCopy()
                : null;
        if (tableJson == null) {
            errors.add("quest reward wrapper requires a table object");
        } else if (!tableJson.has("type") || !tableJson.get("type").isJsonPrimitive()
                || !tableJson.get("type").getAsJsonPrimitive().isString()) {
            errors.add("quest reward table.type must explicitly be minecraft:generic");
        } else {
            String declaredType = tableJson.get("type").getAsString();
            DataResult<?> typeDecoded = LootContextParamSets.CODEC.parse(JsonOps.INSTANCE, tableJson.get("type"));
            if (typeDecoded.error().isPresent()) {
                errors.add("LootTable.DIRECT_CODEC table.type codec rejected \"" + declaredType + "\": "
                        + typeDecoded.error().orElseThrow().message());
            } else if (!"minecraft:generic".equals(declaredType)) {
                errors.add("quest reward table.type must be minecraft:generic");
            }
        }
        if (registries == null) {
            errors.add("quest reward decoding requires registry context");
        }
        if (!errors.isEmpty()) {
            return new ParseResult(null, errors);
        }

        DataResult<LootTable> decoded = LootTable.DIRECT_CODEC.parse(
                RegistryOps.create(JsonOps.INSTANCE, registries),
                tableJson);
        if (decoded.error().isPresent()) {
            return ParseResult.error("LootTable.DIRECT_CODEC rejected table: "
                    + decoded.error().orElseThrow().message());
        }
        LootTable table = decoded.result().orElse(null);
        if (table == null) {
            return ParseResult.error("LootTable.DIRECT_CODEC returned no table");
        }
        if (table.getParamSet() != LootContextParamSets.ALL_PARAMS) {
            return ParseResult.error("quest reward table.type must be minecraft:generic");
        }

        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        table.validate(new ValidationContext(reporter, LootContextParamSets.EMPTY, registries.asGetterLookup()));
        reporter.getReport().ifPresent(report ->
                errors.add("quest reward uses parameters unavailable to LootContextParamSets.EMPTY:" + report));
        if (!errors.isEmpty()) {
            return new ParseResult(null, errors);
        }

        table.setLootTableId(id);
        table.freeze();
        return new ParseResult(new BundledQuestReward(id, table, tableJson), List.of());
    }

    private static String string(JsonObject root, String field) {
        return root.has(field) && root.get(field).isJsonPrimitive()
                && root.get(field).getAsJsonPrimitive().isString()
                ? root.get(field).getAsString().trim()
                : "";
    }

    public record ParseResult(BundledQuestReward reward, List<String> errors) {
        public ParseResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public static ParseResult error(String message) {
            return new ParseResult(null, List.of(message == null ? "invalid quest reward" : message));
        }

        public boolean valid() {
            return this.reward != null && this.errors.isEmpty();
        }
    }
}
