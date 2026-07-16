package com.jvn.villagerretaliation.client.mount;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.VillagerMountTargetCancelPayload;
import com.jvn.villagerretaliation.network.VillagerMountTargetModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
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

    public static void registerGuiLayer(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, VillagerRetaliation.id("mount_target_mode"),
                (graphics, partialTick) -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (!active || minecraft.options.hideGui || minecraft.player == null) {
                        return;
                    }
                    Font font = minecraft.font;
                    Component title = Component.translatable("villagerretaliation.mount.target_hud");
                    Component hint = Component.translatable("villagerretaliation.mount.target_hud_cancel");
                    int width = Math.max(font.width(title), font.width(hint)) + 16;
                    int x = (graphics.guiWidth() - width) / 2;
                    int y = graphics.guiHeight() - 76;
                    graphics.fill(x, y, x + width, y + 28, 0xB0101518);
                    graphics.drawCenteredString(font, title, graphics.guiWidth() / 2, y + 5, 0xFFF2E8C9);
                    graphics.drawCenteredString(font, hint, graphics.guiWidth() / 2, y + 16, 0xFFB8C0C7);
                });
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
