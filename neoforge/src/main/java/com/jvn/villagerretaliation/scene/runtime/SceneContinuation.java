package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.util.NbtDataUtil;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SceneContinuation {
    private final UUID id;
    private UUID sceneInstanceId;
    private ResourceLocation campaignId;
    private UUID dependencyContinuationId;
    private boolean retrySuspendedAction;
    private boolean campaignRecovery;
    private String recoveryFingerprint = "";
    private UUID rewardOperation;
    private Tag unreadableRecovery;
    private Tag unreadableRecoveryCursor;
    private Tag unreadableConversationRecovery;
    private String recoveryPersistenceIssue = "";
    private int preparedRecoveryAction = -1;

    private final UUID playerId;
    private final UUID providerId;
    private final ResourceLocation questId;
    private UUID questRunId;
    private final String sourcePointer;
    private final List<VillagerActionDefinition> actions;
    private final Map<String, String> replacements;
    private int nextActionIndex;
    private State state = State.WAITING;
    private SceneInstance.CompletionResult sceneResult = SceneInstance.CompletionResult.NONE;
    private boolean completionReceipt;
    private ActionStatus actionStatus = ActionStatus.SUSPENDED;
    private String actionDiagnostic = "";
    private DialogueResume dialogueResume;
    private CompoundTag campaignConsent = new CompoundTag();
    private CompoundTag conversationEffect = new CompoundTag();
    private CompoundTag decisionParticipants = new CompoundTag();
    private final Map<Integer, CompoundTag> unreadableCampaignActions = new LinkedHashMap<>();

    public CompoundTag conversationEffect() { return conversationEffect.copy(); }
    public void conversationEffect(CompoundTag source) { conversationEffect = source.copy(); }
    public boolean actionRecovery() { return campaignRecovery || !conversationEffect.isEmpty(); }

    public CompoundTag decisionParticipants() { return decisionParticipants.copy(); }
    public void decisionParticipants(CompoundTag participants) { decisionParticipants = participants.copy(); }

    public CompoundTag campaignConsent() { return campaignConsent.copy(); }
    public void campaignConsent(CompoundTag consent) { campaignConsent = consent.copy(); }

    public boolean pauseForConsent(String diagnostic) {
        if (completionReceipt || actionDiagnostic.equals(diagnostic)) return false;
        actionDiagnostic = diagnostic;
        actionStatus = ActionStatus.SUSPENDED;
        return true;
    }

    public SceneContinuation(UUID id, UUID sceneInstanceId, UUID playerId, UUID providerId,
            ResourceLocation questId, UUID questRunId, String sourcePointer,
            List<VillagerActionDefinition> actions, int nextActionIndex, Map<String, String> replacements) {
        this.id = id;
        this.sceneInstanceId = sceneInstanceId;
        this.playerId = playerId;
        this.providerId = providerId;
        this.questId = questId;
        this.questRunId = questRunId;
        this.sourcePointer = sourcePointer == null ? "" : sourcePointer;
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        this.nextActionIndex = Math.max(0, nextActionIndex);
        this.replacements = replacements == null ? new LinkedHashMap<>() : new LinkedHashMap<>(replacements);
    }

    public boolean hasUnreadableActions() { return !unreadableCampaignActions.isEmpty(); }

    public UUID id() { return id; }
    public UUID sceneInstanceId() { return sceneInstanceId; }
    public ResourceLocation campaignId() { return campaignId; }
    public void bindCampaign(ResourceLocation id) { if (campaignId == null) campaignId = id; }
    public UUID dependencyContinuationId() { return dependencyContinuationId; }
    public boolean retrySuspendedAction() { return retrySuspendedAction; }
    public boolean campaignRecovery() { return campaignRecovery; }
    public int preparedRecoveryAction() { return preparedRecoveryAction; }
    public void prepareRecoveryAction(int index) { preparedRecoveryAction = index; }
    public void clearPreparedRecoveryAction() { preparedRecoveryAction = -1; }
    public void bindQuestRun(UUID run) { if (questRunId == null) questRunId = run; }

    public String recoveryFingerprint() { return recoveryFingerprint; }
    public void bindRecoveryFingerprint(String fingerprint) { if (recoveryFingerprint.isBlank()) recoveryFingerprint = fingerprint; }
    public UUID rewardOperation() { return rewardOperation; }
    public String recoveryIssue() { return unreadableRecovery == null && unreadableConversationRecovery == null ? "" : "Saved campaign action recovery is unreadable. Restore compatible scene data."; }

    public boolean recoveryPersistencePending() { return !recoveryPersistenceIssue.isBlank(); }
    public void recoveryPersistenceIssue(String issue) { recoveryPersistenceIssue = issue == null ? "" : issue; }

    public void enableCampaignRecovery(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) throw new IllegalArgumentException("campaign recovery requires its quest fingerprint");
        if (!campaignRecovery) recoveryFingerprint = fingerprint;
        campaignRecovery = true;
    }

    public void waitForReward(UUID operation, String fingerprint) {
        if (operation == null) throw new IllegalArgumentException("reward recovery requires its operation");
        enableCampaignRecovery(fingerprint);
        rewardOperation = operation;
        dependencyContinuationId = null;
        retrySuspendedAction = true;
        state = State.WAITING;
        actionStatus = ActionStatus.SUSPENDED;
        actionDiagnostic = "Campaign reward is waiting for durable delivery.";
    }

    public UUID playerId() { return playerId; }
    public UUID providerId() { return providerId; }
    public ResourceLocation questId() { return questId; }
    public UUID questRunId() { return questRunId; }
    public String sourcePointer() { return sourcePointer; }
    public List<VillagerActionDefinition> actions() { return actions; }
    public int nextActionIndex() { return nextActionIndex; }
    public State state() { return state; }
    public SceneInstance.CompletionResult sceneResult() { return sceneResult; }
    public boolean completionReceipt() { return completionReceipt && !recoveryPersistencePending() && recoveryIssue().isBlank(); }
    public Map<String, String> replacements() { return replacements; }

    public void advance() { nextActionIndex++; }
    public void nextActionIndex(int value) { nextActionIndex = value; }
    public ActionStatus actionStatus() { return recoveryPersistencePending() ? ActionStatus.SUSPENDED : actionStatus; }
    public String actionDiagnostic() { return recoveryPersistencePending() ? recoveryPersistenceIssue : actionDiagnostic; }
    public DialogueResume dialogueResume() { return dialogueResume; }
    public void dialogueResume(DialogueResume value) { dialogueResume = value; }

    public void finish(ActionResult result) {
        complete();
        actionStatus = result.status();
        actionDiagnostic = result.message();
    }

    public void waitOn(UUID sceneId) {
        waitOn(sceneId, null, false);
    }

    public void waitOn(UUID sceneId, UUID dependencyId, boolean retryAction) {
        sceneInstanceId = sceneId;
        rewardOperation = null;
        dependencyContinuationId = dependencyId;
        retrySuspendedAction = retryAction;
        state = State.WAITING;
        actionStatus = ActionStatus.SUSPENDED;
        actionDiagnostic = "";
        sceneResult = SceneInstance.CompletionResult.NONE;
    }

    public void resuming(SceneInstance.CompletionResult result) {
        state = State.RESUMING;
        sceneResult = result;
    }

    public void complete() {
        state = State.COMPLETED;
        completionReceipt = true;
        actionStatus = ActionStatus.SUCCESS;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        if (sceneInstanceId != null) tag.putUUID("Scene", sceneInstanceId);
        if (campaignRecovery) {
            var recovery = new CompoundTag();
            recovery.putInt("DataVersion", 1);
            recovery.putString("QuestFingerprint", recoveryFingerprint);
            recovery.putInt("PreparedAction", preparedRecoveryAction);
            if (rewardOperation != null) recovery.putUUID("RewardOperation", rewardOperation);
            tag.put("CampaignRecovery", unreadableRecovery == null ? recovery : unreadableRecovery.copy());
        }

        if (!conversationEffect.isEmpty()) {
            tag.put("ConversationEffect", conversationEffect.copy());
            var recovery = new CompoundTag();
            recovery.putInt("DataVersion", 1);
            recovery.putString("QuestFingerprint", recoveryFingerprint);
            recovery.putInt("PreparedAction", preparedRecoveryAction);
            tag.put("ConversationRecovery", unreadableConversationRecovery == null ? recovery : unreadableConversationRecovery.copy());
        }
        if (dependencyContinuationId != null) tag.putUUID("DependencyContinuation", dependencyContinuationId);
        tag.putBoolean("RetrySuspendedAction", retrySuspendedAction);
        tag.putUUID("Player", playerId);
        if (providerId != null) tag.putUUID("Provider", providerId);
        if (questId != null) tag.putString("Quest", questId.toString());
        if (campaignId != null) tag.putString("Campaign", campaignId.toString());
        if (questRunId != null) tag.putUUID("QuestRun", questRunId);
        tag.putString("Source", sourcePointer);
        tag.putInt("Next", nextActionIndex);
        if (unreadableRecovery != null || unreadableConversationRecovery != null) {
            if (unreadableRecoveryCursor == null) tag.remove("Next");
            else tag.put("Next", unreadableRecoveryCursor.copy());
        }
        tag.putString("State", state.name());
        tag.putString("SceneResult", sceneResult.name());
        tag.putBoolean("CompletionReceipt", completionReceipt);
        tag.putString("ActionStatus", actionStatus.name());
        tag.putString("ActionDiagnostic", actionDiagnostic);
        if (!campaignConsent.isEmpty()) tag.put("CampaignConsent", campaignConsent.copy());
        if (!decisionParticipants.isEmpty()) tag.put("DecisionParticipants", decisionParticipants.copy());
        if (dialogueResume != null) {
            CompoundTag dialogue = new CompoundTag();
            dialogue.putString("Tree", dialogueResume.treeId().toString());
            dialogue.putString("Node", dialogueResume.nodeId());
            dialogue.putBoolean("RunActions", dialogueResume.runActions());
            dialogue.putString("LeadingText", dialogueResume.leadingText());
            dialogue.putBoolean("Forced", dialogueResume.forced());
            tag.put("DialogueResume", dialogue);
        }
        ListTag actionTags = new ListTag();
        for (int index = 0; index < actions.size(); index++) {
            var preserved = unreadableCampaignActions.get(index);
            actionTags.add(preserved == null ? saveAction(actions.get(index)) : preserved.copy());
        }
        tag.put("Actions", actionTags);
        CompoundTag replacementTag = new CompoundTag();
        replacements.forEach(replacementTag::putString);
        tag.put("Replacements", replacementTag);
        return tag;
    }

    public static SceneContinuation load(CompoundTag tag) {
        List<VillagerActionDefinition> actions = new ArrayList<>();
        var unreadable = new LinkedHashMap<Integer, CompoundTag>();
        for (Tag raw : tag.getList("Actions", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag action)) continue;
            if (action.getString("Kind").equals("CAMPAIGN_OUTCOME") || action.getString("Kind").equals("CAMPAIGN_FACT")
                    || action.getString("Kind").equals("CAMPAIGN_REWARD")) {
                try {
                    if (action.getString("Kind").equals("CAMPAIGN_REWARD")) {
                        com.jvn.villagerretaliation.campaign.CampaignRewardAction.load(action.getCompound("CampaignReward"));
                    } else if (action.getString("Kind").equals("CAMPAIGN_FACT")) {
                        com.jvn.villagerretaliation.campaign.CampaignFactAction.load(action.getCompound("CampaignFact"));
                    } else com.jvn.villagerretaliation.campaign.CampaignOutcomeAction.load(action.getCompound("CampaignOutcome"));
                } catch (IllegalArgumentException exception) {
                    unreadable.put(actions.size(), action.copy());
                    action = action.copy();
                    action.remove("CampaignOutcome");
                    action.remove("CampaignFact");
                    action.remove("CampaignReward");
                }
            }
            actions.add(loadAction(action));
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        CompoundTag values = tag.getCompound("Replacements");
        for (String key : values.getAllKeys()) replacements.put(key, values.getString(key));
        SceneContinuation value = new SceneContinuation(
                tag.getUUID("Id"), tag.hasUUID("Scene") ? tag.getUUID("Scene")
                        : tag.contains("CampaignRecovery") ? null : tag.getUUID("Scene"), tag.getUUID("Player"),
                tag.hasUUID("Provider") ? tag.getUUID("Provider") : null,
                NbtDataUtil.readResourceLocation(tag, "Quest").orElse(null),
                tag.hasUUID("QuestRun") ? tag.getUUID("QuestRun") : null,
                tag.getString("Source"), actions, tag.getInt("Next"), replacements);
        try { value.state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException ignored) { }
        try { value.sceneResult = SceneInstance.CompletionResult.valueOf(tag.getString("SceneResult")); }
        catch (IllegalArgumentException ignored) { }
        value.unreadableCampaignActions.putAll(unreadable);
        value.campaignRecovery = tag.contains("CampaignRecovery");
        if (value.campaignRecovery) {
            var recovery = tag.getCompound("CampaignRecovery");
            if (!tag.contains("CampaignRecovery", Tag.TAG_COMPOUND) || !recovery.contains("DataVersion", Tag.TAG_INT)
                    || recovery.getInt("DataVersion") != 1 || !recovery.contains("QuestFingerprint", Tag.TAG_STRING)
                    || recovery.getString("QuestFingerprint").isBlank()
                    || !tag.contains("Next", Tag.TAG_INT) || tag.getInt("Next") < 0 || tag.getInt("Next") > actions.size()
                    || !recovery.contains("PreparedAction", Tag.TAG_INT) || recovery.getInt("PreparedAction") < -1
                    || recovery.getInt("PreparedAction") >= actions.size()
                    || recovery.getInt("PreparedAction") >= 0 && recovery.getInt("PreparedAction") != tag.getInt("Next")
                    || recovery.contains("RewardOperation") && !recovery.hasUUID("RewardOperation")) {
                value.unreadableRecovery = tag.get("CampaignRecovery").copy();
                value.unreadableRecoveryCursor = tag.contains("Next") ? tag.get("Next").copy() : null;
            } else {
                value.recoveryFingerprint = recovery.getString("QuestFingerprint");
                value.preparedRecoveryAction = recovery.getInt("PreparedAction");
                value.rewardOperation = recovery.hasUUID("RewardOperation") ? recovery.getUUID("RewardOperation") : null;
            }
        }

        value.conversationEffect = tag.getCompound("ConversationEffect").copy();
        if (tag.contains("ConversationEffect") && (!tag.contains("ConversationEffect", Tag.TAG_COMPOUND) || value.conversationEffect.isEmpty())) {
            value.conversationEffect.put("Unreadable", tag.get("ConversationEffect").copy());
        }
        if (!value.conversationEffect.isEmpty() || tag.contains("ConversationRecovery")) {
            var recovery = tag.getCompound("ConversationRecovery");
            if (value.conversationEffect.isEmpty()) value.conversationEffect.putString("Unreadable", "Missing conversation effect");
            if (!tag.contains("ConversationRecovery", Tag.TAG_COMPOUND) || !recovery.contains("DataVersion", Tag.TAG_INT)
                    || recovery.getInt("DataVersion") != 1 || !recovery.contains("QuestFingerprint", Tag.TAG_STRING)
                    || value.questId != null && recovery.getString("QuestFingerprint").isBlank()
                    || value.campaignRecovery && !recovery.getString("QuestFingerprint").equals(value.recoveryFingerprint)
                    || !tag.contains("Next", Tag.TAG_INT)
                    || tag.getInt("Next") < 0 || tag.getInt("Next") > actions.size()
                    || !recovery.contains("PreparedAction", Tag.TAG_INT) || recovery.getInt("PreparedAction") < -1
                    || recovery.getInt("PreparedAction") >= actions.size()
                    || recovery.getInt("PreparedAction") >= 0 && recovery.getInt("PreparedAction") != tag.getInt("Next")
                    || value.campaignRecovery && recovery.getInt("PreparedAction") != value.preparedRecoveryAction) {
                value.unreadableConversationRecovery = tag.contains("ConversationRecovery") ? tag.get("ConversationRecovery").copy() : new CompoundTag();
                value.unreadableRecoveryCursor = tag.contains("Next") ? tag.get("Next").copy() : null;
            } else {
                value.preparedRecoveryAction = recovery.getInt("PreparedAction");
                value.recoveryFingerprint = recovery.getString("QuestFingerprint");
            }
        }
        value.campaignId = NbtDataUtil.readResourceLocation(tag, "Campaign").orElse(null);
        value.decisionParticipants = tag.getCompound("DecisionParticipants").copy();
        if (tag.contains("DecisionParticipants") && (!tag.contains("DecisionParticipants", Tag.TAG_COMPOUND) || value.decisionParticipants.isEmpty())) {
            value.decisionParticipants.put("Unreadable", tag.get("DecisionParticipants").copy());
        }
        value.campaignConsent = tag.getCompound("CampaignConsent").copy();
        if (tag.contains("CampaignConsent") && (!tag.contains("CampaignConsent", Tag.TAG_COMPOUND) || value.campaignConsent.isEmpty())) {
            value.campaignConsent.put("Unreadable", tag.get("CampaignConsent").copy());
        }
        value.dependencyContinuationId = tag.hasUUID("DependencyContinuation") ? tag.getUUID("DependencyContinuation") : null;
        value.retrySuspendedAction = tag.getBoolean("RetrySuspendedAction");
        value.completionReceipt = tag.getBoolean("CompletionReceipt");
        value.actionStatus = enumValue(ActionStatus.class, tag.getString("ActionStatus"),
                value.completionReceipt ? ActionStatus.SUCCESS : ActionStatus.SUSPENDED);
        value.actionDiagnostic = tag.getString("ActionDiagnostic");
        CompoundTag dialogue = tag.getCompound("DialogueResume");
        ResourceLocation treeId = NbtDataUtil.readResourceLocation(dialogue, "Tree").orElse(null);
        if (treeId != null) value.dialogueResume = new DialogueResume(treeId,
                dialogue.getString("Node"), dialogue.getBoolean("RunActions"), dialogue.getString("LeadingText"), dialogue.getBoolean("Forced"));
        return value;
    }

    public static CompoundTag saveAction(VillagerActionDefinition action) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", action.kind().name());
        putResource(tag, "Quest", action.questId());
        tag.putString("QuestAction", action.questAction().name());
        tag.putInt("Amount", action.amount());
        putResource(tag, "Memory", action.memoryTag());
        tag.putString("MemoryScope", action.memoryScope().name());
        putResource(tag, "Loot", action.lootTable());
        tag.putString("Notification", action.notificationTrigger());
        tag.putString("Text", action.text());
        tag.putString("Forced", action.forcedDialogue());
        tag.putBoolean("Flash", action.flashTracker());
        tag.putString("FactScope", action.factScope().name());
        putResource(tag, "FactTag", action.factTag());
        tag.putString("FactKey", action.factKey());
        tag.putString("FactValue", action.factValue());
        CompoundTag lines = new CompoundTag();
        action.linesByStatus().forEach((key, list) -> {
            ListTag values = new ListTag();
            list.forEach(value -> values.add(StringTag.valueOf(value)));
            lines.put(key, values);
        });
        tag.put("Lines", lines);
        CompoundTag lineKeys = new CompoundTag();
        action.lineKeysByStatus().forEach((key, list) -> {
            ListTag values = new ListTag();
            list.forEach(value -> values.add(StringTag.valueOf(value)));
            lineKeys.put(key, values);
        });
        tag.put("LineKeys", lineKeys);
        CompiledQuestTransition transition = action.questTransition();
        if (transition != null && !transition.equals(CompiledQuestTransition.EMPTY)) {
            CompoundTag transitionTag = new CompoundTag();
            putResource(transitionTag, "Quest", transition.questId());
            transitionTag.putString("Stage", transition.stageId());
            transitionTag.putString("Scene", transition.scenePath());
            transitionTag.putString("Response", transition.responseId());
            transitionTag.putString("Target", transition.target().name());
            transitionTag.putString("TargetStage", transition.targetStage());
            transitionTag.putString("Source", transition.sourcePointer());
            tag.put("Transition", transitionTag);
        }
        putResource(tag, "Scene", action.sceneId());
        tag.putString("Operation", action.sceneOperationId());
        tag.putBoolean("Wait", action.waitForScene());
        tag.putBoolean("Required", action.required());
        if (action.campaignReward() != null) tag.put("CampaignReward", action.campaignReward().save());
        if (action.campaignFact() != null) tag.put("CampaignFact", action.campaignFact().save());
        if (action.campaignOutcome() != null) tag.put("CampaignOutcome", action.campaignOutcome().save());
        return tag;
    }

    public static VillagerActionDefinition loadAction(CompoundTag tag) {
        CompoundTag rawTransition = tag.getCompound("Transition");
        CompiledQuestTransition transition = rawTransition.isEmpty() ? CompiledQuestTransition.EMPTY
                : new CompiledQuestTransition(NbtDataUtil.readResourceLocation(rawTransition, "Quest").orElse(null),
                        rawTransition.getString("Stage"), rawTransition.getString("Scene"),
                        rawTransition.getString("Response"), enumValue(CompiledQuestTransition.Target.class,
                        rawTransition.getString("Target"), CompiledQuestTransition.Target.NONE),
                        rawTransition.getString("TargetStage"), rawTransition.getString("Source"));
        Map<String, List<String>> lines = readLineMap(tag, "Lines");
        Map<String, List<String>> lineKeys = readLineMap(tag, "LineKeys");
        return new VillagerActionDefinition(enumValue(VillagerActionDefinition.Kind.class,tag.getString("Kind"),VillagerActionDefinition.Kind.NONE),NbtDataUtil.readResourceLocation(tag, "Quest").orElse(null),
                enumValue(VillagerActionDefinition.QuestAction.class,tag.getString("QuestAction"),VillagerActionDefinition.QuestAction.NONE),tag.getInt("Amount"),NbtDataUtil.readResourceLocation(tag, "Memory").orElse(null),
                enumValue(com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.class,tag.getString("MemoryScope"),com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.BOTH),NbtDataUtil.readResourceLocation(tag, "Loot").orElse(null),
                tag.getString("Notification"),tag.getString("Text"),tag.getString("Forced"),tag.getBoolean("Flash"),enumValue(QuestFactScope.class,tag.getString("FactScope"),QuestFactScope.PLAYER),
                NbtDataUtil.readResourceLocation(tag, "FactTag").orElse(null),tag.getString("FactKey"),tag.getString("FactValue"),lines,lineKeys,transition,NbtDataUtil.readResourceLocation(tag, "Scene").orElse(null),tag.getString("Operation"),tag.getBoolean("Wait"),tag.getBoolean("Required"),
                tag.contains("CampaignOutcome", Tag.TAG_COMPOUND)
                        ? com.jvn.villagerretaliation.campaign.CampaignOutcomeAction.load(tag.getCompound("CampaignOutcome")) : null,
                tag.contains("CampaignFact", Tag.TAG_COMPOUND)
                        ? com.jvn.villagerretaliation.campaign.CampaignFactAction.load(tag.getCompound("CampaignFact")) : null,
                tag.contains("CampaignReward", Tag.TAG_COMPOUND)
                        ? com.jvn.villagerretaliation.campaign.CampaignRewardAction.load(tag.getCompound("CampaignReward")) : null);
    }

    private static Map<String, List<String>> readLineMap(CompoundTag tag, String field) {
        Map<String, List<String>> lines = new LinkedHashMap<>();
        CompoundTag rawLines = tag.getCompound(field);
        for (String key : rawLines.getAllKeys()) {
            List<String> values = new ArrayList<>();
            for (Tag raw : rawLines.getList(key, Tag.TAG_STRING)) {
                values.add(raw.getAsString());
            }
            lines.put(key, List.copyOf(values));
        }
        return Map.copyOf(lines);
    }

    private static void putResource(CompoundTag tag, String key, ResourceLocation value) {
        if (value != null) tag.putString(key, value.toString());
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public record DialogueResume(ResourceLocation treeId, String nodeId, boolean runActions, String leadingText, boolean forced) {
        public DialogueResume(ResourceLocation treeId, String nodeId, boolean runActions, String leadingText) {
            this(treeId, nodeId, runActions, leadingText, false);
        }
    }

    public enum State {
        WAITING,
        RESUMING,
        COMPLETED
    }
}
