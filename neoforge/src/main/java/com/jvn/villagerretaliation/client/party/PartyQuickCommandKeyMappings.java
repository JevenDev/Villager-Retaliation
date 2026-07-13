package com.jvn.villagerretaliation.client.party;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class PartyQuickCommandKeyMappings {
    private static final String CATEGORY = "key.categories.villagerretaliation";

    public static final KeyMapping QUICK_COMMAND = new KeyMapping(
            "key.villagerretaliation.quick_command",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY
    );

    private PartyQuickCommandKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(QUICK_COMMAND);
    }
}
