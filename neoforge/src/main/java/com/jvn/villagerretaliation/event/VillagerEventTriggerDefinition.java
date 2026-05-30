package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record VillagerEventTriggerDefinition(
        ResourceLocation id,
        Listen listen,
        Set<ResourceLocation> memoryTags,
        Scope scope,
        List<DialogueCondition> conditions,
        List<VillagerActionDefinition> actions,
        long cooldownTicks,
        boolean repeatable) {
    public VillagerEventTriggerDefinition {
        listen = listen == null ? Listen.MEMORY_WRITTEN : listen;
        memoryTags = memoryTags == null ? Set.of() : Set.copyOf(memoryTags);
        scope = scope == null ? Scope.VILLAGE : scope;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        actions = actions == null ? List.of() : List.copyOf(actions);
        cooldownTicks = Math.max(0L, cooldownTicks);
    }

    public boolean listensTo(VillageEventMemory.MemoryEvent event) {
        if (this.listen != Listen.MEMORY_WRITTEN || event == null) {
            return false;
        }
        return this.memoryTags.isEmpty() || this.memoryTags.contains(event.tagId());
    }

    public enum Listen {
        MEMORY_WRITTEN;

        public static Listen bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "memory_written", "memory", "village_memory" -> MEMORY_WRITTEN;
                default -> MEMORY_WRITTEN;
            };
        }
    }

    public enum Scope {
        SOURCE_VILLAGER,
        PLAYER,
        VILLAGE;

        public static Scope bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "source", "source_villager", "villager" -> SOURCE_VILLAGER;
                case "player" -> PLAYER;
                default -> VILLAGE;
            };
        }
    }
}
