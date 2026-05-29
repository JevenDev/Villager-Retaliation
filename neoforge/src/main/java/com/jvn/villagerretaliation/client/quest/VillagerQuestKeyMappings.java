package com.jvn.villagerretaliation.client.quest;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class VillagerQuestKeyMappings {
    public static final KeyMapping OPEN_JOURNAL = new KeyMapping(
            "key.villagerretaliation.open_quest_journal",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.villagerretaliation"
    );
    public static final KeyMapping TOGGLE_TRACKER = new KeyMapping(
            "key.villagerretaliation.toggle_quest_tracker",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.villagerretaliation"
    );

    private VillagerQuestKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNAL);
        event.register(TOGGLE_TRACKER);
    }
}
