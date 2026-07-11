package com.jvn.villagerretaliation.scene.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SceneStepRecord {
    private final String stepId;
    private final ResourceLocation stepType;
    private StepExecutionStatus status;
    private int attempts;
    private long preparedTime;
    private long appliedTime;
    private long completedTime;
    private long wakeTime;
    private String chosenTransition = "";
    private String failureCode = "";
    private final Map<String, String> durableValues = new LinkedHashMap<>();

    public SceneStepRecord(String stepId, ResourceLocation stepType) {
        if (stepId == null || stepId.isBlank() || stepType == null) throw new IllegalArgumentException("step record needs stable id and type");
        this.stepId = stepId;
        this.stepType = stepType;
        this.status = StepExecutionStatus.PENDING;
    }

    public String stepId() { return stepId; }
    public ResourceLocation stepType() { return stepType; }
    public StepExecutionStatus status() { return status; }
    public int attempts() { return attempts; }
    public long preparedTime() { return preparedTime; }
    public long appliedTime() { return appliedTime; }
    public long completedTime() { return completedTime; }
    public long wakeTime() { return wakeTime; }
    public String chosenTransition() { return chosenTransition; }
    public String failureCode() { return failureCode; }
    public Map<String, String> durableValues() { return Map.copyOf(durableValues); }

    public void status(StepExecutionStatus next, long gameTime) {
        this.status = next == null ? StepExecutionStatus.FAILED : next;
        switch (this.status) {
            case PREPARED -> { this.preparedTime = gameTime; this.attempts++; }
            case APPLIED -> this.appliedTime = gameTime;
            case COMPLETED, SKIPPED -> this.completedTime = gameTime;
            default -> { }
        }
    }

    public void wakeTime(long value) { this.wakeTime = Math.max(0L, value); }
    public void chooseTransition(String value) { if (this.chosenTransition.isBlank()) this.chosenTransition = value == null ? "" : value; }
    public void fail(String code) { this.failureCode = code == null ? "step_failed" : code; this.status = StepExecutionStatus.FAILED; }
    public void putDurableValue(String key, String value) {
        if (key != null && !key.isBlank() && value != null) this.durableValues.put(key, value);
    }
    public void resetForRetry() { this.status=StepExecutionStatus.PENDING;this.failureCode="";this.wakeTime=0L; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("StepId", stepId);
        tag.putString("StepType", stepType.toString());
        tag.putString("Status", status.name());
        tag.putInt("Attempts", attempts);
        tag.putLong("PreparedTime", preparedTime);
        tag.putLong("AppliedTime", appliedTime);
        tag.putLong("CompletedTime", completedTime);
        tag.putLong("WakeTime", wakeTime);
        if (!chosenTransition.isBlank()) tag.putString("ChosenTransition", chosenTransition);
        if (!failureCode.isBlank()) tag.putString("FailureCode", failureCode);
        CompoundTag values = new CompoundTag();
        durableValues.forEach(values::putString);
        tag.put("DurableValues", values);
        return tag;
    }

    public static SceneStepRecord load(CompoundTag tag) {
        SceneStepRecord record = new SceneStepRecord(tag.getString("StepId"), ResourceLocation.parse(tag.getString("StepType")));
        record.status = StepExecutionStatus.byName(tag.getString("Status"));
        record.attempts = Math.max(0, tag.getInt("Attempts"));
        record.preparedTime = tag.getLong("PreparedTime");
        record.appliedTime = tag.getLong("AppliedTime");
        record.completedTime = tag.getLong("CompletedTime");
        record.wakeTime = tag.getLong("WakeTime");
        record.chosenTransition = tag.getString("ChosenTransition");
        record.failureCode = tag.getString("FailureCode");
        if (tag.contains("DurableValues", Tag.TAG_COMPOUND)) {
            CompoundTag values = tag.getCompound("DurableValues");
            values.getAllKeys().forEach(key -> record.durableValues.put(key, values.getString(key)));
        }
        return record;
    }
}
