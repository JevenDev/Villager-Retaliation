package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
