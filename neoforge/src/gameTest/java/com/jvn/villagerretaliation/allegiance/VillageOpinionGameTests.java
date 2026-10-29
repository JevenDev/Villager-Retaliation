package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.VillageEntryPayload;
import com.jvn.villagerretaliation.network.VillageOverviewPayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("village_opinion")
@PrefixGameTestTemplate(false)
public final class VillageOpinionGameTests {
    private VillageOpinionGameTests() { }

    @GameTest(template = "empty")
    public static void averageIncludesUnmetAndUnloadedResidents(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = new VillageAllegianceId(UUID.randomUUID());
        var player = UUID.randomUUID();
        var otherPlayer = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        registry.ensureRecord(village, 0, level.dimension().location(), BlockPos.ZERO);
        registry.addOrUpdateResident(village, first, true, 0);
        registry.addOrUpdateResident(village, second, false, 0);
        var reputations = VillagerReputationSavedData.get(level);
        reputations.debugRestoreEntry(first, player, 81);
        reputations.debugRestoreEntry(first, otherPlayer, -100);
        helper.assertValueEqual(VillageOpinion.of(level, village, player).score(), 40.5D,
                "unmet children count as neutral and unloaded residents retain their reputation");
        helper.assertValueEqual(VillageOpinion.of(level, village, otherPlayer).score(), -50.0D,
                "each player sees their own village opinion");
        helper.assertFalse(reputations.hasEntry(second, player), "reading opinion does not create reputation");
        reputations.debugRestoreEntry(second, player, -81);
        helper.assertValueEqual(VillageOpinion.of(level, village, player).score(), 0.0D,
                "positive and negative scores cancel");
        registry.removeResidentEverywhere(first);
        helper.assertValueEqual(VillageOpinion.of(level, village, player).score(), -81.0D,
                "a removed resident no longer contributes");
        registry.removeResidentEverywhere(second);
        helper.assertValueEqual(VillageOpinion.of(level, village, player), VillageOpinion.NEUTRAL,
                "empty villages are neutral");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void opinionUsesWholeRosterAndFollowsMembership(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = new VillageAllegianceId(UUID.randomUUID());
        var destination = new VillageAllegianceId(UUID.randomUUID());
        registry.ensureRecord(village, 0, level.dimension().location(), BlockPos.ZERO);
        registry.ensureRecord(destination, 0, level.dimension().location(), BlockPos.ZERO);
        var player = UUID.randomUUID();
        var reputations = VillagerReputationSavedData.get(level);
        var resident = UUID.randomUUID();
        for (int index = 0; index < 200; index++) {
            var id = index == 0 ? resident : UUID.randomUUID();
            registry.addOrUpdateResident(village, id, true, 0);
            reputations.debugRestoreEntry(id, player, index == 0 ? 200 : 0);
        }
        helper.assertValueEqual(VillageOpinion.of(level, village, player).score(), 1.0D,
                "opinion is not limited by the overview packet roster cap");
        registry.addOrUpdateResident(destination, resident, true, 0);
        helper.assertValueEqual(VillageOpinion.of(level, village, player).score(), 0.0D,
                "moving residents leave their old village average");
        helper.assertValueEqual(VillageOpinion.of(level, destination, player).score(), 200.0D,
                "the destination uses the existing personal reputation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entryRequiresLeavingBeforeRepeating(GameTestHelper helper) {
        var tracker = new VillageEntryTracker();
        var dimension = helper.getLevel().dimension().location();
        var village = new VillageAllegianceId(UUID.randomUUID());
        helper.assertTrue(tracker.update(dimension, village, 0), "first entry displays a banner");
        helper.assertFalse(tracker.update(dimension, village, 10), "remaining inside does not repeat");
        tracker.update(dimension, null, 20);
        helper.assertFalse(tracker.update(dimension, village, 30), "brief boundary crossings do not repeat");
        tracker.update(dimension, null, 40);
        helper.assertTrue(tracker.update(dimension, village, 90), "returning after leaving displays a banner");
        helper.assertTrue(tracker.update(dimension, new VillageAllegianceId(UUID.randomUUID()), 100),
                "entering a different village displays its banner");
        helper.assertTrue(tracker.update(ResourceLocation.withDefaultNamespace("the_nether"), village, 110),
                "dimensions do not share entry state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void opinionPacketsRoundTrip(GameTestHelper helper) {
        var dimension = helper.getLevel().dimension().location();
        var opinion = new VillageOpinion(-40.5D, VillagerReputationLevel.SUSPICIOUS);
        var entry = new VillageEntryPayload(dimension, "Golden Fields", opinion);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            VillageEntryPayload.STREAM_CODEC.encode(buffer, entry);
            helper.assertValueEqual(VillageEntryPayload.STREAM_CODEC.decode(buffer), entry,
                    "entry packet retains the name, dimension and personal average");
            var overview = new VillageOverviewPayload(BlockPos.ZERO, dimension, true, "Golden Fields",
                    VillageLifecycleState.ACTIVE, 2, 0, 0, 2, 0, 0, 0, 1, List.of(), opinion);
            VillageOverviewPayload.STREAM_CODEC.encode(buffer, overview);
            helper.assertValueEqual(VillageOverviewPayload.STREAM_CODEC.decode(buffer), overview,
                    "overview packet retains the opinion");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
}
