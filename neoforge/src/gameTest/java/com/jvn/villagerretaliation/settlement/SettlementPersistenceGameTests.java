package com.jvn.villagerretaliation.settlement;

import java.nio.file.Files;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_persistence")
@PrefixGameTestTemplate(false)
public final class SettlementPersistenceGameTests {
    @GameTest(template = "empty")
    public static void failedSaveRetriesAndPreservesLaterMutations(GameTestHelper helper) throws java.io.IOException {
        var root = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT);
        var directory = Files.createTempDirectory(root, "settlement-save-");
        var blocker = Files.createFile(directory.resolve("blocker"));
        var target = directory.resolve("settlements.dat");
        try {
            var data = new SettlementSavedData();
            data.setDirty();
            data.save(directory.toFile(), helper.getLevel().registryAccess());
            IOUtilities.waitUntilIOWorkerComplete();
            helper.assertTrue(data.isDirty(), "a failed write remains eligible for retry");
            data.save(target.toFile(), helper.getLevel().registryAccess());
            IOUtilities.waitUntilIOWorkerComplete();
            helper.assertFalse(data.isDirty(), "a successful retry clears the failure");
            helper.assertTrue(NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()).contains("data"), "the retry writes readable settlement data");
            data.setDirty();
            data.save(target.toFile(), helper.getLevel().registryAccess());
            data.setDirty();
            IOUtilities.waitUntilIOWorkerComplete();
            helper.assertTrue(data.isDirty(), "completion cannot clear mutations made after snapshot capture");
        } finally {
            IOUtilities.waitUntilIOWorkerComplete();
            Files.deleteIfExists(target);
            Files.deleteIfExists(blocker);
            Files.delete(directory);
        }
        helper.succeed();
    }
}
