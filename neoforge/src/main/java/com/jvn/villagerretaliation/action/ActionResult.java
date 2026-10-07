package com.jvn.villagerretaliation.action;

import java.util.Set;
import java.util.UUID;

public record ActionResult(
        ActionStatus status,
        String message,
        Set<ActionCapability> capabilities,
        VillagerActionResult legacyResult,
        UUID suspensionId,
        SuspensionKind suspensionKind,
        boolean retryOnResume
) {
    public ActionResult {
        status = status == null ? ActionStatus.SKIPPED : status;
        message = message == null ? "" : message;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        legacyResult = legacyResult == null ? VillagerActionResult.EMPTY : legacyResult;
        suspensionKind = suspensionKind == null ? SuspensionKind.SCENE : suspensionKind;
    }

    public ActionResult(ActionStatus status, String message, Set<ActionCapability> capabilities,
            VillagerActionResult legacyResult, UUID suspensionId) {
        this(status, message, capabilities, legacyResult, suspensionId, SuspensionKind.SCENE, false);
    }

    public ActionResult(ActionStatus status, String message, Set<ActionCapability> capabilities,
            VillagerActionResult legacyResult) {
        this(status, message, capabilities, legacyResult, null);
    }

    public boolean success() {
        return this.status == ActionStatus.SUCCESS || this.status == ActionStatus.ALREADY_SATISFIED;
    }

    public boolean suspended() {
        return this.status == ActionStatus.SUSPENDED;
    }

    public static ActionResult alreadySatisfied(String message, Set<ActionCapability> capabilities,
            VillagerActionResult legacyResult) {
        return new ActionResult(ActionStatus.ALREADY_SATISFIED, message, capabilities, legacyResult);
    }

    public static ActionResult unavailable(String message, Set<ActionCapability> capabilities,
            VillagerActionResult legacyResult) {
        return new ActionResult(ActionStatus.UNAVAILABLE, message, capabilities, legacyResult);
    }

    public static ActionResult suspended(UUID suspensionId, Set<ActionCapability> capabilities) {
        return new ActionResult(ActionStatus.SUSPENDED, "waiting for scene", capabilities,
                VillagerActionResult.success(), suspensionId);
    }

    public static ActionResult suspendedContinuation(UUID continuationId, String message,
            Set<ActionCapability> capabilities, VillagerActionResult legacyResult) {
        return new ActionResult(ActionStatus.SUSPENDED, message, capabilities, legacyResult,
                continuationId, SuspensionKind.CONTINUATION, true);
    }

    public enum SuspensionKind {
        SCENE,
        CONTINUATION
    }

    public boolean skipped() {
        return this.status == ActionStatus.SKIPPED;
    }

    public boolean failed() {
        return this.status == ActionStatus.FAILED;
    }

    public static ActionResult success(VillagerActionResult legacyResult, Set<ActionCapability> capabilities) {
        return new ActionResult(ActionStatus.SUCCESS, "action ran", capabilities, legacyResult);
    }

    public static ActionResult skipped(String message, Set<ActionCapability> capabilities) {
        return skipped(message, capabilities, VillagerActionResult.EMPTY);
    }

    public static ActionResult skipped(
            String message,
            Set<ActionCapability> capabilities,
            VillagerActionResult legacyResult) {
        return new ActionResult(ActionStatus.SKIPPED, message, capabilities, legacyResult);
    }

    public static ActionResult failed(String message, Set<ActionCapability> capabilities) {
        return new ActionResult(ActionStatus.FAILED, message, capabilities, VillagerActionResult.EMPTY);
    }
}
