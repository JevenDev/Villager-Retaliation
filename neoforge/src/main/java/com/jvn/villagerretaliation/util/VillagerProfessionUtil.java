package com.jvn.villagerretaliation.util;

import com.jvn.toucanlib.util.ToucanText;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerProfessionUtil {
    private static final ResourceLocation NONE_ID = ResourceLocation.withDefaultNamespace("none");

    private VillagerProfessionUtil() {
    }

    public static Optional<VillagerProfession> parse(String value) {
        return parseId(value).flatMap(location -> BuiltInRegistries.VILLAGER_PROFESSION.getOptional(location));
    }

    public static ResourceLocation id(VillagerProfession profession) {
        if (profession == null) {
            return NONE_ID;
        }

        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return id == null ? NONE_ID : id;
    }

    public static String serializedKey(VillagerProfession profession) {
        ResourceLocation id = id(profession);
        if (NONE_ID.equals(id)) {
            return "none";
        }
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    public static String translationKey(VillagerProfession profession, String fallbackForNone) {
        ResourceLocation id = id(profession);
        if (NONE_ID.equals(id)) {
            return fallbackForNone;
        }

        String path = id.getPath().replace('/', '.');
        if ("minecraft".equals(id.getNamespace())) {
            return "entity.minecraft.villager." + path;
        }
        return "entity.minecraft.villager." + id.getNamespace() + "." + path;
    }

    public static String displayName(VillagerProfession profession, String fallbackForNone) {
        ResourceLocation id = id(profession);
        if (NONE_ID.equals(id)) {
            return fallbackForNone;
        }

        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        return ToucanText.titleCaseIdentifier(name);
    }

    private static Optional<ResourceLocation> parseId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("unemployed".equals(normalized) || "minecraft:unemployed".equals(normalized)) {
            normalized = "none";
        }
        String namespaced = normalized.contains(":") ? normalized : "minecraft:" + normalized;
        return Optional.ofNullable(ResourceLocation.tryParse(namespaced));
    }
}
