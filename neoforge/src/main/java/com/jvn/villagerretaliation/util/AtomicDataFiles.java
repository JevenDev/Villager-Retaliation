package com.jvn.villagerretaliation.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.IOUtilities;

public final class AtomicDataFiles {
    private static final int MAX_RETRIES = 3;

    private AtomicDataFiles() { }

    public static void writeCompressed(CompoundTag snapshot, Path target) throws IOException {
        retry(target, () -> IOUtilities.writeNbtCompressed(snapshot, target), Thread::sleep);
    }

    public static void backup(Path source, Path target) throws IOException {
        retry(target, () -> IOUtilities.atomicWrite(target, stream -> Files.copy(source, stream)), Thread::sleep);
    }

    static void retry(Path target, Write write, Pause pause) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                write.run();
                return;
            } catch (AccessDeniedException denied) {
                if (attempt >= MAX_RETRIES || Files.isDirectory(target)) throw denied;
                try {
                    pause.sleep(5L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    var failure = new InterruptedIOException("Interrupted while retrying data replacement");
                    failure.initCause(interrupted);
                    failure.addSuppressed(denied);
                    throw failure;
                }
            }
        }
    }

    @FunctionalInterface interface Write { void run() throws IOException; }
    @FunctionalInterface interface Pause { void sleep(long millis) throws InterruptedException; }
}
