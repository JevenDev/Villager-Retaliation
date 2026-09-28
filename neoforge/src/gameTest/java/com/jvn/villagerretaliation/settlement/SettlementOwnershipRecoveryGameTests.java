package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementOwnershipRecoveryGameTests {
    @GameTest(template = "empty")
    public static void leadershipOffersRequireAcceptanceAndSurviveReload(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var town = fixture.town();
        UUID owner = town.leader();
        UUID next = UUID.randomUUID();
        var offer = town.offerLeadership(owner, next, 100L);
        helper.assertValueEqual(town.leader(), owner, "offering does not surrender ownership");
        helper.assertFalse(town.acceptLeadership(owner, offer.id(), 101L), "the issuer cannot accept for the successor");
        helper.assertFalse(town.acceptLeadership(UUID.randomUUID(), offer.id(), 101L), "outsiders cannot accept");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.leadershipOffer(), offer, "offers persist across restart");
        helper.assertTrue(restored.acceptLeadership(next, offer.id(), 101L), "only the named successor accepts");
        helper.assertFalse(restored.acceptLeadership(next, offer.id(), 102L), "acceptance cannot replay");
        helper.assertValueEqual(restored.leader(), next, "the accepted successor owns the town");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledExpiredAndReplacedOffersCannotTransferOwnership(GameTestHelper helper) {
        var town = SettlementPopulationGameTests.fixture(helper).town();
        UUID owner = town.leader();
        UUID next = UUID.randomUUID();
        var cancelled = town.offerLeadership(owner, next, 100L);
        helper.assertFalse(town.cancelLeadership(UUID.randomUUID(), cancelled.id()), "outsiders cannot cancel");
        helper.assertTrue(town.cancelLeadership(next, cancelled.id()), "the successor can decline");
        helper.assertFalse(town.acceptLeadership(next, cancelled.id(), 101L), "declined offers cannot be accepted");
        var expired = town.offerLeadership(owner, next, 100L);
        helper.assertFalse(town.acceptLeadership(next, expired.id(), expired.expiresAt()), "expiry is enforced");
        var replaced = town.offerLeadership(owner, next, 200L);
        var latest = town.offerLeadership(owner, UUID.randomUUID(), 201L);
        helper.assertFalse(town.acceptLeadership(next, replaced.id(), 202L), "replacement invalidates old tokens");
        helper.assertTrue(town.cancelLeadership(owner, latest.id()), "the leader can withdraw an offer");
        helper.assertValueEqual(town.leader(), owner, "all rejected workflows preserve ownership");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void administratorCanRecoverOfflineOwnerOnlyAfterMatchingConfirmation(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var town = fixture.town();
        var source = fixture.leader().createCommandSourceStack().withPermission(2);
        UUID offline = UUID.randomUUID();
        helper.assertTrue(town.recoverLeadership(source, offline), "fixture places ownership with an offline account");
        UUID successor = fixture.leader().getUUID();
        helper.assertTrue(SettlementOwnershipService.previewRecovery(source.withPermission(0), town, successor) == null,
                "ordinary players cannot preview recovery");
        UUID token = SettlementOwnershipService.previewRecovery(source, town, successor);
        helper.assertValueEqual(town.leader(), offline, "preview is read-only");
        helper.assertFalse(SettlementOwnershipService.confirmRecovery(source.withPermission(0), token), "confirmation rechecks operator permission");
        helper.assertTrue(SettlementOwnershipService.confirmRecovery(source, token), "the operator recovers without the offline owner's consent");
        helper.assertFalse(SettlementOwnershipService.confirmRecovery(source, token), "confirmation is single-use");
        var reloaded = SettlementSavedData.load(SettlementSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess()).get(town.id()).orElseThrow();
        helper.assertValueEqual(reloaded.leader(), successor, "recovered ownership persists");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recoveryPreviewBecomesStaleAfterAcceptedTransfer(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var town = fixture.town();
        var source = fixture.leader().createCommandSourceStack().withPermission(2);
        UUID token = SettlementOwnershipService.previewRecovery(source, town, UUID.randomUUID());
        UUID successor = UUID.randomUUID();
        var offer = town.offerLeadership(town.leader(), successor, 0L);
        helper.assertTrue(town.acceptLeadership(successor, offer.id(), 1L), "an accepted transfer can occur during a preview");
        helper.assertFalse(SettlementOwnershipService.confirmRecovery(source, token), "the preview cannot override a different owner");
        helper.assertValueEqual(town.leader(), successor, "the newly accepted owner remains authoritative");
        helper.succeed();
    }
}
