package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class CampaignStandingService {
    private CampaignStandingService() { }

    public static Reading read(MinecraftServer server, ResourceLocation campaign, UUID player, Scope scope, String subject) {
        if (server == null || !server.isSameThread() || player == null || scope == null)
            return Reading.unavailable("Campaign standing requires the server thread and a player.");
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN)
            return Reading.unavailable("Campaign standing requires Campaign Mode.");
        var content = CampaignResources.current(server).campaigns().get(campaign);
        if (content == null) return Reading.unavailable("Campaign content is missing or invalid: " + campaign);
        var data = CampaignSavedData.get(server.overworld());
        String issue = data.compatibility(content.definition());
        if (!issue.isBlank()) return Reading.unavailable(issue);
        var definition = CampaignStandingDefinition.find(content.definition(), scope, subject);
        if (definition == null) return Reading.unavailable("Unknown campaign " + scope.name().toLowerCase(java.util.Locale.ROOT) + " standing: " + subject);
        var standing = CampaignStandingSavedData.get(server.overworld());
        if (!standing.diagnostic().isBlank()) return Reading.unavailable(standing.diagnostic());
        var instance = data.instances().get(campaign);
        UUID character = null;
        if (scope == Scope.CHARACTER) {
            var binding = CampaignBindingQueries.character(server, campaign, subject);
            if (!binding.diagnostic().isBlank()) return Reading.unavailable(binding.diagnostic());
            if (binding.binding() == null) return Reading.unavailable("Bind campaign character " + subject + " before checking its personal relationship.");
            character = binding.binding().entityId();
        }
        if (instance == null) return new Reading(definition, null, definition.initial(), 0, definition.reaction(definition.initial()), "");
        var key = new CampaignStanding.Key(campaign, instance.instanceId(), player, scope, subject, character);
        var snapshot = new CampaignStanding.Subject(key, content.definition().revision(), content.definition().fingerprint(),
                content.localization().plainText("en_us", definition.titleKey()).orElse(definition.titleKey()),
                definition.minimum(), definition.maximum(), definition.initial());
        var saved = standing.values().get(key);
        if (saved != null && !saved.subject().compatible(snapshot))
            return Reading.unavailable("Saved standing requires its original campaign definition and bounds. Restore matching content and standing data.");
        int value = saved == null ? definition.initial() : saved.amount();
        return new Reading(definition, snapshot, value, saved == null ? 0 : saved.revision(), definition.reaction(value), "");
    }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || action.questId() == null || action.campaignStanding() == null)
            return ActionResult.failed("Campaign standing action is missing its quest or typed fields.", capabilities);
        var quest = VillagerQuestResources.quest(context.player().server, action.questId()).orElse(null);
        if (quest == null || quest.campaign() == null) return ActionResult.failed("Campaign standing actions must belong to a campaign quest.", capabilities);
        String issue = CampaignQuestAccess.executionIssue(context, quest);
        if (issue.isBlank()) issue = CampaignAuthority.controlIssue(context.player());
        if (!issue.isBlank()) return ActionResult.unavailable(issue, capabilities, VillagerActionResult.EMPTY);
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        var instance = CampaignSavedData.get(context.level()).instances().get(quest.campaign());
        if (progress == null || progress.questRunId() == null || instance == null)
            return ActionResult.unavailable("Start the campaign quest before changing personal standing.", capabilities, VillagerActionResult.EMPTY);
        if (!QuestAvailabilityService.matchesProviderLock(context, quest, progress))
            return ActionResult.unavailable("Return to this quest's assigned character to change personal standing.", capabilities, VillagerActionResult.EMPTY);
        var authored = action.campaignStanding();
        var change = new CampaignStanding.Change(quest.campaign(), instance.instanceId(), context.player().getUUID(), quest.id(),
                progress.questRunId(), authored.scope(), authored.subject(), authored.amount(), authored.operationId());
        var data = CampaignStandingSavedData.get(context.level());
        try {
            var prior = data.replay(change);
            CampaignStanding.Result result;
            if (prior != null) result = new CampaignStanding.Result(prior, false);
            else {
                var reading = read(context.player().server, quest.campaign(), context.player().getUUID(), authored.scope(), authored.subject());
                if (!reading.diagnostic().isBlank()) return ActionResult.unavailable(reading.diagnostic(), capabilities, VillagerActionResult.EMPTY);
                result = data.apply(change, reading.subject(), context.level().getGameTime());
            }
            var legacy = new VillagerActionResult(result.applied(), "", "", Map.of("campaign_standing", authored.subject(),
                    "campaign_standing_value", Integer.toString(result.receipt().after()), "campaign_operation", result.receipt().operation().toString()), false);
            return result.applied() ? ActionResult.success(legacy, capabilities)
                    : ActionResult.alreadySatisfied("Campaign standing change already applied", capabilities, legacy);
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not save campaign standing for {}", change.operation(), exception);
            return ActionResult.unavailable("Campaign standing could not be saved. Check world storage before retrying.", capabilities, VillagerActionResult.EMPTY);
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage(), capabilities);
        }
    }

    public record Reading(CampaignStandingDefinition definition, CampaignStanding.Subject subject, int value, long revision,
            CampaignStandingDefinition.Reaction reaction, String diagnostic) {
        static Reading unavailable(String issue) { return new Reading(null, null, 0, 0, null, issue); }
    }
}
