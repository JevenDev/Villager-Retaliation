package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("village_diplomacy")
@PrefixGameTestTemplate(false)
public final class VillageRelationsGameTests {
    @GameTest(template = "empty")
    public static void personalKnowledgeAndIntroductionsRemainSeparateAcrossReload(GameTestHelper helper) {
        var data = new VillageDiplomacySavedData();
        var a = VillageAllegianceId.random();
        var b = VillageAllegianceId.random();
        var player = UUID.randomUUID();
        data.learn(player, contact(helper, b, 10));
        helper.assertTrue(data.contacts(a).isEmpty(), "visiting does not share contacts automatically");
        data.learn(a, data.personalContacts(player).get(b).introduced(20));
        helper.assertTrue(data.contacts(b).isEmpty(), "introduction is directional");
        helper.assertFalse(data.contacts(a).get(b).established(), "knowledge is distinct from diplomatic contact");
        var restored = VillageDiplomacySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.personalContacts(player), data.personalContacts(player), "personal knowledge survives");
        helper.assertValueEqual(restored.contacts(a), data.contacts(a), "settlement knowledge survives");
        helper.assertTrue(restored.contacts(b).isEmpty(), "reload cannot introduce the sender to the subject");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goodwillIsBoundedIdempotentAndIndependentOfCombat(GameTestHelper helper) {
        var data = new VillageDiplomacySavedData();
        var pair = VillageDiplomacySavedData.Pair.of(VillageAllegianceId.random(), VillageAllegianceId.random());
        var source = UUID.randomUUID();
        var rules = VillageDiplomacyRules.DEFAULT;
        data.set(pair, VillageRelation.HOSTILE, 0, 0);
        helper.assertTrue(data.adjust(pair, source, 1, 100, "food_aid", 100, rules), "first delivery applied");
        helper.assertValueEqual(data.standing(pair.first(), pair.second()), 3, "large delivery respects goodwill cap");
        helper.assertFalse(data.adjust(pair, source, 1, 100, "food_aid", 101, rules), "replayed delivery ignored");
        data.adjust(pair, source, 2, 3, "food_aid", 102, rules);
        helper.assertValueEqual(data.standing(pair.second(), pair.first()), 3, "many small deliveries cannot evade cap");
        var loaded = VillageDiplomacySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertFalse(loaded.adjust(pair, source, 1, 3, "food_aid", 72000, rules), "receipts survive restart");
        loaded.adjust(pair, source, 3, 3, "food_aid", 72100, rules);
        helper.assertValueEqual(loaded.standing(pair.first(), pair.second()), 6, "later meaningful aid can improve standing");
        helper.assertValueEqual(loaded.query(pair, 72100).relation(), VillageRelation.HOSTILE, "standing cannot alter combat agreement");
        helper.assertValueEqual(loaded.history(pair.first(), pair.second()).size(), 2, "only actual changes enter history");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void standingLabelsHaveHysteresis(GameTestHelper helper) {
        var data = new VillageDiplomacySavedData();
        var pair = VillageDiplomacySavedData.Pair.of(VillageAllegianceId.random(), VillageAllegianceId.random());
        var source = UUID.randomUUID();
        data.adjust(pair, source, 1, 20, "test", 1, VillageDiplomacyRules.DEFAULT);
        helper.assertValueEqual(data.band(pair.first(), pair.second()), VillageStanding.Band.FRIENDLY, "positive threshold");
        data.adjust(pair, source, 2, -1, "test", 2, VillageDiplomacyRules.DEFAULT);
        helper.assertValueEqual(data.band(pair.first(), pair.second()), VillageStanding.Band.FRIENDLY, "small reversal retains label");
        data.adjust(pair, source, 3, -10, "test", 3, VillageDiplomacyRules.DEFAULT);
        helper.assertValueEqual(data.band(pair.first(), pair.second()), VillageStanding.Band.NEUTRAL, "meaningful reversal changes label");
        helper.assertValueEqual(data.query(pair, 3).relation(), VillageRelation.NEUTRAL, "no implicit combat change");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void delayedReportsEstablishContactWithoutChangingStanding(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var dimension = helper.getLevel().dimension().location();
        var a = registry.create(0, dimension, BlockPos.ZERO, "");
        var b = registry.create(0, dimension, new BlockPos(64, 64, 64), "");
        var c = registry.create(0, dimension, new BlockPos(128, 64, 128), "");
        var data = new VillageDiplomacySavedData();
        var letter = new VillageLetter(UUID.randomUUID(), a, b, VillageLetter.Kind.STANDING_REPORT,
                contact(helper, c, 1), -40, 100, 500);
        helper.assertTrue(data.send(letter, 100), "report queued");
        VillageDiplomacyService.deliver(data, registry, 499);
        helper.assertTrue(data.contacts(b).isEmpty(), "no knowledge before arrival");
        var loaded = VillageDiplomacySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        VillageDiplomacyService.deliver(loaded, registry, 500);
        helper.assertTrue(loaded.contacts(b).get(a).established(), "recipient knows actual sender");
        helper.assertTrue(loaded.contacts(a).get(b).established(), "sender establishes recipient contact");
        helper.assertFalse(loaded.contacts(b).get(c).established(), "subject only introduced");
        helper.assertTrue(loaded.contacts(c).isEmpty(), "third village is not notified");
        helper.assertValueEqual(loaded.standing(b, c), 0, "report does not change the recipient's opinion");
        helper.assertValueEqual(loaded.reports(b).getFirst().standing(), -40, "report retains attributed value");
        VillageDiplomacyService.deliver(loaded, registry, 900);
        helper.assertValueEqual(loaded.reports(b).size(), 1, "delivered report cannot replay");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thirdPartyInspectionOnlyReturnsMutuallyKnownVillages(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RelationsViewer"));
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        var dimension = helper.getLevel().dimension().location();
        var a = registry.create(0, dimension, BlockPos.ZERO, "");
        var b = registry.create(0, dimension, new BlockPos(64, 64, 64), "");
        var hidden = registry.create(0, dimension, new BlockPos(128, 64, 128), "");
        var data = VillageDiplomacySavedData.get(helper.getLevel());
        data.learn(player.getUUID(), contact(helper, a, 1));
        data.learn(player.getUUID(), contact(helper, b, 1));
        data.learn(a, contact(helper, b, 1));
        data.learn(a, contact(helper, hidden, 1));
        var visible = VillageDiplomacyService.visibleRelations(player, null, a);
        helper.assertValueEqual(visible.size(), 1, "hidden contacts do not affect exposed counts");
        helper.assertTrue(visible.containsKey(b), "mutually known relation visible");
        helper.assertTrue(VillageDiplomacyService.visibleRelations(player, null, hidden).isEmpty(), "unknown origin cannot be inspected");
        helper.assertValueEqual(VillageDiplomacyService.share(player, a, b, data.revision()), VillageDiplomacyService.Result.FORBIDDEN,
                "personal discovery does not grant diplomatic authority");
        registry.removeVillage(a); registry.removeVillage(b); registry.removeVillage(hidden);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyRelationsMigrateAndCorruptLedgerRemainsReadOnly(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var data = new VillageDiplomacySavedData();
        var pair = VillageDiplomacySavedData.Pair.of(VillageAllegianceId.random(), VillageAllegianceId.random());
        data.set(pair, VillageRelation.HOSTILE, 0, 0);
        data.set(pair, VillageRelation.TRUCE, 100, 10);
        var legacy = data.save(new CompoundTag(), provider);
        legacy.putInt("FormatVersion", 1);
        legacy.remove("Ledger");
        var migrated = VillageDiplomacySavedData.load(legacy, provider);
        helper.assertFalse(migrated.readOnly(), "legacy record migrates");
        helper.assertValueEqual(migrated.query(pair, 109).relation(), VillageRelation.TRUCE, "truce preserved");
        helper.assertValueEqual(migrated.query(pair, 110).relation(), VillageRelation.HOSTILE, "underlying hostility preserved");
        var broken = migrated.save(new CompoundTag(), provider);
        broken.getCompound("Ledger").putString("Players", "bad");
        var preserved = VillageDiplomacySavedData.load(broken, provider);
        helper.assertTrue(preserved.readOnly(), "malformed knowledge blocks mutations");
        helper.assertFalse(preserved.learn(UUID.randomUUID(), contact(helper, pair.first(), 10)), "cannot overwrite malformed records");
        helper.assertValueEqual(preserved.save(new CompoundTag(), provider), broken, "invalid data preserved exactly");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mergedContactsFollowIdentityWithoutDuplicatingKnowledge(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var dimension = helper.getLevel().dimension().location();
        var a = registry.create(0, dimension, BlockPos.ZERO, "");
        var b = registry.create(0, dimension, BlockPos.ZERO, "");
        var c = registry.create(0, dimension, BlockPos.ZERO, "");
        var player = UUID.randomUUID();
        var data = new VillageDiplomacySavedData();
        data.learn(player, contact(helper, a, 1));
        data.learn(player, contact(helper, b, 2));
        data.learn(a, contact(helper, c, 1));
        registry.merge(a, b);
        data.reconcile(registry);
        helper.assertValueEqual(data.personalContacts(player).size(), 1, "aliases collapse to one known identity");
        helper.assertTrue(data.contacts(b).containsKey(c), "surviving village inherits contact");
        registry.archive(c);
        data.reconcile(registry);
        helper.assertTrue(data.contacts(b).isEmpty(), "archived contact no longer exposed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void discoveryAndDelegationRequireServerAuthority(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var settlements = com.jvn.villagerretaliation.settlement.SettlementSavedData.get(level);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Diplomat"));
        var delegate = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "DiplomacyDelegate"));
        var center = helper.absolutePos(new BlockPos(512, 2, 512));
        var id = registry.create(0, level.dimension().location(), center, "");
        var town = settlements.establish(registry, id, leader.getUUID(), center, 0).orElseThrow();
        var distant = registry.create(0, level.dimension().location(), center.offset(256, 0, 0), "");
        var data = VillageDiplomacySavedData.get(level);
        leader.moveTo(center.getX(), center.getY(), center.getZ());
        VillageDiplomacyService.discover(leader, distant);
        helper.assertTrue(data.personalContacts(leader.getUUID()).isEmpty(), "remote identity is not a discovery");
        VillageDiplomacyService.discover(leader, id);
        helper.assertTrue(data.personalContacts(leader.getUUID()).containsKey(id), "entering registered village records it");
        data.learn(leader.getUUID(), contact(helper, distant, 1));
        helper.assertTrue(data.contacts(id).isEmpty(), "discovery is not shared automatically");
        helper.assertValueEqual(VillageDiplomacyService.share(leader, id, distant, data.revision() - 1),
                VillageDiplomacyService.Result.STALE, "old actions rejected");
        helper.assertValueEqual(VillageDiplomacyService.share(leader, id, distant, data.revision()),
                VillageDiplomacyService.Result.APPLIED, "leader explicitly shares contact");
        var permission = com.jvn.villagerretaliation.settlement.SettlementPermission.VIEW;
        town.setPermissions(leader.getUUID(), delegate.getUUID(), java.util.Set.of(permission));
        helper.assertTrue(VillageDiplomacyService.visible(delegate, id).containsKey(distant), "authorized perspective adds village contacts");
        helper.assertFalse(VillageDiplomacyService.represents(delegate, town), "viewing does not confer diplomatic authority");
        town.setPermissions(leader.getUUID(), delegate.getUUID(), java.util.Set.of(
                com.jvn.villagerretaliation.settlement.SettlementPermission.DIPLOMACY));
        helper.assertTrue(VillageDiplomacyService.represents(delegate, town), "explicit delegation permits representation");
        town.setPermissions(leader.getUUID(), delegate.getUUID(), java.util.Set.of());
        helper.assertTrue(VillageDiplomacyService.visible(delegate, id).isEmpty(), "revocation removes shared knowledge immediately");
        registry.removeVillage(id);
        registry.removeVillage(distant);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identityMergePreservesDeliveryReceipts(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var dimension = helper.getLevel().dimension().location();
        var a = registry.create(0, dimension, BlockPos.ZERO, "");
        var b = registry.create(0, dimension, BlockPos.ZERO, "");
        var c = registry.create(0, dimension, BlockPos.ZERO, "");
        var data = new VillageDiplomacySavedData();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        data.adjust(VillageDiplomacySavedData.Pair.of(a, c), first, 1, 3, "food_aid", 10, VillageDiplomacyRules.DEFAULT);
        data.adjust(VillageDiplomacySavedData.Pair.of(b, c), second, 1, 3, "food_aid", 20, VillageDiplomacyRules.DEFAULT);
        registry.merge(a, b);
        data.reconcile(registry);
        var restored = VillageDiplomacySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertFalse(restored.adjust(VillageDiplomacySavedData.Pair.of(b, c), first, 1, 3, "food_aid", 72000,
                VillageDiplomacyRules.DEFAULT), "merged-away village receipt still rejects replay");
        helper.assertValueEqual(restored.history(b, c).size(), 2, "both histories retained");
        helper.succeed();
    }

    private static VillageContact contact(GameTestHelper helper, VillageAllegianceId id, long time) {
        return new VillageContact(id, helper.getLevel().dimension().location(), BlockPos.ZERO, VillageContact.Origin.VISIT, time, false);
    }
}
