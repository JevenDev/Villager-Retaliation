package com.jvn.villagerretaliation.client.villager;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class VillagerNameTagKeyMappings {
    public static final KeyMapping TOGGLE_NAME_TAGS = new KeyMapping(
            "key.villagerretaliation.toggle_villager_name_tags",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.villagerretaliation"
    );

    private VillagerNameTagKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_NAME_TAGS);
    }
}
