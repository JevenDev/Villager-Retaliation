package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.combat.PacifyPaymentOffer;
import com.jvn.villagerretaliation.combat.VillagerPacificationResult;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerDialogueResources {
    private static final String DIALOGUE_ROOT = "dialogue/";
    private static final Set<String> ROOT_KEYS = Set.of(
            "replace", "replace_sections", "replace_options", "replace_lines", "replace_messages",
            "replace_openings", "replace_closings", "replace_pacify",
            "options", "lines", "messages", "openings", "closings", "pacify", "metadata",
            "notifications", "entries", "preferences", "rewards", "payments");
    private static final Set<String> OPTION_KEYS = Set.of(
            "id", "label", "type", "request", "order", "professions", "dispositions",
            "metadata",
            "quest_action",
            "requires_villager_unarmed", "villager_unarmed", "requires_villager_armed", "villager_armed",
            "reputation_level", "reputation_levels", "min_reputation", "max_reputation",
            "player_item", "player_items", "player_item_tag", "player_item_tags", "player_item_slot", "player_item_slots",
            "min_player_item_durability", "max_player_item_durability", "min_player_item_durability_percent", "max_player_item_durability_percent",
            "min_held_item_durability", "max_held_item_durability", "min_held_item_durability_percent", "max_held_item_durability_percent",
            "player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments",
            "min_player_item_enchantment_level", "max_player_item_enchantment_level", "min_held_item_enchantment_level", "max_held_item_enchantment_level",
            "take_items", "payment", "give_items", "conditions", "availability", "available_when", "force_camera_towards_villager",
            "show_for_adults", "show_for_babies",
            "requires_unreported_cartographer_map_discovery", "requires_unreported_story_hint_discovery",
            "requires_unreported_combat_survival_report", "requires_unreported_gear_report",
            "requires_unreported_recruitment_followup", "requires_unreported_cured_recognition",
            "requires_recent_village_event", "requires_unreported_gift_advice_result",
            "requires_unapologized_remembered_harm", "requires_unreported_village_defense",
            "requires_shareable_story",
            "requires_known_family", "requires_known_parent", "requires_known_sibling", "requires_known_spouse", "requires_known_child",
            "requires_known_grandparent", "requires_known_grandchild", "requires_known_descendant", "requires_known_aunt_uncle",
            "requires_known_cousin", "requires_known_niece_nephew", "requires_known_extended_family", "requires_known_deceased_family",
            "requires_known_relationship", "requires_known_current_relationship", "requires_known_past_relationship", "requires_known_crush",
            "requires_known_dating_partner", "requires_known_fiance", "requires_known_romantic_spouse", "requires_known_separated_partner",
            "requires_known_widowed_partner", "requires_active_special_orders");
    private static final Set<String> MESSAGE_KEYS = Set.of(
            "id", "key", "text", "lines", "professions", "dispositions",
            "metadata",
            "requires_villager_unarmed", "villager_unarmed", "requires_villager_armed", "villager_armed",
            "show_for_adults", "show_for_babies", "weight");
    private static final Set<String> CONVERSATION_KEYS = Set.of(
            "id", "text", "lines", "professions", "dispositions",
            "metadata",
            "requires_villager_unarmed", "villager_unarmed", "requires_villager_armed", "villager_armed",
            "show_for_adults", "show_for_babies", "first_conversation_only", "first_village_interaction_only", "weight");
    private static final Set<String> PACIFY_KEYS = Set.of(
            "id", "text", "lines", "outcomes", "professions", "dispositions",
            "metadata",
            "requires_villager_unarmed", "villager_unarmed", "requires_villager_armed", "villager_armed", "weight");
    private static final Set<String> LINE_KEYS = Set.of(
            "id", "request", "text", "lines", "text_key", "option", "option_ids", "professions", "dispositions",
            "metadata",
            "mood", "moods", "min_mood_intensity",
            "requires_villager_unarmed", "villager_unarmed", "requires_villager_armed", "villager_armed",
            "reputation_level", "reputation_levels", "min_reputation", "max_reputation",
            "requires_high_knowledge", "requires_high_guts", "requires_high_proficiency", "requires_high_kindness", "requires_high_charm",
            "min_knowledge", "max_knowledge", "min_guts", "max_guts", "min_proficiency", "max_proficiency",
            "min_kindness", "max_kindness", "min_charm", "max_charm",
            "weather", "times", "event_tags", "player_event_tags",
            "requires_container_theft_to_self", "requires_container_theft_from_other",
            "requires_retaliation_to_self", "requires_retaliation_from_other", "retaliation_target_entity_types", "retaliation_target_entities",
            "player_item", "player_items", "player_item_tag", "player_item_tags", "player_item_slot", "player_item_slots",
            "min_player_item_durability", "max_player_item_durability", "min_player_item_durability_percent", "max_player_item_durability_percent",
            "min_held_item_durability", "max_held_item_durability", "min_held_item_durability_percent", "max_held_item_durability_percent",
            "player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments",
            "min_player_item_enchantment_level", "max_player_item_enchantment_level", "min_held_item_enchantment_level", "max_held_item_enchantment_level",
            "story_structure", "story_structures", "story_biome", "story_biomes",
            "conditions", "availability", "available_when",
            "requires_recent_broken_bed_memory", "requires_recent_direct_hit_memory",
            "requires_gear_report_used_in_combat", "requires_gear_report_unused_in_combat",
            "recruitment_followup_scenarios", "requires_recruitment_memory", "recruitment_memory_scenarios",
            "recruitment_memory_biome", "recruitment_memory_biomes",
            "min_recruitment_follow_distance", "requires_recruitment_boat_trip", "requires_recruitment_ocean_crossing",
            "requires_recruitment_swim_trip", "excludes_recruitment_ocean_crossing",
            "first_conversation_only", "gift_advice", "show_for_adults", "show_for_babies", "priority", "category", "weight",
            "italic", "italics", "bold", "bolded", "underlined", "underline", "strikethrough", "obfuscated", "obfuscate",
            "wavy", "wave", "shake", "shaky", "pulse", "pulsing", "jump", "jumping",
            "rainbow", "rainbow_text", "color", "text_color", "gradient_start", "gradientStart", "gradient_end", "gradientEnd", "text_effects",
            "requires_known_family", "requires_known_parent", "requires_known_sibling", "requires_known_spouse", "requires_known_child",
            "requires_known_grandparent", "requires_known_grandchild", "requires_known_descendant", "requires_known_aunt_uncle",
            "requires_known_cousin", "requires_known_niece_nephew", "requires_known_extended_family", "requires_known_deceased_family",
            "requires_known_relationship", "requires_known_current_relationship", "requires_known_past_relationship", "requires_known_crush",
            "requires_known_dating_partner", "requires_known_fiance", "requires_known_romantic_spouse", "requires_known_separated_partner",
            "requires_known_widowed_partner");
    private static final Set<String> PLANNED_BETA13_LINE_DEPRECATED_KEYS = Set.of(
            "requires_known_family", "requires_known_parent", "requires_known_sibling", "requires_known_spouse", "requires_known_child",
            "requires_known_grandparent", "requires_known_grandchild", "requires_known_descendant", "requires_known_aunt_uncle",
            "requires_known_cousin", "requires_known_niece_nephew", "requires_known_extended_family", "requires_known_deceased_family",
            "requires_known_relationship", "requires_known_current_relationship", "requires_known_past_relationship", "requires_known_crush",
            "requires_known_dating_partner", "requires_known_fiance", "requires_known_romantic_spouse", "requires_known_separated_partner",
            "requires_known_widowed_partner",
            "requires_recent_broken_bed_memory", "requires_recent_direct_hit_memory",
            "requires_gear_report_used_in_combat", "requires_gear_report_unused_in_combat",
            "requires_recruitment_memory", "requires_recruitment_boat_trip", "requires_recruitment_ocean_crossing",
            "requires_recruitment_swim_trip", "excludes_recruitment_ocean_crossing",
            "requires_container_theft_to_self", "requires_container_theft_from_other",
            "requires_retaliation_to_self", "requires_retaliation_from_other");
    private static final Set<String> PLANNED_BETA13_OPTION_DEPRECATED_KEYS = Set.of(
            "requires_known_family", "requires_known_parent", "requires_known_sibling", "requires_known_spouse", "requires_known_child",
            "requires_known_grandparent", "requires_known_grandchild", "requires_known_descendant", "requires_known_aunt_uncle",
            "requires_known_cousin", "requires_known_niece_nephew", "requires_known_extended_family", "requires_known_deceased_family",
            "requires_known_relationship", "requires_known_current_relationship", "requires_known_past_relationship", "requires_known_crush",
            "requires_known_dating_partner", "requires_known_fiance", "requires_known_romantic_spouse", "requires_known_separated_partner",
            "requires_known_widowed_partner");
    private static final String PLANNED_BETA13_DEPRECATION_VERSION = "1.0.0-beta.13";
    private static final String PLANNED_BETA13_DEPRECATION_REPLACEMENT = "Use beta.12 conditions blocks instead.";
    private static volatile CachedDialoguePools cachedDialoguePools = CachedDialoguePools.empty();

    private enum DialogueFileSection {
        OPTIONS("options"),
        LINES("lines"),
        MESSAGES("messages"),
        OPENINGS("openings"),
        CLOSINGS("closings"),
        PACIFY("pacify");

        private final String key;

        DialogueFileSection(String key) {
            this.key = key;
        }

        private static Optional<DialogueFileSection> fromPathSegment(String segment) {
            return switch (segment) {
                case "option", "options" -> Optional.of(OPTIONS);
                case "line", "lines" -> Optional.of(LINES);
                case "message", "messages" -> Optional.of(MESSAGES);
                case "opening", "openings" -> Optional.of(OPENINGS);
                case "closing", "closings" -> Optional.of(CLOSINGS);
                case "pacify", "pacification" -> Optional.of(PACIFY);
                default -> Optional.empty();
            };
        }
    }

    private VillagerDialogueResources() {
    }

    public static List<DialogueLine> lines(MinecraftServer server) {
        return load(server, VillagerLocale.DEFAULT_LOCALE).lines();
    }

    public static List<DialogueLine> lines(DialogueContext context) {
        return load(context.level().getServer(), context.locale()).lines();
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
                .map(line -> line.selectText(context.random()))
                .toList();
    }

    public static List<String> closingLines(DialogueContext context, DialogueDisposition disposition) {
        return load(context.level().getServer(), context.locale()).closings().stream()
                .filter(line -> line.matches(context, disposition))
                .map(line -> line.selectText(context.random()))
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
                .map(line -> resolveTemplate(line.selectText(context.random()), replacements));
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
                .map(line -> resolveTemplate(line.selectText(context.random()), replacements));
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
                .map(line -> resolveTemplate(line.selectText(random), replacements));
    }

    public static List<DialogueOptionDefinition> dialogueOptions(DialogueContext context, DialogueDisposition disposition) {
        Optional<List<DialogueOptionDefinition>> activeTreeOptions = DialogueTreeService.activeOptions(context);
        if (activeTreeOptions.isPresent()) {
            return activeTreeOptions.get();
        }

        List<DialogueOptionDefinition> options = new ArrayList<>(load(context.level().getServer(), context.locale()).options().stream()
                .filter(option -> option.matches(context, disposition))
                .sorted(Comparator.comparingInt(DialogueOptionDefinition::order).thenComparing(DialogueOptionDefinition::id))
                .toList());
        options.addAll(DialogueTreeResources.entryOptions(context, disposition));
        options.addAll(VillagerQuestService.stageBranchOptions(context));
        options.sort(Comparator.comparingInt(DialogueOptionDefinition::order).thenComparing(DialogueOptionDefinition::id));
        return List.copyOf(options);
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
                return Optional.of(resolvePacifyText(candidate.selectText(context.random()), payment));
            }
        }
        return Optional.of(resolvePacifyText(candidates.getLast().selectText(context.random()), payment));
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
                return Optional.of(resolveGiftAdviceText(candidate.selectText(context.random()), giftItemName, giftSubject));
            }
        }
        return Optional.of(resolveGiftAdviceText(candidates.getLast().selectText(context.random()), giftItemName, giftSubject));
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
        Map<String, ResourceLocation> lineSources = new HashMap<>();
        Map<String, ResourceLocation> openingSources = new HashMap<>();
        Map<String, ResourceLocation> closingSources = new HashMap<>();
        Map<String, ResourceLocation> pacifySources = new HashMap<>();
        Map<String, ResourceLocation> optionSources = new HashMap<>();
        Map<String, ResourceLocation> messageSources = new HashMap<>();
        DatapackResourceLoader.forEachJsonResource(
                server,
                root,
                (location, resource) -> readFile(
                        location,
                        resource,
                        locale,
                        lines,
                        openings,
                        closings,
                        pacifyLines,
                        options,
                        messages,
                        lineSources,
                        openingSources,
                        closingSources,
                        pacifySources,
                        optionSources,
                        messageSources));
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
            Map<String, KeyedMessageLine> messages,
            Map<String, ResourceLocation> lineSources,
            Map<String, ResourceLocation> openingSources,
            Map<String, ResourceLocation> closingSources,
            Map<String, ResourceLocation> pacifySources,
            Map<String, ResourceLocation> optionSources,
            Map<String, ResourceLocation> messageSources) {
        DatapackResourceLoader.readObject(location, "dialogue", resource).ifPresent(root -> {
            DialogueEntryMetadata rootMetadata = DialogueEntryMetadata.read(location, "dialogue", "file root", root);
            if (readBoolean(root, "replace", false)) {
                clearAllSections(
                        lines,
                        openings,
                        closings,
                        pacifyLines,
                        options,
                        messages,
                        lineSources,
                        openingSources,
                        closingSources,
                        pacifySources,
                        optionSources,
                        messageSources);
            }
            replaceSections(
                    location,
                    root,
                    lines,
                    openings,
                    closings,
                    pacifyLines,
                    options,
                    messages,
                    lineSources,
                    openingSources,
                    closingSources,
                    pacifySources,
                    optionSources,
                    messageSources);
            if (!containsBundledSections(root)) {
                Optional<DialogueFileSection> singleSection = sectionFromPath(location, locale)
                        .or(() -> inferSection(root));
                if (singleSection.isPresent()) {
                    readSingleSectionFile(
                            location,
                            root,
                            singleSection.get(),
                            rootMetadata,
                            defaultProfessionsFor(location, locale),
                            lines,
                            openings,
                            closings,
                            pacifyLines,
                            options,
                            messages,
                            lineSources,
                            openingSources,
                            closingSources,
                            pacifySources,
                            optionSources,
                            messageSources);
                    return;
                }
            }
            DatapackDiagnostics.warnMisplacedRootKeys(location, "dialogue", root, Map.of(
                    "notifications", "data/villagerretaliation/notifications/<locale>/<file>.json",
                    "entries", "data/villagerretaliation/forced_dialogue/<file>.json",
                    "preferences", "data/villagerretaliation/gifts/<file>.json",
                    "rewards", "data/villagerretaliation/gifts/<file>.json",
                    "payments", "data/villagerretaliation/pacification/<file>.json"));
            DatapackDiagnostics.warnUnknownRootKeys(location, "dialogue", root, ROOT_KEYS);
            Set<VillagerProfession> defaultProfessions = defaultProfessionsFor(location, locale);
            readDialogueOptions(location, root, rootMetadata, defaultProfessions, options, optionSources);
            readKeyedMessages(location, root, defaultProfessions, messages, messageSources);
            readDialogueLines(location, root, rootMetadata, defaultProfessions, lines, lineSources);
            readConversationLines(location, root, "openings", defaultProfessions, openings, openingSources);
            readConversationLines(location, root, "closings", defaultProfessions, closings, closingSources);
            readPacifyLines(location, root, defaultProfessions, pacifyLines, pacifySources);
        });
    }

    private static void clearAllSections(
            Map<String, DialogueLine> lines,
            Map<String, ConversationLine> openings,
            Map<String, ConversationLine> closings,
            Map<String, PacifyLine> pacifyLines,
            Map<String, DialogueOptionDefinition> options,
            Map<String, KeyedMessageLine> messages,
            Map<String, ResourceLocation> lineSources,
            Map<String, ResourceLocation> openingSources,
            Map<String, ResourceLocation> closingSources,
            Map<String, ResourceLocation> pacifySources,
            Map<String, ResourceLocation> optionSources,
            Map<String, ResourceLocation> messageSources) {
        lines.clear();
        openings.clear();
        closings.clear();
        pacifyLines.clear();
        options.clear();
        messages.clear();
        lineSources.clear();
        openingSources.clear();
        closingSources.clear();
        pacifySources.clear();
        optionSources.clear();
        messageSources.clear();
    }

    private static void replaceSections(
            ResourceLocation location,
            JsonObject root,
            Map<String, DialogueLine> lines,
            Map<String, ConversationLine> openings,
            Map<String, ConversationLine> closings,
            Map<String, PacifyLine> pacifyLines,
            Map<String, DialogueOptionDefinition> options,
            Map<String, KeyedMessageLine> messages,
            Map<String, ResourceLocation> lineSources,
            Map<String, ResourceLocation> openingSources,
            Map<String, ResourceLocation> closingSources,
            Map<String, ResourceLocation> pacifySources,
            Map<String, ResourceLocation> optionSources,
            Map<String, ResourceLocation> messageSources) {
        Set<DialogueFileSection> sections = java.util.EnumSet.noneOf(DialogueFileSection.class);
        for (String value : readStringList(root, "replace_sections")) {
            String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
            DialogueFileSection.fromPathSegment(normalized).ifPresentOrElse(
                    sections::add,
                    () -> DatapackDiagnostics.warnInvalidDialogueCondition(
                            location,
                            "dialogue",
                            "unknown replace_sections entry \"" + value + "\"."));
        }
        if (readBoolean(root, "replace_options")) {
            sections.add(DialogueFileSection.OPTIONS);
        }
        if (readBoolean(root, "replace_lines")) {
            sections.add(DialogueFileSection.LINES);
        }
        if (readBoolean(root, "replace_messages")) {
            sections.add(DialogueFileSection.MESSAGES);
        }
        if (readBoolean(root, "replace_openings")) {
            sections.add(DialogueFileSection.OPENINGS);
        }
        if (readBoolean(root, "replace_closings")) {
            sections.add(DialogueFileSection.CLOSINGS);
        }
        if (readBoolean(root, "replace_pacify")) {
            sections.add(DialogueFileSection.PACIFY);
        }

        for (DialogueFileSection section : sections) {
            switch (section) {
                case OPTIONS -> {
                    options.clear();
                    optionSources.clear();
                }
                case LINES -> {
                    lines.clear();
                    lineSources.clear();
                }
                case MESSAGES -> {
                    messages.clear();
                    messageSources.clear();
                }
                case OPENINGS -> {
                    openings.clear();
                    openingSources.clear();
                }
                case CLOSINGS -> {
                    closings.clear();
                    closingSources.clear();
                }
                case PACIFY -> {
                    pacifyLines.clear();
                    pacifySources.clear();
                }
            }
        }
    }

    private static void readSingleSectionFile(
            ResourceLocation location,
            JsonObject root,
            DialogueFileSection section,
            DialogueEntryMetadata rootMetadata,
            Set<VillagerProfession> defaultProfessions,
            Map<String, DialogueLine> lines,
            Map<String, ConversationLine> openings,
            Map<String, ConversationLine> closings,
            Map<String, PacifyLine> pacifyLines,
            Map<String, DialogueOptionDefinition> options,
            Map<String, KeyedMessageLine> messages,
            Map<String, ResourceLocation> lineSources,
            Map<String, ResourceLocation> openingSources,
            Map<String, ResourceLocation> closingSources,
            Map<String, ResourceLocation> pacifySources,
            Map<String, ResourceLocation> optionSources,
            Map<String, ResourceLocation> messageSources) {
        JsonObject entry = root.deepCopy();
        entry.remove("replace");
        entry.remove("replace_sections");
        entry.remove("replace_options");
        entry.remove("replace_lines");
        entry.remove("replace_messages");
        entry.remove("replace_openings");
        entry.remove("replace_closings");
        entry.remove("replace_pacify");
        if (section == DialogueFileSection.OPTIONS && !entry.has("type")) {
            entry.addProperty("type", "dialogue_option");
        }

        JsonObject syntheticRoot = new JsonObject();
        JsonArray entries = new JsonArray();
        entries.add(entry);
        syntheticRoot.add(section.key, entries);

        switch (section) {
            case OPTIONS -> readDialogueOptions(location, syntheticRoot, rootMetadata, defaultProfessions, options, optionSources);
            case LINES -> readDialogueLines(location, syntheticRoot, rootMetadata, defaultProfessions, lines, lineSources);
            case MESSAGES -> readKeyedMessages(location, syntheticRoot, defaultProfessions, messages, messageSources);
            case OPENINGS -> readConversationLines(location, syntheticRoot, "openings", defaultProfessions, openings, openingSources);
            case CLOSINGS -> readConversationLines(location, syntheticRoot, "closings", defaultProfessions, closings, closingSources);
            case PACIFY -> readPacifyLines(location, syntheticRoot, defaultProfessions, pacifyLines, pacifySources);
        }
    }

    private static boolean containsBundledSections(JsonObject root) {
        for (DialogueFileSection section : DialogueFileSection.values()) {
            JsonElement element = root.get(section.key);
            if (element == null || !element.isJsonArray()) {
                continue;
            }
            if (section != DialogueFileSection.LINES) {
                return true;
            }
            if (isBundledLinesSection(root, element.getAsJsonArray())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBundledLinesSection(JsonObject root, JsonArray linesElement) {
        if (root.has("request") || root.has("key") || root.has("label") || root.has("outcomes")) {
            return false;
        }
        return linesElement.size() == 0 || linesElement.get(0).isJsonObject();
    }

    private static Optional<DialogueFileSection> sectionFromPath(ResourceLocation location, String locale) {
        String path = location.getPath();
        String root = DIALOGUE_ROOT + locale + "/";
        if (!path.startsWith(root) || !path.endsWith(".json")) {
            return Optional.empty();
        }

        String relativePath = path.substring(root.length(), path.length() - ".json".length());
        for (String segment : relativePath.split("/")) {
            Optional<DialogueFileSection> section = DialogueFileSection.fromPathSegment(segment);
            if (section.isPresent()) {
                return section;
            }
        }
        return Optional.empty();
    }

    private static Optional<DialogueFileSection> inferSection(JsonObject root) {
        String type = readString(root, "type");
        if ("dialogue_option".equals(type) || root.has("label")) {
            return Optional.of(DialogueFileSection.OPTIONS);
        }
        if (root.has("key")) {
            return Optional.of(DialogueFileSection.MESSAGES);
        }
        if (root.has("request")) {
            return Optional.of(DialogueFileSection.LINES);
        }
        if (root.has("outcomes")) {
            return Optional.of(DialogueFileSection.PACIFY);
        }
        return Optional.empty();
    }

    private static void readKeyedMessages(
            ResourceLocation location,
            JsonObject root,
            Set<VillagerProfession> defaultProfessions,
            Map<String, KeyedMessageLine> messages,
            Map<String, ResourceLocation> messageSources) {
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
            String context = entryContext("message", entry, index);
            DatapackDiagnostics.warnUnknownKeys(location, "dialogue message", context, entry, MESSAGE_KEYS);
            DialogueEntryMetadata.read(location, "dialogue message", context, entry);
            String key = readString(entry, "key");
            List<String> entryLines = readLines(entry);
            if (key.isBlank() || entryLines.isEmpty()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            Set<VillagerProfession> professions = readProfessions(location, context, entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            int weight = Math.max(1, readInt(entry, "weight", 10));
            boolean showForAdults = readBoolean(entry, "show_for_adults", true);
            boolean showForBabies = readBoolean(entry, "show_for_babies", professions.isEmpty());
            String resolvedId = id.isBlank() ? fallbackId(location, "message", index) : id;
            putEntry(location, "dialogue message", resolvedId, new KeyedMessageLine(
                    resolvedId,
                    key,
                    entryLines,
                    showForAdults,
                    showForBabies,
                    professions,
                    dispositions,
                    VillagerEquipmentCondition.read(entry),
                    weight
            ), messages, messageSources);
            index++;
        }
    }

    private static void readDialogueOptions(
            ResourceLocation location,
            JsonObject root,
            DialogueEntryMetadata rootMetadata,
            Set<VillagerProfession> defaultProfessions,
            Map<String, DialogueOptionDefinition> options,
            Map<String, ResourceLocation> optionSources) {
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
            String context = entryContext("option", entry, index);
            DatapackDiagnostics.warnUnknownKeys(location, "dialogue option", context, entry, OPTION_KEYS);
            DatapackDiagnostics.warnDeprecatedKeys(
                    location,
                    "dialogue option",
                    context,
                    entry,
                    PLANNED_BETA13_OPTION_DEPRECATED_KEYS,
                    PLANNED_BETA13_DEPRECATION_VERSION,
                    PLANNED_BETA13_DEPRECATION_REPLACEMENT);
            DatapackDiagnostics.warnInertPlayerItemSlots(location, context, entry);
            String id = readString(entry, "id");
            String label = readString(entry, "label");
            String entryType = readString(entry, "type");
            Optional<DialogueRequestType> requestType = readEnum(entry, "request", DialogueRequestType.class);
            if (label.isBlank()
                    || (!entryType.isBlank() && !"dialogue_option".equals(entryType))
                    || requestType.isEmpty()) {
                index++;
                continue;
            }

            boolean showForAdults = readBoolean(entry, "show_for_adults", true);
            boolean showForBabies = readBoolean(entry, "show_for_babies", true);
            Set<VillagerProfession> professions = readProfessions(location, context, entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            VillagerPlayerItemCondition playerItemCondition = VillagerPlayerItemCondition.read(entry);
            VillagerReputationCondition reputationCondition = VillagerReputationCondition.read(entry);
            VillagerEquipmentCondition equipmentCondition = VillagerEquipmentCondition.read(entry);
            DialogueItemPayment itemPayment = readItemPayment(entry);
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
            boolean requiresActiveSpecialOrders = readBoolean(entry, "requires_active_special_orders");
            int order = readInt(entry, "order", index);
            String resolvedId = id.isBlank() ? fallbackId(location, "option", index) : id;
            putEntry(location, "dialogue option", resolvedId, new DialogueOptionDefinition(
                    resolvedId,
                    location,
                    rootMetadata.merge(DialogueEntryMetadata.read(location, "dialogue option", context, entry)),
                    DialogueQuestAction.read(location, context, entry),
                    DialogueTreeReference.EMPTY,
                    label,
                    requestType.get(),
                    showForAdults,
                    showForBabies,
                    professions,
                    dispositions,
                    equipmentCondition,
                    playerItemCondition,
                    reputationCondition,
                    itemPayment,
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
                    DialogueCondition.readList(location, context, entry),
                    requiresActiveSpecialOrders,
                    order
            ), options, optionSources);
            index++;
        }
    }

    private static DialogueItemPayment readItemPayment(JsonObject option) {
        return readItemPayment(option, "give_items", DialogueItemPayment.DialogueItemDestination.VILLAGER_INVENTORY)
                .or(() -> readItemPayment(option, "take_items", DialogueItemPayment.DialogueItemDestination.DISCARD))
                .or(() -> readItemPayment(option, "payment", DialogueItemPayment.DialogueItemDestination.DISCARD))
                .orElse(DialogueItemPayment.empty());
    }

    private static Optional<DialogueItemPayment> readItemPayment(
            JsonObject option,
            String key,
            DialogueItemPayment.DialogueItemDestination fallbackDestination) {
        JsonElement element = option.get(key);
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject entry = element.getAsJsonObject();
        return VillagerInventoryItemRemoval.read(option, key)
                .map(removal -> new DialogueItemPayment(
                        removal,
                        readResponseVariants(entry, "success_response", "success_responses"),
                        readResponseVariants(entry, "failure_response", "failure_responses"),
                        readItemDestination(entry, fallbackDestination),
                        readEnum(entry, "overflow_destination", DialogueItemPayment.DialogueItemDestination.class).orElse(null),
                        readBoolean(entry, "require_space", true)
                ));
    }

    private static DialogueItemPayment.DialogueItemDestination readItemDestination(
            JsonObject entry,
            DialogueItemPayment.DialogueItemDestination fallback) {
        Optional<DialogueItemPayment.DialogueItemDestination> destination =
                readEnum(entry, "destination", DialogueItemPayment.DialogueItemDestination.class);
        if (destination.isPresent()) {
            return destination.get();
        }
        if (hasBoolean(entry, "store_in_villager_inventory")
                || hasBoolean(entry, "store_in_inventory")
                || hasBoolean(entry, "store_items")
                || hasBoolean(entry, "store")) {
            return readBoolean(entry, "store_in_villager_inventory",
                    readBoolean(entry, "store_in_inventory",
                            readBoolean(entry, "store_items", readBoolean(entry, "store"))))
                    ? DialogueItemPayment.DialogueItemDestination.VILLAGER_INVENTORY
                    : DialogueItemPayment.DialogueItemDestination.DISCARD;
        }
        return fallback;
    }

    private static List<String> readResponseVariants(JsonObject entry, String singularKey, String pluralKey) {
        List<String> singular = readStringList(entry, singularKey);
        if (!singular.isEmpty()) {
            return singular;
        }
        return readStringList(entry, pluralKey);
    }

    private static void readDialogueLines(
            ResourceLocation location,
            JsonObject root,
            DialogueEntryMetadata rootMetadata,
            Set<VillagerProfession> defaultProfessions,
            Map<String, DialogueLine> lines,
            Map<String, ResourceLocation> lineSources) {
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
            String context = entryContext("line", entry, index);
            DatapackDiagnostics.warnUnknownKeys(location, "dialogue line", context, entry, LINE_KEYS);
            DatapackDiagnostics.warnDeprecatedKeys(
                    location,
                    "dialogue line",
                    context,
                    entry,
                    PLANNED_BETA13_LINE_DEPRECATED_KEYS,
                    PLANNED_BETA13_DEPRECATION_VERSION,
                    PLANNED_BETA13_DEPRECATION_REPLACEMENT);
            DatapackDiagnostics.warnInertPlayerItemSlots(location, context, entry);
            Optional<DialogueRequestType> requestType = readEnum(entry, "request", DialogueRequestType.class);
            List<String> entryLines = readLines(entry);
            String textKey = readString(entry, "text_key");
            if (requestType.isEmpty() || (entryLines.isEmpty() && textKey.isBlank())) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            String resolvedId = id.isBlank() ? fallbackId(location, "line", index) : id;
            DialogueLine.Builder builder = DialogueLine.builder(
                    resolvedId,
                    requestType.get(),
                    entryLines
            );
            builder.source(location);
            builder.metadata(rootMetadata.merge(DialogueEntryMetadata.read(location, "dialogue line", context, entry)));
            builder.textKey(textKey);
            applyDialogueOptions(location, context, builder, entry, defaultProfessions);
            putEntry(location, "dialogue line", resolvedId, builder.build(), lines, lineSources);
            index++;
        }
    }

    private static void readConversationLines(
            ResourceLocation location,
            JsonObject root,
            String key,
            Set<VillagerProfession> defaultProfessions,
            Map<String, ConversationLine> lines,
            Map<String, ResourceLocation> lineSources) {
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
            String context = entryContext(key, entry, index);
            DatapackDiagnostics.warnUnknownKeys(location, "dialogue " + key, context, entry, CONVERSATION_KEYS);
            DialogueEntryMetadata.read(location, "dialogue " + key, context, entry);
            List<String> entryLines = readLines(entry);
            if (entryLines.isEmpty()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            Set<VillagerProfession> professions = readProfessions(location, context, entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            int weight = Math.max(1, readInt(entry, "weight", 10));
            boolean showForAdults = readBoolean(entry, "show_for_adults", true);
            boolean showForBabies = readBoolean(entry, "show_for_babies", professions.isEmpty());
            boolean firstConversationOnly = readBoolean(entry, "first_conversation_only");
            boolean firstVillageInteractionOnly = readBoolean(entry, "first_village_interaction_only");
            String resolvedId = id.isBlank() ? fallbackId(location, key, index) : id;
            putEntry(location, "dialogue " + key, resolvedId, new ConversationLine(
                    resolvedId,
                    entryLines,
                    showForAdults,
                    showForBabies,
                    professions,
                    dispositions,
                    VillagerEquipmentCondition.read(entry),
                    weight,
                    firstConversationOnly,
                    firstVillageInteractionOnly
            ), lines, lineSources);
            index++;
        }
    }

    private static void readPacifyLines(
            ResourceLocation location,
            JsonObject root,
            Set<VillagerProfession> defaultProfessions,
            Map<String, PacifyLine> lines,
            Map<String, ResourceLocation> pacifySources) {
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
            String context = entryContext("pacify", entry, index);
            DatapackDiagnostics.warnUnknownKeys(location, "pacify line", context, entry, PACIFY_KEYS);
            DialogueEntryMetadata.read(location, "pacify line", context, entry);
            List<String> entryLines = readLines(entry);
            if (entryLines.isEmpty()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            Set<VillagerProfession> professions = readProfessions(location, context, entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            Set<VillagerPacificationResult> outcomes = readEnumSet(entry, "outcomes", VillagerPacificationResult.class);
            int weight = Math.max(1, readInt(entry, "weight", 10));
            String resolvedId = id.isBlank() ? fallbackId(location, "pacify", index) : id;
            putEntry(location, "pacify line", resolvedId, new PacifyLine(
                    resolvedId,
                    entryLines,
                    professions,
                    dispositions,
                    outcomes,
                    VillagerEquipmentCondition.read(entry),
                    weight
            ), lines, pacifySources);
            index++;
        }
    }

    private static <T> void putEntry(
            ResourceLocation location,
            String systemName,
            String id,
            T entry,
            Map<String, T> entries,
            Map<String, ResourceLocation> sources) {
        ResourceLocation previousLocation = sources.put(id, location);
        if (previousLocation != null) {
            DatapackDiagnostics.warnDuplicateId(location, systemName, id, previousLocation);
        }
        entries.put(id, entry);
    }

    private static void applyDialogueOptions(
            ResourceLocation location,
            String context,
            DialogueLine.Builder builder,
            JsonObject entry,
            Set<VillagerProfession> defaultProfessions) {
        Set<VillagerProfession> professions = readProfessions(location, context, entry, defaultProfessions);
        if (!professions.isEmpty()) {
            builder.professions(professions.toArray(VillagerProfession[]::new));
        }

        Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
        if (!dispositions.isEmpty()) {
            builder.dispositions(dispositions.toArray(DialogueDisposition[]::new));
        }

        Set<VillagerMood> moods = EnumSet.noneOf(VillagerMood.class);
        moods.addAll(readEnumSet(entry, "moods", VillagerMood.class));
        readEnum(entry, "mood", VillagerMood.class).ifPresent(moods::add);
        if (!moods.isEmpty()) {
            builder.moods(moods.toArray(VillagerMood[]::new));
            builder.minMoodIntensity(readInt(entry, "min_mood_intensity", 0));
        }

        SocialAttributeCondition socialAttributeCondition = readSocialAttributeCondition(entry);
        if (!socialAttributeCondition.isEmpty()) {
            builder.socialAttributeCondition(socialAttributeCondition);
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
        Set<ResourceLocation> eventTagIds = readMemoryTagIds(location, context, entry, "event_tags");
        if (!eventTagIds.isEmpty()) {
            builder.eventTagIds(eventTagIds.toArray(ResourceLocation[]::new));
        }

        Set<VillageEventMemory.EventTag> playerEventTags = readEnumSet(entry, "player_event_tags", VillageEventMemory.EventTag.class);
        if (!playerEventTags.isEmpty()) {
            builder.playerEventTags(playerEventTags.toArray(VillageEventMemory.EventTag[]::new));
        }
        Set<ResourceLocation> playerEventTagIds = readMemoryTagIds(location, context, entry, "player_event_tags");
        if (!playerEventTagIds.isEmpty()) {
            builder.playerEventTagIds(playerEventTagIds.toArray(ResourceLocation[]::new));
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
        builder.equipmentCondition(VillagerEquipmentCondition.read(entry));
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
        List<String> recruitmentMemoryBiomes = new ArrayList<>();
        recruitmentMemoryBiomes.addAll(readStringList(entry, "recruitment_memory_biome"));
        recruitmentMemoryBiomes.addAll(readStringList(entry, "recruitment_memory_biomes"));
        if (!recruitmentMemoryBiomes.isEmpty()) {
            builder.recruitmentMemoryBiomeKeys(recruitmentMemoryBiomes.toArray(String[]::new));
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
        builder.conditions(DialogueCondition.readList(location, context, entry));
        builder.showForAdults(readBoolean(entry, "show_for_adults", true));
        builder.showForBabies(readBoolean(entry, "show_for_babies", true));
        readEnum(entry, "gift_advice", GiftAdviceKind.class).ifPresent(builder::giftAdviceKind);
        builder.textEffects(DialogueTextEffects.read(entry));
        List<String> optionIds = new ArrayList<>();
        optionIds.addAll(readStringList(entry, "option"));
        optionIds.addAll(readStringList(entry, "option_ids"));
        if (!optionIds.isEmpty()) {
            builder.optionIds(optionIds.toArray(String[]::new));
        }

        builder.priority(readInt(entry, "priority", 0));
        builder.category(readString(entry, "category"));
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

    private static Set<VillagerProfession> readProfessions(
            ResourceLocation location,
            String context,
            JsonObject entry,
            Set<VillagerProfession> defaultProfessions) {
        Set<VillagerProfession> professions = java.util.HashSet.newHashSet(defaultProfessions.size() + 1);
        professions.addAll(defaultProfessions);
        for (String value : readStringList(entry, "professions")) {
            Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(value);
            if (profession.isPresent()) {
                professions.add(profession.get());
            } else {
                DatapackDiagnostics.warnUnknownProfession(location, context, value);
            }
        }
        return Set.copyOf(professions);
    }

    private static String entryContext(String kind, JsonObject entry, int index) {
        String id = readString(entry, "id");
        return id.isBlank() ? kind + "[" + index + "]" : kind + " \"" + id + "\"";
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

    private static Set<ResourceLocation> readMemoryTagIds(
            ResourceLocation location,
            String context,
            JsonObject entry,
            String key) {
        Set<ResourceLocation> values = new java.util.LinkedHashSet<>();
        for (String value : readStringList(entry, key)) {
            VillageEventMemory.parseTagId(value).ifPresentOrElse(
                    values::add,
                    () -> DatapackDiagnostics.warnInvalidDialogueCondition(
                            location,
                            context,
                            key + " references invalid memory tag \"" + value + "\"."));
        }
        return Set.copyOf(values);
    }

    private static SocialAttributeCondition readSocialAttributeCondition(JsonObject entry) {
        SocialAttributeCondition.Builder builder = SocialAttributeCondition.builder();
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            String key = attribute.serializedName();
            if (readBoolean(entry, "requires_high_" + key)) {
                builder.min(attribute, 60);
            }
            Integer minValue = readNullableInt(entry, "min_" + key);
            if (minValue != null) {
                builder.min(attribute, minValue);
            }
            Integer maxValue = readNullableInt(entry, "max_" + key);
            if (maxValue != null) {
                builder.max(attribute, maxValue);
            }
        }
        return builder.build();
    }

    private static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        return DatapackJsonReader.readEnum(entry, key, enumClass);
    }

    private static <E extends Enum<E>> Optional<E> readEnum(String value, Class<E> enumClass) {
        return DatapackJsonReader.readEnum(value, enumClass);
    }

    private static List<String> readStringList(JsonObject entry, String key) {
        return DatapackJsonReader.readStringList(entry, key);
    }

    private static List<String> readLines(JsonObject entry) {
        return DatapackJsonReader.readLines(entry);
    }

    private static String readString(JsonObject entry, String key) {
        return DatapackJsonReader.readString(entry, key);
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        return DatapackJsonReader.readInt(entry, key, fallback);
    }

    private static Integer readNullableInt(JsonObject entry, String key) {
        return DatapackJsonReader.readNullableInt(entry, key);
    }

    private static boolean readBoolean(JsonObject entry, String key) {
        return DatapackJsonReader.readBoolean(entry, key);
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        return DatapackJsonReader.readBoolean(entry, key, fallback);
    }

    private static boolean hasBoolean(JsonObject entry, String key) {
        return DatapackJsonReader.hasPrimitive(entry, key);
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

    public static String resolveTemplate(String text, Map<String, String> replacements) {
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
            List<String> lines,
            boolean showForAdults,
            boolean showForBabies,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            VillagerEquipmentCondition equipmentCondition,
            int weight,
            boolean firstConversationOnly,
            boolean firstVillageInteractionOnly) {
        private String selectText(RandomSource random) {
            return this.lines.get(random.nextInt(this.lines.size()));
        }

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
            if (!this.equipmentCondition.matches(context.villager())) {
                return false;
            }
            if (this.firstConversationOnly && (!context.firstConversation() || context.hasKnownLastSeenDay())) {
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
            List<String> lines,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            Set<VillagerPacificationResult> outcomes,
            VillagerEquipmentCondition equipmentCondition,
            int weight) {
        private String selectText(RandomSource random) {
            return this.lines.get(random.nextInt(this.lines.size()));
        }

        private boolean matches(DialogueContext context, VillagerPacificationResult result) {
            if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
                return false;
            }
            if (!this.equipmentCondition.matches(context.villager())) {
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
            List<String> lines,
            boolean showForAdults,
            boolean showForBabies,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            VillagerEquipmentCondition equipmentCondition,
            int weight) {
        private String selectText(RandomSource random) {
            return this.lines.get(random.nextInt(this.lines.size()));
        }

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
            if (!this.equipmentCondition.matches(context.villager())) {
                return false;
            }
            return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
        }

        private boolean professionSpecific() {
            return !this.professions.isEmpty();
        }
    }
}
