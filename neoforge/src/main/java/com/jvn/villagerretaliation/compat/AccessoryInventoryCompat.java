package com.jvn.villagerretaliation.compat;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Optional, class-linkage-safe access to equipped Curios and Accessories items.
 *
 * <p>Accessories takes precedence when it is installed because its Curios compatibility layer may
 * expose the same underlying slots through both APIs.</p>
 */
public final class AccessoryInventoryCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ACCESSORIES = "accessories";
    private static final String CURIOS = "curios";

    private AccessoryInventoryCompat() {
    }

    public static Snapshot capture(LivingEntity entity) {
        if (entity == null) return Snapshot.empty();
        if (ModList.get().isLoaded(ACCESSORIES)) {
            try {
                return AccessoriesBridge.capture(entity);
            } catch (LinkageError | RuntimeException exception) {
                LOGGER.warn("Accessories inventory could not be captured.", exception);
            }
        }
        if (ModList.get().isLoaded(CURIOS)) {
            try {
                return CuriosBridge.capture(entity);
            } catch (LinkageError | RuntimeException exception) {
                LOGGER.warn("Curios inventory could not be captured.", exception);
            }
        }
        return Snapshot.empty();
    }

    public static List<ItemStack> equippedStacks(LivingEntity entity) {
        if (entity == null) return List.of();
        if (ModList.get().isLoaded(ACCESSORIES)) {
            try {
                return AccessoriesBridge.equippedStacks(entity);
            } catch (LinkageError | RuntimeException exception) {
                LOGGER.warn("Accessories inventory could not be queried.", exception);
            }
        }
        if (ModList.get().isLoaded(CURIOS)) {
            try {
                return CuriosBridge.equippedStacks(entity);
            } catch (LinkageError | RuntimeException exception) {
                LOGGER.warn("Curios inventory could not be queried.", exception);
            }
        }
        return List.of();
    }

    public record Snapshot(String backend, List<SlotSnapshot> slots) {
        public Snapshot {
            backend = backend == null ? "" : backend;
            slots = slots == null ? List.of() : List.copyOf(slots);
        }

        public static Snapshot empty() {
            return new Snapshot("", List.of());
        }

        public void clear(LivingEntity entity) {
            if (entity == null || this.backend.isBlank()) return;
            try {
                switch (this.backend) {
                    case ACCESSORIES -> AccessoriesBridge.clear(entity);
                    case CURIOS -> CuriosBridge.clear(entity);
                    default -> {
                    }
                }
            } catch (LinkageError | RuntimeException exception) {
                LOGGER.warn("Could not clear {} slots for a duel.", this.backend, exception);
            }
        }

        public void restore(LivingEntity entity) {
            if (entity == null || this.backend.isBlank()) return;
            try {
                switch (this.backend) {
                    case ACCESSORIES -> AccessoriesBridge.restore(entity, this.slots);
                    case CURIOS -> CuriosBridge.restore(entity, this.slots);
                    default -> {
                    }
                }
            } catch (LinkageError | RuntimeException exception) {
                LOGGER.warn("Could not restore {} slots after a duel.", this.backend, exception);
            }
        }

        public CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Backend", this.backend);
            ListTag savedSlots = new ListTag();
            for (SlotSnapshot slot : this.slots) {
                CompoundTag saved = new CompoundTag();
                saved.putString("Slot", slot.slot());
                saved.putInt("Index", slot.index());
                saved.put("Stack", slot.stack().saveOptional(provider));
                saved.put("Cosmetic", slot.cosmetic().saveOptional(provider));
                saved.putBoolean("Render", slot.render());
                savedSlots.add(saved);
            }
            tag.put("Slots", savedSlots);
            return tag;
        }

        public static Snapshot load(CompoundTag tag, HolderLookup.Provider provider) {
            if (tag == null
                    || !tag.contains("Backend", Tag.TAG_STRING)
                    || !tag.contains("Slots", Tag.TAG_LIST)) {
                return empty();
            }
            String backend = tag.getString("Backend");
            if (!backend.equals(ACCESSORIES) && !backend.equals(CURIOS)) return empty();
            List<SlotSnapshot> slots = new ArrayList<>();
            for (Tag raw : tag.getList("Slots", Tag.TAG_COMPOUND)) {
                CompoundTag saved = (CompoundTag) raw;
                if (!saved.contains("Slot", Tag.TAG_STRING)
                        || !saved.contains("Index", Tag.TAG_INT)
                        || !saved.contains("Stack", Tag.TAG_COMPOUND)
                        || !saved.contains("Cosmetic", Tag.TAG_COMPOUND)
                        || !saved.contains("Render", Tag.TAG_BYTE)) {
                    continue;
                }
                String slot = saved.getString("Slot");
                int index = saved.getInt("Index");
                if (slot.isBlank() || index < 0) continue;
                slots.add(new SlotSnapshot(
                        slot,
                        index,
                        ItemStack.parseOptional(provider, saved.getCompound("Stack")),
                        ItemStack.parseOptional(provider, saved.getCompound("Cosmetic")),
                        saved.getBoolean("Render")));
            }
            return new Snapshot(backend, slots);
        }
    }

    public record SlotSnapshot(
            String slot, int index, ItemStack stack, ItemStack cosmetic, boolean render) {
        public SlotSnapshot {
            if (slot == null || slot.isBlank()) throw new IllegalArgumentException("slot must not be blank");
            if (index < 0) throw new IllegalArgumentException("index must not be negative");
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            cosmetic = cosmetic == null ? ItemStack.EMPTY : cosmetic.copy();
        }
    }

    /** Loaded only after Accessories' mod id has been confirmed present. */
    private static final class AccessoriesBridge {
        private AccessoriesBridge() {
        }

        private static Snapshot capture(LivingEntity entity) {
            io.wispforest.accessories.api.AccessoriesCapability capability =
                    io.wispforest.accessories.api.AccessoriesCapability.get(entity);
            if (capability == null) return new Snapshot(ACCESSORIES, List.of());
            List<SlotSnapshot> slots = new ArrayList<>();
            capability.getContainers().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        io.wispforest.accessories.api.AccessoriesContainer container = entry.getValue();
                        for (int index = 0; index < container.getSize(); index++) {
                            List<Boolean> renders = container.renderOptions();
                            slots.add(new SlotSnapshot(
                                    entry.getKey(),
                                    index,
                                    container.getAccessories().getItem(index),
                                    container.getCosmeticAccessories().getItem(index),
                                    index >= renders.size() || renders.get(index)));
                        }
                    });
            return new Snapshot(ACCESSORIES, slots);
        }

        private static List<ItemStack> equippedStacks(LivingEntity entity) {
            io.wispforest.accessories.api.AccessoriesCapability capability =
                    io.wispforest.accessories.api.AccessoriesCapability.get(entity);
            if (capability == null) return List.of();
            return capability.getAllEquipped().stream()
                    .map(io.wispforest.accessories.api.slot.SlotEntryReference::stack)
                    .filter(stack -> !stack.isEmpty())
                    .toList();
        }

        private static void clear(LivingEntity entity) {
            io.wispforest.accessories.api.AccessoriesCapability capability =
                    io.wispforest.accessories.api.AccessoriesCapability.get(entity);
            if (capability == null) return;
            for (io.wispforest.accessories.api.AccessoriesContainer container
                    : List.copyOf(capability.getContainers().values())) {
                for (int index = 0; index < container.getSize(); index++) {
                    container.getAccessories().setItem(index, ItemStack.EMPTY);
                    container.getCosmeticAccessories().setItem(index, ItemStack.EMPTY);
                }
                container.markChanged();
                container.update();
            }
        }

        private static void restore(LivingEntity entity, List<SlotSnapshot> snapshots) {
            io.wispforest.accessories.api.AccessoriesCapability capability =
                    io.wispforest.accessories.api.AccessoriesCapability.get(entity);
            if (capability == null) return;
            clear(entity);
            List<SlotSnapshot> pending = new ArrayList<>(snapshots);
            pending.sort(Comparator.comparing(SlotSnapshot::slot).thenComparingInt(SlotSnapshot::index));
            int passes = Math.max(1, pending.size() + 1);
            while (!pending.isEmpty() && passes-- > 0) {
                int restoredBefore = pending.size();
                pending.removeIf(slot -> restoreAccessorySlot(capability, slot));
                capability.updateContainers();
                if (pending.size() == restoredBefore) break;
            }
            if (!pending.isEmpty()) {
                LOGGER.warn("Could not restore {} Accessories slots because they no longer exist.", pending.size());
            }
        }

        private static boolean restoreAccessorySlot(
                io.wispforest.accessories.api.AccessoriesCapability capability, SlotSnapshot slot) {
            io.wispforest.accessories.api.AccessoriesContainer container =
                    capability.getContainers().get(slot.slot());
            if (container == null || slot.index() >= container.getSize()) return false;
            container.getAccessories().setItem(slot.index(), slot.stack().copy());
            container.getCosmeticAccessories().setItem(slot.index(), slot.cosmetic().copy());
            if (slot.index() < container.renderOptions().size()) {
                container.renderOptions().set(slot.index(), slot.render());
            }
            container.markChanged();
            container.update();
            return true;
        }
    }

    /** Loaded only after Curios' mod id has been confirmed present. */
    private static final class CuriosBridge {
        private CuriosBridge() {
        }

        private static Snapshot capture(LivingEntity entity) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .map(handler -> {
                        List<SlotSnapshot> slots = new ArrayList<>();
                        handler.getCurios().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .forEach(entry -> {
                                    var stacks = entry.getValue().getStacks();
                                    var cosmetics = entry.getValue().getCosmeticStacks();
                                    var renders = entry.getValue().getRenders();
                                    for (int index = 0; index < entry.getValue().getSlots(); index++) {
                                        slots.add(new SlotSnapshot(
                                                entry.getKey(), index,
                                                stacks.getStackInSlot(index),
                                                cosmetics.getStackInSlot(index),
                                                index >= renders.size() || renders.get(index)));
                                    }
                                });
                        return new Snapshot(CURIOS, slots);
                    })
                    .orElseGet(() -> new Snapshot(CURIOS, List.of()));
        }

        private static List<ItemStack> equippedStacks(LivingEntity entity) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .map(handler -> {
                        List<ItemStack> stacks = new ArrayList<>();
                        var equipped = handler.getEquippedCurios();
                        for (int index = 0; index < equipped.getSlots(); index++) {
                            ItemStack stack = equipped.getStackInSlot(index);
                            if (!stack.isEmpty()) stacks.add(stack);
                        }
                        return List.copyOf(stacks);
                    })
                    .orElseGet(List::of);
        }

        private static void clear(LivingEntity entity) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                    handler.getCurios().values().forEach(slot -> {
                        for (int index = 0; index < slot.getSlots(); index++) {
                            slot.getStacks().setStackInSlot(index, ItemStack.EMPTY);
                            slot.getCosmeticStacks().setStackInSlot(index, ItemStack.EMPTY);
                        }
                    }));
        }

        private static void restore(LivingEntity entity, List<SlotSnapshot> snapshots) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                clear(entity);
                for (SlotSnapshot snapshot : snapshots) {
                    var slot = handler.getCurios().get(snapshot.slot());
                    if (slot == null || snapshot.index() >= slot.getSlots()) continue;
                    slot.getStacks().setStackInSlot(snapshot.index(), snapshot.stack().copy());
                    slot.getCosmeticStacks().setStackInSlot(snapshot.index(), snapshot.cosmetic().copy());
                    if (snapshot.index() < slot.getRenders().size()) {
                        slot.getRenders().set(snapshot.index(), snapshot.render());
                    }
                }
            });
        }
    }
}
