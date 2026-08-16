package com.jvn.villagerretaliation.quest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class PendingPartyRewardGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private PendingPartyRewardGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void pendingRewardNeverSelectsNearbyReplacement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager original = spawnVillager(helper, new BlockPos(2, 1, 2));
        Villager replacement = spawnVillager(helper, new BlockPos(3, 1, 2));
        UUID originalId = original.getUUID();
        VillagerQuestSavedData.QuestProgress progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(
                originalId,
                helper.getLevel().dimension(),
                original.blockPosition(),
                helper.getLevel().getGameTime());

        helper.assertValueEqual(
                VillagerQuestService.pendingPartyRewardProvider(player, progress),
                original,
                "the live issuer must deliver its own pending reward");

        original.discard();
        helper.assertTrue(replacement.isAlive(), "the nearby replacement must remain available");
        helper.assertTrue(
                VillagerQuestService.pendingPartyRewardProvider(player, progress) == null,
                "a missing issuer must not silently transfer a pending reward to a nearby villager");
        helper.assertValueEqual(
                progress.startedVillagerId(),
                originalId,
                "automatic delivery must preserve the quest's issuer identity");
        helper.succeed();
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new GameTestAssertException("Could not create villager");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) {
            throw new GameTestAssertException("Could not add villager to level");
        }
        level.tickNonPassenger(villager);
        return villager;
    }
}
