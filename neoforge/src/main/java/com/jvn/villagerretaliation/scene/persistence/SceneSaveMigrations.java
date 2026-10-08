package com.jvn.villagerretaliation.scene.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
            } else if (version == 2) {
                markRunIdentityKinds(data);
                version = 3;
            } else if (version == 3) {
                // Version 4 adds bounded terminal operation tombstones; old saves start with none.
                version = 4;
            } else if (version == 4) {
                migrateContinuationResults(data);
                version = 5;
            } else if (version == 5) {
                for (Tag raw : data.getList("Continuations", Tag.TAG_COMPOUND)) {
                    if (raw instanceof CompoundTag continuation) continuation.putBoolean("RetrySuspendedAction", false);
                }
                version = 6;
            } else if (version == 6) {
                version = 7;
            } else if (version == 7) {
                version = 8;
            } else if (version == 8) {
                for (Tag raw : data.getList("Instances", Tag.TAG_COMPOUND)) {
                    if (raw instanceof CompoundTag instance) {
                        instance.putLong("CampaignPausedAt", -1L);
                        instance.putLong("CampaignPausedTicks", 0L);
                    }
                }
                version = 9;
            } else if (version == 9) {
                version = 10;
            } else if (version == 10) {
                version = 11;
            } else if (version == 11) {
                version = 12;
            } else if (version == 12) {
                version = 13;
            } else if (version == 13) {
                version = 14;
            } else if (version == 14) {
                version = 15;
            } else if (version == 15) {
                version = 16;
            } else if (version == 16) {
                version = 17;
            } else if (version == 17) {
                markLegacyBlockOwnership(data);
                version = 18;
            } else if (version == 18) {
                version = 19;
            } else if (version == 19) {
                version = 20;
            } else if (version == 20) {
                for (Tag raw : data.getList("Encounters", Tag.TAG_COMPOUND)) {
                    var encounter = (CompoundTag) raw;
                    if (!encounter.contains("RewardHistory")) {
                        var rewards = new CompoundTag();
                        rewards.putInt("Version", 1);
                        rewards.put("Definition", new CompoundTag());
                        rewards.put("Batches", new ListTag());
                        encounter.put("RewardHistory", rewards);
                    }
                }
                version = 21;
            } else if (version == 21) {
                for (Tag raw : data.getList("Encounters", Tag.TAG_COMPOUND)) {
                    var encounter = (CompoundTag) raw;
                    if (!encounter.contains("PhaseHistory")) {
                        var phases = new CompoundTag();
                        phases.putInt("Version", 1);
                        phases.put("Definition", new CompoundTag());
                        phases.putBoolean("CompletionCaptured", java.util.Set.of("COMPLETED", "FAILED", "CANCELLED", "CLEANING_UP", "CLEANED", "RELEASED").contains(encounter.getString("State")));
                        phases.put("CompletionTriggers", new ListTag());
                        phases.put("Runs", new ListTag());
                        encounter.put("PhaseHistory", phases);
                    }
                }
                version = 22;
            } else throw new IllegalStateException("No scene save migration from version " + version);
        }
        data.putInt(DATA_VERSION, current);
        return new Result(data, source, current, false);
    }

    private static void markLegacyBlockOwnership(CompoundTag data) {
        for (var raw : data.getList("Encounters", Tag.TAG_COMPOUND)) {
            var encounter = (CompoundTag) raw;
            for (var entry : encounter.getList("EnvironmentBlocks", Tag.TAG_COMPOUND)) {
                var block = (CompoundTag) entry;
                if (block.contains("Ownership")) continue;
                var ownership = new CompoundTag();
                ownership.putInt("DataVersion", 1);
                ownership.putString("State", "LEGACY");
                ownership.putString("Diagnostic", "");
                block.put("Ownership", ownership);
            }
        }
    }

    private static void migrateContinuationResults(CompoundTag data) {
        for (Tag raw : data.getList("Continuations", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag continuation) || continuation.contains("ActionStatus", Tag.TAG_STRING)) continue;
            String status = "SUSPENDED";
            if (continuation.getBoolean("CompletionReceipt")) {
                status = switch (continuation.getString("SceneResult")) {
                    case "SUCCESS" -> "SUCCESS";
                    case "FAILURE", "CANCELLED" -> "FAILED";
                    default -> "UNAVAILABLE";
                };
            }
            continuation.putString("ActionStatus", status);
        }
    }

    private static void markRunIdentityKinds(CompoundTag data) {
        ListTag instances = data.getList("Instances", Tag.TAG_COMPOUND);
        for (Tag raw : instances) {
            if (!(raw instanceof CompoundTag instance) || instance.contains("RunIdentityKind", Tag.TAG_STRING)) {
                continue;
            }
            instance.putString("RunIdentityKind",
                    instance.hasUUID("QuestRunId") ? "QUEST_RUN" : "LEGACY_OWNER");
        }
    }

    public record Result(CompoundTag data, int sourceVersion, int targetVersion, boolean futureVersion) { }
}
