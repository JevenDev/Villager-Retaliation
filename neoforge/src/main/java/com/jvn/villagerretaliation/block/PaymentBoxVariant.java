package com.jvn.villagerretaliation.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum PaymentBoxVariant {
    OAK("oak", Blocks.OAK_PLANKS, "Oak Payment Box"),
    SPRUCE("spruce", Blocks.SPRUCE_PLANKS, "Spruce Payment Box"),
    BIRCH("birch", Blocks.BIRCH_PLANKS, "Birch Payment Box"),
    JUNGLE("jungle", Blocks.JUNGLE_PLANKS, "Jungle Payment Box"),
    ACACIA("acacia", Blocks.ACACIA_PLANKS, "Acacia Payment Box"),
    DARK_OAK("dark_oak", Blocks.DARK_OAK_PLANKS, "Dark Oak Payment Box"),
    MANGROVE("mangrove", Blocks.MANGROVE_PLANKS, "Mangrove Payment Box"),
    CHERRY("cherry", Blocks.CHERRY_PLANKS, "Cherry Payment Box"),
    BAMBOO("bamboo", Blocks.BAMBOO_PLANKS, "Bamboo Payment Box"),
    CRIMSON("crimson", Blocks.CRIMSON_PLANKS, "Crimson Payment Box"),
    WARPED("warped", Blocks.WARPED_PLANKS, "Warped Payment Box");

    private final String woodId;
    private final Block planks;
    private final String displayName;

    PaymentBoxVariant(String woodId, Block planks, String displayName) {
        this.woodId = woodId;
        this.planks = planks;
        this.displayName = displayName;
    }

    public String woodId() {
        return this.woodId;
    }

    public String blockId() {
        return this.woodId + "_payment_box";
    }

    public Block planks() {
        return this.planks;
    }

    public String displayName() {
        return this.displayName;
    }
}
