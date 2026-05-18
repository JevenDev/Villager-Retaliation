package com.jvn.villagerretaliation.dialogue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
    private static final String TAG_LAST_DIRECT_HIT_GAME_TIME = "LastDirectHitGameTime";
    private static final String TAG_LAST_DIRECT_HIT_WEAPON = "LastDirectHitWeapon";
    private static final String TAG_REQUEST_TYPE = "RequestType";
    private static final String TAG_GAME_TIME = "GameTime";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_WINDOW_START_GAME_TIME = "WindowStartGameTime";
    private static final String TAG_WINDOW_DAY = "WindowDay";
    private static final String TAG_BAD_FIRST_IMPRESSION = "BadFirstImpression";
    private static final int MAX_RECENT_LINES = 5;

    private final Map<UUID, Map<UUID, InteractionEntry>> entries = new HashMap<>();

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
            data.entries.computeIfAbsent(entryTag.getUUID(TAG_VILLAGER), ignored -> new HashMap<>())
                    .put(entryTag.getUUID(TAG_PLAYER), entry);
        }
        return data;
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
                entriesTag.add(entryTag);
            }
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public InteractionEntry getOrCreate(UUID villagerId, UUID playerId) {
        return this.entries.computeIfAbsent(villagerId, ignored -> new HashMap<>())
                .computeIfAbsent(playerId, ignored -> new InteractionEntry());
    }

    public static class InteractionEntry {
        private boolean hasTalked;
        private long lastPositiveDialogueReputationGameTime = Long.MIN_VALUE;
        private long lastPositiveDialogueReputationDay = Long.MIN_VALUE;
        private long lastNegativeDialogueReputationGameTime = Long.MIN_VALUE;
        private long lastJokeReputationGameTime = Long.MIN_VALUE;
        private long lastSleepDisturbanceNight = Long.MIN_VALUE;
        private boolean badFirstImpression;
        private long lastDirectHitGameTime = Long.MIN_VALUE;
        private String lastDirectHitWeapon;
        private DialogueRequestType consecutiveRequestType;
        private int consecutiveRequestCount;
        private final ArrayDeque<String> recentDialogueIds = new ArrayDeque<>();
        private final Map<DialogueRequestType, Long> lastReputationByRequestType = new EnumMap<>(DialogueRequestType.class);
        private final Map<DialogueRequestType, Long> lastDialogueByRequestType = new EnumMap<>(DialogueRequestType.class);
        private final Map<DialogueRequestType, RequestUseWindow> requestUseWindows = new EnumMap<>(DialogueRequestType.class);

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

        public String lastDirectHitWeapon() {
            return this.lastDirectHitWeapon;
        }

        public void rememberSleepDisturbance(long night) {
            this.lastSleepDisturbanceNight = night;
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
    }
}
