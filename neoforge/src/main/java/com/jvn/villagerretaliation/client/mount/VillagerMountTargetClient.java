package com.jvn.villagerretaliation.client.mount;

import com.jvn.villagerretaliation.network.VillagerMountTargetCancelPayload;
import com.jvn.villagerretaliation.network.VillagerMountTargetModePayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class VillagerMountTargetClient {
    private static boolean active;
    private static int remainingTicks;

    private VillagerMountTargetClient() {
    }

    public static void accept(VillagerMountTargetModePayload payload) {
        active = payload.active();
        remainingTicks = active ? Math.max(1, payload.remainingTicks()) : 0;
    }

    public static void onKey(InputEvent.Key event) {
        if (!active || event.getKey() != GLFW.GLFW_KEY_ESCAPE || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        active = false;
        remainingTicks = 0;
        PacketDistributor.sendToServer(new VillagerMountTargetCancelPayload());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (active && remainingTicks > 0 && --remainingTicks == 0) {
            active = false;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        remainingTicks = 0;
    }
}
