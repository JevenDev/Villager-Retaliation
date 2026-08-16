package com.jvn.villagerretaliation.client.config;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.WeaponAimingDialogueDelayPreferencePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WeaponAimingDialogueDelayClientPreference {
    private static Boolean lastSent;

    private WeaponAimingDialogueDelayClientPreference() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            lastSent = null;
            return;
        }

        boolean enabled = VillagerRetaliationConfig.ENABLE_WEAPON_AIMING_DIALOGUE_DELAY.get();
        if (lastSent != null && lastSent == enabled) {
            return;
        }
        PacketDistributor.sendToServer(new WeaponAimingDialogueDelayPreferencePayload(enabled));
        lastSent = enabled;
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastSent = null;
    }
}
