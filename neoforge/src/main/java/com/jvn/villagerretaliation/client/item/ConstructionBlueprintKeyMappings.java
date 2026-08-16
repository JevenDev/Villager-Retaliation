package com.jvn.villagerretaliation.client.item;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ConstructionBlueprintKeyMappings {
    public static final KeyMapping TOGGLE_PLACEMENT_LOCK = new KeyMapping(
            "key.villagerretaliation.toggle_blueprint_placement_lock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.villagerretaliation"
    );

    private ConstructionBlueprintKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_PLACEMENT_LOCK);
    }
}
