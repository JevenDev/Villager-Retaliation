package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.interaction.CourierRouteChunkLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Lifecycle hooks kept separate so courier tickets can always be released on unload and stop. */
@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID)
public final class CourierChunkLoadingEvents {
    private CourierChunkLoadingEvents() {
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Villager villager
                && villager.level() instanceof ServerLevel level) {
            CourierRouteChunkLoader.onVillagerTick(level, villager);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Villager villager
                && event.getLevel() instanceof ServerLevel level) {
            CourierRouteChunkLoader.onVillagerLeaveLevel(level, villager);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CourierRouteChunkLoader.clearRuntimeState(event.getServer());
    }
}
