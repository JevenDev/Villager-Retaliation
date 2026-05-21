package com.jvn.villagerretaliation.dialogue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerInteractionSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_interactions";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_HAS_TALKED = "HasTalked";
    private static final String TAG_RECENT_LINES = "RecentLines";
    private static final String TAG_LAST_POSITIVE_DIALOGUE_REPUTATION = "LastPositiveDialogueReputation";
    private static final String TAG_LAST_POSITIVE_DIALOGUE_REPUTATION_DAY = "LastPositiveDialogueReputationDay";
    private static final String TAG_LAST_NEGATIVE_DIALOGUE_REPUTATION = "LastNegativeDialogueReputation";
    private static final String TAG_LAST_JOKE_REPUTATION = "LastJokeReputation";
    private static final String TAG_LAST_REQUEST_TYPE_REPUTATION = "LastRequestTypeReputation";
    private static final String TAG_LAST_REQUEST_TYPE_DIALOGUE = "LastRequestTypeDialogue";
    private static final String TAG_CONSECUTIVE_REQUEST_TYPE = "ConsecutiveRequestType";
    private static final String TAG_CONSECUTIVE_REQUEST_COUNT = "ConsecutiveRequestCount";
    private static final String TAG_REQUEST_TYPE_USES = "RequestTypeUses";
    private static final String TAG_LAST_SLEEP_DISTURBANCE_NIGHT = "LastSleepDisturbanceNight";
    private static final String TAG_LAST_BROKEN_BED_GAME_TIME = "LastBrokenBedGameTime";
    private static final String TAG_LAST_DIRECT_HIT_GAME_TIME = "LastDirectHitGameTime";
    private static final String TAG_LAST_DIRECT_HIT_WEAPON = "LastDirectHitWeapon";
    private static final String TAG_REQUEST_TYPE = "RequestType";
    private static final String TAG_GAME_TIME = "GameTime";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_WINDOW_START_GAME_TIME = "WindowStartGameTime";
    private static final String TAG_WINDOW_DAY = "WindowDay";
    private static final String TAG_BAD_FIRST_IMPRESSION = "BadFirstImpression";
    private static final String TAG_GIFT_KNOWLEDGE = "GiftKnowledge";
    private static final String TAG_PROFESSIONS = "Professions";
    private static final String TAG_PROFESSION = "Profession";
    private static final String TAG_LIKED_GIFTS = "LikedGifts";
    private static final String TAG_DISLIKED_GIFTS = "DislikedGifts";
    private static final String TAG_CARTOGRAPHER_MAPS = "CartographerMaps";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_STRUCTURE = "Structure";
    private static final String TAG_TARGET_NAME = "TargetName";
    private static final String TAG_TARGET_X = "TargetX";
    private static final String TAG_TARGET_Y = "TargetY";
    private static final String TAG_TARGET_Z = "TargetZ";
    private static final String TAG_EXPIRES_AT = "ExpiresAt";
    private static final String TAG_FOUND = "Found";
    private static final String TAG_REPORTED = "Reported";
    private static final int MAX_RECENT_LINES = 5;
    private static final int MAX_CARTOGRAPHER_MAPS = 8;

    private final Map<UUID, Map<UUID, InteractionEntry>> entries = new HashMap<>();
    private final Map<UUID, GiftKnowledgeBook> giftKnowledge = new HashMap<>();

    public static VillagerInteractionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerInteractionSavedData::new, VillagerInteractionSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerInteractionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerInteractionSavedData data = new VillagerInteractionSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID(TAG_VILLAGER)
                    || !entryTag.hasUUID(TAG_PLAYER)) {
                continue;
            }

            InteractionEntry entry = new InteractionEntry();
            entry.hasTalked = entryTag.getBoolean(TAG_HAS_TALKED);
            entry.lastPositiveDialogueReputationGameTime = readOptionalLong(entryTag, TAG_LAST_POSITIVE_DIALOGUE_REPUTATION);
            entry.lastPositiveDialogueReputationDay = readOptionalLong(entryTag, TAG_LAST_POSITIVE_DIALOGUE_REPUTATION_DAY);
            entry.lastNegativeDialogueReputationGameTime = readOptionalLong(entryTag, TAG_LAST_NEGATIVE_DIALOGUE_REPUTATION);
            entry.lastJokeReputationGameTime = readOptionalLong(entryTag, TAG_LAST_JOKE_REPUTATION);
            entry.lastSleepDisturbanceNight = readOptionalLong(entryTag, TAG_LAST_SLEEP_DISTURBANCE_NIGHT);
            entry.lastBrokenBedGameTime = readOptionalLong(entryTag, TAG_LAST_BROKEN_BED_GAME_TIME);
            entry.badFirstImpression = entryTag.getBoolean(TAG_BAD_FIRST_IMPRESSION);
            entry.lastDirectHitGameTime = readOptionalLong(entryTag, TAG_LAST_DIRECT_HIT_GAME_TIME);
            if (entryTag.contains(TAG_LAST_DIRECT_HIT_WEAPON, Tag.TAG_STRING)) {
                entry.lastDirectHitWeapon = entryTag.getString(TAG_LAST_DIRECT_HIT_WEAPON);
            }
            if (entryTag.contains(TAG_CONSECUTIVE_REQUEST_TYPE, Tag.TAG_STRING)) {
                try {
                    entry.consecutiveRequestType = DialogueRequestType.valueOf(entryTag.getString(TAG_CONSECUTIVE_REQUEST_TYPE));
                    entry.consecutiveRequestCount = entryTag.getInt(TAG_CONSECUTIVE_REQUEST_COUNT);
                } catch (IllegalArgumentException ignored) {
                    entry.consecutiveRequestType = null;
                    entry.consecutiveRequestCount = 0;
                }
            }
            ListTag recentLines = entryTag.getList(TAG_RECENT_LINES, Tag.TAG_STRING);
            for (Tag rawLine : recentLines) {
                entry.recentDialogueIds.addLast(rawLine.getAsString());
            }
            ListTag requestTypeTimes = entryTag.getList(TAG_LAST_REQUEST_TYPE_REPUTATION, Tag.TAG_COMPOUND);
            for (Tag rawRequestTypeTime : requestTypeTimes) {
                if (!(rawRequestTypeTime instanceof CompoundTag requestTypeTag)) {
                    continue;
                }
                try {
                    DialogueRequestType requestType = DialogueRequestType.valueOf(requestTypeTag.getString(TAG_REQUEST_TYPE));
                    entry.lastReputationByRequestType.put(requestType, requestTypeTag.getLong(TAG_GAME_TIME));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ListTag requestTypeDialogueTimes = entryTag.getList(TAG_LAST_REQUEST_TYPE_DIALOGUE, Tag.TAG_COMPOUND);
            for (Tag rawRequestTypeTime : requestTypeDialogueTimes) {
                if (!(rawRequestTypeTime instanceof CompoundTag requestTypeTag)) {
                    continue;
                }
                try {
                    DialogueRequestType requestType = DialogueRequestType.valueOf(requestTypeTag.getString(TAG_REQUEST_TYPE));
                    entry.lastDialogueByRequestType.put(requestType, requestTypeTag.getLong(TAG_GAME_TIME));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ListTag requestTypeUses = entryTag.getList(TAG_REQUEST_TYPE_USES, Tag.TAG_COMPOUND);
            for (Tag rawRequestTypeUse : requestTypeUses) {
                if (!(rawRequestTypeUse instanceof CompoundTag requestTypeTag)) {
                    continue;
                }
                try {
                    DialogueRequestType requestType = DialogueRequestType.valueOf(requestTypeTag.getString(TAG_REQUEST_TYPE));
                    entry.requestUseWindows.put(requestType, new RequestUseWindow(
                            requestTypeTag.getInt(TAG_COUNT),
                            readOptionalLong(requestTypeTag, TAG_WINDOW_START_GAME_TIME),
                            readOptionalLong(requestTypeTag, TAG_WINDOW_DAY)
                    ));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ListTag cartographerMaps = entryTag.getList(TAG_CARTOGRAPHER_MAPS, Tag.TAG_COMPOUND);
            for (Tag rawMap : cartographerMaps) {
                if (!(rawMap instanceof CompoundTag mapTag)) {
                    continue;
                }
                ResourceLocation dimension = ResourceLocation.tryParse(mapTag.getString(TAG_DIMENSION));
                ResourceLocation structureId = ResourceLocation.tryParse(mapTag.getString(TAG_STRUCTURE));
                if (dimension == null || structureId == null) {
                    continue;
                }
                entry.cartographerMaps.add(new CartographerMapMemory(
                        dimension,
                        structureId,
                        mapTag.getString(TAG_TARGET_NAME),
                        new BlockPos(mapTag.getInt(TAG_TARGET_X), mapTag.getInt(TAG_TARGET_Y), mapTag.getInt(TAG_TARGET_Z)),
                        readOptionalLong(mapTag, TAG_EXPIRES_AT),
                        mapTag.getBoolean(TAG_FOUND),
                        mapTag.getBoolean(TAG_REPORTED)
                ));
            }
            data.entries.computeIfAbsent(entryTag.getUUID(TAG_VILLAGER), ignored -> new HashMap<>())
                    .put(entryTag.getUUID(TAG_PLAYER), entry);
        }
        ListTag giftKnowledgeTag = tag.getList(TAG_GIFT_KNOWLEDGE, Tag.TAG_COMPOUND);
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
                readStringSet(professionTag.getList(TAG_LIKED_GIFTS, Tag.TAG_STRING), knowledgeEntry.likedGifts);
                readStringSet(professionTag.getList(TAG_DISLIKED_GIFTS, Tag.TAG_STRING), knowledgeEntry.dislikedGifts);
                book.byProfession.put(professionTag.getString(TAG_PROFESSION), knowledgeEntry);
            }
            data.giftKnowledge.put(bookTag.getUUID(TAG_PLAYER), book);
        }
        return data;
    }

    private static void readStringSet(ListTag tag, Set<String> values) {
        for (Tag rawValue : tag) {
            values.add(rawValue.getAsString());
        }
    }

    private static long readOptionalLong(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : Long.MIN_VALUE;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<UUID, Map<UUID, InteractionEntry>> villagerEntry : this.entries.entrySet()) {
            for (Map.Entry<UUID, InteractionEntry> playerEntry : villagerEntry.getValue().entrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putUUID(TAG_VILLAGER, villagerEntry.getKey());
                entryTag.putUUID(TAG_PLAYER, playerEntry.getKey());
                entryTag.putBoolean(TAG_HAS_TALKED, playerEntry.getValue().hasTalked);
                entryTag.putLong(TAG_LAST_POSITIVE_DIALOGUE_REPUTATION, playerEntry.getValue().lastPositiveDialogueReputationGameTime);
                entryTag.putLong(TAG_LAST_POSITIVE_DIALOGUE_REPUTATION_DAY, playerEntry.getValue().lastPositiveDialogueReputationDay);
                entryTag.putLong(TAG_LAST_NEGATIVE_DIALOGUE_REPUTATION, playerEntry.getValue().lastNegativeDialogueReputationGameTime);
                entryTag.putLong(TAG_LAST_JOKE_REPUTATION, playerEntry.getValue().lastJokeReputationGameTime);
                entryTag.putLong(TAG_LAST_SLEEP_DISTURBANCE_NIGHT, playerEntry.getValue().lastSleepDisturbanceNight);
                entryTag.putLong(TAG_LAST_BROKEN_BED_GAME_TIME, playerEntry.getValue().lastBrokenBedGameTime);
                entryTag.putBoolean(TAG_BAD_FIRST_IMPRESSION, playerEntry.getValue().badFirstImpression);
                entryTag.putLong(TAG_LAST_DIRECT_HIT_GAME_TIME, playerEntry.getValue().lastDirectHitGameTime);
                if (playerEntry.getValue().lastDirectHitWeapon != null && !playerEntry.getValue().lastDirectHitWeapon.isBlank()) {
                    entryTag.putString(TAG_LAST_DIRECT_HIT_WEAPON, playerEntry.getValue().lastDirectHitWeapon);
                }
                if (playerEntry.getValue().consecutiveRequestType != null) {
                    entryTag.putString(TAG_CONSECUTIVE_REQUEST_TYPE, playerEntry.getValue().consecutiveRequestType.name());
                    entryTag.putInt(TAG_CONSECUTIVE_REQUEST_COUNT, playerEntry.getValue().consecutiveRequestCount);
                }
                ListTag recentLines = new ListTag();
                for (String lineId : playerEntry.getValue().recentDialogueIds) {
                    recentLines.add(StringTag.valueOf(lineId));
                }
                entryTag.put(TAG_RECENT_LINES, recentLines);
                ListTag requestTypeTimes = new ListTag();
                for (Map.Entry<DialogueRequestType, Long> requestTypeEntry : playerEntry.getValue().lastReputationByRequestType.entrySet()) {
                    CompoundTag requestTypeTag = new CompoundTag();
                    requestTypeTag.putString(TAG_REQUEST_TYPE, requestTypeEntry.getKey().name());
                    requestTypeTag.putLong(TAG_GAME_TIME, requestTypeEntry.getValue());
                    requestTypeTimes.add(requestTypeTag);
                }
                entryTag.put(TAG_LAST_REQUEST_TYPE_REPUTATION, requestTypeTimes);
                ListTag requestTypeDialogueTimes = new ListTag();
                for (Map.Entry<DialogueRequestType, Long> requestTypeEntry : playerEntry.getValue().lastDialogueByRequestType.entrySet()) {
                    CompoundTag requestTypeTag = new CompoundTag();
                    requestTypeTag.putString(TAG_REQUEST_TYPE, requestTypeEntry.getKey().name());
                    requestTypeTag.putLong(TAG_GAME_TIME, requestTypeEntry.getValue());
                    requestTypeDialogueTimes.add(requestTypeTag);
                }
                entryTag.put(TAG_LAST_REQUEST_TYPE_DIALOGUE, requestTypeDialogueTimes);
                ListTag requestTypeUses = new ListTag();
                for (Map.Entry<DialogueRequestType, RequestUseWindow> requestTypeEntry : playerEntry.getValue().requestUseWindows.entrySet()) {
                    CompoundTag requestTypeTag = new CompoundTag();
                    RequestUseWindow window = requestTypeEntry.getValue();
                    requestTypeTag.putString(TAG_REQUEST_TYPE, requestTypeEntry.getKey().name());
                    requestTypeTag.putInt(TAG_COUNT, window.count);
                    requestTypeTag.putLong(TAG_WINDOW_START_GAME_TIME, window.windowStartGameTime);
                    requestTypeTag.putLong(TAG_WINDOW_DAY, window.windowDay);
                    requestTypeUses.add(requestTypeTag);
                }
                entryTag.put(TAG_REQUEST_TYPE_USES, requestTypeUses);
                ListTag cartographerMaps = new ListTag();
                for (CartographerMapMemory mapMemory : playerEntry.getValue().cartographerMaps) {
                    CompoundTag mapTag = new CompoundTag();
                    mapTag.putString(TAG_DIMENSION, mapMemory.dimension().toString());
                    mapTag.putString(TAG_STRUCTURE, mapMemory.structureId().toString());
                    mapTag.putString(TAG_TARGET_NAME, mapMemory.targetName());
                    mapTag.putInt(TAG_TARGET_X, mapMemory.targetPos().getX());
                    mapTag.putInt(TAG_TARGET_Y, mapMemory.targetPos().getY());
                    mapTag.putInt(TAG_TARGET_Z, mapMemory.targetPos().getZ());
                    mapTag.putLong(TAG_EXPIRES_AT, mapMemory.expiresAtGameTime());
                    mapTag.putBoolean(TAG_FOUND, mapMemory.found());
                    mapTag.putBoolean(TAG_REPORTED, mapMemory.reported());
                    cartographerMaps.add(mapTag);
                }
                entryTag.put(TAG_CARTOGRAPHER_MAPS, cartographerMaps);
                entriesTag.add(entryTag);
            }
        }
        tag.put(TAG_ENTRIES, entriesTag);
        ListTag giftKnowledgeTag = new ListTag();
        for (Map.Entry<UUID, GiftKnowledgeBook> bookEntry : this.giftKnowledge.entrySet()) {
            CompoundTag bookTag = new CompoundTag();
            bookTag.putUUID(TAG_PLAYER, bookEntry.getKey());
            ListTag professionsTag = new ListTag();
            for (Map.Entry<String, GiftKnowledgeEntry> professionEntry : bookEntry.getValue().byProfession.entrySet()) {
                CompoundTag professionTag = new CompoundTag();
                professionTag.putString(TAG_PROFESSION, professionEntry.getKey());
                professionTag.put(TAG_LIKED_GIFTS, writeStringSet(professionEntry.getValue().likedGifts));
                professionTag.put(TAG_DISLIKED_GIFTS, writeStringSet(professionEntry.getValue().dislikedGifts));
                professionsTag.add(professionTag);
            }
            bookTag.put(TAG_PROFESSIONS, professionsTag);
            giftKnowledgeTag.add(bookTag);
        }
        tag.put(TAG_GIFT_KNOWLEDGE, giftKnowledgeTag);
        return tag;
    }

    private static ListTag writeStringSet(Set<String> values) {
        ListTag tag = new ListTag();
        for (String value : values) {
            tag.add(StringTag.valueOf(value));
        }
        return tag;
    }

    public InteractionEntry getOrCreate(UUID villagerId, UUID playerId) {
        return this.entries.computeIfAbsent(villagerId, ignored -> new HashMap<>())
                .computeIfAbsent(playerId, ignored -> new InteractionEntry());
    }

    public boolean knowsGift(UUID playerId, String professionKey, String itemId, boolean liked) {
        GiftKnowledgeEntry entry = giftKnowledgeEntry(playerId, professionKey, false);
        if (entry == null) {
            return false;
        }
        return liked ? entry.likedGifts.contains(itemId) : entry.dislikedGifts.contains(itemId);
    }

    public void rememberGiftKnowledge(UUID playerId, String professionKey, String itemId, boolean liked) {
        GiftKnowledgeEntry entry = giftKnowledgeEntry(playerId, professionKey, true);
        if (liked) {
            entry.likedGifts.add(itemId);
        } else {
            entry.dislikedGifts.add(itemId);
        }
    }

    public void rememberCartographerMap(
            UUID villagerId,
            UUID playerId,
            ResourceLocation dimension,
            ResourceLocation structureId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime) {
        InteractionEntry entry = getOrCreate(villagerId, playerId);
        entry.cartographerMaps.add(new CartographerMapMemory(
                dimension,
                structureId,
                targetName == null ? "" : targetName,
                targetPos.immutable(),
                expiresAtGameTime,
                false,
                false
        ));
        entry.pruneCartographerMaps();
    }

    public List<VillagerInteractionTracker.CartographerMapReport> markCartographerMapDiscoveriesNear(
            UUID playerId,
            ResourceLocation dimension,
            double x,
            double z,
            double radiusSqr,
            long gameTime) {
        List<VillagerInteractionTracker.CartographerMapReport> discoveries = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, InteractionEntry>> villagerEntry : this.entries.entrySet()) {
            Map<UUID, InteractionEntry> playerEntries = villagerEntry.getValue();
            InteractionEntry entry = playerEntries.get(playerId);
            if (entry == null) {
                continue;
            }
            discoveries.addAll(entry.markCartographerMapDiscoveriesNear(villagerEntry.getKey(), dimension, x, z, radiusSqr, gameTime));
        }
        return discoveries;
    }

    public VillagerInteractionTracker.CartographerMapReport unreportedCartographerMapDiscovery(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).unreportedCartographerMapDiscovery(villagerId);
    }

    public VillagerInteractionTracker.CartographerMapReport claimUnreportedCartographerMapDiscovery(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).claimUnreportedCartographerMapDiscovery(villagerId);
    }

    private GiftKnowledgeEntry giftKnowledgeEntry(UUID playerId, String professionKey, boolean create) {
        GiftKnowledgeBook book = this.giftKnowledge.get(playerId);
        if (book == null) {
            if (!create) {
                return null;
            }
            book = new GiftKnowledgeBook();
            this.giftKnowledge.put(playerId, book);
        }
        if (create) {
            return book.byProfession.computeIfAbsent(professionKey, ignored -> new GiftKnowledgeEntry());
        }
        return book.byProfession.get(professionKey);
    }

    public static class InteractionEntry {
        private boolean hasTalked;
        private long lastPositiveDialogueReputationGameTime = Long.MIN_VALUE;
        private long lastPositiveDialogueReputationDay = Long.MIN_VALUE;
        private long lastNegativeDialogueReputationGameTime = Long.MIN_VALUE;
        private long lastJokeReputationGameTime = Long.MIN_VALUE;
        private long lastSleepDisturbanceNight = Long.MIN_VALUE;
        private long lastBrokenBedGameTime = Long.MIN_VALUE;
        private boolean badFirstImpression;
        private long lastDirectHitGameTime = Long.MIN_VALUE;
        private String lastDirectHitWeapon;
        private DialogueRequestType consecutiveRequestType;
        private int consecutiveRequestCount;
        private final ArrayDeque<String> recentDialogueIds = new ArrayDeque<>();
        private final Map<DialogueRequestType, Long> lastReputationByRequestType = new EnumMap<>(DialogueRequestType.class);
        private final Map<DialogueRequestType, Long> lastDialogueByRequestType = new EnumMap<>(DialogueRequestType.class);
        private final Map<DialogueRequestType, RequestUseWindow> requestUseWindows = new EnumMap<>(DialogueRequestType.class);
        private final List<CartographerMapMemory> cartographerMaps = new ArrayList<>();

        public boolean hasTalked() {
            return this.hasTalked;
        }

        public void markTalked() {
            this.hasTalked = true;
        }

        public List<String> recentDialogueIds() {
            return new ArrayList<>(this.recentDialogueIds);
        }

        public long lastPositiveDialogueReputationGameTime() {
            return this.lastPositiveDialogueReputationGameTime;
        }

        public long lastPositiveDialogueReputationDay() {
            return this.lastPositiveDialogueReputationDay;
        }

        public long lastNegativeDialogueReputationGameTime() {
            return this.lastNegativeDialogueReputationGameTime;
        }

        public long lastJokeReputationGameTime() {
            return this.lastJokeReputationGameTime;
        }

        public long lastReputationGameTime(DialogueRequestType requestType) {
            return this.lastReputationByRequestType.getOrDefault(requestType, Long.MIN_VALUE);
        }

        public long lastDialogueGameTime(DialogueRequestType requestType) {
            return this.lastDialogueByRequestType.getOrDefault(requestType, Long.MIN_VALUE);
        }

        public boolean badFirstImpression() {
            return this.badFirstImpression;
        }

        public boolean hasDisturbedSleepThisNight(long night) {
            return this.lastSleepDisturbanceNight == night;
        }

        public long lastDirectHitGameTime() {
            return this.lastDirectHitGameTime;
        }

        public long lastBrokenBedGameTime() {
            return this.lastBrokenBedGameTime;
        }

        public String lastDirectHitWeapon() {
            return this.lastDirectHitWeapon;
        }

        public void rememberSleepDisturbance(long night) {
            this.lastSleepDisturbanceNight = night;
        }

        public void rememberBrokenBed(long gameTime) {
            this.lastBrokenBedGameTime = gameTime;
        }

        public void rememberDirectHit(long gameTime, String weapon) {
            this.lastDirectHitGameTime = gameTime;
            this.lastDirectHitWeapon = weapon;
        }

        public int consecutiveRequestCount(DialogueRequestType requestType) {
            return this.consecutiveRequestType == requestType ? this.consecutiveRequestCount : 0;
        }

        public int requestUseCount(DialogueRequestType requestType, long gameTime, long day, long resetTicks) {
            RequestUseWindow window = this.requestUseWindows.get(requestType);
            if (window == null || window.isExpired(gameTime, day, resetTicks)) {
                return 0;
            }
            return window.count;
        }

        public void rememberDialogueReputation(DialogueRequestType requestType, int delta, long gameTime, long day, boolean badFirstImpression) {
            if (delta > 0) {
                this.lastPositiveDialogueReputationGameTime = gameTime;
                this.lastPositiveDialogueReputationDay = day;
            } else if (delta < 0) {
                this.lastNegativeDialogueReputationGameTime = gameTime;
            }
            if (requestType == DialogueRequestType.JOKE) {
                this.lastJokeReputationGameTime = gameTime;
            }
            this.lastReputationByRequestType.put(requestType, gameTime);
            this.badFirstImpression = this.badFirstImpression || badFirstImpression;
        }

        public void rememberDialogueId(DialogueRequestType requestType, String dialogueId, long gameTime, long day, long resetTicks) {
            if (this.consecutiveRequestType == requestType) {
                this.consecutiveRequestCount++;
            } else {
                this.consecutiveRequestType = requestType;
                this.consecutiveRequestCount = 1;
            }
            this.requestUseWindows
                    .computeIfAbsent(requestType, ignored -> new RequestUseWindow())
                    .recordUse(gameTime, day, resetTicks);
            this.recentDialogueIds.remove(dialogueId);
            this.recentDialogueIds.addLast(dialogueId);
            this.lastDialogueByRequestType.put(requestType, gameTime);
            while (this.recentDialogueIds.size() > MAX_RECENT_LINES) {
                this.recentDialogueIds.removeFirst();
            }
        }

        public void reduceRepeatedDialogueUseCounts(int amount, long gameTime, long day, long resetTicks) {
            if (amount <= 0) {
                return;
            }
            if (this.consecutiveRequestType != null) {
                this.consecutiveRequestCount = Math.max(0, this.consecutiveRequestCount - amount);
                if (this.consecutiveRequestCount == 0) {
                    this.consecutiveRequestType = null;
                }
            }
            for (RequestUseWindow window : this.requestUseWindows.values()) {
                if (!window.isExpired(gameTime, day, resetTicks)) {
                    window.reduce(amount);
                }
            }
        }

        private List<VillagerInteractionTracker.CartographerMapReport> markCartographerMapDiscoveriesNear(
                UUID villagerId,
                ResourceLocation dimension,
                double x,
                double z,
                double radiusSqr,
                long gameTime) {
            List<VillagerInteractionTracker.CartographerMapReport> discoveries = new ArrayList<>();
            for (int index = 0; index < this.cartographerMaps.size(); index++) {
                CartographerMapMemory mapMemory = this.cartographerMaps.get(index);
                if (mapMemory.reported()
                        || mapMemory.found()
                        || mapMemory.expiresAtGameTime() <= gameTime
                        || !mapMemory.dimension().equals(dimension)) {
                    continue;
                }
                double dx = x - (mapMemory.targetPos().getX() + 0.5D);
                double dz = z - (mapMemory.targetPos().getZ() + 0.5D);
                if (dx * dx + dz * dz <= radiusSqr) {
                    this.cartographerMaps.set(index, mapMemory.withFound(true));
                    discoveries.add(mapMemory.toReport(villagerId));
                }
            }
            return discoveries;
        }

        private VillagerInteractionTracker.CartographerMapReport unreportedCartographerMapDiscovery(UUID villagerId) {
            for (int index = this.cartographerMaps.size() - 1; index >= 0; index--) {
                CartographerMapMemory mapMemory = this.cartographerMaps.get(index);
                if (mapMemory.found() && !mapMemory.reported()) {
                    return mapMemory.toReport(villagerId);
                }
            }
            return null;
        }

        private VillagerInteractionTracker.CartographerMapReport claimUnreportedCartographerMapDiscovery(UUID villagerId) {
            for (int index = this.cartographerMaps.size() - 1; index >= 0; index--) {
                CartographerMapMemory mapMemory = this.cartographerMaps.get(index);
                if (mapMemory.found() && !mapMemory.reported()) {
                    this.cartographerMaps.set(index, mapMemory.withReported(true));
                    return mapMemory.toReport(villagerId);
                }
            }
            return null;
        }

        private void pruneCartographerMaps() {
            while (this.cartographerMaps.size() > MAX_CARTOGRAPHER_MAPS) {
                int reportedIndex = firstReportedCartographerMapIndex();
                this.cartographerMaps.remove(reportedIndex >= 0 ? reportedIndex : 0);
            }
        }

        private int firstReportedCartographerMapIndex() {
            for (int index = 0; index < this.cartographerMaps.size(); index++) {
                if (this.cartographerMaps.get(index).reported()) {
                    return index;
                }
            }
            return -1;
        }
    }

    private record CartographerMapMemory(
            ResourceLocation dimension,
            ResourceLocation structureId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime,
            boolean found,
            boolean reported
    ) {
        private CartographerMapMemory withFound(boolean found) {
            return new CartographerMapMemory(
                    this.dimension,
                    this.structureId,
                    this.targetName,
                    this.targetPos,
                    this.expiresAtGameTime,
                    found,
                    this.reported
            );
        }

        private CartographerMapMemory withReported(boolean reported) {
            return new CartographerMapMemory(
                    this.dimension,
                    this.structureId,
                    this.targetName,
                    this.targetPos,
                    this.expiresAtGameTime,
                    this.found,
                    reported
            );
        }

        private VillagerInteractionTracker.CartographerMapReport toReport(UUID villagerId) {
            return new VillagerInteractionTracker.CartographerMapReport(
                    villagerId,
                    this.dimension,
                    this.structureId,
                    this.targetName,
                    this.targetPos
            );
        }
    }

    private static class RequestUseWindow {
        private int count;
        private long windowStartGameTime = Long.MIN_VALUE;
        private long windowDay = Long.MIN_VALUE;

        private RequestUseWindow() {
        }

        private RequestUseWindow(int count, long windowStartGameTime, long windowDay) {
            this.count = count;
            this.windowStartGameTime = windowStartGameTime;
            this.windowDay = windowDay;
        }

        private boolean isExpired(long gameTime, long day, long resetTicks) {
            return this.windowStartGameTime == Long.MIN_VALUE
                    || this.windowDay != day
                    || gameTime - this.windowStartGameTime >= resetTicks;
        }

        private void recordUse(long gameTime, long day, long resetTicks) {
            if (isExpired(gameTime, day, resetTicks)) {
                this.count = 0;
                this.windowStartGameTime = gameTime;
                this.windowDay = day;
            }
            this.count++;
        }

        private void reduce(int amount) {
            this.count = Math.max(0, this.count - amount);
        }
    }

    private static class GiftKnowledgeBook {
        private final Map<String, GiftKnowledgeEntry> byProfession = new HashMap<>();
    }

    private static class GiftKnowledgeEntry {
        private final Set<String> likedGifts = new LinkedHashSet<>();
        private final Set<String> dislikedGifts = new LinkedHashSet<>();
    }
}
