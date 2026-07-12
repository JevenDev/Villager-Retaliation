package com.jvn.villagerretaliation.scene.runtime;

import net.minecraft.nbt.CompoundTag;

public final class SceneOperationReceipt {
    private final String operationId;
    private final Kind kind;
    private ReceiptState state;
    private final long preparedTime;
    private long appliedTime;
    private long completedTime;
    private String evidence = "";

    public SceneOperationReceipt(String operationId, Kind kind, long preparedTime) {
        if (operationId == null || operationId.isBlank() || kind == null) throw new IllegalArgumentException("receipt requires stable operation id and kind");
        this.operationId=operationId;this.kind=kind;this.preparedTime=preparedTime;this.state=ReceiptState.PREPARED;
    }
    public String operationId(){return operationId;} public Kind kind(){return kind;} public ReceiptState state(){return state;}
    public long preparedTime(){return preparedTime;} public long appliedTime(){return appliedTime;} public long completedTime(){return completedTime;}
    public String evidence(){return evidence;}
    public void applied(long time,String evidence){if(state==ReceiptState.COMPLETED)return;state=ReceiptState.APPLIED;appliedTime=time;this.evidence=evidence==null?"":evidence;}
    public void completed(long time,String evidence){state=ReceiptState.COMPLETED;completedTime=time;if(evidence!=null&&!evidence.isBlank())this.evidence=evidence;}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("OperationId",operationId);t.putString("Kind",kind.name());t.putString("State",state.name());t.putLong("PreparedTime",preparedTime);t.putLong("AppliedTime",appliedTime);t.putLong("CompletedTime",completedTime);t.putString("Evidence",evidence);return t;}
    public static SceneOperationReceipt load(CompoundTag t){SceneOperationReceipt value=new SceneOperationReceipt(t.getString("OperationId"),Kind.valueOf(t.getString("Kind")),t.getLong("PreparedTime"));value.state=ReceiptState.valueOf(t.getString("State"));value.appliedTime=t.getLong("AppliedTime");value.completedTime=t.getLong("CompletedTime");value.evidence=t.getString("Evidence");return value;}
    public enum ReceiptState{PREPARED,APPLIED,COMPLETED}
    public enum Kind{ITEM_GRANT,LOOT_GRANT,EXPERIENCE_GRANT,REPUTATION_CHANGE,GOSSIP_CHANGE,COUNTER_INCREMENT,QUEST_TRANSITION,ENCOUNTER_CREATION,DIALOGUE_DELIVERY,ENCOUNTER_PHASE,FACT_CHANGE,SCENE_TRANSITION}
}
