package com.jvn.villagerretaliation.quest.persistence;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import java.util.Map;
import java.util.Set;

/** Applies an authored quest-definition revision to one persisted quest run. */
public final class QuestDefinitionMigration {
    private QuestDefinitionMigration() {
    }

    public static Result apply(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime) {
        if (definition == null || progress == null) {
            return Result.unchanged();
        }
        int authoredRevision = definition.revision().number();
        int savedRevision = progress.definitionRevision();
        if (savedRevision == 0) {
            progress.adoptDefinitionRevision(authoredRevision);
            return new Result(true, false, true, 0, authoredRevision, QuestDefinition.RevisionPolicy.KEEP);
        }
        if (authoredRevision <= savedRevision) {
            return Result.unchanged();
        }

        for (Map.Entry<String, String> alias : definition.revision().objectiveAliases().entrySet()) {
            progress.remapObjective(alias.getKey(), alias.getValue());
        }
        String aliasedStage = definition.revision().stageAliases().get(progress.currentStage());
        if (aliasedStage != null && definition.stages().containsKey(aliasedStage)) {
            progress.setCurrentStage(aliasedStage);
        }

        QuestDefinition.RevisionPolicy policy = definition.revision().activePolicy();
        String currentStage = progress.currentStage();
        switch (policy) {
            case KEEP -> {
                if (!definition.stages().isEmpty() && !definition.stages().containsKey(currentStage)) {
                    progress.setCurrentStage(QuestLifecycleService.initialStage(definition));
                }
            }
            case RESET_STAGE -> {
                QuestDefinition.Stage stage = definition.stages().get(currentStage);
                if (stage == null) {
                    progress.setCurrentStage(QuestLifecycleService.initialStage(definition));
                } else {
                    progress.resetObjectiveProgress(Set.copyOf(stage.objectives()));
                    progress.resetBonusesForStage(stage.id());
                }
            }
            case RESTART -> progress.restartForDefinitionRevision(QuestLifecycleService.initialStage(definition));
            case FAIL -> progress.fail(gameTime, "definition_revision_" + authoredRevision);
        }
        progress.recordDefinitionMigration(authoredRevision, policy, gameTime);
        return new Result(true, policy == QuestDefinition.RevisionPolicy.FAIL, false,
                savedRevision, authoredRevision, policy);
    }

    public record Result(
            boolean changed,
            boolean failed,
            boolean adoptedLegacyRevision,
            int previousRevision,
            int currentRevision,
            QuestDefinition.RevisionPolicy policy
    ) {
        private static Result unchanged() {
            return new Result(false, false, false, 0, 0, QuestDefinition.RevisionPolicy.KEEP);
        }
    }
}
