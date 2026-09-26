package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class SettlementManagementClient {
    private static final KeyMapping OPEN = new KeyMapping("key.villagerretaliation.settlement_management",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.villagerretaliation");

    private SettlementManagementClient() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.player.isAlive() && !minecraft.player.isSpectator()) {
                PacketDistributor.sendToServer(new SettlementManagementRequestPayload(null,
                        SettlementManagementRequestPayload.Page.TOWNS, 0, "", 0, true));
            }
        }
        if (current() != null && (minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.hurtTime > 0)) {
            minecraft.setScreen(null);
        }
    }

    public static void accept(SettlementManagementPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.hurtTime > 0
                || !minecraft.level.dimension().location().equals(payload.dimension())) {
            return;
        }
        if (payload.openScreen()) {
            minecraft.setScreen(new SettlementManagementScreen(payload));
        } else if (minecraft.screen instanceof SettlementViewScreen view) {
            view.acceptManagement(payload);
        } else {
            var current = current();
            if (current != null) {
                current.accept(payload);
            }
        }
    }

    public static void acceptResult(SettlementManagementActionResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof SettlementViewScreen view) {
            view.acceptResult(payload);
        }
        var current = current();
        if (current == null) {
            return;
        }
        current.result(payload);
        if (Minecraft.getInstance().screen instanceof SettlementEditScreen form) {
            form.result(payload);
        } else if (Minecraft.getInstance().screen instanceof SettlementWorkerScreen form) {
            form.result(payload);
        }
    }

    static SettlementManagementScreen current() {
        return switch (Minecraft.getInstance().screen) {
            case SettlementManagementScreen screen -> screen;
            case SettlementViewScreen view -> view.parent();
            case SettlementEditScreen form -> form.parent();
            case SettlementWorkerScreen form -> form.parent();
            case SettlementConfirmScreen confirmation -> confirmation.parent();
            case SettlementItemPickerScreen picker -> picker.parent().parent();
            case null, default -> null;
        };
    }
}
