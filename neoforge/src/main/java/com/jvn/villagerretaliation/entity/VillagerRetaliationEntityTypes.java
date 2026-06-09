package com.jvn.villagerretaliation.entity;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationEntityTypes {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<VillagerFishingHook>> VILLAGER_FISHING_HOOK =
            ENTITY_TYPES.register("villager_fishing_hook", () -> EntityType.Builder
                    .<VillagerFishingHook>of(VillagerFishingHook::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(5)
                    .build("villager_fishing_hook"));

    private VillagerRetaliationEntityTypes() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
