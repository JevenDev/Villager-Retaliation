package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignFollowGameTests {
    private CampaignFollowGameTests() { }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void accompaniedArrivalSurvivesReloadAndCompletesOnlyItsObjective(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            fixture.step();
            helper.assertTrue(fixture.journey() != null, "enrollment is saved before movement");
            helper.assertFalse(fixture.complete(player), "starting the journey gives no arrival credit");
            fixture.walk(1, player);
            fixture.scenes().persistCampaignHandoff(helper.getLevel());
            fixture.reload();
            fixture.finish(player);
            helper.assertTrue(fixture.complete(player), "accompanied arrival finishes the objective");
            var completed = fixture.journey();
            fixture.reload();
            for (int tick = 0; tick < 5; tick++) fixture.step();
            helper.assertValueEqual(fixture.journey(), completed, "older scene cursor cannot repeat committed arrival");
            helper.assertTrue(fixture.complete(player), "personal receipt survives disk reload");
            var context = fixture.objectiveContext(player);
            var objective = context.definition().objectives().getFirst();
            helper.assertFalse(CampaignFollowService.completed(context, "follow.other", (CampaignStoryObjective.Follow) objective.story()),
                    "another objective cannot borrow this journey");
            var completions = new ArrayList<String>();
            helper.assertTrue(QuestObjectiveDispatchService.poll(context, value -> completions.add(value.id())), "normal quest polling applies the receipt");
            helper.assertFalse(QuestObjectiveDispatchService.poll(context, value -> completions.add(value.id())), "repeat polling is idempotent");
            helper.assertValueEqual(completions, List.of("follow.mentor"), "only the enrolled objective completes");
            fixture.mentor.discard();
            helper.assertTrue(fixture.complete(player), "saved arrival survives later loss of its guide");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void nativeNavigationCanCarryTheAuthoredJourney(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            fixture.step();
            var start = fixture.mentor.position();
            fixture.mentor.setNoAi(false);
            for (int tick = 0; tick < 240 && !fixture.scene().state().terminal(); tick++) {
                player.setPos(fixture.mentor.position().add(0, 0, 0.5));
                fixture.step();
                fixture.mentor.tick();
            }
            helper.assertTrue(fixture.mentor.position().distanceTo(start) > 2, "native pathfinding moves the guide");
            helper.assertValueEqual(fixture.scene().completionResult(), SceneInstance.CompletionResult.SUCCESS,
                    "native navigation reaches the authored destination: " + fixture.scene().diagnostic());
            helper.assertTrue(fixture.complete(player), "actual navigation produces the personal arrival receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void sharedJourneysCreditOnlyPlayersEnrolledAtTheStart(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper, true)) {
            var leader = fixture.player();
            var member = fixture.player();
            var late = fixture.player();
            fixture.party(leader, member);
            var invitation = PartyService.sendInvitation(leader, late);
            helper.assertTrue(PartyService.acceptInvitation(late, invitation.invitationId()).success(), "late player joins the party");
            fixture.accept(leader); fixture.accept(member); fixture.accept(late);
            late.setPos(Vec3.atBottomCenterOf(fixture.destination));
            fixture.launch(leader);
            fixture.step();
            helper.assertValueEqual(fixture.journey().participants().size(), 2, "players already at the destination are not enrolled");
            fixture.walk(1, leader, member);
            late.setPos(fixture.mentor.position());
            fixture.step();
            fixture.finish(leader, member);
            helper.assertTrue(fixture.complete(leader) && fixture.complete(member), "both actual followers receive personal credit");
            helper.assertFalse(fixture.complete(late), "joining the route late cannot backfill its start");
            fixture.reload();
            helper.assertFalse(fixture.complete(late), "reload cannot infer credit from scene or party membership");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void aDisconnectedFollowerPausesMovementAndCanResumeTheSameJourney(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step(); fixture.walk(1, player);
            fixture.base.server.getPlayerList().remove(player);
            fixture.step();
            helper.assertTrue(fixture.scene().campaignPaused(), "disconnected owner pauses the scene");
            helper.assertTrue(fixture.mentor.getNavigation().isDone(), "paused journey stops native movement");
            var prior = fixture.journey();
            fixture.scenes().persistCampaignHandoff(helper.getLevel());
            fixture.reload();
            fixture.time += 1600;
            fixture.step();
            helper.assertValueEqual(fixture.journey(), prior, "offline time produces no observations");
            var returned = fixture.reconnect(player);
            fixture.step();
            fixture.finish(returned);
            helper.assertTrue(fixture.complete(returned), "same player and quest run resume their journey");
            helper.assertValueEqual(fixture.journey().participants().size(), 1, "reconnect keeps the original enrollment");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void walkingAwayWaitsForTheFollowerAndDestinationTeleportationGivesNoCredit(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step(); fixture.walk(1, player);
            player.setPos(player.position().add(40, 0, 0));
            fixture.step();
            helper.assertTrue(fixture.scene().campaignPaused() && fixture.mentor.getNavigation().isDone(), "guide waits for an absent follower");
            helper.assertFalse(fixture.complete(player), "absence gives no completion");
            player.setPos(fixture.mentor.position().add(0, 0, 0.5));
            fixture.step();
            fixture.mentor.setPos(Vec3.atBottomCenterOf(fixture.destination));
            player.setPos(fixture.mentor.position());
            fixture.step();
            helper.assertFalse(fixture.complete(player), "an unobserved jump to the destination does not count as following");
            helper.assertTrue(fixture.scene().diagnostic().contains("observed route"), "interrupted route has a repair diagnostic");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void failedJourneyPreparationAndArrivalWritesCannotExposeCredit(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            fixture.block(fixture.journeyPath());
            try {
                fixture.step();
                helper.assertTrue(fixture.journeys().journeys().isEmpty(), "failed preparation is not published");
                helper.assertTrue(fixture.mentor.getNavigation().isDone(), "no movement before durable enrollment");
            } finally { fixture.restore(fixture.journeyPath()); }
            fixture.step();
            for (int x = 1; x <= 4; x++) fixture.walk(x, player);
            var prior = fixture.journey();
            fixture.block(fixture.journeyPath());
            try {
                fixture.walk(5, player);
                helper.assertValueEqual(fixture.journey(), prior, "failed arrival write keeps the last committed frame");
                helper.assertFalse(fixture.complete(player), "unsaved arrival gives no credit");
                helper.assertTrue(fixture.mentor.getNavigation().isDone(), "storage failure stops the guide");
            } finally { fixture.restore(fixture.journeyPath()); }
            for (int tick = 0; tick < 5; tick++) fixture.step();
            helper.assertTrue(fixture.complete(player), "repair saves the arrival once");
            fixture.reload();
            helper.assertTrue(fixture.complete(player), "repaired receipt is durable");
            helper.assertValueEqual(fixture.journeys().journeys().size(), 1, "retries retain one journey identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void preparedSceneReloadKeepsItsMovementDeadline(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            fixture.step();
            String deadline = fixture.scene().stepRecords().get("walk").durableValues().get("deadline");
            fixture.reload();
            fixture.time += 30;
            fixture.step();
            helper.assertValueEqual(fixture.scene().stepRecords().get("walk").durableValues().get("deadline"), deadline,
                    "reloading saved preparation does not reset its deadline");
            fixture.finish(player);
            helper.assertTrue(fixture.complete(player), "prepared journey remains playable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void scenePreparationFailureStopsBeforeNavigation(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            fixture.block(fixture.scenePath());
            try {
                fixture.step();
                helper.assertTrue(fixture.journey() != null, "personal enrollment is durable first");
                helper.assertTrue(fixture.scene().campaignPaused(), "failed scene preparation pauses");
                helper.assertTrue(fixture.mentor.getNavigation().isDone(), "guide cannot move before its scene checkpoint");
                helper.assertFalse(fixture.complete(player), "preparation alone is not arrival");
            } finally { fixture.restore(fixture.scenePath()); }
            fixture.step();
            fixture.finish(player);
            helper.assertTrue(fixture.complete(player), "storage repair resumes the prepared journey");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void competingJourneysWaitAndCancellationReleasesTheGuide(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var first = fixture.player();
            var second = fixture.player();
            fixture.accept(first); fixture.step(); fixture.walk(1, first);
            var original = fixture.scene();
            fixture.accept(second); fixture.step();
            helper.assertTrue(fixture.journey() == null, "another journey cannot reserve the same guide");
            helper.assertTrue(fixture.scene().campaignPaused(), "competing request waits with a diagnostic");
            SceneTransitionService.cancel(fixture.scenes(), original, fixture.base.scene, "fixture_cancel", "cancel the first walk", fixture.time++);
            CampaignFollowService.maintain(fixture.base.server, fixture.scenes());
            helper.assertTrue(fixture.mentor.getNavigation().isDone(), "cancellation immediately stops the guide");
            helper.assertTrue(CampaignFollowService.cleanup(fixture.base.server, original).isBlank(), "cancellation is durably recorded");
            second.setPos(fixture.mentor.position().add(0, 0, 0.5));
            fixture.step();
            helper.assertTrue(fixture.journey() != null, "next journey can reserve the released guide");
            fixture.finish(second);
            helper.assertFalse(fixture.complete(first), "cancelled follower gets no arrival credit");
            helper.assertTrue(fixture.complete(second), "new journey can finish");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow")
    public static void malformedAndFutureJourneySavesRemainRepairable(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player); fixture.step();
            var original = fixture.saved();
            var incomplete = original.copy();
            incomplete.getList("Journeys", Tag.TAG_COMPOUND).getCompound(0).putString("State", "COMPLETED");
            var missing = original.copy();
            missing.getList("Journeys", Tag.TAG_COMPOUND).getCompound(0).remove("Actor");
            var future = original.copy(); future.putInt("DataVersion", 2);
            var duplicate = original.copy();
            duplicate.getList("Journeys", Tag.TAG_COMPOUND).add(duplicate.getList("Journeys", Tag.TAG_COMPOUND).getCompound(0).copy());
            for (var invalid : List.of(incomplete, missing, future, duplicate)) {
                fixture.reloadJourneys(invalid);
                helper.assertFalse(fixture.journeys().diagnostic().isBlank(), "invalid journey data is diagnosed");
                helper.assertValueEqual(fixture.saved(), invalid, "raw data is preserved for repair");
                helper.assertFalse(fixture.complete(player), "invalid proof cannot complete the objective");
            }
            fixture.reloadJourneys(original);
            fixture.finish(player);
            helper.assertTrue(fixture.complete(player), "restoring the original journey recovers play");
        }
        helper.succeed();
    }
}
