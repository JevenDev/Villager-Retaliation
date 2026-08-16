package com.jvn.villagerretaliation.quest.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.quest.content.bundle.BuiltInQuestBundleCompatibility;
import com.jvn.villagerretaliation.quest.content.bundle.LocalizedReference;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleLocalization;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleRuntimeMaterializer;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestDeterministicLocaleKeys;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocalizationMigration;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class QuestBundleArchitectureGameTests {
    private static final String NS = "examplemod";
    private static final String LINE = "road";
    private static final String SLUG = "alpha";
    private static final String PREFIX = "examplemod.quest.alpha";

    private QuestBundleArchitectureGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_architecture")
    public static void pathsLocalizationAndKeysFollowThePublicContract(GameTestHelper helper) {
        helper.assertTrue(classify(NS, "quests/road/alpha/quest.json").valid(), "quest path rejected");
        helper.assertTrue(classify(NS, "quests/road/alpha/locales/en_us.json").valid(), "locale path rejected");
        helper.assertTrue(classify(NS, "quests/_shared/pools/common.json").valid(), "shared pool rejected");
        helper.assertTrue(!classify(NS, "quests/road/alpha/pools/common.json").valid(), "private pool accepted");
        helper.assertTrue(!classify(NS, "quests/road/alpha/scenes/nested/scene.json").valid(),
                "nested companion accepted");
        helper.assertTrue(!classify(NS, "quests/_shared/alpha/quest.json").valid(),
                "_shared accepted as questline");
        helper.assertTrue(!classify(NS, "quests/road/alpha.json").valid(),
                "loose legacy quest accepted");
        helper.assertTrue(!classify(NS, "quest_messages/en_us.json").valid(),
                "legacy quest messages accepted");
        helper.assertTrue(!classify(NS, "quest_scenes/alpha.json").valid(),
                "legacy scene accepted");
        helper.assertTrue(!classify(NS, "quest_encounters/alpha.json").valid(),
                "legacy encounter accepted");
        helper.assertTrue(!classify(NS, "quest_pools/alpha.json").valid(),
                "legacy pool accepted");
        helper.assertTrue(!classify(NS, "loot_table/quest/alpha.json").valid(),
                "legacy reward accepted");

        LocalizedReference relative = LocalizedReference.read(element("{\"key\":\"#title\"}")).orElseThrow();
        LocalizedReference absolute =
                LocalizedReference.read(element("{\"key\":\"examplemod.shared.title\"}")).orElseThrow();
        helper.assertValueEqual(relative.expand(PREFIX), PREFIX + ".title", "relative expansion changed");
        helper.assertValueEqual(absolute.expand(PREFIX), "examplemod.shared.title", "absolute ID changed");

        JsonObject inline = quest(NS, LINE, SLUG, PREFIX);
        inline.getAsJsonObject("metadata").addProperty("title", "Inline title");
        helper.assertTrue(!QuestBundleLocalization.validateQuest(inline, PREFIX).valid(),
                "inline player-facing title accepted");
        JsonObject codec = quest(NS, LINE, SLUG, PREFIX);
        codec.add("reward_codec_data", object("{\"table\":{\"name\":\"inline codec value\"}}"));
        helper.assertTrue(QuestBundleLocalization.validateQuest(codec, PREFIX).valid(),
                "non-player codec value scanned as text");
        JsonObject dialogueCodec = quest(NS, LINE, SLUG, PREFIX);
        dialogueCodec.add("dialogue", object("{\"offer\":{\"actions\":[{\"type\":\"examplemod:custom\",\"external\":\"opaque\"}]}}"));
        helper.assertTrue(QuestBundleLocalization.validateQuest(dialogueCodec, PREFIX).valid(),
                "opaque action payload mistaken for external structural dialogue");

        JsonObject inlineBossBar = object(
                "{\"id\":\"examplemod:inline_boss\",\"boss_bar_title\":\"Inline boss\"}");
        helper.assertTrue(!QuestBundleLocalization.collectCompanion(inlineBossBar, PREFIX).valid(),
                "inline companion boss-bar title accepted");
        JsonObject inlineLocation = object(
                "{\"id\":\"examplemod:inline_location\",\"location_message\":\"Go there\"}");
        helper.assertTrue(!QuestBundleLocalization.collectCompanion(inlineLocation, PREFIX).valid(),
                "inline companion location message accepted");

        JsonObject localizedEncounter = object(
                "{\"schema\":\"villagerretaliation:encounter/v1\","
                        + "\"id\":\"examplemod:localized_encounter\","
                        + "\"controller\":\"villagerretaliation:controlled\","
                        + "\"spawn_mode\":\"raid_waves\","
                        + "\"location_message\":{\"key\":\"#encounter.location\"},"
                        + "\"waves\":[{\"id\":\"wave_one\","
                        + "\"members\":[{\"entity\":\"minecraft:zombie\","
                        + "\"custom_name\":{\"key\":\"#encounter.member\"},"
                        + "\"name_visible\":true}],"
                        + "\"boss_bar_title\":{\"key\":\"#encounter.boss\"},"
                        + "\"scene_actions\":[{\"id\":\"wave_notice\","
                        + "\"type\":\"notification\","
                        + "\"text\":{\"key\":\"#encounter.wave_notice\"}}]}],"
                        + "\"phases\":[{\"id\":\"phase_one\","
                        + "\"trigger\":{\"type\":\"wave_started\",\"wave\":\"wave_one\"},"
                        + "\"actions\":[{\"id\":\"phase_notice\","
                        + "\"type\":\"notification\","
                        + "\"text\":{\"key\":\"#encounter.phase_notice\"}}]}],"
                        + "\"rewards\":{\"completion\":[{\"id\":\"medal\","
                        + "\"item\":\"minecraft:paper\","
                        + "\"trophy_name\":{\"key\":\"#encounter.trophy\"}}]}}");
        List<QuestBundleTransactions.RawResource> localizedResources = new ArrayList<>(introduce(
                0, "base", NS, LINE, SLUG, quest(NS, LINE, SLUG, PREFIX),
                locale(Map.of(
                        PREFIX + ".title", element("\"Alpha\""),
                        PREFIX + ".encounter.boss", element("\"Localized boss\""),
                        PREFIX + ".encounter.location", element("\"Localized location\""),
                        PREFIX + ".encounter.member", element("\"Localized member\""),
                        PREFIX + ".encounter.wave_notice", element("\"Localized wave notice\""),
                        PREFIX + ".encounter.phase_notice", element("\"Localized phase notice\""),
                        PREFIX + ".encounter.trophy", element("\"Localized trophy\"")))));
        localizedResources.add(raw(
                0, "base", NS, "quests/road/alpha/encounters/localized.json", localizedEncounter));
        QuestBundleTransactions.EffectiveBundle localizedBundle =
                bundle(compile(localizedResources), LINE, SLUG);
        QuestBundleRuntimeMaterializer.DefinitionResult materialized =
                QuestBundleRuntimeMaterializer.materializeDefinition(
                        localizedBundle,
                        QuestBundlePath.Kind.ENCOUNTER,
                        id("examplemod:localized_encounter"));
        helper.assertTrue(materialized.errors().isEmpty(), "companion localization failed");
        JsonObject materializedWave =
                materialized.definition().getAsJsonArray("waves").get(0).getAsJsonObject();
        helper.assertValueEqual(
                materializedWave.get("boss_bar_title").getAsString(),
                "Localized boss", "boss-bar title was not materialized");
        helper.assertValueEqual(
                materialized.definition().get("location_message").getAsString(),
                "Localized location", "location message was not materialized");
        helper.assertValueEqual(
                materializedWave.get("boss_bar_title_key").getAsString(),
                PREFIX + ".encounter.boss", "boss-bar locale key was not retained");
        helper.assertValueEqual(
                materialized.definition().get("location_message_key").getAsString(),
                PREFIX + ".encounter.location", "location-message locale key was not retained");
        List<String> encounterErrors = new ArrayList<>();
        EncounterTemplate parsedEncounter = EncounterResources.parse(
                id("examplemod:localized_encounter"), materialized.definition(), encounterErrors);
        helper.assertTrue(
                parsedEncounter != null
                        && encounterErrors.isEmpty()
                        && !parsedEncounter.locationMessageKey().isBlank()
                        && !parsedEncounter.waves().getFirst().bossBarTitleKey().isBlank()
                        && !parsedEncounter.waves().getFirst().members().getFirst()
                                .options().customNameKey().isBlank()
                        && !parsedEncounter.waves().getFirst().hooks().getFirst().textKey().isBlank()
                        && !parsedEncounter.phases().getFirst().actions().getFirst()
                                .textKey().isBlank()
                        && !parsedEncounter.rewards().completion().getFirst()
                                .trophyNameKey().isBlank(),
                "encounter parser lost runtime locale keys: " + encounterErrors);

        QuestBundleRuntimeMaterializer.Result materializedQuest =
                QuestBundleRuntimeMaterializer.materialize(localizedBundle);
        helper.assertValueEqual(
                materializedQuest.quest().getAsJsonObject("metadata").get("title_key").getAsString(),
                PREFIX + ".title",
                "quest title reference was not retained as a runtime key");

        JsonObject actionRoot = object(
                "{\"actions\":[{\"type\":\"quest\",\"quest\":\"examplemod:alpha\","
                        + "\"action\":\"start\",\"lines\":{\"started\":\"English\","
                        + "\"started_key\":\"examplemod.quest.alpha.dialogue.started\"}}]}");
        com.jvn.villagerretaliation.action.VillagerActionDefinition keyedAction =
                com.jvn.villagerretaliation.action.VillagerActionDefinition.readList(
                                id("examplemod:keyed_action"), "keyed action", actionRoot)
                        .getFirst();
        helper.assertValueEqual(
                keyedAction.lineKeysForStatus("started").getFirst(),
                "examplemod.quest.alpha.dialogue.started",
                "quest action outcome key was not retained");

        QuestContentCatalog builtIns = QuestContentCatalogs.current(helper.getLevel().getServer());
        com.jvn.villagerretaliation.quest.QuestDefinition torch =
                builtIns.quests().get(id("villagerretaliation:torch_bundle"));
        helper.assertTrue(
                torch != null && "quest.village_supply.torch_bundle.title".equals(torch.titleKey()),
                "built-in quest title did not compile to a runtime locale key");
        var dialogueTree = builtIns.dialogueCatalog()
                .tree(com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCompiler.treeId(torch.id()))
                .orElse(null);
        helper.assertTrue(
                dialogueTree != null
                        && dialogueTree.entries().stream().anyMatch(entry -> !entry.labelKey().isBlank())
                        && dialogueTree.nodes().values().stream()
                                .flatMap(node -> node.textVariants().stream())
                                .anyMatch(variant -> !variant.textKey().isBlank()),
                "generated quest dialogue did not retain label and text locale keys");
        QuestDeterministicLocaleKeys.Address address = new QuestDeterministicLocaleKeys.Address(
                id("examplemod:alpha"), List.of("stage_one", "objective_one"), "tracker.text");
        helper.assertValueEqual(
                QuestDeterministicLocaleKeys.relativeKey(address),
                QuestDeterministicLocaleKeys.relativeKey(new QuestDeterministicLocaleKeys.Address(
                        id("examplemod:alpha"), List.of("stage_one", "objective_one"), "tracker.text")),
                "source move changed deterministic key");
        QuestDeterministicLocaleKeys.FreezeResult collision = QuestDeterministicLocaleKeys.freeze(List.of(
                new QuestDeterministicLocaleKeys.Address(id("examplemod:alpha"), List.of("A B"), "tracker.text"),
                new QuestDeterministicLocaleKeys.Address(id("examplemod:alpha"), List.of("a.b"), "tracker.text")));
        helper.assertTrue(!collision.valid() && collision.collisions().size() == 1,
                "deterministic collision not reported");

        JsonObject externalRoot = quest(NS, LINE, SLUG, PREFIX);
        externalRoot.add("external_scenes", element("[\"examplemod:legacy_tree\"]"));
        assertRejected(helper, compile(introduce(
                0, "base", NS, LINE, SLUG, externalRoot,
                locale(PREFIX + ".title", "Alpha"))),
                "structural dialogue must remain in quest.json", "external dialogue root");

        JsonObject externalSlot = quest(NS, LINE, SLUG, PREFIX);
        externalSlot.add("dialogue", object("{\"offer\":{\"external_scene\":\"examplemod:legacy_tree\"}}"));
        assertRejected(helper, compile(introduce(
                0, "base", NS, LINE, SLUG, externalSlot,
                locale(PREFIX + ".title", "Alpha"))),
                "structural dialogue must remain in quest.json", "external dialogue slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_architecture")
    public static void introductionIdentityAndCompatibilityAreValidated(GameTestHelper helper) {
        QuestBundleTransactions.CompatibilityRules packaged =
                BuiltInQuestBundleCompatibility.rules();
        helper.assertValueEqual(
                packaged.frozenQuestIds().size(), 85, "packaged compatibility quest count");
        helper.assertTrue(
                packaged.frozenSlugs().keySet().equals(packaged.frozenQuestIds())
                        && packaged.frozenPrefixes().keySet().equals(packaged.frozenQuestIds()),
                "packaged compatibility identities are incomplete");
        helper.assertTrue(compile(introduce(
                0, "base", NS, LINE, SLUG, quest(NS, LINE, SLUG, PREFIX),
                locale(PREFIX + ".title", "Alpha"))).bundles().containsKey(owner(LINE, SLUG)),
                "valid bundle rejected");
        assertRejected(helper, compile(List.of(
                raw(0, "base", NS, questPath(LINE, SLUG), quest(NS, LINE, SLUG, PREFIX)))),
                "must include locales/en_us.json", "missing English");

        JsonObject wrongNamespace = quest("othermod", LINE, SLUG, "othermod.quest.alpha");
        assertRejected(helper, compile(introduce(0, "base", NS, LINE, SLUG, wrongNamespace,
                locale(PREFIX + ".title", "Alpha"))), "namespace", "namespace mismatch");
        JsonObject wrongLine = quest(NS, "wrong", SLUG, PREFIX);
        assertRejected(helper, compile(introduce(0, "base", NS, LINE, SLUG, wrongLine,
                locale(PREFIX + ".title", "Alpha"))), "questline", "questline mismatch");
        JsonObject wrongSlug = quest(NS, LINE, "different", PREFIX);
        assertRejected(helper, compile(introduce(0, "base", NS, LINE, SLUG, wrongSlug,
                locale(PREFIX + ".title", "Alpha"))), "quest-slug", "slug mismatch");
        JsonObject slashId = quest(NS, LINE, SLUG, PREFIX);
        slashId.addProperty("id", NS + ":nested/alpha");
        assertRejected(helper, compile(introduce(0, "base", NS, LINE, SLUG, slashId,
                locale(PREFIX + ".title", "Alpha"))), "single-segment", "slash quest ID");
        JsonObject wrongPrefix = quest(NS, LINE, SLUG, "quest.legacy.alpha");
        assertRejected(helper, compile(introduce(0, "base", NS, LINE, SLUG, wrongPrefix,
                locale("quest.legacy.alpha.title", "Alpha"))), "must begin with", "third-party prefix");

        ResourceLocation legacyId = id("villagerretaliation:legacy");
        String legacyPrefix = "quest.legacy_line.legacy";
        QuestBundleTransactions.Result legacy = QuestBundleTransactions.compile(
                introduce(0, "base", "villagerretaliation", "legacy_line", "legacy",
                        quest("villagerretaliation", "legacy_line", "legacy", legacyPrefix),
                        locale(legacyPrefix + ".title", "Legacy")),
                new QuestBundleTransactions.CompatibilityRules(
                        Set.of(legacyId), Map.of(legacyId, "legacy"), Map.of(legacyId, legacyPrefix)));
        helper.assertTrue(legacy.bundles().containsKey(
                        QuestBundlePath.Owner.quest("villagerretaliation", "legacy_line", "legacy")),
                "frozen built-in prefix rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_architecture")
    public static void overridesCannotChangeQuestIdentity(GameTestHelper helper) {
        List<QuestBundleTransactions.RawResource> resources = new ArrayList<>(introduce(
                0, "base", NS, LINE, SLUG, quest(NS, LINE, SLUG, PREFIX),
                locale(PREFIX + ".title", "Alpha")));
        JsonObject renamed = quest(NS, LINE, SLUG, PREFIX);
        renamed.addProperty("id", NS + ":renamed");
        resources.add(raw(1, "high", NS, questPath(LINE, SLUG), renamed));
        resources.add(raw(1, "high", NS, localePath(LINE, SLUG, "en_us"),
                locale(PREFIX + ".title", "Renamed")));

        QuestBundleTransactions.Result result = compile(resources);
        helper.assertValueEqual(bundle(result, LINE, SLUG).questId(), id(NS + ":alpha"),
                "quest ID override did not retain the lower bundle");
        helper.assertTrue(diagnostic(result, "quest ID is immutable"), "quest ID change lacked a diagnostic");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_architecture")
    public static void layersRollbackAtomicallyAndLocalesFallbackPerKey(GameTestHelper helper) {
        List<QuestBundleTransactions.RawResource> structural = new ArrayList<>(introduce(
                0, "base", NS, LINE, SLUG, quest(NS, LINE, SLUG, PREFIX),
                locale(PREFIX + ".title", "Lower")));
        structural.add(raw(1, "high", NS, questPath(LINE, SLUG), quest(NS, "wrong", SLUG, PREFIX)));
        structural.add(raw(1, "high", NS, localePath(LINE, SLUG, "en_us"),
                locale(PREFIX + ".title", "Invalid high")));
        QuestBundleTransactions.Result fallback = compile(structural);
        helper.assertValueEqual(
                bundle(fallback, LINE, SLUG).locales().plainText("en_us", PREFIX + ".title").orElse(""),
                "Lower", "invalid structural/English transaction did not retain lower bundle");

        List<QuestBundleTransactions.RawResource> semanticLayer = new ArrayList<>();
        semanticLayer.add(raw(1, "high", NS, questPath(LINE, SLUG), quest(NS, LINE, SLUG, PREFIX)));
        semanticLayer.add(raw(1, "high", NS, localePath(LINE, SLUG, "en_us"),
                locale(PREFIX + ".title", "High")));
        semanticLayer.add(raw(1, "high", NS, localePath(LINE, SLUG, "fr_fr"),
                locale(PREFIX + ".title", "Titre")));
        helper.assertTrue(
                QuestContentCatalogs.removeRejectedStructuralLayer(
                        semanticLayer, owner(LINE, SLUG), 1, "high"),
                "semantic layer removal did not remove structural resources");
        helper.assertTrue(
                semanticLayer.size() == 1
                        && semanticLayer.getFirst().location().getPath().endsWith("/locales/fr_fr.json"),
                "semantic fallback removed the independent optional locale");

        List<QuestBundleTransactions.RawResource> malformed = new ArrayList<>(introduce(
                0, "base", NS, LINE, SLUG, quest(NS, LINE, SLUG, PREFIX),
                locale(PREFIX + ".title", "English")));
        malformed.add(QuestBundleTransactions.RawResource.malformed(
                1, "high", location(NS, localePath(LINE, SLUG, "fr_fr")), "malformed fr_fr"));
        QuestBundleTransactions.Result isolated = compile(malformed);
        helper.assertTrue(bundle(isolated, LINE, SLUG).locales().messages("fr_fr").isEmpty(),
                "malformed optional locale leaked");
        helper.assertValueEqual(
                bundle(isolated, LINE, SLUG).locales().plainText("fr_fr", PREFIX + ".title").orElse(""),
                "English", "rejected locale lost English fallback");

        JsonObject localizedQuest = quest(NS, LINE, SLUG, PREFIX);
        localizedQuest.getAsJsonObject("metadata").add("description", element("{\"key\":\"#description\"}"));
        List<QuestBundleTransactions.RawResource> localePatch = new ArrayList<>(introduce(
                0, "base", NS, LINE, SLUG, localizedQuest,
                locale(Map.of(
                        PREFIX + ".title", element("\"English title\""),
                        PREFIX + ".description", element("\"English description\"")))));
        localePatch.add(raw(1, "locale_pack", NS, localePath(LINE, SLUG, "fr_fr"),
                locale(PREFIX + ".title", "Titre")));
        QuestLocaleCatalog catalog = bundle(compile(localePatch), LINE, SLUG).locales();
        helper.assertTrue(catalog.locales().equals(Set.of("en_us", "fr_fr")), "locale catalog incomplete");
        helper.assertValueEqual(catalog.plainText("fr_fr", PREFIX + ".title").orElse(""), "Titre",
                "locale-only override missing");
        helper.assertValueEqual(catalog.plainText("fr_fr", PREFIX + ".description").orElse(""),
                "English description", "partial locale fallback missing");
        helper.assertValueEqual(catalog.plainText("en_us", PREFIX + ".title").orElse(""), "English title",
                "one player locale lookup altered another");

        QuestContentCatalog base = QuestContentCatalogs.current(helper.getLevel().getServer());
        QuestContentCatalog localized = new QuestContentCatalog(
                base.generation(),
                base.compiledQuestCatalog(),
                base.dialogueCatalog(),
                base.quests(),
                base.objectiveEventQuestIds(),
                base.factQuestIds(),
                base.memoryEventQuestIds(),
                base.exclusiveGroupQuestIds(),
                base.triggerEventQuestIds(),
                base.scenes(),
                base.encounters(),
                base.pools(),
                base.bundles(),
                catalog,
                base.rewards());
        QuestContentCatalogs.installForTests(
                helper.getLevel().getServer(),
                localized,
                QuestContentCatalogs.loadReport(helper.getLevel().getServer()));
        try {
            helper.assertValueEqual(
                    VillagerDialogueResources.globalMessage(
                            helper.getLevel().getServer(),
                            RandomSource.create(41L),
                            PREFIX + ".title",
                            "fr_fr").orElse(""),
                    "Titre",
                    "French player boundary did not use the locale overlay");
            helper.assertValueEqual(
                    VillagerDialogueResources.globalMessage(
                            helper.getLevel().getServer(),
                            RandomSource.create(41L),
                            PREFIX + ".title",
                            "en_us").orElse(""),
                    "English title",
                    "English player boundary was contaminated by another locale");
        } finally {
            QuestContentCatalogs.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_architecture")
    public static void canonicalOwnersAndPrivateCompanionsAreEnforced(GameTestHelper helper) {
        List<QuestBundleTransactions.RawResource> duplicate = new ArrayList<>(introduce(
                0, "base", NS, LINE, SLUG, quest(NS, LINE, SLUG, PREFIX),
                locale(PREFIX + ".title", "Alpha")));
        duplicate.add(raw(0, "base", NS, scenePath(LINE, SLUG, "first"), definition("examplemod:duplicate")));
        duplicate.add(raw(0, "base", NS, scenePath(LINE, SLUG, "second"), definition("examplemod:duplicate")));
        assertRejected(helper, compile(duplicate), "duplicate stable ID", "duplicate companion");

        List<QuestBundleTransactions.RawResource> crossOwner = new ArrayList<>(introduce(
                0, "base", NS, "road", "alpha", quest(NS, "road", "alpha", "examplemod.quest.alpha"),
                locale("examplemod.quest.alpha.title", "Alpha")));
        crossOwner.add(raw(0, "base", NS, scenePath("road", "alpha", "private"),
                definition("examplemod:private_scene")));
        JsonObject beta = quest(NS, "road", "beta", "examplemod.quest.beta");
        beta.addProperty("scene", "examplemod:private_scene");
        crossOwner.addAll(introduce(0, "base", NS, "road", "beta", beta,
                locale("examplemod.quest.beta.title", "Beta")));
        QuestBundleTransactions.Result privateReference = compile(crossOwner);
        helper.assertTrue(privateReference.bundles().containsKey(owner("road", "alpha"))
                        && !privateReference.bundles().containsKey(owner("road", "beta"))
                        && diagnostic(privateReference, "private SCENE"),
                "cross-bundle private access not isolated");

        List<QuestBundleTransactions.RawResource> layeredCrossOwner = new ArrayList<>(introduce(
                0, "base", NS, "road", "alpha", quest(NS, "road", "alpha", "examplemod.quest.alpha"),
                locale("examplemod.quest.alpha.title", "Alpha")));
        layeredCrossOwner.add(raw(0, "base", NS, scenePath("road", "alpha", "private"),
                definition("examplemod:private_scene")));
        layeredCrossOwner.addAll(introduce(
                0, "base", NS, "road", "beta", quest(NS, "road", "beta", "examplemod.quest.beta"),
                locale("examplemod.quest.beta.title", "Lower Beta")));
        JsonObject invalidBetaOverride = quest(NS, "road", "beta", "examplemod.quest.beta");
        invalidBetaOverride.addProperty("scene", "examplemod:private_scene");
        layeredCrossOwner.add(raw(
                1, "patch", NS, questPath("road", "beta"), invalidBetaOverride));
        QuestBundleTransactions.Result layeredPrivateReference = compile(layeredCrossOwner);
        QuestBundleTransactions.EffectiveBundle retainedBeta =
                layeredPrivateReference.bundles().get(owner("road", "beta"));
        helper.assertTrue(retainedBeta != null
                        && !retainedBeta.definitions().get(QuestBundlePath.Kind.QUEST)
                                .get(id("examplemod:beta")).has("scene"),
                "cross-owner override did not retain the lower structural bundle");
        helper.assertValueEqual(
                retainedBeta.locales().plainText("en_us", "examplemod.quest.beta.title").orElse(""),
                "Lower Beta",
                "cross-owner override did not retain lower English");
        helper.assertTrue(layeredPrivateReference.diagnostics().stream().anyMatch(value ->
                        value.layer() == 1 && value.packId().equals("patch")
                                && value.message().contains("private SCENE")),
                "cross-owner rollback lost rejecting layer provenance");

        JsonObject prerequisite = quest(NS, "road", "beta", "examplemod.quest.beta");
        prerequisite.add("availability", object("{\"prerequisites\":[\"examplemod:alpha\"]}"));
        List<QuestBundleTransactions.RawResource> legal = new ArrayList<>(introduce(
                0, "base", NS, "road", "alpha", quest(NS, "road", "alpha", "examplemod.quest.alpha"),
                locale("examplemod.quest.alpha.title", "Alpha")));
        legal.addAll(introduce(0, "base", NS, "road", "beta", prerequisite,
                locale("examplemod.quest.beta.title", "Beta")));
        helper.assertTrue(compile(legal).bundles().size() == 2, "quest prerequisite rejected");

        List<QuestBundleTransactions.RawResource> patch = new ArrayList<>(introduce(
                0, "base", NS, LINE, SLUG, quest(NS, LINE, SLUG, PREFIX),
                locale(PREFIX + ".title", "Alpha")));
        patch.add(raw(1, "patch", NS, scenePath(LINE, SLUG, "added"), definition("examplemod:added_scene")));
        helper.assertTrue(bundle(compile(patch), LINE, SLUG).definitions()
                        .getOrDefault(QuestBundlePath.Kind.SCENE, Map.of())
                        .containsKey(id("examplemod:added_scene")),
                "companion-only patch required quest.json");

        List<QuestBundleTransactions.RawResource> duplicatePrefix = new ArrayList<>(introduce(
                0, "base", NS, "road", "alpha", quest(NS, "road", "alpha", "examplemod.quest.shared"),
                locale("examplemod.quest.shared.title", "Alpha")));
        duplicatePrefix.addAll(introduce(0, "base", NS, "road", "beta",
                quest(NS, "road", "beta", "examplemod.quest.shared"),
                locale("examplemod.quest.shared.title", "Beta")));
        QuestBundleTransactions.Result prefixResult = compile(duplicatePrefix);
        helper.assertTrue(prefixResult.bundles().size() == 1
                        && diagnostic(prefixResult, "localization_prefix"),
                "duplicate localization prefix accepted");

        List<QuestBundleTransactions.RawResource> shared = new ArrayList<>();
        shared.add(raw(0, "base", NS, "quests/_shared/scenes/shared.json",
                definition("examplemod:shared_scene")));
        JsonObject consumer = quest(NS, LINE, SLUG, PREFIX);
        consumer.addProperty("scene", "examplemod:shared_scene");
        shared.addAll(introduce(0, "base", NS, LINE, SLUG, consumer,
                locale(PREFIX + ".title", "Alpha")));
        helper.assertTrue(compile(shared).bundles().containsKey(owner(LINE, SLUG)),
                "_shared companion reference rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_architecture")
    public static void fingerprintsAndSnapshotsPreserveSemanticIdentity(GameTestHelper helper) {
        JsonObject first = object("""
                {"schema":"villagerretaliation:quest/v2","id":"examplemod:alpha",
                 "localization_prefix":"examplemod.quest.alpha",
                 "metadata":{"questline":"road","title":{"key":"#title"}},
                 "events":[{"id":"first"},{"id":"second"}]}
                """);
        JsonObject objectReordered = object("""
                {"events":[{"id":"first"},{"id":"second"}],
                 "metadata":{"title":{"key":"#title"},"questline":"road"},
                 "localization_prefix":"examplemod.quest.alpha","id":"examplemod:alpha",
                 "schema":"villagerretaliation:quest/v2"}
                """);
        JsonObject arrayReordered = objectReordered.deepCopy();
        JsonArray reversed = new JsonArray();
        reversed.add(object("{\"id\":\"second\"}"));
        reversed.add(object("{\"id\":\"first\"}"));
        arrayReordered.add("events", reversed);
        QuestBundleTransactions.EffectiveBundle original =
                compiledBundle(first, locale(PREFIX + ".title", variants("One", "Two")), "first_pack");
        QuestBundleTransactions.EffectiveBundle reordered =
                compiledBundle(objectReordered, locale(PREFIX + ".title", variants("One", "Two")), "renamed_pack");
        QuestBundleTransactions.EffectiveBundle orderedDifference =
                compiledBundle(arrayReordered, locale(PREFIX + ".title", variants("One", "Two")), "first_pack");
        QuestBundleTransactions.EffectiveBundle localeDifference =
                compiledBundle(first, locale(PREFIX + ".title", variants("Two", "One")), "first_pack");
        helper.assertValueEqual(QuestBundleFingerprints.structural(original),
                QuestBundleFingerprints.structural(reordered), "object order or pack name affected identity");
        helper.assertTrue(!QuestBundleFingerprints.structural(original)
                        .equals(QuestBundleFingerprints.structural(orderedDifference)),
                "ordered array did not affect structural identity");
        helper.assertValueEqual(QuestBundleFingerprints.structural(original),
                QuestBundleFingerprints.structural(localeDifference),
                "locale affected persistent structural identity");
        helper.assertTrue(!QuestBundleFingerprints.migrationEquivalent(original)
                        .equals(QuestBundleFingerprints.migrationEquivalent(localeDifference)),
                "variant order did not affect migration fingerprint");

        QuestBundleTransactions.RawResource raw = raw(
                0, "base", NS, questPath(LINE, SLUG), quest(NS, LINE, SLUG, PREFIX));
        raw.root().addProperty("id", "examplemod:mutated");
        helper.assertValueEqual(raw.root().get("id").getAsString(), "examplemod:alpha",
                "raw definition mutable");
        QuestBundleTransactions.EffectiveBundle effective = compiledBundle(
                quest(NS, LINE, SLUG, PREFIX),
                locale(PREFIX + ".title", object("{\"text\":\"Alpha\",\"weight\":2}")), "base");
        effective.definitions().get(QuestBundlePath.Kind.QUEST)
                .get(id("examplemod:alpha")).addProperty("id", "examplemod:mutated");
        helper.assertValueEqual(effective.definitions().get(QuestBundlePath.Kind.QUEST)
                        .get(id("examplemod:alpha")).get("id").getAsString(),
                "examplemod:alpha", "effective definition mutable");
        effective.locales().messages("en_us").get(PREFIX + ".title")
                .getAsJsonObject().addProperty("text", "Mutated");
        helper.assertValueEqual(effective.locales().plainText("en_us", PREFIX + ".title").orElse(""),
                "Alpha", "locale payload mutable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_architecture")
    public static void inlineFallbackMigrationNeverUsesArbitraryFirstWins(GameTestHelper helper) {
        QuestDeterministicLocaleKeys.Address title = new QuestDeterministicLocaleKeys.Address(
                id("examplemod:alpha"), List.of(), "title");
        QuestLocalizationMigration.Claim first = new QuestLocalizationMigration.Claim(
                title, "", element("\"Agreed\""), "quest/a/title");
        QuestLocalizationMigration.Claim second = new QuestLocalizationMigration.Claim(
                title, "", element("\"Agreed\""), "quest/b/title");

        QuestLocalizationMigration.Result agreed =
                QuestLocalizationMigration.materialize(PREFIX, Map.of(), List.of(first, second));
        helper.assertTrue(agreed.valid(), "agreeing inline fallbacks were rejected");
        helper.assertValueEqual(
                agreed.english().get(PREFIX + ".title").getAsString(),
                "Agreed",
                "agreeing fallback was not materialized");

        QuestLocalizationMigration.Result existingWins = QuestLocalizationMigration.materialize(
                PREFIX,
                Map.of(PREFIX + ".title", element("\"Existing\"")),
                List.of(
                        new QuestLocalizationMigration.Claim(
                                title, "", element("\"First fallback\""), "quest/a/title"),
                        new QuestLocalizationMigration.Claim(
                                title, "", element("\"Second fallback\""), "quest/b/title")));
        helper.assertTrue(existingWins.valid(), "existing keyed English did not win over fallbacks");
        helper.assertValueEqual(
                existingWins.english().get(PREFIX + ".title").getAsString(),
                "Existing",
                "inline fallback replaced an existing keyed value");

        QuestLocalizationMigration.Result conflict = QuestLocalizationMigration.materialize(
                PREFIX,
                Map.of(),
                List.of(
                        new QuestLocalizationMigration.Claim(
                                title, "", element("\"First\""), "quest/a/title"),
                        new QuestLocalizationMigration.Claim(
                                title, "", element("\"Second\""), "quest/b/title")));
        helper.assertTrue(!conflict.valid()
                        && conflict.diagnostics().getFirst().sources()
                                .equals(List.of("quest/a/title", "quest/b/title")),
                "distinct fallbacks did not report every source");

        QuestLocalizationMigration.Result collision = QuestLocalizationMigration.materialize(
                PREFIX,
                Map.of(),
                List.of(
                        new QuestLocalizationMigration.Claim(
                                new QuestDeterministicLocaleKeys.Address(
                                        id("examplemod:alpha"), List.of("A B"), "text"),
                                "", element("\"First\""), "quest/a/text"),
                        new QuestLocalizationMigration.Claim(
                                new QuestDeterministicLocaleKeys.Address(
                                        id("examplemod:alpha"), List.of("a.b"), "text"),
                                "", element("\"Second\""), "quest/b/text")));
        helper.assertTrue(!collision.valid()
                        && collision.diagnostics().stream()
                                .anyMatch(value -> value.message().contains("generated locale key collision")),
                "generated collision was not diagnosed");
        helper.succeed();
    }

    private static void assertRejected(
            GameTestHelper helper, QuestBundleTransactions.Result result, String expected, String name) {
        helper.assertTrue(result.bundles().isEmpty() && diagnostic(result, expected),
                name + " was not rejected with an actionable diagnostic");
    }

    private static QuestBundleTransactions.Result compile(List<QuestBundleTransactions.RawResource> resources) {
        return QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
    }

    private static QuestBundleTransactions.EffectiveBundle compiledBundle(
            JsonObject quest, JsonObject english, String pack) {
        return bundle(compile(introduce(0, pack, NS, LINE, SLUG, quest, english)), LINE, SLUG);
    }

    private static QuestBundleTransactions.EffectiveBundle bundle(
            QuestBundleTransactions.Result result, String questline, String slug) {
        QuestBundleTransactions.EffectiveBundle value = result.bundles().get(owner(questline, slug));
        if (value == null) {
            throw new IllegalStateException("missing bundle: " + result.diagnostics());
        }
        return value;
    }

    private static List<QuestBundleTransactions.RawResource> introduce(
            int layer, String pack, String namespace, String questline, String slug,
            JsonObject quest, JsonObject english) {
        return List.of(
                raw(layer, pack, namespace, questPath(questline, slug), quest),
                raw(layer, pack, namespace, localePath(questline, slug, "en_us"), english));
    }

    private static QuestBundleTransactions.RawResource raw(
            int layer, String pack, String namespace, String path, JsonObject root) {
        return QuestBundleTransactions.RawResource.valid(layer, pack, location(namespace, path), root);
    }

    private static JsonObject quest(String namespace, String questline, String slug, String prefix) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "villagerretaliation:quest/v2");
        root.addProperty("id", namespace + ":" + slug);
        root.addProperty("localization_prefix", prefix);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("questline", questline);
        metadata.add("title", element("{\"key\":\"#title\"}"));
        root.add("metadata", metadata);
        return root;
    }

    private static JsonObject definition(String stableId) {
        JsonObject root = new JsonObject();
        root.addProperty("id", stableId);
        return root;
    }

    private static JsonObject locale(String key, String value) {
        return locale(key, element("\"" + value + "\""));
    }

    private static JsonObject locale(String key, JsonElement value) {
        return locale(Map.of(key, value));
    }

    private static JsonObject locale(Map<String, JsonElement> values) {
        JsonObject root = new JsonObject();
        values.forEach(root::add);
        return root;
    }

    private static JsonElement variants(String first, String second) {
        return element("{\"variants\":[{\"text\":\"" + first + "\",\"weight\":2},"
                + "{\"text\":\"" + second + "\",\"weight\":1}]}");
    }

    private static JsonObject object(String value) {
        return element(value).getAsJsonObject();
    }

    private static JsonElement element(String value) {
        return JsonParser.parseString(value);
    }

    private static boolean diagnostic(QuestBundleTransactions.Result result, String fragment) {
        return result.diagnostics().stream().anyMatch(value -> value.message().contains(fragment));
    }

    private static QuestBundlePath.Classification classify(String namespace, String path) {
        return QuestBundlePath.classify(location(namespace, path));
    }

    private static QuestBundlePath.Owner owner(String questline, String slug) {
        return QuestBundlePath.Owner.quest(NS, questline, slug);
    }

    private static String questPath(String questline, String slug) {
        return "quests/" + questline + "/" + slug + "/quest.json";
    }

    private static String localePath(String questline, String slug, String locale) {
        return "quests/" + questline + "/" + slug + "/locales/" + locale + ".json";
    }

    private static String scenePath(String questline, String slug, String name) {
        return "quests/" + questline + "/" + slug + "/scenes/" + name + ".json";
    }

    private static ResourceLocation location(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
