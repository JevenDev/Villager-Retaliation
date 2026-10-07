package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID)
public final class VillageLifeEvents {
    private VillageLifeEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Villager villager) VillageLifeService.beforeTick(villager);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Villager villager) VillageLifeService.afterTick(villager);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        VillageLifeService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        VillageLifeService.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (!event.isCanceled()) VillageLifeService.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        VillageLifeService.clear(event.getServer());
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("villagelife").requires(source -> source.hasPermission(2))
                .then(Commands.literal("diagnostics").executes(context -> {
                    var diagnostics = VillageLifeService.diagnostics(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(diagnostics.toString()), false);
                    return 1;
                })));
    }
}
