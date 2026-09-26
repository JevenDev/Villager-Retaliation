package com.jvn.villagerretaliation.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

record SettlementForestTick(BlockPos position, ResourceLocation type, boolean fluid, int delay, TickPriority priority) {
    SettlementForestTick {
        position = position.immutable();
        if (delay < 0 || delay > 24_000 || !(fluid ? BuiltInRegistries.FLUID.containsKey(type) : BuiltInRegistries.BLOCK.containsKey(type))) {
            throw new IllegalArgumentException("Invalid deferred forest tick");
        }
    }

    void apply(LevelChunk chunk) {
        long now = chunk.getLevel().getGameTime();
        long order = chunk.getLevel().nextSubTickCount();
        if (this.fluid) {
            var fluidType = BuiltInRegistries.FLUID.get(this.type);
            if (chunk.getFluidState(this.position).is(fluidType)) {
                chunk.getFluidTicks().schedule(new ScheduledTick<>(fluidType, this.position, now + this.delay, this.priority, order));
            }
        } else {
            var block = BuiltInRegistries.BLOCK.get(this.type);
            if (chunk.getBlockState(this.position).is(block)) {
                chunk.getBlockTicks().schedule(new ScheduledTick<>(block, this.position, now + this.delay, this.priority, order));
            }
        }
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Position", this.position.asLong());
        tag.putString("Type", this.type.toString());
        tag.putBoolean("Fluid", this.fluid);
        tag.putInt("Delay", this.delay);
        tag.putInt("Priority", this.priority.getValue());
        return tag;
    }

    static SettlementForestTick load(CompoundTag tag) {
        if (!tag.contains("Position", net.minecraft.nbt.Tag.TAG_LONG) || tag.getInt("Priority") < -3 || tag.getInt("Priority") > 3) {
            throw new IllegalArgumentException("Invalid deferred forest tick position or priority");
        }
        var type = ResourceLocation.tryParse(tag.getString("Type"));
        if (type == null) throw new IllegalArgumentException("Deferred forest tick requires a registered type");
        return new SettlementForestTick(BlockPos.of(tag.getLong("Position")), type, tag.getBoolean("Fluid"),
                tag.getInt("Delay"), TickPriority.byValue(tag.getInt("Priority")));
    }
}
