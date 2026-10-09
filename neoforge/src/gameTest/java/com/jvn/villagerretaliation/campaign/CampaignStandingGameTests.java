package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignStandingGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:standing");
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:quest");
    private CampaignStandingGameTests() { }

    @GameTest(template = "empty", batch = "campaign_standing")
    public static void standingChangesKeepPlayersFactionsAndIndividualCharactersSeparate(GameTestHelper helper) throws IOException {
        try (var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var subject = subject(UUID.randomUUID(), UUID.randomUUID(), Scope.CHARACTER, "mentor", UUID.randomUUID());
            var change = change(subject, 20, "help");
            var result = disk.data().apply(change, subject, 1);
            helper.assertTrue(result.applied(), "first change applies");
            helper.assertValueEqual(result.receipt().after(), 20, "relationship changes by its authored amount");
            var replacement = subject(subject.key().instance(), subject.key().player(), Scope.CHARACTER, "mentor", UUID.randomUUID());
            helper.assertFalse(disk.data().apply(change, replacement, 2).applied(), "replaying after replacement cannot credit a second individual");
            helper.assertFalse(disk.data().values().containsKey(replacement.key()), "replacement has no inherited relationship");
            var faction = subject(subject.key().instance(), subject.key().player(), Scope.FACTION, "mentor", null);
            var other = subject(subject.key().instance(), UUID.randomUUID(), Scope.CHARACTER, "mentor", subject.key().character());
            disk.data().apply(change(faction, -10, "guild"), faction, 3);
            disk.data().apply(change(other, 5, "personal"), other, 4);
            disk.data().apply(change(replacement, 3, "new_person"), replacement, 5);
            disk.reload();
            helper.assertValueEqual(disk.data().values().get(subject.key()).amount(), 20, "original individual retains their relationship");
            helper.assertValueEqual(disk.data().values().get(replacement.key()).amount(), 3, "replacement starts from the authored initial value");
            helper.assertValueEqual(disk.data().values().get(faction.key()).amount(), -10, "same-named faction is a separate scope");
            helper.assertValueEqual(disk.data().values().get(other.key()).amount(), 5, "another player's history is independent");
            helper.assertValueEqual(disk.data().replay(change), result.receipt(), "reload preserves the first receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing")
    public static void clampingAndConflictingOperationsRemainDurable(GameTestHelper helper) throws Exception {
        try (var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var subject = subject(UUID.randomUUID(), UUID.randomUUID(), Scope.FACTION, "wardens", null);
            var change = change(subject, Integer.MAX_VALUE, "large");
            var upper = disk.data().apply(change, subject, 1);
            helper.assertValueEqual(upper.receipt().after(), 100, "large positive change clamps without overflow");
            var conflict = new CampaignStanding.Change(change.campaign(), change.instance(), change.player(), change.quest(), change.questRun(),
                    change.scope(), change.subject(), 1, change.operationId());
            try { disk.data().apply(conflict, subject, 2); helper.fail("conflicting operation must not overwrite the receipt"); }
            catch (IllegalArgumentException expected) { }
            var lower = disk.data().apply(change(subject, Integer.MIN_VALUE, "large_negative"), subject, 3);
            helper.assertValueEqual(lower.receipt().after(), -100, "large negative change clamps without overflow");
            var concurrent = change(subject, 10, "concurrent");
            try (var threads = Executors.newFixedThreadPool(2)) {
                var results = threads.invokeAll(List.<Callable<CampaignStanding.Result>>of(
                        () -> disk.data().apply(concurrent, subject, 4), () -> disk.data().apply(concurrent, subject, 4)));
                helper.assertTrue(results.get(0).get().applied() != results.get(1).get().applied(), "concurrent replay commits only once");
            }
            disk.reload();
            helper.assertValueEqual(disk.data().values().get(subject.key()).amount(), -90, "concurrent requests apply one delta");
            helper.assertValueEqual(disk.data().receipts().size(), 3, "only distinct operations remain");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing")
    public static void failedStandingWritesPublishNoChanges(GameTestHelper helper) throws IOException {
        try (var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var subject = subject(UUID.randomUUID(), UUID.randomUUID(), Scope.FACTION, "wardens", null);
            var change = change(subject, 10, "help");
            disk.blockWrites();
            try { disk.data().apply(change, subject, 1); helper.fail("blocked storage must reject the change"); }
            catch (IOException expected) { }
            helper.assertTrue(disk.data().values().isEmpty() && disk.data().receipts().isEmpty(), "failed save publishes neither values nor receipts");
            disk.restoreWrites();
            disk.data().apply(change, subject, 1);
            disk.reload();
            helper.assertValueEqual(disk.data().values().get(subject.key()).amount(), 10, "retry applies the original change once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing")
    public static void unreadableStandingHistoryIsPreservedWithoutPublishingPartialValues(GameTestHelper helper) throws IOException {
        try (var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var subject = subject(UUID.randomUUID(), UUID.randomUUID(), Scope.CHARACTER, "mentor", UUID.randomUUID());
            var change = change(subject, 10, "help");
            disk.data().apply(change, subject, 1);
            var original = disk.read();
            for (Consumer<CompoundTag> corrupt : List.<Consumer<CompoundTag>>of(
                    tag -> tag.putInt("DataVersion", 2),
                    tag -> tag.remove("Receipts"),
                    tag -> tag.putString("Receipts", "broken"),
                    tag -> tag.getList("Receipts", 10).add(tag.getList("Receipts", 10).getCompound(0).copy()),
                    tag -> receipt(tag).putString("Campaign", "INVALID"),
                    tag -> receipt(tag).putString("Scope", "UNKNOWN"),
                    tag -> receipt(tag).remove("Character"),
                    tag -> receipt(tag).putUUID("Operation", UUID.randomUUID()),
                    tag -> receipt(tag).putInt("Amount", 11),
                    tag -> receipt(tag).putInt("Before", 1),
                    tag -> receipt(tag).putLong("Revision", 2),
                    tag -> receipt(tag).putInt("Minimum", 20),
                    tag -> receipt(tag).putString("Fingerprint", "changed"))) {
                var broken = original.copy(); corrupt.accept(broken);
                var dormant = CampaignStandingSavedData.load(broken, disk.directory());
                helper.assertFalse(dormant.diagnostic().isBlank(), "invalid history reports a repair path");
                helper.assertTrue(dormant.values().isEmpty() && dormant.receipts().isEmpty(), "invalid history publishes no partial values");
                helper.assertValueEqual(dormant.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "unreadable history is preserved");
                try { dormant.apply(change, subject, 2); helper.fail("dormant history cannot receive more changes"); }
                catch (IOException expected) { }
                helper.assertValueEqual(disk.read(), original, "invalid load does not overwrite the valid file");
            }
        }
        helper.succeed();
    }

    private static CompoundTag receipt(CompoundTag tag) { return tag.getList("Receipts", 10).getCompound(0); }
    private static CampaignStanding.Subject subject(UUID instance, UUID player, Scope scope, String id, UUID character) {
        return new CampaignStanding.Subject(new CampaignStanding.Key(CAMPAIGN, instance, player, scope, id, character), 1,
                "0".repeat(64), "Saved standing label", -100, 100, 0);
    }
    private static CampaignStanding.Change change(CampaignStanding.Subject subject, int amount, String operation) {
        var key = subject.key();
        return new CampaignStanding.Change(key.campaign(), key.instance(), key.player(), QUEST, UUID.randomUUID(),
                key.scope(), key.subject(), amount, operation);
    }
}
