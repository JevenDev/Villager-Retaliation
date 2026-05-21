package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerNameClientCache;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerNameSyncPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerInteractionClientHandler {
    private static final long VILLAGER_CHAT_CONTINUATION_WINDOW_MILLIS = 15_000L;
    private static final int VILLAGER_CHAT_WRAP_PADDING_PIXELS = 24;
    private static int lastChatSpeakerEntityId = Integer.MIN_VALUE;
    private static String lastChatSpeakerLabel = "";
    private static long lastChatMessageMillis;

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
        resetVillagerChatGroup();
        minecraft.setScreen(new VillagerInteractionScreen(
                payload.entityId(),
                villagerName,
                payload.professionName(),
                payload.baby(),
                payload.reputation(),
                payload.reputationLevel(),
                payload.mood(),
                payload.followingPlayer(),
                payload.knownLikedGiftNames(),
                payload.knownDislikedGiftNames()
        ));
    }

    public static void acceptDialogue(VillagerDialogueResponsePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.updateReputation(
                    payload.reputation(),
                    payload.reputationLevel(),
                    payload.mood(),
                    payload.knownLikedGiftNames(),
                    payload.knownDislikedGiftNames()
            );
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
        resetVillagerChatGroup(payload.entityId());
    }

    private static void pushVillagerChatMessage(Minecraft minecraft, int entityId, String text, String speakerLabel) {
        if (minecraft.player == null || text == null || text.isBlank()) {
            return;
        }
        String resolvedSpeaker = speakerLabel == null || speakerLabel.isBlank()
                ? resolveVillagerSpeakerName(minecraft, entityId)
                : speakerLabel;
        int accentColor = villagerChatAccentColor(minecraft, entityId);
        if (shouldStartVillagerChatGroup(entityId, resolvedSpeaker)) {
            addVillagerChatMessage(minecraft, formatVillagerChatHeader(resolvedSpeaker, accentColor), accentColor);
        }
        addVillagerChatLines(minecraft, text, accentColor);
        rememberVillagerChatGroup(entityId, resolvedSpeaker);
    }

    private static void addVillagerChatMessage(Minecraft minecraft, Component message, int accentColor) {
        minecraft.gui.getChat().addMessage(message, null, villagerChatTag(accentColor));
    }

    private static void addVillagerChatLines(Minecraft minecraft, String text, int accentColor) {
        for (String line : wrapVillagerChatLine(minecraft, text)) {
            addVillagerChatMessage(minecraft, formatVillagerChatLine(line), accentColor);
        }
    }

    private static boolean shouldStartVillagerChatGroup(int entityId, String speakerLabel) {
        long now = Util.getMillis();
        return entityId != lastChatSpeakerEntityId
                || !speakerLabel.equals(lastChatSpeakerLabel)
                || now - lastChatMessageMillis > VILLAGER_CHAT_CONTINUATION_WINDOW_MILLIS;
    }

    private static void rememberVillagerChatGroup(int entityId, String speakerLabel) {
        lastChatSpeakerEntityId = entityId;
        lastChatSpeakerLabel = speakerLabel;
        lastChatMessageMillis = Util.getMillis();
    }

    private static void resetVillagerChatGroup(int entityId) {
        if (entityId == lastChatSpeakerEntityId) {
            resetVillagerChatGroup();
        }
    }

    private static void resetVillagerChatGroup() {
        lastChatSpeakerEntityId = Integer.MIN_VALUE;
        lastChatSpeakerLabel = "";
        lastChatMessageMillis = 0L;
    }

    private static GuiMessageTag villagerChatTag(int accentColor) {
        return new GuiMessageTag(
                accentColor,
                null,
                Component.literal("Villager dialogue").withStyle(ChatFormatting.GRAY),
                "Villager"
        );
    }

    private static Component formatVillagerChatHeader(String speakerLabel, int accentColor) {
        return Component.literal(speakerLabel).withStyle(style -> style.withColor(accentColor));
    }

    private static Component formatVillagerChatLine(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static List<String> wrapVillagerChatLine(Minecraft minecraft, String text) {
        int maxWidth = Math.max(40, minecraft.gui.getChat().getWidth() - VILLAGER_CHAT_WRAP_PADDING_PIXELS);
        List<String> lines = new ArrayList<>();
        String remaining = text.strip();
        while (!remaining.isEmpty()) {
            String candidate = minecraft.font.plainSubstrByWidth(remaining, maxWidth);
            int splitIndex = candidate.length();
            if (splitIndex < remaining.length()) {
                int lastSpace = candidate.lastIndexOf(' ');
                if (lastSpace > 0) {
                    splitIndex = lastSpace;
                }
            }
            if (splitIndex <= 0) {
                splitIndex = Math.max(1, candidate.length());
            }

            String line = remaining.substring(0, splitIndex).stripTrailing();
            if (!line.isEmpty()) {
                lines.add(line);
            }
            remaining = remaining.substring(splitIndex).stripLeading();
        }
        return lines;
    }

    private static int villagerChatAccentColor(Minecraft minecraft, int entityId) {
        if (minecraft.level == null) {
            return 0x808080;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof Villager villager) || villager.isBaby()) {
            return 0x808080;
        }
        return professionAccentColor(villager.getVillagerData().getProfession());
    }

    private static int professionAccentColor(VillagerProfession profession) {
        if (profession == VillagerProfession.ARMORER) {
            return 0x8FA7B3;
        }
        if (profession == VillagerProfession.BUTCHER) {
            return 0xD64F4F;
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return 0x4FB6B8;
        }
        if (profession == VillagerProfession.CLERIC) {
            return 0xB967FF;
        }
        if (profession == VillagerProfession.FARMER) {
            return 0x7CFC00;
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return 0x3BA7FF;
        }
        if (profession == VillagerProfession.FLETCHER) {
            return 0x83B547;
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return 0xA86A3D;
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return 0xD9558F;
        }
        if (profession == VillagerProfession.MASON) {
            return 0x9A8F86;
        }
        if (profession == VillagerProfession.NITWIT) {
            return 0x6AD36A;
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return 0xF2F2F2;
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            return 0x6FC3D0;
        }
        if (profession == VillagerProfession.WEAPONSMITH) {
            return 0xFF8A2A;
        }
        return 0xBDBDBD;
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
