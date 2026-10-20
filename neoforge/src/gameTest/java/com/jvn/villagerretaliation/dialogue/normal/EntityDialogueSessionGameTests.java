package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueResponseTimer;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import com.jvn.villagerretaliation.dialogue.NarrativeActors;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_sessions")
@PrefixGameTestTemplate(false)
public final class EntityDialogueSessionGameTests {
    private EntityDialogueSessionGameTests() { }

    @GameTest(template = "empty")
    public static void mobDialogueDeadlineSurvivesReloadWithoutRepeatingEffects(GameTestHelper helper) {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var prior = DialogueTreeSessionSavedData.get(level);
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var context = new EntityDialogueContext(level, player, cow, "en_us", "");
        var id = ResourceLocation.parse("test:entity_timer");
        var response = new DialogueTreeDefinition.Response("accept", "Accept", DialogueEntryMetadata.EMPTY, "done",
                DialogueRequestType.STORY, List.of(), List.of(), List.of(), false, 0);
        var opening = new DialogueTreeDefinition.Node("welcome", List.of("Choose."), List.of(), List.of(),
                List.of(), List.of(response), false, List.of(), null, new DialogueResponseTimer(40, "accept", ""));
        var done = new DialogueTreeDefinition.Node("done", List.of("Done."), actions(), List.of(), List.of(), true);
        var tree = new DialogueTreeDefinition(id, "Entity tree", "", DialogueEntryMetadata.EMPTY,
                List.of(), List.of(), Map.of("welcome", opening, "done", done));
        try {
            DialogueTreeResources.installTestTrees(level.getServer(), context.locale(), List.of(tree), Set.of());
            var data = new DialogueTreeSessionSavedData();
            var key = new DialogueTreeService.SessionKey(player.getUUID(), cow.getUUID());
            data.put(key, new DialogueTreeService.Session(UUID.randomUUID(), id, "welcome", level.getGameTime(),
                    Map.of(), level.getGameTime(), false));
            storage.set("villagerretaliation_dialogue_trees", DialogueTreeSessionSavedData.load(data.save(new CompoundTag(), level.registryAccess())));
            int experience = player.totalExperience;
            DialogueTreeService.tick(context);
            helper.assertValueEqual(player.totalExperience, experience + 7, "mob dialogue executes the deadline reward");
            data = DialogueTreeSessionSavedData.get(level);
            helper.assertTrue(data.get(key) == null, "terminal mob dialogue retires its cursor");
            storage.set("villagerretaliation_dialogue_trees", DialogueTreeSessionSavedData.load(data.save(new CompoundTag(), level.registryAccess())));
            DialogueTreeService.tick(context);
            helper.assertValueEqual(player.totalExperience, experience + 7, "reload does not repeat mob dialogue rewards");
        } finally {
            storage.set("villagerretaliation_dialogue_trees", prior);
            DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entitySceneRecoveryRequiresRegistrationAndRecordsCompletion(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var data = new SceneSavedData();
        var compiled = SceneCompiler.compile(SceneParser.parse(ResourceLocation.parse("test:quest_scenes/entity.json"),
                JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"test:entity","definition_version":1,
                 "ownership":"player","entry_step":"finish","steps":[{"id":"finish","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject()).resource());
        helper.assertTrue(compiled.valid(), "entity scene fixture compiles");
        var scene = data.start(compiled.scene(), "entity_scene/" + cow.getUUID(),
                new SceneOwner(SceneResource.OwnershipMode.PLAYER, player.getUUID(), null, null, ""),
                null, Set.of(player.getUUID()), Map.of(), 0L).instance();
        var continuation = data.suspendContinuation(scene, player.getUUID(), cow.getUUID(), "entity_test", actions(), 0, Map.of());
        continuation.entityRuntime(true);
        SceneTransitionService.complete(data, scene, 1L);
        var loaded = SceneSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        int experience = player.totalExperience;
        SceneContinuationService.maintain(helper.getLevel().getServer(), loaded);
        helper.assertValueEqual(player.totalExperience, experience, "unregistered mobs remain inactive");
        try (var registration = NarrativeActors.register(ResourceLocation.parse("minecraft:cow"),
                (level, viewer, speaker) -> new EntityDialogueContext(level, viewer, speaker, "en_us", ""))) {
            SceneContinuationService.maintain(helper.getLevel().getServer(), loaded);
            SceneContinuationService.maintain(helper.getLevel().getServer(), loaded);
            helper.assertValueEqual(player.totalExperience, experience + 7, "registered mob continuation executes once");
            helper.assertTrue(loaded.continuations().getFirst().completionReceipt(), "mob continuation records completion");
            loaded = SceneSavedData.load(loaded.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            SceneContinuationService.maintain(helper.getLevel().getServer(), loaded);
            helper.assertValueEqual(player.totalExperience, experience + 7, "completion receipt survives reload");
        }
        var uncertain = new SceneContinuation(UUID.randomUUID(), scene.id(), player.getUUID(), cow.getUUID(),
                null, null, "uncertain", actions(), 0, Map.of());
        uncertain.entityRuntime(true);
        uncertain.prepareRecoveryAction(0);
        var restored = SceneContinuation.load(uncertain.save());
        helper.assertValueEqual(restored.preparedRecoveryAction(), 0, "uncertain action marker survives reload");
        for (boolean missing : List.of(false, true)) {
            var invalid = uncertain.save();
            if (missing) invalid.remove("EntityRuntime");
            else invalid.putString("EntityRuntime", "true");
            boolean rejected = false;
            try { SceneContinuation.load(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "a malformed entity marker cannot downgrade recovery to the legacy executor");
        }
        helper.succeed();
    }

    private static List<VillagerActionDefinition> actions() {
        return VillagerActionDefinition.readListOrInline(ResourceLocation.parse("test:entity_session"), "actions",
                JsonParser.parseString("{\"type\":\"experience\",\"amount\":7}").getAsJsonObject());
    }
}
