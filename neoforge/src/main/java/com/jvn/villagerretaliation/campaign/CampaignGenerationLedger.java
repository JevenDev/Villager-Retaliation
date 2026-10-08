package com.jvn.villagerretaliation.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class CampaignGenerationLedger {
    private static final int MAX_CELLS = 4096;
    private final Map<BlockPos, CampaignGeneratedBlock> cells = new LinkedHashMap<>();
    private String diagnostic = "";
    private CompoundTag preserved;

    public synchronized Map<BlockPos, CampaignGeneratedBlock> cells() { return Collections.unmodifiableMap(new LinkedHashMap<>(cells)); }
    public synchronized String diagnostic() { return diagnostic; }

    synchronized void record(CampaignGeneratedBlock candidate) {
        if (!diagnostic.isBlank()) return;
        var prior = cells.get(candidate.position());
        if (prior == null && cells.size() >= MAX_CELLS) {
            diagnostic = "Generated campaign ownership exceeds this chunk's cell limit. Correct the structure datapack.";
            return;
        }
        if (prior != null && !prior.sameIntent(candidate))
            cells.put(candidate.position(), prior.conflict("Overlapping structure definitions claim this position. Inspect the authored layouts."));
        else if (prior == null || prior.state() == CampaignGeneratedBlock.State.PENDING)
            cells.put(candidate.position(), candidate);
    }

    public synchronized CompoundTag save() {
        if (preserved != null) return preserved.copy();
        var tag = new CompoundTag();
        tag.putInt("DataVersion", 1);
        tag.putString("Diagnostic", diagnostic);
        var list = new ListTag();
        cells.values().forEach(cell -> list.add(cell.save()));
        tag.put("Cells", list);
        return tag;
    }

    public static CampaignGenerationLedger load(CompoundTag tag) {
        var ledger = new CampaignGenerationLedger();
        try {
            CampaignStateCodec.require(tag.getAllKeys().equals(java.util.Set.of("DataVersion", "Diagnostic", "Cells"))
                    && tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == 1 && tag.contains("Diagnostic", Tag.TAG_STRING),
                    "unsupported generated campaign ownership");
            String diagnostic = tag.getString("Diagnostic");
            CampaignStateCodec.require(diagnostic.length() <= 512, "invalid generated campaign ownership diagnostic");
            var cells = CampaignStateCodec.list(tag, "Cells", Tag.TAG_COMPOUND);
            CampaignStateCodec.require(cells.size() <= MAX_CELLS, "too many generated campaign cells");
            for (var raw : cells) {
                var cell = CampaignGeneratedBlock.load((CompoundTag) raw);
                CampaignStateCodec.require(ledger.cells.putIfAbsent(cell.position(), cell) == null, "duplicate generated campaign cell");
            }
            ledger.diagnostic = diagnostic;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ledger.cells.clear();
            ledger.preserved = tag.copy();
            ledger.diagnostic = "Generated campaign ownership is unreadable. Restore compatible chunk data before changing this location.";
        }
        return ledger;
    }
}
