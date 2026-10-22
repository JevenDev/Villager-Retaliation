package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueExchange;
import com.jvn.villagerretaliation.dialogue.DialogueTemplates;
import com.jvn.villagerretaliation.dialogue.DialogueWordPools;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_words")
@PrefixGameTestTemplate(false)
public final class DialogueWordPoolGameTests {
    private static final String TOKEN = "{word:villagerretaliation:mood/content}";
    private static final ResourceLocation ID = ResourceLocation.parse("custom:greetings/friendly");

    private DialogueWordPoolGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void seededWordsShareChoicesWithoutChangingLegacyRandomness(GameTestHelper helper) {
        var values = List.of("happy", "glad", "in good spirits");
        var random = RandomSource.create(42L);
        var expected = RandomSource.create(42L);
        String choice = values.get(expected.nextInt(values.size()));
        var issues = new ArrayList<String>();
        var resolver = DialogueWordPools.resolver(id -> values, random, issues::add);
        String text = resolver.apply(TOKEN + " and " + TOKEN);
        helper.assertValueEqual(text, choice + " and " + choice, "one choice per pool per line");
        helper.assertValueEqual(random.nextLong(), expected.nextLong(), "repeated reference consumes one roll");
        var untouched = RandomSource.create(87L);
        var control = RandomSource.create(87L);
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("Hello {player}.", Map.of("player", "Alex"),
                helper.getLevel().getServer(), "en_us", untouched), "Hello Alex.", "legacy placeholders still resolve");
        helper.assertValueEqual(untouched.nextLong(), control.nextLong(), "legacy lines do not consume a word roll");
        helper.assertTrue(issues.isEmpty(), "valid words produce no diagnostics");
        DialogueWordPoolResources.warm(helper.getLevel().getServer());
        helper.assertValueEqual(DialogueTemplates.resolveTemplate(TOKEN, Map.of(), null, "en_us", null), TOKEN,
                "rendering without a server preserves unresolved text even after another server warms the cache");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void invalidPoolsAndReferencesAreDiagnosedWithoutDeletingText(GameTestHelper helper) {
        for (String json : List.of("{}", "{\"values\":[]}", "{\"values\":[1]}", "{\"values\":[\" \"]}",
                "{\"values\":[\"{player}\"]}", "{\"values\":[\"<aqua>glad</aqua>\"]}")) {
            boolean rejected = false;
            try { DialogueWordPoolResources.readValues(JsonParser.parseString(json).getAsJsonObject()); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid pool rejected: " + json);
        }
        var issues = new ArrayList<String>();
        var resolver = DialogueWordPools.resolver(id -> List.of(), RandomSource.create(1), issues::add);
        for (String token : List.of("{word}", "{word:}", "{word:custom:pool", "{word:Bad:pool}", "{word:custom:missing}"))
            helper.assertValueEqual(resolver.apply(token), token, "unresolved reference is preserved");
        helper.assertValueEqual(issues.size(), 5, "malformed and missing references are diagnosed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void effectsCuesAndNetworkTextKeepPreparedWords(GameTestHelper helper) {
        var resolver = DialogueWordPools.resolver(id -> List.of("in good spirits"), RandomSource.create(2), issue -> helper.fail(issue));
        var segments = DialogueTextSegment.parse("<aqua>" + TOKEN + "<pause:5></aqua> " + TOKEN, DialogueTextEffects.NONE)
                .stream().map(segment -> segment.mapText(resolver)).toList();
        helper.assertValueEqual(DialogueTextSegment.plainText(segments), "in good spirits in good spirits", "word choice shared across segments");
        helper.assertValueEqual(segments.getFirst().effects().color(), 0x55FFFF, "aqua formatting survives");
        helper.assertValueEqual(segments.getFirst().cues().getFirst().offset(), "in good spirits".length(), "cue follows expanded word");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DialogueTextSegment.writeList(buffer, segments);
            helper.assertValueEqual(DialogueTextSegment.readList(buffer), segments, "redisplay receives the prepared wording and cues");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void packOverridesLocaleFallbackAndCacheReloadUseEffectiveResources(GameTestHelper helper) throws Exception {
        Path root = Files.createTempDirectory("vr-word-pools-");
        try {
            Path low = root.resolve("low"), high = root.resolve("high");
            String resource = "data/custom/dialogue_words/en_us/greetings/friendly.json";
            Files.createDirectories(low.resolve(resource).getParent());
            Files.createDirectories(high.resolve(resource).getParent());
            Files.writeString(low.resolve(resource), "{\"values\":[\"old\"]}");
            Files.writeString(high.resolve(resource), "{\"values\":[\"override\"]}");
            Path translated = high.resolve("data/custom/dialogue_words/fr_fr/greetings/friendly.json");
            Files.createDirectories(translated.getParent());
            Files.writeString(translated, "{\"values\":[\"heureux\"]}");
            try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack("low", low), pack("high", high)))) {
                ServerResourceCache<Map<String, Map<ResourceLocation, com.jvn.villagerretaliation.dialogue.DialogueWordPool>>> cache = ServerResourceCache.create(Map::of,
                        server -> DialogueWordPoolResources.read(manager.listResources("dialogue_words", id -> id.getPath().endsWith(".json"))
                                .entrySet().stream().map(entry -> new DatapackResourceLoader.JsonResource(entry.getKey(), entry.getValue())).toList()));
                var server = helper.getLevel().getServer();
                helper.assertValueEqual(DialogueWordPoolResources.values(cache.get(server), "en_us", ID), List.of("override"), "higher pack replaces pool");
                helper.assertValueEqual(DialogueWordPoolResources.values(cache.get(server), "fr-FR", ID), List.of("heureux"), "requested locale wins");
                helper.assertValueEqual(DialogueWordPoolResources.values(cache.get(server), "de_de", ID), List.of("override"), "missing locale falls back per pool");
                Files.writeString(high.resolve(resource), "{\"values\":[\"reloaded\"]}");
                helper.assertValueEqual(DialogueWordPoolResources.values(cache.get(server), "en_us", ID), List.of("override"), "loaded snapshot stays stable");
                cache.clear();
                helper.assertValueEqual(DialogueWordPoolResources.values(cache.get(server), "en_us", ID), List.of("reloaded"), "cache invalidation reads edited pool");
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void exchangesAndPairCertificationUseLiteralWordPools(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        String template = "I'm feeling <aqua>" + TOKEN + "</aqua> this {time_of_day}.";
        var speech = new DialogueExchange(SpeakerKind.ROLE, template, "").resolve(context, Map.of());
        helper.assertFalse(speech.text().contains("{word"), "exchange expands words before formatting");
        helper.assertFalse(speech.text().contains("{time_of_day}"), "ordinary placeholders work alongside pools");
        var words = DialogueWordPoolResources.values(player.server, context.locale(), ResourceLocation.parse("villagerretaliation:mood/content"));
        helper.assertTrue(words.stream().anyMatch(speech.text()::contains), "exchange contains a pool value");
        helper.assertTrue(PairedDialogueService.renderTemplate("Hello " + TOKEN, Map.of()).isPresent(), "pair candidates retain unresolved words without rolling");
        helper.assertFalse(PairedDialogueService.renderTemplate("Hello {unknown}", Map.of()).isPresent(), "other unresolved placeholders remain invalid");
        helper.assertTrue(DialogueWordPools.matches(TOKEN + " " + TOKEN, "glad glad", id -> words), "certification accepts repeated selected word");
        helper.assertFalse(DialogueWordPools.matches(TOKEN + " " + TOKEN, "happy glad", id -> words), "certification rejects inconsistent choices");
        helper.assertFalse(DialogueWordPools.matches(TOKEN, "angry", id -> words), "certification rejects unrelated wording");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void previewReadsUnsavedCustomPoolsAndLocaleFallback(GameTestHelper helper) throws Exception {
        Path root = Files.createTempDirectory("vr-word-preview-");
        try {
            var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(root);
            var pool = new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(
                    com.jvn.villagerretaliation.authoring.core.DefinitionKind.RESOURCE,
                    "data/custom/dialogue_words/en_us/greetings/friendly.json",
                    JsonParser.parseString("{\"values\":[\"unsaved greeting\"]}").getAsJsonObject());
            workspace.add(pool);
            var owner = new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(
                    com.jvn.villagerretaliation.authoring.core.DefinitionKind.TREE,
                    "data/custom/dialogue_trees/en_us/hello.json", new com.google.gson.JsonObject());
            var lookup = com.jvn.villagerretaliation.authoring.preview.PreviewWordPools.lookup(
                    workspace, owner, "fr_fr", (id, language) -> id.equals(ID) ? List.of() : List.of("fallback"));
            helper.assertValueEqual(lookup.apply(ID), List.of("unsaved greeting"), "preview uses unsaved workspace pool with locale fallback");
            helper.assertValueEqual(lookup.apply(ResourceLocation.parse("custom:missing")), List.of("fallback"), "preview delegates absent pools");
            var translated = com.jvn.villagerretaliation.authoring.preview.PreviewWordPools.lookup(
                    workspace, owner, "fr_fr", (id, language) -> language.equals("fr_fr") ? List.of("bonjour") : List.of());
            helper.assertValueEqual(translated.apply(ID), List.of("bonjour"), "loaded requested locale precedes workspace English fallback");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void speakingStylesChooseWeightedPhrasesAndKeepFallbacks(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        String style = com.jvn.villagerretaliation.dialogue.SpeakingStyle.forSpeaker(villager.getUUID());
        var pool = com.jvn.villagerretaliation.dialogue.DialogueWordPool.read(JsonParser.parseString(
                "{\"values\":[\"fallback\",{\"text\":\"chosen\",\"weight\":3,\"conditions\":[{\"type\":\"speaking_style\",\"style\":\""
                        + style + "\"}]}]}").getAsJsonObject());
        helper.assertValueEqual(pool.select(context, RandomSource.create(1)), "chosen", "matching style replaces the fallback");
        helper.assertValueEqual(pool.select(null, RandomSource.create(1)), "fallback", "missing context uses safe fallback");
        var saved = villager.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var restored = EntityType.VILLAGER.create(helper.getLevel());
        restored.load(saved);
        helper.assertValueEqual(com.jvn.villagerretaliation.dialogue.SpeakingStyle.forSpeaker(restored.getUUID()), style,
                "save and reload preserve the voice");
        var weighted = com.jvn.villagerretaliation.dialogue.DialogueWordPool.read(JsonParser.parseString(
                "{\"values\":[{\"text\":\"rare\",\"weight\":1},{\"text\":\"common\",\"weight\":9}]}").getAsJsonObject());
        int common = 0;
        var random = RandomSource.create(12);
        for (int i = 0; i < 1000; i++) if (weighted.select(null, random).equals("common")) common++;
        helper.assertTrue(common > 850 && common < 950, "weights influence phrase frequency");
        helper.succeed();
    }

    private static PathPackResources pack(String id, Path directory) {
        return new PathPackResources(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()), directory);
    }
}
