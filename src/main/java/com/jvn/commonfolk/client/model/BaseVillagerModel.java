package com.jvn.commonfolk.client.model;

import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.VillagerHeadModel;
import net.minecraft.world.entity.npc.AbstractVillager;

public abstract class BaseVillagerModel<T extends AbstractVillager> extends HierarchicalModel<T> implements HeadedModel, VillagerHeadModel {
}
