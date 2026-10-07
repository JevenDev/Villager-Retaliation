package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.content.CampaignRewardDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CampaignRewardService {
    private static final Map<UUID, String> REPORTED = new HashMap<>();
    private static int recoveryCursor;

    private CampaignRewardService() {
    }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        if (context == null || action.questId() == null || action.campaignReward() == null) {
            return ActionResult.failed("Campaign reward action is missing its quest or typed fields.", capabilities);
        }
        var quest = VillagerQuestResources.quest(context.player().server, action.questId()).orElse(null);
        if (quest == null || quest.campaign() == null) return ActionResult.failed("Campaign rewards must belong to a campaign quest.", capabilities);
        String issue = CampaignQuestAccess.executionIssue(context, quest);
        if (issue.isBlank()) issue = CampaignAuthority.controlIssue(context.player());
        if (!issue.isBlank()) return ActionResult.unavailable(issue, capabilities, VillagerActionResult.EMPTY);
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        var instance = CampaignSavedData.get(context.level()).instances().get(quest.campaign());
        if (progress == null || progress.questRunId() == null || instance == null) {
            return ActionResult.unavailable("Start this campaign quest before receiving its reward.", capabilities, VillagerActionResult.EMPTY);
        }
        if (!QuestAvailabilityService.matchesProviderLock(context, quest, progress)) {
            return ActionResult.unavailable("Return to this quest's assigned character to receive its reward.", capabilities, VillagerActionResult.EMPTY);
        }
        var definition = CampaignResources.current(context.player().server).campaigns().get(quest.campaign()).definition();
        var request = action.campaignReward();
        var reward = definition.rewards().get(request.reward());
        if (reward == null || !reward.quest().equals(quest.id())) return ActionResult.failed("Unknown reward for this campaign quest: " + request.reward(), capabilities);
        issue = eligibility(instance, context.player().getUUID(), reward);
        if (!issue.isBlank()) return ActionResult.unavailable(issue, capabilities, VillagerActionResult.EMPTY);
        var data = CampaignRewardSavedData.get(context.level());
        if (!data.diagnostic().isBlank()) return ActionResult.unavailable(data.diagnostic(), capabilities, VillagerActionResult.EMPTY);
        UUID operation = operationId(instance.instanceId(), context.player().getUUID(), reward, request.operationId());
        try {
            var proposal = new CampaignRewardReceipt(operation, UUID.randomUUID(), definition.id(), instance.instanceId(),
                    definition.revision(), definition.fingerprint(), context.player().getUUID(), quest.id(), progress.questRunId(),
                    reward.id(), request.operationId(), reward.scope(), reward.milestone(), reward.experience(), List.of(),
                    Math.max(0L, context.level().getGameTime()), false);
            var receipt = data.receipts().get(operation);
            if (receipt != null) {
                if (!receipt.sameIntent(proposal)) return ActionResult.failed("This campaign reward operation already belongs to another grant.", capabilities);
            } else {
                if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) return ActionResult.unavailable("This campaign quest is no longer active.", capabilities, VillagerActionResult.EMPTY);
                List<CompoundTag> items = List.of();
                if (reward.lootTable() != null) {
                    var rolled = QuestRewardResolver.roll(context.level(), context.player().getLuck(), reward.lootTable(), context.player().getRandom());
                    if (!rolled.resolution().resolved()) return ActionResult.unavailable(rolled.resolution().diagnostic(), capabilities, VillagerActionResult.EMPTY);
                    items = rolled.items().stream().filter(item -> !item.isEmpty()).map(item -> (CompoundTag) item.save(context.player().registryAccess())).toList();
                }
                receipt = data.prepare(new CampaignRewardReceipt(operation, proposal.token(), definition.id(), instance.instanceId(),
                        definition.revision(), definition.fingerprint(), context.player().getUUID(), quest.id(), progress.questRunId(),
                        reward.id(), request.operationId(), reward.scope(), reward.milestone(), reward.experience(), items, proposal.preparedAt(), false));
            }
            var output = new VillagerActionResult(false, "", "", Map.of("campaign_reward", receipt.reward(),
                    "campaign_reward_operation", operation.toString(), "campaign_reward_recipient", receipt.player().toString()), false);
            if (!receipt.player().equals(context.player().getUUID())) {
                return receipt.delivered() ? ActionResult.alreadySatisfied("The shared reward has already been delivered.", capabilities, output)
                        : ActionResult.unavailable("The shared reward is reserved for its first recipient and is awaiting delivery.", capabilities, output);
            }
            boolean delivered = deliver(context.player(), data, receipt, CampaignPlayerRewards.directory(context.player()));
            return delivered ? ActionResult.success(new VillagerActionResult(true, "", "", output.replacements(), false), capabilities)
                    : ActionResult.alreadySatisfied("Campaign reward already received.", capabilities, output);
        } catch (IllegalArgumentException exception) {
            return ActionResult.unavailable(exception.getMessage(), capabilities, VillagerActionResult.EMPTY);
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not save reward {} for campaign {}", reward.id(), definition.id(), exception);
            return ActionResult.failed("The campaign reward could not be saved. Check world storage before retrying.", capabilities);
        }
    }

    static String eligibility(CampaignInstance instance, UUID playerId, CampaignRewardDefinition reward) {
        if (reward.scope() == CampaignRewardDefinition.Scope.PERSONAL) return "";
        var outcome = instance.outcomes().get(reward.milestone());
        var player = instance.players().get(playerId);
        var progress = player == null ? null : player.milestones().get(reward.milestone());
        if (outcome == null || player == null || !player.participation().contains(reward.milestone())
                || progress == null || progress.route() != CampaignInstance.Route.ORIGINAL) {
            return "This reward requires participation in the original shared milestone. Catch-up paths have their own personal rewards.";
        }
        return reward.outcome().isBlank() || reward.outcome().equals(outcome.outcome()) ? "" : "This reward requires a different story outcome.";
    }

    static UUID operationId(UUID instance, UUID player, CampaignRewardDefinition reward, String operation) {
        String owner = reward.scope() == CampaignRewardDefinition.Scope.SHARED ? "world" : player.toString();
        return UUID.nameUUIDFromBytes(String.join("|", instance.toString(), "reward", reward.quest().toString(), owner, operation).getBytes(StandardCharsets.UTF_8));
    }

    static boolean deliver(ServerPlayer player, CampaignRewardSavedData data, CampaignRewardReceipt receipt, Path directory) throws IOException {
        boolean applied = CampaignPlayerRewards.deliver(player, receipt, directory);
        data.finish(receipt.operation(), receipt.token(), player.getUUID());
        return applied;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 100L == 0L) recover(event.getServer());
    }

    static void recover(MinecraftServer server) {
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) return;
        var data = CampaignRewardSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return;
        var pending = data.receipts().values().stream().filter(receipt -> !receipt.delivered()
                && server.getPlayerList().getPlayer(receipt.player()) != null).toList();
        if (pending.isEmpty()) return;
        int count = Math.min(16, pending.size());
        for (int index = 0; index < count; index++) {
            var receipt = pending.get(Math.floorMod(recoveryCursor, pending.size()));
            recoveryCursor = Math.floorMod(recoveryCursor + 1, pending.size());
            var player = server.getPlayerList().getPlayer(receipt.player());
            String issue = CampaignQuestAccess.contentIssue(server, receipt.quest(), receipt.campaign());
            var instance = CampaignSavedData.get(server.overworld()).instances().get(receipt.campaign());
            if (issue.isBlank() && (instance == null || !receipt.instance().equals(instance.instanceId())
                    || receipt.definitionRevision() != instance.definitionRevision() || !receipt.fingerprint().equals(instance.fingerprint()))) {
                issue = "The pending reward belongs to a different campaign instance or content revision. Restore matching content and saves.";
            }
            if (issue.isBlank()) {
                var reward = CampaignResources.current(server).campaigns().get(receipt.campaign()).definition().rewards().get(receipt.reward());
                if (reward == null || !reward.quest().equals(receipt.quest()) || reward.scope() != receipt.scope()
                        || reward.experience() != receipt.experience() || !reward.milestone().equals(receipt.milestone())) {
                    issue = "The saved reward no longer matches its campaign definition. Restore the original reward content.";
                }
            }
            if (issue.isBlank()) {
                try {
                    deliver(player, data, receipt, CampaignPlayerRewards.directory(player));
                    REPORTED.remove(receipt.operation());
                } catch (IOException | IllegalArgumentException exception) {
                    issue = exception.getMessage() == null ? "Campaign reward recovery failed. Check world storage and reward content." : exception.getMessage();
                }
            }
            if (!issue.isBlank() && !issue.equals(REPORTED.put(receipt.operation(), issue))) LogUtils.getLogger().warn("Campaign reward {}: {}", receipt.operation(), issue);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        REPORTED.clear();
        recoveryCursor = 0;
    }
}
