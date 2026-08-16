package com.jvn.villagerretaliation.mount;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record VillagerMountAssignment(
        UUID villagerId,
        UUID mountId,
        ResourceLocation mountType,
        ResourceLocation mountDimension,
        BlockPos lastMountPosition,
        ResourceLocation parkingDimension,
        BlockPos parkingPosition,
        long assignedGameTime) {

    public VillagerMountAssignment withMountLocation(ResourceLocation dimension, BlockPos position) {
        return new VillagerMountAssignment(villagerId, mountId, mountType, dimension, position,
                parkingDimension, parkingPosition, assignedGameTime);
    }

    public VillagerMountAssignment withParkingAnchor(ResourceLocation dimension, BlockPos position) {
        return new VillagerMountAssignment(villagerId, mountId, mountType, mountDimension, lastMountPosition,
                dimension, position, assignedGameTime);
    }

    public VillagerMountAssignment withoutParkingAnchor() {
        return withParkingAnchor(null, null);
    }
}
