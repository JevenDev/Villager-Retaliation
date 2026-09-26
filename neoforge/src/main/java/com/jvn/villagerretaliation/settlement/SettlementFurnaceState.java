package com.jvn.villagerretaliation.settlement;

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
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

public final class SettlementFurnaceState {
    private static final String ID_TAG = "VillagerRetaliationSettlementFurnace";
    private static final String VERSION_TAG = "VillagerRetaliationSettlementFurnaceVersion";
    private static final String LAST_TICK_TAG = "VillagerRetaliationSettlementFurnaceTick";
    private static final List<String> FIELDS = List.of("Items", "BurnTime", "CookTime", "CookTimeTotal", "RecipesUsed");
    private final UUID id;
    private final UUID area;
    private final BlockPos position;
    private final ResourceLocation block;
    private CompoundTag baseline;
    private CompoundTag contents;
    private long checkedAt;
    private long version;
    private boolean pending;
    private boolean conflict;

    private SettlementFurnaceState(UUID id, UUID area, BlockPos position, ResourceLocation block) {
        this.id = id;
        this.area = area;
        this.position = position.immutable();
        this.block = block;
    }

    UUID id() {
        return this.id;
    }

    static void bindConstruction(AbstractFurnaceBlockEntity entity, UUID id) {
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

    public ResourceLocation block() {
        return this.block;
    }

    public boolean pending() {
        return this.pending;
    }

    public boolean conflict() {
        return this.conflict;
    }

    public int burnTime() {
        return this.contents.getInt("BurnTime");
    }

    public static void onVanillaTick(net.minecraft.world.level.Level level, AbstractFurnaceBlockEntity furnace) {
        if (!level.isClientSide && furnace.getPersistentData().hasUUID(ID_TAG)) {
            furnace.getPersistentData().putLong(LAST_TICK_TAG, level.getGameTime());
        }
    }

    static boolean supported(AbstractFurnaceBlockEntity furnace) {
        return furnace.getClass() == FurnaceBlockEntity.class || furnace.getClass() == BlastFurnaceBlockEntity.class
                || furnace.getClass() == SmokerBlockEntity.class;
    }

    static SettlementFurnaceState capture(ServerLevel level, UUID area, AbstractFurnaceBlockEntity furnace, long now) {
        var persistent = furnace.getPersistentData();
        UUID id = persistent.hasUUID(ID_TAG) ? persistent.getUUID(ID_TAG) : UUID.randomUUID();
        persistent.putUUID(ID_TAG, id);
        var result = new SettlementFurnaceState(id, area, furnace.getBlockPos(),
                BuiltInRegistries.BLOCK.getKey(furnace.getBlockState().getBlock()));
        result.observe(level.registryAccess(), furnace, now);
        return result;
    }

    boolean matches(AbstractFurnaceBlockEntity furnace) {
        return supported(furnace) && this.position.equals(furnace.getBlockPos())
                && this.block.equals(BuiltInRegistries.BLOCK.getKey(furnace.getBlockState().getBlock()))
                && furnace.getPersistentData().hasUUID(ID_TAG)
                && this.id.equals(furnace.getPersistentData().getUUID(ID_TAG));
    }

    boolean reconcile(HolderLookup.Provider provider, AbstractFurnaceBlockEntity furnace) {
        if (!matches(furnace)) {
            this.conflict = this.pending;
            return false;
        }
        if (!this.pending) {
            return true;
        }
        long applied = furnace.getPersistentData().getLong(VERSION_TAG);
        if (applied < this.version) {
            CompoundTag live = furnace.saveWithoutMetadata(provider);
            if (!fields(live).equals(this.baseline)) {
                this.conflict = true;
                return false;
            }
            FIELDS.forEach(key -> {
                live.remove(key);
                if (this.contents.contains(key)) {
                    live.put(key, this.contents.get(key).copy());
                }
            });
            furnace.loadWithComponents(live, provider);
            furnace.getPersistentData().putLong(VERSION_TAG, this.version);
        }
        this.pending = false;
        this.conflict = false;
        this.baseline = fields(furnace.saveWithoutMetadata(provider));
        this.contents = this.baseline.copy();
        this.version = Math.max(this.version, applied);
        return true;
    }

    void observe(HolderLookup.Provider provider, AbstractFurnaceBlockEntity furnace, long now) {
        refresh(provider, furnace);
        this.checkedAt = now;
    }

    void refresh(HolderLookup.Provider provider, AbstractFurnaceBlockEntity furnace) {
        if (this.pending) {
            throw new IllegalStateException("Pending furnace work must be reconciled before observation");
        }
        this.checkedAt = Math.max(this.checkedAt, furnace.getPersistentData().getLong(LAST_TICK_TAG));
        this.baseline = fields(furnace.saveWithoutMetadata(provider));
        this.contents = this.baseline.copy();
        this.version = Math.max(this.version, furnace.getPersistentData().getLong(VERSION_TAG));
        this.conflict = false;
    }

    void pause(long now) {
        this.checkedAt = now;
    }

    void missing() {
        this.conflict = this.pending;
    }

    NonNullList<ItemStack> items(HolderLookup.Provider provider) {
        var items = NonNullList.withSize(3, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(this.contents, items, provider);
        return items;
    }

    void updateItems(ServerLevel level, NonNullList<ItemStack> items) {
        var previous = items(level.registryAccess()).getFirst();
        CompoundTag updated = this.contents.copy();
        ContainerHelper.saveAllItems(updated, items, true, level.registryAccess());
        if (!ItemStack.isSameItemSameComponents(previous, items.getFirst())) {
            updated.putInt("CookTime", 0);
            updated.putInt("CookTimeTotal", recipe(level, items.getFirst()).map(holder -> holder.value().getCookingTime()).orElse(200));
        }
        update(updated);
    }

    void advance(ServerLevel level, long now) {
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
        this.checkedAt = now;
        if (elapsed == 0 || this.conflict) {
            return;
        }
        var items = items(level.registryAccess());
        int burn = Math.max(0, this.contents.getInt("BurnTime"));
        int progress = Math.max(0, this.contents.getInt("CookTime"));
        int total = Math.max(1, this.contents.getInt("CookTimeTotal"));
        CompoundTag recipesUsed = this.contents.getCompound("RecipesUsed").copy();
        RecipeHolder<AbstractCookingRecipe> recipe = recipe(level, items.getFirst()).orElse(null);
        for (int tick = 0; tick < elapsed; tick++) {
            if (burn > 0) {
                burn--;
            }
            ItemStack input = items.getFirst();
            ItemStack fuel = items.get(1);
            if (burn > 0 || !fuel.isEmpty() && !input.isEmpty()) {
                ItemStack result = recipe == null || input.isEmpty() ? ItemStack.EMPTY
                        : recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
                ItemStack output = items.get(2);
                boolean canBurn = !result.isEmpty() && result.getCount() + output.getCount() <= Math.min(64, result.getMaxStackSize())
                        && (output.isEmpty() || ItemStack.isSameItemSameComponents(output, result));
                if (burn == 0 && canBurn) {
                    burn = Math.max(0, fuel.getBurnTime(recipeType()) / (isFast() ? 2 : 1));
                    if (burn > 0) {
                        if (fuel.hasCraftingRemainingItem()) {
                            items.set(1, fuel.getCraftingRemainingItem());
                        } else {
                            fuel.shrink(1);
                            if (fuel.isEmpty()) {
                                items.set(1, fuel.getCraftingRemainingItem());
                            }
                        }
                    }
                }
                if (burn > 0 && canBurn) {
                    progress++;
                    if (progress >= total) {
                        progress = 0;
                        total = Math.max(1, recipe.value().getCookingTime());
                        items.set(2, result.copyWithCount(output.getCount() + result.getCount()));
                        if (input.is(Blocks.WET_SPONGE.asItem()) && items.get(1).is(Items.BUCKET)) {
                            items.set(1, new ItemStack(Items.WATER_BUCKET));
                        }
                        input.shrink(1);
                        String key = recipe.id().toString();
                        recipesUsed.putInt(key, Math.addExact(recipesUsed.getInt(key), 1));
                        if (input.isEmpty()) {
                            recipe = null;
                        }
                    }
                } else {
                    progress = 0;
                }
            } else if (progress > 0) {
                progress = Math.clamp(progress - 2, 0, total);
            }
        }
        CompoundTag updated = this.contents.copy();
        ContainerHelper.saveAllItems(updated, items, true, level.registryAccess());
        updated.putInt("BurnTime", burn);
        updated.putInt("CookTime", progress);
        updated.putInt("CookTimeTotal", total);
        updated.put("RecipesUsed", recipesUsed);
        update(updated);
    }

    @SuppressWarnings("unchecked")
    RecipeType<AbstractCookingRecipe> recipeType() {
        RecipeType<?> type = this.block.equals(ResourceLocation.withDefaultNamespace("blast_furnace")) ? RecipeType.BLASTING
                : this.block.equals(ResourceLocation.withDefaultNamespace("smoker")) ? RecipeType.SMOKING : RecipeType.SMELTING;
        return (RecipeType<AbstractCookingRecipe>) type;
    }

    java.util.Optional<RecipeHolder<AbstractCookingRecipe>> recipe(ServerLevel level, ItemStack input) {
        return input.isEmpty() ? java.util.Optional.empty()
                : level.getRecipeManager().getRecipeFor(recipeType(), new SingleRecipeInput(input), level);
    }

    private boolean isFast() {
        return this.block.equals(ResourceLocation.withDefaultNamespace("blast_furnace"))
                || this.block.equals(ResourceLocation.withDefaultNamespace("smoker"));
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
        FIELDS.forEach(key -> {
            if (source.contains(key)) {
                result.put(key, source.get(key).copy());
            }
        });
        return result;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        tag.putLong("Position", this.position.asLong());
        tag.putString("Block", this.block.toString());
        tag.put("Baseline", this.baseline.copy());
        tag.put("Contents", this.contents.copy());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putBoolean("Pending", this.pending);
        tag.putBoolean("Conflict", this.conflict);
        return tag;
    }

    static SettlementFurnaceState load(CompoundTag tag) {
        ResourceLocation block = ResourceLocation.tryParse(tag.getString("Block"));
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area") || !tag.contains("Position", Tag.TAG_LONG)
                || block == null || !List.of("furnace", "blast_furnace", "smoker").stream()
                        .map(ResourceLocation::withDefaultNamespace).toList().contains(block)) {
            throw new IllegalArgumentException("Settlement furnace identity, area, position or type is invalid");
        }
        var state = new SettlementFurnaceState(tag.getUUID("Id"), tag.getUUID("Area"),
                BlockPos.of(tag.getLong("Position")), block);
        state.baseline = fields(tag.getCompound("Baseline"));
        state.contents = fields(tag.getCompound("Contents"));
        state.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.pending = tag.getBoolean("Pending");
        state.conflict = tag.getBoolean("Conflict");
        return state;
    }
}
