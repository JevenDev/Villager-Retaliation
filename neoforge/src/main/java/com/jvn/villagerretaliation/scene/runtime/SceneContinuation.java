package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
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
    private final UUID playerId;
    private final UUID providerId;
    private final ResourceLocation questId;
    private final UUID questRunId;
    private final String sourcePointer;
    private final List<VillagerActionDefinition> actions;
    private final Map<String, String> replacements;
    private int nextActionIndex;
    private State state = State.WAITING;
    private SceneInstance.CompletionResult sceneResult = SceneInstance.CompletionResult.NONE;
    private boolean completionReceipt;

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

    public UUID id() { return id; }
    public UUID sceneInstanceId() { return sceneInstanceId; }
    public UUID playerId() { return playerId; }
    public UUID providerId() { return providerId; }
    public ResourceLocation questId() { return questId; }
    public UUID questRunId() { return questRunId; }
    public String sourcePointer() { return sourcePointer; }
    public List<VillagerActionDefinition> actions() { return actions; }
    public int nextActionIndex() { return nextActionIndex; }
    public State state() { return state; }
    public SceneInstance.CompletionResult sceneResult() { return sceneResult; }
    public boolean completionReceipt() { return completionReceipt; }
    public Map<String, String> replacements() { return replacements; }

    public void advance() { nextActionIndex++; }

    public void waitOn(UUID sceneId) {
        sceneInstanceId = sceneId;
        state = State.WAITING;
        sceneResult = SceneInstance.CompletionResult.NONE;
    }

    public void resuming(SceneInstance.CompletionResult result) {
        state = State.RESUMING;
        sceneResult = result;
    }

    public void complete() {
        state = State.COMPLETED;
        completionReceipt = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Scene", sceneInstanceId);
        tag.putUUID("Player", playerId);
        if (providerId != null) tag.putUUID("Provider", providerId);
        if (questId != null) tag.putString("Quest", questId.toString());
        if (questRunId != null) tag.putUUID("QuestRun", questRunId);
        tag.putString("Source", sourcePointer);
        tag.putInt("Next", nextActionIndex);
        tag.putString("State", state.name());
        tag.putString("SceneResult", sceneResult.name());
        tag.putBoolean("CompletionReceipt", completionReceipt);
        ListTag actionTags = new ListTag();
        actions.forEach(action -> actionTags.add(saveAction(action)));
        tag.put("Actions", actionTags);
        CompoundTag replacementTag = new CompoundTag();
        replacements.forEach(replacementTag::putString);
        tag.put("Replacements", replacementTag);
        return tag;
    }

    public static SceneContinuation load(CompoundTag tag) {
        List<VillagerActionDefinition> actions = new ArrayList<>();
        for (Tag raw : tag.getList("Actions", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag action) actions.add(loadAction(action));
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        CompoundTag values = tag.getCompound("Replacements");
        for (String key : values.getAllKeys()) replacements.put(key, values.getString(key));
        SceneContinuation value = new SceneContinuation(
                tag.getUUID("Id"), tag.getUUID("Scene"), tag.getUUID("Player"),
                tag.hasUUID("Provider") ? tag.getUUID("Provider") : null,
                ResourceLocation.tryParse(tag.getString("Quest")),
                tag.hasUUID("QuestRun") ? tag.getUUID("QuestRun") : null,
                tag.getString("Source"), actions, tag.getInt("Next"), replacements);
        try { value.state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException ignored) { }
        try { value.sceneResult = SceneInstance.CompletionResult.valueOf(tag.getString("SceneResult")); }
        catch (IllegalArgumentException ignored) { }
        value.completionReceipt = tag.getBoolean("CompletionReceipt");
        return value;
    }

    private static CompoundTag saveAction(VillagerActionDefinition action) {
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
        return tag;
    }

    private static VillagerActionDefinition loadAction(CompoundTag tag) {
        CompoundTag rawTransition = tag.getCompound("Transition");
        CompiledQuestTransition transition = rawTransition.isEmpty() ? CompiledQuestTransition.EMPTY
                : new CompiledQuestTransition(ResourceLocation.tryParse(rawTransition.getString("Quest")),
                        rawTransition.getString("Stage"), rawTransition.getString("Scene"),
                        rawTransition.getString("Response"), enumValue(CompiledQuestTransition.Target.class,
                        rawTransition.getString("Target"), CompiledQuestTransition.Target.NONE),
                        rawTransition.getString("TargetStage"), rawTransition.getString("Source"));
        Map<String, List<String>> lines = readLineMap(tag, "Lines");
        Map<String, List<String>> lineKeys = readLineMap(tag, "LineKeys");
        return new VillagerActionDefinition(enumValue(VillagerActionDefinition.Kind.class,tag.getString("Kind"),VillagerActionDefinition.Kind.NONE),ResourceLocation.tryParse(tag.getString("Quest")),
                enumValue(VillagerActionDefinition.QuestAction.class,tag.getString("QuestAction"),VillagerActionDefinition.QuestAction.NONE),tag.getInt("Amount"),ResourceLocation.tryParse(tag.getString("Memory")),
                enumValue(com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.class,tag.getString("MemoryScope"),com.jvn.villagerretaliation.village.VillageEventMemory.MemoryScope.BOTH),ResourceLocation.tryParse(tag.getString("Loot")),
                tag.getString("Notification"),tag.getString("Text"),tag.getString("Forced"),tag.getBoolean("Flash"),enumValue(QuestFactScope.class,tag.getString("FactScope"),QuestFactScope.PLAYER),
                ResourceLocation.tryParse(tag.getString("FactTag")),tag.getString("FactKey"),tag.getString("FactValue"),lines,lineKeys,transition,ResourceLocation.tryParse(tag.getString("Scene")),tag.getString("Operation"),tag.getBoolean("Wait"),tag.getBoolean("Required"));
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

    public enum State {
        WAITING,
        RESUMING,
        COMPLETED
    }
}
