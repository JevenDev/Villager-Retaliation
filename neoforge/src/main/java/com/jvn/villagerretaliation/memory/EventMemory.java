package com.jvn.villagerretaliation.memory;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public interface EventMemory {
    ResourceLocation tagId();
    long gameTime();
    UUID sourceId();
    UUID playerId();
}
