package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;

/** Pure crash-recovery decision table used by executors and semantic tests. */
public final class SceneRecoveryPolicy {
    private SceneRecoveryPolicy() { }
    public static Decision decide(StepExecutionStatus step,RecoveryMode mode,SceneOperationReceipt.ReceiptState receipt,boolean worldVerified){
        if(step==null)return Decision.BLOCK;
        return switch(step){
            case PENDING->Decision.PREPARE;
            case APPLIED->Decision.VERIFY;
            case COMPLETED,SKIPPED->Decision.DONE;
            case FAILED->Decision.BLOCK;
            case PREPARED,RUNNING->{
                if(mode==RecoveryMode.NATURALLY_IDEMPOTENT||mode==RecoveryMode.WORLD_RECONCILED)yield Decision.RECONCILE;
                if(receipt==SceneOperationReceipt.ReceiptState.APPLIED||receipt==SceneOperationReceipt.ReceiptState.COMPLETED)yield Decision.VERIFY;
                if(worldVerified)yield Decision.MARK_APPLIED;
                yield Decision.BLOCK;
            }
        };
    }
    public enum Decision{PREPARE,RECONCILE,MARK_APPLIED,VERIFY,DONE,BLOCK}
}
