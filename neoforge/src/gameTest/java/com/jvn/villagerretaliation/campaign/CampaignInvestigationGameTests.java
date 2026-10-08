package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignInvestigationGameTests {
    private CampaignInvestigationGameTests() { }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void authoredInteractionSavesPersonalEvidenceAndCompletesOnce(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var player = fixture.player();
            fixture.accept(player);
            helper.assertFalse(fixture.complete(player), "finding the source alone does not investigate it");
            var event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, source);
            NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled(), "registered interaction handler consumes the authored evidence");
            helper.assertTrue(fixture.complete(player), "saved interaction completes the objective");
            var receipt = fixture.data().receipts().values().iterator().next();
            helper.assertValueEqual(receipt.source(), source.getUUID(), "receipt retains the bound entity");
            helper.assertValueEqual(receipt.title(), "A damaged ledger", "localized title is retained");
            helper.assertValueEqual(receipt.text(), "The final shipment was taken to the old watchtower.", "localized evidence is retained");
            helper.assertFalse(CampaignEvidenceService.investigate(player, source).applied(), "repeated interaction is idempotent");
            fixture.base.reload();
            fixture.reload();
            helper.assertTrue(fixture.complete(player), "receipt survives a disk reload");
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "one durable receipt");
            helper.assertValueEqual(fixture.resolve().entity().getUUID(), source.getUUID(), "source identity survives reload");
            source.discard();
            helper.assertTrue(fixture.complete(player), "recorded discovery survives loss of its source");
            var completed = new ArrayList<String>();
            helper.assertTrue(QuestObjectiveDispatchService.poll(fixture.context(player), objective -> completed.add(objective.id())),
                    "existing dispatch completes the objective");
            helper.assertFalse(QuestObjectiveDispatchService.poll(fixture.context(player), objective -> completed.add(objective.id())),
                    "repeat dispatch cannot duplicate completion");
            helper.assertValueEqual(completed, List.of("inspect.ledger"), "only the authored objective completes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void onlyTheBoundSourceCanAwardInvestigation(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var duplicate = fixture.source(2, 2);
            var player = fixture.player();
            fixture.accept(player);
            helper.assertValueEqual(fixture.resolve().entity(), source, "nearest eligible evidence is bound");
            helper.assertFalse(CampaignEvidenceService.investigate(player, duplicate).matched(), "equivalent tag cannot replace bound identity");
            player.getInventory().add(new ItemStack(Items.WRITTEN_BOOK));
            helper.setBlock(2, 1, 3, Blocks.LECTERN);
            var marker = helper.spawn(EntityType.MARKER, 1, 1, 2);
            marker.addTag("campaign_fixture:ledger");
            helper.assertFalse(CampaignEvidenceService.handle(player, marker), "tagged marker is not an authored interaction");
            marker.discard();
            helper.assertFalse(fixture.complete(player), "ordinary inventory and construction do not investigate");
            helper.assertTrue(CampaignEvidenceService.investigate(player, source).applied(), "exact bound interaction awards credit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void investigationIsPersonalAndRequiresTheCurrentQuestRun(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var first = fixture.player();
            var second = fixture.player();
            helper.assertFalse(CampaignEvidenceService.investigate(second, source).matched(), "pre-quest interaction gives no future credit");
            fixture.accept(first);
            fixture.accept(second);
            fixture.party(first, second);
            helper.assertTrue(CampaignEvidenceService.investigate(first, source).applied(), "first player investigates");
            helper.assertFalse(fixture.complete(second), "party membership does not copy personal discovery");
            helper.assertTrue(CampaignEvidenceService.investigate(second, source).applied(), "second player investigates separately");
            var progress = fixture.context(first).progress();
            var priorRun = progress.questRunId();
            progress.start(fixture.mentor.getUUID(), first.serverLevel().dimension(), fixture.mentor.blockPosition(), first.serverLevel().getGameTime());
            progress.beginRun(first.getUUID(), fixture.base.campaignId, null);
            progress.setCurrentStage("inspect");
            helper.assertFalse(priorRun.equals(progress.questRunId()), "fixture starts a new quest run");
            helper.assertFalse(fixture.complete(first), "previous run does not fulfill a new objective");
            helper.assertTrue(CampaignEvidenceService.investigate(first, source).applied(), "new run can inspect the same evidence");
            helper.assertValueEqual(fixture.data().receipts().size(), 3, "receipts are scoped to player and quest run");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void canceledRemoteAndSpectatorInteractionsGiveNoCredit(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var player = fixture.player();
            fixture.accept(player);
            var event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, source);
            event.setCanceled(true);
            CampaignEvidenceService.onInteract(event);
            helper.assertTrue(fixture.data().receipts().isEmpty(), "canceled interaction has no receipt");
            player.setPos(source.getX() + 20, source.getY(), source.getZ());
            helper.assertFalse(CampaignEvidenceService.investigate(player, source).matched(), "remote request fails reach check");
            player.setPos(source.getX() + 1, source.getY(), source.getZ());
            player.setGameMode(GameType.SPECTATOR);
            helper.assertFalse(CampaignEvidenceService.investigate(player, source).matched(), "spectator does not investigate");
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(source.getX() + 2, source.getY(), source.getZ());
            helper.setBlock(source.blockPosition().offset(1, 0, 0).subtract(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)), Blocks.STONE);
            helper.setBlock(source.blockPosition().offset(1, 1, 0).subtract(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)), Blocks.STONE);
            helper.assertFalse(CampaignEvidenceService.investigate(player, source).matched(), "blocked sight prevents investigation");
            helper.assertTrue(fixture.data().receipts().isEmpty(), "rejected requests do not save observations");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void missingUnloadedAndChangedSourcesStayRecoverable(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var player = fixture.player();
            fixture.accept(player);
            helper.assertFalse(fixture.resolve().available(), "absent evidence remains unavailable");
            var source = fixture.source(1, 2);
            var identity = fixture.resolve().binding();
            source.removeTag("campaign_fixture:ledger");
            helper.assertFalse(fixture.resolve().available(), "removed authoring tag is diagnosed");
            source.addTag("campaign_fixture:ledger");
            source.setPos(source.getX() + 1, source.getY(), source.getZ());
            helper.assertFalse(fixture.resolve().available(), "moved source is diagnosed");
            source.setPos(identity.position().getX(), identity.position().getY(), identity.position().getZ());
            helper.assertTrue(fixture.resolve().available(), "restored position recovers source");
            source.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.source(2, 2);
            helper.assertFalse(fixture.resolve().available(), "unloaded original is never replaced by another source");
            fixture.reload();
            helper.assertFalse(fixture.complete(player), "missing source does not imply an observation");
            var restored = new Interaction(EntityType.INTERACTION, helper.getLevel());
            restored.setUUID(identity.entity());
            restored.setPos(identity.position().getX(), identity.position().getY(), identity.position().getZ());
            restored.addTag("campaign_fixture:ledger");
            helper.getLevel().addFreshEntity(restored);
            fixture.sources.add(restored);
            helper.assertValueEqual(fixture.resolve().entity(), restored, "original identity can return after unloading");
            helper.assertTrue(CampaignEvidenceService.investigate(player, restored).applied(), "recovered source is playable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void failedBindingAndReceiptSavesDoNotPublishProgress(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var player = fixture.player();
            fixture.blockWrites();
            try {
                fixture.accept(player);
                var failed = fixture.resolve();
                helper.assertFalse(failed.available(), "binding save failure is visible");
                helper.assertTrue(fixture.data().bindings().isEmpty(), "failed binding remains unpublished");
            } finally {
                fixture.restoreWrites();
            }
            helper.assertTrue(fixture.resolve().available(), "binding can retry after storage repair");
            fixture.blockWrites();
            try {
                var failed = CampaignEvidenceService.investigate(player, source);
                helper.assertFalse(failed.diagnostic().isBlank(), "receipt save failure is reported");
                helper.assertTrue(failed.title().isBlank() && failed.text().isBlank(), "unsaved evidence is not presented");
                helper.assertTrue(fixture.data().receipts().isEmpty(), "failed receipt remains unpublished");
                helper.assertFalse(fixture.complete(player), "failed save cannot finish the objective");
            } finally {
                fixture.restoreWrites();
            }
            helper.assertTrue(CampaignEvidenceService.investigate(player, source).applied(), "repaired storage accepts a retry");
            fixture.reload();
            helper.assertTrue(fixture.complete(player), "retried receipt survives reload");
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "retry records one receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void malformedEvidenceSavesArePreservedForRepair(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var player = fixture.player();
            fixture.accept(player);
            helper.assertTrue(CampaignEvidenceService.investigate(player, source).applied(), "fixture records evidence");
            var original = fixture.saved();
            for (String field : List.of("Source", "Operation", "QuestRun", "Instance")) {
                var malformed = original.copy();
                malformed.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putUUID(field, UUID.randomUUID());
                fixture.reload(malformed);
                helper.assertFalse(fixture.data().diagnostic().isBlank(), "inconsistent " + field + " is diagnosed");
                helper.assertFalse(fixture.complete(player), "malformed receipt gives no credit");
                helper.assertValueEqual(fixture.saved(), malformed, "malformed data is retained");
            }
            var future = original.copy();
            future.putInt("DataVersion", 2);
            fixture.reload(future);
            helper.assertFalse(fixture.data().diagnostic().isBlank(), "future save remains dormant");
            helper.assertValueEqual(fixture.saved(), future, "future data is preserved");
            var duplicate = original.copy();
            var bindings = duplicate.getList("Bindings", Tag.TAG_COMPOUND);
            bindings.add(bindings.getCompound(0).copy());
            fixture.reload(duplicate);
            helper.assertFalse(fixture.data().diagnostic().isBlank(), "duplicate ownership is rejected");
            fixture.reload(original);
            helper.assertTrue(fixture.complete(player), "restoring original data repairs progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation")
    public static void missingContentSandboxAndChangedDefinitionsDoNotConsumeEvidence(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var source = fixture.source(1, 2);
            var player = fixture.player();
            fixture.accept(player);
            helper.assertTrue(fixture.resolve().available(), "fixture source bound");
            fixture.base.mode("sandbox");
            helper.assertFalse(CampaignEvidenceService.investigate(player, source).matched(), "sandbox retains ordinary interaction");
            helper.assertFalse(fixture.complete(player), "sandbox does not advance campaign");
            fixture.base.mode("campaign");
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(fixture.base.catalog, Map.of(), List.of()));
            helper.assertFalse(CampaignEvidenceService.investigate(player, source).matched(), "missing campaign stays dormant");
            helper.assertFalse(fixture.resolve().available(), "missing content is recoverable");
            var changed = fixture.base.raw();
            changed.getAsJsonArray("evidence").get(0).getAsJsonObject().addProperty("search_radius", 9);
            fixture.base.install(changed);
            helper.assertFalse(CampaignEvidenceService.investigate(player, source).diagnostic().isBlank(), "changed fingerprint blocks work");
            helper.assertTrue(fixture.data().receipts().isEmpty(), "blocked work has no receipts");
            CampaignResources.installForTests(fixture.base.server, fixture.base.campaigns);
            helper.assertTrue(CampaignEvidenceService.investigate(player, source).applied(), "restoring matching content resumes play");
        }
        helper.succeed();
    }
}
