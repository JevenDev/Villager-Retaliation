package com.jvn.villagerretaliation.notification;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;

public record VillagerNotificationContext(
        ServerLevel level,
        AbstractVillager villager,
        Player player,
        LivingEntity target,
        int reputation,
        VillagerReputationLevel reputationLevel,
        RandomSource random,
        String locale) {
    public VillagerProfession profession() {
        return this.villager instanceof Villager resident
                ? resident.getVillagerData().getProfession()
                : VillagerProfession.NONE;
    }

    public ResourceLocation targetEntityTypeId() {
        return this.target == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(this.target.getType());
    }
}
