package com.jvn.villagerretaliation.client.settlement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

final class SettlementViewKeyMappings {
    static final KeyMapping OPEN = new KeyMapping("key.villagerretaliation.settlement_world_view", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.villagerretaliation");
    static final KeyMapping PEEK = key("peek", GLFW.GLFW_KEY_GRAVE_ACCENT);
    static final KeyMapping SELECT = key("select", GLFW.GLFW_KEY_V);
    static final KeyMapping MOVE = key("move", GLFW.GLFW_KEY_G);
    static final KeyMapping RESIZE = key("resize", GLFW.GLFW_KEY_R);
    static final KeyMapping DRAW = key("draw", GLFW.GLFW_KEY_B);
    static final KeyMapping PLOTS = key("plots", GLFW.GLFW_KEY_P);
    static final KeyMapping BOUNDS = key("bounds", GLFW.GLFW_KEY_L);
    static final KeyMapping ACTIONS = key("actions", GLFW.GLFW_KEY_C);

    private SettlementViewKeyMappings() { }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.villagerretaliation.settlement_" + name, KeyConflictContext.GUI,
                InputConstants.Type.KEYSYM, code, "key.categories.villagerretaliation");
    }

    static void register(RegisterKeyMappingsEvent event) {
        for (var mapping : new KeyMapping[]{OPEN, SELECT, MOVE, RESIZE, DRAW, PLOTS, BOUNDS, ACTIONS, PEEK}) event.register(mapping);
    }

    static boolean matches(KeyMapping mapping, int key, int scan) {
        return mapping.isActiveAndMatches(InputConstants.getKey(key, scan));
    }

    static Component hint(Component label, KeyMapping mapping) {
        return label.copy().append(" [").append(mapping.getTranslatedKeyMessage()).append("]");
    }
}
