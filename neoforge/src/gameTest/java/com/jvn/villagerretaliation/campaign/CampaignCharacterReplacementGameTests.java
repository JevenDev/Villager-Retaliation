package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignCharacterReplacementGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("campaign_fixture:replacements");

    private CampaignCharacterReplacementGameTests() { }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void authoredReplacementsRequireAValidAcyclicSuccessor(GameTestHelper helper) throws IOException {
        var root = CampaignCharacterTestFixture.resource("campaigns/replacements/campaign.json");
        helper.assertTrue(CampaignSchema.validate(CAMPAIGN, root).isEmpty(), "fixture matches the exported schema");
        for (int mutation = 0; mutation < 6; mutation++) {
            var broken = root.deepCopy();
            var mentor = character(broken, 0);
            if (mutation == 0) mentor.remove("replacement_role");
            if (mutation == 1) mentor.addProperty("loss_policy", "downed");
            if (mutation == 2) mentor.addProperty("replacement_role", "missing");
            if (mutation == 3) mentor.addProperty("replacement_role", "mentor");
            if (mutation == 4) { character(broken, 1).addProperty("loss_policy", "replacement"); character(broken, 1).addProperty("replacement_role", "mentor"); }
            if (mutation == 5) mentor.addProperty("replacement_role", true);
            var parsed = CampaignDefinitionParser.parse(CAMPAIGN, broken);
            helper.assertFalse(parsed.valid(), "invalid successor rejected: " + mutation);
            helper.assertTrue(parsed.diagnostics().stream().anyMatch(issue -> issue.jsonPointer().startsWith("/characters/")), "diagnostics identify the authored role");
            if (mutation < 2 || mutation == 5) helper.assertFalse(CampaignSchema.validate(CAMPAIGN, broken).isEmpty(), "schema and runtime agree on replacement fields");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void characterSaveMigrationPreservesBindingsAndRejectsInvalidReceipts(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor();
            var legacy = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            legacy.putInt("DataVersion", 1); legacy.remove("Replacements");
            var legacyBinding = legacy.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0);
            legacyBinding.putString("LossPolicy", "DOWNED");
            var original = CampaignCharacterBinding.load(legacyBinding);
            var migrated = CampaignCharacterSavedData.load(legacy, fixture.base.disk.root);
            helper.assertTrue(migrated.diagnostic().isBlank() && migrated.replacements().isEmpty(), "version one migrates explicitly");
            helper.assertValueEqual(migrated.forEntity(mentor.getUUID()), original, "migration keeps identity and ownership");
            helper.assertValueEqual(migrated.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"), 2, "next save uses version two");
            mentor.discard(); fixture.villager(); fixture.base.resolve();
            var valid = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            for (int mutation = 0; mutation < 8; mutation++) {
                var broken = valid.copy();
                var receipts = broken.getList("Replacements", Tag.TAG_COMPOUND);
                var receipt = receipts.getCompound(0);
                if (mutation == 0) broken.putInt("DataVersion", 99);
                if (mutation == 1) broken.remove("Replacements");
                if (mutation == 2) receipts.add(receipt.copy());
                if (mutation == 3) receipt.putUUID("Operation", UUID.randomUUID());
                if (mutation == 4) receipt.putUUID("ReplacementEntity", UUID.randomUUID());
                if (mutation == 5) receipt.putString("ReplacementRole", "mentor");
                if (mutation == 6) broken.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).putBoolean("Lost", false);
                if (mutation == 7) broken.putInt("DataVersion", 1);
                var rejected = CampaignCharacterSavedData.load(broken, fixture.base.disk.root);
                helper.assertFalse(rejected.diagnostic().isBlank(), "invalid replacement save is dormant: " + mutation);
                helper.assertTrue(rejected.bindings().isEmpty() && rejected.replacements().isEmpty(), "no partial receipt state published");
                helper.assertValueEqual(rejected.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "invalid save remains recoverable");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void unloadedOriginalNeverCastsItsSuccessor(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); var saved = mentor.saveWithoutId(new CompoundTag());
            fixture.villager(); mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); fixture.reload();
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            helper.assertFalse(fixture.base.resolve().available(), "unloaded original waits");
            CampaignCharacterActivation.activate(fixture.base.server, List.of(UUID.randomUUID()));
            helper.assertTrue(fixture.data().replacements().isEmpty(), "absence never prepares replacement");
            helper.assertValueEqual(fixture.data().bindings().size(), 1, "successor is not cast speculatively");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "resolution does not load chunks");
            var restored = fixture.restore(saved);
            helper.assertValueEqual(fixture.base.resolve().entity(), restored, "original UUID resumes its role");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void replacementRetainsBothIdentitiesAndTheSuccessorsPersonalState(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); var original = fixture.data().forEntity(mentor.getUUID());
            var saved = mentor.saveWithoutId(new CompoundTag());
            var successor = fixture.villager(); successor.setCustomName(Component.literal("Existing successor"));
            successor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 6));
            var independent = CampaignCharacterService.resolve(fixture.base.server, CAMPAIGN, "successor");
            mentor.discard();
            var replacement = fixture.base.resolve();
            helper.assertValueEqual(replacement.entity(), successor, "authored successor takes over");
            var receipt = fixture.data().replacements().get(original.key());
            helper.assertTrue(receipt.committed(), "replacement commits before acknowledgement");
            helper.assertValueEqual(fixture.data().forEntity(mentor.getUUID()), original.withLoss(true), "original binding is preserved");
            helper.assertValueEqual(replacement.binding(), independent.binding(), "successor keeps its own binding");
            helper.assertValueEqual(successor.getCustomName().getString(), "Existing successor", "casting preserves personal name");
            helper.assertValueEqual(successor.getInventory().getItem(0).getCount(), 6, "casting preserves inventory");
            fixture.reload(); fixture.restore(saved);
            helper.assertValueEqual(fixture.base.resolve().entity(), successor, "restored original cannot reclaim a committed role");
            helper.assertValueEqual(fixture.data().replacements().get(original.key()), receipt, "reload and retries keep the exact receipt");
            helper.assertFalse(CampaignCharacterReplacements.authorization(fixture.base.server, CAMPAIGN, "mentor", mentor.getUUID(), successor.getUUID()).isBlank(), "saved chain authorizes the successor");
            helper.assertTrue(CampaignCharacterReplacements.authorization(fixture.base.server, CAMPAIGN, "mentor", UUID.randomUUID(), successor.getUUID()).isBlank(), "unrelated identities cannot use the authorization");
            helper.assertTrue(CampaignCharacterReplacements.authorization(fixture.base.server, CAMPAIGN, "mentor", successor.getUUID(), mentor.getUUID()).isBlank(), "replacement cannot run backwards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void originalRecoveryBeforePreparationKeepsItsRole(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); var saved = mentor.saveWithoutId(new CompoundTag());
            mentor.discard(); var restored = fixture.restore(saved);
            helper.assertValueEqual(fixture.base.resolve().entity(), restored, "recovery wins before a decision is saved");
            helper.assertTrue(fixture.data().replacements().isEmpty(), "recovery does not record replacement");
            fixture.reload(); helper.assertFalse(fixture.data().forEntity(mentor.getUUID()).lost(), "recovered status is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void missingSuccessorWaitsWithADurableDecision(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); var saved = mentor.saveWithoutId(new CompoundTag()); mentor.discard();
            helper.assertFalse(fixture.base.resolve().available(), "missing successor is recoverable");
            var prepared = fixture.data().replacements().values().iterator().next();
            helper.assertFalse(prepared.committed(), "decision precedes casting");
            fixture.reload(); fixture.restore(saved);
            helper.assertFalse(fixture.base.resolve().available(), "original cannot displace a prepared decision");
            var successor = fixture.villager();
            helper.assertValueEqual(fixture.base.resolve().entity(), successor, "newly available successor completes the saved decision");
            helper.assertValueEqual(fixture.data().replacements().get(prepared.key()).operation(), prepared.operation(), "retry uses the same operation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void failedPreparationCannotCastOrSpawnASuccessor(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var raw = fixture.base.raw(); character(raw, 1).addProperty("source", "owned_spawn"); character(raw, 1).addProperty("search_radius", 2); fixture.base.install(raw);
            fixture.mentor().discard();
            var snapshot = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var blocked = Files.createTempFile(fixture.base.disk.root, "blocked-", ".tmp");
            try {
                fixture.characters(CampaignCharacterSavedData.load(snapshot, blocked));
                helper.assertFalse(fixture.base.resolve().available(), "failed preparation is unavailable");
                helper.assertTrue(fixture.data().replacements().isEmpty(), "failed receipt is not published");
                helper.assertValueEqual(fixture.data().bindings().size(), 1, "no successor binding exists before preparation");
                helper.assertTrue(CampaignSpawnSavedData.get(helper.getLevel()).receipts().isEmpty(), "no spawn can occur before preparation");
            } finally { Files.delete(blocked); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void failedCommitKeepsThePreparedDecisionAndExactSuccessor(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); mentor.discard(); fixture.base.resolve();
            var successor = fixture.villager(); CampaignCharacterService.resolve(fixture.base.server, CAMPAIGN, "successor");
            var prepared = fixture.data().replacements().values().iterator().next();
            var snapshot = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var blocked = Files.createTempFile(fixture.base.disk.root, "blocked-", ".tmp");
            try {
                fixture.characters(CampaignCharacterSavedData.load(snapshot, blocked));
                helper.assertFalse(fixture.base.resolve().available(), "failed commit is not acknowledged");
                helper.assertValueEqual(fixture.data().replacements().get(prepared.key()), prepared, "prepared decision remains unchanged");
                helper.assertValueEqual(fixture.data().bindings().size(), 2, "already saved successor is retained");
            } finally { Files.delete(blocked); }
            fixture.reload();
            helper.assertValueEqual(fixture.base.resolve().entity(), successor, "retry commits the same successor");
            helper.assertTrue(fixture.data().replacements().get(prepared.key()).committed(), "recovered storage permits commit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void duplicateCommitsReturnTheSavedReplacement(GameTestHelper helper) throws Exception {
        try (var fixture = new Fixture(helper); var executor = Executors.newFixedThreadPool(2)) {
            var mentor = fixture.mentor(); mentor.discard(); fixture.base.resolve();
            fixture.villager(); var successor = CampaignCharacterService.resolve(fixture.base.server, CAMPAIGN, "successor").binding();
            var data = fixture.data(); var receipt = data.replacements().values().iterator().next();
            long now = helper.getLevel().getGameTime();
            var results = executor.invokeAll(List.<Callable<CampaignCharacterReplacement>>of(
                    () -> data.commitReplacement(receipt.key(), successor, now), () -> data.commitReplacement(receipt.key(), successor, now + 1)));
            helper.assertValueEqual(results.getFirst().get(), results.getLast().get(), "simultaneous retries return one committed receipt");
            fixture.reload(); helper.assertValueEqual(fixture.data().replacements().get(receipt.key()), results.getFirst().get(), "winning receipt survives reload");
            try { fixture.data().recordLoss(mentor.getUUID(), false); helper.fail("retired identity cannot reclaim the role"); }
            catch (IOException expected) { helper.assertTrue(fixture.data().forEntity(mentor.getUUID()).lost(), "retired binding remains lost"); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void unloadedSuccessorKeepsItsIdentityAcrossReloads(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.mentor().discard(); var successor = fixture.villager(); fixture.base.resolve();
            var receipt = fixture.data().replacements().values().iterator().next();
            var saved = successor.saveWithoutId(new CompoundTag()); successor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.villager(); fixture.reload();
            helper.assertFalse(fixture.base.resolve().available(), "unloaded successor waits without recasting");
            helper.assertValueEqual(fixture.data().replacements().get(receipt.key()), receipt, "unloading cannot change the decision");
            helper.assertValueEqual(fixture.data().bindings().size(), 2, "third villager cannot take the role");
            var restored = fixture.restore(saved);
            helper.assertValueEqual(fixture.base.resolve().entity(), restored, "successor returns with the same identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void replacementChainsRemainReadOnlyInDialogueConditions(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var raw = fixture.base.raw(); var last = character(raw, 1).deepCopy(); last.addProperty("id", "last");
            character(raw, 1).addProperty("loss_policy", "replacement"); character(raw, 1).addProperty("replacement_role", "last");
            raw.getAsJsonArray("characters").add(last); fixture.base.install(raw);
            var mentor = fixture.mentor(); mentor.discard();
            var successor = fixture.villager(); var player = fixture.player(); var context = fixture.context(player, successor);
            helper.assertTrue(new CampaignCharacterPredicate(CAMPAIGN, "mentor", CampaignCharacterPredicate.State.LOST).evaluate(context).matches(), "unresolved loss remains visible");
            helper.assertFalse(new CampaignCharacterPredicate(CAMPAIGN, "mentor", CampaignCharacterPredicate.State.REPLACED).evaluate(context).matches(), "condition does not prepare replacement");
            helper.assertTrue(fixture.data().replacements().isEmpty(), "predicates never cast or commit");
            fixture.base.resolve(); successor.discard(); var lastVillager = fixture.villager();
            helper.assertValueEqual(fixture.base.resolve().entity(), lastVillager, "chain reaches the last role");
            fixture.reload(); context = fixture.context(player, lastVillager);
            for (var state : List.of(CampaignCharacterPredicate.State.SPEAKER, CampaignCharacterPredicate.State.AVAILABLE, CampaignCharacterPredicate.State.REPLACED)) {
                helper.assertTrue(new CampaignCharacterPredicate(CAMPAIGN, "mentor", state).evaluate(context).matches(), "condition follows committed chain: " + state);
            }
            helper.assertFalse(new CampaignCharacterPredicate(CAMPAIGN, "mentor", CampaignCharacterPredicate.State.LOST).evaluate(context).matches(), "active role has a living successor");
            helper.assertValueEqual(fixture.data().replacements().size(), 2, "each lost role has one receipt");
            helper.assertFalse(CampaignCharacterReplacements.authorization(fixture.base.server, CAMPAIGN, "mentor", mentor.getUUID(), lastVillager.getUUID()).isBlank(), "authorization spans the complete chain");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void activationCreatesAnOwnedSuccessorOnlyOnce(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var raw = fixture.base.raw(); character(raw, 1).addProperty("source", "owned_spawn"); character(raw, 1).addProperty("search_radius", 2); fixture.base.install(raw);
            var mentor = fixture.mentor(); mentor.discard();
            helper.getLevel().setBlockAndUpdate(fixture.base.origin.below(), Blocks.STONE.defaultBlockState());
            CampaignCharacterActivation.activate(fixture.base.server, List.of(UUID.randomUUID()));
            var first = fixture.base.resolve(); helper.assertTrue(first.available(), first.diagnostic()); fixture.villagers.add(first.entity());
            var receipt = fixture.data().replacements().values().iterator().next();
            fixture.reload(); CampaignCharacterActivation.activate(fixture.base.server, List.of(UUID.randomUUID()));
            helper.assertValueEqual(fixture.base.resolve().entity(), first.entity(), "activation never spawns a second successor");
            helper.assertValueEqual(fixture.data().replacements().get(receipt.key()), receipt, "replacement receipt is stable");
            helper.assertValueEqual(CampaignSpawnSavedData.get(helper.getLevel()).receipts().size(), 1, "successor has one independent spawn receipt");
            first.entity().discard();
            helper.assertFalse(fixture.base.resolve().available(), "lost successor with downed policy is not silently respawned");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void missingOrIncompatibleContentCannotAdvanceAReplacement(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.mentor().discard(); fixture.base.resolve(); var prepared = fixture.data().replacements().values().iterator().next(); fixture.villager();
            fixture.base.mode("sandbox"); helper.assertFalse(fixture.base.resolve().available(), "sandbox cannot replace campaign characters"); fixture.base.mode("campaign");
            CampaignResources.installForTests(fixture.base.server, new com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog(fixture.base.catalog, java.util.Map.of(), List.of()));
            helper.assertFalse(fixture.base.resolve().available(), "missing campaign remains dormant");
            var raw = fixture.base.raw(); raw.addProperty("revision", 2); fixture.base.install(raw);
            helper.assertFalse(fixture.base.resolve().available(), "incompatible content waits");
            helper.assertValueEqual(fixture.data().replacements().get(prepared.key()), prepared, "pending receipt remains intact");
            CampaignResources.installForTests(fixture.base.server, fixture.base.campaigns);
            helper.assertTrue(fixture.base.resolve().available(), "restored content resumes replacement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void activeQuestsAndSuspendedScenesContinueWithTheirSuccessor(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); var active = fixture.player(); var absent = fixture.player();
            for (var player : List.of(active, absent)) fixture.select(player, mentor, "visit", "offer");
            var run = fixture.progress(active).questRunId(); var sceneData = SceneSavedData.get(helper.getLevel());
            var validCharacters = fixture.data(); var future = new CompoundTag(); future.putInt("DataVersion", 99);
            fixture.characters(CampaignCharacterSavedData.load(future, fixture.base.disk.root));
            helper.assertFalse(CampaignQuestReconciliation.executionIssue(helper.getLevel(), active.getUUID(), fixture.quest(), fixture.progress(active)).isBlank(),
                    "unreadable character ownership blocks quest actions");
            fixture.characters(validCharacters);
            for (var scene : sceneData.active()) CampaignSceneCharacters.resolve(fixture.base.server, sceneData, scene, fixture.base.scene);
            mentor.discard(); var successor = fixture.villager(); fixture.base.resolve(); fixture.reload();
            helper.assertTrue(CampaignQuestReconciliation.reconcile(active, fixture.quest(), fixture.progress(active)), "online player rebinds");
            helper.assertValueEqual(fixture.progress(absent).startedVillagerId(), mentor.getUUID(), "absent progress is not fabricated");
            helper.assertTrue(CampaignQuestReconciliation.reconcile(absent, fixture.quest(), fixture.progress(absent)), "returning player reconciles independently");
            helper.assertValueEqual(fixture.progress(active).questRunId(), run, "replacement keeps the active quest run");
            helper.assertValueEqual(fixture.progress(active).providerRebindHistory().getFirst().previousProviderId(), mentor.getUUID(), "issuer history retains original identity");
            var successorContext = QuestExecutionContext.fromDialogueContext(fixture.context(active, successor), fixture.quest(), "replacement_test");
            helper.assertTrue(VillagerQuestProviderType.INSTANCE.matchesIssuerLock(successorContext, fixture.quest(), fixture.progress(active)), "saved replacement satisfies an otherwise strict issuer lock");
            var pending = SceneSavedData.get(helper.getLevel()).continuations().stream()
                    .filter(value -> value.playerId().equals(active.getUUID()) && !value.actions().isEmpty()
                            && value.actions().getFirst().kind() == com.jvn.villagerretaliation.action.VillagerActionDefinition.Kind.START_SCENE)
                    .findFirst().orElseThrow();
            pending.dialogueResume(new com.jvn.villagerretaliation.scene.runtime.SceneContinuation.DialogueResume(CAMPAIGN, "start", false, ""));
            helper.assertTrue(SceneContinuationService.hasPendingDialogue(fixture.context(active, successor)), "pending dialogue blocks successor responses until its actions finish");
            pending.dialogueResume(null);
            String suffix = "/0/greeting";
            helper.assertTrue(pending.sourcePointer().endsWith(suffix), "fixture owns a suspended greeting sequence: " + pending.sourcePointer());
            String source = pending.sourcePointer().substring(0, pending.sourcePointer().length() - suffix.length());
            var repeated = SceneContinuationService.run(fixture.context(active, successor), pending.actions(), java.util.Map.of(), () -> {}, source, null, CAMPAIGN, run);
            helper.assertValueEqual(repeated.continuationId(), pending.id(), "successor retries reuse the original continuation");
            fixture.advance();
            repeated = SceneContinuationService.run(fixture.context(active, successor), pending.actions(), java.util.Map.of(), () -> {}, source, null, CAMPAIGN, run);
            helper.assertValueEqual(repeated.continuationId(), pending.id(), "completed continuation cannot be recreated through a successor");
            helper.assertTrue(repeated.outcome().success(), "retry returns the saved completion");
            for (var player : List.of(active, absent)) {
                helper.assertValueEqual(fixture.progress(player).currentStage(), "report", "suspended actions finish with successor");
                helper.assertValueEqual(fixture.progress(player).providerRebindHistory().size(), 1, "reconciliation does not repeat history");
                fixture.select(player, successor, "report", "turn_in");
                helper.assertValueEqual(fixture.progress(player).completionCount(), 1, "successor completes the original quest");
            }
            var scenes = SceneSavedData.get(helper.getLevel());
            for (var scene : scenes.all()) for (String alias : List.of("guide", "issuer")) {
                var binding = scene.actorBindings().get(alias);
                helper.assertValueEqual(binding.entityId(), successor.getUUID(), "campaign and quest-provider actors follow successor");
                helper.assertValueEqual(binding.generation(), 2L, "scene binding records one replacement");
                helper.assertValueEqual(binding.replacementHistory().size(), 1, "scene history survives reload and retries");
            }
            helper.assertTrue(scenes.continuations().stream().allMatch(value -> value.completionReceipt()), "original-provider continuations settle");
            var late = fixture.player(); fixture.select(late, successor, "visit", "offer"); fixture.advance(); fixture.select(late, successor, "report", "turn_in");
            helper.assertValueEqual(fixture.progress(late).completionCount(), 1, "late player uses the current role");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void fixedSceneActorsAndFailedSceneSavesKeepTheOriginalBinding(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); var player = fixture.player(); fixture.select(player, mentor, "visit", "offer");
            var scenes = SceneSavedData.get(helper.getLevel()); var scene = scenes.active().getFirst();
            CampaignSceneCharacters.resolve(fixture.base.server, scenes, scene, fixture.base.scene);
            var original = scene.actorBindings().get("guide"); mentor.discard(); fixture.villager(); fixture.base.resolve();
            var raw = CampaignCharacterTestFixture.resource("quests/campaign_fixture/replacements/scenes/replacement_greeting.json");
            raw.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("replacement_policy", "fixed");
            var fixed = SceneCompiler.compile(SceneParser.parse(fixture.base.sceneId, raw).resource()).scene();
            helper.assertTrue(CampaignSceneCharacters.resolve(fixture.base.server, scenes, scene, fixed).contains("fixed actor"), "authored fixed policy blocks replacement");
            helper.assertValueEqual(scene.actorBindings().get("guide"), original, "fixed actor retains identity and history");
            var futureTag = new CompoundTag(); futureTag.putInt("DataVersion", Integer.MAX_VALUE);
            var future = SceneSavedData.load(futureTag, helper.getLevel().registryAccess());
            helper.assertFalse(CampaignSceneCharacters.resolve(fixture.base.server, future, scene, fixture.base.scene).isBlank(), "unsavable scene is unavailable");
            helper.assertValueEqual(scene.actorBindings().get("guide"), original, "failed write rolls back the replacement");
            helper.assertTrue(CampaignSceneCharacters.resolve(fixture.base.server, scenes, scene, fixture.base.scene).isBlank(), "retry persists the compatible binding");
            helper.assertValueEqual(SceneInstance.load(scene.save()).actorBindings().get("guide").generation(), 2L, "retry does not duplicate history");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_replacements")
    public static void interruptedConsentFollowsOnlyAnAuthorizedSuccessorAndRechecksPermissions(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor(); var player = fixture.player();
            var instance = CampaignSavedData.get(helper.getLevel()).instances().get(CAMPAIGN);
            var accepted = new CampaignQuestAcceptance(UUID.randomUUID(), instance.instanceId(), instance.fingerprint(), instance.worldRevision(),
                    helper.getLevel().getGameTime(), java.util.Map.of());
            var consent = new CampaignQuestConsent(player.getUUID(), mentor.getUUID(), java.util.Map.of(CAMPAIGN,
                    new CampaignQuestConsent.Entry(accepted, new CompoundTag()))).save();
            mentor.discard(); var successor = fixture.villager(); var context = fixture.context(player, successor);
            helper.assertFalse(CampaignQuestAcceptanceService.resumeAccess(context, consent).allowed(), "equivalent villager cannot inherit approval before a saved decision");
            helper.assertValueEqual(CampaignQuestAcceptanceService.remainingConsent(context, consent), consent, "unauthorized caller cannot rewrite approval identity");
            fixture.base.resolve(); fixture.reload();
            helper.assertTrue(CampaignQuestAcceptanceService.resumeAccess(context, consent).allowed(), "saved successor can resume approved offer");
            var transferred = CampaignQuestAcceptanceService.remainingConsent(context, consent);
            helper.assertValueEqual(CampaignQuestConsent.load(transferred).providerId(), successor.getUUID(), "remaining approval follows the authorized speaker");
            helper.assertValueEqual(CampaignQuestConsent.load(transferred).quests(), CampaignQuestConsent.load(consent).quests(), "handover preserves acceptance and progress snapshots");
            var unrelated = fixture.villager();
            helper.assertFalse(CampaignQuestAcceptanceService.resumeAccess(fixture.context(player, unrelated), consent).allowed(), "unrelated character cannot reuse approval");
            var leader = fixture.player();
            helper.assertFalse(CampaignQuestAcceptanceService.resumeAccess(fixture.context(leader, successor), consent).allowed(), "approval remains personal");
            helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.createParty(leader).success(), "leader creates party");
            try {
                var invitation = com.jvn.villagerretaliation.party.PartyService.sendInvitation(leader, player);
                helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.acceptInvitation(player, invitation.invitationId()).success(), "controller joins party");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, player.getUUID(), false);
                var denied = CampaignQuestAcceptanceService.resumeAccess(context, transferred);
                helper.assertTrue(denied.paused() && !denied.allowed(), "revoked privileges pause even a saved replacement");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, player.getUUID(), true);
                helper.assertTrue(CampaignQuestAcceptanceService.resumeAccess(context, transferred).allowed(), "restored authority resumes consent");
            } finally {
                com.jvn.villagerretaliation.party.PartyService.leaveParty(player);
                com.jvn.villagerretaliation.party.PartyService.leaveParty(leader);
            }
            fixture.select(player, successor, "visit", "offer");
            helper.assertFalse(CampaignQuestAcceptanceService.resumeAccess(context, transferred).allowed(), "changed quest progress still requires reviewing the offer");
        }
        helper.succeed();
    }

    private static JsonObject character(JsonObject root, int index) { return root.getAsJsonArray("characters").get(index).getAsJsonObject(); }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignCharacterTestFixture base;
        final List<Villager> villagers = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        Fixture(GameTestHelper helper) throws IOException { this.helper = helper; base = new CampaignCharacterTestFixture(helper, "replacements", "replacement_greeting"); }
        CampaignCharacterSavedData data() { return CampaignCharacterSavedData.get(helper.getLevel()); }
        void characters(CampaignCharacterSavedData data) { base.server.overworld().getDataStorage().set(CampaignCharacterSavedData.DATA_NAME, data); }
        com.jvn.villagerretaliation.quest.QuestDefinition quest() { return base.catalog.quest(CAMPAIGN).orElseThrow(); }
        VillagerQuestSavedData.QuestProgress progress(ServerPlayer player) { return VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), CAMPAIGN); }
        Villager villager() { var entity = helper.spawn(EntityType.VILLAGER, 1, 1, 1); entity.setNoAi(true); villagers.add(entity); return entity; }
        Villager mentor() { var entity = villager(); helper.assertValueEqual(base.resolve().entity(), entity, "fixture casts mentor"); return entity; }
        Villager restore(CompoundTag snapshot) { var entity = EntityType.VILLAGER.create(helper.getLevel()); entity.load(snapshot); helper.assertTrue(helper.getLevel().addFreshEntity(entity), "same identity reloads"); villagers.add(entity); return entity; }
        ServerPlayer player() { var player = helper.makeMockServerPlayerInLevel(); players.add(player); return player; }
        com.jvn.villagerretaliation.dialogue.DialogueContext context(ServerPlayer player, Villager villager) { return VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager); }
        void select(ServerPlayer player, Villager villager, String stage, String slot) {
            players.forEach(value -> VillagerConversationService.endForPlayer(value, false));
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            base.select(context(player, villager), stage, slot);
        }
        void reload() throws IOException {
            base.reload(); var storage = base.server.overworld().getDataStorage();
            storage.set("villagerretaliation_quests", VillagerQuestSavedData.load(VillagerQuestSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()));
            storage.set("villagerretaliation_scenes", SceneSavedData.load(SceneSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()));
        }
        void advance() {
            BuiltinSceneStepExecutors.register(); var scenes = SceneSavedData.get(helper.getLevel()); long now = helper.getLevel().getGameTime();
            for (int tick = 0; tick < 45; tick++) {
                for (var scene : List.copyOf(scenes.active())) com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.process(base.server, scenes, scene, base.scene, now + tick);
                SceneContinuationService.maintain(base.server, scenes);
                for (var player : players) { player.tickCount = tick; VillagerQuestService.onPlayerTick(player); }
            }
        }
        @Override public void close() throws IOException {
            players.forEach(player -> VillagerConversationService.endForPlayer(player, false));
            villagers.forEach(Entity::discard); DialogueTreeService.clearRuntimeState(); base.close();
        }
    }
}
