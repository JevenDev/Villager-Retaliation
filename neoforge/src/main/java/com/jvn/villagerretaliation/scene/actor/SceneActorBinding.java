package com.jvn.villagerretaliation.scene.actor;

import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.util.NbtDataUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Immutable durable actor identity. Live entity lookup is deliberately outside this model. */
public record SceneActorBinding(
        String alias,
        ResourceLocation actorType,
        String targetIdentity,
        UUID entityId,
        ResourceLocation sourceType,
        ResourceLocation lastDimension,
        BlockPos lastPosition,
        Map<String, String> displaySnapshot,
        long generation,
        BindingState state,
        List<ReplacementHistoryEntry> replacementHistory
) {
    public SceneActorBinding {
        alias = alias == null ? "" : alias;
        if (alias.isBlank() || actorType == null || targetIdentity == null || targetIdentity.isBlank()) {
            throw new IllegalArgumentException("actor binding requires alias, type, and stable target identity");
        }
        sourceType = sourceType == null ? actorType : sourceType;
        lastPosition = lastPosition == null ? null : lastPosition.immutable();
        displaySnapshot = displaySnapshot == null ? Map.of() : Map.copyOf(displaySnapshot);
        generation = Math.max(1L, generation);
        state = state == null ? BindingState.SNAPSHOT : state;
        replacementHistory = replacementHistory == null ? List.of() : List.copyOf(replacementHistory);
    }

    public static SceneActorBinding entity(String alias, ResourceLocation actorType, UUID entityId,
            ResourceLocation sourceType, ResourceLocation dimension, BlockPos position, String displayName, boolean live) {
        if (entityId == null) throw new IllegalArgumentException("entity actor binding requires UUID");
        Map<String, String> display = displayName == null || displayName.isBlank()
                ? Map.of() : Map.of("name", displayName);
        return new SceneActorBinding(alias, actorType, entityId.toString(), entityId, sourceType, dimension, position,
                display, 1L, live ? BindingState.LIVE : BindingState.SNAPSHOT, List.of());
    }

    public static SceneActorBinding fromQuestProvider(String alias, ResourceLocation actorType, QuestProviderBinding provider) {
        if (provider == null || provider.providerId() == null) {
            throw new IllegalArgumentException("quest provider snapshot requires a stable provider UUID");
        }
        Map<String, String> display = new LinkedHashMap<>();
        if (!provider.displayName().isBlank()) display.put("name", provider.displayName());
        if (provider.professionId() != null) display.put("profession", provider.professionId().toString());
        display.put("level", Integer.toString(provider.level()));
        if (!provider.villageKey().isBlank()) display.put("village", provider.villageKey());
        return new SceneActorBinding(alias, actorType, provider.providerId().toString(), provider.providerId(),
                provider.providerType(), provider.dimension() == null ? null : provider.dimension().location(), provider.pos(),
                display, 1L, provider.live() ? BindingState.LIVE : BindingState.SNAPSHOT, List.of());
    }

    public SceneActorBinding withObservation(ResourceLocation dimension, BlockPos position, boolean live) {
        return new SceneActorBinding(this.alias, this.actorType, this.targetIdentity, this.entityId, this.sourceType,
                dimension == null ? this.lastDimension : dimension,
                position == null ? this.lastPosition : position,
                this.displaySnapshot, this.generation, live ? BindingState.LIVE : BindingState.SNAPSHOT,
                this.replacementHistory);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Alias", this.alias);
        tag.putString("ActorType", this.actorType.toString());
        tag.putString("TargetIdentity", this.targetIdentity);
        if (this.entityId != null) tag.putUUID("EntityId", this.entityId);
        tag.putString("SourceType", this.sourceType.toString());
        if (this.lastDimension != null) tag.putString("LastDimension", this.lastDimension.toString());
        NbtDataUtil.putBlockPos(tag, "LastPosition", this.lastPosition);
        CompoundTag display = new CompoundTag();
        this.displaySnapshot.forEach(display::putString);
        tag.put("DisplaySnapshot", display);
        tag.putLong("Generation", this.generation);
        tag.putString("State", this.state.name());
        ListTag history = new ListTag();
        this.replacementHistory.stream().map(ReplacementHistoryEntry::save).forEach(history::add);
        tag.put("ReplacementHistory", history);
        return tag;
    }

    public static SceneActorBinding load(CompoundTag tag) {
        ResourceLocation type = ResourceLocation.tryParse(tag.getString("ActorType"));
        ResourceLocation source = ResourceLocation.tryParse(tag.getString("SourceType"));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("LastDimension"));
        Map<String, String> display = new LinkedHashMap<>();
        if (tag.contains("DisplaySnapshot", Tag.TAG_COMPOUND)) {
            CompoundTag displayTag = tag.getCompound("DisplaySnapshot");
            displayTag.getAllKeys().forEach(key -> display.put(key, displayTag.getString(key)));
        }
        List<ReplacementHistoryEntry> history = new ArrayList<>();
        for (Tag raw : tag.getList("ReplacementHistory", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag entry) history.add(ReplacementHistoryEntry.load(entry));
        }
        return new SceneActorBinding(tag.getString("Alias"), type, tag.getString("TargetIdentity"),
                tag.hasUUID("EntityId") ? tag.getUUID("EntityId") : null, source, dimension,
                NbtDataUtil.readBlockPos(tag, "LastPosition").orElse(null), display, tag.getLong("Generation"),
                BindingState.byName(tag.getString("State")), history);
    }

    public enum BindingState {
        LIVE,
        SNAPSHOT,
        MISSING,
        DEAD;

        static BindingState byName(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException exception) {
                return SNAPSHOT;
            }
        }
    }

    public record ReplacementHistoryEntry(
            String previousIdentity,
            String newIdentity,
            long previousGeneration,
            long newGeneration,
            String reason,
            long gameTime,
            String operatorIdentity
    ) {
        public ReplacementHistoryEntry {
            previousIdentity = previousIdentity == null ? "" : previousIdentity;
            newIdentity = newIdentity == null ? "" : newIdentity;
            reason = reason == null || reason.isBlank() ? "unspecified" : reason;
            operatorIdentity = operatorIdentity == null ? "" : operatorIdentity;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("PreviousIdentity", this.previousIdentity);
            tag.putString("NewIdentity", this.newIdentity);
            tag.putLong("PreviousGeneration", this.previousGeneration);
            tag.putLong("NewGeneration", this.newGeneration);
            tag.putString("Reason", this.reason);
            tag.putLong("GameTime", this.gameTime);
            if (!this.operatorIdentity.isBlank()) tag.putString("Operator", this.operatorIdentity);
            return tag;
        }

        static ReplacementHistoryEntry load(CompoundTag tag) {
            return new ReplacementHistoryEntry(tag.getString("PreviousIdentity"), tag.getString("NewIdentity"),
                    tag.getLong("PreviousGeneration"), tag.getLong("NewGeneration"), tag.getString("Reason"),
                    tag.getLong("GameTime"), tag.getString("Operator"));
        }
    }
}
