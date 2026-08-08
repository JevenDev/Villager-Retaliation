package com.jvn.villagerretaliation.interaction;

import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class GiftPreferenceResolverGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final ResourceLocation BOATS = ResourceLocation.withDefaultNamespace("boats");

    private GiftPreferenceResolverGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void resolutionUsesPriorityExactnessScopeAndStableIds(GameTestHelper helper) {
        GiftPreferenceDefinition tag = definition(
                "test:tag",
                Set.of(VillagerProfession.FISHERMAN),
                1,
                10,
                GiftPreferenceDefinition.ItemMatcher.tag(BOATS));
        GiftPreferenceDefinition exact = definition(
                "test:exact",
                Set.of(VillagerProfession.FISHERMAN),
                -3,
                10,
                GiftPreferenceDefinition.ItemMatcher.item(ResourceLocation.withDefaultNamespace("oak_boat")));

        ResolvedGiftPreference exactResult = GiftPreferenceResolver.resolve(
                        List.of(tag, exact),
                        null,
                        VillagerProfession.FISHERMAN,
                        new ItemStack(Items.OAK_BOAT))
                .orElseThrow();
        helper.assertValueEqual(exactResult.categoryId(), exact.id(), "exact item should win equal-priority overlap");

        GiftPreferenceDefinition higherPriorityTag = definition(
                "test:higher_priority",
                Set.of(VillagerProfession.FISHERMAN),
                2,
                50,
                GiftPreferenceDefinition.ItemMatcher.tag(BOATS));
        ResolvedGiftPreference priorityResult = GiftPreferenceResolver.resolve(
                        List.of(exact, higherPriorityTag),
                        null,
                        VillagerProfession.FISHERMAN,
                        new ItemStack(Items.OAK_BOAT))
                .orElseThrow();
        helper.assertValueEqual(
                priorityResult.categoryId(),
                higherPriorityTag.id(),
                "priority should win before exactness");

        GiftPreferenceDefinition stableA = definition(
                "test:a",
                Set.of(VillagerProfession.FISHERMAN),
                1,
                0,
                GiftPreferenceDefinition.ItemMatcher.item(ResourceLocation.withDefaultNamespace("oak_boat")));
        GiftPreferenceDefinition stableZ = definition(
                "test:z",
                Set.of(VillagerProfession.FISHERMAN),
                2,
                0,
                GiftPreferenceDefinition.ItemMatcher.item(ResourceLocation.withDefaultNamespace("oak_boat")));
        ResolvedGiftPreference stableResult = GiftPreferenceResolver.resolve(
                        List.of(stableZ, stableA),
                        null,
                        VillagerProfession.FISHERMAN,
                        new ItemStack(Items.OAK_BOAT))
                .orElseThrow();
        helper.assertValueEqual(stableResult.categoryId(), stableA.id(), "category id should break remaining ties");

        GiftPreferenceDefinition global = definition(
                "test:global",
                Set.of(),
                1,
                0,
                GiftPreferenceDefinition.ItemMatcher.item(ResourceLocation.withDefaultNamespace("oak_boat")));
        ResolvedGiftPreference professionResult = GiftPreferenceResolver.resolve(
                        List.of(global, stableZ),
                        null,
                        VillagerProfession.FISHERMAN,
                        new ItemStack(Items.OAK_BOAT))
                .orElseThrow();
        helper.assertValueEqual(
                professionResult.categoryId(),
                stableZ.id(),
                "profession category should win a remaining tie against a global category");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void oneTagCategoryAppliesAcrossItemsAndMatchesClientResolution(GameTestHelper helper) {
        GiftPreferenceDefinition boats = definition(
                "test:boats",
                Set.of(VillagerProfession.FISHERMAN),
                3,
                20,
                GiftPreferenceDefinition.ItemMatcher.tag(BOATS));
        ItemStack oakBoat = new ItemStack(Items.OAK_BOAT);
        ItemStack spruceBoat = new ItemStack(Items.SPRUCE_BOAT);

        ResolvedGiftPreference oakResult = GiftPreferenceResolver.resolve(
                        List.of(boats), null, VillagerProfession.FISHERMAN, oakBoat)
                .orElseThrow();
        ResolvedGiftPreference spruceResult = GiftPreferenceResolver.resolve(
                        List.of(boats), null, VillagerProfession.FISHERMAN, spruceBoat)
                .orElseThrow();
        helper.assertValueEqual(oakResult.categoryId(), boats.id(), "oak boat category");
        helper.assertValueEqual(spruceResult.categoryId(), boats.id(), "spruce boat category");

        GiftPreferenceView knownView = view(boats, true, 3);
        GiftPreferenceView unknownView = view(boats, false, 0);
        helper.assertValueEqual(
                GiftPreferenceResolver.resolveView(List.of(knownView), oakBoat).orElseThrow().categoryId(),
                oakResult.categoryId(),
                "client and server should resolve the same category");
        helper.assertValueEqual(
                GiftPreferenceResolver.resolveView(List.of(unknownView), spruceBoat).orElseThrow().rating(),
                0,
                "unknown view should not contain the live rating");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void discoveredIdentityUsesTheCurrentRatingAndProfession(GameTestHelper helper) {
        GiftPreferenceDefinition oldDefinition = definition(
                "test:changing",
                Set.of(VillagerProfession.FISHERMAN),
                2,
                0,
                GiftPreferenceDefinition.ItemMatcher.item(ResourceLocation.withDefaultNamespace("cod")));
        GiftPreferenceDefinition newDefinition = definition(
                "test:changing",
                Set.of(VillagerProfession.FISHERMAN),
                3,
                0,
                GiftPreferenceDefinition.ItemMatcher.item(ResourceLocation.withDefaultNamespace("cod")));

        GiftPreferenceView oldView = view(oldDefinition, true, oldDefinition.rating());
        GiftPreferenceView newView = view(newDefinition, true, newDefinition.rating());
        helper.assertValueEqual(oldView.categoryId(), newView.categoryId(), "discovery identity should remain stable");
        helper.assertValueEqual(newView.rating(), 3, "known category should use the current definition rating");
        helper.assertFalse(
                GiftPreferenceResolver.resolve(
                                List.of(newDefinition),
                                null,
                                VillagerProfession.FARMER,
                                new ItemStack(Items.COD))
                        .isPresent(),
                "profession-specific definition should not leak to another profession");
        helper.assertValueEqual(
                VillagerGiftPreferences.GiftReaction.fromRating(3),
                VillagerGiftPreferences.GiftReaction.LOVED,
                "strongly loved consequence mapping");
        helper.assertValueEqual(
                VillagerGiftPreferences.GiftReaction.fromRating(-2),
                VillagerGiftPreferences.GiftReaction.HATED,
                "strongly disliked consequence mapping");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void bundledDataUsesTagsAndExactOverrides(GameTestHelper helper) {
        ResolvedGiftPreference cookedFish = VillagerGiftPreferences.evaluate(
                helper.getLevel(), VillagerProfession.FISHERMAN, new ItemStack(Items.COOKED_COD));
        ResolvedGiftPreference rawFish = VillagerGiftPreferences.evaluate(
                helper.getLevel(), VillagerProfession.FISHERMAN, new ItemStack(Items.COD));
        ResolvedGiftPreference boat = VillagerGiftPreferences.evaluate(
                helper.getLevel(), VillagerProfession.FISHERMAN, new ItemStack(Items.BIRCH_BOAT));
        ResolvedGiftPreference pufferfish = VillagerGiftPreferences.evaluate(
                helper.getLevel(), VillagerProfession.FISHERMAN, new ItemStack(Items.PUFFERFISH));

        helper.assertValueEqual(cookedFish.rating(), 3, "cooked fish tag rating");
        helper.assertValueEqual(cookedFish.source(), GiftPreferenceDefinition.MatchSource.TAG, "cooked fish tag source");
        helper.assertValueEqual(rawFish.rating(), 2, "raw fish tag rating");
        helper.assertValueEqual(boat.rating(), 1, "boat tag rating");
        helper.assertValueEqual(pufferfish.rating(), -1, "pufferfish exact override rating");
        helper.assertValueEqual(
                pufferfish.source(),
                GiftPreferenceDefinition.MatchSource.ITEM,
                "pufferfish exact override source");
        helper.succeed();
    }

    private static GiftPreferenceDefinition definition(
            String id,
            Set<VillagerProfession> professions,
            int rating,
            int priority,
            GiftPreferenceDefinition.ItemMatcher... matchers) {
        VillagerGiftPreferences.GiftReaction reaction = VillagerGiftPreferences.GiftReaction.fromRating(rating);
        return new GiftPreferenceDefinition(
                ResourceLocation.parse(id),
                professions,
                rating,
                reaction.defaultPerItemReputation(),
                "",
                priority,
                null,
                GiftCategoryName.EMPTY,
                List.of(matchers));
    }

    private static GiftPreferenceView view(GiftPreferenceDefinition definition, boolean known, int rating) {
        return new GiftPreferenceView(
                definition.id(),
                rating,
                known,
                definition.priority(),
                definition.professionSpecific(),
                definition.name(),
                definition.matchers().stream()
                        .map(matcher -> new GiftPreferenceView.Matcher(matcher.source(), matcher.value()))
                        .toList());
    }
}
