package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

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
            screen.updateReputation(payload.reputation(), payload.reputationLevel());
        }
    }

    public static void acceptNotice(VillagerInteractionNoticePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen && screen.matchesVillager(payload.entityId())) {
            screen.showNotice(payload.text());
            return;
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(formatVillagerChatMessage(minecraft, payload.entityId(), payload.text()), false);
        }
    }

    public static void acceptConversationEnded(VillagerConversationEndedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.closeFromServer();
        }
        if (minecraft.player != null && !payload.goodbyeText().isBlank()) {
            minecraft.player.displayClientMessage(formatVillagerChatMessage(minecraft, payload.entityId(), payload.goodbyeText()), false);
        }
    }

    private static Component formatVillagerChatMessage(Minecraft minecraft, int entityId, String text) {
        return Component.literal("<" + resolveVillagerSpeakerName(minecraft, entityId) + "> " + text);
    }

    private static String resolveVillagerSpeakerName(Minecraft minecraft, int entityId) {
        if (minecraft.level == null) {
            return "Villager";
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof Villager villager)) {
            return "Villager";
        }
        String profession = professionName(villager.getVillagerData().getProfession());
        if (!villager.hasCustomName()) {
            return profession + " Villager";
        }
        String customName = villager.getCustomName() == null ? "" : villager.getCustomName().getString().trim();
        if (customName.isBlank()) {
            return profession + " Villager";
        }
        return profession + " " + customName;
    }

    private static String professionName(VillagerProfession profession) {
        String rawName = profession == null ? null : profession.name();
        if (rawName == null || rawName.isBlank() || "none".equals(rawName)) {
            return "Villager";
        }
        StringBuilder builder = new StringBuilder(rawName.length());
        boolean capitalizeNext = true;
        for (char character : rawName.replace('_', ' ').toCharArray()) {
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
                builder.append(character);
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }
}
