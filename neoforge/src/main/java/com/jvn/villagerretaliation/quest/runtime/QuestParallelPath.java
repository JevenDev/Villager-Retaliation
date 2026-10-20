package com.jvn.villagerretaliation.quest.runtime;

import net.minecraft.nbt.CompoundTag;

public record QuestParallelPath(String stage, String target, Phase phase) {
    public enum Phase { ENTERING, ACTIVE, EXITING, ARRIVED }

    public QuestParallelPath {
        if (stage == null || stage.isBlank() || target == null || phase == null)
            throw new IllegalArgumentException("Invalid saved parallel quest path.");
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Stage", stage);
        tag.putString("Target", target);
        tag.putString("Phase", phase.name());
        return tag;
    }

    public static QuestParallelPath load(CompoundTag tag) {
        return new QuestParallelPath(tag.getString("Stage"), tag.getString("Target"), Phase.valueOf(tag.getString("Phase")));
    }
}
