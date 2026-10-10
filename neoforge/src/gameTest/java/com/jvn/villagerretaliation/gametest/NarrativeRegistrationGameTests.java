package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionSequenceRunner;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ClientSync;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ToolingMetadata;
import com.jvn.villagerretaliation.api.registry.FreezableExtensionRegistry;
import com.jvn.villagerretaliation.api.registry.RuntimeTypeDescriptor;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutor;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestRegistryMetadata;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.provider.QuestProviderRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_registration")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID)
public final class NarrativeRegistrationGameTests {
    private static final ResourceLocation STEP = VillagerRetaliation.id("registration_contract_step");
    private static final ResourceLocation ALIAS = VillagerRetaliation.id("registration_contract_legacy");
    private static final AtomicInteger PARSED = new AtomicInteger();
    private static final AtomicInteger VALIDATED = new AtomicInteger();
    private static final AtomicInteger APPLIED = new AtomicInteger();

    private NarrativeRegistrationGameTests() { }

    @SubscribeEvent
    public static void registerFixture(FMLCommonSetupEvent event) {
        VillagerRetaliationRegistries.SCENE_STEPS.register(new RuntimeTypeDescriptor(
                STEP, Set.of(ALIAS), Set.of(), Set.of(), object -> {
                    PARSED.incrementAndGet();
                    JsonObject parsed = object.deepCopy();
                    parsed.addProperty("parsed", true);
                    return parsed;
                }, value -> {
                    VALIDATED.incrementAndGet();
                    JsonObject parsed = (JsonObject) value;
                    return parsed.has("parsed") && parsed.get("accepted").getAsBoolean()
                            ? List.of() : List.of("extension data rejected");
                }, (value, context) -> value, String::valueOf, RecoveryMode.NATURALLY_IDEMPOTENT,
                new ToolingMetadata("Registration fixture", "Registration fixture", Map.of("type", "object"), false),
                ClientSync.NONE));
        SceneStepExecutors.register(ALIAS, new SceneStepExecutor() {
            public RecoveryMode recoveryMode() { return RecoveryMode.NATURALLY_IDEMPOTENT; }
            public SceneStepResult prepare(SceneExecutionContext context) { return SceneStepResult.ready(); }
            public SceneStepResult apply(SceneExecutionContext context) {
                APPLIED.incrementAndGet();
                return SceneStepResult.applied();
            }
            public SceneStepResult verify(SceneExecutionContext context) { return SceneStepResult.complete(); }
            public SceneStepResult reconcile(SceneExecutionContext context) {
                return SceneStepResult.block("fixture_interrupted", "fixture must apply within the test session");
            }
        });
    }

    @GameTest(template = "empty")
    public static void publicQuestDescriptorsMatchRuntimeRegistrations(GameTestHelper helper) {
        VillagerRetaliationRegistries.registerBuiltIns();
        assertMirror(helper, VillagerRetaliationRegistries.QUEST_ACTIONS,
                VillagerActionRegistry.descriptors().stream().map(value -> value.id()).collect(Collectors.toSet()));
        assertMirror(helper, VillagerRetaliationRegistries.QUEST_CONDITIONS,
                DialogueCondition.descriptors().stream().map(value -> value.id()).collect(Collectors.toSet()));
        assertMirror(helper, VillagerRetaliationRegistries.QUEST_OBJECTIVES,
                QuestObjectiveRegistry.descriptors().stream().map(value -> value.id()).collect(Collectors.toSet()));
        assertMirror(helper, VillagerRetaliationRegistries.QUEST_TRIGGER_EVENTS,
                QuestTriggerRegistry.descriptors().stream().map(value -> value.id()).collect(Collectors.toSet()));
        helper.assertValueEqual(VillagerRetaliationRegistries.QUEST_PROVIDERS.descriptors().stream()
                        .map(RuntimeTypeDescriptor::id).collect(Collectors.toSet()),
                QuestProviderRegistry.descriptors().stream().map(value -> value.id()).collect(Collectors.toSet()),
                "provider descriptor identities match runtime registrations");
        Set<VillagerActionDefinition.Kind> reconciled = Set.of(VillagerActionDefinition.Kind.CAMPAIGN_PAYMENT,
                VillagerActionDefinition.Kind.CAMPAIGN_REWARD, VillagerActionDefinition.Kind.CAMPAIGN_STANDING,
                VillagerActionDefinition.Kind.CAMPAIGN_FACT, VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME,
                VillagerActionDefinition.Kind.START_SCENE);
        for (var action : VillagerActionRegistry.descriptors()) {
            var mirrored = VillagerRetaliationRegistries.QUEST_ACTIONS.get(VillagerRetaliation.id(action.id())).orElseThrow();
            helper.assertValueEqual(mirrored.recoveryMode(), action.recoveryMode(), "action recovery agrees");
            helper.assertValueEqual(action.reconcilesPreparedOperation(), reconciled.contains(action.kind()),
                    "prepared operation reconciliation retains its existing conservative action set");
            for (String alias : action.aliases()) {
                helper.assertTrue(VillagerRetaliationRegistries.QUEST_ACTIONS.get(VillagerRetaliation.id(alias)).orElseThrow() == mirrored,
                        "public action alias resolves to its runtime type");
            }
        }
        var metadata = QuestRegistryMetadata.export().getAsJsonObject("registries");
        helper.assertTrue(metadata.getAsJsonArray("public_quest_actions").asList().stream()
                        .allMatch(value -> value.getAsJsonObject().get("runtime_binding").getAsString().equals("descriptor_only")),
                "quest descriptors must not advertise an executable extension path");
        var sceneFixture = metadata.getAsJsonArray("scene_steps").asList().stream()
                .map(value -> value.getAsJsonObject()).filter(value -> value.get("id").getAsString().equals(STEP.toString()))
                .findFirst().orElseThrow();
        helper.assertValueEqual(sceneFixture.get("runtime_binding").getAsString(), "scene_executor",
                "attached scene executors expose executable bindings");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actionAliasParsesAndExecutesThroughExistingRunner(GameTestHelper helper) {
        var raw = json("{\"actions\":[{\"type\":\"xp\",\"amount\":7,\"required\":true}]}");
        var actions = VillagerActionDefinition.readList(VillagerRetaliation.id("registration_fixture"), "fixture", raw);
        helper.assertValueEqual(actions.size(), 1, "registered action alias parses");
        helper.assertValueEqual(VillagerActionRegistry.canonicalTypeId(actions.getFirst()), "experience", "alias resolves canonically");
        var player = helper.makeMockServerPlayerInLevel();
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "fixture provider exists");
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        int before = player.totalExperience;
        var result = ActionSequenceRunner.run(context, actions, 0, Map.of(), null, null);
        helper.assertTrue(result.outcome().success(), "registered handler completes through the existing runner");
        helper.assertValueEqual(player.totalExperience, before + 7, "registered effect applies once");
        helper.assertValueEqual(result.nextActionIndex(), 1, "runner advances the existing cursor");
        var schema = QuestV2Schema.export().getAsJsonObject("$defs").getAsJsonObject("action");
        helper.assertValueEqual(schema.getAsJsonArray("allOf"), VillagerActionRegistry.structuralRules(),
                "authoring validation derives action rules from runtime registrations");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredConditionSchemasPreserveRecursiveComposition(GameTestHelper helper) {
        JsonObject schema = new JsonObject();
        schema.addProperty("$ref", "#/$defs/condition");
        schema.add("$defs", QuestV2Schema.export().getAsJsonObject("$defs"));
        for (String invalid : List.of("{\"type\":\"and\"}", "{\"type\":\"or\",\"conditions\":[]}",
                "{\"type\":\"not\"}", "{\"type\":\"not\",\"condition\":{\"type\":\"unsupported\"}}")) {
            helper.assertFalse(QuestJsonSchema.validate(json(invalid), schema).isEmpty(),
                    "registered condition schemas retain required and recursive constraints: " + invalid);
        }
        helper.assertTrue(QuestJsonSchema.validate(json("""
                {"type":"all_of","conditions":[{"type":"not","condition":{"type":"reputation","min":1}}]}
                """), schema).isEmpty(), "registered aliases retain the same valid recursive structure");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredSceneExtensionValidatesAndExecutesAliases(GameTestHelper helper) {
        BuiltinSceneStepExecutors.register();
        PARSED.set(0);
        VALIDATED.set(0);
        APPLIED.set(0);
        var rejected = SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id("scene_registration_fixture"), scene(false)).resource());
        helper.assertTrue(!rejected.valid() && rejected.diagnostics().stream().anyMatch(value -> value.message().equals("extension data rejected")),
                "registered validator rejects invalid extension content");
        var compiled = SceneCompiler.compile(SceneParser.parse(VillagerRetaliation.id("scene_registration_fixture"), scene(true)).resource());
        helper.assertTrue(compiled.valid(), "registered extension content compiles");
        helper.assertValueEqual(compiled.scene().steps().get("extension").type(), STEP, "compiled extension type uses canonical identity");
        helper.assertTrue(SceneStepExecutors.get(ALIAS).orElseThrow() == SceneStepExecutors.get(STEP).orElseThrow(),
                "runtime executor attachment honors aliases");
        var typeSchema = com.jvn.villagerretaliation.scene.schema.SceneSchema.sceneV1().getAsJsonObject("properties")
                .getAsJsonObject("steps").getAsJsonObject("items").getAsJsonObject("properties").getAsJsonObject("type");
        helper.assertTrue(typeSchema.getAsJsonArray("enum").asList().stream().anyMatch(value -> value.getAsString().equals(ALIAS.toString())),
                "authoring schema accepts the same aliases as compilation and execution");
        helper.assertValueEqual(PARSED.get(), 2, "registered parser handles candidate validation");
        helper.assertValueEqual(VALIDATED.get(), 2, "registered validator receives parsed values");
        UUID player = UUID.randomUUID();
        SceneSavedData data = new SceneSavedData();
        var instance = data.start(compiled.scene(), "registration/extension",
                new SceneOwner(SceneResource.OwnershipMode.PLAYER, player, null, null, ""),
                null, Set.of(player), Map.of(), 0L).instance();
        for (int tick = 0; tick < 8 && !instance.state().terminal(); tick++) {
            SceneStepEngine.process(helper.getLevel().getServer(), data, instance, compiled.scene(), tick);
        }
        helper.assertTrue(instance.state().terminal(), "registered extension reaches the existing scene terminal");
        helper.assertValueEqual(APPLIED.get(), 1, "registered executor applies once through the existing lifecycle");
        helper.succeed();
    }

    private static void assertMirror(GameTestHelper helper, FreezableExtensionRegistry<RuntimeTypeDescriptor> registry,
            Set<String> runtimeIds) {
        helper.assertValueEqual(registry.descriptors().stream().map(value -> value.id().getPath()).collect(Collectors.toSet()),
                runtimeIds, registry.name() + " descriptors match actual runtime registrations");
    }

    private static JsonObject scene(boolean accepted) {
        return json("""
                {"schema":"villagerretaliation:scene/v1","id":"villagerretaliation:registration_fixture",
                 "entry_step":"extension","actors":[],"steps":[
                  {"id":"extension","type":"villagerretaliation:registration_contract_legacy",
                   "data":{"accepted":%s},"next":"finish"},
                  {"id":"finish","type":"villagerretaliation:scene_complete"}]}
                """.formatted(accepted));
    }

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
