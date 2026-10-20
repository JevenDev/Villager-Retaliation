package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.QuestProviderType;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressState;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressionService;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_quests")
@PrefixGameTestTemplate(false)
public final class EntityQuestRuntimeGameTests {
    private EntityQuestRuntimeGameTests() { }

    @GameTest(template = "empty")
    public static void mobProviderIdentityAndTypeSurviveQuestPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var wolf = helper.spawn(EntityType.WOLF, 1, 1, 1);
        wolf.setNoAi(true);
        var definition = definition();
        var provider = provider();
        var binding = QuestProviderBinding.fromEntity(provider.id(), wolf, "test:pack");
        var context = QuestExecutionContext.fromLiveProvider(level, player, definition, "accept", provider, binding, wolf);
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(player.getUUID(), definition.id());
        helper.assertTrue(QuestAvailabilityService.canStart(context, progress, false, id -> true, () -> 0),
                "generic availability delegates to a non-villager provider");
        var event = QuestLifecycleService.start(definition.id(), progress, binding.snapshot(), null,
                level.getGameTime(), player.getUUID(), null, false);
        helper.assertValueEqual(event.type(), QuestLifecycleService.LifecycleEventType.STARTED, "the shared lifecycle accepts a mob provider");
        QuestLifecycleService.initializeStage(definition, progress, level.getGameTime());
        helper.assertTrue(provider.matchesProviderLock(context, definition, progress), "a custom provider uses shared issuer locking without a villager hook");
        progress.providerType(ResourceLocation.parse("test:other_provider"));
        helper.assertFalse(provider.matchesProviderLock(context, definition, progress), "provider locking rejects the wrong provider type");
        progress.providerType(provider.id());
        UUID run = progress.questRunId();
        var loaded = VillagerQuestSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        var restored = loaded.get(player.getUUID(), definition.id());
        var snapshot = restored.providerSnapshot(null).orElseThrow();
        helper.assertValueEqual(snapshot.type(), provider.id(), "the provider type remains explicit after loading");
        helper.assertValueEqual(snapshot.id(), wolf.getUUID(), "the original mob identity survives loading");
        helper.assertValueEqual(restored.questRunId(), run, "the original run identity survives loading");
        helper.assertTrue(VillagerQuestProviderType.INSTANCE.bindingFromProgress(level, restored).isEmpty(),
                "a saved mob provider cannot be silently reconstructed as a villager");
        var saved = QuestExecutionContext.fromSavedProvider(level, player, definition, "resume", provider,
                QuestProviderBinding.fromSnapshot(snapshot));
        helper.assertValueEqual(saved.conditions((QuestProgressState) restored).providerId(), wolf.getUUID(),
                "shared condition assembly uses the saved mob provider");
        helper.assertFalse(QuestAvailabilityService.canStart(saved, restored, false, id -> true, () -> 0),
                "generic availability cannot restart an active run");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void objectivesAndProgressionAcceptSharedEntityContextAndBaseState(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        cow.setNoAi(true);
        var context = new EntityDialogueContext(level, player, cow, "en_us", "");
        var definition = definition();
        var progress = new QuestProgressState();
        progress.start(cow.getUUID(), level.dimension(), null, level.getGameTime());
        progress.setCurrentStage("active");
        var evaluation = new QuestObjectiveEvaluationContext(player, context, level, definition, progress,
                objective -> 0, (objective, stack) -> false, objective -> 0, objective -> true,
                objective -> ConditionEvaluator.all(context.conditions(), null, objective.conditions())
                        == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED,
                objective -> com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDebugState.EMPTY);
        var completed = new java.util.ArrayList<String>();
        helper.assertTrue(QuestObjectiveDispatchService.poll(evaluation, objective -> completed.add(objective.id())),
                "generic objective polling evaluates a cow speaker with base progress state");
        helper.assertValueEqual(completed, List.of("healthy"), "the condition objective publishes completion once");
        helper.assertFalse(QuestObjectiveDispatchService.poll(evaluation, objective -> completed.add(objective.id())),
                "completed generic objectives do not replay");
        helper.assertTrue(QuestProgressionService.advanceSaved(definition, progress, level.getGameTime(),
                objective -> progress.objectiveComplete(objective.id()), conditions -> true), "generic progress advances through the authored stage");
        helper.assertValueEqual(progress.currentStage(), "ready", "the authored destination is preserved");
        helper.assertValueEqual(progress.providerId(), cow.getUUID(), "progression preserves a non-villager provider");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyProviderSavesUseExplicitFallbackAndMalformedTypesStayBlocked(GameTestHelper helper) {
        var data = new VillagerQuestSavedData();
        UUID player = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        var id = ResourceLocation.parse("test:legacy_provider");
        var progress = data.getOrCreate(player, id);
        progress.start(entity, helper.getLevel().dimension(), null, 10);
        progress.setIssuer(entity, "Keeper", "", 1, helper.getLevel().dimension(), net.minecraft.core.BlockPos.ZERO, "");
        helper.assertValueEqual(progress.providerSnapshot(VillagerQuestProviderType.ID).orElseThrow().type(), VillagerQuestProviderType.ID,
                "legacy saves use the adapter's chosen provider fallback");
        var tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        tag.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putString("IssuerProviderType", "invalid provider type!");
        var restored = VillagerQuestSavedData.load(tag, helper.getLevel().registryAccess()).get(player, id);
        helper.assertTrue(restored.providerSnapshot(VillagerQuestProviderType.ID).isEmpty(), "invalid explicit types cannot become the fallback provider");
        helper.assertTrue(VillagerQuestProviderType.INSTANCE.bindingFromProgress(helper.getLevel(), restored).isEmpty(),
                "the villager adapter also rejects an invalid explicit type");
        helper.succeed();
    }

    private static QuestDefinition definition() {
        var id = ResourceLocation.parse("test:entity_runtime");
        return QuestDefinitionFixtures.read(id, JsonParser.parseString("""
                {"objectives":[{"id":"healthy","type":"condition","conditions":[{"type":"health","subject":"speaker","min":0.5}]}],
                 "entry_stage":"active","stages":{"active":{"objectives":["healthy"],"complete_when":["healthy"],"next":"ready"},"ready":{"objectives":[]}}}
                """).getAsJsonObject(), id);
    }

    private static QuestProviderType provider() {
        return new QuestProviderType() {
            public ResourceLocation id() { return ResourceLocation.parse("test:mob_provider"); }
            public boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition) {
                return context.binding() != null && id().equals(context.binding().providerType());
            }
        };
    }
}
