package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignCharacterPredicate(ResourceLocation campaign, String role, State state) {
    public CampaignCharacterPredicate {
        if (campaign == null || !CampaignFactFields.validId(role) || role.length() > 256 || state == null) {
            throw new IllegalArgumentException("Invalid campaign character condition.");
        }
    }

    public static JsonObject schema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("description", "Checks a campaign role and its saved replacements without casting or spawning.");
        schema.addProperty("additionalProperties", false);
        var fields = new JsonObject();
        var type = new JsonObject(); type.addProperty("const", "campaign_character");
        fields.add("type", type);
        fields.add("campaign", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
        fields.add("role", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
        fields.add("state", CampaignFieldSchema.enumeration("bound", "available", "downed", "lost", "speaker", "replaced"));
        var required = new JsonArray();
        for (String field : new String[]{"campaign", "role", "state"}) required.add(field);
        schema.add("required", required); schema.add("properties", fields);
        return schema;
    }

    public static Map<String, String> issues(JsonObject object) { return CampaignFieldSchema.issues(object, schema(), "campaign character condition"); }

    public static CampaignCharacterPredicate read(JsonObject object) {
        if (!issues(object).isEmpty()) return null;
        return new CampaignCharacterPredicate(ResourceLocation.parse(object.get("campaign").getAsString()), object.get("role").getAsString(),
                State.valueOf(object.get("state").getAsString().toUpperCase(Locale.ROOT)));
    }

    public Evaluation evaluate(DialogueContext context) {
        if (context == null || context.level() == null) return new Evaluation(false, "Campaign character context is unavailable.");
        var result = state == State.REPLACED ? CampaignBindingQueries.originalCharacter(context.level().getServer(), campaign, role)
                : CampaignBindingQueries.character(context.level().getServer(), campaign, role);
        if (!result.diagnostic().isBlank()) return new Evaluation(false, result.diagnostic());
        boolean matches = switch (state) {
            case BOUND -> result.binding() != null;
            case AVAILABLE -> result.available();
            case DOWNED -> result.entity() != null && VillagerDownedService.isDowned(result.entity());
            case LOST -> result.binding() != null && result.binding().lost();
            case SPEAKER -> result.available() && result.entity() == context.villager();
            case REPLACED -> result.binding() != null && java.util.Optional.ofNullable(CampaignCharacterSavedData.get(context.level())
                    .replacements().get(result.binding().key())).map(CampaignCharacterReplacement::committed).orElse(false);
        };
        return new Evaluation(matches, "");
    }

    public enum State { BOUND, AVAILABLE, DOWNED, LOST, SPEAKER, REPLACED }
    public record Evaluation(boolean matches, String diagnostic) { }
}
