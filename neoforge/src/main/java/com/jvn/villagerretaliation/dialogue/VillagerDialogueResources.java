package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.combat.PacifyPaymentOffer;
import com.jvn.villagerretaliation.combat.VillagerPacificationResult;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerDialogueResources {
    private static final String DIALOGUE_ROOT = "dialogue/";

    private static volatile CachedDialoguePools cachedDialoguePools = CachedDialoguePools.empty();

    private VillagerDialogueResources() {
    }

    public static List<DialogueLine> lines(MinecraftServer server) {
        return load(server, VillagerLocale.DEFAULT_LOCALE).lines();
    }

    public static void warm(MinecraftServer server) {
        load(server, VillagerLocale.DEFAULT_LOCALE);
    }

    public static void clearCache() {
        cachedDialoguePools = CachedDialoguePools.empty();
    }

    public static List<String> openingLines(DialogueContext context, DialogueDisposition disposition) {
        List<ConversationLine> candidates = load(context.level().getServer(), context.locale()).openings().stream()
                .filter(line -> line.matches(context, disposition))
                .toList();
        List<ConversationLine> firstInteractionCandidates = candidates.stream()
                .filter(ConversationLine::firstInteractionSpecific)
                .toList();
        if (!firstInteractionCandidates.isEmpty()) {
            candidates = firstInteractionCandidates;
        }
        return candidates.stream()
                .map(ConversationLine::text)
                .toList();
    }

    public static List<String> closingLines(DialogueContext context, DialogueDisposition disposition) {
        return load(context.level().getServer(), context.locale()).closings().stream()
                .filter(line -> line.matches(context, disposition))
                .map(ConversationLine::text)
                .toList();
    }

    public static Optional<String> message(DialogueContext context, String key) {
        return message(context, key, Map.of());
    }

    public static Optional<String> message(DialogueContext context, String key, Map<String, String> replacements) {
        DialogueDisposition disposition = VillagerDialogueService.moodFor(context);
        List<KeyedMessageLine> candidates = load(context.level().getServer(), context.locale()).messages().stream()
                .filter(line -> line.matches(context, key, disposition))
                .toList();
        return selectMessage(candidates, context.random().nextInt(Math.max(1, totalMessageWeight(candidates))))
                .map(line -> resolveTemplate(line.text(), replacements));
    }

    public static Optional<String> professionPriorityMessage(DialogueContext context, String key, Map<String, String> replacements) {
        DialogueDisposition disposition = VillagerDialogueService.moodFor(context);
        List<KeyedMessageLine> matches = load(context.level().getServer(), context.locale()).messages().stream()
                .filter(line -> line.matches(context, key, disposition))
                .toList();
        boolean hasProfessionSpecificMatch = matches.stream().anyMatch(KeyedMessageLine::professionSpecific);
        List<KeyedMessageLine> candidates = matches.stream()
                .filter(line -> !hasProfessionSpecificMatch || line.professionSpecific())
                .toList();
        return selectMessage(candidates, context.random().nextInt(Math.max(1, totalMessageWeight(candidates))))
                .map(line -> resolveTemplate(line.text(), replacements));
    }

    public static Optional<String> globalMessage(MinecraftServer server, net.minecraft.util.RandomSource random, String key) {
        return globalMessage(server, random, key, Map.of());
    }

    public static Optional<String> globalMessage(
            MinecraftServer server,
            net.minecraft.util.RandomSource random,
            String key,
            Map<String, String> replacements) {
        return globalMessage(server, random, key, VillagerLocale.DEFAULT_LOCALE, replacements);
    }

    public static Optional<String> globalMessage(
            MinecraftServer server,
            net.minecraft.util.RandomSource random,
            String key,
            String locale) {
        return globalMessage(server, random, key, locale, Map.of());
    }

    public static Optional<String> globalMessage(
            MinecraftServer server,
            net.minecraft.util.RandomSource random,
            String key,
            String locale,
            Map<String, String> replacements) {
        List<KeyedMessageLine> candidates = load(server, locale).messages().stream()
                .filter(line -> line.matches(key))
                .toList();
        return selectMessage(candidates, random.nextInt(Math.max(1, totalMessageWeight(candidates))))
                .map(line -> resolveTemplate(line.text(), replacements));
    }

    public static List<DialogueOptionDefinition> dialogueOptions(DialogueContext context, DialogueDisposition disposition) {
        return load(context.level().getServer(), context.locale()).options().stream()
                .filter(option -> option.matches(context, disposition))
                .sorted(Comparator.comparingInt(DialogueOptionDefinition::order).thenComparing(DialogueOptionDefinition::id))
                .toList();
    }

    public static Optional<DialogueOptionDefinition> dialogueOption(DialogueContext context, String optionId) {
        if (optionId == null || optionId.isBlank()) {
            return Optional.empty();
        }
        DialogueDisposition disposition = VillagerDialogueService.moodFor(context);
        return dialogueOptions(context, disposition).stream()
                .filter(option -> option.id().equals(optionId))
                .findFirst();
    }

    public static Optional<String> pacifyLine(DialogueContext context, VillagerPacificationResult result, PacifyPaymentOffer payment) {
        List<PacifyLine> candidates = load(context.level().getServer(), context.locale()).pacifyLines().stream()
                .filter(line -> line.matches(context, result))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = candidates.stream().mapToInt(PacifyLine::weight).sum();
        int selected = context.random().nextInt(Math.max(1, totalWeight));
        for (PacifyLine candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) {
                return Optional.of(resolvePacifyText(candidate.text(), payment));
            }
        }
        return Optional.of(resolvePacifyText(candidates.getLast().text(), payment));
    }

    public static Optional<String> giftAdviceLine(
            DialogueContext context,
            GiftAdviceKind giftAdviceKind,
            String giftItemName,
            String giftSubject) {
        DialogueDisposition disposition = VillagerDialogueService.moodFor(context);
        List<DialogueLine> candidates = load(context.level().getServer(), context.locale()).lines().stream()
                .filter(line -> line.giftAdviceKind() == giftAdviceKind)
                .filter(line -> line.matches(context, DialogueRequestType.GIFT_PREFERENCES, disposition))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = candidates.stream().mapToInt(line -> Math.max(1, line.weight())).sum();
        int selected = context.random().nextInt(Math.max(1, totalWeight));
        for (DialogueLine candidate : candidates) {
            selected -= Math.max(1, candidate.weight());
            if (selected < 0) {
                return Optional.of(resolveGiftAdviceText(candidate.text(), giftItemName, giftSubject));
            }
        }
        return Optional.of(resolveGiftAdviceText(candidates.getLast().text(), giftItemName, giftSubject));
    }

    private static DialoguePool load(MinecraftServer server, String locale) {
        String normalizedLocale = VillagerLocale.normalize(locale);
        CachedDialoguePools current = cachedDialoguePools;
        if (current.server() == server) {
            DialoguePool cachedPool = current.poolsByLocale().get(normalizedLocale);
            if (cachedPool != null) {
                return cachedPool;
            }
        }

        synchronized (VillagerDialogueResources.class) {
            current = cachedDialoguePools;
            Map<String, DialoguePool> poolsByLocale = current.server() == server
                    ? new HashMap<>(current.poolsByLocale())
                    : new HashMap<>();
            DialoguePool cachedPool = poolsByLocale.get(normalizedLocale);
            if (cachedPool != null) {
                return cachedPool;
            }

            DialoguePool loadedPool = read(server, normalizedLocale);
            poolsByLocale.put(normalizedLocale, loadedPool);
            cachedDialoguePools = new CachedDialoguePools(server, Map.copyOf(poolsByLocale));
            return loadedPool;
        }
    }

    private static DialoguePool read(MinecraftServer server, String locale) {
        Map<String, DialogueLine> lines = new LinkedHashMap<>();
        Map<String, ConversationLine> openings = new LinkedHashMap<>();
        Map<String, ConversationLine> closings = new LinkedHashMap<>();
        Map<String, PacifyLine> pacifyLines = new LinkedHashMap<>();
        Map<String, DialogueOptionDefinition> options = new LinkedHashMap<>();
        Map<String, KeyedMessageLine> messages = new LinkedHashMap<>();

        readLocale(server, VillagerLocale.DEFAULT_LOCALE, lines, openings, closings, pacifyLines, options, messages);
        if (!VillagerLocale.DEFAULT_LOCALE.equals(locale)) {
            readLocale(server, locale, lines, openings, closings, pacifyLines, options, messages);
        }

        return new DialoguePool(
                List.copyOf(lines.values()),
                List.copyOf(openings.values()),
                List.copyOf(closings.values()),
                List.copyOf(pacifyLines.values()),
                List.copyOf(options.values()),
                List.copyOf(messages.values())
        );
    }

    private static void readLocale(
            MinecraftServer server,
            String locale,
            Map<String, DialogueLine> lines,
            Map<String, ConversationLine> openings,
            Map<String, ConversationLine> closings,
            Map<String, PacifyLine> pacifyLines,
            Map<String, DialogueOptionDefinition> options,
            Map<String, KeyedMessageLine> messages) {
        String root = DIALOGUE_ROOT + locale;
        server.getResourceManager()
                .listResources(root, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), locale, lines, openings, closings, pacifyLines, options, messages));
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            String locale,
            Map<String, DialogueLine> lines,
            Map<String, ConversationLine> openings,
            Map<String, ConversationLine> closings,
            Map<String, PacifyLine> pacifyLines,
            Map<String, DialogueOptionDefinition> options,
            Map<String, KeyedMessageLine> messages) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Set<VillagerProfession> defaultProfessions = defaultProfessionsFor(location, locale);
            readDialogueOptions(location, root, defaultProfessions, options);
            readKeyedMessages(location, root, defaultProfessions, messages);
            readDialogueLines(location, root, defaultProfessions, lines);
            readConversationLines(location, root, "openings", defaultProfessions, openings);
            readConversationLines(location, root, "closings", defaultProfessions, closings);
            readPacifyLines(location, root, defaultProfessions, pacifyLines);
        } catch (IOException | IllegalStateException exception) {
            // Invalid dialogue resources are ignored so one bad datapack file cannot break every conversation.
        }
    }

    private static void readKeyedMessages(
            ResourceLocation location,
            JsonObject root,
            Set<VillagerProfession> defaultProfessions,
            Map<String, KeyedMessageLine> messages) {
        JsonArray entries = root.getAsJsonArray("messages");
        if (entries == null) {
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            String key = readString(entry, "key");
            String text = readString(entry, "text");
            if (key.isBlank() || text.isBlank()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            Set<VillagerProfession> professions = readProfessions(entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            int weight = Math.max(1, readInt(entry, "weight", 10));
            boolean showForAdults = readBoolean(entry, "show_for_adults", true);
            boolean showForBabies = readBoolean(entry, "show_for_babies", true);
            String resolvedId = id.isBlank() ? fallbackId(location, "message", index) : id;
            messages.put(resolvedId, new KeyedMessageLine(
                    resolvedId,
                    key,
                    text,
                    showForAdults,
                    showForBabies,
                    professions,
                    dispositions,
                    weight
            ));
            index++;
        }
    }

    private static void readDialogueOptions(
            ResourceLocation location,
            JsonObject root,
            Set<VillagerProfession> defaultProfessions,
            Map<String, DialogueOptionDefinition> options) {
        JsonArray entries = root.getAsJsonArray("options");
        if (entries == null) {
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            String id = readString(entry, "id");
            String label = readString(entry, "label");
            Optional<DialogueRequestType> requestType = readEnum(entry, "type", DialogueRequestType.class);
            if (id.isBlank() || label.isBlank() || requestType.isEmpty()) {
                index++;
                continue;
            }

            boolean showForAdults = readBoolean(entry, "show_for_adults", true);
            boolean showForBabies = readBoolean(entry, "show_for_babies", true);
            Set<VillagerProfession> professions = readProfessions(entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            VillagerPlayerItemCondition playerItemCondition = VillagerPlayerItemCondition.read(entry);
            VillagerReputationCondition reputationCondition = VillagerReputationCondition.read(entry);
            boolean forceCameraTowardsVillager = readBoolean(entry, "force_camera_towards_villager");
            boolean requiresUnreportedCartographerMapDiscovery = readBoolean(entry, "requires_unreported_cartographer_map_discovery");
            boolean requiresUnreportedStoryHintDiscovery = readBoolean(entry, "requires_unreported_story_hint_discovery");
            boolean requiresUnreportedCombatSurvivalReport = readBoolean(entry, "requires_unreported_combat_survival_report");
            boolean requiresUnreportedGearReport = readBoolean(entry, "requires_unreported_gear_report");
            boolean requiresUnreportedRecruitmentFollowup = readBoolean(entry, "requires_unreported_recruitment_followup");
            boolean requiresUnreportedCuredRecognition = readBoolean(entry, "requires_unreported_cured_recognition");
            boolean requiresRecentVillageEvent = readBoolean(entry, "requires_recent_village_event");
            boolean requiresUnreportedGiftAdviceResult = readBoolean(entry, "requires_unreported_gift_advice_result");
            boolean requiresUnapologizedRememberedHarm = readBoolean(entry, "requires_unapologized_remembered_harm");
            boolean requiresUnreportedVillageDefense = readBoolean(entry, "requires_unreported_village_defense");
            boolean requiresShareableStory = readBoolean(entry, "requires_shareable_story");
            boolean requiresKnownFamily = readBoolean(entry, "requires_known_family");
            boolean requiresKnownParent = readBoolean(entry, "requires_known_parent");
            boolean requiresKnownSibling = readBoolean(entry, "requires_known_sibling");
            boolean requiresKnownSpouse = readBoolean(entry, "requires_known_spouse");
            boolean requiresKnownChild = readBoolean(entry, "requires_known_child");
            boolean requiresKnownGrandparent = readBoolean(entry, "requires_known_grandparent");
            boolean requiresKnownGrandchild = readBoolean(entry, "requires_known_grandchild");
            boolean requiresKnownDescendant = readBoolean(entry, "requires_known_descendant");
            boolean requiresKnownAuntUncle = readBoolean(entry, "requires_known_aunt_uncle");
            boolean requiresKnownCousin = readBoolean(entry, "requires_known_cousin");
            boolean requiresKnownNieceNephew = readBoolean(entry, "requires_known_niece_nephew");
            boolean requiresKnownExtendedFamily = readBoolean(entry, "requires_known_extended_family");
            boolean requiresKnownDeceasedFamily = readBoolean(entry, "requires_known_deceased_family");
            boolean requiresKnownRelationship = readBoolean(entry, "requires_known_relationship");
            boolean requiresKnownCurrentRelationship = readBoolean(entry, "requires_known_current_relationship");
            boolean requiresKnownPastRelationship = readBoolean(entry, "requires_known_past_relationship");
            boolean requiresKnownCrush = readBoolean(entry, "requires_known_crush");
            boolean requiresKnownDatingPartner = readBoolean(entry, "requires_known_dating_partner");
            boolean requiresKnownFiance = readBoolean(entry, "requires_known_fiance");
            boolean requiresKnownRomanticSpouse = readBoolean(entry, "requires_known_romantic_spouse");
            boolean requiresKnownSeparatedPartner = readBoolean(entry, "requires_known_separated_partner");
            boolean requiresKnownWidowedPartner = readBoolean(entry, "requires_known_widowed_partner");
            int order = readInt(entry, "order", index);
            options.put(id, new DialogueOptionDefinition(
                    id,
                    label,
                    requestType.get(),
                    showForAdults,
                    showForBabies,
                    professions,
                    dispositions,
                    playerItemCondition,
                    reputationCondition,
                    forceCameraTowardsVillager,
                    requiresUnreportedCartographerMapDiscovery,
                    requiresUnreportedStoryHintDiscovery,
                    requiresUnreportedCombatSurvivalReport,
                    requiresUnreportedGearReport,
                    requiresUnreportedRecruitmentFollowup,
                    requiresUnreportedCuredRecognition,
                    requiresRecentVillageEvent,
                    requiresUnreportedGiftAdviceResult,
                    requiresUnapologizedRememberedHarm,
                    requiresUnreportedVillageDefense,
                    requiresShareableStory,
                    requiresKnownFamily,
                    requiresKnownParent,
                    requiresKnownSibling,
                    requiresKnownSpouse,
                    requiresKnownChild,
                    requiresKnownGrandparent,
                    requiresKnownGrandchild,
                    requiresKnownDescendant,
                    requiresKnownAuntUncle,
                    requiresKnownCousin,
                    requiresKnownNieceNephew,
                    requiresKnownExtendedFamily,
                    requiresKnownDeceasedFamily,
                    requiresKnownRelationship,
                    requiresKnownCurrentRelationship,
                    requiresKnownPastRelationship,
                    requiresKnownCrush,
                    requiresKnownDatingPartner,
                    requiresKnownFiance,
                    requiresKnownRomanticSpouse,
                    requiresKnownSeparatedPartner,
                    requiresKnownWidowedPartner,
                    order
            ));
            index++;
        }
    }

    private static void readDialogueLines(
            ResourceLocation location,
            JsonObject root,
            Set<VillagerProfession> defaultProfessions,
            Map<String, DialogueLine> lines) {
        JsonArray entries = root.getAsJsonArray("lines");
        if (entries == null) {
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            Optional<DialogueRequestType> requestType = readEnum(entry, "type", DialogueRequestType.class);
            String text = readString(entry, "text");
            if (requestType.isEmpty() || text.isBlank()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            String resolvedId = id.isBlank() ? fallbackId(location, "line", index) : id;
            DialogueLine.Builder builder = DialogueLine.builder(
                    resolvedId,
                    requestType.get(),
                    text
            );
            applyDialogueOptions(builder, entry, defaultProfessions);
            lines.put(resolvedId, builder.build());
            index++;
        }
    }

    private static void readConversationLines(
            ResourceLocation location,
            JsonObject root,
            String key,
            Set<VillagerProfession> defaultProfessions,
            Map<String, ConversationLine> lines) {
        JsonArray entries = root.getAsJsonArray(key);
        if (entries == null) {
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            String text = readString(entry, "text");
            if (text.isBlank()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            Set<VillagerProfession> professions = readProfessions(entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            int weight = Math.max(1, readInt(entry, "weight", 10));
            boolean showForAdults = readBoolean(entry, "show_for_adults", true);
            boolean showForBabies = readBoolean(entry, "show_for_babies", true);
            boolean firstConversationOnly = readBoolean(entry, "first_conversation_only");
            boolean firstVillageInteractionOnly = readBoolean(entry, "first_village_interaction_only");
            String resolvedId = id.isBlank() ? fallbackId(location, key, index) : id;
            lines.put(resolvedId, new ConversationLine(
                    resolvedId,
                    text,
                    showForAdults,
                    showForBabies,
                    professions,
                    dispositions,
                    weight,
                    firstConversationOnly,
                    firstVillageInteractionOnly
            ));
            index++;
        }
    }

    private static void readPacifyLines(
            ResourceLocation location,
            JsonObject root,
            Set<VillagerProfession> defaultProfessions,
            Map<String, PacifyLine> lines) {
        JsonArray entries = root.getAsJsonArray("pacify");
        if (entries == null) {
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            String text = readString(entry, "text");
            if (text.isBlank()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            Set<VillagerProfession> professions = readProfessions(entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            Set<VillagerPacificationResult> outcomes = readEnumSet(entry, "outcomes", VillagerPacificationResult.class);
            int weight = Math.max(1, readInt(entry, "weight", 10));
            String resolvedId = id.isBlank() ? fallbackId(location, "pacify", index) : id;
            lines.put(resolvedId, new PacifyLine(
                    resolvedId,
                    text,
                    professions,
                    dispositions,
                    outcomes,
                    weight
            ));
            index++;
        }
    }

    private static void applyDialogueOptions(
            DialogueLine.Builder builder,
            JsonObject entry,
            Set<VillagerProfession> defaultProfessions) {
        Set<VillagerProfession> professions = readProfessions(entry, defaultProfessions);
        if (!professions.isEmpty()) {
            builder.professions(professions.toArray(VillagerProfession[]::new));
        }

        Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
        if (!dispositions.isEmpty()) {
            builder.dispositions(dispositions.toArray(DialogueDisposition[]::new));
        }

        Set<DialogueContext.WeatherState> weatherStates = readEnumSet(entry, "weather", DialogueContext.WeatherState.class);
        if (!weatherStates.isEmpty()) {
            builder.weatherStates(weatherStates.toArray(DialogueContext.WeatherState[]::new));
        }

        Set<DialogueContext.TimeOfDay> timeOfDays = readEnumSet(entry, "times", DialogueContext.TimeOfDay.class);
        if (!timeOfDays.isEmpty()) {
            builder.timeOfDays(timeOfDays.toArray(DialogueContext.TimeOfDay[]::new));
        }

        Set<VillageEventMemory.EventTag> eventTags = readEnumSet(entry, "event_tags", VillageEventMemory.EventTag.class);
        if (!eventTags.isEmpty()) {
            builder.eventTags(eventTags.toArray(VillageEventMemory.EventTag[]::new));
        }

        Set<VillageEventMemory.EventTag> playerEventTags = readEnumSet(entry, "player_event_tags", VillageEventMemory.EventTag.class);
        if (!playerEventTags.isEmpty()) {
            builder.playerEventTags(playerEventTags.toArray(VillageEventMemory.EventTag[]::new));
        }
        if (readBoolean(entry, "requires_container_theft_to_self")) {
            builder.requiresContainerTheftToSelf();
        }
        if (readBoolean(entry, "requires_container_theft_from_other")) {
            builder.requiresContainerTheftFromOther();
        }
        if (readBoolean(entry, "requires_retaliation_to_self")) {
            builder.requiresRetaliationToSelf();
        }
        if (readBoolean(entry, "requires_retaliation_from_other")) {
            builder.requiresRetaliationFromOther();
        }
        List<ResourceLocation> retaliationTargetEntityTypes = new ArrayList<>();
        for (String value : readStringList(entry, "retaliation_target_entity_types")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                retaliationTargetEntityTypes.add(id);
            }
        }
        for (String value : readStringList(entry, "retaliation_target_entities")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                retaliationTargetEntityTypes.add(id);
            }
        }
        if (!retaliationTargetEntityTypes.isEmpty()) {
            builder.retaliationTargetEntityTypes(retaliationTargetEntityTypes.toArray(ResourceLocation[]::new));
        }
        VillagerReputationCondition reputationCondition = VillagerReputationCondition.read(entry);
        if (!reputationCondition.isEmpty()) {
            builder.reputationCondition(reputationCondition);
        }

        VillagerPlayerItemCondition playerItemCondition = VillagerPlayerItemCondition.read(entry);
        if (!playerItemCondition.isEmpty()) {
            builder.playerItemCondition(playerItemCondition);
        }

        List<ResourceLocation> storyTargetIds = new ArrayList<>();
        for (String value : readStringList(entry, "story_structure")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                storyTargetIds.add(id);
            }
        }
        for (String value : readStringList(entry, "story_structures")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                storyTargetIds.add(id);
            }
        }
        for (String value : readStringList(entry, "story_biome")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                storyTargetIds.add(id);
            }
        }
        for (String value : readStringList(entry, "story_biomes")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                storyTargetIds.add(id);
            }
        }
        if (!storyTargetIds.isEmpty()) {
            builder.storyTargetIds(storyTargetIds.toArray(ResourceLocation[]::new));
        }

        if (readBoolean(entry, "requires_recent_broken_bed_memory")) {
            builder.requiresRecentBrokenBedMemory();
        }
        if (readBoolean(entry, "requires_recent_direct_hit_memory")) {
            builder.requiresRecentDirectHitMemory();
        }
        if (readBoolean(entry, "requires_gear_report_used_in_combat")) {
            builder.requiresGearReportUsedInCombat();
        }
        if (readBoolean(entry, "requires_gear_report_unused_in_combat")) {
            builder.requiresGearReportUnusedInCombat();
        }
        List<String> recruitmentFollowupScenarios = readStringList(entry, "recruitment_followup_scenarios");
        if (!recruitmentFollowupScenarios.isEmpty()) {
            builder.recruitmentFollowupScenarios(recruitmentFollowupScenarios.toArray(String[]::new));
        }
        if (readBoolean(entry, "requires_recruitment_memory")) {
            builder.requiresRecruitmentMemory();
        }
        List<String> recruitmentMemoryScenarios = readStringList(entry, "recruitment_memory_scenarios");
        if (!recruitmentMemoryScenarios.isEmpty()) {
            builder.recruitmentMemoryScenarios(recruitmentMemoryScenarios.toArray(String[]::new));
        }
        builder.minRecruitmentFollowDistance(readInt(entry, "min_recruitment_follow_distance", 0));
        if (readBoolean(entry, "requires_recruitment_boat_trip")) {
            builder.requiresRecruitmentBoatTrip();
        }
        if (readBoolean(entry, "requires_recruitment_ocean_crossing")) {
            builder.requiresRecruitmentOceanCrossing();
        }
        if (readBoolean(entry, "requires_recruitment_swim_trip")) {
            builder.requiresRecruitmentSwimTrip();
        }
        if (readBoolean(entry, "excludes_recruitment_ocean_crossing")) {
            builder.excludesRecruitmentOceanCrossing();
        }
        if (readBoolean(entry, "first_conversation_only")) {
            builder.firstConversationOnly();
        }
        if (readBoolean(entry, "requires_known_family")) {
            builder.requiresKnownFamily();
        }
        if (readBoolean(entry, "requires_known_parent")) {
            builder.requiresKnownParent();
        }
        if (readBoolean(entry, "requires_known_sibling")) {
            builder.requiresKnownSibling();
        }
        if (readBoolean(entry, "requires_known_spouse")) {
            builder.requiresKnownSpouse();
        }
        if (readBoolean(entry, "requires_known_child")) {
            builder.requiresKnownChild();
        }
        if (readBoolean(entry, "requires_known_grandparent")) {
            builder.requiresKnownGrandparent();
        }
        if (readBoolean(entry, "requires_known_grandchild")) {
            builder.requiresKnownGrandchild();
        }
        if (readBoolean(entry, "requires_known_descendant")) {
            builder.requiresKnownDescendant();
        }
        if (readBoolean(entry, "requires_known_aunt_uncle")) {
            builder.requiresKnownAuntUncle();
        }
        if (readBoolean(entry, "requires_known_cousin")) {
            builder.requiresKnownCousin();
        }
        if (readBoolean(entry, "requires_known_niece_nephew")) {
            builder.requiresKnownNieceNephew();
        }
        if (readBoolean(entry, "requires_known_extended_family")) {
            builder.requiresKnownExtendedFamily();
        }
        if (readBoolean(entry, "requires_known_deceased_family")) {
            builder.requiresKnownDeceasedFamily();
        }
        if (readBoolean(entry, "requires_known_relationship")) {
            builder.requiresKnownRelationship();
        }
        if (readBoolean(entry, "requires_known_current_relationship")) {
            builder.requiresKnownCurrentRelationship();
        }
        if (readBoolean(entry, "requires_known_past_relationship")) {
            builder.requiresKnownPastRelationship();
        }
        if (readBoolean(entry, "requires_known_crush")) {
            builder.requiresKnownCrush();
        }
        if (readBoolean(entry, "requires_known_dating_partner")) {
            builder.requiresKnownDatingPartner();
        }
        if (readBoolean(entry, "requires_known_fiance")) {
            builder.requiresKnownFiance();
        }
        if (readBoolean(entry, "requires_known_romantic_spouse")) {
            builder.requiresKnownRomanticSpouse();
        }
        if (readBoolean(entry, "requires_known_separated_partner")) {
            builder.requiresKnownSeparatedPartner();
        }
        if (readBoolean(entry, "requires_known_widowed_partner")) {
            builder.requiresKnownWidowedPartner();
        }
        builder.showForAdults(readBoolean(entry, "show_for_adults", true));
        builder.showForBabies(readBoolean(entry, "show_for_babies", true));
        readEnum(entry, "gift_advice", GiftAdviceKind.class).ifPresent(builder::giftAdviceKind);
        List<String> optionIds = new ArrayList<>();
        optionIds.addAll(readStringList(entry, "option"));
        optionIds.addAll(readStringList(entry, "option_ids"));
        if (!optionIds.isEmpty()) {
            builder.optionIds(optionIds.toArray(String[]::new));
        }

        builder.weight(readInt(entry, "weight", 10));
    }

    private static Set<VillagerProfession> defaultProfessionsFor(ResourceLocation location, String locale) {
        String path = location.getPath();
        String professionRoot = DIALOGUE_ROOT + locale + "/professions/";
        if (!path.startsWith(professionRoot) || !path.endsWith(".json")) {
            return Set.of();
        }

        String key = path.substring(professionRoot.length(), path.length() - ".json".length());
        Optional<VillagerProfession> namespacedProfession = parseNamespacedProfessionPath(key);
        if (namespacedProfession.isPresent()) {
            return Set.of(namespacedProfession.get());
        }
        String firstSegment = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
        return VillagerProfessionUtil.parse(firstSegment).map(Set::of).orElse(Set.of());
    }

    private static Set<VillagerProfession> readProfessions(JsonObject entry, Set<VillagerProfession> defaultProfessions) {
        Set<VillagerProfession> professions = java.util.HashSet.newHashSet(defaultProfessions.size() + 1);
        professions.addAll(defaultProfessions);
        for (String value : readStringList(entry, "professions")) {
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        return Set.copyOf(professions);
    }

    private static Optional<VillagerProfession> parseNamespacedProfessionPath(String key) {
        String[] segments = key.split("/");
        if (segments.length < 2) {
            return Optional.empty();
        }

        for (int end = segments.length; end >= 2; end--) {
            String path = String.join("/", java.util.Arrays.copyOfRange(segments, 1, end));
            Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(segments[0] + ":" + path);
            if (profession.isPresent()) {
                return profession;
            }
        }
        return Optional.empty();
    }

    private static <E extends Enum<E>> Set<E> readEnumSet(JsonObject entry, String key, Class<E> enumClass) {
        Set<E> values = EnumSet.noneOf(enumClass);
        for (String value : readStringList(entry, key)) {
            readEnum(value, enumClass).ifPresent(values::add);
        }
        return Set.copyOf(values);
    }

    private static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        return readEnum(readString(entry, key), enumClass);
    }

    private static <E extends Enum<E>> Optional<E> readEnum(String value, Class<E> enumClass) {
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
    }

    private static boolean readBoolean(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static String fallbackId(ResourceLocation location, String group, int index) {
        return stablePath(location).replace('/', '_').replace(".json", "") + "_" + group + "_" + index;
    }

    private static String stablePath(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(DIALOGUE_ROOT)) {
            return path;
        }
        String remainder = path.substring(DIALOGUE_ROOT.length());
        int slash = remainder.indexOf('/');
        return slash < 0 ? remainder : remainder.substring(slash + 1);
    }

    private static String resolvePacifyText(String text, PacifyPaymentOffer payment) {
        String count = Integer.toString(payment.count());
        return text
                .replace("{emerald_cost}", count)
                .replace("{emeralds}", payment.itemNameForCount())
                .replace("{payment_cost}", count)
                .replace("{payment_item}", payment.itemName())
                .replace("{payment_items}", payment.itemNameForCount());
    }

    private static String resolveGiftAdviceText(String text, String giftItemName, String giftSubject) {
        return text
                .replace("{gift_item}", giftItemName)
                .replace("{gift_subject}", giftSubject);
    }

    private static int totalMessageWeight(List<KeyedMessageLine> candidates) {
        return candidates.stream().mapToInt(KeyedMessageLine::weight).sum();
    }

    private static Optional<KeyedMessageLine> selectMessage(List<KeyedMessageLine> candidates, int selected) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        for (KeyedMessageLine candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.getLast());
    }

    static String resolveTemplate(String text, Map<String, String> replacements) {
        String resolved = text;
        Map<String, String> safeReplacements = new HashMap<>(replacements);
        for (Map.Entry<String, String> entry : safeReplacements.entrySet()) {
            resolved = replaceTemplateToken(
                    resolved,
                    "{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue(),
                    entry.getKey().endsWith("_article")
            );
        }
        return resolved;
    }

    private static String replaceTemplateToken(String text, String token, String replacement, boolean capitalizeAtSentenceStart) {
        StringBuilder builder = new StringBuilder(text.length());
        int cursor = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            builder.append(text, cursor, index);
            builder.append(capitalizeAtSentenceStart && isSentenceStart(text, index)
                    ? capitalizeFirstLetter(replacement)
                    : replacement);
            cursor = index + token.length();
            index = text.indexOf(token, cursor);
        }
        builder.append(text, cursor, text.length());
        return builder.toString();
    }

    private static boolean isSentenceStart(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || ch == '"' || ch == '\'' || ch == '(' || ch == '[') {
                continue;
            }
            return ch == '.' || ch == '!' || ch == '?';
        }
        return true;
    }

    private static String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private record DialoguePool(
            List<DialogueLine> lines,
            List<ConversationLine> openings,
            List<ConversationLine> closings,
            List<PacifyLine> pacifyLines,
            List<DialogueOptionDefinition> options,
            List<KeyedMessageLine> messages) {
        private static DialoguePool empty() {
            return new DialoguePool(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private record CachedDialoguePools(MinecraftServer server, Map<String, DialoguePool> poolsByLocale) {
        private static CachedDialoguePools empty() {
            return new CachedDialoguePools(null, Map.of());
        }
    }

    private record ConversationLine(
            String id,
            String text,
            boolean showForAdults,
            boolean showForBabies,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            int weight,
            boolean firstConversationOnly,
            boolean firstVillageInteractionOnly) {
        private boolean matches(DialogueContext context, DialogueDisposition disposition) {
            if (context.villager().isBaby()) {
                if (!this.showForBabies) {
                    return false;
                }
            } else if (!this.showForAdults) {
                return false;
            }
            if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
                return false;
            }
            if (this.firstConversationOnly && !context.firstConversation()) {
                return false;
            }
            if (this.firstVillageInteractionOnly && !context.firstVillageInteraction()) {
                return false;
            }
            return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
        }

        private boolean firstInteractionSpecific() {
            return this.firstConversationOnly || this.firstVillageInteractionOnly;
        }
    }

    private record PacifyLine(
            String id,
            String text,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            Set<VillagerPacificationResult> outcomes,
            int weight) {
        private boolean matches(DialogueContext context, VillagerPacificationResult result) {
            if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
                return false;
            }
            if (!this.dispositions.isEmpty() && !this.dispositions.contains(VillagerDialogueService.moodFor(context))) {
                return false;
            }
            return this.outcomes.isEmpty() || this.outcomes.contains(result);
        }
    }

    private record KeyedMessageLine(
            String id,
            String key,
            String text,
            boolean showForAdults,
            boolean showForBabies,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            int weight) {
        private boolean matches(String key) {
            return this.key.equals(key);
        }

        private boolean matches(DialogueContext context, String key, DialogueDisposition disposition) {
            if (!matches(key)) {
                return false;
            }
            if (context.villager().isBaby()) {
                if (!this.showForBabies) {
                    return false;
                }
            } else if (!this.showForAdults) {
                return false;
            }
            if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
                return false;
            }
            return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
        }

        private boolean professionSpecific() {
            return !this.professions.isEmpty();
        }
    }
}
