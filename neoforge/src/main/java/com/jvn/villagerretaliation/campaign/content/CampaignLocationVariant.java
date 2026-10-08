package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public record CampaignLocationVariant(String id, String location, ResourceLocation layout, int layoutRevision,
        List<Cell> cells, String pointer) {
    public CampaignLocationVariant { cells = List.copyOf(cells); }

    static JsonObject schema() {
        var state = CampaignSchema.type("string");
        state.addProperty("minLength", 1);
        state.addProperty("maxLength", 1024);
        state.addProperty("pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\\])?$(?![\\s\\S])");
        var id = CampaignSchema.text("^[a-z0-9][a-z0-9_./-]*$");
        var cells = CampaignSchema.array(CampaignSchema.object(List.of("id", "expected", "placed"),
                "id", id, "expected", state, "placed", state), 1);
        cells.addProperty("maxItems", 256);
        var revision = CampaignSchema.type("integer");
        revision.addProperty("minimum", 1);
        revision.addProperty("maximum", Integer.MAX_VALUE);
        return CampaignSchema.object(List.of("id", "location", "layout", "layout_revision", "cells"),
                "id", id, "location", id, "layout", CampaignSchema.text("^[a-z0-9_.-]+:[a-z0-9_./-]+$"),
                "layout_revision", revision, "cells", cells);
    }

    static Map<String, CampaignLocationVariant> read(ResourceLocation source, JsonArray entries, List<QuestDiagnostic> issues) {
        var variants = new LinkedHashMap<String, CampaignLocationVariant>();
        for (int index = 0; index < entries.size(); index++) {
            var raw = entries.get(index).getAsJsonObject();
            String pointer = "/location_variants/" + index;
            var cells = new ArrayList<Cell>();
            var ids = new HashSet<String>();
            var authored = raw.getAsJsonArray("cells");
            for (int at = 0; at < authored.size(); at++) {
                var cell = authored.get(at).getAsJsonObject();
                String where = pointer + "/cells/" + at;
                String id = cell.get("id").getAsString();
                if (!ids.add(id)) issues.add(CampaignSchema.error(source, where + "/id", "duplicate variant cell " + id));
                var expected = state(source, cell.get("expected").getAsString(), where + "/expected", issues);
                var placed = state(source, cell.get("placed").getAsString(), where + "/placed", issues);
                if (expected != null && placed != null) cells.add(new Cell(id, expected, placed, where));
            }
            var variant = new CampaignLocationVariant(raw.get("id").getAsString(), raw.get("location").getAsString(),
                    ResourceLocation.parse(raw.get("layout").getAsString()), raw.get("layout_revision").getAsInt(), cells, pointer);
            if (variants.putIfAbsent(variant.id(), variant) != null)
                issues.add(CampaignSchema.error(source, pointer + "/id", "duplicate location variant " + variant.id()));
        }
        return Collections.unmodifiableMap(variants);
    }

    private static BlockState state(ResourceLocation source, String input, String pointer, List<QuestDiagnostic> issues) {
        try {
            var reader = new StringReader(input);
            var result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, false);
            if (reader.canRead()) throw new IllegalArgumentException("unexpected trailing block data");
            return result.blockState();
        } catch (CommandSyntaxException | IllegalArgumentException exception) {
            issues.add(CampaignSchema.error(source, pointer, "invalid campaign block state: " + exception.getMessage()));
            return null;
        }
    }

    static void validate(CampaignDefinition definition, List<QuestDiagnostic> issues) {
        for (var variant : definition.locationVariants().values()) {
            if (!definition.locations().containsKey(variant.location())) issues.add(CampaignSchema.error(definition.source(),
                    variant.pointer() + "/location", "unknown campaign location " + variant.location()));
        }
        for (var milestone : definition.milestones().values()) for (var outcome : milestone.outcomes().values()) {
            var seen = new HashSet<String>();
            for (int index = 0; index < outcome.locationVariants().size(); index++) {
                String id = outcome.locationVariants().get(index);
                String pointer = outcome.pointer() + "/location_variants/" + index;
                if (!milestone.irreversible()) issues.add(CampaignSchema.error(definition.source(), pointer,
                        "location variants require an irreversible world outcome"));
                if (!definition.locationVariants().containsKey(id)) issues.add(CampaignSchema.error(definition.source(), pointer,
                        "unknown location variant " + id));
                if (!seen.add(id)) issues.add(CampaignSchema.error(definition.source(), pointer, "duplicate location variant " + id));
            }
        }
    }

    public record Cell(String id, BlockState expected, BlockState placed, String pointer) { }
}
