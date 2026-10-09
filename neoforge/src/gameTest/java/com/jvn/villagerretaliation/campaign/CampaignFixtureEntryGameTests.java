package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_fixture_entries")
@PrefixGameTestTemplate(false)
public final class CampaignFixtureEntryGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("campaign_fixture:reconciliation");
    private static final String ENTRY = "dragon_after_admission";
    private CampaignFixtureEntryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void authoredChapterEntryOpensNormalOffersWithoutParticipationOrRewards(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            var player = fixture.player();
            var before = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var preview = CampaignFixtureService.inspect(fixture.server, CAMPAIGN, ENTRY, player.getUUID());
            helper.assertTrue(preview.available(), preview.diagnostic());
            helper.assertValueEqual(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()), before, "preview never opens or changes campaign state");
            helper.assertTrue(preview.effects().stream().anyMatch(line -> line.contains("world outcome decision")), "preview exposes the shared effect");
            var request = request(fixture, player.getUUID(), ENTRY);
            var result = CampaignFixtureService.enter(fixture.server, request);
            helper.assertTrue(result.ready(), result.diagnostic());
            var instance = fixture.data.instances().get(CAMPAIGN);
            var personal = instance.players().get(player.getUUID());
            helper.assertTrue(CampaignProgression.chapter(fixture.content.definition(), instance, player.getUUID(), "dragon").available(), "authored setup opens the later chapter");
            helper.assertTrue(personal.participation().isEmpty(), "operator setup invents no participation");
            helper.assertValueEqual(instance.outcomes().get("decision").origin(), CampaignInstance.Origin.OPERATOR_FIXTURE, "world setup records its actual origin");
            helper.assertValueEqual(personal.milestones().get("decision").route(), CampaignInstance.Route.CATCH_UP, "setup personal progress follows the shared outcome");
            helper.assertValueEqual(player.totalExperience, 0, "chapter entry grants no rewards");
            helper.assertTrue(CampaignRewardSavedData.get(helper.getLevel()).receipts().isEmpty(), "setup grants no reward receipts");
            helper.assertTrue(fixture.progress(player, "decision") == null, "setup does not invent a played quest run");
            helper.assertFalse(CampaignRewardService.eligibility(instance, player.getUUID(), fixture.content.definition().rewards().get("decision_shared")).isBlank(),
                    "fixture player cannot claim the original shared reward");
            fixture.reload();
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN), instance, "setup and audit survive reload");
            fixture.select(player, "dragon", "watch", "offer", 0);
            helper.assertValueEqual(fixture.progress(player, "dragon").currentStage(), "watch", "normal dialogue starts the selected chapter");
            var saved = fixture.data.instances().get(CAMPAIGN);
            var retried = CampaignFixtureService.enter(fixture.server, request);
            helper.assertTrue(retried.ready(), retried.diagnostic());
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN), saved, "retry cannot reset a later active quest");
            helper.assertValueEqual(retried.receipt(), result.receipt(), "retry returns the original audit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void competingFixtureEntriesKeepTheWinningOutcomeAndRejectReusedIntent(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            var winner = request(fixture, first, ENTRY);
            var loser = request(fixture, second, "dragon_after_refusal");
            helper.assertTrue(CampaignFixtureService.enter(fixture.server, winner).ready(), "first setup commits");
            var committed = fixture.data.instances().get(CAMPAIGN);
            helper.assertFalse(CampaignFixtureService.enter(fixture.server, loser).ready(), "stale competing preview cannot commit");
            var refreshed = request(fixture, second, "dragon_after_refusal");
            var conflict = CampaignFixtureService.enter(fixture.server, refreshed);
            helper.assertTrue(!conflict.ready() && conflict.diagnostic().contains("already resolved as admitted"), "fresh preview still cannot rewrite an irreversible outcome");
            var changed = new CampaignFixtureService.Request(CAMPAIGN, winner.entry(), second, winner.expected(), winner.operation(), winner.operator(), winner.reason());
            helper.assertFalse(CampaignFixtureService.enter(fixture.server, changed).ready(), "operation cannot be reused for another player");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN), committed, "losing requests change neither player nor world state");
            var follower = CampaignFixtureService.enter(fixture.server, request(fixture, second, ENTRY));
            helper.assertTrue(follower.ready(), follower.diagnostic());
            helper.assertFalse(follower.receipt().effects().getFirst().applied(), "matching shared history is reused");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).outcomes(), committed.outcomes(), "matching setup preserves the original outcome operation");
            fixture.reload();
            helper.assertTrue(fixture.data.diagnostic().isBlank(), "both audits survive reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void failedFixtureSavePublishesNothingAndTheSameRequestCanRecover(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            var request = request(fixture, UUID.randomUUID(), ENTRY);
            var path = fixture.directory.resolve("blocked");
            Files.writeString(path, "occupied");
            var storage = fixture.server.overworld().getDataStorage();
            var broken = CampaignSavedData.load(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()), path);
            storage.set(CampaignSavedData.DATA_NAME, broken);
            try {
                var failed = CampaignFixtureService.enter(fixture.server, request);
                helper.assertTrue(!failed.ready() && failed.receipt() == null, "failed storage returns no committed setup");
                helper.assertTrue(broken.instances().isEmpty(), "failed atomic save publishes no outcomes, personal progress, facts, or audit");
            } finally {
                storage.set(CampaignSavedData.DATA_NAME, fixture.data);
                Files.delete(path);
            }
            helper.assertTrue(CampaignFixtureService.enter(fixture.server, request).ready(), "unchanged operation succeeds after storage recovers");
            fixture.reload();
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).fixtureEntries().size(), 1, "recovered setup has one audit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void fixtureSetupPreservesActualParticipationAndOrdinaryFactHistory(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            var participant = fixture.player();
            fixture.finishDecision(participant, 0);
            var before = fixture.data.instances().get(CAMPAIGN);
            UUID late = UUID.randomUUID();
            var result = CampaignFixtureService.enter(fixture.server, request(fixture, late, ENTRY));
            helper.assertTrue(result.ready(), result.diagnostic());
            var instance = fixture.data.instances().get(CAMPAIGN);
            helper.assertValueEqual(instance.outcomes(), before.outcomes(), "fixture retains a real player's committed world decision");
            helper.assertValueEqual(instance.players().get(participant.getUUID()), before.players().get(participant.getUUID()), "original participation and progression are untouched");
            helper.assertTrue(instance.players().get(late).participation().isEmpty(), "offline fixture target receives no participation");
            helper.assertValueEqual(participant.totalExperience, 40, "setup cannot repeat a participant's reward");
            var stale = request(fixture, UUID.randomUUID(), ENTRY);
            var fact = fixture.data.commitFact(fixture.content.definition(), late, "learned_decision", CampaignFacts.Mutation.SET, "false",
                    UUID.randomUUID(), instance.players().get(late).revision(), helper.getLevel().getGameTime());
            helper.assertValueEqual(fact.status(), CampaignSavedData.Status.APPLIED, "ordinary fact mutation continues after fixture setup");
            helper.assertFalse(CampaignFixtureService.enter(fixture.server, stale).ready(), "fact changes invalidate an earlier preview token");
            fixture.reload();
            helper.assertTrue(fixture.data.diagnostic().isBlank(), "later facts preserve the historical fixture audit");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).fixtureEntries().get(result.receipt().operation()), result.receipt(), "audit preserves its original fact effect");
            var rejected = fixture.data.commitFact(fixture.content.definition(), late, "learned_decision", CampaignFacts.Mutation.SET, "true",
                    result.receipt().operation(), fixture.data.instances().get(CAMPAIGN).players().get(late).revision(), helper.getLevel().getGameTime());
            helper.assertValueEqual(rejected.status(), CampaignSavedData.Status.INVALID_REQUEST, "ordinary effects cannot consume an audit operation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void fixtureAuditsMigrateAndCorruptOrFutureStateRemainsReadableForRepair(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            var original = fixture.data.open(fixture.content, helper.getLevel().getGameTime());
            var legacy = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            legacy.putInt("DataVersion", 3);
            legacy.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0).remove("FixtureEntries");
            var migrated = CampaignSavedData.load(legacy, fixture.directory);
            helper.assertTrue(migrated.diagnostic().isBlank(), "version three migrates");
            helper.assertValueEqual(migrated.instances().get(CAMPAIGN), original, "migration invents no setup or progression");
            helper.assertFalse(legacy.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0).contains("FixtureEntries"), "migration preserves input");
            var result = CampaignFixtureService.enter(fixture.server, request(fixture, UUID.randomUUID(), ENTRY));
            helper.assertTrue(result.ready(), result.diagnostic());
            var saved = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            for (String corruption : List.of("missing", "operator", "outcome", "operation", "future", "forged_legacy")) {
                var damaged = saved.copy();
                var campaign = damaged.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0);
                var audit = campaign.getList("FixtureEntries", Tag.TAG_COMPOUND).getCompound(0);
                switch (corruption) {
                    case "missing" -> campaign.remove("FixtureEntries");
                    case "operator" -> audit.remove("Operator");
                    case "outcome" -> audit.getList("Effects", Tag.TAG_COMPOUND).getCompound(0).putString("Value", "refused");
                    case "operation" -> audit.putUUID("Operation", UUID.randomUUID());
                    case "future" -> damaged.putInt("DataVersion", CampaignSavedData.CURRENT_DATA_VERSION + 1);
                    case "forged_legacy" -> damaged.putInt("DataVersion", 3);
                }
                var blocked = CampaignSavedData.load(damaged, fixture.directory);
                helper.assertFalse(blocked.diagnostic().isBlank(), "invalid audit is diagnosed: " + corruption);
                helper.assertValueEqual(blocked.save(new CompoundTag(), helper.getLevel().registryAccess()), damaged, "repairable raw data is preserved: " + corruption);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void operatorCommandsPreviewAuditAndEnterForAnOfflinePlayer(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            UUID player = UUID.randomUUID(), operation = UUID.randomUUID();
            var dispatcher = fixture.server.getCommands().getDispatcher();
            var source = fixture.server.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2).withSuppressedOutput();
            String inspect = "vr admin campaign fixture inspect " + CAMPAIGN + " " + ENTRY + " " + player;
            helper.assertValueEqual(dispatcher.execute(inspect, source), 1, "operator can preview authored effects");
            boolean denied = false;
            try { dispatcher.execute(inspect, source.withPermission(0)); } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            helper.assertTrue(denied, "fixture command requires operator permission");
            String enter = "vr admin campaign fixture enter " + CAMPAIGN + " " + ENTRY + " " + player + " "
                    + fixture.data.fixtureToken(fixture.content.definition()) + " " + operation + " inspect later chapter";
            helper.assertValueEqual(dispatcher.execute(enter, source), 1, "operator enters an offline player's fixture chapter");
            helper.assertValueEqual(dispatcher.execute(enter, source), 1, "same command returns its saved audit");
            helper.assertValueEqual(dispatcher.execute("vr admin campaign fixture history " + CAMPAIGN, source), 1, "operator can inspect history");
            helper.assertValueEqual(dispatcher.execute(inspect + " 999", source), 0, "invalid pages are rejected");
            var receipt = fixture.data.instances().get(CAMPAIGN).fixtureEntries().get(operation);
            helper.assertValueEqual(receipt.reason(), "inspect later chapter", "operator reason is durable");
            helper.assertValueEqual(receipt.player(), player, "audit identifies the offline target");
            var report = CampaignReferenceReport.build(CampaignResources.current(fixture.server));
            var key = CampaignReferenceReport.Key.of(CampaignReferenceReport.Kind.FIXTURE, CAMPAIGN, ENTRY);
            helper.assertTrue(report.nodes().containsKey(key), "fixture entry appears in the dependency report");
            helper.assertTrue(report.trace(key, CampaignReferenceReport.Direction.DEPENDENCIES, 1).stream()
                    .anyMatch(match -> match.reference().to().kind() == CampaignReferenceReport.Kind.OUTCOME), "authored outcome dependency can be traced");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void fixtureEntryRefusesActiveQuestsSandboxAndMissingContent(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            var player = fixture.player();
            fixture.select(player, "decision", "choose", "offer", 0);
            var request = request(fixture, player.getUUID(), ENTRY);
            var before = fixture.data.instances().get(CAMPAIGN);
            helper.assertFalse(CampaignFixtureService.enter(fixture.server, request).ready(), "fixture does not skip an active quest");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN), before, "refusal preserves active progress");
            var storage = fixture.server.overworld().getDataStorage();
            var mode = CampaignWorldModeSavedData.get(helper.getLevel());
            var content = CampaignResources.current(fixture.server);
            try {
                var sandbox = new CompoundTag(); sandbox.putInt("DataVersion", 1); sandbox.putString("Mode", "sandbox");
                storage.set(CampaignWorldModeSavedData.DATA_NAME, CampaignWorldModeSavedData.load(sandbox, helper.getLevel().registryAccess()));
                helper.assertFalse(CampaignFixtureService.enter(fixture.server, request(fixture, UUID.randomUUID(), ENTRY)).ready(), "sandbox refuses fixture progression");
                storage.set(CampaignWorldModeSavedData.DATA_NAME, mode);
                CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(fixture.catalog, Map.of(), List.of()));
                helper.assertFalse(CampaignFixtureService.enter(fixture.server, request(fixture, UUID.randomUUID(), ENTRY)).ready(), "missing campaign preserves dormant progress");
            } finally {
                storage.set(CampaignWorldModeSavedData.DATA_NAME, mode);
                CampaignResources.installForTests(fixture.server, content);
            }
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN), before, "unavailable environments change no campaign state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void fixtureLocationChangesPreservePlayerEditsAndResumeTheirSavedReceipts(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            UUID player = UUID.randomUUID();
            var definition = fixture.base.campaigns.campaigns().get(fixture.base.campaignId).definition();
            var preview = CampaignFixtureService.inspect(fixture.base.server, fixture.base.campaignId, "sealed_arrival", player);
            helper.assertTrue(preview.available(), preview.diagnostic());
            helper.assertTrue(preview.effects().stream().anyMatch(line -> line.contains("Owned cell floor")), "preview lists the exact bounded cells");
            helper.getLevel().setBlock(fixture.floor, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            var request = new CampaignFixtureService.Request(fixture.base.campaignId, "sealed_arrival", player,
                    fixture.campaigns().fixtureToken(definition), UUID.randomUUID(), UUID.randomUUID(), "test the sealed arrival");
            var pending = CampaignFixtureService.enter(fixture.base.server, request);
            helper.assertTrue(!pending.ready() && pending.receipt() != null, "world effects can suspend after the atomic campaign setup is saved");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.floor).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK), "player construction is preserved");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).isAir(), "conflict preflight prevents a partial variant");
            var instance = fixture.instance();
            var operation = fixture.receipt("sealed").operation();
            fixture.reload();
            var repeated = CampaignFixtureService.enter(fixture.base.server, request);
            helper.assertFalse(repeated.ready(), "saved conflict remains recoverable after reload");
            helper.assertValueEqual(fixture.instance(), instance, "pending retry repeats no campaign effects");
            helper.assertValueEqual(fixture.receipt("sealed").operation(), operation, "variant recovery retains the original operation");
            helper.assertValueEqual(CampaignLocationVariants.preserve(fixture.base.server, operation, request.operator(), "retain player construction"),
                    "", "operator can settle the bounded conflict through the existing repair path");
            helper.assertTrue(CampaignFixtureService.enter(fixture.base.server, request).ready(), "entry becomes ready after its world changes settle");
            helper.assertValueEqual(fixture.instance(), instance, "settling the location cannot duplicate the fixture audit");
            helper.assertTrue(fixture.instance().players().get(player).participation().isEmpty(), "world setup grants no participation");
            helper.assertTrue(CampaignRewardSavedData.get(helper.getLevel()).receipts().isEmpty(), "world setup grants no shared rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fixture_entries", timeoutTicks = 200)
    public static void savedFixtureAuditsRequireMatchingAuthoredEntriesAndReadableQuestState(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignPlayableFixtureGameTests.Fixture(helper)) {
            var request = request(fixture, UUID.randomUUID(), ENTRY);
            var storage = fixture.server.overworld().getDataStorage();
            var quests = VillagerQuestSavedData.get(helper.getLevel());
            var future = quests.save(new CompoundTag(), helper.getLevel().registryAccess());
            future.putInt("DataVersion", VillagerQuestSavedData.CURRENT_DATA_VERSION + 1);
            storage.set("villagerretaliation_quests", VillagerQuestSavedData.load(future, helper.getLevel().registryAccess()));
            try {
                helper.assertFalse(CampaignFixtureService.enter(fixture.server, request).ready(), "future quest state blocks fixture setup");
                helper.assertTrue(fixture.data.instances().isEmpty(), "unreadable quest state cannot be bypassed");
            } finally { storage.set("villagerretaliation_quests", quests); }
            helper.assertTrue(CampaignFixtureService.enter(fixture.server, request).ready(), "readable state permits authored setup");
            for (String field : List.of("Entry", "Chapter")) {
                var saved = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
                saved.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0).getList("FixtureEntries", Tag.TAG_COMPOUND).getCompound(0).putString(field, "missing");
                var changed = CampaignSavedData.load(saved, fixture.directory);
                helper.assertTrue(changed.diagnostic().isBlank(), "history remains readable without relying on loaded content");
                helper.assertFalse(changed.compatibility(fixture.content.definition()).isBlank(), "loaded content diagnoses a mismatched fixture " + field);
                helper.assertValueEqual(changed.save(new CompoundTag(), helper.getLevel().registryAccess()), saved, "incompatible fixture history is preserved");
            }
        }
        helper.succeed();
    }

    private static CampaignFixtureService.Request request(CampaignPlayableFixtureGameTests.Fixture fixture, UUID player, String entry) {
        return new CampaignFixtureService.Request(CAMPAIGN, entry, player, fixture.data.fixtureToken(fixture.content.definition()),
                UUID.randomUUID(), UUID.randomUUID(), "enter authored chapter");
    }
}
