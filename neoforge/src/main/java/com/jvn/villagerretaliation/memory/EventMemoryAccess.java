package com.jvn.villagerretaliation.memory;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public interface EventMemoryAccess {
    List<? extends EventMemory> personal(UUID entity);
    List<? extends EventMemory> group(String group);
    boolean remember(DialogueRuntimeContext context, ResourceLocation tag, boolean personal, boolean group);
}
