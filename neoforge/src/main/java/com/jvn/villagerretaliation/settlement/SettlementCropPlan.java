package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record SettlementCropPlan(List<Integer> percentages, boolean demand, int cycles) {
    public SettlementCropPlan(List<Integer> percentages, boolean demand) { this(percentages, demand, 0); }
    public SettlementCropPlan(List<Integer> percentages) { this(percentages, false); }
    public static final SettlementCropPlan NONE = new SettlementCropPlan(List.of(0, 0, 0, 0, 0, 0));

    public SettlementCropPlan {
        if (cycles < 0 || cycles > 2) throw new IllegalArgumentException("Invalid planting reserve cycles");
        percentages = List.copyOf(percentages);
        int total = percentages.stream().mapToInt(Integer::intValue).sum();
        if (percentages.size() != Crop.values().length - 1 || percentages.stream().anyMatch(value -> value < 0 || value > 100)
                || total != 0 && total != 100) throw new IllegalArgumentException("Crop percentages must total 100 or zero");
    }

    public boolean enabled() { return percentages.stream().anyMatch(value -> value > 0); }

    public boolean permits(Crop crop) {
        return crop != Crop.ANY && (!enabled() || percentages.get(crop.ordinal() - 1) > 0);
    }

    public Crop crop(SettlementArea area, BlockPos soil) {
        if (!enabled()) return Crop.ANY;
        int width = area.max().getX() - area.min().getX() + 1;
        int size = width * (area.max().getZ() - area.min().getZ() + 1);
        int index = Math.floorMod((soil.getZ() - area.min().getZ()) * width + soil.getX() - area.min().getX(), size);
        int cumulative = 0;
        for (int crop = 0; crop < percentages.size(); crop++) {
            cumulative += percentages.get(crop);
            if ((long) index * 100 < (long) cumulative * size) return Crop.values()[crop + 1];
        }
        throw new IllegalStateException("Incomplete crop plan");
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putBoolean("Demand", demand);
        tag.putInt("Cycles", cycles);
        tag.putIntArray("Percentages", percentages.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    public static SettlementCropPlan load(CompoundTag tag) {
        if (!tag.contains("Percentages", Tag.TAG_INT_ARRAY)) throw new IllegalArgumentException("Missing crop percentages");
        if (tag.contains("Demand") && !tag.contains("Demand", Tag.TAG_BYTE)) throw new IllegalArgumentException("Invalid demand mode");
        if (tag.contains("Cycles") && !tag.contains("Cycles", Tag.TAG_INT)) throw new IllegalArgumentException("Invalid planting reserve cycles");
        return new SettlementCropPlan(java.util.Arrays.stream(tag.getIntArray("Percentages")).boxed().toList(), tag.getBoolean("Demand"), tag.getInt("Cycles"));
    }

    public void write(RegistryFriendlyByteBuf buffer) { percentages.forEach(buffer::writeVarInt); buffer.writeBoolean(demand); buffer.writeVarInt(cycles); }

    public static SettlementCropPlan read(RegistryFriendlyByteBuf buffer) {
        var values = new java.util.ArrayList<Integer>();
        for (int i = 1; i < Crop.values().length; i++) values.add(buffer.readVarInt());
        return new SettlementCropPlan(values, buffer.readBoolean(), buffer.readVarInt());
    }
}
