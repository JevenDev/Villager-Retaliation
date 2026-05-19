package com.jvn.villagerretaliation.client.villager;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

public final class VillagerNameTagOverlay {
    private static final double MAX_NAME_TAG_DISTANCE = 64.0D;

    private VillagerNameTagOverlay() {
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof AbstractVillager villager) || villager.hasCustomName()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (minecraft.player.distanceToSqr(villager) > MAX_NAME_TAG_DISTANCE * MAX_NAME_TAG_DISTANCE) {
            return;
        }

        VillagerNameClientCache.displayName(villager.getId()).ifPresent(name -> {
            event.setContent(name);
            event.setCanRender(TriState.TRUE);
        });
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        VillagerNameClientCache.pruneMissing();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VillagerNameClientCache.clear();
    }
}
