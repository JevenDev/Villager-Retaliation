package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SceneActionSuspension {
    private SceneActionSuspension() {
    }

    public static ActionResult poll(SceneSavedData data, ActionResult waiting) {
        if (!waiting.suspended()) return waiting;
        if (waiting.suspensionId() == null) return unavailable("saved suspension target is missing");
        if (waiting.suspensionKind() == ActionResult.SuspensionKind.CAMPAIGN_REWARD) return waiting;
        if (waiting.suspensionKind() == ActionResult.SuspensionKind.CONTINUATION) {
            SceneContinuation continuation = data.continuation(waiting.suspensionId()).orElse(null);
            if (continuation == null) return unavailable("saved continuation is unavailable");
            return continuation.completionReceipt()
                    ? new ActionResult(continuation.actionStatus(), continuation.actionDiagnostic(),
                            Set.of(), VillagerActionResult.EMPTY) : waiting;
        }
        SceneInstance scene = data.get(waiting.suspensionId()).orElse(null);
        if (scene == null) return unavailable("saved scene is unavailable");
        if (!scene.state().terminal()) return waiting;
        return scene.completionResult() == SceneInstance.CompletionResult.SUCCESS
                ? ActionResult.success(VillagerActionResult.EMPTY, Set.of())
                : ActionResult.failed("scene " + scene.completionResult(), Set.of());
    }

    public static boolean referencesScene(SceneSavedData data, ActionResult waiting, UUID sceneId) {
        if (!waiting.suspended() || waiting.suspensionId() == null) return false;
        if (waiting.suspensionKind() == ActionResult.SuspensionKind.SCENE) return sceneId.equals(waiting.suspensionId());
        if (waiting.suspensionKind() != ActionResult.SuspensionKind.CONTINUATION) return false;
        SceneContinuation continuation = data.continuation(waiting.suspensionId()).orElse(null);
        return continuation != null && sceneId.equals(continuation.sceneInstanceId());
    }

    public static boolean dependsOnScene(SceneSavedData data, ActionResult waiting, UUID sceneId) {
        return dependsOn(data, waiting, new Target(ActionResult.SuspensionKind.SCENE, sceneId));
    }

    public static boolean dependsOnContinuation(SceneSavedData data, ActionResult waiting, UUID continuationId) {
        return dependsOn(data, waiting, new Target(ActionResult.SuspensionKind.CONTINUATION, continuationId));
    }

    private static boolean dependsOn(SceneSavedData data, ActionResult waiting, Target expected) {
        ArrayDeque<Target> pending = new ArrayDeque<>();
        Set<Target> visited = new HashSet<>();
        enqueue(pending, waiting);
        while (!pending.isEmpty()) {
            Target target = pending.removeFirst();
            if (!visited.add(target)) continue;
            if (target.equals(expected)) return true;
            if (target.kind() == ActionResult.SuspensionKind.SCENE) {
                SceneInstance scene = data.get(target.id()).orElse(null);
                if (scene != null && !scene.state().terminal()) {
                    scene.receipts().values().forEach(receipt -> enqueue(pending, receipt.actionResult()));
                }
            } else if (target.kind() == ActionResult.SuspensionKind.CONTINUATION) {
                SceneContinuation continuation = data.continuation(target.id()).orElse(null);
                if (continuation == null || continuation.completionReceipt() || continuation.rewardOperation() != null) continue;
                if (continuation.dependencyContinuationId() != null) {
                    pending.addLast(new Target(ActionResult.SuspensionKind.CONTINUATION, continuation.dependencyContinuationId()));
                } else if (continuation.sceneInstanceId() != null) {
                    pending.addLast(new Target(ActionResult.SuspensionKind.SCENE, continuation.sceneInstanceId()));
                }
            }
        }
        return false;
    }

    private static void enqueue(ArrayDeque<Target> pending, ActionResult result) {
        if (result.suspended() && result.suspensionId() != null) pending.addLast(new Target(result.suspensionKind(), result.suspensionId()));
    }

    private static ActionResult unavailable(String diagnostic) {
        return ActionResult.unavailable(diagnostic, Set.of(), VillagerActionResult.EMPTY);
    }

    private record Target(ActionResult.SuspensionKind kind, UUID id) {
    }
}
