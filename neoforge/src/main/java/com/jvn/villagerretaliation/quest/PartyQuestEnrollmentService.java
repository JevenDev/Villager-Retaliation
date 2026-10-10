package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartySharedQuestRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

final class PartyQuestEnrollmentService {
    @FunctionalInterface
    interface StartEligibility {
        boolean canStart(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress);
    }

    @FunctionalInterface
    interface MemberStartEffects {
        void apply(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress,
                VillagerQuestTargets.LocatedTarget target);
    }

    private final StartEligibility eligibility;
    private final BiConsumer<QuestDefinition, VillagerQuestSavedData.QuestProgress> prepareState;
    private final MemberStartEffects startEffects;
    private final Consumer<ServerPlayer> tracker;

    PartyQuestEnrollmentService(StartEligibility eligibility,
            BiConsumer<QuestDefinition, VillagerQuestSavedData.QuestProgress> prepareState,
            MemberStartEffects startEffects, Consumer<ServerPlayer> tracker) {
        this.eligibility = eligibility;
        this.prepareState = prepareState;
        this.startEffects = startEffects;
        this.tracker = tracker;
    }

    StartPlan preparePartyStart(DialogueContext context, QuestDefinition definition) {
        if (!PartyQuestService.isShareable(definition)) {
            return null;
        }
        PartyRecord party =
                PartyService
                        .getPartyForPlayer(context.level(), context.player().getUUID())
                        .orElse(null);
        if (party == null) {
            return null;
        }
        Optional<PartySharedQuestRecord> existing =
                PartyQuestService.findCompatible(party, definition, context.providerId());
        PartySharedQuestRecord shared = existing.orElseGet(
                () -> PartyQuestService.getOrCreate(
                        context.level(), party, definition, context.providerId()));
        return new StartPlan(party, shared, existing.isEmpty());
    }

    void shareStartedQuest(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress started,
            StartPlan partyStart) {
        if (partyStart == null) {
            return;
        }
        PartyRecord party = partyStart.party();
        PartySharedQuestRecord shared = partyStart.shared();
        shared.enroll(context.player().getUUID(), false);
        PartyQuestService.mergePersonalProgress(shared, definition, started);
        PartyQuestService.syncPersonalProgress(shared, definition, started);
        if (!partyStart.created()) {
            syncSharedQuestEnrollmentSnapshots(context.level(), shared, definition);
            PartyService.markChanged(context.level());
            context.player().sendSystemMessage(Component.translatable("villagerretaliation.party.quest_shared"));
            return;
        }
        for (UUID playerId : party.playerIds()) {
            if (playerId.equals(context.player().getUUID())) {
                continue;
            }
            ServerPlayer member = context.level().getServer().getPlayerList().getPlayer(playerId);
            if (member == null || member.serverLevel() != context.level()) {
                shared.enroll(playerId, true);
                continue;
            }
            startOrLinkPartyMemberQuest(member, context.villager(), definition, started, shared);
        }
        syncSharedQuestEnrollmentSnapshots(context.level(), shared, definition);
        PartyService.markChanged(context.level());
        context.player().sendSystemMessage(Component.translatable("villagerretaliation.party.quest_shared"));
    }

    void linkExistingActivePartyQuest(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!PartyQuestService.isShareable(definition)) {
            return;
        }
        PartyRecord party =
                PartyService
                        .getPartyForPlayer(context.level(), context.player().getUUID())
                        .orElse(null);
        if (party == null) {
            return;
        }
        PartySharedQuestRecord shared =
                PartyQuestService.findCompatible(party, definition, progress.startedVillagerId()).orElse(null);
        if (shared == null || shared.linked(context.player().getUUID())) {
            return;
        }
        if (progress.questRunId() != null && !progress.questRunId().equals(shared.instanceId())) {
            return;
        }
        shared.enroll(context.player().getUUID(), false);
        if (!progress.linkPartyQuest(shared.instanceId())) {
            shared.removeEnrollment(context.player().getUUID());
            return;
        }
        PartyQuestService.mergePersonalProgress(shared, definition, progress);
        syncSharedQuestEnrollmentSnapshots(context.level(), shared, definition);
        PartyService.markChanged(context.level());
        this.tracker.accept(context.player());
        context.player().sendSystemMessage(Component.translatable("villagerretaliation.party.quest_shared"));
    }

    private void syncSharedQuestEnrollmentSnapshots(
            ServerLevel level,
            PartySharedQuestRecord shared,
            QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        for (PartySharedQuestRecord.Enrollment enrollment
                : shared.enrollments().values()) {
            if (enrollment.pendingStart()) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = data.get(enrollment.playerId(), definition.id());
            if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
                continue;
            }
            PartyQuestService.syncPersonalProgress(shared, definition, progress);
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(enrollment.playerId());
            if (online != null) {
                this.tracker.accept(online);
            }
        }
        data.setDirty();
    }

    private boolean startOrLinkPartyMemberQuest(
            ServerPlayer member,
            Villager provider,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress sourceProgress,
            PartySharedQuestRecord shared) {
        if (member == null || provider != null && member.serverLevel() != provider.level()) {
            return false;
        }
        ServerLevel level = member.serverLevel();
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress existing = data.get(member.getUUID(), definition.id());
        DialogueContext memberContext = provider == null ? DialogueContext.forPlayer(member)
                : VillagerInteractionService.createDialogueContext(level, member, provider);
        if (existing != null && existing.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            if (!QuestAvailabilityService.matchesProviderLock(memberContext, definition, existing)) {
                shared.removeEnrollment(member.getUUID());
                return false;
            }
            if (existing.questRunId() != null && !existing.questRunId().equals(shared.instanceId())) {
                shared.removeEnrollment(member.getUUID());
                return false;
            }
            shared.enroll(member.getUUID(), false);
            if (!existing.linkPartyQuest(shared.instanceId())) {
                shared.removeEnrollment(member.getUUID());
                return false;
            }
            PartyQuestService.mergePersonalProgress(shared, definition, existing);
            PartyQuestService.syncPersonalProgress(shared, definition, existing);
            data.setDirty();
            this.tracker.accept(member);
            return true;
        }
        if (!this.eligibility.canStart(memberContext, definition, existing)) {
            shared.removeEnrollment(member.getUUID());
            return false;
        }

        VillagerQuestSavedData.QuestProgress linked = data.getOrCreate(member.getUUID(), definition.id());
        QuestProviderBinding providerBinding = VillagerQuestProviderType.INSTANCE.bindingFromDialogueContext(memberContext);
        VillagerQuestTargets.LocatedTarget target = sourceProgress.targetPos() == null
                ? null
                : new VillagerQuestTargets.LocatedTarget(
                        sourceProgress.targetDimension() == null ? level.dimension() : sourceProgress.targetDimension(),
                        sourceProgress.targetPos(),
                        sourceProgress.targetObjectiveId());
        QuestLifecycleService.start(definition.id(), linked, providerBinding, target,
                level.getGameTime(), member.getUUID(), shared.instanceId());
        linked.adoptDefinitionRevision(definition.revision().number());
        this.prepareState.accept(definition, linked);
        shared.enroll(member.getUUID(), false);
        linked.linkPartyQuest(shared.instanceId());
        data.setDirty();
        this.startEffects.apply(memberContext, definition, linked, target);
        PartyQuestService.syncPersonalProgress(shared, definition, linked);
        data.setDirty();
        this.tracker.accept(member);
        member.sendSystemMessage(Component.translatable("villagerretaliation.party.quest_shared"));
        return true;
    }

    record StartPlan(
            PartyRecord party,
            PartySharedQuestRecord shared,
            boolean created) {
    }

    void attachPendingPartyQuests(ServerPlayer player, boolean deliverRewards) {
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PartyRecord party =
                PartyService
                        .getPartyForPlayer(level, player.getUUID())
                        .orElse(null);
        if (party == null) {
            return;
        }
        for (PartySharedQuestRecord shared : party.sharedQuests()) {
            PartySharedQuestRecord.Enrollment enrollment =
                    shared.enrollment(player.getUUID());
            if (enrollment == null || !enrollment.pendingStart()) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources.quest(player.getServer(), shared.questId()).orElse(null);
            Villager provider = VillagerEntityResolver.loaded(player.getServer(), shared.sourceVillagerId());
            VillagerQuestSavedData.QuestProgress source = canonicalSharedProgress(level, shared);
            if (definition == null || provider == null && shared.sourceVillagerId() != null || provider != null && provider.level() != level || source == null) {
                continue;
            }
            boolean linked = startOrLinkPartyMemberQuest(player, provider, definition, source, shared);
            if (linked && shared.completed()) {
                VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
                VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), shared.questId());
                if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
                    PartyQuestService.syncPersonalProgress(shared, definition, progress);
                    QuestLifecycleService.complete(
                            definition.id(),
                            progress,
                            level.getGameTime(),
                            definition.rules().consumeOnCompletion());
                    PartySharedQuestRecord.Enrollment linkedEnrollment =
                            shared.enrollment(player.getUUID());
                    if (linkedEnrollment != null) {
                        linkedEnrollment.markPendingReward();
                    }
                    progress.markPendingPartyReward();
                    data.setDirty();
                }
            }
            PartyService.markChanged(level);
        }
        if (deliverRewards) PartyQuestRewardService.deliverPendingPartyRewards(player);
    }

    static VillagerQuestSavedData.QuestProgress canonicalSharedProgress(
            ServerLevel level,
            PartySharedQuestRecord shared) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress completed = null;
        for (PartySharedQuestRecord.Enrollment enrollment
                : shared.enrollments().values()) {
            VillagerQuestSavedData.QuestProgress progress = data.get(enrollment.playerId(), shared.questId());
            if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
                return progress;
            }
            if (progress != null && progress.state() != VillagerQuestSavedData.QuestState.NOT_STARTED) {
                completed = progress;
            }
        }
        return completed;
    }

}
