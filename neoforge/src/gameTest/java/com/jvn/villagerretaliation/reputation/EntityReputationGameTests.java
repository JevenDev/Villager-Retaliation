package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.reputation.runtime.EntityReputationSavedData;
import com.jvn.villagerretaliation.reputation.runtime.ReputationChange;
import com.jvn.villagerretaliation.reputation.runtime.ReputationProfile;
import com.jvn.villagerretaliation.reputation.runtime.ReputationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_reputation")
@PrefixGameTestTemplate(false)
public final class EntityReputationGameTests {
    private EntityReputationGameTests() { }

    @GameTest(template = "empty")
    public static void mobReputationPublishesDurableChangesAndRoundTrips(GameTestHelper helper) {
        var wolf = helper.spawn(EntityType.WOLF, 1, 1, 1);
        var player = helper.makeMockServerPlayerInLevel();
        var data = new EntityReputationSavedData();
        var profile = new ReputationProfile<>("neutral", List.of(
                new ReputationProfile.Tier<>("friend", 10, true),
                new ReputationProfile.Tier<>("enemy", -10, false)));
        var events = new ArrayList<ReputationChange<String>>();
        var service = new ReputationService<>(data, profile, change -> {
            helper.assertValueEqual(data.get(change.entityId(), change.playerId()).reputation(), change.score(),
                    "state is updated before observers run");
            helper.assertTrue(data.isDirty(), "persistence is marked before observers run");
            events.add(change);
        });
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        var result = service.add(wolf.getUUID(), player.getUUID(), 12, 40, pos);
        pos.set(9, 9, 9);
        helper.assertTrue(result.tierChanged() && result.tier().equals("friend"), "a mob can use its own tier profile");
        helper.assertValueEqual(events.size(), 1, "one change publishes one event");
        helper.assertValueEqual(result.position(), new BlockPos(1, 2, 3), "event positions are immutable snapshots");
        service.set(wolf.getUUID(), player.getUUID(), 12, 50, null);
        helper.assertValueEqual(events.size(), 1, "setting the same score does not publish duplicate changes");
        helper.assertValueEqual(data.get(wolf.getUUID(), player.getUUID()).lastInteractionGameTime(), 40L,
                "idempotent sets preserve interaction history");
        var loaded = EntityReputationSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        var reloaded = new ReputationService<>(loaded, profile);
        helper.assertValueEqual(reloaded.score(wolf.getUUID(), player.getUUID()), 12, "generic reputation survives saving");
        helper.assertValueEqual(reloaded.tier(wolf.getUUID(), player.getUUID()), "friend", "tiers use the consumer profile after loading");
        helper.assertValueEqual(reloaded.score(UUID.randomUUID(), player.getUUID()), 0, "different entities have independent reputation");
        helper.assertValueEqual(reloaded.score(wolf.getUUID(), UUID.randomUUID()), 0, "different players have independent reputation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villagerSaveFormatRetainsAllExistingMetadata(GameTestHelper helper) {
        var entry = new CompoundTag();
        var entity = UUID.randomUUID();
        var player = UUID.randomUUID();
        entry.putUUID("Villager", entity);
        entry.putUUID("Player", player);
        entry.putInt("Reputation", -42);
        entry.putLong("LastInteractionGameTime", 100);
        entry.putInt("DirectHits", 3);
        entry.putInt("WitnessedCrimes", 4);
        entry.putLong("LastTradeDay", 7);
        entry.putInt("TradesToday", 2);
        entry.putLong("LastGiftDay", 6);
        com.jvn.villagerretaliation.util.NbtDataUtil.putBlockPos(entry, "LastKnownVillagerPosition", new BlockPos(2, 4, 6));
        var entries = new ListTag();
        entries.add(entry);
        var tag = new CompoundTag();
        tag.put("Entries", entries);
        var data = VillagerReputationSavedData.load(tag, helper.getLevel().registryAccess());
        helper.assertValueEqual(data.save(new CompoundTag(), helper.getLevel().registryAccess()), tag,
                "the shared state preserves the exact villager persistence payload");
        var service = new ReputationService<>(data, VillagerReputationLevel.PROFILE);
        service.add(entity, player, 5, 101, new BlockPos(2, 4, 6));
        helper.assertValueEqual(data.get(entity, player).reputation(), -37, "villager storage implements the shared service");
        helper.assertValueEqual(data.get(entity, player).tradesToday(), 2, "shared changes preserve villager bookkeeping");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierProfilesKeepPriorityAndReadCurrentThresholds(GameTestHelper helper) {
        int[] threshold = {10};
        var profile = new ReputationProfile<>("neutral", List.of(
                new ReputationProfile.Tier<>("first", () -> threshold[0], true),
                new ReputationProfile.Tier<>("second", 5, true)));
        helper.assertValueEqual(profile.tier(10), "first", "authored tier priority is retained");
        threshold[0] = 20;
        helper.assertValueEqual(profile.tier(10), "second", "profiles can follow live configuration");
        helper.assertValueEqual(VillagerReputationLevel.fromReputation(
                com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ROYALTY_THRESHOLD.get()),
                VillagerReputationLevel.ROYALTY, "the existing positive tier boundary stays inclusive");
        helper.assertValueEqual(VillagerReputationLevel.fromReputation(
                com.jvn.villagerretaliation.config.VillagerRetaliationConfig.FEARED_THRESHOLD.get()),
                VillagerReputationLevel.FEARED, "the existing negative tier boundary stays inclusive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villagerFacadeUsesSharedMutationsWithoutLosingIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var player = helper.makeMockServerPlayerInLevel();
        villager.setNoAi(true);
        helper.assertTrue(VillagerReputationManager.setReputation(level, villager, player.getUUID(), 40),
                "the existing live facade still sets reputation");
        VillagerReputationManager.addDialogueReputation(level, villager, player, 5);
        helper.assertValueEqual(VillagerReputationManager.getReputation(level, villager, player.getUUID()), 45,
                "existing dialogue changes use the shared service");
        helper.assertTrue(VillagerReputationManager.setReputation(level, villager.getUUID(), player.getUUID(),
                50, villager.blockPosition()), "offline identity updates use the shared service");
        helper.assertValueEqual(VillagerReputationManager.getReputation(level, villager, player.getUUID()), 50,
                "offline and live changes address the same record");
        helper.succeed();
    }
}
