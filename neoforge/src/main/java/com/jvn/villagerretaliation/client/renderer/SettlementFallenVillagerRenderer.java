package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.settlement.SettlementFallenVillager;
import com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedPose;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementFallenVillagerRenderer extends EntityRenderer<SettlementFallenVillager> {
    private final VillagerRetaliationVillagerRenderer renderer;
    private final Map<SettlementFallenVillager, Villager> models = new WeakHashMap<>();
    public SettlementFallenVillagerRenderer(EntityRendererProvider.Context context) {
        super(context); this.renderer = new VillagerRetaliationVillagerRenderer(context);
    }
    private Villager model(SettlementFallenVillager body) {
        return models.computeIfAbsent(body, entity -> {
            var villager = EntityType.VILLAGER.create(entity.level());
            var appearance = entity.appearance(); villager.load(appearance);
            if (!appearance.hasUUID("UUID")) villager.setUUID(entity.getUUID());
            villager.setId(entity.getId());
            VillagerDownedClientCache.presentation(villager, VillagerDownedPose.SIDE_LYING);
            return villager;
        });
    }
    @Override public void render(SettlementFallenVillager body, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        var villager = model(body); villager.moveTo(body.position()); villager.tickCount = body.tickCount;

        this.renderer.render(villager, yaw, partialTick, poses, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(SettlementFallenVillager body) { return this.renderer.getTextureLocation(model(body)); }
}
