package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("village_diplomacy")
@PrefixGameTestTemplate(false)
public final class VillageDiplomacyGameTests {
    @GameTest(template = "empty")
    public static void transitionsRenewalAndExactExpiry(GameTestHelper helper) {
        var data = new VillageDiplomacySavedData();
        var pair = VillageDiplomacySavedData.Pair.of(VillageAllegianceId.random(), VillageAllegianceId.random());
        helper.assertValueEqual(data.query(pair, 100).relation(), VillageRelation.NEUTRAL, "missing is neutral");
        for (VillageRelation base : new VillageRelation[]{VillageRelation.NEUTRAL, VillageRelation.HOSTILE}) {
            data.set(pair, base, 0, 100);
            data.set(pair, VillageRelation.TRUCE, 10, 100);
            helper.assertValueEqual(data.query(pair, 109).relation(), VillageRelation.TRUCE, "before deadline");
            helper.assertValueEqual(data.query(pair, 110).relation(), base, "expiry restores underlying relation");
            data.set(pair, VillageRelation.TRUCE, 10, 110);
            data.set(pair, VillageRelation.TRUCE, 20, 115);
            helper.assertValueEqual(data.query(pair, 134).resumesAs(), base, "renewal retains underlying relation");
            helper.assertValueEqual(data.query(pair, 135).relation(), base, "renewed expiry");
            data.set(pair, VillageRelation.TRUCE, 0, 140);
            helper.assertTrue(data.query(pair, Long.MAX_VALUE).expiresAt().isEmpty(), "indefinite has no deadline");
            helper.assertValueEqual(data.query(pair, Long.MAX_VALUE).relation(), VillageRelation.TRUCE, "indefinite persists");
            data.set(pair, base, 0, 150);
            helper.assertValueEqual(data.query(pair, 150).relation(), base, "explicit set cancels truce");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void serverApiValidatesPairsAndThreads(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var a = registry.create(0, level.dimension().location(), BlockPos.ZERO, "");
        var b = registry.create(0, level.dimension().location(), BlockPos.ZERO, "");
        helper.assertValueEqual(VillageDiplomacyApi.set(level, a, b, VillageRelation.HOSTILE),
                VillageDiplomacyApi.ChangeResult.APPLIED, "server change accepted");
        helper.assertValueEqual(VillageDiplomacyApi.query(level, b, a).relation(), VillageRelation.HOSTILE, "symmetric");
        helper.assertValueEqual(VillageDiplomacyApi.set(level, a, a, VillageRelation.HOSTILE),
                VillageDiplomacyApi.ChangeResult.SAME_VILLAGE, "self rejected");
        int size = registry.records().size();
        helper.assertValueEqual(VillageDiplomacyApi.set(level, a, VillageAllegianceId.random(), VillageRelation.HOSTILE),
                VillageDiplomacyApi.ChangeResult.INVALID_VILLAGE, "unknown rejected");
        helper.assertValueEqual(registry.records().size(), size, "queries and changes do not discover villages");
        helper.assertValueEqual(VillageDiplomacyApi.set(level, a, b, VillageRelation.TRUCE),
                VillageDiplomacyApi.ChangeResult.INVALID_STATE, "truce uses dedicated operation");
        helper.assertValueEqual(VillageDiplomacyApi.truce(level, a, b, -1),
                VillageDiplomacyApi.ChangeResult.INVALID_DURATION, "negative duration rejected");
        helper.assertValueEqual(VillageDiplomacyApi.truce(level, a, b, Long.MAX_VALUE),
                VillageDiplomacyApi.ChangeResult.INVALID_DURATION, "overflow rejected");
        helper.assertValueEqual(CompletableFuture.supplyAsync(() -> VillageDiplomacyApi.set(level, a, b, VillageRelation.NEUTRAL)).join(),
                VillageDiplomacyApi.ChangeResult.WRONG_THREAD, "off-thread mutation rejected");
        helper.assertValueEqual(VillageDiplomacyApi.query(level, a, b).relation(), VillageRelation.HOSTILE, "failed changes preserve state");
        registry.archive(b);
        helper.assertValueEqual(VillageDiplomacyApi.query(level, a, b).relation(), VillageRelation.NEUTRAL, "archived relation inactive");
        helper.assertValueEqual(VillageDiplomacyApi.truce(level, a, b),
                VillageDiplomacyApi.ChangeResult.INVALID_VILLAGE, "archived mutation rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mergesUseLatestExplicitChangeAndUndoDoesNotCopyAgreements(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var a = registry.create(0, level.dimension().location(), BlockPos.ZERO, "");
        var b = registry.create(0, level.dimension().location(), BlockPos.ZERO, "");
        var c = registry.create(0, level.dimension().location(), BlockPos.ZERO, "");
        VillageDiplomacyApi.set(level, a, c, VillageRelation.HOSTILE);
        VillageDiplomacyApi.set(level, b, c, VillageRelation.NEUTRAL);
        VillageDiplomacyApi.truce(level, a, b, 0);
        helper.assertTrue(registry.merge(a, b), "merge accepted");
        helper.assertValueEqual(VillageDiplomacyApi.query(level, a, c).relation(), VillageRelation.NEUTRAL, "latest neutral reset wins");
        helper.assertValueEqual(VillageDiplomacyApi.query(level, a, b).relation(), VillageRelation.NEUTRAL, "self relation removed");
        VillageDiplomacyApi.set(level, a, c, VillageRelation.HOSTILE);
        helper.assertTrue(registry.undoMerge(a), "undo accepted");
        helper.assertValueEqual(VillageDiplomacyApi.query(level, a, c).relation(), VillageRelation.NEUTRAL, "restored source gets no inherited agreement");
        helper.assertValueEqual(VillageDiplomacyApi.query(level, b, c).relation(), VillageRelation.HOSTILE, "survivor retains agreement");
        registry.removeVillage(b);
        helper.assertValueEqual(VillageDiplomacyApi.query(level, b, c).relation(), VillageRelation.NEUTRAL, "removed village inactive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedDataSurvivesDiskReloadAndDeadline(GameTestHelper helper) throws Exception {
        var directory = Files.createTempDirectory("village-diplomacy-");
        var provider = helper.getLevel().registryAccess();
        var factory = new SavedData.Factory<>(VillageDiplomacySavedData::new, VillageDiplomacySavedData::load, DataFixTypes.LEVEL);
        var pair = VillageDiplomacySavedData.Pair.of(VillageAllegianceId.random(), VillageAllegianceId.random());
        try {
            var storage = new DimensionDataStorage(directory.toFile(), helper.getLevel().getServer().getFixerUpper(), provider);
            var data = storage.computeIfAbsent(factory, "diplomacy");
            data.set(pair, VillageRelation.HOSTILE, 0, 100);
            data.set(pair, VillageRelation.TRUCE, 50, 100);
            storage.save();
            IOUtilities.waitUntilIOWorkerComplete();
            var reloadedStorage = new DimensionDataStorage(directory.toFile(), helper.getLevel().getServer().getFixerUpper(), provider);
            var restored = reloadedStorage.computeIfAbsent(factory, "diplomacy");
            helper.assertValueEqual(restored.query(pair, 149).relation(), VillageRelation.TRUCE, "disk reload preserves truce");
            helper.assertValueEqual(restored.query(pair, 149).expiresAt().orElseThrow(), 150L, "absolute deadline survives");
            helper.assertValueEqual(restored.query(pair, 150).relation(), VillageRelation.HOSTILE, "restored deadline expires exactly");
            restored.set(pair, VillageRelation.TRUCE, 0, 160);
            var indefinite = VillageDiplomacySavedData.load(restored.save(new CompoundTag(), provider), provider);
            helper.assertValueEqual(indefinite.query(pair, Long.MAX_VALUE).relation(), VillageRelation.TRUCE, "indefinite survives");
        } finally {
            IOUtilities.waitUntilIOWorkerComplete();
            Files.deleteIfExists(directory.resolve("diplomacy.dat"));
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedEntriesAndFutureFormatsArePreservedSafely(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var data = new VillageDiplomacySavedData();
        var pair = VillageDiplomacySavedData.Pair.of(VillageAllegianceId.random(), VillageAllegianceId.random());
        data.set(pair, VillageRelation.HOSTILE, 0, 0);
        var root = data.save(new CompoundTag(), provider);
        var bad = new CompoundTag();
        bad.putString("State", "ALLIED");
        root.getList("Relations", Tag.TAG_COMPOUND).add(bad);
        var loaded = VillageDiplomacySavedData.load(root, provider);
        helper.assertValueEqual(loaded.query(pair, 0).relation(), VillageRelation.HOSTILE, "valid records survive corrupt neighbors");
        helper.assertFalse(loaded.diagnostics().isEmpty(), "rejection reported");
        helper.assertValueEqual(loaded.save(new CompoundTag(), provider).getList("RejectedEntries", Tag.TAG_COMPOUND).size(),
                1, "rejected entry preserved");
        root.putInt("FormatVersion", 2);
        var future = VillageDiplomacySavedData.load(root, provider);
        helper.assertTrue(future.readOnly(), "future format is read-only");
        helper.assertFalse(future.set(pair, VillageRelation.NEUTRAL, 0, 0), "future mutation rejected");
        helper.assertValueEqual(future.query(pair, 0).relation(), VillageRelation.NEUTRAL, "uninterpreted data grants no aggression");
        helper.assertValueEqual(future.save(new CompoundTag(), provider), root, "future root preserved exactly");
        helper.succeed();
    }
}
