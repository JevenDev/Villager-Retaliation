package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

interface ConversationEffects {
    ActionResult apply(Operation operation);
    ActionResult poll(ServerPlayer controller, ActionResult waiting);
    boolean recoverable(VillagerActionDefinition action);

    default boolean recoverable(DialogueRuntimeContext context, VillagerActionDefinition action) {
        return context instanceof DialogueContext && recoverable(action);
    }

    record Operation(DialogueRuntimeContext context, ServerPlayer controller, ConversationSession.Identity identity,
            String effect, UUID actor, UUID questRun, VillagerActionDefinition action, Map<String, String> replacements,
            CompoundTag consent, CompoundTag participants) {
        public Operation {
            replacements = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(replacements));
            consent = consent.copy();
            participants = participants.copy();
        }
        @Override public CompoundTag consent() { return consent.copy(); }
        @Override public CompoundTag participants() { return participants.copy(); }
    }
}
