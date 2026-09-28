package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementRecoveryGameTests {
    @GameTest(template = "empty")
    public static void futureFormatRemainsByteExactThroughStorageSaveAndRestart(GameTestHelper helper) throws IOException {
        var root = root(helper, town(helper, 0));
        root.putInt("FormatVersion", Integer.MAX_VALUE);
        root.putString("UnknownFutureField", "preserve");
        try (var disk = new Disk(helper, root)) {
            byte[] before = Files.readAllBytes(disk.file());
            var data = disk.load();
            helper.assertTrue(data.readOnly(), "future formats disable settlement mutation");
            helper.assertFalse(data.canEstablish(helper.getLevel().dimension().location(), BlockPos.ZERO), "founding is blocked");
            data.setDirty();
            disk.save();
            helper.assertTrue(Arrays.equals(before, Files.readAllBytes(disk.file())), "normal disk save preserves every byte");
            helper.assertValueEqual(data.save(new CompoundTag(), helper.getLevel().registryAccess()), root, "serialization preserves unknown fields");
            helper.assertTrue(disk.load().readOnly(), "restart retains the protection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unreadableFileCannotFallBackToWritableEmptyData(GameTestHelper helper) throws IOException {
        try (var disk = new Disk(helper, root(helper))) {
            byte[] corrupt = new byte[] {31, -117, 0, 7, 9};
            Files.write(disk.file(), corrupt);
            var data = disk.load();
            helper.assertTrue(data.readOnly(), "Minecraft's failed read creates a protected instance");
            data.setDirty();
            disk.save();
            helper.assertTrue(Arrays.equals(corrupt, Files.readAllBytes(disk.file())), "undecodable bytes are never overwritten");
            helper.assertTrue(disk.load().readOnly(), "protection survives a restart");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quarantinedRecordSurvivesHealthyMutationAndCanBeRepaired(GameTestHelper helper) throws IOException {
        var good = town(helper, 0);
        var bad = town(helper, 256);
        var input = root(helper, good, bad);
        input.getList("Settlements", Tag.TAG_COMPOUND).getCompound(1).putString("Stage", "INVALID");
        try (var disk = new Disk(helper, input)) {
            var data = disk.load();
            helper.assertFalse(data.readOnly(), "known identity and plots allow isolation");
            helper.assertValueEqual(data.settlements().size(), 1, "the unrelated town stays usable");
            helper.assertTrue(data.get(bad.id()).isEmpty(), "the invalid record is unavailable");
            helper.assertFalse(data.canEstablish(bad.dimension(), bad.center()), "quarantined plots stay reserved");
            var registry = new VillageAllegianceRegistrySavedData();
            registry.ensureRecord(bad.id(), 0, bad.dimension(), bad.center());
            helper.assertTrue(data.establish(registry, bad.id(), UUID.randomUUID(), new BlockPos(512, 64, 0), 0).isEmpty(),
                    "the quarantined identity cannot be reused elsewhere");
            data.get(good.id()).orElseThrow().economy().credit(CurrencyAmount.of(7, 1));
            disk.save();
            data = disk.load();
            helper.assertValueEqual(data.get(good.id()).orElseThrow().economy().balance(), CurrencyAmount.of(7, 1),
                    "healthy mutation persists across the actual disk boundary");
            var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getList("Settlements", Tag.TAG_COMPOUND).getCompound(1),
                    input.getList("Settlements", Tag.TAG_COMPOUND).getCompound(1), "quarantine preserves the exact invalid payload");
            helper.assertValueEqual(saved.getCompound("RecoveryOriginalRoot"), input, "the original collection is retained");
            saved.getList("Settlements", Tag.TAG_COMPOUND).getCompound(1).putString("Stage", "CAMP");
            disk.write(saved);
            var repaired = disk.load();
            helper.assertTrue(repaired.recoveryDiagnostics().isEmpty(), "repair and restart leave recovery mode");
            helper.assertTrue(repaired.get(bad.id()).isPresent(), "the repaired town is available again");
            helper.assertValueEqual(repaired.get(good.id()).orElseThrow().economy().balance(), CurrencyAmount.of(7, 1),
                    "repair does not roll back healthy progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateIdentitiesQuarantineEveryContender(GameTestHelper helper) throws IOException {
        var first = town(helper, 0);
        var other = town(helper, 256);
        var root = root(helper, first, other);
        var entries = root.getList("Settlements", Tag.TAG_COMPOUND);
        var duplicate = first.save(helper.getLevel().registryAccess());
        duplicate.putUUID("Leader", UUID.randomUUID());
        entries.add(duplicate);
        try (var disk = new Disk(helper, root)) {
            var data = disk.load();
            helper.assertTrue(data.get(first.id()).isEmpty(), "no duplicate wins by load order");
            helper.assertTrue(data.get(other.id()).isPresent(), "unrelated identity survives");
            data.setDirty();
            disk.save();
            helper.assertValueEqual(disk.load().recoveryDiagnostics().size(), 2, "both payloads survive restart");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void conflictingPlotsQuarantineBothOwnersWithoutLosingClaims(GameTestHelper helper) throws IOException {
        var first = town(helper, 0);
        var conflict = town(helper, 16);
        var unrelated = town(helper, 256);
        try (var disk = new Disk(helper, root(helper, first, conflict, unrelated))) {
            var data = disk.load();
            helper.assertValueEqual(data.settlements().size(), 1, "both conflicting owners are withheld");
            helper.assertTrue(data.get(unrelated.id()).isPresent(), "unrelated plots remain usable");
            helper.assertFalse(data.canEstablish(first.dimension(), first.center()), "first claims are reserved");
            helper.assertFalse(data.canEstablish(conflict.dimension(), conflict.center()), "second claims are reserved");
            data.setDirty();
            disk.save();
            helper.assertValueEqual(disk.load().recoveryDiagnostics().size(), 2, "conflicts persist across disk save");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownBoundariesProtectTheEntireRoot(GameTestHelper helper) throws IOException {
        var root = root(helper, town(helper, 0), town(helper, 256));
        root.getList("Settlements", Tag.TAG_COMPOUND).getCompound(1).remove("Id");
        try (var disk = new Disk(helper, root)) {
            var before = Files.readAllBytes(disk.file());
            var data = disk.load();
            helper.assertTrue(data.readOnly(), "an unknown identity cannot be isolated safely");
            helper.assertTrue(data.settlements().isEmpty(), "no unsafe runtime records escape recovery");
            data.setDirty();
            disk.save();
            helper.assertTrue(Arrays.equals(before, Files.readAllBytes(disk.file())), "the entire file is protected");
            disk.write(root(helper, town(helper, 512)));
            helper.assertFalse(disk.load().readOnly(), "restoring a valid backup recovers the storage");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedBoundaryTypesNeverBecomeEmptyClaims(GameTestHelper helper) throws IOException {
        for (String key : java.util.List.of("Plots", "Migrated", "FormatVersion")) {
            var root = root(helper, town(helper, 0), town(helper, 256));
            if (key.equals("FormatVersion")) root.putString(key, "56");
            else root.getList("Settlements", Tag.TAG_COMPOUND).getCompound(1).getCompound("Territory").putString(key, "invalid");
            try (var disk = new Disk(helper, root)) {
                var before = Files.readAllBytes(disk.file());
                var data = disk.load();
                helper.assertTrue(data.readOnly(), "malformed " + key + " protects all boundaries");
                data.setDirty();
                disk.save();
                helper.assertTrue(Arrays.equals(before, Files.readAllBytes(disk.file())), "malformed boundary bytes survive saving");
            }
        }
        helper.succeed();
    }

    private static SettlementRecord town(GameTestHelper helper, int x) {
        return new SettlementRecord(VillageAllegianceId.random(), helper.getLevel().dimension().location(),
                new BlockPos(x, 64, 0), UUID.randomUUID(), 0, () -> {});
    }

    private static CompoundTag root(GameTestHelper helper, SettlementRecord... towns) {
        var root = new SettlementSavedData().save(new CompoundTag(), helper.getLevel().registryAccess());
        var entries = new ListTag();
        for (var town : towns) entries.add(town.save(helper.getLevel().registryAccess()));
        root.put("Settlements", entries);
        return root;
    }

    private static final class Disk implements AutoCloseable {
        private final GameTestHelper helper;
        private final Path directory;
        private DimensionDataStorage storage;

        Disk(GameTestHelper helper, CompoundTag input) throws IOException {
            this.helper = helper;
            this.directory = Files.createTempDirectory("settlement-recovery-");
            write(input);
        }

        Path file() { return this.directory.resolve("villagerretaliation_settlements.dat"); }

        void write(CompoundTag input) throws IOException {
            IOUtilities.waitUntilIOWorkerComplete();
            var wrapper = new CompoundTag();
            wrapper.put("data", input);
            NbtUtils.addCurrentDataVersion(wrapper);
            NbtIo.writeCompressed(wrapper, file());
        }

        SettlementSavedData load() {
            IOUtilities.waitUntilIOWorkerComplete();
            this.storage = new DimensionDataStorage(this.directory.toFile(), this.helper.getLevel().getServer().getFixerUpper(),
                    this.helper.getLevel().registryAccess());
            return SettlementSavedData.get(this.storage, this.directory);
        }

        void save() {
            this.storage.save();
            IOUtilities.waitUntilIOWorkerComplete();
        }

        @Override
        public void close() throws IOException {
            IOUtilities.waitUntilIOWorkerComplete();
            Files.deleteIfExists(file());
            Files.delete(this.directory);
        }
    }
}
