package com.jvn.villagerretaliation.exploration;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record MapLocation(UUID id, ResourceLocation dimension, int x, int z, Icon icon,
                          String name, String previewName, boolean alwaysShowName) {
    public static final int MAX_NAME = 80;
    public static final int MAX_LOCATIONS = 256;

    public MapLocation {
        if (id == null || dimension == null || icon == null || Math.abs((long) x) > 29_999_984
                || Math.abs((long) z) > 29_999_984 || !validText(name) || name.isBlank() || !validText(previewName))
            throw new IllegalArgumentException("Invalid map location");
        name = name.strip();
        previewName = previewName.strip();
    }

    private static boolean validText(String value) {
        return value != null && value.length() <= MAX_NAME
                && value.chars().noneMatch(c -> Character.isISOControl(c) || c == 167);
    }

    public String label() { return previewName.isBlank() ? name : previewName; }

    public MapLocation withId(UUID next) {
        return new MapLocation(next, dimension, x, z, icon, name, previewName, alwaysShowName);
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id); buffer.writeResourceLocation(dimension); buffer.writeInt(x); buffer.writeInt(z);
        buffer.writeEnum(icon); buffer.writeUtf(name, MAX_NAME); buffer.writeUtf(previewName, MAX_NAME);
        buffer.writeBoolean(alwaysShowName);
    }

    public static MapLocation read(RegistryFriendlyByteBuf buffer) {
        return new MapLocation(buffer.readUUID(), buffer.readResourceLocation(), buffer.readInt(), buffer.readInt(),
                buffer.readEnum(Icon.class), buffer.readUtf(MAX_NAME), buffer.readUtf(MAX_NAME), buffer.readBoolean());
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putString("Dimension", dimension.toString()); tag.putInt("X", x); tag.putInt("Z", z);
        tag.putString("Icon", icon.name()); tag.putString("Name", name); tag.putString("PreviewName", previewName);
        tag.putBoolean("AlwaysShowName", alwaysShowName);
        return tag;
    }

    public static MapLocation load(CompoundTag tag) {
        return new MapLocation(tag.getUUID("Id"), ResourceLocation.tryParse(tag.getString("Dimension")),
                tag.getInt("X"), tag.getInt("Z"), Icon.valueOf(tag.getString("Icon")),
                tag.getString("Name"), tag.getString("PreviewName"), tag.getBoolean("AlwaysShowName"));
    }

    public enum Icon {
        EMERALD, OMINOUS_BANNER, IRON_SWORD, DIAMOND_SWORD, IRON_AXE, IRON_HOE, IRON_SHOVEL, IRON_PICKAXE,
        CHEST, BARREL, BREAD, WHEAT, FISHING_ROD, DIAMOND_ORE, IRON_ORE, GOLD_ORE, COAL_ORE,
        EMERALD_ORE, COPPER_ORE, REDSTONE_ORE, LAPIS_ORE, CRAFTING_TABLE, FURNACE, ANVIL,
        WHITE_BED, CAMPFIRE, TORCH, APPLE, BOW, SHIELD, BOOK, COMPASS,
        BREWING_STAND, LOOM, BLAST_FURNACE, SMOKER, STONECUTTER, CARTOGRAPHY_TABLE,
        COMPOSTER, FLETCHING_TABLE, CAULDRON, LECTERN, SMITHING_TABLE, GRINDSTONE
    }
}
