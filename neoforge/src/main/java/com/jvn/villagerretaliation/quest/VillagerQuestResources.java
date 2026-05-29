package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerQuestResources {
    private static final String RESOURCE_ROOT = "quests";
    private static final int DEFAULT_STRUCTURE_SEARCH_RADIUS = 256;
    private static final int DEFAULT_DISCOVERY_RADIUS = 128;

    private static volatile CachedQuests cachedQuests = new CachedQuests(null, Map.of());

    private VillagerQuestResources() {
    }

    public static void warm(MinecraftServer server) {
        quests(server);
    }

    public static void clearCache() {
        cachedQuests = new CachedQuests(null, Map.of());
    }

    public static Collection<QuestDefinition> quests(MinecraftServer server) {
        return load(server).values();
    }

    public static Optional<QuestDefinition> quest(MinecraftServer server, ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(load(server).get(id));
    }

    private static Map<ResourceLocation, QuestDefinition> load(MinecraftServer server) {
        CachedQuests current = cachedQuests;
        if (current.server() == server) {
            return current.quests();
        }

        synchronized (VillagerQuestResources.class) {
            current = cachedQuests;
            if (current.server() == server) {
                return current.quests();
            }

            Map<ResourceLocation, QuestDefinition> quests = read(server);
            cachedQuests = new CachedQuests(server, quests);
            return quests;
        }
    }

    private static Map<ResourceLocation, QuestDefinition> read(MinecraftServer server) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(RESOURCE_ROOT, location -> location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), quests, sources));
        return Map.copyOf(quests);
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, ResourceLocation> sources) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            ResourceLocation fallbackId = fallbackQuestId(location);
            QuestDefinition definition = readQuest(location, root, fallbackId);
            if (definition == null) {
                return;
            }
            ResourceLocation previous = sources.put(definition.id(), location);
            if (previous != null) {
                DatapackDiagnostics.warnDuplicateId(location, "quest", definition.id().toString(), previous);
            }
            quests.put(definition.id(), definition);
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            DatapackDiagnostics.warnSkippedFile(location, "quest", exception);
        }
    }

    private static QuestDefinition readQuest(ResourceLocation location, JsonObject root, ResourceLocation fallbackId) {
        ResourceLocation id = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
        if (id == null) {
            return null;
        }

        JsonObject display = DatapackJsonReader.readObject(root, "display");
        String title = firstNonBlank(
                DatapackJsonReader.readString(root, "title"),
                display == null ? "" : DatapackJsonReader.readString(display, "title"));
        String description = firstNonBlank(
                DatapackJsonReader.readString(root, "description"),
                display == null ? "" : DatapackJsonReader.readString(display, "description"));
        ResourceLocation parent = DatapackJsonReader.readResourceLocation(root, "parent").orElse(null);

        return new QuestDefinition(
                id,
                title,
                description,
                DatapackJsonReader.readString(root, "questline", "questline_id"),
                parent,
                readOffer(location, root),
                readTarget(root),
                readRewards(root),
                readDialogue(root)
        );
    }

    private static QuestDefinition.Offer readOffer(ResourceLocation location, JsonObject root) {
        JsonObject offer = DatapackJsonReader.readObject(root, "offer");
        if (offer == null) {
            return QuestDefinition.Offer.any();
        }

        Set<VillagerProfession> professions = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(offer, "profession", "professions", "offered_by")) {
            Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(value);
            if (profession.isPresent()) {
                professions.add(profession.get());
            } else {
                DatapackDiagnostics.warnUnknownProfession(location, "quest offer", value);
            }
        }

        int minLevel = readVillagerLevel(offer, "min_villager_level", readVillagerLevel(offer, "min_level", 1));
        return new QuestDefinition.Offer(
                professions,
                minLevel,
                readSkillRequirements(offer)
        );
    }

    private static Map<VillagerSkill, Integer> readSkillRequirements(JsonObject offer) {
        JsonElement element = offer.get("skills");
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }

        Map<VillagerSkill, Integer> skills = new LinkedHashMap<>();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonObject()) {
                    JsonObject entry = child.getAsJsonObject();
                    VillagerSkill skill = VillagerSkill.bySerializedName(DatapackJsonReader.readString(entry, "skill"));
                    if (skill != null) {
                        skills.put(skill, Math.max(1, DatapackJsonReader.readInt(entry, "min", 1)));
                    }
                }
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                VillagerSkill skill = VillagerSkill.bySerializedName(entry.getKey());
                if (skill == null) {
                    continue;
                }
                int min = entry.getValue().isJsonObject()
                        ? DatapackJsonReader.readInt(entry.getValue().getAsJsonObject(), "min", 1)
                        : DatapackJsonReader.readInt(entry.getValue(), 1);
                skills.put(skill, Math.max(1, min));
            }
        }
        return Map.copyOf(skills);
    }

    private static QuestDefinition.Target readTarget(JsonObject root) {
        JsonObject target = DatapackJsonReader.readObject(root, "target");
        ResourceLocation structure = target == null
                ? null
                : DatapackJsonReader.readResourceLocation(target, "structure").orElse(null);
        List<String> pieces = target == null
                ? List.of()
                : DatapackJsonReader.readStringList(target, "piece", "pieces", "structure_piece", "structure_pieces");
        int searchRadius = target == null
                ? DEFAULT_STRUCTURE_SEARCH_RADIUS
                : DatapackJsonReader.readInt(target, "search_radius", DEFAULT_STRUCTURE_SEARCH_RADIUS);
        int discoveryRadius = target == null
                ? DEFAULT_DISCOVERY_RADIUS
                : DatapackJsonReader.readInt(target, "discovery_radius", DEFAULT_DISCOVERY_RADIUS);
        ResourceLocation proofItem = target == null
                ? null
                : DatapackJsonReader.readResourceLocation(target, "proof_item").orElse(null);

        JsonObject criteria = DatapackJsonReader.readObject(root, "criteria");
        if (criteria != null) {
            TargetParts parts = readTargetCriteria(criteria, structure, pieces, proofItem);
            structure = parts.structure();
            pieces = parts.pieces();
            proofItem = parts.proofItem();
        }

        return new QuestDefinition.Target(structure, pieces, searchRadius, discoveryRadius, proofItem);
    }

    private static TargetParts readTargetCriteria(
            JsonObject criteria,
            ResourceLocation structure,
            List<String> pieces,
            ResourceLocation proofItem) {
        for (Map.Entry<String, JsonElement> entry : criteria.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject criterion = entry.getValue().getAsJsonObject();
            String trigger = DatapackJsonReader.readString(criterion, "trigger").toLowerCase(Locale.ROOT);
            if (structure == null && trigger.contains("structure")) {
                structure = DatapackJsonReader.readResourceLocation(criterion, "structure").orElse(null);
                List<String> criterionPieces = DatapackJsonReader.readStringList(criterion, "piece", "pieces", "structure_piece", "structure_pieces");
                if (!criterionPieces.isEmpty()) {
                    pieces = criterionPieces;
                }
            }
            if (proofItem == null && (trigger.contains("inventory") || criterion.has("item") || criterion.has("proof_item"))) {
                proofItem = DatapackJsonReader.readResourceLocation(criterion, "proof_item")
                        .or(() -> DatapackJsonReader.readResourceLocation(criterion, "item"))
                        .orElse(null);
            }
        }
        return new TargetParts(structure, pieces, proofItem);
    }

    private static QuestDefinition.Rewards readRewards(JsonObject root) {
        JsonObject rewards = DatapackJsonReader.readObject(root, "rewards");
        if (rewards == null) {
            return QuestDefinition.Rewards.EMPTY;
        }
        return new QuestDefinition.Rewards(
                Math.max(0, DatapackJsonReader.readInt(rewards, "experience", DatapackJsonReader.readInt(rewards, "xp", 0))),
                DatapackJsonReader.readInt(rewards, "reputation", 0),
                DatapackJsonReader.readInt(rewards, "gossip_reputation", 0),
                DatapackJsonReader.readResourceLocation(rewards, "loot")
                        .or(() -> DatapackJsonReader.readResourceLocation(rewards, "loot_table"))
                        .orElse(null),
                readMemoryEvent(rewards)
        );
    }

    private static VillageEventMemory.EventTag readMemoryEvent(JsonObject rewards) {
        String value = DatapackJsonReader.readString(rewards, "memory", "memory_event");
        if (value.isBlank()) {
            return null;
        }
        try {
            return VillageEventMemory.EventTag.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static QuestDefinition.Dialogue readDialogue(JsonObject root) {
        JsonObject dialogue = DatapackJsonReader.readObject(root, "dialogue");
        if (dialogue == null) {
            return QuestDefinition.Dialogue.EMPTY;
        }
        return new QuestDefinition.Dialogue(
                readLines(dialogue, "start"),
                readLines(dialogue, "reminder"),
                readLines(dialogue, "turn_in"),
                readLines(dialogue, "already_completed"),
                readLines(dialogue, "unavailable"),
                readLines(dialogue, "missing_target"),
                readLines(dialogue, "missing_proof"),
                readLines(dialogue, "locate_failed")
        );
    }

    private static List<String> readLines(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (element.isJsonArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    String value = child.getAsString().trim();
                    if (!value.isBlank()) {
                        lines.add(value);
                    }
                }
            }
            return List.copyOf(lines);
        }
        if (element.isJsonObject()) {
            return DatapackJsonReader.readLines(element.getAsJsonObject());
        }
        return List.of();
    }

    private static ResourceLocation fallbackQuestId(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(RESOURCE_ROOT + "/") || !path.endsWith(".json")) {
            return null;
        }
        String questPath = path.substring((RESOURCE_ROOT + "/").length(), path.length() - ".json".length());
        return ResourceLocation.tryParse(location.getNamespace() + ":" + questPath);
    }

    private static int readVillagerLevel(JsonObject object, String key, int fallback) {
        String value = DatapackJsonReader.readString(object, key);
        if (value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "novice" -> 1;
            case "apprentice" -> 2;
            case "journeyman" -> 3;
            case "expert" -> 4;
            case "master" -> 5;
            default -> {
                try {
                    yield Math.max(1, Math.min(5, Integer.parseInt(value)));
                } catch (NumberFormatException ignored) {
                    yield fallback;
                }
            }
        };
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record TargetParts(ResourceLocation structure, List<String> pieces, ResourceLocation proofItem) {
    }

    private record CachedQuests(MinecraftServer server, Map<ResourceLocation, QuestDefinition> quests) {
    }
}
