package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.network.HiredDebugPreviewSyncPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class HiredDebugPreviewGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private HiredDebugPreviewGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sharedStoragePreviewIncludesEveryVillagerName(GameTestHelper helper) {
        BlockPos storage = helper.absolutePos(new BlockPos(2, 2, 2));
        AssignedContainerRecord first = assignedStorage(helper, storage, UUID.randomUUID());
        AssignedContainerRecord second = assignedStorage(helper, storage, UUID.randomUUID());

        var entries = HiredDebugPreviewService.storageEntries(List.of(
                new HiredDebugPreviewService.NamedStorageAssignments("Alice", List.of(first)),
                new HiredDebugPreviewService.NamedStorageAssignments("Bob", List.of(second))));

        helper.assertValueEqual(entries.size(), 1, "shared container preview count");
        helper.assertValueEqual(entries.getFirst().ownerName(), "Alice, Bob", "shared container owner names");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sharedJobSitePreviewIncludesEveryVillagerName(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos max = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos center = helper.absolutePos(new BlockPos(2, 3, 2));
        ResourceLocation dimension = helper.getLevel().dimension().location();
        var first = workArea(dimension, min, max, center, "Alice", "Farmer");
        var second = workArea(dimension, min, max, center, "Bob", "Farmer");

        var entries = HiredDebugPreviewService.workAreaEntries(List.of(first, second));

        helper.assertValueEqual(entries.size(), 1, "shared job site preview count");
        helper.assertValueEqual(entries.getFirst().ownerName(), "Alice, Bob", "shared job site owner names");
        helper.succeed();
    }

    private static HiredDebugPreviewSyncPayload.WorkAreaEntry workArea(
            ResourceLocation dimension,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            String ownerName,
            String jobName) {
        return new HiredDebugPreviewSyncPayload.WorkAreaEntry(
                dimension,
                min,
                max,
                center,
                true,
                min,
                false,
                max,
                false,
                ownerName,
                jobName);
    }

    private static AssignedContainerRecord assignedStorage(GameTestHelper helper, BlockPos pos, UUID villagerId) {
        return new AssignedContainerRecord(
                helper.getLevel().dimension(),
                pos,
                villagerId,
                UUID.randomUUID(),
                AssignedStorageService.GENERAL_PURPOSE,
                0,
                "valid");
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }
        for (Path candidate : List.of(
                Path.of("src/main/gameteststructures"),
                Path.of("../src/main/gameteststructures"),
                Path.of("neoforge/src/main/gameteststructures"))) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}
