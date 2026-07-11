package com.jvn.villagerretaliation.quest.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Side-effect-free, deterministic migrations for mod-owned quest save formats. */
public final class QuestSaveMigrations {
    public static final String DATA_VERSION_TAG = "DataVersion";

    private QuestSaveMigrations() {
    }

    public static MigrationResult migrate(CompoundTag input, int currentVersion) {
        CompoundTag data = input == null ? new CompoundTag() : input.copy();
        int sourceVersion = data.contains(DATA_VERSION_TAG, Tag.TAG_INT)
                ? Math.max(0, data.getInt(DATA_VERSION_TAG))
                : 0;
        if (sourceVersion > currentVersion) {
            return new MigrationResult(data, sourceVersion, sourceVersion, true);
        }
        int version = sourceVersion;
        while (version < currentVersion) {
            version = migrateOne(data, version);
        }
        data.putInt(DATA_VERSION_TAG, currentVersion);
        return new MigrationResult(data, sourceVersion, currentVersion, false);
    }

    private static int migrateOne(CompoundTag data, int version) {
        return switch (version) {
            case 0 -> 1;
            default -> throw new IllegalStateException("No quest save migration from version " + version);
        };
    }

    public record MigrationResult(
            CompoundTag data,
            int sourceVersion,
            int targetVersion,
            boolean futureVersion) {
    }
}
