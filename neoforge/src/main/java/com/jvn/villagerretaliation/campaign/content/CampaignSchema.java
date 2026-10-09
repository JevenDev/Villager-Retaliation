package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class CampaignSchema {
    public static final String SCHEMA_ID = "villagerretaliation:campaign/v1";
    private static final String LOCAL_ID = "^[a-z0-9][a-z0-9_./-]*$";
    private static final String RESOURCE_ID = "^[a-z0-9_.-]+:[a-z0-9_./-]+$";

    private CampaignSchema() {
    }

    public static JsonObject export() {
        JsonObject prerequisite = object(List.of(),
                "chapter", text(LOCAL_ID), "milestone", text(LOCAL_ID), "outcome", text(LOCAL_ID));
        JsonObject chapter = object(List.of("id", "title_key", "quests"),
                "id", text(LOCAL_ID), "title_key", text(""),
                "prerequisites", array(prerequisite, 0), "quests", array(text(RESOURCE_ID), 1));
        JsonObject outcome = object(List.of("id", "title_key"), "id", text(LOCAL_ID), "title_key", text(""),
                "location_variants", array(text(LOCAL_ID), 0));
        JsonObject catchUp = object(List.of("quest", "stage"), "quest", text(RESOURCE_ID), "stage", text(LOCAL_ID));
        JsonObject observation = object(List.of("event", "outcome"),
                "event", enumeration("minecraft:ender_dragon_defeated", CampaignDefinition.Observation.CHARACTER_LOST.toString()),
                "outcome", text(LOCAL_ID), "character", text(LOCAL_ID));
        JsonObject lossEvent = object(List.of("event"), "event", enumeration(CampaignDefinition.Observation.CHARACTER_LOST.toString()));
        lossEvent.addProperty("additionalProperties", true);
        JsonObject lossCharacter = object(List.of("character"), "character", text(LOCAL_ID));
        lossCharacter.addProperty("additionalProperties", true);
        observation.add("if", lossEvent);
        observation.add("then", lossCharacter);
        observation.add("else", object(List.of("event", "outcome"), "event", enumeration("minecraft:ender_dragon_defeated"), "outcome", text(LOCAL_ID)));
        JsonObject milestone = object(List.of("id", "chapter", "quest", "outcomes"),
                "id", text(LOCAL_ID), "chapter", text(LOCAL_ID), "quest", text(RESOURCE_ID), "stage", text(LOCAL_ID),
                "irreversible", type("boolean"), "outcomes", array(outcome, 1),
                "catch_up", catchUp, "observe", observation);
        JsonObject fact = object(List.of("id", "scope", "kind"),
                "id", text(LOCAL_ID), "scope", enumeration("world", "player"),
                "kind", enumeration("tag", "variable", "counter"));
        JsonObject experience = type("integer");
        experience.addProperty("minimum", 0);
        experience.addProperty("maximum", Integer.MAX_VALUE);
        JsonObject reward = object(List.of("id", "quest", "scope"), "id", text(LOCAL_ID), "quest", text(RESOURCE_ID),
                "scope", enumeration("personal", "participants", "shared"), "milestone", text(LOCAL_ID), "outcome", text(LOCAL_ID),
                "experience", experience, "loot_table", text(RESOURCE_ID));
        JsonObject coordinate = type("integer");
        coordinate.addProperty("minimum", -30_000_000);
        coordinate.addProperty("maximum", 30_000_000);
        JsonObject radius = type("integer");
        radius.addProperty("minimum", 1);
        radius.addProperty("maximum", CampaignLocationDefinition.MAX_SEARCH_RADIUS);
        JsonObject location = object(List.of("id", "title_key", "source", "dimension"),
                "id", text(LOCAL_ID), "title_key", text(""), "source", enumeration("marker", "village", "structure"),
                "dimension", text(RESOURCE_ID), "origin", object(List.of("x", "y", "z"),
                        "x", coordinate, "y", coordinate, "z", coordinate),
                "search_radius", radius, "structure", text(RESOURCE_ID), "structure_marker", text(RESOURCE_ID));
        location.getAsJsonObject("properties").getAsJsonObject("structure_marker").addProperty("description",
                "Tag on a persistent minecraft:marker entity inside the registered structure.");
        JsonObject taggedStructure = object(List.of("structure_marker"), "structure_marker", text(RESOURCE_ID));
        taggedStructure.addProperty("additionalProperties", true);
        JsonObject structureSource = object(List.of("source"), "source", enumeration("structure"));
        structureSource.addProperty("additionalProperties", true);
        location.add("if", taggedStructure);
        location.add("then", structureSource);
        JsonObject characterRadius = type("integer");
        characterRadius.addProperty("minimum", 1);
        characterRadius.addProperty("maximum", CampaignCharacterDefinition.MAX_SEARCH_RADIUS);
        JsonObject character = object(List.of("id", "title_key", "source", "location", "loss_policy"),
                "id", text(LOCAL_ID), "title_key", text(""), "source", enumeration("existing_villager", "owned_spawn"),
                "location", text(LOCAL_ID), "search_radius", characterRadius, "profession", text(RESOURCE_ID),
                "loss_policy", enumeration("downed", "alternate_path", "replacement"), "replacement_role", text(LOCAL_ID));
        JsonObject replacementPolicy = object(List.of("loss_policy"), "loss_policy", enumeration("replacement"));
        replacementPolicy.addProperty("additionalProperties", true);
        JsonObject replacementRole = object(List.of("replacement_role"), "replacement_role", text(LOCAL_ID));
        replacementRole.addProperty("additionalProperties", true);
        JsonArray replacementRules = new JsonArray();
        replacementRules.add(conditional(replacementPolicy, replacementRole));
        replacementRules.add(conditional(replacementRole, replacementPolicy));
        character.add("allOf", replacementRules);
        JsonObject ownedSource = object(List.of("source"), "source", enumeration("owned_spawn"));
        ownedSource.addProperty("additionalProperties", true);
        JsonObject spawnRadius = characterRadius.deepCopy();
        spawnRadius.addProperty("maximum", CampaignCharacterDefinition.MAX_SPAWN_RADIUS);
        JsonObject ownedBounds = object(List.of(), "search_radius", spawnRadius);
        ownedBounds.addProperty("additionalProperties", true);
        character.add("if", ownedSource);
        character.add("then", ownedBounds);
        JsonObject evidenceRadius = type("integer");
        evidenceRadius.addProperty("minimum", 1);
        evidenceRadius.addProperty("maximum", CampaignEvidenceDefinition.MAX_SEARCH_RADIUS);
        JsonObject evidence = object(List.of("id", "title_key", "text_key", "source", "location", "entity_tag"),
                "id", text(LOCAL_ID), "title_key", text(""), "text_key", text(""), "source", enumeration("interaction"),
                "location", text(LOCAL_ID), "entity_tag", text(RESOURCE_ID), "search_radius", evidenceRadius);
        JsonObject revision = type("integer");
        revision.addProperty("minimum", 1);
        revision.addProperty("maximum", Integer.MAX_VALUE);
        JsonObject root = object(List.of("schema", "id", "revision", "title_key", "localization_prefix",
                        "quest_bundles", "chapters", "milestones"),
                "schema", enumeration(SCHEMA_ID), "id", text(RESOURCE_ID), "revision", revision,
                "title_key", text(""), "localization_prefix", text(""),
                "quest_bundles", array(text(RESOURCE_ID), 1), "chapters", array(chapter, 1),
                "milestones", array(milestone, 0), "facts", array(fact, 0), "rewards", array(reward, 0),
                "locations", array(location, 0), "characters", array(character, 0), "evidence", array(evidence, 0),
                "location_variants", array(CampaignLocationVariant.schema(), 0),
                "fixture_entries", array(CampaignFixtureEntry.schema(), 0),
                "relationships", array(CampaignStandingDefinition.schema(), 0), "factions", array(CampaignStandingDefinition.schema(), 0),
                "transactions", array(CampaignTransactionDefinition.schema(), 0));
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://villagerretaliation.dev/schema/campaign-v1.schema.json");
        root.addProperty("title", "Villager Retaliation Campaign v1");
        return root;
    }

    public static List<QuestDiagnostic> validate(ResourceLocation source, JsonElement input) {
        return QuestJsonSchema.validate(input, export()).stream()
                .map(issue -> error(source, issue.pointer(), issue.message())).toList();
    }

    static QuestDiagnostic error(ResourceLocation source, String pointer, String message) {
        return new QuestDiagnostic(QuestDiagnostic.Severity.ERROR, "campaign.invalid", source, "",
                source == null ? "" : source.toString(), pointer, message,
                "Correct the campaign definition at this source location and reload datapacks.", Set.of());
    }

    static JsonObject object(List<String> required, Object... fields) {
        JsonObject result = type("object");
        result.addProperty("additionalProperties", false);
        JsonArray requiredFields = new JsonArray();
        required.forEach(requiredFields::add);
        result.add("required", requiredFields);
        JsonObject properties = new JsonObject();
        for (int index = 0; index < fields.length; index += 2) {
            properties.add((String) fields[index], (JsonElement) fields[index + 1]);
        }
        result.add("properties", properties);
        return result;
    }

    private static JsonObject conditional(JsonObject condition, JsonObject consequence) {
        JsonObject result = object(List.of());
        result.addProperty("additionalProperties", true);
        result.add("if", condition);
        result.add("then", consequence);
        return result;
    }

    static JsonObject type(String type) {
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        return result;
    }

    static JsonObject text(String pattern) {
        JsonObject result = type("string");
        result.addProperty("minLength", 1);
        result.addProperty("maxLength", 256);
        result.addProperty("pattern", (pattern.isBlank() ? "^[a-zA-Z0-9_.:/-]+$" : pattern) + "(?![\\s\\S])");
        return result;
    }

    private static JsonObject enumeration(String... values) {
        JsonObject result = text("");
        JsonArray options = new JsonArray();
        for (String value : values) options.add(value);
        result.add("enum", options);
        return result;
    }

    static JsonObject array(JsonObject items, int minimum) {
        JsonObject result = type("array");
        result.add("items", items);
        result.addProperty("minItems", minimum);
        result.addProperty("maxItems", 1024);
        return result;
    }
}
