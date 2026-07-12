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
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import com.jvn.villagerretaliation.scene.runtime.SceneRecoveryPolicy;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.scene.SceneOperatorService;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
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
        helper.assertTrue(migrated.targetVersion() == 2 && migrated.data().contains("Instances"),
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
        scene.block("manual_test","blocked",2L);var resumed=SceneOperatorService.resume(helper.getLevel(),scene.id(),"repair complete","TestOperator");helper.assertTrue(resumed.success()&&scene.state()==SceneState.RUNNING,"operator should resume repaired blocked scene");
        SceneLifecycleIntegration.onQuestTerminal(helper.getLevel(),player.getUUID(),VillagerRetaliation.id("operator_test_quest"),"abandoned");helper.assertValueEqual(scene.state(),SceneState.CANCELLED,"quest abandonment scene state");helper.assertTrue(data.auditEntries().stream().filter(a->a.sceneId().equals(scene.id())).count()>=2&&scene.receipts().containsKey("existing_reward"),"operator mutations should append audit entries without deleting history");helper.succeed();
    }

    private static RuntimeTypeDescriptor descriptor(String path, Set<net.minecraft.resources.ResourceLocation> aliases) {
        return new RuntimeTypeDescriptor(VillagerRetaliation.id(path), aliases, Set.of(), Set.of(),
                JsonObject::deepCopy, value -> List.of(), (value, context) -> value, String::valueOf,
                RecoveryMode.NATURALLY_IDEMPOTENT,
                new ToolingMetadata(path, path, Map.of("type", "object"), true), ClientSync.NONE);
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
