package com.jvn.villagerretaliation.combat.downed;

import java.util.UUID;
import net.minecraft.world.entity.EntityDimensions;

public enum VillagerDownedPose {
    SITTING(1.25F, 0.77F),
    SIDE_LYING(2.5F, 0.36F),
    HANDS_AND_KNEES(1.75F, 0.56F);

    private static final VillagerDownedPose[] VALUES = values();

    private final float widthScale;
    private final float heightScale;

    VillagerDownedPose(float widthScale, float heightScale) {
        this.widthScale = widthScale;
        this.heightScale = heightScale;
    }

    public static VillagerDownedPose forVillager(UUID villagerId) {
        return VALUES[Math.floorMod(villagerId.hashCode(), VALUES.length)];
    }

    public EntityDimensions dimensions(EntityDimensions standingDimensions) {
        return standingDimensions.scale(this.widthScale, this.heightScale);
    }
}
