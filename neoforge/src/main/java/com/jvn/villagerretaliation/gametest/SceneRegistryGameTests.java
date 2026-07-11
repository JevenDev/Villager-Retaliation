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
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
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
import com.jvn.villagerretaliation.scene.runtime.StepExecutionStatus;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorBindingService;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.BindingSource;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.DeathPolicy;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.MissingActorPolicy;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration.ReplacementPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SceneRegistryGameTests {
    private static final String EMPTY_TEMPLATE = "villagerretaliation:empty";

    private SceneRegistryGameTests() {
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
                {"actions":[{"type":"start_scene","scene_id":"villagerretaliation:gate_ambush",
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
        helper.assertTrue(migrated.targetVersion() == 1 && migrated.data().contains("Instances"),
                "legacy pre-release scene list should migrate explicitly from version zero");
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

    private static RuntimeTypeDescriptor descriptor(String path, Set<net.minecraft.resources.ResourceLocation> aliases) {
        return new RuntimeTypeDescriptor(VillagerRetaliation.id(path), aliases, Set.of(), Set.of(),
                JsonObject::deepCopy, value -> List.of(), (value, context) -> value, String::valueOf,
                RecoveryMode.NATURALLY_IDEMPOTENT,
                new ToolingMetadata(path, path, Map.of("type", "object"), true), ClientSync.NONE);
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

    private static void expectFailure(GameTestHelper helper, Runnable operation, String message) {
        try {
            operation.run();
            helper.fail("Expected " + message + " rejection");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected precise registry contract rejection.
        }
    }
}
