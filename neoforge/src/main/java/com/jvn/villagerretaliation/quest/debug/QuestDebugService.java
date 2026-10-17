package com.jvn.villagerretaliation.quest.debug;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;
import com.jvn.villagerretaliation.quest.QuestTriggerDispatchResult;
import com.jvn.villagerretaliation.quest.QuestTriggerDispatcher;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService.DebugInspectResult;
import com.jvn.villagerretaliation.quest.VillagerQuestService.DebugRemoveResult;
import com.jvn.villagerretaliation.quest.VillagerQuestService.DebugStartResult;
import com.jvn.villagerretaliation.quest.VillagerQuestService.ProviderRebindResult;
import com.jvn.villagerretaliation.quest.VillagerQuestService.QuestActionOutcome;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import com.jvn.villagerretaliation.quest.inventory.QuestInventoryService;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.query.QuestProgressSnapshot;
import com.jvn.villagerretaliation.quest.tracking.QuestPresentationService;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

public final class QuestDebugService {
    private final Access access;

    public QuestDebugService(Access access) { this.access = access; }

    public ProviderRebindResult debugRebindQuest(
            ServerPlayer player,
            Villager replacement,
            ResourceLocation questId) {
        if (player == null || replacement == null || questId == null || !(player.level() instanceof ServerLevel level)) {
            return new ProviderRebindResult(false, "Missing player, replacement provider, or quest id.", null, null);
        }
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(level.getServer(), questId).orElse(null);
        QuestDefinition definition = compiled == null ? null : compiled.asQuestDefinition();
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), questId);
        boolean resumableTerminal = progress != null
                && (progress.hasPendingLifecycleEvents() || progress.hasPendingTriggerEvents());
        if (definition == null || progress == null
                || (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE && !resumableTerminal)) {
            return new ProviderRebindResult(false, "Quest is missing, inactive, or has no deferred lifecycle work.",
                    progress == null ? null : progress.startedVillagerId(), replacement.getUUID());
        }
        UUID previousId = progress.startedVillagerId();
        Villager previous = VillagerEntityResolver.loaded(level.getServer(), previousId);
        if (previous != null && previous.isAlive()) {
            return new ProviderRebindResult(false, "The current provider is still live; rebind was refused.",
                    previousId, replacement.getUUID());
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, replacement);
        QuestProviderBinding binding = VillagerQuestProviderType.INSTANCE.bindingFromDialogueContext(context);
        if (!compiled.provider().providerType().equals(binding.providerType())) {
            return new ProviderRebindResult(false, "Replacement provider type is incompatible with this quest.",
                    previousId, replacement.getUUID());
        }
        QuestExecutionContext execution = QuestExecutionContext.fromDialogueContext(context, definition, "operator_rebind");
        if (!VillagerQuestProviderType.INSTANCE.matchesOffer(execution, definition)) {
            return new ProviderRebindResult(false, "Replacement provider does not satisfy the quest provider filters.",
                    previousId, replacement.getUUID());
        }
        progress.rebindProvider(binding, level.getGameTime(), "operator_rebind");
        SceneLifecycleIntegration.onQuestProviderRebind(level, player.getUUID(), questId, previousId, replacement,
                "compatible_quest_provider_rebind");
        int pendingBeforeReplay = progress.pendingLifecycleEvents().size() + progress.pendingTriggerEvents().size();
        access.resumePendingTriggerWork(context, compiled, progress);
        data.setDirty();
        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.PROVIDER, questId,
                "rebind result=accepted previous=" + previousId + " replacement=" + replacement.getUUID());
        access.sendTrackerSync(player, true, true);
        return new ProviderRebindResult(true,
                "Rebound quest " + questId + " from " + previousId + " to " + replacement.getUUID() + "."
                        + (pendingBeforeReplay > 0
                                ? " Replayed " + pendingBeforeReplay + " deferred lifecycle event(s)."
                                : ""),
                previousId,
                replacement.getUUID());
    }

    public DebugStartResult debugStartQuest(ServerPlayer player, Villager provider, ResourceLocation questId, boolean force) {
        if (player == null || provider == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugStartResult(false, "This debug command must be run by a player so nearby villagers can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugStartResult(false, "Unknown quest: " + questId);
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, provider);
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
        if (!force && progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return new DebugStartResult(false, "Quest is already active for " + player.getGameProfile().getName() + ".");
        }
        if (!force && !access.canStart(context, definition, progress, true)) {
            return new DebugStartResult(false, "Quest cannot restart from its current state. Use force_start to replace existing quest state.");
        }

        boolean replacedExisting = force && progress != null && progress.state() != VillagerQuestSavedData.QuestState.NOT_STARTED;
        QuestActionOutcome outcome = access.startQuest(context, definition, true, force);
        if (!"started".equals(outcome.status())) {
            return new DebugStartResult(false, outcome.text().isBlank() ? "Debug quest start failed." : outcome.text());
        }

        String providerName = VillagerPresetNameRegistry.resolveDisplayName(provider).getString();
        String profession = VillagerInteractionTextUtil.professionName(provider.getVillagerData().getProfession(), "villager");
        BlockPos pos = provider.blockPosition();
        String replaced = replacedExisting
                ? " Existing quest state was replaced."
                : "";
        return new DebugStartResult(
                true,
                "Started quest " + definition.title()
                        + " for " + player.getGameProfile().getName()
                        + " from provider " + providerName + " the " + profession
                        + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + ". Offer requirements were bypassed for debug."
                        + replaced);
    }

    public DebugRemoveResult debugRemoveQuest(ServerPlayer player, ResourceLocation questId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugRemoveResult(false, "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugRemoveResult(false, "Unknown quest: " + questId);
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress removed = data.remove(player.getUUID(), definition.id());
        if (removed == null) {
            return new DebugRemoveResult(false, "No saved quest state exists for " + definition.title()
                    + " on " + player.getGameProfile().getName() + ".");
        }

        access.clearTrackedQuestIf(data, player, definition.id());
        access.sendTrackerSync(player, true);

        return new DebugRemoveResult(
                true,
                "Removed quest " + definition.title()
                        + " for " + player.getGameProfile().getName()
                        + ". Previous state was " + removed.state().name().toLowerCase(Locale.ROOT) + ".");
    }

    public DebugInspectResult debugInspectQuest(ServerPlayer player, ResourceLocation questId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
        DialogueContext context = progress == null
                ? null
                : access.contextForStartedVillager(level, player, progress).orElse(null);
        List<String> lines = new ArrayList<>();
        lines.add(QuestDebugFormatter.header(definition));
        lines.add(QuestDebugFormatter.identityLine(definition, debugParentState(data, player, definition.parent())));
        QuestDefinition.Rules rules = definition.rules();
        lines.add(QuestDebugFormatter.rulesLine(rules));
        lines.add(QuestDebugFormatter.activeStateLine(rules));
        lines.add(QuestDebugFormatter.branchingLine(rules.branching()));
        lines.add(access.debugProgressLine(player, definition, progress, context));
        if (progress != null) {
            lines.add(QuestDebugFormatter.issuerLine(new QuestDebugFormatter.IssuerLine(
                    QuestPresentationService.issuerStatusView(player, QuestProgressSnapshot.capture(progress)),
                    progress.startedVillagerId() == null ? "none" : progress.startedVillagerId().toString(),
                    progress.issuerName(),
                    progress.issuerProfession(),
                    debugDimension(progress.issuerDimension()),
                    debugPos(progress.issuerPos()))));
            lines.add(QuestDebugFormatter.targetProgressLine(new QuestDebugFormatter.TargetProgressLine(
                    progress.visitedTarget(),
                    progress.hasProof(),
                    progress.targetObjectiveId(),
                    debugDimension(progress.targetDimension()),
                    debugPos(progress.targetPos()))));
            lines.add(QuestDebugFormatter.timesLine(progress));
            lines.add(QuestDebugFormatter.choiceHistoryLine(progress));
            lines.add(QuestDebugFormatter.providerRebindHistoryLine(progress));
            lines.add(QuestDebugFormatter.pendingLifecycleEventsLine(progress));
        }
        if (definition.target().hasStructureTarget()) {
            lines.add(QuestDebugFormatter.targetDefinitionLine(definition.target()));
        }
        for (QuestDefinition.Objective objective : definition.objectives()) {
            lines.add(access.debugObjectiveLine(player, level, definition, progress, context, objective));
        }
        if (progress != null) {
            progress.stageActions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    lines.add("stage_action=" + entry.getKey() + " status=" + entry.getValue().status()
                            + " continuation=" + entry.getValue().continuationId()
                            + " diagnostic=" + entry.getValue().diagnostic()));
            if (!progress.pendingStageTransition().isBlank()) lines.add("pending_stage=" + progress.pendingStageTransition());
        }
        String inventoryCacheLine = debugInventoryCacheLine(player, definition);
        if (!inventoryCacheLine.isBlank()) {
            lines.add(inventoryCacheLine);
        }
        return new DebugInspectResult(true, lines, "");
    }

    public DebugInspectResult debugTraceRecent(ServerPlayer player, int limit) {
        if (player == null) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so trace state can be resolved.");
        }
        List<QuestDebugTraceService.Event> events = QuestDebugTraceService.recent(player, limit);
        List<String> lines = new ArrayList<>();
        lines.add("trace enabled=" + QuestDebugTraceService.isEnabled(player)
                + " events=" + events.size()
                + " capacity=" + QuestDebugTraceService.capacity());
        QuestDebugTraceService.counts(player).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("trace_count type="
                        + entry.getKey().name().toLowerCase(Locale.ROOT)
                        + " count=" + entry.getValue()));
        events.forEach(event -> lines.add(event.line()));
        return new DebugInspectResult(true, lines, "");
    }

    public DebugInspectResult debugTraceClear(ServerPlayer player) {
        if (player == null) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so trace state can be resolved.");
        }
        QuestDebugTraceService.clear(player);
        return new DebugInspectResult(true, List.of("trace cleared"), "");
    }

    public DebugInspectResult debugTraceSetEnabled(ServerPlayer player, boolean enabled) {
        if (!QuestDebugTraceService.setEnabled(player, enabled)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so trace state can be resolved.");
        }
        return new DebugInspectResult(true, List.of("trace enabled=" + QuestDebugTraceService.isEnabled(player)
                + " capacity=" + QuestDebugTraceService.capacity()), "");
    }

    public DebugInspectResult debugObjectives(ServerPlayer player, ResourceLocation questId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), definition.id());
        DialogueContext context = access.contextForStartedVillager(level, player, progress).orElse(null);
        List<String> lines = new ArrayList<>();
        lines.add("objectives quest=" + definition.id()
                + " count=" + definition.objectives().size()
                + " active_context=" + (context != null));
        for (QuestDefinition.Objective objective : definition.objectives()) {
            lines.add(access.debugObjectiveLine(player, level, definition, progress, context, objective));
        }
        if (progress != null) {
            progress.stageActions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    lines.add("stage_action=" + entry.getKey() + " status=" + entry.getValue().status()
                            + " continuation=" + entry.getValue().continuationId()
                            + " diagnostic=" + entry.getValue().diagnostic()));
            if (!progress.pendingStageTransition().isBlank()) lines.add("pending_stage=" + progress.pendingStageTransition());
        }
        return new DebugInspectResult(true, lines, "");
    }

    public DebugInspectResult debugSetQuestStage(ServerPlayer player, ResourceLocation questId, String stage) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return new DebugInspectResult(false, List.of(), "Quest is not active for " + player.getGameProfile().getName() + ".");
        }
        String targetStage = stage == null ? "" : stage.trim();
        if (!definition.stages().containsKey(targetStage)) {
            return new DebugInspectResult(false, List.of(), "Unknown stage \"" + targetStage + "\" for quest " + definition.id() + ".");
        }
        DialogueContext context = access.contextForStartedVillager(level, player, progress).orElse(null);
        if (context == null) {
            return new DebugInspectResult(false, List.of(), "The started villager for " + definition.id() + " is not loaded.");
        }
        String previous = progress.currentStage();
        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.STAGE_TRANSITION, definition.id(),
                "attempt from=" + previous + " to=" + targetStage);
        boolean changed = access.changeQuestStage(context, definition, progress, targetStage, true, true);
        if (changed) {
            changed |= access.advanceStageIfComplete(context, definition, progress);
            data.setDirty();
        }
        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.STAGE_TRANSITION, definition.id(),
                "result changed=" + changed + " from=" + previous + " to=" + progress.currentStage());
        return new DebugInspectResult(true, List.of("set_stage quest=" + definition.id()
                + " changed=" + changed
                + " previous=" + previous
                + " current=" + progress.currentStage()), "");
    }

    public DebugInspectResult debugFireTrigger(
            ServerPlayer player,
            ResourceLocation questId,
            QuestDefinition.TriggerEvent event) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }
        if (event == null) {
            return new DebugInspectResult(false, List.of(), "Trigger event is not recognized.");
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return new DebugInspectResult(false, List.of(), "Quest is not active for " + player.getGameProfile().getName() + ".");
        }
        DialogueContext context = access.contextForStartedVillager(level, player, progress).orElse(null);
        if (context == null) {
            return new DebugInspectResult(false, List.of(), "The started villager for " + definition.id() + " is not loaded.");
        }
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(level.getServer(), definition.id()).orElse(null);
        if (compiled == null) {
            return new DebugInspectResult(false, List.of(), "Compiled quest is missing for " + definition.id() + ".");
        }

        List<String> lines = new ArrayList<>();
        List<CompiledQuestTrigger> candidates = compiled.triggerIndex().candidates(event, progress.currentStage());
        lines.add("fire_trigger quest=" + definition.id()
                + " event=" + QuestTriggerRegistry.canonicalEventId(event)
                + " stage=" + progress.currentStage()
                + " candidates=" + candidates.size());
        long gameTime = level.getGameTime();
        for (CompiledQuestTrigger compiledTrigger : candidates) {
            lines.add(debugTriggerFilterLine(context, gameTime, progress, compiledTrigger.definition(), event));
        }
        QuestTriggerDispatchResult result = QuestTriggerDispatcher.dispatch(
                context,
                compiled,
                progress,
                event,
                access::runQuestTriggerActions);
        if (result.dirty()) {
            data.setDirty();
            access.sendTrackerSync(player, true);
        }
        lines.add("trigger_result dirty=" + result.dirty()
                + " candidates=" + result.trace().candidateTriggers()
                + " evaluated=" + result.trace().evaluatedTriggers()
                + " matched=" + result.trace().matchedTriggers()
                + " ran=" + result.trace().ranTriggers());
        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.TRIGGER, definition.id(),
                "fire event=" + QuestTriggerRegistry.canonicalEventId(event)
                        + " matched=" + result.trace().matchedTriggers()
                        + " ran=" + result.trace().ranTriggers());
        return new DebugInspectResult(true, lines, "");
    }

    public DebugInspectResult debugDryRunTriggerActions(
            ServerPlayer player,
            ResourceLocation questId,
            String triggerId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), definition.id());
        DialogueContext context = access.contextForStartedVillager(level, player, progress).orElse(null);
        String id = triggerId == null ? "" : triggerId.trim();
        List<QuestDefinition.Trigger> triggers = definition.triggers().stream()
                .filter(trigger -> trigger.id().equals(id))
                .toList();
        if (triggers.isEmpty()) {
            return new DebugInspectResult(false, List.of(), "Unknown trigger \"" + id + "\" for quest " + definition.id() + ".");
        }

        List<String> lines = new ArrayList<>();
        Map<String, String> replacements = new LinkedHashMap<>(context == null ? Map.of() : access.replacements(context, definition, progress));
        for (QuestDefinition.Trigger trigger : triggers) {
            lines.add("actions dry_run quest=" + definition.id()
                    + " trigger=" + trigger.id()
                    + " count=" + trigger.actions().size()
                    + " live_context=" + (context != null));
            for (int i = 0; i < trigger.actions().size(); i++) {
                VillagerActionDefinition action = trigger.actions().get(i);
                ActionResult result = VillagerActionRegistry.dryRun(context, action, replacements);
                String line = "action[" + i + "] type=" + VillagerActionRegistry.canonicalTypeId(action)
                        + " status=" + result.status().name().toLowerCase(Locale.ROOT)
                        + " message=" + blankAs(result.message(), "none")
                        + " capabilities=" + result.capabilities();
                lines.add(line);
                QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.ACTION, definition.id(), line);
            }
        }
        return new DebugInspectResult(true, lines, "");
    }

    public DebugInspectResult debugFactScope(ServerPlayer player, String scopeKey) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so fact state can be resolved.");
        }
        String key = scopeKey == null ? "" : scopeKey.trim();
        if (key.isBlank()) {
            return new DebugInspectResult(false, List.of(), "Fact scope cannot be blank.");
        }
        VillagerQuestFacts.FactSnapshot snapshot = VillagerQuestFacts.get(level).debugSnapshot(key);
        List<String> lines = new ArrayList<>();
        lines.add("facts scope=" + key
                + " empty=" + snapshot.empty()
                + " tags=" + snapshot.tags().size()
                + " variables=" + snapshot.variables().size()
                + " counters=" + snapshot.counters().size());
        snapshot.tags().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(tag -> lines.add("fact tag=" + tag));
        snapshot.variables().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("fact variable " + entry.getKey() + "=" + entry.getValue()));
        snapshot.counters().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add("fact counter " + entry.getKey() + "=" + entry.getValue()));
        return new DebugInspectResult(true, lines, "");
    }

    public String debugTriggerFilterLine(
            DialogueContext context,
            long gameTime,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Trigger trigger,
            QuestDefinition.TriggerEvent event) {
        if (trigger == null) {
            return "trigger_filter id=none result=failed reason=missing_trigger";
        }
        if (trigger.event() != event) {
            return "trigger_filter id=" + trigger.id() + " result=filtered reason=event_mismatch expected="
                    + QuestTriggerRegistry.canonicalEventId(trigger.event())
                    + " actual=" + QuestTriggerRegistry.canonicalEventId(event);
        }
        if (trigger.event() == QuestDefinition.TriggerEvent.PROXIMITY) {
            if (context == null || context.player() == null || context.villager() == null) {
                return "trigger_filter id=" + trigger.id() + " result=filtered reason=provider_context_missing";
            }
            double radius = trigger.radius();
            if (context.player().distanceToSqr(context.villager()) > radius * radius) {
                return "trigger_filter id=" + trigger.id() + " result=filtered reason=provider_out_of_radius radius=" + radius;
            }
        }
        if (!trigger.stages().isEmpty() && !trigger.stages().contains(progress.currentStage())) {
            return "trigger_filter id=" + trigger.id() + " result=filtered reason=stage current="
                    + progress.currentStage() + " allowed=" + trigger.stages();
        }
        long lastTriggered = progress.lastTriggerGameTime(trigger.id());
        if (!trigger.repeatable() && lastTriggered >= 0L) {
            return "trigger_filter id=" + trigger.id() + " result=filtered reason=not_repeatable last=" + lastTriggered;
        }
        if (trigger.cooldownTicks() > 0L) {
            if (lastTriggered >= 0L && gameTime - lastTriggered < trigger.cooldownTicks()) {
                return "trigger_filter id=" + trigger.id() + " result=filtered reason=cooldown remaining="
                        + (trigger.cooldownTicks() - (gameTime - lastTriggered));
            }
            if (lastTriggered < 0L
                    && QuestTriggerRegistry.isContinuous(trigger.event())
                    && progress.startedGameTime() >= 0L
                    && gameTime - progress.startedGameTime() < trigger.cooldownTicks()) {
                return "trigger_filter id=" + trigger.id() + " result=filtered reason=initial_continuous_cooldown remaining="
                        + (trigger.cooldownTicks() - (gameTime - progress.startedGameTime()));
            }
        }
        if (!trigger.conditions().isEmpty()) {
            if (context == null) {
                return "trigger_filter id=" + trigger.id() + " result=filtered reason=conditions_unknown";
            }
            Optional<DialogueCondition.ConditionEvaluationTrace> unmatched =
                    DialogueCondition.firstUnmatched(context, trigger.conditions());
            if (unmatched.isPresent()) {
                return "trigger_filter id=" + trigger.id() + " result=filtered reason=condition "
                        + formatConditionTrace(unmatched.get());
            }
        }
        return "trigger_filter id=" + trigger.id() + " result=matched reason=all_filters_passed cooldown="
                + trigger.cooldownTicks();
    }

    private String debugParentState(
            VillagerQuestSavedData data,
            ServerPlayer player,
            ResourceLocation parentId) {
        VillagerQuestSavedData.QuestProgress parentProgress = parentId == null ? null : data.get(player.getUUID(), parentId);
        return QuestDebugFormatter.parentState(parentId, parentProgress);
    }

    private String debugInventoryCacheLine(ServerPlayer player, QuestDefinition definition) {
        return QuestInventoryService.debugInventoryCacheLine(player, definition);
    }

    private String debugDimension(ResourceKey<Level> dimension) {
        return QuestDebugFormatter.dimension(dimension);
    }

    private String debugPos(BlockPos pos) {
        return QuestDebugFormatter.pos(pos);
    }

    public String debugEnum(Enum<?> value) {
        return QuestDebugFormatter.enumName(value);
    }

    public String formatConditionTrace(DialogueCondition.ConditionEvaluationTrace trace) {
        if (trace == null) {
            return "type=unknown outcome=unknown message=missing";
        }
        return "type=" + trace.canonicalTypeId()
                + " outcome=" + trace.outcome().name().toLowerCase(Locale.ROOT)
                + (trace.message().isBlank() ? "" : " message=" + trace.message())
                + " children=" + trace.children().size();
    }

    public String blankAs(String value, String fallback) {
        return QuestDebugFormatter.blankAs(value, fallback);
    }

    public interface Access {
        boolean changeQuestStage(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, String stage, boolean runExitActions, boolean runEntryActions);
        boolean advanceStageIfComplete(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress);
        QuestActionOutcome startQuest(DialogueContext context, QuestDefinition definition, boolean bypassOfferRequirements, boolean forceRestart);
        boolean canStart(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, boolean bypassOfferRequirements);
        Optional<DialogueContext> contextForStartedVillager(ServerLevel level, ServerPlayer player, VillagerQuestSavedData.QuestProgress progress);
        boolean resumePendingTriggerWork(DialogueContext context, CompiledQuest compiled, VillagerQuestSavedData.QuestProgress progress);
        boolean runQuestTriggerActions(QuestTriggerContext triggerContext, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, QuestDefinition.Trigger trigger);
        void sendTrackerSync(ServerPlayer player, boolean flash);
        void sendTrackerSync(ServerPlayer player, boolean flash, boolean force);
        void clearTrackedQuestIf(VillagerQuestSavedData data, ServerPlayer player, ResourceLocation questId);
        Map<String, String> replacements(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress);
        String debugProgressLine(ServerPlayer player, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, DialogueContext context);
        String debugObjectiveLine(ServerPlayer player, ServerLevel level, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, DialogueContext context, QuestDefinition.Objective objective);
    }
}
