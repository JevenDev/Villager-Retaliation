package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.normal.GiftAdviceKind;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

public final class VillagerGiftKnowledgeService {
    private static final String GLOBAL_PROFESSION_KEY = "*";

    private VillagerGiftKnowledgeService() {
    }

    public static GiftKnowledgeSnapshot knownGifts(ServerLevel level, ServerPlayer player, VillagerProfession profession) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        String professionKey = professionKey(profession);
        migrateLegacyKnowledge(level, data, player, profession, professionKey);
        List<GiftPreferenceView> preferences = VillagerGiftResources.definitions(level, profession).stream()
                .map(definition -> view(
                        definition,
                        knowsCategory(data, player, professionKey, definition)))
                .toList();
        return new GiftKnowledgeSnapshot(preferences);
    }

    public static List<GiftKnowledgeDiscovery> discoverFromGiftQuestion(DialogueContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        VillagerProfession profession = context.profession();
        String professionKey = professionKey(profession);
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        migrateLegacyKnowledge(level, data, player, profession, professionKey);
        List<GiftPreferenceDefinition> unknown = new java.util.ArrayList<>(
                unknownDefinitions(level, data, player, professionKey, profession));
        List<GiftKnowledgeDiscovery> discoveries = new java.util.ArrayList<>();
        int revealCount = Math.min(GiftDiscoveryPolicy.questionRevealCount(), unknown.size());
        for (int index = 0; index < revealCount; index++) {
            GiftPreferenceDefinition discovered = unknown.remove(context.random().nextInt(unknown.size()));
            String knowledgeKey = discovered.professionSpecific() ? professionKey : GLOBAL_PROFESSION_KEY;
            discoverCategory(data, player, knowledgeKey, discovered.id().toString());
            String categoryName = discovered.name().component(discovered.id()).getString();
            String revealedName = categoryName + " " + ratingLabel(discovered.rating());
            if (index == 0 && discovered.rating() > 0) {
                VillagerInteractionTracker.rememberGiftAdvice(
                        level,
                        context.villager(),
                        player,
                        discovered.id().toString(),
                        categoryName,
                        knowledgeKey);
            }
            discoveries.add(new GiftKnowledgeDiscovery(
                    giftAdviceKind(discovered.rating() > 0, discovered.professionSpecific()),
                    revealedName,
                    giftSubject(profession),
                    discovered.id().toString(),
                    knowledgeKey));
        }
        return List.copyOf(discoveries);
    }

    public static boolean discoverFromGift(
            ServerLevel level,
            ServerPlayer player,
            VillagerProfession profession,
            ItemStack giftedStack,
            ResolvedGiftPreference giftPreference) {
        if (giftedStack.isEmpty() || !giftPreference.matched()) {
            return false;
        }

        String knowledgeKey = giftPreference.professionSpecific() ? professionKey(profession) : GLOBAL_PROFESSION_KEY;
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        return discoverCategory(data, player, knowledgeKey, giftPreference.categoryId().toString());
    }

    public static Optional<String> randomLikedGiftName(
            ServerLevel level,
            VillagerProfession profession,
            String excludedCategoryId,
            String locale,
            RandomSource random) {
        List<GiftPreferenceDefinition> candidates = VillagerGiftResources.definitions(level, profession).stream()
                .filter(definition -> definition.rating() > 0)
                .filter(definition -> !definition.id().toString().equals(excludedCategoryId))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        GiftPreferenceDefinition selected = candidates.get(random.nextInt(candidates.size()));
        return Optional.of(selected.name().component(selected.id()).getString());
    }

    private static void migrateLegacyKnowledge(
            ServerLevel level,
            VillagerInteractionSavedData data,
            ServerPlayer player,
            VillagerProfession profession,
            String professionKey) {
        for (String sourceProfession : List.of(GLOBAL_PROFESSION_KEY, professionKey)) {
            for (boolean liked : List.of(false, true)) {
                for (String itemId : data.legacyGiftIds(player.getUUID(), sourceProfession, liked)) {
                    ResourceLocation id = ResourceLocation.tryParse(itemId);
                    if (id == null) {
                        continue;
                    }
                    ItemStack stack = BuiltInRegistries.ITEM.getOptional(id)
                            .map(ItemStack::new)
                            .orElse(ItemStack.EMPTY);
                    ResolvedGiftPreference resolved = VillagerGiftPreferences.evaluate(level, profession, stack);
                    if (!resolved.matched()) {
                        continue;
                    }
                    String targetProfession = resolved.professionSpecific()
                            ? professionKey
                            : GLOBAL_PROFESSION_KEY;
                    discoverCategory(data, player, targetProfession, resolved.categoryId().toString());
                    if (!GLOBAL_PROFESSION_KEY.equals(sourceProfession)
                            && data.removeLegacyGift(player.getUUID(), sourceProfession, itemId, liked)) {
                        data.setDirty();
                    }
                }
            }
        }
    }

    private static boolean discoverCategory(
            VillagerInteractionSavedData data,
            ServerPlayer player,
            String professionKey,
            String categoryId) {
        boolean changed = data.rememberGiftCategory(player.getUUID(), professionKey, categoryId);
        if (changed) {
            data.setDirty();
        }
        return changed;
    }

    public static String ratingLabel(int rating) {
        return rating > 0 ? "+".repeat(rating) : rating < 0 ? "-".repeat(-rating) : "0";
    }

    private static List<GiftPreferenceDefinition> unknownDefinitions(
            ServerLevel level,
            VillagerInteractionSavedData data,
            ServerPlayer player,
            String professionKey,
            VillagerProfession profession) {
        return VillagerGiftResources.definitions(level, profession).stream()
                .filter(definition -> definition.rating() != 0)
                .filter(definition -> !knowsCategory(data, player, professionKey, definition))
                .toList();
    }

    private static boolean knowsCategory(
            VillagerInteractionSavedData data,
            ServerPlayer player,
            String professionKey,
            GiftPreferenceDefinition definition) {
        String knowledgeKey = definition.professionSpecific() ? professionKey : GLOBAL_PROFESSION_KEY;
        return data.knowsGiftCategory(player.getUUID(), knowledgeKey, definition.id().toString());
    }

    private static GiftPreferenceView view(GiftPreferenceDefinition definition, boolean known) {
        return new GiftPreferenceView(
                definition.id(),
                known ? definition.rating() : 0,
                known,
                definition.priority(),
                definition.professionSpecific(),
                definition.name(),
                definition.matchers().stream()
                        .map(matcher -> new GiftPreferenceView.Matcher(matcher.source(), matcher.value()))
                        .toList());
    }

    private static String giftSubject(VillagerProfession profession) {
        String professionName = VillagerInteractionTextUtil.professionName(profession, "villager").toLowerCase(Locale.ROOT);
        return VillagerInteractionTextUtil.withIndefiniteArticle(professionName);
    }

    private static GiftAdviceKind giftAdviceKind(boolean liked, boolean professionSpecific) {
        if (professionSpecific) {
            return liked ? GiftAdviceKind.PROFESSION_LIKED : GiftAdviceKind.PROFESSION_DISLIKED;
        }
        return liked ? GiftAdviceKind.GLOBAL_LIKED : GiftAdviceKind.GLOBAL_DISLIKED;
    }

    public static String professionKey(VillagerProfession profession) {
        return VillagerProfessionUtil.serializedKey(profession);
    }

    public record GiftKnowledgeSnapshot(List<GiftPreferenceView> preferences) {
        public GiftKnowledgeSnapshot {
            preferences = preferences == null ? List.of() : List.copyOf(preferences);
        }
    }

    public record GiftKnowledgeDiscovery(
            GiftAdviceKind adviceKind,
            String itemName,
            String subject,
            String itemId,
            String targetProfessionKey) {
    }

}
