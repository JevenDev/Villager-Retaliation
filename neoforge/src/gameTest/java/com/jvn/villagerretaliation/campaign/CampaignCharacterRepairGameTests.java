package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_character_repairs")
@PrefixGameTestTemplate(false)
public final class CampaignCharacterRepairGameTests {
    private CampaignCharacterRepairGameTests() { }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void repairsPreserveIdentityHistoryAndInvalidateRequestsAcrossLossAndRecovery(GameTestHelper helper) throws Exception {
        try (var disk = new Disk()) {
            var data = disk.read();
            var original = binding(UUID.randomUUID(), "mentor", CampaignCharacterDefinition.LossPolicy.DOWNED, UUID.randomUUID(), 1L);
            data.bind(original);
            UUID token = data.token(original.key()), operator = UUID.randomUUID();
            data.recordLoss(original.entityId(), true);
            helper.assertFalse(data.token(original.key()).equals(token), "confirmed loss invalidates requests made before it");
            var next = binding(original.instance(), original.role(), original.lossPolicy(), UUID.randomUUID(), 2L);
            rejected(helper, () -> data.repair(UUID.randomUUID(), token, next, operator, "stale", 2L));
            var first = data.repair(UUID.randomUUID(), data.token(original.key()), next, operator, "restore mentor", 2L);
            var restored = disk.read();
            helper.assertValueEqual(restored.bindings().get(original.key()), next, "repair binding survives reload");
            helper.assertValueEqual(restored.repairs().get(first.operation()), first, "repair audit survives reload");
            helper.assertTrue(restored.reserved(original.entityId()) && restored.forEntity(original.entityId()) == null,
                    "historical identity stays reserved without acting as the current character");
            restored.recordLoss(next.entityId(), true);
            UUID lostToken = restored.token(next.key());
            restored.recordLoss(next.entityId(), false);
            helper.assertFalse(restored.token(next.key()).equals(lostToken), "recovery invalidates requests made while lost");
            var back = restored.repair(UUID.randomUUID(), restored.token(original.key()), original, operator, "restore original identity", 3L);
            helper.assertValueEqual(restored.bindings().get(original.key()), original, "original identity can be explicitly recovered");
            helper.assertFalse(restored.token(original.key()).equals(token), "returning to original fields cannot revive an old request");
            helper.assertValueEqual(restored.authorization(original.key(), next.entityId(), original.entityId()), List.of(back), "consumers can follow the remaining repair");
            helper.assertValueEqual(restored.repair(first.operation(), first.expected(), next, operator, first.reason(), 2L), first, "duplicate request retains its original receipt");
            helper.assertValueEqual(restored.bindings().get(original.key()), original, "old retry cannot undo recovery");
            var foreignRole = binding(original.instance(), "other", original.lossPolicy(), next.entityId(), 4L);
            rejected(helper, () -> restored.bind(foreignRole));
            helper.assertValueEqual(disk.read().token(original.key()), restored.token(original.key()), "history token survives another reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void competingRepairsPublishOneBindingAndOneAudit(GameTestHelper helper) throws Exception {
        try (var disk = new Disk(); var executor = Executors.newFixedThreadPool(2)) {
            var data = disk.read();
            var original = binding(UUID.randomUUID(), "mentor", CampaignCharacterDefinition.LossPolicy.DOWNED, UUID.randomUUID(), 1L);
            data.bind(original);
            UUID token = data.token(original.key()), operator = UUID.randomUUID();
            var tasks = List.<Callable<Boolean>>of(
                    () -> attempt(data, token, binding(original.instance(), "mentor", original.lossPolicy(), UUID.randomUUID(), 2L), operator),
                    () -> attempt(data, token, binding(original.instance(), "mentor", original.lossPolicy(), UUID.randomUUID(), 2L), operator));
            var results = executor.invokeAll(tasks);
            helper.assertTrue(results.getFirst().get() != results.getLast().get(), "exactly one request commits");
            helper.assertValueEqual(data.repairs().size(), 1, "losing request creates no audit");
            helper.assertValueEqual(disk.read().bindings(), data.bindings(), "persisted winner is authoritative");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void successorRepairsPreserveTheAuthoredReplacementDecision(GameTestHelper helper) throws Exception {
        try (var disk = new Disk()) {
            var data = disk.read();
            var original = binding(UUID.randomUUID(), "mentor", CampaignCharacterDefinition.LossPolicy.REPLACEMENT, UUID.randomUUID(), 1L);
            var successor = binding(original.instance(), "successor", CampaignCharacterDefinition.LossPolicy.DOWNED, UUID.randomUUID(), 1L);
            data.bind(original); data.bind(successor);
            data.recordLoss(original.entityId(), true);
            data.prepareReplacement(data.bindings().get(original.key()), successor.role(), 2L);
            var replacement = data.commitReplacement(original.key(), successor, 2L);
            var current = binding(original.instance(), successor.role(), successor.lossPolicy(), UUID.randomUUID(), 3L);
            data.repair(UUID.randomUUID(), data.token(successor.key()), current, UUID.randomUUID(), "repair successor", 3L);
            helper.assertValueEqual(disk.read().replacements().get(original.key()), replacement, "historical replacement remains unchanged");
            helper.assertValueEqual(data.commitReplacement(original.key(), current, 4L), replacement, "replacement retry recognizes its audited successor");
            rejected(helper, () -> data.commitReplacement(original.key(), successor, 4L));
            rejected(helper, () -> data.repair(UUID.randomUUID(), data.token(original.key()), original, UUID.randomUUID(), "undo death", 4L));
            helper.assertTrue(data.bindings().get(original.key()).lost(), "repair cannot resurrect a role with an authored successor");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void migrationsCorruptionAndFailedWritesKeepTheLastDurableState(GameTestHelper helper) throws Exception {
        try (var disk = new Disk()) {
            var data = disk.read();
            var original = binding(UUID.randomUUID(), "mentor", CampaignCharacterDefinition.LossPolicy.DOWNED, UUID.randomUUID(), 1L);
            data.bind(original);
            var before = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            for (int version : List.of(1, 2)) {
                var legacy = before.copy();
                legacy.putInt("DataVersion", version); legacy.remove("Repairs");
                if (version == 1) legacy.remove("Replacements");
                var migrated = CampaignCharacterSavedData.load(legacy, disk.root);
                helper.assertTrue(migrated.diagnostic().isBlank(), "previous character data migrates");
                helper.assertValueEqual(migrated.bindings().get(original.key()), original, "migration keeps the original identity");
                helper.assertTrue(migrated.repairs().isEmpty(), "migration invents no operator audit");
            }
            Path blocked = disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            var failing = CampaignCharacterSavedData.load(before, blocked);
            var next = binding(original.instance(), original.role(), original.lossPolicy(), UUID.randomUUID(), 2L);
            boolean failed = false;
            try { failing.repair(UUID.randomUUID(), failing.token(original.key()), next, UUID.randomUUID(), "repair", 2L); }
            catch (IOException expected) { failed = true; }
            helper.assertTrue(failed, "failed save is surfaced");
            helper.assertValueEqual(failing.save(new CompoundTag(), helper.getLevel().registryAccess()), before, "failed save publishes no partial repair");
            data.repair(UUID.randomUUID(), data.token(original.key()), next, UUID.randomUUID(), "repair", 2L);
            var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var missing = saved.copy(); missing.remove("Repairs");
            var legacy = saved.copy(); legacy.putInt("DataVersion", 2);
            var future = saved.copy(); future.putInt("DataVersion", CampaignCharacterSavedData.CURRENT_DATA_VERSION + 1);
            var wrongToken = saved.copy(); wrongToken.getList("Repairs", Tag.TAG_COMPOUND).getCompound(0).putUUID("Expected", UUID.randomUUID());
            var wrongCurrent = saved.copy(); wrongCurrent.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).putUUID("Entity", UUID.randomUUID());
            for (var invalid : List.of(missing, legacy, future, wrongToken, wrongCurrent)) {
                var unreadable = CampaignCharacterSavedData.load(invalid, disk.root);
                helper.assertFalse(unreadable.diagnostic().isBlank(), "invalid repair history is dormant");
                helper.assertValueEqual(unreadable.save(new CompoundTag(), helper.getLevel().registryAccess()), invalid, "unreadable state is preserved");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void operatorRepairsPreserveVillagerStateAndReconcileQuestAndSceneProviders(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(), quest -> { }, campaign -> { },
                List.of(), scene -> scene.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("replacement_policy", "compatible_replacement"))) {
            var original = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
            var target = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 1);
            original.setNoAi(true); target.setNoAi(true);
            target.setCustomName(net.minecraft.network.chat.Component.literal("Existing villager"));
            target.getPersistentData().putString("repair_fixture", "personal state");
            var originalBinding = fixture.resolve().binding();
            helper.assertValueEqual(originalBinding.entityId(), original.getUUID(), "fixture casts original identity");
            var targetBefore = target.saveWithoutId(new CompoundTag());
            var player = helper.makeMockServerPlayerInLevel();
            try {
                var quests = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(helper.getLevel());
                var quest = fixture.catalog.quest(fixture.campaignId).orElseThrow();
                var progress = quests.getOrCreate(player.getUUID(), fixture.campaignId);
                progress.start(original.getUUID(), helper.getLevel().dimension(), fixture.origin, 1L);
                progress.setCurrentStage("visit");
                progress.markObjectiveComplete(quest.objectives().getFirst().id());
                var scenes = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
                var scene = scenes.start(fixture.scene, "character-repair",
                        new com.jvn.villagerretaliation.scene.runtime.SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                                player.getUUID(), null, null, ""), UUID.randomUUID(), java.util.Set.of(player.getUUID()), java.util.Map.of(), 1L, fixture.campaignId).instance();
                scene.bindCampaign(fixture.campaignId);
                helper.assertValueEqual(CampaignSceneCharacters.resolve(fixture.server, scenes, scene, fixture.scene), "", "scene casts original character");
                var source = fixture.server.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2).withSuppressedOutput();
                var dispatcher = fixture.server.getCommands().getDispatcher();
                var data = CampaignCharacterSavedData.get(helper.getLevel());
                String prefix = "vr admin campaign binding character campaign_fixture:characters mentor";
                helper.assertValueEqual(dispatcher.execute(prefix, source), 1, "operator can inspect identity and token");
                boolean denied = false;
                try { dispatcher.execute(prefix, source.withPermission(0)); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
                helper.assertTrue(denied, "ordinary player cannot access repairs");
                String command = prefix + " repair " + data.token(originalBinding.key()) + " " + UUID.randomUUID() + " " + target.getUUID() + " repair mentor";
                helper.assertValueEqual(dispatcher.execute(command, source), 1, "repair command chooses eligible loaded actor");
                helper.assertValueEqual(target.saveWithoutId(new CompoundTag()), targetBefore, "repair preserves all personal villager data");
                helper.assertValueEqual(dispatcher.execute(command, source), 1, "retry returns the saved repair");
                helper.assertValueEqual(data.repairs().size(), 1, "retry does not create another audit");
                helper.assertTrue(CampaignQuestCharacters.reconcile(player, quest, progress), "personal quest adopts authorized provider");
                helper.assertValueEqual(progress.startedVillagerId(), target.getUUID(), "quest points at repaired role");
                helper.assertTrue(progress.objectiveComplete(quest.objectives().getFirst().id()), "earned objective progress remains");
                helper.assertValueEqual(CampaignSceneCharacters.resolve(fixture.server, scenes, scene, fixture.scene), "", "compatible scene follows the saved repair");
                helper.assertValueEqual(scene.actorBindings().get("guide").entityId(), target.getUUID(), "scene uses repaired character");
                helper.assertValueEqual(scene.actorBindings().get("guide").replacementHistory().size(), 1, "scene retains original speaker identity");
                helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.campaignId).players().size(), 0, "repair grants no personal campaign participation");
                helper.assertValueEqual(dispatcher.execute(prefix + " 999", source), 0, "invalid audit page is rejected");
                fixture.reload();
                helper.assertValueEqual(fixture.resolve().entity(), target, "repaired identity survives ledger reload");
            } finally {
                fixture.server.getPlayerList().remove(player);
                original.discard(); target.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void activeFixedScenesRefuseRepairAndOperatorScenesRecordItsAuthority(GameTestHelper helper) throws Exception {
        for (String policy : List.of("fixed", "operator_rebindable")) {
            try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(), quest -> { }, campaign -> { },
                    List.of(), scene -> {
                        var actor = scene.getAsJsonArray("actors").get(0).getAsJsonObject();
                        actor.addProperty("replacement_policy", policy);
                        if (policy.equals("operator_rebindable")) {
                            actor.addProperty("binding_source", "quest_provider");
                            actor.remove("binding");
                        }
                    })) {
                var original = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
                var target = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 1);
                original.setNoAi(true); target.setNoAi(true);
                try {
                    var binding = fixture.resolve().binding();
                    var data = CampaignCharacterSavedData.get(helper.getLevel());
                    var scenes = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
                    UUID owner = UUID.randomUUID(), operator = UUID.randomUUID();
                    var scene = scenes.start(fixture.scene, "scene-policy",
                            new com.jvn.villagerretaliation.scene.runtime.SceneOwner(com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER,
                                    owner, null, null, ""), UUID.randomUUID(), java.util.Set.of(owner), java.util.Map.of(), 1L, fixture.campaignId).instance();
                    scene.bindCampaign(fixture.campaignId);
                    helper.assertValueEqual(CampaignSceneCharacters.resolve(fixture.server, scenes, scene, fixture.scene), "", "initial scene binding resolves");
                    var result = CampaignCharacterRepairs.repair(fixture.server, new CampaignCharacterRepairs.Request(fixture.campaignId, "mentor",
                            target.getUUID(), data.token(binding.key()), UUID.randomUUID(), operator, "repair scene character"));
                    if (policy.equals("fixed")) {
                        helper.assertFalse(result.accepted(), "fixed actor refuses canonical reassignment");
                        helper.assertTrue(result.diagnostic().contains(scene.id().toString()), "refusal identifies the blocking scene");
                        helper.assertValueEqual(data.bindings().get(binding.key()), binding, "refusal preserves the original character");
                        helper.assertTrue(data.repairs().isEmpty(), "refusal writes no audit");
                    } else {
                        helper.assertTrue(result.accepted(), result.diagnostic());
                        helper.assertValueEqual(CampaignSceneCharacters.resolve(fixture.server, scenes, scene, fixture.scene), "", "operator actor accepts audited repair");
                        var rebound = scene.actorBindings().get("guide");
                        helper.assertValueEqual(rebound.entityId(), target.getUUID(), "operator actor uses repaired identity");
                        helper.assertValueEqual(rebound.replacementHistory().getFirst().operatorIdentity(), operator.toString(), "scene preserves operator authority");
                    }
                } finally { original.discard(); target.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void activeJourneysResumeWithRepairedGuidesWithoutResettingEarnedDistance(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignFollowTestFixture(helper, false,
                scene -> scene.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("replacement_policy", "compatible_replacement"))) {
            var player = fixture.player();
            fixture.accept(player);
            fixture.step();
            fixture.walk(1, player);
            var before = fixture.journey();
            var target = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 1);
            target.setNoAi(true); target.setOnGround(true); target.setPos(fixture.mentor.position());
            try {
                var data = CampaignCharacterSavedData.get(helper.getLevel());
                var binding = data.bindings().get(new CampaignCharacterBinding.Key(fixture.base.campaignId, "mentor"));
                var repair = CampaignCharacterRepairs.repair(fixture.base.server, new CampaignCharacterRepairs.Request(fixture.base.campaignId,
                        "mentor", target.getUUID(), data.token(binding.key()), UUID.randomUUID(), player.getUUID(), "recover journey guide"));
                helper.assertTrue(repair.accepted(), repair.diagnostic());
                var questContext = fixture.objectiveContext(player);
                helper.assertTrue(CampaignQuestCharacters.reconcile(player, questContext.definition(), questContext.progress()), "quest provider reconciles before scene resumes");
                fixture.block(fixture.journeyPath());
                try {
                    fixture.step();
                    helper.assertValueEqual(fixture.journey(), before, "failed journal write preserves the durable journey");
                    helper.assertFalse(fixture.complete(player), "repair cannot award arrival");
                } finally { fixture.restore(fixture.journeyPath()); }
                fixture.step();
                var rebound = fixture.journey();
                helper.assertValueEqual(rebound.actor(), target.getUUID(), "active journey adopts authorized character");
                helper.assertValueEqual(rebound.distance(), before.distance(), "rebind keeps earned distance");
                helper.assertValueEqual(rebound.participants(), before.participants(), "rebind keeps participant trails");
                helper.assertValueEqual(rebound.startedAt(), before.startedAt(), "rebind keeps original start");
                fixture.reload();
                helper.assertValueEqual(fixture.journey().actor(), target.getUUID(), "repaired guide survives reload");
                for (int x = 2; x <= 6; x++) {
                    var position = net.minecraft.world.phys.Vec3.atBottomCenterOf(fixture.base.origin).add(x, 0, 0);
                    target.setPos(position); player.setPos(position.add(0, 0, 0.5)); fixture.step();
                }
                helper.assertTrue(fixture.complete(player), "continued accompaniment earns arrival");
                var completed = fixture.journey();
                var restore = CampaignCharacterRepairs.repair(fixture.base.server, new CampaignCharacterRepairs.Request(fixture.base.campaignId,
                        "mentor", fixture.mentor.getUUID(), data.token(binding.key()), UUID.randomUUID(), player.getUUID(), "restore original guide"));
                helper.assertTrue(restore.accepted(), restore.diagnostic());
                fixture.step();
                helper.assertValueEqual(fixture.journey(), completed, "later repair preserves completed journey evidence");
            } finally { target.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void characterRepairRequiresCampaignModeAuthoredEligibilityAndLoadedOriginals(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(), quest -> { },
                campaign -> campaign.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("profession", "minecraft:librarian"))) {
            var original = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
            var target = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 1);
            original.setNoAi(true); target.setNoAi(true);
            original.setVillagerData(original.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
            try {
                helper.assertFalse(repair(fixture, original).accepted(), "missing saved casting location is recoverable without implicit search");
                original.getPersistentData().put("VillagerRetaliationCampaignOwner", new CompoundTag());
                helper.assertFalse(fixture.resolve().available(), "automatic casting preserves owned actors even without their spawn ledger");
                original.getPersistentData().remove("VillagerRetaliationCampaignOwner");
                var binding = fixture.resolve().binding();
                helper.assertFalse(repair(fixture, target).accepted(), "wrong profession is refused");
                target.setVillagerData(target.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
                target.getPersistentData().put("VillagerRetaliationCampaignOwner", new CompoundTag());
                helper.assertFalse(repair(fixture, target).accepted(), "repair preserves owned actors even without their spawn ledger");
                target.getPersistentData().remove("VillagerRetaliationCampaignOwner");
                fixture.mode("sandbox");
                helper.assertFalse(repair(fixture, target).accepted(), "Sandbox character state cannot be rewritten");
                fixture.mode("campaign");
                target.setPos(target.getX() + 10, target.getY(), target.getZ());
                helper.assertFalse(repair(fixture, target).accepted(), "casting radius is enforced");
                target.setPos(original.getX() + 1, original.getY(), original.getZ());
                original.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                var unloaded = repair(fixture, target);
                helper.assertFalse(unloaded.accepted(), "unloaded living original is not silently replaced");
                helper.assertTrue(unloaded.diagnostic().contains("unloaded"), "operator is told to load the original identity");
                var data = CampaignCharacterSavedData.get(helper.getLevel());
                helper.assertValueEqual(data.bindings().get(binding.key()), binding, "refused attempts preserve ownership and loss state");
                helper.assertTrue(data.repairs().isEmpty(), "refused repairs leave no audit or effects");
            } finally { original.discard(); target.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void ownedCharactersRecoverOnlyTheirOriginalSpawnReceiptWithoutResettingPersonalState(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "spawns", "spawn_greeting")) {
            var floor = helper.getLevel().getBlockState(fixture.origin.below());
            helper.getLevel().setBlockAndUpdate(fixture.origin.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            var resolved = fixture.resolve();
            helper.assertTrue(resolved.available(), resolved.diagnostic());
            var original = resolved.entity();
            var impostor = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 1);
            try {
                original.setNoAi(true); impostor.setNoAi(true);
                original.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 11));
                original.getPersistentData().putInt("PersonalRelationship", 37);
                var before = original.saveWithoutId(new CompoundTag());
                var receipt = CampaignSpawnSavedData.get(helper.getLevel()).receipts().get(resolved.binding().key());
                var empty = new CompoundTag();
                empty.putInt("DataVersion", CampaignCharacterSavedData.CURRENT_DATA_VERSION);
                for (String field : List.of("Bindings", "Replacements", "Repairs")) empty.put(field, new net.minecraft.nbt.ListTag());
                fixture.server.overworld().getDataStorage().set(CampaignCharacterSavedData.DATA_NAME, CampaignCharacterSavedData.load(empty, fixture.disk.root));
                helper.assertFalse(repair(fixture, impostor).accepted(), "owned roles cannot recast an arbitrary villager");
                var repaired = repair(fixture, original);
                helper.assertTrue(repaired.accepted(), repaired.diagnostic());
                helper.assertValueEqual(repaired.binding(), receipt.binding(), "missing role ledger recovers its original birth identity");
                helper.assertValueEqual(original.saveWithoutId(new CompoundTag()), before, "recovery preserves personal inventory and relationships");
                helper.assertValueEqual(CampaignSpawnSavedData.get(helper.getLevel()).receipts().get(resolved.binding().key()), receipt, "spawn operation is never repeated");
                fixture.reload();
                helper.assertValueEqual(fixture.resolve().entity(), original, "recovered owned identity survives reload");
                helper.assertValueEqual(CampaignSpawnSavedData.get(helper.getLevel()).receipts().size(), 1, "exactly one spawn receipt remains");
            } finally {
                original.discard(); impostor.discard();
                helper.getLevel().setBlockAndUpdate(fixture.origin.below(), floor);
            }
        }
        helper.succeed();
    }

    private static CampaignCharacterRepairs.Result repair(CampaignCharacterTestFixture fixture, net.minecraft.world.entity.npc.Villager target) {
        var data = CampaignCharacterSavedData.get(fixture.helper.getLevel());
        var key = new CampaignCharacterBinding.Key(fixture.campaignId, "mentor");
        return CampaignCharacterRepairs.repair(fixture.server, new CampaignCharacterRepairs.Request(fixture.campaignId, "mentor",
                target.getUUID(), data.token(key), UUID.randomUUID(), UUID.randomUUID(), "recover character binding"));
    }

    private static CampaignCharacterBinding binding(UUID instance, String role, CampaignCharacterDefinition.LossPolicy policy, UUID entity, long time) {
        return new CampaignCharacterBinding(ResourceLocation.parse("campaign_fixture:repair"), instance, 1, "fingerprint", role,
                CampaignCharacterDefinition.Source.EXISTING_VILLAGER, policy, entity, ResourceLocation.parse("minecraft:overworld"), new BlockPos(0, 70, 0), time, false);
    }

    private static boolean attempt(CampaignCharacterSavedData data, UUID token, CampaignCharacterBinding binding, UUID operator) throws IOException {
        try { data.repair(UUID.randomUUID(), token, binding, operator, "repair", binding.boundAt()); return true; }
        catch (IllegalArgumentException expected) { return false; }
    }

    private static void rejected(GameTestHelper helper, Checked action) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "invalid character repair must be rejected");
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }

    private static final class Disk implements AutoCloseable {
        final Path root = Files.createTempDirectory("campaign-character-repairs-");
        Disk() throws IOException { }
        CampaignCharacterSavedData read() throws IOException {
            var path = root.resolve(CampaignCharacterSavedData.DATA_NAME + ".dat");
            return CampaignCharacterSavedData.load(Files.exists(path)
                    ? NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound("data") : new CompoundTag(), root);
        }
        @Override public void close() throws IOException {
            try (var files = Files.list(root)) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(root);
        }
    }
}
