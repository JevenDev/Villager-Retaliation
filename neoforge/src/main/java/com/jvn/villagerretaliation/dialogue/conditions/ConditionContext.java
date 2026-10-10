package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public record ConditionContext(
        UUID playerId,
        UUID providerId,
        ProviderSnapshot savedProvider,
        Entity liveProvider,
        QuestRun questRun,
        Set<ConditionCapability> capabilities,
        ConditionLeafAccess queries
) {
    public ConditionContext {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        if (queries == null) throw new IllegalArgumentException("condition queries must not be null");
    }

    public record ProviderSnapshot(int level, ResourceKey<Level> dimension, BlockPos pos, String villageKey) {
        public ProviderSnapshot {
            pos = pos == null ? null : pos.immutable();
            villageKey = villageKey == null ? "" : villageKey;
        }
    }

    public record QuestRun(ResourceLocation questId, UUID runId) { }
}
