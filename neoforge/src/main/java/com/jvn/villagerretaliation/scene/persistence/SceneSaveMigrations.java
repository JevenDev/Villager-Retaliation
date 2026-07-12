package com.jvn.villagerretaliation.scene.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Pure scene-save migrations. Version 0 covers pre-release quest-embedded scene lists. */
public final class SceneSaveMigrations {
    public static final String DATA_VERSION = "DataVersion";
    public static final int QUEST_SAVE_BASE_VERSION = 2;

    private SceneSaveMigrations() {
    }

    public static Result migrate(CompoundTag input, int current) {
        CompoundTag data = input == null ? new CompoundTag() : input.copy();
        int source = data.contains(DATA_VERSION, Tag.TAG_INT) ? Math.max(0, data.getInt(DATA_VERSION)) : 0;
        if (source > current) return new Result(data, source, source, true);
        int version = source;
        while (version < current) {
            if (version == 0) {
                if (!data.contains("Instances", Tag.TAG_LIST) && data.contains("Scenes", Tag.TAG_LIST)) {
                    data.put("Instances", data.get("Scenes").copy());
                }
                data.putInt("SourceQuestDataVersion", QUEST_SAVE_BASE_VERSION);
                version = 1;
            } else if (version == 1) {
                // Version 2 adds the structured Encounters list; absence means no owned encounters.
                version = 2;
            } else throw new IllegalStateException("No scene save migration from version " + version);
        }
        data.putInt(DATA_VERSION, current);
        return new Result(data, source, current, false);
    }

    public record Result(CompoundTag data, int sourceVersion, int targetVersion, boolean futureVersion) { }
}
