package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.normal.GiftAdviceKind;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
            String giftName = giftAdviceName(
                    level,
                    context.locale(),
                    profession,
                    discovered,
                    context.random());
            if (index == 0 && discovered.rating() > 0) {
                VillagerInteractionTracker.rememberGiftAdvice(
                        level,
                        context.villager(),
                        player,
                        discovered.id().toString(),
                        giftName,
                        knowledgeKey);
            }
            discoveries.add(new GiftKnowledgeDiscovery(
                    giftAdviceKind(discovered.rating() > 0, discovered.professionSpecific()),
                    giftName,
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
        List<ItemStack> gifts = new ArrayList<>();
        for (GiftPreferenceDefinition candidate : candidates) {
            representativeGift(level, profession, candidate, random).ifPresent(gifts::add);
        }
        if (gifts.isEmpty()) {
            return Optional.empty();
        }
        ItemStack selected = gifts.get(random.nextInt(gifts.size()));
        return Optional.of(VillagerItemText.dialogueName(level.getServer(), locale, selected));
    }

    static String giftAdviceName(
            ServerLevel level,
            String locale,
            VillagerProfession profession,
            GiftPreferenceDefinition definition,
            RandomSource random) {
        String categoryName = definition.name().component(definition.id()).getString();
        return representativeGift(level, profession, definition, random)
                .map(stack -> VillagerItemText.dialogueName(level.getServer(), locale, stack))
                .orElse(categoryName);
    }

    static Optional<ItemStack> representativeGift(
            ServerLevel level,
            VillagerProfession profession,
            GiftPreferenceDefinition definition,
            RandomSource random) {
        List<Item> candidates = definition.matchers().stream()
                .flatMap(matcher -> matchingItems(matcher).stream())
                .filter(item -> definition.bestMatcher(new ItemStack(item)).isPresent())
                .filter(item -> VillagerGiftResources.preference(level, profession, new ItemStack(item))
                        .map(resolved -> resolved.categoryId().equals(definition.id()))
                        .orElse(false))
                .distinct()
                .sorted(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(candidates.get(random.nextInt(candidates.size()))));
    }

    private static List<Item> matchingItems(GiftPreferenceDefinition.ItemMatcher matcher) {
        if (matcher.source() == GiftPreferenceDefinition.MatchSource.ITEM) {
            return BuiltInRegistries.ITEM.getOptional(matcher.value())
                    .filter(item -> item != Items.AIR)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        if (matcher.source() != GiftPreferenceDefinition.MatchSource.TAG) {
            return List.of();
        }
        return BuiltInRegistries.ITEM
                .getTag(TagKey.create(Registries.ITEM, matcher.value()))
                .stream()
                .flatMap(holders -> holders.stream())
                .map(holder -> holder.value())
                .filter(item -> item != Items.AIR)
                .toList();
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
                        .map(matcher -> new GiftPreferenceView.Matcher(
                                matcher.source(), matcher.value(), matcher.stackPredicate()))
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
