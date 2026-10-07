package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Set;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.mixin.EntityStorageAccessor;
import com.jvn.villagerretaliation.mixin.PersistentEntitySectionManagerAccessor;
import com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignSpawnGameTests {
    private CampaignSpawnGameTests() { }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void dedicatedSpawnsRequireSmallSearchBoundsAndExplicitOwnership(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var raw = fixture.raw();
            var role = raw.getAsJsonArray("characters").get(0).getAsJsonObject();
            role.remove("search_radius");
            var parsed = CampaignDefinitionParser.parse(fixture.campaignId, raw);
            helper.assertTrue(parsed.valid(), "owned source compiles");
            helper.assertValueEqual(parsed.definition().characters().get("mentor").searchRadius(), 4, "owned search defaults to four blocks");
            role.addProperty("search_radius", 9);
            parsed = CampaignDefinitionParser.parse(fixture.campaignId, raw);
            helper.assertFalse(parsed.valid(), "owned search cannot exceed eight blocks");
            helper.assertFalse(CampaignSchema.validate(fixture.campaignId, raw).isEmpty(), "exported schema enforces the same bound");
            helper.assertTrue(parsed.diagnostics().stream().anyMatch(issue -> issue.jsonPointer().equals("/characters/0/search_radius")), "diagnostic points to the authored radius");
            role.addProperty("search_radius", 8);
            helper.assertTrue(CampaignDefinitionParser.parse(fixture.campaignId, raw).valid(), "maximum bounded search is accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void spawnAcknowledgementFollowsNativeEntityAndReceiptPersistence(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var neighbor = helper.spawn(EntityType.PIG, 1, 1, 1);
            neighbor.setNoAi(true);
            var passenger = helper.spawn(EntityType.CHICKEN, 1, 1, 1);
            helper.assertTrue(passenger.startRiding(neighbor, true), "neighbor has a passenger to preserve");
            var playerBlock = fixture.origin.below();
            helper.getLevel().setBlockAndUpdate(playerBlock, Blocks.GOLD_BLOCK.defaultBlockState());
            var result = fixture.resolve();
            helper.assertTrue(result.available(), "dedicated role becomes available: " + result.diagnostic());
            var spawned = result.entity();
            var receipt = data(helper).forEntity(spawned.getUUID());
            helper.assertTrue(receipt.committed() && CampaignOwnedSpawns.owns(receipt, spawned), "acknowledged entity has committed ownership");
            helper.assertValueEqual(spawned.getVillagerData().getProfession(), VillagerProfession.LIBRARIAN, "authored profession applied on birth");
            helper.assertValueEqual(spawned.getCustomName().getString(), "Campaign mentor", "campaign owns the character name");
            helper.assertValueEqual(neighbor.chunkPosition(), spawned.chunkPosition(), "unrelated passenger tree shares the checkpointed chunk");
            var snapshot = entityChunk(helper, spawned);
            helper.assertTrue(contains(snapshot, spawned.getUUID()), "native entity chunk contains the mentor before acknowledgement");
            var savedNeighbor = snapshot.getList("Entities", Tag.TAG_COMPOUND).stream().map(tag -> (CompoundTag) tag)
                    .filter(tag -> tag.getUUID("UUID").equals(neighbor.getUUID())).findFirst().orElseThrow();
            helper.assertValueEqual(savedNeighbor.getList("Passengers", Tag.TAG_COMPOUND).getCompound(0).getUUID("UUID"), passenger.getUUID(), "native checkpoint preserves unrelated passengers");
            helper.assertValueEqual(helper.getLevel().getBlockState(playerBlock), Blocks.GOLD_BLOCK.defaultBlockState(), "checkpoint preserves player construction");
            fixture.reload();
            helper.assertTrue(data(helper).forEntity(spawned.getUUID()).committed(), "receipt was written to disk before acknowledgement");
            helper.assertValueEqual(fixture.resolve().entity(), spawned, "repeated resolution keeps one entity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void reloadingReceiptsNeverResetsAnExistingCharactersState(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var first = fixture.resolve();
            helper.assertTrue(first.available(), first.diagnostic());
            var entity = first.entity();
            entity.setNoAi(true);
            entity.setVillagerData(entity.getVillagerData().setProfession(VillagerProfession.FARMER));
            entity.getInventory().setItem(0, new ItemStack(Items.EMERALD, 11));
            entity.getPersistentData().putInt("PersonalRelationship", 37);
            entity.setPos(entity.getX() + 12, entity.getY(), entity.getZ());
            var before = entity.saveWithoutId(new CompoundTag());
            fixture.reload();
            for (int attempt = 0; attempt < 4; attempt++) helper.assertValueEqual(fixture.resolve().entity(), entity, "retry follows the original UUID");
            helper.assertValueEqual(entity.saveWithoutId(new CompoundTag()), before, "receipt never replaces an established character with its birth snapshot");
            helper.assertValueEqual(data(helper).receipts().size(), 1, "one role has one spawn receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void unloadedOrPermanentlyLostCommittedCharactersCannotSpawnAgain(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var first = fixture.resolve();
            helper.assertTrue(first.available(), first.diagnostic());
            var entity = first.entity();
            var snapshot = entity.saveWithoutId(new CompoundTag());
            entity.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.reload();
            var unavailable = fixture.resolve();
            helper.assertFalse(unavailable.available(), "unloaded committed role is unavailable");
            helper.assertFalse(unavailable.binding().lost(), "unloaded does not mean dead");
            helper.assertTrue(helper.getLevel().getEntity(entity.getUUID()) == null, "an empty loaded origin cannot duplicate a committed character");
            var restored = EntityType.VILLAGER.create(helper.getLevel());
            restored.load(snapshot);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "original identity can return");
            helper.assertValueEqual(fixture.resolve().entity(), restored, "returning character resumes the role");
            restored.discard();
            fixture.reload();
            helper.assertTrue(fixture.resolve().binding().lost(), "known permanent removal remains repairable without respawning");
            helper.assertTrue(helper.getLevel().getEntity(entity.getUUID()) == null, "permanent removal never repeats the spawn");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void preparedSpawnResumesWithReservedIdentityAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var receipt = prepare(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            fixture.reload();
            helper.assertTrue(helper.getLevel().getEntity(receipt.binding().entityId()) == null, "prepared receipt has no entity yet");
            var result = fixture.resolve();
            helper.assertTrue(result.available(), result.diagnostic());
            helper.assertValueEqual(result.entity().getUUID(), receipt.binding().entityId(), "recovery uses the reserved identity");
            helper.assertTrue(data(helper).forEntity(result.entity().getUUID()).committed(), "recovered spawn commits once");
            helper.assertTrue(contains(entityChunk(helper, result.entity()), result.entity().getUUID()), "recovered entity is saved before commit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void nativeReloadCompletesAPreparedReceiptWithoutDuplicatingTheCharacter(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var receipt = prepare(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            var entity = (Villager) EntityType.loadEntityRecursive(receipt.entity(), helper.getLevel(), value -> value);
            helper.assertTrue(helper.getLevel().addFreshEntity(entity), "entity enters between preparation and commit");
            CampaignEntityCheckpoint.persist(helper.getLevel(), entity);
            var nativeSnapshot = entityChunk(helper, entity).getList("Entities", Tag.TAG_COMPOUND).stream().map(tag -> (CompoundTag) tag)
                    .filter(tag -> tag.getUUID("UUID").equals(entity.getUUID())).findFirst().orElseThrow();
            entity.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.reload();
            var restored = (Villager) EntityType.loadEntityRecursive(nativeSnapshot, helper.getLevel(), value -> value);
            var event = new EntityJoinLevelEvent(restored, helper.getLevel(), true);
            CampaignOwnedSpawns.onEntityJoin(event);
            helper.assertFalse(event.isCanceled(), "saved owned entity can recover its prepared receipt");
            helper.assertTrue(data(helper).forEntity(entity.getUUID()).committed(), "disk admission completes the receipt before presentation");
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "native character returns");
            helper.assertValueEqual(fixture.resolve().entity(), restored, "recovery does not create another character");
            fixture.reload();
            helper.assertTrue(data(helper).forEntity(entity.getUUID()).committed(), "admission commit survives another restart");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void failedPreparationCannotPublishABindingOrEntity(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var blocked = fixture.disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            try {
                helper.getLevel().getServer().overworld().getDataStorage().set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(new CompoundTag(), blocked));
                var result = fixture.resolve();
                helper.assertFalse(result.available(), "spawn save failure is recoverable");
                helper.assertTrue(result.diagnostic().contains("could not be saved"), "write failure is explained");
                helper.assertTrue(data(helper).receipts().isEmpty(), "failed preparation does not publish a receipt");
                helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().isEmpty(), "failed preparation does not claim a role");
            } finally { Files.delete(blocked); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void failedCommitRollsBackOnlyTheFreshEntityAndRetriesItsIdentity(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var receipt = prepare(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            CampaignCharacterSavedData.get(helper.getLevel()).bind(receipt.binding());
            var blocked = fixture.disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            var original = fixture.disk.read(CampaignSpawnSavedData.DATA_NAME);
            try {
                fixture.server.overworld().getDataStorage().set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(original, blocked));
                var result = fixture.resolve();
                helper.assertFalse(result.available(), "commit failure cannot acknowledge the character");
                helper.assertTrue(result.diagnostic().contains("could not be saved"), "commit failure is explained");
                helper.assertTrue(helper.getLevel().getEntity(receipt.binding().entityId()) == null, "failed fresh spawn is removed from gameplay");
                helper.assertFalse(CampaignCharacterSavedData.get(helper.getLevel()).forEntity(receipt.binding().entityId()).lost(), "rollback does not falsely record character death");
                helper.assertFalse(data(helper).forEntity(receipt.binding().entityId()).committed(), "prepared receipt survives for repair");
            } finally { Files.delete(blocked); }
            fixture.reload();
            var recovered = fixture.resolve();
            helper.assertTrue(recovered.available(), recovered.diagnostic());
            helper.assertValueEqual(recovered.entity().getUUID(), receipt.binding().entityId(), "retry preserves the reservation");
            helper.assertTrue(data(helper).forEntity(recovered.entity().getUUID()).committed(), "repaired storage allows commitment");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void playerEditsBlockPreparedSpawnsWithoutBeingOverwritten(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var receipt = prepare(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            helper.getLevel().setBlockAndUpdate(fixture.origin, Blocks.DIAMOND_BLOCK.defaultBlockState());
            var result = fixture.resolve();
            helper.assertFalse(result.available(), "changed reserved space blocks the spawn");
            helper.assertTrue(result.diagnostic().contains("obstruct"), "author sees a repairable obstruction");
            helper.assertValueEqual(helper.getLevel().getBlockState(fixture.origin), Blocks.DIAMOND_BLOCK.defaultBlockState(), "player construction is preserved");
            helper.assertFalse(data(helper).forEntity(receipt.binding().entityId()).committed(), "obstructed reservation remains prepared");
            helper.getLevel().setBlockAndUpdate(fixture.origin, Blocks.AIR.defaultBlockState());
            helper.assertValueEqual(fixture.resolve().entity().getUUID(), receipt.binding().entityId(), "clearing the position resumes its reservation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void missingLocationsAndWorldModesDoNotSpawnOrLoadDistantChunks(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var player = UUID.randomUUID();
            for (String mode : List.of("sandbox", "legacy")) {
                fixture.mode(mode);
                CampaignCharacterActivation.activate(fixture.server, List.of(player));
                helper.assertFalse(fixture.resolve().available(), "other world modes reject owned spawning");
                helper.assertTrue(data(helper).receipts().isEmpty(), "other modes never reserve an entity");
            }
            fixture.mode("campaign");
            var raw = fixture.raw();
            raw.getAsJsonArray("locations").get(0).getAsJsonObject().getAsJsonObject("origin").addProperty("x", 20_000_000);
            fixture.install(raw);
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            CampaignCharacterActivation.activate(fixture.server, List.of(player));
            helper.assertFalse(fixture.resolve().available(), "unloaded location remains recoverable");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "spawning cannot force distant chunks");
            helper.assertTrue(data(helper).receipts().isEmpty(), "missing location has no false spawn receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void availableChapterActivatesItsFirstProviderWithoutAnotherConversation(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            CampaignCharacterActivation.activate(fixture.server, List.of());
            helper.assertTrue(data(helper).receipts().isEmpty(), "empty server does not activate chapters");
            var player = UUID.randomUUID();
            CampaignCharacterActivation.activate(fixture.server, List.of(player));
            helper.assertValueEqual(data(helper).receipts().size(), 1, "available chapter creates its first provider");
            var receipt = data(helper).receipts().values().iterator().next();
            helper.assertTrue(receipt.committed(), "activation completes the durable spawn");
            CampaignCharacterActivation.activate(fixture.server, List.of(player, UUID.randomUUID()));
            helper.assertValueEqual(data(helper).receipts().size(), 1, "another player cannot spawn another provider");
            var entity = fixture.resolve().entity();
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(fixture.catalog, Map.of(), List.of()));
            helper.assertFalse(fixture.resolve().available(), "missing content keeps the role dormant");
            helper.assertValueEqual(helper.getLevel().getEntity(receipt.binding().entityId()), entity, "missing content preserves the existing character");
            var changed = fixture.raw(); changed.addProperty("revision", 2); fixture.install(changed);
            helper.assertFalse(fixture.resolve().available(), "incompatible revisions cannot rewrite the spawn");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void competingPreparationsReturnOneDurableReservation(GameTestHelper helper) throws Exception {
        try (var fixture = fixture(helper); var executor = Executors.newFixedThreadPool(2)) {
            var first = candidate(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            var second = candidate(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            var data = data(helper);
            var results = executor.invokeAll(List.<Callable<CampaignSpawnReceipt>>of(() -> data.prepare(first), () -> data.prepare(second)));
            var winner = results.getFirst().get();
            helper.assertValueEqual(results.getLast().get(), winner, "competing callers receive the same reservation");
            fixture.reload();
            helper.assertValueEqual(data(helper).forEntity(winner.binding().entityId()), winner, "winner is durable before publication");
            for (var duplicate : List.of(candidate(helper, fixture, "other", winner.binding().entityId(), UUID.randomUUID()),
                    candidate(helper, fixture, "other", UUID.randomUUID(), winner.operation()))) {
                try { data(helper).prepare(duplicate); helper.fail("duplicate entity or operation cannot claim another role"); }
                catch (IllegalArgumentException expected) { helper.assertValueEqual(data(helper).receipts().size(), 1, "original reservation preserved"); }
            }
            try { data(helper).commit(winner.key(), UUID.randomUUID(), helper.getLevel().getGameTime()); helper.fail("wrong operation cannot commit"); }
            catch (IllegalArgumentException expected) { helper.assertFalse(data(helper).forEntity(winner.binding().entityId()).committed(), "wrong operation leaves reservation pending"); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void invalidOrFutureReceiptsStayDormantWithoutOverwritingTheirBytes(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var receipt = prepare(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            var original = fixture.disk.read(CampaignSpawnSavedData.DATA_NAME);
            for (int mutation = 0; mutation < 7; mutation++) {
                var broken = original.copy();
                var saved = broken.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0);
                if (mutation == 0) broken.putInt("DataVersion", 99);
                if (mutation == 1) saved.getCompound("Entity").putUUID("UUID", UUID.randomUUID());
                if (mutation == 2) saved.getCompound("Entity").getCompound("NeoForgeData").remove("VillagerRetaliationCampaignOwner");
                if (mutation == 3) saved.getCompound("Binding").putString("Source", "EXISTING_VILLAGER");
                if (mutation == 4) saved.putString("CommittedAt", "yes");
                if (mutation == 5) broken.getList("Receipts", Tag.TAG_COMPOUND).add(saved.copy());
                if (mutation == 6) broken = new CompoundTag();
                var loaded = CampaignSpawnSavedData.load(broken, fixture.disk.root);
                helper.assertFalse(loaded.diagnostic().isBlank(), "invalid spawn ledger becomes dormant: " + mutation);
                helper.assertValueEqual(loaded.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "dormant bytes remain readable");
                try { loaded.prepare(receipt); helper.fail("dormant ledger cannot overwrite disk"); }
                catch (IOException expected) { helper.assertValueEqual(fixture.disk.read(CampaignSpawnSavedData.DATA_NAME), original, "original disk preserved"); }
            }
            var copy = receipt.entity(); copy.putUUID("UUID", UUID.randomUUID());
            helper.assertValueEqual(receipt.entity().getUUID("UUID"), receipt.binding().entityId(), "receipt snapshots cannot be mutated by callers");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void dedicatedCharacterRunsTheAuthoredQuestAndScene(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var result = fixture.resolve();
            helper.assertTrue(result.available(), result.diagnostic());
            var villager = result.entity(); villager.setNoAi(true);
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
            try {
                fixture.select(context, "visit", "offer");
                var progress = VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), fixture.campaignId);
                helper.assertTrue(progress != null, "dedicated provider offers its authored quest");
                var scenes = SceneSavedData.get(helper.getLevel());
                BuiltinSceneStepExecutors.register();
                long now = helper.getLevel().getGameTime();
                for (int tick = 0; tick < 40; tick++) {
                    for (var scene : List.copyOf(scenes.active())) SceneStepEngine.process(fixture.server, scenes, scene, fixture.scene, now + tick);
                    SceneContinuationService.maintain(fixture.server, scenes);
                    player.tickCount = tick; VillagerQuestService.onPlayerTick(player);
                }
                helper.assertValueEqual(progress.currentStage(), "report", "dedicated character scene permits quest progression");
                helper.assertValueEqual(scenes.all().iterator().next().actorBindings().get("guide").entityId(), villager.getUUID(), "scene uses the campaign-owned character");
                fixture.select(context, "report", "turn_in");
                helper.assertValueEqual(progress.completionCount(), 1, "dedicated provider completes its quest");
                CampaignCharacterActivation.activate(fixture.server, List.of(player.getUUID()));
                helper.assertValueEqual(data(helper).receipts().size(), 1, "completion never spawns another mentor");
            } finally { VillagerConversationService.endForPlayer(player, false); DialogueTreeService.clearRuntimeState(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void catchUpOnlyProviderActivatesAfterTheSharedMilestone(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var resources = new ArrayList<QuestBundleTransactions.RawResource>();
            for (String file : List.of("quest.json", "locales/en_us.json", "scenes/spawn_greeting.json")) {
                String path = "quests/campaign_fixture/spawns/" + file;
                resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id(path), CampaignCharacterTestFixture.resource(path)));
            }
            var original = CampaignCharacterTestFixture.resource("quests/campaign_fixture/spawns/quest.json");
            original.addProperty("id", "campaign_fixture:spawn_original");
            original.addProperty("localization_prefix", "campaign_fixture.quest.spawn_original");
            original.getAsJsonObject("provider").remove("campaign_role");
            original.getAsJsonArray("stages").get(0).getAsJsonObject().remove("on_enter");
            var locale = CampaignCharacterTestFixture.resource("quests/campaign_fixture/spawns/locales/en_us.json");
            var messages = new com.google.gson.JsonObject();
            locale.getAsJsonObject("messages").entrySet().forEach(entry -> messages.add(
                    entry.getKey().replace("campaign_fixture.quest.spawns.", "campaign_fixture.quest.spawn_original."), entry.getValue().deepCopy()));
            locale.add("messages", messages);
            resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id("quests/campaign_fixture/spawn_original/quest.json"), original));
            resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id("quests/campaign_fixture/spawn_original/locales/en_us.json"), locale));
            var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
            helper.assertTrue(bundles.diagnostics().isEmpty(), "catch-up fixture bundles compile: " + bundles.diagnostics());
            var quests = VillagerQuestResources.snapshotForCatalog(fixture.server, bundles);
            var catalog = new QuestContentCatalog(fixture.catalog.generation() + 1L, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                    quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(),
                    quests.triggerEventQuestIds(), Map.of(fixture.sceneId, fixture.scene), Map.of(), List.of(), bundles.bundles(), bundles.localization(), QuestRewardCatalog.fromBundles(bundles.bundles()));
            var raw = fixture.raw();
            raw.getAsJsonArray("quest_bundles").add("campaign_fixture:spawn_original");
            var chapter = raw.getAsJsonArray("chapters").get(0).getAsJsonObject();
            chapter.add("quests", JsonParser.parseString("[\"campaign_fixture:spawn_original\"]"));
            raw.getAsJsonArray("milestones").add(JsonParser.parseString("""
                    {"id":"event","chapter":"arrival","quest":"campaign_fixture:spawn_original","stage":"visit","irreversible":true,
                     "outcomes":[{"id":"resolved","title_key":"campaign_fixture.campaign.spawns.chapter"}],
                     "catch_up":{"quest":"campaign_fixture:spawns","stage":"visit"}}
                    """));
            var campaigns = CampaignContentCatalog.compile(catalog, List.of(new CampaignContentCatalog.Source(id("campaigns/spawns/campaign.json"),
                    "campaign_fixture", raw, fixture.campaigns.campaigns().get(fixture.campaignId).localization(), List.of())));
            helper.assertTrue(campaigns.diagnostics().isEmpty(), "authored catch-up references compile: " + campaigns.diagnostics());
            QuestContentCatalogs.installForTests(fixture.server, catalog, fixture.report);
            CampaignResources.installForTests(fixture.server, campaigns);
            var player = UUID.randomUUID();
            CampaignCharacterActivation.activate(fixture.server, List.of(player));
            helper.assertTrue(data(helper).receipts().isEmpty(), "unneeded catch-up provider stays unspawned");
            var content = campaigns.campaigns().get(fixture.campaignId);
            var campaignData = CampaignSavedData.get(helper.getLevel());
            var instance = campaignData.open(content, helper.getLevel().getGameTime());
            var committed = campaignData.commitWorldOutcome(content, "event", "resolved", UUID.randomUUID(), instance.worldRevision(),
                    CampaignInstance.Origin.PLAYER_DECISION, Set.of(UUID.randomUUID()), helper.getLevel().getGameTime());
            helper.assertValueEqual(committed.status(), CampaignSavedData.Status.APPLIED, "another player resolves the shared event");
            CampaignCharacterActivation.activate(fixture.server, List.of(player));
            helper.assertValueEqual(data(helper).receipts().size(), 1, "late player's catch-up quest activates its own provider");
            helper.assertTrue(fixture.resolve().available(), "late player can meet the dedicated catch-up character");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void failedNativeRecoveryWaitsForDurableReceiptStorage(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var receipt = prepare(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            CampaignCharacterSavedData.get(helper.getLevel()).bind(receipt.binding());
            var saved = fixture.disk.read(CampaignSpawnSavedData.DATA_NAME);
            var blocked = fixture.disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            var entity = (Villager) EntityType.loadEntityRecursive(receipt.entity(), helper.getLevel(), value -> value);
            try {
                fixture.server.overworld().getDataStorage().set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(saved, blocked));
                var event = new EntityJoinLevelEvent(entity, helper.getLevel(), true);
                CampaignOwnedSpawns.onEntityJoin(event);
                helper.assertTrue(event.isCanceled(), "uncommitted character cannot enter gameplay when its receipt write fails");
                helper.assertFalse(data(helper).forEntity(entity.getUUID()).committed(), "failed admission retains its reservation");
                helper.assertValueEqual(fixture.disk.read(CampaignSpawnSavedData.DATA_NAME), saved, "recovery failure preserves the original receipt");
            } finally { Files.delete(blocked); }
            fixture.reload();
            var event = new EntityJoinLevelEvent(entity, helper.getLevel(), true);
            CampaignOwnedSpawns.onEntityJoin(event);
            helper.assertFalse(event.isCanceled(), "repaired storage allows the same disk entity to enter");
            helper.assertTrue(data(helper).forEntity(entity.getUUID()).committed(), "recovered receipt is committed before admission");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_spawns")
    public static void reservedUuidCannotClaimAnEntityWithDifferentOwnership(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var receipt = prepare(helper, fixture, "mentor", UUID.randomUUID(), UUID.randomUUID());
            var existing = EntityType.VILLAGER.create(helper.getLevel());
            existing.setUUID(receipt.binding().entityId());
            existing.moveTo(fixture.origin.getX() + 0.5, fixture.origin.getY(), fixture.origin.getZ() + 0.5, 0, 0);
            existing.getPersistentData().putString("ExistingOwner", "another campaign");
            helper.assertTrue(helper.getLevel().addFreshEntity(existing), "foreign character occupies the reserved identity");
            var before = existing.saveWithoutId(new CompoundTag());
            var result = fixture.resolve();
            helper.assertFalse(result.available(), "conflicting ownership blocks the spawn");
            helper.assertTrue(result.diagnostic().contains("does not belong"), "ownership conflict has a repair diagnostic");
            helper.assertValueEqual(existing.saveWithoutId(new CompoundTag()), before, "conflicting entity remains untouched");
            helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().isEmpty(), "UUID collision cannot grant another character the role or its death protection");
            helper.assertFalse(data(helper).forEntity(existing.getUUID()).committed(), "conflict cannot acknowledge a spawn");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    private static CampaignCharacterTestFixture fixture(GameTestHelper helper) throws IOException {
        var fixture = new CampaignCharacterTestFixture(helper, "spawns", "spawn_greeting");
        helper.getLevel().setBlockAndUpdate(fixture.origin.below(), Blocks.STONE.defaultBlockState());
        return fixture;
    }

    private static CampaignSpawnSavedData data(GameTestHelper helper) { return CampaignSpawnSavedData.get(helper.getLevel()); }

    private static CampaignSpawnReceipt prepare(GameTestHelper helper, CampaignCharacterTestFixture fixture, String role, UUID entity, UUID operation) throws IOException {
        return data(helper).prepare(candidate(helper, fixture, role, entity, operation));
    }

    private static CampaignSpawnReceipt candidate(GameTestHelper helper, CampaignCharacterTestFixture fixture, String role, UUID entity, UUID operation) throws IOException {
        var content = CampaignResources.current(fixture.server).campaigns().get(fixture.campaignId);
        var definition = content.definition();
        var instance = CampaignSavedData.get(helper.getLevel()).open(content, helper.getLevel().getGameTime());
        var binding = new CampaignCharacterBinding(definition.id(), instance.instanceId(), definition.revision(), definition.fingerprint(), role,
                CampaignCharacterDefinition.Source.OWNED_SPAWN, CampaignCharacterDefinition.LossPolicy.DOWNED, entity,
                helper.getLevel().dimension().location(), fixture.origin, helper.getLevel().getGameTime(), false);
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setUUID(entity);
        villager.moveTo(fixture.origin.getX() + 0.5, fixture.origin.getY(), fixture.origin.getZ() + 0.5, 0, 0);
        var owner = new CompoundTag();
        owner.putUUID("Instance", instance.instanceId()); owner.putUUID("Operation", operation);
        owner.putString("Campaign", fixture.campaignId.toString()); owner.putString("Role", role);
        villager.getPersistentData().put("VillagerRetaliationCampaignOwner", owner);
        var snapshot = new CompoundTag();
        helper.assertTrue(villager.save(snapshot), "test reservation has a valid entity snapshot");
        return new CampaignSpawnReceipt(binding, operation, snapshot, -1L);
    }

    private static CompoundTag entityChunk(GameTestHelper helper, Entity entity) {
        var manager = ((ServerLevelEntityManagerAccessor) helper.getLevel()).villagerretaliation$entityManager();
        var storage = (EntityStorage) ((PersistentEntitySectionManagerAccessor) manager).villagerretaliation$permanentStorage();
        return ((EntityStorageAccessor) storage).villagerretaliation$regionStorage().read(new ChunkPos(entity.blockPosition())).join().orElseThrow();
    }

    private static boolean contains(CompoundTag chunk, UUID entity) {
        return chunk.getList("Entities", Tag.TAG_COMPOUND).stream().map(tag -> (CompoundTag) tag).anyMatch(tag -> tag.hasUUID("UUID") && tag.getUUID("UUID").equals(entity));
    }
}
