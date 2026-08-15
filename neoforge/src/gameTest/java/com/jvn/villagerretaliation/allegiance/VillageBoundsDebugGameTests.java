package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageBoundsDebugGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillageBoundsDebugGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void subscriptionRequiresOperatorAndResistsResetSpam(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        ServerboundRequestLimiter.clear(playerId);

        try {
            helper.assertFalse(
                    VillageBoundsDebugService.canUseVillageBoundsDebug(player),
                    "a non-operator player must not receive the village bounds debug feed");
            helper.assertValueEqual(
                    VillageBoundsDebugService.REQUIRED_PERMISSION_LEVEL,
                    2,
                    "the village bounds debug feed must require operator permission");
            helper.assertTrue(
                    VillageBoundsDebugService.tryAcquireSubscriptionPermit(player),
                    "the first enable should acquire the cooldown");
            helper.assertFalse(
                    VillageBoundsDebugService.tryAcquireSubscriptionPermit(player),
                    "resetting subscription state must not bypass the enable cooldown");
        } finally {
            ServerboundRequestLimiter.clear(playerId);
            player.discard();
        }
        helper.succeed();
    }
}
