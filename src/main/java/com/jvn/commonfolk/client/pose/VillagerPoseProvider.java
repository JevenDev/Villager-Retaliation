package com.jvn.commonfolk.client.pose;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.AbstractVillager;

public interface VillagerPoseProvider<T extends AbstractVillager> {
    VillagerArmPose getArmPose(T villager, float attackTime);

    boolean shouldUseCombatModel(T villager);

    boolean shouldRenderHeldItem(T villager);

    default InteractionHand getPreferredHand(T villager) {
        return villager.getMainHandItem().isEmpty() && !villager.getOffhandItem().isEmpty()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }
}
