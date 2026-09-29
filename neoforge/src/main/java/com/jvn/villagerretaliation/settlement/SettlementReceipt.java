package com.jvn.villagerretaliation.settlement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

record SettlementReceipt(UUID stream, long sequence) {
    private static final Map<Object, Map<String, Issuer>> ISSUERS = new WeakHashMap<>();

    SettlementReceipt {
        if (stream == null || sequence <= 0) throw new IllegalArgumentException("Invalid settlement receipt sequence");
    }

    static SettlementReceipt issue(Object owner, String kind, BlockPos position) {
        return issue(owner, kind + ":" + (position.getX() >> 4) + ":" + (position.getZ() >> 4));
    }

    static synchronized SettlementReceipt issue(Object owner, String lane) {
        var issuer = ISSUERS.computeIfAbsent(owner, ignored -> new HashMap<>()).computeIfAbsent(lane, ignored -> new Issuer());
        return new SettlementReceipt(issuer.stream, issuer.next = Math.incrementExact(issuer.next));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Stream", this.stream);
        tag.putLong("Sequence", this.sequence);
        return tag;
    }

    static SettlementReceipt load(CompoundTag tag) {
        if (!tag.hasUUID("Stream") || !tag.contains("Sequence", Tag.TAG_LONG)
                || !tag.getAllKeys().equals(java.util.Set.of("Stream", "Sequence"))) throw new IllegalArgumentException("Settlement receipt is incomplete");
        return new SettlementReceipt(tag.getUUID("Stream"), tag.getLong("Sequence"));
    }

    private static final class Issuer {
        // a fresh namespace after reload prevents sequence reuse from stale snapshots
        private final UUID stream = UUID.randomUUID();
        private long next;
    }
}
