package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class VillagerInteractionClientHandler {
    private VillagerInteractionClientHandler() {
    }

    public static void open(OpenVillagerInteractionPayload payload) {
        Minecraft.getInstance().setScreen(new VillagerInteractionScreen(
                payload.entityId(),
                payload.villagerName(),
                payload.professionName(),
                payload.reputation(),
                payload.reputationLevel(),
                payload.greetingText()
        ));
    }

    public static void acceptDialogue(VillagerDialogueResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.setDialogueText(payload.text());
        }
    }

    public static void acceptNotice(VillagerInteractionNoticePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen && screen.matchesVillager(payload.entityId())) {
            screen.showNotice(payload.text());
            return;
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(payload.text()), false);
        }
    }

    public static void acceptConversationEnded(VillagerConversationEndedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.closeFromServer();
        }
        if (minecraft.player != null && !payload.goodbyeText().isBlank()) {
            minecraft.player.displayClientMessage(Component.literal(payload.goodbyeText()), false);
        }
    }
}
