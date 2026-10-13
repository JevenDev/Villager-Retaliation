package com.jvn.villagerretaliation.party;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Write-ahead, at-most-once attempt ledger. Atomic disk commit precedes every network send. */
public final class CompanionAmbientLedger {
    private static MinecraftServer server;
    private static CompanionAmbientLedger current;
    final CompoundTag root;
    private final Path file;
    private boolean failed;
    CompanionAmbientLedger(Path file, CompoundTag root) { this.file = file; this.root = root; }
    public static synchronized CompanionAmbientLedger get(MinecraftServer value) {
        if (server != value) {
            server = value;
            Path file = value.getWorldPath(LevelResource.ROOT).resolve("data/villagerretaliation_companion_ambient.dat");
            try {
                CompoundTag root = read(file);
                if (!root.isEmpty() && (root.getInt("Version") != 1 || !root.hasUUID("World"))) throw new IOException("Unknown or invalid ambient ledger");
                if (root.isEmpty()) { root.putInt("Version", 1); root.putUUID("World", UUID.randomUUID()); }
                current = new CompanionAmbientLedger(file, root);
            } catch (Exception e) {
                current = new CompanionAmbientLedger(file, new CompoundTag()); current.failed = true;
                com.mojang.logging.LogUtils.getLogger().error("Companion ambient ledger unavailable; all callouts suppressed", e);
            }
        }
        return current;
    }
    static CompoundTag read(Path file) throws IOException {
        Path marker=file.resolveSibling(file.getFileName()+".identity");
        if (!Files.exists(file) && Files.exists(marker)) throw new IOException("Ambient ledger missing beside its identity receipt");
        CompoundTag root=Files.exists(file)?NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()):new CompoundTag();
        if (!root.isEmpty() && (!Files.exists(marker) || !Files.readString(marker).equals(identity(root))))
            throw new IOException("Ambient ledger identity/revision mismatch; restore both files from the same backup");
        return root;
    }
    private static String identity(CompoundTag root) { return root.getUUID("World")+"/"+root.getLong("Revision"); }
    public long clock() { return root.getLong("Clock"); }
    void tick() { root.putLong("Clock", clock() + 1); }
    public boolean available() { return !failed; }
    CompoundTag section(String name) {
        if (!root.contains(name)) root.put(name, new CompoundTag()); return root.getCompound(name);
    }
    public String instance(String dimension, String structure, int x, int z) {
        return root.getUUID("World") + "/" + dimension + "/" + structure + "/" + x + "/" + z;
    }
    public boolean heard(String key, UUID recipient) { return section("Recipients").contains(key + "/" + recipient); }
    public boolean spoken(String key, UUID speaker) { return section("Speakers").contains(key + "/" + speaker); }
    boolean cooling(UUID entity) { return section("Cooldowns").getLong(entity.toString()) > clock(); }
    boolean partyCooling(UUID party) { return party!=null && section("PartyCooldowns").getLong(party.toString())>clock(); }
    public synchronized boolean reserve(String key, UUID speaker, List<UUID> recipients, String line, String anchor, int cooldown) {
        return reserve(key,speaker,recipients,line,anchor,cooldown,null);
    }
    synchronized boolean reserve(String key, UUID speaker, List<UUID> recipients, String line, String anchor, int cooldown,UUID party) {
        if (failed || spoken(key, speaker) || cooling(speaker) || partyCooling(party) || recipients.isEmpty()
                || recipients.stream().anyMatch(p -> heard(key, p) || cooling(p))) return false;
        String eventId = UUID.randomUUID().toString();
        section("Speakers").putString(key + "/" + speaker, eventId);
        recipients.forEach(p -> { section("Recipients").putString(key + "/" + p, eventId); section("Cooldowns").putLong(p.toString(), clock() + cooldown); });
        section("Cooldowns").putLong(speaker.toString(), clock() + cooldown);
        if(party!=null)section("PartyCooldowns").putLong(party.toString(),clock()+cooldown);
        var event = new CompoundTag(); event.putInt("EvidenceVersion",1); event.putString("Instance", key); event.putUUID("Speaker", speaker);
        event.putString("Line", line); event.putString("Anchor", anchor); event.putLong("Tick", clock()); event.putString("Result", "reserved_attempt_consumed");
        var targets = new ListTag(); recipients.forEach(p -> targets.add(StringTag.valueOf(p.toString()))); event.put("Recipients", targets);
        section("Events").put(eventId, event);
        section("LastLine").putString(speaker.toString(), line);
        return commit();
    }
    public synchronized boolean commit() {
        if (failed) return false;
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            root.putLong("Revision",root.getLong("Revision")+1);
            NbtIo.writeCompressed(root, temporary);
            try (var stream = new RandomAccessFile(temporary.toFile(), "rw")) { stream.getFD().sync(); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Path marker=file.resolveSibling(file.getFileName()+".identity");
            Path markerTemporary=marker.resolveSibling(marker.getFileName()+".tmp");
            Files.writeString(markerTemporary,identity(root));
            try(var stream=new RandomAccessFile(markerTemporary.toFile(),"rw")) { stream.getFD().sync(); }
            Files.move(markerTemporary,marker,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().error("Companion ambient persistence failed; suppressing further callouts", e);
            return false;
        }
    }
    public static synchronized void close(MinecraftServer value) {
        if (server == value && current != null) current.commit(); server = null; current = null;
    }
}
