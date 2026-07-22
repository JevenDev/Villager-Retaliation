package com.jvn.villagerretaliation.duel;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class DuelGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private DuelGameTests() {}

    @GameTest(template = EMPTY_TEMPLATE)
    public static void recordsAreIsolatedPerPlayerAndLossStreakRefuses(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.markStarted(villager, first, 100L);
        data.complete(villager, first, DuelResult.PLAYER_WIN);
        data.complete(villager, first, DuelResult.PLAYER_WIN);
        DuelSavedData.DuelRecord refused = data.complete(villager, first, DuelResult.PLAYER_WIN);
        helper.assertTrue(refused.refuses() && refused.consecutiveLosses() == 3,
                "three consecutive losses should permanently refuse the opponent");
        helper.assertValueEqual(data.record(villager, second), DuelSavedData.DuelRecord.EMPTY,
                "duel records must remain isolated per player");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villagerVictoryResetsLossStreakAndQueuesReactions(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        data.complete(villager, player, DuelResult.PLAYER_WIN);
        DuelSavedData.DuelRecord record = data.complete(villager, player, DuelResult.VILLAGER_WIN);
        helper.assertValueEqual(record.consecutiveLosses(), 0, "villager victory should reset its loss streak");
        helper.assertValueEqual(data.consumeReaction(villager, player), DuelSavedData.Reaction.GLOAT,
                "victory should queue a one-time gloat");
        helper.assertValueEqual(data.consumeReaction(villager, player), DuelSavedData.Reaction.SULK,
                "later queued reactions should remain available");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void recordsHistoryAndStoryAcknowledgementsSerialize(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID speaker = UUID.randomUUID();
        UUID village = UUID.randomUUID();
        data.markStarted(villager, player, 500L);
        DuelSavedData.DuelRecord record = data.complete(villager, player, DuelResult.VILLAGER_WIN);
        UUID eventId = UUID.randomUUID();
        data.remember(new DuelSavedData.DuelMemory(eventId, villager, player, "Ada", "Player",
                DuelResult.VILLAGER_WIN, 16, 600L, new BlockPos(1, 2, 3).asLong(), village,
                record.villagerWins(), record.villagerLosses()));
        data.acknowledgeStory(speaker, player, eventId);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        DuelSavedData loaded = DuelSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.record(villager, player), record, "duel record did not survive NBT");
        helper.assertValueEqual(loaded.history().size(), 1, "duel history did not survive NBT");
        helper.assertTrue(loaded.storyAcknowledged(speaker, player, eventId),
                "story acknowledgement did not survive NBT");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villageHistoryIsCappedAtSixtyFour(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID(), player = UUID.randomUUID(), village = UUID.randomUUID();
        for (int index = 0; index < 70; index++) {
            data.remember(new DuelSavedData.DuelMemory(UUID.randomUUID(), villager, player, "Ada", "Player",
                    DuelResult.DRAW, 0, index, BlockPos.ZERO.asLong(), village, 0, 0));
        }
        helper.assertValueEqual(data.history().size(), 64, "village duel history must remain bounded");
        helper.succeed();
    }
}
