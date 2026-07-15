package com.jvn.villagerretaliation.combat.downed;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;

public enum VillagerDownedPose {
    SITTING(VillagerRetaliation.id("sitting"), 1.25F, 0.77F),
    SIDE_LYING(VillagerRetaliation.id("side_lying"), 2.5F, 0.36F),
    HANDS_AND_KNEES(VillagerRetaliation.id("hands_and_knees"), 1.75F, 0.56F),
    SECOND_WIND_CRAWL(ResourceLocation.fromNamespaceAndPath("secondwind", "crawl"), 1.75F, 0.42F);

    private static final VillagerDownedPose[] ORIGINAL_VALUES = {SITTING, SIDE_LYING, HANDS_AND_KNEES};

    private final ResourceLocation id;
    private final float widthScale;
    private final float heightScale;

    VillagerDownedPose(ResourceLocation id, float widthScale, float heightScale) {
        this.id = id;
        this.widthScale = widthScale;
        this.heightScale = heightScale;
    }

    public static VillagerDownedPose forVillager(UUID villagerId) {
        return ORIGINAL_VALUES[Math.floorMod(villagerId.hashCode(), ORIGINAL_VALUES.length)];
    }

    public static Optional<VillagerDownedPose> fromId(ResourceLocation id) {
        for (VillagerDownedPose pose : values()) {
            if (pose.id.equals(id)) {
                return Optional.of(pose);
            }
        }
        return Optional.empty();
    }

    public ResourceLocation id() {
        return this.id;
    }

    public EntityDimensions dimensions(EntityDimensions standingDimensions) {
        return standingDimensions.scale(this.widthScale, this.heightScale);
    }
}
