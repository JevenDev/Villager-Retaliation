package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SceneInstance {
    private final UUID id;
    private final ResourceLocation sceneId;
    private int definitionVersion;
    private String definitionHash;
    private final String operationId;
    private final SceneOwner owner;
    private final UUID owningQuestInstance;
    private final RunIdentityKind runIdentityKind;
    private ResourceLocation owningQuestId;
    private final Set<UUID> participants;
    private SceneState state;
    private String currentStep;
    private final Map<String, SceneActorBinding> actorBindings;
    private final Map<String, SceneStepRecord> stepRecords;
    private final long startGameTime;
    private long updateGameTime;
    private int retryCount;
    private String failureCode = "";
    private String diagnostic = "";
    private CleanupStatus cleanupStatus = CleanupStatus.NOT_STARTED;
    private final List<PendingOperation> pendingOperations = new ArrayList<>();
    private final Map<String, SceneOperationReceipt> receipts = new LinkedHashMap<>();
    private CompletionResult completionResult = CompletionResult.NONE;
    private boolean deadlineHandled;

    public SceneInstance(UUID id, CompiledScene definition, String operationId, SceneOwner owner, UUID owningQuestInstance,
            Set<UUID> participants, Map<String, SceneActorBinding> actorBindings, long gameTime) {
        if (id == null || definition == null || operationId == null || operationId.isBlank() || owner == null) {
            throw new IllegalArgumentException("scene instance requires id, definition, stable operation, and owner");
        }
        this.id = id;
        this.sceneId = definition.id();
        this.definitionVersion = definition.definitionVersion();
        this.definitionHash = definition.definitionHash();
        this.operationId = operationId;
        this.owner = owner;
        this.owningQuestInstance = owningQuestInstance;
        this.runIdentityKind = RunIdentityKind.QUEST_RUN;
        this.participants = participants == null ? new LinkedHashSet<>() : new LinkedHashSet<>(participants);
        this.actorBindings = actorBindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(actorBindings);
        this.stepRecords = new LinkedHashMap<>();
        this.state = SceneState.PENDING;
        this.currentStep = definition.entryStep();
        this.startGameTime = gameTime;
        this.updateGameTime = gameTime;
    }

    private SceneInstance(UUID id, ResourceLocation sceneId, int definitionVersion, String definitionHash, String operationId,
            SceneOwner owner, UUID owningQuestInstance, RunIdentityKind runIdentityKind,
            Set<UUID> participants, SceneState state, String currentStep,
            Map<String, SceneActorBinding> actorBindings, Map<String, SceneStepRecord> stepRecords, long startGameTime,
            long updateGameTime) {
        this.id=id; this.sceneId=sceneId; this.definitionVersion=definitionVersion; this.definitionHash=definitionHash;
        this.operationId=operationId; this.owner=owner; this.owningQuestInstance=owningQuestInstance;
        this.runIdentityKind=runIdentityKind==null?RunIdentityKind.QUEST_RUN:runIdentityKind;
        this.participants=new LinkedHashSet<>(participants); this.state=state; this.currentStep=currentStep;
        this.actorBindings=new LinkedHashMap<>(actorBindings); this.stepRecords=new LinkedHashMap<>(stepRecords);
        this.startGameTime=startGameTime; this.updateGameTime=updateGameTime;
    }

    public UUID id(){return id;} public ResourceLocation sceneId(){return sceneId;} public int definitionVersion(){return definitionVersion;}
    public String definitionHash(){return definitionHash;} public String operationId(){return operationId;} public SceneOwner owner(){return owner;}
    public UUID owningQuestInstance(){return owningQuestInstance;} public Set<UUID> participants(){return Set.copyOf(participants);}
    public RunIdentityKind runIdentityKind(){return runIdentityKind;}
    public ResourceLocation owningQuestId(){return owningQuestId;} public void linkQuest(ResourceLocation id){if(owningQuestId==null)owningQuestId=id;}
    public SceneState state(){return state;} public String currentStep(){return currentStep;}
    public Map<String,SceneActorBinding> actorBindings(){return Map.copyOf(actorBindings);}
    public Map<String,SceneStepRecord> stepRecords(){return Map.copyOf(stepRecords);} public long startGameTime(){return startGameTime;}
    public long updateGameTime(){return updateGameTime;} public int retryCount(){return retryCount;}
    public String failureCode(){return failureCode;} public String diagnostic(){return diagnostic;}
    public CleanupStatus cleanupStatus(){return cleanupStatus;} public List<PendingOperation> pendingOperations(){return List.copyOf(pendingOperations);}
    public CompletionResult completionResult(){return completionResult;}
    public boolean deadlineHandled(){return deadlineHandled;}
    public Map<String,SceneOperationReceipt> receipts(){return Map.copyOf(receipts);}
    public SceneOperationReceipt prepareReceipt(String operationId,SceneOperationReceipt.Kind kind,long time){return receipts.computeIfAbsent(operationId,id->new SceneOperationReceipt(id,kind,time));}
    public SceneOperationReceipt receipt(String operationId){return receipts.get(operationId);}

    public SceneStepRecord currentRecord(ResourceLocation type) { return stepRecords.computeIfAbsent(currentStep, id -> new SceneStepRecord(id, type)); }
    public void transition(SceneState next, long time) { state=next; updateGameTime=time; }
    public void advance(String stepId, long time) { currentStep=stepId; state=SceneState.RUNNING; updateGameTime=time; }
    public void block(String code,String message,long time){state=SceneState.BLOCKED;failureCode=code;diagnostic=message;updateGameTime=time;}
    public void fail(String code,String message,long time){state=SceneState.FAILED;failureCode=code;diagnostic=message;completionResult=CompletionResult.FAILURE;updateGameTime=time;}
    public void complete(long time){state=SceneState.COMPLETED;completionResult=CompletionResult.SUCCESS;updateGameTime=time;}
    public void cancel(String code,String message,long time){state=SceneState.CANCELLED;failureCode=code;diagnostic=message;completionResult=CompletionResult.CANCELLED;updateGameTime=time;}
    public void markDeadlineHandled(){deadlineHandled=true;}
    public void retry(){retryCount++; failureCode=""; diagnostic=""; state=SceneState.RUNNING;}
    public void cleanupStatus(CleanupStatus value){cleanupStatus=value;}
    public void reconcileDefinition(CompiledScene definition){definitionVersion=definition.definitionVersion();definitionHash=definition.definitionHash();}
    public void replaceBinding(String alias, SceneActorBinding binding){actorBindings.put(alias,binding);}
    public boolean mergeLaunchContext(Set<UUID> additionalParticipants, Map<String, SceneActorBinding> additionalBindings) {
        boolean changed = additionalParticipants != null && participants.addAll(additionalParticipants);
        if (additionalBindings != null) {
            for (Map.Entry<String, SceneActorBinding> entry : additionalBindings.entrySet()) {
                if (!actorBindings.containsKey(entry.getKey())) {
                    actorBindings.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
        }
        return changed;
    }

    public CompoundTag save() {
        CompoundTag tag=new CompoundTag(); tag.putUUID("InstanceId",id); tag.putString("SceneId",sceneId.toString());
        tag.putInt("DefinitionVersion",definitionVersion);tag.putString("DefinitionHash",definitionHash);tag.putString("OperationId",operationId);
        tag.put("Owner",saveOwner(owner)); if(owningQuestInstance!=null)tag.putUUID("OwningQuestInstance",owningQuestInstance);
        if(owningQuestInstance!=null)tag.putUUID("QuestRunId",owningQuestInstance);
        tag.putString("RunIdentityKind",runIdentityKind.name());
        if(owningQuestId!=null)tag.putString("OwningQuestId",owningQuestId.toString());
        ListTag people=new ListTag();participants.forEach(v->people.add(StringTag.valueOf(v.toString())));tag.put("Participants",people);
        tag.putString("State",state.name());tag.putString("CurrentStep",currentStep);tag.putLong("StartGameTime",startGameTime);
        tag.putLong("UpdateGameTime",updateGameTime);tag.putInt("RetryCount",retryCount);tag.putString("FailureCode",failureCode);
        tag.putString("Diagnostic",diagnostic);tag.putString("CleanupStatus",cleanupStatus.name());tag.putString("CompletionResult",completionResult.name());
        tag.putBoolean("DeadlineHandled",deadlineHandled);
        ListTag bindings=new ListTag();actorBindings.values().forEach(v->bindings.add(v.save()));tag.put("ActorBindings",bindings);
        ListTag records=new ListTag();stepRecords.values().forEach(v->records.add(v.save()));tag.put("StepRecords",records);
        ListTag receiptTags=new ListTag();receipts.values().forEach(v->receiptTags.add(v.save()));tag.put("Receipts",receiptTags);
        ListTag pending=new ListTag();pendingOperations.forEach(v->pending.add(v.save()));tag.put("PendingOperations",pending);return tag;
    }

    public static SceneInstance load(CompoundTag tag) {
        Map<String,SceneActorBinding> bindings=new LinkedHashMap<>();for(Tag raw:tag.getList("ActorBindings",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag value){var b=SceneActorBinding.load(value);bindings.put(b.alias(),b);}
        Map<String,SceneStepRecord> records=new LinkedHashMap<>();for(Tag raw:tag.getList("StepRecords",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag value){var r=SceneStepRecord.load(value);records.put(r.stepId(),r);}
        Set<UUID> people=new LinkedHashSet<>();for(Tag raw:tag.getList("Participants",Tag.TAG_STRING))try{people.add(UUID.fromString(raw.getAsString()));}catch(IllegalArgumentException ignored){}
        SceneInstance value=new SceneInstance(tag.getUUID("InstanceId"),ResourceLocation.parse(tag.getString("SceneId")),tag.getInt("DefinitionVersion"),tag.getString("DefinitionHash"),tag.getString("OperationId"),loadOwner(tag.getCompound("Owner")),tag.hasUUID("OwningQuestInstance")?tag.getUUID("OwningQuestInstance"):null,runIdentityKind(tag),people,SceneState.byName(tag.getString("State")),tag.getString("CurrentStep"),bindings,records,tag.getLong("StartGameTime"),tag.getLong("UpdateGameTime"));
        value.retryCount=tag.getInt("RetryCount");value.failureCode=tag.getString("FailureCode");value.diagnostic=tag.getString("Diagnostic");
        value.owningQuestId=ResourceLocation.tryParse(tag.getString("OwningQuestId"));
        try{value.cleanupStatus=CleanupStatus.valueOf(tag.getString("CleanupStatus"));}catch(IllegalArgumentException ignored){}
        try{value.completionResult=CompletionResult.valueOf(tag.getString("CompletionResult"));}catch(IllegalArgumentException ignored){}
        value.deadlineHandled=tag.getBoolean("DeadlineHandled");
        for(Tag raw:tag.getList("Receipts",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag receipt){var r=SceneOperationReceipt.load(receipt);value.receipts.put(r.operationId(),r);}
        for(Tag raw:tag.getList("PendingOperations",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag operation)value.pendingOperations.add(PendingOperation.load(operation));return value;
    }

    private static CompoundTag saveOwner(SceneOwner owner){CompoundTag t=new CompoundTag();t.putString("Mode",owner.mode().name());if(owner.playerId()!=null)t.putUUID("Player",owner.playerId());if(owner.partyId()!=null)t.putUUID("Party",owner.partyId());if(owner.questInstanceId()!=null)t.putUUID("Quest",owner.questInstanceId());t.putString("World",owner.worldKey());return t;}
    private static SceneOwner loadOwner(CompoundTag t){var mode=com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.valueOf(t.getString("Mode"));return new SceneOwner(mode,t.hasUUID("Player")?t.getUUID("Player"):null,t.hasUUID("Party")?t.getUUID("Party"):null,t.hasUUID("Quest")?t.getUUID("Quest"):null,t.getString("World"));}
    private static RunIdentityKind runIdentityKind(CompoundTag tag){try{return RunIdentityKind.valueOf(tag.getString("RunIdentityKind"));}catch(IllegalArgumentException ignored){return RunIdentityKind.QUEST_RUN;}}

    public enum CleanupStatus{NOT_STARTED,RUNNING,COMPLETE,BLOCKED} public enum CompletionResult{NONE,SUCCESS,FAILURE,CANCELLED}
    public enum RunIdentityKind{QUEST_RUN,LEGACY_OWNER}
    public record PendingOperation(String operationId,String kind,String state){public PendingOperation{operationId=operationId==null?"":operationId;kind=kind==null?"":kind;state=state==null?"prepared":state;}CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("Id",operationId);t.putString("Kind",kind);t.putString("State",state);return t;}static PendingOperation load(CompoundTag t){return new PendingOperation(t.getString("Id"),t.getString("Kind"),t.getString("State"));}}
}
