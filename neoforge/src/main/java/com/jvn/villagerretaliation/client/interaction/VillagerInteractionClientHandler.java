package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerNameClientCache;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.DialogueTextSegment;
import com.jvn.villagerretaliation.network.OpenVillagerInteractionPayload;
import com.jvn.villagerretaliation.network.VillagerNameSyncPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.List;
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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

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
        VillagerProfessionUiColors.ColorPair professionUiColors = resolveProfessionUiColors(entity, payload.baby());
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
                professionUiColors,
                genderName,
                payload.baby(),
                payload.reputation(),
                payload.reputationLevel(),
                payload.mood(),
                payload.primaryMood(),
                payload.followingPlayer(),
                payload.stayingHere(),
                payload.forcedDialogue(),
                payload.clipboardMenu(),
                payload.hiredByPlayer(),
                payload.hiredByOtherPlayer(),
                payload.hiredRemainingDays(),
                payload.walletEmeralds(),
                payload.maxWalletEmeralds(),
                payload.lifetimeWalletEarned(),
                payload.lifetimeWalletDeposited(),
                payload.walletCurrencyName(),
                payload.walletCurrencyPluralName(),
                payload.walletCurrencyLabel(),
                payload.forceCameraTowardsVillager(),
                payload.availableHiredRoles(),
                payload.activeHiredRole(),
                payload.activeBrewingOrder(),
                payload.activeBuilderTask(),
                payload.selectedLoggingFilters(),
                payload.loggingStripLogs(),
                payload.loggingHarvestLeaves(),
                payload.loggingBonemealSaplings(),
                payload.loggingPlantSaplings(),
                payload.loggingPickUpDecayDrops(),
                payload.selectedAnimalBreedingTargets(),
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
        pushVillagerChatMessage(Minecraft.getInstance(), payload.entityId(), payload.text(), payload.speakerLabel(), payload.textSegments());
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

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VillagerInteractionChatVisibility.restoreHiddenVillagerMessages(Minecraft.getInstance());
        ClientVillagerConversationState.clear();
        resetVillagerChatGroup();
    }

    private static VillagerInteractionSessionScreen activeInteractionScreen(Screen screen, int villagerEntityId) {
        if (screen instanceof VillagerInteractionSessionScreen interactionScreen
                && interactionScreen.matchesVillager(villagerEntityId)) {
            return interactionScreen;
        }
        return null;
    }

    private static void pushVillagerChatMessage(
            Minecraft minecraft,
            int entityId,
            String text,
            String speakerLabel,
            List<DialogueTextSegment> textSegments) {
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
        String displayedSpeaker = startsChatGroup ? resolvedSpeaker : "";
        List<DialogueTextSegment> displayedSegments = dialogueTextEffectsDisabled()
                ? List.of()
                : displayedChatSegments(text.strip(), displayedSpeaker, textSegments);
        addVillagerChatMessage(
                minecraft,
                formatVillagerChatMessage(text.strip(), currentChatGroupMessageIndex, displayedSpeaker, accentColor, textSegments),
                accentColor,
                displayedSegments
        );
        currentChatGroupMessageIndex++;
        rememberVillagerChatGroup(entityId, resolvedSpeaker);
    }

    private static void addVillagerChatMessage(Minecraft minecraft, Component message, int accentColor) {
        addVillagerChatMessage(minecraft, message, accentColor, List.of());
    }

    private static void addVillagerChatMessage(
            Minecraft minecraft,
            Component message,
            int accentColor,
            List<DialogueTextSegment> textSegments) {
        minecraft.gui.getChat().addMessage(message, null, villagerChatTag(accentColor));
        VillagerAnimatedChatText.remember(textSegments);
    }

    private static void addVillagerChatSeparator(Minecraft minecraft, int accentColor) {
        if (VillagerRetaliationConfig.SEPARATE_VILLAGER_CHAT_MESSAGES.get()) {
            addVillagerChatSpacer(minecraft);
        }
    }

    private static void addVillagerChatSpeakerSeparator(Minecraft minecraft, int accentColor) {
        if (VillagerRetaliationConfig.SEPARATE_VILLAGER_CHAT_SPEAKERS.get()) {
            addVillagerChatSpacer(minecraft);
        }
    }

    private static void addVillagerChatSpacer(Minecraft minecraft) {
        // Insert a blank line without any chat tag so no provider/system band is rendered.
        minecraft.gui.getChat().addMessage(Component.literal(" "), null, null);
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

    private static Component formatVillagerChatMessage(
            String text,
            int lineIndex,
            String speakerLabel,
            int accentColor,
            List<DialogueTextSegment> textSegments) {
        int color = lineIndex % 2 == 0 ? VILLAGER_CHAT_PRIMARY_TEXT_COLOR : VILLAGER_CHAT_SECONDARY_TEXT_COLOR;
        MutableComponent message = Component.empty();
        if (speakerLabel != null && !speakerLabel.isBlank()) {
            message.append(Component.literal(gui("chat.speaker_prefix", speakerLabel))
                    .withStyle(style -> style.withColor(accentColor)));
        }
        if (dialogueTextEffectsDisabled()) {
            message.append(Component.literal(text).withStyle(style -> style.withColor(color)));
            return message;
        }
        List<DialogueTextSegment> safeSegments = textSegments == null || textSegments.isEmpty()
                ? DialogueTextSegment.plain(text, DialogueTextEffects.NONE)
                : textSegments;
        for (DialogueTextSegment segment : safeSegments) {
            appendStyledSegment(message, segment, color);
        }
        return message;
    }

    private static void appendStyledSegment(MutableComponent message, DialogueTextSegment segment, int fallbackColor) {
        DialogueTextEffects effects = segment.effects();
        if (effects.rainbow()) {
            int index = 0;
            int length = Math.max(1, segment.text().codePointCount(0, segment.text().length()));
            for (int offset = 0; offset < segment.text().length(); ) {
                int codePoint = segment.text().codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                int color = rainbowColor(index / (float) length);
                message.append(styledText(glyph, effects, color));
                offset += Character.charCount(codePoint);
                index++;
            }
            return;
        }
        if (effects.hasGradient()) {
            int length = Math.max(1, segment.text().codePointCount(0, segment.text().length()) - 1);
            int index = 0;
            for (int offset = 0; offset < segment.text().length(); ) {
                int codePoint = segment.text().codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                int color = lerpColor(effects.gradientStartColor(), effects.gradientEndColor(), length == 0 ? 0.0F : index / (float) length);
                message.append(styledText(glyph, effects, color));
                offset += Character.charCount(codePoint);
                index++;
            }
            return;
        }
        message.append(styledText(segment.text(), effects, effects.color() == null ? fallbackColor : effects.color()));
    }

    private static Component styledText(String text, DialogueTextEffects effects, int color) {
        return Component.literal(text).withStyle(style -> style
                .withColor(VillagerChatEffectRenderer.usesAnimatedRenderer(effects)
                        ? VillagerChatEffectRenderer.STATIC_EFFECT_TEXT_COLOR
                        : color)
                .withItalic(effects.italic())
                .withBold(effects.bold())
                .withUnderlined(effects.underlined())
                .withStrikethrough(effects.strikethrough())
                .withObfuscated(effects.obfuscated()));
    }

    private static int lerpColor(int start, int end, float progress) {
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        int red = Math.round(((start >> 16) & 0xFF) + (((end >> 16) & 0xFF) - ((start >> 16) & 0xFF)) * clamped);
        int green = Math.round(((start >> 8) & 0xFF) + (((end >> 8) & 0xFF) - ((start >> 8) & 0xFF)) * clamped);
        int blue = Math.round((start & 0xFF) + ((end & 0xFF) - (start & 0xFF)) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private static int rainbowColor(float progress) {
        float hue = progress - (float) Math.floor(progress);
        float scaled = hue * 6.0F;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float saturation = 0.85F;
        float value = 1.0F;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));
        return switch (Math.floorMod(sector, 6)) {
            case 0 -> rgb(value, t, p);
            case 1 -> rgb(q, value, p);
            case 2 -> rgb(p, value, t);
            case 3 -> rgb(p, q, value);
            case 4 -> rgb(t, p, value);
            default -> rgb(value, p, q);
        };
    }

    private static int rgb(float red, float green, float blue) {
        return (Math.round(red * 255.0F) << 16)
                | (Math.round(green * 255.0F) << 8)
                | Math.round(blue * 255.0F);
    }

    private static List<DialogueTextSegment> displayedChatSegments(
            String text,
            String speakerLabel,
            List<DialogueTextSegment> textSegments) {
        List<DialogueTextSegment> displayedSegments = new java.util.ArrayList<>();
        if (speakerLabel != null && !speakerLabel.isBlank()) {
            displayedSegments.add(new DialogueTextSegment(gui("chat.speaker_prefix", speakerLabel), DialogueTextEffects.NONE));
        }
        displayedSegments.addAll(textSegments == null || textSegments.isEmpty()
                ? DialogueTextSegment.plain(text, DialogueTextEffects.NONE)
                : textSegments);
        return List.copyOf(displayedSegments);
    }

    private static boolean dialogueTextEffectsDisabled() {
        return VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get();
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

    private static VillagerProfessionUiColors.ColorPair resolveProfessionUiColors(Entity entity, boolean baby) {
        if (!(entity instanceof Villager villager) || baby || villager.isBaby()) {
            return VillagerProfessionUiColors.DEFAULT_COLORS;
        }
        return VillagerProfessionUiColors.colorsFor(villager.getVillagerData().getProfession());
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
