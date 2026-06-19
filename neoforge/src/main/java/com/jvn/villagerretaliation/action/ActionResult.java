package com.jvn.villagerretaliation.action;

import java.util.Set;

public record ActionResult(
        ActionStatus status,
        String message,
        Set<ActionCapability> capabilities,
        VillagerActionResult legacyResult
) {
    public ActionResult {
        status = status == null ? ActionStatus.SKIPPED : status;
        message = message == null ? "" : message;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        legacyResult = legacyResult == null ? VillagerActionResult.EMPTY : legacyResult;
    }

    public boolean success() {
        return this.status == ActionStatus.SUCCESS;
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
