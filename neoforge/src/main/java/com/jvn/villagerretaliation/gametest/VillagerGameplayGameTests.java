package com.jvn.villagerretaliation.gametest;

import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerGameplayGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private VillagerGameplayGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredContractIndexesClipboardWorkforce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HiredVillagerIndex.clearRuntimeState();

        ServerPlayer player = fakePlayer(level, "VrWorkerIndex");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, player, 1, 8);
        helper.assertTrue(HiredVillagerContractService.isHiredBy(level, villager, player), "contract should be active");
        helper.assertTrue(HiredVillagerIndex.find(player, villager.getUUID()).isPresent(), "hired villager should be indexed");

        ClipboardWorkforceSnapshot snapshot = ClipboardWorkforceService.snapshot(player);
        helper.assertValueEqual(snapshot.totalHired(), 1, "clipboard workforce total");
        helper.assertValueEqual(snapshot.workers().size(), 1, "clipboard worker rows");
        helper.assertValueEqual(snapshot.workers().getFirst().villagerId(), villager.getUUID(), "clipboard worker id");

        HiredVillagerContractService.endHireContract(level, villager, player);
        helper.assertTrue(HiredVillagerIndex.find(player, villager.getUUID()).isEmpty(), "ended contract should leave index");
        helper.assertValueEqual(ClipboardWorkforceService.snapshot(player).totalHired(), 0, "clipboard total after end");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void clipboardPreviewPacketRequiresHeldClipboard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HiredDebugPreviewService.clearRuntimeState();

        ServerPlayer player = fakePlayer(level, "VrPreviewGuard");
        HiredDebugPreviewService.DebugPreviewSummary rejected =
                HiredDebugPreviewService.setClipboardPreviewEnabled(player, true);
        helper.assertFalse(rejected.enabled(), "preview should reject players without a held clipboard");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VillagerRetaliationItems.CLIPBOARD.get()));
        HiredDebugPreviewService.DebugPreviewSummary accepted =
                HiredDebugPreviewService.setClipboardPreviewEnabled(player, true);
        helper.assertTrue(accepted.enabled(), "preview should accept a held clipboard");

        HiredDebugPreviewService.DebugPreviewSummary repeated =
                HiredDebugPreviewService.setClipboardPreviewEnabled(player, true);
        helper.assertTrue(repeated.enabled(), "repeated enable should stay enabled");

        HiredDebugPreviewService.setClipboardPreviewEnabled(player, false);
        HiredDebugPreviewService.clearRuntimeState();
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
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
        return villager;
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("src/main/gameteststructures"));
        candidates.add(Path.of("../src/main/gameteststructures"));
        candidates.add(Path.of("neoforge/src/main/gameteststructures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}
