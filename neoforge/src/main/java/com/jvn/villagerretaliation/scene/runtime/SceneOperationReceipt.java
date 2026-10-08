package com.jvn.villagerretaliation.scene.runtime;

import net.minecraft.nbt.CompoundTag;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import java.util.Set;
import java.util.UUID;

public final class SceneOperationReceipt {
    private final String operationId;
    private final Kind kind;
    private ReceiptState state;
    private final long preparedTime;
    private long appliedTime;
    private long completedTime;
    private String evidence = "";
    private ActionStatus actionStatus;
    private String actionDiagnostic = "";
    private UUID suspensionId;
    private ActionResult.SuspensionKind suspensionKind = ActionResult.SuspensionKind.SCENE;
    private boolean retryOnResume;

    public SceneOperationReceipt(String operationId, Kind kind, long preparedTime) {
        if (operationId == null || operationId.isBlank() || kind == null) throw new IllegalArgumentException("receipt requires stable operation id and kind");
        this.operationId=operationId;this.kind=kind;this.preparedTime=preparedTime;this.state=ReceiptState.PREPARED;
    }
    public String operationId(){return operationId;} public Kind kind(){return kind;} public ReceiptState state(){return state;}
    public long preparedTime(){return preparedTime;} public long appliedTime(){return appliedTime;} public long completedTime(){return completedTime;}
    public String evidence(){return evidence;}
    public void applied(long time,String evidence){if(state==ReceiptState.COMPLETED)return;state=ReceiptState.APPLIED;appliedTime=time;this.evidence=evidence==null?"":evidence;}
    public void completed(long time,String evidence){state=ReceiptState.COMPLETED;completedTime=time;if(evidence!=null&&!evidence.isBlank())this.evidence=evidence;}
    public boolean settled() {
        return state == ReceiptState.COMPLETED || state == ReceiptState.REJECTED;
    }
    public ActionResult actionResult() {
        if (actionStatus != null) return new ActionResult(actionStatus, actionDiagnostic, Set.of(),
                VillagerActionResult.EMPTY, suspensionId, suspensionKind, retryOnResume);
        if (state == ReceiptState.APPLIED || state == ReceiptState.COMPLETED)
            return ActionResult.alreadySatisfied("effect already applied", Set.of(), VillagerActionResult.EMPTY);
        return ActionResult.failed("receipt has no recorded action result", Set.of());
    }
    public void prepareRetry() {
        if (state != ReceiptState.WAITING) throw new IllegalStateException("only a suspended receipt can prepare a retry");
        state = ReceiptState.PREPARED;
        actionStatus = null;
        actionDiagnostic = "";
        suspensionId = null;
        suspensionKind = ActionResult.SuspensionKind.SCENE;
        retryOnResume = false;
        evidence = "";
    }
    public void recordAction(ActionResult result, long time) {
        actionStatus = result.status();
        actionDiagnostic = result.message();
        suspensionId = result.suspensionId();
        suspensionKind = result.suspensionKind();
        retryOnResume = result.retryOnResume();
        if (result.suspended()) {
            state = ReceiptState.WAITING;
            evidence = result.message();
        } else if (result.success()) {
            applied(time, result.message());
            completed(time, result.message());
        } else {
            state = ReceiptState.REJECTED;
            evidence = result.message();
        }
    }
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("OperationId",operationId);t.putString("Kind",kind.name());t.putString("State",state.name());t.putLong("PreparedTime",preparedTime);t.putLong("AppliedTime",appliedTime);t.putLong("CompletedTime",completedTime);t.putString("Evidence",evidence);if(actionStatus!=null)t.putString("ActionStatus",actionStatus.name());t.putString("ActionDiagnostic",actionDiagnostic);if(suspensionId!=null)t.putUUID("Suspension",suspensionId);t.putString("SuspensionKind",suspensionKind.name());t.putBoolean("RetryOnResume",retryOnResume);return t;}
    public static SceneOperationReceipt load(CompoundTag t){SceneOperationReceipt value=new SceneOperationReceipt(t.getString("OperationId"),Kind.valueOf(t.getString("Kind")),t.getLong("PreparedTime"));value.state=ReceiptState.valueOf(t.getString("State"));value.appliedTime=t.getLong("AppliedTime");value.completedTime=t.getLong("CompletedTime");value.evidence=t.getString("Evidence");if(t.contains("ActionStatus"))value.actionStatus=ActionStatus.valueOf(t.getString("ActionStatus"));value.actionDiagnostic=t.getString("ActionDiagnostic");value.suspensionId=t.hasUUID("Suspension")?t.getUUID("Suspension"):null;if(t.contains("SuspensionKind"))value.suspensionKind=ActionResult.SuspensionKind.valueOf(t.getString("SuspensionKind"));value.retryOnResume=t.getBoolean("RetryOnResume");return value;}
    public enum ReceiptState{PREPARED,WAITING,APPLIED,COMPLETED,REJECTED}
    public enum Kind{ITEM_GRANT,LOOT_GRANT,EXPERIENCE_GRANT,REPUTATION_CHANGE,GOSSIP_CHANGE,PROFILE_ATTRIBUTE_CHANGE,COUNTER_INCREMENT,QUEST_TRANSITION,ENCOUNTER_CREATION,DIALOGUE_DELIVERY,ENCOUNTER_PHASE,FACT_CHANGE,SCENE_TRANSITION}
}
