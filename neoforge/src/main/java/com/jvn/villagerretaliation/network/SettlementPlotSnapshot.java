package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;

public record SettlementPlotSnapshot(Totals totals, List<Plot> plots) {
    public SettlementPlotSnapshot {
        java.util.Objects.requireNonNull(totals);
        plots = List.copyOf(plots);
        if (plots.size() > SettlementViewWindow.MAX_CHUNKS || plots.stream().map(Plot::chunk).distinct().count() != plots.size()) {
            throw new IllegalArgumentException("Invalid bounded plot snapshot");
        }
    }

    static void write(RegistryFriendlyByteBuf buffer, SettlementPlotSnapshot snapshot) {
        Totals.write(buffer, snapshot.totals());
        buffer.writeVarInt(snapshot.plots().size());
        for (var plot : snapshot.plots()) {
            buffer.writeChunkPos(plot.chunk());
            buffer.writeBoolean(plot.owned());
            buffer.writeBoolean(plot.eligible());
            buffer.writeVarLong(plot.price());
            buffer.writeUtf(plot.freeFailure(), 64);
            buffer.writeUtf(plot.paidFailure(), 64);
        }
    }

    static SettlementPlotSnapshot read(RegistryFriendlyByteBuf buffer) {
        var totals = Totals.read(buffer);
        int count = VillagerPayloads.readCollectionSize(buffer, SettlementViewWindow.MAX_CHUNKS, "settlement plots");
        var plots = new ArrayList<Plot>();
        for (int i = 0; i < count; i++) plots.add(new Plot(buffer.readChunkPos(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarLong(), buffer.readUtf(64), buffer.readUtf(64)));
        return new SettlementPlotSnapshot(totals, plots);
    }

    public record Plot(ChunkPos chunk, boolean owned, boolean eligible, long price, String freeFailure, String paidFailure) {
        public Plot {
            java.util.Objects.requireNonNull(chunk);
            if (price < 0 || freeFailure == null || paidFailure == null || freeFailure.length() > 64 || paidFailure.length() > 64) {
                throw new IllegalArgumentException("Invalid plot quote");
            }
        }
    }

    public record Totals(int owned, int capacity, int free, long revision, CurrencyAmount spendable, String conflict) {
        public Totals {
            if (owned < 0 || capacity < owned || free < 0 || revision < 0 || spendable == null || conflict == null || conflict.length() > 256) {
                throw new IllegalArgumentException("Invalid territory totals");
            }
        }

        static void write(RegistryFriendlyByteBuf buffer, Totals totals) {
            buffer.writeVarInt(totals.owned());
            buffer.writeVarInt(totals.capacity());
            buffer.writeVarInt(totals.free());
            buffer.writeVarLong(totals.revision());
            totals.spendable().write(buffer);
            buffer.writeUtf(totals.conflict(), 256);
        }

        static Totals read(RegistryFriendlyByteBuf buffer) {
            return new Totals(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(),
                    CurrencyAmount.read(buffer), buffer.readUtf(256));
        }
    }
}
