package com.jvn.villagerretaliation.dialogue;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

final class GiftKnowledgeStore {
    private static final String TAG_GIFT_KNOWLEDGE = "GiftKnowledge";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_PROFESSIONS = "Professions";
    private static final String TAG_PROFESSION = "Profession";
    private static final String TAG_DISCOVERED_CATEGORIES = "DiscoveredCategories";
    private static final String TAG_LIKED_GIFTS = "LikedGifts";
    private static final String TAG_DISLIKED_GIFTS = "DislikedGifts";

    private final Map<UUID, GiftKnowledgeBook> booksByPlayer = new HashMap<>();

    void load(CompoundTag root) {
        this.booksByPlayer.clear();
        ListTag giftKnowledgeTag = root.getList(TAG_GIFT_KNOWLEDGE, Tag.TAG_COMPOUND);
        for (Tag rawBook : giftKnowledgeTag) {
            if (!(rawBook instanceof CompoundTag bookTag) || !bookTag.hasUUID(TAG_PLAYER)) {
                continue;
            }
            GiftKnowledgeBook book = new GiftKnowledgeBook();
            ListTag professionsTag = bookTag.getList(TAG_PROFESSIONS, Tag.TAG_COMPOUND);
            for (Tag rawProfession : professionsTag) {
                if (!(rawProfession instanceof CompoundTag professionTag)
                        || !professionTag.contains(TAG_PROFESSION, Tag.TAG_STRING)) {
                    continue;
                }
                GiftKnowledgeEntry knowledgeEntry = new GiftKnowledgeEntry();
                readStringSet(
                        professionTag.getList(TAG_DISCOVERED_CATEGORIES, Tag.TAG_STRING),
                        knowledgeEntry.discoveredCategories);
                readStringSet(professionTag.getList(TAG_LIKED_GIFTS, Tag.TAG_STRING), knowledgeEntry.legacyLikedGifts);
                readStringSet(professionTag.getList(TAG_DISLIKED_GIFTS, Tag.TAG_STRING), knowledgeEntry.legacyDislikedGifts);
                book.byProfession.put(professionTag.getString(TAG_PROFESSION), knowledgeEntry);
            }
            this.booksByPlayer.put(bookTag.getUUID(TAG_PLAYER), book);
        }
    }

    void save(CompoundTag root) {
        ListTag giftKnowledgeTag = new ListTag();
        for (Map.Entry<UUID, GiftKnowledgeBook> bookEntry : this.booksByPlayer.entrySet()) {
            CompoundTag bookTag = new CompoundTag();
            bookTag.putUUID(TAG_PLAYER, bookEntry.getKey());
            ListTag professionsTag = new ListTag();
            for (Map.Entry<String, GiftKnowledgeEntry> professionEntry : bookEntry.getValue().byProfession.entrySet()) {
                GiftKnowledgeEntry knowledge = professionEntry.getValue();
                CompoundTag professionTag = new CompoundTag();
                professionTag.putString(TAG_PROFESSION, professionEntry.getKey());
                professionTag.put(TAG_DISCOVERED_CATEGORIES, writeStringSet(knowledge.discoveredCategories));
                if (!knowledge.legacyLikedGifts.isEmpty()) {
                    professionTag.put(TAG_LIKED_GIFTS, writeStringSet(knowledge.legacyLikedGifts));
                }
                if (!knowledge.legacyDislikedGifts.isEmpty()) {
                    professionTag.put(TAG_DISLIKED_GIFTS, writeStringSet(knowledge.legacyDislikedGifts));
                }
                professionsTag.add(professionTag);
            }
            bookTag.put(TAG_PROFESSIONS, professionsTag);
            giftKnowledgeTag.add(bookTag);
        }
        root.put(TAG_GIFT_KNOWLEDGE, giftKnowledgeTag);
    }

    boolean knowsCategory(UUID playerId, String professionKey, String categoryId) {
        GiftKnowledgeEntry entry = giftKnowledgeEntry(playerId, professionKey, false);
        return entry != null && entry.discoveredCategories.contains(categoryId);
    }

    Set<String> discoveredCategories(UUID playerId, String... professionKeys) {
        GiftKnowledgeBook book = this.booksByPlayer.get(playerId);
        if (book == null) {
            return Set.of();
        }
        Set<String> categories = new LinkedHashSet<>();
        if (professionKeys == null || professionKeys.length == 0) {
            book.byProfession.values().forEach(entry -> categories.addAll(entry.discoveredCategories));
        } else {
            for (String professionKey : professionKeys) {
                GiftKnowledgeEntry entry = book.byProfession.get(professionKey);
                if (entry != null) {
                    categories.addAll(entry.discoveredCategories);
                }
            }
        }
        return Set.copyOf(categories);
    }

    boolean rememberCategory(UUID playerId, String professionKey, String categoryId) {
        return giftKnowledgeEntry(playerId, professionKey, true).discoveredCategories.add(categoryId);
    }

    boolean knowsGift(UUID playerId, String professionKey, String itemId, boolean liked) {
        GiftKnowledgeEntry entry = giftKnowledgeEntry(playerId, professionKey, false);
        if (entry == null) {
            return false;
        }
        return liked ? entry.legacyLikedGifts.contains(itemId) : entry.legacyDislikedGifts.contains(itemId);
    }

    Set<String> legacyGiftIds(UUID playerId, String professionKey, boolean liked) {
        GiftKnowledgeEntry entry = giftKnowledgeEntry(playerId, professionKey, false);
        if (entry == null) {
            return Set.of();
        }
        return Set.copyOf(liked ? entry.legacyLikedGifts : entry.legacyDislikedGifts);
    }

    boolean removeLegacyGift(UUID playerId, String professionKey, String itemId, boolean liked) {
        GiftKnowledgeEntry entry = giftKnowledgeEntry(playerId, professionKey, false);
        if (entry == null) {
            return false;
        }
        return (liked ? entry.legacyLikedGifts : entry.legacyDislikedGifts).remove(itemId);
    }

    boolean hasGiftKnowledge(UUID playerId, String... professionKeys) {
        GiftKnowledgeBook book = this.booksByPlayer.get(playerId);
        if (book == null) {
            return false;
        }
        if (professionKeys == null || professionKeys.length == 0) {
            return book.byProfession.values().stream().anyMatch(GiftKnowledgeEntry::hasKnownGifts);
        }
        for (String professionKey : professionKeys) {
            if (book.hasKnownGifts(professionKey)) {
                return true;
            }
        }
        return false;
    }

    boolean rememberGiftKnowledge(UUID playerId, String professionKey, String itemId, boolean liked) {
        GiftKnowledgeEntry entry = giftKnowledgeEntry(playerId, professionKey, true);
        Set<String> target = liked ? entry.legacyLikedGifts : entry.legacyDislikedGifts;
        Set<String> opposite = liked ? entry.legacyDislikedGifts : entry.legacyLikedGifts;
        boolean changed = target.add(itemId);
        changed |= opposite.remove(itemId);
        return changed;
    }

    private GiftKnowledgeEntry giftKnowledgeEntry(UUID playerId, String professionKey, boolean create) {
        GiftKnowledgeBook book = this.booksByPlayer.get(playerId);
        if (book == null) {
            if (!create) {
                return null;
            }
            book = new GiftKnowledgeBook();
            this.booksByPlayer.put(playerId, book);
        }
        if (create) {
            return book.byProfession.computeIfAbsent(professionKey, ignored -> new GiftKnowledgeEntry());
        }
        return book.byProfession.get(professionKey);
    }

    private static void readStringSet(ListTag tag, Set<String> values) {
        for (Tag rawValue : tag) {
            values.add(rawValue.getAsString());
        }
    }

    private static ListTag writeStringSet(Set<String> values) {
        ListTag tag = new ListTag();
        for (String value : values) {
            tag.add(StringTag.valueOf(value));
        }
        return tag;
    }

    private static class GiftKnowledgeBook {
        private final Map<String, GiftKnowledgeEntry> byProfession = new HashMap<>();

        private boolean hasKnownGifts(String professionKey) {
            GiftKnowledgeEntry entry = this.byProfession.get(professionKey);
            return entry != null && entry.hasKnownGifts();
        }
    }

    private static class GiftKnowledgeEntry {
        private final Set<String> discoveredCategories = new LinkedHashSet<>();
        private final Set<String> legacyLikedGifts = new LinkedHashSet<>();
        private final Set<String> legacyDislikedGifts = new LinkedHashSet<>();

        private boolean hasKnownGifts() {
            return !this.discoveredCategories.isEmpty()
                    || !this.legacyLikedGifts.isEmpty()
                    || !this.legacyDislikedGifts.isEmpty();
        }
    }
}
