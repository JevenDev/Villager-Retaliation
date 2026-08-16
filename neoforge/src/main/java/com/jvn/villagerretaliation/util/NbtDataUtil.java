package com.jvn.villagerretaliation.util;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class NbtDataUtil {
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";

    private NbtDataUtil() {
    }

    public static CompoundTag blockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_X, pos.getX());
        tag.putInt(TAG_Y, pos.getY());
        tag.putInt(TAG_Z, pos.getZ());
        return tag;
    }

    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag posTag = tag.getCompound(key);
        return Optional.of(new BlockPos(posTag.getInt(TAG_X), posTag.getInt(TAG_Y), posTag.getInt(TAG_Z)));
    }

    public static void putBlockPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos != null) {
            tag.put(key, blockPos(pos));
        }
    }

    public static Optional<ResourceLocation> readResourceLocation(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(tag.getString(key)));
    }

    public static void putResourceLocation(CompoundTag tag, String key, ResourceLocation id) {
        if (id != null) {
            tag.putString(key, id.toString());
        }
    }

    public static Set<String> readStringSet(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        ListTag valuesTag = tag.getList(key, Tag.TAG_STRING);
        for (Tag rawValue : valuesTag) {
            String value = rawValue.getAsString();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return Set.copyOf(values);
    }

    public static ListTag stringList(Iterable<String> values) {
        ListTag listTag = new ListTag();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                listTag.add(StringTag.valueOf(value));
            }
        }
        return listTag;
    }
}
