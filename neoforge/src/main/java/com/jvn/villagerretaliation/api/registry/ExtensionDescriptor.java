package com.jvn.villagerretaliation.api.registry;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Public identity shared by every Villager Retaliation extension registry. */
public interface ExtensionDescriptor {
    ResourceLocation id();

    default Set<ResourceLocation> aliases() {
        return Set.of();
    }
}
