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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class VillagerGiftKnowledgeService {
    private static final String GLOBAL_PROFESSION_KEY = "*";

    private VillagerGiftKnowledgeService() {
    }

    public static GiftKnowledgeSnapshot knownGifts(ServerLevel level, ServerPlayer player, VillagerProfession profession) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        String professionKey = professionKey(profession);
        List<GiftPreferenceView> preferences = VillagerGiftResources.definitions(level, profession).stream()
                .map(definition -> view(
                        definition,
                        knowsCategory(data, player, professionKey, definition)))
                .toList();
        return new GiftKnowledgeSnapshot(preferences);
    }

    public static Optional<GiftKnowledgeDiscovery> discoverFromGiftQuestion(DialogueContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        VillagerProfession profession = context.profession();
        String professionKey = professionKey(profession);
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        List<GiftPreferenceDefinition> unknown = unknownDefinitions(level, data, player, professionKey, profession);
        if (unknown.isEmpty()) {
            return Optional.empty();
        }

        GiftAdviceSelection selection = selectGiftAdviceCandidate(context, unknown);
        GiftPreferenceDefinition discovered = selection.definition();
        boolean liked = selection.claimedLiked();
        String knowledgeKey = discovered.professionSpecific() ? professionKey : GLOBAL_PROFESSION_KEY;
        String categoryName = discovered.name().component(discovered.id()).getString();
        if (selection.truthful() && data.rememberGiftCategory(
                player.getUUID(),
                knowledgeKey,
                discovered.id().toString())) {
            data.setDirty();
        }
        if (liked) {
            VillagerInteractionTracker.rememberGiftAdvice(
                    level,
                    context.villager(),
                    player,
                    discovered.id().toString(),
                    categoryName,
                    knowledgeKey);
        }

        return Optional.of(new GiftKnowledgeDiscovery(
                giftAdviceKind(liked, discovered.professionSpecific()),
                categoryName,
                giftSubject(profession),
                discovered.id().toString(),
                knowledgeKey));
    }

    private static GiftAdviceSelection selectGiftAdviceCandidate(
            DialogueContext context,
            List<GiftPreferenceDefinition> unknown) {
        List<GiftPreferenceDefinition> wrongAdviceCandidates = unknown.stream()
                .filter(definition -> definition.rating() < 0)
                .toList();
        if (!wrongAdviceCandidates.isEmpty() && context.random().nextInt(100) < wrongAdviceChancePercent(context)) {
            return new GiftAdviceSelection(
                    wrongAdviceCandidates.get(context.random().nextInt(wrongAdviceCandidates.size())),
                    true,
                    false);
        }

        GiftPreferenceDefinition definition = unknown.get(context.random().nextInt(unknown.size()));
        return new GiftAdviceSelection(definition, definition.rating() > 0, true);
    }

    private static int wrongAdviceChancePercent(DialogueContext context) {
        return switch (context.reputationLevel()) {
            case FEARED -> 55;
            case DESPISED -> 45;
            case HOSTILE -> 35;
            case SUSPICIOUS -> 20;
            case NEUTRAL -> 8;
            case TRUSTED -> 2;
            case RESPECTED -> 1;
            case REVERED, ROYALTY -> 0;
        };
    }

    public static boolean rememberGiftResult(
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
        boolean changed = data.rememberGiftCategory(
                player.getUUID(),
                knowledgeKey,
                giftPreference.categoryId().toString());
        if (changed) {
            data.setDirty();
        }
        return changed;
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

    static String displayItemName(Item item) {
        return new ItemStack(item).getHoverName().getString();
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

    private record GiftAdviceSelection(
            GiftPreferenceDefinition definition,
            boolean claimedLiked,
            boolean truthful) {
    }
}
