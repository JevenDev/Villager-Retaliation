package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public final class QuestIds {
    private QuestIds() {
    }

    public static ResourceLocation parse(String value, ResourceLocation contextLocation) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.contains(":")) {
            return ResourceLocation.tryParse(normalized);
        }
        String namespace = contextLocation == null ? VillagerRetaliation.MOD_ID : contextLocation.getNamespace();
        ResourceLocation contextual = ResourceLocation.tryParse(namespace + ":" + normalized);
        return contextual == null ? ResourceLocation.tryParse(normalized) : contextual;
    }

    public static ResourceLocation parseInModNamespace(String value) {
        return parse(value, ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest"));
    }
}
