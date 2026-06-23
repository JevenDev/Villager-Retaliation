package com.jvn.villagerretaliation.interaction.work;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record HiredPathTarget(BlockPos blockPos, BlockPos approachPos, Vec3 hitPos) {
}
