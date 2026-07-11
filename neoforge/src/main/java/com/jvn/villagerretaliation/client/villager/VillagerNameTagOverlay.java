package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.client.config.VillagerRetaliationClientPreferences;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionVisibilityFade;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

public final class VillagerNameTagOverlay {
    private static final double MAX_NAME_TAG_DISTANCE = 64.0D;

    private VillagerNameTagOverlay() {
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!VillagerRetaliationServerConfigClient.showVillagerNameTags()
                || !VillagerRetaliationClientPreferences.showVillagerNameTags()
                || !VillagerPresetNameRegistry.isVillagerForm(event.getEntity())
                || event.getEntity().hasCustomName()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (minecraft.player.distanceToSqr(event.getEntity()) > MAX_NAME_TAG_DISTANCE * MAX_NAME_TAG_DISTANCE) {
            return;
        }

        VillagerNameClientCache.displayName(event.getEntity().getId()).ifPresent(name -> {
            event.setContent(name);
            event.setCanRender(TriState.TRUE);
        });
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            while (VillagerNameTagKeyMappings.TOGGLE_NAME_TAGS.consumeClick()) {
                boolean enabled = VillagerRetaliationClientPreferences.toggleShowVillagerNameTags();
                String key = enabled
                        ? "message.villagerretaliation.villager_name_tags.enabled"
                        : "message.villagerretaliation.villager_name_tags.disabled";
                minecraft.player.displayClientMessage(Component.translatable(key), true);
            }
        }
        VillagerNameClientCache.pruneMissing();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VillagerNameClientCache.clear();
        VillagerRetaliationServerConfigClient.reset();
        VillagerInteractionVisibilityFade.reset();
    }
}
