package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.memory.EventMemory;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MemoryConditions {
    private MemoryConditions() { }

    public static boolean matches(DialogueCondition.Memory condition, UUID player, UUID speaker,
            List<? extends EventMemory> personal, List<? extends EventMemory> group) {
        return switch (condition.source()) {
            case THIS_VILLAGER -> matches(condition, player, speaker, personal, false);
            case OTHER_VILLAGER -> matches(condition, player, speaker, group, true);
            case ANY -> matches(condition, player, speaker, personal, false) || matches(condition, player, speaker, group, false);
        };
    }

    private static boolean matches(DialogueCondition.Memory condition, UUID player, UUID speaker,
            List<? extends EventMemory> events, boolean excludeSpeaker) {
        for (var event : events) {
            if (condition.tags().contains(event.tagId())
                    && (!condition.currentPlayerOnly() || Objects.equals(player, event.playerId()))
                    && (!excludeSpeaker || !Objects.equals(speaker, event.sourceId()))) return true;
        }
        return false;
    }
}
