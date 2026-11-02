package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.CandidateArbitrator;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates;
import com.jvn.villagerretaliation.dialogue.DialogueWordHistory;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
import com.jvn.villagerretaliation.dialogue.resources.DialogueTextTemplateResources;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.runtime.ReputationProfile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class GreetingReviewChecks {
    private static final ResourceLocation ID = ResourceLocation.parse("villagerretaliation:greetings/shared");
    private static final List<String> PROFESSIONS = List.of("shared", "armorer", "butcher", "cartographer", "cleric",
            "farmer", "fisherman", "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd",
            "toolsmith", "unemployed", "weaponsmith");
    private static final List<String> PATHS = List.of("return_greeting", "brief_hello", "player_well",
            "player_steady", "player_struggling", "player_private");
    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("ordinary", VillagerMood.NEUTRAL, VillagerReputationLevel.NEUTRAL, false),
            new Scenario("friendly", VillagerMood.CONTENT, VillagerReputationLevel.TRUSTED, false),
            new Scenario("stressed", VillagerMood.STRESSED, VillagerReputationLevel.NEUTRAL, false),
            new Scenario("guarded", VillagerMood.SUSPICIOUS, VillagerReputationLevel.SUSPICIOUS, false),
            new Scenario("afraid", VillagerMood.AFRAID, VillagerReputationLevel.NEUTRAL, false),
            new Scenario("grieving", VillagerMood.GRIEVING, VillagerReputationLevel.NEUTRAL, false),
            new Scenario("injured", VillagerMood.NEUTRAL, VillagerReputationLevel.NEUTRAL, true));

    private record Scenario(String name, VillagerMood mood, VillagerReputationLevel reputation, boolean injured) { }

    private static final ReputationProfile<VillagerReputationLevel> REPUTATION = defaultReputationProfile();

    private GreetingReviewChecks() { }

    public static int run(Path directory) throws IOException {
        var tree = read("dialogue_trees/en_us/greetings/shared.json");
        var library = DialogueTextTemplateResources.library(null, "en_us");
        var global = expand(read("dialogue/en_us/global/messages/greetings/shared.json"), library);
        var contextCache = new LinkedHashMap<ResourceLocation, com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.Rule>();
        Function<ResourceLocation, com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.Rule> contexts =
                id -> contextCache.computeIfAbsent(id, PreviewWordPools::bundledContext);
        var cache = new LinkedHashMap<ResourceLocation, DialogueWordPool>();
        Function<ResourceLocation, DialogueWordPool> pools = id -> cache.computeIfAbsent(id, key -> PreviewWordPools.bundled(key, "en_us"));
        var transcripts = new StringBuilder("Greeting conversation review\nReproduce with .\\gradlew.bat verifyAuthoring\n\n");
        var coverage = new StringBuilder("Greeting phrase coverage\nExisting mood, reputation, speaking-style and boundary sampler with default thresholds\n\n");
        var seenNodes = new java.util.TreeSet<String>();
        var seenChoices = new java.util.TreeSet<String>();
        long seed = 0;
        int conversations = 0;
        for (String profession : PROFESSIONS) {
            var messages = new ArrayList<JsonObject>();
            for (var entry : global.getAsJsonArray("messages")) messages.add(entry.getAsJsonObject());
            if (!profession.equals("shared")) for (var entry : expand(read("dialogue/en_us/professions/" + profession
                    + "/messages/greetings/shared.json"), library).getAsJsonArray("messages")) messages.add(entry.getAsJsonObject());
            for (var scenario : SCENARIOS) for (String path : PATHS) {
                var conditions = conditions(profession, scenario, contexts);
                var history = new DialogueWordHistory();
                long conversationSeed = ++seed;
                transcripts.append(profession).append(" / ").append(scenario.name()).append(" / ").append(path)
                        .append(" / seed ").append(conversationSeed).append('\n');
                try (var session = new DialoguePreviewSession(tree, ID, conditions,
                        key -> message(messages, key, conditions, conversationSeed))) {
                    session.start("");
                    choose(session, "ordinary", false, transcripts);
                    drain(session, pools, conditions, conversationSeed, history, transcripts);
                    require(session.choices().size() == 3, "greeting must offer three responses");
                    if (path.startsWith("player_")) {
                        choose(session, "ask_wellbeing", true, transcripts);
                        drain(session, pools, conditions, conversationSeed + 1, history, transcripts);
                        require(session.choices().size() == 4, "wellbeing must offer four responses");
                    }
                    choose(session, path, true, transcripts);
                    drain(session, pools, conditions, conversationSeed + 2, history, transcripts);
                    require(session.choices().isEmpty() && !session.hasNextLine(), "greeting path must end");
                    var visited = session.drainCoverage();
                    seenNodes.addAll(visited.nodes()); seenChoices.addAll(visited.choices());
                }
                transcripts.append('\n');
                conversations++;
            }
            var conditions = conditions(profession, SCENARIOS.getFirst(), contexts);
            for (String key : List.of("greeting.shared.ordinary", "greeting.shared.wellbeing")) {
                String template = message(messages, key, conditions, 1);
                var report = WordPoolPreview.coverage(template, pools, conditions, 1, REPUTATION);
                coverage.append(profession).append(" / ").append(key).append(" / ").append(report.rows().size()).append(" samples\n");
                for (var finding : report.findings()) coverage.append("  ").append(finding.kind()).append(": ").append(finding.message()).append('\n');
                require(report.rows().stream().allMatch(row -> row.result().diagnostics().isEmpty()), "coverage contains grammar or resolution diagnostics: " + profession + " " + key);
                coverage.append('\n');
            }
        }
        require(seenNodes.size() == 8 && seenChoices.size() == 8, "all greeting nodes and response paths must be reviewed");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("transcripts.txt"), transcripts, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("coverage.txt"), coverage, StandardCharsets.UTF_8);
        System.out.println("Reviewed " + conversations + " greeting conversations across " + PROFESSIONS.size()
                + " owners and " + SCENARIOS.size() + " scenarios: " + directory.toAbsolutePath());
        return 1;
    }

    private static ReputationProfile<VillagerReputationLevel> defaultReputationProfile() {
        var defaults = new com.jvn.villagerretaliation.config.VillagerRetaliationConfigModel.Reputation();
        return new ReputationProfile<>(VillagerReputationLevel.NEUTRAL, List.of(
                new ReputationProfile.Tier<>(VillagerReputationLevel.ROYALTY, defaults.royaltyThreshold, true),
                new ReputationProfile.Tier<>(VillagerReputationLevel.REVERED, defaults.reveredThreshold, true),
                new ReputationProfile.Tier<>(VillagerReputationLevel.RESPECTED, defaults.respectedThreshold, true),
                new ReputationProfile.Tier<>(VillagerReputationLevel.TRUSTED, defaults.trustedThreshold, true),
                new ReputationProfile.Tier<>(VillagerReputationLevel.FEARED, defaults.fearedThreshold, false),
                new ReputationProfile.Tier<>(VillagerReputationLevel.DESPISED, defaults.despisedThreshold, false),
                new ReputationProfile.Tier<>(VillagerReputationLevel.HOSTILE, defaults.hostileThreshold, false),
                new ReputationProfile.Tier<>(VillagerReputationLevel.SUSPICIOUS, defaults.suspiciousThreshold, false)));
    }

    private static PreviewConditions conditions(String profession, Scenario scenario,
            Function<ResourceLocation, com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources.Rule> contexts) {
        var result = new PreviewConditions();
        result.contexts(contexts);
        result.profession(profession.equals("unemployed") || profession.equals("shared") ? "none" : profession);
        result.mood(scenario.mood(), 60);
        int score = scenario.reputation() == VillagerReputationLevel.NEUTRAL ? 0 : REPUTATION.tiers().stream()
                .filter(tier -> tier.value() == scenario.reputation()).findFirst().orElseThrow().threshold().getAsInt();
        result.reputation(score, scenario.reputation());
        result.speakingStyle(com.jvn.villagerretaliation.dialogue.SpeakingStyle.STYLES.get(SCENARIOS.indexOf(scenario) % 4));
        result.override("villager_age", ConditionOutcome.MATCHED);
        result.override("health", scenario.injured() ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET);
        return result;
    }

    private static void choose(DialoguePreviewSession session, String id, boolean spoken, StringBuilder transcript) {
        int index = java.util.stream.IntStream.range(0, session.choices().size()).filter(i -> session.choices().get(i).id().equals(id)).findFirst().orElseThrow();
        var choice = session.choices().get(index);
        require(choice.available(), "response must be available: " + id);
        transcript.append("Choice: ").append(choice.label()).append('\n');
        session.choose(index);
        if (spoken) require(session.currentLine() != null
                && choice.label().equals(session.currentLine().get("resolved_text").getAsString()), "selected response and spoken text must agree");
    }

    private static void drain(DialoguePreviewSession session, Function<ResourceLocation, DialogueWordPool> pools,
            PreviewConditions conditions, long seed, DialogueWordHistory history, StringBuilder transcript) {
        for (int step = 0; step < 32; step++) {
            var line = session.currentLine();
            require(line != null, "greeting exchange needs a line");
            String template = line.get("resolved_text").getAsString();
            var result = WordPoolPreview.inspect(template, pools, conditions, seed + step, history, "review");
            require(result.diagnostics().isEmpty(), "greeting diagnostics: " + result.diagnostics());
            require(!result.text().isBlank() && !result.text().contains("{") && !result.text().startsWith("greeting."),
                    "unresolved or empty greeting: " + result.text());
            require(result.text().replaceAll("<[^>]+>", "").strip().matches("(?s).*[.!?]$"),
                    "greeting must end with sentence punctuation: " + result.text());
            String speaker = line.getAsJsonObject("speaker").get("type").getAsString().equals("player") ? "Player" : "Villager";
            transcript.append(speaker).append(": ").append(result.text()).append('\n');
            for (var selection : result.selections()) transcript.append("  ").append(selection.pool()).append(" -> ")
                    .append(selection.entry().id()).append(" [").append(selection.entry().source()).append("]")
                    .append(" priority=").append(selection.entry().priority()).append(" eligible=").append(selection.eligible().size())
                    .append(selection.fallback() ? " fallback" : "").append('\n');
            if (!session.hasNextLine()) return;
            session.advance();
        }
        throw new AssertionError("greeting exchange exceeded its step budget");
    }

    private static String message(List<JsonObject> messages, String key, PreviewConditions conditions, long seed) {
        var matching = messages.stream().filter(entry -> key.equals(entry.get("key").getAsString())
                && conditions.evaluate(ID, entry) == ConditionOutcome.MATCHED).toList();
        var message = select(matching, conditions, seed);
        if (message.has("text")) return message.get("text").getAsString();
        var variants = message.getAsJsonArray("variants").asList().stream().map(JsonElement::getAsJsonObject).toList();
        return select(variants, conditions, seed).get("text").getAsString();
    }

    private static JsonObject select(List<JsonObject> entries, PreviewConditions conditions, long seed) {
        var candidates = entries.stream().map(entry -> new CandidateArbitrator.Candidate<>(
                entry.has("id") ? entry.get("id").getAsString() : "", entry,
                entry.has("priority") ? entry.get("priority").getAsInt() : 0,
                entry.has("chance") ? entry.get("chance").getAsDouble() : 1,
                entry.has("weight") ? entry.get("weight").getAsLong() : 10,
                conditions.evaluate(ID, entry) == ConditionOutcome.MATCHED)).toList();
        return CandidateArbitrator.select(candidates, RandomSource.create(seed), ignored -> true).orElseThrow();
    }

    private static JsonObject expand(JsonObject source, DialoguePresentationTemplates.Library library) {
        return DialoguePresentationTemplates.expandDocument(source, library, (pointer, issue) -> { throw new AssertionError(pointer + ": " + issue); });
    }

    private static JsonObject read(String path) throws IOException {
        var stream = GreetingReviewChecks.class.getResourceAsStream("/data/villagerretaliation/" + path);
        if (stream == null) throw new IOException("Missing greeting resource: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
