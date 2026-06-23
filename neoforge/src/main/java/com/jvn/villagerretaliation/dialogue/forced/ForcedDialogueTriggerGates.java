package com.jvn.villagerretaliation.dialogue.forced;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOutputMode;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueTrigger;

final class ForcedDialogueTriggerGates {
    private ForcedDialogueTriggerGates() {
    }

    static boolean containerEnabled() {
        return VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                && VillagerRetaliationConfig.ENABLE_CONTAINER_FORCED_DIALOGUE.get();
    }

    static boolean retaliationEnabled() {
        return VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                && VillagerRetaliationConfig.ENABLE_RETALIATION_FORCED_DIALOGUE.get();
    }

    static boolean playerItemProximityEnabled() {
        return VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get()
                && VillagerRetaliationConfig.ENABLE_PLAYER_ITEM_PROXIMITY_FORCED_DIALOGUE.get();
    }

    static boolean isSharedContainerTrigger(ForcedDialogueTrigger trigger) {
        return trigger == ForcedDialogueTrigger.CONTAINER_THEFT
                || trigger == ForcedDialogueTrigger.CONTAINER_OPENED;
    }

    static boolean isChatOutput(ForcedDialogueDefinition definition) {
        return definition.output().mode() == ForcedDialogueOutputMode.CHAT;
    }
}
