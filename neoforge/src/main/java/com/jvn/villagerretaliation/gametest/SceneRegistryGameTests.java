package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ClientSync;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ToolingMetadata;
import com.jvn.villagerretaliation.api.registry.FreezableExtensionRegistry;
import com.jvn.villagerretaliation.api.registry.RuntimeTypeDescriptor;
import com.jvn.villagerretaliation.quest.QuestRegistryMetadata;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.persistence.SceneSaveMigrations;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneDefinitionReconciler;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneScheduler;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import com.jvn.villagerretaliation.scene.runtime.StepExecutionStatus;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import com.jvn.villagerretaliation.scene.runtime.SceneRecoveryPolicy;
import com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.executor.EncounterStepExecutors;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.SceneOperatorService;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorBindingService;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.BindingSource;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.DeathPolicy;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.MissingActorPolicy;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.ReplacementPolicy;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SceneRegistryGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private SceneRegistryGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dialogueSegmentsRespectPacketBoundsWithoutDroppingText(GameTestHelper helper) {
        List<DialogueTextSegment> styled = new java.util.ArrayList<>();
        for (int index = 0; index < 80; index++) {
            styled.add(new DialogueTextSegment(
                    "x",
                    DialogueTextEffects.fromTag(index % 2 == 0 ? "bold" : "italic")));
        }

        List<DialogueTextSegment> bounded = DialogueTextSegment.forNetwork(styled);
        helper.assertValueEqual(bounded.size(), 64, "packet style run limit");
        helper.assertValueEqual(
                DialogueTextSegment.plainText(bounded),
                "x".repeat(80),
                "folding excess style runs must preserve the dialogue tail");

        String boundaryText = "a".repeat(511) + "😀";
        String truncated = DialogueTextSegment.plainText(DialogueTextSegment.forNetwork(
                List.of(new DialogueTextSegment(boundaryText, DialogueTextEffects.NONE))));
        helper.assertTrue(truncated.length() <= 512, "network dialogue must fit its encoded character limit");
        helper.assertFalse(!truncated.isEmpty() && Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)),
                "network dialogue must not end with half of a surrogate pair");
        helper.assertValueEqual(DialogueTextSegment.plainText(null), "", "null segment list plain text");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dialogueEffectTagsSurviveEveryPacketTextShape(GameTestHelper helper) {
        String taggedText = "Harrison. The village has <shake>teeth</shake> too.";
        String plainText = "Harrison. The village has teeth too.";
        List<DialogueTextSegment> parsed = DialogueTextSegment.parse(taggedText, DialogueTextEffects.NONE);

        List<DialogueTextSegment> forcedBroadcast = DialogueTextSegment.forNetwork(taggedText, parsed);
        helper.assertValueEqual(
                DialogueTextSegment.plainText(forcedBroadcast),
                plainText,
                "forced and nearby broadcasts must remove effect markup");
        helper.assertTrue(
                forcedBroadcast.stream().anyMatch(segment -> segment.text().equals("teeth") && segment.effects().shake()),
                "forced and nearby broadcasts must retain shake segments");

        List<DialogueTextSegment> directNotice = DialogueTextSegment.forNetwork(
                "A <wave>warning</wave>.",
                List.of());
        helper.assertValueEqual(
                DialogueTextSegment.plainText(directNotice),
                "A warning.",
                "notices without pre-parsed segments must remove effect markup");
        helper.assertTrue(
                directNotice.stream().anyMatch(segment -> segment.text().equals("warning") && segment.effects().wavy()),
                "notices without pre-parsed segments must retain animated effects");

        List<DialogueTextSegment> legacyPlainSegments = DialogueTextSegment.forNetwork(
                taggedText,
                DialogueTextSegment.plain(taggedText, DialogueTextEffects.NONE));
        helper.assertValueEqual(
                DialogueTextSegment.plainText(legacyPlainSegments),
                plainText,
                "legacy packets must not leak effect markup");
        helper.assertTrue(
                legacyPlainSegments.stream().anyMatch(segment -> segment.effects().shake()),
                "legacy packets must recover their animated effects");

        List<DialogueTextSegment> ordinaryDialogue = DialogueTextSegment.forNetwork(plainText, parsed);
        helper.assertTrue(
                ordinaryDialogue.stream().anyMatch(segment -> segment.effects().shake()),
                "ordinary dialogue with canonical plain text must retain its effects");

        List<DialogueTextSegment> mismatched = DialogueTextSegment.forNetwork(
                "Authoritative text.",
                List.of(new DialogueTextSegment("Different text.", DialogueTextEffects.fromTag("shake"))));
        helper.assertValueEqual(
                DialogueTextSegment.plainText(mismatched),
                "Authoritative text.",
                "genuinely mismatched style runs must not replace packet text");
        helper.assertFalse(
                mismatched.stream().anyMatch(segment -> segment.effects().active()),
                "genuinely mismatched style runs must be discarded");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void malformedSceneHistoryDoesNotPreventSaveRecovery(GameTestHelper helper) {
        CompoundTag root = new CompoundTag();
        root.putInt(SceneSaveMigrations.DATA_VERSION, SceneSavedData.CURRENT_DATA_VERSION);

        net.minecraft.nbt.ListTag audit = new net.minecraft.nbt.ListTag();
        audit.add(new CompoundTag());
        UUID validSceneId = UUID.randomUUID();
        audit.add(new SceneAuditEntry(
                validSceneId, "guide", "missing", "rebound", "recovered", 10L, "test").save());
        root.put("Audit", audit);

        net.minecraft.nbt.ListTag tombstones = new net.minecraft.nbt.ListTag();
        tombstones.add(new CompoundTag());
        CompoundTag validTombstone = new CompoundTag();
        validTombstone.putString("OperationKey", "test-operation");
        validTombstone.putUUID("InstanceId", UUID.randomUUID());
        validTombstone.putString("SceneId", "villagerretaliation:test_scene");
        validTombstone.putString("Result", "SUCCESS");
        tombstones.add(validTombstone);
        root.put("Tombstones", tombstones);

        SceneSavedData loaded = SceneSavedData.load(root, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.auditEntries().size(), 1, "valid audit entries after malformed record");
        helper.assertValueEqual(loaded.auditEntries().getFirst().sceneId(), validSceneId,
                "valid audit entry must survive malformed neighbor");
        helper.assertValueEqual(loaded.tombstones().size(), 1, "valid tombstones after malformed record");
        helper.assertValueEqual(loaded.tombstones().getFirst().operationKey(), "test-operation",
                "valid tombstone must survive malformed neighbor");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void publicRegistryRejectsDuplicatesAliasesAndLateRegistration(GameTestHelper helper) {
        FreezableExtensionRegistry<RuntimeTypeDescriptor> registry = new FreezableExtensionRegistry<>("test type");
        RuntimeTypeDescriptor first = descriptor("registry_first", Set.of(VillagerRetaliation.id("legacy_first")));
        registry.register(first);
        helper.assertTrue(registry.get(VillagerRetaliation.id("legacy_first")).orElseThrow() == first,
                "registered alias should resolve to its canonical descriptor");
        expectFailure(helper, () -> registry.register(descriptor("registry_first", Set.of())), "duplicate id");
        expectFailure(helper, () -> registry.register(descriptor("registry_second",
                Set.of(VillagerRetaliation.id("legacy_first")))), "alias collision");
        registry.freeze();
        expectFailure(helper, () -> registry.register(descriptor("registry_late", Set.of())), "registry freeze");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void extensionDescriptorsExportDeterministically(GameTestHelper helper) {
        VillagerRetaliationRegistries.registerBuiltIns();
        JsonObject registries = QuestRegistryMetadata.export().getAsJsonObject("registries");
        helper.assertTrue(registries.getAsJsonArray("actor_types").size() >= 5,
                "actor descriptors should be exported for third-party tooling");
        helper.assertTrue(registries.getAsJsonArray("scene_steps").asList().stream()
                        .map(value -> value.getAsJsonObject().get("id").getAsString())
                        .anyMatch("villagerretaliation:move_actor"::equals),
                "built-in scene steps should use the public descriptor export");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void actorBindingRoundTripsIdentitySnapshotAndHistory(GameTestHelper helper) {
        SceneActorDeclaration declaration = actor("guide", ReplacementPolicy.COMPATIBLE_REPLACEMENT);
        SceneActorBinding first = binding("guide", "Ada", 1);
        SceneActorBinding second = binding("guide", "Bea", 2);
        SceneActorBindingService.RebindResult result = SceneActorBindingService.rebind(declaration, first, second,
                SceneActorBindingService.RebindKind.COMPATIBLE, 91L, "provider_return_replacement", "");
        helper.assertTrue(result.accepted() && result.changed(), "compatible binding should be replaced");
        SceneActorBinding loaded = SceneActorBinding.load(result.binding().save());
        helper.assertTrue(loaded.equals(result.binding()), "actor binding should round trip every durable field");
        helper.assertTrue(loaded.generation() == 2L && loaded.replacementHistory().size() == 1,
                "round-tripped binding should preserve generation and replacement history");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fixedActorIsNeverSilentlyReplaced(GameTestHelper helper) {
        SceneActorBinding first = binding("narrator", "Original", 1);
        SceneActorBindingService.RebindResult result = SceneActorBindingService.rebind(
                actor("narrator", ReplacementPolicy.FIXED), first, binding("narrator", "Nearby", 2),
                SceneActorBindingService.RebindKind.COMPATIBLE, 20L, "nearby_candidate", "");
        helper.assertFalse(result.accepted(), "fixed narrative actor must reject a nearby replacement");
        helper.assertTrue(result.binding().equals(first) && result.binding().replacementHistory().isEmpty(),
                "rejected replacement must preserve identity and history");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void compatibleReplacementRequiresMatchingActorType(GameTestHelper helper) {
        SceneActorDeclaration declaration = actor("scout", ReplacementPolicy.COMPATIBLE_REPLACEMENT);
        SceneActorBinding first = binding("scout", "Scout", 1);
        SceneActorBinding wrongType = SceneActorBinding.entity("scout", VillagerRetaliation.id("player"), UUID.randomUUID(),
                VillagerRetaliation.id("player"), VillagerRetaliation.id("overworld"), BlockPos.ZERO, "Player", true);
        helper.assertFalse(SceneActorBindingService.rebind(declaration, first, wrongType,
                        SceneActorBindingService.RebindKind.COMPATIBLE, 30L, "candidate", "").accepted(),
                "compatible replacement must retain the authored actor type");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void operatorRebindAppendsAuditableHistory(GameTestHelper helper) {
        SceneActorBindingService.RebindResult result = SceneActorBindingService.rebind(
                actor("captain", ReplacementPolicy.OPERATOR_REBINDABLE), binding("captain", "Old Captain", 1),
                binding("captain", "New Captain", 2), SceneActorBindingService.RebindKind.OPERATOR,
                404L, "operator_repair", "ServerOwner");
        helper.assertTrue(result.accepted() && result.changed(), "operator rebind should be accepted");
        var history = result.binding().replacementHistory().getFirst();
        helper.assertTrue(history.gameTime() == 404L && history.operatorIdentity().equals("ServerOwner")
                        && history.reason().equals("operator_repair"),
                "operator rebind should retain reason, time, and operator identity");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sceneCompilerKeepsExplicitEntryAndStableGraphIds(GameTestHelper helper) {
        SceneParser.ParseResult parsed = SceneParser.parse(VillagerRetaliation.id("quest_scenes/test.json"), validScene());
        helper.assertTrue(parsed.valid(), "valid scene resource should parse: " + parsed.diagnostics());
        SceneCompiler.CompileResult compiled = SceneCompiler.compile(parsed.resource());
        helper.assertTrue(compiled.valid(), "valid scene graph should compile: " + compiled.diagnostics());
        helper.assertValueEqual(compiled.scene().entryStep(), "opening_wait", "authored scene entry step");
        helper.assertTrue(compiled.scene().steps().containsKey("opening_wait")
                        && compiled.scene().steps().containsKey("finish"),
                "compiled graph should preserve explicitly authored stable ids");
        helper.assertTrue(compiled.scene().definitionHash().length() == 64,
                "compiled scene should carry a canonical SHA-256 definition hash");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sceneActorDownedPolicyAndBindingStateRoundTrip(GameTestHelper helper) {
        JsonObject root = validScene();
        root.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("lethal_damage_policy", "downed");
        SceneParser.ParseResult parsed = SceneParser.parse(
                VillagerRetaliation.id("quest_scenes/downed_actor.json"), root);
        helper.assertTrue(parsed.valid(), "downed actor policy should parse: " + parsed.diagnostics());
        helper.assertValueEqual(
                parsed.resource().actors().getFirst().lethalDamagePolicy(),
                SceneActorDeclaration.LethalDamagePolicy.DOWNED,
                "scene actor lethal policy");

        SceneActorBinding downed = binding("guide", "Ada", 1)
                .withState(SceneActorBinding.BindingState.DOWNED);
        helper.assertValueEqual(
                SceneActorBinding.load(downed.save()).state(),
                SceneActorBinding.BindingState.DOWNED,
                "downed binding state persistence");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void activeSceneActorTransitionsDownedAndBackToLive(GameTestHelper helper) {
        JsonObject root = validScene();
        root.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("lethal_damage_policy", "downed");
        var compiled = compiledScene(root);
        SceneResources.installTestScenes(helper.getLevel().getServer(), List.of(compiled));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, 2, 2);
        SceneActorBinding binding = SceneActorBinding.entity(
                "guide",
                VillagerRetaliation.id("villager"),
                villager.getUUID(),
                VillagerRetaliation.id("villager"),
                helper.getLevel().dimension().location(),
                villager.blockPosition(),
                "Guide",
                true);
        SceneSavedData data = SceneSavedData.get(helper.getLevel());
        SceneInstance scene = data.start(
                compiled,
                "downed_actor_runtime",
                new SceneOwner(
                        com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        player.getUUID(),
                        null,
                        null,
                        ""),
                null,
                Set.of(player.getUUID()),
                Map.of("guide", binding),
                helper.getLevel().getGameTime()).instance();
        try {
            helper.assertValueEqual(
                    SceneLifecycleIntegration.protectingScenes(helper.getLevel(), villager),
                    Set.of(compiled.id()),
                    "active scene protection source");
            VillagerDownedService.enterDowned(
                    helper.getLevel(),
                    villager,
                    new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("scene_test")));
            helper.assertValueEqual(
                    scene.actorBindings().get("guide").state(),
                    SceneActorBinding.BindingState.DOWNED,
                    "scene binding after incapacitation");

            VillagerDownedService.recover(villager);
            helper.assertValueEqual(
                    scene.actorBindings().get("guide").state(),
                    SceneActorBinding.BindingState.LIVE,
                    "scene binding after recovery");
        } finally {
            SceneResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sceneCompilerRejectsDuplicateIdsAndImmediateCycles(GameTestHelper helper) {
        JsonObject root = validScene();
        root.getAsJsonArray("steps").add(root.getAsJsonArray("steps").get(0).deepCopy());
        SceneParser.ParseResult duplicate = SceneParser.parse(VillagerRetaliation.id("quest_scenes/duplicate.json"), root);
        helper.assertTrue(duplicate.diagnostics().stream().anyMatch(value -> value.code().equals("scene.step.duplicate")),
                "duplicate stable step ids should be rejected structurally");

        JsonObject cycle = JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:cycle","entry_step":"a",
                 "actors":[],"steps":[
                   {"id":"a","type":"villagerretaliation:scene_branch","transitions":{"loop":"b"}},
                   {"id":"b","type":"villagerretaliation:scene_branch","transitions":{"loop":"a"}},
                   {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
        SceneCompiler.CompileResult compiled = SceneCompiler.compile(
                SceneParser.parse(VillagerRetaliation.id("quest_scenes/cycle.json"), cycle).resource());
        helper.assertTrue(compiled.diagnostics().stream().anyMatch(value -> value.code().equals("scene.cycle.immediate")),
                "unbounded immediate graph cycles should be rejected");
        helper.assertTrue(compiled.diagnostics().stream().anyMatch(value -> value.code().equals("scene.step.unreachable")
                        && value.severity() == SceneDiagnostic.Severity.WARNING),
                "unreachable graph nodes should be diagnosed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questActionParsesStableSceneLaunchOperation(GameTestHelper helper) {
        JsonObject wrapper = JsonParser.parseString("""
                {"actions":[{"type":"start_scene","scene":"villagerretaliation:gate_ambush",
                  "operation_id":"acceptance/gate_ambush","wait_for_result":true}]}
                """).getAsJsonObject();
        List<VillagerActionDefinition> actions = VillagerActionDefinition.readList(
                VillagerRetaliation.id("quests/test.json"), "test", wrapper, VillagerRetaliation.id("test"));
        helper.assertTrue(actions.size() == 1, "start_scene action should parse through the canonical action model");
        VillagerActionDefinition action = actions.getFirst();
        helper.assertValueEqual(action.kind(), VillagerActionDefinition.Kind.START_SCENE, "scene action kind");
        helper.assertValueEqual(action.sceneOperationId(), "acceptance/gate_ambush", "stable scene operation id");
        helper.assertTrue(action.waitForScene(), "scene action should retain wait_for_result");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sceneInstanceSaveAndStableLaunchIndexRoundTrip(GameTestHelper helper) {
        var definition = compiledValidScene();
        SceneSavedData data = new SceneSavedData();
        UUID ownerId = UUID.randomUUID();
        SceneOwner owner = new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                ownerId, null, null, "");
        SceneSavedData.StartResult first = data.start(definition, "quest/start/gate", owner, null, Set.of(ownerId), Map.of(), 100L);
        SceneSavedData.StartResult repeated = data.start(definition, "quest/start/gate", owner, null, Set.of(ownerId), Map.of(), 101L);
        helper.assertTrue(first.created() && !repeated.created() && first.instance().id().equals(repeated.instance().id()),
                "same owner and stable operation id must reuse one scene instance");
        var record = first.instance().currentRecord(definition.steps().get(definition.entryStep()).type());
        record.status(StepExecutionStatus.PREPARED, 102L);
        record.wakeTime(140L);
        first.instance().transition(SceneState.WAITING, 102L);
        SceneInstance loaded = SceneInstance.load(first.instance().save());
        helper.assertTrue(loaded.id().equals(first.instance().id()) && loaded.state() == SceneState.WAITING
                        && loaded.stepRecords().get("opening_wait").wakeTime() == 140L,
                "scene instance should persist owner, state, current step, and execution record");
        CompoundTag legacy = new CompoundTag();
        legacy.put("Scenes", new net.minecraft.nbt.ListTag());
        var migrated = SceneSaveMigrations.migrate(legacy, SceneSavedData.CURRENT_DATA_VERSION);
        helper.assertTrue(migrated.targetVersion() == SceneSavedData.CURRENT_DATA_VERSION
                        && migrated.data().contains("Instances"),
                "legacy pre-release scene list should migrate explicitly from version zero");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void legacyQuestInstanceAliasSurvivesRepeatedSaves(GameTestHelper helper) {
        var definition = compiledValidScene();
        ResourceLocation questId = VillagerRetaliation.id("legacy_scene_quest");
        UUID playerId = UUID.randomUUID();
        UUID legacyOwnerId = UUID.randomUUID();
        SceneOwner legacyOwner = new SceneOwner(
                com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE,
                playerId, null, legacyOwnerId, "");
        SceneInstance legacy = new SceneSavedData().start(definition, "stage/open", legacyOwner,
                legacyOwnerId, Set.of(playerId), Map.of(), 10L, questId).instance();
        legacy.prepareReceipt("legacy_reward", SceneOperationReceipt.Kind.LOOT_GRANT, 11L)
                .completed(11L, "already granted");
        CompoundTag legacyTag = legacy.save();
        legacyTag.remove("QuestRunId");
        legacyTag.remove("RunIdentityKind");
        CompoundTag root = new CompoundTag();
        root.putInt(SceneSaveMigrations.DATA_VERSION, 2);
        net.minecraft.nbt.ListTag instances = new net.minecraft.nbt.ListTag();
        instances.add(legacyTag);
        SceneInstance legacyPlayerScene = new SceneSavedData().start(definition, "player/open",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""),
                null, Set.of(playerId), Map.of(), 10L, questId).instance();
        CompoundTag legacyPlayerTag = legacyPlayerScene.save();
        legacyPlayerTag.remove("QuestRunId");
        legacyPlayerTag.remove("RunIdentityKind");
        instances.add(legacyPlayerTag);
        root.put("Instances", instances);

        UUID definitiveRun = VillagerQuestSavedData.QuestProgress.deterministicRunId(playerId, questId, 1);
        SceneOwner definitiveOwner = new SceneOwner(
                com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE,
                playerId, null, definitiveRun, "");
        SceneSavedData loaded = SceneSavedData.load(root, helper.getLevel().registryAccess());
        var firstReuse = loaded.start(definition, "stage/open", definitiveOwner, definitiveRun,
                Set.of(playerId), Map.of(), 20L, questId);
        helper.assertTrue(!firstReuse.created() && firstReuse.instanceId().equals(legacy.id()),
                "pre-run-id quest-instance scene must be reused after migration");
        helper.assertTrue(firstReuse.instance().receipts().containsKey("legacy_reward")
                        && firstReuse.instance().runIdentityKind() == SceneInstance.RunIdentityKind.LEGACY_OWNER,
                "migration must preserve receipts and durable legacy identity kind");
        var playerReuse = loaded.start(definition, "player/open",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""),
                definitiveRun, Set.of(playerId), Map.of(), 20L, questId);
        helper.assertTrue(!playerReuse.created() && playerReuse.instanceId().equals(legacyPlayerScene.id()),
                "pre-run-id player-owned scene must also reuse its migrated operation alias");

        for (int reload = 0; reload < 2; reload++) {
            CompoundTag saved = loaded.save(new CompoundTag(), helper.getLevel().registryAccess());
            loaded = SceneSavedData.load(saved, helper.getLevel().registryAccess());
            var reused = loaded.start(definition, "stage/open", definitiveOwner, definitiveRun,
                    Set.of(playerId), Map.of(), 30L + reload, questId);
            helper.assertTrue(!reused.created() && reused.instanceId().equals(legacy.id()),
                    "legacy operation alias must survive every save and reload");
        }

        loaded.get(legacy.id()).orElseThrow().complete(40L);
        CompoundTag terminalSave = loaded.save(new CompoundTag(), helper.getLevel().registryAccess());
        SceneSavedData afterTerminal = SceneSavedData.load(terminalSave, helper.getLevel().registryAccess());
        UUID repeatRun = VillagerQuestSavedData.QuestProgress.deterministicRunId(playerId, questId, 2);
        var repeat = afterTerminal.start(definition, "stage/open",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE,
                        playerId, null, repeatRun, ""),
                repeatRun, Set.of(playerId), Map.of(), 50L, questId);
        helper.assertTrue(repeat.created() && !repeat.instanceId().equals(legacy.id()),
                "terminal legacy scenes must not block a legitimate repeat run");

        var once = SceneSaveMigrations.migrate(root, SceneSavedData.CURRENT_DATA_VERSION);
        var twice = SceneSaveMigrations.migrate(once.data(), SceneSavedData.CURRENT_DATA_VERSION);
        helper.assertTrue(once.data().equals(twice.data()), "scene migration must be idempotent");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void schedulerBoundsWorkAndReconcilesStableDefinitions(GameTestHelper helper) {
        var definition = compiledValidScene();
        SceneResources.installTestScenes(helper.getLevel().getServer(), List.of(definition));
        SceneSavedData data = new SceneSavedData();
        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            data.start(definition, "operation/" + i,
                    new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                            player, null, null, ""), null, Set.of(player), Map.of(), 0L);
        }
        Set<String> owners = new java.util.LinkedHashSet<>();
        SceneScheduler scheduler = new SceneScheduler(3, (server, repository, instance, scene, time) -> {
            owners.add(instance.owner().stableKey());
            instance.transition(SceneState.RUNNING, time);
            return SceneScheduler.ProcessResult.idle();
        });
        scheduler.rebuild(data, 0L);
        var tick = scheduler.tick(helper.getLevel().getServer(), data, 1L);
        helper.assertTrue(tick.workPerformed() == 3 && owners.size() == 3,
                "scheduler must cap work and give distinct owners a fair turn");

        SceneInstance instance = data.all().getFirst();
        instance.currentRecord(definition.steps().get(instance.currentStep()).type()).status(StepExecutionStatus.COMPLETED, 2L);
        JsonObject compatibleJson = validScene(); compatibleJson.addProperty("definition_version", 4);
        compatibleJson.addProperty("metadata_note", "hash change");
        var compatible = SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id("quest_scenes/test.json"), compatibleJson).resource()).scene();
        helper.assertTrue(SceneDefinitionReconciler.reconcile(instance, compatible).safe(),
                "definition reload should continue when stable executed ids and types remain compatible");
        JsonObject incompatibleJson = validScene();
        incompatibleJson.getAsJsonArray("steps").get(0).getAsJsonObject()
                .addProperty("type", "villagerretaliation:wait_condition");
        var incompatible = SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id("quest_scenes/test.json"), incompatibleJson).resource()).scene();
        helper.assertFalse(SceneDefinitionReconciler.reconcile(instance, incompatible).safe(),
                "definition reload must block when an executed stable id changes type");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void operationReceiptsPreventDuplicateNonIdempotentEffects(GameTestHelper helper) {
        var definition = compiledValidScene(); SceneSavedData data = new SceneSavedData(); UUID player = UUID.randomUUID();
        SceneInstance instance = data.start(definition, "receipts",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player,null,null,""),
                null,Set.of(player),Map.of(),0L).instance();
        var step=definition.steps().get(instance.currentStep());var record=instance.currentRecord(step.type());
        int[] effects={0};
        for(SceneOperationReceipt.Kind kind:List.of(SceneOperationReceipt.Kind.ITEM_GRANT,
                SceneOperationReceipt.Kind.REPUTATION_CHANGE,SceneOperationReceipt.Kind.EXPERIENCE_GRANT,
                SceneOperationReceipt.Kind.COUNTER_INCREMENT,SceneOperationReceipt.Kind.QUEST_TRANSITION)){
            SceneExecutionContext context=new SceneExecutionContext(helper.getLevel().getServer(),data,instance,definition,step,record,10L,true);
            helper.assertValueEqual(SceneReceiptGuard.applyOnce(context,kind.name(),kind,()->effects[0]++,"semantic-test").status(),
                    SceneReceiptGuard.Status.APPLIED,"first receipt application");
            helper.assertValueEqual(SceneReceiptGuard.applyOnce(context,kind.name(),kind,()->effects[0]++,"semantic-test").status(),
                    SceneReceiptGuard.Status.ALREADY_APPLIED,"repeated receipt application");
        }
        helper.assertTrue(effects[0]==5,"receipts should apply item, reputation, XP, counter, and quest-transition effects exactly once each");
        SceneInstance loaded=SceneInstance.load(instance.save());
        helper.assertTrue(loaded.receipts().size()==5&&loaded.receipts().values().stream().allMatch(r->r.state()==SceneOperationReceipt.ReceiptState.APPLIED),
                "applied operation receipts should survive save/reload");
        var loadedRecord=loaded.stepRecords().get(loaded.currentStep());
        var loadedContext=new SceneExecutionContext(helper.getLevel().getServer(),data,loaded,definition,step,loadedRecord,12L,false);
        SceneOperationReceipt ambiguous=loadedContext.prepareReceipt("ambiguous",SceneOperationReceipt.Kind.LOOT_GRANT);
        helper.assertValueEqual(SceneReceiptGuard.applyOnce(loadedContext,"ambiguous",SceneOperationReceipt.Kind.LOOT_GRANT,()->effects[0]++,"").status(),
                SceneReceiptGuard.Status.AMBIGUOUS_PREPARED,"ambiguous prepared receipt after reload");
        helper.assertTrue(ambiguous.state()==SceneOperationReceipt.ReceiptState.PREPARED&&effects[0]==5,
                "ambiguous recovery must block rather than duplicate an effect");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void everyIntermediateStepStateHasExplicitRecoveryDecision(GameTestHelper helper) {
        helper.assertValueEqual(SceneRecoveryPolicy.decide(StepExecutionStatus.PENDING,
                com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode.RECEIPT_REQUIRED,null,false),
                SceneRecoveryPolicy.Decision.PREPARE,"pending recovery");
        helper.assertValueEqual(SceneRecoveryPolicy.decide(StepExecutionStatus.PREPARED,
                com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode.RECEIPT_REQUIRED,
                SceneOperationReceipt.ReceiptState.PREPARED,false),SceneRecoveryPolicy.Decision.BLOCK,"ambiguous prepared recovery");
        helper.assertValueEqual(SceneRecoveryPolicy.decide(StepExecutionStatus.PREPARED,
                com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode.RECEIPT_REQUIRED,
                SceneOperationReceipt.ReceiptState.APPLIED,false),SceneRecoveryPolicy.Decision.VERIFY,"prepared with applied receipt");
        helper.assertValueEqual(SceneRecoveryPolicy.decide(StepExecutionStatus.RUNNING,
                com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode.WORLD_RECONCILED,null,false),
                SceneRecoveryPolicy.Decision.RECONCILE,"running world reconciliation");
        helper.assertValueEqual(SceneRecoveryPolicy.decide(StepExecutionStatus.APPLIED,
                com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode.RECEIPT_REQUIRED,null,false),
                SceneRecoveryPolicy.Decision.VERIFY,"applied recovery");
        helper.assertValueEqual(SceneRecoveryPolicy.decide(StepExecutionStatus.COMPLETED,
                com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode.RECEIPT_REQUIRED,null,false),
                SceneRecoveryPolicy.Decision.DONE,"completed recovery");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void waitTicksReloadUsesRemainingAbsoluteDeadline(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();
        JsonObject json=JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:wait_reload","entry_step":"wait",
                 "actors":[],"steps":[{"id":"wait","type":"villagerretaliation:wait_ticks","data":{"ticks":50},"next":"done"},
                 {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
        var definition=compiledScene(json);SceneSavedData data=new SceneSavedData();UUID player=UUID.randomUUID();
        SceneInstance instance=data.start(definition,"wait",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player,null,null,""),null,Set.of(player),Map.of(),100L).instance();
        var step=definition.steps().get("wait");var record=instance.currentRecord(step.type());var executor=SceneStepExecutors.get(step.type()).orElseThrow();
        executor.prepare(new SceneExecutionContext(helper.getLevel().getServer(),data,instance,definition,step,record,100L,true));
        SceneInstance loaded=SceneInstance.load(instance.save());var loadedRecord=loaded.stepRecords().get("wait");
        var waiting=executor.reconcile(new SceneExecutionContext(helper.getLevel().getServer(),data,loaded,definition,step,loadedRecord,120L,false));
        helper.assertTrue(waiting.outcome()==com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.WAIT&&waiting.wakeTime()==150L,
                "reloaded wait should keep the original wake time instead of restarting its full duration");
        var complete=executor.reconcile(new SceneExecutionContext(helper.getLevel().getServer(),data,loaded,definition,step,loadedRecord,150L,false));
        helper.assertValueEqual(complete.outcome(),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.APPLIED,"wait completion at persisted deadline");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void branchChoiceRemainsStableAfterReload(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();ServerPlayer player=helper.makeMockServerPlayerInLevel();Villager villager=spawnVillager(helper,1,1);
        JsonObject json=JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:branch_reload","entry_step":"choice","actors":[
                 {"alias":"player","type":"villagerretaliation:player","binding_source":"owner_player"},
                 {"alias":"guide","type":"villagerretaliation:villager","binding_source":"quest_provider"}],"steps":[
                 {"id":"choice","type":"villagerretaliation:scene_branch","actors":["guide"],"data":{"branches":[{"transition":"left","conditions":[]}]},"transitions":{"left":"done","right":"failed"}},
                 {"id":"done","type":"villagerretaliation:scene_complete"},{"id":"failed","type":"villagerretaliation:scene_fail"}]}
                """).getAsJsonObject();
        var definition=compiledScene(json);Map<String,SceneActorBinding> bindings=Map.of(
                "player",SceneActorBinding.entity("player",VillagerRetaliation.id("player"),player.getUUID(),VillagerRetaliation.id("player"),helper.getLevel().dimension().location(),player.blockPosition(),player.getName().getString(),true),
                "guide",SceneActorBinding.entity("guide",VillagerRetaliation.id("villager"),villager.getUUID(),VillagerRetaliation.id("villager"),helper.getLevel().dimension().location(),villager.blockPosition(),villager.getName().getString(),true));
        SceneSavedData data=new SceneSavedData();SceneInstance instance=data.start(definition,"branch",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),bindings,0L).instance();
        var step=definition.steps().get("choice");var record=instance.currentRecord(step.type());var executor=SceneStepExecutors.get(step.type()).orElseThrow();
        var first=executor.apply(new SceneExecutionContext(helper.getLevel().getServer(),data,instance,definition,step,record,1L,true));
        helper.assertValueEqual(first.outcome(),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.APPLIED,"initial branch evaluation");
        SceneInstance loaded=SceneInstance.load(instance.save());var loadedRecord=loaded.stepRecords().get("choice");
        var replay=executor.apply(new SceneExecutionContext(helper.getLevel().getServer(),data,loaded,definition,step,loadedRecord,2L,false));
        helper.assertTrue(replay.outcome()==com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.APPLIED
                        &&loadedRecord.durableValues().get("chosen_transition").equals("left"),
                "recorded branch should not be reevaluated after reload");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dialogueReceiptPreventsReloadSpam(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();ServerPlayer player=helper.makeMockServerPlayerInLevel();Villager villager=spawnVillager(helper,1,1);
        JsonObject json=JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:dialogue_reload","entry_step":"speak","actors":[
                 {"alias":"guide","type":"villagerretaliation:villager","binding_source":"quest_provider"}],"steps":[
                 {"id":"speak","type":"villagerretaliation:dialogue","actors":["guide"],"data":{"text":"Hold the gate!"},"next":"done"},
                 {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();var definition=compiledScene(json);
        var binding=SceneActorBinding.entity("guide",VillagerRetaliation.id("villager"),villager.getUUID(),VillagerRetaliation.id("villager"),helper.getLevel().dimension().location(),villager.blockPosition(),villager.getName().getString(),true);
        SceneSavedData data=new SceneSavedData();SceneInstance instance=data.start(definition,"dialogue",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of("guide",binding),0L).instance();
        var step=definition.steps().get("speak");var record=instance.currentRecord(step.type());var executor=SceneStepExecutors.get(step.type()).orElseThrow();
        var first=executor.apply(new SceneExecutionContext(helper.getLevel().getServer(),data,instance,definition,step,record,1L,true));
        SceneInstance loaded=SceneInstance.load(instance.save());var replay=executor.reconcile(new SceneExecutionContext(helper.getLevel().getServer(),data,loaded,definition,step,loaded.stepRecords().get("speak"),2L,false));
        helper.assertTrue(first.outcome()==com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.APPLIED&&replay.outcome()==first.outcome()&&loaded.receipts().size()==1,
                "dialogue reload should reuse the completed recipient receipt instead of delivering again");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void movementWaitsForChunkThenReconcilesArrivalAndTimeout(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();Villager villager=EntityType.VILLAGER.create(helper.getLevel());helper.assertTrue(villager!=null,"villager should create");
        BlockPos start=helper.absolutePos(new BlockPos(1,1,1));villager.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);helper.assertTrue(helper.getLevel().addFreshEntity(villager),"villager should spawn");
        BlockPos target=start.offset(1000,0,1000);JsonObject json=JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:move_reload","entry_step":"move","actors":[
                 {"alias":"guide","type":"villagerretaliation:villager","binding_source":"quest_provider","missing_actor_policy":"block"}],"steps":[
                 {"id":"move","type":"villagerretaliation:move_actor","actors":["guide"],"data":{"timeout_ticks":40,"poll_ticks":2},"next":"done"},
                 {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();JsonObject dataJson=json.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data");dataJson.addProperty("x",target.getX());dataJson.addProperty("y",target.getY());dataJson.addProperty("z",target.getZ());
        var definition=compiledScene(json);var binding=SceneActorBinding.entity("guide",VillagerRetaliation.id("villager"),villager.getUUID(),VillagerRetaliation.id("villager"),helper.getLevel().dimension().location(),villager.blockPosition(),"Guide",true);UUID owner=UUID.randomUUID();SceneSavedData repository=new SceneSavedData();SceneInstance instance=repository.start(definition,"move",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,owner,null,null,""),null,Set.of(owner),Map.of("guide",binding),0L).instance();
        var step=definition.steps().get("move");var record=instance.currentRecord(step.type());var executor=SceneStepExecutors.get(step.type()).orElseThrow();executor.prepare(new SceneExecutionContext(helper.getLevel().getServer(),repository,instance,definition,step,record,0L,true));
        var unloaded=executor.apply(new SceneExecutionContext(helper.getLevel().getServer(),repository,instance,definition,step,record,1L,true));helper.assertValueEqual(unloaded.outcome(),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.WAIT,"movement with unloaded destination chunk");
        var timedOut=executor.reconcile(new SceneExecutionContext(helper.getLevel().getServer(),repository,instance,definition,step,record,41L,false));helper.assertValueEqual(timedOut.outcome(),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.FAIL,"movement timeout failure transition");
        helper.getLevel().getChunk(target.getX()>>4,target.getZ()>>4);UUID villagerId=villager.getUUID();villager.discard();Villager reloadedVillager=EntityType.VILLAGER.create(helper.getLevel());helper.assertTrue(reloadedVillager!=null,"reloaded villager should create");reloadedVillager.setUUID(villagerId);reloadedVillager.moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,0,0);helper.assertTrue(helper.getLevel().addFreshEntity(reloadedVillager),"reloaded actor should return in the destination chunk");SceneInstance loaded=SceneInstance.load(instance.save());
        helper.runAfterDelay(1,()->{
            var arrived=executor.reconcile(new SceneExecutionContext(helper.getLevel().getServer(),repository,loaded,definition,step,loaded.stepRecords().get("move"),2L,false));helper.assertTrue(arrived.outcome()==com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.APPLIED,"movement arrival after chunk return: "+arrived.code()+" / "+arrived.diagnostic());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void encounterReloadOwnsOnlyItsMobsAndCleanupHonorsPolicy(GameTestHelper helper) {
        EncounterTemplate remove=new EncounterTemplate(VillagerRetaliation.id("test_ambush"),1,VillagerRetaliation.id("controlled"),
                List.of(new EncounterTemplate.Member(ResourceLocation.parse("minecraft:zombie"),2)),1,4,16,6,
                EncounterTemplate.RespawnPolicy.NEVER,EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS,EncounterTemplate.CompletionCondition.ALL_DEFEATED);
        EncounterTemplate preserve=new EncounterTemplate(VillagerRetaliation.id("test_world_ambush"),1,VillagerRetaliation.id("controlled"),
                List.of(new EncounterTemplate.Member(ResourceLocation.parse("minecraft:zombie"),1)),0,4,16,6,
                EncounterTemplate.RespawnPolicy.NEVER,EncounterTemplate.CleanupPolicy.PRESERVE_IN_WORLD,EncounterTemplate.CompletionCondition.ALL_DEFEATED);
        EncounterTemplate pillagers=new EncounterTemplate(VillagerRetaliation.id("test_pillagers"),1,VillagerRetaliation.id("controlled"),
                List.of(new EncounterTemplate.Member(ResourceLocation.parse("minecraft:pillager"),1)),0,4,16,6,
                EncounterTemplate.RespawnPolicy.NEVER,EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS,EncounterTemplate.CompletionCondition.ALL_DEFEATED);
        EncounterTemplate equipped=new EncounterTemplate(VillagerRetaliation.id("test_equipped"),1,VillagerRetaliation.id("controlled"),
                List.of(new EncounterTemplate.Member(ResourceLocation.parse("minecraft:zombie"),1,Map.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new EncounterTemplate.Gear(ResourceLocation.parse("minecraft:diamond_sword"),1,Map.of(ResourceLocation.parse("minecraft:sharpness"),2),0.0F)))),0,4,16,6,
                EncounterTemplate.RespawnPolicy.NEVER,EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS,EncounterTemplate.CompletionCondition.ALL_DEFEATED);
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(remove,preserve,pillagers,equipped));var definition=compiledValidScene();
        UUID owner=UUID.randomUUID();Set<UUID> party=Set.of(owner,UUID.randomUUID(),UUID.randomUUID());SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(definition,"encounter_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,owner,null,null,""),null,party,Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));
        var started=data.startEncounter(remove,scene,"ambush/start",helper.getLevel().dimension().location(),anchor,"normal");helper.assertTrue(started.created()&&!data.startEncounter(remove,scene,"ambush/start",helper.getLevel().dimension().location(),anchor,"normal").created(),"stable encounter operation should create one instance");
        var spawn=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,started.encounter(),remove);helper.assertValueEqual(spawn.status(),EncounterService.Status.ACTIVE,"encounter spawn result");int owned=started.encounter().spawned().size();helper.assertTrue(owned==4,"party-size scaling should persist the deterministic expected count");
        var unrelated=EntityType.ZOMBIE.create(helper.getLevel());helper.assertTrue(unrelated!=null,"unrelated zombie should create");unrelated.moveTo(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,0,0);helper.getLevel().addFreshEntity(unrelated);
        EncounterInstance loaded=EncounterInstance.load(started.encounter().save());EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,loaded,remove);helper.assertTrue(loaded.spawned().size()==owned&&!loaded.spawned().contains(unrelated.getUUID()),"reload reconciliation must not duplicate mobs or count unrelated nearby mobs");helper.assertTrue(loaded.partySize()==3&&loaded.expectedCount()==4,"party scaling inputs should remain stable after reload");
        EncounterService.cleanup(helper.getLevel().getServer(),data,started.encounter(),false);helper.assertTrue(started.encounter().state()==EncounterInstance.EncounterState.CLEANED,"abandonment cleanup should remove loaded owned survivors");
        var preserved=data.startEncounter(preserve,scene,"ambush/preserve",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,preserved,preserve);UUID survivor=preserved.spawned().iterator().next();EncounterService.cleanup(helper.getLevel().getServer(),data,preserved,false);helper.assertTrue(preserved.state()==EncounterInstance.EncounterState.RELEASED&&helper.getLevel().getEntity(survivor)!=null,"preserve policy should release surviving mobs into the world");
        var vanilla=data.startEncounter(pillagers,scene,"ambush/pillager",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,vanilla,pillagers);var pillager=(net.minecraft.world.entity.LivingEntity)helper.getLevel().getEntity(vanilla.spawned().iterator().next());helper.assertTrue(pillager.getMainHandItem().is(net.minecraft.world.item.Items.CROSSBOW),"vanilla spawn initialization should arm encounter pillagers");
        var authored=data.startEncounter(equipped,scene,"ambush/equipped",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,authored,equipped);var zombie=(net.minecraft.world.entity.LivingEntity)helper.getLevel().getEntity(authored.spawned().iterator().next());helper.assertTrue(zombie.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SWORD),"authored encounter equipment should override vanilla gear");
        EncounterService.cleanup(helper.getLevel().getServer(),data,vanilla,false);EncounterService.cleanup(helper.getLevel().getServer(),data,authored,false);helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void encounterAreasPersistLeaveAndMobDeadlines(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("invalid_area"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:invalid_area","members":[{"entity":"minecraft:zombie"}],
                 "area":{"radius":300,"leave_behavior":"wander","mystery":true}}
                """).getAsJsonObject(),errors);
        helper.assertTrue(errors.stream().anyMatch(value->value.contains("radius"))&&errors.stream().anyMatch(value->value.contains("leave_behavior"))&&errors.stream().anyMatch(value->value.contains("unknown area field")),"malformed encounter areas should produce focused diagnostics");
        EncounterTemplate template=new EncounterTemplate(VillagerRetaliation.id("test_area"),1,VillagerRetaliation.id("controlled"),
                List.of(new EncounterTemplate.Member(ResourceLocation.parse("minecraft:zombie"),1)),0,1,16,3,
                EncounterTemplate.RespawnPolicy.NEVER,EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS,EncounterTemplate.CompletionCondition.ALL_DEFEATED,
                EncounterTemplate.SpawnMode.GROUP,1,0,EncounterTemplate.WaveTrigger.ALL_DEFEATED,false,"",
                new EncounterTemplate.Area(6,4,EncounterTemplate.LeaveBehavior.FAIL,20,EncounterTemplate.MobBehavior.TELEPORT,10));
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template));ServerPlayer player=helper.makeMockServerPlayerInLevel();
        SceneSavedData data=new SceneSavedData();var definition=compiledValidScene();SceneInstance scene=data.start(definition,"area_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();
        BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));var encounter=data.startEncounter(template,scene,"area/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();
        player.moveTo(anchor.getX()+.5D,anchor.getY(),anchor.getZ()+.5D,0,0);
        EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);var mob=helper.getLevel().getEntity(encounter.spawned().iterator().next());helper.assertTrue(mob!=null,"area test mob should spawn");
        player.moveTo(anchor.getX()+20.5D,anchor.getY(),anchor.getZ()+.5D,0,0);mob.moveTo(anchor.getX()+20.5D,anchor.getY(),anchor.getZ()+.5D,0,0);
        EncounterService.updateArea(helper.getLevel().getServer(),data,encounter,template,10L);EncounterInstance loaded=EncounterInstance.load(encounter.save());
        helper.assertTrue(loaded.leaveWarned().contains(player.getUUID())&&loaded.leaveDeadlines().get(player.getUUID())==30L,"leave warning and absolute failure deadline should survive reload");
        helper.assertTrue(loaded.mobDeadlines().get(mob.getUUID())==20L,"mob teleport deadline should survive reload");
        EncounterService.updateArea(helper.getLevel().getServer(),data,loaded,template,20L);helper.assertTrue(mob.blockPosition().closerThan(anchor,2.0D),"owned mob should return to the durable anchor after its bounded timeout");
        var failed=EncounterService.updateArea(helper.getLevel().getServer(),data,loaded,template,30L);helper.assertTrue(failed.status()==EncounterService.Status.FAILED&&loaded.state()==EncounterInstance.EncounterState.FAILED,"participant leave timeout should fail the encounter once");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void authoredEncounterWavesKeepIdentityCompositionAndHooksAfterReload(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("authored_waves"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:authored_waves","spawn_mode":"raid_waves","boss_bar":true,
                 "waves":[
                   {"id":"scouts","members":[{"entity":"minecraft:zombie"}],"boss_bar_title":"Scouts","scene_actions":[{"id":"warn","type":"notification","text":"Scouts incoming."}]},
                   {"id":"guard","members":[{"entity":"minecraft:skeleton"}],"delay_ticks":20,"trigger":"all_defeated","boss_bar_title":"Guard","equipment":{"head":{"item":"minecraft:iron_helmet"}},"dialogue_hook":{"id":"guard_line","text":"The guard has arrived."}}
                 ]}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"authored wave fixture should parse: "+errors);
        var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_waves"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_waves","members":[{"entity":"minecraft:zombie"}],"wave_count":2,
                 "waves":[{"id":"same","members":[{"entity":"minecraft:zombie"}]},{"id":"same","members":[{"entity":"minecraft:zombie"}]}]}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("mutually exclusive"))&&invalidErrors.stream().anyMatch(value->value.contains("duplicate wave id")),"incompatible authored waves should report focused diagnostics");
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template));ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();var definition=compiledValidScene();SceneInstance scene=data.start(definition,"wave_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));player.moveTo(anchor.getX()+.5D,anchor.getY(),anchor.getZ()+.5D,0,0);var encounter=data.startEncounter(template,scene,"waves/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();
        EncounterTemplate legacy=new EncounterTemplate(VillagerRetaliation.id("legacy_waves"),1,VillagerRetaliation.id("controlled"),List.of(new EncounterTemplate.Member(ResourceLocation.parse("minecraft:zombie"),1)),0,1,16,3,EncounterTemplate.RespawnPolicy.NEVER,EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS,EncounterTemplate.CompletionCondition.ALL_DEFEATED,EncounterTemplate.SpawnMode.RAID_WAVES,2,20,EncounterTemplate.WaveTrigger.ALL_DEFEATED,false,"");EncounterInstance oldSave=new EncounterInstance(UUID.randomUUID(),legacy.id(),scene.id(),"legacy","legacy",Set.of(),helper.getLevel().dimension().location(),anchor,1,"normal",legacy.cleanupPolicy(),legacy.completionCondition(),2);oldSave.addSpawn(UUID.randomUUID());CompoundTag oldTag=oldSave.save();oldTag.remove("CurrentWaveId");oldTag.remove("CurrentWaveIndex");oldTag.remove("StartedWaves");EncounterInstance migrated=EncounterInstance.load(oldTag);var legacyWait=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,migrated,legacy);helper.assertTrue(legacyWait.status()==EncounterService.Status.WAITING&&migrated.currentWaveId().equals("repeat_1"),"legacy wave saves must still wait for the prior repeated wave to be defeated");EncounterService.hideBossBar(migrated.id());
        EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(encounter.currentWaveId().equals("scouts")&&encounter.spawned().size()==1&&encounter.firedWaveHooks().contains("scouts/warn"),"first authored wave should persist its identity, composition, and hook receipt");encounter.defeated(encounter.spawned().iterator().next());var waiting=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(waiting.status()==EncounterService.Status.WAITING&&encounter.currentWaveId().equals("guard")&&encounter.nextWaveAt()>0,"second wave delay and identity should persist before spawning");EncounterInstance loaded=EncounterInstance.load(encounter.save());long hookCount=loaded.firedWaveHooks().size();
        helper.runAfterDelay(21,()->{EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,loaded,template);helper.assertTrue(loaded.spawned().size()==2&&loaded.currentWaveId().equals("guard")&&loaded.firedWaveHooks().contains("guard/guard_line")&&loaded.firedWaveHooks().size()==hookCount+1,"reloaded second wave should spawn once and fire its hook once");var entity=loaded.spawned().stream().map(helper.getLevel()::getEntity).filter(value->value instanceof net.minecraft.world.entity.LivingEntity&&value.getType()==EntityType.SKELETON).map(value->(net.minecraft.world.entity.LivingEntity)value).findFirst().orElse(null);helper.assertTrue(entity!=null&&entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET),"wave-specific composition and equipment should apply");helper.succeed();});
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void eliteEncounterMembersApplySafeAttributesAfterVanillaInitialization(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("elite_member"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:elite_member","members":[{
                  "entity":"minecraft:zombie","custom_name":"Gatebreaker","name_visible":true,"glowing":true,"persistent":true,
                  "health":40,"movement_speed":0.4,"attack_damage":9,"armor":12,"knockback_resistance":0.5,
                  "boss":true,"boss_bar_color":"purple","boss_bar_overlay":"notched_10"}]}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"elite member fixture should parse: "+errors);
        var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("invalid_elite"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:invalid_elite","members":[{
                  "entity":"minecraft:zombie","name_visible":true,"health":5000,"attributes":{"example:unsafe":2,"minecraft:max_health":20},"boss_bar_color":"blue"}]}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("name_visible requires"))&&invalidErrors.stream().anyMatch(value->value.contains("unknown or unsafe encounter attribute"))&&invalidErrors.stream().anyMatch(value->value.contains("boss-bar presentation requires"))&&invalidErrors.stream().anyMatch(value->value.contains("duplicates attribute")),"unsafe elite configuration should produce focused diagnostics: "+invalidErrors);
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template));ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();var definition=compiledValidScene();SceneInstance scene=data.start(definition,"elite_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));player.moveTo(anchor.getX()+.5D,anchor.getY(),anchor.getZ()+.5D,0,0);var encounter=data.startEncounter(template,scene,"elite/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);var zombie=(net.minecraft.world.entity.Mob)helper.getLevel().getEntity(encounter.spawned().iterator().next());
        helper.assertTrue(zombie!=null&&zombie.getCustomName()!=null&&zombie.getCustomName().getString().equals("Gatebreaker")&&zombie.isCustomNameVisible()&&zombie.isCurrentlyGlowing()&&zombie.isPersistenceRequired(),"safe elite presentation should apply");helper.assertTrue(zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue()==40.0D&&zombie.getHealth()==zombie.getMaxHealth()&&zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue()==0.4D&&zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue()==9.0D&&zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getBaseValue()==12.0D&&zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getBaseValue()==0.5D,"elite base attributes and resulting current health should apply after vanilla initialization");helper.assertTrue(EncounterService.hasMobBossBar(zombie.getUUID())&&zombie.getPersistentData().getString("VillagerRetaliationBossColor").equals("PURPLE"),"boss designation should create a durable participant boss bar");EncounterInstance loaded=EncounterInstance.load(encounter.save());EncounterService.hideBossBars(encounter);helper.assertTrue(!EncounterService.hasMobBossBar(zombie.getUUID()),"simulated reload should clear transient boss-bar state");EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,loaded,template);helper.assertTrue(loaded.spawned().size()==1&&EncounterService.hasMobBossBar(zombie.getUUID()),"elite reload reconciliation must not duplicate the mob and should restore its boss bar");EncounterService.onDeath(zombie);helper.assertTrue(!EncounterService.hasMobBossBar(zombie.getUUID()),"elite death should remove its boss bar");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void authoredSpawnPointsPersistSelectionsAndRejectInvalidSources(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("authored_points"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:authored_points","members":[{"entity":"minecraft:zombie","count":2}],
                 "spawn_points":[{"id":"west","x":2,"y":1,"z":2,"weight":3},{"id":"east","x":7,"y":1,"z":7,"weight":1}],"spawn_selection":"sequential"}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"authored spawn points should parse: "+errors);
        var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_points"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_points","members":[{"entity":"minecraft:zombie"}],"spawn_mode":"near_player",
                 "spawn_points":[{"id":"same","actor":"guide","x":1,"weight":0},{"id":"same","marker":"guide","dimension":"not valid"}],"spawn_selection":"mystery"}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("mutually exclusive"))&&invalidErrors.stream().anyMatch(value->value.contains("duplicate spawn point id"))&&invalidErrors.stream().anyMatch(value->value.contains("weight"))&&invalidErrors.stream().anyMatch(value->value.contains("incompatible"))&&invalidErrors.stream().anyMatch(value->value.contains("unknown spawn_selection")),"invalid spawn points should produce focused diagnostics: "+invalidErrors);
        ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(compiledValidScene(),"spawn_points",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));BlockPos west=helper.absolutePos(new BlockPos(2,1,2));BlockPos east=helper.absolutePos(new BlockPos(7,1,7));var points=List.of(new EncounterInstance.ResolvedSpawnPoint("west",helper.getLevel().dimension().location(),west,3),new EncounterInstance.ResolvedSpawnPoint("east",helper.getLevel().dimension().location(),east,1));EncounterInstance encounter=data.startEncounter(template,scene,"points/start",helper.getLevel().dimension().location(),anchor,"normal",points).encounter();var result=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(result.status()==EncounterService.Status.ACTIVE&&encounter.spawned().size()==2,"authored points should spawn both members");helper.assertValueEqual(encounter.selectedSpawnPoints(),Map.of(0,"west",1,"east"),"durable sequential point selections");helper.assertValueEqual(encounter.spawnPointSequence(),2,"durable sequential cursor");helper.assertTrue(encounter.spawned().stream().map(helper.getLevel()::getEntity).allMatch(entity->entity!=null&&(entity.blockPosition().closerThan(west,3.0D)||entity.blockPosition().closerThan(east,3.0D))),"members should remain at their selected authored points");CompoundTag interruptedTag=encounter.save();interruptedTag.remove("Spawned");interruptedTag.remove("SelectedSpawnPoints");interruptedTag.remove("SpawnPointSequence");EncounterInstance loaded=EncounterInstance.load(interruptedTag);EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,loaded,template);helper.assertValueEqual(loaded.selectedSpawnPoints(),Map.of(0,"west",1,"east"),"world-recovered point selections");helper.assertValueEqual(loaded.spawnPointSequence(),2,"world-recovered sequential cursor");helper.assertTrue(loaded.spawned().size()==2,"reload reconciliation must not reroll or duplicate authored-point spawns");
        BuiltinSceneStepExecutors.register();var sourceErrors=new java.util.ArrayList<String>();EncounterTemplate missing=EncounterResources.parse(VillagerRetaliation.id("missing_point_actor"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:missing_point_actor","members":[{"entity":"minecraft:zombie"}],"spawn_points":[{"id":"gate","marker":"missing_gate"}]}
                """).getAsJsonObject(),sourceErrors);helper.assertTrue(missing!=null&&sourceErrors.isEmpty(),"missing actor template should parse before scene resolution: "+sourceErrors);EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(missing));JsonObject startJson=JsonParser.parseString(("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:point_source_scene","ownership":"player","entry_step":"start","actors":[],"steps":[{"id":"start","type":"villagerretaliation:start_encounter","data":{"template":"villagerretaliation:missing_point_actor","x":%d,"y":%d,"z":%d,"dimension":"minecraft:overworld"},"next":"done"},{"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).formatted(anchor.getX(),anchor.getY(),anchor.getZ())).getAsJsonObject();var startDefinition=compiledScene(startJson);SceneInstance sourceScene=data.start(startDefinition,"point_source",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();var startStep=startDefinition.steps().get("start");var startRecord=sourceScene.currentRecord(startStep.type());var executor=SceneStepExecutors.get(startStep.type()).orElseThrow();var context=new SceneExecutionContext(helper.getLevel().getServer(),data,sourceScene,startDefinition,startStep,startRecord,1L,true);helper.assertValueEqual(executor.prepare(context).outcome(),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.READY,"spawn-point start preparation");var missingResult=executor.apply(context);helper.assertTrue(missingResult.outcome()==com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.FAIL&&missingResult.diagnostic().contains("missing or positionless actor"),"missing marker actor should fail with a focused diagnostic: "+missingResult.diagnostic());
        var dimensionErrors=new java.util.ArrayList<String>();EncounterTemplate incompatible=EncounterResources.parse(VillagerRetaliation.id("incompatible_point"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:incompatible_point","members":[{"entity":"minecraft:zombie"}],"spawn_points":[{"id":"nether","x":0,"y":64,"z":0,"dimension":"minecraft:the_nether"}]}
                """).getAsJsonObject(),dimensionErrors);helper.assertTrue(incompatible!=null&&dimensionErrors.isEmpty(),"incompatible dimension is a scene-start validation: "+dimensionErrors);EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(incompatible));JsonObject incompatibleJson=startJson.deepCopy();incompatibleJson.addProperty("id","villagerretaliation:incompatible_point_scene");incompatibleJson.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data").addProperty("template","villagerretaliation:incompatible_point");var incompatibleDefinition=compiledScene(incompatibleJson);SceneInstance incompatibleScene=data.start(incompatibleDefinition,"incompatible_point",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();var incompatibleStep=incompatibleDefinition.steps().get("start");var incompatibleContext=new SceneExecutionContext(helper.getLevel().getServer(),data,incompatibleScene,incompatibleDefinition,incompatibleStep,incompatibleScene.currentRecord(incompatibleStep.type()),2L,true);executor.prepare(incompatibleContext);var incompatibleResult=executor.apply(incompatibleContext);helper.assertTrue(incompatibleResult.outcome()==com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.FAIL&&incompatibleResult.diagnostic().contains("incompatible dimension"),"cross-dimension spawn point should fail precisely: "+incompatibleResult.diagnostic());helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void encounterPhasesPersistTriggersActionsAndReceipts(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("phase_test"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:phase_test","spawn_mode":"raid_waves","waves":[
                  {"id":"scouts","members":[{"id":"scout","entity":"minecraft:zombie"}]},
                  {"id":"captain_wave","members":[{"id":"captain","entity":"minecraft:zombie","custom_name":"Phase Captain","health":30}]}
                ],"phases":[
                  {"id":"scouts_start","trigger":{"type":"wave_started","wave":"scouts"},"actions":[{"id":"warn","type":"notification","text":"Scouts incoming."}]},
                  {"id":"scouts_clear","trigger":{"type":"wave_completed","wave":"scouts"},"actions":[{"id":"fact","type":"fact","scope":"player","key":"phase_scouts","value":"cleared"}]},
                  {"id":"half_remaining","trigger":{"type":"remaining_percentage","percentage":50},"actions":[{"id":"branch","type":"transition","target":"finish"}]},
                  {"id":"hold_line","trigger":{"type":"elapsed_time","ticks":5},"repeatable":true,"repeat_interval_ticks":5,"max_fires":2,"actions":[{"id":"line","type":"dialogue","text":"Hold the line."}]},
                  {"id":"captain_falls","trigger":{"type":"elite_defeated","member":"captain"},"actions":[{"id":"fact","type":"fact","scope":"player","tag":"villagerretaliation:phase_captain_defeated"}]}
                ]}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"encounter phase fixture should parse: "+errors);
        var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_phases"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_phases","members":[{"entity":"minecraft:zombie"}],"phases":[
                  {"id":"missing_wave","trigger":{"type":"wave_started","wave":"missing"},"actions":[{"id":"warn","type":"notification","text":"Missing."}]},
                  {"id":"bad_repeat","trigger":{"type":"elapsed_time","ticks":3},"repeatable":true,"actions":[{"id":"line","type":"dialogue","text":"Again."}]},
                  {"id":"bad_action","trigger":{"type":"remaining_percentage","percentage":50},"actions":[{"id":"branch","type":"transition","target":"done","text":"unreachable"}]}
                ]}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("unreachable"))&&invalidErrors.stream().anyMatch(value->value.contains("repeat_interval_ticks"))&&invalidErrors.stream().anyMatch(value->value.contains("max_fires"))&&invalidErrors.stream().anyMatch(value->value.contains("unknown wave")),"malformed phases should produce focused diagnostics: "+invalidErrors);
        ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();var definition=compiledValidScene();SceneResources.installTestScenes(helper.getLevel().getServer(),List.of(definition));SceneInstance scene=data.start(definition,"phase_test",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();var sceneRecord=scene.currentRecord(definition.steps().get(scene.currentStep()).type());BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));EncounterInstance encounter=data.startEncounter(template,scene,"phase/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertValueEqual(encounter.phaseFireCounts().get("scouts_start"),1,"wave-start phase fire count");UUID scout=encounter.spawned().iterator().next();var scoutEntity=helper.getLevel().getEntity(scout);if(scoutEntity!=null)scoutEntity.discard();encounter.defeated(scout);encounter.defeatedMember("scout");EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertValueEqual(encounter.phaseFireCounts().get("scouts_clear"),1,"wave-complete phase fire count");helper.assertValueEqual(encounter.phaseFireCounts().get("half_remaining"),1,"remaining-enemy phase fire count");helper.assertValueEqual(sceneRecord.chosenTransition(),"finish","phase-selected scene transition");helper.assertValueEqual(com.jvn.villagerretaliation.quest.VillagerQuestFacts.get(helper.getLevel()).variable(com.jvn.villagerretaliation.quest.QuestScopeKey.player(player.getUUID()),"phase_scouts").orElse(""),"cleared","phase player fact");
        helper.runAfterDelay(6,()->{EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertValueEqual(encounter.phaseFireCounts().get("hold_line"),1,"first bounded repeatable phase fire");helper.runAfterDelay(6,()->{EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertValueEqual(encounter.phaseFireCounts().get("hold_line"),2,"repeatable phase maximum");UUID captain=encounter.spawned().stream().filter(id->!encounter.defeated().contains(id)).findFirst().orElseThrow();var captainEntity=helper.getLevel().getEntity(captain);helper.assertTrue(captainEntity!=null&&captainEntity.getPersistentData().getString("VillagerRetaliationMemberId").equals("captain"),"spawned elite should retain its stable member identity");captainEntity.discard();encounter.defeated(captain);encounter.defeatedMember("captain");EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertValueEqual(encounter.phaseFireCounts().get("captain_falls"),1,"elite defeat phase fire count");helper.assertTrue(com.jvn.villagerretaliation.quest.VillagerQuestFacts.get(helper.getLevel()).hasTag(com.jvn.villagerretaliation.quest.QuestScopeKey.player(player.getUUID()),VillagerRetaliation.id("phase_captain_defeated")),"elite phase fact tag should apply");EncounterInstance loaded=EncounterInstance.load(encounter.save());SceneInstance loadedScene=SceneInstance.load(scene.save());helper.assertValueEqual(loaded.phaseFireCounts(),encounter.phaseFireCounts(),"persisted phase fire counts");helper.assertValueEqual(loaded.phaseNextAt(),encounter.phaseNextAt(),"persisted phase repeat deadlines");long phaseReceipts=loadedScene.receipts().values().stream().filter(receipt->receipt.kind()==SceneOperationReceipt.Kind.ENCOUNTER_PHASE).count();helper.assertValueEqual(phaseReceipts,6L,"one durable receipt per phase run");helper.assertTrue(loadedScene.receipts().values().stream().allMatch(receipt->receipt.state()==SceneOperationReceipt.ReceiptState.COMPLETED),"phase and action receipts should survive reload completed once");helper.succeed();});});
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void composableEncounterObjectivesParseEvaluateAndPersist(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("objective_catalog"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:objective_catalog","members":[{"id":"leader","entity":"minecraft:zombie","custom_name":"Leader"}],
                 "spawn_points":[{"id":"gate","x":2,"y":1,"z":2},{"id":"yard","x":7,"y":1,"z":7}],
                 "completion_objectives":{"mode":"any","objectives":[
                   {"id":"clear","type":"all_defeated"},{"id":"gone","type":"all_gone"},{"id":"survive","type":"survive_duration","duration_ticks":40},
                   {"id":"protect","type":"protect_actor","actor":"guide","duration_ticks":40},{"id":"deny","type":"prevent_entry","point":"gate","duration_ticks":40},
                   {"id":"escort","type":"escort_actor","actor":"guide","point":"yard"},{"id":"targets","type":"destroy_targets","actors":["crate_a","crate_b"]},
                   {"id":"leader","type":"defeat_leader","member":"leader"},{"id":"retrieve","type":"retrieve_item","item":"minecraft:apple","count":2},
                   {"id":"hold","type":"hold_areas","points":["gate","yard"],"duration_ticks":20}
                 ]}}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"objective catalog should parse every objective type: "+errors);
        var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_objectives"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_objectives","members":[{"entity":"minecraft:zombie"}],"completion_condition":"all_defeated",
                 "completion_objectives":{"mode":"all","objectives":[{"id":"bad","type":"escort_actor","actor":"guide","point":"missing","duration_ticks":2}]}}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("mutually exclusive"))&&invalidErrors.stream().anyMatch(value->value.contains("unreachable"))&&invalidErrors.stream().anyMatch(value->value.contains("unknown spawn point")),"invalid objective composition should report focused diagnostics: "+invalidErrors);
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template));ServerPlayer player=helper.makeMockServerPlayerInLevel();player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE,2));SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(compiledValidScene(),"objective_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));var points=List.of(new EncounterInstance.ResolvedSpawnPoint("gate",helper.getLevel().dimension().location(),helper.absolutePos(new BlockPos(2,1,2)),1),new EncounterInstance.ResolvedSpawnPoint("yard",helper.getLevel().dimension().location(),helper.absolutePos(new BlockPos(7,1,7)),1));EncounterInstance encounter=data.startEncounter(template,scene,"objectives/start",helper.getLevel().dimension().location(),anchor,"normal",points).encounter();var result=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(result.status()==EncounterService.Status.COMPLETED&&encounter.completedObjectives().contains("retrieve")&&encounter.customCompletion(),"any composition should complete from a retrieved participant item without consuming it");EncounterInstance loaded=EncounterInstance.load(encounter.save());helper.assertValueEqual(loaded.completedObjectives(),encounter.completedObjectives(),"completed objectives survive reload");helper.assertTrue(loaded.customCompletion()&&player.getInventory().countItem(net.minecraft.world.item.Items.APPLE)==2,"custom completion flag persists and retrieve_item is non-consuming");
        var protectErrors=new java.util.ArrayList<String>();EncounterTemplate protect=EncounterResources.parse(VillagerRetaliation.id("protect_failure"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:protect_failure","members":[{"entity":"minecraft:zombie"}],"completion_objectives":{"objectives":[{"id":"guard","type":"protect_actor","actor":"guide","duration_ticks":20}]}}
        """).getAsJsonObject(),protectErrors);helper.assertTrue(protect!=null&&protectErrors.isEmpty(),"protect objective should parse: "+protectErrors);EncounterInstance failed=data.startEncounter(protect,scene,"objectives/protect",helper.getLevel().dimension().location(),anchor,"normal").encounter();failed.destroyActorAlias("guide");var failure=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,failed,protect);helper.assertTrue(failure.status()==EncounterService.Status.FAILED&&failed.failedObjectives().contains("guard"),"all composition should fail durably when a protected actor is destroyed");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void controlledEncounterAlliesStaySeparateRecoverAndPreserveSafely(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("controlled_allies"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:controlled_allies","members":[{"entity":"minecraft:zombie"}],
                 "allies":[
                   {"id":"guard","entity":"minecraft:iron_golem","invulnerable":true,"revivable":true,"revive_delay_ticks":20,"replacement_policy":"missing_if_loaded","cleanup_policy":"preserve","affects_completion":true},
                   {"id":"guide","actor":"guide","required_survival":true,"invulnerable":true,"cleanup_policy":"preserve"}
                 ]}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"controlled ally fixture should parse: "+errors);
        var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_allies"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_allies","members":[{"entity":"minecraft:zombie"}],"allies":[
                  {"id":"same","entity":"minecraft:iron_golem","required_survival":true,"revivable":true},
                  {"id":"same","actor":"guide","count":2,"equipment":{"mainhand":{"item":"minecraft:stick"}}},
                  {"id":"both","entity":"minecraft:iron_golem","actor":"guide"}
                ]}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("exactly one entity or actor"))&&invalidErrors.stream().anyMatch(value->value.contains("duplicate ally id"))&&invalidErrors.stream().anyMatch(value->value.contains("mutually exclusive"))&&invalidErrors.stream().anyMatch(value->value.contains("unreachable")),"invalid ally policies should produce focused diagnostics: "+invalidErrors);
        ServerPlayer player=helper.makeMockServerPlayerInLevel();Villager guide=spawnVillager(helper,1,1);var definition=compiledValidScene();SceneActorBinding binding=SceneActorBinding.entity("guide",VillagerRetaliation.id("villager"),guide.getUUID(),VillagerRetaliation.id("villager"),helper.getLevel().dimension().location(),guide.blockPosition(),"Guide",true);SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(definition,"ally_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of("guide",binding),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));EncounterInstance encounter=data.startEncounter(template,scene,"allies/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();var active=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(active.status()==EncounterService.Status.ACTIVE&&encounter.spawned().size()==1&&encounter.allies().size()==2,"hostiles and controlled allies should populate separate bounded ledgers");EncounterInstance.AllyIdentity guard=encounter.allies().get("guard"),captured=encounter.allies().get("guide");helper.assertTrue(!encounter.spawned().contains(guard.entityId())&&!encounter.spawned().contains(captured.entityId())&&captured.boundActor(),"allies must never enter hostile completion counts");var guardEntity=helper.getLevel().getEntity(guard.entityId());helper.assertTrue(guardEntity!=null&&guardEntity.isInvulnerable()&&guide.isInvulnerable(),"entity and bound allies should receive scoped invulnerability");EncounterInstance loaded=EncounterInstance.load(encounter.save());helper.assertValueEqual(loaded.allies().keySet(),encounter.allies().keySet(),"ally identity keys survive reload");helper.assertValueEqual(loaded.allies().get("guard").entityId(),guard.entityId(),"ally UUID survives reload");
        var hostile=helper.getLevel().getEntity(encounter.spawned().iterator().next());helper.assertTrue(EncounterService.shouldCancelFriendlyDamage(guide,guardEntity,null)&&!EncounterService.shouldCancelFriendlyDamage(guide,hostile,null),"friendly-fire protection should cover same-encounter allies without shielding hostile members");guardEntity.discard();encounter.replaceAlly(guard.dead(helper.getLevel().getGameTime()+20));EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(encounter.allyCompletionBlocked()&&encounter.allies().get("guard").state()==EncounterInstance.AllyState.DEAD,"an affecting revivable ally should durably gate completion until recovery");encounter.replaceAlly(encounter.allies().get("guard").dead(0L));EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(!encounter.allyCompletionBlocked()&&encounter.allies().get("guard").generation()==2&&!encounter.allies().get("guard").entityId().equals(guard.entityId()),"revival should create one replacement generation without reusing the dead UUID");EncounterService.cleanup(helper.getLevel().getServer(),data,encounter,false);helper.assertTrue(!guide.isInvulnerable()&&!guide.getPersistentData().hasUUID("VillagerRetaliationEncounterAlly"),"preserved bound allies should restore prior invulnerability and shed encounter ownership");helper.assertTrue(encounter.spawned().size()==1&&encounter.allies().size()==2&&encounter.allies().values().stream().allMatch(value->value.state()==EncounterInstance.AllyState.PRESERVED),"cleanup must preserve separate ally identities with their terminal state");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void encounterFailureRetriesPersistAndResetOwnedProgressOnce(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("retry_wave"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:retry_wave","members":[{"entity":"minecraft:zombie","count":2}],
                 "phases":[{"id":"opening","trigger":{"type":"wave_started","wave":"repeat_1"},"actions":[{"id":"line","type":"notification","text":"Attempt started."}]}],
                 "failure":{"on_player_death":"reset_wave","on_protected_actor_death":"pause","retry_delay_ticks":20,"max_attempts":3,"retain_defeated":false}}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"retry policy fixture should parse: "+errors);
        var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_retry"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_retry","members":[{"entity":"minecraft:zombie"}],
                 "failure":{"on_player_death":"branch_scene","retry_delay_ticks":20000,"max_attempts":0,"branch_step":"BAD STEP","mystery":true}}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("unknown failure field"))&&invalidErrors.stream().anyMatch(value->value.contains("retry_delay_ticks"))&&invalidErrors.stream().anyMatch(value->value.contains("max_attempts"))&&invalidErrors.stream().anyMatch(value->value.contains("branch_step")),"invalid retry policy should produce focused diagnostics: "+invalidErrors);
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template));ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();var definition=compiledValidScene();SceneInstance scene=data.start(definition,"retry_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));EncounterInstance encounter=data.startEncounter(template,scene,"retry/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(encounter.spawned().size()==2&&encounter.phaseFireCounts().get("opening")==1,"initial attempt should spawn once and fire its phase once");UUID defeated=encounter.spawned().iterator().next();var defeatedEntity=helper.getLevel().getEntity(defeated);if(defeatedEntity!=null)defeatedEntity.discard();encounter.defeated(defeated);Set<UUID> firstAttempt=encounter.spawned();long deadline=helper.getLevel().getGameTime()+20;encounter.scheduleFailure(EncounterTemplate.FailureAction.RESET_WAVE,deadline,"participant died","");EncounterInstance waiting=EncounterInstance.load(encounter.save());helper.assertTrue(waiting.attemptCount()==2&&waiting.retryAt()==deadline&&waiting.pendingFailureAction()==EncounterTemplate.FailureAction.RESET_WAVE,"attempt count, deadline, and action should survive reload");var before=EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,waiting,template);helper.assertTrue(before.status()==EncounterService.Status.WAITING&&waiting.spawned().equals(firstAttempt),"retry must not alter owned progress before its absolute deadline");CompoundTag readyTag=waiting.save();readyTag.putLong("RetryAt",0L);EncounterInstance ready=EncounterInstance.load(readyTag);EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,ready,template);helper.assertTrue(ready.spawned().size()==2&&ready.defeated().isEmpty()&&java.util.Collections.disjoint(ready.spawned(),firstAttempt),"wave reset should retire the old owned wave and spawn one clean replacement wave");helper.assertTrue(ready.phaseFireCounts().get("opening")==1&&ready.startedWaves().contains("repeat_1"),"retry must not replay one-time wave or phase effects");Set<UUID> beforePause=ready.spawned();ready.scheduleFailure(EncounterTemplate.FailureAction.PAUSE,0L,"protected actor unavailable","guide");EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,ready,template);helper.assertTrue(ready.state()==EncounterInstance.EncounterState.ACTIVE&&ready.spawned().equals(beforePause),"timed pause should resume without replacing owned mobs");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void encounterVariantsChooseOncePersistAndRejectRecursion(GameTestHelper helper) {
        EncounterStepExecutors.register();var errors=new java.util.ArrayList<String>();
        EncounterTemplate zombies=EncounterResources.parse(VillagerRetaliation.id("variant_zombies"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:variant_zombies","members":[{"entity":"minecraft:zombie"}]}
                """).getAsJsonObject(),errors);
        EncounterTemplate skeletons=EncounterResources.parse(VillagerRetaliation.id("variant_skeletons"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:variant_skeletons","members":[{"entity":"minecraft:skeleton"}]}
                """).getAsJsonObject(),errors);
        EncounterTemplate selector=EncounterResources.parse(VillagerRetaliation.id("roadblock_variants"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:roadblock_variants","variants":[{"id":"zombie_roadblock","weight":3,"template":"villagerretaliation:variant_zombies"},{"id":"skeleton_ambush","weight":2,"template":"villagerretaliation:variant_skeletons"}]}
                """).getAsJsonObject(),errors);
        helper.assertTrue(errors.isEmpty()&&zombies!=null&&skeletons!=null&&selector!=null,"variant fixtures should parse: "+errors);EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(zombies,skeletons,selector));long seed=EncounterResources.variantSeed(UUID.fromString("00000000-0000-0000-0000-000000000123"),"variant/start");var firstResolution=EncounterResources.resolve(helper.getLevel().getServer(),selector.id(),seed);var secondResolution=EncounterResources.resolve(helper.getLevel().getServer(),selector.id(),seed);helper.assertTrue(firstResolution.valid()&&firstResolution.equals(secondResolution)&&Set.of("zombie_roadblock","skeleton_ambush").contains(firstResolution.selectedVariantId()),"same deterministic seed must resolve the same weighted variant exactly once");
        BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));JsonObject sceneJson=JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:variant_scene","ownership":"player","entry_step":"start","actors":[],"steps":[{"id":"start","type":"villagerretaliation:start_encounter","data":{"variants":[{"id":"zombie_roadblock","weight":3,"template":"villagerretaliation:variant_zombies"},{"id":"skeleton_ambush","weight":2,"template":"villagerretaliation:variant_skeletons"}],"x":%d,"y":%d,"z":%d},"transitions":{"zombie_roadblock":"fight","skeleton_ambush":"fight"}},{"id":"fight","type":"villagerretaliation:wait_encounter","data":{"encounter_step":"start"},"next":"done"},{"id":"done","type":"villagerretaliation:scene_complete"}]}
                """.formatted(anchor.getX(),anchor.getY(),anchor.getZ())).getAsJsonObject();var definition=compiledScene(sceneJson);ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(definition,"variant/start",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();var step=definition.steps().get("start");var record=scene.currentRecord(step.type());var executor=SceneStepExecutors.get(step.type()).orElseThrow();var prepare=executor.prepare(new SceneExecutionContext(helper.getLevel().getServer(),data,scene,definition,step,record,1L,true));helper.assertValueEqual(prepare.outcome(),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.READY,"variant start preparation");String selected=record.durableValues().get("encounter_variant_id"),resolved=record.durableValues().get("encounter_resolved_template"),storedSeed=record.durableValues().get("encounter_variant_seed");SceneInstance loadedScene=SceneInstance.load(scene.save());var loadedRecord=loadedScene.stepRecords().get("start");helper.assertTrue(selected.equals(loadedRecord.durableValues().get("encounter_variant_id"))&&resolved.equals(loadedRecord.durableValues().get("encounter_resolved_template"))&&storedSeed.equals(loadedRecord.durableValues().get("encounter_variant_seed")),"variant id, resolved template, and seed must survive reload before spawning");var context=new SceneExecutionContext(helper.getLevel().getServer(),data,loadedScene,definition,step,loadedRecord,2L,false);helper.assertValueEqual(executor.apply(context).outcome(),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.APPLIED,"persisted variant spawn");EncounterInstance encounter=data.encounters().getLast();EncounterInstance loadedEncounter=EncounterInstance.load(encounter.save());helper.assertTrue(loadedEncounter.selectedVariantId().equals(selected)&&loadedEncounter.templateId().toString().equals(resolved)&&loadedRecord.chosenTransition().equals(selected)&&encounter.spawned().size()==1,"selected variant should persist and expose its branch without duplicate mobs");executor.apply(context);helper.assertTrue(data.encounters().size()==1&&encounter.spawned().size()==1,"replaying the start operation must reuse the selected variant and encounter");
        var cycleErrors=new java.util.ArrayList<String>();EncounterTemplate cycleA=EncounterResources.parse(VillagerRetaliation.id("cycle_a"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:cycle_a","variants":[{"id":"to_b","template":"villagerretaliation:cycle_b"}]}
                """).getAsJsonObject(),cycleErrors);EncounterTemplate cycleB=EncounterResources.parse(VillagerRetaliation.id("cycle_b"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:cycle_b","variants":[{"id":"to_a","template":"villagerretaliation:cycle_a"}]}
                """).getAsJsonObject(),cycleErrors);EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(cycleA,cycleB));var recursive=EncounterResources.resolve(helper.getLevel().getServer(),cycleA.id(),seed);helper.assertTrue(!recursive.valid()&&recursive.diagnostic().contains("recursive"),"recursive variant chains must be rejected with a focused diagnostic");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void encounterEnvironmentRestoresOnlyOwnedMatchingBlocks(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("environment_test"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:environment_test","members":[{"entity":"minecraft:zombie"}],"environment":{"cues":[{"id":"bell","type":"sound","sound":"minecraft:block.bell.use"},{"id":"smoke_column","type":"glowing_column","particle":"minecraft:smoke","count":16,"height":4}],"temporary_blocks":[{"id":"edited","block":"minecraft:glass","offset_x":1,"offset_y":2},{"id":"restored","block":"minecraft:barrier","offset_x":-1,"offset_y":2}]}}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"environment fixture should parse: "+errors);var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_environment"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_environment","members":[{"entity":"minecraft:zombie"}],"environment":{"cues":[{"id":"complex","type":"particles","particle":"minecraft:dust"}],"temporary_blocks":[{"id":"unsafe","block":"minecraft:diamond_block","offset_x":100}],"mystery":true}}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("unknown environment field"))&&invalidErrors.stream().anyMatch(value->value.contains("simple particle"))&&invalidErrors.stream().anyMatch(value->value.contains("non-allowlisted"))&&invalidErrors.stream().anyMatch(value->value.contains("offset_x")),"unsafe environment fields should produce focused diagnostics: "+invalidErrors);
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template));ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(compiledValidScene(),"environment_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4)),edited=anchor.offset(1,2,0),restored=anchor.offset(-1,2,0);helper.getLevel().setBlock(edited,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);helper.getLevel().setBlock(restored,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);EncounterInstance encounter=data.startEncounter(template,scene,"environment/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(helper.getLevel().getBlockState(edited).is(net.minecraft.world.level.block.Blocks.GLASS)&&helper.getLevel().getBlockState(restored).is(net.minecraft.world.level.block.Blocks.BARRIER)&&encounter.firedEnvironmentCues().size()==2,"environment setup should apply bounded blocks and one-time cues");EncounterInstance loaded=EncounterInstance.load(encounter.save());helper.assertTrue(loaded.environmentBlocks().size()==2&&loaded.firedEnvironmentCues().equals(encounter.firedEnvironmentCues()),"environment ownership and cue receipts should survive reload");helper.getLevel().setBlock(edited,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);EncounterService.cleanup(helper.getLevel().getServer(),data,encounter,true);helper.assertTrue(helper.getLevel().getBlockState(edited).is(net.minecraft.world.level.block.Blocks.STONE)&&helper.getLevel().getBlockState(restored).isAir(),"cleanup must preserve player edits and restore only matching encounter-owned blocks");helper.assertTrue(encounter.environmentBlocks().get("edited").state()==EncounterInstance.EnvironmentBlockState.PRESERVED&&encounter.environmentBlocks().get("restored").state()==EncounterInstance.EnvironmentBlockState.RESTORED,"environment cleanup decisions must be durable and idempotent");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void encounterGuidancePersistsParticipantDiscoveryAndArrival(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("guidance_test"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:guidance_test","members":[{"entity":"minecraft:zombie"}],"guidance":{"coordinate_message":"Find {location}; {distance}m {direction}.","arrival_message":"Arrived at {coordinates}.","discovery_radius":32,"arrival_radius":6,"distance_tracker":true,"compass_target":true,"directional_particles":true,"hud_marker":true,"exact_coordinates":"after_discovery","update_interval_ticks":20}}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"guidance fixture should parse: "+errors);var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_guidance"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_guidance","members":[{"entity":"minecraft:zombie"}],"location_message":"legacy","guidance":{"discovery_radius":4,"arrival_radius":9,"exact_coordinates":"sometimes","mystery":true}}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("mutually exclusive"))&&invalidErrors.stream().anyMatch(value->value.contains("unknown guidance field"))&&invalidErrors.stream().anyMatch(value->value.contains("exact_coordinates"))&&invalidErrors.stream().anyMatch(value->value.contains("arrival_radius")),"invalid guidance should produce focused diagnostics: "+invalidErrors);
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template));ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(compiledValidScene(),"guidance_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));player.moveTo(anchor.getX()+100.5D,anchor.getY(),anchor.getZ()+.5D,0,0);EncounterInstance encounter=data.startEncounter(template,scene,"guidance/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(encounter.guidanceNotified().contains(player.getUUID())&&!encounter.guidanceDiscovered().contains(player.getUUID())&&!encounter.guidanceArrived().contains(player.getUUID()),"far participant should receive guidance without discovering exact coordinates");var hidden=EncounterService.guidanceReplacements(player,encounter,template);helper.assertTrue(hidden.get("encounter_coordinates").isEmpty()&&!hidden.get("encounter_distance").isEmpty()&&!hidden.get("encounter_direction").isEmpty(),"tracker replacements should hide coordinates while retaining distance and bearing");player.moveTo(anchor.getX()+2.5D,anchor.getY(),anchor.getZ()+.5D,0,0);EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);helper.assertTrue(encounter.guidanceDiscovered().contains(player.getUUID())&&encounter.guidanceArrived().contains(player.getUUID()),"near participant should durably discover and arrive");ServerPlayer outsider=helper.makeMockServerPlayerInLevel();var privateValues=EncounterService.guidanceReplacements(outsider,encounter,template);helper.assertTrue(privateValues.get("encounter_coordinates").isEmpty()&&privateValues.get("encounter_distance").isEmpty()&&privateValues.get("encounter_dimension").isEmpty(),"guidance replacements must not expose participant-only destinations to scene outsiders");EncounterInstance loaded=EncounterInstance.load(encounter.save());helper.assertTrue(loaded.guidanceNotified().equals(encounter.guidanceNotified())&&loaded.guidanceDiscovered().equals(encounter.guidanceDiscovered())&&loaded.guidanceArrived().equals(encounter.guidanceArrived())&&loaded.guidanceNextAt().equals(encounter.guidanceNextAt()),"guidance decisions and update deadlines should survive reload");EncounterService.cleanup(helper.getLevel().getServer(),data,encounter,true);helper.assertTrue(encounter.guidanceCleared().contains(player.getUUID()),"cleanup should durably remove participant guidance");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void encounterRewardsAndDropsAreReceiptGuarded(GameTestHelper helper) {
        var errors=new java.util.ArrayList<String>();EncounterTemplate template=EncounterResources.parse(VillagerRetaliation.id("reward_test"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:reward_test","members":[{"id":"captain","entity":"minecraft:zombie","equipment":{"mainhand":{"item":"minecraft:iron_sword","drop_chance":1}}}],"phases":[{"id":"opening","trigger":{"type":"elapsed_time","ticks":20},"actions":[{"id":"warn","type":"notification","text":"Opening."}]}],"rewards":{"waves":[{"id":"wave_supply","wave":"repeat_1","item":"minecraft:arrow","count":4}],"phases":[{"id":"phase_token","phase":"opening","item":"minecraft:iron_nugget","count":2}],"completion":[{"id":"victory_medal","item":"minecraft:emerald","trophy_name":"Victory Medal"}],"trophies":[{"id":"captain_badge","member":"captain","item":"minecraft:gold_nugget","name":"Captain Badge"}],"drop_policy":"trophy_only"}}
                """).getAsJsonObject(),errors);helper.assertTrue(template!=null&&errors.isEmpty(),"reward fixture should parse: "+errors);var authoredErrors=new java.util.ArrayList<String>();EncounterTemplate authoredOnly=EncounterResources.parse(VillagerRetaliation.id("authored_drop_test"),JsonParser.parseString("{\"schema\":\"villagerretaliation:encounter/v1\",\"id\":\"villagerretaliation:authored_drop_test\",\"members\":[{\"entity\":\"minecraft:zombie\",\"equipment\":{\"mainhand\":{\"item\":\"minecraft:iron_sword\",\"drop_chance\":1}}}],\"rewards\":{\"drop_policy\":\"authored_only\"}}").getAsJsonObject(),authoredErrors);EncounterTemplate suppressed=EncounterResources.parse(VillagerRetaliation.id("suppressed_drop_test"),JsonParser.parseString("{\"schema\":\"villagerretaliation:encounter/v1\",\"id\":\"villagerretaliation:suppressed_drop_test\",\"members\":[{\"entity\":\"minecraft:zombie\"}],\"rewards\":{\"drop_policy\":\"suppress\"}}").getAsJsonObject(),authoredErrors);helper.assertTrue(authoredOnly!=null&&suppressed!=null&&authoredErrors.isEmpty(),"additional drop-policy fixtures should parse: "+authoredErrors);var invalidErrors=new java.util.ArrayList<String>();EncounterResources.parse(VillagerRetaliation.id("bad_rewards"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:bad_rewards","members":[{"id":"known","entity":"minecraft:zombie"}],"rewards":{"waves":[{"id":"duplicate","wave":"missing","item":"minecraft:arrow"}],"completion":[{"id":"duplicate","loot_table":"minecraft:chests/simple_dungeon","count":2}],"trophies":[{"id":"bad_trophy","member":"missing","item":"minecraft:gold_nugget"}],"drop_policy":"trophy_only","mystery":true}}
                """).getAsJsonObject(),invalidErrors);helper.assertTrue(invalidErrors.stream().anyMatch(value->value.contains("unknown rewards field"))&&invalidErrors.stream().anyMatch(value->value.contains("unknown wave"))&&invalidErrors.stream().anyMatch(value->value.contains("cannot configure count"))&&invalidErrors.stream().anyMatch(value->value.contains("unknown named member"))&&invalidErrors.stream().anyMatch(value->value.contains("duplicate encounter reward id")),"invalid rewards should produce focused diagnostics: "+invalidErrors);var missingLootErrors=new java.util.ArrayList<String>();EncounterTemplate missingLoot=EncounterResources.parse(VillagerRetaliation.id("missing_loot"),JsonParser.parseString("""
                {"schema":"villagerretaliation:encounter/v1","id":"villagerretaliation:missing_loot","members":[{"entity":"minecraft:zombie"}],"rewards":{"completion":[{"id":"missing","loot_table":"example:missing"}]}}
                """).getAsJsonObject(),missingLootErrors);EncounterResources.validateRewardLootTables(helper.getLevel().getServer(),missingLoot,missingLootErrors);helper.assertTrue(missingLootErrors.stream().anyMatch(value->value.contains("unknown encounter reward loot table")),"missing reward loot tables should be diagnosed at registry load");
        EncounterResources.installTestTemplates(helper.getLevel().getServer(),List.of(template,authoredOnly,suppressed));var definition=compiledValidScene();SceneResources.installTestScenes(helper.getLevel().getServer(),List.of(definition));ServerPlayer player=helper.makeMockServerPlayerInLevel();SceneSavedData data=new SceneSavedData();SceneInstance scene=data.start(definition,"reward_scene",new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of(),0L).instance();BlockPos anchor=helper.absolutePos(new BlockPos(4,1,4));EncounterInstance encounter=data.startEncounter(template,scene,"reward/start",helper.getLevel().dimension().location(),anchor,"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,encounter,template);var zombie=(net.minecraft.world.entity.LivingEntity)helper.getLevel().getEntity(encounter.spawned().iterator().next());encounter.startPhaseFire("opening",0L);EncounterService.refresh(helper.getLevel().getServer(),data,encounter);helper.assertValueEqual(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_NUGGET),2,"phase reward should grant once");helper.assertTrue(template.rewards()!=null&&template.rewards().dropPolicy()==EncounterTemplate.DropPolicy.TROPHY_ONLY&&zombie.getPersistentData().hasUUID("VillagerRetaliationEncounter"),"spawned hostile should retain its trophy-only policy and encounter owner tag; policy="+(template.rewards()==null?null:template.rewards().dropPolicy())+" tag="+zombie.getPersistentData());var drops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();drops.add(new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),zombie.getX(),zombie.getY(),zombie.getZ(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH)));EncounterService.onDrops(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(zombie,zombie.damageSources().generic(),drops,false),data);helper.assertTrue(drops.size()==1&&drops.getFirst().getItem().is(net.minecraft.world.item.Items.GOLD_NUGGET)&&drops.getFirst().getItem().getHoverName().getString().equals("Captain Badge"),"trophy_only should suppress normal and authored equipment drops and emit the named trophy; got "+drops.stream().map(drop->drop.getItem().getItem()+"/"+drop.getItem().getHoverName().getString()).toList());var replayDrops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();EncounterService.onDrops(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(zombie,zombie.damageSources().generic(),replayDrops,false),data);helper.assertTrue(replayDrops.isEmpty(),"a durable spawn index must never yield its trophy twice");EncounterService.onDeath(zombie,data);helper.assertTrue(encounter.state()==EncounterInstance.EncounterState.COMPLETED,"defeating the only hostile should complete the encounter");helper.assertValueEqual(player.getInventory().countItem(net.minecraft.world.item.Items.ARROW),4,"wave reward count");helper.assertValueEqual(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD),1,"completion trophy count");int receipts=scene.receipts().size();EncounterService.refresh(helper.getLevel().getServer(),data,encounter);EncounterService.maintainCleanup(helper.getLevel().getServer(),data);helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.ARROW)==4&&player.getInventory().countItem(net.minecraft.world.item.Items.IRON_NUGGET)==2&&player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD)==1&&scene.receipts().size()==receipts,"refresh and maintenance must not duplicate granted rewards");EncounterInstance loadedEncounter=EncounterInstance.load(encounter.save());SceneInstance loadedScene=SceneInstance.load(scene.save());helper.assertTrue(loadedEncounter.claimedTrophySpawns().equals(encounter.claimedTrophySpawns())&&loadedScene.receipts().size()==scene.receipts().size(),"trophy claims and reward receipts should survive reload");EncounterInstance authoredEncounter=data.startEncounter(authoredOnly,scene,"reward/authored",helper.getLevel().dimension().location(),anchor.offset(3,0,0),"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,authoredEncounter,authoredOnly);var authoredMob=(net.minecraft.world.entity.LivingEntity)helper.getLevel().getEntity(authoredEncounter.spawned().iterator().next());var authoredDrops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();authoredDrops.add(new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),authoredMob.getX(),authoredMob.getY(),authoredMob.getZ(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH)));EncounterService.onDrops(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(authoredMob,authoredMob.damageSources().generic(),authoredDrops,false),data);helper.assertTrue(authoredDrops.size()==1&&authoredDrops.getFirst().getItem().is(net.minecraft.world.item.Items.IRON_SWORD),"authored_only should replace vanilla loot with deterministic authored equipment");authoredEncounter.cancel();var cancelledDrops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();cancelledDrops.add(new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),authoredMob.getX(),authoredMob.getY(),authoredMob.getZ(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH)));EncounterService.onDrops(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(authoredMob,authoredMob.damageSources().generic(),cancelledDrops,false),data);helper.assertTrue(cancelledDrops.isEmpty(),"cancelled encounters must suppress special drops instead of becoming a farming path");EncounterInstance suppressedEncounter=data.startEncounter(suppressed,scene,"reward/suppressed",helper.getLevel().dimension().location(),anchor.offset(-3,0,0),"normal").encounter();EncounterService.reconcileSpawn(helper.getLevel().getServer(),data,suppressedEncounter,suppressed);var suppressedMob=(net.minecraft.world.entity.LivingEntity)helper.getLevel().getEntity(suppressedEncounter.spawned().iterator().next());var suppressedDrops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();suppressedDrops.add(new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),suppressedMob.getX(),suppressedMob.getY(),suppressedMob.getZ(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH)));EncounterService.onDrops(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(suppressedMob,suppressedMob.damageSources().generic(),suppressedDrops,false),data);helper.assertTrue(suppressedDrops.isEmpty(),"suppress should remove every encounter item drop");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void missingActorsFollowRequiredAndOptionalPolicies(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();
        JsonObject requiredJson=JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:missing_required","entry_step":"move","actors":[
                 {"alias":"guide","type":"villagerretaliation:villager","binding_source":"unbound","missing_actor_policy":"block"}],"steps":[
                 {"id":"move","type":"villagerretaliation:move_actor","actors":["guide"],"data":{"x":0,"y":64,"z":0},"next":"done"},
                 {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
        JsonObject optionalJson=requiredJson.deepCopy();optionalJson.addProperty("id","villagerretaliation:missing_optional");JsonObject optionalActor=optionalJson.getAsJsonArray("actors").get(0).getAsJsonObject();optionalActor.addProperty("required",false);optionalActor.addProperty("replacement_policy","optional");optionalActor.addProperty("missing_actor_policy","skip");
        for(var entry:List.of(Map.entry(compiledScene(requiredJson),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.BLOCK),Map.entry(compiledScene(optionalJson),com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.SKIP))){var definition=entry.getKey();UUID owner=UUID.randomUUID();SceneSavedData data=new SceneSavedData();SceneInstance instance=data.start(definition,"missing/"+owner,new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,owner,null,null,""),null,Set.of(owner),Map.of(),0L).instance();var step=definition.steps().get("move");var result=SceneStepExecutors.get(step.type()).orElseThrow().apply(new SceneExecutionContext(helper.getLevel().getServer(),data,instance,definition,step,instance.currentRecord(step.type()),1L,true));helper.assertValueEqual(result.outcome(),entry.getValue(),"missing actor policy outcome");}
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void offlineDialogueWaitsAndPartyReconnectCannotDuplicateSceneOrReward(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();JsonObject json=JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:offline_dialogue","ownership":"party","entry_step":"speak","actors":[],"steps":[
                 {"id":"speak","type":"villagerretaliation:dialogue","data":{"text":"Regroup at the gate."},"next":"done"},
                 {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();var definition=compiledScene(json);UUID party=UUID.randomUUID();UUID offline=UUID.randomUUID();SceneOwner owner=new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PARTY,null,party,null,"");SceneSavedData data=new SceneSavedData();var first=data.start(definition,"party/gate",owner,null,Set.of(offline),Map.of(),0L);var step=definition.steps().get("speak");var waiting=SceneStepExecutors.get(step.type()).orElseThrow().apply(new SceneExecutionContext(helper.getLevel().getServer(),data,first.instance(),definition,step,first.instance().currentRecord(step.type()),1L,true));helper.assertTrue(waiting.outcome()==com.jvn.villagerretaliation.scene.runtime.SceneStepResult.Outcome.WAIT&&first.instance().receipts().isEmpty(),"offline dialogue should wait without recording a false delivery");first.instance().prepareReceipt("party_reward",SceneOperationReceipt.Kind.LOOT_GRANT,2L).completed(2L,"claimed");var reconnect=data.start(definition,"party/gate",owner,null,Set.of(offline,UUID.randomUUID()),Map.of(),3L);helper.assertTrue(!reconnect.created()&&reconnect.instance().id().equals(first.instance().id())&&reconnect.instance().receipts().size()==1,"party reconnect or membership changes must not duplicate the scene or its reward receipt");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void compatibleQuestProviderRebindUpdatesSceneHistory(GameTestHelper helper) {
        Villager first=spawnVillager(helper,1,1);Villager second=spawnVillager(helper,2,1);JsonObject json=validScene();json.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("replacement_policy","compatible_replacement");var definition=compiledScene(json);SceneResources.installTestScenes(helper.getLevel().getServer(),List.of(definition));SceneSavedData data=SceneSavedData.get(helper.getLevel());UUID player=UUID.randomUUID();ResourceLocation quest=VillagerRetaliation.id("compatible_provider_scene");SceneActorBinding binding=SceneActorBinding.entity("guide",VillagerRetaliation.id("villager"),first.getUUID(),VillagerRetaliation.id("villager"),helper.getLevel().dimension().location(),first.blockPosition(),"First",true);SceneInstance scene=data.start(definition,"compatible/"+UUID.randomUUID(),new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player,null,null,""),null,Set.of(player),Map.of("guide",binding),0L).instance();scene.linkQuest(quest);SceneLifecycleIntegration.onQuestProviderRebind(helper.getLevel(),player,quest,first.getUUID(),second,"semantic_test_rebind");SceneActorBinding rebound=scene.actorBindings().get("guide");helper.assertTrue(second.getUUID().equals(rebound.entityId())&&rebound.replacementHistory().size()==1,"compatible quest-provider rebind should update the scene binding and append history");helper.assertTrue(data.auditEntries().stream().anyMatch(a->scene.id().equals(a.sceneId())&&a.actorAlias().equals("guide")),"compatible provider rebind should append a scene audit entry");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void operatorRepairAuditsWithoutErasingHistoryOrReceipts(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel();Villager first=spawnVillager(helper,1,1);Villager second=spawnVillager(helper,2,1);
        JsonObject json=validScene();json.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("replacement_policy","operator_rebindable");var definition=compiledScene(json);SceneResources.installTestScenes(helper.getLevel().getServer(),List.of(definition));SceneSavedData data=SceneSavedData.get(helper.getLevel());
        SceneActorBinding binding=SceneActorBinding.entity("guide",VillagerRetaliation.id("villager"),first.getUUID(),VillagerRetaliation.id("villager"),helper.getLevel().dimension().location(),first.blockPosition(),"First",true);SceneInstance scene=data.start(definition,"operator/"+UUID.randomUUID(),new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,player.getUUID(),null,null,""),null,Set.of(player.getUUID()),Map.of("guide",binding),0L).instance();scene.linkQuest(VillagerRetaliation.id("operator_test_quest"));scene.prepareReceipt("existing_reward",SceneOperationReceipt.Kind.EXPERIENCE_GRANT,1L).completed(1L,"already granted");
        var rebound=SceneOperatorService.rebind(helper.getLevel(),scene.id(),"guide",second,"repair binding","TestOperator");helper.assertTrue(rebound.success()&&scene.actorBindings().get("guide").replacementHistory().size()==1,"operator should rebind and retain replacement history");helper.assertTrue(scene.receipts().containsKey("existing_reward"),"operator repair must not erase receipts");
        scene.blockForRepair("manual_test","blocked",2L);var resumed=SceneOperatorService.resume(helper.getLevel(),scene.id(),"repair complete","TestOperator");helper.assertTrue(resumed.success()&&scene.state()==SceneState.RUNNING,"operator should resume repaired blocked scene");
        SceneLifecycleIntegration.onQuestTerminal(helper.getLevel(),player.getUUID(),VillagerRetaliation.id("operator_test_quest"),"abandoned");helper.assertValueEqual(scene.state(),SceneState.CANCELLED,"quest abandonment scene state");helper.assertTrue(data.auditEntries().stream().filter(a->a.sceneId().equals(scene.id())).count()>=2&&scene.receipts().containsKey("existing_reward"),"operator mutations should append audit entries without deleting history");helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questRunIdentityIsPlayerScopedStableAndRepeatable(GameTestHelper helper) {
        ResourceLocation questId = VillagerRetaliation.id("repeatable_scene_quest");
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        VillagerQuestSavedData.QuestProgress first = new VillagerQuestSavedData.QuestProgress();
        VillagerQuestSavedData.QuestProgress second = new VillagerQuestSavedData.QuestProgress();

        first.start(null, null, null, 1L);
        UUID firstRun = first.beginRun(firstPlayer, questId, null);
        helper.assertValueEqual(first.beginRun(firstPlayer, questId, null), firstRun,
                "duplicate start work in one active run must retain its run id");
        second.start(null, null, null, 1L);
        UUID unrelatedRun = second.beginRun(secondPlayer, questId, null);
        helper.assertFalse(firstRun.equals(unrelatedRun),
                "unrelated players on the same quest ordinal need globally distinct run ids");

        first.start(null, null, null, 2L);
        UUID repeatRun = first.beginRun(firstPlayer, questId, null);
        helper.assertFalse(firstRun.equals(repeatRun), "a legitimate repeat must allocate a new run id");
        helper.assertValueEqual(first.startCount(), 2, "duplicate identity allocation must not increment start count");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void overallDeadlinePreemptsWaitAndSurvivesReload(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();
        JsonObject timedJson = validScene();
        timedJson.addProperty("timeout_ticks", 10);
        timedJson.addProperty("failure_policy", "fail_scene");
        timedJson.getAsJsonArray("steps").get(0).getAsJsonObject()
                .getAsJsonObject("data").addProperty("ticks", 100);
        var definition = compiledScene(timedJson);
        SceneResources.installTestScenes(helper.getLevel().getServer(), List.of(definition));
        UUID playerId = UUID.randomUUID();
        SceneSavedData data = new SceneSavedData();
        SceneInstance waiting = data.start(definition, "deadline/wait",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""),
                null, Set.of(playerId), Map.of(), 100L).instance();
        SceneScheduler scheduler = new SceneScheduler(8, SceneStepEngine::process);
        scheduler.rebuild(helper.getLevel().getServer(), data, 100L);
        var initial = scheduler.tick(helper.getLevel().getServer(), data, 100L);
        helper.assertTrue(waiting.state() == SceneState.WAITING && initial.sleeping() == 1,
                "long wait should retain one bounded deadline wake");

        SceneScheduler reloaded = new SceneScheduler(8, SceneStepEngine::process);
        reloaded.rebuild(helper.getLevel().getServer(), data, 105L);
        helper.assertTrue(reloaded.tick(helper.getLevel().getServer(), data, 109L).workPerformed() == 0,
                "reload before the deadline must preserve the original absolute deadline");
        var deadlineTick = reloaded.tick(helper.getLevel().getServer(), data, 110L);
        helper.assertTrue(deadlineTick.workPerformed() == 1 && waiting.state() == SceneState.FAILED
                        && waiting.deadlineHandled() && deadlineTick.sleeping() == 0,
                "overall timeout must fire at its deadline instead of the later step wake");
        helper.assertTrue(reloaded.tick(helper.getLevel().getServer(), data, 111L).workPerformed() == 0,
                "a handled deadline must never repeatedly fire");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void blockedSceneKeepsItsOverallDeadlineWake(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();
        JsonObject timedJson = validScene();
        timedJson.addProperty("timeout_ticks", 10);
        timedJson.addProperty("failure_policy", "cancel_scene");
        var definition = compiledScene(timedJson);
        SceneResources.installTestScenes(helper.getLevel().getServer(), List.of(definition));
        UUID playerId = UUID.randomUUID();
        SceneSavedData data = new SceneSavedData();
        SceneInstance blocked = data.start(definition, "deadline/blocked",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""),
                null, Set.of(playerId), Map.of(), 200L).instance();
        blocked.block("actor_missing", "required actor is unavailable", 201L);

        SceneScheduler scheduler = new SceneScheduler(4, SceneStepEngine::process);
        scheduler.rebuild(helper.getLevel().getServer(), data, 205L);
        helper.assertTrue(scheduler.tick(helper.getLevel().getServer(), data, 209L).workPerformed() == 0,
                "blocked scene must sleep without polling before its deadline");
        var timeout = scheduler.tick(helper.getLevel().getServer(), data, 210L);
        helper.assertTrue(timeout.workPerformed() == 1 && blocked.state() == SceneState.CANCELLED
                        && blocked.deadlineHandled(),
                "blocked scene must time out without an actor-return event");

        SceneInstance overdue = data.start(definition, "deadline/overdue",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        UUID.randomUUID(), null, null, ""),
                null, Set.of(), Map.of(), 300L).instance();
        overdue.block("actor_missing", "required actor is unavailable", 301L);
        SceneScheduler afterDeadlineReload = new SceneScheduler(4, SceneStepEngine::process);
        afterDeadlineReload.rebuild(helper.getLevel().getServer(), data, 315L);
        helper.assertTrue(afterDeadlineReload.tick(helper.getLevel().getServer(), data, 315L).workPerformed() == 1
                        && overdue.state() == SceneState.CANCELLED,
                "reload after an elapsed deadline must process the timeout exactly once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sharedRunIdentityPrecedesScenesAndCannotBeReplaced(GameTestHelper helper) {
        ResourceLocation questId = VillagerRetaliation.id("shared_scene_quest");
        UUID sharedRun = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        VillagerQuestSavedData.QuestProgress first = new VillagerQuestSavedData.QuestProgress();
        VillagerQuestSavedData.QuestProgress second = new VillagerQuestSavedData.QuestProgress();

        second.start(null, null, null, 1L);
        second.beginRun(secondPlayer, questId, null);
        second.start(null, null, null, 2L);
        second.beginRun(secondPlayer, questId, null);
        first.start(null, null, null, 3L);
        second.start(null, null, null, 3L);
        helper.assertValueEqual(first.beginRun(firstPlayer, questId, sharedRun), sharedRun,
                "party leader must receive the shared identity before authored actions");
        helper.assertValueEqual(second.beginRun(secondPlayer, questId, sharedRun), sharedRun,
                "different personal start histories must converge on the shared identity");
        helper.assertTrue(first.linkPartyQuest(sharedRun) && second.linkPartyQuest(sharedRun),
                "linking the already definitive shared run should be idempotent");
        helper.assertFalse(first.linkPartyQuest(UUID.randomUUID()),
                "a party link must never replace an identity after scene launch could have occurred");
        helper.assertValueEqual(first.questRunId(), sharedRun, "rejected relink must preserve the original run id");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questInstanceOperationsAreScopedToDefinitiveRun(GameTestHelper helper) {
        var definition = compiledValidScene();
        ResourceLocation questId = VillagerRetaliation.id("operation_scope_quest");
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        UUID firstRun = VillagerQuestSavedData.QuestProgress.deterministicRunId(firstPlayer, questId, 1);
        UUID unrelatedRun = VillagerQuestSavedData.QuestProgress.deterministicRunId(secondPlayer, questId, 1);
        SceneSavedData data = new SceneSavedData();
        SceneOwner firstOwner = new SceneOwner(
                com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE,
                firstPlayer, null, firstRun, "");
        SceneOwner unrelatedOwner = new SceneOwner(
                com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE,
                secondPlayer, null, unrelatedRun, "");

        var first = data.start(definition, "stage/start", firstOwner, firstRun,
                Set.of(firstPlayer), Map.of(), 1L, questId);
        var duplicate = data.start(definition, "stage/start", firstOwner, firstRun,
                Set.of(firstPlayer, secondPlayer), Map.of(), 2L, questId);
        var unrelated = data.start(definition, "stage/start", unrelatedOwner, unrelatedRun,
                Set.of(secondPlayer), Map.of(), 2L, questId);
        UUID repeatRun = VillagerQuestSavedData.QuestProgress.deterministicRunId(firstPlayer, questId, 2);
        var repeat = data.start(definition, "stage/start",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE,
                        firstPlayer, null, repeatRun, ""),
                repeatRun, Set.of(firstPlayer), Map.of(), 3L, questId);

        helper.assertTrue(first.created() && !duplicate.created()
                        && duplicate.instanceId().equals(first.instanceId()),
                "the same operation in one run must reuse one scene");
        helper.assertTrue(unrelated.created() && !unrelated.instanceId().equals(first.instanceId()),
                "unrelated players must not collide on QUEST_INSTANCE ownership");
        helper.assertTrue(repeat.created() && !repeat.instanceId().equals(first.instanceId()),
                "a repeatable quest run must create a new scene");
        helper.assertTrue(first.instance().participants().contains(secondPlayer),
                "reusing a shared scene must merge later valid participants");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void executorFailureRunsAuthoredFailureStepExactlyOnce(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();
        JsonObject json = JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:failure_branch",
                 "failure_policy":"run_failure_step","entry_step":"move","actors":[
                  {"alias":"guide","type":"villagerretaliation:villager","binding_source":"unbound",
                   "missing_actor_policy":"fail"}],"steps":[
                  {"id":"move","type":"villagerretaliation:move_actor","actors":["guide"],
                   "data":{"x":0,"y":64,"z":0},"next":"done","failure_step":"recover"},
                  {"id":"recover","type":"villagerretaliation:scene_complete"},
                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
        var definition = compiledScene(json);
        SceneResources.installTestScenes(helper.getLevel().getServer(), List.of(definition));
        UUID playerId = UUID.randomUUID();
        SceneSavedData data = new SceneSavedData();
        SceneInstance scene = data.start(definition, "failure/branch",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""),
                null, Set.of(playerId), Map.of(), 0L).instance();
        SceneScheduler scheduler = new SceneScheduler(8, SceneStepEngine::process);
        scheduler.rebuild(helper.getLevel().getServer(), data, 0L);
        scheduler.tick(helper.getLevel().getServer(), data, 1L);
        helper.assertTrue(scene.state() == SceneState.COMPLETED && scene.currentStep().equals("recover"),
                "ordinary executor failure must follow failure_step and complete its recovery branch");
        helper.assertTrue(scene.cleanupStatus() == SceneInstance.CleanupStatus.RUNNING
                        && data.takeCleanupBatch(8, 1L).stream().filter(value -> value.id().equals(scene.id())).count() == 1,
                "completed recovery branch must queue cleanup exactly once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void transitionPoliciesArePersistedRepairableAndIdempotent(GameTestHelper helper) {
        var base = compiledValidScene();
        UUID playerId = UUID.randomUUID();
        for (var policy : com.jvn.villagerretaliation.scene.model.SceneResource.TransitionPolicy.values()) {
            var definition = sceneWithPolicy(base, policy, policy);
            SceneSavedData data = new SceneSavedData();
            SceneInstance scene = data.start(definition, "policy/" + policy.name().toLowerCase(),
                    new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                            playerId, null, null, ""),
                    null, Set.of(playerId), Map.of(), 0L).instance();
            SceneTransitionService.fail(data, scene, definition, "semantic_failure", "semantic failure", 1L);
            switch (policy) {
                case FAIL_SCENE -> helper.assertValueEqual(scene.state(), SceneState.FAILED, "FAIL_SCENE state");
                case CANCEL_SCENE -> helper.assertValueEqual(scene.state(), SceneState.CANCELLED, "CANCEL_SCENE state");
                case BLOCK_FOR_REPAIR -> helper.assertTrue(scene.state() == SceneState.BLOCKED
                                && scene.repairableBlocked(),
                        "BLOCK_FOR_REPAIR must produce an explicitly repairable block");
                case RUN_FAILURE_STEP -> helper.assertTrue(scene.state() == SceneState.BLOCKED
                                && scene.repairableBlocked()
                                && scene.failureCode().contains("failure_step_missing"),
                        "missing failure_step must block with a focused repair diagnostic");
            }
        }

        var cancellationDefinition = sceneWithPolicy(base,
                com.jvn.villagerretaliation.scene.model.SceneResource.TransitionPolicy.FAIL_SCENE,
                com.jvn.villagerretaliation.scene.model.SceneResource.TransitionPolicy.BLOCK_FOR_REPAIR);
        SceneSavedData cancellationData = new SceneSavedData();
        SceneInstance cancelled = cancellationData.start(cancellationDefinition, "policy/cancel",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""), null, Set.of(playerId), Map.of(), 0L).instance();
        SceneTransitionService.cancel(cancellationData, cancelled, cancellationDefinition,
                "quest_abandoned", "quest abandoned", 2L);
        helper.assertTrue(cancelled.state() == SceneState.BLOCKED && cancelled.repairableBlocked(),
                "cancellation must honor cancellation_policy instead of directly cancelling");

        SceneInstance pending = new SceneSavedData().start(base, "policy/pending",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        UUID.randomUUID(), null, null, ""), null, Set.of(), Map.of(), 0L).instance();
        pending.preparePolicyTransition(SceneInstance.TransitionIntent.FAILURE,
                "persisted_failure", "detected before save");
        SceneInstance reloaded = SceneInstance.load(pending.save());
        SceneSavedData reloadedData = new SceneSavedData();
        SceneTransitionService.applyPrepared(reloadedData, reloaded, base, 3L);
        SceneTransitionService.applyPrepared(reloadedData, reloaded, base, 4L);
        helper.assertTrue(reloaded.state() == SceneState.FAILED && reloaded.policyApplied()
                        && reloaded.cleanupStatus() == SceneInstance.CleanupStatus.RUNNING,
                "reload between detection and policy application must terminalize once and retain cleanup intent");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void everyTerminalResultStartsDurableCleanup(GameTestHelper helper) {
        var definition = compiledValidScene();
        SceneSavedData data = new SceneSavedData();
        java.util.List<SceneInstance> scenes = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            UUID playerId = UUID.randomUUID();
            scenes.add(data.start(definition, "terminal/" + index,
                    new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                            playerId, null, null, ""), null, Set.of(playerId), Map.of(), 0L).instance());
        }
        SceneTransitionService.complete(data, scenes.get(0), 1L);
        SceneTransitionService.fail(data, scenes.get(1), definition, "failed", "failed", 1L);
        SceneTransitionService.cancel(data, scenes.get(2), definition, "cancelled", "cancelled", 1L);
        helper.assertTrue(scenes.stream().allMatch(scene -> scene.cleanupStatus() == SceneInstance.CleanupStatus.RUNNING),
                "complete, fail, and cancel must remain unsettled until cleanup finishes");
        helper.assertValueEqual(data.takeCleanupBatch(8, 1L).size(), 3,
                "each terminal path must enqueue one cleanup job");
        helper.assertTrue(data.takeCleanupBatch(8, 1L).isEmpty(), "cleanup jobs must dequeue only once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sceneContinuationSurvivesReloadAndResumesOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager provider = spawnVillager(helper, 1, 1);
        var definition = compiledValidScene();
        UUID ownerId = player.getUUID();
        SceneSavedData data = new SceneSavedData();
        SceneInstance scene = data.start(definition, "continuation/success",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        ownerId, null, null, ""), null, Set.of(ownerId), Map.of(), 0L).instance();
        JsonObject wrapper = JsonParser.parseString("""
                {"actions":[
                  {"type":"start_scene","scene":"villagerretaliation:gate_scene","operation_id":"wait","wait_for_result":true},
                  {"type":"experience","amount":7}]}
                """).getAsJsonObject();
        List<VillagerActionDefinition> actions = VillagerActionDefinition.readList(
                VillagerRetaliation.id("quests/continuation.json"), "continuation", wrapper, null);
        var first = data.suspendContinuation(scene, ownerId, provider.getUUID(),
                "stage_entry/0/wait", actions, 1, Map.of("branch", "bold"));
        var duplicate = data.suspendContinuation(scene, ownerId, provider.getUUID(),
                "stage_entry/0/wait", actions, 1, Map.of());
        helper.assertTrue(first.id().equals(duplicate.id()) && data.continuations().size() == 1,
                "duplicate packets must reuse one pending continuation");
        SceneTransitionService.complete(data, scene, 1L);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        SceneSavedData loaded = SceneSavedData.load(saved, helper.getLevel().registryAccess());
        int before = player.totalExperience;
        SceneContinuationService.maintain(helper.getLevel().getServer(), loaded);
        SceneContinuationService.maintain(helper.getLevel().getServer(), loaded);
        var resumed = loaded.continuations().getFirst();
        helper.assertTrue(player.totalExperience == before + 7 && resumed.completionReceipt()
                        && resumed.sceneResult() == SceneInstance.CompletionResult.SUCCESS,
                "successful scene continuation must resume exactly once after reload");
        helper.assertValueEqual(resumed.replacements().get("branch"), "bold",
                "compiled continuation replacements must survive reload");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void failedAndCancelledContinuationsRemainDistinguishable(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager provider = spawnVillager(helper, 1, 1);
        var definition = compiledValidScene();
        SceneSavedData data = new SceneSavedData();
        java.util.List<SceneInstance> scenes = new java.util.ArrayList<>();
        for (int index = 0; index < 2; index++) {
            scenes.add(data.start(definition, "continuation/result/" + index,
                    new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                            player.getUUID(), null, null, ""), null, Set.of(player.getUUID()), Map.of(), 0L).instance());
            data.suspendContinuation(scenes.get(index), player.getUUID(), provider.getUUID(),
                    "result/" + index, List.of(), 0, Map.of());
        }
        SceneTransitionService.fail(data, scenes.get(0), definition, "failed", "failed", 1L);
        SceneTransitionService.cancel(data, scenes.get(1), definition, "cancelled", "cancelled", 1L);
        SceneContinuationService.maintain(helper.getLevel().getServer(), data);
        helper.assertTrue(data.continuations().stream().allMatch(value -> value.completionReceipt())
                        && data.continuations().stream().map(value -> value.sceneResult()).collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of(SceneInstance.CompletionResult.FAILURE, SceneInstance.CompletionResult.CANCELLED)),
                "failure and cancellation outcomes must persist distinctly without running success actions");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questTransitionShapeIsTypedAndOriginSceneIsNotCancelled(GameTestHelper helper) {
        JsonObject gate = JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:gate_transition",
                 "entry_step":"finish_quest","actors":[],"steps":[
                  {"id":"finish_quest","type":"villagerretaliation:quest_transition",
                   "data":{"quest":"gate_story:gate_ambush","target":"complete"},"next":"done"},
                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
        var definition = compiledScene(gate);
        helper.assertTrue(definition.steps().get("finish_quest").parameters().get("target").getAsString().equals("complete"),
                "gate example target complete shape must compile as a typed quest transition");
        JsonObject mixed = gate.deepCopy();
        mixed.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data")
                .addProperty("target_stage", "other");
        var mixedResult = SceneCompiler.compile(SceneParser.parse(
                VillagerRetaliation.id("quest_scenes/mixed_transition.json"), mixed).resource());
        helper.assertTrue(mixedResult.diagnostics().stream().anyMatch(value -> value.code().equals("scene.quest_transition.invalid")),
                "mixed terminal and stage targets must be rejected during scene compilation");

        SceneSavedData data = new SceneSavedData();
        UUID playerId = UUID.randomUUID();
        ResourceLocation questId = ResourceLocation.parse("gate_story:gate_ambush");
        SceneInstance origin = data.start(definition, "origin",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""), null, Set.of(playerId), Map.of(), 0L, questId).instance();
        SceneInstance sibling = data.start(definition, "sibling",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        playerId, null, null, ""), null, Set.of(playerId), Map.of(), 0L, questId).instance();
        SceneLifecycleIntegration.withOriginatingScene(origin.id(), () -> {
            helper.assertTrue(SceneLifecycleIntegration.isOriginatingScene(origin.id()),
                    "quest terminal callback must recognize its originating scene");
            if (!SceneLifecycleIntegration.isOriginatingScene(sibling.id())) {
                SceneTransitionService.cancel(data, sibling, definition,
                        "quest_completed", "owning quest became terminal", 1L);
            }
            return null;
        });
        helper.assertTrue(!origin.state().terminal() && sibling.state() == SceneState.CANCELLED,
                "originating scene must remain active while sibling cancellation follows policy");
        SceneTransitionService.complete(data, origin, 2L);
        helper.assertValueEqual(origin.state(), SceneState.COMPLETED,
                "originating quest-transition scene must finish completed, not cancelled");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sceneResourceBoundaryRejectsErrorsButLoadsWarnings(GameTestHelper helper) {
        JsonObject invalid = validScene();
        invalid.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("next", "missing_step");
        JsonObject warningOnly = validScene();
        warningOnly.addProperty("id", "villagerretaliation:warning_only_scene");
        warningOnly.getAsJsonArray("steps").add(JsonParser.parseString("""
                {"id":"unused","type":"villagerretaliation:scene_complete"}
                """));
        ResourceLocation invalidSource = VillagerRetaliation.id("quest_scenes/invalid_boundary.json");
        ResourceLocation warningSource = VillagerRetaliation.id("quest_scenes/warning_boundary.json");
        SceneResources.installTestResources(helper.getLevel().getServer(),
                Map.of(invalidSource, invalid, warningSource, warningOnly));
        helper.assertTrue(SceneResources.scene(helper.getLevel().getServer(), VillagerRetaliation.id("gate_scene")).isEmpty(),
                "parser/compiler errors must never enter SceneResources");
        helper.assertTrue(SceneResources.scene(helper.getLevel().getServer(),
                VillagerRetaliation.id("warning_only_scene")).isPresent(),
                "warning-only scenes must remain loadable");
        var invalidDiagnostics = SceneResources.diagnostics(helper.getLevel().getServer())
                .get(VillagerRetaliation.id("gate_scene"));
        helper.assertTrue(invalidDiagnostics != null && invalidDiagnostics.stream().anyMatch(value ->
                        value.severity() == SceneDiagnostic.Severity.ERROR
                                && invalidSource.equals(value.resourceId())
                                && value.path().contains("transition")),
                "scene diagnostics must retain source resource ids and focused JSON paths");
        SceneResources.clearCache();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void settledTerminalHistoryCompactsWithoutReplay(GameTestHelper helper) {
        var definition = compiledValidScene();
        ResourceLocation questId = VillagerRetaliation.id("compaction_quest");
        UUID playerId = UUID.randomUUID();
        UUID runId = VillagerQuestSavedData.QuestProgress.deterministicRunId(playerId, questId, 1);
        SceneOwner owner = new SceneOwner(
                com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE,
                playerId, null, runId, "");
        SceneSavedData data = new SceneSavedData();
        SceneInstance scene = data.start(definition, "compaction/reward", owner, runId,
                Set.of(playerId), Map.of(), 0L, questId).instance();
        scene.prepareReceipt("reward", SceneOperationReceipt.Kind.LOOT_GRANT, 1L)
                .completed(1L, "granted once");
        var continuation = data.suspendContinuation(scene, playerId, null, "compaction/continuation",
                List.of(), 0, Map.of());
        continuation.complete();
        SceneTransitionService.complete(data, scene, 2L);
        scene.cleanupStatus(SceneInstance.CleanupStatus.COMPLETE);
        long maintenanceTime = 2L + SceneSavedData.TERMINAL_RETENTION_TICKS;
        helper.assertValueEqual(data.compactTerminalHistory(maintenanceTime, 8), 1,
                "fully settled terminal scene should compact incrementally");
        helper.assertTrue(data.all().isEmpty() && data.tombstones().size() == 1
                        && data.tombstones().getFirst().completedReceiptIds().contains("reward"),
                "compaction must retain a replay-blocking receipt tombstone");
        var replay = data.start(definition, "compaction/reward", owner, runId,
                Set.of(playerId), Map.of(), maintenanceTime + 1L, questId);
        helper.assertTrue(!replay.created() && replay.instance() == null && replay.instanceId().equals(scene.id()),
                "compacted operation must not replay its scene, reward, or continuation");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        SceneSavedData reloaded = SceneSavedData.load(saved, helper.getLevel().registryAccess());
        var replayAfterReload = reloaded.start(definition, "compaction/reward", owner, runId,
                Set.of(playerId), Map.of(), maintenanceTime + 2L, questId);
        helper.assertTrue(!replayAfterReload.created() && reloaded.tombstones().size() == 1,
                "operation tombstone must survive reload and continue blocking replay");

        SceneInstance unresolved = reloaded.start(definition, "compaction/unresolved",
                new SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                        UUID.randomUUID(), null, null, ""), null, Set.of(), Map.of(), 0L).instance();
        unresolved.prepareReceipt("ambiguous_reward", SceneOperationReceipt.Kind.LOOT_GRANT, 1L);
        SceneTransitionService.complete(reloaded, unresolved, 2L);
        unresolved.cleanupStatus(SceneInstance.CleanupStatus.COMPLETE);
        helper.assertValueEqual(reloaded.compactTerminalHistory(maintenanceTime, 8), 0,
                "unresolved receipts must prevent terminal compaction");
        helper.assertTrue(reloaded.get(unresolved.id()).isPresent(),
                "ambiguous terminal record must remain available for operator repair");
        helper.succeed();
    }

    private static RuntimeTypeDescriptor descriptor(String path, Set<net.minecraft.resources.ResourceLocation> aliases) {
        return new RuntimeTypeDescriptor(VillagerRetaliation.id(path), aliases, Set.of(), Set.of(),
                JsonObject::deepCopy, value -> List.of(), (value, context) -> value, String::valueOf,
                RecoveryMode.NATURALLY_IDEMPOTENT,
                new ToolingMetadata(path, path, Map.of("type", "object"), true), ClientSync.NONE);
    }

    private static com.jvn.villagerretaliation.scene.model.CompiledScene sceneWithPolicy(
            com.jvn.villagerretaliation.scene.model.CompiledScene base,
            com.jvn.villagerretaliation.scene.model.SceneResource.TransitionPolicy failure,
            com.jvn.villagerretaliation.scene.model.SceneResource.TransitionPolicy cancellation) {
        return new com.jvn.villagerretaliation.scene.model.CompiledScene(
                base.id(), base.definitionVersion(), base.definitionHash(), base.ownership(), base.metadata(),
                base.actors(), base.entryStep(), base.steps(), failure, cancellation,
                base.cleanupPolicy(), base.timeoutTicks());
    }

    private static Villager spawnVillager(GameTestHelper helper, int x, int z) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "villager should create");
        BlockPos position = helper.absolutePos(new BlockPos(x, 1, z));
        villager.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(villager), "villager should spawn in the test structure");
        return villager;
    }

    private static SceneActorDeclaration actor(String alias, ReplacementPolicy policy) {
        return new SceneActorDeclaration(alias, VillagerRetaliation.id("villager"),
                Set.of(VillagerRetaliation.id("capability/dialogue")), true, BindingSource.QUEST_PROVIDER, "issuer",
                policy, MissingActorPolicy.BLOCK, DeathPolicy.APPLY_MISSING_POLICY, Map.of(), 200L);
    }

    private static SceneActorBinding binding(String alias, String name, int position) {
        return SceneActorBinding.entity(alias, VillagerRetaliation.id("villager"), UUID.randomUUID(),
                VillagerRetaliation.id("villager"), VillagerRetaliation.id("overworld"),
                new BlockPos(position, 64, position), name, true);
    }

    private static JsonObject validScene() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:gate_scene",
                 "definition_version":3,"ownership":"player","entry_step":"opening_wait",
                 "actors":[{"alias":"guide","type":"villagerretaliation:villager","required":true,
                   "capabilities":["villagerretaliation:capability/dialogue"],"binding_source":"quest_provider",
                   "replacement_policy":"fixed","missing_actor_policy":"block"}],
                 "steps":[
                   {"id":"opening_wait","type":"villagerretaliation:wait_ticks","actors":["guide"],
                    "data":{"ticks":20},"next":"finish"},
                   {"id":"finish","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
    }

    private static com.jvn.villagerretaliation.scene.model.CompiledScene compiledValidScene() {
        SceneParser.ParseResult parsed = SceneParser.parse(VillagerRetaliation.id("quest_scenes/test.json"), validScene());
        SceneCompiler.CompileResult compiled = SceneCompiler.compile(parsed.resource());
        if (!compiled.valid()) throw new IllegalStateException(compiled.diagnostics().toString());
        return compiled.scene();
    }

    private static com.jvn.villagerretaliation.scene.model.CompiledScene compiledScene(JsonObject root) {
        SceneCompiler.CompileResult compiled=SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id("quest_scenes/test.json"),root).resource());
        if(!compiled.valid())throw new IllegalStateException(compiled.diagnostics().toString());return compiled.scene();
    }

    private static void expectFailure(GameTestHelper helper, Runnable operation, String message) {
        try {
            operation.run();
            helper.fail("Expected " + message + " rejection");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected precise registry contract rejection.
        }
    }
}
