package com.jvn.villagerretaliation.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

public final class AdvancementGrant {
    private AdvancementGrant() { }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","required":["advancement"],"properties":{
                 "advancement":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"},
                 "criterion":{"type":"string","maxLength":256},"questline":{"type":"string","minLength":1,"maxLength":256}}}
                """).getAsJsonObject();
    }

    public static ActionResult execute(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || context.player() == null) return ActionResult.failed("A player is required", capabilities);
        var data = action.parameters();
        var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(data, schema());
        if (!issues.isEmpty()) return ActionResult.failed(issues.getFirst().message(), capabilities);
        if (data.has("questline") && !questlineComplete(context, data.get("questline").getAsString()))
            return ActionResult.alreadySatisfied("Waiting for all quests in the questline", capabilities, VillagerActionResult.success());
        var id = ResourceLocation.tryParse(data.get("advancement").getAsString());
        var holder = id == null ? null : context.level().getServer().getAdvancements().get(id);
        if (holder == null) return ActionResult.failed("Unknown advancement: " + id, capabilities);
        var criteria = holder.value().criteria().keySet();
        String criterion = data.has("criterion") ? data.get("criterion").getAsString() : "";
        if (!criterion.isBlank() && !criteria.contains(criterion))
            return ActionResult.failed("Unknown advancement criterion: " + criterion, capabilities);
        boolean changed = false;
        if (criterion.isBlank()) {
            for (String name : criteria) changed |= context.player().getAdvancements().award(holder, name);
        } else changed = context.player().getAdvancements().award(holder, criterion);
        return changed ? ActionResult.success(VillagerActionResult.success(), capabilities)
                : ActionResult.alreadySatisfied("Advancement already granted", capabilities, VillagerActionResult.success());
    }

    public static boolean questlineComplete(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context, String questline) {
        var quests = com.jvn.villagerretaliation.quest.VillagerQuestResources.quests(context.level().getServer()).stream()
                .filter(quest -> quest.questline().equals(questline)).toList();
        var data = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(context.level());
        return !quests.isEmpty() && quests.stream().allMatch(quest -> {
            var progress = data.get(context.player().getUUID(), quest.id());
            return progress != null && progress.completionCount() > 0;
        });
    }

    public static void onQuestCompleted(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context) {
        var data = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(context.level());
        for (var quest : com.jvn.villagerretaliation.quest.VillagerQuestResources.quests(context.level().getServer())) {
            var owner = data.get(context.player().getUUID(), quest.id());
            if (owner == null || owner.completionCount() == 0) continue;
            for (var trigger : quest.triggers()) {
                if (trigger.event() != com.jvn.villagerretaliation.quest.QuestDefinition.TriggerEvent.COMPLETED
                        || !trigger.conditions().isEmpty()) continue;
                for (var action : trigger.actions()) {
                    if (action.kind() != VillagerActionDefinition.Kind.ADVANCEMENT || !action.parameters().has("questline")) continue;
                    var result = execute(context, action);
                    if (!result.success()) com.mojang.logging.LogUtils.getLogger().warn("Questline advancement failed: {}", result.message());
                }
            }
        }
    }

    public static JsonObject resourceSchema() {
        return JsonParser.parseString("""
                {"type":"object","required":["display","criteria"],"properties":{
                 "parent":{"type":"string"},
                 "display":{"type":"object","required":["title","description","icon"],"properties":{
                   "title":{"type":["string","object","array"]},"description":{"type":["string","object","array"]},
                   "icon":{"type":"object","properties":{"id":{"type":"string"},"count":{"type":"integer","minimum":1}}},
                   "frame":{"enum":["task","goal","challenge"]},"background":{"type":"string"},
                   "show_toast":{"type":"boolean"},"announce_to_chat":{"type":"boolean"},"hidden":{"type":"boolean"}}},
                 "criteria":{"type":"object"},"requirements":{"type":"array","items":{"type":"array","items":{"type":"string"}}},
                 "rewards":{"type":"object"},"sends_telemetry_event":{"type":"boolean"}}}
                """).getAsJsonObject();
    }
}
