package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class CourierChunkLoadingGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private CourierChunkLoadingGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void courierWindowIncludesCurrentRouteAndStorageEndpoints(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        ChunkPos current = villager.chunkPosition();
        BlockPos routeTarget = chunkCenter(current.x + 1, current.z, villager.getBlockY());
        BlockPos input = chunkCenter(current.x + 2, current.z, villager.getBlockY());
        BlockPos output = chunkCenter(current.x + 3, current.z, villager.getBlockY());
        UUID hirerId = UUID.randomUUID();

        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        data.assign(record(level, villager, hirerId, input, AssignedStorageService.INPUT_PURPOSE, 0));
        data.assign(record(level, villager, hirerId, output, AssignedStorageService.OUTPUT_PURPOSE, 1));

        HiredRoute route = new HiredRoute(List.of(villager.blockPosition(), routeTarget), false);
        CompoundTag state = new CompoundTag();
        state.putInt("CourierRouteIndex", 1);
        Set<ChunkPos> desired = CourierRouteChunkLoader.desiredChunks(level, villager, state, route);

        helper.assertValueEqual(desired.size(), 4, "courier ticket window must remain bounded");
        helper.assertTrue(desired.contains(current), "window should tick the courier's current chunk");
        helper.assertTrue(desired.contains(new ChunkPos(routeTarget)), "window should tick the next route node");
        helper.assertTrue(desired.contains(new ChunkPos(input)), "window should load a current input endpoint");
        helper.assertTrue(desired.contains(new ChunkPos(output)), "window should load a current output endpoint");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void courierWindowTargetsExpandedBranchNode(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ChunkPos current = villager.chunkPosition();
        BlockPos routeEnd = chunkCenter(current.x + 1, current.z, villager.getBlockY());
        BlockPos branchEnd = routeEnd.offset(0, 0, 12);
        HiredRoute route = new HiredRoute(
                List.of(villager.blockPosition(), routeEnd),
                false,
                List.of(new HiredRoute.Branch(routeEnd, branchEnd)));
        CompoundTag state = new CompoundTag();
        state.putInt("CourierRouteIndex", 2);

        Set<ChunkPos> desired = CourierRouteChunkLoader.desiredChunks(
                helper.getLevel(), villager, state, route);

        helper.assertTrue(
                desired.contains(new ChunkPos(branchEnd)),
                "courier ticket window should follow the expanded branch traversal node");
        helper.succeed();
    }
    @GameTest(template = EMPTY_TEMPLATE)
    public static void courierWindowPrioritizesStorageAtActiveBranch(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        ChunkPos current = villager.chunkPosition();
        BlockPos routeEnd = chunkCenter(current.x + 1, current.z, villager.getBlockY());
        BlockPos branchEnd = new BlockPos(
                ((current.x + 1) << 4) + 15,
                villager.getBlockY(),
                (current.z << 4) + 8);
        BlockPos activeBranchInput = branchEnd.east();
        BlockPos distantInput = chunkCenter(current.x + 4, current.z, villager.getBlockY());
        BlockPos distantOutput = chunkCenter(current.x + 5, current.z, villager.getBlockY());
        UUID hirerId = UUID.randomUUID();

        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        data.assign(record(level, villager, hirerId, distantInput, AssignedStorageService.INPUT_PURPOSE, 0));
        data.assign(record(level, villager, hirerId, activeBranchInput, AssignedStorageService.INPUT_PURPOSE, 1));
        data.assign(record(level, villager, hirerId, distantOutput, AssignedStorageService.OUTPUT_PURPOSE, 2));

        HiredRoute route = new HiredRoute(
                List.of(villager.blockPosition(), routeEnd),
                false,
                List.of(new HiredRoute.Branch(routeEnd, branchEnd)));
        CompoundTag state = new CompoundTag();
        state.putInt("CourierRouteIndex", 2);

        Set<ChunkPos> desired = CourierRouteChunkLoader.desiredChunks(level, villager, state, route);

        helper.assertTrue(
                desired.contains(new ChunkPos(activeBranchInput)),
                "the active branch input must take ticket priority over distant fallback assignments");
        helper.assertTrue(desired.size() <= 4, "active branch ticket window must remain bounded");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void courierWindowRestoresPersistedDetourTarget(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ChunkPos current = villager.chunkPosition();
        BlockPos routeTarget = chunkCenter(current.x + 1, current.z, villager.getBlockY());
        BlockPos storedTarget = chunkCenter(current.x + 2, current.z, villager.getBlockY());
        HiredRoute route = new HiredRoute(List.of(villager.blockPosition(), routeTarget), false);
        CompoundTag restoredState = new CompoundTag();
        restoredState.putInt("CourierRouteIndex", 1);
        restoredState.putLong("CourierStorageTarget", storedTarget.asLong());

        Set<ChunkPos> desired =
                CourierRouteChunkLoader.desiredChunks(helper.getLevel(), villager, restoredState, route);

        helper.assertTrue(
                desired.contains(new ChunkPos(storedTarget)),
                "a persisted storage detour should be restored after the villager loads");
        helper.assertTrue(desired.size() <= 4, "restored ticket window must remain bounded");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void assignedTransferResolvesReplacementContainer(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        BlockPos storagePos = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlock(storagePos, Blocks.CHEST.defaultBlockState(), 3);
        Container first = (Container) level.getBlockEntity(storagePos);
        first.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        AssignedStorageSavedData.get(level).assign(record(
                level,
                villager,
                UUID.randomUUID(),
                storagePos,
                AssignedStorageService.INPUT_PURPOSE,
                0));

        AtomicInteger received = new AtomicInteger();
        int firstMoved = AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                storagePos,
                stack -> stack.is(Items.COBBLESTONE),
                2,
                stack -> {
                    received.addAndGet(stack.getCount());
                    return ItemStack.EMPTY;
                });

        level.setBlock(storagePos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(storagePos, Blocks.CHEST.defaultBlockState(), 3);
        Container replacement = (Container) level.getBlockEntity(storagePos);
        replacement.setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        int secondMoved = AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                storagePos,
                stack -> stack.is(Items.COBBLESTONE),
                3,
                stack -> {
                    received.addAndGet(stack.getCount());
                    return ItemStack.EMPTY;
                });

        helper.assertValueEqual(firstMoved, 2, "initial live container transfer");
        helper.assertValueEqual(secondMoved, 3, "replacement live container transfer");
        helper.assertValueEqual(received.get(), 5, "villager should receive only extracted live items");
        helper.assertValueEqual(
                replacement.getItem(0).getCount(),
                4,
                "replacement container should be dynamically updated");
        helper.succeed();
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) {
            throw new IllegalStateException("Failed to create villager");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static BlockPos chunkCenter(int chunkX, int chunkZ, int y) {
        return new BlockPos((chunkX << 4) + 8, y, (chunkZ << 4) + 8);
    }

    private static AssignedContainerRecord record(
            net.minecraft.server.level.ServerLevel level,
            Villager villager,
            UUID hirerId,
            BlockPos pos,
            String purpose,
            int priority) {
        return new AssignedContainerRecord(
                level.dimension(),
                pos.immutable(),
                villager.getUUID(),
                hirerId,
                purpose,
                priority,
                "valid");
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
