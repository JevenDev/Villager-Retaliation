package com.jvn.villagerretaliation.interaction.work.production;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record HiredProductionOrder(UUID id, HiredProductionRecipe recipe, Mode mode, int amount, BlockPos storage,
                                   ItemStack output, long produced, boolean paused, boolean cancelled, String blocker) {
    public static final int MAX_AMOUNT = 4096;
    public static final int MAX_BATCH = 64;

    public HiredProductionOrder {
        Objects.requireNonNull(id);
        Objects.requireNonNull(recipe);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(blocker);
        output = Objects.requireNonNull(output).copy();
        storage = storage == null ? null : storage.immutable();
        if (amount < 1 || amount > MAX_AMOUNT || produced < 0 || blocker.length() > 160
                || output.isEmpty() || output.getCount() > MAX_BATCH || mode == Mode.MAINTAIN_STOCK && storage == null) {
            throw new IllegalArgumentException("Invalid production order");
        }
    }

    public static HiredProductionOrder create(Level level, HiredProductionRecipe recipe, Mode mode, int amount, BlockPos storage) {
        return new HiredProductionOrder(UUID.randomUUID(), recipe, mode, amount, storage, recipe.output(level), 0L, false, false, "");
    }

    @Override
    public ItemStack output() {
        return output.copy();
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(output, stack);
    }

    public boolean complete() {
        return mode == Mode.BATCH && produced >= amount;
    }

    public boolean runnable() {
        return !paused && !cancelled && !complete();
    }

    HiredProductionOrder state(long produced, boolean paused, boolean cancelled, String blocker) {
        return new HiredProductionOrder(id, recipe, mode, amount, storage, output, produced, paused, cancelled, blocker);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Id", id);
        tag.put("Recipe", recipe.save());
        tag.putString("Mode", mode.name());
        tag.putInt("Amount", amount);
        if (storage != null) tag.putLong("Storage", storage.asLong());
        tag.put("Output", output.save(provider));
        tag.putLong("Produced", produced);
        tag.putBoolean("Paused", paused);
        tag.putBoolean("Cancelled", cancelled);
        tag.putString("Blocker", blocker);
        return tag;
    }

    public static HiredProductionOrder load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Id")
                || !tag.contains("Recipe", Tag.TAG_COMPOUND) || !tag.contains("Mode", Tag.TAG_STRING)
                || !tag.contains("Amount", Tag.TAG_INT) || tag.contains("Storage") && !tag.contains("Storage", Tag.TAG_LONG)
                || !tag.contains("Output", Tag.TAG_COMPOUND) || !tag.contains("Produced", Tag.TAG_LONG)
                || !tag.contains("Paused", Tag.TAG_BYTE) || !tag.contains("Cancelled", Tag.TAG_BYTE)
                || !tag.contains("Blocker", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Invalid saved production order");
        }
        return new HiredProductionOrder(tag.getUUID("Id"), HiredProductionRecipe.load(tag.getCompound("Recipe")),
                Mode.valueOf(tag.getString("Mode")), tag.getInt("Amount"),
                tag.contains("Storage") ? BlockPos.of(tag.getLong("Storage")) : null,
                ItemStack.parse(provider, tag.getCompound("Output")).orElseThrow(() -> new IllegalArgumentException("Invalid production output")),
                tag.getLong("Produced"), tag.getBoolean("Paused"), tag.getBoolean("Cancelled"), tag.getString("Blocker"));
    }

    public enum Mode {
        BATCH,
        CONTINUOUS,
        MAINTAIN_STOCK
    }
}
