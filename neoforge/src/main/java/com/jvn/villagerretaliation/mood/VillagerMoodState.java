package com.jvn.villagerretaliation.mood;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record VillagerMoodState(
        VillagerMood primaryMood,
        int intensity,
        String causeTag,
        UUID sourcePlayerId,
        UUID sourceEntityId,
        long startedGameTime,
        long lastUpdatedGameTime,
        long decayTicks) {
    public static final int MIN_INTENSITY = 0;
    public static final int MAX_INTENSITY = 100;
    public static final long DEFAULT_DECAY_TICKS = 20L * 60L * 10L;
    public static final VillagerMoodState DEFAULT = neutral(0L);

    private static final String TAG_PRIMARY_MOOD = "PrimaryMood";
    private static final String TAG_INTENSITY = "Intensity";
    private static final String TAG_CAUSE = "Cause";
    private static final String TAG_SOURCE_PLAYER = "SourcePlayer";
    private static final String TAG_SOURCE_ENTITY = "SourceEntity";
    private static final String TAG_STARTED_GAME_TIME = "StartedGameTime";
    private static final String TAG_LAST_UPDATED_GAME_TIME = "LastUpdatedGameTime";
    private static final String TAG_DECAY_TICKS = "DecayTicks";

    public VillagerMoodState {
        primaryMood = primaryMood == null ? VillagerMood.NEUTRAL : primaryMood;
        intensity = Math.clamp(intensity, MIN_INTENSITY, MAX_INTENSITY);
        causeTag = causeTag == null ? "" : causeTag;
        decayTicks = Math.max(1L, decayTicks);
        if (primaryMood == VillagerMood.NEUTRAL || intensity <= 0) {
            primaryMood = VillagerMood.NEUTRAL;
            intensity = 0;
            causeTag = "";
            sourcePlayerId = null;
            sourceEntityId = null;
        }
    }

    public static VillagerMoodState neutral(long gameTime) {
        return new VillagerMoodState(VillagerMood.NEUTRAL, 0, "", null, null, gameTime, gameTime, DEFAULT_DECAY_TICKS);
    }

    public static VillagerMoodState of(
            VillagerMood primaryMood,
            int intensity,
            String causeTag,
            UUID sourcePlayerId,
            UUID sourceEntityId,
            long gameTime,
            long decayTicks) {
        return new VillagerMoodState(primaryMood, intensity, causeTag, sourcePlayerId, sourceEntityId, gameTime, gameTime, decayTicks);
    }

    public boolean isNeutral() {
        return this.primaryMood == VillagerMood.NEUTRAL || this.intensity <= 0;
    }

    public VillagerMoodState withEffectiveDecay(long gameTime) {
        if (isNeutral() || gameTime <= this.lastUpdatedGameTime) {
            return this;
        }

        long elapsedTicks = gameTime - this.lastUpdatedGameTime;
        int decayAmount = (int) Math.min(MAX_INTENSITY, elapsedTicks * MAX_INTENSITY / this.decayTicks);
        int decayedIntensity = this.intensity - decayAmount;
        if (decayedIntensity <= 0) {
            return neutral(gameTime);
        }
        return new VillagerMoodState(
                this.primaryMood,
                decayedIntensity,
                this.causeTag,
                this.sourcePlayerId,
                this.sourceEntityId,
                this.startedGameTime,
                gameTime,
                this.decayTicks
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_PRIMARY_MOOD, this.primaryMood.serializedName());
        tag.putInt(TAG_INTENSITY, this.intensity);
        tag.putString(TAG_CAUSE, this.causeTag);
        if (this.sourcePlayerId != null) {
            tag.putUUID(TAG_SOURCE_PLAYER, this.sourcePlayerId);
        }
        if (this.sourceEntityId != null) {
            tag.putUUID(TAG_SOURCE_ENTITY, this.sourceEntityId);
        }
        tag.putLong(TAG_STARTED_GAME_TIME, this.startedGameTime);
        tag.putLong(TAG_LAST_UPDATED_GAME_TIME, this.lastUpdatedGameTime);
        tag.putLong(TAG_DECAY_TICKS, this.decayTicks);
        return tag;
    }

    public static VillagerMoodState load(CompoundTag tag) {
        if (tag == null) {
            return DEFAULT;
        }

        VillagerMood primaryMood = tag.contains(TAG_PRIMARY_MOOD, Tag.TAG_STRING)
                ? VillagerMood.bySerializedName(tag.getString(TAG_PRIMARY_MOOD))
                : VillagerMood.NEUTRAL;
        int intensity = tag.contains(TAG_INTENSITY, Tag.TAG_INT) ? tag.getInt(TAG_INTENSITY) : 0;
        String cause = tag.contains(TAG_CAUSE, Tag.TAG_STRING) ? tag.getString(TAG_CAUSE) : "";
        UUID sourcePlayerId = tag.hasUUID(TAG_SOURCE_PLAYER) ? tag.getUUID(TAG_SOURCE_PLAYER) : null;
        UUID sourceEntityId = tag.hasUUID(TAG_SOURCE_ENTITY) ? tag.getUUID(TAG_SOURCE_ENTITY) : null;
        long startedGameTime = tag.contains(TAG_STARTED_GAME_TIME, Tag.TAG_LONG) ? tag.getLong(TAG_STARTED_GAME_TIME) : 0L;
        long lastUpdatedGameTime = tag.contains(TAG_LAST_UPDATED_GAME_TIME, Tag.TAG_LONG)
                ? tag.getLong(TAG_LAST_UPDATED_GAME_TIME)
                : startedGameTime;
        long decayTicks = tag.contains(TAG_DECAY_TICKS, Tag.TAG_LONG) ? tag.getLong(TAG_DECAY_TICKS) : DEFAULT_DECAY_TICKS;
        return new VillagerMoodState(primaryMood, intensity, cause, sourcePlayerId, sourceEntityId, startedGameTime, lastUpdatedGameTime, decayTicks);
    }

    public boolean sameVisibleState(VillagerMoodState other) {
        return other != null
                && this.primaryMood == other.primaryMood
                && this.intensity == other.intensity
                && Objects.equals(this.causeTag, other.causeTag)
                && Objects.equals(this.sourcePlayerId, other.sourcePlayerId)
                && Objects.equals(this.sourceEntityId, other.sourceEntityId);
    }
}
