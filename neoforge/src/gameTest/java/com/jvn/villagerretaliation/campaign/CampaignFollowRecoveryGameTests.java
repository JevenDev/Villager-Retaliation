package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneStepRecord;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignFollowRecoveryGameTests {
    private CampaignFollowRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void compiledFollowSettingsRemainAuthoritative(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var step = fixture.base.scene.steps().get("walk");
            step.parameters().addProperty("actor", "missing");
            step.parameters().addProperty("target_actor", "missing");
            step.parameters().addProperty("arrival_distance", 1000);
            step.parameters().addProperty("follow_distance", 1000);
            step.parameters().addProperty("timeout_ticks", 1);
            step.parameters().addProperty("speed", 100);
            step.parameters().addProperty("allow_teleport", true);
            step.parameters().addProperty("path_failure_policy", "skip");
            var player = fixture.player();
            fixture.accept(player);
            fixture.step();
            helper.assertValueEqual(fixture.journey().followDistance(), 4.0, "compiled radius controls enrollment");
            long deadline = Long.parseLong(fixture.scene().stepRecords().get("walk").durableValues().get("deadline"));
            helper.assertTrue(deadline > fixture.time + 100, "raw JSON cannot shorten the compiled deadline");
            fixture.step();
            helper.assertFalse(fixture.complete(player), "raw arrival radius cannot complete an untraveled journey");
            helper.assertFalse(fixture.scene().state().terminal(), "raw skip policy cannot finish the scene");
            fixture.finish(player);
            helper.assertTrue(fixture.complete(player), "compiled actor and destination remain playable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void missingContentSandboxAndChangedCampaignsPauseTheJourney(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step(); fixture.walk(1, player);
            var prior = fixture.journey();
            fixture.base.mode("sandbox");
            fixture.step();
            helper.assertTrue(fixture.scene().campaignPaused(), "Sandbox cannot advance campaign movement");
            helper.assertTrue(fixture.mentor.getNavigation().isDone(), "mode gate stops movement");
            fixture.base.mode("campaign");
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(fixture.base.catalog, Map.of(), List.of()));
            fixture.step();
            helper.assertTrue(fixture.scene().campaignPaused(), "missing campaign stays dormant");
            var changed = fixture.base.raw();
            changed.getAsJsonArray("locations").get(1).getAsJsonObject().getAsJsonObject("origin").addProperty("x", fixture.destination.getX() + 1);
            fixture.base.install(changed);
            fixture.step();
            helper.assertTrue(fixture.scene().campaignPaused(), "incompatible fingerprint blocks movement");
            helper.assertValueEqual(fixture.journey(), prior, "blocked content cannot change observed participation");
            helper.assertFalse(fixture.complete(player), "blocked journey gives no credit");
            CampaignResources.installForTests(fixture.base.server, fixture.base.campaigns);
            fixture.step();
            fixture.finish(player);
            helper.assertTrue(fixture.complete(player), "restoring compatible content resumes the same journey");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void everyEnrolledPlayerMustRemainEligibleAndReachTheDestination(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper, true)) {
            var leader = fixture.player();
            var member = fixture.player();
            fixture.party(leader, member);
            fixture.accept(leader); fixture.accept(member); fixture.launch(leader);
            fixture.step(); fixture.walk(1, leader, member);
            var prior = fixture.journey();
            member.setGameMode(GameType.SPECTATOR);
            fixture.step();
            helper.assertTrue(fixture.scene().campaignPaused() && fixture.mentor.getNavigation().isDone(), "spectator participation pauses the guide");
            helper.assertValueEqual(fixture.journey(), prior, "spectator time cannot contribute to travel");
            member.setGameMode(GameType.SURVIVAL);
            fixture.step();
            for (int x = 2; x <= 4; x++) fixture.walk(x, leader, member);
            member.setPos(Vec3.atBottomCenterOf(fixture.base.origin).add(3, 0, 0.5));
            fixture.walk(5, leader);
            helper.assertFalse(fixture.complete(leader) || fixture.complete(member), "the leader cannot finish before the enrolled member arrives");
            member.setPos(fixture.mentor.position().add(0, 0, 0.5));
            for (int tick = 0; tick < 5; tick++) fixture.step();
            helper.assertTrue(fixture.complete(leader) && fixture.complete(member), "arrival is committed when both followers reach the destination");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void aDifferentQuestRunCannotContinueOrBorrowTheJourney(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step(); fixture.walk(1, player);
            var progress = fixture.objectiveContext(player).progress();
            var original = progress.questRunId();
            progress.start(fixture.mentor.getUUID(), player.serverLevel().dimension(), fixture.mentor.blockPosition(), fixture.time);
            progress.beginRun(player.getUUID(), fixture.base.campaignId, null);
            progress.setCurrentStage("follow");
            helper.assertFalse(original.equals(progress.questRunId()), "fixture starts another quest run");
            var context = fixture.context();
            var result = SceneStepExecutors.get(context.step().type()).orElseThrow().apply(context);
            helper.assertValueEqual(result.outcome(), SceneStepResult.Outcome.FAIL, "changed personal objective cancels the old journey");
            helper.assertValueEqual(fixture.journey().state(), CampaignFollowJourney.State.CANCELLED, "cancellation is durable before failure transition");
            helper.assertFalse(fixture.complete(player), "new run cannot borrow prior travel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void cleanupStorageFailureKeepsTheReservationAndStopsMovement(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step(); fixture.walk(1, player);
            SceneTransitionService.cancel(fixture.scenes(), fixture.scene(), fixture.base.scene, "fixture_cancel", "cancel this journey", fixture.time++);
            fixture.block(fixture.journeyPath());
            try {
                helper.assertFalse(CampaignFollowService.cleanup(fixture.base.server, fixture.scene()).isBlank(), "failed cleanup reports repairable storage");
                helper.assertTrue(fixture.mentor.getNavigation().isDone(), "cleanup failure stops the guide");
                helper.assertValueEqual(fixture.journey().state(), CampaignFollowJourney.State.ACTIVE, "reservation is retained until cancellation is durable");
            } finally { fixture.restore(fixture.journeyPath()); }
            helper.assertTrue(CampaignFollowService.cleanup(fixture.base.server, fixture.scene()).isBlank(), "storage repair saves cancellation");
            var cancelled = fixture.journey();
            helper.assertValueEqual(cancelled.state(), CampaignFollowJourney.State.CANCELLED, "cancelled journey releases its actor");
            CampaignFollowService.cleanup(fixture.base.server, fixture.scene());
            helper.assertValueEqual(fixture.journey(), cancelled, "repeated cleanup is idempotent");
            helper.assertFalse(fixture.complete(player), "cancelled journey cannot award arrival credit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void activeTimeoutCancelsWithoutGrantingArrival(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step(); fixture.walk(1, player);
            fixture.time += 1200;
            fixture.step();
            helper.assertValueEqual(fixture.journey().state(), CampaignFollowJourney.State.CANCELLED, "active travel time still respects the deadline");
            helper.assertTrue(fixture.mentor.getNavigation().isDone(), "timeout stops native movement");
            helper.assertFalse(fixture.complete(player), "timeout cannot substitute for arrival");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void changedSavedMovementDestinationBlocksBeforeNavigation(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step();
            var context = fixture.context();
            context.record().putDurableValue("destination_x", Integer.toString(fixture.destination.getX() + 4));
            fixture.scenes().persistCampaignHandoff(helper.getLevel());
            fixture.reload();
            fixture.step();
            helper.assertTrue(fixture.scene().diagnostic().contains("destination"), "changed movement record is diagnosed after reload");
            helper.assertTrue(fixture.mentor.getNavigation().isDone(), "untrusted saved target cannot move the guide");
            helper.assertFalse(fixture.complete(player), "mismatched target cannot grant completion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void partialMovementPreparationAndCompetingMovesCannotAdvance(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step();
            var original = fixture.context();
            var context = new SceneExecutionContext(original.server(), original.repository(), original.instance(), original.definition(),
                    original.step(), new SceneStepRecord(original.step().id(), original.step().type()), fixture.time++, true);
            context.record().putDurableValue("deadline", Long.toString(fixture.time + 1200));
            var follow = SceneStepExecutors.get(context.step().type()).orElseThrow();
            var prepared = follow.prepare(context);
            helper.assertValueEqual(prepared.outcome(), SceneStepResult.Outcome.BLOCK, "partial saved movement is diagnosed");
            helper.assertTrue(fixture.mentor.getNavigation().isDone(), "partial preparation cannot navigate");
            var moveType = ResourceLocation.parse("villagerretaliation:move_actor");
            var parameters = new com.google.gson.JsonObject();
            parameters.addProperty("actor", "guide"); parameters.addProperty("target_actor", "destination");
            var step = new CompiledScene.CompiledStep("competing_move", moveType, List.of("guide", "destination"), parameters, Map.of(), "", false, false);
            var movementContext = new SceneExecutionContext(fixture.base.server, fixture.scenes(), fixture.scene(), fixture.base.scene,
                    step, new SceneStepRecord(step.id(), step.type()), fixture.time++, true);
            var movement = SceneStepExecutors.get(moveType).orElseThrow();
            helper.assertValueEqual(movement.prepare(movementContext).outcome(), SceneStepResult.Outcome.READY, "ordinary move resolves its target");
            var result = movement.apply(movementContext);
            helper.assertValueEqual(result.outcome(), SceneStepResult.Outcome.WAIT, "other movement cannot steal a reserved guide");
            helper.assertTrue(result.diagnostic().contains("reserved"), "reservation explains the blocked move");
            helper.assertFalse(fixture.complete(player), "neither partial nor competing movement gives credit");
        }
        helper.succeed();
    }
}
