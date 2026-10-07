package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class CampaignFactService {
    private CampaignFactService() {
    }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || action.questId() == null || action.campaignFact() == null) {
            return ActionResult.failed("Campaign fact action is missing its quest or typed fields.", capabilities);
        }
        var quest = VillagerQuestResources.quest(context.player().server, action.questId()).orElse(null);
        if (quest == null || quest.campaign() == null) return ActionResult.failed("Campaign fact actions must belong to a campaign quest.", capabilities);
        String issue = CampaignQuestAccess.executionIssue(context, quest);
        if (issue.isBlank()) issue = CampaignAuthority.controlIssue(context.player());
        if (!issue.isBlank()) return ActionResult.unavailable(issue, capabilities, VillagerActionResult.EMPTY);
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        var data = CampaignSavedData.get(context.level());
        var instance = data.instances().get(quest.campaign());
        if (progress == null || progress.questRunId() == null || instance == null) {
            return ActionResult.unavailable("Start the campaign quest before changing its story facts.", capabilities, VillagerActionResult.EMPTY);
        }
        if (!QuestAvailabilityService.matchesProviderLock(context, quest, progress)) {
            return ActionResult.unavailable("Return to this quest's assigned character to change its story facts.", capabilities, VillagerActionResult.EMPTY);
        }
        var content = CampaignResources.current(context.player().server).campaigns().get(quest.campaign());
        var change = action.campaignFact();
        var fact = content.definition().facts().get(change.fact());
        if (fact == null) return ActionResult.failed("Unknown campaign fact: " + change.fact(), capabilities);
        boolean shared = fact.scope() == CampaignDefinition.FactScope.WORLD;
        if (shared) {
            var milestone = content.definition().milestones().get(change.milestone());
            if (milestone == null || !milestone.irreversible() || !milestone.quest().equals(quest.id())) {
                return ActionResult.failed("Shared fact actions must name an irreversible milestone owned by this quest.", capabilities);
            }
            var player = instance.players().get(context.player().getUUID());
            var entered = player == null ? null : player.milestones().get(milestone.id());
            if (entered == null || entered.route() != CampaignInstance.Route.ORIGINAL) {
                return ActionResult.unavailable("Reach this milestone's original path before changing shared facts. Catch-up paths use personal facts.", capabilities, VillagerActionResult.EMPTY);
            }
        }
        UUID operation = operationId(instance.instanceId(), context.player().getUUID(), quest.id(), progress.questRunId(), change, shared);
        long revision = shared ? instance.worldRevision()
                : instance.players().getOrDefault(context.player().getUUID(), CampaignInstance.PlayerRecord.empty()).revision();
        try {
            var result = data.commitFact(content.definition(), context.player().getUUID(), change.fact(), change.mutation(),
                    change.value(), operation, revision, context.level().getGameTime());
            var legacy = new VillagerActionResult(result.status() == CampaignSavedData.Status.APPLIED, "", "", Map.of(
                    "campaign_fact", change.fact(), "campaign_fact_value", result.value() == null ? "" : result.value().text(),
                    "campaign_operation", operation.toString()), false);
            return switch (result.status()) {
                case APPLIED -> ActionResult.success(legacy, capabilities);
                case ALREADY_SATISFIED -> ActionResult.alreadySatisfied("Campaign fact operation already applied", capabilities, legacy);
                case INVALID_REQUEST -> ActionResult.failed(result.diagnostic(), capabilities);
                default -> ActionResult.unavailable(result.diagnostic(), capabilities, legacy);
            };
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not save story fact {} for campaign {}", change.fact(), quest.campaign(), exception);
            return ActionResult.failed("The story fact could not be saved. Check world storage before retrying.", capabilities);
        }
    }
    static UUID operationId(UUID instance, UUID player, net.minecraft.resources.ResourceLocation quest, UUID run,
            CampaignFactAction change, boolean shared) {
        String owner = shared ? String.join("|", "world", quest.toString(), change.milestone())
                : String.join("|", "player", quest.toString(), player.toString(), run.toString());
        return UUID.nameUUIDFromBytes((instance + "|fact|" + owner + "|" + change.operationId()).getBytes(StandardCharsets.UTF_8));
    }

}
