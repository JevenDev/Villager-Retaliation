package com.jvn.villagerretaliation.quest.provider;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record QuestProviderTypeDescriptor(
        ResourceLocation id,
        Set<ResourceLocation> capabilities
) {
    public QuestProviderTypeDescriptor {
        if (id == null) {
            throw new IllegalArgumentException("provider type id must not be null");
        }
        capabilities = freezeCapabilities(capabilities);
    }

    private static Set<ResourceLocation> freezeCapabilities(Set<ResourceLocation> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<ResourceLocation> copy = new LinkedHashSet<>();
        for (ResourceLocation value : values) {
            if (value != null) {
                copy.add(value);
            }
        }
        return copy.isEmpty() ? Set.of() : Set.copyOf(copy);
    }
}
