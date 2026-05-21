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
    private static final String TAG_STORY_HINTS = "StoryHints";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_KIND = "Kind";
    private static final String TAG_STRUCTURE = "Structure";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_TARGET_NAME = "TargetName";
    private static final String TAG_TARGET_X = "TargetX";
    private static final String TAG_TARGET_Y = "TargetY";
    private static final String TAG_TARGET_Z = "TargetZ";
    private static final String TAG_EXPIRES_AT = "ExpiresAt";
    private static final String TAG_FOUND = "Found";
    private static final String TAG_REPORTED = "Reported";
    private static final String TAG_COMBAT_SURVIVAL_UNREPORTED = "CombatSurvivalUnreported";
    private static final String TAG_COMBAT_SURVIVAL_EVENT_KIND = "CombatSurvivalEventKind";
    private static final String TAG_COMBAT_SURVIVAL_GAME_TIME = "CombatSurvivalGameTime";
    private static final String TAG_GEAR_REPORT_UNREPORTED = "GearReportUnreported";
    private static final String TAG_GEAR_REPORT_KIND = "GearReportKind";
    private static final String TAG_GEAR_REPORT_USED_IN_COMBAT = "GearReportUsedInCombat";
    private static final String TAG_GEAR_REPORT_GAME_TIME = "GearReportGameTime";
    private static final String TAG_RECRUITMENT_FOLLOWUP_UNREPORTED = "RecruitmentFollowupUnreported";
    private static final String TAG_RECRUITMENT_FOLLOWUP_SCENARIO = "RecruitmentFollowupScenario";
    private static final String TAG_RECRUITMENT_FOLLOWUP_GAME_TIME = "RecruitmentFollowupGameTime";
    private static final String TAG_RECRUITMENT_MEMORY_SCENARIO = "RecruitmentMemoryScenario";
    private static final String TAG_RECRUITMENT_MEMORY_BIOME = "RecruitmentMemoryBiome";
    private static final String TAG_RECRUITMENT_MEMORY_DISTANCE = "RecruitmentMemoryDistance";
    private static final String TAG_RECRUITMENT_MEMORY_GAME_TIME = "RecruitmentMemoryGameTime";
    private static final String TAG_GIFT_ADVICE_ITEM_ID = "GiftAdviceItemId";
    private static final String TAG_GIFT_ADVICE_ITEM_NAME = "GiftAdviceItemName";
    private static final String TAG_GIFT_ADVICE_TARGET_PROFESSION = "GiftAdviceTargetProfession";
    private static final String TAG_GIFT_ADVICE_RESULT_UNREPORTED = "GiftAdviceResultUnreported";
    private static final String TAG_GIFT_ADVICE_RESULT_PROFESSION = "GiftAdviceResultProfession";
    private static final String TAG_GIFT_ADVICE_RESULT_PROFESSION_NAME = "GiftAdviceResultProfessionName";
    private static final String TAG_GIFT_ADVICE_RESULT_VILLAGER_NAME = "GiftAdviceResultVillagerName";
    private static final String TAG_GIFT_ADVICE_RESULT_LIKED = "GiftAdviceResultLiked";
    private static final String TAG_GIFT_ADVICE_RESULT_GAME_TIME = "GiftAdviceResultGameTime";
    private static final int MAX_RECENT_LINES = 5;
    private static final int MAX_CARTOGRAPHER_MAPS = 8;
    private static final int MAX_STORY_HINTS = 12;

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
            entry.combatSurvivalUnreported = entryTag.getBoolean(TAG_COMBAT_SURVIVAL_UNREPORTED);
            if (entryTag.contains(TAG_COMBAT_SURVIVAL_EVENT_KIND, Tag.TAG_STRING)) {
                entry.combatSurvivalEventKind = entryTag.getString(TAG_COMBAT_SURVIVAL_EVENT_KIND);
            }
            entry.combatSurvivalGameTime = readOptionalLong(entryTag, TAG_COMBAT_SURVIVAL_GAME_TIME);
            entry.gearReportUnreported = entryTag.getBoolean(TAG_GEAR_REPORT_UNREPORTED);
            if (entryTag.contains(TAG_GEAR_REPORT_KIND, Tag.TAG_STRING)) {
                entry.gearReportKind = entryTag.getString(TAG_GEAR_REPORT_KIND);
            }
            entry.gearReportUsedInCombat = entryTag.getBoolean(TAG_GEAR_REPORT_USED_IN_COMBAT);
            entry.gearReportGameTime = readOptionalLong(entryTag, TAG_GEAR_REPORT_GAME_TIME);
            entry.recruitmentFollowupUnreported = entryTag.getBoolean(TAG_RECRUITMENT_FOLLOWUP_UNREPORTED);
            if (entryTag.contains(TAG_RECRUITMENT_FOLLOWUP_SCENARIO, Tag.TAG_STRING)) {
                entry.recruitmentFollowupScenario = entryTag.getString(TAG_RECRUITMENT_FOLLOWUP_SCENARIO);
            }
            entry.recruitmentFollowupGameTime = readOptionalLong(entryTag, TAG_RECRUITMENT_FOLLOWUP_GAME_TIME);
            if (entryTag.contains(TAG_RECRUITMENT_MEMORY_SCENARIO, Tag.TAG_STRING)) {
                entry.recruitmentMemoryScenario = entryTag.getString(TAG_RECRUITMENT_MEMORY_SCENARIO);
            }
            if (entryTag.contains(TAG_RECRUITMENT_MEMORY_BIOME, Tag.TAG_STRING)) {
                entry.recruitmentMemoryBiome = entryTag.getString(TAG_RECRUITMENT_MEMORY_BIOME);
            }
            entry.recruitmentMemoryDistance = entryTag.getInt(TAG_RECRUITMENT_MEMORY_DISTANCE);
            entry.recruitmentMemoryGameTime = readOptionalLong(entryTag, TAG_RECRUITMENT_MEMORY_GAME_TIME);
            if (entryTag.contains(TAG_GIFT_ADVICE_ITEM_ID, Tag.TAG_STRING)) {
                entry.giftAdviceItemId = entryTag.getString(TAG_GIFT_ADVICE_ITEM_ID);
            }
            if (entryTag.contains(TAG_GIFT_ADVICE_ITEM_NAME, Tag.TAG_STRING)) {
                entry.giftAdviceItemName = entryTag.getString(TAG_GIFT_ADVICE_ITEM_NAME);
            }
            if (entryTag.contains(TAG_GIFT_ADVICE_TARGET_PROFESSION, Tag.TAG_STRING)) {
                entry.giftAdviceTargetProfession = entryTag.getString(TAG_GIFT_ADVICE_TARGET_PROFESSION);
            }
            entry.giftAdviceResultUnreported = entryTag.getBoolean(TAG_GIFT_ADVICE_RESULT_UNREPORTED);
            if (entryTag.contains(TAG_GIFT_ADVICE_RESULT_PROFESSION, Tag.TAG_STRING)) {
                entry.giftAdviceResultProfession = entryTag.getString(TAG_GIFT_ADVICE_RESULT_PROFESSION);
            }
            if (entryTag.contains(TAG_GIFT_ADVICE_RESULT_PROFESSION_NAME, Tag.TAG_STRING)) {
                entry.giftAdviceResultProfessionName = entryTag.getString(TAG_GIFT_ADVICE_RESULT_PROFESSION_NAME);
            }
            if (entryTag.contains(TAG_GIFT_ADVICE_RESULT_VILLAGER_NAME, Tag.TAG_STRING)) {
                entry.giftAdviceResultVillagerName = entryTag.getString(TAG_GIFT_ADVICE_RESULT_VILLAGER_NAME);
            }
            entry.giftAdviceResultLiked = entryTag.getBoolean(TAG_GIFT_ADVICE_RESULT_LIKED);
            entry.giftAdviceResultGameTime = readOptionalLong(entryTag, TAG_GIFT_ADVICE_RESULT_GAME_TIME);
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
            ListTag storyHints = entryTag.getList(TAG_STORY_HINTS, Tag.TAG_COMPOUND);
            for (Tag rawHint : storyHints) {
                if (!(rawHint instanceof CompoundTag hintTag)) {
                    continue;
                }
                ResourceLocation dimension = ResourceLocation.tryParse(hintTag.getString(TAG_DIMENSION));
                ResourceLocation targetId = ResourceLocation.tryParse(hintTag.getString(TAG_TARGET));
                if (dimension == null || targetId == null) {
                    continue;
                }
                VillagerInteractionTracker.StoryHintKind kind;
                try {
                    kind = VillagerInteractionTracker.StoryHintKind.valueOf(hintTag.getString(TAG_KIND));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                entry.storyHints.add(new StoryHintMemory(
                        dimension,
                        kind,
                        targetId,
                        hintTag.getString(TAG_TARGET_NAME),
                        new BlockPos(hintTag.getInt(TAG_TARGET_X), hintTag.getInt(TAG_TARGET_Y), hintTag.getInt(TAG_TARGET_Z)),
                        readOptionalLong(hintTag, TAG_EXPIRES_AT),
                        hintTag.getBoolean(TAG_FOUND),
                        hintTag.getBoolean(TAG_REPORTED)
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
                entryTag.putBoolean(TAG_COMBAT_SURVIVAL_UNREPORTED, playerEntry.getValue().combatSurvivalUnreported);
                if (playerEntry.getValue().combatSurvivalEventKind != null && !playerEntry.getValue().combatSurvivalEventKind.isBlank()) {
                    entryTag.putString(TAG_COMBAT_SURVIVAL_EVENT_KIND, playerEntry.getValue().combatSurvivalEventKind);
                }
                entryTag.putLong(TAG_COMBAT_SURVIVAL_GAME_TIME, playerEntry.getValue().combatSurvivalGameTime);
                entryTag.putBoolean(TAG_GEAR_REPORT_UNREPORTED, playerEntry.getValue().gearReportUnreported);
                if (playerEntry.getValue().gearReportKind != null && !playerEntry.getValue().gearReportKind.isBlank()) {
                    entryTag.putString(TAG_GEAR_REPORT_KIND, playerEntry.getValue().gearReportKind);
                }
                entryTag.putBoolean(TAG_GEAR_REPORT_USED_IN_COMBAT, playerEntry.getValue().gearReportUsedInCombat);
                entryTag.putLong(TAG_GEAR_REPORT_GAME_TIME, playerEntry.getValue().gearReportGameTime);
                entryTag.putBoolean(TAG_RECRUITMENT_FOLLOWUP_UNREPORTED, playerEntry.getValue().recruitmentFollowupUnreported);
                if (playerEntry.getValue().recruitmentFollowupScenario != null && !playerEntry.getValue().recruitmentFollowupScenario.isBlank()) {
                    entryTag.putString(TAG_RECRUITMENT_FOLLOWUP_SCENARIO, playerEntry.getValue().recruitmentFollowupScenario);
                }
                entryTag.putLong(TAG_RECRUITMENT_FOLLOWUP_GAME_TIME, playerEntry.getValue().recruitmentFollowupGameTime);
                if (playerEntry.getValue().recruitmentMemoryScenario != null && !playerEntry.getValue().recruitmentMemoryScenario.isBlank()) {
                    entryTag.putString(TAG_RECRUITMENT_MEMORY_SCENARIO, playerEntry.getValue().recruitmentMemoryScenario);
                }
                if (playerEntry.getValue().recruitmentMemoryBiome != null && !playerEntry.getValue().recruitmentMemoryBiome.isBlank()) {
                    entryTag.putString(TAG_RECRUITMENT_MEMORY_BIOME, playerEntry.getValue().recruitmentMemoryBiome);
                }
                entryTag.putInt(TAG_RECRUITMENT_MEMORY_DISTANCE, playerEntry.getValue().recruitmentMemoryDistance);
                entryTag.putLong(TAG_RECRUITMENT_MEMORY_GAME_TIME, playerEntry.getValue().recruitmentMemoryGameTime);
                if (playerEntry.getValue().giftAdviceItemId != null && !playerEntry.getValue().giftAdviceItemId.isBlank()) {
                    entryTag.putString(TAG_GIFT_ADVICE_ITEM_ID, playerEntry.getValue().giftAdviceItemId);
                }
                if (playerEntry.getValue().giftAdviceItemName != null && !playerEntry.getValue().giftAdviceItemName.isBlank()) {
                    entryTag.putString(TAG_GIFT_ADVICE_ITEM_NAME, playerEntry.getValue().giftAdviceItemName);
                }
                if (playerEntry.getValue().giftAdviceTargetProfession != null && !playerEntry.getValue().giftAdviceTargetProfession.isBlank()) {
                    entryTag.putString(TAG_GIFT_ADVICE_TARGET_PROFESSION, playerEntry.getValue().giftAdviceTargetProfession);
                }
                entryTag.putBoolean(TAG_GIFT_ADVICE_RESULT_UNREPORTED, playerEntry.getValue().giftAdviceResultUnreported);
                if (playerEntry.getValue().giftAdviceResultProfession != null && !playerEntry.getValue().giftAdviceResultProfession.isBlank()) {
                    entryTag.putString(TAG_GIFT_ADVICE_RESULT_PROFESSION, playerEntry.getValue().giftAdviceResultProfession);
                }
                if (playerEntry.getValue().giftAdviceResultProfessionName != null && !playerEntry.getValue().giftAdviceResultProfessionName.isBlank()) {
                    entryTag.putString(TAG_GIFT_ADVICE_RESULT_PROFESSION_NAME, playerEntry.getValue().giftAdviceResultProfessionName);
                }
                if (playerEntry.getValue().giftAdviceResultVillagerName != null && !playerEntry.getValue().giftAdviceResultVillagerName.isBlank()) {
                    entryTag.putString(TAG_GIFT_ADVICE_RESULT_VILLAGER_NAME, playerEntry.getValue().giftAdviceResultVillagerName);
                }
                entryTag.putBoolean(TAG_GIFT_ADVICE_RESULT_LIKED, playerEntry.getValue().giftAdviceResultLiked);
                entryTag.putLong(TAG_GIFT_ADVICE_RESULT_GAME_TIME, playerEntry.getValue().giftAdviceResultGameTime);
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
                ListTag storyHints = new ListTag();
                for (StoryHintMemory storyHint : playerEntry.getValue().storyHints) {
                    CompoundTag hintTag = new CompoundTag();
                    hintTag.putString(TAG_DIMENSION, storyHint.dimension().toString());
                    hintTag.putString(TAG_KIND, storyHint.kind().name());
                    hintTag.putString(TAG_TARGET, storyHint.targetId().toString());
                    hintTag.putString(TAG_TARGET_NAME, storyHint.targetName());
                    hintTag.putInt(TAG_TARGET_X, storyHint.targetPos().getX());
                    hintTag.putInt(TAG_TARGET_Y, storyHint.targetPos().getY());
                    hintTag.putInt(TAG_TARGET_Z, storyHint.targetPos().getZ());
                    hintTag.putLong(TAG_EXPIRES_AT, storyHint.expiresAtGameTime());
                    hintTag.putBoolean(TAG_FOUND, storyHint.found());
                    hintTag.putBoolean(TAG_REPORTED, storyHint.reported());
                    storyHints.add(hintTag);
                }
                entryTag.put(TAG_STORY_HINTS, storyHints);
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

    public void rememberStoryHint(
            UUID villagerId,
            UUID playerId,
            ResourceLocation dimension,
            VillagerInteractionTracker.StoryHintKind kind,
            ResourceLocation targetId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime,
            long gameTime) {
        InteractionEntry entry = getOrCreate(villagerId, playerId);
        if (entry.hasOpenStoryHint(dimension, kind, targetId, targetPos, gameTime)) {
            return;
        }
        entry.storyHints.add(new StoryHintMemory(
                dimension,
                kind,
                targetId,
                targetName == null ? "" : targetName,
                targetPos.immutable(),
                expiresAtGameTime,
                false,
                false
        ));
        entry.pruneStoryHints();
    }

    public List<VillagerInteractionTracker.StoryHintReport> markStoryHintDiscoveriesNear(
            UUID playerId,
            ResourceLocation dimension,
            ResourceLocation currentBiomeId,
            double x,
            double z,
            double radiusSqr,
            long gameTime) {
        List<VillagerInteractionTracker.StoryHintReport> discoveries = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, InteractionEntry>> villagerEntry : this.entries.entrySet()) {
            Map<UUID, InteractionEntry> playerEntries = villagerEntry.getValue();
            InteractionEntry entry = playerEntries.get(playerId);
            if (entry == null) {
                continue;
            }
            discoveries.addAll(entry.markStoryHintDiscoveriesNear(
                    villagerEntry.getKey(),
                    dimension,
                    currentBiomeId,
                    x,
                    z,
                    radiusSqr,
                    gameTime
            ));
        }
        return discoveries;
    }

    public VillagerInteractionTracker.CartographerMapReport unreportedCartographerMapDiscovery(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).unreportedCartographerMapDiscovery(villagerId);
    }

    public VillagerInteractionTracker.CartographerMapReport claimUnreportedCartographerMapDiscovery(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).claimUnreportedCartographerMapDiscovery(villagerId);
    }

    public VillagerInteractionTracker.StoryHintReport unreportedStoryHintDiscovery(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).unreportedStoryHintDiscovery(villagerId);
    }

    public VillagerInteractionTracker.StoryHintReport claimUnreportedStoryHintDiscovery(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).claimUnreportedStoryHintDiscovery(villagerId);
    }

    public void rememberCombatSurvivalReport(UUID villagerId, UUID playerId, String eventKind, long gameTime) {
        getOrCreate(villagerId, playerId).rememberCombatSurvivalReport(eventKind, gameTime);
    }

    public VillagerInteractionTracker.CombatSurvivalReport unreportedCombatSurvivalReport(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).unreportedCombatSurvivalReport(villagerId);
    }

    public VillagerInteractionTracker.CombatSurvivalReport claimUnreportedCombatSurvivalReport(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).claimUnreportedCombatSurvivalReport(villagerId);
    }

    public void rememberGearReport(UUID villagerId, UUID playerId, String gearKind, long gameTime) {
        getOrCreate(villagerId, playerId).rememberGearReport(gearKind, gameTime);
    }

    public VillagerInteractionTracker.GearReport unreportedGearReport(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).unreportedGearReport(villagerId);
    }

    public VillagerInteractionTracker.GearReport claimUnreportedGearReport(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).claimUnreportedGearReport(villagerId);
    }

    public boolean markGearReportsUsedInCombat(UUID villagerId, boolean weaponEquipped, boolean armorEquipped) {
        Map<UUID, InteractionEntry> playerEntries = this.entries.get(villagerId);
        if (playerEntries == null) {
            return false;
        }
        boolean changed = false;
        for (InteractionEntry entry : playerEntries.values()) {
            changed |= entry.markGearReportUsedInCombat(weaponEquipped, armorEquipped);
        }
        return changed;
    }

    public void rememberRecruitmentFollowup(UUID villagerId, UUID playerId, String scenario, long gameTime) {
        getOrCreate(villagerId, playerId).rememberRecruitmentFollowup(scenario, gameTime);
    }

    public VillagerInteractionTracker.RecruitmentFollowupReport unreportedRecruitmentFollowup(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).unreportedRecruitmentFollowup(villagerId);
    }

    public VillagerInteractionTracker.RecruitmentFollowupReport claimUnreportedRecruitmentFollowup(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).claimUnreportedRecruitmentFollowup(villagerId);
    }

    public void rememberRecruitmentMemory(UUID villagerId, UUID playerId, String scenario, String biomeName, int distanceBlocks, long gameTime) {
        getOrCreate(villagerId, playerId).rememberRecruitmentMemory(scenario, biomeName, distanceBlocks, gameTime);
    }

    public VillagerInteractionTracker.RecruitmentMemory recruitmentMemory(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).recruitmentMemory(villagerId);
    }

    public void rememberGiftAdvice(UUID villagerId, UUID playerId, String itemId, String itemName, String targetProfessionKey) {
        getOrCreate(villagerId, playerId).rememberGiftAdvice(itemId, itemName, targetProfessionKey);
    }

    public boolean markGiftAdviceResult(
            UUID playerId,
            UUID testedVillagerId,
            String itemId,
            String itemName,
            String testedProfessionKey,
            String testedProfessionName,
            String testedVillagerName,
            boolean liked,
            long gameTime) {
        boolean changed = false;
        for (Map.Entry<UUID, Map<UUID, InteractionEntry>> villagerEntry : this.entries.entrySet()) {
            if (villagerEntry.getKey().equals(testedVillagerId)) {
                continue;
            }
            InteractionEntry entry = villagerEntry.getValue().get(playerId);
            if (entry != null && entry.markGiftAdviceResult(
                    itemId,
                    itemName,
                    testedProfessionKey,
                    testedProfessionName,
                    testedVillagerName,
                    liked,
                    gameTime)) {
                changed = true;
            }
        }
        return changed;
    }

    public VillagerInteractionTracker.GiftAdviceResultReport unreportedGiftAdviceResult(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).unreportedGiftAdviceResult(villagerId);
    }

    public VillagerInteractionTracker.GiftAdviceResultReport claimUnreportedGiftAdviceResult(UUID villagerId, UUID playerId) {
        return getOrCreate(villagerId, playerId).claimUnreportedGiftAdviceResult(villagerId);
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
        private boolean combatSurvivalUnreported;
        private String combatSurvivalEventKind;
        private long combatSurvivalGameTime = Long.MIN_VALUE;
        private boolean gearReportUnreported;
        private String gearReportKind;
        private boolean gearReportUsedInCombat;
        private long gearReportGameTime = Long.MIN_VALUE;
        private boolean recruitmentFollowupUnreported;
        private String recruitmentFollowupScenario;
        private long recruitmentFollowupGameTime = Long.MIN_VALUE;
        private String recruitmentMemoryScenario;
        private String recruitmentMemoryBiome;
        private int recruitmentMemoryDistance;
        private long recruitmentMemoryGameTime = Long.MIN_VALUE;
        private String giftAdviceItemId;
        private String giftAdviceItemName;
        private String giftAdviceTargetProfession;
        private boolean giftAdviceResultUnreported;
        private String giftAdviceResultProfession;
        private String giftAdviceResultProfessionName;
        private String giftAdviceResultVillagerName;
        private boolean giftAdviceResultLiked;
        private long giftAdviceResultGameTime = Long.MIN_VALUE;
        private DialogueRequestType consecutiveRequestType;
        private int consecutiveRequestCount;
        private final ArrayDeque<String> recentDialogueIds = new ArrayDeque<>();
        private final Map<DialogueRequestType, Long> lastReputationByRequestType = new EnumMap<>(DialogueRequestType.class);
        private final Map<DialogueRequestType, Long> lastDialogueByRequestType = new EnumMap<>(DialogueRequestType.class);
        private final Map<DialogueRequestType, RequestUseWindow> requestUseWindows = new EnumMap<>(DialogueRequestType.class);
        private final List<CartographerMapMemory> cartographerMaps = new ArrayList<>();
        private final List<StoryHintMemory> storyHints = new ArrayList<>();

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

        public void rememberCombatSurvivalReport(String eventKind, long gameTime) {
            this.combatSurvivalUnreported = true;
            this.combatSurvivalEventKind = eventKind == null || eventKind.isBlank() ? "combat" : eventKind;
            this.combatSurvivalGameTime = gameTime;
        }

        public VillagerInteractionTracker.CombatSurvivalReport unreportedCombatSurvivalReport(UUID villagerId) {
            if (!this.combatSurvivalUnreported) {
                return null;
            }
            return new VillagerInteractionTracker.CombatSurvivalReport(
                    villagerId,
                    this.combatSurvivalEventKind == null || this.combatSurvivalEventKind.isBlank() ? "combat" : this.combatSurvivalEventKind,
                    this.combatSurvivalGameTime
            );
        }

        public VillagerInteractionTracker.CombatSurvivalReport claimUnreportedCombatSurvivalReport(UUID villagerId) {
            VillagerInteractionTracker.CombatSurvivalReport report = unreportedCombatSurvivalReport(villagerId);
            this.combatSurvivalUnreported = false;
            return report;
        }

        public void rememberGearReport(String gearKind, long gameTime) {
            this.gearReportUnreported = true;
            this.gearReportKind = gearKind == null || gearKind.isBlank() ? "gear" : gearKind;
            this.gearReportUsedInCombat = false;
            this.gearReportGameTime = gameTime;
        }

        public VillagerInteractionTracker.GearReport unreportedGearReport(UUID villagerId) {
            if (!this.gearReportUnreported) {
                return null;
            }
            return new VillagerInteractionTracker.GearReport(
                    villagerId,
                    this.gearReportKind == null || this.gearReportKind.isBlank() ? "gear" : this.gearReportKind,
                    this.gearReportUsedInCombat,
                    this.gearReportGameTime
            );
        }

        public VillagerInteractionTracker.GearReport claimUnreportedGearReport(UUID villagerId) {
            VillagerInteractionTracker.GearReport report = unreportedGearReport(villagerId);
            this.gearReportUnreported = false;
            return report;
        }

        public boolean markGearReportUsedInCombat(boolean weaponEquipped, boolean armorEquipped) {
            if (!this.gearReportUnreported || this.gearReportUsedInCombat || !gearReportMatchesEquippedGear(weaponEquipped, armorEquipped)) {
                return false;
            }
            this.gearReportUsedInCombat = true;
            return true;
        }

        private boolean gearReportMatchesEquippedGear(boolean weaponEquipped, boolean armorEquipped) {
            String gearKind = this.gearReportKind == null ? "" : this.gearReportKind;
            return switch (gearKind) {
                case "weapon" -> weaponEquipped;
                case "armor" -> armorEquipped;
                default -> weaponEquipped || armorEquipped;
            };
        }

        public void rememberRecruitmentFollowup(String scenario, long gameTime) {
            this.recruitmentFollowupUnreported = true;
            this.recruitmentFollowupScenario = scenario == null || scenario.isBlank() ? "safe" : scenario;
            this.recruitmentFollowupGameTime = gameTime;
        }

        public VillagerInteractionTracker.RecruitmentFollowupReport unreportedRecruitmentFollowup(UUID villagerId) {
            if (!this.recruitmentFollowupUnreported) {
                return null;
            }
            return new VillagerInteractionTracker.RecruitmentFollowupReport(
                    villagerId,
                    this.recruitmentFollowupScenario == null || this.recruitmentFollowupScenario.isBlank()
                            ? "safe"
                            : this.recruitmentFollowupScenario,
                    this.recruitmentFollowupGameTime
            );
        }

        public VillagerInteractionTracker.RecruitmentFollowupReport claimUnreportedRecruitmentFollowup(UUID villagerId) {
            VillagerInteractionTracker.RecruitmentFollowupReport report = unreportedRecruitmentFollowup(villagerId);
            this.recruitmentFollowupUnreported = false;
            return report;
        }

        public void rememberRecruitmentMemory(String scenario, String biomeName, int distanceBlocks, long gameTime) {
            this.recruitmentMemoryScenario = scenario == null || scenario.isBlank() ? "safe" : scenario;
            this.recruitmentMemoryBiome = biomeName == null || biomeName.isBlank() ? "the wilds" : biomeName;
            this.recruitmentMemoryDistance = Math.max(0, distanceBlocks);
            this.recruitmentMemoryGameTime = gameTime;
        }

        public VillagerInteractionTracker.RecruitmentMemory recruitmentMemory(UUID villagerId) {
            if (this.recruitmentMemoryGameTime == Long.MIN_VALUE) {
                return null;
            }
            return new VillagerInteractionTracker.RecruitmentMemory(
                    villagerId,
                    this.recruitmentMemoryScenario == null || this.recruitmentMemoryScenario.isBlank()
                            ? "safe"
                            : this.recruitmentMemoryScenario,
                    this.recruitmentMemoryBiome == null || this.recruitmentMemoryBiome.isBlank()
                            ? "the wilds"
                            : this.recruitmentMemoryBiome,
                    this.recruitmentMemoryDistance,
                    this.recruitmentMemoryGameTime
            );
        }

        public void rememberGiftAdvice(String itemId, String itemName, String targetProfessionKey) {
            this.giftAdviceItemId = itemId;
            this.giftAdviceItemName = itemName;
            this.giftAdviceTargetProfession = targetProfessionKey == null || targetProfessionKey.isBlank()
                    ? "*"
                    : targetProfessionKey;
        }

        public boolean markGiftAdviceResult(
                String itemId,
                String itemName,
                String testedProfessionKey,
                String testedProfessionName,
                String testedVillagerName,
                boolean liked,
                long gameTime) {
            if (this.giftAdviceItemId == null
                    || this.giftAdviceItemId.isBlank()
                    || this.giftAdviceTargetProfession == null
                    || this.giftAdviceTargetProfession.isBlank()
                    || !this.giftAdviceItemId.equals(itemId)
                    || !giftAdviceMatchesProfession(testedProfessionKey)) {
                return false;
            }
            this.giftAdviceItemId = null;
            this.giftAdviceItemName = null;
            this.giftAdviceTargetProfession = null;
            this.giftAdviceResultUnreported = true;
            this.giftAdviceItemId = itemId;
            this.giftAdviceItemName = itemName;
            this.giftAdviceResultProfession = testedProfessionKey;
            this.giftAdviceResultProfessionName = testedProfessionName;
            this.giftAdviceResultVillagerName = testedVillagerName;
            this.giftAdviceResultLiked = liked;
            this.giftAdviceResultGameTime = gameTime;
            return true;
        }

        public VillagerInteractionTracker.GiftAdviceResultReport unreportedGiftAdviceResult(UUID villagerId) {
            if (!this.giftAdviceResultUnreported) {
                return null;
            }
            return new VillagerInteractionTracker.GiftAdviceResultReport(
                    villagerId,
                    this.giftAdviceItemId,
                    this.giftAdviceItemName,
                    this.giftAdviceResultProfession,
                    this.giftAdviceResultProfessionName,
                    this.giftAdviceResultVillagerName,
                    this.giftAdviceResultLiked,
                    this.giftAdviceResultGameTime
            );
        }

        public VillagerInteractionTracker.GiftAdviceResultReport claimUnreportedGiftAdviceResult(UUID villagerId) {
            VillagerInteractionTracker.GiftAdviceResultReport report = unreportedGiftAdviceResult(villagerId);
            this.giftAdviceResultUnreported = false;
            return report;
        }

        private boolean giftAdviceMatchesProfession(String testedProfessionKey) {
            return this.giftAdviceTargetProfession == null
                    || this.giftAdviceTargetProfession.isBlank()
                    || this.giftAdviceTargetProfession.equals("*")
                    || this.giftAdviceTargetProfession.equals(testedProfessionKey);
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

        private List<VillagerInteractionTracker.StoryHintReport> markStoryHintDiscoveriesNear(
                UUID villagerId,
                ResourceLocation dimension,
                ResourceLocation currentBiomeId,
                double x,
                double z,
                double radiusSqr,
                long gameTime) {
            List<VillagerInteractionTracker.StoryHintReport> discoveries = new ArrayList<>();
            for (int index = 0; index < this.storyHints.size(); index++) {
                StoryHintMemory storyHint = this.storyHints.get(index);
                if (storyHint.reported()
                        || storyHint.found()
                        || storyHint.expiresAtGameTime() <= gameTime
                        || !storyHint.dimension().equals(dimension)
                        || !storyHint.matchesCurrentPlace(currentBiomeId)) {
                    continue;
                }
                double dx = x - (storyHint.targetPos().getX() + 0.5D);
                double dz = z - (storyHint.targetPos().getZ() + 0.5D);
                if (dx * dx + dz * dz <= radiusSqr) {
                    this.storyHints.set(index, storyHint.withFound(true));
                    discoveries.add(storyHint.toReport(villagerId));
                }
            }
            return discoveries;
        }

        private VillagerInteractionTracker.StoryHintReport unreportedStoryHintDiscovery(UUID villagerId) {
            for (int index = this.storyHints.size() - 1; index >= 0; index--) {
                StoryHintMemory storyHint = this.storyHints.get(index);
                if (storyHint.found() && !storyHint.reported()) {
                    return storyHint.toReport(villagerId);
                }
            }
            return null;
        }

        private VillagerInteractionTracker.StoryHintReport claimUnreportedStoryHintDiscovery(UUID villagerId) {
            for (int index = this.storyHints.size() - 1; index >= 0; index--) {
                StoryHintMemory storyHint = this.storyHints.get(index);
                if (storyHint.found() && !storyHint.reported()) {
                    this.storyHints.set(index, storyHint.withReported(true));
                    return storyHint.toReport(villagerId);
                }
            }
            return null;
        }

        private boolean hasOpenStoryHint(
                ResourceLocation dimension,
                VillagerInteractionTracker.StoryHintKind kind,
                ResourceLocation targetId,
                BlockPos targetPos,
                long gameTime) {
            for (StoryHintMemory storyHint : this.storyHints) {
                if (!storyHint.reported()
                        && storyHint.expiresAtGameTime() > gameTime
                        && storyHint.dimension().equals(dimension)
                        && storyHint.kind() == kind
                        && storyHint.targetId().equals(targetId)
                        && storyHint.targetPos().equals(targetPos)) {
                    return true;
                }
            }
            return false;
        }

        private void pruneCartographerMaps() {
            while (this.cartographerMaps.size() > MAX_CARTOGRAPHER_MAPS) {
                int reportedIndex = firstReportedCartographerMapIndex();
                this.cartographerMaps.remove(reportedIndex >= 0 ? reportedIndex : 0);
            }
        }

        private void pruneStoryHints() {
            while (this.storyHints.size() > MAX_STORY_HINTS) {
                int reportedIndex = firstReportedStoryHintIndex();
                this.storyHints.remove(reportedIndex >= 0 ? reportedIndex : 0);
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

        private int firstReportedStoryHintIndex() {
            for (int index = 0; index < this.storyHints.size(); index++) {
                if (this.storyHints.get(index).reported()) {
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

    private record StoryHintMemory(
            ResourceLocation dimension,
            VillagerInteractionTracker.StoryHintKind kind,
            ResourceLocation targetId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime,
            boolean found,
            boolean reported
    ) {
        private boolean matchesCurrentPlace(ResourceLocation currentBiomeId) {
            return this.kind != VillagerInteractionTracker.StoryHintKind.BIOME
                    || this.targetId.equals(currentBiomeId);
        }

        private StoryHintMemory withFound(boolean found) {
            return new StoryHintMemory(
                    this.dimension,
                    this.kind,
                    this.targetId,
                    this.targetName,
                    this.targetPos,
                    this.expiresAtGameTime,
                    found,
                    this.reported
            );
        }

        private StoryHintMemory withReported(boolean reported) {
            return new StoryHintMemory(
                    this.dimension,
                    this.kind,
                    this.targetId,
                    this.targetName,
                    this.targetPos,
                    this.expiresAtGameTime,
                    this.found,
                    reported
            );
        }

        private VillagerInteractionTracker.StoryHintReport toReport(UUID villagerId) {
            return new VillagerInteractionTracker.StoryHintReport(
                    villagerId,
                    this.dimension,
                    this.kind,
                    this.targetId,
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
