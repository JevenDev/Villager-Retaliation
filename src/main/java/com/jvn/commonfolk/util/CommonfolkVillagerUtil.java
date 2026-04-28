package com.jvn.commonfolk.util;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class CommonfolkVillagerUtil {
    private CommonfolkVillagerUtil() {
    }

    public static boolean isAdultVillager(Entity entity) {
        return entity instanceof Villager villager && !villager.isBaby();
    }

    public static boolean isBabyVillager(Entity entity) {
        return entity instanceof Villager villager && villager.isBaby();
    }

    public static Optional<VillagerProfession> professionOf(LivingEntity entity) {
        if (entity instanceof Villager villager) {
            return Optional.of(villager.getVillagerData().getProfession());
        }

        return Optional.empty();
    }
}
