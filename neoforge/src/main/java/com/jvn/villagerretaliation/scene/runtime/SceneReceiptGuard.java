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
        ActionResult result = effect.get();
        receipt.recordAction(result, context.gameTime());
        context.repository().changed();
        return new ActionApplyResult(result, receipt, false);
    }

    public record ActionApplyResult(ActionResult outcome, SceneOperationReceipt receipt, boolean ambiguous) {
    }

    public enum Status{APPLIED,ALREADY_APPLIED,AMBIGUOUS_PREPARED}
    public record ApplyResult(Status status,SceneOperationReceipt receipt){}
}
