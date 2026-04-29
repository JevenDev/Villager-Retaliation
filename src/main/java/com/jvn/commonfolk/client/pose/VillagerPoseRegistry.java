package com.jvn.commonfolk.client.pose;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerPoseRegistry {
    private static final Map<VillagerProfession, VillagerArmPose> PROFESSION_ITEM_USE_POSES = new HashMap<>();

    static {
        PROFESSION_ITEM_USE_POSES.put(VillagerProfession.CLERIC, VillagerArmPose.CASTING_OR_POTION);
    }

    private VillagerPoseRegistry() {
    }

    public static Optional<VillagerArmPose> itemUsePose(Villager villager) {
        return Optional.ofNullable(PROFESSION_ITEM_USE_POSES.get(villager.getVillagerData().getProfession()));
    }

    public static void registerItemUsePose(VillagerProfession profession, VillagerArmPose pose) {
        PROFESSION_ITEM_USE_POSES.put(profession, pose);
    }
}
