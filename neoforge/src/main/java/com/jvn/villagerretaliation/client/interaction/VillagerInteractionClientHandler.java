package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerNameClientCache;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerNameSyncPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerInteractionClientHandler {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";
    private static final long VILLAGER_CHAT_CONTINUATION_WINDOW_MILLIS = 15_000L;
    private static final int VILLAGER_CHAT_PRIMARY_TEXT_COLOR = 0xFFFFFF;
    private static final int VILLAGER_CHAT_SECONDARY_TEXT_COLOR = 0xD8D8D8;
    private static int lastChatSpeakerEntityId = Integer.MIN_VALUE;
    private static String lastChatSpeakerLabel = "";
    private static long lastChatMessageMillis;
    private static int currentChatGroupMessageIndex;

    private VillagerInteractionClientHandler() {
    }

    public static void open(OpenVillagerInteractionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(payload.entityId());
        String villagerName = resolveVillagerName(payload.villagerNameKey(), payload.villagerNameFallback());
        String professionName = resolveProfessionName(entity, payload.professionName(), payload.baby());
        String genderName = resolveGenderName(payload.genderName());
        VillagerNameClientCache.accept(new VillagerNameSyncPayload(
                payload.entityId(),
                entity == null ? new UUID(0L, 0L) : entity.getUUID(),
                payload.villagerNameKey(),
                villagerName
        ));
        ClientVillagerConversationState.rememberSpeakerLabel(
                payload.entityId(),
                formatSpeakerLabel(villagerName, professionName)
        );
        resetVillagerChatGroup();
        minecraft.setScreen(new VillagerInteractionScreen(
                payload.entityId(),
                villagerName,
                professionName,
                genderName,
                payload.baby(),
                payload.reputation(),
                payload.reputationLevel(),
                payload.mood(),
                payload.primaryMood(),
                payload.followingPlayer(),
                payload.forcedDialogue(),
                payload.forceCameraTowardsVillager(),
                payload.dialogueOptions(),
                payload.knownLikedGiftNames(),
                payload.knownDislikedGiftNames(),
                payload.familyTree(),
                payload.relationships()
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
                    payload.primaryMood(),
                    payload.forceCameraTowardsVillager(),
                    payload.dialogueOptions(),
                    payload.knownLikedGiftNames(),
                    payload.knownDislikedGiftNames()
            );
        } else if (minecraft.screen instanceof VillagerInteractionChatScreen screen
                && screen.matchesVillager(payload.entityId())) {
            screen.updateReputation(
                    payload.reputation(),
                    payload.reputationLevel(),
                    payload.mood(),
                    payload.primaryMood(),
                    payload.forceCameraTowardsVillager(),
                    payload.dialogueOptions(),
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
        } else if (minecraft.screen instanceof VillagerInteractionChatScreen screen
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
        boolean startsChatGroup = shouldStartVillagerChatGroup(entityId, resolvedSpeaker);
        if (startsChatGroup) {
            currentChatGroupMessageIndex = 0;
            addVillagerChatSpeakerSeparator(minecraft, accentColor);
        }
        if (shouldSeparateVillagerChatMessage()) {
            addVillagerChatSeparator(minecraft, accentColor);
        }
        addVillagerChatMessage(
                minecraft,
                formatVillagerChatMessage(text.strip(), currentChatGroupMessageIndex, startsChatGroup ? resolvedSpeaker : "", accentColor),
                accentColor
        );
        currentChatGroupMessageIndex++;
        rememberVillagerChatGroup(entityId, resolvedSpeaker);
    }

    private static void addVillagerChatMessage(Minecraft minecraft, Component message, int accentColor) {
        minecraft.gui.getChat().addMessage(message, null, villagerChatTag(accentColor));
    }

    private static void addVillagerChatSeparator(Minecraft minecraft, int accentColor) {
        if (VillagerRetaliationConfig.SEPARATE_VILLAGER_CHAT_MESSAGES.get()) {
            addVillagerChatMessage(minecraft, Component.empty(), accentColor);
        }
    }

    private static void addVillagerChatSpeakerSeparator(Minecraft minecraft, int accentColor) {
        if (VillagerRetaliationConfig.SEPARATE_VILLAGER_CHAT_SPEAKERS.get()) {
            addVillagerChatMessage(minecraft, Component.empty(), accentColor);
        }
    }

    private static boolean shouldSeparateVillagerChatMessage() {
        return VillagerRetaliationConfig.SEPARATE_VILLAGER_CHAT_MESSAGES.get() && currentChatGroupMessageIndex > 0;
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
        currentChatGroupMessageIndex = 0;
    }

    private static GuiMessageTag villagerChatTag(int accentColor) {
        return new GuiMessageTag(
                accentColor,
                null,
                Component.translatable(GUI_KEY_PREFIX + "chat.tooltip").withStyle(ChatFormatting.GRAY),
                I18n.get(GUI_KEY_PREFIX + "chat.tag")
        );
    }

    private static Component formatVillagerChatMessage(String text, int lineIndex, String speakerLabel, int accentColor) {
        int color = lineIndex % 2 == 0 ? VILLAGER_CHAT_PRIMARY_TEXT_COLOR : VILLAGER_CHAT_SECONDARY_TEXT_COLOR;
        MutableComponent message = Component.empty();
        if (speakerLabel != null && !speakerLabel.isBlank()) {
            message.append(Component.literal(I18n.get(GUI_KEY_PREFIX + "chat.speaker_prefix", speakerLabel))
                    .withStyle(style -> style.withColor(accentColor)));
        }
        return message.append(Component.literal(text).withStyle(style -> style.withColor(color)));
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
            return I18n.get(GUI_KEY_PREFIX + "speaker.villager");
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof Villager villager)) {
            return VillagerNameClientCache.displayName(entityId)
                    .map(Component::getString)
                    .filter(name -> !name.isBlank())
                    .orElseGet(() -> I18n.get(GUI_KEY_PREFIX + "speaker.villager"));
        }
        if (villager.isBaby()) {
            return I18n.get(GUI_KEY_PREFIX + "speaker.child");
        }
        String profession = localizedProfessionName(villager);
        Optional<String> cachedDisplayName = VillagerNameClientCache.displayName(entityId)
                .map(Component::getString)
                .map(String::trim)
                .filter(name -> !name.isBlank());
        if (cachedDisplayName.isPresent()) {
            return formatSpeakerLabel(cachedDisplayName.get(), profession);
        }
        if (!villager.hasCustomName()) {
            return isGenericProfession(profession)
                    ? I18n.get(GUI_KEY_PREFIX + "speaker.villager")
                    : I18n.get(GUI_KEY_PREFIX + "speaker.profession", profession);
        }
        String customName = villager.getCustomName() == null ? "" : villager.getCustomName().getString().trim();
        if (customName.isBlank()) {
            return isGenericProfession(profession)
                    ? I18n.get(GUI_KEY_PREFIX + "speaker.villager")
                    : I18n.get(GUI_KEY_PREFIX + "speaker.profession", profession);
        }
        return formatSpeakerLabel(customName, profession);
    }

    private static String resolveVillagerName(String villagerNameKey, String villagerNameFallback) {
        if (villagerNameKey != null && !villagerNameKey.isBlank() && I18n.exists(villagerNameKey)) {
            return I18n.get(villagerNameKey);
        }
        return villagerNameFallback == null || villagerNameFallback.isBlank()
                ? I18n.get(GUI_KEY_PREFIX + "speaker.villager")
                : villagerNameFallback;
    }

    private static String formatSpeakerLabel(String villagerName, String profession) {
        if (profession == null || profession.isBlank() || isGenericProfession(profession)) {
            return villagerName;
        }
        return I18n.get(GUI_KEY_PREFIX + "speaker.named", profession, villagerName);
    }

    private static String resolveProfessionName(Entity entity, String professionKey, boolean baby) {
        if (professionKey != null && !professionKey.isBlank() && I18n.exists(professionKey)) {
            return I18n.get(professionKey);
        }
        if (entity instanceof Villager villager) {
            return localizedProfessionName(villager);
        }
        return I18n.get(GUI_KEY_PREFIX + (baby ? "profession.child" : "profession.unemployed"));
    }

    private static String localizedProfessionName(Villager villager) {
        if (villager.isBaby()) {
            return I18n.get(GUI_KEY_PREFIX + "profession.child");
        }
        VillagerProfession profession = villager.getVillagerData().getProfession();
        String key = professionTranslationKey(profession);
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        return VillagerInteractionTextUtil.professionName(profession, I18n.get(GUI_KEY_PREFIX + "profession.unemployed"));
    }

    private static String professionTranslationKey(VillagerProfession profession) {
        return VillagerProfessionUtil.translationKey(profession, GUI_KEY_PREFIX + "profession.unemployed");
    }

    private static String resolveGenderName(String genderName) {
        if (genderName == null || genderName.isBlank()) {
            return I18n.get(GUI_KEY_PREFIX + "gender.unknown");
        }
        String key = GUI_KEY_PREFIX + "gender." + genderName.trim().toLowerCase(Locale.ROOT);
        return I18n.exists(key) ? I18n.get(key) : genderName;
    }

    private static boolean isGenericProfession(String profession) {
        return profession.equals(I18n.get(GUI_KEY_PREFIX + "speaker.villager"))
                || profession.equals(I18n.get(GUI_KEY_PREFIX + "profession.unemployed"));
    }
}
