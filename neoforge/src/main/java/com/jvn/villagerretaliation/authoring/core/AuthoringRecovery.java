package com.jvn.villagerretaliation.authoring.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

final class AuthoringRecovery {
    private static final Map<Path, Slot> slots = new WeakHashMap<>();
    private static final class Slot { long generation; }

    static synchronized AuthoringWorkspace.RecoveryWrite prepare(Path destination, Supplier<byte[]> content) {
        Path key = destination;
        Slot slot = null;
        for (var entry : slots.entrySet()) {
            Path candidate = entry.getKey();
            if (destination.equals(candidate)) { key = candidate; slot = entry.getValue(); break; }
        }
        if (slot == null) { slot = new Slot(); slots.put(key, slot); }
        return new Write(key, slot, ++slot.generation, content);
    }

    // pending writes retain the canonical key so reopened workspaces share cancellation
    private record Write(Path destination, Slot slot, long generation, Supplier<byte[]> content) implements AuthoringWorkspace.RecoveryWrite {
        @Override public void write() throws IOException {
            synchronized (AuthoringRecovery.class) { if (slot.generation != generation) return; }
            byte[] bytes = content == null ? null : content.get();
            synchronized (AuthoringRecovery.class) {
                if (slot.generation != generation) return;
                if (bytes == null) {
                    AuthoringWorkspace.rejectSymlinks(destination);
                    Files.deleteIfExists(destination);
                } else AuthoringWorkspace.atomicWrite(destination, bytes);
            }
        }
    }
}
