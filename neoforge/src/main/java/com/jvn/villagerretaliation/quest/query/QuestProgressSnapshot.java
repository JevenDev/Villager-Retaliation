package com.jvn.villagerretaliation.quest.query;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressState;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record QuestProgressSnapshot(QuestState state, UUID questRunId, UUID startedVillagerId,
        long startedGameTime, long completedGameTime, int completionCount, String consumedReason,
        String currentStage, boolean visitedTarget, ResourceKey<Level> targetDimension, BlockPos targetPos,
        String targetObjectiveId, String issuerName, String issuerProfession, ResourceKey<Level> issuerDimension,
        BlockPos issuerPos, Map<String, ResourceLocation> resolvedObjectiveItems, Set<String> completedObjectives,
        Map<String, Long> timerRemaining, Set<String> activeStages) {
    public QuestProgressSnapshot(QuestState state, UUID questRunId, UUID startedVillagerId,
            long startedGameTime, long completedGameTime, int completionCount, String consumedReason,
            String currentStage, boolean visitedTarget, ResourceKey<Level> targetDimension, BlockPos targetPos,
            String targetObjectiveId, String issuerName, String issuerProfession, ResourceKey<Level> issuerDimension,
            BlockPos issuerPos, Map<String, ResourceLocation> resolvedObjectiveItems, Set<String> completedObjectives,
            Map<String, Long> timerRemaining) {
        this(state, questRunId, startedVillagerId, startedGameTime, completedGameTime, completionCount,
                consumedReason, currentStage, visitedTarget, targetDimension, targetPos, targetObjectiveId,
                issuerName, issuerProfession, issuerDimension, issuerPos, resolvedObjectiveItems, completedObjectives,
                timerRemaining, Set.of(currentStage));
    }

    public QuestProgressSnapshot(QuestState state, UUID questRunId, UUID startedVillagerId,
        long startedGameTime, long completedGameTime, int completionCount, String consumedReason,
        String currentStage, boolean visitedTarget, ResourceKey<Level> targetDimension, BlockPos targetPos,
        String targetObjectiveId, String issuerName, String issuerProfession, ResourceKey<Level> issuerDimension,
        BlockPos issuerPos, Map<String, ResourceLocation> resolvedObjectiveItems, Set<String> completedObjectives) {
        this(state, questRunId, startedVillagerId, startedGameTime, completedGameTime, completionCount,
                consumedReason, currentStage, visitedTarget, targetDimension, targetPos, targetObjectiveId,
                issuerName, issuerProfession, issuerDimension, issuerPos, resolvedObjectiveItems, completedObjectives, Map.of());
    }
    public QuestProgressSnapshot {
        targetPos = targetPos == null ? null : targetPos.immutable();
        issuerPos = issuerPos == null ? null : issuerPos.immutable();
        resolvedObjectiveItems = Map.copyOf(resolvedObjectiveItems);
        completedObjectives = Set.copyOf(completedObjectives);
        timerRemaining = Map.copyOf(timerRemaining);
        activeStages = Set.copyOf(activeStages);
    }

    public static QuestProgressSnapshot capture(QuestProgressState progress) {
        return capture(progress, null);
    }

    public static QuestProgressSnapshot capture(QuestProgressState progress, QuestDefinition definition) {
        if (progress == null) return null;
        var items = new LinkedHashMap<String, ResourceLocation>();
        var completed = new LinkedHashSet<String>();
        if (definition != null) for (var objective : definition.objectives()) {
            var item = progress.resolvedObjectiveItem(objective.id());
            if (item != null) items.put(objective.id(), item);
            if (progress.objectiveComplete(objective.id())) completed.add(objective.id());
        }
        return new QuestProgressSnapshot(progress.state(), progress.questRunId(), progress.startedVillagerId(),
                progress.startedGameTime(), progress.completedGameTime(), progress.completionCount(), progress.consumedReason(),
                progress.currentStage(), progress.visitedTarget(), progress.targetDimension(), progress.targetPos(), progress.targetObjectiveId(),
                progress.issuerName(), progress.issuerProfession(), progress.issuerDimension(), progress.issuerPos(), items, completed, definition == null ? Map.of() : progress.remainingTimers(definition), progress.activeStages());
    }

    public boolean objectiveComplete(String id) { return id != null && completedObjectives.contains(id); }

    public static ResourceLocation resolvedItem(QuestDefinition.Objective objective, QuestProgressSnapshot progress) {
        if (objective == null) return null;
        return objective.usesRandomItemSelection()
                ? progress == null ? null : progress.resolvedObjectiveItems().get(objective.id())
                : objective.item();
    }

    public static List<QuestDefinition.Objective> activeObjectives(QuestDefinition definition, QuestProgressSnapshot progress) {
        if (definition == null || definition.objectives().isEmpty()) return List.of();
        return progress == null || definition.stages().isEmpty() ? definition.objectives()
                : progress.activeStages().stream().flatMap(stage -> QuestObjectiveQuery.stageObjectives(definition, stage).stream()).distinct().toList();
    }
}
