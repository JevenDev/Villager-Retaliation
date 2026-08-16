package com.jvn.villagerretaliation.interaction.work.logging;

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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class HiredLoggingFilters {
    public static final String LEGACY_FILTER_TAG = "LoggingFilter";
    private static final String FILTERS_TAG = "LoggingFilters";
    private static final String ANY_FILTER = "any";

    private HiredLoggingFilters() {
    }

    public static List<ResourceLocation> options() {
        return BuiltInRegistries.BLOCK.holders()
                .filter(holder -> holder.is(BlockTags.LOGS))
                .map(holder -> holder.unwrapKey()
                        .map(key -> key.location())
                        .orElseGet(() -> BuiltInRegistries.BLOCK.getKey(holder.value())))
                .distinct()
                .sorted(Comparator
                        .comparing(ResourceLocation::getNamespace)
                        .thenComparing(ResourceLocation::getPath))
                .toList();
    }

    public static Set<ResourceLocation> selectedFilterIds(CompoundTag state) {
        Set<ResourceLocation> selected = new LinkedHashSet<>();
        if (state.contains(FILTERS_TAG, Tag.TAG_LIST)) {
            ListTag list = state.getList(FILTERS_TAG, Tag.TAG_STRING);
            for (Tag entry : list) {
                if (entry instanceof StringTag stringTag) {
                    ResourceLocation id = resolveStoredFilterId(stringTag.getAsString());
                    if (id != null) {
                        selected.add(id);
                    }
                }
            }
            return selected;
        }

        ResourceLocation legacyId = resolveStoredFilterId(state.getString(LEGACY_FILTER_TAG));
        if (legacyId != null) {
            selected.add(legacyId);
        }
        return selected;
    }

    public static void toggleFilter(CompoundTag state, String filterId) {
        if (filterId == null || filterId.isBlank() || ANY_FILTER.equals(filterId)) {
            clearFilters(state);
            return;
        }

        ResourceLocation resolved = resolveStoredFilterId(filterId);
        if (resolved == null) {
            return;
        }

        Set<ResourceLocation> selected = selectedFilterIds(state);
        if (!selected.remove(resolved)) {
            selected.add(resolved);
        }
        writeSelectedFilters(state, selected);
    }

    public static boolean matches(BlockState state, Set<ResourceLocation> selectedFilterIds) {
        if (selectedFilterIds == null || selectedFilterIds.isEmpty()) {
            return true;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return selectedFilterIds.contains(blockId);
    }

    public static List<String> selectedFilterStrings(CompoundTag state) {
        return selectedFilterIds(state).stream()
                .map(ResourceLocation::toString)
                .toList();
    }

    public static String label(ResourceLocation id) {
        if (id == null) {
            return "any logs";
        }
        String path = id.getPath().replace('_', ' ');
        return "minecraft".equals(id.getNamespace()) ? path : id.getNamespace() + ":" + path;
    }

    public static String selectionLabel(CompoundTag state) {
        Set<ResourceLocation> selected = selectedFilterIds(state);
        if (selected.isEmpty()) {
            return "any logs";
        }
        if (selected.size() == 1) {
            return label(selected.iterator().next());
        }
        return selected.size() + " log types";
    }

    private static void writeSelectedFilters(
            CompoundTag state,
            Set<ResourceLocation> selected) {
        if (selected.isEmpty()) {
            clearFilters(state);
            return;
        }

        ListTag list = new ListTag();
        for (ResourceLocation option : selected.stream()
                .sorted(Comparator.comparing(ResourceLocation::getNamespace)
                        .thenComparing(ResourceLocation::getPath))
                .toList()) {
            list.add(StringTag.valueOf(option.toString()));
        }
        if (list.isEmpty()) {
            clearFilters(state);
            return;
        }
        state.put(FILTERS_TAG, list);
        state.putString(LEGACY_FILTER_TAG, list.getString(0));
    }

    private static void clearFilters(CompoundTag state) {
        state.remove(FILTERS_TAG);
        state.putString(LEGACY_FILTER_TAG, ANY_FILTER);
    }

    private static ResourceLocation resolveStoredFilterId(String filter) {
        if (filter == null || filter.isBlank() || ANY_FILTER.equals(filter)) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(filter);
        if (parsed != null && isLogBlock(parsed)) {
            return parsed;
        }
        // Pre-namespaced saves stored only the path. This slower migration path is never used for
        // modern persisted selections, keeping worker ticks independent of a full registry scan.
        for (ResourceLocation option : options()) {
            if (option.getPath().equals(filter)) {
                return option;
            }
        }
        return null;
    }

    private static boolean isLogBlock(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id)
                .map(block -> block.defaultBlockState().is(BlockTags.LOGS))
                .orElse(false);
    }
}
