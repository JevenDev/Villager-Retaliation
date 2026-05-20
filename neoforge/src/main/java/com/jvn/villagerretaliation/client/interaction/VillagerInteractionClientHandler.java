package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerNameClientCache;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerNameSyncPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerInteractionClientHandler {
    private VillagerInteractionClientHandler() {
    }

    public static void open(OpenVillagerInteractionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        String villagerName = resolveVillagerName(payload.villagerNameKey(), payload.villagerNameFallback());
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(payload.entityId());
        VillagerNameClientCache.accept(new VillagerNameSyncPayload(
                payload.entityId(),
                entity == null ? new UUID(0L, 0L) : entity.getUUID(),
                payload.villagerNameKey(),
                villagerName
        ));
        ClientVillagerConversationState.rememberSpeakerLabel(
                payload.entityId(),
                formatSpeakerLabel(villagerName, payload.professionName())
        );
        minecraft.setScreen(new VillagerInteractionScreen(
                payload.entityId(),
                villagerName,
                payload.professionName(),
                payload.baby(),
                payload.reputation(),
                payload.reputationLevel(),
                payload.mood()
        ));
    }

    public static void acceptDialogue(VillagerDialogueResponsePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.updateReputation(payload.reputation(), payload.reputationLevel(), payload.mood());
        }
    }

    public static void acceptNotice(VillagerInteractionNoticePayload payload) {
        pushVillagerChatMessage(Minecraft.getInstance(), payload.entityId(), payload.text(), payload.speakerLabel());
    }

    public static void acceptConversationEnded(VillagerConversationEndedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.closeFromServer();
        }
        ClientVillagerConversationState.forgetSpeakerLabel(payload.entityId());
    }

    private static void pushVillagerChatMessage(Minecraft minecraft, int entityId, String text, String speakerLabel) {
        if (minecraft.player == null || text == null || text.isBlank()) {
            return;
        }
        minecraft.player.displayClientMessage(formatVillagerChatMessage(minecraft, entityId, text, speakerLabel), false);
    }

    private static Component formatVillagerChatMessage(Minecraft minecraft, int entityId, String text, String speakerLabel) {
        String resolvedSpeaker = speakerLabel == null || speakerLabel.isBlank()
                ? resolveVillagerSpeakerName(minecraft, entityId)
                : speakerLabel;
        return Component.literal("<" + resolvedSpeaker + "> " + text);
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
        if (villager.isBaby()) {
            return "Child Villager";
        }
        String profession = VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "Villager");
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
}
