package com.jvn.villagerretaliation.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class AtomicDataFilesGameTests {
    private AtomicDataFilesGameTests() { }

    @GameTest(template = "empty", batch = "campaign_atomic_storage")
    public static void temporaryAccessDenialRetriesTheSameSaveWithinABoundedDelay(GameTestHelper helper) throws IOException {
        var attempts = new ArrayList<Integer>();
        var delays = new ArrayList<Long>();
        AtomicDataFiles.retry(Path.of("atomic_retry_fixture.dat"), () -> {
            attempts.add(attempts.size());
            if (attempts.size() < 3) throw new AccessDeniedException("atomic_retry_fixture.dat");
        }, delays::add);
        helper.assertValueEqual(attempts.size(), 3, "two temporary denials precede one successful save");
        helper.assertValueEqual(delays, List.of(5L, 10L), "retry waits stay short and bounded");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_atomic_storage")
    public static void persistentDenialAndOtherFailuresStayVisible(GameTestHelper helper) throws IOException {
        int[] attempts = {0};
        var delays = new ArrayList<Long>();
        var denied = new AccessDeniedException("atomic_retry_fixture.dat");
        try {
            AtomicDataFiles.retry(Path.of("atomic_retry_fixture.dat"), () -> { attempts[0]++; throw denied; }, delays::add);
            helper.fail("permanent access denial must escape the writer");
        } catch (AccessDeniedException actual) {
            helper.assertTrue(actual == denied, "the storage failure is preserved");
        }
        helper.assertValueEqual(attempts[0], 4, "persistent denial has one initial attempt and three retries");
        helper.assertValueEqual(delays, List.of(5L, 10L, 20L), "maximum added delay is 35 milliseconds");
        var other = new IOException("unavailable disk");
        attempts[0] = 0; delays.clear();
        try {
            AtomicDataFiles.retry(Path.of("atomic_retry_fixture.dat"), () -> { attempts[0]++; throw other; }, delays::add);
            helper.fail("other storage errors must escape immediately");
        } catch (IOException actual) {
            helper.assertTrue(actual == other, "unrelated failures are not translated or swallowed");
        }
        helper.assertValueEqual(attempts[0], 1, "non-sharing failures are not retried");
        helper.assertTrue(delays.isEmpty(), "non-sharing failures have no retry delay");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_atomic_storage")
    public static void directoryConflictsAreNotRetried(GameTestHelper helper) throws IOException {
        var directory = Files.createTempDirectory("campaign-directory-conflict-");
        var delays = new ArrayList<Long>();
        try {
            try {
                AtomicDataFiles.retry(directory, () -> { throw new AccessDeniedException(directory.toString()); }, delays::add);
                helper.fail("a directory cannot become a save file");
            } catch (AccessDeniedException expected) {
                helper.assertTrue(Files.isDirectory(directory), "conflicting directory is preserved");
            }
            helper.assertTrue(delays.isEmpty(), "a known directory conflict is immediately recoverable");
        } finally { Files.delete(directory); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_atomic_storage")
    public static void interruptedRetriesPreserveTheInterruptAndStorageFailure(GameTestHelper helper) throws IOException {
        var denied = new AccessDeniedException("atomic_retry_fixture.dat");
        try {
            try {
                AtomicDataFiles.retry(Path.of("atomic_retry_fixture.dat"), () -> { throw denied; },
                        millis -> { throw new InterruptedException("server is stopping"); });
                helper.fail("interruption must cancel retrying");
            } catch (InterruptedIOException failure) {
                helper.assertTrue(Thread.currentThread().isInterrupted(), "interruption is retained");
                helper.assertTrue(failure.getCause() instanceof InterruptedException, "interruption remains the cause");
                helper.assertTrue(failure.getSuppressed().length == 1 && failure.getSuppressed()[0] == denied,
                        "original access denial remains available for diagnosis");
            }
        } finally { Thread.interrupted(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_atomic_storage")
    public static void compressedSnapshotsAndPlayerBackupsRoundTrip(GameTestHelper helper) throws IOException {
        var directory = Files.createTempDirectory("campaign-atomic-data-");
        var target = directory.resolve("campaign.dat");
        var backup = directory.resolve("campaign.dat_old");
        try {
            for (int revision = 0; revision < 100; revision++) {
                var snapshot = new CompoundTag(); snapshot.putInt("Revision", revision);
                AtomicDataFiles.writeCompressed(snapshot, target);
                helper.assertValueEqual(NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()), snapshot, "acknowledged snapshot is readable");
            }
            AtomicDataFiles.backup(target, backup);
            var next = new CompoundTag(); next.putInt("Revision", 100);
            AtomicDataFiles.writeCompressed(next, target);
            helper.assertValueEqual(NbtIo.readCompressed(backup, NbtAccounter.unlimitedHeap()).getInt("Revision"), 99, "backup retains the prior player snapshot");
            helper.assertValueEqual(NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()), next, "replacement contains the new snapshot");
        } finally {
            Files.deleteIfExists(backup); Files.deleteIfExists(target); Files.delete(directory);
        }
        helper.succeed();
    }
}
