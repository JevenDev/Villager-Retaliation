package com.jvn.villagerretaliation.quest.tracking;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.query.QuestProgressSnapshot;
import com.jvn.villagerretaliation.scene.SceneJournalPresenter;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class QuestPresentationService {
    private static final int APPROXIMATE_COORDINATE_STEP = 50;

    private QuestPresentationService() {
    }

    public static QuestTrackerSyncPayload.Entry withRuntimeJournal(QuestTrackerSyncPayload.Entry entry,
            QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, long completedGameTime) {
        return withRuntimeJournalView(entry, definition, QuestProgressSnapshot.capture(progress, definition), completedGameTime);
    }

    public static Map<String, String> trackerReplacements(ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, QuestDefinition.Objective objective,
            boolean activeConditionsMet, boolean hasRequiredProof, ObjectiveState state) {
        return trackerReplacementsView(player, definition, QuestProgressSnapshot.capture(progress, definition), objective,
                activeConditionsMet, hasRequiredProof, state);
    }

    public static Map<String, String> dialogueReplacements(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, QuestDefinition.Objective objective,
            boolean activeConditionsMet, boolean hasRequiredProof, ObjectiveState state) {
        return dialogueReplacementsView(context, definition, QuestProgressSnapshot.capture(progress, definition), objective,
                activeConditionsMet, hasRequiredProof, state);
    }

    public static List<QuestTrackerSyncPayload.ObjectiveStep> completedObjectiveSteps(ServerPlayer player,
            QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
        return completedObjectiveStepsView(player, definition, QuestProgressSnapshot.capture(progress, definition));
    }

    public static String issuerSummary(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        return issuerSummaryView(player, QuestProgressSnapshot.capture(progress));
    }

    public static String issuerLocationSummary(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        return issuerLocationSummaryView(player, QuestProgressSnapshot.capture(progress));
    }

    public static String issuerStatus(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        return issuerStatusView(player, QuestProgressSnapshot.capture(progress));
    }

    public static QuestTrackerSyncPayload.Entry availableEntry(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            List<QuestTrackerSyncPayload.Prerequisite> prerequisites) {
        String issuer = context.villager() == null ? "" : providerSummary(context.villager());
        BlockPos pos = context.origin();
        String issuerLocation = "Current location: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + " in " + dimensionDisplayName(context.level().dimension());
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.putAll(com.jvn.villagerretaliation.progress.PlayerHistoryService.replacements(player));
        replacements.put("quest", definition.title());
        replacements.put("issuer", issuer);
        replacements.put("issuer_name", context.villager() == null ? "" : VillagerPresetNameRegistry.resolveDisplayName(context.villager()).getString());
        replacements.put("issuer_profession", VillagerInteractionTextUtil.professionName(
                context.profession(),
                "villager"));
        String fallbackObjective = definition.description().isBlank()
                ? context.villager() == null ? "Accept this quest in your journal." : "Talk to {issuer} to accept this quest."
                : definition.description();
        QuestDefinition.Step step = new QuestDefinition.Step(
                fallbackObjective,
                definition.descriptionKey(),
                false,
                0.0F,
                Map.of());
        String status = resolveGlobalText(player, "quest.tracker.status.available", "Available", replacements);
        return QuestTrackerPresenter.entry(new QuestTrackerPresenter.EntryInput(
                player,
                definition,
                new QuestDefinition.SelectedText(definition.title(), definition.titleKey()),
                step,
                replacements,
                status,
                issuer,
                issuerLocation,
                List.of(),
                QuestTrackerPresenter.rewardPreviews(player, definition, replacements),
                prerequisites,
                0.0F,
                false,
                VillagerQuestSavedData.QuestState.NOT_STARTED))
                .withQuestAvailable(true);
    }

    public static QuestTrackerSyncPayload.Entry completedHistoryEntry(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.CompletionHistoryEntry history,
            List<QuestTrackerSyncPayload.Prerequisite> prerequisites) {
        String issuer = completionIssuerSummary(history);
        String issuerLocation = completionIssuerLocationSummary(history);
        Map<String, String> replacements = completionHistoryReplacements(player, definition, history, issuer, issuerLocation);
        QuestDefinition.Step fallback = QuestTrackerPresenter.fallbackStep("completed", VillagerQuestSavedData.QuestState.COMPLETED);
        QuestDefinition.Step step = definition.tracker().step("completed", fallback);
        QuestDefinition.SelectedText title = definition.tracker().title().isBlank() && definition.tracker().titleKey().isBlank()
                ? new QuestDefinition.SelectedText(definition.title(), definition.titleKey())
                : new QuestDefinition.SelectedText(definition.tracker().title(), definition.tracker().titleKey());
        String status = resolveGlobalText(player, "quest.tracker.status.completed", "Completed", replacements);
        return withRuntimeJournalView(QuestTrackerPresenter.entry(new QuestTrackerPresenter.EntryInput(
                player,
                definition,
                title,
                step,
                replacements,
                status,
                issuer,
                issuerLocation,
                List.of(),
                QuestTrackerPresenter.rewardPreviews(player, definition, replacements),
                prerequisites,
                completedObjectiveStepsView(player, definition, null),
                QuestTrackerPresenter.fallbackProgress("completed"),
                false,
                VillagerQuestSavedData.QuestState.COMPLETED))
                .withQuestId(completedHistoryQuestId(definition.id(), history.completionIndex())),
                definition,
                null,
                history.completedGameTime());
    }

    public static QuestTrackerSyncPayload.Entry withRuntimeJournalView(
            QuestTrackerSyncPayload.Entry entry,
            QuestDefinition definition,
            QuestProgressSnapshot progress,
            long completedGameTime) {
        if (entry == null || definition == null) {
            return entry;
        }
        long expiresAt = -1L;
        if (progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && progress.startedGameTime() >= 0L
                && definition.rules().expiration().afterTicks() > 0L) {
            expiresAt = progress.startedGameTime() + definition.rules().expiration().afterTicks();
        }
        QuestTrackerSyncPayload.Waypoint waypoint = QuestTrackerSyncPayload.Waypoint.NONE;
        if (progress != null && progress.targetPos() != null && progress.targetDimension() != null) {
            BlockPos pos = progress.targetPos();
            waypoint = new QuestTrackerSyncPayload.Waypoint(
                    progress.targetDimension().location().toString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ());
        }
        return entry.withJournal(entry.journal().withRuntime(expiresAt, completedGameTime, waypoint));
    }

    public static Map<String, String> trackerReplacementsView(
            ServerPlayer player, QuestDefinition definition, QuestProgressSnapshot progress,
            QuestDefinition.Objective objective, boolean activeConditionsMet, boolean hasRequiredProof, ObjectiveState state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(com.jvn.villagerretaliation.progress.PlayerHistoryService.replacements(player));
        values.put("quest", questTitle(player, definition, Map.of()));
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, progress));
        String timerKey = objective == null ? com.jvn.villagerretaliation.quest.runtime.QuestTimers.stageKey(progress == null ? "" : progress.currentStage())
                : com.jvn.villagerretaliation.quest.runtime.QuestTimers.objectiveKey(objective.id());
        long remaining = progress == null ? -1 : progress.timerRemaining().getOrDefault(timerKey, -1L);
        values.put("timer_remaining_ticks", Long.toString(remaining));
        values.put("timer_remaining_seconds", remaining < 0 ? "" : Long.toString((remaining + 19) / 20));
        values.put("quest_stage", progress == null ? "" : progress.currentStage());
        values.put("current_stage", progress == null ? "" : progress.currentStage());
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof ? "yes" : "no");
        values.put("active_conditions", activeConditionsMet ? "met" : "unmet");
        values.put("objective", progress == null ? "" : progress.targetObjectiveId());
        values.putAll(SceneJournalPresenter.encounterReplacements(player,definition.id()));
        addObjectiveReplacements(values, definition, progress, objective, state);
        addIssuerReplacements(values, player, progress);

        BlockPos targetPos = progress == null ? null : progress.targetPos();
        values.put("target_dimension", targetDimensionText(progress));
        values.put("destination_name", "the destination");
        if (objective != null && objective.type() == QuestDefinition.ObjectiveType.FOLLOWER_ARRIVAL) {
            var destination = com.jvn.villagerretaliation.quest.follower.QuestFollowerArrivalObjective.destination(player.serverLevel(), objective).orElse(null);
            if (destination != null) {
                targetPos = destination.position();
                values.put("target_dimension", dimensionDisplayName(destination.dimension()));
                if (!destination.name().isBlank()) values.put("destination_name", destination.name());
            }
        }
        if (targetPos != null) {
            boolean escort = objective != null && objective.type() == QuestDefinition.ObjectiveType.FOLLOWER_ARRIVAL;
            values.put("target_x", Integer.toString(escort ? targetPos.getX() : roundCoordinate(targetPos.getX())));
            values.put("target_y", Integer.toString(targetPos.getY()));
            values.put("target_z", Integer.toString(escort ? targetPos.getZ() : roundCoordinate(targetPos.getZ())));
            values.put("direction", directionPhrase(player.blockPosition(), targetPos));
            values.put("distance", Integer.toString(roundDistance(player.blockPosition(), targetPos)));
        } else {
            values.put("target_x", "unknown");
            values.put("target_y", "unknown");
            values.put("target_z", "unknown");
            values.put("direction", "somewhere beyond the map");
            values.put("distance", "unknown");
        }
        return Map.copyOf(values);
    }

    public static Map<String, String> dialogueReplacementsView(
            DialogueContext context,
            QuestDefinition definition,
            QuestProgressSnapshot progress,
            QuestDefinition.Objective objective, boolean activeConditionsMet, boolean hasRequiredProof, ObjectiveState state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(com.jvn.villagerretaliation.progress.PlayerHistoryService.replacements(context.player()));
        values.put("quest", questTitle(context, definition, Map.of()));
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, progress));
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof ? "yes" : "no");
        values.put("active_conditions", activeConditionsMet ? "met" : "unmet");
        values.put("objective", progress == null ? "" : progress.targetObjectiveId());
        addObjectiveReplacements(values, definition, progress, objective, state);
        addIssuerReplacements(values, context, progress);

        BlockPos targetPos = progress == null ? null : progress.targetPos();
        values.put("target_dimension", targetDimensionText(progress));
        if (targetPos != null) {
            values.put("target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("direction", directionPhrase(context.origin(), targetPos));
            values.put("distance", Integer.toString(roundDistance(context.origin(), targetPos)));
        } else {
            values.put("target_x", "unknown");
            values.put("target_z", "unknown");
            values.put("direction", "somewhere beyond my maps");
            values.put("distance", "unknown");
        }
        return Map.copyOf(values);
    }

    public static Map<String, String> completionHistoryReplacements(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.CompletionHistoryEntry history,
            String issuer,
            String issuerLocation) {
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(com.jvn.villagerretaliation.progress.PlayerHistoryService.replacements(player));
        values.put("quest", questTitle(player, definition, Map.of()));
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, null));
        values.put("quest_stage", "completed");
        values.put("current_stage", "completed");
        values.put("visited_target", "yes");
        values.put("has_proof", "yes");
        values.put("active_conditions", "met");
        values.put("objective", "");
        values.put("issuer", issuer);
        values.put("issuer_name", completionIssuerName(history));
        values.put("issuer_profession", completionIssuerProfessionName(history));
        values.put("issuer_dimension", completionIssuerDimensionText(history));
        values.put("issuer_location", issuerLocation);
        values.put("issuer_status", "completed");
        values.put("completion_count", Integer.toString(history.completionIndex()));
        values.put("completed_time", Long.toString(history.completedGameTime()));
        values.put("started_time", Long.toString(history.startedGameTime()));
        BlockPos issuerPos = history.issuerPos();
        if (issuerPos == null) {
            values.put("issuer_x", "unknown");
            values.put("issuer_y", "unknown");
            values.put("issuer_z", "unknown");
        } else {
            values.put("issuer_x", Integer.toString(issuerPos.getX()));
            values.put("issuer_y", Integer.toString(issuerPos.getY()));
            values.put("issuer_z", Integer.toString(issuerPos.getZ()));
        }
        return Map.copyOf(values);
    }

    public static String completedHistoryQuestId(ResourceLocation questId, int completionIndex) {
        String suffix = "#completed/" + Math.max(1, completionIndex);
        String base = questId == null ? "" : questId.toString();
        if (base.length() + suffix.length() <= 128) {
            return base + suffix;
        }
        return base.substring(0, Math.max(0, 128 - suffix.length())) + suffix;
    }

    public static String completionIssuerSummary(VillagerQuestSavedData.CompletionHistoryEntry history) {
        String name = completionIssuerName(history);
        String profession = completionIssuerProfessionName(history);
        if (profession.isBlank() || "villager".equalsIgnoreCase(profession)) {
            return name;
        }
        return name + " the " + profession;
    }

    private static String completionIssuerName(VillagerQuestSavedData.CompletionHistoryEntry history) {
        if (history != null && !history.issuerName().isBlank()) {
            return history.issuerName();
        }
        return "Unknown villager";
    }

    private static String completionIssuerProfessionName(VillagerQuestSavedData.CompletionHistoryEntry history) {
        if (history != null && !history.issuerProfession().isBlank()) {
            ResourceLocation professionId = ResourceLocation.tryParse(history.issuerProfession());
            if (professionId != null) {
                return VillagerInteractionTextUtil.resourcePathName(professionId);
            }
        }
        return "villager";
    }

    public static String completionIssuerLocationSummary(VillagerQuestSavedData.CompletionHistoryEntry history) {
        BlockPos pos = history == null ? null : history.issuerPos();
        if (pos == null) {
            return "Completion issuer location unknown";
        }
        String dimension = completionIssuerDimensionText(history);
        return "Completed near " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + (dimension.isBlank() || "unknown".equals(dimension) ? "" : " in " + dimension);
    }

    private static String completionIssuerDimensionText(VillagerQuestSavedData.CompletionHistoryEntry history) {
        ResourceKey<Level> dimension = history == null ? null : history.issuerDimension();
        return dimensionDisplayName(dimension);
    }

    public static String providerSummary(Villager villager) {
        String name = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        String profession = VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "villager");
        if (profession.isBlank() || "villager".equalsIgnoreCase(profession)) {
            return name;
        }
        return name + " the " + profession;
    }

    public static void addActiveTrackerStep(
            List<QuestTrackerSyncPayload.ObjectiveStep> steps,
            ServerPlayer player,
            QuestDefinition definition,
            String stepKey,
            boolean include,
            boolean complete,
            Map<String, String> replacements) {
        if (!include || steps.size() >= QuestTrackerSyncPayload.MAX_OBJECTIVE_STEPS) {
            return;
        }
        QuestDefinition.Step fallback = QuestTrackerPresenter.fallbackStep(stepKey, VillagerQuestSavedData.QuestState.ACTIVE);
        QuestDefinition.Step step = definition.tracker().step(stepKey, fallback);
        String label = QuestTrackerPresenter.resolveText(
                player,
                new QuestDefinition.SelectedText(step.text(), step.textKey()),
                replacements);
        if (!label.isBlank()) {
            steps.add(new QuestTrackerSyncPayload.ObjectiveStep(label, complete));
        }
    }

    public static List<QuestTrackerSyncPayload.ObjectiveStep> completedObjectiveStepsView(
            ServerPlayer player,
            QuestDefinition definition,
            QuestProgressSnapshot progress) {
        if (player == null || definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        List<QuestTrackerSyncPayload.ObjectiveStep> steps = new ArrayList<>();
        for (QuestDefinition.Objective objective : definition.objectives()) {
            String label = completedObjectiveStepLabel(player, definition, progress, objective);
            if (!label.isBlank()) {
                steps.add(new QuestTrackerSyncPayload.ObjectiveStep(label, true));
            }
            if (steps.size() >= QuestTrackerSyncPayload.MAX_OBJECTIVE_STEPS) {
                break;
            }
        }
        return List.copyOf(steps);
    }

    private static String completedObjectiveStepLabel(
            ServerPlayer player,
            QuestDefinition definition,
            QuestProgressSnapshot progress,
            QuestDefinition.Objective objective) {
        if (objective == null) {
            return "";
        }
        QuestDefinition.Step fallback = new QuestDefinition.Step(
                completedObjectiveFallback(objective),
                "",
                false,
                1.0F,
                Map.of());
        QuestDefinition.Step step = QuestTrackerPresenter.objectiveTrackerStep(objective, fallback, true);
        return QuestTrackerPresenter.resolveText(
                player,
                new QuestDefinition.SelectedText(step.text(), step.textKey()),
                completedObjectiveReplacements(player, definition, progress, objective));
    }

    private static Map<String, String> completedObjectiveReplacements(
            ServerPlayer player,
            QuestDefinition definition,
            QuestProgressSnapshot progress,
            QuestDefinition.Objective objective) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest", questTitle(player, definition, Map.of()));
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, null));
        values.put("quest_stage", "completed");
        values.put("current_stage", "completed");
        values.put("visited_target", "yes");
        values.put("has_proof", "yes");
        values.put("active_conditions", "met");
        values.put("objective", objective.id());
        values.put("objective_id", objective.id());
        values.put("objective_type", objective.type().name().toLowerCase(Locale.ROOT));
        ResourceLocation objectiveItem = QuestProgressSnapshot.resolvedItem(objective, progress);
        values.put("objective_item", objectiveItem == null ? questItemName(definition, progress) : itemName(objectiveItem));
        values.put("objective_item_id", objectiveItem == null ? "" : objectiveItem.toString());
        values.put("objective_count", Integer.toString(objective.count()));
        values.put("objective_progress_count", Integer.toString(objective.count()));
        values.put("objective_entity", objectiveEntityName(objective));
        values.put("objective_block", objectiveBlockName(objective));
        values.put("objective_block_id", objectiveBlockId(objective));
        values.put("objective_memory", objectiveMemoryName(objective));
        values.put("objective_memory_id", objectiveMemoryId(objective));
        values.put("objective_gift_reaction", objectiveGiftReaction(objective));
        values.put("objective_reputation", "0");
        values.put("objective_reputation_level", objectiveReputationLevel(objective));
        values.put("objective_reputation_min", objective.minReputation() == null ? "" : objective.minReputation().toString());
        values.put("objective_reputation_max", objective.maxReputation() == null ? "" : objective.maxReputation().toString());
        values.put("objective_choice", objectiveChoiceValue(objective));
        values.put("objective_choice_key", objective.type() == QuestDefinition.ObjectiveType.CHOICE ? objective.factKey() : "");
        values.put("objective_choice_value", objectiveChoiceValue(objective));
        values.put("objective_fact", objectiveFactName(objective));
        values.put("objective_fact_id", objectiveFactId(objective));
        values.put("objective_fact_key", objective.factKey());
        values.put("objective_fact_value", objectiveFactValue(objective));
        values.put("objective_fact_scope", objective.factScope().name().toLowerCase(Locale.ROOT));
        values.put("objective_radius", Integer.toString(objective.radius()));
        values.put("objective_complete", "yes");
        values.put("objective_progress", "1.00");

        BlockPos targetPos = objective.location();
        if (targetPos == null) {
            values.put("objective_target_x", "unknown");
            values.put("objective_target_y", "unknown");
            values.put("objective_target_z", "unknown");
            values.put("objective_target_dimension", "unknown");
        } else {
            values.put("objective_target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("objective_target_y", Integer.toString(roundCoordinate(targetPos.getY())));
            values.put("objective_target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("objective_target_dimension", objective.dimension() == null
                    ? "unknown"
                    : dimensionDisplayName(objective.dimension()));
        }
        return Map.copyOf(values);
    }

    public static String completedObjectiveFallback(QuestDefinition.Objective objective) {
        return switch (objective.type()) {
            case STRUCTURE_VISIT, LOCATION_VISIT -> "Reach {target}.";
            case ITEM_CHECK -> "Collect {objective_count} {objective_item}.";
            case MOB_KILL -> "Defeat {objective_count} {objective_entity}.";
            case BLOCK_BREAK -> "Break {objective_count} {objective_block}.";
            case BLOCK_PLACE -> "Place {objective_count} {objective_block}.";
            case BLOCK_INTERACT -> "Use {objective_count} {objective_block}.";
            case MEMORY_EVENT -> "Resolve {objective_memory}.";
            case TRADE -> "Complete {objective_count} trades.";
            case GIFT -> "Give {objective_count} {objective_item}.";
            case REPUTATION -> "Reach {objective_reputation_level} reputation.";
            case CHOICE -> "Make a quest choice.";
            case FACT -> "Resolve {objective_fact}.";
            case CRITERION -> "Complete {objective_count} quest events.";
            case CAMPAIGN_OUTCOME -> "Resolve the story outcome.";
            case CAMPAIGN_SPEAK -> "Speak with the campaign character.";
            case CAMPAIGN_INVESTIGATE -> "Investigate the evidence.";
            case CAMPAIGN_FOLLOW -> "Follow the campaign character to the destination.";
            case FOLLOWER_ARRIVAL -> "Escort the companion to the destination.";
            case CONDITION -> "Meet the quest condition.";
        };
    }

    public static String resolveQuestText(
            DialogueContext context,
            QuestDefinition.SelectedText selected,
            Map<String, String> replacements) {
        if (selected == null) {
            return "";
        }
        if (context.villager() == null) return resolveQuestText(context.player(), selected, replacements);
        if (!selected.key().isBlank()) {
            return VillagerDialogueResources
                    .message(context, selected.key(), replacements)
                    .orElseGet(() -> VillagerDialogueResources.resolveTemplate(selected.text(), replacements));
        }
        return VillagerDialogueResources.resolveTemplate(selected.text(), replacements);
    }

    public static String resolveQuestText(
            ServerPlayer player,
            QuestDefinition.SelectedText selected,
            Map<String, String> replacements) {
        return QuestTrackerPresenter.resolveText(player, selected, replacements);
    }

    public static String resolveGlobalText(
            ServerPlayer player,
            String key,
            String fallback,
            Map<String, String> replacements) {
        return QuestTrackerPresenter.resolveGlobalText(player, key, fallback, replacements);
    }

    public static String questTitle(
            DialogueContext context,
            QuestDefinition definition,
            Map<String, String> replacements) {
        return resolveQuestText(
                context,
                new QuestDefinition.SelectedText(definition.title(), definition.titleKey()),
                replacements);
    }

    public static String questTitle(
            ServerPlayer player,
            QuestDefinition definition,
            Map<String, String> replacements) {
        return resolveQuestText(
                player,
                new QuestDefinition.SelectedText(definition.title(), definition.titleKey()),
                replacements);
    }

    private static void addIssuerReplacements(
            Map<String, String> values,
            ServerPlayer player,
            QuestProgressSnapshot progress) {
        values.put("issuer", issuerSummaryView(player, progress));
        values.put("issuer_name", issuerName(player, progress));
        values.put("issuer_profession", issuerProfessionName(player, progress));
        values.put("issuer_dimension", issuerDimensionText(player, progress));
        values.put("issuer_location", issuerLocationSummaryView(player, progress));
        values.put("issuer_status", issuerStatusView(player, progress));
        BlockPos issuerPos = issuerPos(player, progress);
        if (issuerPos == null) {
            values.put("issuer_x", "unknown");
            values.put("issuer_y", "unknown");
            values.put("issuer_z", "unknown");
        } else {
            values.put("issuer_x", Integer.toString(issuerPos.getX()));
            values.put("issuer_y", Integer.toString(issuerPos.getY()));
            values.put("issuer_z", Integer.toString(issuerPos.getZ()));
        }
    }

    private static void addObjectiveReplacements(
            Map<String, String> values,
            QuestDefinition definition,
            QuestProgressSnapshot progress,
            QuestDefinition.Objective objective,
            ObjectiveState state) {
        if (objective == null) {
            values.put("objective_id", progress == null ? "" : progress.targetObjectiveId());
            values.put("objective_type", "");
            values.put("objective_item", questItemName(definition, progress));
            values.put("objective_item_id", "");
            values.put("objective_count", "");
            values.put("objective_progress_count", "0");
            values.put("objective_entity", "");
            values.put("objective_block", "");
            values.put("objective_block_id", "");
            values.put("objective_memory", "");
            values.put("objective_memory_id", "");
            values.put("objective_gift_reaction", "");
            values.put("objective_reputation", "0");
            values.put("objective_reputation_level", "");
            values.put("objective_reputation_min", "");
            values.put("objective_reputation_max", "");
            values.put("objective_choice", "");
            values.put("objective_choice_key", "");
            values.put("objective_choice_value", "");
            values.put("objective_fact", "");
            values.put("objective_fact_id", "");
            values.put("objective_fact_key", "");
            values.put("objective_fact_value", "");
            values.put("objective_fact_scope", "");
            values.put("objective_radius", "");
            values.put("objective_complete", "no");
            values.put("objective_progress", "0");
            values.put("objective_target_x", "unknown");
            values.put("objective_target_y", "unknown");
            values.put("objective_target_z", "unknown");
            values.put("objective_target_dimension", "unknown");
            return;
        }

        values.put("objective", objective.id());
        values.put("objective_id", objective.id());
        values.put("objective_type", objective.type().name().toLowerCase(Locale.ROOT));
        ResourceLocation objectiveItem = QuestProgressSnapshot.resolvedItem(objective, progress);
        values.put("objective_item", objectiveItem == null ? questItemName(definition, progress) : itemName(objectiveItem));
        values.put("objective_item_id", objectiveItem == null ? "" : objectiveItem.toString());
        values.put("objective_count", Integer.toString(objective.count()));
        values.put("objective_progress_count", Integer.toString(state.currentCount()));
        values.put("objective_entity", objectiveEntityName(objective));
        values.put("objective_block", objectiveBlockName(objective));
        values.put("objective_block_id", objectiveBlockId(objective));
        values.put("objective_memory", objectiveMemoryName(objective));
        values.put("objective_memory_id", objectiveMemoryId(objective));
        values.put("objective_gift_reaction", objectiveGiftReaction(objective));
        values.put("objective_reputation", Integer.toString(state.reputation()));
        values.put("objective_reputation_level", objectiveReputationLevel(objective));
        values.put("objective_reputation_min", objective.minReputation() == null ? "" : objective.minReputation().toString());
        values.put("objective_reputation_max", objective.maxReputation() == null ? "" : objective.maxReputation().toString());
        values.put("objective_choice", objectiveChoiceValue(objective));
        values.put("objective_choice_key", objective.type() == QuestDefinition.ObjectiveType.CHOICE ? objective.factKey() : "");
        values.put("objective_choice_value", objectiveChoiceValue(objective));
        values.put("objective_fact", objectiveFactName(objective));
        values.put("objective_fact_id", objectiveFactId(objective));
        values.put("objective_fact_key", objective.factKey());
        values.put("objective_fact_value", objectiveFactValue(objective));
        values.put("objective_fact_scope", objective.factScope().name().toLowerCase(Locale.ROOT));
        values.put("objective_radius", Integer.toString(objective.radius()));
        boolean complete = state.complete();
        values.put("objective_complete", complete ? "yes" : "no");
        values.put("objective_progress", String.format(Locale.ROOT, "%.2f", state.progress()));

        BlockPos targetPos = progress != null
                && objective.id().equals(progress.targetObjectiveId())
                ? progress.targetPos()
                : null;
        if (targetPos == null && objective.location() != null) {
            targetPos = objective.location();
        }
        if (targetPos == null) {
            values.put("objective_target_x", "unknown");
            values.put("objective_target_y", "unknown");
            values.put("objective_target_z", "unknown");
            values.put("objective_target_dimension", "unknown");
        } else {
            values.put("objective_target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("objective_target_y", Integer.toString(roundCoordinate(targetPos.getY())));
            values.put("objective_target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("objective_target_dimension", targetDimensionText(progress));
        }
    }

    private static void addIssuerReplacements(
            Map<String, String> values,
            DialogueContext context,
            QuestProgressSnapshot progress) {
        addIssuerReplacements(values, context.player(), progress);
    }

    private static String questItemName(QuestDefinition definition, QuestProgressSnapshot progress) {
        if (definition.target().hasProofItem()) {
            return itemName(definition.target().proofItem());
        }
        for (QuestDefinition.Objective objective : QuestProgressSnapshot.activeObjectives(definition, progress)) {
            ResourceLocation itemId = QuestProgressSnapshot.resolvedItem(objective, progress);
            if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK
                    && itemId != null
                    && (progress == null || !progress.objectiveComplete(objective.id()))) {
                return itemName(itemId);
            }
        }
        for (QuestDefinition.Objective objective : QuestProgressSnapshot.activeObjectives(definition, progress)) {
            ResourceLocation itemId = QuestProgressSnapshot.resolvedItem(objective, progress);
            if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK && itemId != null) {
                return itemName(itemId);
            }
        }
        return "proof";
    }

    public static String itemName(ResourceLocation itemId) {
        if (itemId == null) {
            return "proof";
        }
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .map(item -> new ItemStack(item).getHoverName().getString())
                .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(itemId));
    }

    private static String objectiveEntityName(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.MOB_KILL) {
            return "";
        }
        if (!objective.entityTypes().isEmpty()) {
            ResourceLocation entityType = objective.entityTypes().iterator().next();
            return BuiltInRegistries.ENTITY_TYPE.getOptional(entityType)
                    .map(type -> type.getDescription().getString())
                    .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(entityType));
        }
        if (!objective.entityTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.entityTags().iterator().next());
        }
        return "mobs";
    }

    private static String objectiveBlockName(QuestDefinition.Objective objective) {
        if (objective == null
                || (objective.type() != QuestDefinition.ObjectiveType.BLOCK_BREAK
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_PLACE
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_INTERACT)) {
            return "";
        }
        if (!objective.blockTypes().isEmpty()) {
            ResourceLocation blockType = objective.blockTypes().iterator().next();
            return BuiltInRegistries.BLOCK.getOptional(blockType)
                    .map(block -> block.getName().getString())
                    .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(blockType));
        }
        if (!objective.blockTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.blockTags().iterator().next());
        }
        return "blocks";
    }

    private static String objectiveBlockId(QuestDefinition.Objective objective) {
        if (objective == null
                || (objective.type() != QuestDefinition.ObjectiveType.BLOCK_BREAK
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_PLACE
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_INTERACT)) {
            return "";
        }
        if (!objective.blockTypes().isEmpty()) {
            return objective.blockTypes().iterator().next().toString();
        }
        if (!objective.blockTags().isEmpty()) {
            return "#" + objective.blockTags().iterator().next();
        }
        return "";
    }

    private static String objectiveMemoryName(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.MEMORY_EVENT) {
            return "";
        }
        if (!objective.memoryTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.memoryTags().iterator().next());
        }
        return "event";
    }

    private static String objectiveMemoryId(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.MEMORY_EVENT) {
            return "";
        }
        return objective.memoryTags().isEmpty() ? "" : objective.memoryTags().iterator().next().toString();
    }

    private static String objectiveGiftReaction(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.GIFT) {
            return "";
        }
        return objective.giftReactions().isEmpty() ? "" : objective.giftReactions().iterator().next();
    }

    private static String objectiveReputationLevel(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.REPUTATION) {
            return "";
        }
        return objective.reputationLevels().isEmpty()
                ? "required"
                : objective.reputationLevels().iterator().next().name().toLowerCase(Locale.ROOT);
    }

    private static String objectiveChoiceValue(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.CHOICE) {
            return "";
        }
        return objective.factValues().isEmpty() ? "" : objective.factValues().iterator().next();
    }

    private static String objectiveFactName(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.FACT) {
            return "";
        }
        if (!objective.factTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.factTags().iterator().next());
        }
        if (!objective.factKey().isBlank()) {
            return objective.factKey().replace('_', ' ').replace('.', ' ');
        }
        return "fact";
    }

    private static String objectiveFactId(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.FACT) {
            return "";
        }
        if (!objective.factTags().isEmpty()) {
            return objective.factTags().iterator().next().toString();
        }
        return objective.factKey();
    }

    private static String objectiveFactValue(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.FACT) {
            return "";
        }
        if (!objective.factValues().isEmpty()) {
            return objective.factValues().iterator().next();
        }
        if (objective.factMin() != null && objective.factMax() != null) {
            return objective.factMin() + "-" + objective.factMax();
        }
        if (objective.factMin() != null) {
            return ">=" + objective.factMin();
        }
        if (objective.factMax() != null) {
            return "<=" + objective.factMax();
        }
        return "";
    }

    public static String issuerSummaryView(ServerPlayer player, QuestProgressSnapshot progress) {
        String name = issuerName(player, progress);
        String profession = issuerProfessionName(player, progress);
        if (profession.isBlank() || "villager".equalsIgnoreCase(profession)) {
            return name;
        }
        return name + " the " + profession;
    }

    private static String issuerName(ServerPlayer player, QuestProgressSnapshot progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        }
        if (progress != null && !progress.issuerName().isBlank()) {
            return progress.issuerName();
        }
        return "Unknown villager";
    }

    private static String issuerProfessionName(ServerPlayer player, QuestProgressSnapshot progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "villager");
        }
        if (progress != null && !progress.issuerProfession().isBlank()) {
            ResourceLocation professionId = ResourceLocation.tryParse(progress.issuerProfession());
            if (professionId != null) {
                return VillagerInteractionTextUtil.resourcePathName(professionId);
            }
        }
        return "villager";
    }

    public static String issuerLocationSummaryView(ServerPlayer player, QuestProgressSnapshot progress) {
        Villager live = liveStartedVillager(player, progress);
        if (live != null && live.isAlive()) {
            BlockPos livePos = live.blockPosition();
            return "Current location: " + livePos.getX() + ", " + livePos.getY() + ", " + livePos.getZ()
                    + " in " + dimensionDisplayName(live.level().dimension());
        }
        BlockPos pos = progress == null ? null : progress.issuerPos();
        if (pos == null) {
            return "Last seen location unknown";
        }
        String dimension = issuerDimensionText(player, progress);
        return "Last seen near " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + (dimension.isBlank() || "unknown".equals(dimension) ? "" : " in " + dimension);
    }

    private static BlockPos issuerPos(ServerPlayer player, QuestProgressSnapshot progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return villager.blockPosition();
        }
        return progress == null ? null : progress.issuerPos();
    }

    private static String issuerDimensionText(ServerPlayer player, QuestProgressSnapshot progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return dimensionDisplayName(villager.level().dimension());
        }
        ResourceKey<Level> dimension = progress == null ? null : progress.issuerDimension();
        return dimensionDisplayName(dimension);
    }

    private static String targetDimensionText(QuestProgressSnapshot progress) {
        ResourceKey<Level> dimension = progress == null ? null : progress.targetDimension();
        return dimensionDisplayName(dimension);
    }

    public static String dimensionDisplayName(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return "unknown";
        }
        if (Level.OVERWORLD.equals(dimension)) {
            return "Overworld";
        }
        if (Level.NETHER.equals(dimension)) {
            return "The Nether";
        }
        if (Level.END.equals(dimension)) {
            return "The End";
        }
        String path = dimension.location().getPath().replace('_', ' ');
        if (path.isBlank()) {
            return dimension.location().toString();
        }
        StringBuilder builder = new StringBuilder(path.length());
        boolean capitalizeNext = true;
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (Character.isWhitespace(character)) {
                builder.append(character);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    public static String issuerStatusView(ServerPlayer player, QuestProgressSnapshot progress) {
        if (liveStartedVillager(player, progress) != null) {
            return "current";
        }
        return progress != null
                && (progress.issuerPos() != null || !progress.issuerName().isBlank() || progress.issuerDimension() != null)
                ? "last_seen"
                : "unknown";
    }

    private static Villager liveStartedVillager(ServerPlayer player, QuestProgressSnapshot progress) {
        if (player.level() instanceof ServerLevel level) {
            if (progress == null || progress.startedVillagerId() == null) return null;
            Entity entity = level.getEntity(progress.startedVillagerId());
            return entity instanceof Villager villager ? villager : null;
        }
        return null;
    }

    public static String targetName(QuestDefinition definition) {
        return definition.target().structure() == null
                ? "the target"
                : VillagerInteractionTextUtil.resourcePathName(definition.target().structure());
    }

    private static int roundCoordinate(int value) {
        return Math.round((float) value / APPROXIMATE_COORDINATE_STEP) * APPROXIMATE_COORDINATE_STEP;
    }

    private static int roundDistance(BlockPos origin, BlockPos target) {
        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();
        return Math.max(100, Math.round((float) Math.sqrt((double) dx * dx + (double) dz * dz) / 100.0F) * 100);
    }

    private static String directionPhrase(BlockPos origin, BlockPos target) {
        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();
        String northSouth = dz < -32 ? "north" : dz > 32 ? "south" : "";
        String eastWest = dx > 32 ? "east" : dx < -32 ? "west" : "";
        if (!northSouth.isBlank() && !eastWest.isBlank()) {
            return northSouth + "-" + eastWest;
        }
        if (!northSouth.isBlank()) {
            return northSouth;
        }
        if (!eastWest.isBlank()) {
            return eastWest;
        }
        return "nearby";
    }

    public record ObjectiveState(int currentCount, int reputation, boolean complete, float progress) {
        public static final ObjectiveState EMPTY = new ObjectiveState(0, 0, false, 0.0F);
    }
}
