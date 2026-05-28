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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
    private static final int DEFAULT_PROFESSION_ACCENT_COLOR = 0xBDBDBD;
    private static final Map<VillagerProfession, Integer> PROFESSION_ACCENT_COLORS = Map.ofEntries(
            Map.entry(VillagerProfession.ARMORER, 0x8FA7B3),
            Map.entry(VillagerProfession.BUTCHER, 0xD64F4F),
            Map.entry(VillagerProfession.CARTOGRAPHER, 0x4FB6B8),
            Map.entry(VillagerProfession.CLERIC, 0xB967FF),
            Map.entry(VillagerProfession.FARMER, 0x7CFC00),
            Map.entry(VillagerProfession.FISHERMAN, 0x3BA7FF),
            Map.entry(VillagerProfession.FLETCHER, 0x83B547),
            Map.entry(VillagerProfession.LEATHERWORKER, 0xA86A3D),
            Map.entry(VillagerProfession.LIBRARIAN, 0xD9558F),
            Map.entry(VillagerProfession.MASON, 0x9A8F86),
            Map.entry(VillagerProfession.NITWIT, 0x6AD36A),
            Map.entry(VillagerProfession.SHEPHERD, 0xF2F2F2),
            Map.entry(VillagerProfession.TOOLSMITH, 0x6FC3D0),
            Map.entry(VillagerProfession.WEAPONSMITH, 0xFF8A2A)
    );
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
        boolean replacingInteractionScreen = minecraft.screen instanceof VillagerInteractionSessionScreen;
        if (!replacingInteractionScreen) {
            VillagerInteractionChatVisibility.hidePreviousVillagerMessages(minecraft);
        }
        if (replacingInteractionScreen) {
            VillagerInteractionSessionScreen interactionScreen = (VillagerInteractionSessionScreen) minecraft.screen;
            interactionScreen.replaceFromServer();
        }
        VillagerInteractionScreen screen = new VillagerInteractionScreen(
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
        );
        minecraft.setScreen(screen);
        boolean forceCamera = payload.forcedDialogue() || payload.forceCameraTowardsVillager();
        if (replacingInteractionScreen && ClientVillagerConversationState.active()) {
            ClientVillagerConversationState.retarget(payload.entityId(), forceCamera);
        } else {
            ClientVillagerConversationState.start(payload.entityId(), forceCamera);
        }
    }

    public static void acceptDialogue(VillagerDialogueResponsePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        VillagerInteractionSessionScreen screen = activeInteractionScreen(minecraft.screen, payload.entityId());
        if (screen != null) {
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
        VillagerInteractionSessionScreen screen = activeInteractionScreen(minecraft.screen, payload.entityId());
        if (screen != null) {
            screen.closeFromServer();
        }
        ClientVillagerConversationState.forgetSpeakerLabel(payload.entityId());
        resetVillagerChatGroup(payload.entityId());
    }

    private static VillagerInteractionSessionScreen activeInteractionScreen(Screen screen, int villagerEntityId) {
        if (screen instanceof VillagerInteractionSessionScreen interactionScreen
                && interactionScreen.matchesVillager(villagerEntityId)) {
            return interactionScreen;
        }
        return null;
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
                Component.translatable(guiKey("chat.tooltip")).withStyle(ChatFormatting.GRAY),
                gui("chat.tag")
        );
    }

    private static Component formatVillagerChatMessage(String text, int lineIndex, String speakerLabel, int accentColor) {
        int color = lineIndex % 2 == 0 ? VILLAGER_CHAT_PRIMARY_TEXT_COLOR : VILLAGER_CHAT_SECONDARY_TEXT_COLOR;
        MutableComponent message = Component.empty();
        if (speakerLabel != null && !speakerLabel.isBlank()) {
            message.append(Component.literal(gui("chat.speaker_prefix", speakerLabel))
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
        return PROFESSION_ACCENT_COLORS.getOrDefault(profession, DEFAULT_PROFESSION_ACCENT_COLOR);
    }

    private static String resolveVillagerSpeakerName(Minecraft minecraft, int entityId) {
        String cachedSpeakerLabel = ClientVillagerConversationState.resolveSpeakerLabel(entityId);
        if (cachedSpeakerLabel != null && !cachedSpeakerLabel.isBlank()) {
            return cachedSpeakerLabel;
        }
        if (minecraft.level == null) {
            return gui("speaker.villager");
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof Villager villager)) {
            return VillagerNameClientCache.displayName(entityId)
                    .map(Component::getString)
                    .filter(name -> !name.isBlank())
                    .orElseGet(() -> gui("speaker.villager"));
        }
        if (villager.isBaby()) {
            return gui("speaker.child");
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
                    ? gui("speaker.villager")
                    : gui("speaker.profession", profession);
        }
        String customName = villager.getCustomName() == null ? "" : villager.getCustomName().getString().trim();
        if (customName.isBlank()) {
            return isGenericProfession(profession)
                    ? gui("speaker.villager")
                    : gui("speaker.profession", profession);
        }
        return formatSpeakerLabel(customName, profession);
    }

    private static String resolveVillagerName(String villagerNameKey, String villagerNameFallback) {
        if (hasTranslation(villagerNameKey)) {
            return I18n.get(villagerNameKey);
        }
        return villagerNameFallback == null || villagerNameFallback.isBlank()
                ? gui("speaker.villager")
                : villagerNameFallback;
    }

    private static String formatSpeakerLabel(String villagerName, String profession) {
        if (profession == null || profession.isBlank() || isGenericProfession(profession)) {
            return villagerName;
        }
        return gui("speaker.named", profession, villagerName);
    }

    private static String resolveProfessionName(Entity entity, String professionKey, boolean baby) {
        if (hasTranslation(professionKey)) {
            return I18n.get(professionKey);
        }
        if (entity instanceof Villager villager) {
            return localizedProfessionName(villager);
        }
        return baby ? gui("profession.child") : gui("profession.unemployed");
    }

    private static String localizedProfessionName(Villager villager) {
        if (villager.isBaby()) {
            return gui("profession.child");
        }
        VillagerProfession profession = villager.getVillagerData().getProfession();
        String key = professionTranslationKey(profession);
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        return VillagerInteractionTextUtil.professionName(profession, gui("profession.unemployed"));
    }

    private static String professionTranslationKey(VillagerProfession profession) {
        return VillagerProfessionUtil.translationKey(profession, guiKey("profession.unemployed"));
    }

    private static String resolveGenderName(String genderName) {
        if (genderName == null || genderName.isBlank()) {
            return gui("gender.unknown");
        }
        String key = guiKey("gender." + genderName.trim().toLowerCase(Locale.ROOT));
        return I18n.exists(key) ? I18n.get(key) : genderName;
    }

    private static boolean isGenericProfession(String profession) {
        return profession.equals(gui("speaker.villager"))
                || profession.equals(gui("profession.unemployed"));
    }

    private static String gui(String key, Object... args) {
        return I18n.get(guiKey(key), args);
    }

    private static String guiKey(String key) {
        return GUI_KEY_PREFIX + key;
    }

    private static boolean hasTranslation(String translationKey) {
        return translationKey != null && !translationKey.isBlank() && I18n.exists(translationKey);
    }
}
