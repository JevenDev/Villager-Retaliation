package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worker_performance")
@PrefixGameTestTemplate(false)
public final class ContainerClaimPerformanceGameTests {
    @GameTest(template = "empty", timeoutTicks = 250, batch = "claim_expiration")
    public static void renewedAndNewClaimsSurviveExpirationOfOlderClaims(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        var candidate = new VillagerInventoryOverflowService.ContainerCandidate(position, new SimpleContainer(3), List.of(position));
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var provide = VillagerFilterPolicy.TransferOperation.PROVIDE;
        ContainerTransferClaimLedger.replaceAll(level, first, provide, Map.of(position, List.of(new ItemStack(Items.IRON_INGOT, 12))));
        ContainerTransferClaimLedger.replaceAll(level, second, provide, Map.of(position, List.of(new ItemStack(Items.IRON_INGOT, 8))));
        helper.assertValueEqual(ContainerTransferClaimLedger.count(level, candidate, null, provide, stack -> stack.is(Items.IRON_INGOT)), 20,
                "both owners reserve their cargo");
        helper.runAfterDelay(100, () -> ContainerTransferClaimLedger.replaceAll(level, first, provide,
                Map.of(position, List.of(new ItemStack(Items.IRON_INGOT, 4)))));
        helper.runAfterDelay(201, () -> {
            try {
                helper.assertValueEqual(ContainerTransferClaimLedger.count(level, candidate, null, provide, stack -> stack.is(Items.IRON_INGOT)), 4,
                        "only the older owner's claim expires");
                var snapshot = ContainerTransferClaimLedger.snapshot(level, first, provide);
                snapshot.get(position).getFirst().setCount(64);
                helper.assertValueEqual(ContainerTransferClaimLedger.count(level, candidate, null, provide, stack -> true), 4,
                        "claim snapshots remain defensive copies");
                ContainerTransferClaimLedger.release(first);
                ContainerTransferClaimLedger.replaceAll(level, second, provide, Map.of(position, List.of(new ItemStack(Items.IRON_INGOT, 7))));
                helper.assertValueEqual(ContainerTransferClaimLedger.count(level, candidate, null, provide, stack -> true), 7,
                        "new claims are visible immediately after cleanup and release");
                ContainerTransferClaimLedger.release(second, provide);
                helper.assertValueEqual(ContainerTransferClaimLedger.count(level, candidate, null, provide, stack -> true), 0,
                        "operation-specific release removes the remaining claim");
                helper.succeed();
            } finally {
                ContainerTransferClaimLedger.release(first);
                ContainerTransferClaimLedger.release(second);
            }
        });
    }
}
