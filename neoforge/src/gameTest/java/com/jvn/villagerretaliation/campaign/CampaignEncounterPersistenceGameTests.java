package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterPersistenceGameTests {
    private CampaignEncounterPersistenceGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_persistence")
    public static void failedCreationSavePreventsMobsAlliesAndEnvironmentChanges(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.block(); fixture.step(); fixture.step();
            helper.assertTrue(fixture.scene().campaignPaused(), "failed creation save pauses the authored timeout on the same tick");
            fixture.run();
            helper.assertTrue(fixture.entities().isEmpty(), "unsaved encounter identity must not create mobs or allies");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).isAir(), "unsaved encounter must not alter its environment");
            var pending = fixture.encounter();
            helper.assertTrue(pending != null, "failed save retains the prepared encounter");
            helper.assertTrue(EncounterService.refresh(fixture.base.base.server, fixture.data(), pending).status() == EncounterService.Status.WAITING,
                    "maintenance cannot bypass failed creation storage");
            helper.assertTrue(fixture.entities().isEmpty(), "maintenance creates no entities before storage recovers");
            UUID identity = pending.id();
            fixture.restore(); fixture.run();
            helper.assertValueEqual(fixture.encounter().id(), identity, "storage repair retains the prepared encounter identity");
            helper.assertValueEqual(fixture.entities().size(), 2, "repair creates the mob and ally once");
            helper.assertTrue(fixture.disk().encounter(identity).isPresent(), "creation identity reaches the actual save");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.encounter().id(), identity, "disk reload retains encounter ownership");
            helper.assertValueEqual(fixture.entities().size(), 2, "reload reuses owned mobs and allies");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_persistence")
    public static void creationIdentityIsOnDiskBeforeEntitiesJoinTheWorld(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var evidence = new ArrayList<Boolean>();
            Consumer<EntityJoinLevelEvent> listener = event -> {
                var owner = event.getEntity().getPersistentData();
                if (owner.hasUUID("VillagerRetaliationEncounter"))
                    evidence.add(fixture.disk().encounter(owner.getUUID("VillagerRetaliationEncounter")).isPresent());
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(evidence, List.of(true), "each mob joins only after its encounter identity is durable");
            var scene = fixture.disk().get(fixture.sceneId).orElseThrow();
            helper.assertValueEqual(scene.receipt(fixture.sceneId + "/start/encounter").state(),
                    SceneOperationReceipt.ReceiptState.COMPLETED, "creation completion is saved before the scene advances");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_persistence")
    public static void failedCreationCompletionWriteKeepsTheSceneAtItsSavedStart(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            Consumer<EntityJoinLevelEvent> listener = event -> {
                if (!event.getEntity().getPersistentData().hasUUID("VillagerRetaliationEncounter")) return;
                try { fixture.block(); }
                catch (IOException exception) { throw new AssertionError("cannot interrupt the creation completion write", exception); }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            var encounter = fixture.encounter();
            helper.assertTrue(encounter != null && fixture.entities().size() == 2, "fixture interrupts storage after spawning");
            helper.assertValueEqual(fixture.scene().currentStep(), "start", "unsaved creation completion cannot advance the scene");
            helper.assertTrue(fixture.data().campaignPersistencePending(), "failed completion remains pending");
            var held = EncounterService.refresh(fixture.base.base.server, fixture.data(), encounter);
            helper.assertValueEqual(held.status(), EncounterService.Status.WAITING, "encounter maintenance waits for the same pending save");
            UUID identity = encounter.id();
            fixture.restore(); fixture.run(); fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.encounter().id(), identity, "completion recovery preserves the encounter identity");
            helper.assertValueEqual(fixture.entities().size(), 2, "completion recovery never spawns the initial group again");
            helper.assertValueEqual(fixture.scene().currentStep(), "wait", "saved completion releases the next authored step");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_persistence")
    public static void ordinaryEncountersKeepTheirExistingSaveBehavior(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var ordinary = new SceneSavedData();
            var scene = ordinary.start(fixture.definition, "ordinary_encounter", fixture.scene().owner(), null,
                    fixture.scene().participants(), Map.of(), helper.getLevel().getGameTime()).instance();
            var encounter = ordinary.startEncounter(fixture.template, scene, "ordinary/start", helper.getLevel().dimension().location(),
                    fixture.anchor, "normal").encounter();
            fixture.block();
            try {
                var result = EncounterService.reconcileSpawn(fixture.base.base.server, ordinary, encounter, fixture.template);
                helper.assertValueEqual(result.status(), EncounterService.Status.ACTIVE, "ordinary encounter still uses its existing asynchronous save path");
                helper.assertValueEqual(encounter.spawned().size(), 1, "ordinary mob spawns");
                helper.assertFalse(ordinary.campaignPersistencePending(), "ordinary encounter does not request campaign storage");
            } finally {
                encounter.spawned().forEach(id -> { var entity = helper.getLevel().getEntity(id); if (entity != null) entity.discard(); });
                encounter.allies().values().forEach(ally -> { var entity = helper.getLevel().getEntity(ally.entityId()); if (entity != null) entity.discard(); });
            }
        }
        helper.succeed();
    }

    static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignConversationGameTests.Fixture base;
        final CompiledScene definition;
        final EncounterTemplate template;
        final UUID sceneId;
        final BlockPos anchor;
        private final net.minecraft.world.level.chunk.LevelChunk ownershipChunk;
        private final CampaignBlockOwnershipLedger priorOwnership;
        private Path backup;
        private long time;

        Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            base = new CampaignConversationGameTests.Fixture(helper, false, true, false);
            base.effects("""
                    [{"id":"scene","action":{"type":"start_scene","quest":"campaign_fixture:characters",
                      "scene_id":"campaign_fixture:character_greeting","operation_id":"encounter_scene","wait_for_result":true,"required":true}}]
                    """);
            anchor = helper.absolutePos(new BlockPos(5, 2, 5));
            ownershipChunk = helper.getLevel().getChunkSource().getChunkNow(barrier().getX() >> 4, barrier().getZ() >> 4);
            priorOwnership = ownershipChunk.getExistingDataOrNull(CampaignBlockOwnership.OWNERSHIP.get());
            ownershipChunk.setData(CampaignBlockOwnership.OWNERSHIP, new CampaignBlockOwnershipLedger());
            definition = CampaignConversationSceneHandoverGameTests.scene(base, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"start","actors":[],"steps":[
                      {"id":"start","type":"villagerretaliation:start_encounter","data":{"template":"campaign_fixture:persistent_encounter",
                        "x":%d,"y":%d,"z":%d,"dimension":"minecraft:overworld"},"next":"wait"},
                      {"id":"wait","type":"villagerretaliation:wait_encounter","data":{"encounter_step":"start"},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """.formatted(anchor.getX(), anchor.getY(), anchor.getZ()));
            var errors = new ArrayList<String>();
            template = EncounterResources.parse(ResourceLocation.parse("campaign_fixture:persistent_encounter"), JsonParser.parseString("""
                    {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
                     "spawn_mode":"fixed","spawn_radius":0,"members":[{"entity":"minecraft:cow"}],
                     "allies":[{"id":"guard","entity":"minecraft:iron_golem","cleanup_policy":"remove"}],
                     "environment":{"temporary_blocks":[{"id":"barrier","block":"minecraft:glass","offset_x":2,"offset_y":2}]}}
                    """).getAsJsonObject(), errors);
            helper.assertTrue(template != null && errors.isEmpty(), "encounter fixture parses: " + errors);
            var old = QuestContentCatalogs.current(base.base.server);
            var campaigns = CampaignResources.current(base.base.server).campaigns();
            var catalog = new QuestContentCatalog(old.generation() + 1, old.compiledQuestCatalog(), old.dialogueCatalog(), old.quests(),
                    old.objectiveEventQuestIds(), old.factQuestIds(), old.memoryEventQuestIds(), old.exclusiveGroupQuestIds(), old.triggerEventQuestIds(),
                    old.scenes(), Map.of(template.id(), template), old.pools(), old.bundles(), old.localization(), old.rewards(), old.conversations());
            QuestContentCatalogs.installForTests(base.base.server, catalog, base.base.report);
            CampaignResources.installForTests(base.base.server, new CampaignContentCatalog(catalog, campaigns, List.of()));
            var player = base.player();
            CampaignConversationConfirmationGameTests.acceptQuest(base, player);
            var result = base.start(player);
            var receipt = result.session().receipts().values().iterator().next();
            helper.assertTrue(receipt.result().suspended(), "conversation waits for its encounter scene");
            sceneId = receipt.result().suspensionId();
            time = helper.getLevel().getGameTime();
        }

        SceneSavedData data() { return SceneSavedData.get(helper.getLevel()); }
        SceneInstance scene() { return data().get(sceneId).orElseThrow(); }
        EncounterInstance encounter() { return data().encounters().stream().filter(value -> value.sceneId().equals(sceneId)).findFirst().orElse(null); }
        BlockPos barrier() { return anchor.offset(2, 2, 0); }
        List<Entity> entities() {
            var found = new ArrayList<Entity>();
            for (var entity : helper.getLevel().getAllEntities()) {
                var tag = entity.getPersistentData();
                if (tag.hasUUID("VillagerRetaliationScene") && tag.getUUID("VillagerRetaliationScene").equals(sceneId)
                        || encounter() != null && tag.hasUUID("VillagerRetaliationEncounterAlly")
                                && tag.getUUID("VillagerRetaliationEncounterAlly").equals(encounter().id())) found.add(entity);
            }
            return found;
        }
        Path path() { return base.base.server.getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_scenes.dat"); }
        SceneSavedData disk() {
            try { return SceneSavedData.load(NbtIo.readCompressed(path(), NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess()); }
            catch (IOException exception) { throw new AssertionError("cannot read the actual scene save", exception); }
        }
        void reload() { base.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", disk()); SceneStepEngine.clearRuntimeState(); }
        void step() { SceneStepEngine.process(base.base.server, data(), scene(), definition, time++); }
        void run() {
            for (int count = 0; count < 12 && !scene().state().terminal(); count++) step();
        }
        void block() throws IOException {
            backup = Files.createTempFile(base.base.disk.root, "encounter-backup-", ".dat");
            Files.delete(backup);
            if (Files.exists(path())) Files.move(path(), backup);
            Files.createDirectories(path());
            Files.writeString(path().resolve("blocked"), "encounter storage failure fixture");
        }
        void restore() throws IOException {
            if (backup == null) return;
            Files.delete(path().resolve("blocked")); Files.delete(path());
            if (Files.exists(backup)) Files.move(backup, path());
            backup = null;
        }
        @Override public void close() throws IOException {
            restore();
            entities().forEach(Entity::discard);
            helper.getLevel().removeBlock(barrier(), false);
            if (priorOwnership == null) ownershipChunk.removeData(CampaignBlockOwnership.OWNERSHIP);
            else ownershipChunk.setData(CampaignBlockOwnership.OWNERSHIP, priorOwnership);
            EncounterService.clearRuntimeState(); SceneStepEngine.clearRuntimeState();
            base.close();
        }
    }
}
