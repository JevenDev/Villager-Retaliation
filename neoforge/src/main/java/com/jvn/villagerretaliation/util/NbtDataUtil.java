package com.jvn.villagerretaliation.util;

import com.jvn.toucanlib.util.ToucanNbt;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

public final class NbtDataUtil {
    private NbtDataUtil() { }
    public static CompoundTag blockPos(BlockPos pos) { return ToucanNbt.blockPos(pos); }
    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) { return ToucanNbt.readBlockPos(tag, key); }
    public static void putBlockPos(CompoundTag tag, String key, BlockPos pos) { ToucanNbt.putBlockPos(tag, key, pos); }
    public static Optional<ResourceLocation> readResourceLocation(CompoundTag tag, String key) {
        return ToucanNbt.readResourceLocation(tag, key);
    }
    public static void putResourceLocation(CompoundTag tag, String key, ResourceLocation id) {
        ToucanNbt.putResourceLocation(tag, key, id);
    }
    public static Set<String> readStringSet(CompoundTag tag, String key) { return ToucanNbt.readStringSet(tag, key); }
    public static ListTag stringList(Iterable<String> values) { return ToucanNbt.stringList(values); }
}
