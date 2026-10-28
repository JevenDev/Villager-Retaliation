package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestRewardService;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

final class PartyQuestRewardService {
    private PartyQuestRewardService() { }

    static void completeSharedQuest(
            DialogueContext completingContext,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress completingProgress) {
        Optional<com.jvn.villagerretaliation.party.PartySharedQuestRecord> sharedOptional =
                PartyQuestService.sharedForPlayer(
                        completingContext.level(),
                        completingContext.player().getUUID(),
                        definition.id());
        if (sharedOptional.isEmpty()) {
            return;
        }
        com.jvn.villagerretaliation.party.PartySharedQuestRecord shared = sharedOptional.get();
        com.jvn.villagerretaliation.party.PartyRecord party =
                com.jvn.villagerretaliation.party.PartyService
                        .getPartyForPlayer(completingContext.level(), completingContext.player().getUUID())
                        .orElse(null);
        shared.markCompleted();
        if (party != null && party.removeTrackedQuest(definition.id())) {
            PartyService.markChanged(completingContext.level());
        }
        com.jvn.villagerretaliation.party.PartySharedQuestRecord.Enrollment completingEnrollment =
                shared.enrollment(completingContext.player().getUUID());
        if (completingEnrollment != null) {
            completingEnrollment.markRewardClaimed();
        }
        completingProgress.markPartyRewardClaimed();
        PartyQuestService.mergePersonalProgress(shared, definition, completingProgress);

        VillagerQuestSavedData data = VillagerQuestSavedData.get(completingContext.level());
        for (com.jvn.villagerretaliation.party.PartySharedQuestRecord.Enrollment enrollment
                : shared.enrollments().values()) {
            if (enrollment.playerId().equals(completingContext.player().getUUID())) {
                continue;
            }
            if (enrollment.pendingStart()) {
                enrollment.markPendingReward();
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = data.get(enrollment.playerId(), definition.id());
            if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
                if (progress != null && progress.completionCount() > 0) {
                    enrollment.markRewardClaimed();
                }
                continue;
            }
            PartyQuestService.syncPersonalProgress(shared, definition, progress);
            progress.performance(completingProgress.performance());
            if (completingProgress.performance().resolvedRewards() != null)
                progress.recordRewardDelivery(new com.jvn.villagerretaliation.quest.runtime.QuestRewardDelivery(
                        completingProgress.performance().resolvedRewards(), 0));
            QuestLifecycleService.complete(
                    definition.id(),
                    progress,
                    completingContext.level().getGameTime(),
                    definition.rules().consumeOnCompletion());
            enrollment.markPendingReward();
            progress.markPendingPartyReward();
            data.setDirty();
            PartyService.markChanged(completingContext.level());
            SceneLifecycleIntegration.onQuestTerminal(completingContext.level(), enrollment.playerId(), definition.id(), "completed");
            ServerPlayer player = completingContext.level().getServer().getPlayerList().getPlayer(enrollment.playerId());
            DialogueContext context = pendingPartyRewardContext(player, progress);
            if (context == null) continue;
            finishLinkedPartyReward(context, definition, progress, enrollment);
        }
        if (party != null && shared.settled()) {
            party.removeSharedQuest(shared.instanceId());
        }
        data.setDirty();
        com.jvn.villagerretaliation.party.PartyService.markChanged(completingContext.level());
    }

    private static boolean finishLinkedPartyReward(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            com.jvn.villagerretaliation.party.PartySharedQuestRecord.Enrollment enrollment) {
        if (progress.partyRewardClaimed()) {
            if (enrollment != null && !enrollment.rewardClaimed()) {
                enrollment.markRewardClaimed();
            }
            return true;
        }
        if (enrollment != null && enrollment.rewardClaimed()) {
            progress.markPartyRewardClaimed();
            VillagerQuestSavedData.get(context.level()).setDirty();
            return true;
        }
        if (progress.rewardDelivery() == null
                && !QuestRewardService.unresolvedDiagnostic(context.level().getServer(), definition).isBlank()) return false;
        if (!VillagerQuestService.awardRewards(context, definition, progress).success()) return false;
        progress.markPartyRewardClaimed();
        if (enrollment != null) {
            enrollment.markRewardClaimed();
        }
        VillagerQuestService.onLinkedPartyRewardDelivered(context, definition, progress);
        return true;
    }

    static void deliverPendingPartyRewards(ServerPlayer player) {
        for (var entry : VillagerQuestSavedData.get(player.serverLevel()).progress(player.getUUID())) {
            deliverPendingPartyReward(player, entry.getKey(), entry.getValue());
        }
    }

    static void deliverPendingPartyReward(ServerPlayer player, ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!progress.pendingPartyReward() || progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) return;
        var party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(player.getServer(), questId).orElse(null);
        QuestDefinition definition = compiled == null ? null : compiled.asQuestDefinition();
        if (definition == null) {
            return;
        }
        DialogueContext context = pendingPartyRewardContext(player, progress);
        if (context == null) return;
        com.jvn.villagerretaliation.party.PartySharedQuestRecord shared = null;
        com.jvn.villagerretaliation.party.PartySharedQuestRecord.Enrollment enrollment = null;
        if (party != null && progress.partyQuestInstanceId() != null) {
            shared = party.sharedQuests().stream()
                    .filter(candidate -> candidate.instanceId().equals(progress.partyQuestInstanceId()))
                    .findFirst()
                    .orElse(null);
            enrollment = shared == null ? null : shared.enrollment(player.getUUID());
        }
        finishLinkedPartyReward(context, definition, progress, enrollment);
        if (shared != null && shared.settled()) {
            party.removeSharedQuest(shared.instanceId());
        }
        if (enrollment != null || shared != null) {
            com.jvn.villagerretaliation.party.PartyService.markChanged(player.serverLevel());
        }
    }

    static DialogueContext pendingPartyRewardContext(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        if (player == null) return null;
        if (progress.startedVillagerId() == null) return DialogueContext.forPlayer(player);
        Villager provider = pendingPartyRewardProvider(player, progress);
        return provider == null ? null : VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
    }

    static Villager pendingPartyRewardProvider(
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress) {
        Villager provider = VillagerEntityResolver.loaded(player.getServer(), progress.startedVillagerId());
        return provider != null && provider.isAlive() && provider.level() == player.serverLevel()
                ? provider
                : null;
    }

}
