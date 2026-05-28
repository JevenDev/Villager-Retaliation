package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public sealed interface DialogueCondition permits DialogueCondition.AllOf, DialogueCondition.AnyOf,
        DialogueCondition.Not, DialogueCondition.Reputation, DialogueCondition.Memory,
        DialogueCondition.Family, DialogueCondition.Relationship, DialogueCondition.RecruitmentMemory,
        DialogueCondition.VillagerAge, DialogueCondition.SocialAttribute, DialogueCondition.Skill,
        DialogueCondition.Weather, DialogueCondition.Time {

    boolean matches(DialogueContext context);

    int specificityScore();

    static List<DialogueCondition> readList(ResourceLocation location, String context, JsonObject entry) {
        JsonElement element = entry.get("conditions");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            warnInvalid(location, context, "conditions must be an array of condition objects.");
            return List.of();
        }

        List<DialogueCondition> conditions = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            read(location, context + " conditions[" + index + "]", child).ifPresent(conditions::add);
            index++;
        }
        return List.copyOf(conditions);
    }

    private static Optional<DialogueCondition> read(ResourceLocation location, String context, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            warnInvalid(location, context, "condition must be an object.");
            return Optional.empty();
        }

        JsonObject condition = element.getAsJsonObject();
        String type = readString(condition, "type").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "all", "all_of", "and" -> readChildren(location, context, condition).map(AllOf::new);
            case "any", "any_of", "or" -> readChildren(location, context, condition).map(AnyOf::new);
            case "not" -> read(location, context + ".condition", condition.get("condition")).map(Not::new);
            case "reputation" -> Optional.of(readReputation(condition));
            case "memory" -> readMemory(location, context, condition);
            case "family" -> Optional.of(readFamily(condition));
            case "relationship" -> Optional.of(readRelationship(condition));
            case "recruitment_memory" -> Optional.of(readRecruitmentMemory(condition));
            case "villager_age" -> Optional.of(readVillagerAge(condition));
            case "social_attribute", "attribute", "stat" -> readSocialAttribute(location, context, condition);
            case "skill" -> readSkill(location, context, condition);
            case "weather" -> readWeather(condition);
            case "time", "time_of_day" -> readTime(condition);
            default -> {
                warnInvalid(location, context, "unknown condition type \"" + type + "\".");
                yield Optional.empty();
            }
        };
    }

    private static Optional<List<DialogueCondition>> readChildren(ResourceLocation location, String context, JsonObject condition) {
        JsonElement element = condition.get("conditions");
        if (element == null || !element.isJsonArray()) {
            warnInvalid(location, context, "compound condition must contain a conditions array.");
            return Optional.empty();
        }

        List<DialogueCondition> children = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            read(location, context + ".conditions[" + index + "]", child).ifPresent(children::add);
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

        EnumSet<VillageEventMemory.EventTag> tags = EnumSet.noneOf(VillageEventMemory.EventTag.class);
        for (String value : readStringList(condition, "tag")) {
            readEnum(value, VillageEventMemory.EventTag.class).ifPresent(tags::add);
        }
        for (String value : readStringList(condition, "tags")) {
            readEnum(value, VillageEventMemory.EventTag.class).ifPresent(tags::add);
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

    private static Optional<DialogueCondition> readWeather(JsonObject condition) {
        EnumSet<DialogueContext.WeatherState> states = EnumSet.noneOf(DialogueContext.WeatherState.class);
        for (String value : readStringList(condition, "state")) {
            readEnum(value, DialogueContext.WeatherState.class).ifPresent(states::add);
        }
        for (String value : readStringList(condition, "states")) {
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
        return times.isEmpty() ? Optional.empty() : Optional.of(new Time(Set.copyOf(times)));
    }

    private static void warnInvalid(ResourceLocation location, String context, String message) {
        DatapackDiagnostics.warnInvalidDialogueCondition(location, context, message);
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
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

    record AllOf(List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return this.conditions.stream().allMatch(condition -> condition.matches(context));
        }

        @Override
        public int specificityScore() {
            return this.conditions.stream().mapToInt(DialogueCondition::specificityScore).sum();
        }
    }

    record AnyOf(List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean matches(DialogueContext context) {
            return this.conditions.stream().anyMatch(condition -> condition.matches(context));
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
            return !this.condition.matches(context);
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

    record Memory(Set<VillageEventMemory.EventTag> tags, MemorySource source, boolean currentPlayerOnly, MemoryKind kind)
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
            for (VillageEventMemory.MemoryEvent event : context.recentEvents()) {
                if (!this.tags.contains(event.tag())) {
                    continue;
                }
                if (this.currentPlayerOnly && !playerId.equals(event.playerId())) {
                    continue;
                }
                if (this.source == MemorySource.THIS_VILLAGER && !villagerId.equals(event.sourceId())) {
                    continue;
                }
                if (this.source == MemorySource.OTHER_VILLAGER && villagerId.equals(event.sourceId())) {
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
