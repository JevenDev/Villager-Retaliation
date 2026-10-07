package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import java.util.Set;
import java.util.function.Supplier;

public final class SceneReceiptGuard {
    private SceneReceiptGuard() { }
    public static ApplyResult applyOnce(SceneExecutionContext context,String suffix,SceneOperationReceipt.Kind kind,
            Runnable effect,String evidence){
        SceneOperationReceipt receipt=context.prepareReceipt(suffix,kind);
        if(receipt.state()==SceneOperationReceipt.ReceiptState.COMPLETED||receipt.state()==SceneOperationReceipt.ReceiptState.APPLIED)
            return new ApplyResult(Status.ALREADY_APPLIED,receipt);
        if(!context.preparedThisSession())return new ApplyResult(Status.AMBIGUOUS_PREPARED,receipt);
        effect.run();receipt.applied(context.gameTime(),evidence);context.repository().changed();return new ApplyResult(Status.APPLIED,receipt);
    }
    public static ActionApplyResult applyActionOnce(SceneExecutionContext context, String suffix,
            SceneOperationReceipt.Kind kind, Supplier<ActionResult> effect) {
        SceneOperationReceipt receipt = context.prepareReceipt(suffix, kind);
        if (receipt.actionResult().suspended()) {
            ActionResult waiting = receipt.actionResult();
            ActionResult result = SceneActionSuspension.poll(context.repository(), waiting);
            if (result.suspended()) return new ActionApplyResult(result, receipt, false);
            if (result.success() && waiting.retryOnResume()) result = effect.get();
            return recordAction(context, receipt, result);
        }
        if (receipt.state() != SceneOperationReceipt.ReceiptState.PREPARED) {
            if (receipt.state() == SceneOperationReceipt.ReceiptState.APPLIED) {
                receipt.completed(context.gameTime(), receipt.evidence());
                context.repository().changed();
            }
            return new ActionApplyResult(receipt.actionResult(), receipt, false);
        }
        if (!context.preparedThisSession()) {
            return new ActionApplyResult(ActionResult.failed("effect receipt is ambiguous after reload", Set.of()),
                    receipt, true);
        }
        context.repository().changed();
        return recordAction(context, receipt, effect.get());
    }

    private static ActionApplyResult recordAction(SceneExecutionContext context,
            SceneOperationReceipt receipt, ActionResult result) {
        if (result.suspended() && SceneActionSuspension.dependsOnScene(context.repository(), result, context.instance().id())) {
            result = ActionResult.failed("cyclic scene action suspension", result.capabilities());
        }
        receipt.recordAction(result, context.gameTime());
        context.repository().changed();
        return new ActionApplyResult(result, receipt, false);
    }

    public record ActionApplyResult(ActionResult outcome, SceneOperationReceipt receipt, boolean ambiguous) {
    }

    public enum Status{APPLIED,ALREADY_APPLIED,AMBIGUOUS_PREPARED}
    public record ApplyResult(Status status,SceneOperationReceipt receipt){}
}
