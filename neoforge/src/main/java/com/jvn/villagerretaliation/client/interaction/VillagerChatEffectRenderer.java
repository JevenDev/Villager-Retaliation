package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;

public final class VillagerChatEffectRenderer {
    private VillagerChatEffectRenderer() {
    }

    public static void startReappearFade() {
        VillagerInteractionVisibilityFade.fadeIn();
    }

    public static void startDisappearFade() {
        VillagerInteractionVisibilityFade.fadeOut();
    }

    static boolean animatedChatEffectsEnabled() {
        return !VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get();
    }
}
