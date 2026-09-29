package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.BrewingStandBlockEntityAccessor;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

public final class SettlementBrewingStandState {
    private static final String ID_TAG = "VillagerRetaliationSettlementBrewingStand";
    private static final String VERSION_TAG = "VillagerRetaliationSettlementBrewingStandVersion";
    private static final String LAST_TICK_TAG = "VillagerRetaliationSettlementBrewingStandTick";
    private static final List<String> FIELDS = List.of("Items", "BrewTime", "Fuel");
    private final UUID id;
    private final UUID area;
    private final BlockPos position;
    private CompoundTag baseline;
    private CompoundTag contents;
    private long checkedAt;
    private long version;
    private boolean pending;
    private boolean conflict;

    private SettlementBrewingStandState(UUID id, UUID area, BlockPos position) {
        this.id = id;
        this.area = area;
        this.position = position.immutable();
    }

    public UUID id() {
        return this.id;
    }

    public static UUID identity(BrewingStandBlockEntity stand) {
        var persistent = stand.getPersistentData();
        if (!persistent.hasUUID(ID_TAG)) {
            persistent.putUUID(ID_TAG, UUID.randomUUID());
            stand.setChanged();
        }
        return persistent.getUUID(ID_TAG);
    }

    static void bindConstruction(BrewingStandBlockEntity entity, UUID id) {
        var persistent = entity.getPersistentData();
        persistent.putUUID(ID_TAG, id);
        persistent.remove(VERSION_TAG);
        persistent.remove(LAST_TICK_TAG);
    }

    void constructionReady() {
        this.conflict = false;
    }

    public UUID area() {
        return this.area;
    }

    public BlockPos position() {
        return this.position;
    }

    public boolean pending() {
        return this.pending;
    }

    public boolean conflict() {
        return this.conflict;
    }

    int brewTime() {
        return this.contents.getShort("BrewTime");
    }

    int fuel() {
        return this.contents.getByte("Fuel");
    }

    public static void onVanillaTick(Level level, BrewingStandBlockEntity stand) {
        if (!level.isClientSide && stand.getPersistentData().hasUUID(ID_TAG)) {
            stand.getPersistentData().putLong(LAST_TICK_TAG, level.getGameTime());
        }
    }

    static boolean supported(BrewingStandBlockEntity stand) {
        return stand.getClass() == BrewingStandBlockEntity.class;
    }

    static SettlementBrewingStandState capture(ServerLevel level, UUID area, BrewingStandBlockEntity stand, long now) {
        UUID id = identity(stand);
        var state = new SettlementBrewingStandState(id, area, stand.getBlockPos());
        state.observe(level.registryAccess(), stand, now);
        return state;
    }

    boolean matches(BrewingStandBlockEntity stand) {
        return supported(stand) && this.position.equals(stand.getBlockPos())
                && stand.getPersistentData().hasUUID(ID_TAG) && this.id.equals(stand.getPersistentData().getUUID(ID_TAG));
    }

    boolean reconcile(HolderLookup.Provider provider, BrewingStandBlockEntity stand) {
        if (!matches(stand)) {
            this.conflict = this.pending;
            return false;
        }
        if (!this.pending) {
            return true;
        }
        long applied = stand.getPersistentData().getLong(VERSION_TAG);
        if (applied < this.version) {
            if (!fields(stand.saveWithoutMetadata(provider)).equals(this.baseline)) {
                this.conflict = true;
                return false;
            }
            CompoundTag live = stand.saveWithoutMetadata(provider);
            for (String key : FIELDS) {
                live.remove(key);
                if (this.contents.contains(key)) {
                    live.put(key, this.contents.get(key).copy());
                }
            }
            stand.loadWithComponents(live, provider);
            var ingredient = ResourceLocation.tryParse(this.contents.getString("ActiveIngredient"));
            ((BrewingStandBlockEntityAccessor) stand).villagerretaliation$setIngredient(
                    ingredient == null ? Items.AIR : BuiltInRegistries.ITEM.get(ingredient));
            stand.getPersistentData().putLong(VERSION_TAG, this.version);
        }
        this.pending = false;
        this.conflict = false;
        this.version = Math.max(this.version, applied);
        refresh(provider, stand);
        return true;
    }

    void observe(HolderLookup.Provider provider, BrewingStandBlockEntity stand, long now) {
        refresh(provider, stand);
        this.checkedAt = Math.max(this.checkedAt, now);
    }

    void refresh(HolderLookup.Provider provider, BrewingStandBlockEntity stand) {
        if (this.pending) {
            throw new IllegalStateException("Pending brewing work must be reconciled before observation");
        }
        this.checkedAt = Math.max(this.checkedAt, stand.getPersistentData().getLong(LAST_TICK_TAG));
        this.baseline = fields(stand.saveWithoutMetadata(provider));
        this.contents = this.baseline.copy();
        var ingredient = ((BrewingStandBlockEntityAccessor) stand).villagerretaliation$getIngredient();
        if (ingredient != null && brewTime() > 0) {
            this.contents.putString("ActiveIngredient", BuiltInRegistries.ITEM.getKey(ingredient).toString());
        }
        this.version = Math.max(this.version, stand.getPersistentData().getLong(VERSION_TAG));
        this.conflict = false;
    }

    void pause(long now) {
        this.checkedAt = Math.max(this.checkedAt, now);
    }

    void missing() {
        this.conflict = this.pending;
    }

    NonNullList<ItemStack> items(HolderLookup.Provider provider) {
        var items = NonNullList.withSize(5, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(this.contents, items, provider);
        return items;
    }

    void updateItems(ServerLevel level, NonNullList<ItemStack> items) {
        CompoundTag updated = this.contents.copy();
        ContainerHelper.saveAllItems(updated, items, true, level.registryAccess());
        update(updated);
    }

    void advance(ServerLevel level, long now) {
        int elapsed = SettlementSimulationClock.elapsed(this.checkedAt, now);
        this.checkedAt = Math.max(this.checkedAt, now);
        if (elapsed == 0 || this.conflict) {
            return;
        }
        var items = items(level.registryAccess());
        int fuel = Math.max(0, fuel());
        int progress = Math.max(0, brewTime());
        ResourceLocation active = ResourceLocation.tryParse(this.contents.getString("ActiveIngredient"));
        for (int tick = 0; tick < elapsed; tick++) {
            if (fuel == 0 && items.get(4).is(Items.BLAZE_POWDER)) {
                items.get(4).shrink(1);
                fuel = BrewingStandBlockEntity.FUEL_USES;
            }
            ItemStack ingredient = items.get(3);
            boolean brewable = !ingredient.isEmpty() && level.potionBrewing().isIngredient(ingredient);
            if (brewable) {
                brewable = false;
                for (int slot = 0; slot < 3; slot++) {
                    brewable |= !items.get(slot).isEmpty() && level.potionBrewing().hasMix(items.get(slot), ingredient);
                }
            }
            if (progress > 0) {
                if (progress == 1 && brewable && ingredient.getCount() > 1 && ingredient.hasCraftingRemainingItem()) {
                    break;
                }
                progress--;
                if (progress == 0 && brewable) {
                    if (!net.neoforged.neoforge.event.EventHooks.onPotionAttemptBrew(items)) {
                        for (int slot = 0; slot < 3; slot++) {
                            items.set(slot, level.potionBrewing().mix(ingredient, items.get(slot)));
                        }
                        net.neoforged.neoforge.event.EventHooks.onPotionBrewed(items);
                        ItemStack remainder = ingredient.hasCraftingRemainingItem() ? ingredient.getCraftingRemainingItem() : ItemStack.EMPTY;
                        ingredient.shrink(1);
                        items.set(3, ingredient.isEmpty() ? remainder : ingredient);
                    }
                } else if (!brewable || !BuiltInRegistries.ITEM.getKey(ingredient.getItem()).equals(active)) {
                    progress = 0;
                }
            } else if (brewable && fuel > 0) {
                fuel--;
                progress = 400;
                active = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
            }
        }
        CompoundTag updated = this.contents.copy();
        ContainerHelper.saveAllItems(updated, items, true, level.registryAccess());
        updated.putShort("BrewTime", (short) progress);
        updated.putByte("Fuel", (byte) fuel);
        updated.remove("ActiveIngredient");
        if (progress > 0 && active != null) {
            updated.putString("ActiveIngredient", active.toString());
        }
        update(updated);
    }

    private void update(CompoundTag updated) {
        if (!this.contents.equals(updated)) {
            this.contents = updated;
            this.pending = true;
            this.version++;
        }
    }

    private static CompoundTag fields(CompoundTag source) {
        CompoundTag result = new CompoundTag();
        for (String key : FIELDS) {
            if (source.contains(key)) {
                result.put(key, source.get(key).copy());
            }
        }
        return result;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        tag.putLong("Position", this.position.asLong());
        tag.put("Baseline", this.baseline.copy());
        tag.put("Contents", this.contents.copy());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putBoolean("Pending", this.pending);
        tag.putBoolean("Conflict", this.conflict);
        return tag;
    }

    static SettlementBrewingStandState load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area") || !tag.contains("Position", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Settlement brewing stand identity, area or position is invalid");
        }
        var state = new SettlementBrewingStandState(tag.getUUID("Id"), tag.getUUID("Area"), BlockPos.of(tag.getLong("Position")));
        state.baseline = fields(tag.getCompound("Baseline"));
        state.contents = fields(tag.getCompound("Contents"));
        if (tag.getCompound("Contents").contains("ActiveIngredient", Tag.TAG_STRING)) {
            state.contents.putString("ActiveIngredient", tag.getCompound("Contents").getString("ActiveIngredient"));
        }
        if (state.brewTime() < 0 || state.brewTime() > 400 || state.fuel() < 0 || state.fuel() > BrewingStandBlockEntity.FUEL_USES) {
            throw new IllegalArgumentException("Settlement brewing progress or fuel is invalid");
        }
        state.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.pending = tag.getBoolean("Pending");
        state.conflict = tag.getBoolean("Conflict");
        return state;
    }
}
