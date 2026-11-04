package com.jvn.villagerretaliation.settlement;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record SettlementTerrainOptions(Mode mode, int depth) {
    public static final int MAX_DEPTH = 4;
    public static final SettlementTerrainOptions NONE = new SettlementTerrainOptions(Mode.NONE, 2);

    public SettlementTerrainOptions {
        if (mode == null || depth < 1 || depth > MAX_DEPTH) throw new IllegalArgumentException("Invalid terrain preparation options");
    }

    public boolean enabled() { return this.mode != Mode.NONE; }
    public boolean level() { return this.mode == Mode.LEVEL || this.mode == Mode.LEVEL_FILL || this.mode == Mode.LEVEL_FOUNDATION; }
    public boolean fill() { return this.mode == Mode.FILL || this.mode == Mode.LEVEL_FILL || foundation(); }
    public boolean foundation() { return this.mode == Mode.FOUNDATION || this.mode == Mode.LEVEL_FOUNDATION; }
    public void write(RegistryFriendlyByteBuf buffer) { buffer.writeEnum(this.mode); buffer.writeVarInt(this.depth); }
    public static SettlementTerrainOptions read(RegistryFriendlyByteBuf buffer) {
        return new SettlementTerrainOptions(buffer.readEnum(Mode.class), buffer.readVarInt());
    }

    public enum Mode {
        NONE("Keep terrain"), LEVEL("Level ground"), FILL("Fill shallow holes"), FOUNDATION("Cobblestone foundation"),
        LEVEL_FILL("Level and fill"), LEVEL_FOUNDATION("Level and foundation");
        private final String label;
        Mode(String label) { this.label = label; }
        public String label() { return this.label; }
    }
}
