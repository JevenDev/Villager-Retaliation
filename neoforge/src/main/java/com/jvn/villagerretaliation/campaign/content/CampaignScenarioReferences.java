package com.jvn.villagerretaliation.campaign.content;

import static com.jvn.villagerretaliation.campaign.content.CampaignScenario.*;
import static com.jvn.villagerretaliation.campaign.content.CampaignScenarioParser.issue;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class CampaignScenarioReferences {
    private CampaignScenarioReferences() { }

    static List<QuestDiagnostic> validate(CampaignScenario scenario, CampaignContentCatalog catalog) {
        var issues = new ArrayList<QuestDiagnostic>();
        var content = catalog.campaigns().get(scenario.campaign());
        if (content == null) {
            issue(scenario, issues, "/campaign", "scenario campaign is unavailable");
            return issues;
        }
        var campaign = content.definition();
        for (int index = 0; index < scenario.actors().size(); index++) {
            var actor = scenario.actors().get(index);
            if (actor.role().isBlank()) continue;
            var role = campaign.characters().get(actor.role());
            if (role == null || role.source() != CampaignCharacterDefinition.Source.EXISTING_VILLAGER)
                issue(scenario, issues, "/actors/" + index + "/role", "scenario actor requires an existing-villager campaign role");
        }
        for (int index = 0; index < scenario.locations().size(); index++) {
            var location = campaign.locations().get(scenario.locations().get(index).alias());
            if (location == null || !location.dimension().toString().equals("minecraft:overworld"))
                issue(scenario, issues, "/locations/" + index + "/alias", "scenario location must name an overworld campaign location");
        }
        var sessions = new LinkedHashMap<String, ConversationDefinition>();
        var prompts = new LinkedHashMap<String, QuestPrompt>();
        for (var step : scenario.steps()) {
            String at = step.pointer();
            QuestResponse response = step instanceof QuestResponse value ? value : null;
            if (step instanceof QuestPrompt prompt) {
                prompts.put(prompt.id(), prompt);
                response = new QuestResponse(prompt.id(), prompt.player(), prompt.actor(), prompt.quest(), prompt.stage(), prompt.slot(), "", false, prompt.pointer());
            } else if (step instanceof QuestSelect select) {
                var prompt = prompts.get(select.prompt());
                if (prompt != null) response = new QuestResponse(select.id(), prompt.player(), prompt.actor(), prompt.quest(), prompt.stage(), prompt.slot(), select.response(), select.confirm(), select.pointer());
            }
            if (response != null) {
                var quest = catalog.quests().compiledQuest(response.quest()).orElse(null);
                if (quest == null || !campaign.questBundles().contains(response.quest())) {
                    issue(scenario, issues, at + "/quest", "quest does not belong to this campaign");
                    continue;
                }
                var selected = response;
                var binding = catalog.quests().dialogueCatalog().bindings(response.quest()).stream()
                        .filter(value -> value.stageId().equals(selected.stage()) && value.slot().equals(selected.slot())).findFirst().orElse(null);
                var tree = binding == null ? null : catalog.quests().dialogueCatalog().trees().get(binding.treeId());
                var entry = tree == null ? null : tree.entry(binding.entryId()).orElse(null);
                var node = entry == null ? null : tree.nodes().get(entry.start());
                if (node == null) issue(scenario, issues, at + "/slot", "quest dialogue binding is unavailable");
                else if (!selected.response().isBlank() && node.responses().stream().noneMatch(value -> value.id().equals(selected.response())))
                    issue(scenario, issues, at + "/response", "unknown authored quest response");
            } else if (step instanceof AssertQuest assertion) {
                var quest = catalog.quests().compiledQuest(assertion.quest()).orElse(null);
                if (quest == null || !campaign.questBundles().contains(assertion.quest()))
                    issue(scenario, issues, at + "/quest", "quest does not belong to this campaign");
                else if (!assertion.stage().isBlank() && !quest.stagesById().containsKey(assertion.stage()))
                    issue(scenario, issues, at + "/stage", "unknown quest stage");
            } else if (step instanceof StartConversation start) {
                var conversation = catalog.quests().conversations().get(start.conversation());
                if (conversation == null || !conversation.campaign().equals(campaign.id()))
                    issue(scenario, issues, at + "/conversation", "conversation does not belong to this campaign");
                else {
                    sessions.put(start.session(), conversation);
                    if (!conversation.topics().containsKey(start.topic())) issue(scenario, issues, at + "/topic", "unknown conversation topic");
                }
            } else if (step instanceof Fixture fixture && !campaign.fixtureEntries().containsKey(fixture.entry()))
                issue(scenario, issues, at + "/entry", "unknown campaign fixture entry");
            else if (step instanceof AssertOutcome assertion) {
                var milestone = campaign.milestones().get(assertion.milestone());
                if (milestone == null || !milestone.irreversible()) issue(scenario, issues, at + "/milestone", "unknown irreversible campaign milestone");
                else if (!milestone.outcomes().containsKey(assertion.outcome())) issue(scenario, issues, at + "/outcome", "unknown milestone outcome");
            } else if (step instanceof AssertPlayer assertion && assertion.participation() != null) {
                var found = new java.util.HashSet<String>();
                for (int index = 0; index < assertion.participation().size(); index++) {
                    String id = assertion.participation().get(index);
                    var milestone = campaign.milestones().get(id);
                    if (milestone == null || !milestone.irreversible() || !found.add(id))
                        issue(scenario, issues, at + "/participation/" + index, "participation must name distinct irreversible milestones");
                }
            } else if (step instanceof WorldEvent observation && campaign.milestones().values().stream()
                    .noneMatch(milestone -> milestone.observation() != null && milestone.observation().event().equals(observation.event())))
                issue(scenario, issues, at + "/event", "campaign does not observe this world event");
            var conversation = sessions.get(CampaignScenarioParser.sessionReference(step));
            if (conversation == null) continue;
            if (step instanceof ConversationIntent intent && intent.intent() == Intent.CHOOSE
                    && conversation.nodes().values().stream().noneMatch(node -> node.responses().containsKey(intent.argument())))
                issue(scenario, issues, at + "/argument", "unknown conversation response");
            else if (step instanceof AssertCheck assertion && !conversation.checks().containsKey(assertion.check()))
                issue(scenario, issues, at + "/check", "unknown conversation check");
            else if (step instanceof AssertConversation assertion) {
                if (!assertion.node().isBlank() && !conversation.nodes().containsKey(assertion.node())) issue(scenario, issues, at + "/node", "unknown conversation node");
                if (!assertion.outcome().isBlank() && !conversation.outcomes().containsKey(assertion.outcome())) issue(scenario, issues, at + "/outcome", "unknown conversation outcome");
            }
        }
        return List.copyOf(issues);
    }
}
