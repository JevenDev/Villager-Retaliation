package com.jvn.villagerretaliation.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class CampaignBlockOwnershipLedger {
    public static final int CURRENT_DATA_VERSION = 1;
    private static final int MAX_POSITIONS = 4096;
    private final Map<BlockPos, Receipt> receipts = new LinkedHashMap<>();
    private CompoundTag preserved;
    private String diagnostic = "";

    public String diagnostic() { return diagnostic; }
    public Receipt get(BlockPos position) { return receipts.get(position); }
    public Map<BlockPos, Receipt> receipts() { return Collections.unmodifiableMap(receipts); }

    void record(Receipt receipt) {
        CampaignStateCodec.require(diagnostic.isBlank(), diagnostic);
        var prior = receipts.get(receipt.position());
        CampaignStateCodec.require(prior != null || receipts.size() < MAX_POSITIONS, "campaign block ownership capacity reached for this chunk");
        CampaignStateCodec.require(prior == null || prior.state() == State.RESTORED || prior.sameIdentity(receipt),
                "another operation owns this campaign block");
        receipts.put(receipt.position(), receipt);
    }

    void advance(Receipt previous, Receipt next) {
        CampaignStateCodec.require(diagnostic.isBlank(), diagnostic);
        CampaignStateCodec.require(previous.equals(receipts.get(previous.position())) && previous.state() == State.APPLIED
                && previous.position().equals(next.position()) && previous.owner().equals(next.owner())
                && !previous.operation().equals(next.operation()) && previous.placed().equals(next.original())
                && next.state() == State.RESERVED, "campaign block ownership changed before its next operation");
        receipts.put(next.position(), next);
    }

    boolean changedOutsideOperation(BlockPos position, String reason) {
        if (!diagnostic.isBlank()) return false;
        var receipt = receipts.get(position);
        if (receipt == null || receipt.state() == State.RESTORED || receipt.state() == State.CONFLICT) return false;
        receipts.put(position.immutable(), receipt.conflict(reason));
        return true;
    }

    public CompoundTag save() {
        if (preserved != null) return preserved.copy();
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var entries = new ListTag();
        receipts.values().forEach(receipt -> entries.add(receipt.save()));
        tag.put("Receipts", entries);
        return tag;
    }

    public static CampaignBlockOwnershipLedger load(CompoundTag tag) {
        var ledger = new CampaignBlockOwnershipLedger();
        try {
            CampaignStateCodec.require(tag.getAllKeys().equals(java.util.Set.of("DataVersion", "Receipts"))
                    && tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION,
                    "unsupported campaign block ownership version");
            var entries = CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND);
            CampaignStateCodec.require(entries.size() <= MAX_POSITIONS, "campaign block ownership capacity exceeded");
            for (var raw : entries) {
                var value = (CompoundTag) raw;
                var receipt = Receipt.load(value);
                CampaignStateCodec.require(ledger.receipts.putIfAbsent(receipt.position(), receipt) == null, "duplicate campaign block ownership");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ledger.receipts.clear();
            ledger.preserved = tag.copy();
            ledger.diagnostic = exception.getMessage() + ". Restore compatible campaign block ownership before continuing.";
        }
        return ledger;
    }

    public enum State { RESERVED, APPLIED, RESTORED, CONFLICT }

    public record Receipt(BlockPos position, UUID owner, UUID operation, CampaignBlockSnapshot original,
            CampaignBlockSnapshot placed, State state, String diagnostic) {
        public Receipt {
            CampaignStateCodec.require(position != null && owner != null && operation != null && original != null && placed != null && state != null
                    && diagnostic != null && diagnostic.length() <= 512, "invalid campaign block ownership receipt");
            CampaignStateCodec.require(BlockPos.of(position.asLong()).equals(position), "campaign block position cannot be stored without loss");
            CampaignStateCodec.require(state == State.CONFLICT ? !diagnostic.isBlank() : diagnostic.isEmpty(), "invalid campaign ownership diagnostic");
            position = position.immutable();
            original.validatePosition(position);
            placed.validatePosition(position);
        }

        CompoundTag save() {
            var value = new CompoundTag();
            value.putLong("Position", position.asLong());
            value.putUUID("Owner", owner);
            value.putUUID("Operation", operation);
            value.put("Original", original.save());
            value.put("Placed", placed.save());
            value.putString("State", state.name());
            value.putString("Diagnostic", diagnostic);
            return value;
        }

        static Receipt load(CompoundTag value) {
            CampaignStateCodec.require(value.getAllKeys().equals(java.util.Set.of("Position", "Owner", "Operation", "Original", "Placed", "State", "Diagnostic"))
                    && value.contains("Position", Tag.TAG_LONG) && value.hasUUID("Owner") && value.hasUUID("Operation")
                    && value.contains("Original", Tag.TAG_COMPOUND) && value.contains("Placed", Tag.TAG_COMPOUND)
                    && value.contains("State", Tag.TAG_STRING) && value.contains("Diagnostic", Tag.TAG_STRING), "incomplete campaign block ownership");
            return new Receipt(BlockPos.of(value.getLong("Position")), value.getUUID("Owner"), value.getUUID("Operation"),
                    CampaignBlockSnapshot.load(value.getCompound("Original")), CampaignBlockSnapshot.load(value.getCompound("Placed")),
                    State.valueOf(value.getString("State")), value.getString("Diagnostic"));
        }

        public boolean sameIdentity(Receipt other) {
            return other != null && position.equals(other.position) && owner.equals(other.owner) && operation.equals(other.operation)
                    && original.equals(other.original) && placed.equals(other.placed);
        }

        public Receipt applied() { return new Receipt(position, owner, operation, original, placed, State.APPLIED, ""); }
        public Receipt restored() { return new Receipt(position, owner, operation, original, placed, State.RESTORED, ""); }
        public Receipt conflict(String reason) { return new Receipt(position, owner, operation, original, placed, State.CONFLICT, reason); }
    }
}
