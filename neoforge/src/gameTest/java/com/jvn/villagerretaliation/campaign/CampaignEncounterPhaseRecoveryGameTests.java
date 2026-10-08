package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.quest.QuestFactReceipt;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterPhaseRecoveryGameTests {
    private CampaignEncounterPhaseRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void nativeReceiptSurvivesMissingAcknowledgementAndLaterFactChanges(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            var effect = effect(fixture);
            fixture.facts().applyCampaignFact(helper.getLevel(), effect, false);
            fixture.facts().applyCampaignFact(helper.getLevel(), new QuestFactReceipt(UUID.randomUUID(), UUID.randomUUID(),
                    List.of(QuestScopeKey.WORLD), null, "phase_state", "later"), false);
            fixture.base.block(); fixture.refresh();
            helper.assertTrue(fixture.base.scene().stepRecords().get("wait").chosenTransition().isBlank(), "failed acknowledgement stops the transition");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "failed acknowledgement stops the reward");
            fixture.base.restore(); fixture.base.reload(); reloadFacts(fixture); fixture.refresh();
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").orElse(""), "later", "native receipt preserves later story changes");
            helper.assertValueEqual(fixture.base.scene().stepRecords().get("wait").chosenTransition(), "done", "saved cursor resumes the original transition");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 5, "phase reward follows durable completion once");
            fixture.base.reload(); reloadFacts(fixture); fixture.refresh();
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").orElse(""), "later", "reload cannot replay the earlier assignment");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 5, "reload cannot duplicate the phase reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void missingNativeProofBlocksAnAcknowledgedPhase(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.defeat();
            helper.getLevel().getDataStorage().set(CampaignEncounterPhasePersistenceGameTests.Fixture.FACTS, new VillagerQuestFacts());
            var result = fixture.refresh();
            helper.assertValueEqual(result.status(), EncounterService.Status.WAITING, "older native facts require repair");
            helper.assertTrue(result.diagnostic().contains("older"), "conflicting saves have a useful diagnostic");
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "acknowledged effects cannot be recreated from an older native save");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void finalDefeatCompletesPhaseBeforeItsRewards(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper, template(root -> {
            root.getAsJsonArray("members").get(0).getAsJsonObject().addProperty("count", 1);
            root.getAsJsonArray("phases").get(0).getAsJsonObject().getAsJsonObject("trigger").addProperty("percentage", 0);
        }))) {
            fixture.blockFacts(); fixture.defeat();
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "last enemy defeat cannot bypass a pending phase");
            EncounterService.maintainCleanup(fixture.base.base.base.server, fixture.base.data());
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "maintenance cannot bypass the phase either");
            fixture.restoreFacts(); fixture.base.reload(); fixture.refresh();
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").orElse(""), "cleared", "final phase observes the completed encounter");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 5, "reward follows the final phase");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void completionCannotStartAnUntriggeredTimerLater(GameTestHelper helper) throws IOException {
        long before = helper.getLevel().getGameTime();
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper, template(root -> {
            root.getAsJsonArray("members").get(0).getAsJsonObject().addProperty("count", 1);
            var trigger = JsonParser.parseString("{\"type\":\"elapsed_time\",\"ticks\":100}").getAsJsonObject();
            root.getAsJsonArray("phases").get(0).getAsJsonObject().add("trigger", trigger);
        }))) {
            fixture.defeat();
            fixture.base.reload();
            ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(before + 200L);
            EncounterService.maintainCleanup(fixture.base.base.base.server, fixture.base.data());
            helper.assertTrue(fixture.base.encounter().phaseFireCounts().isEmpty(), "completion captures eligible triggers before time advances");
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "closed encounter cannot start a new timed story effect");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "closed encounter cannot earn a later timed reward");
        } finally { ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(before); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void disconnectedControllerPausesPhaseEffectsUntilControlReturns(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            var player = fixture.player();
            UUID conversation = fixture.base.scene().conversationEffect().getUUID("Session");
            fixture.base.base.base.server.getPlayerList().remove(player);
            fixture.defeat();
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "offline controller cannot advance phase effects");
            helper.assertValueEqual(fixture.base.encounter().phaseFireCounts().getOrDefault("turning_point", 0), 1, "observed trigger is retained while authority is unavailable");
            fixture.base.reload();
            var returned = fixture.base.base.reconnect(player);
            fixture.base.base.intent(returned, ConversationService.view(returned, conversation), ConversationSession.Intent.TAKE_CONTROL, "");
            fixture.refresh();
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").orElse(""), "cleared", "authorized controller resumes the retained phase");
            helper.assertValueEqual(returned.getInventory().countItem(Items.EMERALD), 5, "reward follows the resumed phase once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void changedPhaseMeaningWaitsForOriginalContent(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            installTemplate(fixture, template(root -> root.getAsJsonArray("phases").get(0).getAsJsonObject()
                    .getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("value", "different")));
            helper.assertTrue(fixture.refresh().diagnostic().contains("changed"), "pending phase cannot adopt different meaning");
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "changed content cannot apply an effect");
            installTemplate(fixture, CampaignEncounterPhasePersistenceGameTests.Fixture.TEMPLATE);
            fixture.refresh();
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").orElse(""), "cleared", "restoring content resumes the original action");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void legacyCompletedPhaseDoesNotReplay(GameTestHelper helper) throws IOException { legacy(helper, true); }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void legacyAmbiguousPhaseWaitsForRepair(GameTestHelper helper) throws IOException { legacy(helper, false); }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void unsupportedPhaseHistoryIsPreservedWithItsEncounter(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            var snapshot = fixture.base.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var history = savedEncounter(fixture, snapshot).getCompound("PhaseHistory");
            history.putInt("Version", 99);
            history.putString("FutureField", "preserve");
            install(fixture, snapshot);
            helper.assertValueEqual(fixture.refresh().status(), EncounterService.Status.WAITING, "future phase history blocks execution");
            helper.assertValueEqual(fixture.base.encounter().save().getCompound("PhaseHistory"), history, "unsupported history remains repairable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void unsupportedNativeFactHistoryCannotBeOverwritten(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            var snapshot = fixture.facts().save(new CompoundTag(), helper.getLevel().registryAccess());
            var history = snapshot.getCompound("CampaignEffects");
            history.putInt("Version", 99);
            history.putString("FutureField", "preserve");
            helper.getLevel().getDataStorage().set(CampaignEncounterPhasePersistenceGameTests.Fixture.FACTS, VillagerQuestFacts.load(snapshot, helper.getLevel().registryAccess()));
            fixture.defeat();
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "unsupported native history blocks effects");
            helper.assertValueEqual(fixture.facts().save(new CompoundTag(), helper.getLevel().registryAccess()).getCompound("CampaignEffects"), history, "unsupported native history remains unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void nativeReceiptCoversAllScopesAndPreservesUnrelatedFacts(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            UUID offline = UUID.randomUUID();
            var quest = ResourceLocation.parse("campaign_fixture:characters");
            var scopes = List.of(QuestScopeKey.WORLD, QuestScopeKey.player(fixture.player().getUUID()), QuestScopeKey.player(offline), QuestScopeKey.quest(offline, quest));
            var unrelated = ResourceLocation.parse("campaign_fixture:unrelated");
            fixture.facts().setTag(QuestScopeKey.WORLD, unrelated);
            fixture.facts().setVariable(QuestScopeKey.WORLD, "unrelated", "keep");
            fixture.facts().addCounter(QuestScopeKey.WORLD, "counter", 7);
            var effect = new QuestFactReceipt(UUID.randomUUID(), UUID.randomUUID(), scopes, null, "atomic", "ready");
            fixture.blockFacts();
            try { fixture.facts().applyCampaignFact(helper.getLevel(), effect, false); helper.fail("blocked native save accepted the fact"); }
            catch (IOException expected) { }
            for (var scope : scopes) helper.assertFalse(fixture.facts().variable(scope, "atomic").isPresent(), "failed transaction publishes no scope");
            fixture.restoreFacts(); fixture.facts().applyCampaignFact(helper.getLevel(), effect, false); reloadFacts(fixture);
            for (var scope : scopes) helper.assertValueEqual(fixture.facts().variable(scope, "atomic").orElse(""), "ready", "all scopes share the saved receipt");
            helper.assertTrue(fixture.facts().hasTag(QuestScopeKey.WORLD, unrelated), "unrelated tags survive");
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "unrelated").orElse(""), "keep", "unrelated variables survive");
            helper.assertValueEqual(fixture.facts().counter(QuestScopeKey.WORLD, "counter"), 7, "unrelated counters survive");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void pendingTransitionCannotTakeOverAnotherSceneStep(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            fixture.base.scene().advance("done", helper.getLevel().getGameTime());
            var result = fixture.refresh();
            helper.assertValueEqual(result.status(), EncounterService.Status.WAITING, "pending transition requires its original step");
            helper.assertTrue(fixture.base.scene().stepRecords().get("wait").chosenTransition().isBlank(), "stale transition cannot modify the old step after advancement");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "stale transition cannot finish its phase reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void conflictingTransitionFailsWithoutGrantingItsPhaseReward(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.base.scene().stepRecords().get("wait").chooseTransition("start");
            fixture.defeat();
            helper.assertValueEqual(fixture.base.encounter().state(), EncounterInstance.EncounterState.FAILED, "conflicting authored transitions fail explicitly");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "required failure prevents the phase reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void repeatablePhasesKeepSeparateReceiptsWithoutReplayingOldRuns(GameTestHelper helper) throws IOException {
        long before = helper.getLevel().getGameTime();
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper, template(root -> {
            var phase = root.getAsJsonArray("phases").get(0).getAsJsonObject();
            phase.getAsJsonArray("actions").remove(1);
            phase.addProperty("repeatable", true);
            phase.addProperty("repeat_interval_ticks", 1);
            phase.addProperty("max_fires", 2);
        }))) {
            fixture.defeat();
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 5, "first phase run earns one reward");
            ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(before + 1L);
            fixture.refresh();
            helper.assertValueEqual(fixture.base.encounter().phaseFireCounts().get("turning_point"), 2, "second trigger has a separate ordinal");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 10, "each completed ordinal earns its authored reward once");
            fixture.base.reload(); reloadFacts(fixture); fixture.refresh();
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 10, "reloading cannot replay either phase run");
        } finally { ((net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData()).setGameTime(before); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void phaseRecoveryPreservesExistingSceneStepIdentifiers(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            CampaignConversationSceneHandoverGameTests.scene(fixture.base.base, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"start","actors":[],"steps":[
                      {"id":"start","type":"villagerretaliation:start_encounter","data":{"template":"campaign_fixture:persistent_encounter"},"next":"wait"},
                      {"id":"wait","type":"villagerretaliation:wait_encounter","data":{"encounter_step":"start"},"next":"done"},
                      {"id":"Wait for allies","type":"villagerretaliation:wait_encounter","data":{"encounter_step":"start"},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            fixture.base.scene().advance("Wait for allies", helper.getLevel().getGameTime());
            fixture.base.scene().currentRecord(ResourceLocation.parse("villagerretaliation:wait_encounter"));
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            fixture.base.reload(); fixture.refresh();
            helper.assertValueEqual(fixture.base.scene().stepRecords().get("Wait for allies").chosenTransition(), "done", "phase source keeps the existing scene identifier contract");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 5, "recovered phase completes with the original source identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void missingCurrentPhaseHistoryCannotBecomeEmptyHistory(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            var snapshot = fixture.base.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            savedEncounter(fixture, snapshot).remove("PhaseHistory");
            install(fixture, snapshot);
            helper.assertTrue(fixture.refresh().diagnostic().contains("missing"), "missing current history requires repair");
            helper.assertFalse(fixture.base.encounter().save().contains("PhaseHistory"), "saving cannot erase the distinction between missing and empty history");
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "missing history cannot authorize a new effect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void malformedPhaseOwnershipPreservesItsEncounter(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            var snapshot = fixture.base.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var history = savedEncounter(fixture, snapshot).getCompound("PhaseHistory");
            history.getList("Runs", Tag.TAG_COMPOUND).getCompound(0).putString("Quest", "invalid:Broken identity");
            install(fixture, snapshot);
            helper.assertTrue(fixture.base.encounter() != null, "invalid phase ownership cannot discard its encounter");
            helper.assertValueEqual(fixture.refresh().status(), EncounterService.Status.WAITING, "invalid ownership blocks phase effects");
            helper.assertValueEqual(fixture.base.encounter().save().getCompound("PhaseHistory"), history, "invalid ownership remains available for repair");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void rejectedPhaseActionCannotRetryAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            String operation = fixture.base.sceneId + "/encounter/" + fixture.base.encounter().id() + "/phase/turning_point/1/action/fact";
            fixture.base.scene().receipts().get(operation).recordAction(
                    com.jvn.villagerretaliation.action.ActionResult.failed("permanent phase failure", java.util.Set.of()), helper.getLevel().getGameTime());
            fixture.base.data().persistCampaignHandoff(helper.getLevel());
            fixture.base.reload(); fixture.refresh();
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "a saved permanent failure cannot apply on retry");
            helper.assertValueEqual(fixture.base.encounter().state(), EncounterInstance.EncounterState.FAILED, "saved failure fails its encounter");
            helper.assertTrue(fixture.base.scene().stepRecords().get("wait").chosenTransition().isBlank(), "saved failure prevents later transitions");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "saved failure prevents phase rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_recovery")
    public static void cancelledEncounterCannotResumePendingPhase(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper, template(root ->
                root.add("environment", JsonParser.parseString("""
                        {"temporary_blocks":[{"id":"barrier","block":"minecraft:glass","offset_x":2,"offset_y":2}]}
                        """))))) {
            helper.assertTrue(helper.getLevel().getBlockState(fixture.base.barrier()).is(net.minecraft.world.level.block.Blocks.GLASS), "encounter has an owned barrier");
            fixture.blockFacts(); fixture.defeat(); fixture.restoreFacts();
            fixture.base.encounter().cancel();
            fixture.base.data().persistCampaignHandoff(helper.getLevel());
            fixture.base.reload(); fixture.refresh();
            EncounterService.maintainCleanup(fixture.base.base.base.server, fixture.base.data());
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "cancellation prevents pending facts from committing");
            helper.assertValueEqual(fixture.base.encounter().state(), EncounterInstance.EncounterState.CANCELLED, "maintenance preserves cancellation");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.base.barrier()).isAir(), "pending phases cannot block cancellation cleanup");
            helper.assertTrue(fixture.base.scene().stepRecords().get("wait").chosenTransition().isBlank(), "cancellation prevents the pending transition");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "cancellation prevents phase rewards");
        }
        helper.succeed();
    }

    private static void legacy(GameTestHelper helper, boolean completed) throws IOException {
        try (var fixture = new CampaignEncounterPhasePersistenceGameTests.Fixture(helper)) {
            fixture.base.encounter().startPhaseFire("turning_point", 0);
            String operation = fixture.base.sceneId + "/encounter/" + fixture.base.encounter().id() + "/phase/turning_point/1";
            var receipt = fixture.base.scene().prepareReceipt(operation, SceneOperationReceipt.Kind.ENCOUNTER_PHASE, helper.getLevel().getGameTime());
            if (completed) receipt.completed(helper.getLevel().getGameTime(), "legacy phase");
            var snapshot = fixture.base.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            snapshot.putInt("DataVersion", 21);
            savedEncounter(fixture, snapshot).remove("PhaseHistory");
            install(fixture, snapshot);
            var result = fixture.refresh();
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "legacy phase cannot replay its fact");
            if (!completed) helper.assertTrue(result.status() == EncounterService.Status.WAITING && result.diagnostic().contains("legacy"), "ambiguous legacy action waits for repair");
            helper.assertValueEqual(fixture.base.scene().receipts().get(operation).state(), completed
                    ? SceneOperationReceipt.ReceiptState.COMPLETED : SceneOperationReceipt.ReceiptState.PREPARED, "migration preserves old completion evidence");
        }
        helper.succeed();
    }

    private static QuestFactReceipt effect(CampaignEncounterPhasePersistenceGameTests.Fixture fixture) {
        var run = fixture.base.encounter().save().getCompound("PhaseHistory").getList("Runs", Tag.TAG_COMPOUND).getCompound(0);
        String operation = fixture.base.sceneId + "/encounter/" + fixture.base.encounter().id() + "/phase/turning_point/1/action/fact";
        UUID id = UUID.nameUUIDFromBytes(("encounter-phase-fact/" + operation).getBytes(StandardCharsets.UTF_8));
        UUID token = UUID.nameUUIDFromBytes((run.getUUID("Token") + "/action/fact").getBytes(StandardCharsets.UTF_8));
        return new QuestFactReceipt(id, token, List.of(QuestScopeKey.WORLD), null, "phase_state", "cleared");
    }
    private static String template(Consumer<JsonObject> change) {
        var root = JsonParser.parseString(CampaignEncounterPhasePersistenceGameTests.Fixture.TEMPLATE).getAsJsonObject();
        change.accept(root);
        return root.toString();
    }
    private static void reloadFacts(CampaignEncounterPhasePersistenceGameTests.Fixture fixture) throws IOException {
        fixture.helper.getLevel().getDataStorage().set(CampaignEncounterPhasePersistenceGameTests.Fixture.FACTS,
                VillagerQuestFacts.load(NbtIo.readCompressed(fixture.path(), NbtAccounter.unlimitedHeap()).getCompound("data"), fixture.helper.getLevel().registryAccess()));
    }
    private static CompoundTag savedEncounter(CampaignEncounterPhasePersistenceGameTests.Fixture fixture, CompoundTag snapshot) {
        return snapshot.getList("Encounters", Tag.TAG_COMPOUND).stream().map(value -> (CompoundTag) value)
                .filter(value -> value.getUUID("Id").equals(fixture.base.encounter().id())).findFirst().orElseThrow();
    }
    private static void install(CampaignEncounterPhasePersistenceGameTests.Fixture fixture, CompoundTag snapshot) {
        fixture.helper.getLevel().getDataStorage().set("villagerretaliation_scenes", SceneSavedData.load(snapshot, fixture.helper.getLevel().registryAccess()));
    }
    private static void installTemplate(CampaignEncounterPhasePersistenceGameTests.Fixture fixture, String json) {
        var errors = new java.util.ArrayList<String>();
        var replacement = com.jvn.villagerretaliation.scene.encounter.EncounterResources.parse(fixture.base.template.id(), JsonParser.parseString(json).getAsJsonObject(), errors);
        fixture.helper.assertTrue(replacement != null && errors.isEmpty(), "replacement phase template parses: " + errors);
        var server = fixture.base.base.base.server;
        var old = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.current(server);
        var campaigns = com.jvn.villagerretaliation.campaign.content.CampaignResources.current(server).campaigns();
        var catalog = new com.jvn.villagerretaliation.quest.content.QuestContentCatalog(old.generation() + 1, old.compiledQuestCatalog(), old.dialogueCatalog(), old.quests(),
                old.objectiveEventQuestIds(), old.factQuestIds(), old.memoryEventQuestIds(), old.exclusiveGroupQuestIds(), old.triggerEventQuestIds(),
                old.scenes(), java.util.Map.of(replacement.id(), replacement), old.pools(), old.bundles(), old.localization(), old.rewards(), old.conversations());
        com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.installForTests(server, catalog, fixture.base.base.base.report);
        com.jvn.villagerretaliation.campaign.content.CampaignResources.installForTests(server,
                new com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog(catalog, campaigns, List.of()));
    }
}
