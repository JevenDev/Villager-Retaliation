package com.jvn.villagerretaliation.interaction.work;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;

public final class HiredAnimalBreedingTargets {
    private static final String TARGETS_TAG = "AnimalBreedingTargets";
    private static final String ALL_TARGETS = "all";

    private HiredAnimalBreedingTargets() {
    }

    public static List<ResourceLocation> options() {
        return BuiltInRegistries.ENTITY_TYPE.holders()
                .map(holder -> holder.unwrapKey()
                        .map(key -> key.location())
                        .orElseGet(() -> BuiltInRegistries.ENTITY_TYPE.getKey(holder.value())))
                .filter(id -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                    return type != null
                            && type.canSummon()
                            && Animal.class.isAssignableFrom(type.getBaseClass());
                })
                .distinct()
                .sorted(Comparator
                        .comparing(ResourceLocation::getNamespace)
                        .thenComparing(ResourceLocation::getPath))
                .toList();
    }

    public static Set<ResourceLocation> selectedTargetIds(CompoundTag state) {
        List<ResourceLocation> options = options();
        Set<ResourceLocation> selected = new LinkedHashSet<>();
        if (!state.contains(TARGETS_TAG, Tag.TAG_LIST)) {
            return selected;
        }
        ListTag list = state.getList(TARGETS_TAG, Tag.TAG_STRING);
        for (Tag entry : list) {
            if (entry instanceof StringTag stringTag) {
                ResourceLocation id = resolveTargetId(stringTag.getAsString(), options);
                if (id != null) {
                    selected.add(id);
                }
            }
        }
        return selected;
    }

    public static void toggleTarget(CompoundTag state, String targetId) {
        if (targetId == null || targetId.isBlank() || ALL_TARGETS.equals(targetId)) {
            clearTargets(state);
            return;
        }

        List<ResourceLocation> options = options();
        ResourceLocation resolved = resolveTargetId(targetId, options);
        if (resolved == null) {
            return;
        }

        Set<ResourceLocation> selected = selectedTargetIds(state);
        if (!selected.remove(resolved)) {
            selected.add(resolved);
        }
        writeSelectedTargets(state, selected, options);
    }

    public static boolean matches(Animal animal, Set<ResourceLocation> selectedTargetIds) {
        if (selectedTargetIds == null || selectedTargetIds.isEmpty()) {
            return true;
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        return selectedTargetIds.contains(typeId);
    }

    public static List<String> selectedTargetStrings(CompoundTag state) {
        return selectedTargetIds(state).stream()
                .map(ResourceLocation::toString)
                .toList();
    }

    public static String label(ResourceLocation id) {
        if (id == null) {
            return "all animals";
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type != null) {
            String name = type.getDescription().getString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        String path = id.getPath().replace('_', ' ');
        return "minecraft".equals(id.getNamespace()) ? path : id.getNamespace() + ":" + path;
    }

    public static String selectionLabel(CompoundTag state) {
        Set<ResourceLocation> selected = selectedTargetIds(state);
        if (selected.isEmpty()) {
            return "all animals";
        }
        if (selected.size() == 1) {
            return label(selected.iterator().next());
        }
        return selected.size() + " animal types";
    }

    private static void writeSelectedTargets(
            CompoundTag state,
            Set<ResourceLocation> selected,
            List<ResourceLocation> options) {
        if (selected.isEmpty()) {
            clearTargets(state);
            return;
        }

        ListTag list = new ListTag();
        for (ResourceLocation option : options) {
            if (selected.contains(option)) {
                list.add(StringTag.valueOf(option.toString()));
            }
        }
        if (list.isEmpty()) {
            clearTargets(state);
            return;
        }
        state.put(TARGETS_TAG, list);
    }

    private static void clearTargets(CompoundTag state) {
        state.remove(TARGETS_TAG);
    }

    private static ResourceLocation resolveTargetId(String target, List<ResourceLocation> options) {
        if (target == null || target.isBlank() || ALL_TARGETS.equals(target)) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(target);
        if (parsed != null && options.contains(parsed)) {
            return parsed;
        }
        for (ResourceLocation option : options) {
            if (option.getPath().equals(target)) {
                return option;
            }
        }
        return null;
    }
}
