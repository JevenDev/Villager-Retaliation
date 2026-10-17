package com.jvn.villagerretaliation.exploration;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class MapRegionFile implements AutoCloseable {
    static final int SIDE = 16, COUNT = SIDE * SIDE, HEADER = 32, ENTRY = 32, SECTOR = 256;
    static final int DATA_START = ((HEADER + COUNT * ENTRY * 2 + SECTOR - 1) / SECTOR) * SECTOR;
    private static final int MAGIC = 0x56525231, MAX_COMPRESSED = MapStore.MAX_FILE_BYTES + 65536;
    private static final long MAX_REGION_BYTES = DATA_START + 3L * COUNT * MAX_COMPRESSED;
    private final FileChannel channel;
    private final Slot[][] slots = new Slot[COUNT][2];
    private final boolean[][] damaged = new boolean[COUNT][2];
    private final int x, z;
    private record Slot(long generation, long offset, int length, int rawLength, int checksum) { }
    private record Pending(int index, int copy, Slot slot) { }

    MapRegionFile(Path path, int x, int z) throws IOException {
        this.x = x; this.z = z;
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Path pending = path.resolveSibling(path.getFileName() + ".pending");
            var header = ByteBuffer.allocate(DATA_START);
            header.putInt(MAGIC).putInt(1).putInt(x).putInt(z).putInt(SIDE).putInt(SECTOR).putInt(0);
            header.putInt(checksum(header.array(), 0, HEADER - 4)); header.clear();
            try (var file = FileChannel.open(pending, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                write(file, header, 0); file.force(true);
            }
            Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE);
        }
        channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try { load(); }
        catch (IOException | RuntimeException e) { channel.close(); throw e; }
    }
    static long indexOffset(int index, int copy) { return SECTOR + (long)(copy * COUNT + index) * ENTRY; }
    static int index(int x, int z) { return Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE); }
    private void load() throws IOException {
        long size = channel.size();
        if (size < DATA_START || size > MAX_REGION_BYTES) throw new IOException("Invalid map region size");
        var header = read(channel, HEADER, 0);
        if (header.getInt() != MAGIC || header.getInt() != 1 || header.getInt() != x || header.getInt() != z
                || header.getInt() != SIDE || header.getInt() != SECTOR || header.getInt() != 0
                || header.getInt() != checksum(header.array(), 0, HEADER - 4)) throw new IOException("Invalid map region header");
        var entries = read(channel, COUNT * ENTRY * 2, SECTOR);
        for (int i = 0; i < COUNT; i++) for (int copy = 0; copy < 2; copy++) {
            byte[] bytes = new byte[ENTRY]; entries.position((copy * COUNT + i) * ENTRY); entries.get(bytes);
            if (Arrays.equals(bytes, new byte[ENTRY])) continue;
            var data = ByteBuffer.wrap(bytes);
            var slot = new Slot(data.getLong(), data.getLong(), data.getInt(), data.getInt(), data.getInt());
            boolean valid = data.getInt() == entryChecksum(i, bytes) && slot.generation > 0 && slot.offset >= DATA_START
                    && slot.offset % SECTOR == 0 && slot.length > 0 && slot.length <= MAX_COMPRESSED
                    && slot.rawLength >= 16 && slot.rawLength <= MapStore.MAX_FILE_BYTES && slot.offset <= size - slot.length;
            if (valid) slots[i][copy] = slot;
            else damaged[i][copy] = true;
        }
        var occupied = new BitSet();
        for (int i = 0; i < COUNT; i++) {
            for (var slot : slots[i]) if (slot != null) {
                int start = (int)(slot.offset / SECTOR), end = start + sectors(slot.length);
                if (occupied.nextSetBit(start) >= 0 && occupied.nextSetBit(start) < end) throw new IOException("Overlapping map records");
                occupied.set(start, end);
            }
        }
    }
    private Slot latest(int index) throws IOException {
        var a = slots[index][0]; var b = slots[index][1];
        if (a == null && b == null && (damaged[index][0] || damaged[index][1])) throw new IOException("Damaged map tile index");
        return a == null ? b : b == null || a.generation >= b.generation ? a : b;
    }
    static final class ReadBuffer {
        byte[] compressed = new byte[0], raw = new byte[0];
    }
    byte[] read(int index) throws IOException {
        var buffer = new ReadBuffer();
        return read(index, buffer) == 0 ? null : buffer.raw;
    }
    int read(int index, ReadBuffer buffer) throws IOException {
        var slot = latest(index);
        if (slot == null) return 0;
        if (buffer.compressed.length < slot.length) buffer.compressed = new byte[slot.length];
        if (buffer.raw.length < slot.rawLength) buffer.raw = new byte[slot.rawLength];
        var bytes = ByteBuffer.wrap(buffer.compressed, 0, slot.length);
        while (bytes.hasRemaining()) {
            int count = channel.read(bytes, slot.offset + bytes.position());
            if (count < 0) throw new EOFException("Truncated map region");
        }
        if (checksum(buffer.compressed, 0, slot.length) != slot.checksum) throw new IOException("Map region record checksum");
        var inflater = new Inflater();
        try {
            inflater.setInput(buffer.compressed, 0, slot.length);
            int offset = 0;
            while (offset < slot.rawLength) {
                int count = inflater.inflate(buffer.raw, offset, slot.rawLength - offset);
                if (count == 0) throw new IOException("Truncated compressed map tile");
                offset += count;
            }
            if (!inflater.finished() || inflater.getRemaining() != 0) throw new IOException("Invalid compressed map tile length");
            return slot.rawLength;
        } catch (DataFormatException e) { throw new IOException("Invalid compressed map tile", e); }
        finally { inflater.end(); }
    }
    void write(Map<Integer, byte[]> tiles) throws IOException {
        var occupied = occupied();
        var pending = new ArrayList<Pending>();
        for (var tile : tiles.entrySet()) {
            int index = tile.getKey();
            var current = latest(index);
            if (current != null && current.generation == Long.MAX_VALUE) throw new IOException("Map generation exhausted");
            byte[] raw = tile.getValue();
            var bytes = new ByteArrayOutputStream();
            var deflater = new Deflater(Deflater.BEST_SPEED);
            try (var out = new DeflaterOutputStream(bytes, deflater)) { out.write(raw); }
            finally { deflater.end(); }
            byte[] compressed = bytes.toByteArray();
            int count = sectors(compressed.length), start = DATA_START / SECTOR;
            while (true) {
                start = occupied.nextClearBit(start);
                int next = occupied.nextSetBit(start);
                if (next < 0 || next >= start + count) break;
                start = next + 1;
            }
            long offset = (long)start * SECTOR;
            if (offset + compressed.length > MAX_REGION_BYTES) throw new IOException("Map region capacity exceeded");
            occupied.set(start, start + count);
            write(channel, ByteBuffer.wrap(compressed), offset);
            int copy = current == slots[index][0] ? 1 : 0;
            pending.add(new Pending(index, copy, new Slot(current == null ? 1 : current.generation + 1,
                    offset, compressed.length, raw.length, checksum(compressed, 0, compressed.length))));
        }
        // index copies live in separate sectors, and replacements become durable before publication
        channel.force(true);
        for (var update : pending) {
            var slot = update.slot;
            var bytes = ByteBuffer.allocate(ENTRY).putLong(slot.generation).putLong(slot.offset)
                    .putInt(slot.length).putInt(slot.rawLength).putInt(slot.checksum);
            bytes.putInt(entryChecksum(update.index, bytes.array())).flip();
            write(channel, bytes, indexOffset(update.index, update.copy));
        }
        channel.force(true);
        for (var update : pending) {
            slots[update.index][update.copy] = update.slot;
            damaged[update.index][update.copy] = false;
        }
    }
    private BitSet occupied() throws IOException {
        var occupied = new BitSet(); occupied.set(0, DATA_START / SECTOR);
        for (int i = 0; i < COUNT; i++) for (int copy = 0; copy < 2; copy++) {
            // an incomplete index may still reference recoverable data, so do not recycle its space
            if (damaged[i][copy]) occupied.set(0, (int)((channel.size() + SECTOR - 1) / SECTOR));
            var slot = slots[i][copy];
            if (slot != null) { int start = (int)(slot.offset / SECTOR); occupied.set(start, start + sectors(slot.length)); }
        }
        return occupied;
    }
    private int entryChecksum(int index, byte[] bytes) {
        var crc = new CRC32(); crc.update(ByteBuffer.allocate(12).putInt(x).putInt(z).putInt(index).array());
        crc.update(bytes, 0, ENTRY - 4); return (int)crc.getValue();
    }
    private static int checksum(byte[] bytes, int offset, int length) {
        var crc = new CRC32(); crc.update(bytes, offset, length); return (int)crc.getValue();
    }
    private static int sectors(int length) { return (length + SECTOR - 1) / SECTOR; }
    private static ByteBuffer read(FileChannel file, int length, long offset) throws IOException {
        var bytes = ByteBuffer.allocate(length);
        while (bytes.hasRemaining()) {
            int count = file.read(bytes, offset + bytes.position());
            if (count < 0) throw new EOFException("Truncated map region");
        }
        return bytes.flip();
    }
    private static void write(FileChannel file, ByteBuffer bytes, long offset) throws IOException {
        int start = bytes.position();
        while (bytes.hasRemaining()) file.write(bytes, offset + bytes.position() - start);
    }
    @Override public void close() throws IOException { channel.close(); }
}
