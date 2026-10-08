package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.InteractionRequestValidator;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.CampaignQuestConfirmationPayload;
import com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestStageBranchOptionIds;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class CampaignQuestAcceptanceService {
    private static final Map<MinecraftServer, Map<UUID, Request>> REQUESTS = new HashMap<>();
    private static final ThreadLocal<Approval> APPROVAL = new ThreadLocal<>();
    private static final long TIMEOUT_TICKS = 1200L;

    private CampaignQuestAcceptanceService() {
    }

    public static boolean beforeOption(DialogueContext context, DialogueOptionDefinition option) {
        var preview = DialogueTreeService.previewSelection(context, option);
        var actions = new ArrayList<>(preview.actions());
        var referenced = new LinkedHashSet<ResourceLocation>();
        var starts = new LinkedHashSet<ResourceLocation>();
        if (!option.questAction().isEmpty()) referenced.add(option.questAction().questId());
        if (option.questAction().action() == DialogueQuestAction.Action.START) starts.add(option.questAction().questId());
        var branch = QuestStageBranchOptionIds.parse(option.id()).orElse(null);
        if (branch != null) {
            referenced.add(branch.questId());
            var quest = VillagerQuestResources.quest(context.level().getServer(), branch.questId()).orElse(null);
            var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), branch.questId());
            var stage = quest == null || progress == null ? null : quest.stages().get(progress.currentStage());
            if (stage != null) stage.branches().stream().filter(value -> value.id().equals(branch.branchId()))
                    .findFirst().ifPresent(value -> actions.addAll(value.actions()));
        }
        collectActions(context, actions, starts, referenced);
        return beforeSelection(context, option.id(), starts, referenced, actions,
                () -> {
                    var fresh = VillagerInteractionService.createDialogueContext(context.player().serverLevel(), context.player(), context.villager());
                    return VillagerDialogueResources.dialogueOption(fresh, option.id()).filter(option::equals).isPresent()
                            && preview.equals(DialogueTreeService.previewSelection(fresh, option));
                },
                () -> VillagerDialogueRequestHandler.handle(context.player(), context.villager().getId(), option.id()));
    }

    private static void collectActions(DialogueContext context, List<VillagerActionDefinition> actions,
            Set<ResourceLocation> starts, Set<ResourceLocation> referenced) {
        for (var action : actions) {
            if (action.questId() == null) continue;
            referenced.add(action.questId());
            if (action.kind() == VillagerActionDefinition.Kind.QUEST
                    && action.questAction() == VillagerActionDefinition.QuestAction.START) starts.add(action.questId());
            if (action.kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION) {
                var quest = VillagerQuestResources.quest(context.level().getServer(), action.questId()).orElse(null);
                if (quest != null && action.questTransition().stageId().equals(QuestLifecycleService.initialStage(quest))) {
                    starts.add(action.questId());
                }
            }
        }
    }

    static boolean beforeSelection(DialogueContext context, String optionId, Set<ResourceLocation> starts,
            Set<ResourceLocation> referenced, BooleanSupplier stillSelected, Runnable execute) {
        return beforeSelection(context, optionId, starts, referenced, List.of(), stillSelected, execute);
    }

    static boolean beforeSelection(DialogueContext context, String optionId, Set<ResourceLocation> starts,
            Set<ResourceLocation> referenced, List<VillagerActionDefinition> actions, BooleanSupplier stillSelected, Runnable execute) {
        ServerPlayer player = context.player();
        var approval = APPROVAL.get();
        if (approval != null && approval.consent.playerId().equals(player.getUUID())
                && approval.optionId.equals(optionId)) return false;
        var pending = requests(player);
        pending.remove(player.getUUID());
        Preparation prepared;
        try {
            prepared = prepareSelection(context, starts, referenced, actions);
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not save campaign quest confirmation for {}", player.getUUID(), exception);
            notice(context, "Campaign state could not be saved. Check world storage before retrying.");
            return true;
        }
        if (!prepared.diagnostic().isBlank()) {
            notice(context, prepared.diagnostic());
            return true;
        }
        var confirmation = prepared.confirmation();
        if (confirmation == null) return false;
        UUID sessionId = VillagerConversationService.sessionId(player, context.villager());
        if (sessionId == null) return true;
        var impacts = new LinkedHashMap<ResourceLocation, CampaignQuestAcceptance>();
        confirmation.consent().quests().forEach((id, entry) -> impacts.put(id, entry.acceptance()));
        var request = new Request(confirmation.id(), 1L, player.getUUID(), context.villager().getUUID(), context.villager().getId(),
                sessionId, optionId, confirmation.createdAt(), QuestContentCatalogs.current(player.server), confirmation.snapshots(),
                impacts, confirmation.consent().decisions(), stillSelected, execute, confirmation.text());
        pending.put(player.getUUID(), request);
        send(player, request, true, confirmation.text());
        return true;
    }

    public static Preparation prepareActions(DialogueContext context, List<VillagerActionDefinition> actions) throws IOException {
        return prepareActions(context, context.player().getUUID(), actions);
    }

    public static Preparation prepareActions(DialogueContext context, UUID actor, List<VillagerActionDefinition> actions) throws IOException {
        var starts = new LinkedHashSet<ResourceLocation>();
        var referenced = new LinkedHashSet<ResourceLocation>();
        collectActions(context, actions, starts, referenced);
        return prepareSelection(context, starts, referenced, actions, actor);
    }

    public static Preparation prepareRemainingAction(DialogueContext context, UUID actor, VillagerActionDefinition action, CompoundTag savedConsent) throws IOException {
        if (action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME) {
            var prepared = CampaignOutcomeService.prepare(context, action, actor);
            if (!prepared.diagnostic().isBlank()) return new Preparation(null, prepared.diagnostic());
            if (prepared.applied() || !prepared.requiresConfirmation()) return new Preparation(null, "");
            if (!savedConsent.isEmpty()) {
                var consent = CampaignQuestConsent.load(savedConsent);
                if (consent.playerId().equals(actor) && matchesConsentProvider(context, consent)
                        && prepared.consent().equals(consent.decisions().get(prepared.consent().operationId()))) return new Preparation(null, "");
            }
        } else if (action.kind() == VillagerActionDefinition.Kind.QUEST && action.questAction() == VillagerActionDefinition.QuestAction.START
                && !savedConsent.isEmpty()) {
            var consent = CampaignQuestConsent.load(savedConsent);
            if (consent.playerId().equals(actor) && matchesConsentProvider(context, consent) && consent.quests().containsKey(action.questId())
                    && resumeAccess(context, actor, savedConsent).allowed()) return new Preparation(null, "");
        }
        return prepareActions(context, actor, List.of(action));
    }

    private static Preparation prepareSelection(DialogueContext context, Set<ResourceLocation> starts,
            Set<ResourceLocation> referenced, List<VillagerActionDefinition> actions) throws IOException {
        return prepareSelection(context, starts, referenced, actions, context.player().getUUID());
    }

    private static Preparation prepareSelection(DialogueContext context, Set<ResourceLocation> starts,
            Set<ResourceLocation> referenced, List<VillagerActionDefinition> actions, UUID actor) throws IOException {
        ServerPlayer player = context.player();
        DialogueContext personal = starts.isEmpty() ? null : personalContext(context, actor);
        if (!starts.isEmpty() && personal == null) return new Preparation(null, "The original player must return before accepting another personal quest.");
        var impacts = new LinkedHashMap<ResourceLocation, CampaignQuestAcceptance>();
        var descriptions = new ArrayList<String>();
        var decisions = new LinkedHashMap<UUID, CampaignOutcomeConsent>();
        for (var action : actions) {
            if (action.kind() != VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME) continue;
            var prepared = CampaignOutcomeService.prepare(context, action, actor);
            if (!prepared.diagnostic().isBlank() || prepared.applied()) {
                return new Preparation(null, prepared.applied() ? "This shared outcome is already committed: " + prepared.title() : prepared.diagnostic());
            }
            if (prepared.requiresConfirmation()) {
                var consent = prepared.consent();
                var previous = decisions.putIfAbsent(consent.operationId(), consent);
                if (previous != null && !previous.equals(consent)) {
                    return new Preparation(null, "This choice contains conflicting outcomes for one operation. Ask the author to separate them.");
                }
                descriptions.add(prepared.title());
            }
        }
        if (actions.stream().filter(value -> value.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME).count() > 1) {
            return new Preparation(null, "A choice can resolve only one shared milestone. Ask the author to separate these decisions.");
        }
        UUID requestId = UUID.randomUUID();
        long now = context.level().getGameTime();
        for (ResourceLocation questId : starts) {
            var quest = VillagerQuestResources.quest(player.server, questId).orElse(null);
            if (quest == null || !requiresConfirmation(personal, quest)) continue;
            String issue = CampaignQuestAccess.availabilityIssue(personal, quest);
            if (issue.isBlank()) issue = CampaignAuthority.controlIssue(player, actor);
            if (!issue.isBlank()) {
                return new Preparation(null, issue);
            }
            var content = CampaignResources.current(player.server).campaigns().get(quest.campaign());
            var instance = CampaignSavedData.get(context.level()).open(content, now);
            String title = QuestContentCatalogs.current(player.server).localization()
                    .plainText(context.locale(), quest.titleKey()).orElse(quest.title());
            var fixed = new LinkedHashMap<String, String>();
            for (var milestone : content.definition().milestones().values()) {
                if (!milestone.quest().equals(quest.id()) || !milestone.irreversible()
                        || instance.outcomes().containsKey(milestone.id())) continue;
                var titles = new ArrayList<String>();
                for (var outcome : milestone.outcomes().values()) {
                    titles.add(content.localization().plainText(context.locale(), outcome.titleKey()).orElse(outcome.id()));
                }
                descriptions.add(title + ": " + String.join(" / ", titles));
                if (milestone.outcomes().size() == 1) fixed.put(milestone.id(), milestone.outcomes().keySet().iterator().next());
            }
            impacts.put(questId, new CampaignQuestAcceptance(requestId, instance.instanceId(), instance.fingerprint(),
                    instance.worldRevision(), now, fixed));
        }
        if (impacts.isEmpty() && decisions.isEmpty()) return new Preparation(null, "");
        var snapshots = new LinkedHashMap<ResourceLocation, CompoundTag>();
        var allReferences = new LinkedHashSet<>(referenced);
        allReferences.addAll(starts);
        decisions.values().forEach(value -> allReferences.add(value.questId()));
        allReferences.forEach(id -> snapshots.put(id, snapshot(context.level(), actor, id)));
        String text = decisions.isEmpty()
                ? "This quest can permanently change the shared world for everyone, including players who are offline."
                        + "\n\n" + String.join("\n", descriptions)
                        + "\n\nPlayers who arrive later will follow a catch-up path. Accept this quest?"
                : "This decision permanently changes the shared story for everyone, including players who are offline."
                        + "\n\n" + String.join("\n", descriptions) + "\n\nCommit this outcome?";
        if (text.length() > CampaignQuestConfirmationPayload.MAX_TEXT_LENGTH) {
            return new Preparation(null, "This quest has too many shared consequences to display. Ask the author to split the quest.");
        }
        var entries = new LinkedHashMap<ResourceLocation, CampaignQuestConsent.Entry>();
        impacts.forEach((id, acceptance) -> entries.put(id, new CampaignQuestConsent.Entry(acceptance, snapshots.get(id))));
        var consent = new CampaignQuestConsent(actor, context.villager().getUUID(), entries, decisions);
        return new Preparation(new CampaignConfirmation(requestId, now, consent, snapshots, text), "");
    }

    public static ResumeAccess confirmationAccess(DialogueContext context, CampaignConfirmation confirmation) {
        return confirmationAccess(context, context.player().getUUID(), confirmation);
    }

    public static ResumeAccess confirmationAccess(DialogueContext context, UUID actor, CampaignConfirmation confirmation) {
        var access = resumeAccess(context, actor, confirmation.consent().save());
        if (!access.allowed()) return access;
        for (var entry : confirmation.snapshots().entrySet()) {
            if (!entry.getValue().equals(snapshot(context.level(), actor, entry.getKey()))) {
                return new ResumeAccess(false, "Your quest progress changed. Review the current consequences again.");
            }
        }
        for (var quest : confirmation.consent().quests().keySet()) {
            var personal = personalContext(context, actor);
            if (personal == null) return new ResumeAccess(true, "The original player must return before accepting another personal quest.");
            if (!com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(personal, quest, Set.of("available"))) {
                return new ResumeAccess(false, "This quest is no longer available. Review the current choices.");
            }
        }
        return new ResumeAccess(false, "");
    }

    private static DialogueContext personalContext(DialogueContext controller, UUID actor) {
        if (actor.equals(controller.player().getUUID())) return controller;
        var player = controller.player().server.getPlayerList().getPlayer(actor);
        return player == null || player.serverLevel() != controller.level() ? null
                : VillagerInteractionService.createDialogueContext(player.serverLevel(), player, controller.villager());
    }

    public record Preparation(CampaignConfirmation confirmation, String diagnostic) { }

    public static boolean requiresConfirmation(DialogueContext context, QuestDefinition quest) {
        if (quest.campaign() == null) return false;
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) return false;
        var content = CampaignResources.current(context.level().getServer()).campaigns().get(quest.campaign());
        if (content == null) return false;
        var instance = CampaignSavedData.get(context.level()).instances().get(quest.campaign());
        return content.definition().milestones().values().stream().anyMatch(milestone -> milestone.irreversible()
                && milestone.quest().equals(quest.id()) && (instance == null || !instance.outcomes().containsKey(milestone.id())));
    }

    public static CampaignQuestAcceptance approved(DialogueContext context, QuestDefinition quest) {
        var approval = APPROVAL.get();
        if (approval == null || !approval.consent.playerId().equals(context.player().getUUID())
                || !approval.consent.providerId().equals(context.villager().getUUID()) || !CampaignAuthority.canControl(context.player())) return null;
        var accepted = approval.remaining.get(quest.id());
        var entry = approval.consent.quests().get(quest.id());
        if (entry == null || !entry.progress().equals(snapshot(context.player(), quest.id()))) return null;
        var instance = quest.campaign() == null ? null : CampaignSavedData.get(context.level()).instances().get(quest.campaign());
        if (accepted == null || instance == null || !accepted.instanceId().equals(instance.instanceId())
                || !accepted.fingerprint().equals(instance.fingerprint()) || accepted.worldRevision() != instance.worldRevision()) return null;
        approval.remaining.remove(quest.id());
        return accepted;
    }

    public static boolean persistAcceptedStart(DialogueContext context) {
        var data = VillagerQuestSavedData.get(context.level());
        data.requireCampaignPersistence();
        try {
            data.persistCampaignProgress(context.level());
            return true;
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not save accepted campaign quest for {}", context.player().getUUID(), exception);
            return false;
        }
    }

    public static void handle(ServerPlayer player, CampaignQuestConfirmationRequestPayload payload) {
        var request = requests(player).get(player.getUUID());
        if (request == null || !request.id().equals(payload.requestId()) || request.revision() != payload.revision()) return;
        requests(player).remove(player.getUUID());
        if (!payload.accept()) {
            send(player, request, false, "");
            return;
        }
        var target = InteractionRequestValidator.requireDialogueConversation(player, request.entityId()).orElse(null);
        String issue = CampaignAuthority.controlIssue(player);
        if (issue.isBlank() && (target == null || !target.villager().getUUID().equals(request.providerId())
                || !Objects.equals(request.sessionId(), VillagerConversationService.sessionId(player, target.villager())))) {
            issue = "This conversation has changed. Choose the quest again.";
        }
        if (issue.isBlank()) {
            var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, target.villager());
            for (var decision : request.decisions().values()) {
                issue = CampaignOutcomeService.resumeAccess(context, decision).diagnostic();
                if (!issue.isBlank()) break;
            }
        }
        if (issue.isBlank() && (player.serverLevel().getGameTime() - request.createdAt() > TIMEOUT_TICKS
                || request.catalog() != QuestContentCatalogs.current(player.server) || !request.stillSelected().getAsBoolean())) {
            issue = "This quest offer has changed. Choose it again to review the current consequences.";
        }
        if (issue.isBlank()) issue = stateIssue(player, request);
        send(player, request, false, issue);
        if (!issue.isBlank()) {
            if (target != null) VillagerInteractionService.sendDialogueReputation(player, target.villager(), player.serverLevel());
            return;
        }
        APPROVAL.set(new Approval(request, player.serverLevel().getGameTime()));
        try {
            request.execute().run();
        } finally {
            APPROVAL.remove();
        }
    }

    private static String stateIssue(ServerPlayer player, Request request) {
        for (var entry : request.snapshots().entrySet()) {
            if (!entry.getValue().equals(snapshot(player, entry.getKey()))) return "Your quest progress changed. Review the quest offer again.";
        }
        for (var entry : request.impacts().entrySet()) {
            var quest = VillagerQuestResources.quest(player.server, entry.getKey()).orElse(null);
            if (quest == null) return "This quest is currently unavailable.";
            String issue = CampaignQuestAccess.contentIssue(player.serverLevel(), quest);
            if (!issue.isBlank()) return issue;
            var instance = CampaignSavedData.get(player.serverLevel()).instances().get(quest.campaign());
            var accepted = entry.getValue();
            if (instance == null || !accepted.instanceId().equals(instance.instanceId())
                    || !accepted.fingerprint().equals(instance.fingerprint()) || accepted.worldRevision() != instance.worldRevision()) {
                String outcomes = instance == null ? "" : String.join(", ", instance.outcomes().values().stream()
                        .map(CampaignInstance.OutcomeCommit::title).toList());
                return "The shared story changed. " + outcomes + " Choose the quest again to see your current path.";
            }
        }
        return "";
    }

    private static CompoundTag snapshot(ServerPlayer player, ResourceLocation questId) {
        return snapshot(player.serverLevel(), player.getUUID(), questId);
    }

    private static CompoundTag snapshot(net.minecraft.server.level.ServerLevel level, UUID player, ResourceLocation questId) {
        var progress = VillagerQuestSavedData.get(level).get(player, questId);
        return progress == null ? new CompoundTag() : progress.campaignRequestSnapshot();
    }

    private static Map<UUID, Request> requests(ServerPlayer player) {
        return REQUESTS.computeIfAbsent(player.server, ignored -> new HashMap<>());
    }

    private static void notice(DialogueContext context, String text) {
        VillagerInteractionService.sendDialogueReputation(context.player(), context.villager(), context.level());
        VillagerInteractionService.sendPersonalVillagerChat(context.player(), context.villager(), text);
    }

    private static void send(ServerPlayer player, Request request, boolean pending, String text) {
        com.jvn.villagerretaliation.network.VillagerReputationNetworking.sendCampaignConfirmation(player, new CampaignQuestConfirmationPayload(request.id(), request.revision(),
                request.entityId(), request.providerId(), pending, text.substring(0, Math.min(text.length(), CampaignQuestConfirmationPayload.MAX_TEXT_LENGTH))));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) requests(player).remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REQUESTS.remove(event.getServer());
    }

    public static CompoundTag captureConsent() {
        var approval = APPROVAL.get();
        if (approval == null || approval.remaining.isEmpty() && approval.decisions.isEmpty()) return new CompoundTag();
        var entries = new LinkedHashMap<ResourceLocation, CampaignQuestConsent.Entry>();
        approval.remaining.forEach((id, accepted) -> entries.put(id, approval.consent.quests().get(id)));
        return new CampaignQuestConsent(approval.consent.playerId(), approval.consent.providerId(), entries, approval.decisions).save();
    }

    public static void withConsent(CompoundTag tag, Runnable work) {
        if (tag.isEmpty()) {
            work.run();
            return;
        }
        var previous = APPROVAL.get();
        APPROVAL.set(new Approval(CampaignQuestConsent.load(tag), ""));
        try {
            work.run();
        } finally {
            if (previous == null) APPROVAL.remove();
            else APPROVAL.set(previous);
        }
    }

    public static ResumeAccess resumeAccess(DialogueContext context, CompoundTag tag) {
        if (tag.isEmpty()) return new ResumeAccess(false, "");
        return resumeAccess(context, context.player().getUUID(), tag);
    }

    public static ResumeAccess resumeAccess(DialogueContext context, UUID actor, CompoundTag tag) {
        if (tag.isEmpty()) return new ResumeAccess(false, "");
        CampaignQuestConsent consent;
        try {
            consent = CampaignQuestConsent.load(tag);
        } catch (IllegalArgumentException exception) {
            return new ResumeAccess(true, exception.getMessage());
        }
        if (!consent.playerId().equals(actor) || !matchesConsentProvider(context, consent)) {
            return new ResumeAccess(false, "The player or character for this accepted quest changed.");
        }
        String authority = CampaignAuthority.controlIssue(context.player(), actor);
        if (!authority.isBlank()) return new ResumeAccess(true, authority);
        for (var decision : consent.decisions().values()) {
            var access = CampaignOutcomeService.resumeAccess(context, actor, decision);
            if (!access.allowed()) return access;
        }
        for (var entry : consent.quests().entrySet()) {
            if (alreadyAccepted(context.level(), actor, entry.getKey(), entry.getValue().acceptance())) continue;
            var quest = VillagerQuestResources.quest(context.level().getServer(), entry.getKey()).orElse(null);
            if (quest == null) return new ResumeAccess(true, "Accepted campaign quest content is unavailable: " + entry.getKey());
            String issue = CampaignQuestAccess.contentIssue(context.level(), quest, VillagerQuestSavedData.get(context.level()).get(actor, quest.id()));
            if (!issue.isBlank()) return new ResumeAccess(true, issue);
            var accepted = entry.getValue().acceptance();
            var instance = quest.campaign() == null ? null : CampaignSavedData.get(context.level()).instances().get(quest.campaign());
            if (instance == null || !accepted.instanceId().equals(instance.instanceId()) || !accepted.fingerprint().equals(instance.fingerprint())) {
                return new ResumeAccess(true, "Accepted quest ownership or campaign content changed. Restore compatible content or repair the binding.");
            }
            if (accepted.worldRevision() != instance.worldRevision()) {
                return new ResumeAccess(false, "The shared story changed while this quest offer was interrupted. Review its current path again.");
            }
            if (!entry.getValue().progress().equals(snapshot(context.level(), actor, entry.getKey()))) {
                return new ResumeAccess(false, "Your quest progress changed while this offer was interrupted. Review the quest again.");
            }
        }
        return new ResumeAccess(false, "");
    }

    public static CompoundTag remainingConsent(DialogueContext context, CompoundTag tag) {
        if (tag.isEmpty()) return tag;
        return remainingConsent(context, context.player().getUUID(), tag);
    }

    public static CompoundTag remainingConsent(DialogueContext context, UUID actor, CompoundTag tag) {
        if (tag.isEmpty()) return tag;
        var consent = CampaignQuestConsent.load(tag);
        if (!consent.playerId().equals(actor) || !matchesConsentProvider(context, consent)) return tag;
        var remaining = new LinkedHashMap<>(consent.quests());
        remaining.entrySet().removeIf(entry -> alreadyAccepted(context.level(), actor, entry.getKey(), entry.getValue().acceptance()));
        var decisions = new LinkedHashMap<>(consent.decisions());
        decisions.values().removeIf(value -> CampaignOutcomeService.committed(context, value));
        return remaining.isEmpty() && decisions.isEmpty() ? new CompoundTag()
                : new CampaignQuestConsent(consent.playerId(), context.villager().getUUID(), remaining, decisions).save();
    }

    private static boolean matchesConsentProvider(DialogueContext context, CampaignQuestConsent consent) {
        if (consent.providerId().equals(context.villager().getUUID())) return true;
        var server = context.level().getServer();
        var replacement = context.villager().getUUID();
        return consent.quests().keySet().stream().allMatch(quest -> CampaignQuestCharacters.canReplaceProvider(server, quest, consent.providerId(), replacement))
                && consent.decisions().values().stream().allMatch(decision -> CampaignQuestCharacters.canReplaceProvider(server, decision.questId(), consent.providerId(), replacement));
    }

    static boolean approvedOutcome(DialogueContext context, UUID actor, CampaignOutcomeConsent consent) {
        var approval = APPROVAL.get();
        return approval != null && approval.consent.playerId().equals(actor)
                && approval.consent.providerId().equals(context.villager().getUUID())
                && consent.equals(approval.decisions.get(consent.operationId()));
    }

    static void outcomeApplied(UUID operationId) {
        var approval = APPROVAL.get();
        if (approval != null) approval.decisions.remove(operationId);
    }

    private static boolean alreadyAccepted(net.minecraft.server.level.ServerLevel level, UUID actor, ResourceLocation questId, CampaignQuestAcceptance acceptance) {
        var progress = VillagerQuestSavedData.get(level).get(actor, questId);
        var receipt = progress == null ? null : progress.campaignAcceptance();
        return receipt != null && receipt.requestId().equals(acceptance.requestId())
                && receipt.instanceId().equals(acceptance.instanceId()) && receipt.fingerprint().equals(acceptance.fingerprint());
    }

    public record ResumeAccess(boolean paused, String diagnostic) {
        public boolean allowed() { return diagnostic.isBlank(); }
    }

    static Request pending(ServerPlayer player) {
        return requests(player).get(player.getUUID());
    }

    record Request(UUID id, long revision, UUID playerId, UUID providerId, int entityId, UUID sessionId,
            String optionId, long createdAt, QuestContentCatalog catalog, Map<ResourceLocation, CompoundTag> snapshots,
            Map<ResourceLocation, CampaignQuestAcceptance> impacts, Map<UUID, CampaignOutcomeConsent> decisions, BooleanSupplier stillSelected, Runnable execute, String text) {
    }

    private static final class Approval {
        private final CampaignQuestConsent consent;
        private final String optionId;
        private final Map<ResourceLocation, CampaignQuestAcceptance> remaining = new HashMap<>();
        private final Map<UUID, CampaignOutcomeConsent> decisions = new LinkedHashMap<>();

        private Approval(Request request, long acceptedAt) {
            this(consent(request, acceptedAt), request.optionId());
        }

        private Approval(CampaignQuestConsent consent, String optionId) {
            this.consent = consent;
            this.optionId = optionId;
            consent.quests().forEach((id, entry) -> remaining.put(id, entry.acceptance()));
            decisions.putAll(consent.decisions());
        }

        private static CampaignQuestConsent consent(Request request, long acceptedAt) {
            var entries = new LinkedHashMap<ResourceLocation, CampaignQuestConsent.Entry>();
            request.impacts().forEach((id, accepted) -> entries.put(id,
                    new CampaignQuestConsent.Entry(new CampaignQuestAcceptance(accepted.requestId(), accepted.instanceId(),
                            accepted.fingerprint(), accepted.worldRevision(), acceptedAt, accepted.fixedOutcomes()), request.snapshots().get(id))));
            return new CampaignQuestConsent(request.playerId(), request.providerId(), entries, request.decisions());
        }
    }
}
