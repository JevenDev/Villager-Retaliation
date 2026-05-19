package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerInteractionClientHandler {
    private VillagerInteractionClientHandler() {
    }

    public static void open(OpenVillagerInteractionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        String villagerName = resolveVillagerName(payload.villagerNameKey(), payload.villagerNameFallback());
        ClientVillagerConversationState.rememberSpeakerLabel(
                payload.entityId(),
                formatSpeakerLabel(villagerName, payload.professionName())
        );
        minecraft.setScreen(new VillagerInteractionScreen(
                payload.entityId(),
                villagerName,
                payload.professionName(),
                payload.reputation(),
                payload.reputationLevel()
        ));
        pushVillagerChatMessage(minecraft, payload.entityId(), payload.greetingText());
    }

    public static void acceptDialogue(VillagerDialogueResponsePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.updateReputation(payload.reputation(), payload.reputationLevel());
        }
        pushVillagerChatMessage(minecraft, payload.entityId(), payload.text());
    }

    public static void acceptNotice(VillagerInteractionNoticePayload payload) {
        pushVillagerChatMessage(Minecraft.getInstance(), payload.entityId(), payload.text());
    }

    public static void acceptConversationEnded(VillagerConversationEndedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.closeFromServer();
        }
        pushVillagerChatMessage(minecraft, payload.entityId(), payload.goodbyeText());
        ClientVillagerConversationState.forgetSpeakerLabel(payload.entityId());
    }

    private static void pushVillagerChatMessage(Minecraft minecraft, int entityId, String text) {
        if (minecraft.player == null || text == null || text.isBlank()) {
            return;
        }
        minecraft.player.displayClientMessage(formatVillagerChatMessage(minecraft, entityId, text), false);
    }

    private static Component formatVillagerChatMessage(Minecraft minecraft, int entityId, String text) {
        return Component.literal("<" + resolveVillagerSpeakerName(minecraft, entityId) + "> " + text);
    }

    private static String resolveVillagerSpeakerName(Minecraft minecraft, int entityId) {
        String cachedSpeakerLabel = ClientVillagerConversationState.resolveSpeakerLabel(entityId);
        if (cachedSpeakerLabel != null && !cachedSpeakerLabel.isBlank()) {
            return cachedSpeakerLabel;
        }
        if (minecraft.level == null) {
            return "Villager";
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof Villager villager)) {
            return "Villager";
        }
        String profession = professionName(villager.getVillagerData().getProfession());
        if (!villager.hasCustomName()) {
            return profession.equals("Villager") ? "Villager" : profession + " Villager";
        }
        String customName = villager.getCustomName() == null ? "" : villager.getCustomName().getString().trim();
        if (customName.isBlank()) {
            return profession.equals("Villager") ? "Villager" : profession + " Villager";
        }
        return formatSpeakerLabel(customName, profession);
    }

    private static String resolveVillagerName(String villagerNameKey, String villagerNameFallback) {
        if (villagerNameKey != null && !villagerNameKey.isBlank() && I18n.exists(villagerNameKey)) {
            return I18n.get(villagerNameKey);
        }
        return villagerNameFallback == null || villagerNameFallback.isBlank() ? "Villager" : villagerNameFallback;
    }

    private static String formatSpeakerLabel(String villagerName, String profession) {
        if (profession == null || profession.isBlank() || profession.equals("Villager")) {
            return villagerName;
        }
        return profession + " " + villagerName;
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
