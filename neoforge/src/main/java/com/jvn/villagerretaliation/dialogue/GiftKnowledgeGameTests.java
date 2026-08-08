package com.jvn.villagerretaliation.dialogue;

import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class GiftKnowledgeGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private GiftKnowledgeGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void categoriesPersistPerPlayerAndProfession(GameTestHelper helper) {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        String categoryId = "test:fisherman/cooked_fish";
        GiftKnowledgeStore store = new GiftKnowledgeStore();

        helper.assertTrue(store.rememberCategory(firstPlayer, "minecraft:fisherman", categoryId), "first discovery");
        helper.assertFalse(
                store.rememberCategory(firstPlayer, "minecraft:fisherman", categoryId),
                "duplicate discovery should not change data");
        helper.assertTrue(
                store.knowsCategory(firstPlayer, "minecraft:fisherman", categoryId),
                "same player and profession should know category");
        helper.assertFalse(
                store.knowsCategory(firstPlayer, "minecraft:farmer", categoryId),
                "knowledge should remain profession-wide without crossing professions");
        helper.assertFalse(
                store.knowsCategory(secondPlayer, "minecraft:fisherman", categoryId),
                "another player should not inherit discovery");

        CompoundTag saved = new CompoundTag();
        store.save(saved);
        GiftKnowledgeStore loaded = new GiftKnowledgeStore();
        loaded.load(saved);

        helper.assertTrue(
                loaded.knowsCategory(firstPlayer, "minecraft:fisherman", categoryId),
                "category identity should survive save and load");
        helper.assertValueEqual(
                loaded.discoveredCategories(firstPlayer, "minecraft:fisherman"),
                Set.of(categoryId),
                "saved knowledge should contain category ids only");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void removedCategoriesAndLegacyItemsRemainMigrationSafe(GameTestHelper helper) {
        UUID playerId = UUID.randomUUID();
        GiftKnowledgeStore store = new GiftKnowledgeStore();
        CompoundTag legacyRoot = legacyGiftData(playerId, "minecraft:fisherman", "minecraft:cod");
        store.load(legacyRoot);
        store.rememberCategory(playerId, "minecraft:fisherman", "missing_mod:removed_category");

        CompoundTag saved = new CompoundTag();
        store.save(saved);
        GiftKnowledgeStore loaded = new GiftKnowledgeStore();
        loaded.load(saved);

        helper.assertTrue(
                loaded.knowsCategory(playerId, "minecraft:fisherman", "missing_mod:removed_category"),
                "removed datapack categories should remain harmless stored identities");
        helper.assertValueEqual(
                loaded.legacyGiftIds(playerId, "minecraft:fisherman", true),
                Set.of("minecraft:cod"),
                "legacy item knowledge should remain available for centralized migration");
        helper.assertTrue(
                loaded.removeLegacyGift(playerId, "minecraft:fisherman", "minecraft:cod", true),
                "migrated profession item should be removable");
        helper.assertTrue(
                loaded.legacyGiftIds(playerId, "minecraft:fisherman", true).isEmpty(),
                "migrated legacy item should not be retained");
        helper.succeed();
    }

    private static CompoundTag legacyGiftData(UUID playerId, String profession, String likedItem) {
        CompoundTag professionTag = new CompoundTag();
        professionTag.putString("Profession", profession);
        ListTag liked = new ListTag();
        liked.add(StringTag.valueOf(likedItem));
        professionTag.put("LikedGifts", liked);
        ListTag professions = new ListTag();
        professions.add(professionTag);

        CompoundTag bookTag = new CompoundTag();
        bookTag.putUUID("Player", playerId);
        bookTag.put("Professions", professions);
        ListTag books = new ListTag();
        books.add(bookTag);

        CompoundTag root = new CompoundTag();
        root.put("GiftKnowledge", books);
        return root;
    }
}
