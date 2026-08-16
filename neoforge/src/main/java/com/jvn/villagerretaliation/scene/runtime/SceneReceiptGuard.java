package com.jvn.villagerretaliation.scene.runtime;

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
    public enum Status{APPLIED,ALREADY_APPLIED,AMBIGUOUS_PREPARED}
    public record ApplyResult(Status status,SceneOperationReceipt receipt){}
}
