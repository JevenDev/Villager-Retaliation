package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public sealed interface CampaignStoryObjective {
    QuestDefinition.ObjectiveType type();

    record Outcome(String milestone, String outcome, CampaignOutcomePredicate.Scope scope) implements CampaignStoryObjective {
        public Outcome {
            if (!CampaignFactFields.validId(milestone) || !CampaignFactFields.validId(outcome) || scope == null)
                throw new IllegalArgumentException("Story outcome objective requires a milestone, outcome and scope.");
        }
        @Override public QuestDefinition.ObjectiveType type() { return QuestDefinition.ObjectiveType.CAMPAIGN_OUTCOME; }
    }

    record Speak(String role, ResourceLocation conversation, String node) implements CampaignStoryObjective {
        public Speak {
            if (!CampaignFactFields.validId(role) || conversation == null || !CampaignFactFields.validId(node))
                throw new IllegalArgumentException("Speaking objective requires a role, conversation and node.");
        }
        @Override public QuestDefinition.ObjectiveType type() { return QuestDefinition.ObjectiveType.CAMPAIGN_SPEAK; }
    }

    record Investigate(String evidence) implements CampaignStoryObjective {
        public Investigate {
            if (!CampaignFactFields.validId(evidence)) throw new IllegalArgumentException("Investigation requires an authored evidence source.");
        }
        @Override public QuestDefinition.ObjectiveType type() { return QuestDefinition.ObjectiveType.CAMPAIGN_INVESTIGATE; }
    }

    record Follow(String role, String location, ResourceLocation scene, String step) implements CampaignStoryObjective {
        public Follow {
            if (!CampaignFactFields.validId(role) || !CampaignFactFields.validId(location) || scene == null || !CampaignFactFields.validId(step))
                throw new IllegalArgumentException("Following requires a role, destination, scene and step.");
        }
        @Override public QuestDefinition.ObjectiveType type() { return QuestDefinition.ObjectiveType.CAMPAIGN_FOLLOW; }
    }

    static CampaignStoryObjective read(QuestDefinition.ObjectiveType type, JsonObject objective) {
        if (type == QuestDefinition.ObjectiveType.CAMPAIGN_FOLLOW) {
            var story = objective.getAsJsonObject("story");
            return new Follow(story.get("role").getAsString(), story.get("location").getAsString(),
                    ResourceLocation.parse(story.get("scene").getAsString()), story.get("step").getAsString());
        }
        if (type == QuestDefinition.ObjectiveType.CAMPAIGN_INVESTIGATE) return new Investigate(objective.getAsJsonObject("story").get("evidence").getAsString());
        if (type != QuestDefinition.ObjectiveType.CAMPAIGN_OUTCOME && type != QuestDefinition.ObjectiveType.CAMPAIGN_SPEAK) return null;
        var story = objective.getAsJsonObject("story");
        return type == QuestDefinition.ObjectiveType.CAMPAIGN_OUTCOME
                ? new Outcome(story.get("milestone").getAsString(), story.get("outcome").getAsString(),
                        CampaignOutcomePredicate.Scope.valueOf(story.get("scope").getAsString().toUpperCase(Locale.ROOT)))
                : new Speak(story.get("role").getAsString(), ResourceLocation.parse(story.get("conversation").getAsString()),
                        story.get("node").getAsString());
    }

    static java.util.Map<String, String> referenceIssues(CampaignStoryObjective story,
            com.jvn.villagerretaliation.campaign.content.CampaignDefinition campaign,
            com.jvn.villagerretaliation.quest.content.QuestContentCatalog catalog, ResourceLocation quest) {
        var issues = new java.util.LinkedHashMap<String, String>();
        if (story instanceof Follow follow) {
            if (!campaign.characters().containsKey(follow.role())) issues.put("role", "Unknown campaign character: " + follow.role());
            if (!campaign.locations().containsKey(follow.location())) issues.put("location", "Unknown campaign destination: " + follow.location());
            var scene = catalog.scenes().get(follow.scene());
            if (scene == null) issues.put("scene", "Scene is missing or invalid: " + follow.scene());
            else {
                for (var bundle : catalog.bundles().values()) {
                    if (!bundle.owner().shared() && !quest.equals(bundle.questId()) && bundle.definitions()
                            .getOrDefault(com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath.Kind.SCENE, java.util.Map.of())
                            .containsKey(follow.scene())) issues.put("scene", "Private scene belongs to another quest bundle.");
                }
                var step = scene.steps().get(follow.step());
                if (step == null || step.follow() == null) issues.put("step", "Objective requires an authored follow_actor step.");
                else {
                    var guide = scene.actors().get(step.follow().actor());
                    var destination = scene.actors().get(step.follow().targetActor());
                    if (guide == null || !guide.bindingReference().equals(follow.role())) issues.put("role", "Follow step uses another character role.");
                    if (destination == null || !destination.bindingReference().equals(follow.location())) issues.put("location", "Follow step uses another destination.");
                }
            }
        } else if (story instanceof Investigate investigation) {
            if (!campaign.evidence().containsKey(investigation.evidence())) issues.put("evidence", "Unknown campaign evidence source: " + investigation.evidence());
        } else if (story instanceof Outcome outcome) {
            var milestone = campaign.milestones().get(outcome.milestone());
            if (milestone == null) issues.put("milestone", "Unknown campaign milestone: " + outcome.milestone());
            else {
                if (!milestone.outcomes().containsKey(outcome.outcome())) issues.put("outcome", "Unknown milestone outcome: " + outcome.outcome());
                if (!milestone.irreversible() && outcome.scope() != CampaignOutcomePredicate.Scope.COMPLETED)
                    issues.put("scope", "World outcomes, discovery and participation require an irreversible milestone.");
            }
        } else if (story instanceof Speak speak) {
            if (!campaign.characters().containsKey(speak.role())) issues.put("role", "Unknown campaign character: " + speak.role());
            var conversation = catalog.conversations().get(speak.conversation());
            if (conversation == null) issues.put("conversation", "Conversation is missing or invalid: " + speak.conversation());
            else {
                if (!conversation.campaign().equals(campaign.id())) issues.put("conversation", "Conversation belongs to another campaign.");
                if (!conversation.owner().shared() && !quest.equals(conversation.quest())) issues.put("conversation", "Private conversation belongs to another quest bundle.");
                var node = conversation.nodes().get(speak.node());
                if (node == null) issues.put("node", "Unknown conversation node: " + speak.node());
                else if (node.exchanges().stream().noneMatch(exchange -> exchange.speaker().kind()
                        == com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind.ROLE
                        && exchange.speaker().role().equals(speak.role()))) issues.put("role", "The authored character does not speak at this node.");
            }
        }
        return issues;
    }

    static JsonObject schema(QuestDefinition.ObjectiveType type) {
        var fields = new JsonObject();
        var required = new JsonArray();
        if (type == QuestDefinition.ObjectiveType.CAMPAIGN_FOLLOW) {
            for (String key : new String[]{"role", "location", "step"}) fields.add(key, CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("scene", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
            for (String key : new String[]{"role", "location", "scene", "step"}) required.add(key);
        } else if (type == QuestDefinition.ObjectiveType.CAMPAIGN_INVESTIGATE) {
            fields.add("evidence", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            required.add("evidence");
        } else if (type == QuestDefinition.ObjectiveType.CAMPAIGN_OUTCOME) {
            fields.add("milestone", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("outcome", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("scope", CampaignFieldSchema.enumeration("world", "discovered", "participated", "completed"));
            for (String key : new String[]{"milestone", "outcome", "scope"}) required.add(key);
        } else if (type == QuestDefinition.ObjectiveType.CAMPAIGN_SPEAK) {
            fields.add("role", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("conversation", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
            fields.add("node", CampaignFieldSchema.string(CampaignOutcomeAction.ID_PATTERN, 256));
            for (String key : new String[]{"role", "conversation", "node"}) required.add(key);
        } else throw new IllegalArgumentException("Unknown campaign story objective type.");
        var story = new JsonObject();
        story.addProperty("type", "object");
        story.addProperty("additionalProperties", false);
        story.add("properties", fields);
        story.add("required", required);
        var properties = new JsonObject();
        for (String key : new String[]{"id", "optional", "tracker", "conditions", "metadata", "ui"}) properties.add(key, new JsonObject());
        var kind = new JsonObject();
        kind.addProperty("const", type.name().toLowerCase(Locale.ROOT));
        properties.add("type", kind);
        properties.add("story", story);
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        schema.add("properties", properties);
        var requiredStory = new JsonArray();
        requiredStory.add("type");
        requiredStory.add("story");
        schema.add("required", requiredStory);
        return schema;
    }
}
