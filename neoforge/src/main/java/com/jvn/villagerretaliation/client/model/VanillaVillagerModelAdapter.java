package com.jvn.villagerretaliation.client.model;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.npc.AbstractVillager;

public class VanillaVillagerModelAdapter<T extends AbstractVillager> extends BaseVillagerModel<T> {
    private final VillagerModel<T> vanillaModel;
    private final VillagerDialogueMouthParts dialogueMouthParts;

    public VanillaVillagerModelAdapter(ModelPart root) {
        this.vanillaModel = new VillagerModel<>(root);
        this.dialogueMouthParts = VillagerDialogueMouthParts.find(root);
    }

    @Override
    public void setupAnim(T villager, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.vanillaModel.setupAnim(villager, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.dialogueMouthParts.apply(villager, ageInTicks);
    }

    @Override
    public ModelPart root() {
        return this.vanillaModel.root();
    }

    @Override
    public ModelPart getHead() {
        return this.vanillaModel.getHead();
    }

    @Override
    public void hatVisible(boolean visible) {
        this.vanillaModel.hatVisible(visible);
    }
}
