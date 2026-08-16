package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public sealed interface DialogueCondition permits DialogueCondition.Invalid, DialogueCondition.AllOf, DialogueCondition.AnyOf,
        DialogueCondition.Not, DialogueCondition.Reputation, DialogueCondition.Memory,
        DialogueCondition.Family, DialogueCondition.Relationship, DialogueCondition.RecruitmentMemory,
        DialogueCondition.VillagerAge, DialogueCondition.SocialAttribute, DialogueCondition.Skill,
        DialogueCondition.VillagerLevel, DialogueCondition.Quest, DialogueCondition.QuestFact,
        DialogueCondition.SelectedChoice, DialogueCondition.StageHistory,
        DialogueCondition.PlayerItem, DialogueCondition.VillagerEquipment,
        DialogueCondition.Biome, DialogueCondition.Dimension, DialogueCondition.Advancement,
        DialogueCondition.Scoreboard, DialogueCondition.NearbyEntity, DialogueCondition.Village,
        DialogueCondition.Mood, DialogueCondition.Weather, DialogueCondition.Time,
        DialogueCondition.TriggerPayload {

    boolean matches(DialogueContext context);

    default boolean matches(DialogueContext context, QuestTriggerContext triggerContext) {
        return matches(context);
    }

    int specificityScore();

    static boolean matchesAll(DialogueContext context, List<DialogueCondition> conditions) {
        return ConditionRegistry.matchesAll(context, conditions);
    }

    static boolean matchesAll(
            DialogueContext context,
            QuestTriggerContext triggerContext,
            List<DialogueCondition> conditions) {
        return ConditionRegistry.matchesAll(context, triggerContext, conditions);
    }

    static ConditionEvaluationTrace trace(DialogueContext context, DialogueCondition condition) {
        return ConditionRegistry.trace(context, condition);
    }

    static Optional<ConditionEvaluationTrace> firstUnmatched(
            DialogueContext context,
            List<DialogueCondition> conditions) {
        return ConditionRegistry.traceAll(context, conditions).stream()
                .map(ConditionEvaluationTrace::firstUnmatched)
                .flatMap(Optional::stream)
                .findFirst();
    }

    static List<ConditionTypeDescriptor> descriptors() {
        return ConditionRegistry.descriptors();
    }

    static String canonicalTypeId(String type) {
        return ConditionRegistry.canonicalTypeId(type);
    }

    static String canonicalTypeId(DialogueCondition condition) {
        return ConditionRegistry.canonicalTypeId(condition);
    }

    static Set<ConditionCapability> capabilities(DialogueCondition condition) {
        return ConditionRegistry.capabilities(condition);
    }

    static List<DialogueCondition> readList(ResourceLocation location, String context, JsonObject entry) {
        return readList(location, context, entry, null);
    }

    static List<DialogueCondition> readList(
            ResourceLocation location,
            String context,
            JsonObject entry,
            ResourceLocation defaultQuestId) {
        List<DialogueCondition> conditions = new ArrayList<>();
        conditions.addAll(readConditionArray(location, context, entry, "conditions", defaultQuestId));

        JsonObject availability = entry == null ? null : readObject(entry, "availability");
        if (availability != null) {
            conditions.addAll(readConditionArray(location, context + " availability", availability, "conditions", defaultQuestId));
        }
        JsonObject availableWhen = entry == null ? null : readObject(entry, "available_when");
        if (availableWhen != null) {
            conditions.addAll(readConditionArray(location, context + " available_when", availableWhen, "conditions", defaultQuestId));
        } else if (entry != null && entry.has("available_when")) {
            conditions.addAll(readConditionArray(location, context + " available_when", entry, "available_when", defaultQuestId));
        }
        return List.copyOf(conditions);
    }

    private static List<DialogueCondition> readConditionArray(
            ResourceLocation location,
            String context,
            JsonObject entry,
            String key,
            ResourceLocation defaultQuestId) {
        if (entry == null) {
            return List.of();
        }
        JsonElement element = entry.get("conditions");
        if (!"conditions".equals(key)) {
            element = entry.get(key);
        }
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            warnInvalid(location, context, "conditions must be an array of condition objects.");
            return List.of(new Invalid("conditions must be an array"));
        }

        List<DialogueCondition> conditions = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            read(location, context + " conditions[" + index + "]", child, defaultQuestId).ifPresent(conditions::add);
            index++;
        }
        return List.copyOf(conditions);
    }

    private static Optional<DialogueCondition> read(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || !element.isJsonObject()) {
            warnInvalid(location, context, "condition must be an object.");
            return Optional.of(new Invalid("condition must be an object"));
        }

        JsonObject condition = element.getAsJsonObject();
        return ConditionRegistry.read(location, context, condition, defaultQuestId)
                .or(() -> Optional.of(new Invalid("condition could not be parsed")));
    }

    private static Optional<List<DialogueCondition>> readChildren(
            ResourceLocation location,
            String context,
            JsonObject condition,
            ResourceLocation defaultQuestId) {
        JsonElement element = condition.get("conditions");
        if (element == null || !element.isJsonArray()) {
            warnInvalid(location, context, "compound condition must contain a conditions array.");
            return Optional.empty();
        }

        List<DialogueCondition> children = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            read(location, context + ".conditions[" + index + "]", child, defaultQuestId).ifPresent(children::add);
            index++;
        }
        return children.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(children));
    }

    private static Reputation readReputation(JsonObject condition) {
        EnumSet<VillagerReputationLevel> levels = EnumSet.noneOf(VillagerReputationLevel.class);
        for (String value : readStringList(condition, "level")) {
            readEnum(value, VillagerReputationLevel.class).ifPresent(levels::add);
        }
        for (String value : readStringList(condition, "levels")) {
            readEnum(value, VillagerReputationLevel.class).ifPresent(levels::add);
        }
        for (String value : readStringList(condition, "reputation_level")) {
            readEnum(value, VillagerReputationLevel.class).ifPresent(levels::add);
        }
        for (String value : readStringList(condition, "reputation_levels")) {
            readEnum(value, VillagerReputationLevel.class).ifPresent(levels::add);
        }
        Integer min = readNullableInt(condition, "min");
        if (min == null) {
            min = readNullableInt(condition, "min_reputation");
        }
        Integer max = readNullableInt(condition, "max");
        if (max == null) {
            max = readNullableInt(condition, "max_reputation");
        }
        return new Reputation(Set.copyOf(levels), min, max);
    }

    private static Optional<DialogueCondition> readMemory(ResourceLocation location, String context, JsonObject condition) {
        String kind = readString(condition, "kind").toLowerCase(Locale.ROOT);
        if (!kind.isBlank()) {
            return switch (kind) {
                case "recent_broken_bed" -> Optional.of(new Memory(Set.of(), MemorySource.ANY, true, MemoryKind.RECENT_BROKEN_BED));
                case "recent_direct_hit" -> Optional.of(new Memory(Set.of(), MemorySource.ANY, true, MemoryKind.RECENT_DIRECT_HIT));
                case "gear_report_used_in_combat" -> Optional.of(new Memory(Set.of(), MemorySource.ANY, true, MemoryKind.GEAR_REPORT_USED_IN_COMBAT));
                case "gear_report_unused_in_combat" -> Optional.of(new Memory(Set.of(), MemorySource.ANY, true, MemoryKind.GEAR_REPORT_UNUSED_IN_COMBAT));
                case "recruitment_memory" -> Optional.of(new Memory(Set.of(), MemorySource.ANY, true, MemoryKind.RECRUITMENT_MEMORY));
                default -> {
                    warnInvalid(location, context, "unknown memory kind \"" + kind + "\".");
                    yield Optional.empty();
                }
            };
        }

        java.util.LinkedHashSet<ResourceLocation> tags = new java.util.LinkedHashSet<>();
        for (String value : readStringList(condition, "tag")) {
            VillageEventMemory.parseTagId(value).ifPresentOrElse(
                    tags::add,
                    () -> warnInvalid(location, context, "memory condition references invalid tag \"" + value + "\"."));
        }
        for (String value : readStringList(condition, "tags")) {
            VillageEventMemory.parseTagId(value).ifPresentOrElse(
                    tags::add,
                    () -> warnInvalid(location, context, "memory condition references invalid tag \"" + value + "\"."));
        }
        if (tags.isEmpty()) {
            warnInvalid(location, context, "memory condition must define tag, tags, or kind.");
            return Optional.empty();
        }
        return Optional.of(new Memory(Set.copyOf(tags), readMemorySource(condition), readBoolean(condition, "player", true), MemoryKind.EVENT_TAG));
    }

    private static Family readFamily(JsonObject condition) {
        return new Family(readNormalizedStrings(condition, "relation", "relations"));
    }

    private static Relationship readRelationship(JsonObject condition) {
        return new Relationship(readNormalizedStrings(condition, "state", "states", "relation", "relations"));
    }

    private static RecruitmentMemory readRecruitmentMemory(JsonObject condition) {
        Set<String> scenarios = readNormalizedStrings(condition, "scenario", "scenarios");
        Set<String> biomeKeys = readBiomeKeys(condition, "biome", "biomes");
        Boolean boatTrip = readNullableBoolean(condition, "boat_trip");
        Boolean oceanCrossing = readNullableBoolean(condition, "ocean_crossing");
        Boolean swimTrip = readNullableBoolean(condition, "swim_trip");
        Boolean excludesOceanCrossing = readNullableBoolean(condition, "excludes_ocean_crossing");
        Integer minDistance = readNullableInt(condition, "min_follow_distance");
        if (minDistance == null) {
            minDistance = readNullableInt(condition, "min_recruitment_follow_distance");
        }
        return new RecruitmentMemory(scenarios, biomeKeys, minDistance, boatTrip, oceanCrossing, swimTrip, excludesOceanCrossing);
    }

    private static MemorySource readMemorySource(JsonObject condition) {
        String source = readString(condition, "source").toLowerCase(Locale.ROOT);
        return switch (source) {
            case "self", "this_villager", "villager" -> MemorySource.THIS_VILLAGER;
            case "other", "other_villager", "another_villager" -> MemorySource.OTHER_VILLAGER;
            default -> MemorySource.ANY;
        };
    }

    private static VillagerAge readVillagerAge(JsonObject condition) {
        Boolean baby = readNullableBoolean(condition, "baby");
        Boolean adult = readNullableBoolean(condition, "adult");
        return new VillagerAge(baby, adult);
    }

    private static Optional<DialogueCondition> readSocialAttribute(ResourceLocation location, String context, JsonObject condition) {
        EnumSet<VillagerSocialAttribute> attributes = EnumSet.noneOf(VillagerSocialAttribute.class);
        for (String value : readStringList(condition, "attribute")) {
            VillagerSocialAttribute attribute = VillagerSocialAttribute.bySerializedName(value);
            if (attribute != null) {
                attributes.add(attribute);
            }
        }
        for (String value : readStringList(condition, "attributes")) {
            VillagerSocialAttribute attribute = VillagerSocialAttribute.bySerializedName(value);
            if (attribute != null) {
                attributes.add(attribute);
            }
        }
        for (String value : readStringList(condition, "stat")) {
            VillagerSocialAttribute attribute = VillagerSocialAttribute.bySerializedName(value);
            if (attribute != null) {
                attributes.add(attribute);
            }
        }
        for (String value : readStringList(condition, "stats")) {
            VillagerSocialAttribute attribute = VillagerSocialAttribute.bySerializedName(value);
            if (attribute != null) {
                attributes.add(attribute);
            }
        }
        if (attributes.isEmpty()) {
            warnInvalid(location, context, "social_attribute condition must define attribute, attributes, stat, or stats.");
            return Optional.empty();
        }
        return Optional.of(new SocialAttribute(Set.copyOf(attributes), readNullableInt(condition, "min"), readNullableInt(condition, "max")));
    }

    private static Optional<DialogueCondition> readSkill(ResourceLocation location, String context, JsonObject condition) {
        EnumSet<VillagerSkill> skills = EnumSet.noneOf(VillagerSkill.class);
        for (String value : readStringList(condition, "skill")) {
            VillagerSkill skill = VillagerSkill.bySerializedName(value);
            if (skill != null) {
                skills.add(skill);
            }
        }
        for (String value : readStringList(condition, "skills")) {
            VillagerSkill skill = VillagerSkill.bySerializedName(value);
            if (skill != null) {
                skills.add(skill);
            }
        }
        if (skills.isEmpty()) {
            warnInvalid(location, context, "skill condition must define skill or skills.");
            return Optional.empty();
        }
        VillagerSkillRank minRank = VillagerSkillRank.bySerializedName(readString(condition, "min_rank"));
        VillagerSkillRank maxRank = VillagerSkillRank.bySerializedName(readString(condition, "max_rank"));
        return Optional.of(new Skill(
                Set.copyOf(skills),
                readNullableInt(condition, "min"),
                readNullableInt(condition, "max"),
                minRank,
                maxRank));
    }

    private static Optional<DialogueCondition> readVillagerLevel(ResourceLocation location, String context, JsonObject condition) {
        Set<Integer> levels = new java.util.LinkedHashSet<>();
        for (String value : readStringList(condition, "level")) {
            readVillagerLevelValue(value).ifPresent(levels::add);
        }
        for (String value : readStringList(condition, "levels")) {
            readVillagerLevelValue(value).ifPresent(levels::add);
        }
        Integer min = readVillagerLevelBound(condition, "min");
        if (min == null) {
            min = readVillagerLevelBound(condition, "min_level");
        }
        Integer max = readVillagerLevelBound(condition, "max");
        if (max == null) {
            max = readVillagerLevelBound(condition, "max_level");
        }
        if (levels.isEmpty() && min == null && max == null) {
            warnInvalid(location, context, "villager_level condition must define level, levels, min, or max.");
            return Optional.empty();
        }
        return Optional.of(new VillagerLevel(Set.copyOf(levels), min, max));
    }

    private static Optional<DialogueCondition> readQuest(
            ResourceLocation location,
            String context,
            JsonObject condition,
            ResourceLocation defaultQuestId) {
        ResourceLocation questId = defaultQuestId;
        for (String key : List.of("quest", "quest_id", "id")) {
            String value = readString(condition, key);
            if (!value.isBlank()) {
                questId = QuestIds.parse(value, location);
                break;
            }
        }
        if (questId == null) {
            warnInvalid(location, context, "quest condition must define quest or quest_id unless a default quest is available.");
            return Optional.empty();
        }
        Set<String> states = readNormalizedStrings(condition, "state", "states");
        return Optional.of(new Quest(questId, states));
    }

    private static Optional<DialogueCondition> readQuestFact(
            ResourceLocation location,
            String context,
            JsonObject condition,
            ResourceLocation defaultQuestId) {
        ResourceLocation questId = readQuestReference(location, context, condition, defaultQuestId);
        QuestFactScope scope = QuestFactScope.bySerializedName(
                readString(condition, "scope"),
                questId == null ? QuestFactScope.PLAYER : QuestFactScope.QUEST);
        Set<ResourceLocation> tags = readResourceLocationSet(location, context, condition, "tag", "tags", "fact_tag", "quest_tag");
        String key = firstNonBlank(
                readString(condition, "key"),
                firstNonBlank(
                        readString(condition, "variable"),
                        firstNonBlank(readString(condition, "counter"), readString(condition, "fact"))));
        Set<String> stageValues = readRawStringSet(condition, "stage", "stages");
        if (key.isBlank() && !stageValues.isEmpty()) {
            key = "stage";
        }
        LinkedHashSet<String> rawValues = new LinkedHashSet<>(readRawStringSet(condition, "value", "values"));
        rawValues.addAll(stageValues);
        Set<String> values = Set.copyOf(rawValues);
        Integer min = readNullableInt(condition, "min");
        Integer max = readNullableInt(condition, "max");
        if (tags.isEmpty() && key.isBlank()) {
            warnInvalid(location, context, "quest_fact condition must define tag, tags, key, variable, counter, stage, or stages.");
            return Optional.empty();
        }
        if (scope == QuestFactScope.QUEST && questId == null) {
            warnInvalid(location, context, "quest_fact condition with quest scope must define quest or have a default quest.");
            return Optional.empty();
        }
        return Optional.of(new QuestFact(scope, questId, tags, key, values, min, max));
    }

    private static Optional<DialogueCondition> readSelectedChoice(
            ResourceLocation location,
            String context,
            JsonObject condition,
            ResourceLocation defaultQuestId) {
        ResourceLocation questId = readQuestReference(location, context, condition, defaultQuestId);
        if (questId == null) {
            warnInvalid(location, context, "selected_choice condition must define quest or have a default quest.");
            return Optional.empty();
        }
        String responseId = firstNonBlank(
                readString(condition, "response"),
                firstNonBlank(readString(condition, "response_id"), readString(condition, "choice")));
        if (responseId.isBlank()) {
            warnInvalid(location, context, "selected_choice condition must define response, response_id, or choice.");
            return Optional.empty();
        }
        return Optional.of(new SelectedChoice(
                questId,
                firstNonBlank(readString(condition, "scene_path"), readString(condition, "scene")),
                responseId,
                firstNonBlank(readString(condition, "prior_stage"), readString(condition, "from_stage")),
                firstNonBlank(readString(condition, "next_stage"), readString(condition, "to_stage"))));
    }

    private static Optional<DialogueCondition> readStageHistory(
            ResourceLocation location,
            String context,
            JsonObject condition,
            ResourceLocation defaultQuestId) {
        ResourceLocation questId = readQuestReference(location, context, condition, defaultQuestId);
        if (questId == null) {
            warnInvalid(location, context, "stage_history condition must define quest or have a default quest.");
            return Optional.empty();
        }
        String stage = readString(condition, "stage");
        String priorStage = firstNonBlank(readString(condition, "prior_stage"), readString(condition, "from_stage"));
        String nextStage = firstNonBlank(readString(condition, "next_stage"), readString(condition, "to_stage"));
        if (stage.isBlank() && priorStage.isBlank() && nextStage.isBlank()) {
            warnInvalid(location, context, "stage_history condition must define stage, prior_stage, from_stage, next_stage, or to_stage.");
            return Optional.empty();
        }
        return Optional.of(new StageHistory(questId, stage, priorStage, nextStage));
    }

    private static Optional<DialogueCondition> readMood(ResourceLocation location, String context, JsonObject condition) {
        EnumSet<VillagerMood> moods = EnumSet.noneOf(VillagerMood.class);
        for (String value : readStringList(condition, "mood")) {
            readEnum(value, VillagerMood.class).ifPresent(moods::add);
        }
        for (String value : readStringList(condition, "moods")) {
            readEnum(value, VillagerMood.class).ifPresent(moods::add);
        }
        for (String value : readStringList(condition, "state")) {
            readEnum(value, VillagerMood.class).ifPresent(moods::add);
        }
        for (String value : readStringList(condition, "states")) {
            readEnum(value, VillagerMood.class).ifPresent(moods::add);
        }
        if (moods.isEmpty()) {
            warnInvalid(location, context, "mood condition must define mood, moods, state, or states.");
            return Optional.empty();
        }
        Integer min = readNullableInt(condition, "min");
        if (min == null) {
            min = readNullableInt(condition, "min_intensity");
        }
        if (min == null) {
            min = readNullableInt(condition, "min_mood_intensity");
        }
        Integer max = readNullableInt(condition, "max");
        if (max == null) {
            max = readNullableInt(condition, "max_intensity");
        }
        if (max == null) {
            max = readNullableInt(condition, "max_mood_intensity");
        }
        return Optional.of(new Mood(Set.copyOf(moods), min, max));
    }

    private static ResourceLocation readQuestReference(
            ResourceLocation location,
            String context,
            JsonObject condition,
            ResourceLocation defaultQuestId) {
        ResourceLocation questId = defaultQuestId;
        for (String key : List.of("quest", "quest_id")) {
            String value = readString(condition, key);
            if (!value.isBlank()) {
                questId = QuestIds.parse(value, location);
                break;
            }
        }
        return questId;
    }

    private static Optional<DialogueCondition> readWeather(JsonObject condition) {
        EnumSet<DialogueContext.WeatherState> states = EnumSet.noneOf(DialogueContext.WeatherState.class);
        for (String value : readStringList(condition, "state")) {
            readEnum(value, DialogueContext.WeatherState.class).ifPresent(states::add);
        }
        for (String value : readStringList(condition, "states")) {
            readEnum(value, DialogueContext.WeatherState.class).ifPresent(states::add);
        }
        for (String value : readStringList(condition, "weather")) {
            readEnum(value, DialogueContext.WeatherState.class).ifPresent(states::add);
        }
        for (String value : readStringList(condition, "weathers")) {
            readEnum(value, DialogueContext.WeatherState.class).ifPresent(states::add);
        }
        return states.isEmpty() ? Optional.empty() : Optional.of(new Weather(Set.copyOf(states)));
    }

    private static Optional<DialogueCondition> readTime(JsonObject condition) {
        EnumSet<DialogueContext.TimeOfDay> times = EnumSet.noneOf(DialogueContext.TimeOfDay.class);
        for (String value : readStringList(condition, "value")) {
            readEnum(value, DialogueContext.TimeOfDay.class).ifPresent(times::add);
        }
        for (String value : readStringList(condition, "values")) {
            readEnum(value, DialogueContext.TimeOfDay.class).ifPresent(times::add);
        }
        for (String value : readStringList(condition, "time")) {
            readEnum(value, DialogueContext.TimeOfDay.class).ifPresent(times::add);
        }
        for (String value : readStringList(condition, "times")) {
            readEnum(value, DialogueContext.TimeOfDay.class).ifPresent(times::add);
        }
        return times.isEmpty() ? Optional.empty() : Optional.of(new Time(Set.copyOf(times)));
    }

    private static Optional<DialogueCondition> readPlayerItem(
            ResourceLocation location, String context, JsonObject condition) {
        JsonObject normalized = condition.deepCopy();
        copyAlias(normalized, "item", "player_item");
        copyAlias(normalized, "items", "player_items");
        copyAlias(normalized, "item_tag", "player_item_tag");
        copyAlias(normalized, "item_tags", "player_item_tags");
        copyAlias(normalized, "slot", "player_item_slot");
        copyAlias(normalized, "slots", "player_item_slots");
        copyAlias(normalized, "enchantment", "player_item_enchantment");
        copyAlias(normalized, "enchantments", "player_item_enchantments");
        VillagerPlayerItemCondition parsed = VillagerPlayerItemCondition.read(normalized);
        if (parsed.isEmpty()) {
            warnInvalid(location, context, "player_item condition must define an item predicate.");
            return Optional.empty();
        }
        return Optional.of(new PlayerItem(parsed));
    }

    private static Optional<DialogueCondition> readVillagerEquipment(
            ResourceLocation location, String context, JsonObject condition) {
        Boolean armed = readNullableBoolean(condition, "armed");
        Boolean unarmed = readNullableBoolean(condition, "unarmed");
        VillagerEquipmentCondition parsed = armed != null || unarmed != null
                ? new VillagerEquipmentCondition(Boolean.TRUE.equals(unarmed) || Boolean.FALSE.equals(armed),
                        Boolean.TRUE.equals(armed) || Boolean.FALSE.equals(unarmed))
                : VillagerEquipmentCondition.read(condition);
        if (parsed.isEmpty()) {
            warnInvalid(location, context, "villager_equipment condition must define armed or unarmed.");
            return Optional.empty();
        }
        return Optional.of(new VillagerEquipment(parsed));
    }

    private static Optional<DialogueCondition> readBiome(
            ResourceLocation location, String context, JsonObject condition) {
        Set<ResourceLocation> biomes = readResourceLocationSet(location, context, condition, "biome", "biomes");
        Set<ResourceLocation> tags = readResourceLocationSet(
                location, context, condition, "tag", "tags", "biome_tag", "biome_tags");
        if (biomes.isEmpty() && tags.isEmpty()) {
            warnInvalid(location, context, "biome condition must define biome, biomes, biome_tag, or biome_tags.");
            return Optional.empty();
        }
        return Optional.of(new Biome(biomes, tags));
    }

    private static Optional<DialogueCondition> readDimension(
            ResourceLocation location, String context, JsonObject condition) {
        Set<ResourceLocation> dimensions = readResourceLocationSet(
                location, context, condition, "dimension", "dimensions", "value", "values");
        if (dimensions.isEmpty()) {
            warnInvalid(location, context, "dimension condition must define dimension or dimensions.");
            return Optional.empty();
        }
        return Optional.of(new Dimension(dimensions));
    }

    private static Optional<DialogueCondition> readAdvancement(
            ResourceLocation location, String context, JsonObject condition) {
        Set<ResourceLocation> advancements = readResourceLocationSet(
                location, context, condition, "advancement", "advancements", "id", "ids");
        if (advancements.isEmpty()) {
            warnInvalid(location, context, "advancement condition must define advancement or advancements.");
            return Optional.empty();
        }
        return Optional.of(new Advancement(advancements, readBoolean(condition, "all", true)));
    }

    private static Optional<DialogueCondition> readScoreboard(
            ResourceLocation location, String context, JsonObject condition) {
        String objective = firstNonBlank(readString(condition, "objective"), readString(condition, "score"));
        if (objective.isBlank()) {
            warnInvalid(location, context, "scoreboard condition must define objective.");
            return Optional.empty();
        }
        Integer exact = readNullableInt(condition, "value");
        Integer min = exact == null ? readNullableInt(condition, "min") : exact;
        Integer max = exact == null ? readNullableInt(condition, "max") : exact;
        return Optional.of(new Scoreboard(objective, min, max));
    }

    private static Optional<DialogueCondition> readNearbyEntity(
            ResourceLocation location, String context, JsonObject condition) {
        Set<ResourceLocation> types = readResourceLocationSet(
                location, context, condition, "entity", "entities", "entity_type", "entity_types");
        Set<ResourceLocation> tags = readResourceLocationSet(
                location, context, condition, "entity_tag", "entity_tags");
        if (types.isEmpty() && tags.isEmpty()) {
            warnInvalid(location, context, "nearby_entity condition must define entity types or tags.");
            return Optional.empty();
        }
        Integer radiusValue = readNullableInt(condition, "radius");
        double radius = Math.max(1.0D, Math.min(128.0D, radiusValue == null ? 16.0D : radiusValue.doubleValue()));
        Integer min = readNullableInt(condition, "min_count");
        Integer max = readNullableInt(condition, "max_count");
        String origin = readString(condition, "origin").toLowerCase(Locale.ROOT);
        return Optional.of(new NearbyEntity(types, tags, radius, min == null ? 1 : Math.max(0, min), max,
                "player".equals(origin)));
    }

    private static Village readVillage(JsonObject condition) {
        Set<String> keys = readNormalizedStrings(condition, "key", "keys", "village", "villages");
        return new Village(readBoolean(condition, "present", true), keys);
    }

    private static TriggerPayload readTriggerPayload(JsonObject condition) {
        Set<String> events = readNormalizedStrings(condition, "event", "events");
        Map<String, Set<String>> any = readPayloadQuery(condition, "any");
        Map<String, Set<String>> all = new LinkedHashMap<>(readPayloadQuery(condition, "all"));
        Map<String, Set<String>> not = readPayloadQuery(condition, "not");
        for (String key : List.of(
                "mob", "entity", "block", "item", "gift_reaction", "event_villager",
                "event_villager_type", "trade_cost_a", "trade_cost_b", "trade_result",
                "criterion", "memory_tag")) {
            Set<String> values = readNormalizedStrings(condition, key, key + "s");
            if (!values.isEmpty()) {
                all.put(key, values);
            }
        }
        JsonObject data = readObject(condition, "data");
        if (data != null) {
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    all.put("criterion_" + normalizePayloadKey(entry.getKey()),
                            Set.of(entry.getValue().getAsString().toLowerCase(Locale.ROOT)));
                }
            }
        }
        return new TriggerPayload(events, any, all, not,
                readNullableInt(condition, "min_reputation"),
                readNullableInt(condition, "max_reputation"));
    }

    private static Map<String, Set<String>> readPayloadQuery(JsonObject condition, String key) {
        JsonObject object = readObject(condition, key);
        if (object == null) {
            return Map.of();
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonObject wrapper = new JsonObject();
            wrapper.add("values", entry.getValue());
            Set<String> values = readNormalizedStrings(wrapper, "values");
            if (!values.isEmpty()) {
                result.put(normalizePayloadKey(entry.getKey()), values);
            }
        }
        return Map.copyOf(result);
    }

    private static String normalizePayloadKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_");
    }

    private static void copyAlias(JsonObject object, String alias, String canonical) {
        if (!object.has(canonical) && object.has(alias)) {
            object.add(canonical, object.get(alias).deepCopy());
        }
    }

    private static void warnInvalid(ResourceLocation location, String context, String message) {
        DatapackDiagnostics.warnInvalidDialogueCondition(location, context, message);
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static JsonObject readObject(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
    }

    private static List<String> readStringList(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive()) {
                continue;
            }
            String value = child.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static Set<String> readNormalizedStrings(JsonObject entry, String... keys) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (String key : keys) {
            for (String value : readStringList(entry, key)) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isBlank()) {
                    values.add(normalized);
                }
            }
        }
        return Set.copyOf(values);
    }

    private static Set<String> readRawStringSet(JsonObject entry, String... keys) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (String key : keys) {
            for (String value : readStringList(entry, key)) {
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return Set.copyOf(values);
    }

    private static Set<ResourceLocation> readResourceLocationSet(
            ResourceLocation location,
            String context,
            JsonObject entry,
            String... keys) {
        java.util.LinkedHashSet<ResourceLocation> values = new java.util.LinkedHashSet<>();
        for (String key : keys) {
            for (String value : readStringList(entry, key)) {
                ResourceLocation tagId = ResourceLocation.tryParse(value);
                if (tagId == null) {
                    warnInvalid(location, context, "quest fact tag \"" + value + "\" is not a valid resource location.");
                } else {
                    values.add(tagId);
                }
            }
        }
        return Set.copyOf(values);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static Set<String> readBiomeKeys(JsonObject entry, String... keys) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (String key : keys) {
            for (String value : readStringList(entry, key)) {
                String normalized = normalizeBiomeKey(value);
                if (!normalized.isBlank()) {
                    values.add(normalized);
                }
            }
        }
        return Set.copyOf(values);
    }

    private static String normalizeBiomeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace(':', '_')
                .replaceAll("[^a-z0-9]+", "_");
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        return normalized.replaceAll("^_+|_+$", "");
    }

    private static Integer readNullableInt(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static Boolean readNullableBoolean(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsBoolean();
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static Integer readVillagerLevelBound(JsonObject entry, String key) {
        String value = readString(entry, key);
        return value.isBlank() ? null : readVillagerLevelValue(value).orElse(null);
    }

    private static Optional<Integer> readVillagerLevelValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "novice" -> Optional.of(1);
            case "apprentice" -> Optional.of(2);
            case "journeyman" -> Optional.of(3);
            case "expert" -> Optional.of(4);
            case "master" -> Optional.of(5);
            default -> {
                try {
                    yield Optional.of(Math.max(1, Math.min(5, Integer.parseInt(normalized))));
                } catch (NumberFormatException ignored) {
                    yield Optional.empty();
                }
            }
        };
    }

    private static <E extends Enum<E>> Optional<E> readEnum(String value, Class<E> enumClass) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    enum ConditionCapability {
        PLAYER_LIVE,
        PROVIDER_LIVE,
        PROVIDER_SNAPSHOT,
        TRIGGER_PAYLOAD,
        VILLAGE_KNOWN,
        WORLD_KNOWN
    }

    enum ConditionOutcome {
        MATCHED,
        UNMET,
        UNKNOWN
    }

    record ConditionTypeDescriptor(
            String id,
            Set<String> aliases,
            Set<ConditionCapability> capabilities,
            Class<? extends DialogueCondition> implementationType
    ) {
        public ConditionTypeDescriptor {
            id = normalizeType(id);
            aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }

    record ConditionEvaluationTrace(
            String canonicalTypeId,
            ConditionOutcome outcome,
            String message,
            List<ConditionEvaluationTrace> children
    ) {
        public ConditionEvaluationTrace {
            canonicalTypeId = canonicalTypeId == null || canonicalTypeId.isBlank() ? "unknown" : canonicalTypeId;
            outcome = outcome == null ? ConditionOutcome.UNKNOWN : outcome;
            message = message == null ? "" : message;
            children = children == null ? List.of() : List.copyOf(children);
        }

        public boolean matched() {
            return this.outcome == ConditionOutcome.MATCHED;
        }

        public Optional<ConditionEvaluationTrace> firstUnmatched() {
            if (this.outcome != ConditionOutcome.MATCHED && this.children.isEmpty()) {
                return Optional.of(this);
            }
            for (ConditionEvaluationTrace child : this.children) {
                Optional<ConditionEvaluationTrace> failed = child.firstUnmatched();
                if (failed.isPresent()) {
                    return failed;
                }
            }
            return this.outcome == ConditionOutcome.MATCHED ? Optional.empty() : Optional.of(this);
        }
    }

    final class ConditionRegistry {
        private static final List<ConditionTypeRegistration> REGISTRATIONS = List.of(
                register(
                        "all",
                        AllOf.class,
                        aliases("all_of", "and"),
                        Set.of(),
                        (location, context, condition, defaultQuestId) ->
                                readChildren(location, context, condition, defaultQuestId).map(AllOf::new)),
                register(
                        "any",
                        AnyOf.class,
                        aliases("any_of", "or"),
                        Set.of(),
                        (location, context, condition, defaultQuestId) ->
                                readChildren(location, context, condition, defaultQuestId).map(AnyOf::new)),
                register(
                        "not",
                        Not.class,
                        Set.of(),
                        Set.of(),
                        (location, context, condition, defaultQuestId) ->
                                DialogueCondition.read(location, context + ".condition", condition.get("condition"), defaultQuestId).map(Not::new)),
                register(
                        "reputation",
                        Reputation.class,
                        Set.of(),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> Optional.of(readReputation(condition))),
                register(
                        "memory",
                        Memory.class,
                        Set.of(),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.PROVIDER_LIVE,
                                ConditionCapability.PROVIDER_SNAPSHOT, ConditionCapability.VILLAGE_KNOWN),
                        (location, context, condition, defaultQuestId) -> readMemory(location, context, condition)),
                register(
                        "family",
                        Family.class,
                        Set.of(),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> Optional.of(readFamily(condition))),
                register(
                        "relationship",
                        Relationship.class,
                        Set.of(),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> Optional.of(readRelationship(condition))),
                register(
                        "recruitment_memory",
                        RecruitmentMemory.class,
                        Set.of(),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> Optional.of(readRecruitmentMemory(condition))),
                register(
                        "villager_age",
                        VillagerAge.class,
                        Set.of(),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> Optional.of(readVillagerAge(condition))),
                register(
                        "social_attribute",
                        SocialAttribute.class,
                        aliases("attribute", "stat"),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> readSocialAttribute(location, context, condition)),
                register(
                        "skill",
                        Skill.class,
                        Set.of(),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> readSkill(location, context, condition)),
                register(
                        "villager_level",
                        VillagerLevel.class,
                        aliases("trade_level"),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> readVillagerLevel(location, context, condition)),
                register(
                        "quest",
                        Quest.class,
                        Set.of(),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.WORLD_KNOWN),
                        DialogueCondition::readQuest),
                register(
                        "quest_fact",
                        QuestFact.class,
                        aliases("quest_tag", "quest_variable", "quest_counter", "quest_stage", "fact", "stage"),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.WORLD_KNOWN,
                                ConditionCapability.PROVIDER_SNAPSHOT, ConditionCapability.VILLAGE_KNOWN),
                        DialogueCondition::readQuestFact),
                register(
                        "selected_choice",
                        SelectedChoice.class,
                        aliases("choice_selected", "response_selected", "quest_choice_selected"),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.WORLD_KNOWN),
                        DialogueCondition::readSelectedChoice),
                register(
                        "stage_history",
                        StageHistory.class,
                        aliases("quest_stage_history", "visited_stage"),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.WORLD_KNOWN),
                        DialogueCondition::readStageHistory),
                register(
                        "player_item",
                        PlayerItem.class,
                        aliases("item", "held_item"),
                        capabilities(ConditionCapability.PLAYER_LIVE),
                        (location, context, condition, defaultQuestId) -> readPlayerItem(location, context, condition)),
                register(
                        "villager_equipment",
                        VillagerEquipment.class,
                        aliases("equipment", "armed"),
                        capabilities(ConditionCapability.PROVIDER_LIVE),
                        (location, context, condition, defaultQuestId) -> readVillagerEquipment(location, context, condition)),
                register(
                        "biome",
                        Biome.class,
                        Set.of(),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.WORLD_KNOWN),
                        (location, context, condition, defaultQuestId) -> readBiome(location, context, condition)),
                register(
                        "dimension",
                        Dimension.class,
                        Set.of(),
                        capabilities(ConditionCapability.WORLD_KNOWN),
                        (location, context, condition, defaultQuestId) -> readDimension(location, context, condition)),
                register(
                        "advancement",
                        Advancement.class,
                        aliases("advancements"),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.WORLD_KNOWN),
                        (location, context, condition, defaultQuestId) -> readAdvancement(location, context, condition)),
                register(
                        "scoreboard",
                        Scoreboard.class,
                        aliases("score"),
                        capabilities(ConditionCapability.PLAYER_LIVE, ConditionCapability.WORLD_KNOWN),
                        (location, context, condition, defaultQuestId) -> readScoreboard(location, context, condition)),
                register(
                        "nearby_entity",
                        NearbyEntity.class,
                        aliases("nearby_entities", "entity_nearby"),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.WORLD_KNOWN),
                        (location, context, condition, defaultQuestId) -> readNearbyEntity(location, context, condition)),
                register(
                        "village",
                        Village.class,
                        aliases("village_presence"),
                        capabilities(ConditionCapability.VILLAGE_KNOWN),
                        (location, context, condition, defaultQuestId) -> Optional.of(readVillage(condition))),
                register(
                        "trigger_payload",
                        TriggerPayload.class,
                        aliases("event_payload", "quest_trigger_payload"),
                        capabilities(ConditionCapability.TRIGGER_PAYLOAD),
                        (location, context, condition, defaultQuestId) ->
                                Optional.of(readTriggerPayload(condition))),
                register(
                        "mood",
                        Mood.class,
                        aliases("villager_mood"),
                        capabilities(ConditionCapability.PROVIDER_LIVE, ConditionCapability.PROVIDER_SNAPSHOT),
                        (location, context, condition, defaultQuestId) -> readMood(location, context, condition)),
                register(
                        "weather",
                        Weather.class,
                        Set.of(),
                        capabilities(ConditionCapability.WORLD_KNOWN),
                        (location, context, condition, defaultQuestId) -> readWeather(condition)),
                register(
                        "time",
                        Time.class,
                        aliases("time_of_day"),
                        capabilities(ConditionCapability.WORLD_KNOWN),
                        (location, context, condition, defaultQuestId) -> readTime(condition))
        );
        private static final Map<String, ConditionTypeRegistration> BY_ALIAS = registrationsByAlias();
        private static final Map<Class<? extends DialogueCondition>, ConditionTypeRegistration> BY_IMPLEMENTATION =
                registrationsByImplementation();

        private ConditionRegistry() {
        }

        static Optional<DialogueCondition> read(
                ResourceLocation location,
                String context,
                JsonObject condition,
                ResourceLocation defaultQuestId) {
            String type = normalizeType(readString(condition, "type"));
            ConditionTypeRegistration registration = BY_ALIAS.get(type);
            if (registration == null) {
                warnInvalid(location, context, "unknown condition type \"" + type + "\".");
                return Optional.empty();
            }
            return registration.reader().read(location, context, condition, defaultQuestId);
        }

        static boolean matchesAll(DialogueContext context, List<DialogueCondition> conditions) {
            return matchesAll(context, null, conditions);
        }

        static boolean matchesAll(
                DialogueContext context,
                QuestTriggerContext triggerContext,
                List<DialogueCondition> conditions) {
            if (conditions == null || conditions.isEmpty()) {
                return true;
            }
            for (DialogueCondition condition : conditions) {
                if (condition == null || !condition.matches(context, triggerContext)) {
                    return false;
                }
            }
            return true;
        }

        static ConditionEvaluationTrace trace(DialogueContext context, DialogueCondition condition) {
            if (condition == null) {
                return new ConditionEvaluationTrace("unknown", ConditionOutcome.UNKNOWN, "condition is missing", List.of());
            }
            if (condition instanceof Invalid invalid) {
                return new ConditionEvaluationTrace("invalid", ConditionOutcome.UNMET, invalid.reason(), List.of());
            }
            if (condition instanceof AllOf allOf) {
                List<ConditionEvaluationTrace> children = traceAll(context, allOf.conditions());
                return new ConditionEvaluationTrace("all", allOutcome(children), allMessage(children), children);
            }
            if (condition instanceof AnyOf anyOf) {
                List<ConditionEvaluationTrace> children = traceAll(context, anyOf.conditions());
                return new ConditionEvaluationTrace("any", anyOutcome(children), anyMessage(children), children);
            }
            if (condition instanceof Not not) {
                ConditionEvaluationTrace child = trace(context, not.condition());
                ConditionOutcome outcome = switch (child.outcome()) {
                    case MATCHED -> ConditionOutcome.UNMET;
                    case UNMET -> ConditionOutcome.MATCHED;
                    case UNKNOWN -> ConditionOutcome.UNKNOWN;
                };
                return new ConditionEvaluationTrace("not", outcome, "negated " + child.outcome().name().toLowerCase(Locale.ROOT), List.of(child));
            }
            String id = canonicalTypeId(condition);
            if (context == null) {
                return new ConditionEvaluationTrace(id, ConditionOutcome.UNKNOWN, "live context unavailable", List.of());
            }
            boolean matched = condition.matches(context);
            return new ConditionEvaluationTrace(
                    id,
                    matched ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET,
                    matched ? "matched" : "condition returned false",
                    List.of());
        }

        static List<ConditionEvaluationTrace> traceAll(DialogueContext context, List<DialogueCondition> conditions) {
            if (conditions == null || conditions.isEmpty()) {
                return List.of();
            }
            List<ConditionEvaluationTrace> traces = new ArrayList<>();
            for (DialogueCondition condition : conditions) {
                traces.add(trace(context, condition));
            }
            return List.copyOf(traces);
        }

        static List<ConditionTypeDescriptor> descriptors() {
            return REGISTRATIONS.stream()
                    .map(ConditionTypeRegistration::descriptor)
                    .toList();
        }

        static String canonicalTypeId(String type) {
            String normalized = normalizeType(type);
            ConditionTypeRegistration registration = BY_ALIAS.get(normalized);
            return registration == null ? normalized : registration.descriptor().id();
        }

        static String canonicalTypeId(DialogueCondition condition) {
            if (condition == null) {
                return "unknown";
            }
            ConditionTypeRegistration registration = BY_IMPLEMENTATION.get(condition.getClass());
            if (registration != null) {
                return registration.descriptor().id();
            }
            return normalizeType(condition.getClass().getSimpleName());
        }

        static Set<ConditionCapability> capabilities(DialogueCondition condition) {
            if (condition == null) {
                return Set.of();
            }
            if (condition instanceof AllOf allOf) {
                return childCapabilities(allOf.conditions());
            }
            if (condition instanceof AnyOf anyOf) {
                return childCapabilities(anyOf.conditions());
            }
            if (condition instanceof Not not) {
                return capabilities(not.condition());
            }
            ConditionTypeRegistration registration = BY_IMPLEMENTATION.get(condition.getClass());
            return registration == null ? Set.of() : registration.descriptor().capabilities();
        }

        private static Set<ConditionCapability> childCapabilities(List<DialogueCondition> conditions) {
            LinkedHashSet<ConditionCapability> result = new LinkedHashSet<>();
            if (conditions != null) {
                for (DialogueCondition child : conditions) {
                    result.addAll(capabilities(child));
                }
            }
            return Set.copyOf(result);
        }

        private static ConditionOutcome allOutcome(List<ConditionEvaluationTrace> children) {
            boolean sawUnknown = false;
            for (ConditionEvaluationTrace child : children) {
                if (child.outcome() == ConditionOutcome.UNMET) {
                    return ConditionOutcome.UNMET;
                }
                sawUnknown |= child.outcome() == ConditionOutcome.UNKNOWN;
            }
            return sawUnknown ? ConditionOutcome.UNKNOWN : ConditionOutcome.MATCHED;
        }

        private static String allMessage(List<ConditionEvaluationTrace> children) {
            return allOutcome(children) == ConditionOutcome.MATCHED ? "all conditions matched" : "one or more conditions did not match";
        }

        private static ConditionOutcome anyOutcome(List<ConditionEvaluationTrace> children) {
            boolean sawUnknown = false;
            for (ConditionEvaluationTrace child : children) {
                if (child.outcome() == ConditionOutcome.MATCHED) {
                    return ConditionOutcome.MATCHED;
                }
                sawUnknown |= child.outcome() == ConditionOutcome.UNKNOWN;
            }
            return sawUnknown ? ConditionOutcome.UNKNOWN : ConditionOutcome.UNMET;
        }

        private static String anyMessage(List<ConditionEvaluationTrace> children) {
            return anyOutcome(children) == ConditionOutcome.MATCHED ? "at least one condition matched" : "no conditions matched";
        }

        private static Map<String, ConditionTypeRegistration> registrationsByAlias() {
            Map<String, ConditionTypeRegistration> byAlias = new LinkedHashMap<>();
            for (ConditionTypeRegistration registration : REGISTRATIONS) {
                byAlias.put(registration.descriptor().id(), registration);
                for (String alias : registration.descriptor().aliases()) {
                    byAlias.put(normalizeType(alias), registration);
                }
            }
            return Map.copyOf(byAlias);
        }

        private static Map<Class<? extends DialogueCondition>, ConditionTypeRegistration> registrationsByImplementation() {
            Map<Class<? extends DialogueCondition>, ConditionTypeRegistration> byImplementation = new HashMap<>();
            for (ConditionTypeRegistration registration : REGISTRATIONS) {
                byImplementation.put(registration.descriptor().implementationType(), registration);
            }
            return Map.copyOf(byImplementation);
        }

        private static ConditionTypeRegistration register(
                String id,
                Class<? extends DialogueCondition> implementationType,
                Set<String> aliases,
                Set<ConditionCapability> capabilities,
                ConditionReader reader) {
            return new ConditionTypeRegistration(
                    new ConditionTypeDescriptor(id, aliases, capabilities, implementationType),
                    reader);
        }

        private static Set<String> aliases(String... aliases) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            if (aliases != null) {
                for (String alias : aliases) {
                    String normalized = normalizeType(alias);
                    if (!normalized.isBlank()) {
                        result.add(normalized);
                    }
                }
            }
            return Set.copyOf(result);
        }

        private static Set<ConditionCapability> capabilities(ConditionCapability... capabilities) {
            return capabilities == null || capabilities.length == 0
                    ? Set.of()
                    : Set.of(capabilities);
        }

        private record ConditionTypeRegistration(
                ConditionTypeDescriptor descriptor,
                ConditionReader reader
        ) {
        }

        @FunctionalInterface
        private interface ConditionReader {
            Optional<DialogueCondition> read(
                    ResourceLocation location,
                    String context,
                    JsonObject condition,
                    ResourceLocation defaultQuestId);
        }
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    record Invalid(String reason) implements DialogueCondition {
        public Invalid {
            reason = reason == null ? "invalid condition" : reason;
        }

        @Override
        public boolean matches(DialogueContext context) {
            return false;
        }

        @Override
        public int specificityScore() {
            return 0;
        }
    }

    record AllOf(List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return DialogueCondition.matchesAll(context, this.conditions);
        }

        @Override
        public boolean matches(DialogueContext context, QuestTriggerContext triggerContext) {
            return DialogueCondition.matchesAll(context, triggerContext, this.conditions);
        }

        @Override
        public int specificityScore() {
            return this.conditions.stream().mapToInt(DialogueCondition::specificityScore).sum();
        }
    }

    record AnyOf(List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            if (context == null) {
                return false;
            }
            for (DialogueCondition condition : this.conditions) {
                if (condition != null && condition.matches(context)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matches(DialogueContext context, QuestTriggerContext triggerContext) {
            for (DialogueCondition condition : this.conditions) {
                if (condition != null && condition.matches(context, triggerContext)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int specificityScore() {
            return this.conditions.stream()
                    .map(DialogueCondition::specificityScore)
                    .max(Comparator.naturalOrder())
                    .orElse(0);
        }
    }

    record Not(DialogueCondition condition) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return context != null
                    && this.condition != null
                    && !(this.condition instanceof Invalid)
                    && !this.condition.matches(context);
        }

        @Override
        public boolean matches(DialogueContext context, QuestTriggerContext triggerContext) {
            return this.condition != null
                    && !(this.condition instanceof Invalid)
                    && !this.condition.matches(context, triggerContext);
        }

        @Override
        public int specificityScore() {
            return this.condition.specificityScore();
        }
    }

    record Reputation(Set<VillagerReputationLevel> levels, Integer minReputation, Integer maxReputation) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            if (!this.levels.isEmpty() && !this.levels.contains(context.reputationLevel())) {
                return false;
            }
            if (this.minReputation != null && context.reputation() < this.minReputation) {
                return false;
            }
            return this.maxReputation == null || context.reputation() <= this.maxReputation;
        }

        @Override
        public int specificityScore() {
            return 3;
        }
    }

    record Memory(Set<ResourceLocation> tags, MemorySource source, boolean currentPlayerOnly, MemoryKind kind)
            implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return switch (this.kind) {
                case RECENT_BROKEN_BED -> context.hasRecentBrokenBedMemory();
                case RECENT_DIRECT_HIT -> context.hasRecentDirectHitMemory();
                case GEAR_REPORT_USED_IN_COMBAT -> context.hasUnreportedGearReportUsedInCombat();
                case GEAR_REPORT_UNUSED_IN_COMBAT -> context.hasUnreportedGearReportUnusedInCombat();
                case RECRUITMENT_MEMORY -> context.hasRecruitmentMemory();
                case EVENT_TAG -> matchesEventTag(context);
            };
        }

        private boolean matchesEventTag(DialogueContext context) {
            UUID playerId = context.player().getUUID();
            UUID villagerId = context.villager().getUUID();
            return switch (this.source) {
                case THIS_VILLAGER -> matchesEventList(context.personalEvents(), playerId, villagerId, false);
                case OTHER_VILLAGER -> matchesEventList(context.villageEvents(), playerId, villagerId, true);
                case ANY -> matchesEventList(context.personalEvents(), playerId, villagerId, false)
                        || matchesEventList(context.villageEvents(), playerId, villagerId, false);
            };
        }

        private boolean matchesEventList(
                List<VillageEventMemory.MemoryEvent> events,
                UUID playerId,
                UUID villagerId,
                boolean excludeSpeaker) {
            for (VillageEventMemory.MemoryEvent event : events) {
                if (!this.tags.contains(event.tagId())) {
                    continue;
                }
                if (this.currentPlayerOnly && !playerId.equals(event.playerId())) {
                    continue;
                }
                if (excludeSpeaker && villagerId.equals(event.sourceId())) {
                    continue;
                }
                return true;
            }
            return false;
        }

        @Override
        public int specificityScore() {
            return 5;
        }
    }

    record Family(Set<String> relations) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            if (this.relations.isEmpty()) {
                return context.hasKnownFamily();
            }
            for (String relation : this.relations) {
                if (matchesRelation(context, relation)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesRelation(DialogueContext context, String relation) {
            return switch (relation) {
                case "family", "any" -> context.hasKnownFamily();
                case "parent" -> context.hasKnownParent();
                case "sibling" -> context.hasKnownSibling();
                case "spouse" -> context.hasKnownSpouse();
                case "child" -> context.hasKnownChild();
                case "grandparent" -> context.hasKnownGrandparent();
                case "grandchild" -> context.hasKnownGrandchild();
                case "descendant" -> context.hasKnownDescendant();
                case "aunt_uncle", "aunt_or_uncle" -> context.hasKnownAuntUncle();
                case "cousin" -> context.hasKnownCousin();
                case "niece_nephew", "niece_or_nephew" -> context.hasKnownNieceNephew();
                case "extended_family" -> context.hasKnownExtendedFamily();
                case "deceased_family" -> context.hasKnownDeceasedFamily();
                default -> false;
            };
        }

        @Override
        public int specificityScore() {
            return 5;
        }
    }

    record Relationship(Set<String> states) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            if (this.states.isEmpty()) {
                return context.hasKnownRelationship();
            }
            for (String state : this.states) {
                if (matchesState(context, state)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesState(DialogueContext context, String state) {
            return switch (state) {
                case "relationship", "any" -> context.hasKnownRelationship();
                case "current", "current_relationship" -> context.hasKnownCurrentRelationship();
                case "past", "past_relationship" -> context.hasKnownPastRelationship();
                case "crush" -> context.hasKnownCrush();
                case "dating", "dating_partner" -> context.hasKnownDatingPartner();
                case "fiance", "fiancee" -> context.hasKnownFiance();
                case "romantic_spouse", "spouse" -> context.hasKnownRomanticSpouse();
                case "separated", "separated_partner" -> context.hasKnownSeparatedPartner();
                case "widowed", "widowed_partner" -> context.hasKnownWidowedPartner();
                default -> false;
            };
        }

        @Override
        public int specificityScore() {
            return 5;
        }
    }

    record RecruitmentMemory(
            Set<String> scenarios,
            Set<String> biomeKeys,
            Integer minFollowDistance,
            Boolean boatTrip,
            Boolean oceanCrossing,
            Boolean swimTrip,
            Boolean excludesOceanCrossing) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            if (!context.hasRecruitmentMemory()) {
                return false;
            }
            if (!this.scenarios.isEmpty() && this.scenarios.stream().noneMatch(context::hasRecruitmentMemoryScenario)) {
                return false;
            }
            if (!this.biomeKeys.isEmpty() && !this.biomeKeys.contains(context.recruitmentMemoryBiomeKey())) {
                return false;
            }
            if (this.minFollowDistance != null && context.recruitmentMemoryDistanceBlocks() < this.minFollowDistance) {
                return false;
            }
            if (this.boatTrip != null && context.hasRecruitmentMemoryBoatTrip() != this.boatTrip) {
                return false;
            }
            if (this.oceanCrossing != null && context.hasRecruitmentMemoryOceanCrossing() != this.oceanCrossing) {
                return false;
            }
            if (this.swimTrip != null && context.hasRecruitmentMemorySwimTrip() != this.swimTrip) {
                return false;
            }
            return this.excludesOceanCrossing == null
                    || !this.excludesOceanCrossing
                    || !context.hasRecruitmentMemoryOceanCrossing();
        }

        @Override
        public int specificityScore() {
            return 0;
        }
    }

    record VillagerAge(Boolean baby, Boolean adult) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            boolean isBaby = context.villager().isBaby();
            if (this.baby != null && isBaby != this.baby) {
                return false;
            }
            return this.adult == null || isBaby != this.adult;
        }

        @Override
        public int specificityScore() {
            return 2;
        }
    }

    record SocialAttribute(Set<VillagerSocialAttribute> attributes, Integer minValue, Integer maxValue) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            for (VillagerSocialAttribute attribute : this.attributes) {
                int value = context.socialAttributeValue(attribute);
                if (this.minValue != null && value < this.minValue) {
                    continue;
                }
                if (this.maxValue != null && value > this.maxValue) {
                    continue;
                }
                return true;
            }
            return false;
        }

        @Override
        public int specificityScore() {
            return 4;
        }
    }

    record Skill(
            Set<VillagerSkill> skills,
            Integer minValue,
            Integer maxValue,
            VillagerSkillRank minRank,
            VillagerSkillRank maxRank) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            for (VillagerSkill skill : this.skills) {
                int value = context.skillValue(skill);
                if (this.minValue != null && value < this.minValue) {
                    continue;
                }
                if (this.maxValue != null && value > this.maxValue) {
                    continue;
                }
                if (this.minRank != null && value < this.minRank.minInclusive()) {
                    continue;
                }
                if (this.maxRank != null && value > this.maxRank.maxInclusive()) {
                    continue;
                }
                return true;
            }
            return false;
        }

        @Override
        public int specificityScore() {
            return 4;
        }
    }

    record VillagerLevel(Set<Integer> levels, Integer minLevel, Integer maxLevel) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            int level = context.villager().getVillagerData().getLevel();
            if (!this.levels.isEmpty() && !this.levels.contains(level)) {
                return false;
            }
            if (this.minLevel != null && level < this.minLevel) {
                return false;
            }
            return this.maxLevel == null || level <= this.maxLevel;
        }

        @Override
        public int specificityScore() {
            return 3;
        }
    }

    record Quest(ResourceLocation questId, Set<String> states) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return VillagerQuestService.matchesState(context, this.questId, this.states);
        }

        @Override
        public int specificityScore() {
            return 8;
        }
    }

    record QuestFact(
            QuestFactScope scope,
            ResourceLocation questId,
            Set<ResourceLocation> tags,
            String key,
            Set<String> values,
            Integer min,
            Integer max) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            QuestScopeKey scopeKey = this.scope.scope(context, this.questId);
            if (scopeKey.isBlank()) {
                return false;
            }
            VillagerQuestFacts facts = VillagerQuestFacts.get(context.level());
            if (!this.tags.isEmpty() && this.tags.stream().noneMatch(tag -> facts.hasTag(scopeKey, tag))) {
                return false;
            }
            if (this.key == null || this.key.isBlank()) {
                return !this.tags.isEmpty();
            }
            Optional<String> variable = facts.variable(scopeKey, this.key);
            if (!this.values.isEmpty() && variable.stream().noneMatch(this.values::contains)) {
                return false;
            }
            int counter = facts.counter(scopeKey, this.key);
            if (this.min != null && counter < this.min) {
                return false;
            }
            if (this.max != null && counter > this.max) {
                return false;
            }
            return !this.values.isEmpty() || this.min != null || this.max != null || variable.isPresent() || counter != 0;
        }

        @Override
        public int specificityScore() {
            return 8;
        }
    }

    record SelectedChoice(
            ResourceLocation questId,
            String scenePath,
            String responseId,
            String priorStage,
            String nextStage) implements DialogueCondition {
        public SelectedChoice {
            scenePath = scenePath == null ? "" : scenePath.trim();
            responseId = responseId == null ? "" : responseId.trim();
            priorStage = priorStage == null ? "" : priorStage.trim();
            nextStage = nextStage == null ? "" : nextStage.trim();
        }

        @Override
        public boolean matches(DialogueContext context) {
            return VillagerQuestService.hasSelectedChoice(
                    context,
                    this.questId,
                    this.scenePath,
                    this.responseId,
                    this.priorStage,
                    this.nextStage);
        }

        @Override
        public int specificityScore() {
            return 9;
        }
    }

    record StageHistory(
            ResourceLocation questId,
            String stage,
            String priorStage,
            String nextStage) implements DialogueCondition {
        public StageHistory {
            stage = stage == null ? "" : stage.trim();
            priorStage = priorStage == null ? "" : priorStage.trim();
            nextStage = nextStage == null ? "" : nextStage.trim();
        }

        @Override
        public boolean matches(DialogueContext context) {
            return VillagerQuestService.hasStageHistory(
                    context,
                    this.questId,
                    this.stage,
                    this.priorStage,
                    this.nextStage);
        }

        @Override
        public int specificityScore() {
            return 9;
        }
    }

    record PlayerItem(VillagerPlayerItemCondition condition) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return this.condition.matches(context.player());
        }

        @Override
        public int specificityScore() {
            return 5;
        }
    }

    record VillagerEquipment(VillagerEquipmentCondition condition) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return this.condition.matches(context.villager());
        }

        @Override
        public int specificityScore() {
            return 4;
        }
    }

    record Biome(Set<ResourceLocation> biomes, Set<ResourceLocation> tags) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            var biome = context.level().getBiome(context.villager().blockPosition());
            boolean idMatch = biome.unwrapKey().map(key -> this.biomes.contains(key.location())).orElse(false);
            boolean tagMatch = this.tags.stream().anyMatch(id -> biome.is(
                    net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, id)));
            return idMatch || tagMatch;
        }

        @Override
        public int specificityScore() {
            return 4;
        }
    }

    record Dimension(Set<ResourceLocation> dimensions) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return this.dimensions.contains(context.level().dimension().location());
        }

        @Override
        public int specificityScore() {
            return 3;
        }
    }

    record Advancement(Set<ResourceLocation> advancements, boolean requireAll) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            java.util.function.Predicate<ResourceLocation> completed = id -> {
                var advancement = context.level().getServer().getAdvancements().get(id);
                return advancement != null && context.player().getAdvancements().getOrStartProgress(advancement).isDone();
            };
            return this.requireAll ? this.advancements.stream().allMatch(completed) : this.advancements.stream().anyMatch(completed);
        }

        @Override
        public int specificityScore() {
            return 6;
        }
    }

    record Scoreboard(String objective, Integer min, Integer max) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            net.minecraft.world.scores.Scoreboard scoreboard = context.level().getScoreboard();
            net.minecraft.world.scores.Objective target = scoreboard.getObjective(this.objective);
            if (target == null) {
                return false;
            }
            net.minecraft.world.scores.ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(context.player(), target);
            if (score == null) {
                return false;
            }
            int value = score.value();
            return (this.min == null || value >= this.min) && (this.max == null || value <= this.max);
        }

        @Override
        public int specificityScore() {
            return 5;
        }
    }

    record NearbyEntity(
            Set<ResourceLocation> entityTypes,
            Set<ResourceLocation> entityTags,
            double radius,
            int minCount,
            Integer maxCount,
            boolean aroundPlayer) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            net.minecraft.world.entity.Entity origin = this.aroundPlayer ? context.player() : context.villager();
            net.minecraft.world.phys.AABB bounds = origin.getBoundingBox().inflate(this.radius);
            int count = context.level().getEntities(origin, bounds, entity -> {
                ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                if (this.entityTypes.contains(id)) {
                    return true;
                }
                var holder = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType());
                return this.entityTags.stream().anyMatch(tag -> holder.is(
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tag)));
            }).size();
            return count >= this.minCount && (this.maxCount == null || count <= this.maxCount);
        }

        @Override
        public int specificityScore() {
            return 5;
        }
    }

    record Village(boolean present, Set<String> keys) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            String key = context.villageKey() == null ? "" : context.villageKey().trim().toLowerCase(Locale.ROOT);
            boolean known = !key.isBlank();
            if (known != this.present) {
                return false;
            }
            return !known || this.keys.isEmpty() || this.keys.contains(key);
        }

        @Override
        public int specificityScore() {
            return 3;
        }
    }

    record TriggerPayload(
            Set<String> events,
            Map<String, Set<String>> any,
            Map<String, Set<String>> all,
            Map<String, Set<String>> not,
            Integer minReputation,
            Integer maxReputation
    ) implements DialogueCondition {
        public TriggerPayload {
            events = events == null ? Set.of() : Set.copyOf(events);
            any = freezeQuery(any);
            all = freezeQuery(all);
            not = freezeQuery(not);
        }

        @Override
        public boolean matches(DialogueContext context) {
            return false;
        }

        @Override
        public boolean matches(DialogueContext context, QuestTriggerContext triggerContext) {
            if (triggerContext == null) {
                return false;
            }
            String event = QuestTriggerRegistry.canonicalEventId(triggerContext.event());
            if (!this.events.isEmpty() && !this.events.contains(event)) {
                return false;
            }
            for (Map.Entry<String, Set<String>> expected : this.all.entrySet()) {
                if (!matchesValue(triggerContext, expected)) {
                    return false;
                }
            }
            if (!this.any.isEmpty() && this.any.entrySet().stream()
                    .noneMatch(expected -> matchesValue(triggerContext, expected))) {
                return false;
            }
            if (this.not.entrySet().stream().anyMatch(expected -> matchesValue(triggerContext, expected))) {
                return false;
            }
            if (this.minReputation != null || this.maxReputation != null) {
                String value = triggerContext.value("reputation");
                if (value.isBlank()) {
                    return false;
                }
                try {
                    int reputation = Integer.parseInt(value);
                    if (this.minReputation != null && reputation < this.minReputation) {
                        return false;
                    }
                    if (this.maxReputation != null && reputation > this.maxReputation) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int specificityScore() {
            return 5 + this.all.size() * 2 + this.any.size() + this.not.size();
        }

        private static boolean matchesValue(
                QuestTriggerContext triggerContext,
                Map.Entry<String, Set<String>> expected) {
            String actual = triggerContext.value(expected.getKey()).toLowerCase(Locale.ROOT);
            return !actual.isBlank() && expected.getValue().contains(actual);
        }

        private static Map<String, Set<String>> freezeQuery(Map<String, Set<String>> query) {
            if (query == null || query.isEmpty()) {
                return Map.of();
            }
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            query.forEach((key, values) -> {
                String normalized = normalizePayloadKey(key);
                if (!normalized.isBlank() && values != null && !values.isEmpty()) {
                    copy.put(normalized, values.stream()
                            .filter(java.util.Objects::nonNull)
                            .map(value -> value.trim().toLowerCase(Locale.ROOT))
                            .filter(value -> !value.isBlank())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
                }
            });
            return Map.copyOf(copy);
        }
    }

    record Mood(Set<VillagerMood> moods, Integer minIntensity, Integer maxIntensity) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            if (!this.moods.isEmpty() && !this.moods.contains(context.primaryMood())) {
                return false;
            }
            int intensity = context.moodIntensity();
            if (this.minIntensity != null && intensity < this.minIntensity) {
                return false;
            }
            return this.maxIntensity == null || intensity <= this.maxIntensity;
        }

        @Override
        public int specificityScore() {
            return 4;
        }
    }

    record Weather(Set<DialogueContext.WeatherState> states) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return this.states.contains(context.weather());
        }

        @Override
        public int specificityScore() {
            return 4;
        }
    }

    record Time(Set<DialogueContext.TimeOfDay> times) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return this.times.contains(context.timeOfDay());
        }

        @Override
        public int specificityScore() {
            return 4;
        }
    }

    enum MemorySource {
        ANY,
        THIS_VILLAGER,
        OTHER_VILLAGER
    }

    enum MemoryKind {
        EVENT_TAG,
        RECENT_BROKEN_BED,
        RECENT_DIRECT_HIT,
        GEAR_REPORT_USED_IN_COMBAT,
        GEAR_REPORT_UNUSED_IN_COMBAT,
        RECRUITMENT_MEMORY
    }
}
