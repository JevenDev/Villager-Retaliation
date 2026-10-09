package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutor;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import java.io.IOException;

public final class CampaignFollowStepExecutor implements SceneStepExecutor {
    private final SceneStepExecutor movement;

    public CampaignFollowStepExecutor(SceneStepExecutor movement) { this.movement = movement; }

    @Override public RecoveryMode recoveryMode() { return RecoveryMode.WORLD_RECONCILED; }

    @Override public SceneStepResult prepare(SceneExecutionContext context) {
        var prepared = CampaignFollowService.prepare(context);
        if (prepared.outcome() != SceneStepResult.Outcome.READY) return stopped(context, prepared);
        var fields = java.util.List.of("destination_dimension", "destination_x", "destination_y", "destination_z", "deadline", "follow_pause_ticks");
        var saved = context.record().durableValues();
        boolean existing = fields.stream().anyMatch(saved::containsKey);
        if (existing && !fields.stream().allMatch(saved::containsKey))
            return stopped(context, SceneStepResult.block("follow_preparation_incomplete", "The saved journey movement record is incomplete."));
        var result = existing ? SceneStepResult.ready() : movement.prepare(context);
        if (result.outcome() != SceneStepResult.Outcome.READY) return stopped(context, result);
        if (!existing) context.record().putDurableValue("follow_pause_ticks", Long.toString(pausedTicks(context)));
        var repaired = CampaignMovementRepairs.reconcile(context);
        if (repaired.outcome() != SceneStepResult.Outcome.READY) return stopped(context, repaired);
        var movementIssue = movementIssue(context);
        if (!movementIssue.isBlank()) return stopped(context, SceneStepResult.block("follow_preparation_changed", movementIssue));
        context.repository().changed();
        try {
            context.repository().persistCampaignHandoff(context.server().overworld());
            return result;
        } catch (IOException exception) {
            return CampaignFollowService.waitFor(context, "Journey scene preparation could not be saved: " + exception.getMessage());
        }
    }

    @Override public SceneStepResult apply(SceneExecutionContext context) {
        var ready = movementReady(context);
        if (ready.outcome() != SceneStepResult.Outcome.READY) return stopped(context, ready);
        var observed = CampaignFollowService.observe(context);
        if (observed.outcome() != SceneStepResult.Outcome.READY) return stopped(context, observed);
        var deadline = context.record().durableValues().get("deadline");
        if (deadline == null) return stopped(context, SceneStepResult.block("follow_deadline_missing", "The prepared movement deadline is missing."));
        try {
            long preparedPause = Long.parseLong(context.record().durableValues().get("follow_pause_ticks"));
            long paused = pausedTicks(context);
            long limit = Long.parseLong(deadline);
            if (preparedPause < 0 || preparedPause > paused || limit < 0)
                return stopped(context, SceneStepResult.block("follow_deadline_invalid", "The prepared movement clock is inconsistent."));
            long extension = paused - preparedPause;
            limit = extension > Long.MAX_VALUE - limit ? Long.MAX_VALUE : limit + extension;
            if (context.gameTime() >= limit)
                return stopped(context, SceneStepResult.fail("follow_timeout", "The accompanied journey exceeded its authored movement deadline."));
        } catch (NumberFormatException exception) {
            return stopped(context, SceneStepResult.block("follow_deadline_invalid", "The prepared movement deadline is unreadable."));
        }
        var movementResult = movement.apply(context);
        return movementResult.outcome() == SceneStepResult.Outcome.APPLIED
                ? SceneStepResult.waitUntil(context.gameTime() + context.step().follow().pollTicks(), "Waiting for the enrolled players to reach the destination.")
                : stopped(context, movementResult);
    }

    @Override public SceneStepResult reconcile(SceneExecutionContext context) { return apply(context); }

    @Override public SceneStepResult verify(SceneExecutionContext context) {
        var ready = movementReady(context);
        if (ready.outcome() != SceneStepResult.Outcome.READY) return stopped(context, ready);
        var result = CampaignFollowService.observe(context);
        return result.outcome() == SceneStepResult.Outcome.APPLIED ? SceneStepResult.complete() : stopped(context, result);
    }

    @Override public SceneStepResult cancel(SceneExecutionContext context) { return CampaignFollowService.cancel(context); }

    @Override public SceneStepResult cleanup(SceneExecutionContext context) { return CampaignFollowService.cancel(context); }

    private static SceneStepResult movementReady(SceneExecutionContext context) {
        var refreshed = CampaignFollowService.refreshDestination(context);
        if (refreshed.outcome() != SceneStepResult.Outcome.READY) return refreshed;
        var repaired = CampaignMovementRepairs.reconcile(context);
        if (repaired.outcome() != SceneStepResult.Outcome.READY) return repaired;
        String issue = movementIssue(context);
        return issue.isBlank() ? SceneStepResult.ready() : SceneStepResult.block("follow_preparation_changed", issue);
    }

    private static String movementIssue(SceneExecutionContext context) {
        var journey = CampaignFollowSavedData.get(context.server().overworld()).journeys()
                .get(new CampaignFollowJourney.Key(context.instance().id(), context.step().id()));
        if (journey == null) return "The saved journey is unavailable. Restore its campaign save.";
        var values = context.record().durableValues();
        return journey.dimension().toString().equals(values.get("destination_dimension"))
                && Integer.toString(journey.destination().getX()).equals(values.get("destination_x"))
                && Integer.toString(journey.destination().getY()).equals(values.get("destination_y"))
                && Integer.toString(journey.destination().getZ()).equals(values.get("destination_z"))
                ? "" : "The movement destination no longer matches the saved campaign journey.";
    }

    private static long pausedTicks(SceneExecutionContext context) {
        return Math.max(0L, context.instance().timeoutStartGameTime() - context.instance().startGameTime());
    }

    private static SceneStepResult stopped(SceneExecutionContext context, SceneStepResult result) {
        if (result.outcome() == SceneStepResult.Outcome.BLOCK || result.outcome() == SceneStepResult.Outcome.FAIL
                || result.outcome() == SceneStepResult.Outcome.APPLIED)
            CampaignFollowService.stop(context.server(), context.instance(), context.definition());
        if (result.outcome() == SceneStepResult.Outcome.FAIL) {
            var cancelled = CampaignFollowService.cancel(context);
            if (cancelled.outcome() != SceneStepResult.Outcome.COMPLETE) return cancelled;
        }
        return result;
    }
}
